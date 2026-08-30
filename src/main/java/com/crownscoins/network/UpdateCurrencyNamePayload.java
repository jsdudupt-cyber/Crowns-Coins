package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A founder's request to rename the currency of the kingdom bound to the
 * currently open Mint House. It deliberately has no kingdom or block target.
 */
public record UpdateCurrencyNamePayload(int containerId, String currencyName) implements CustomPacketPayload {
    public static final Type<UpdateCurrencyNamePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "update_currency_name")
    );

    public static final StreamCodec<ByteBuf, UpdateCurrencyNamePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        UpdateCurrencyNamePayload::containerId,
        ByteBufCodecs.stringUtf8(Kingdom.MAX_CURRENCY_NAME_LENGTH),
        UpdateCurrencyNamePayload::currencyName,
        UpdateCurrencyNamePayload::new
    );

    @Override
    public Type<UpdateCurrencyNamePayload> type() {
        return TYPE;
    }
}
