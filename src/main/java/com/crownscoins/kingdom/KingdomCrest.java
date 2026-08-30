package com.crownscoins.kingdom;

import java.util.Locale;
import java.util.Optional;

/**
 * The ten heraldic crests available for the centre of a minted coin.
 * Each crest deliberately reuses a symbol that already has a client texture.
 */
public enum KingdomCrest {
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

    public String translationKey() {
        return "crest.crownscoins." + name().toLowerCase(Locale.ROOT);
    }
}
