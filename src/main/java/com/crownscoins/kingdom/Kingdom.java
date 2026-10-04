package com.crownscoins.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Server-owned kingdom state. Instances are only mutated by {@link KingdomSavedData},
 * which marks the backing {@code SavedData} dirty after every successful mutation.
 */
public final class Kingdom {
    public static final int MIN_KINGDOM_NAME_LENGTH = 2;
    public static final int MAX_KINGDOM_NAME_LENGTH = 32;
    public static final int MIN_CURRENCY_NAME_LENGTH = 1;
    public static final int MAX_CURRENCY_NAME_LENGTH = 24;
    /** One bronze coin is the base unit used by every kingdom. */
    public static final int COPPER_COIN_VALUE = 1;
    /** Twenty bronze coins have the economic value of one iron coin. */
    public static final int IRON_COIN_VALUE = 20;
    /** Twenty-five iron coins have the economic value of one gold coin. */
    public static final int GOLD_COIN_VALUE = 500;

    /**
     * The serialized form deliberately contains only primitive, server-verifiable data.
     * Membership is encoded as UUIDs rather than player names so name changes do not
     * orphan a kingdom member. Coin values are the same for every kingdom, so they are
     * not stored; older saves that still contain them are read and the extra fields ignored.
     */
    public static final Codec<Kingdom> CODEC = Stored.CODEC.flatXmap(
        Stored::toKingdom,
        kingdom -> DataResult.success(Stored.of(kingdom))
    );

    private final UUID id;
    private final UUID founder;
    private final LinkedHashSet<UUID> members;
    private final String name;
    private final String currencyName;
    private final Symbol crest;

    /** Creates a new kingdom whose only initial member is its founder. */
    public Kingdom(UUID id, UUID founder, String name, String currencyName, Symbol crest) {
        this(id, founder, List.of(Objects.requireNonNull(founder, "founder")), name, currencyName, crest);
    }

    private Kingdom(
        UUID id,
        UUID founder,
        Collection<UUID> members,
        String name,
        String currencyName,
        Symbol crest
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.founder = Objects.requireNonNull(founder, "founder");
        this.name = normalizeText(name, "Kingdom name", MIN_KINGDOM_NAME_LENGTH, MAX_KINGDOM_NAME_LENGTH);
        this.currencyName = normalizeText(currencyName, "Currency name", MIN_CURRENCY_NAME_LENGTH, MAX_CURRENCY_NAME_LENGTH);
        this.crest = Objects.requireNonNull(crest, "crest");

        Objects.requireNonNull(members, "members");
        this.members = new LinkedHashSet<>();
        for (UUID member : members) {
            this.members.add(Objects.requireNonNull(member, "member"));
        }
        // Founder membership is an invariant, including for old/corrupted save files.
        this.members.add(this.founder);
    }

    public static Kingdom create(UUID founder, String name, String currencyName, Symbol crest) {
        return new Kingdom(UUID.randomUUID(), founder, name, currencyName, crest);
    }

    /**
     * Converts a display name to the key used for case-insensitive uniqueness checks.
     * Callers should validate the input first; this method is public so menus can offer
     * the same local feedback without becoming authoritative.
     */
    public static String canonicalName(String name) {
        return normalizeText(name, "Kingdom name", MIN_KINGDOM_NAME_LENGTH, MAX_KINGDOM_NAME_LENGTH).toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String raw, String field, int minLength, int maxLength) {
        Objects.requireNonNull(raw, field);
        String normalized = raw.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(field + " must contain " + minLength + "-" + maxLength + " characters");
        }
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isISOControl(codePoint) || codePoint == '§') {
                throw new IllegalArgumentException(field + " contains a disallowed control character");
            }
            offset += Character.charCount(codePoint);
        }
        return normalized;
    }

    public UUID id() {
        return id;
    }

    public UUID founder() {
        return founder;
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    private List<UUID> memberList() {
        return List.copyOf(members);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isFounder(UUID playerId) {
        return founder.equals(playerId);
    }

    public String name() {
        return name;
    }

    public String currencyName() {
        return currencyName;
    }

    public Symbol crest() {
        return crest;
    }

    /** Fixed catalog value of one coin of this metal, the same in every kingdom. */
    public int value(Metal metal) {
        return switch (Objects.requireNonNull(metal, "metal")) {
            case IRON -> IRON_COIN_VALUE;
            case COPPER -> COPPER_COIN_VALUE;
            case GOLD -> GOLD_COIN_VALUE;
        };
    }

    /* Package-private: used only while normalizing legacy saved-data crests. */
    Kingdom withCrest(Symbol replacementCrest) {
        return new Kingdom(this.id, this.founder, this.members, this.name, this.currencyName, replacementCrest);
    }

    /* Package-private: SavedData owns mutations so it can mark itself dirty. */
    Kingdom withCurrencyName(String replacementCurrencyName) {
        return new Kingdom(this.id, this.founder, this.members, this.name, replacementCurrencyName, this.crest);
    }

    /* Package-private: SavedData owns mutations so it can maintain the name index. */
    Kingdom withName(String replacementName) {
        return new Kingdom(this.id, this.founder, this.members, replacementName, this.currencyName, this.crest);
    }

    /* Package-private: SavedData owns mutations so it can mark itself dirty. */
    boolean addMember(UUID playerId) {
        return members.add(Objects.requireNonNull(playerId, "playerId"));
    }

    /* Package-private: the founder may never be removed. */
    boolean removeMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return !founder.equals(playerId) && members.remove(playerId);
    }

    /**
     * Raw saved form. Invalid data becomes a normal codec error instead of an
     * exception thrown while a world's saved data is being loaded.
     */
    private record Stored(
        UUID id,
        UUID founder,
        List<UUID> members,
        String name,
        String currencyName,
        Symbol crest
    ) {
        private static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Stored::id),
            UUIDUtil.CODEC.fieldOf("founder").forGetter(Stored::founder),
            UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Stored::members),
            Codec.STRING.fieldOf("name").forGetter(Stored::name),
            Codec.STRING.fieldOf("currency_name").forGetter(Stored::currencyName),
            Symbol.CODEC.fieldOf("crest").forGetter(Stored::crest)
        ).apply(instance, Stored::new));

        private static Stored of(Kingdom kingdom) {
            return new Stored(
                kingdom.id, kingdom.founder, kingdom.memberList(), kingdom.name, kingdom.currencyName, kingdom.crest
            );
        }

        private DataResult<Kingdom> toKingdom() {
            try {
                return DataResult.success(new Kingdom(id, founder, members, name, currencyName, crest));
            } catch (IllegalArgumentException | NullPointerException e) {
                return DataResult.error(() -> "Invalid saved kingdom: " + e.getMessage());
            }
        }
    }

    public enum Metal {
        IRON,
        COPPER,
        GOLD
    }
}
