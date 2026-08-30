package com.crownscoins.kingdom;

import java.util.Locale;
import java.util.Optional;

/**
 * The heraldic crests available for the centre of a minted coin.
 * Each crest deliberately reuses a symbol that already has a client texture.
 */
public enum KingdomCrest {
    ROYAL_CROWN(Symbol.CROWN),
    CROWNED_LION(Symbol.LION),
    DOUBLE_HEADED_EAGLE(Symbol.EAGLE),
    WOLF_HEAD(Symbol.WOLF),
    DRAGON(Symbol.DRAGON),
    TOWER_CASTLE(Symbol.TOWER),
    CROSSED_SWORDS(Symbol.SWORD),
    ANCIENT_TREE(Symbol.LEAF),
    SUN_AND_MOON(Symbol.SUN),
    STAG(Symbol.HORSE),
    SHIP(Symbol.COMPASS);

    private final Symbol symbol;

    KingdomCrest(Symbol symbol) {
        this.symbol = symbol;
    }

    public int id() {
        return ordinal() + 1;
    }

    public Symbol symbol() {
        return symbol;
    }

    public static KingdomCrest byId(int id) {
        if (id < 1 || id > values().length) {
            throw new IllegalArgumentException("Unknown kingdom crest id: " + id);
        }
        return values()[id - 1];
    }

    public static Optional<KingdomCrest> fromSymbol(Symbol symbol) {
        for (KingdomCrest crest : values()) {
            if (crest.symbol == symbol) {
                return Optional.of(crest);
            }
        }
        return Optional.empty();
    }

    public static boolean isSupported(Symbol symbol) {
        return fromSymbol(symbol).isPresent();
    }

    /**
     * Converts the fifteen early prototype symbols into the nearest one of the
     * permanent heraldic crests. It keeps older development worlds usable
     * after the crest catalogue was narrowed to the finalized designs.
     */
    public static Symbol normalizeLegacy(Symbol symbol) {
        if (isSupported(symbol)) {
            return symbol;
        }
        return switch (symbol) {
            case MOON, STAR -> Symbol.SUN;
            case CROWN, HEART -> Symbol.LION;
            case SHIELD, CROSS -> Symbol.TOWER;
            case HAMMER, ANVIL -> Symbol.SWORD;
            case FLAME -> Symbol.DRAGON;
            case WAVE, RIVER -> Symbol.COMPASS;
            case FLOWER -> Symbol.LEAF;
            case DIAMOND, LIGHTNING -> Symbol.EAGLE;
            case MOUNTAIN -> Symbol.WOLF;
            case SUN, SWORD, TOWER, DRAGON, WOLF, EAGLE, LION, HORSE, LEAF, COMPASS -> symbol;
        };
    }

    public String translationKey() {
        return "crest.crownscoins." + name().toLowerCase(Locale.ROOT);
    }
}
