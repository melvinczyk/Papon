package com.groid.papon.block;

import com.groid.papon.boss.Mechanics;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.fx.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La Freidora: the coliseum's sand gives way to a vat of frying oil (boss/Freidora). Step in and you are
 * deep-fried: a share of your health through armor (it is oil, not fire: fire immunity does not help),
 * flung up out of it, slowed. Chef Papon does not mind it. Put back to sand when the fryer closes.
 */
public class FryingOilBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 7, 16);

    public FryingOilBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FLOOR;                    // you sink into it
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity e) || e instanceof PaponBossEntity) return;
        if (entity.getY() > pos.getY() + 0.75) return;
        long now = level.getGameTime();
        var tag = e.getPersistentData();
        if (now - tag.getLong("PaponFried") < 14) return;
        tag.putLong("PaponFried", now);
        if (Mechanics.trueHit(e, 0.10F, null, true)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.3, 1.0, v.z * 0.3);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
            Fx.spark(server, e.position().add(0, 0.4, 0), 14, 0.6, 0.35F, 0xFFC84A);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.4F, 1.3F);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + 0.78, z = pos.getZ() + random.nextDouble();
        if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.04, 0);
        if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.WHITE_ASH, x, y + 0.1, z, 0, 0.05, 0);
        if (random.nextInt(60) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.4F, 1.6F + random.nextFloat() * 0.3F, false);
        }
    }
}
