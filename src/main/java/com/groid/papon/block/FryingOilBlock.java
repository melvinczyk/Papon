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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La Freidora: the coliseum's sand gives way to a vat of frying oil two blocks deep (boss/Freidora). Fall in
 * and you are deep-fried: a share of your health on the way in and a share every tick you stay in it, through
 * armor (it is oil, not fire: fire immunity does not help; it hurts the fireproof half again as much, as
 * their resistance boils off). The boil throws you up now and then: that is the way out. Chef Papon does
 * not mind it. Put back to sand when the fryer closes.
 */
public class FryingOilBlock extends Block {
    /** Share of maximum health a tick in the oil (x {@link #FIREPROOF} for the fire-immune), and on the way in. */
    public static final float PER_TICK = 0.0125F, DUNK = 0.08F, FIREPROOF = 1.5F;
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
        // you sink into it: through the top layer to the bottom of the vat
        return level.getBlockState(pos.below()).is(this) ? Shapes.empty() : FLOOR;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity e) || e instanceof PaponBossEntity) return;
        boolean deep = level.getBlockState(pos.below()).is(this);
        if (entity.getY() > pos.getY() + (deep ? 1.0 : 0.75)) return;
        long now = level.getGameTime();
        var tag = e.getPersistentData();
        long last = tag.getLong("PaponOilTick");
        if (last == now) return;                        // both layers of the vat: once a tick
        tag.putLong("PaponOilTick", now);
        boolean fireproof = e.fireImmune() || e.hasEffect(MobEffects.FIRE_RESISTANCE);
        float k = fireproof ? FIREPROOF : 1F;
        boolean dunk = now - last > 20;
        Mechanics.trueHit(e, (PER_TICK + (dunk ? DUNK : 0)) * k, null, true);
        if (fireproof && now % 20 == 0) e.removeEffect(MobEffects.FIRE_RESISTANCE);
        if (dunk) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
            Fx.spark(server, e.position().add(0, 0.4, 0), 14, 0.6, 0.35F, 0xFFC84A);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        if (now - tag.getLong("PaponFried") >= 16) {
            // the boil throws you up: the way out, if you steer for the sand
            tag.putLong("PaponFried", now);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.5, 0.78, v.z * 0.5);
            e.hurtMarked = true;
            Fx.spark(server, e.position().add(0, 0.4, 0), 8, 0.5, 0.3F, 0xFFC84A);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.4F, 1.3F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos.above()).is(this)) return;          // the bottom of the vat: the top layer bubbles
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + 0.78, z = pos.getZ() + random.nextDouble();
        if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.04, 0);
        if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.WHITE_ASH, x, y + 0.1, z, 0, 0.05, 0);
        if (random.nextInt(60) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.4F, 1.6F + random.nextFloat() * 0.3F, false);
        }
    }
}
