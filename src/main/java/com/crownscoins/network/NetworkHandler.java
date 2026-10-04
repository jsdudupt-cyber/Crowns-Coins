package com.crownscoins.network;

import com.crownscoins.menu.MintHouseMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Common, dedicated-server-safe registration and dispatch for Crowns & Coins payloads. */
public final class NetworkHandler {
    public static final String NETWORK_VERSION = "8";

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
        registrar.playToServer(UpdateCurrencyNamePayload.TYPE, UpdateCurrencyNamePayload.STREAM_CODEC, NetworkHandler::handleUpdateCurrencyName);
        registrar.playToServer(UpdateKingdomNamePayload.TYPE, UpdateKingdomNamePayload.STREAM_CODEC, NetworkHandler::handleUpdateKingdomName);
        registrar.playToServer(UpdateMembersPayload.TYPE, UpdateMembersPayload.STREAM_CODEC, NetworkHandler::handleUpdateMembers);
        registrar.playToClient(KingdomInfoPayload.TYPE, KingdomInfoPayload.STREAM_CODEC, NetworkHandler::handleKingdomInfo);
    }

    /** Runs on the client: refreshes the names shown by the matching open Mint House menu. */
    private static void handleKingdomInfo(KingdomInfoPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof MintHouseMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applyKingdomInfo(payload.kingdomName(), payload.currencyName(), payload.memberNames());
        }
    }

    private static void handleUpdateMembers(UpdateMembersPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof MemberRequestHandler menu)
                || player.containerMenu.containerId != payload.containerId()) {
            return;
        }
        if (!menu.isMemberRequestValid(player)) {
            player.closeContainer();
            return;
        }
        menu.handleMemberRequest(player, payload);
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

    private static void handleUpdateCurrencyName(UpdateCurrencyNamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof CurrencyNameRequestHandler menu)
                || player.containerMenu.containerId != payload.containerId()) {
            return;
        }
        if (!menu.isCurrencyNameRequestValid(player)) {
            player.closeContainer();
            return;
        }
        menu.handleCurrencyNameRequest(player, payload);
    }

    private static void handleUpdateKingdomName(UpdateKingdomNamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof KingdomNameRequestHandler menu)
                || player.containerMenu.containerId != payload.containerId()) {
            return;
        }
        if (!menu.isKingdomNameRequestValid(player)) {
            player.closeContainer();
            return;
        }
        menu.handleKingdomNameRequest(player, payload);
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

    /** Implemented only by the live Mint House menu opened by a kingdom founder. */
    public interface CurrencyNameRequestHandler {
        boolean isCurrencyNameRequestValid(ServerPlayer player);

        void handleCurrencyNameRequest(ServerPlayer player, UpdateCurrencyNamePayload payload);
    }

    /** Implemented only by the live Mint House menu opened by a kingdom founder. */
    public interface MemberRequestHandler {
        boolean isMemberRequestValid(ServerPlayer player);

        void handleMemberRequest(ServerPlayer player, UpdateMembersPayload payload);
    }

    /** Implemented only by the live Mint House menu opened by a kingdom founder. */
    public interface KingdomNameRequestHandler {
        boolean isKingdomNameRequestValid(ServerPlayer player);

        void handleKingdomNameRequest(ServerPlayer player, UpdateKingdomNamePayload payload);
    }
}
