package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A founder's request to add or remove one player from the kingdom bound to the
 * currently open Mint House. It carries only a player name: the kingdom and
 * the block come from the live server-side menu.
 */
public record UpdateMembersPayload(int containerId, boolean add, String playerName) implements CustomPacketPayload {
    public static final int MAX_PLAYER_NAME_LENGTH = 32;

    public static final Type<UpdateMembersPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "update_members")
    );

    public static final StreamCodec<ByteBuf, UpdateMembersPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        UpdateMembersPayload::containerId,
        ByteBufCodecs.BOOL,
        UpdateMembersPayload::add,
        ByteBufCodecs.stringUtf8(MAX_PLAYER_NAME_LENGTH),
        UpdateMembersPayload::playerName,
        UpdateMembersPayload::new
    );

    @Override
    public Type<UpdateMembersPayload> type() {
        return TYPE;
    }
}
