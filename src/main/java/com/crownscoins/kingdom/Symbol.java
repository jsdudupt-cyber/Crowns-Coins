package com.crownscoins.kingdom;
import com.mojang.serialization.Codec;

/** Fixed server-authoritative catalog. Clients transmit only these IDs. */
public enum Symbol {
    SUN, MOON, STAR, CROWN, SWORD, SHIELD, TOWER, DRAGON, WOLF, EAGLE,
    LION, HORSE, HAMMER, ANVIL, HEART, FLAME, WAVE, LEAF, FLOWER, DIAMOND,
    MOUNTAIN, RIVER, CROSS, LIGHTNING, COMPASS;

    public static Symbol byId(int id) {
        if (id < 1 || id > values().length) throw new IllegalArgumentException("Unknown symbol id: " + id);
        return values()[id - 1];
    }

    public int id() { return ordinal() + 1; }
    public static final Codec<Symbol> CODEC = Codec.STRING.xmap(Symbol::valueOf, Symbol::name);
}
