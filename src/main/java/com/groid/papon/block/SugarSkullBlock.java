package com.groid.papon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A sugar skull set in the walls of El Coliseo de los Muertos (boss/Coliseum): bone, its face painted (black sockets
 * ringed with petals, a flower on the brow, a stitched grin) on its {@link #FACING} side, its eyes glowing ectoplasm blue.
 */
public class SugarSkullBlock extends HorizontalDirectionalBlock {
    public SugarSkullBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(14) != 0) return;
        var f = state.getValue(FACING);
        level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + f.getStepX() * 0.6, pos.getY() + 0.6, pos.getZ() + 0.5 + f.getStepZ() * 0.6,
                0, 0.02, 0);
    }
}
