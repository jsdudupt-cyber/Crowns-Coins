package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client refresh of the names and member list shown by an open Mint
 * House screen, sent after the founder changes them so the screen never has to
 * be closed and reopened. It is display data only and authorizes nothing.
 */
public record KingdomInfoPayload(int containerId, String kingdomName, String currencyName, List<String> memberNames)
    implements CustomPacketPayload {
    public static final Type<KingdomInfoPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "kingdom_info")
    );

    public static final StreamCodec<ByteBuf, KingdomInfoPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        KingdomInfoPayload::containerId,
        ByteBufCodecs.stringUtf8(Kingdom.MAX_KINGDOM_NAME_LENGTH),
        KingdomInfoPayload::kingdomName,
        ByteBufCodecs.stringUtf8(Kingdom.MAX_CURRENCY_NAME_LENGTH),
        KingdomInfoPayload::currencyName,
        ByteBufCodecs.stringUtf8(UpdateMembersPayload.MAX_PLAYER_NAME_LENGTH).apply(ByteBufCodecs.list(64)),
        KingdomInfoPayload::memberNames,
        KingdomInfoPayload::new
    );

    @Override
    public Type<KingdomInfoPayload> type() {
        return TYPE;
    }
}
