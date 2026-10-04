package com.crownscoins.coin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShopPricingTest {
    @Test
    void paysExactlyWhenTheBuyerHasTheRightCoins() {
        ShopPricing.Settlement sale = ShopPricing.settle(22, 0, 1, 5, 0, 0, 0).orElseThrow();
        assertEquals(new CoinWallet.Payment(0, 1, 2), sale.payment());
        assertEquals(new CoinWallet.Payment(0, 0, 0), sale.change());
    }

    @Test
    void overpaysAndTheTillMakesChange() {
        // Price 5, the buyer has only one silver coin (20). The till holds 15 copper.
        ShopPricing.Settlement sale = ShopPricing.settle(5, 0, 1, 0, 0, 0, 15).orElseThrow();
        assertEquals(new CoinWallet.Payment(0, 1, 0), sale.payment());
        assertEquals(new CoinWallet.Payment(0, 0, 15), sale.change());
    }

    @Test
    void refusesWhenTheTillCannotMakeChange() {
        assertEquals(Optional.empty(), ShopPricing.settle(5, 0, 1, 0, 0, 0, 3));
    }

    @Test
    void refusesWhenTheBuyerCannotPayEnough() {
        assertEquals(Optional.empty(), ShopPricing.settle(100, 0, 1, 5, 0, 0, 0));
    }

    @Test
    void aGoldCoinForACheapItemNeedsChangeInSmallerCoins() {
        // Price 1, one gold coin (500): 499 change = 24 silver + 19 copper.
        ShopPricing.Settlement sale = ShopPricing.settle(1, 1, 0, 0, 0, 30, 30).orElseThrow();
        assertEquals(new CoinWallet.Payment(1, 0, 0), sale.payment());
        assertEquals(499, sale.change().value());
    }

    @Test
    void paymentMinusChangeIsAlwaysThePrice() {
        for (int price = 1; price <= 60; price++) {
            Optional<ShopPricing.Settlement> sale = ShopPricing.settle(price, 1, 3, 4, 2, 6, 40);
            if (sale.isPresent()) {
                assertEquals(price, sale.get().payment().value() - sale.get().change().value());
            }
        }
    }

    @Test
    void rejectsAFreePrice() {
        assertEquals(Optional.empty(), ShopPricing.settle(0, 5, 5, 5, 5, 5, 5));
    }

    @Test
    void splitsAValueIntoTheFewestCoins() {
        assertEquals(new CoinWallet.Payment(1, 2, 3), ShopPricing.split(543));
        assertTrue(ShopPricing.split(0).value() == 0);
    }
}
