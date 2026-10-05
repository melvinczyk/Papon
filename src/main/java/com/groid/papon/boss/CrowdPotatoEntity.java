package com.groid.papon.boss;

import com.groid.papon.fx.Fx;
import com.groid.papon.registry.ModEffects;
import com.groid.papon.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A potato thrown from the stands of El Coliseo Papa at a challenger who strays near them. It is
 * there to annoy, not to hurt: a quarter of a heart, a little shove, and a faceful of potato
 * ({@link ModEffects#SPLATTERED}: a tenth of the screen covered for five seconds). It goes
 * straight through the boss, the band and the fans' own railing; a block just squashes it.
 */
public class CrowdPotatoEntity extends ThrowableItemProjectile {
    public static final float DAMAGE = 0.5F;
    public static final int SPLAT_TICKS = 100;

    public CrowdPotatoEntity(EntityType<? extends CrowdPotatoEntity> type, Level level) { super(type, level); }

    public CrowdPotatoEntity(Level level, Vec3 from, Vec3 velocity) {
        super(ModEntities.CROWD_POTATO.get(), from.x, from.y, from.z, level);
        setDeltaMovement(velocity);
    }

    @Override
    protected Item getDefaultItem() { return Items.POTATO; }

    @Override
    protected float getGravity() { return 0.04F; }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 100) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) { return e instanceof Player && super.canHitEntity(e); }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel) || !(result.getEntity() instanceof LivingEntity e)) return;
        if (e.hurt(damageSources().thrown(this, null), DAMAGE)) {
            Vec3 push = getDeltaMovement().multiply(1, 0, 1).normalize().scale(0.32);
            e.push(push.x, 0.12, push.z);
            e.hurtMarked = true;
        }
        e.addEffect(new MobEffectInstance(ModEffects.SPLATTERED.get(), SPLAT_TICKS, 0, false, false, true));
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = result.getLocation();
        Fx.chunks(level, at, 5, 0.2F, Fx.POTATO, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.9F, 1.3F + random.nextFloat() * 0.3F);
        discard();
    }
}
