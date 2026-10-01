package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import com.mojang.serialization.MapCodec;
import com.crownscoins.kingdom.KingdomSavedData;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.menu.KingdomCreationMenu;
import com.crownscoins.menu.MintFurnaceMenu;
import com.crownscoins.menu.MintHouseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class MintHouseBlock extends BaseEntityBlock {
    private static final MapCodec<MintHouseBlock> CODEC = simpleCodec(MintHouseBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The functional controller is stored in the FOOT half; both physical halves open it. */
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

    public MintHouseBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? new MintHouseBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> blockEntityType
    ) {
        return state.getValue(PART) == BedPart.FOOT && !level.isClientSide()
            ? createTickerHelper(blockEntityType, CrownsCoins.MINT_HOUSE_ENTITY.get(), MintHouseBlockEntity::serverTick)
            : null;
    }

    /** Faces its decorated press panel toward the player who places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos rightHalf = context.getClickedPos().relative(facing.getClockWise());
        return context.getLevel().getBlockState(rightHalf).canBeReplaced(context)
            ? this.defaultBlockState().setValue(FACING, facing).setValue(PART, BedPart.FOOT)
            : null;
    }

    /** Places the companion half after the main half is placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide()) {
            level.setBlock(pos.relative(state.getValue(FACING).getClockWise()), state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
        }
    }

    /** Keeps both physical halves together, including explosions and survival mining. */
    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighbourPos,
        BlockState neighbour,
        RandomSource random
    ) {
        if (direction == companionDirection(state)) {
            if (neighbour.is(this)
                && neighbour.getValue(PART) != state.getValue(PART)
                && neighbour.getValue(FACING) == state.getValue(FACING)) {
                return state;
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighbourPos, neighbour, random);
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
        builder.add(FACING, PART);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos mintPos = mainHalfPos(pos, state);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(serverLevel.getBlockEntity(mintPos) instanceof MintHouseBlockEntity mintHouse)) return InteractionResult.FAIL;

        // The left half is a simple nugget furnace. It is usable before binding,
        // but it waits to smelt until the right-hand press has a kingdom owner.
        if (state.getValue(PART) == BedPart.HEAD) {
            serverPlayer.openMenu(
                new SimpleMenuProvider(
                    (id, inventory, ignored) -> new MintFurnaceMenu(id, inventory, serverLevel, mintPos),
                    Component.literal("Fornalha de Moedas")
                ),
                buffer -> buffer.writeBlockPos(mintPos)
            );
            return InteractionResult.SUCCESS_SERVER;
        }

        // The right-hand press owns the kingdom association, currency name and designs.
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
                    serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new KingdomCreationMenu(id, serverLevel, mintPos), Component.translatable("menu.crownscoins.create_kingdom")), mintPos);
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
            new SimpleMenuProvider(
                (id, inventory, ignored) -> new MintHouseMenu(
                    id,
                    inventory,
                    serverLevel,
                    mintPos,
                    true
                ),
                Component.translatable("menu.crownscoins.mint")
            ),
            buffer -> {
                buffer.writeBlockPos(mintPos);
                buffer.writeUtf(boundKingdom.name(), Kingdom.MAX_KINGDOM_NAME_LENGTH);
                buffer.writeUtf(boundKingdom.currencyName(), Kingdom.MAX_CURRENCY_NAME_LENGTH);
                buffer.writeVarInt(boundKingdom.crest().id());
                buffer.writeBoolean(boundKingdom.isFounder(serverPlayer.getUUID()));
                buffer.writeVarInt(boundKingdom.ironValue());
                buffer.writeVarInt(boundKingdom.copperValue());
                buffer.writeVarInt(boundKingdom.goldValue());
                buffer.writeBoolean(true);
            }
        );
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Direction companionDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == BedPart.FOOT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private static BlockPos mainHalfPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? pos : pos.relative(state.getValue(FACING).getCounterClockWise());
    }
}
