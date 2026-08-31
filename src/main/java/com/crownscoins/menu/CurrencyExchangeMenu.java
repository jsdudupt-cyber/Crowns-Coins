package com.crownscoins.menu;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinData;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/**
 * Public, server-authoritative currency exchange.
 *
 * <p>A single stack can contain only one item-component value, which makes the
 * input naturally enforce the requirement that all coins come from the same
 * kingdom, currency, crown, style, and two side symbols. The result preserves
 * that identity and changes only its denomination material and value.</p>
 */
public final class CurrencyExchangeMenu extends AbstractContainerMenu {
    public static final int COPPER_TO_IRON_COUNT = 20;
    public static final int IRON_TO_GOLD_COUNT = 25;
    public static final int NUGGETS_PER_MELTED_COIN = 1;

    private static final int EXCHANGE_INPUT_SLOT = 0;
    private static final int EXCHANGE_OUTPUT_SLOT = 1;
    private static final int MELT_INPUT_SLOT = 2;
    private static final int MELT_OUTPUT_SLOT = 3;
    private static final int PLAYER_SLOT_START = 4;
    private static final int PLAYER_MAIN_END = PLAYER_SLOT_START + 27;
    private static final int PLAYER_SLOT_END = PLAYER_MAIN_END + 9;

    private static final int EXCHANGE_INPUT_X = 48;
    private static final int EXCHANGE_INPUT_Y = 61;
    private static final int EXCHANGE_OUTPUT_X = 159;
    private static final int EXCHANGE_OUTPUT_Y = 61;
    private static final int MELT_INPUT_X = 48;
    private static final int MELT_INPUT_Y = 124;
    private static final int MELT_OUTPUT_X = 159;
    private static final int MELT_OUTPUT_Y = 124;
    private static final int PLAYER_INVENTORY_X = 47;
    private static final int PLAYER_INVENTORY_Y = 195;

    private final ResourceKey<Level> dimension;
    private final BlockPos exchangePos;
    private final SimpleContainer exchangeInput = new SimpleContainer(1);
    private final SimpleContainer exchangeResult = new SimpleContainer(1);
    private final SimpleContainer meltInput = new SimpleContainer(1);
    private final SimpleContainer meltResult = new SimpleContainer(1);

    /** Server constructor; the only target is the exact block the player opened. */
    public CurrencyExchangeMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos exchangePos) {
        this(containerId, inventory, level.dimension(), exchangePos);
    }

    /** Client factory; the server sends only the fixed block position. */
    public CurrencyExchangeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, inventory.player.level().dimension(), data.readBlockPos());
    }

    private CurrencyExchangeMenu(int containerId, Inventory inventory, ResourceKey<Level> dimension, BlockPos exchangePos) {
        super(CrownsCoins.CURRENCY_EXCHANGE_MENU.get(), containerId);
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.exchangePos = Objects.requireNonNull(exchangePos, "exchangePos").immutable();

        // Each temporary container has one internal slot (index 0); the named
        // constants above are the menu slot positions used by quick-move logic.
        this.addSlot(new InputCoinSlot(this.exchangeInput, 0, EXCHANGE_INPUT_X, EXCHANGE_INPUT_Y, this::refreshExchangeResult));
        this.addSlot(new ExchangeResultSlot(this.exchangeResult, 0, EXCHANGE_OUTPUT_X, EXCHANGE_OUTPUT_Y));
        this.addSlot(new InputCoinSlot(this.meltInput, 0, MELT_INPUT_X, MELT_INPUT_Y, this::refreshMeltResult));
        this.addSlot(new MeltResultSlot(this.meltResult, 0, MELT_OUTPUT_X, MELT_OUTPUT_Y));
        this.addStandardInventorySlots(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
        this.refreshExchangeResult();
        this.refreshMeltResult();
    }

    /** The exchange is intentionally usable by every player, but only at this live block. */
    @Override
    public boolean stillValid(Player player) {
        if (!player.isAlive() || !player.level().dimension().equals(this.dimension) || !player.level().hasChunkAt(this.exchangePos)) {
            return false;
        }
        if (!player.level().getBlockState(this.exchangePos).is(CrownsCoins.CURRENCY_EXCHANGE.get())) {
            return false;
        }
        return player.distanceToSqr(
            this.exchangePos.getX() + 0.5D,
            this.exchangePos.getY() + 0.5D,
            this.exchangePos.getZ() + 0.5D
        ) <= 64.0D;
    }

    /** Shift-click support for both input sockets and generated outputs. */
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
        ItemStack original = stack.copy();
        boolean moved;
        if (slotIndex == EXCHANGE_OUTPUT_SLOT || slotIndex == MELT_OUTPUT_SLOT) {
            moved = this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true);
        } else if (slotIndex == EXCHANGE_INPUT_SLOT || slotIndex == MELT_INPUT_SLOT) {
            moved = this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true);
        } else if (isValidCoin(stack)) {
            // Prefer the conversion socket, then let a filled conversion socket
            // leave the second compatible stack in the melting socket.
            moved = this.moveItemStackTo(stack, EXCHANGE_INPUT_SLOT, EXCHANGE_INPUT_SLOT + 1, false);
            if (!moved) {
                moved = this.moveItemStackTo(stack, MELT_INPUT_SLOT, MELT_INPUT_SLOT + 1, false);
            }
            if (!moved) {
                moved = this.moveBetweenPlayerRows(stack, slotIndex);
            }
        } else {
            moved = this.moveBetweenPlayerRows(stack, slotIndex);
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Result slots are previews backed by their inputs, never independent
        // inventory. Only return real player-owned inputs on close.
        this.clearContainer(player, this.exchangeInput);
        this.clearContainer(player, this.meltInput);
        this.exchangeResult.setItem(0, ItemStack.EMPTY);
        this.meltResult.setItem(0, ItemStack.EMPTY);
    }

    private void refreshExchangeResult() {
        this.exchangeResult.setItem(0, exchangeOutputFor(this.exchangeInput.getItem(0)));
    }

    private void refreshMeltResult() {
        this.meltResult.setItem(0, meltOutputFor(this.meltInput.getItem(0)));
    }

    private void takeExchangeResult() {
        Exchange exchange = exchangeFor(this.exchangeInput.getItem(0));
        if (exchange == null) {
            this.refreshExchangeResult();
            return;
        }
        ItemStack source = this.exchangeInput.getItem(0);
        source.shrink(exchange.requiredCoins());
        if (source.isEmpty()) {
            this.exchangeInput.setItem(0, ItemStack.EMPTY);
        } else {
            this.exchangeInput.setChanged();
        }
        this.refreshExchangeResult();
    }

    private void takeMeltResult() {
        if (meltOutputFor(this.meltInput.getItem(0)).isEmpty()) {
            this.refreshMeltResult();
            return;
        }
        ItemStack source = this.meltInput.getItem(0);
        source.shrink(1);
        if (source.isEmpty()) {
            this.meltInput.setItem(0, ItemStack.EMPTY);
        } else {
            this.meltInput.setChanged();
        }
        this.refreshMeltResult();
    }

    private boolean moveBetweenPlayerRows(ItemStack stack, int slotIndex) {
        if (slotIndex < PLAYER_MAIN_END) {
            return this.moveItemStackTo(stack, PLAYER_MAIN_END, PLAYER_SLOT_END, false);
        }
        return this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_MAIN_END, false);
    }

    private static ItemStack exchangeOutputFor(ItemStack source) {
        Exchange exchange = exchangeFor(source);
        if (exchange == null) {
            return ItemStack.EMPTY;
        }
        CoinData original = source.get(CrownsCoins.COIN_DATA.get());
        if (original == null) {
            return ItemStack.EMPTY;
        }
        long targetValue = (long) original.value() * exchange.valueMultiplier();
        if (targetValue > CoinData.MAX_VALUE) {
            return ItemStack.EMPTY;
        }

        ItemStack output = new ItemStack(itemFor(exchange.targetMaterial()));
        output.set(CrownsCoins.COIN_DATA.get(), new CoinData(
            original.kingdomId(),
            original.kingdomName(),
            original.currencyName(),
            original.kingdomCrest(),
            exchange.targetMaterial(),
            (int) targetValue,
            original.styleId(),
            original.symbols()
        ));
        // The item renderer uses these native strings for both visible side
        // symbols and the permanent kingdom centre crest. Rebuild all three
        // values from CoinData so conversion repairs stacks minted by older
        // renderer versions that did not carry the crest string.
        output.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
            List.of(),
            List.of(),
            List.of(
                modelSymbolName(original, 0),
                modelSymbolName(original, 1),
                original.kingdomCrest().name().toLowerCase(Locale.ROOT)
            ),
            List.of()
        ));
        output.set(DataComponents.CUSTOM_NAME, Component.literal(original.currencyName()));
        return output;
    }

    /** Keeps legacy valid coins convertible even if they predate the two side-symbol rule. */
    private static String modelSymbolName(CoinData data, int index) {
        return data.symbols().size() > index
            ? data.symbols().get(index).name().toLowerCase(Locale.ROOT)
            : "blank";
    }

    private static ItemStack meltOutputFor(ItemStack source) {
        CoinData.Material material = coinMaterial(source);
        if (material == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(nuggetFor(material), NUGGETS_PER_MELTED_COIN);
    }

    /**
     * A compatible coin is a real mod coin with a component whose material also
     * matches the physical item. This rejects plain/forged base items.
     */
    private static boolean isValidCoin(ItemStack stack) {
        return coinMaterial(stack) != null;
    }

    private static CoinData.Material coinMaterial(ItemStack stack) {
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        if (data == null) {
            return null;
        }
        if (stack.is(CrownsCoins.COPPER_COIN.get()) && data.material() == CoinData.Material.COPPER) {
            return CoinData.Material.COPPER;
        }
        if (stack.is(CrownsCoins.IRON_COIN.get()) && data.material() == CoinData.Material.IRON) {
            return CoinData.Material.IRON;
        }
        if (stack.is(CrownsCoins.GOLD_COIN.get()) && data.material() == CoinData.Material.GOLD) {
            return CoinData.Material.GOLD;
        }
        return null;
    }

    private static Exchange exchangeFor(ItemStack source) {
        CoinData.Material material = coinMaterial(source);
        if (material == CoinData.Material.COPPER && source.getCount() >= COPPER_TO_IRON_COUNT) {
            return new Exchange(CoinData.Material.IRON, COPPER_TO_IRON_COUNT, COPPER_TO_IRON_COUNT);
        }
        if (material == CoinData.Material.IRON && source.getCount() >= IRON_TO_GOLD_COUNT) {
            return new Exchange(CoinData.Material.GOLD, IRON_TO_GOLD_COUNT, IRON_TO_GOLD_COUNT);
        }
        return null;
    }

    private static Item itemFor(CoinData.Material material) {
        return switch (material) {
            case COPPER -> CrownsCoins.COPPER_COIN.get();
            case IRON -> CrownsCoins.IRON_COIN.get();
            case GOLD -> CrownsCoins.GOLD_COIN.get();
        };
    }

    private static Item nuggetFor(CoinData.Material material) {
        return switch (material) {
            case COPPER -> CrownsCoins.COPPER_NUGGET.get();
            case IRON -> Items.IRON_NUGGET;
            case GOLD -> Items.GOLD_NUGGET;
        };
    }

    private record Exchange(CoinData.Material targetMaterial, int requiredCoins, int valueMultiplier) {
    }

    /** Input socket that immediately refreshes the matching generated result. */
    private static final class InputCoinSlot extends Slot {
        private final Runnable changed;

        private InputCoinSlot(SimpleContainer container, int index, int x, int y, Runnable changed) {
            super(container, index, x, y);
            this.changed = changed;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isValidCoin(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.changed.run();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack removed = super.remove(amount);
            // Picking an input up is a remove operation in the vanilla menu;
            // refresh immediately so a former result never remains clickable.
            if (!removed.isEmpty()) {
                this.changed.run();
            }
            return removed;
        }
    }

    private final class ExchangeResultSlot extends Slot {
        private ExchangeResultSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return exchangeFor(CurrencyExchangeMenu.this.exchangeInput.getItem(0)) != null;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            CurrencyExchangeMenu.this.takeExchangeResult();
        }
    }

    private final class MeltResultSlot extends Slot {
        private MeltResultSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !meltOutputFor(CurrencyExchangeMenu.this.meltInput.getItem(0)).isEmpty();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            CurrencyExchangeMenu.this.takeMeltResult();
        }
    }
}
