package com.groid.papon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A bone-and-iron brazier of El Coliseo de los Muertos burning spirit fire: blue flames, souls rising off it. */
public class SpiritBrazierBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 1, 12), Block.box(7, 1, 7, 9, 7, 9),
            Block.box(3, 7, 3, 13, 9, 13), Block.box(1, 9, 1, 15, 13, 15));

    public SpiritBrazierBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.85, z = pos.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.6, y + random.nextDouble() * 0.2,
                    z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03 + random.nextDouble() * 0.04, 0);
        }
        if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SOUL, x, y + 0.4, z, 0, 0.05, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.6, z, 0, 0.05, 0);
        if (random.nextInt(40) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.4F, 0.6F + random.nextFloat() * 0.3F, false);
        }
    }
}
