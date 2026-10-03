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

    public MolotovEntity(EntityType<? extends MolotovEntity> type, Level level) {
        super(type, level);
    }

    public MolotovEntity(Level level, LivingEntity thrower) {
        super(ModEntities.MOLOTOV.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.TEQUILA.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 0, 0.02, 0);
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = result.getLocation();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.4F, 0.8F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GLASS_BOTTLE)), at.x, at.y + 0.2, at.z,
                12, 0.2, 0.1, 0.2, 0.15);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 80, RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.06);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 10, 0.6, 0.2, 0.6, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 14, 0.8, 0.4, 0.8, 0.02);
        PaponNetwork.shake(level, at, 6, 0.12F, 8);
        float damage = 7F * PaponConfig.BOSS_DAMAGE.get().floatValue();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS, 2, RADIUS),
                e -> e.isAlive() && e != getOwner() && !(e instanceof CaravanEntity) && !(e instanceof PaponBossEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e.position().distanceTo(at) > RADIUS + e.getBbWidth() / 2) continue;
            if (e.hurt(ModDamage.of(level, ModDamage.MOLOTOV, this, getOwner()), damage)) e.setSecondsOnFire(6);
        }
        discard();
    }
}
