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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ectoplasm: the glowing blue running in the cracks of the floor of El Coliseo de los Muertos (boss/Coliseum.cracks), a
 * little below the tiles either side, swirling (an animated texture) and giving off light and the odd soul. Harmless in
 * itself; Las Velas de Animas erupt out of it.
 */
public class EctoplasmBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public EctoplasmBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(9) == 0) {
            level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(), 0, 0.015 + random.nextDouble() * 0.02, 0);
        }
        if (random.nextInt(400) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS,
                    0.5F, 0.7F + random.nextFloat() * 0.4F, false);
        }
    }
}
