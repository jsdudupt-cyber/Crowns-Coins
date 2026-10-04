package com.crownscoins.kingdom;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/** Server-side player name lookups shared by the member screen and the admin commands. */
public final class PlayerLookup {
    private PlayerLookup() {
    }

    /** Finds a player by name: online players first, then the server's name cache. */
    public static Optional<NameAndId> byName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(new NameAndId(online.getGameProfile()));
        }
        return server.services().nameToIdCache().get(name);
    }

    /** A readable name for a player id: the live name, else the cached one, else a short id. */
    public static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().name();
        }
        return server.services().nameToIdCache().get(id).map(NameAndId::name).orElse(id.toString().substring(0, 8));
    }
}
