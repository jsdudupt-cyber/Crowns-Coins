package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import com.crownscoins.CrownsCoinsConfig;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Stops players who do not belong to a kingdom from breaking its Mint House, which
 * would otherwise drop the coins and nuggets inside to whoever broke it.
 *
 * <p>Explosions and pistons are handled by the block's own properties (it is
 * blast-proof and cannot be pushed). Operators can always break it, so an
 * administrator can clean up an abandoned station.</p>
 */
@EventBusSubscriber(modid = CrownsCoins.MOD_ID)
public final class MintHouseProtection {
    private MintHouseProtection() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        // The event also fires on the client, where the server config may not be loaded: decide the side first.
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!CrownsCoinsConfig.PROTECT_MINT_HOUSE.get()) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(CrownsCoins.MINT_HOUSE.get())) {
            return;
        }

        // The controller (and its chest) lives in the foot half; either half can be targeted.
        BlockPos footPos = state.getValue(MintHouseBlock.PART) == BedPart.FOOT
            ? event.getPos()
            : event.getPos().relative(state.getValue(MintHouseBlock.FACING).getCounterClockWise());
        if (!(level.getBlockEntity(footPos) instanceof MintHouseBlockEntity mintHouse)) {
            return;
        }
        Optional<UUID> kingdomId = mintHouse.kingdomId();
        if (kingdomId.isEmpty()) {
            return;
        }
        Optional<Kingdom> kingdom = KingdomSavedData.get(level).find(kingdomId.get());
        if (kingdom.isEmpty() || kingdom.get().isMember(player.getUUID()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }

        event.setCanceled(true);
        // The check runs on the server only, so tell the client the block is still there.
        event.setNotifyClient(true);
        player.sendSystemMessage(Component.translatable("message.crownscoins.protected", kingdom.get().name()));
    }
}
