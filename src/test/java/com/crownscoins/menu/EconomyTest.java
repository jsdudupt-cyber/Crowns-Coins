package com.crownscoins.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import org.junit.jupiter.api.Test;

class EconomyTest {
    @Test
    void mintingCostsAreTwoFiveAndSevenNuggets() {
        assertEquals(2, MintHouseMenu.nuggetsPerCoin(Kingdom.Metal.COPPER));
        assertEquals(5, MintHouseMenu.nuggetsPerCoin(Kingdom.Metal.IRON));
        assertEquals(7, MintHouseMenu.nuggetsPerCoin(Kingdom.Metal.GOLD));
    }

    @Test
    void meltingReturnsExactlyWhatMintingCosts() {
        assertEquals(MintHouseMenu.nuggetsPerCoin(Kingdom.Metal.COPPER), CurrencyExchangeMenu.nuggetsPerCoin(CoinData.Material.COPPER));
        assertEquals(MintHouseMenu.nuggetsPerCoin(Kingdom.Metal.IRON), CurrencyExchangeMenu.nuggetsPerCoin(CoinData.Material.IRON));
        assertEquals(MintHouseMenu.nuggetsPerCoin(Kingdom.Metal.GOLD), CurrencyExchangeMenu.nuggetsPerCoin(CoinData.Material.GOLD));
    }

    @Test
    void denominationsAreTwentyAndTwentyFiveApart() {
        assertEquals(20, Kingdom.IRON_COIN_VALUE / Kingdom.COPPER_COIN_VALUE);
        assertEquals(25, Kingdom.GOLD_COIN_VALUE / Kingdom.IRON_COIN_VALUE);
        assertEquals(CurrencyExchangeMenu.COPPER_TO_IRON_COUNT, Kingdom.IRON_COIN_VALUE / Kingdom.COPPER_COIN_VALUE);
        assertEquals(CurrencyExchangeMenu.IRON_TO_GOLD_COUNT, Kingdom.GOLD_COIN_VALUE / Kingdom.IRON_COIN_VALUE);
    }
}
