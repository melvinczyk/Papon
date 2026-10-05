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
 * El Gigante (phase four, PaponBossEntity.tickP4Gigante): a giant skeleton of blue light that rises up out of the floor of El
 * Coliseo de los Muertos itself, at one end of the arena, waist deep in it: its sugar skull 26 blocks over the floor, its
 * ribs and spine, shoulders as wide as a house, two long arms of bone-light. (2026-10-04, the user: "it needs to be actually
 * in the arena... a giant threat; it needs to shoot the lasers from its eyes, thicker; its hand attacks need to be swipes
 * that smack the player into the walls".) It rises, looks for you (its eyes charge), and lasers come out of both eyes;
 * its hands sweep across the floor in great arcs (a band marked on the floor first) and send anyone they catch flying into
 * the wall; it slams; it sinks back into the floor. Drawn by client/SpiritGiantRenderer; the server says what it is doing
 * ({@link #act}), where its hands go ({@link #hand}: the same sums both sides) and where its eyes look.
 */
public class SpiritGiantEntity extends Entity {
    public static final int RISE = 0, IDLE = 1, CHARGE = 2, LASER = 3, SLAM_L = 4, SLAM_R = 5, SINK = 6, SWIPE_L = 7, SWIPE_R = 8;
    /** Ticks a rise and a sink take; a slam: the hand up, then down onto its mark at {@link #SLAM_HIT}, held, back. */
    public static final int RISE_T = 46, SINK_T = 40, SLAM_HIT = 21, SLAM_T = 40;
    /** A swipe: the wind-up (the hand drawn back and up, the band marked) to SWIPE_FROM, the sweep to SWIPE_TO, back by SWIPE_T. */
    public static final int SWIPE_FROM = 18, SWIPE_TO = 27, SWIPE_T = 46;
    /** How far round the swipe goes, either side of straight ahead (radians). */
    public static final double SWIPE_ARC = Math.toRadians(80);
    /** The skull's centre over the floor (the entity stands on the floor), and forward of it. */
    public static final double HEAD_Y = 26, HEAD_FWD = 2;
    /** The shoulders: over the floor, out to each side. */
    public static final double SHOULDER_Y = 15.5, SHOULDER_OUT = 8.5;
    /** Its arms: upper arm and forearm (blocks). */
    public static final double UPPER = 13, FORE = 13;
    /** Its voxels' size (blocks): the renderer's scale too. */
    public static final float V = 1.0F;

    private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_L = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_R = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    /** Where its eyes are looking (the lasers' aim), and the swipe's radius from its middle. */
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SWIPE = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.FLOAT);
    private int ownerId = -1;
    private int life = 500;

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
        g.entityData.set(DATA_HAND_L, g.handRest(0).toVector3f());
        g.entityData.set(DATA_HAND_R, g.handRest(1).toVector3f());
        g.entityData.set(DATA_AIM, lookAt.toVector3f());
        return g;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ACT, RISE);
        entityData.define(DATA_ACT_AT, 0);
        entityData.define(DATA_HAND_L, new Vector3f());
        entityData.define(DATA_HAND_R, new Vector3f());
        entityData.define(DATA_AIM, new Vector3f());
        entityData.define(DATA_SWIPE, 20F);
    }

    public void act(int act) {
        entityData.set(DATA_ACT, act);
        entityData.set(DATA_ACT_AT, (int) (level().getGameTime() & 0x7FFFFFFF));
        if (level() instanceof ServerLevel level) {
            Vec3 h = head(0);
            switch (act) {
                case RISE -> {
                    level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 6.0F, 0.4F);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.4F);
                }
                case CHARGE -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 6.0F, 0.5F);
                case SLAM_L, SLAM_R, SWIPE_L, SWIPE_R -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0F, 0.5F);
                case SINK -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 6.0F, 0.5F);
                default -> {}
            }
        }
    }

    public void sink() { if (act() != SINK) act(SINK); }

    /** Hand {@code i} (0 its left, 1 its right) is going to come down on {@code at}. */
    public void handTarget(int i, Vec3 at) { entityData.set(i == 0 ? DATA_HAND_L : DATA_HAND_R, at.toVector3f()); }

    /** Where its eyes look (the lasers' end). */
    public void aim(Vec3 at) { entityData.set(DATA_AIM, at.toVector3f()); }

    public Vec3 aim() { return new Vec3(entityData.get(DATA_AIM)); }

    /** A swipe {@code r} blocks out from its middle (the band its hand sweeps). */
    public void swipe(int act, double r) {
        entityData.set(DATA_SWIPE, (float) r);
        act(act);
    }

    public double swipeRadius() { return entityData.get(DATA_SWIPE); }

    public int act() { return entityData.get(DATA_ACT); }

    /** Ticks since the current act began (both sides: from the synced game tick). */
    public float actTime(float partial) {
        long now = level().getGameTime() & 0x7FFFFFFFL;
        long d = (now - entityData.get(DATA_ACT_AT)) & 0x7FFFFFFFL;
        return d > (1L << 30) ? 0 : d + partial;
    }

    /** How far up out of the floor it has come (0 under it, 1 risen). */
    public float risen(float partial) {
        float t = actTime(partial);
        return switch (act()) {
            case RISE -> smooth(t / RISE_T);
            case SINK -> 1 - smooth(t / SINK_T);
            default -> 1;
        };
    }

    /** How far below its risen place it is (blocks). */
    public double sunk(float partial) { return (1 - risen(partial)) * 44; }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    public Vec3 forward() { return Vec3.directionFromRotation(0, getYRot()); }

    public Vec3 right() {
        Vec3 f = forward();
        return new Vec3(-f.z, 0, f.x);
    }

    /** The skull's centre (world), bobbing as it breathes. */
    public Vec3 head(float partial) {
        double bob = Math.sin((tickCount + partial) * 0.05) * 0.4;
        return position().add(forward().scale(HEAD_FWD)).add(0, HEAD_Y - sunk(partial) + bob, 0);
    }

    /** Eye {@code side} (-1 its right, 1 its left): in its socket, the front of the skull. */
    public Vec3 eye(int side, float partial) {
        return head(partial).add(right().scale(side * 3.2 * V)).add(0, -0.2 * V, 0).add(forward().scale(4.6 * V));
    }

    /** Shoulder {@code i} (0 its left, 1 its right). */
    public Vec3 shoulder(int i, float partial) {
        return position().add(right().scale((i == 0 ? 1 : -1) * SHOULDER_OUT)).add(forward().scale(0.5)).add(0, SHOULDER_Y - sunk(partial), 0);
    }

    /** A hand's place at rest: down on the floor in front of it, a little out. */
    public Vec3 handRest(int i) {
        return position().add(forward().scale(11)).add(right().scale((i == 0 ? 1 : -1) * 11)).add(0, 1.2, 0);
    }

    /** Where hand {@code i} is aimed (its slam's mark on the floor). */
    public Vec3 handMark(int i) { return new Vec3(entityData.get(i == 0 ? DATA_HAND_L : DATA_HAND_R)); }

    /** The swipe's angle now (radians from straight ahead, toward its right positive), for hand {@code i}'s swipe. */
    public static double swipeAngle(int i, float t) {
        double from = (i == 0 ? 1 : -1) * SWIPE_ARC * 1.15, to = -from * 0.95;
        if (t < SWIPE_FROM) return from;
        if (t < SWIPE_TO) {
            double k = (t - SWIPE_FROM) / (SWIPE_TO - SWIPE_FROM);
            k = k * k * (3 - 2 * k);
            return from + (to - from) * k;
        }
        return to;
    }

    /** Where hand {@code i} is now: at rest; in a slam up, over and down onto its mark; in a swipe drawn back, swept, back. */
    public Vec3 hand(int i, float partial) {
        Vec3 rest = handRest(i).add(0, -sunk(partial), 0);
        int a = act();
        float t = actTime(partial);
        if ((a == SWIPE_L && i == 0) || (a == SWIPE_R && i == 1)) {
            double r = swipeRadius();
            Vec3 f = forward(), rt = right();
            double ang = swipeAngle(i, t);
            Vec3 sweep = position().add(f.scale(Math.cos(ang) * r)).add(rt.scale(-Math.sin(ang) * r)).add(0, 1.6, 0);
            Vec3 back = position().add(f.scale(Math.cos(ang) * r * 0.55)).add(rt.scale(-Math.sin(ang) * r * 0.55)).add(0, 12, 0);
            if (t < SWIPE_FROM - 6) return rest.lerp(back, smooth(t / (SWIPE_FROM - 6F)));
            if (t < SWIPE_FROM) return back.lerp(sweep, smooth((t - SWIPE_FROM + 6) / 6F));
            if (t < SWIPE_TO + 4) return sweep;
            return sweep.lerp(rest, smooth((t - SWIPE_TO - 4) / (SWIPE_T - SWIPE_TO - 4F)));
        }
        boolean slam = (a == SLAM_L && i == 0) || (a == SLAM_R && i == 1);
        if (!slam) return rest.add(0, Math.sin((tickCount + partial) * 0.08 + i * 2) * 0.4, 0);
        Vec3 mark = handMark(i).add(0, 0.8, 0);
        Vec3 high = rest.lerp(mark, 0.4).add(0, 16, 0);
        if (t < 9) return rest.lerp(high, smooth(t / 9F));
        if (t < SLAM_HIT) {
            float k = (t - 9) / (SLAM_HIT - 9F);
            return high.lerp(mark, k * k);
        }
        if (t < 30) return mark;
        return mark.lerp(rest, smooth((t - 30) / 10F));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                Vec3 h = head(0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, h.x + (random.nextDouble() - 0.5) * 16,
                        h.y + (random.nextDouble() - 0.5) * 16, h.z + (random.nextDouble() - 0.5) * 16, 0, 0.05, 0);
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
        if (act() == RISE && t < RISE_T && tickCount % 4 == 0) {
            // tearing up out of the floor
            Fx.chunks(level, position().add(0, 1, 0), 10, 0.5F, 0x2C2438, 0.9);
            PaponNetwork.shake(level, position(), 90, 0.5F, 6);
        }
        if ((act() == SLAM_L || act() == SLAM_R) && (int) t == SLAM_HIT) {
            Vec3 at = handMark(act() == SLAM_L ? 0 : 1);
            Fx.flash(level, at.add(0, 1, 0), 2.5F);
            PaponNetwork.shake(level, at, 60, 0.8F, 16);
        }
        if (act() == SLAM_L || act() == SLAM_R) { if (t >= SLAM_T) act(IDLE); }
        if (act() == SWIPE_L || act() == SWIPE_R) {
            if ((int) t == SWIPE_FROM) level.playSound(null, getX(), getY() + 4, getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 6.0F, 0.3F);
            if (t >= SWIPE_T) act(IDLE);
        }
        if (act() == RISE && t >= RISE_T) act(IDLE);
        if (act() == SINK && t >= SINK_T) discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(50, 60, 50).move(0, 20, 0); }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }
}
