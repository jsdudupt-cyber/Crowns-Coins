package com.crownscoins;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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
import org.slf4j.Logger;

/** Entry point and content registry for Crowns & Coins. */
@Mod(CrownsCoins.MOD_ID)
public final class CrownsCoins {
    public static final String MOD_ID = "crownscoins";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<MintHouseBlock> MINT_HOUSE = BLOCKS.register("mint_house", () -> new MintHouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MintHouseBlockEntity>> MINT_HOUSE_ENTITY = BLOCK_ENTITIES.register("mint_house", () -> new BlockEntityType<>(MintHouseBlockEntity::new, false, MINT_HOUSE.get()));
    public static final DeferredItem<BlockItem> MINT_HOUSE_ITEM = ITEMS.registerSimpleBlockItem("mint_house", MINT_HOUSE);
    public static final DeferredItem<Item> IRON_COIN = ITEMS.registerSimpleItem("iron_coin", p -> p.stacksTo(64));
    public static final DeferredItem<Item> COPPER_COIN = ITEMS.registerSimpleItem("copper_coin", p -> p.stacksTo(64));
    public static final DeferredItem<Item> GOLD_COIN = ITEMS.registerSimpleItem("gold_coin", p -> p.stacksTo(64));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crownscoins"))
            .icon(() -> GOLD_COIN.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MINT_HOUSE_ITEM.get());
                output.accept(IRON_COIN.get());
                output.accept(COPPER_COIN.get());
                output.accept(GOLD_COIN.get());
            }).build());

    public CrownsCoins(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        TABS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
