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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Server-side state for minting at a bound Mint House. Its payload validator
 * accepts catalog IDs only and never changes a player's inventory.
 */
public final class MintHouseMenu extends MintHouseBoundMenu implements NetworkHandler.MintCoinRequestHandler {
    public static final int IRON_METAL_ID = 1;
    public static final int COPPER_METAL_ID = 2;
    public static final int GOLD_METAL_ID = 3;
    private static final int MATERIAL_SLOT = 0;
    private static final int PLAYER_SLOT_START = MATERIAL_SLOT + 1;
    private static final int PLAYER_MAIN_END = PLAYER_SLOT_START + 27;
    private static final int PLAYER_SLOT_END = PLAYER_MAIN_END + 9;
    private static final int MATERIAL_SLOT_X = 311;
    private static final int MATERIAL_SLOT_Y = 227;
    private static final int PLAYER_INVENTORY_X = 271;
    private static final int PLAYER_INVENTORY_Y = 348;

    private final ClientMintData clientData;
    /** One temporary input slot, returned to its owner like a vanilla crafting grid. */
    private final Container materialSlot;

    /** Server constructor. The player inventory remains fully usable while minting. */
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
            new ClientMintData(data.readUtf(Kingdom.MAX_KINGDOM_NAME_LENGTH), Symbol.byId(data.readVarInt()), data.readVarInt(), data.readVarInt(), data.readVarInt())
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
        this.materialSlot = new SimpleContainer(1);
        this.addSlot(new Slot(this.materialSlot, MATERIAL_SLOT, MATERIAL_SLOT_X, MATERIAL_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isMintingIngot(stack);
            }
        });
        this.addStandardInventorySlots(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    /** Server-authored display data only; never used to authorize minting. */
    public ClientMintData clientData() {
        return clientData;
    }

    /** Returns whether the visible input slot contains the matching metal ingot. */
    public boolean hasMaterialFor(Kingdom.Metal metal) {
        return this.materialSlot.getItem(MATERIAL_SLOT).is(ingotFor(metal));
    }

    /** Moves shift-clicked stacks between the ingredient slot and the real player inventory. */
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
        } else if (isMintingIngot(stack)) {
            moved = this.moveItemStackTo(stack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false);
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

    /** Mints exactly one server-authenticated coin after all live checks pass. */
    @Override
    public void handleMintCoinRequest(ServerPlayer player, MintCoinPayload payload) {
        if (payload.containerId() != this.containerId || !isMintCoinRequestValid(player)) {
            player.closeContainer();
            return;
        }

        Optional<MintRequest> request = validatePayload(player, payload.metalId(), payload.styleId(), payload.symbolIds());
        if (request.isEmpty()) {
            player.sendSystemMessage(Component.literal("Mint request was rejected."));
            return;
        }

        MintRequest validated = request.get();
        final ItemStack coin;
        try {
            coin = createCoin(validated);
        } catch (IllegalArgumentException ignored) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.mint_rejected"));
            return;
        }

        if (!consumeInputIngot(validated.metal())) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.missing_ingot"));
            return;
        }

        if (!player.getInventory().add(coin)) {
            player.drop(coin, false);
        }
        player.sendSystemMessage(Component.translatable("message.crownscoins.coin_minted", validated.kingdom().currencyName()));
    }

    /**
     * Packet-handler entry point. It proves the exact Mint House menu is still open
     * before processing client-selected metal/style/symbol IDs.
     */
    public static Optional<MintRequest> validateCurrentPayload(
        ServerPlayer player,
        int metalId,
        int styleId,
        List<Integer> symbolIds
    ) {
        if (!(player.containerMenu instanceof MintHouseMenu menu)) {
            return Optional.empty();
        }
        return menu.validatePayload(player, metalId, styleId, symbolIds);
    }

    /**
     * Validates a decoded mint request without consuming an ingot or creating an
     * item. The caller must separately check and consume the corresponding ingot
     * after this method succeeds.
     */
    public Optional<MintRequest> validatePayload(ServerPlayer player, int metalId, int styleId, List<Integer> symbolIds) {
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
        if (metal.isEmpty()
            || styleId < 1
            || styleId > CoinData.MAX_STYLE_ID
            || symbolIds == null
            || symbolIds.size() != CoinData.REQUIRED_SECONDARY_SYMBOLS
            || !KingdomCrest.isSupported(kingdom.get().crest())) {
            return Optional.empty();
        }

        List<Symbol> symbols = new ArrayList<>(symbolIds.size());
        Set<Symbol> seenSymbols = EnumSet.noneOf(Symbol.class);
        try {
            for (Integer symbolId : symbolIds) {
                Symbol symbol = symbolId == null ? null : Symbol.byId(symbolId);
                if (symbol == null || !seenSymbols.add(symbol)) {
                    return Optional.empty();
                }
                symbols.add(symbol);
            }
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }

        return Optional.of(new MintRequest(kingdom.get(), metal.get(), styleId, List.copyOf(symbols)));
    }

    private static ItemStack createCoin(MintRequest request) {
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
            kingdom.crest(),
            material,
            kingdom.value(request.metal()),
            request.styleId(),
            request.symbols()
        );
        coin.set(CrownsCoins.COIN_DATA.get(), coinData);
        coin.set(DataComponents.CUSTOM_NAME, Component.literal(kingdom.currencyName()));
        return coin;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.materialSlot);
    }

    private boolean consumeInputIngot(Kingdom.Metal metal) {
        ItemStack stack = this.materialSlot.getItem(MATERIAL_SLOT);
        if (!stack.is(ingotFor(metal))) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.materialSlot.setItem(MATERIAL_SLOT, ItemStack.EMPTY);
        } else {
            this.materialSlot.setChanged();
        }
        return true;
    }

    private boolean moveBetweenPlayerRows(ItemStack stack, int slotIndex) {
        if (slotIndex < PLAYER_MAIN_END) {
            return this.moveItemStackTo(stack, PLAYER_MAIN_END, PLAYER_SLOT_END, false);
        }
        return this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_MAIN_END, false);
    }

    private static boolean isMintingIngot(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.GOLD_INGOT);
    }

    private static Item ingotFor(Kingdom.Metal metal) {
        return switch (metal) {
            case IRON -> Items.IRON_INGOT;
            case COPPER -> Items.COPPER_INGOT;
            case GOLD -> Items.GOLD_INGOT;
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

    /** A server-validated mint intent. The two symbols preserve left/right catalog order. */
    public record MintRequest(Kingdom kingdom, Kingdom.Metal metal, int styleId, List<Symbol> symbols) {
    }

    /** Immutable snapshot written by the server while the menu opens. */
    public record ClientMintData(String kingdomName, Symbol crest, int ironValue, int copperValue, int goldValue) {
        private static ClientMintData empty() {
            return new ClientMintData("", Symbol.CROWN, 1, 1, 1);
        }
    }
}
