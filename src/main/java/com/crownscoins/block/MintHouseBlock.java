package com.crownscoins.block;

import com.mojang.serialization.MapCodec;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.menu.KingdomCreationMenu;
import com.crownscoins.menu.MintHouseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class MintHouseBlock extends BaseEntityBlock {
    private static final MapCodec<MintHouseBlock> CODEC = simpleCodec(MintHouseBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MintHouseBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MintHouseBlockEntity(pos, state); }

    /** Faces its decorated press panel toward the player who places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

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
                buffer.writeUtf(boundKingdom.currencyName(), Kingdom.MAX_CURRENCY_NAME_LENGTH);
                buffer.writeVarInt(boundKingdom.crest().id());
                buffer.writeBoolean(boundKingdom.isFounder(serverPlayer.getUUID()));
                buffer.writeVarInt(boundKingdom.ironValue());
                buffer.writeVarInt(boundKingdom.copperValue());
                buffer.writeVarInt(boundKingdom.goldValue());
            }
        );
        return InteractionResult.SUCCESS_SERVER;
    }
}
