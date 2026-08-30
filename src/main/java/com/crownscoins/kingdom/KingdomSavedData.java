package com.crownscoins.kingdom;

import com.crownscoins.CrownsCoins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Global, server-side repository of all Crowns & Coins kingdoms.
 *
 * <p>The data is intentionally attached to the server overworld so all dimensions
 * share one kingdom catalog. It is never constructed or mutated from a client.
 * {@link SavedData} writes it to the world's {@code data/crownscoins/kingdoms.dat}
 * file whenever the server saves after a change.</p>
 */
public final class KingdomSavedData extends SavedData {
    private static final String DATA_PATH = "kingdoms";

    public static final Codec<KingdomSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kingdom.CODEC.listOf().fieldOf("kingdoms").forGetter(KingdomSavedData::kingdomList)
        ).apply(instance, KingdomSavedData::new)
    );

    public static final SavedDataType<KingdomSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, DATA_PATH),
        KingdomSavedData::new,
        CODEC
    );

    private final Map<UUID, Kingdom> kingdomsById = new LinkedHashMap<>();
    private final Map<UUID, UUID> kingdomByMember = new HashMap<>();
    private final Map<String, UUID> kingdomByCanonicalName = new HashMap<>();

    /** Creates an empty catalog for a new world. */
    public KingdomSavedData() {
    }

    private KingdomSavedData(List<Kingdom> kingdoms) {
        Objects.requireNonNull(kingdoms, "kingdoms");
        boolean migratedLegacyCrest = false;
        for (Kingdom kingdom : kingdoms) {
            Kingdom existing = Objects.requireNonNull(kingdom, "kingdom");
            Symbol normalizedCrest = KingdomCrest.normalizeLegacy(existing.crest());
            if (normalizedCrest != existing.crest()) {
                existing = existing.withCrest(normalizedCrest);
                migratedLegacyCrest = true;
            }
            indexExisting(existing);
        }
        if (migratedLegacyCrest) {
            setDirty();
        }
    }

    /**
     * Returns the one global store for this server, regardless of the supplied
     * dimension. Call this only from server-side code.
     */
    public static KingdomSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Returns all kingdoms in stable creation/load order. */
    public Collection<Kingdom> kingdoms() {
        return List.copyOf(kingdomsById.values());
    }

    public Optional<Kingdom> find(UUID kingdomId) {
        return Optional.ofNullable(kingdomsById.get(Objects.requireNonNull(kingdomId, "kingdomId")));
    }

    public Optional<Kingdom> findByMember(UUID playerId) {
        UUID kingdomId = kingdomByMember.get(Objects.requireNonNull(playerId, "playerId"));
        return kingdomId == null ? Optional.empty() : Optional.ofNullable(kingdomsById.get(kingdomId));
    }

    public Optional<Kingdom> findByName(String name) {
        UUID kingdomId = kingdomByCanonicalName.get(Kingdom.canonicalName(name));
        return kingdomId == null ? Optional.empty() : Optional.ofNullable(kingdomsById.get(kingdomId));
    }

    public boolean hasKingdom(UUID playerId) {
        return kingdomByMember.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Atomically validates and creates a kingdom. The caller must already have
     * validated that this request originated from a valid server-side Mint House
     * menu; this repository verifies the global invariants that a menu cannot.
     *
     * @throws IllegalStateException if the founder already belongs to a kingdom
     * @throws IllegalArgumentException if the name is in use or a kingdom field is invalid
     */
    public Kingdom createKingdom(
        UUID founder,
        String name,
        String currencyName,
        Symbol crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) {
        Objects.requireNonNull(founder, "founder");
        if (hasKingdom(founder)) {
            throw new IllegalStateException("A player may belong to only one kingdom");
        }

        String canonicalName = Kingdom.canonicalName(name);
        if (kingdomByCanonicalName.containsKey(canonicalName)) {
            throw new IllegalArgumentException("A kingdom with that name already exists");
        }

        Kingdom kingdom = Kingdom.create(founder, name, currencyName, crest, ironValue, copperValue, goldValue);
        indexExisting(kingdom);
        setDirty();
        return kingdom;
    }

    /**
     * Adds a player to a kingdom only when they do not already belong to another one.
     * It is ready for future member-management UI without weakening the one-kingdom
     * rule. Returns {@code true} only when a membership was actually added.
     */
    public boolean addMember(UUID kingdomId, UUID playerId) {
        Kingdom kingdom = kingdomsById.get(Objects.requireNonNull(kingdomId, "kingdomId"));
        Objects.requireNonNull(playerId, "playerId");
        if (kingdom == null || kingdomByMember.containsKey(playerId)) {
            return false;
        }
        if (!kingdom.addMember(playerId)) {
            return false;
        }
        kingdomByMember.put(playerId, kingdomId);
        setDirty();
        return true;
    }

    /**
     * Removes a non-founder member. The founder is intentionally permanent until a
     * future explicit ownership-transfer/removal feature is designed.
     */
    public boolean removeMember(UUID kingdomId, UUID playerId) {
        Kingdom kingdom = kingdomsById.get(Objects.requireNonNull(kingdomId, "kingdomId"));
        Objects.requireNonNull(playerId, "playerId");
        if (kingdom == null || !kingdomId.equals(kingdomByMember.get(playerId)) || !kingdom.removeMember(playerId)) {
            return false;
        }
        kingdomByMember.remove(playerId);
        setDirty();
        return true;
    }

    private List<Kingdom> kingdomList() {
        return new ArrayList<>(kingdomsById.values());
    }

    private void indexExisting(Kingdom kingdom) {
        Kingdom existing = kingdomsById.putIfAbsent(kingdom.id(), kingdom);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate kingdom UUID in saved data: " + kingdom.id());
        }

        String canonicalName = Kingdom.canonicalName(kingdom.name());
        UUID existingName = kingdomByCanonicalName.putIfAbsent(canonicalName, kingdom.id());
        if (existingName != null) {
            kingdomsById.remove(kingdom.id());
            throw new IllegalArgumentException("Duplicate kingdom name in saved data: " + kingdom.name());
        }

        List<UUID> indexedMembers = new ArrayList<>();
        for (UUID member : kingdom.members()) {
            UUID previousKingdom = kingdomByMember.putIfAbsent(member, kingdom.id());
            if (previousKingdom != null) {
                for (UUID indexedMember : indexedMembers) {
                    kingdomByMember.remove(indexedMember, kingdom.id());
                }
                kingdomByCanonicalName.remove(canonicalName, kingdom.id());
                kingdomsById.remove(kingdom.id());
                throw new IllegalArgumentException("Member " + member + " belongs to more than one kingdom in saved data");
            }
            indexedMembers.add(member);
        }
    }
}
