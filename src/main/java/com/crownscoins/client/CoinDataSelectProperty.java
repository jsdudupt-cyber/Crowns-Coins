package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Client-only selector that picks a coin's model from the network-synchronized
 * {@link CoinData} shape. The item definitions select on {@code crownscoins:coin_shape}.
 */
public record CoinDataSelectProperty() implements SelectItemModelProperty<Integer> {
    private static final Codec<Integer> SHAPE_VALUE_CODEC = Codec.intRange(CoinData.DEFAULT_SHAPE_ID, CoinData.MAX_SHAPE_ID);
    public static final Type<CoinDataSelectProperty, Integer> SHAPE_TYPE = Type.create(
        MapCodec.unit(new CoinDataSelectProperty()),
        SHAPE_VALUE_CODEC
    );

    @Override
    public @Nullable Integer get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        return data == null ? null : data.shapeId();
    }

    @Override
    public Codec<Integer> valueCodec() {
        return SHAPE_VALUE_CODEC;
    }

    @Override
    public Type<CoinDataSelectProperty, Integer> type() {
        return SHAPE_TYPE;
    }
}
