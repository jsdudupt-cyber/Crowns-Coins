package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client intent to create a kingdom at the Mint House represented by the currently open server menu.
 *
 * <p>The target block, dimension, founder, and kingdom id are intentionally absent. The server derives
 * all of them from the open menu and the sending player.</p>
 */
public record CreateKingdomPayload(
        int containerId,
        String kingdomName,
        String currencyName,
        int crestId,
        int ironValue,
        int copperValue,
        int goldValue
) implements CustomPacketPayload {
    public static final int MAX_KINGDOM_NAME_LENGTH = 32;
    public static final int MAX_CURRENCY_NAME_LENGTH = 24;

    public static final Type<CreateKingdomPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "create_kingdom")
    );

    /**
     * Transport limits are deliberately narrow. Semantic checks (blank names, values, and crest catalog
     * membership) remain server-side in the menu/service handling this payload.
     */
    public static final StreamCodec<ByteBuf, CreateKingdomPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CreateKingdomPayload::containerId,
            ByteBufCodecs.stringUtf8(MAX_KINGDOM_NAME_LENGTH),
            CreateKingdomPayload::kingdomName,
            ByteBufCodecs.stringUtf8(MAX_CURRENCY_NAME_LENGTH),
            CreateKingdomPayload::currencyName,
            ByteBufCodecs.VAR_INT,
            CreateKingdomPayload::crestId,
            ByteBufCodecs.VAR_INT,
            CreateKingdomPayload::ironValue,
            ByteBufCodecs.VAR_INT,
            CreateKingdomPayload::copperValue,
            ByteBufCodecs.VAR_INT,
            CreateKingdomPayload::goldValue,
            CreateKingdomPayload::new
    );

    @Override
    public Type<CreateKingdomPayload> type() {
        return TYPE;
    }
}
