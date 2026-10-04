package com.crownscoins.coin;

import com.crownscoins.kingdom.Kingdom;
import java.util.Optional;

/**
 * The arithmetic of a shop sale, kept free of Minecraft types so it can be unit tested.
 *
 * <p>Prices are whole copper-coin units (a silver coin is 20, a gold coin 500). A buyer
 * pays with the coins they carry, overpaying when they have no exact combination, and the
 * shop's till makes the change.</p>
 */
public final class ShopPricing {
    /** The highest price an owner can set; keeps the value inside a synced 16-bit number. */
    public static final int MAX_PRICE = 30_000;
    /** The most a buyer ever overpays: a single gold coin for a one-copper item. */
    private static final int MAX_OVERPAY = Kingdom.GOLD_COIN_VALUE;

    private ShopPricing() {
    }

    /** What changes hands in one sale. */
    public record Settlement(CoinWallet.Payment payment, CoinWallet.Payment change) {
    }

    /**
     * Finds the smallest payment that covers {@code price} with the buyer's coins, and the
     * change the till can give back. The buyer's payment goes into the till first, so it
     * can be part of its own change. Empty when the buyer cannot pay or the till cannot
     * make the change.
     */
    public static Optional<Settlement> settle(
        long price,
        int gold, int iron, int copper,
        int tillGold, int tillIron, int tillCopper
    ) {
        if (price <= 0) {
            return Optional.empty();
        }
        for (long total = price; total <= price + MAX_OVERPAY; total++) {
            Optional<CoinWallet.Payment> payment = CoinWallet.plan(total, gold, iron, copper);
            if (payment.isEmpty()) {
                continue;
            }
            long changeValue = total - price;
            if (changeValue == 0) {
                return Optional.of(new Settlement(payment.get(), new CoinWallet.Payment(0, 0, 0)));
            }
            CoinWallet.Payment paid = payment.get();
            Optional<CoinWallet.Payment> change = CoinWallet.plan(
                changeValue,
                tillGold + paid.gold(),
                tillIron + paid.iron(),
                tillCopper + paid.copper()
            );
            if (change.isPresent()) {
                return Optional.of(new Settlement(paid, change.get()));
            }
        }
        return Optional.empty();
    }

    /** Splits a copper-unit value into the fewest coins. */
    public static CoinWallet.Payment split(long value) {
        long gold = value / Kingdom.GOLD_COIN_VALUE;
        long rest = value % Kingdom.GOLD_COIN_VALUE;
        long iron = rest / Kingdom.IRON_COIN_VALUE;
        return new CoinWallet.Payment((int) gold, (int) iron, (int) (rest % Kingdom.IRON_COIN_VALUE));
    }
}
