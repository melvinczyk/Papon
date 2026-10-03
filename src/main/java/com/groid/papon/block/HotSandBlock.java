package com.groid.papon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** La Freidora's warning: sand about to give way, cracked, glowing from the oil under it (boss/Freidora). */
public class HotSandBlock extends Block {
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.12F), 1.2F);

    public HotSandBlock(Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(EMBER, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
