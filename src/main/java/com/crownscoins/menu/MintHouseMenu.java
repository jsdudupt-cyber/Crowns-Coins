package com.crownscoins.menu;

import com.crownscoins.block.MintHouseBlockEntity;
import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomCrest;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.Symbol;
import com.crownscoins.network.KingdomInfoPayload;
import com.crownscoins.network.MintCoinPayload;
import com.crownscoins.network.NetworkHandler;
import com.crownscoins.network.UpdateCurrencyNamePayload;
import com.crownscoins.network.UpdateKingdomNamePayload;
import com.crownscoins.network.UpdateMembersPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.players.NameAndId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-side state for minting at a bound Mint House. Its payload validator
 * accepts a catalog metal/shape selection only and never trusts client
 * inventory, kingdom, quantity, or currency metadata.
 */
public final class MintHouseMenu extends MintHouseBoundMenu implements
    NetworkHandler.MintCoinRequestHandler,
    NetworkHandler.CurrencyNameRequestHandler,
    NetworkHandler.KingdomNameRequestHandler,
    NetworkHandler.MemberRequestHandler {
    public static final int IRON_METAL_ID = 1;
    public static final int COPPER_METAL_ID = 2;
    public static final int GOLD_METAL_ID = 3;
    /** Minting is deliberately nugget-based, with a distinct cost per denomination. */
    public static final int COPPER_NUGGETS_PER_COIN = 2;
    public static final int IRON_NUGGETS_PER_COIN = 5;
    public static final int GOLD_NUGGETS_PER_COIN = 7;
    /** Upper bound for the member list shown in the settings view. */
    public static final int MAX_LISTED_MEMBERS = 24;
    /**
     * Slot layout: the 27-slot coin chest, then the nine hotbar slots. The
     * backpack is deliberately not part of this menu, so no slot is ever
     * parked off-screen.
     */
    private static final int COIN_STORAGE_SLOT_START = 0;
    private static final int COIN_STORAGE_SLOT_END = COIN_STORAGE_SLOT_START + MintHouseBlockEntity.COIN_STORAGE_SLOTS;
    private static final int HOTBAR_SLOT_START = COIN_STORAGE_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;
    private ClientMintData clientData;
    /** Persistent 27-slot coin chest held by the exact Mint House block entity. */
    private final Container coinStorage;

    /** Server constructor. The player's hotbar remains usable while minting. */
    public MintHouseMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos mintHousePos) {
        this(CrownsCoins.MINT_HOUSE_MENU.get(), containerId, inventory, level.dimension(), mintHousePos, ClientMintData.empty());
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
                readMemberNames(data)
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
        this.coinStorage = coinStorageFor(inventory, mintHousePos);
        for (int slot = 0; slot < MintHouseBlockEntity.COIN_STORAGE_SLOTS; slot++) {
            this.addSlot(new CoinStorageSlot(
                this.coinStorage,
                slot,
                MintHouseLayout.coinStorageSlotX(slot),
                MintHouseLayout.coinStorageSlotY(slot)
            ));
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(
                inventory,
                column,
                MintHouseLayout.PLAYER_HOTBAR_X + column * MintHouseLayout.PLAYER_HOTBAR_STEP,
                MintHouseLayout.PLAYER_HOTBAR_Y
            ));
        }
    }

    /** Server-authored display data only; never used to authorize minting. */
    public ClientMintData clientData() {
        return clientData;
    }

    /** Returns whether the chest holds at least one base coin of this metal. */
    public boolean hasMaterialFor(Kingdom.Metal metal) {
        return mintableCoinCountFor(metal) > 0;
    }

    /**
     * Identifies the denomination of the first base coin in the chest. The
     * screen uses this synchronized state to pick the metal for the player;
     * the server still independently validates the eventual request.
     */
    public Optional<Kingdom.Metal> materialMetal() {
        return firstBaseMetalInStorage();
    }

    /** Number of base coins of this metal in the chest, i.e. how many coins one mint can produce. */
    public int mintableCoinCountFor(Kingdom.Metal metal) {
        return baseCoinCountForMetal(metal);
    }

    /** Shift-click moves coins between the coin chest and the visible hotbar. */
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
        if (slotIndex >= COIN_STORAGE_SLOT_START && slotIndex < COIN_STORAGE_SLOT_END) {
            moved = this.moveItemStackTo(stack, HOTBAR_SLOT_START, HOTBAR_SLOT_END, true);
        } else if (MintHouseBlockEntity.acceptsCoin(stack)) {
            moved = this.moveItemStackTo(stack, COIN_STORAGE_SLOT_START, COIN_STORAGE_SLOT_END, false);
        } else {
            moved = false;
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

    /** Ensures the exact live Mint House is still bound to a kingdom the player belongs to. */
    @Override
    public boolean isMintCoinRequestValid(ServerPlayer player) {
        return currentMintHouse(player)
            .flatMap(mintHouse -> mintHouse.kingdomId())
            .flatMap(kingdomId -> KingdomSavedData.get((ServerLevel) player.level()).find(kingdomId))
            .filter(kingdom -> KingdomCrest.isSupported(kingdom.crest()) && kingdom.isMember(player.getUUID()))
            .isPresent();
    }

    /** Only the founder of the bound kingdom may add or remove members. */
    @Override
    public boolean isMemberRequestValid(ServerPlayer player) {
        return isCurrencyNameRequestValid(player);
    }

    /** Adds or removes one named player; the kingdom always comes from this live menu. */
    @Override
    public void handleMemberRequest(ServerPlayer player, UpdateMembersPayload payload) {
        if (payload.containerId() != this.containerId || !isMemberRequestValid(player)) {
            player.closeContainer();
            return;
        }

        String name = payload.playerName().strip();
        Optional<UUID> kingdomId = currentMintHouse(player).flatMap(MintHouseBlockEntity::kingdomId);
        Optional<NameAndId> target = name.isEmpty() ? Optional.empty() : resolvePlayer((ServerLevel) player.level(), name);
        if (kingdomId.isEmpty() || target.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.member_not_found", name));
            return;
        }

        KingdomSavedData kingdoms = KingdomSavedData.get((ServerLevel) player.level());
        boolean changed = payload.add()
            ? kingdoms.addMember(kingdomId.get(), target.get().id())
            : kingdoms.removeMember(kingdomId.get(), target.get().id());
        if (!changed) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.member_rejected", target.get().name()));
            return;
        }
        player.sendSystemMessage(Component.translatable(
            payload.add() ? "message.crownscoins.member_added" : "message.crownscoins.member_removed",
            target.get().name()
        ));
        pushKingdomInfo(player);
    }

    /** Sends the current names and members to the open screen so it never shows stale data. */
    private void pushKingdomInfo(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        currentMintHouse(player)
            .flatMap(MintHouseBlockEntity::kingdomId)
            .flatMap(kingdomId -> KingdomSavedData.get(level).find(kingdomId))
            .ifPresent(kingdom -> PacketDistributor.sendToPlayer(player, new KingdomInfoPayload(
                this.containerId,
                kingdom.name(),
                kingdom.currencyName(),
                memberNames(level, kingdom)
            )));
    }

    /** Client side: applies a refresh pushed by the server to the data the screen reads. */
    public void applyKingdomInfo(String kingdomName, String currencyName, List<String> memberNames) {
        this.clientData = this.clientData.withInfo(kingdomName, currencyName, memberNames);
    }

    private static Optional<NameAndId> resolvePlayer(ServerLevel level, String name) {
        MinecraftServer server = level.getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(new NameAndId(online.getGameProfile()));
        }
        return server.services().nameToIdCache().get(name);
    }

    /**
     * Display names of a kingdom's members, founder first. Online players use
     * their live name; offline ones fall back to the server's name cache.
     */
    public static List<String> memberNames(ServerLevel level, Kingdom kingdom) {
        MinecraftServer server = level.getServer();
        List<UUID> others = new ArrayList<>(kingdom.members());
        others.remove(kingdom.founder());
        List<UUID> ordered = new ArrayList<>();
        ordered.add(kingdom.founder());
        ordered.addAll(others);

        List<String> names = new ArrayList<>();
        for (UUID id : ordered) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            String name = online != null
                ? online.getGameProfile().name()
                : server.services().nameToIdCache().get(id).map(NameAndId::name).orElse(id.toString().substring(0, 8));
            names.add(name);
            if (names.size() >= MAX_LISTED_MEMBERS) {
                break;
            }
        }
        return names;
    }

    /** Writes the member list in the same format that {@link #readMemberNames} expects. */
    public static void writeMemberNames(RegistryFriendlyByteBuf buffer, List<String> names) {
        buffer.writeVarInt(names.size());
        for (String name : names) {
            buffer.writeUtf(name, UpdateMembersPayload.MAX_PLAYER_NAME_LENGTH);
        }
    }

    private static List<String> readMemberNames(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_LISTED_MEMBERS);
        List<String> names = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            names.add(buffer.readUtf(UpdateMembersPayload.MAX_PLAYER_NAME_LENGTH));
        }
        return names;
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
        pushKingdomInfo(player);
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
        pushKingdomInfo(player);
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
            player.sendSystemMessage(Component.translatable("message.crownscoins.mint_rejected"));
            return;
        }

        MintRequest validated = request.get();
        // Minting stamps a design onto every matching base coin in the chest;
        // the furnace is what turns nuggets into base coins.
        if (payload.quantity() < 0) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.mint_rejected"));
            return;
        }
        int available = baseCoinCountFor(validated);
        int quantity = payload.quantity() == MintCoinPayload.ALL ? available : Math.min(payload.quantity(), available);
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

        if (!consumeBaseCoins(validated, quantity)) {
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
     * Validates the exact format selection received from the client without
     * consuming input. Shape zero is reserved for plain base coins, so only
     * the selectable catalog shapes (one through twelve) can be minted here.
     */
    public Optional<MintRequest> validatePayload(ServerPlayer player, int metalId, int shapeId) {
        if (!CoinData.isValidShapeId(shapeId) || shapeId == CoinData.DEFAULT_SHAPE_ID) {
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
            material,
            kingdom.value(request.metal()),
            request.shapeId()
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
        List<String> memberNames
    ) {
        /** Same snapshot with refreshed names and members; the crest and permissions are unchanged. */
        private ClientMintData withInfo(String newKingdomName, String newCurrencyName, List<String> newMemberNames) {
            return new ClientMintData(newKingdomName, newCurrencyName, crest, canEditCurrency, List.copyOf(newMemberNames));
        }

        private static ClientMintData empty() {
            return new ClientMintData("", "", Symbol.CROWN, false, List.of());
        }
    }
}
