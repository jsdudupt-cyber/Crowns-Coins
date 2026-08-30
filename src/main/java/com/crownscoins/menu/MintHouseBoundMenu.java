package com.crownscoins.menu;

import com.crownscoins.CrownsCoins;
import com.crownscoins.block.MintHouseBlockEntity;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

/** Shared server-side validity checks for menus that target one exact Mint House. */
abstract class MintHouseBoundMenu extends AbstractContainerMenu {
    /** The standard eight-block interaction distance, squared. */
    protected static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    private final ResourceKey<Level> dimension;
    private final BlockPos mintHousePos;

    protected MintHouseBoundMenu(MenuType<?> menuType, int containerId, ResourceKey<Level> dimension, BlockPos mintHousePos) {
        super(menuType, containerId);
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.mintHousePos = Objects.requireNonNull(mintHousePos, "mintHousePos").immutable();
    }

    public final ResourceKey<Level> dimension() {
        return dimension;
    }

    public final BlockPos mintHousePos() {
        return mintHousePos;
    }

    /**
     * Validates the live world state without loading a chunk. This must remain true
     * for every action performed through the menu, not only when it is opened.
     */
    @Override
    public boolean stillValid(Player player) {
        if (!player.isAlive() || !player.level().dimension().equals(dimension) || !player.level().hasChunkAt(mintHousePos)) {
            return false;
        }
        if (!player.level().getBlockState(mintHousePos).is(CrownsCoins.MINT_HOUSE.get())) {
            return false;
        }
        if (!(player.level().getBlockEntity(mintHousePos) instanceof MintHouseBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
            mintHousePos.getX() + 0.5D,
            mintHousePos.getY() + 0.5D,
            mintHousePos.getZ() + 0.5D
        ) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    /** Only the exact currently-open server menu may authorize a packet. */
    protected final boolean isCurrentAndValid(ServerPlayer player) {
        return player.containerMenu == this && stillValid(player);
    }

    /**
     * Returns the actual block entity only after the full live-menu validation.
     * Never retain a block-entity reference across requests; it can be replaced.
     */
    protected final Optional<MintHouseBlockEntity> currentMintHouse(ServerPlayer player) {
        if (!isCurrentAndValid(player)) {
            return Optional.empty();
        }
        return player.level().getBlockEntity(mintHousePos) instanceof MintHouseBlockEntity mintHouse
            ? Optional.of(mintHouse)
            : Optional.empty();
    }
}
