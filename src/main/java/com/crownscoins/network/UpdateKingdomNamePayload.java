package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Founder-only request to rename the kingdom bound to the currently open Mint
 * House. There is no client-supplied kingdom ID, so the server always resolves
 * the live block binding before applying the change.
 */
public record UpdateKingdomNamePayload(int containerId, String kingdomName) implements CustomPacketPayload {
    public static final Type<UpdateKingdomNamePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "update_kingdom_name")
    );

    public static final StreamCodec<ByteBuf, UpdateKingdomNamePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        UpdateKingdomNamePayload::containerId,
        ByteBufCodecs.stringUtf8(Kingdom.MAX_KINGDOM_NAME_LENGTH),
        UpdateKingdomNamePayload::kingdomName,
        UpdateKingdomNamePayload::new
    );

    @Override
    public Type<UpdateKingdomNamePayload> type() {
        return TYPE;
    }
}
