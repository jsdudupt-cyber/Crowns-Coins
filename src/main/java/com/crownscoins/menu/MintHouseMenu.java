package com.crownscoins.menu;

import com.crownscoins.block.MintHouseBlockEntity;
import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.network.MintCoinPayload;
import com.crownscoins.network.NetworkHandler;
import com.crownscoins.network.UpdateCurrencyNamePayload;
import com.crownscoins.network.UpdateKingdomNamePayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Server-side state for minting at a bound Mint House. Its payload validator
 * accepts a catalog metal/shape selection only and never trusts client
 * inventory, kingdom, quantity, or currency metadata.
 */
public final class MintHouseMenu extends MintHouseBoundMenu implements
    NetworkHandler.MintCoinRequestHandler,
    NetworkHandler.CurrencyNameRequestHandler,
    NetworkHandler.KingdomNameRequestHandler {
    public static final int IRON_METAL_ID = 1;
    public static final int COPPER_METAL_ID = 2;
    public static final int GOLD_METAL_ID = 3;
    /** Minting is deliberately nugget-based, with a distinct cost per denomination. */
    public static final int COPPER_NUGGETS_PER_COIN = 2;
    public static final int IRON_NUGGETS_PER_COIN = 5;
    public static final int GOLD_NUGGETS_PER_COIN = 7;
    private static final int MATERIAL_SLOT = 0;
    private static final int COIN_STORAGE_SLOT_START = MATERIAL_SLOT + 1;
    private static final int COIN_STORAGE_SLOT_END = COIN_STORAGE_SLOT_START + MintHouseBlockEntity.COIN_STORAGE_SLOTS;
    private static final int PLAYER_SLOT_START = COIN_STORAGE_SLOT_END;
    private static final int PLAYER_MAIN_END = PLAYER_SLOT_START + 27;
    private static final int PLAYER_SLOT_END = PLAYER_MAIN_END + 9;
    private final ClientMintData clientData;
    /** One temporary input slot, returned to its owner like a vanilla crafting grid. */
    private final Container materialSlot;
    /** Persistent 27-slot coin chest held by the exact Mint House block entity. */
    private final Container coinStorage;
    /** True only when opened from the right-hand matrix and engraving table. */
    private final boolean designMode;

    /** Server constructor. The player inventory remains fully usable while minting. */
    public MintHouseMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos mintHousePos) {
        this(containerId, inventory, level, mintHousePos, false);
    }

    /** The selected physical half determines the initial menu layout. */
    public MintHouseMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos mintHousePos, boolean openDesignAtStart) {
        this(CrownsCoins.MINT_HOUSE_MENU.get(), containerId, inventory, level.dimension(), mintHousePos, ClientMintData.empty(openDesignAtStart));
    }

    /** Client factory; authoritative state remains in the corresponding server menu. */
    public MintHouseMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(
            CrownsCoins.MINT_HOUSE_MENU.get(),
            containerId,
            inventory,
            inventory.player.level().dimension(),
            data.readBlockPos(),
            new ClientMintData(
                data.readUtf(Kingdom.MAX_KINGDOM_NAME_LENGTH),
                data.readUtf(Kingdom.MAX_CURRENCY_NAME_LENGTH),
                Symbol.byId(data.readVarInt()),
                data.readBoolean(),
                data.readVarInt(),
                data.readVarInt(),
                data.readVarInt(),
                data.readBoolean()
            )
        );
    }

    private MintHouseMenu(
        MenuType<?> menuType,
        int containerId,
        Inventory inventory,
        ResourceKey<Level> dimension,
        BlockPos mintHousePos,
        ClientMintData clientData
    ) {
        super(menuType, containerId, dimension, mintHousePos);
        this.clientData = clientData;
        this.designMode = clientData.openDesignAtStart();
        this.materialSlot = new SimpleContainer(1);
        this.coinStorage = coinStorageFor(inventory, mintHousePos);
        int materialX = designMode ? MintHouseLayout.MATERIAL_SLOT_X : MintHouseLayout.PRESS_MATERIAL_SLOT_X;
        int materialY = designMode ? MintHouseLayout.MATERIAL_SLOT_Y : MintHouseLayout.PRESS_MATERIAL_SLOT_Y;
        this.addSlot(new Slot(
            this.materialSlot,
            MATERIAL_SLOT,
            materialX,
            materialY
        ) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MintHouseMenu.this.acceptsInput(stack);
            }
        });
        for (int slot = 0; slot < MintHouseBlockEntity.COIN_STORAGE_SLOTS; slot++) {
            int column = designMode ? slot % 9 : slot % 3;
            int row = designMode ? slot / 9 : slot / 3;
            this.addSlot(new CoinStorageSlot(
                this.coinStorage,
                slot,
                designMode ? MintHouseLayout.coinStorageSlotX(slot) : MintHouseLayout.PRESS_COIN_STORAGE_X + column * 18,
                designMode ? MintHouseLayout.coinStorageSlotY(slot) : MintHouseLayout.PRESS_COIN_STORAGE_Y + row * 18
            ));
        }
        this.addInventoryExtendedSlots(
            inventory,
            designMode ? MintHouseLayout.PLAYER_INVENTORY_X : MintHouseLayout.PRESS_PLAYER_INVENTORY_X,
            designMode ? MintHouseLayout.PLAYER_INVENTORY_Y : MintHouseLayout.PRESS_PLAYER_INVENTORY_Y
        );
        this.addInventoryHotbarSlots(
            inventory,
            designMode ? MintHouseLayout.PLAYER_HOTBAR_X : MintHouseLayout.PRESS_PLAYER_HOTBAR_X,
            designMode ? MintHouseLayout.PLAYER_HOTBAR_Y : MintHouseLayout.PRESS_PLAYER_HOTBAR_Y
        );
    }

    /** Server-authored display data only; never used to authorize minting. */
    public ClientMintData clientData() {
        return clientData;
    }

    /** Returns whether the visible input slot contains enough matching nuggets. */
    public boolean hasMaterialFor(Kingdom.Metal metal) {
        return mintableCoinCountFor(metal) > 0;
    }

    /**
     * Returns the complete matching stack in the mint socket. The client uses
     * this only to describe the action; the server recalculates it immediately
     * before minting so a packet can never choose its own output quantity.
     */
    public int materialCountFor(Kingdom.Metal metal) {
        if (designMode) {
            return baseCoinCountForMetal(metal);
        }
        ItemStack stack = this.materialSlot.getItem(MATERIAL_SLOT);
        return stack.is(nuggetFor(metal)) || metalForBaseCoin(stack).filter(metal::equals).isPresent()
            ? stack.getCount() : 0;
    }

    /**
     * Lets a player replace one denomination with another in a single normal
     * left-click. Vanilla slots only merge matching stacks, which made a copper
     * nugget look rejected whenever iron or gold nuggets were already in the
     * mint socket. The previous stack is put on the cursor and is never lost.
     */
    @Override
    public void clicked(int slotIndex, int button, ContainerInput clickType, Player player) {
        if (slotIndex == MATERIAL_SLOT && clickType == ContainerInput.PICKUP && button == 0) {
            ItemStack carried = this.getCarried();
            ItemStack installed = this.materialSlot.getItem(MATERIAL_SLOT);
            if (!carried.isEmpty()
                && !installed.isEmpty()
                && acceptsInput(carried)
                && acceptsInput(installed)
                && carried.getItem() != installed.getItem()) {
                this.materialSlot.setItem(MATERIAL_SLOT, carried.copy());
                this.setCarried(installed.copy());
                this.broadcastChanges();
                return;
            }
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    /**
     * Identifies the denomination that the live input slot can mint. The
     * screen uses this synchronized slot state to select the metal for the
     * player; the server still independently validates the eventual request.
     */
    public Optional<Kingdom.Metal> materialMetal() {
        if (designMode) {
            return firstBaseMetalInStorage();
        }
        return metalForMintingMaterial(this.materialSlot.getItem(MATERIAL_SLOT));
    }

    /** Number of coins the live nugget input can produce for the selected denomination. */
    public int mintableCoinCountFor(Kingdom.Metal metal) {
        if (designMode) {
            return baseCoinCountForMetal(metal);
        }
        ItemStack stack = this.materialSlot.getItem(MATERIAL_SLOT);
        if (metalForBaseCoin(stack).filter(metal::equals).isPresent()) {
            return stack.getCount();
        }
        return materialCountFor(metal) / nuggetsPerCoin(metal);
    }

    /** Moves shift-clicked stacks among the ingredient socket, coin chest, and player inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        boolean moved;
        if (slotIndex == MATERIAL_SLOT) {
            moved = this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true);
        } else if (slotIndex >= COIN_STORAGE_SLOT_START && slotIndex < COIN_STORAGE_SLOT_END) {
            moved = this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true);
        } else if (acceptsInput(stack)) {
            moved = this.moveItemStackTo(stack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false);
            if (!moved) {
                moved = moveBetweenPlayerRows(stack, slotIndex);
            }
        } else if (MintHouseBlockEntity.acceptsCoin(stack)) {
            moved = this.moveItemStackTo(stack, COIN_STORAGE_SLOT_START, COIN_STORAGE_SLOT_END, false);
            if (!moved) {
                moved = moveBetweenPlayerRows(stack, slotIndex);
            }
        } else {
            moved = moveBetweenPlayerRows(stack, slotIndex);
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }

    /** Ensures the exact live Mint House is still bound to a real server kingdom. */
    @Override
    public boolean isMintCoinRequestValid(ServerPlayer player) {
        return currentMintHouse(player)
            .flatMap(mintHouse -> mintHouse.kingdomId())
            .flatMap(kingdomId -> KingdomSavedData.get((ServerLevel) player.level()).find(kingdomId))
            .filter(kingdom -> KingdomCrest.isSupported(kingdom.crest()))
            .isPresent();
    }

    /** Only the founder of the currently bound kingdom may rename its currency. */
    @Override
    public boolean isCurrencyNameRequestValid(ServerPlayer player) {
        return currentMintHouse(player)
            .flatMap(mintHouse -> mintHouse.kingdomId())
            .flatMap(kingdomId -> KingdomSavedData.get((ServerLevel) player.level()).find(kingdomId))
            .filter(kingdom -> kingdom.isFounder(player.getUUID()))
            .isPresent();
    }

    /** Updates the currency name through SavedData after rechecking the live menu. */
    @Override
    public void handleCurrencyNameRequest(ServerPlayer player, UpdateCurrencyNamePayload payload) {
        if (payload.containerId() != this.containerId || !isCurrencyNameRequestValid(player)) {
            player.closeContainer();
            return;
        }

        Optional<Kingdom> updated = currentMintHouse(player)
            .flatMap(MintHouseBlockEntity::kingdomId)
            .flatMap(kingdomId -> KingdomSavedData.get((ServerLevel) player.level())
                .updateCurrencyName(kingdomId, player.getUUID(), payload.currencyName()));
        if (updated.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.currency_name_rejected"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.crownscoins.currency_name_saved", updated.get().currencyName()));
    }

    /** The founder can rename only the real kingdom bound to this exact Mint House. */
    @Override
    public boolean isKingdomNameRequestValid(ServerPlayer player) {
        return isCurrencyNameRequestValid(player);
    }

    /** Persists a valid unique kingdom name without accepting any client kingdom identity. */
    @Override
    public void handleKingdomNameRequest(ServerPlayer player, UpdateKingdomNamePayload payload) {
        if (payload.containerId() != this.containerId || !isKingdomNameRequestValid(player)) {
            player.closeContainer();
            return;
        }

        Optional<Kingdom> updated = currentMintHouse(player)
            .flatMap(MintHouseBlockEntity::kingdomId)
            .flatMap(kingdomId -> KingdomSavedData.get((ServerLevel) player.level())
                .updateKingdomName(kingdomId, player.getUUID(), payload.kingdomName()));
        if (updated.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.kingdom_name_rejected"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.crownscoins.kingdom_name_saved", updated.get().name()));
    }

    /**
     * Mints every coin that the matching nugget stack can afford after all
     * live checks pass. Players can split a stack first when they want fewer
     * coins; the server always derives the quantity from its own live slot.
     */
    @Override
    public void handleMintCoinRequest(ServerPlayer player, MintCoinPayload payload) {
        if (payload.containerId() != this.containerId || !isMintCoinRequestValid(player)) {
            player.closeContainer();
            return;
        }

        Optional<MintRequest> request = validatePayload(player, payload.metalId(), payload.shapeId());
        if (request.isEmpty()) {
            player.sendSystemMessage(Component.literal("Mint request was rejected."));
            return;
        }

        MintRequest validated = request.get();
        boolean makingBaseCoin = validated.shapeId() == CoinData.DEFAULT_SHAPE_ID;
        int quantity = makingBaseCoin
            ? materialCountFor(validated.metal()) / nuggetsPerCoin(validated.metal())
            : baseCoinCountFor(validated);
        if (quantity <= 0) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.missing_nuggets"));
            return;
        }

        final ItemStack coin;
        try {
            coin = createCoin(validated, quantity);
        } catch (IllegalArgumentException ignored) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.mint_rejected"));
            return;
        }

        if (makingBaseCoin) {
            int requiredNuggets = quantity * nuggetsPerCoin(validated.metal());
            if (!consumeInputNuggets(validated.metal(), requiredNuggets)) {
                player.sendSystemMessage(Component.translatable("message.crownscoins.missing_nuggets"));
                return;
            }
        } else if (!consumeBaseCoins(validated, quantity)) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.mint_rejected"));
            return;
        }

        // The built-in chest is the first destination. If it is full, continue
        // through the normal player inventory and finally drop the exact
        // remainder. That gives every minted coin a safe destination.
        ItemStack remaining = currentMintHouse(player)
            .map(mintHouse -> mintHouse.storeCoins(coin))
            .orElse(coin);
        if (!remaining.isEmpty()) {
            player.getInventory().add(remaining);
        }
        if (!remaining.isEmpty()) {
            player.drop(remaining, false);
        }
        player.sendSystemMessage(Component.translatable(
            "message.crownscoins.coin_minted",
            quantity,
            validated.kingdom().currencyName()
        ));
    }

    /**
     * Packet-handler entry point. It proves the exact Mint House menu is still open
     * before processing the client-selected metal and shape IDs.
     */
    public static Optional<MintRequest> validateCurrentPayload(
        ServerPlayer player,
        int metalId
    ) {
        return validateCurrentPayload(player, metalId, CoinData.DEFAULT_SHAPE_ID);
    }

    /** Validates a client-selected metal and supported catalog shape for the live menu. */
    public static Optional<MintRequest> validateCurrentPayload(
        ServerPlayer player,
        int metalId,
        int shapeId
    ) {
        if (!(player.containerMenu instanceof MintHouseMenu menu)) {
            return Optional.empty();
        }
        return menu.validatePayload(player, metalId, shapeId);
    }

    /**
     * Validates a decoded mint request without consuming nuggets or creating an
     * item. The caller must separately check and consume the corresponding nuggets
     * after this method succeeds.
     */
    public Optional<MintRequest> validatePayload(ServerPlayer player, int metalId) {
        return validatePayload(player, metalId, CoinData.DEFAULT_SHAPE_ID);
    }

    /**
     * Validates the exact format selection received from the client without
     * consuming input. Shape zero remains valid for old clients and legacy
     * coin stacks; one through ten are the new selectable catalog values.
     */
    public Optional<MintRequest> validatePayload(ServerPlayer player, int metalId, int shapeId) {
        if (!CoinData.isValidShapeId(shapeId) || (designMode && shapeId == CoinData.DEFAULT_SHAPE_ID)) {
            return Optional.empty();
        }
        Optional<MintHouseBlockEntity> mintHouse = currentMintHouse(player);
        if (mintHouse.isEmpty()) {
            return Optional.empty();
        }

        Optional<Kingdom> kingdom = mintHouse.get()
            .kingdomId()
            .flatMap(kingdomId -> KingdomSavedData.get((ServerLevel) player.level()).find(kingdomId));
        if (kingdom.isEmpty()) {
            return Optional.empty();
        }

        Optional<Kingdom.Metal> metal = metalById(metalId);
        if (metal.isEmpty() || !KingdomCrest.isSupported(kingdom.get().crest())) {
            return Optional.empty();
        }

        return Optional.of(new MintRequest(kingdom.get(), metal.get(), shapeId));
    }

    private static ItemStack createCoin(MintRequest request, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Mint quantity must be positive");
        }
        Kingdom kingdom = request.kingdom();
        ItemStack coin = new ItemStack(switch (request.metal()) {
            case IRON -> CrownsCoins.IRON_COIN.get();
            case COPPER -> CrownsCoins.COPPER_COIN.get();
            case GOLD -> CrownsCoins.GOLD_COIN.get();
        });
        CoinData.Material material = switch (request.metal()) {
            case IRON -> CoinData.Material.IRON;
            case COPPER -> CoinData.Material.COPPER;
            case GOLD -> CoinData.Material.GOLD;
        };
        CoinData coinData = new CoinData(
            kingdom.id(),
            kingdom.name(),
            kingdom.currencyName(),
            // Kingdom provenance remains part of the stored currency data even
            // though the coin face itself is intentionally plain.
            kingdom.crest(),
            material,
            kingdom.value(request.metal()),
            Symbol.CROWN.id(),
            request.shapeId(),
            List.of()
        );
        coin.set(CrownsCoins.COIN_DATA.get(), coinData);
        coin.set(DataComponents.CUSTOM_NAME, Component.literal(kingdom.currencyName()));
        coin.setCount(quantity);
        return coin;
    }

    /** Shared trusted factory for the left furnace: it may create only clean base coins. */
    public static ItemStack createBaseCoin(Kingdom kingdom, Kingdom.Metal metal) {
        return createCoin(new MintRequest(kingdom, metal, CoinData.DEFAULT_SHAPE_ID), 1);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.materialSlot);
    }

    private boolean consumeInputNuggets(Kingdom.Metal metal, int quantity) {
        ItemStack stack = this.materialSlot.getItem(MATERIAL_SLOT);
        if (quantity < 1 || !stack.is(nuggetFor(metal)) || stack.getCount() < quantity) {
            return false;
        }
        stack.shrink(quantity);
        if (stack.isEmpty()) {
            this.materialSlot.setItem(MATERIAL_SLOT, ItemStack.EMPTY);
        } else {
            this.materialSlot.setChanged();
        }
        return true;
    }

    /** The left-hand mint only accepts clean, round bases created for this table's kingdom. */
    private int baseCoinCountFor(MintRequest request) {
        int amount = 0;
        for (int slot = 0; slot < this.coinStorage.getContainerSize(); slot++) {
            ItemStack stack = this.coinStorage.getItem(slot);
            if (isBaseCoinFor(stack, request)) {
                amount += stack.getCount();
            }
        }
        return amount;
    }

    private boolean consumeBaseCoins(MintRequest request, int quantity) {
        if (quantity < 1 || baseCoinCountFor(request) < quantity) {
            return false;
        }
        int remaining = quantity;
        for (int slot = 0; slot < this.coinStorage.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = this.coinStorage.getItem(slot);
            if (!isBaseCoinFor(stack, request)) {
                continue;
            }
            int used = Math.min(remaining, stack.getCount());
            this.coinStorage.removeItem(slot, used);
            remaining -= used;
        }
        return remaining == 0;
    }

    private static boolean isBaseCoinFor(ItemStack stack, MintRequest request) {
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        return data != null
            && data.shapeId() == CoinData.DEFAULT_SHAPE_ID
            && data.kingdomId().equals(request.kingdom().id())
            && data.material() == coinMaterial(request.metal());
    }

    private boolean moveBetweenPlayerRows(ItemStack stack, int slotIndex) {
        if (slotIndex < PLAYER_MAIN_END) {
            return this.moveItemStackTo(stack, PLAYER_MAIN_END, PLAYER_SLOT_END, false);
        }
        return this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_MAIN_END, false);
    }

    /**
     * The server always resolves the live block entity. The client may receive
     * the menu before its local block entity reaches the render world, so it
     * gets an equal-size placeholder which is immediately filled by menu slot
     * synchronization. Both sides therefore always expose exactly 64 slots.
     */
    private static Container coinStorageFor(Inventory inventory, BlockPos mintHousePos) {
        return inventory.player.level().getBlockEntity(mintHousePos) instanceof MintHouseBlockEntity mintHouse
            ? mintHouse
            : new SimpleContainer(MintHouseBlockEntity.COIN_STORAGE_SLOTS);
    }

    /** A normal chest slot, restricted to the three currency denominations. */
    private static final class CoinStorageSlot extends Slot {
        private CoinStorageSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return MintHouseBlockEntity.acceptsCoin(stack);
        }
    }

    private static boolean isMintingMaterial(ItemStack stack) {
        return metalForMintingMaterial(stack).isPresent();
    }

    /** The right press receives only round base coins; nuggets belong to the left furnace. */
    private boolean acceptsInput(ItemStack stack) {
        return designMode ? metalForBaseCoin(stack).isPresent() : isMintingMaterial(stack);
    }

    private static Optional<Kingdom.Metal> metalForMintingMaterial(ItemStack stack) {
        if (stack.is(Items.COPPER_NUGGET)) {
            return Optional.of(Kingdom.Metal.COPPER);
        }
        if (stack.is(Items.IRON_NUGGET)) {
            return Optional.of(Kingdom.Metal.IRON);
        }
        if (stack.is(Items.GOLD_NUGGET)) {
            return Optional.of(Kingdom.Metal.GOLD);
        }
        return metalForBaseCoin(stack);
    }

    private static Optional<Kingdom.Metal> metalForBaseCoin(ItemStack stack) {
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        if (data == null || data.shapeId() != CoinData.DEFAULT_SHAPE_ID) {
            return Optional.empty();
        }
        return switch (data.material()) {
            case COPPER -> Optional.of(Kingdom.Metal.COPPER);
            case IRON -> Optional.of(Kingdom.Metal.IRON);
            case GOLD -> Optional.of(Kingdom.Metal.GOLD);
        };
    }

    /** The press automatically reads which base denomination is available in its chest. */
    private Optional<Kingdom.Metal> firstBaseMetalInStorage() {
        for (int slot = 0; slot < this.coinStorage.getContainerSize(); slot++) {
            Optional<Kingdom.Metal> metal = metalForBaseCoin(this.coinStorage.getItem(slot));
            if (metal.isPresent()) {
                return metal;
            }
        }
        return Optional.empty();
    }

    private int baseCoinCountForMetal(Kingdom.Metal metal) {
        int amount = 0;
        for (int slot = 0; slot < this.coinStorage.getContainerSize(); slot++) {
            if (metalForBaseCoin(this.coinStorage.getItem(slot)).filter(metal::equals).isPresent()) {
                amount += this.coinStorage.getItem(slot).getCount();
            }
        }
        return amount;
    }

    private static CoinData.Material coinMaterial(Kingdom.Metal metal) {
        return switch (metal) {
            case COPPER -> CoinData.Material.COPPER;
            case IRON -> CoinData.Material.IRON;
            case GOLD -> CoinData.Material.GOLD;
        };
    }

    private static Item nuggetFor(Kingdom.Metal metal) {
        return switch (metal) {
            case IRON -> Items.IRON_NUGGET;
            case COPPER -> Items.COPPER_NUGGET;
            case GOLD -> Items.GOLD_NUGGET;
        };
    }

    /** Shared client/server display and validation rule for the three minting costs. */
    public static int nuggetsPerCoin(Kingdom.Metal metal) {
        return switch (metal) {
            case COPPER -> COPPER_NUGGETS_PER_COIN;
            case IRON -> IRON_NUGGETS_PER_COIN;
            case GOLD -> GOLD_NUGGETS_PER_COIN;
        };
    }

    private static Optional<Kingdom.Metal> metalById(int metalId) {
        return switch (metalId) {
            case IRON_METAL_ID -> Optional.of(Kingdom.Metal.IRON);
            case COPPER_METAL_ID -> Optional.of(Kingdom.Metal.COPPER);
            case GOLD_METAL_ID -> Optional.of(Kingdom.Metal.GOLD);
            default -> Optional.empty();
        };
    }

    /** A server-validated mint intent containing only the kingdom, metal, and catalog shape. */
    public record MintRequest(Kingdom kingdom, Kingdom.Metal metal, int shapeId) {
    }

    /** Immutable snapshot written by the server while the menu opens. */
    public record ClientMintData(
        String kingdomName,
        String currencyName,
        Symbol crest,
        boolean canEditCurrency,
        int ironValue,
        int copperValue,
        int goldValue,
        boolean openDesignAtStart
    ) {
        private static ClientMintData empty(boolean openDesignAtStart) {
            return new ClientMintData(
                "",
                "",
                Symbol.CROWN,
                false,
                Kingdom.IRON_COIN_VALUE,
                Kingdom.COPPER_COIN_VALUE,
                Kingdom.GOLD_COIN_VALUE,
                openDesignAtStart
            );
        }
    }
}
