package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.KingdomNames;
import com.crownscoins.kingdom.Kingdom;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client list of every kingdom's current name and currency, so coin tooltips and
 * titles stay correct after a rename. Display data only; it authorizes nothing.
 */
public record KingdomNamesPayload(List<KingdomNames.Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 1024;

    public static final Type<KingdomNamesPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "kingdom_names")
    );

    private static final StreamCodec<ByteBuf, KingdomNames.Entry> ENTRY_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        KingdomNames.Entry::kingdomId,
        ByteBufCodecs.stringUtf8(Kingdom.MAX_KINGDOM_NAME_LENGTH),
        KingdomNames.Entry::kingdomName,
        ByteBufCodecs.stringUtf8(Kingdom.MAX_CURRENCY_NAME_LENGTH),
        KingdomNames.Entry::currencyName,
        KingdomNames.Entry::new
    );

    public static final StreamCodec<ByteBuf, KingdomNamesPayload> STREAM_CODEC = StreamCodec.composite(
        ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
        KingdomNamesPayload::entries,
        KingdomNamesPayload::new
    );

    @Override
    public Type<KingdomNamesPayload> type() {
        return TYPE;
    }
}
