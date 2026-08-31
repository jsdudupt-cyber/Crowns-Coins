package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * removal path when the table is broken.</p>
 */
public final class MintHouseBlockEntity extends BlockEntity implements Container {
    /** One simple-chest page, reserved for the three Crowns & Coins denominations. */
    public static final int COIN_STORAGE_SLOTS = 27;

    private UUID kingdomId;
    private final NonNullList<ItemStack> coinStorage = NonNullList.withSize(COIN_STORAGE_SLOTS, ItemStack.EMPTY);

    public MintHouseBlockEntity(BlockPos pos, BlockState state) { super(CrownsCoins.MINT_HOUSE_ENTITY.get(), pos, state); }
    public Optional<UUID> kingdomId() { return Optional.ofNullable(kingdomId); }
    public void bind(UUID id) { kingdomId = id; setChanged(); }

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
        return ContainerHelper.takeItem(this.coinStorage, slot);
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
        for (int slot = 0; slot < COIN_STORAGE_SLOTS; slot++) {
            this.coinStorage.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, this.coinStorage);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("kingdom_id", UUIDUtil.CODEC, kingdomId);
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
