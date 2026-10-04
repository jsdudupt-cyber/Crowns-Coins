package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import com.crownscoins.coin.CoinFactory;
import com.crownscoins.coin.CoinWallet;
import com.crownscoins.coin.ShopPricing;
import com.crownscoins.kingdom.KingdomSavedData;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The shop side of a Currency Exchange: which kingdom owns it, the price of each item and
 * the till of coins taken in sales.
 *
 * <p>The goods are never stored here. They stay in any container touching the table, so a
 * normal chest is the shelf. Only plain items are for sale (no custom name, enchantment or
 * stored contents), so a price set for "diamond" can never sell a special one by accident.</p>
 */
public final class CurrencyExchangeBlockEntity extends BlockEntity {
    private static final Codec<Map<String, Integer>> PRICES_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);

    private UUID kingdomId;
    private final Map<String, Integer> prices = new HashMap<>();
    private int tillGold;
    private int tillIron;
    private int tillCopper;

    /** One kind of item on the shelf and how many of it there are. */
    public record Listing(Item item, int count) {
    }

    public CurrencyExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(CrownsCoins.CURRENCY_EXCHANGE_ENTITY.get(), pos, state);
    }

    public Optional<UUID> kingdomId() {
        return Optional.ofNullable(this.kingdomId);
    }

    public void bind(UUID id) {
        this.kingdomId = id;
        this.setChanged();
    }

    /** The price in copper units, or 0 when the item is not for sale. */
    public int price(Item item) {
        return this.prices.getOrDefault(key(item), 0);
    }

    public void setPrice(Item item, int price) {
        int clamped = Math.max(0, Math.min(ShopPricing.MAX_PRICE, price));
        if (clamped == 0) {
            this.prices.remove(key(item));
        } else {
            this.prices.put(key(item), clamped);
        }
        this.setChanged();
    }

    public int tillGold() {
        return this.tillGold;
    }

    public int tillIron() {
        return this.tillIron;
    }

    public int tillCopper() {
        return this.tillCopper;
    }

    public void addToTill(CoinWallet.Payment coins) {
        this.tillGold += coins.gold();
        this.tillIron += coins.iron();
        this.tillCopper += coins.copper();
        this.setChanged();
    }

    public void removeFromTill(CoinWallet.Payment coins) {
        this.tillGold = Math.max(0, this.tillGold - coins.gold());
        this.tillIron = Math.max(0, this.tillIron - coins.iron());
        this.tillCopper = Math.max(0, this.tillCopper - coins.copper());
        this.setChanged();
    }

    /** Empties the till and returns what was in it. */
    public CoinWallet.Payment emptyTill() {
        CoinWallet.Payment content = new CoinWallet.Payment(this.tillGold, this.tillIron, this.tillCopper);
        this.tillGold = 0;
        this.tillIron = 0;
        this.tillCopper = 0;
        this.setChanged();
        return content;
    }

    /** Everything on the shelf, grouped by item and sorted by name so the list stays steady. */
    public List<Listing> listings() {
        Map<String, Listing> grouped = new TreeMap<>(Comparator.naturalOrder());
        for (Container shelf : this.shelves()) {
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                ItemStack stack = shelf.getItem(slot);
                if (!isForSale(stack)) {
                    continue;
                }
                grouped.merge(
                    key(stack.getItem()),
                    new Listing(stack.getItem(), stack.getCount()),
                    (left, right) -> new Listing(left.item(), left.count() + right.count())
                );
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /** Takes one plain item of this kind off the shelf; false if none is left. */
    public boolean takeOne(Item item) {
        for (Container shelf : this.shelves()) {
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                ItemStack stack = shelf.getItem(slot);
                if (isForSale(stack) && stack.is(item)) {
                    stack.shrink(1);
                    shelf.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
                    shelf.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isForSale(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
    }

    /** The containers touching the table, except the Mint House and other exchanges. */
    private List<Container> shelves() {
        List<Container> shelves = new ArrayList<>();
        if (this.level == null) {
            return shelves;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = this.worldPosition.relative(direction);
            BlockState state = this.level.getBlockState(neighbour);
            if (state.getBlock() instanceof MintHouseBlock || state.getBlock() instanceof CurrencyExchangeBlock) {
                continue;
            }
            Container container = HopperBlockEntity.getContainerAt(this.level, neighbour);
            if (container != null) {
                shelves.add(container);
            }
        }
        return shelves;
    }

    private static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** Breaking the table drops the till's coins so nothing is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(this.level instanceof ServerLevel serverLevel) || this.kingdomId == null) {
            return;
        }
        KingdomSavedData.get(serverLevel).find(this.kingdomId).ifPresent(kingdom -> {
            CoinWallet.Payment content = new CoinWallet.Payment(this.tillGold, this.tillIron, this.tillCopper);
            for (ItemStack coins : CoinFactory.coins(kingdom, content)) {
                Containers.dropItemStack(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, coins);
            }
        });
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.kingdomId = input.read("kingdom_id", UUIDUtil.CODEC).orElse(null);
        this.prices.clear();
        input.read("prices", PRICES_CODEC).ifPresent(map -> map.forEach((name, price) -> {
            if (price != null && price > 0) {
                this.prices.put(name, Math.min(price, ShopPricing.MAX_PRICE));
            }
        }));
        this.tillGold = Math.max(0, input.read("till_gold", Codec.INT).orElse(0));
        this.tillIron = Math.max(0, input.read("till_iron", Codec.INT).orElse(0));
        this.tillCopper = Math.max(0, input.read("till_copper", Codec.INT).orElse(0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("kingdom_id", UUIDUtil.CODEC, this.kingdomId);
        output.store("prices", PRICES_CODEC, this.prices);
        output.store("till_gold", Codec.INT, this.tillGold);
        output.store("till_iron", Codec.INT, this.tillIron);
        output.store("till_copper", Codec.INT, this.tillCopper);
    }

}
