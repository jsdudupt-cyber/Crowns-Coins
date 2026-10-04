package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import com.crownscoins.CrownsCoinsConfig;
import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.menu.MintHouseMenu;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The exact Mint House binding and its integrated coin chest.
 *
 * <p>The chest deliberately lives on the block entity instead of in a menu-local
 * {@code SimpleContainer}: it is saved with the world, shared by every player
 * opening this exact table, and dropped by Minecraft's normal block-entity
 * removal path ({@link #preRemoveSideEffects}) when the table is broken. The
 * furnace input is exposed as one extra slot, so it is dropped the same way
 * and hoppers can feed it nuggets.</p>
 */
public final class MintHouseBlockEntity extends BlockEntity implements WorldlyContainer {
    /** One simple-chest page, reserved for the three Crowns & Coins denominations. */
    public static final int COIN_STORAGE_SLOTS = 27;
    /** Container slot that exposes the furnace nugget input (after the coin chest). */
    public static final int FURNACE_SLOT = COIN_STORAGE_SLOTS;
    private static final int[] COIN_SLOTS = IntStream.range(0, COIN_STORAGE_SLOTS).toArray();
    private static final int[] FURNACE_SLOTS = {FURNACE_SLOT};
    /** Pulling nuggets from a neighbouring container: how often it is tried. */
    private static final int PULL_INTERVAL_TICKS = 8;

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
    /** Runtime-only: whether the furnace is smelting, and whether the lit block state was checked since loading. */
    private boolean furnaceWorking;
    private boolean litSynced;

    public MintHouseBlockEntity(BlockPos pos, BlockState state) { super(CrownsCoins.MINT_HOUSE_ENTITY.get(), pos, state); }
    public Optional<UUID> kingdomId() { return Optional.ofNullable(kingdomId); }
    public void bind(UUID id) { kingdomId = id; setChanged(); }
    public Container furnaceInput() { return furnaceInput; }

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
        if (CrownsCoinsConfig.PULL_NUGGETS_FROM_NEIGHBOURS.get() && serverLevel.getGameTime() % PULL_INTERVAL_TICKS == 0) {
            mintHouse.pullNuggetsFromNeighbours(serverLevel);
        }
        mintHouse.smeltOneCoin(serverLevel);
    }

    /**
     * Pulls nuggets into the furnace from containers touching either half of the Mint House
     * (chests, barrels, shulker boxes, a hopper...). It tops the furnace up to a full stack of
     * the nugget it already holds, or starts with the first nugget it finds, and only works once
     * the Mint House belongs to a kingdom (otherwise the nuggets would just sit there).
     */
    private void pullNuggetsFromNeighbours(ServerLevel level) {
        if (kingdomId == null || !(level.getBlockState(this.worldPosition).getBlock() instanceof MintHouseBlock)) {
            return;
        }
        ItemStack current = furnaceInput.getItem(0);
        if (!current.isEmpty() && (metalForNugget(current).isEmpty() || current.getCount() >= current.getMaxStackSize())) {
            return;
        }
        BlockPos headPos = this.worldPosition.relative(this.getBlockState().getValue(MintHouseBlock.FACING).getClockWise());
        for (BlockPos half : new BlockPos[] {this.worldPosition, headPos}) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = half.relative(direction);
                if (neighbour.equals(this.worldPosition) || neighbour.equals(headPos)
                    || level.getBlockState(neighbour).getBlock() instanceof MintHouseBlock) {
                    continue;
                }
                Container source = HopperBlockEntity.getContainerAt(level, neighbour);
                if (source != null && pullFrom(source, direction.getOpposite())) {
                    return;
                }
            }
        }
    }

    /** Moves nuggets from one container into the furnace input; returns true if any moved. */
    private boolean pullFrom(Container source, Direction sideOfSource) {
        int[] slots = source instanceof WorldlyContainer worldly
            ? worldly.getSlotsForFace(sideOfSource)
            : IntStream.range(0, source.getContainerSize()).toArray();
        for (int slot : slots) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty() || metalForNugget(stack).isEmpty()) {
                continue;
            }
            if (source instanceof WorldlyContainer worldly && !worldly.canTakeItemThroughFace(slot, stack, sideOfSource)) {
                continue;
            }
            ItemStack current = furnaceInput.getItem(0);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
                continue;
            }
            int room = stack.getMaxStackSize() - current.getCount();
            if (room <= 0) {
                return false;
            }
            ItemStack taken = source.removeItem(slot, Math.min(room, stack.getCount()));
            if (taken.isEmpty()) {
                continue;
            }
            if (current.isEmpty()) {
                furnaceInput.setItem(0, taken);
            } else {
                current.grow(taken.getCount());
                furnaceInput.setChanged();
            }
            source.setChanged();
            return true;
        }
        return false;
    }

    private void smeltOneCoin(ServerLevel level) {
        ItemStack nuggets = furnaceInput.getItem(0);
        Optional<Kingdom.Metal> metal = metalForNugget(nuggets);
        Optional<Kingdom> kingdom = kingdomId().flatMap(id -> KingdomSavedData.get(level).find(id));
        if (metal.isEmpty() || kingdom.isEmpty() || nuggets.getCount() < MintHouseMenu.nuggetsPerCoin(metal.get())) {
            furnaceTicks = 0;
            setWorking(level, false);
            return;
        }

        furnaceTicks++;
        int ticksNeeded = furnaceTicksPerCoin();
        if (furnaceTicks < ticksNeeded) {
            setWorking(level, true);
            return;
        }

        ItemStack baseCoin = MintHouseMenu.createBaseCoin(kingdom.get(), metal.get());
        if (!canStoreCoins(baseCoin)) {
            // The chest is full: the fire goes out until there is room again.
            furnaceTicks = ticksNeeded;
            setWorking(level, false);
            return;
        }

        nuggets.shrink(MintHouseMenu.nuggetsPerCoin(metal.get()));
        if (nuggets.isEmpty()) {
            furnaceInput.setItem(0, ItemStack.EMPTY);
        } else {
            furnaceInput.setChanged();
        }
        storeCoins(baseCoin);
        // A soft chime for every base coin that lands in the chest.
        level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 1.3F);
        furnaceTicks = 0;
        setChanged();
    }

    /**
     * Remembers whether the furnace is smelting and lights or darkens the furnace half
     * of the block to match. The block state is re-checked once after loading, so a
     * world saved while lit cannot stay lit forever.
     */
    private void setWorking(ServerLevel level, boolean working) {
        if (litSynced && working == furnaceWorking) {
            return;
        }
        litSynced = true;
        furnaceWorking = working;
        BlockPos headPos = this.worldPosition.relative(this.getBlockState().getValue(MintHouseBlock.FACING).getClockWise());
        BlockState head = level.getBlockState(headPos);
        if (head.is(CrownsCoins.MINT_HOUSE.get())
            && head.getValue(MintHouseBlock.PART) == BedPart.HEAD
            && head.getValue(MintHouseBlock.LIT) != working) {
            level.setBlock(headPos, head.setValue(MintHouseBlock.LIT, working), Block.UPDATE_ALL);
        }
    }

    /** How many ticks one coin takes, from the server config. */
    public static int furnaceTicksPerCoin() {
        return CrownsCoinsConfig.FURNACE_TICKS_PER_COIN.get();
    }

    /** Ticks smelted so far for the current coin, 0 up to {@link #furnaceTicksPerCoin()}; synchronized to the furnace screen. */
    public int furnaceProgress() {
        return furnaceTicks;
    }

    /** True while nuggets are actually being smelted; synchronized to the furnace screen. */
    public boolean isFurnaceWorking() {
        return furnaceWorking;
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

    /** The coin chest plus the furnace input socket, which hoppers see as one extra slot. */
    @Override
    public int getContainerSize() {
        return COIN_STORAGE_SLOTS + 1;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.coinStorage) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return furnaceInput.getItem(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == FURNACE_SLOT) {
            return furnaceInput.getItem(0);
        }
        return slot >= 0 && slot < COIN_STORAGE_SLOTS ? this.coinStorage.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == FURNACE_SLOT) {
            return furnaceInput.removeItem(0, amount);
        }
        ItemStack result = ContainerHelper.removeItem(this.coinStorage, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == FURNACE_SLOT) {
            return furnaceInput.removeItemNoUpdate(0);
        }
        ItemStack result = ContainerHelper.takeItem(this.coinStorage, slot);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!canPlaceItem(slot, stack) && !stack.isEmpty()) {
            return;
        }
        if (slot == FURNACE_SLOT) {
            furnaceInput.setItem(0, stack);
            stack.limitSize(this.getMaxStackSize(stack));
            return;
        }
        if (slot < 0 || slot >= COIN_STORAGE_SLOTS) {
            return;
        }
        this.coinStorage.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    /** Hoppers ask this before inserting, so unsupported items are refused instead of lost. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FURNACE_SLOT) {
            return metalForNugget(stack).isPresent();
        }
        return slot >= 0 && slot < COIN_STORAGE_SLOTS && acceptsCoin(stack);
    }

    /** From below, hoppers see the coin chest; from any other side they see the nugget input. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? COIN_SLOTS : FURNACE_SLOTS;
    }

    /** Nuggets may be fed in from above or the sides, never into the coin chest. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return slot == FURNACE_SLOT && direction != Direction.DOWN && metalForNugget(stack).isPresent();
    }

    /**
     * Only finished (designed) coins can be piped out from below. Plain base
     * coins stay in the chest because the press needs them.
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        if (!CrownsCoinsConfig.ALLOW_HOPPER_OUTPUT.get() || direction != Direction.DOWN || slot < 0 || slot >= COIN_STORAGE_SLOTS) {
            return false;
        }
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        return data != null && data.shapeId() != CoinData.DEFAULT_SHAPE_ID;
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
        furnaceInput.setItem(0, ItemStack.EMPTY);
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
