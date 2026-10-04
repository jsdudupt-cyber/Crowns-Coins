package com.crownscoins;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the mod's advancements that no vanilla trigger can detect (founding a
 * kingdom, minting, adding a member, paying). The two that depend on holding an
 * item are plain {@code inventory_changed} advancements in the data pack.
 */
public final class ModAdvancements {
    public static final String KINGDOM = "kingdom";
    public static final String FIRST_MINT = "first_mint";
    public static final String MEMBERS = "members";
    public static final String PAY = "pay";

    private static final String CRITERION = "triggered";

    private ModAdvancements() {
    }

    /** Awards a manual advancement; does nothing if it is missing or already earned. */
    public static void award(ServerPlayer player, String name) {
        AdvancementHolder holder = player.level().getServer().getAdvancements()
            .get(Identifier.fromNamespaceAndPath(CrownsCoins.MOD_ID, name));
        if (holder != null) {
            player.getAdvancements().award(holder, CRITERION);
        }
    }
}
