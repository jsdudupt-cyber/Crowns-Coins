package com.crownscoins.kingdom;

import com.mojang.serialization.Codec;
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
    public static final int MIN_COIN_VALUE = 1;
    public static final int MAX_COIN_VALUE = 1_000_000;
    /** One bronze coin is the base unit used by every kingdom. */
    public static final int COPPER_COIN_VALUE = 1;
    /** Twenty bronze coins have the economic value of one iron coin. */
    public static final int IRON_COIN_VALUE = 20;
    /** Twenty-five iron coins have the economic value of one gold coin. */
    public static final int GOLD_COIN_VALUE = 500;

    /**
     * The serialized form deliberately contains only primitive, server-verifiable data.
     * Membership is encoded as UUIDs rather than player names so name changes do not
     * orphan a kingdom member.
     */
    public static final Codec<Kingdom> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Kingdom::id),
            UUIDUtil.CODEC.fieldOf("founder").forGetter(Kingdom::founder),
            UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Kingdom::memberList),
            Codec.STRING.fieldOf("name").forGetter(Kingdom::name),
            Codec.STRING.fieldOf("currency_name").forGetter(Kingdom::currencyName),
            Symbol.CODEC.fieldOf("crest").forGetter(Kingdom::crest),
            Codec.INT.fieldOf("iron_value").forGetter(Kingdom::ironValue),
            Codec.INT.fieldOf("copper_value").forGetter(Kingdom::copperValue),
            Codec.INT.fieldOf("gold_value").forGetter(Kingdom::goldValue)
        ).apply(instance, Kingdom::fromPersistentData)
    );

    private final UUID id;
    private final UUID founder;
    private final LinkedHashSet<UUID> members;
    private final String name;
    private final String currencyName;
    private final Symbol crest;
    private final int ironValue;
    private final int copperValue;
    private final int goldValue;

    /** Creates a new kingdom whose only initial member is its founder. */
    public Kingdom(
        UUID id,
        UUID founder,
        String name,
        String currencyName,
        Symbol crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) {
        this(id, founder, List.of(Objects.requireNonNull(founder, "founder")), name, currencyName, crest, ironValue, copperValue, goldValue);
    }

    private Kingdom(
        UUID id,
        UUID founder,
        Collection<UUID> members,
        String name,
        String currencyName,
        Symbol crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.founder = Objects.requireNonNull(founder, "founder");
        this.name = normalizeText(name, "Kingdom name", MIN_KINGDOM_NAME_LENGTH, MAX_KINGDOM_NAME_LENGTH);
        this.currencyName = normalizeText(currencyName, "Currency name", MIN_CURRENCY_NAME_LENGTH, MAX_CURRENCY_NAME_LENGTH);
        this.crest = Objects.requireNonNull(crest, "crest");
        validateValue(ironValue);
        validateValue(copperValue);
        validateValue(goldValue);
        this.ironValue = ironValue;
        this.copperValue = copperValue;
        this.goldValue = goldValue;

        Objects.requireNonNull(members, "members");
        this.members = new LinkedHashSet<>();
        for (UUID member : members) {
            this.members.add(Objects.requireNonNull(member, "member"));
        }
        // Founder membership is an invariant, including for old/corrupted save files.
        this.members.add(this.founder);
    }

    private static Kingdom fromPersistentData(
        UUID id,
        UUID founder,
        List<UUID> members,
        String name,
        String currencyName,
        Symbol crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) {
        return new Kingdom(id, founder, members, name, currencyName, crest, ironValue, copperValue, goldValue);
    }

    public static Kingdom create(
        UUID founder,
        String name,
        String currencyName,
        Symbol crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) {
        return new Kingdom(UUID.randomUUID(), founder, name, currencyName, crest, ironValue, copperValue, goldValue);
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
            if (Character.isISOControl(codePoint) || codePoint == '\u00A7') {
                throw new IllegalArgumentException(field + " contains a disallowed control character");
            }
            offset += Character.charCount(codePoint);
        }
        return normalized;
    }

    private static void validateValue(int value) {
        if (value < MIN_COIN_VALUE || value > MAX_COIN_VALUE) {
            throw new IllegalArgumentException("Coin value must be between " + MIN_COIN_VALUE + " and " + MAX_COIN_VALUE);
        }
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

    public int ironValue() {
        return ironValue;
    }

    public int copperValue() {
        return copperValue;
    }

    public int goldValue() {
        return goldValue;
    }

    public int value(Metal metal) {
        return switch (Objects.requireNonNull(metal, "metal")) {
            case IRON -> ironValue;
            case COPPER -> copperValue;
            case GOLD -> goldValue;
        };
    }

    /** Returns whether this kingdom follows the shared Crown & Coins denomination. */
    public boolean hasStandardEconomy() {
        return isStandardEconomy(this.ironValue, this.copperValue, this.goldValue);
    }

    /** Validates the fixed denomination used for every kingdom currency. */
    public static boolean isStandardEconomy(int ironValue, int copperValue, int goldValue) {
        return ironValue == IRON_COIN_VALUE
            && copperValue == COPPER_COIN_VALUE
            && goldValue == GOLD_COIN_VALUE;
    }

    /* Package-private: used only while normalizing legacy saved-data crests. */
    Kingdom withCrest(Symbol replacementCrest) {
        return new Kingdom(
            this.id,
            this.founder,
            this.members,
            this.name,
            this.currencyName,
            replacementCrest,
            this.ironValue,
            this.copperValue,
            this.goldValue
        );
    }

    /* Package-private: SavedData owns mutations so it can mark itself dirty. */
    Kingdom withCurrencyName(String replacementCurrencyName) {
        return new Kingdom(
            this.id,
            this.founder,
            this.members,
            this.name,
            replacementCurrencyName,
            this.crest,
            this.ironValue,
            this.copperValue,
            this.goldValue
        );
    }

    /* Package-private: upgrades legacy saves to the common denomination. */
    Kingdom withStandardEconomy() {
        return new Kingdom(
            this.id,
            this.founder,
            this.members,
            this.name,
            this.currencyName,
            this.crest,
            IRON_COIN_VALUE,
            COPPER_COIN_VALUE,
            GOLD_COIN_VALUE
        );
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

    public enum Metal {
        IRON,
        COPPER,
        GOLD
    }
}
