package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.registry.ModEntities;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One round from Chef Papon's potato gatling: a baked potato at bullet speed, nearly flat. Small
 * damage that ignores the usual half-second of invulnerability after a hit (it is a gatling: every
 * round counts) and a nudge back; bursts into mash on whatever it hits. Shields stop it.
 */
public class PotatoShotEntity extends ThrowableItemProjectile {
    public PotatoShotEntity(EntityType<? extends PotatoShotEntity> type, Level level) { super(type, level); }

    public PotatoShotEntity(Level level, LivingEntity shooter) { super(ModEntities.POTATO_SHOT.get(), shooter, level); }

    @Override
    protected Item getDefaultItem() { return Items.BAKED_POTATO; }

    @Override
    protected float getGravity() { return 0.006F; }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 60) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity e = result.getEntity();
        if (!(level() instanceof ServerLevel) || e == getOwner() || e instanceof CaravanEntity || e instanceof PaponBossEntity) return;
        if (e instanceof LivingEntity le) le.invulnerableTime = 0;
        float damage = 1.2F * PaponConfig.BOSS_DAMAGE.get().floatValue();
        if (e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), damage)) {
            Vec3 push = getDeltaMovement().multiply(1, 0, 1).normalize().scale(0.18);
            e.push(push.x, 0.04, push.z);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = result.getLocation();
        com.groid.papon.fx.Fx.chunks(level, at, 2, 0.18F, com.groid.papon.fx.Fx.POTATO, 0.25);
        if (random.nextInt(3) == 0) { level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 0.6F, 1.4F); }
        discard();
    }
}
