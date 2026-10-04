package com.crownscoins.coin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoinWalletTest {
    @Test
    void usesTheLargestCoinsFirst() {
        CoinWallet.Payment payment = CoinWallet.plan(521, 2, 5, 9).orElseThrow();
        assertEquals(new CoinWallet.Payment(1, 1, 1), payment);
        assertEquals(521, payment.value());
    }

    @Test
    void fallsBackToSmallerCoinsWhenTheBigOnesDoNotFit() {
        // 500 would need a gold coin the payer does not have: 25 iron coins pay it exactly.
        assertEquals(new CoinWallet.Payment(0, 25, 0), CoinWallet.plan(500, 0, 40, 0).orElseThrow());
        // 30 = one iron + ten copper.
        assertEquals(new CoinWallet.Payment(0, 1, 10), CoinWallet.plan(30, 0, 1, 12).orElseThrow());
    }

    @Test
    void givesUpAGoldCoinWhenTheRemainderCannotBeFormed() {
        // 505 with one gold, no iron and only 3 copper: the gold coin would leave 5 copper,
        // which cannot be paid. There is no other way, so the payment fails.
        assertEquals(Optional.empty(), CoinWallet.plan(505, 1, 0, 3));
        // With enough copper the same gold coin works.
        assertEquals(new CoinWallet.Payment(1, 0, 5), CoinWallet.plan(505, 1, 0, 5).orElseThrow());
    }

    @Test
    void thereIsNoChange() {
        // One gold coin cannot pay 20: no change is ever given.
        assertEquals(Optional.empty(), CoinWallet.plan(20, 1, 0, 0));
        assertEquals(Optional.empty(), CoinWallet.plan(1, 0, 3, 0));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertTrue(CoinWallet.plan(0, 5, 5, 5).isEmpty());
        assertTrue(CoinWallet.plan(-3, 5, 5, 5).isEmpty());
    }

    @Test
    void holdingValueAddsEveryDenomination() {
        assertEquals(500 + 2 * 20 + 3, new CoinWallet.Holding(java.util.UUID.randomUUID(), "Florins", 1, 2, 3).value());
    }
}
