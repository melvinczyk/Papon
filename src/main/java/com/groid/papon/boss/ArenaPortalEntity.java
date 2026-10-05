package com.groid.papon.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way back in: a small portal in the Taco Truck's window, where the chef stood, while El Coliseo Papa is
 * up and he is still standing. Step into it and you land in the middle of the sand (a moment of
 * Resistance so the landing is not a death). It is his: {@link PaponBossEntity} spawns it and tells it where
 * the sand is every tick; it closes when he stops (the coliseum came down, he fell). Never saved.
 */
public class ArenaPortalEntity extends Entity {
    @Nullable private Vec3 target;
    private int orphan;
    private final Map<UUID, Integer> cooldown = new HashMap<>();

    public ArenaPortalEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Called by the boss every tick: where it sends you. */
    public void hold(Vec3 to) {
        orphan = 0;
        target = to;
    }

    /** How open it is, 0..1 (it swirls open over a second). */
    public float open(float partial) { return Math.min(1F, (tickCount + partial) / 20F); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            float k = open(0);
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90);
                Vec3 at = position().add(side.scale(Math.cos(a) * 0.55 * k)).add(0, 1.1 + Math.sin(a) * 1.0 * k, 0);
                level().addParticle(ParticleTypes.PORTAL, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.4, -0.1, (random.nextDouble() - 0.5) * 0.4);
            }
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 1.1 + (random.nextDouble() - 0.5) * 1.6, getZ(), 0, 0.02, 0);
            }
            return;
        }
        if (++orphan > 60) {
            discard();
            return;
        }
        if (tickCount % 80 == 0) { level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 1.4F); }
        cooldown.replaceAll((k, v) -> v - 1);
        cooldown.values().removeIf(v -> v <= 0);
        if (target == null || tickCount < 20) return;
        for (ServerPlayer p : ((ServerLevel) level()).getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.8, 0.2, 0.8), ServerPlayer::isAlive)) {
            if (p.isSpectator() || p.isPassenger() || cooldown.containsKey(p.getUUID())) continue;
            cooldown.put(p.getUUID(), 60);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
            Coliseum.send((ServerLevel) level(), p, target);
            level().playSound(null, target.x, target.y + 1, target.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2F, 0.7F);
        }
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
