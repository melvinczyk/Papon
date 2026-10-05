package com.groid.papon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A cluster of ofrenda candles, tall and short, wax run down them, all burning (El Coliseo de los Muertos). */
public class MuertosCandlesBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 11, 14);
    /** The wicks (model pixels: x, top, z), where the flames are (tools/gen_muertos.py candles). */
    private static final double[][] WICKS = {{5, 11, 5}, {11, 8, 6}, {6, 7, 11}, {11, 9.5, 11}, {8.5, 5.5, 8.5}};

    public MuertosCandlesBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (double[] w : WICKS) {
            if (random.nextInt(3) != 0) continue;
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + w[0] / 16, pos.getY() + w[1] / 16 + 0.06, pos.getZ() + w[2] / 16, 0, 0.004, 0);
        }
        if (random.nextInt(10) == 0) {
            double[] w = WICKS[random.nextInt(WICKS.length)];
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + w[0] / 16, pos.getY() + w[1] / 16 + 0.2, pos.getZ() + w[2] / 16, 0, 0.02, 0);
        }
    }
}
