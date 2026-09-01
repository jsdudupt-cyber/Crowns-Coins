package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client intent to mint the matching ingot stack at the Mint House represented by the currently open server menu.
 *
 * <p>The client supplies only a metal id. It never supplies an ItemStack, a
 * quantity, a kingdom id, a Mint House position, or any currency metadata.</p>
 */
public record MintCoinPayload(int containerId, int metalId) implements CustomPacketPayload {

    public static final Type<MintCoinPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "mint_coin")
    );

    public static final StreamCodec<ByteBuf, MintCoinPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        MintCoinPayload::containerId,
        ByteBufCodecs.VAR_INT,
        MintCoinPayload::metalId,
        MintCoinPayload::new
    );

    @Override
    public Type<MintCoinPayload> type() {
        return TYPE;
    }
}
