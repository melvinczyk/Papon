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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bronze brazier on the coliseum's walls: a bowl of coals and a fire that never goes out... except when Chef
 * Papon wants the dark (phase three: they go out round the bowl one by one, {@link #LIT} false) and when it
 * comes back for his last dance, crimson ({@link #CRIMSON}).
 */
public class ArenaBrazierBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty CRIMSON = BooleanProperty.create("crimson");

    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 1, 12), Block.box(7, 1, 7, 9, 7, 9),
            Block.box(3, 7, 3, 13, 9, 13), Block.box(1, 9, 1, 15, 13, 15));

    public ArenaBrazierBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(CRIMSON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, CRIMSON);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.85, z = pos.getZ() + 0.5;
        if (!state.getValue(LIT)) {
            // out: a thread of smoke off the ash
            if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
            return;
        }
        if (state.getValue(CRIMSON)) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.FIRE.get(), -1, 0.55F + random.nextFloat() * 0.3F,
                        1.4F, 0xFF2A34, 1F, 14 + random.nextInt(8), 0), x + (random.nextDouble() - 0.5) * 0.6, y + random.nextDouble() * 0.2,
                        z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03 + random.nextDouble() * 0.03, 0);
            }
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.6, z, 0, 0.05, 0);
            if (random.nextInt(40) == 0) {
                level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.6F + random.nextFloat() * 0.2F, false);
            }
            return;
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.6, y + random.nextDouble() * 0.2,
                    z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02 + random.nextDouble() * 0.03, 0);
        }
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.6, z, 0, 0.05, 0);
        if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        if (random.nextInt(40) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }
}
