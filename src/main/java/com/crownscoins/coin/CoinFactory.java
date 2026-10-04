package com.crownscoins.coin;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Builds real coin stacks for a kingdom, for coins that come out of a shop till as change or takings. */
public final class CoinFactory {
    /** Till coins have no remembered design, so they get the first one, which still counts as money. */
    private static final int TILL_SHAPE_ID = 1;

    private CoinFactory() {
    }

    /** The stacks (at most 64 coins each) for a number of coins of one metal. */
    public static List<ItemStack> coins(Kingdom kingdom, CoinData.Material material, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = count;
        while (remaining > 0) {
            int amount = Math.min(remaining, 64);
            ItemStack stack = new ItemStack(itemFor(material), amount);
            stack.set(CrownsCoins.COIN_DATA.get(), new CoinData(
                kingdom.id(),
                kingdom.name(),
                kingdom.currencyName(),
                material,
                valueOf(material),
                TILL_SHAPE_ID
            ));
            stacks.add(stack);
            remaining -= amount;
        }
        return stacks;
    }

    /** Coins for a whole payment: gold first, then silver, then copper. */
    public static List<ItemStack> coins(Kingdom kingdom, CoinWallet.Payment payment) {
        List<ItemStack> stacks = new ArrayList<>(coins(kingdom, CoinData.Material.GOLD, payment.gold()));
        stacks.addAll(coins(kingdom, CoinData.Material.IRON, payment.iron()));
        stacks.addAll(coins(kingdom, CoinData.Material.COPPER, payment.copper()));
        return stacks;
    }

    public static Item itemFor(CoinData.Material material) {
        return switch (material) {
            case COPPER -> CrownsCoins.COPPER_COIN.get();
            case IRON -> CrownsCoins.IRON_COIN.get();
            case GOLD -> CrownsCoins.GOLD_COIN.get();
        };
    }

    private static int valueOf(CoinData.Material material) {
        return switch (material) {
            case COPPER -> Kingdom.COPPER_COIN_VALUE;
            case IRON -> Kingdom.IRON_COIN_VALUE;
            case GOLD -> Kingdom.GOLD_COIN_VALUE;
        };
    }
}
