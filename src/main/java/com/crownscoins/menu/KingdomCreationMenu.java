package com.crownscoins.menu;

import com.crownscoins.block.MintHouseBlockEntity;
import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.network.CreateKingdomPayload;
import com.crownscoins.network.NetworkHandler;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-side state for the create-kingdom screen. The screen itself is never
 * authoritative: handlers must call {@link #validatePayload(ServerPlayer, String,
 * String, int, int, int, int)} before touching {@link KingdomSavedData}.
 */
public final class KingdomCreationMenu extends MintHouseBoundMenu implements NetworkHandler.KingdomCreationRequestHandler {
    private static final UUID VALIDATION_KINGDOM_ID = new UUID(0L, 0L);

    /**
     * Convenience constructor for server opening. A menu type can be supplied through
     * the other constructor once the registry/client screen is wired up.
     */
    public KingdomCreationMenu(int containerId, ServerLevel level, BlockPos mintHousePos) {
        this(CrownsCoins.KINGDOM_CREATION_MENU.get(), containerId, level.dimension(), mintHousePos);
    }

    /** Client factory; the server sends only the target position with the menu open packet. */
    public KingdomCreationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(CrownsCoins.KINGDOM_CREATION_MENU.get(), containerId, inventory.player.level().dimension(), data.readBlockPos());
    }

    public KingdomCreationMenu(MenuType<?> menuType, int containerId, ResourceKey<Level> dimension, BlockPos mintHousePos) {
        super(menuType, containerId, dimension, mintHousePos);
    }

    /** This UI owns no inventory slots. */
    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    /**
     * First authorization gate called by {@link NetworkHandler}. It has no client
     * supplied target: the block, dimension, binding, and player membership all
     * come from this live server-side menu.
     */
    @Override
    public boolean isKingdomCreationRequestValid(ServerPlayer player) {
        Optional<MintHouseBlockEntity> mintHouse = currentMintHouse(player);
        if (mintHouse.isEmpty() || mintHouse.get().kingdomId().isPresent()) {
            return false;
        }
        return !KingdomSavedData.get((ServerLevel) player.level()).hasKingdom(player.getUUID());
    }

    /**
     * Creates the kingdom and binds the exact verified Mint House. This method is
     * called on the server thread, but it repeats every live check so it remains
     * safe if a caller bypasses the common network dispatcher in the future.
     */
    @Override
    public void handleKingdomCreationRequest(ServerPlayer player, CreateKingdomPayload payload) {
        if (payload.containerId() != this.containerId || !isKingdomCreationRequestValid(player)) {
            player.closeContainer();
            return;
        }

        Optional<CreationRequest> request = validatePayload(
            player,
            payload.kingdomName(),
            payload.currencyName(),
            payload.ironValue(),
            payload.copperValue(),
            payload.goldValue(),
            payload.crestId()
        );
        if (request.isEmpty()) {
            player.sendSystemMessage(Component.literal("Kingdom creation request was rejected."));
            return;
        }

        // Re-fetch the block entity immediately before both mutations. The request
        // cannot bind another Mint House because this menu owns the fixed position.
        Optional<MintHouseBlockEntity> mintHouse = currentMintHouse(player);
        if (mintHouse.isEmpty() || mintHouse.get().kingdomId().isPresent()) {
            player.closeContainer();
            return;
        }

        CreationRequest details = request.get();
        try {
            Kingdom kingdom = KingdomSavedData.get((ServerLevel) player.level()).createKingdom(
                player.getUUID(),
                details.kingdomName(),
                details.currencyName(),
                details.crest(),
                details.ironValue(),
                details.copperValue(),
                details.goldValue()
            );
            mintHouse.get().bind(kingdom.id());
            player.sendSystemMessage(Component.literal("Kingdom created and this Mint House is now bound to " + kingdom.name() + "."));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The SavedData repeats all global invariants, including name and member
            // uniqueness. Do not disclose internal exception data to packet senders.
            player.sendSystemMessage(Component.literal("Kingdom creation request was rejected."));
        }
    }

    /**
     * Packet-handler entry point. It rejects a request unless this exact creation
     * menu is the player's active menu and every live Mint House condition holds.
     */
    public static Optional<CreationRequest> validateCurrentPayload(
        ServerPlayer player,
        String kingdomName,
        String currencyName,
        int ironValue,
        int copperValue,
        int goldValue,
        int crestId
    ) {
        if (!(player.containerMenu instanceof KingdomCreationMenu menu)) {
            return Optional.empty();
        }
        return menu.validatePayload(player, kingdomName, currencyName, ironValue, copperValue, goldValue, crestId);
    }

    /**
     * Validates a decoded client intent. It does not create a kingdom or bind a
     * block; the network handler performs those mutations only after receiving a
     * non-empty result.
     */
    public Optional<CreationRequest> validatePayload(
        ServerPlayer player,
        String kingdomName,
        String currencyName,
        int ironValue,
        int copperValue,
        int goldValue,
        int crestId
    ) {
        Optional<MintHouseBlockEntity> mintHouse = currentMintHouse(player);
        if (mintHouse.isEmpty() || mintHouse.get().kingdomId().isPresent()) {
            return Optional.empty();
        }

        KingdomSavedData kingdoms = KingdomSavedData.get((ServerLevel) player.level());
        if (kingdoms.hasKingdom(player.getUUID())) {
            return Optional.empty();
        }
        if (!Kingdom.isStandardEconomy(ironValue, copperValue, goldValue)) {
            return Optional.empty();
        }

        final Symbol crest;
        try {
            crest = KingdomCrest.byId(crestId).symbol();
            // Use the domain constructor so server payload validation has exactly
            // the same text/value constraints as persisted kingdom creation.
            new Kingdom(
                VALIDATION_KINGDOM_ID,
                player.getUUID(),
                kingdomName,
                currencyName,
                crest,
                ironValue,
                copperValue,
                goldValue
            );
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }

        if (kingdoms.findByName(kingdomName).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new CreationRequest(kingdomName, currencyName, crest, ironValue, copperValue, goldValue));
    }

    /** A server-validated creation intent suitable for {@link KingdomSavedData#createKingdom}. */
    public record CreationRequest(
        String kingdomName,
        String currencyName,
        Symbol crest,
        int ironValue,
        int copperValue,
        int goldValue
    ) {
    }
}
