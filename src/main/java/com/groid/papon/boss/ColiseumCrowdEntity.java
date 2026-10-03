package com.groid.papon.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The crowd of El Coliseo Papa: one invisible entity at the middle of the sand that stands for
 * every fan in the stands. It holds their mood ({@link CrowdMood}) and the seed their seats and
 * looks are dealt from (Coliseum.seats); client/ColiseumCrowdRenderer draws them all and
 * client/CrowdSounds makes their noise. Removed with the coliseum.
 *
 * The boss sets a standing mood ({@link #setMood}) and fires short reactions over it
 * ({@link #react}: a roar when he lands one on you, boos when you land one on him).
 */
public class ColiseumCrowdEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_MOOD = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);

    private CrowdMood base = CrowdMood.CHEER;
    private int reactTicks;
    /** Client: ticks since the mood last changed (for blending poses and sounds). */
    private int moodTicks;
    private int lastMood = -1;
    private int prevMood;

    public ColiseumCrowdEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_MOOD, 0);
        entityData.define(DATA_SEED, 0);
    }

    public void setSeed(int seed) {
        entityData.set(DATA_SEED, seed);
    }

    public int seed() {
        return entityData.get(DATA_SEED);
    }

    public CrowdMood mood() {
        return CrowdMood.byId(entityData.get(DATA_MOOD));
    }

    /** Client: the mood before this one, and how long this one has lasted. */
    public CrowdMood previousMood() {
        return CrowdMood.byId(prevMood);
    }

    public int moodTicks() {
        return moodTicks;
    }

    /** The standing mood (what they go back to after a reaction). */
    public void setMood(CrowdMood m) {
        base = m;
        if (reactTicks <= 0) entityData.set(DATA_MOOD, m.ordinal());
    }

    /** A burst of something for a while, then back to the standing mood. */
    public void react(CrowdMood m, int ticks) {
        reactTicks = ticks;
        entityData.set(DATA_MOOD, m.ordinal());
    }

    public boolean reacting() {
        return reactTicks > 0;
    }

    @Override
    public void tick() {
        super.tick();
        int now = entityData.get(DATA_MOOD);
        if (now != lastMood) {
            prevMood = lastMood < 0 ? now : lastMood;
            lastMood = now;
            moodTicks = 0;
        } else {
            moodTicks++;
        }
        if (!level().isClientSide && reactTicks > 0 && --reactTicks == 0) entityData.set(DATA_MOOD, base.ordinal());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(Coliseum.A + 24, Coliseum.HEIGHT + 4, Coliseum.B + 24);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 200 * 200;
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_SEED, tag.getInt("Seed"));
        base = CrowdMood.byId(tag.getInt("Mood"));
        entityData.set(DATA_MOOD, base.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Seed", seed());
        tag.putInt("Mood", base.ordinal());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
