package com.groid.papon.block;

import com.groid.papon.boss.Mechanics;
import com.groid.papon.boss.PaponBossEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La Grieta de las Almas (phase four): the floor torn open, two blocks deep, and the dark in it full of souls. Step in
 * and you sink through the top into it; the souls drag at you: a share of your health every tick (through armor),
 * slowed, the dark closing in, pulled down. Every so often they wail and the void heaves (you are thrown up: the way
 * out). Chef Papon does not mind it. Put back to the floor when the rift closes (PaponBossEntity, Coliseum.restoreFloor).
 */
public class SoulVoidBlock extends Block {
    /** Share of maximum health a tick down in it, and on the way in. */
    public static final float PER_TICK = 0.012F, DRAGGED = 0.1F;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 4, 16);

    public SoulVoidBlock(Properties props) { super(props); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // the top of the rift gives way under you; you stand on what is left at its bottom
        return level.getBlockState(pos.below()).is(this) ? Shapes.empty() : FLOOR;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof LivingEntity e) || e instanceof PaponBossEntity) return;
        if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) return;
        boolean deep = level.getBlockState(pos.below()).is(this);
        if (entity.getY() > pos.getY() + (deep ? 1.0 : 0.6)) return;
        long now = level.getGameTime();
        var tag = e.getPersistentData();
        long last = tag.getLong("PaponVoidTick");
        if (last == now) return;
        boolean fresh = now - last > 10;
        tag.putLong("PaponVoidTick", now);
        if (fresh) {
            Mechanics.trueHit(e, DRAGGED, null, true);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 0.8F, 1.6F);
        } else { Mechanics.trueHit(e, PER_TICK, null, true); }
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
        if ((now + e.getId()) % 22 == 0) {
            // the souls wail and the void heaves: thrown up and out
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.4, 0, 0.4).add(0, 0.95, 0));
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.2F, 0.5F);
        } else { e.setDeltaMovement(e.getDeltaMovement().multiply(0.6, 1, 0.6).add(0, -0.04, 0)); }
        e.hurtMarked = true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SCULK_SOUL, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(),
                    0, 0.06 + random.nextDouble() * 0.05, 0);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + random.nextDouble(), pos.getY() + 0.95, pos.getZ() + random.nextDouble(),
                    0, 0.02, 0);
        }
        if (random.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS,
                    1.0F, 0.4F + random.nextFloat() * 0.3F, false);
        }
    }
}
