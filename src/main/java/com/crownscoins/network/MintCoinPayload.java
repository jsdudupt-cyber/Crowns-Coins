package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client intent to stamp base coins from the Mint House chest with a chosen design.
 *
 * <p>The client supplies only a metal id, a catalog shape id and a requested
 * amount ({@code 0} means every matching base coin). It never supplies an
 * ItemStack, a kingdom id, a Mint House position, or any currency metadata. The
 * server clamps the amount to what the chest really holds.</p>
 */
public record MintCoinPayload(int containerId, int metalId, int shapeId, int quantity) implements CustomPacketPayload {
    public static final int ALL = 0;

    public static final Type<MintCoinPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "mint_coin")
    );

    public static final StreamCodec<ByteBuf, MintCoinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::containerId,
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::metalId,
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::shapeId,
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::quantity,
            MintCoinPayload::new
    );

    @Override
    public Type<MintCoinPayload> type() {
        return TYPE;
    }
}
