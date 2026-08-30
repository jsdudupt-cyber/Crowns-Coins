package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Symbol;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Client-only selectors read only the network-synchronized CoinData component. */
public record CoinDataSelectProperty(Selector selector) implements SelectItemModelProperty<Integer> {
    private static final Codec<Integer> VALUE_CODEC = Codec.intRange(1, CoinData.MAX_STYLE_ID);
    public static final Type<CoinDataSelectProperty, Integer> STYLE_TYPE = createType(Selector.STYLE);
    public static final Type<CoinDataSelectProperty, Integer> CREST_TYPE = createType(Selector.CREST);
    public static final Type<CoinDataSelectProperty, Integer> SYMBOL_ONE_TYPE = createType(Selector.SYMBOL_ONE);
    public static final Type<CoinDataSelectProperty, Integer> SYMBOL_TWO_TYPE = createType(Selector.SYMBOL_TWO);
    public static final Type<CoinDataSelectProperty, Integer> SYMBOL_THREE_TYPE = createType(Selector.SYMBOL_THREE);

    private static Type<CoinDataSelectProperty, Integer> createType(Selector selector) {
        return Type.create(MapCodec.unit(new CoinDataSelectProperty(selector)), VALUE_CODEC);
    }

    @Override
    public @Nullable Integer get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        if (data == null) {
            return null;
        }
        return switch (selector) {
            case STYLE -> data.styleId();
            // Every coin face uses the Royal Crown as its fixed central mark.
            // The kingdom crest remains in CoinData for provenance and tooltips.
            case CREST -> Symbol.CROWN.id();
            case SYMBOL_ONE -> symbolId(data, 0);
            case SYMBOL_TWO -> symbolId(data, 1);
            case SYMBOL_THREE -> symbolId(data, 2);
        };
    }

    private static @Nullable Integer symbolId(CoinData data, int index) {
        return data.symbols().size() > index ? data.symbols().get(index).id() : null;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return VALUE_CODEC;
    }

    @Override
    public Type<CoinDataSelectProperty, Integer> type() {
        return switch (selector) {
            case STYLE -> STYLE_TYPE;
            case CREST -> CREST_TYPE;
            case SYMBOL_ONE -> SYMBOL_ONE_TYPE;
            case SYMBOL_TWO -> SYMBOL_TWO_TYPE;
            case SYMBOL_THREE -> SYMBOL_THREE_TYPE;
        };
    }

    public enum Selector {
        STYLE,
        CREST,
        SYMBOL_ONE,
        SYMBOL_TWO,
        SYMBOL_THREE
    }
}
