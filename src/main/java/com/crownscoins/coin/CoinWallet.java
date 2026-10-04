package com.crownscoins.coin;

import com.crownscoins.CrownsCoins;
import com.crownscoins.kingdom.Kingdom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Counting, paying and making exact change with the coins a player carries.
 *
 * <p>Only minted coins count as money: plain base coins (shape zero) are an
 * intermediate product of the furnace. Coins of different kingdoms are different
 * currencies and are never mixed.</p>
 */
public final class CoinWallet {
    private CoinWallet() {
    }

    /** How many coins of each denomination to hand over. */
    public record Payment(int gold, int iron, int copper) {
        public long value() {
            return (long) gold * Kingdom.GOLD_COIN_VALUE + (long) iron * Kingdom.IRON_COIN_VALUE + (long) copper * Kingdom.COPPER_COIN_VALUE;
        }
    }

    /** Totals per currency (kingdom) found in an inventory, in a stable order. */
    public record Holding(UUID kingdomId, String currencyName, int gold, int iron, int copper) {
        public long value() {
            return new Payment(gold, iron, copper).value();
        }
    }

    /**
     * Finds a way to pay exactly {@code amount} with the coins available, preferring the
     * fewest high-value coins burned on small prices (it uses as many gold coins as fit,
     * then iron, then copper). There is no change: if the exact sum cannot be formed the
     * result is empty.
     */
    public static Optional<Payment> plan(long amount, int goldAvailable, int ironAvailable, int copperAvailable) {
        if (amount <= 0) {
            return Optional.empty();
        }
        long maxGold = Math.min(goldAvailable, amount / Kingdom.GOLD_COIN_VALUE);
        for (long gold = maxGold; gold >= 0; gold--) {
            long afterGold = amount - gold * Kingdom.GOLD_COIN_VALUE;
            long maxIron = Math.min(ironAvailable, afterGold / Kingdom.IRON_COIN_VALUE);
            for (long iron = maxIron; iron >= 0; iron--) {
                long copper = afterGold - iron * Kingdom.IRON_COIN_VALUE;
                if (copper <= copperAvailable) {
                    return Optional.of(new Payment((int) gold, (int) iron, (int) copper));
                }
            }
        }
        return Optional.empty();
    }

    /** Every currency the inventory holds, with how many coins of each denomination. */
    public static List<Holding> holdings(Inventory inventory) {
        Map<UUID, int[]> counts = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            CoinData data = moneyData(stack);
            if (data == null) {
                continue;
            }
            int[] amounts = counts.computeIfAbsent(data.kingdomId(), ignored -> new int[3]);
            amounts[index(data.material())] += stack.getCount();
            names.put(data.kingdomId(), data.currencyName());
        }
        List<Holding> result = new ArrayList<>();
        counts.forEach((kingdomId, amounts) -> result.add(new Holding(kingdomId, names.get(kingdomId), amounts[0], amounts[1], amounts[2])));
        return result;
    }

    /**
     * Removes the coins of a payment from the inventory and returns them, with their
     * designs intact, ready to be handed to someone else.
     */
    public static List<ItemStack> take(Inventory inventory, UUID kingdomId, Payment payment) {
        List<ItemStack> taken = new ArrayList<>();
        takeOf(inventory, kingdomId, CoinData.Material.GOLD, payment.gold(), taken);
        takeOf(inventory, kingdomId, CoinData.Material.IRON, payment.iron(), taken);
        takeOf(inventory, kingdomId, CoinData.Material.COPPER, payment.copper(), taken);
        return taken;
    }

    private static void takeOf(Inventory inventory, UUID kingdomId, CoinData.Material material, int count, List<ItemStack> taken) {
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            CoinData data = moneyData(stack);
            if (data == null || !data.kingdomId().equals(kingdomId) || data.material() != material) {
                continue;
            }
            int amount = Math.min(remaining, stack.getCount());
            taken.add(stack.split(amount));
            remaining -= amount;
        }
        inventory.setChanged();
    }

    /** The coin's data if the stack is a real, minted coin, otherwise null. */
    private static CoinData moneyData(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        return data != null && data.shapeId() != CoinData.DEFAULT_SHAPE_ID ? data : null;
    }

    private static int index(CoinData.Material material) {
        return switch (material) {
            case GOLD -> 0;
            case IRON -> 1;
            case COPPER -> 2;
        };
    }
}
