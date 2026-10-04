package com.crownscoins.network;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.KingdomNames;
import com.crownscoins.kingdom.KingdomSavedData;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps every client's copy of the kingdom names up to date. */
@EventBusSubscriber(modid = CrownsCoins.MOD_ID)
public final class KingdomSync {
    private KingdomSync() {
    }

    /** Sends the current names to everyone; call after a kingdom is created, renamed or deleted. */
    public static void sendToAll(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload(server));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, payload(player.level().getServer()));
        }
    }

    private static KingdomNamesPayload payload(MinecraftServer server) {
        List<KingdomNames.Entry> entries = KingdomSavedData.get(server.overworld()).kingdoms().stream()
            .map(kingdom -> new KingdomNames.Entry(kingdom.id(), kingdom.name(), kingdom.currencyName()))
            .toList();
        return new KingdomNamesPayload(entries);
    }
}
