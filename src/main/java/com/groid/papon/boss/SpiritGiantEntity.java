package com.groid.papon.boss;

import com.groid.papon.fx.Fx;
import com.groid.papon.network.PaponNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * El Gigante (phase four, PaponBossEntity.tickP4Gigante): a giant skeleton of blue light standing on the ground far below
 * El Coliseo de los Muertos, so all you see of it is its skull over the wall at one end of the arena and its hands. It
 * rises, looks for you (its eyes charge), opens its jaw and a laser comes out of its mouth (the boss's beam); its hands come
 * down on the floor where it is told; then it sinks away. Drawn by client/SpiritGiantRenderer (a neon sugar skull, voxels of
 * light); the server only says what it is doing ({@link #act}) and where its hands are going.
 */
public class SpiritGiantEntity extends Entity {
    public static final int RISE = 0, IDLE = 1, CHARGE = 2, LASER = 3, SLAM_L = 4, SLAM_R = 5, SINK = 6;
    /** Ticks a rise and a sink take; a slam: the hand up, then down onto its mark at {@link #SLAM_HIT}, held, back. */
    public static final int RISE_T = 40, SINK_T = 34, SLAM_HIT = 21, SLAM_T = 40;
    /** The skull's centre over the entity (blocks), up out of the ground (it stands far below the floor). */
    public static final double HEAD_Y = 34, HEAD_FWD = 2;

    private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_L = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_R = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private int ownerId = -1;
    private int life = 400;

    public SpiritGiantEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static SpiritGiantEntity create(ServerLevel level, PaponBossEntity boss, Vec3 at, Vec3 lookAt) {
        SpiritGiantEntity g = new SpiritGiantEntity(com.groid.papon.registry.ModEntities.SPIRIT_GIANT.get(), level);
        g.ownerId = boss.getId();
        float yaw = (float) (Mth.atan2(lookAt.z - at.z, lookAt.x - at.x) * Mth.RAD_TO_DEG) - 90F;
        g.moveTo(at.x, at.y, at.z, yaw, 0);
        g.act(RISE);
        g.entityData.set(DATA_HAND_L, g.handRest(1).toVector3f());
        g.entityData.set(DATA_HAND_R, g.handRest(-1).toVector3f());
        return g;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ACT, RISE);
        entityData.define(DATA_ACT_AT, 0);
        entityData.define(DATA_HAND_L, new Vector3f());
        entityData.define(DATA_HAND_R, new Vector3f());
    }

    public void act(int act) {
        entityData.set(DATA_ACT, act);
        entityData.set(DATA_ACT_AT, (int) (level().getGameTime() & 0x7FFFFFFF));
        if (level() instanceof ServerLevel level) {
            Vec3 h = head(0);
            switch (act) {
                case RISE -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 5.0F, 0.4F);
                case CHARGE -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 5.0F, 0.5F);
                case SLAM_L, SLAM_R -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
                case SINK -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 5.0F, 0.5F);
                default -> {}
            }
        }
    }

    public void sink() {
        if (act() != SINK) act(SINK);
    }

    /** Hand {@code i} (0 left, 1 right) is going to come down on {@code at}. */
    public void handTarget(int i, Vec3 at) {
        entityData.set(i == 0 ? DATA_HAND_L : DATA_HAND_R, at.toVector3f());
    }

    public int act() {
        return entityData.get(DATA_ACT);
    }

    /** Ticks since the current act began (both sides: from the synced game tick). */
    public float actTime(float partial) {
        long now = level().getGameTime() & 0x7FFFFFFFL;
        long d = (now - entityData.get(DATA_ACT_AT)) & 0x7FFFFFFFL;
        return d > (1L << 30) ? 0 : d + partial;
    }

    /** How far up out of the ground it has come (0 below, 1 risen). */
    public float risen(float partial) {
        float t = actTime(partial);
        return switch (act()) {
            case RISE -> smooth(t / RISE_T);
            case SINK -> 1 - smooth(t / SINK_T);
            default -> 1;
        };
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    public Vec3 forward() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    /** The skull's centre (world). */
    public Vec3 head(float partial) {
        double rise = (1 - risen(partial)) * -30;
        return position().add(forward().scale(HEAD_FWD)).add(0, HEAD_Y + rise, 0);
    }

    /** Its mouth (world): where the laser comes out. */
    public Vec3 mouth() {
        return head(0).add(forward().scale(6.5)).add(0, -5.5, 0);
    }

    /** A hand's place on the wall at rest: {@code side} 1 its left (on your right as you face it), -1 its right. */
    public Vec3 handRest(int side) {
        Vec3 f = forward();
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return position().add(f.scale(7)).add(r.scale(-side * 15)).add(0, HEAD_Y - 4, 0);
    }

    /** Where hand {@code i} is aimed now (its mark on the floor). */
    public Vec3 handMark(int i) {
        return new Vec3(entityData.get(i == 0 ? DATA_HAND_L : DATA_HAND_R));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                Vec3 h = head(0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, h.x + (random.nextDouble() - 0.5) * 14,
                        h.y + (random.nextDouble() - 0.5) * 14, h.z + (random.nextDouble() - 0.5) * 14, 0, 0.05, 0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        boolean owner = level.getEntity(ownerId) instanceof PaponBossEntity b && b.isAlive();
        if (!owner || --life <= 0) {
            discard();
            return;
        }
        float t = actTime(0);
        if ((act() == SLAM_L || act() == SLAM_R) && (int) t == SLAM_HIT) {
            Vec3 at = handMark(act() == SLAM_L ? 0 : 1);
            Fx.flash(level, at.add(0, 1, 0), 2.5F);
            PaponNetwork.shake(level, at, 60, 0.8F, 16);
        }
        if (act() == SLAM_L || act() == SLAM_R) {
            if (t >= SLAM_T) act(IDLE);
        }
        if (act() == RISE && t >= RISE_T) act(IDLE);
        if (act() == SINK && t >= SINK_T) discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(40, 60, 40).move(0, 30, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
