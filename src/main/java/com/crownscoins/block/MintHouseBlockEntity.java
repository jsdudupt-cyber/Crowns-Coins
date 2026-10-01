package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.menu.MintHouseMenu;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The exact Mint House binding and its integrated coin chest.
 *
 * <p>The chest deliberately lives on the block entity instead of in a menu-local
 * {@code SimpleContainer}: it is saved with the world, shared by every player
 * opening this exact table, and dropped by Minecraft's normal block-entity
 * removal path ({@link #preRemoveSideEffects}) when the table is broken.</p>
 */
public final class MintHouseBlockEntity extends BlockEntity implements Container {
    /** One simple-chest page, reserved for the three Crowns & Coins denominations. */
    public static final int COIN_STORAGE_SLOTS = 27;
    private static final int FURNACE_TICKS_PER_COIN = 20;

    private UUID kingdomId;
    private final NonNullList<ItemStack> coinStorage = NonNullList.withSize(COIN_STORAGE_SLOTS, ItemStack.EMPTY);
    /** Persistent input socket shared with the left-hand nugget furnace menu. */
    private final SimpleContainer furnaceInput = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            MintHouseBlockEntity.this.setChanged();
        }
    };
    private int furnaceTicks;

    public MintHouseBlockEntity(BlockPos pos, BlockState state) { super(CrownsCoins.MINT_HOUSE_ENTITY.get(), pos, state); }
    public Optional<UUID> kingdomId() { return Optional.ofNullable(kingdomId); }
    public void bind(UUID id) { kingdomId = id; setChanged(); }
    public Container furnaceInput() { return furnaceInput; }

    /**
     * Minecraft already drops this block entity's coin chest because it is a
     * {@link Container}. The separate furnace input socket needs the same treatment.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            Containers.dropContents(this.level, pos, furnaceInput);
        }
    }

    /** Returns true for the three physical coin items accepted by the integrated chest. */
    public static boolean acceptsCoin(ItemStack stack) {
        return stack.is(CrownsCoins.COPPER_COIN.get())
            || stack.is(CrownsCoins.IRON_COIN.get())
            || stack.is(CrownsCoins.GOLD_COIN.get());
    }

    /**
     * Inserts as much as possible into the chest and returns the untouched
     * remainder. Callers must then try the player inventory/drop path.
     */
    public ItemStack storeCoins(ItemStack stack) {
        if (stack.isEmpty() || !acceptsCoin(stack)) {
            return stack;
        }

        ItemStack remaining = stack.copy();
        mergeIntoExistingStacks(remaining);
        moveIntoEmptySlots(remaining);
        if (remaining.getCount() != stack.getCount()) {
            this.setChanged();
        }
        return remaining;
    }

    /** Runs only on the server. Nuggets slowly become clean round coins in the shared chest. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MintHouseBlockEntity mintHouse) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        mintHouse.smeltOneCoin(serverLevel);
    }

    private void smeltOneCoin(ServerLevel level) {
        ItemStack nuggets = furnaceInput.getItem(0);
        Optional<Kingdom.Metal> metal = metalForNugget(nuggets);
        Optional<Kingdom> kingdom = kingdomId().flatMap(id -> KingdomSavedData.get(level).find(id));
        if (metal.isEmpty() || kingdom.isEmpty() || nuggets.getCount() < MintHouseMenu.nuggetsPerCoin(metal.get())) {
            furnaceTicks = 0;
            return;
        }

        furnaceTicks++;
        if (furnaceTicks < FURNACE_TICKS_PER_COIN) {
            return;
        }

        ItemStack baseCoin = MintHouseMenu.createBaseCoin(kingdom.get(), metal.get());
        if (!canStoreCoins(baseCoin)) {
            furnaceTicks = FURNACE_TICKS_PER_COIN;
            return;
        }

        nuggets.shrink(MintHouseMenu.nuggetsPerCoin(metal.get()));
        if (nuggets.isEmpty()) {
            furnaceInput.setItem(0, ItemStack.EMPTY);
        } else {
            furnaceInput.setChanged();
        }
        storeCoins(baseCoin);
        furnaceTicks = 0;
        setChanged();
    }

    private static Optional<Kingdom.Metal> metalForNugget(ItemStack stack) {
        if (stack.is(Items.COPPER_NUGGET)) return Optional.of(Kingdom.Metal.COPPER);
        if (stack.is(Items.IRON_NUGGET)) return Optional.of(Kingdom.Metal.IRON);
        if (stack.is(Items.GOLD_NUGGET)) return Optional.of(Kingdom.Metal.GOLD);
        return Optional.empty();
    }

    private boolean canStoreCoins(ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack target : coinStorage) {
            if (ItemStack.isSameItemSameComponents(target, stack)) {
                remaining -= Math.max(0, target.getMaxStackSize() - target.getCount());
            }
        }
        for (ItemStack target : coinStorage) {
            if (target.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            }
        }
        return remaining <= 0;
    }

    @Override
    public int getContainerSize() {
        return COIN_STORAGE_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.coinStorage) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < COIN_STORAGE_SLOTS ? this.coinStorage.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(this.coinStorage, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(this.coinStorage, slot);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= COIN_STORAGE_SLOTS || (!stack.isEmpty() && !acceptsCoin(stack))) {
            return;
        }
        this.coinStorage.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.level != null
            && !this.isRemoved()
            && player.distanceToSqr(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
            ) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < COIN_STORAGE_SLOTS; slot++) {
            this.coinStorage.set(slot, ItemStack.EMPTY);
        }
        this.setChanged();
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        kingdomId = input.read("kingdom_id", UUIDUtil.CODEC).orElse(null);
        furnaceInput.setItem(0, input.read("furnace_input", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        furnaceTicks = input.read("furnace_ticks", Codec.INT).orElse(0);
        for (int slot = 0; slot < COIN_STORAGE_SLOTS; slot++) {
            this.coinStorage.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, this.coinStorage);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("kingdom_id", UUIDUtil.CODEC, kingdomId);
        output.store("furnace_input", ItemStack.CODEC, furnaceInput.getItem(0));
        output.store("furnace_ticks", Codec.INT, furnaceTicks);
        ContainerHelper.saveAllItems(output, this.coinStorage);
    }

    private void mergeIntoExistingStacks(ItemStack remaining) {
        for (ItemStack target : this.coinStorage) {
            if (!ItemStack.isSameItemSameComponents(target, remaining)) {
                continue;
            }
            int transferable = Math.min(remaining.getCount(), target.getMaxStackSize() - target.getCount());
            if (transferable > 0) {
                target.grow(transferable);
                remaining.shrink(transferable);
            }
            if (remaining.isEmpty()) {
                return;
            }
        }
    }

    private void moveIntoEmptySlots(ItemStack remaining) {
        for (int slot = 0; slot < COIN_STORAGE_SLOTS && !remaining.isEmpty(); slot++) {
            if (!this.coinStorage.get(slot).isEmpty()) {
                continue;
            }
            int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            this.coinStorage.set(slot, remaining.split(amount));
        }
    }
}
