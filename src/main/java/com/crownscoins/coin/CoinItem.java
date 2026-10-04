package com.crownscoins.coin;

import com.crownscoins.CrownsCoins;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A minted coin. Its title is the currency's current name, so renaming a kingdom's
 * currency also renames the coins already minted.
 */
public final class CoinItem extends Item {
    public CoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        return data != null ? Component.literal(KingdomNames.currencyName(data)) : super.getName(stack);
    }

    /**
     * Older coins were minted with their currency name baked in as a custom name, which
     * would hide the current one. While a player carries such a coin the baked name is
     * dropped. A name a player gave the coin themselves (for example on an anvil) differs
     * from the baked one and is left alone.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        CoinData data = stack.get(CrownsCoins.COIN_DATA.get());
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (data != null && custom != null && custom.getString().equals(data.currencyName())) {
            stack.remove(DataComponents.CUSTOM_NAME);
        }
    }
}
