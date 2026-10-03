package com.groid.papon.entity.npc;

import com.groid.papon.entity.Speech;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base for the Taco Truck's cast: the Papon at the window and everyone in line. Like the Wandering
 * Papon they are scenery with a job, so nothing can hurt, push, target or despawn them; only
 * {@link #discard()} removes one (customers do that themselves once they have walked off).
 *
 * Each carries a synced state ordinal plus a server tick counter, the same pattern as the
 * Wandering Papon: the state picks the GeckoLib animation and one-shot animations are exactly as
 * long as their states, so server and client hand over together. Each also has a synced speech
 * line (see {@link Speech}) that the renderer draws as a bubble while it lasts.
 */
public abstract class TacoNpc extends PathfinderMob implements GeoEntity, Speech.Speaker {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(TacoNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SPEECH =
            SynchedEntityData.defineId(TacoNpc.class, EntityDataSerializers.STRING);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected int stateTicks;
    private int speechTicks;
    /** Client: ticks left of voice babble for the line now showing. */
    private int babbleTicks;

    protected TacoNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.0F);
        setPathfindingMalus(BlockPathTypes.WATER, 8.0F);
        setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, 0);
        entityData.define(DATA_SPEECH, "");
    }

    protected int stateId() {
        return entityData.get(DATA_STATE);
    }

    protected void setStateId(int id) {
        entityData.set(DATA_STATE, id);
        stateTicks = 0;
    }

    // ------------------------------------------------------------------ speech

    @Override
    public String getSpeech() {
        return entityData.get(DATA_SPEECH);
    }

    @Override
    public void setSpeech(String encoded, int ticks) {
        entityData.set(DATA_SPEECH, encoded);
        speechTicks = ticks;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SPEECH.equals(key) && level().isClientSide) {
            babbleTicks = getSpeech().isEmpty() ? 0 : Speech.babbleLength(getSpeech());
        }
    }

    /** Client: whether a line is being spoken right now (drives the talking jaw). */
    public boolean isBabbling() {
        return babbleTicks > 0;
    }

    /** Pitch and sound used for this character's talking blips. */
    protected abstract Speech.Voice voice();

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (babbleTicks > 0) {
                babbleTicks--;
                voice().blip(this, babbleTicks);
            }
            return;
        }
        if (speechTicks > 0 && --speechTicks == 0) {
            entityData.set(DATA_SPEECH, "");
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        stateTicks++;
        serverStep((ServerLevel) level());
    }

    protected abstract void serverStep(ServerLevel level);

    /** Turns body and head to face a point, instantly. */
    protected void face(Vec3 at) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float yaw = (float) (Math.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ------------------------------------------------------------------ animation sounds

    /** Sound keyframes in the crowd animations name a cue; {@link SoundCues} maps it to a sound. */
    protected <T extends TacoNpc> void onSoundCue(SoundKeyframeEvent<T> event) {
        SoundCues.play(this, event.getKeyframeData().getSound());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("StateTicks", stateTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        stateTicks = tag.getInt("StateTicks");
    }

    // ------------------------------------------------------------------ nobody gets hurt in line

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public void die(DamageSource source) { setHealth(getMaxHealth()); }
    @Override public void kill() {}
    @Override public void setHealth(float health) { super.setHealth(Math.max(health, getMaxHealth())); }
    @Override public void remove(RemovalReason reason) { if (reason != RemovalReason.KILLED) super.remove(reason); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeSeenAsEnemy() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean ignoreExplosion() { return true; }
    @Override public void thunderHit(ServerLevel level, LightningBolt bolt) {}
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canFreeze() { return false; }
    @Override public boolean isOnFire() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public void push(Entity entity) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean startRiding(Entity vehicle, boolean force) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public void checkDespawn() {}
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
}
