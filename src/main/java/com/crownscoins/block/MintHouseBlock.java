package com.crownscoins.block;

import com.mojang.serialization.MapCodec;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.menu.KingdomCreationMenu;
import com.crownscoins.menu.MintHouseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class MintHouseBlock extends BaseEntityBlock {
    private static final MapCodec<MintHouseBlock> CODEC = simpleCodec(MintHouseBlock::new);
    public MintHouseBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MintHouseBlockEntity(pos, state); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(serverLevel.getBlockEntity(pos) instanceof MintHouseBlockEntity mintHouse)) return InteractionResult.FAIL;
        var kingdoms = KingdomSavedData.get(serverLevel);
        Kingdom boundKingdom;
        if (mintHouse.kingdomId().isEmpty()) {
            var owned = kingdoms.findByMember(serverPlayer.getUUID());
            if (owned.isPresent()) {
                if (!owned.get().isFounder(serverPlayer.getUUID())) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.crownscoins.already_member"));
                    return InteractionResult.FAIL;
                }
                mintHouse.bind(owned.get().id());
                serverPlayer.sendSystemMessage(Component.translatable("message.crownscoins.mint_bound"));
                boundKingdom = owned.get();
            } else {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new KingdomCreationMenu(id, serverLevel, pos), Component.translatable("menu.crownscoins.create_kingdom")), pos);
                return InteractionResult.SUCCESS_SERVER;
            }
        } else {
            var existing = mintHouse.kingdomId().flatMap(kingdoms::find);
            if (existing.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.crownscoins.invalid_mint"));
                return InteractionResult.FAIL;
            }
            boundKingdom = existing.get();
        }
        serverPlayer.openMenu(
            new SimpleMenuProvider((id, inventory, ignored) -> new MintHouseMenu(id, inventory, serverLevel, pos), Component.translatable("menu.crownscoins.mint")),
            buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeUtf(boundKingdom.name(), Kingdom.MAX_KINGDOM_NAME_LENGTH);
                buffer.writeVarInt(boundKingdom.crest().id());
                buffer.writeVarInt(boundKingdom.ironValue());
                buffer.writeVarInt(boundKingdom.copperValue());
                buffer.writeVarInt(boundKingdom.goldValue());
            }
        );
        return InteractionResult.SUCCESS_SERVER;
    }
}
