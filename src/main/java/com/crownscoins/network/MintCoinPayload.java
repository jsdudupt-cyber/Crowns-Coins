package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client intent to mint the matching ingot stack at the Mint House represented by the currently open server menu.
 *
 * <p>The client supplies only a metal id and a catalog shape id. It never
 * supplies an ItemStack, a quantity, a kingdom id, a Mint House position, or
 * any currency metadata.</p>
 */
public record MintCoinPayload(int containerId, int metalId, int shapeId) implements CustomPacketPayload {

    public static final Type<MintCoinPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "mint_coin")
    );

    /**
     * Reads the former two-field packet as shape zero when it is encountered.
     * New packets append the selected shape, keeping the legacy metal intent
     * intact while the server remains the authority that validates both ids.
     */
    public static final StreamCodec<ByteBuf, MintCoinPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MintCoinPayload decode(ByteBuf buffer) {
            int containerId = ByteBufCodecs.VAR_INT.decode(buffer);
            int metalId = ByteBufCodecs.VAR_INT.decode(buffer);
            int shapeId = buffer.isReadable()
                ? ByteBufCodecs.VAR_INT.decode(buffer)
                : CoinData.DEFAULT_SHAPE_ID;
            return new MintCoinPayload(containerId, metalId, shapeId);
        }

        @Override
        public void encode(ByteBuf buffer, MintCoinPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buffer, payload.containerId());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.metalId());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.shapeId());
        }
    };

    /** Convenience overload while older screens transition to shape selection. */
    public MintCoinPayload(int containerId, int metalId) {
        this(containerId, metalId, CoinData.DEFAULT_SHAPE_ID);
    }

    @Override
    public Type<MintCoinPayload> type() {
        return TYPE;
    }
}
