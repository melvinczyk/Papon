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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * El Gigante (phase four): a giant skeleton of the dead that rises up out of the floor of El Coliseo de los Muertos itself, at
 * one end of the arena, waist deep in it: a sugar skull 27 blocks over the floor, a ribcage and spine, shoulders as wide as a
 * house, two long arms of bone down to hands that can sweep the whole floor. (2026-10-04, the user: "it needs to be actually in
 * the arena... a giant threat; lasers from its eyes, thicker; its hand attacks need to be swipes that smack the player into the
 * walls". 2026-10-05: "a massive, three-dimensional physical presence... more convincing skeletal structure... its attacks a
 * meaningful part of the fight, alongside the serpents and the boss".) It rises, looks for you (its skull turns to follow
 * you, its eyes charge), and lasers come out of both eyes; its hands sweep across the floor in great arcs (a band marked on
 * the floor first) and send anyone they catch flying into the wall; one hand or both come down on you, and the floor rings
 * out from where they land; it roars; it sinks back into the floor. In the second half of phase four it stays in the arena
 * and fights beside him (PaponBossEntity.tickGiantLayer); the boss decides what it does and what it hits
 * (PaponBossEntity.tickGiantHazards), this says where its parts are: {@link #hand}, {@link #eye}, {@link #head}, the same sums
 * on the server and every client. Drawn by client/SpiritGiantRenderer.
 * <p>
 * The entity itself stands at the arena's middle ({@link #base} is where the giant stands): a server sends an entity only to
 * players within its tracking range of where it is, and the far end of the dead's coliseum can be further than that from you.
 */
public class SpiritGiantEntity extends net.minecraft.world.entity.Entity {
    public static final int RISE = 0, IDLE = 1, CHARGE = 2, LASER = 3, SLAM_L = 4, SLAM_R = 5, SINK = 6, SWIPE_L = 7, SWIPE_R = 8,
            SLAM_BOTH = 9, ROAR = 10;
    /** Ticks a rise and a sink take; a slam: the hand up, then down onto its mark at {@link #SLAM_HIT}, held, back. */
    public static final int RISE_T = 56, SINK_T = 44, SLAM_HIT = 21, SLAM_T = 40;
    /** Both hands: up over its head to {@link #SLAM2_UP}, held, down onto their marks at {@link #SLAM2_HIT}, back by SLAM2_T. */
    public static final int SLAM2_UP = 16, SLAM2_HIT = 30, SLAM2_T = 56;
    /** A roar: thrown back, arms wide, then forward at you. */
    public static final int ROAR_T = 50;
    /** A swipe: the wind-up (the hand drawn back and up, the band marked) to SWIPE_FROM, the sweep to SWIPE_TO, back by SWIPE_T. */
    public static final int SWIPE_FROM = 18, SWIPE_TO = 27, SWIPE_T = 46;
    /** How far round the swipe goes, either side of straight ahead (radians). */
    public static final double SWIPE_ARC = Math.toRadians(80);
    /** The skull's centre over the floor (the giant stands on the floor), and forward of it. */
    public static final double HEAD_Y = 27, HEAD_FWD = 2.4;
    /** The shoulders: over the floor, out to each side. */
    public static final double SHOULDER_Y = 18.5, SHOULDER_OUT = 10.5;
    /** Its arms: upper arm and forearm (blocks). */
    public static final double UPPER = 13.5, FORE = 13.5;
    /** The skull's units (blocks): the renderer's scale too (it was 1.0: a bobble head on a body of sticks). */
    public static final float V = 0.74F;
    /** The eyes in the skull (skull units): out to each side, down, forward. */
    public static final double EYE_OUT = 3.3, EYE_DOWN = 0.5, EYE_FWD = 5.6;

    private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_L = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_R = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    /** Where its eyes are looking (the lasers' aim, or you), and the swipe's radius from its middle. */
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SWIPE = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.FLOAT);
    /** Where it stands (the entity is at the arena's middle), and its facing (degrees, like a yaw). */
    private static final EntityDataAccessor<Vector3f> DATA_BASE = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_FACING = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.FLOAT);
    /** How wild it is (phase four's stage: 1..3): it breathes faster, its eyes burn hotter. */
    private static final EntityDataAccessor<Integer> DATA_FURY = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;
    private int life = 500;
    /** Client: the skull's turn toward what it looks at, eased (yaw, pitch; radians). */
    private float lookYaw, lookPitch, lookYawO, lookPitchO;

    public SpiritGiantEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** A giant standing at {@code at}, facing {@code lookAt}; the entity itself at {@code middle} (the arena's centre). */
    public static SpiritGiantEntity create(ServerLevel level, PaponBossEntity boss, Vec3 at, Vec3 lookAt, Vec3 middle) {
        SpiritGiantEntity g = new SpiritGiantEntity(com.groid.papon.registry.ModEntities.SPIRIT_GIANT.get(), level);
        g.ownerId = boss.getId();
        float yaw = (float) (Mth.atan2(lookAt.z - at.z, lookAt.x - at.x) * Mth.RAD_TO_DEG) - 90F;
        g.moveTo(middle.x, middle.y, middle.z, yaw, 0);
        g.entityData.set(DATA_BASE, at.toVector3f());
        g.entityData.set(DATA_FACING, yaw);
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
        entityData.define(DATA_BASE, new Vector3f());
        entityData.define(DATA_FACING, 0F);
        entityData.define(DATA_FURY, 1);
    }

    public void act(int act) {
        entityData.set(DATA_ACT, act);
        entityData.set(DATA_ACT_AT, (int) (level().getGameTime() & 0x7FFFFFFF));
        if (level() instanceof ServerLevel level) {
            Vec3 h = head(0);
            switch (act) {
                case RISE -> {
                    level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 6.0F, 0.4F);
                    level.playSound(null, base().x, base().y, base().z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.4F);
                }
                case CHARGE -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 6.0F, 0.5F);
                case SLAM_L, SLAM_R, SWIPE_L, SWIPE_R, SLAM_BOTH -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0F, 0.5F);
                case ROAR -> {
                    level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.35F);
                    level.playSound(null, h.x, h.y, h.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.4F);
                }
                case SINK -> level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 6.0F, 0.5F);
                default -> {}
            }
        }
    }

    public void sink() { if (act() != SINK) act(SINK); }

    /** Keeps it in the arena at least this many ticks longer. */
    public void keep(int ticks) { life = Math.max(life, ticks); }

    public void fury(int stage) { if (entityData.get(DATA_FURY) != stage) entityData.set(DATA_FURY, stage); }

    public int fury() { return entityData.get(DATA_FURY); }

    /** Hand {@code i} (0 its left, 1 its right) is going to come down on {@code at}. */
    public void handTarget(int i, Vec3 at) { entityData.set(i == 0 ? DATA_HAND_L : DATA_HAND_R, at.toVector3f()); }

    /** Where its eyes look (the lasers' end, or you). */
    public void aim(Vec3 at) {
        Vector3f v = at.toVector3f();
        if (!v.equals(entityData.get(DATA_AIM))) entityData.set(DATA_AIM, v);
    }

    public Vec3 aim() { return new Vec3(entityData.get(DATA_AIM)); }

    /** A swipe {@code r} blocks out from where it stands (the band its hand sweeps). */
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
    public double sunk(float partial) { return (1 - risen(partial)) * 46; }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Where it stands: on the floor, at the end it rose at. */
    public Vec3 base() {
        Vector3f b = entityData.get(DATA_BASE);
        return b.lengthSquared() == 0 ? position() : new Vec3(b);
    }

    public Vec3 forward() { return Vec3.directionFromRotation(0, entityData.get(DATA_FACING)); }

    public Vec3 right() {
        Vec3 f = forward();
        return new Vec3(-f.z, 0, f.x);
    }

    /** How it leans at {@code partial}: forward into a slam or a laser, back in a roar (blocks of the skull's travel). */
    public double lean(float partial) {
        float t = actTime(partial);
        return switch (act()) {
            case CHARGE -> 1.6 * smooth(t / 20F);
            case LASER -> 2.0;
            case SLAM_BOTH -> t < SLAM2_UP ? -1.8 * smooth(t / SLAM2_UP) : t < SLAM2_HIT ? -1.8 + 5.6 * smooth((t - SLAM2_UP) / (SLAM2_HIT - SLAM2_UP))
                    : 3.8 * (1 - smooth((t - SLAM2_HIT - 8) / 16F));
            case SLAM_L, SLAM_R -> t < 9 ? -0.8 * smooth(t / 9F) : t < SLAM_HIT ? 2.6 * smooth((t - 9) / (SLAM_HIT - 9F)) : 2.6 * (1 - smooth((t - 28) / 12F));
            case ROAR -> t < 14 ? -3.2 * smooth(t / 14F) : t < 36 ? -3.2 + 6.4 * smooth((t - 14) / 10F) : 3.2 * (1 - smooth((t - 36) / 14F));
            default -> 0;
        };
    }

    /** The skull's centre (world), bobbing as it breathes, leaning with what it does. */
    public Vec3 head(float partial) {
        double breath = fury() >= 3 ? 0.11 : fury() == 2 ? 0.08 : 0.05;
        double bob = Math.sin((tickCount + partial) * breath) * 0.5;
        return base().add(forward().scale(HEAD_FWD + lean(partial))).add(0, HEAD_Y - sunk(partial) + bob - Math.abs(lean(partial)) * 0.25, 0);
    }

    /** The skull's turn toward what it looks at (radians from straight ahead, toward its right positive; and up). */
    public float[] look(float partial) {
        float[] l = level().isClientSide ? new float[]{Mth.lerp(partial, lookYawO, lookYaw), Mth.lerp(partial, lookPitchO, lookPitch)} : lookWant();
        l[1] += actPitch(partial);
        return l;
    }

    /** The skull thrown back in a roar, ducked as it charges its eyes, recoiling as the lasers fire, nodding into a slam. */
    private float actPitch(float partial) {
        float t = actTime(partial);
        return switch (act()) {
            case ROAR -> t < 14 ? 0.55F * smooth(t / 14F) : t < 36 ? 0.55F - 0.85F * smooth((t - 14) / 8F) : -0.3F * (1 - smooth((t - 36) / 14F));
            case CHARGE -> -0.12F * smooth(t / 20F);
            case LASER -> 0.05F + 0.02F * Mth.sin(t * 2.1F);
            case SLAM_BOTH -> t < SLAM2_UP ? 0.3F * smooth(t / SLAM2_UP) : t < SLAM2_HIT ? 0.3F - 0.6F * smooth((t - SLAM2_UP) / (SLAM2_HIT - SLAM2_UP))
                    : -0.3F * (1 - smooth((t - SLAM2_HIT - 6) / 16F));
            case RISE -> 0.35F * (1 - smooth(t / RISE_T));
            default -> 0;
        };
    }

    private float[] lookWant() {
        Vec3 h = base().add(forward().scale(HEAD_FWD)).add(0, HEAD_Y, 0);
        Vec3 d = aim().subtract(h);
        Vec3 f = forward(), r = right();
        double fc = d.dot(f), rc = d.dot(r);
        float yaw = (float) Mth.clamp(Math.atan2(rc, Math.max(1, fc)), -0.9, 0.9);
        float pitch = (float) Mth.clamp(Math.atan2(d.y, Math.sqrt(fc * fc + rc * rc)), -0.75, 0.3);
        return new float[]{yaw, pitch};
    }

    /** A point in the skull (skull units: right, up, forward) in the world, the skull turned to look. */
    public Vec3 skull(double x, double y, double z, float partial) {
        float[] lk = look(partial);
        double cy = Math.cos(lk[0]), sy = Math.sin(lk[0]), cp = Math.cos(lk[1]), sp = Math.sin(lk[1]);
        // pitch about the skull's right axis, then yaw about up
        double y1 = y * cp + z * sp, z1 = -y * sp + z * cp;
        double x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
        Vec3 f = forward(), r = right();
        return head(partial).add(r.scale(x2 * V)).add(0, y1 * V, 0).add(f.scale(z2 * V));
    }

    /** Eye {@code side} (-1 its right, 1 its left): deep in its socket. */
    public Vec3 eye(int side, float partial) { return skull(side * EYE_OUT, -EYE_DOWN, EYE_FWD, partial); }

    /** Shoulder {@code i} (0 its left, 1 its right). */
    public Vec3 shoulder(int i, float partial) {
        double l = lean(partial) * 0.55;
        return base().add(right().scale((i == 0 ? 1 : -1) * SHOULDER_OUT)).add(forward().scale(0.6 + l)).add(0, SHOULDER_Y - sunk(partial) - Math.abs(l) * 0.2, 0);
    }

    /** A hand's place at rest: down on the floor in front of it, a little out. */
    public Vec3 handRest(int i) {
        return base().add(forward().scale(12)).add(right().scale((i == 0 ? 1 : -1) * 12)).add(0, 1.2, 0);
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
        if (a == RISE) {
            // the hands come first, clawing up out of the floor, then pull the rest of it after them
            float k = smooth(t / (RISE_T * 0.55F));
            return handRest(i).add(0, -18 * (1 - k), 0).add(0, Math.sin(t * 0.6 + i) * 0.4 * (1 - k), 0);
        }
        if ((a == SWIPE_L && i == 0) || (a == SWIPE_R && i == 1)) {
            double r = swipeRadius();
            Vec3 f = forward(), rt = right(), b = base();
            double ang = swipeAngle(i, t);
            Vec3 sweep = b.add(f.scale(Math.cos(ang) * r)).add(rt.scale(-Math.sin(ang) * r)).add(0, 1.6, 0);
            Vec3 back = b.add(f.scale(Math.cos(ang) * r * 0.55)).add(rt.scale(-Math.sin(ang) * r * 0.55)).add(0, 13, 0);
            if (t < SWIPE_FROM - 6) return rest.lerp(back, smooth(t / (SWIPE_FROM - 6F)));
            if (t < SWIPE_FROM) return back.lerp(sweep, smooth((t - SWIPE_FROM + 6) / 6F));
            if (t < SWIPE_TO + 4) return sweep;
            return sweep.lerp(rest, smooth((t - SWIPE_TO - 4) / (SWIPE_T - SWIPE_TO - 4F)));
        }
        if (a == SLAM_BOTH) {
            Vec3 mark = handMark(i).add(0, 0.9, 0);
            Vec3 high = head(partial).add(right().scale((i == 0 ? 1 : -1) * 5.5)).add(forward().scale(-1)).add(0, 9, 0);
            if (t < SLAM2_UP) return rest.lerp(high, smooth(t / SLAM2_UP));
            if (t < SLAM2_HIT - 6) return high.add(0, Math.sin(t * 0.9) * 0.3, 0);
            if (t < SLAM2_HIT) {
                float k = (t - SLAM2_HIT + 6) / 6F;
                return high.lerp(mark, k * k);
            }
            if (t < SLAM2_HIT + 12) return mark;
            return mark.lerp(rest, smooth((t - SLAM2_HIT - 12) / (SLAM2_T - SLAM2_HIT - 12F)));
        }
        if (a == ROAR) {
            // arms flung wide and up, then down on the floor either side as it bellows forward
            Vec3 wide = shoulder(i, partial).add(right().scale((i == 0 ? 1 : -1) * 14)).add(forward().scale(3)).add(0, 8, 0);
            if (t < 14) return rest.lerp(wide, smooth(t / 14F));
            if (t < 30) return wide.add(0, Math.sin(t * 1.3) * 0.5, 0);
            return wide.lerp(rest, smooth((t - 30) / 16F));
        }
        boolean slam = (a == SLAM_L && i == 0) || (a == SLAM_R && i == 1);
        double breath = fury() >= 3 ? 0.14 : 0.08;
        if (!slam) return rest.add(0, Math.sin((tickCount + partial) * breath + i * 2) * 0.5, 0);
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
            lookYawO = lookYaw;
            lookPitchO = lookPitch;
            float[] want = lookWant();
            float k = act() == LASER ? 0.3F : 0.12F;
            lookYaw += (want[0] - lookYaw) * k;
            lookPitch += (want[1] - lookPitch) * k;
            if (random.nextInt(2) == 0) {
                Vec3 h = head(0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, h.x + (random.nextDouble() - 0.5) * 12,
                        h.y + (random.nextDouble() - 0.5) * 12, h.z + (random.nextDouble() - 0.5) * 12, 0, 0.05, 0);
            }
            // where it comes up through the floor: wisps of the dead
            if (risen(0) > 0.05F && random.nextInt(3) == 0) {
                Vec3 b = base();
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL, b.x + Math.cos(a) * 7.5, b.y + 0.3, b.z + Math.sin(a) * 5.5,
                        0, 0.06 + random.nextDouble() * 0.05, 0);
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
            Fx.chunks(level, base().add(0, 1, 0), 12, 0.6F, 0x2C2438, 1.0);
            PaponNetwork.shake(level, base(), 90, 0.5F, 6);
        }
        if ((act() == SLAM_L || act() == SLAM_R) && (int) t == SLAM_HIT) {
            Vec3 at = handMark(act() == SLAM_L ? 0 : 1);
            Fx.flash(level, at.add(0, 1, 0), 2.5F);
            Fx.chunks(level, at.add(0, 0.5, 0), 18, 0.6F, 0x2C2438, 0.9);
            PaponNetwork.shake(level, at, 60, 0.8F, 16);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
        if (act() == SLAM_BOTH && (int) t == SLAM2_HIT) {
            for (int i = 0; i < 2; i++) {
                Vec3 at = handMark(i);
                Fx.flash(level, at.add(0, 1, 0), 3.5F);
                Fx.chunks(level, at.add(0, 0.5, 0), 24, 0.7F, 0x2C2438, 1.1);
            }
            Vec3 mid = handMark(0).lerp(handMark(1), 0.5);
            PaponNetwork.shake(level, mid, 90, 1.0F, 22);
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6.0F, 0.4F);
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.3F);
        }
        if (act() == ROAR && (int) t == 26) PaponNetwork.shake(level, base(), 120, 0.7F, 30);
        if (act() == SLAM_L || act() == SLAM_R) { if (t >= SLAM_T) act(IDLE); }
        if (act() == SLAM_BOTH && t >= SLAM2_T) act(IDLE);
        if (act() == ROAR && t >= ROAR_T) act(IDLE);
        if (act() == SWIPE_L || act() == SWIPE_R) {
            if ((int) t == SWIPE_FROM) level.playSound(null, base().x, base().y + 4, base().z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 6.0F, 0.3F);
            if (t >= SWIPE_T) act(IDLE);
        }
        if (act() == RISE && t >= RISE_T) act(IDLE);
        if (act() == SINK && t >= SINK_T) discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() { return new AABB(base(), base()).inflate(60, 10, 60).expandTowards(0, 50, 0); }

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
