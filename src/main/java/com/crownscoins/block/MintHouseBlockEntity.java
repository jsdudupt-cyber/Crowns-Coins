package com.crownscoins.block;

import com.crownscoins.CrownsCoins;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The exact Mint House binding, stored with the block rather than the client. */
public final class MintHouseBlockEntity extends BlockEntity {
    private UUID kingdomId;
    public MintHouseBlockEntity(BlockPos pos, BlockState state) { super(CrownsCoins.MINT_HOUSE_ENTITY.get(), pos, state); }
    public Optional<UUID> kingdomId() { return Optional.ofNullable(kingdomId); }
    public void bind(UUID id) { kingdomId = id; setChanged(); }
    @Override public void loadAdditional(ValueInput input) { super.loadAdditional(input); kingdomId = input.read("kingdom_id", UUIDUtil.CODEC).orElse(null); }
    @Override public void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.storeNullable("kingdom_id", UUIDUtil.CODEC, kingdomId); }
}
