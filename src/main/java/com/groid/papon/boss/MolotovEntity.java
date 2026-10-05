package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModDamage;
import com.groid.papon.registry.ModEntities;
import com.groid.papon.registry.ModItems;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A tequila bottle with a burning rag, lobbed by Chef Papon at anyone keeping their distance.
 * Bursts into a ball of fire where it lands: damage and burning in a small radius, no fire left on
 * the ground (the arena stays in one piece).
 */
public class MolotovEntity extends ThrowableItemProjectile {
    private static final double RADIUS = 3.2;

    public MolotovEntity(EntityType<? extends MolotovEntity> type, Level level) { super(type, level); }

    public MolotovEntity(Level level, LivingEntity thrower) { super(ModEntities.MOLOTOV.get(), thrower, level); }

    @Override
    protected Item getDefaultItem() { return ModItems.TEQUILA.get(); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0) {
            // the burning rag
            level().addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.FIRE.get(), -1, 0.3F, 1.3F,
                    0xFFFFFF, 1F, 10, 0), getX(), getY() + 0.25, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = result.getLocation();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.4F, 0.8F);
        com.groid.papon.fx.Fx.chunks(level, at.add(0, 0.2, 0), 6, 0.18F, 0xD8EEE6, 0.35);
        com.groid.papon.fx.Fx.blast(level, at, 1.3F);
        com.groid.papon.fx.Fx.burst(level, at.add(0, 0.3, 0), 0.7F, 4, RADIUS * 0.4);
        com.groid.papon.fx.Fx.scorch(level, at, (float) RADIUS * 0.8F, 160);
        PaponNetwork.shake(level, at, 6, 0.12F, 8);
        float damage = 7F * PaponConfig.BOSS_DAMAGE.get().floatValue();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS, 2, RADIUS),
                e -> e.isAlive() && e != getOwner() && !(e instanceof CaravanEntity) && !(e instanceof PaponBossEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e.position().distanceTo(at) > RADIUS + e.getBbWidth() / 2) continue;
            if (e.hurt(ModDamage.of(level, ModDamage.MOLOTOV, this, getOwner()), damage)) {
                e.setSecondsOnFire(6);
                Mechanics.heat(e, 1, getOwner());
            }
        }
        discard();
    }
}
