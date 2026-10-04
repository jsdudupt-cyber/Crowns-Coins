package com.crownscoins.menu;

import com.crownscoins.CrownsCoins;
import com.crownscoins.block.MintHouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Left-hand half of the Mint House. It is intentionally a simple one-input
 * furnace: players place native nuggets here and the block entity processes
 * them automatically into base coins in the shared chest.
 */
public final class MintFurnaceMenu extends MintHouseBoundMenu {
    private static final int INPUT_SLOT = 0;
    /** One source of truth for both the real slots and their visual guides. */
    public static final int INPUT_SLOT_X = 47;
    public static final int INPUT_SLOT_Y = 123;
    public static final int CHEST_SLOT_X = 387;
    public static final int CHEST_SLOT_Y = 100;
    public static final int CHEST_SLOT_X_STEP = 20;
    public static final int CHEST_SLOT_Y_STEP = 20;
    public static final int PLAYER_SLOT_X = 141;
    public static final int PLAYER_SLOT_X_STEP = 20;
    public static final int PLAYER_ROW_ONE_Y = 240;
    public static final int PLAYER_ROW_TWO_Y = 260;
    public static final int PLAYER_HOTBAR_Y = 288;
    private static final int CHEST_SLOT_START = 1;
    /** The painted arca shows only the first nine chest slots, and only those are menu slots. */
    private static final int CHEST_VIEW_SLOTS = 9;
    private static final int CHEST_SLOT_END = CHEST_SLOT_START + CHEST_VIEW_SLOTS;
    private static final int PLAYER_SLOT_START = CHEST_SLOT_END;
    /**
     * Only the two painted backpack rows and the hotbar are quick-move targets.
     * The third backpack row is registered after them, off-screen, so shift-click
     * can never hide items there.
     */
    private static final int PLAYER_MAIN_END = PLAYER_SLOT_START + 18;
    private static final int PLAYER_SLOT_END = PLAYER_MAIN_END + 9;
    private final Container furnaceInput;

    public MintFurnaceMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos mintHousePos) {
        this(CrownsCoins.MINT_FURNACE_MENU.get(), containerId, inventory, level.dimension(), mintHousePos);
    }

    public MintFurnaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(CrownsCoins.MINT_FURNACE_MENU.get(), containerId, inventory, inventory.player.level().dimension(), data.readBlockPos());
    }

    private MintFurnaceMenu(
        MenuType<?> menuType,
        int containerId,
        Inventory inventory,
        ResourceKey<Level> dimension,
        BlockPos mintHousePos
    ) {
        super(menuType, containerId, dimension, mintHousePos);
        this.furnaceInput = furnaceInputFor(inventory, mintHousePos);
        // The coordinates centre normal 16px Minecraft items in the larger
        // painted recesses from the player-authored furnace background.
        this.addSlot(new Slot(this.furnaceInput, INPUT_SLOT, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isNugget(stack);
            }
        });
        Container coinStorage = coinStorageFor(inventory, mintHousePos);
        for (int slot = 0; slot < CHEST_VIEW_SLOTS; slot++) {
            int x = chestSlotX(slot);
            int y = chestSlotY(slot);
            this.addSlot(new ReadOnlyCoinSlot(coinStorage, slot, x, y));
        }
        addFurnaceInventorySlots(inventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot source = this.slots.get(slotIndex);
        if (source == null || !source.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = source.getItem().copy();
        ItemStack stack = source.getItem();
        if (slotIndex == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < PLAYER_SLOT_START) {
            return ItemStack.EMPTY;
        } else if (isNugget(stack)) {
            if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < PLAYER_MAIN_END) {
            if (!this.moveItemStackTo(stack, PLAYER_MAIN_END, PLAYER_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_MAIN_END, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged();
        return original;
    }

    public static boolean isNugget(ItemStack stack) {
        return stack.is(Items.COPPER_NUGGET) || stack.is(Items.IRON_NUGGET) || stack.is(Items.GOLD_NUGGET);
    }

    /** Used only to animate the furnace UI while it has a valid stack to process. */
    public boolean hasNuggets() {
        return isNugget(this.furnaceInput.getItem(INPUT_SLOT));
    }

    /**
     * The supplied layout intentionally shows two roomy inventory rows plus
     * the hotbar. The third backpack row remains part of the real inventory
     * but stays off-screen rather than drawing over the artwork.
     */
    private void addFurnaceInventorySlots(Inventory inventory) {
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                // The player-authored artwork uses roomy 40px cells.  A
                // normal Minecraft item remains 16px, centered in each one.
                int y = row == 0 ? PLAYER_ROW_ONE_Y : PLAYER_ROW_TWO_Y;
                this.addSlot(new Slot(inventory, column + row * 9 + 9, playerSlotX(column), y));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, playerSlotX(column), PLAYER_HOTBAR_Y));
        }
    }

    public static int chestSlotX(int slot) {
        return CHEST_SLOT_X + (slot % 3) * CHEST_SLOT_X_STEP;
    }

    public static int chestSlotY(int slot) {
        return CHEST_SLOT_Y + (slot / 3) * CHEST_SLOT_Y_STEP;
    }

    public static int playerSlotX(int column) {
        return PLAYER_SLOT_X + column * PLAYER_SLOT_X_STEP;
    }

    private static Container furnaceInputFor(Inventory inventory, BlockPos mintHousePos) {
        return inventory.player.level().getBlockEntity(mintHousePos) instanceof MintHouseBlockEntity mintHouse
            ? mintHouse.furnaceInput()
            : new SimpleContainer(1);
    }

    private static Container coinStorageFor(Inventory inventory, BlockPos mintHousePos) {
        return inventory.player.level().getBlockEntity(mintHousePos) instanceof MintHouseBlockEntity mintHouse
            ? mintHouse
            : new SimpleContainer(MintHouseBlockEntity.COIN_STORAGE_SLOTS);
    }

    /** Furnace output belongs to the shared arca; this screen shows it without moving it. */
    private static final class ReadOnlyCoinSlot extends Slot {
        private ReadOnlyCoinSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
