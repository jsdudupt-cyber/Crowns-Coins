package com.crownscoins;

import com.mojang.logging.LogUtils;
import com.crownscoins.coin.CoinData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.Symbol;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.crownscoins.block.MintHouseBlock;
import com.crownscoins.block.MintHouseBlockEntity;
import com.crownscoins.block.CurrencyExchangeBlock;
import com.crownscoins.network.NetworkHandler;
import com.crownscoins.menu.CurrencyExchangeMenu;
import com.crownscoins.menu.KingdomCreationMenu;
import com.crownscoins.menu.MintFurnaceMenu;
import com.crownscoins.menu.MintHouseMenu;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.slf4j.Logger;

/** Entry point and content registry for Crowns & Coins. */
@Mod(CrownsCoins.MOD_ID)
public final class CrownsCoins {
    public static final String MOD_ID = "crownscoins";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Stable, non-player-owned provenance for the sample coins in the creative tab. */
    private static final UUID CREATIVE_SAMPLE_KINGDOM_ID = new UUID(0L, 1L);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final DeferredBlock<MintHouseBlock> MINT_HOUSE = BLOCKS.registerBlock(
        "mint_house",
        MintHouseBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F)
    );
    /** Public station for exchanging and melting already-minted currency. */
    public static final DeferredBlock<CurrencyExchangeBlock> CURRENCY_EXCHANGE = BLOCKS.registerBlock(
        "currency_exchange",
        CurrencyExchangeBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MintHouseBlockEntity>> MINT_HOUSE_ENTITY = BLOCK_ENTITIES.register("mint_house", () -> new BlockEntityType<>(MintHouseBlockEntity::new, false, MINT_HOUSE.get()));
    public static final DeferredItem<BlockItem> MINT_HOUSE_ITEM = ITEMS.registerSimpleBlockItem("mint_house", MINT_HOUSE);
    public static final DeferredItem<BlockItem> CURRENCY_EXCHANGE_ITEM = ITEMS.registerSimpleBlockItem("currency_exchange", CURRENCY_EXCHANGE);
    public static final DeferredItem<Item> IRON_COIN = ITEMS.registerSimpleItem("iron_coin", p -> p.stacksTo(64));
    public static final DeferredItem<Item> COPPER_COIN = ITEMS.registerSimpleItem("copper_coin", p -> p.stacksTo(64));
    public static final DeferredItem<Item> GOLD_COIN = ITEMS.registerSimpleItem("gold_coin", p -> p.stacksTo(64));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoinData>> COIN_DATA = DATA_COMPONENTS.registerComponentType(
        "coin_data",
        builder -> builder.persistent(CoinData.CODEC).networkSynchronized(CoinData.STREAM_CODEC).cacheEncoding()
    );
    public static final DeferredHolder<MenuType<?>, MenuType<KingdomCreationMenu>> KINGDOM_CREATION_MENU = MENUS.register("kingdom_creation", () -> IMenuTypeExtension.create(KingdomCreationMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MintFurnaceMenu>> MINT_FURNACE_MENU = MENUS.register("mint_furnace", () -> IMenuTypeExtension.create(MintFurnaceMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MintHouseMenu>> MINT_HOUSE_MENU = MENUS.register("mint_house", () -> IMenuTypeExtension.create(MintHouseMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<CurrencyExchangeMenu>> CURRENCY_EXCHANGE_MENU = MENUS.register("currency_exchange", () -> IMenuTypeExtension.create(CurrencyExchangeMenu::new));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crownscoins"))
            .icon(() -> creativeCoin(GOLD_COIN.get(), CoinData.Material.GOLD, Kingdom.GOLD_COIN_VALUE, 1))
            .displayItems((parameters, output) -> {
                output.accept(MINT_HOUSE_ITEM.get());
                output.accept(CURRENCY_EXCHANGE_ITEM.get());
                addCreativeCoinShapes(output, COPPER_COIN.get(), CoinData.Material.COPPER, Kingdom.COPPER_COIN_VALUE);
                addCreativeCoinShapes(output, IRON_COIN.get(), CoinData.Material.IRON, Kingdom.IRON_COIN_VALUE);
                addCreativeCoinShapes(output, GOLD_COIN.get(), CoinData.Material.GOLD, Kingdom.GOLD_COIN_VALUE);
            }).build());

    /**
     * Adds one safe, fully populated sample for every minted shape.  Coin models use
     * {@link CoinData} to select their appearance, so plain default stacks would show
     * the obsolete fallback texture in the creative inventory.
     */
    private static void addCreativeCoinShapes(
        CreativeModeTab.Output output,
        Item item,
        CoinData.Material material,
        int value
    ) {
        for (int shapeId = 1; shapeId <= CoinData.MAX_SHAPE_ID; shapeId++) {
            output.accept(creativeCoin(item, material, value, shapeId));
        }
    }

    private static net.minecraft.world.item.ItemStack creativeCoin(
        Item item,
        CoinData.Material material,
        int value,
        int shapeId
    ) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        stack.set(COIN_DATA.get(), new CoinData(
            CREATIVE_SAMPLE_KINGDOM_ID,
            "Reino de Exemplo",
            "Moeda de Exemplo",
            material,
            value,
            shapeId
        ));
        return stack;
    }

    public CrownsCoins(IEventBus eventBus) {
        eventBus.addListener(NetworkHandler::register);
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        TABS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        DATA_COMPONENTS.register(eventBus);
    }
}
