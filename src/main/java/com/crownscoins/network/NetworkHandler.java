package com.crownscoins.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Common, dedicated-server-safe registration and dispatch for Crowns & Coins payloads. */
public final class NetworkHandler {
    public static final String NETWORK_VERSION = "1";

    private NetworkHandler() {}

    /**
     * Register this method on the mod event bus with {@code eventBus.addListener(NetworkHandler::register)}.
     * PayloadRegistrar handlers run on the main game thread by default, which is required for menu,
     * inventory, block-entity, and SavedData mutations.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(CreateKingdomPayload.TYPE, CreateKingdomPayload.STREAM_CODEC, NetworkHandler::handleCreateKingdom);
        registrar.playToServer(MintCoinPayload.TYPE, MintCoinPayload.STREAM_CODEC, NetworkHandler::handleMintCoin);
    }

    private static void handleCreateKingdom(CreateKingdomPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof KingdomCreationRequestHandler menu)
                || player.containerMenu.containerId != payload.containerId()) {
            return;
        }
        if (!menu.isKingdomCreationRequestValid(player)) {
            player.closeContainer();
            return;
        }
        menu.handleKingdomCreationRequest(player, payload);
    }

    private static void handleMintCoin(MintCoinPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof MintCoinRequestHandler menu)
                || player.containerMenu.containerId != payload.containerId()) {
            return;
        }
        if (!menu.isMintCoinRequestValid(player)) {
            player.closeContainer();
            return;
        }
        menu.handleMintCoinRequest(player, payload);
    }

    /** Implemented only by the live server-side kingdom-creation menu. */
    public interface KingdomCreationRequestHandler {
        boolean isKingdomCreationRequestValid(ServerPlayer player);

        void handleKingdomCreationRequest(ServerPlayer player, CreateKingdomPayload payload);
    }

    /** Implemented only by the live server-side Mint House menu. */
    public interface MintCoinRequestHandler {
        boolean isMintCoinRequestValid(ServerPlayer player);

        void handleMintCoinRequest(ServerPlayer player, MintCoinPayload payload);
    }
}
