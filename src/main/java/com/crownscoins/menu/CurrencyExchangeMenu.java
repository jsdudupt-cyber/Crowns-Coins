package com.crownscoins.menu;

import com.crownscoins.CrownsCoins;
import com.crownscoins.block.CurrencyExchangeBlockEntity;
import com.crownscoins.coin.CoinData;
import com.crownscoins.coin.CoinFactory;
import com.crownscoins.coin.CoinWallet;
import com.crownscoins.coin.ShopPricing;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
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
import net.minecraft.world.level.Level;

/**
 * Public, server-authoritative currency exchange.
 *
 * <p>A single stack can contain only one item-component value, so the input
 * naturally enforces the requirement that all coins come from the same
 * kingdom and currency. The result preserves that identity and changes only
 * its denomination material and value.</p>
 */
public final class CurrencyExchangeMenu extends AbstractContainerMenu {
    public static final int COPPER_TO_IRON_COUNT = 20;
    public static final int IRON_TO_GOLD_COUNT = 25;

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

    /** The shop list shows this many items at a time; each row is a read-only slot that carries the item picture. */
    public static final int SHOP_ROWS = 5;
    public static final int SHOP_ROW_X = 16;
    public static final int SHOP_ROW_FIRST_Y = 42;
    public static final int SHOP_ROW_PITCH = 19;
    private static final int SHOP_ROW_START = PLAYER_SLOT_END;

    /** Button ids sent by the screen through {@link #clickMenuButton}. */
    public static final int BUTTON_BUY = 0;
    public static final int BUTTON_SELECT = 10;
    public static final int BUTTON_PRICE_MINUS_1 = 20;
    public static final int BUTTON_PRICE_PLUS_1 = 21;
    public static final int BUTTON_PRICE_MINUS_20 = 22;
    public static final int BUTTON_PRICE_PLUS_20 = 23;
    public static final int BUTTON_PRICE_MINUS_500 = 24;
    public static final int BUTTON_PRICE_PLUS_500 = 25;
    public static final int BUTTON_PRICE_CLEAR = 26;
    public static final int BUTTON_PAGE_PREVIOUS = 30;
    public static final int BUTTON_PAGE_NEXT = 31;
    public static final int BUTTON_WITHDRAW = 40;

    /** Layout of the synced numbers; each is kept inside 16 bits because that is how they travel. */
    private static final int DATA_PAGE = 0;
    private static final int DATA_PAGE_COUNT = 1;
    private static final int DATA_SELECTED = 2;
    private static final int DATA_PRICE = 3;
    private static final int DATA_STOCK = DATA_PRICE + SHOP_ROWS;
    private static final int DATA_TILL_GOLD = DATA_STOCK + SHOP_ROWS;
    private static final int DATA_TILL_IRON = DATA_TILL_GOLD + 1;
    private static final int DATA_TILL_COPPER = DATA_TILL_IRON + 1;
    private static final int DATA_SIZE = DATA_TILL_COPPER + 1;
    private static final int MAX_SYNCED = 30_000;

    private final ResourceKey<Level> dimension;
    private final BlockPos exchangePos;
    private final SimpleContainer exchangeInput = new SimpleContainer(1);
    private final SimpleContainer exchangeResult = new SimpleContainer(1);
    private final SimpleContainer meltInput = new SimpleContainer(1);
    private final SimpleContainer meltResult = new SimpleContainer(1);
    private final SimpleContainer shopRows = new SimpleContainer(SHOP_ROWS);
    private final ContainerData shopData = new SimpleContainerData(DATA_SIZE);
    /** Who owns the shop and whether this player is one of its members; fixed when the menu opens. */
    private final UUID shopKingdomId;
    private final String shopKingdomName;
    private final String shopCurrencyName;
    private final boolean shopMember;
    /** Null on the client: only the server reads the shelf and handles sales. */
    private final ServerLevel serverLevel;
    private List<CurrencyExchangeBlockEntity.Listing> listings = List.of();
    /** Client only: which tab is showing. The server never needs it. */
    private boolean shopTab;

    /** Server constructor; the only target is the exact block the player opened. */
    public CurrencyExchangeMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos exchangePos) {
        this(containerId, inventory, level.dimension(), exchangePos, level, shopOwner(level, exchangePos), inventory.player.getUUID());
    }

    private CurrencyExchangeMenu(
        int containerId, Inventory inventory, ResourceKey<Level> dimension, BlockPos exchangePos,
        ServerLevel level, Kingdom owner, UUID playerId
    ) {
        this(containerId, inventory, dimension, exchangePos, level,
            owner == null ? null : owner.id(),
            owner == null ? "" : owner.name(),
            owner == null ? "" : owner.currencyName(),
            owner != null && owner.isMember(playerId));
    }

    /** Client factory; the server sends the block position and who owns the shop. */
    public CurrencyExchangeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, inventory.player.level().dimension(), data.readBlockPos(), null, readOwner(data));
    }

    private CurrencyExchangeMenu(
        int containerId, Inventory inventory, ResourceKey<Level> dimension, BlockPos exchangePos,
        ServerLevel level, OpeningData opening
    ) {
        this(containerId, inventory, dimension, exchangePos, level,
            opening.kingdomId(), opening.kingdomName(), opening.currencyName(), opening.member());
    }

    private CurrencyExchangeMenu(
        int containerId, Inventory inventory, ResourceKey<Level> dimension, BlockPos exchangePos,
        ServerLevel level, UUID shopKingdomId, String shopKingdomName, String shopCurrencyName, boolean shopMember
    ) {
        super(CrownsCoins.CURRENCY_EXCHANGE_MENU.get(), containerId);
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.exchangePos = Objects.requireNonNull(exchangePos, "exchangePos").immutable();
        this.serverLevel = level;
        this.shopKingdomId = shopKingdomId;
        this.shopKingdomName = shopKingdomName;
        this.shopCurrencyName = shopCurrencyName;
        this.shopMember = shopMember;

        // Each temporary container has one internal slot (index 0); the named
        // constants above are the menu slot positions used by quick-move logic.
        this.addSlot(new InputCoinSlot(this.exchangeInput, 0, EXCHANGE_INPUT_X, EXCHANGE_INPUT_Y, this::refreshExchangeResult, this::onExchangeTab));
        this.addSlot(new ExchangeResultSlot(this.exchangeResult, 0, EXCHANGE_OUTPUT_X, EXCHANGE_OUTPUT_Y));
        this.addSlot(new InputCoinSlot(this.meltInput, 0, MELT_INPUT_X, MELT_INPUT_Y, this::refreshMeltResult, this::onExchangeTab));
        this.addSlot(new MeltResultSlot(this.meltResult, 0, MELT_OUTPUT_X, MELT_OUTPUT_Y));
        this.addStandardInventorySlots(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
        for (int row = 0; row < SHOP_ROWS; row++) {
            this.addSlot(new ShopRowSlot(this.shopRows, row, SHOP_ROW_X, SHOP_ROW_FIRST_Y + row * SHOP_ROW_PITCH));
        }
        this.addDataSlots(this.shopData);
        this.refreshExchangeResult();
        this.refreshMeltResult();
        this.refreshShop();
    }

    private boolean onExchangeTab() {
        return !this.shopTab;
    }

    /** What the server tells the client when the menu opens: the position and the shop's owner. */
    private record OpeningData(UUID kingdomId, String kingdomName, String currencyName, boolean member) {
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, BlockPos pos, Kingdom owner, boolean member) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(owner != null);
        if (owner != null) {
            buffer.writeUUID(owner.id());
            buffer.writeUtf(owner.name(), Kingdom.MAX_KINGDOM_NAME_LENGTH);
            buffer.writeUtf(owner.currencyName(), Kingdom.MAX_CURRENCY_NAME_LENGTH);
        }
        buffer.writeBoolean(member);
    }

    private static OpeningData readOwner(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return new OpeningData(null, "", "", buffer.readBoolean());
        }
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(Kingdom.MAX_KINGDOM_NAME_LENGTH);
        String currency = buffer.readUtf(Kingdom.MAX_CURRENCY_NAME_LENGTH);
        return new OpeningData(id, name, currency, buffer.readBoolean());
    }

    private static Kingdom shopOwner(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CurrencyExchangeBlockEntity exchange)) {
            return null;
        }
        return exchange.kingdomId().flatMap(KingdomSavedData.get(level)::find).orElse(null);
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
        if (slotIndex < 0 || slotIndex >= SHOP_ROW_START) {
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

    // ----- Shop: client-readable state -----

    public boolean isShopTab() {
        return this.shopTab;
    }

    public void setShopTab(boolean shopTab) {
        this.shopTab = shopTab;
    }

    public boolean hasShopKingdom() {
        return this.shopKingdomId != null;
    }

    public UUID shopKingdomId() {
        return this.shopKingdomId;
    }

    public String shopKingdomName() {
        return this.shopKingdomName;
    }

    public String shopCurrencyName() {
        return this.shopCurrencyName;
    }

    public boolean isShopMember() {
        return this.shopMember;
    }

    public int shopPage() {
        return this.shopData.get(DATA_PAGE);
    }

    public int shopPageCount() {
        return Math.max(1, this.shopData.get(DATA_PAGE_COUNT));
    }

    public int selectedRow() {
        return this.shopData.get(DATA_SELECTED);
    }

    /** The price of the item in a row, in copper units; 0 means it is not for sale. */
    public int rowPrice(int row) {
        return this.shopData.get(DATA_PRICE + row);
    }

    public int rowStock(int row) {
        return this.shopData.get(DATA_STOCK + row);
    }

    public ItemStack rowItem(int row) {
        return this.shopRows.getItem(row);
    }

    public CoinWallet.Payment till() {
        return new CoinWallet.Payment(
            this.shopData.get(DATA_TILL_GOLD),
            this.shopData.get(DATA_TILL_IRON),
            this.shopData.get(DATA_TILL_COPPER)
        );
    }

    // ----- Shop: server side -----

    /** Keeps the shelf list fresh while the menu is open, then syncs it like any other slot. */
    @Override
    public void broadcastChanges() {
        this.refreshShop();
        super.broadcastChanges();
    }

    private CurrencyExchangeBlockEntity shop() {
        return this.serverLevel != null && this.serverLevel.getBlockEntity(this.exchangePos) instanceof CurrencyExchangeBlockEntity exchange
            ? exchange
            : null;
    }

    private void refreshShop() {
        CurrencyExchangeBlockEntity shop = this.shop();
        if (shop == null) {
            return;
        }
        this.listings = shop.listings().stream()
            .filter(listing -> this.shopMember || shop.price(listing.item()) > 0)
            .toList();
        int pageCount = Math.max(1, (this.listings.size() + SHOP_ROWS - 1) / SHOP_ROWS);
        int page = Math.max(0, Math.min(this.shopData.get(DATA_PAGE), pageCount - 1));
        this.shopData.set(DATA_PAGE, page);
        this.shopData.set(DATA_PAGE_COUNT, pageCount);
        for (int row = 0; row < SHOP_ROWS; row++) {
            int index = page * SHOP_ROWS + row;
            if (index < this.listings.size()) {
                CurrencyExchangeBlockEntity.Listing listing = this.listings.get(index);
                ItemStack shown = new ItemStack(listing.item());
                if (!ItemStack.matches(this.shopRows.getItem(row), shown)) {
                    this.shopRows.setItem(row, shown);
                }
                this.shopData.set(DATA_PRICE + row, Math.min(shop.price(listing.item()), MAX_SYNCED));
                this.shopData.set(DATA_STOCK + row, Math.min(listing.count(), MAX_SYNCED));
            } else {
                if (!this.shopRows.getItem(row).isEmpty()) {
                    this.shopRows.setItem(row, ItemStack.EMPTY);
                }
                this.shopData.set(DATA_PRICE + row, 0);
                this.shopData.set(DATA_STOCK + row, 0);
            }
        }
        int selected = this.shopData.get(DATA_SELECTED);
        if (selected >= 0 && this.shopRows.getItem(selected).isEmpty()) {
            this.shopData.set(DATA_SELECTED, -1);
        }
        this.shopData.set(DATA_TILL_GOLD, Math.min(shop.tillGold(), MAX_SYNCED));
        this.shopData.set(DATA_TILL_IRON, Math.min(shop.tillIron(), MAX_SYNCED));
        this.shopData.set(DATA_TILL_COPPER, Math.min(shop.tillCopper(), MAX_SYNCED));
    }

    /** The listing a row stands for right now, or null if the row is empty. */
    private CurrencyExchangeBlockEntity.Listing listingAt(int row) {
        if (row < 0 || row >= SHOP_ROWS) {
            return null;
        }
        int index = this.shopData.get(DATA_PAGE) * SHOP_ROWS + row;
        return index < this.listings.size() ? this.listings.get(index) : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || this.serverLevel == null || !this.stillValid(player)) {
            return false;
        }
        CurrencyExchangeBlockEntity shop = this.shop();
        if (shop == null) {
            return false;
        }
        this.refreshShop();
        if (id >= BUTTON_BUY && id < BUTTON_BUY + SHOP_ROWS) {
            this.buy(serverPlayer, shop, id - BUTTON_BUY);
        } else if (id >= BUTTON_SELECT && id < BUTTON_SELECT + SHOP_ROWS) {
            if (this.shopMember && this.listingAt(id - BUTTON_SELECT) != null) {
                int row = id - BUTTON_SELECT;
                this.shopData.set(DATA_SELECTED, this.shopData.get(DATA_SELECTED) == row ? -1 : row);
            }
        } else if (id >= BUTTON_PRICE_MINUS_1 && id <= BUTTON_PRICE_CLEAR) {
            this.changePrice(shop, id);
        } else if (id == BUTTON_PAGE_PREVIOUS || id == BUTTON_PAGE_NEXT) {
            int step = id == BUTTON_PAGE_NEXT ? 1 : -1;
            this.shopData.set(DATA_PAGE, Math.max(0, Math.min(this.shopData.get(DATA_PAGE) + step, this.shopData.get(DATA_PAGE_COUNT) - 1)));
            this.shopData.set(DATA_SELECTED, -1);
        } else if (id == BUTTON_WITHDRAW) {
            this.withdraw(serverPlayer, shop);
        } else {
            return false;
        }
        this.refreshShop();
        return true;
    }

    /** Only a member of the owning kingdom may price the goods. */
    private void changePrice(CurrencyExchangeBlockEntity shop, int button) {
        CurrencyExchangeBlockEntity.Listing listing = this.listingAt(this.shopData.get(DATA_SELECTED));
        if (!this.shopMember || listing == null) {
            return;
        }
        int current = shop.price(listing.item());
        int next = switch (button) {
            case BUTTON_PRICE_MINUS_1 -> current - 1;
            case BUTTON_PRICE_PLUS_1 -> current + 1;
            case BUTTON_PRICE_MINUS_20 -> current - 20;
            case BUTTON_PRICE_PLUS_20 -> current + 20;
            case BUTTON_PRICE_MINUS_500 -> current - 500;
            case BUTTON_PRICE_PLUS_500 -> current + 500;
            default -> 0;
        };
        shop.setPrice(listing.item(), next);
    }

    private void withdraw(ServerPlayer player, CurrencyExchangeBlockEntity shop) {
        Kingdom kingdom = this.shopKingdom();
        if (!this.shopMember || kingdom == null) {
            return;
        }
        CoinWallet.Payment takings = shop.emptyTill();
        if (takings.value() == 0) {
            return;
        }
        for (ItemStack coins : CoinFactory.coins(kingdom, takings)) {
            giveOrDrop(player, coins);
        }
    }

    private Kingdom shopKingdom() {
        return this.shopKingdomId == null ? null : KingdomSavedData.get(this.serverLevel).find(this.shopKingdomId).orElse(null);
    }

    /**
     * One sale. Everything is worked out first, and nothing changes until the sale is sure
     * to go through: the item is still on the shelf, the buyer can pay, and the till can
     * make the change.
     */
    private void buy(ServerPlayer player, CurrencyExchangeBlockEntity shop, int row) {
        Kingdom kingdom = this.shopKingdom();
        CurrencyExchangeBlockEntity.Listing listing = this.listingAt(row);
        if (kingdom == null || listing == null) {
            return;
        }
        int price = shop.price(listing.item());
        if (price <= 0) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.shop_not_for_sale"), true);
            return;
        }
        CoinWallet.Holding purse = CoinWallet.holdings(player.getInventory()).stream()
            .filter(holding -> holding.kingdomId().equals(kingdom.id()))
            .findFirst()
            .orElse(null);
        var sale = purse == null ? java.util.Optional.<ShopPricing.Settlement>empty() : ShopPricing.settle(
            price,
            purse.gold(), purse.iron(), purse.copper(),
            shop.tillGold(), shop.tillIron(), shop.tillCopper()
        );
        if (sale.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.shop_cannot_pay", kingdom.currencyName()), true);
            return;
        }
        if (!shop.takeOne(listing.item())) {
            player.sendSystemMessage(Component.translatable("message.crownscoins.shop_sold_out"), true);
            return;
        }

        CoinWallet.take(player.getInventory(), kingdom.id(), sale.get().payment());
        shop.addToTill(sale.get().payment());
        shop.removeFromTill(sale.get().change());
        for (ItemStack coins : CoinFactory.coins(kingdom, sale.get().change())) {
            giveOrDrop(player, coins);
        }
        ItemStack goods = new ItemStack(listing.item());
        Component goodsName = goods.getHoverName();
        giveOrDrop(player, goods);
        this.serverLevel.playSound(null, this.exchangePos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6F, 1.3F);
        player.sendSystemMessage(Component.translatable("message.crownscoins.shop_bought", goodsName, priceText(price)), true);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
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
            exchange.targetMaterial(),
            (int) targetValue,
            // The design (shape) is preserved when a denomination is converted.
            original.shapeId()
        ));
        return output;
    }

    private static ItemStack meltOutputFor(ItemStack source) {
        CoinData.Material material = coinMaterial(source);
        if (material == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(nuggetFor(material), nuggetsPerCoin(material));
    }

    /** Melting returns exactly what minting the base coin costs, so recycling loses nothing. */
    public static int nuggetsPerCoin(CoinData.Material material) {
        return switch (material) {
            case COPPER -> MintHouseMenu.COPPER_NUGGETS_PER_COIN;
            case IRON -> MintHouseMenu.IRON_NUGGETS_PER_COIN;
            case GOLD -> MintHouseMenu.GOLD_NUGGETS_PER_COIN;
        };
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
            case COPPER -> Items.COPPER_NUGGET;
            case IRON -> Items.IRON_NUGGET;
            case GOLD -> Items.GOLD_NUGGET;
        };
    }

    private record Exchange(CoinData.Material targetMaterial, int requiredCoins, int valueMultiplier) {
    }

    /** Input socket that immediately refreshes the matching generated result. */
    private static final class InputCoinSlot extends Slot {
        private final Runnable changed;
        private final BooleanSupplier active;

        private InputCoinSlot(SimpleContainer container, int index, int x, int y, Runnable changed, BooleanSupplier active) {
            super(container, index, x, y);
            this.changed = changed;
            this.active = active;
        }

        @Override
        public boolean isActive() {
            return this.active.getAsBoolean();
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
        public boolean isActive() {
            return !CurrencyExchangeMenu.this.shopTab;
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
        public boolean isActive() {
            return !CurrencyExchangeMenu.this.shopTab;
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

    /** A read-only slot that only exists to show one shelf item's picture and name; it can never be used. */
    private final class ShopRowSlot extends Slot {
        private ShopRowSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return CurrencyExchangeMenu.this.shopTab;
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

    /** "1 ouro, 2 prata, 3 cobre" for a price in copper units. */
    public static Component priceText(long copper) {
        return coinsText(ShopPricing.split(copper));
    }

    /** The same wording for coins counted one by one, such as what a player carries. */
    public static Component coinsText(CoinWallet.Payment parts) {
        List<Component> pieces = new java.util.ArrayList<>();
        if (parts.gold() > 0) {
            pieces.add(Component.translatable("gui.crownscoins.shop_unit_gold", parts.gold()));
        }
        if (parts.iron() > 0) {
            pieces.add(Component.translatable("gui.crownscoins.shop_unit_iron", parts.iron()));
        }
        if (parts.copper() > 0 || pieces.isEmpty()) {
            pieces.add(Component.translatable("gui.crownscoins.shop_unit_copper", parts.copper()));
        }
        net.minecraft.network.chat.MutableComponent joined = Component.empty();
        for (int i = 0; i < pieces.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(pieces.get(i));
        }
        return joined;
    }
}
