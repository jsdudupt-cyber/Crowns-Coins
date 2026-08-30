package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client intent to mint the matching ingot stack at the Mint House represented by the currently open server menu.
 *
 * <p>The client supplies only a metal id, a visual style id, and catalog symbol ids. It never supplies
 * an ItemStack, a quantity, a kingdom id, a Mint House position, or any currency metadata.</p>
 */
public record MintCoinPayload(int containerId, int metalId, int styleId, List<Integer> symbolIds) implements CustomPacketPayload {
    public static final int MAX_SYMBOLS = CoinData.REQUIRED_SECONDARY_SYMBOLS;

    public static final Type<MintCoinPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "mint_coin")
    );

    /** Decoding refuses collections with more than the two allowed secondary symbols. */
    public static final StreamCodec<ByteBuf, List<Integer>> SYMBOL_IDS_CODEC = ByteBufCodecs.VAR_INT.apply(
            ByteBufCodecs.list(MAX_SYMBOLS)
    );

    public static final StreamCodec<ByteBuf, MintCoinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::containerId,
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::metalId,
            ByteBufCodecs.VAR_INT,
            MintCoinPayload::styleId,
            SYMBOL_IDS_CODEC,
            MintCoinPayload::symbolIds,
            MintCoinPayload::new
    );

    public MintCoinPayload {
        // Prevent a UI or later menu code from mutating the decoded selection after it was validated.
        symbolIds = List.copyOf(symbolIds);
    }

    @Override
    public Type<MintCoinPayload> type() {
        return TYPE;
    }
}
