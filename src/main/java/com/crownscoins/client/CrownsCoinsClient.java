package com.crownscoins.client;

import com.crownscoins.CrownsCoins;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

/** Client-only registration keeps GUI classes out of dedicated servers. */
@EventBusSubscriber(modid = CrownsCoins.MOD_ID, value = Dist.CLIENT)
public final class CrownsCoinsClient {
    private CrownsCoinsClient() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CrownsCoins.KINGDOM_CREATION_MENU.get(), KingdomCreationScreen::new);
        event.register(CrownsCoins.MINT_FURNACE_MENU.get(), MintFurnaceScreen::new);
        event.register(CrownsCoins.MINT_HOUSE_MENU.get(), MintHouseScreen::new);
        event.register(CrownsCoins.CURRENCY_EXCHANGE_MENU.get(), CurrencyExchangeScreen::new);
    }

    @SubscribeEvent
    public static void registerCoinTooltip(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderAfterAll(
            CrownsCoins.COIN_DATA.get(),
            TooltipAppender.createComponentAppender(CrownsCoins.COIN_DATA.get())
        );
    }

    @SubscribeEvent
    public static void registerCoinModelProperties(RegisterSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, "coin_shape"), CoinDataSelectProperty.SHAPE_TYPE);
    }
}
