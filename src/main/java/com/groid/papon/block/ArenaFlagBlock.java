package com.groid.papon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A stretch of the coliseum's wall with La Bandera Papa hung on it (tools/gen_flag.py): the block is
 * plain ashlar; its block entity knows which way the wall faces and what kind of flag it carries, and
 * client/ArenaFlagRenderer draws the cloth waving in front of it. Unbreakable like the rest.
 */
public class ArenaFlagBlock extends Block implements EntityBlock {
    public ArenaFlagBlock(Properties props) { super(props); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ArenaFlagBlockEntity(pos, state); }
}
