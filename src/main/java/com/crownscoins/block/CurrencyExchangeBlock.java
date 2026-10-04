package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import com.crownscoins.menu.CurrencyExchangeMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.crownscoins.kingdom.Kingdom;
import com.crownscoins.kingdom.KingdomSavedData;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The exchange counter. Converting and melting coins is open to everyone. The shop tab belongs to the
 * kingdom of whoever placed it (or the first member to open it): its members set prices, everyone else buys.
 */
public final class CurrencyExchangeBlock extends BaseEntityBlock {
    private static final MapCodec<CurrencyExchangeBlock> CODEC = simpleCodec(CurrencyExchangeBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CurrencyExchangeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CurrencyExchangeBlockEntity(pos, state);
    }

    /** A kingdom member who places the table makes it that kingdom's shop. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player
                && serverLevel.getBlockEntity(pos) instanceof CurrencyExchangeBlockEntity exchange) {
            KingdomSavedData.get(serverLevel).findByMember(player.getUUID()).ifPresent(kingdom -> exchange.bind(kingdom.id()));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(serverLevel.getBlockEntity(pos) instanceof CurrencyExchangeBlockEntity exchange)) {
            return InteractionResult.FAIL;
        }

        KingdomSavedData kingdoms = KingdomSavedData.get(serverLevel);
        // A kingdom an administrator deleted leaves the shop pointing at nothing: free it.
        if (exchange.kingdomId().isPresent() && kingdoms.find(exchange.kingdomId().get()).isEmpty()) {
            exchange.bind(null);
        }
        // An unowned table becomes the shop of the first kingdom member who opens it.
        if (exchange.kingdomId().isEmpty()) {
            kingdoms.findByMember(serverPlayer.getUUID()).ifPresent(kingdom -> exchange.bind(kingdom.id()));
        }
        Kingdom owner = exchange.kingdomId().flatMap(kingdoms::find).orElse(null);
        boolean member = owner != null && owner.isMember(serverPlayer.getUUID());

        serverPlayer.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CurrencyExchangeMenu(containerId, inventory, serverLevel, pos),
                Component.translatable("menu.crownscoins.currency_exchange")
            ),
            buffer -> CurrencyExchangeMenu.writeOpeningData(buffer, pos, owner, member)
        );
        return InteractionResult.SUCCESS_SERVER;
    }
}
