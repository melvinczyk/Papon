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
            SLAM_BOTH = 9, ROAR = 10, GRAB = 11, LUNGE = 12;
    /**
     * Ticks a rise and a sink take; a slam: the hand up, then down onto its mark at {@link #SLAM_HIT}, held, back.
     * The rise (2026-10-05, the user: "he should dig his way out of the ground instead of simply appearing... the arena ground
     * should react... take enough time to establish his enormous scale"): the floor cracks and heaves first (to
     * {@link #DIG_HANDS}), a hand bursts up through it and slams down flat, then the other ({@link #DIG_HAND_T} later each), and it
     * hauls itself up out of the floor on them in two heaves (from {@link #DIG_HEAVE}), the skull breaking the surface first.
     */
    public static final int RISE_T = 124, SINK_T = 44, SLAM_HIT = 21, SLAM_T = 40;
    public static final int DIG_HANDS = 34, DIG_HAND_T = 14, DIG_HEAVE = 70;
    /**
     * La Mano (GRAB): it rears up, its hand drawn back over its head (to GRAB_LOCK); wades through the floor toward you, the
     * ground tearing up ahead of it, bending its whole body over the arena (to GRAB_WADE); its hand comes down on your spot
     * (GRAB_CLOSE). Missed, the hand stays pressed into the floor a moment and goes back (GRAB_T_MISS). Caught (2026-10-06, the
     * user: "Oh shit, he actually caught me... pick the player up high enough that his enormous size becomes visually obvious"):
     * hoisted high over its head, forty blocks over the floor (GRAB_LIFT); brought round in front of its face (GRAB_BRING); its
     * eyes charge, point-blank, while you struggle (mash jump: {@link #struggle}) to GRAB_BLAST, when they fire into its own fist;
     * the fist goes up (GRAB_SLAM_UP) and comes down into the floor (GRAB_SLAM), and stays there, spent, to GRAB_T_HELD: your
     * window.
     */
    public static final int GRAB_LOCK = 22, GRAB_WADE = 54, GRAB_CLOSE = 66, GRAB_LIFT = 92, GRAB_BRING = 108, GRAB_BLAST = 144,
            GRAB_SLAM_UP = 154, GRAB_SLAM = 162, GRAB_T_HELD = 218, GRAB_T_MISS = 104;
    /**
     * La Embestida (LUNGE, 2026-10-06, the user: "I can essentially ignore him... sudden bursts of movement, terrifying
     * acceleration"): it rears back with its claws up, the line it will take torn open on the floor (to LUNGE_GO), and throws
     * itself along it through the floor, bent right over, its hands raking ahead of it (to LUNGE_STOP), and hauls itself
     * upright where it ended (LUNGE_T).
     */
    public static final int LUNGE_GO = 22, LUNGE_STOP = 36, LUNGE_T = 64;
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
    /** La Mano: where it wades to, the way it turns to face, which hand reaches, and whom it holds (-1 nobody). */
    private static final EntityDataAccessor<Vector3f> DATA_GRAB_TO = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_GRAB_YAW = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GRAB_HAND = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HELD = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CAUGHT = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.BOOLEAN);
    /** How far whoever it holds has struggled free (0..100). */
    private static final EntityDataAccessor<Integer> DATA_STRUGGLE = SynchedEntityData.defineId(SpiritGiantEntity.class, EntityDataSerializers.INT);
    private boolean releasing;
    /** Presses of jump it takes to get out of its fist. */
    private int struggleNeed = 12;
    /** Client: where it stood and faced last tick and this one (it wades about now: drawn between them). */
    private Vec3 baseO, baseN;
    private float facingO, facingN;
    /** Server: wading this tick (the floor torn up round it). */
    private double wading;
    private int ownerId = -1;
    private int life = 720;
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
        entityData.define(DATA_GRAB_TO, new Vector3f());
        entityData.define(DATA_GRAB_YAW, 0F);
        entityData.define(DATA_GRAB_HAND, 0);
        entityData.define(DATA_HELD, -1);
        entityData.define(DATA_CAUGHT, false);
        entityData.define(DATA_STRUGGLE, 0);
    }

    public void act(int act) {
        entityData.set(DATA_ACT, act);
        entityData.set(DATA_ACT_AT, (int) (level().getGameTime() & 0x7FFFFFFF));
        if (level() instanceof ServerLevel level) {
            Vec3 h = head(0);
            switch (act) {
                case RISE -> {
                    // the ground first: a rumble deep under the floor (the rest of the dig: tick)
                    level.playSound(null, base().x, base().y, base().z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 6.0F, 0.35F);
                    level.playSound(null, base().x, base().y, base().z, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.HOSTILE, 5.0F, 0.5F);
                }
                case GRAB -> {
                    level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.3F);
                    level.playSound(null, h.x, h.y, h.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5.0F, 0.4F);
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

    public void sink() {
        if (act() == SINK) return;
        letGo();
        act(SINK);
    }

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

    /** How far up out of the floor it has come (0 under it, 1 risen): dug out in two heaves on its planted hands. */
    public float risen(float partial) {
        float t = actTime(partial);
        return switch (act()) {
            case RISE -> 0.5F * smooth((t - DIG_HEAVE) / 26F) + 0.5F * smooth((t - DIG_HEAVE - 30) / (RISE_T - DIG_HEAVE - 30F));
            case SINK -> 1 - smooth(t / SINK_T);
            default -> 1;
        };
    }

    /** How far the floor has broken open round it as it digs (0 whole .. 1), for the cracks and the debris. */
    public float dug(float partial) {
        if (act() != RISE) return 1;
        return smooth(actTime(partial) / (DIG_HANDS + 8F));
    }

    /** How far below its risen place it is (blocks); ploughing through the floor it rides a little lower. */
    public double sunk(float partial) {
        double s = (1 - risen(partial)) * 46;
        if (act() == GRAB) {
            float t = actTime(partial);
            s += 3.0 * Math.sin(Math.PI * Mth.clamp((t - GRAB_LOCK) / (GRAB_WADE - GRAB_LOCK), 0, 1));
        }
        if (act() == LUNGE) s += 3.5 * Math.sin(Math.PI * Mth.clamp((actTime(partial) - LUNGE_GO + 4) / (LUNGE_STOP - LUNGE_GO + 8F), 0, 1));
        return s;
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Where it stands: on the floor, at the end it rose at; wading toward you through the floor in La Mano. */
    public Vec3 base() { return base(0); }

    public Vec3 base(float partial) {
        Vec3 at = level().isClientSide && baseN != null ? baseO.lerp(baseN, partial) : syncedBase();
        int a = act();
        if (a != GRAB && a != LUNGE) return at;
        Vector3f to = entityData.get(DATA_GRAB_TO);
        if (to.lengthSquared() == 0) return at;
        float t = actTime(partial);
        return at.lerp(new Vec3(to), a == GRAB ? wade(t) : lunge(t));
    }

    private Vec3 syncedBase() {
        Vector3f b = entityData.get(DATA_BASE);
        return b.lengthSquared() == 0 ? position() : new Vec3(b);
    }

    /** How far along its wade it is (0 .. 1). */
    private static double wade(float t) { return smooth((t - GRAB_LOCK) / (GRAB_WADE - GRAB_LOCK)); }

    /** How far along its lunge (0 .. 1): a burst, fastest at the start, braking into the end. */
    private static double lunge(float t) {
        float k = Mth.clamp((t - LUNGE_GO) / (LUNGE_STOP - LUNGE_GO), 0, 1);
        return 1 - (1 - k) * (1 - k) * (1 - k);
    }

    public float facing(float partial) {
        float yaw = level().isClientSide && baseN != null ? Mth.rotLerp(partial, facingO, facingN) : entityData.get(DATA_FACING);
        int a = act();
        if (a != GRAB && a != LUNGE) return yaw;
        float k = smooth(actTime(partial) / (a == LUNGE ? 12F : 18F));
        return yaw + Mth.wrapDegrees(entityData.get(DATA_GRAB_YAW) - yaw) * k;
    }

    /**
     * (2026-10-06) It hunts you: wades toward {@code to} through the floor at {@code speed} blocks a tick, turning to face where it
     * goes ({@code turn} degrees a tick; it turns first, then walks); only between its acts.
     */
    public void stalk(Vec3 to, double speed, float turn) {
        if (act() != IDLE) return;
        Vec3 b = syncedBase();
        Vec3 d = new Vec3(to.x - b.x, 0, to.z - b.z);
        double len = d.length();
        float yaw = entityData.get(DATA_FACING);
        if (len > 0.5) {
            float want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float diff = Mth.wrapDegrees(want - yaw);
            yaw += Mth.clamp(diff, -turn, turn);
            entityData.set(DATA_FACING, yaw);
            double go = Math.min(speed, len) * Math.max(0, Math.cos(Math.toRadians(diff)));
            if (go > 1e-3) {
                entityData.set(DATA_BASE, b.add(d.normalize().scale(go)).toVector3f());
                wading = go;
            }
        }
    }

    /** La Embestida: throws itself through the floor to {@code to}. */
    public void lungeTo(Vec3 to) {
        Vec3 from = syncedBase();
        entityData.set(DATA_GRAB_TO, to.toVector3f());
        entityData.set(DATA_GRAB_YAW, (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90F);
        act(LUNGE);
    }

    public Vec3 forward() { return forward(0); }

    public Vec3 forward(float partial) { return Vec3.directionFromRotation(0, facing(partial)); }

    public Vec3 right(float partial) {
        Vec3 f = forward(partial);
        return new Vec3(-f.z, 0, f.x);
    }

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
            case RISE -> t < DIG_HEAVE ? 3.0 : 3.0 * (1 - smooth((t - DIG_HEAVE - 30) / 30F));       // hunched over its hands as it hauls
            case GRAB -> grabLean(t);
            case LUNGE -> t < LUNGE_GO ? -3.2 * smooth(t / LUNGE_GO) : t < LUNGE_STOP ? -3.2 + 12.2 * smooth((t - LUNGE_GO) / (LUNGE_STOP - LUNGE_GO))
                    : 9 * (1 - smooth((t - LUNGE_STOP - 6) / (LUNGE_T - LUNGE_STOP - 6F)));
            default -> 0;
        };
    }

    /** La Mano: reared back, then bent right over the arena after you, straightening as it lifts you (or slowly, missed). */
    private double grabLean(float t) {
        if (t < GRAB_LOCK) return -2.2 * smooth(t / GRAB_LOCK);
        if (t < GRAB_WADE) return -2.2 + 9.2 * smooth((t - GRAB_LOCK) / (GRAB_WADE - GRAB_LOCK));
        if (t < GRAB_CLOSE) return 7 + 8 * smooth((t - GRAB_WADE) / (GRAB_CLOSE - GRAB_WADE));
        if (caught()) {
            // reared back holding you up; leaning in to its face; recoiling from the blast; bent right over into the slam, spent
            if (t < GRAB_LIFT) return 15 - 17 * smooth((t - GRAB_CLOSE) / (GRAB_LIFT - GRAB_CLOSE));
            if (t < GRAB_BRING) return -2 + 5.5 * smooth((t - GRAB_LIFT) / (GRAB_BRING - GRAB_LIFT));
            if (t < GRAB_BLAST) return 3.5;
            if (t < GRAB_SLAM_UP) return 3.5 - 4.5 * smooth((t - GRAB_BLAST) / (GRAB_SLAM_UP - GRAB_BLAST));
            if (t < GRAB_SLAM) return -1 + 11 * smooth((t - GRAB_SLAM_UP) / (GRAB_SLAM - GRAB_SLAM_UP));
            return 10 * (1 - smooth((t - GRAB_T_HELD + 22) / 22F));
        }
        if (t < GRAB_CLOSE + 12) return 15;
        return 15 * (1 - smooth((t - GRAB_CLOSE - 12) / (GRAB_T_MISS - GRAB_CLOSE - 12F)));
    }

    /** La Mano's reaching hand (0 its left, 1 its right). */
    public int grabHand() { return entityData.get(DATA_GRAB_HAND); }

    /** Whom it holds in its fist (an entity id), or -1. */
    public int held() { return entityData.get(DATA_HELD); }

    /** This La Mano caught someone (the hand lifts and throws, rather than pressing the floor and going back). */
    public boolean caught() { return entityData.get(DATA_CAUGHT); }

    /**
     * La Mano: it wades to {@code to} (turned to face {@code faceTo}) and its hand {@code hand} comes down on {@code mark}
     * (set by the caller once it has picked where you will be).
     */
    public void grab(Vec3 to, Vec3 faceTo, int hand) {
        Vec3 from = base();
        entityData.set(DATA_GRAB_TO, to.toVector3f());
        entityData.set(DATA_GRAB_YAW, (float) (Mth.atan2(faceTo.z - to.z, faceTo.x - to.x) * Mth.RAD_TO_DEG) - 90F);
        entityData.set(DATA_GRAB_HAND, hand);
        entityData.set(DATA_HELD, -1);
        entityData.set(DATA_CAUGHT, false);
        entityData.set(DATA_STRUGGLE, 0);
        handTarget(hand, faceTo);
        act(GRAB);
        if (from.distanceToSqr(to) < 1e-4) entityData.set(DATA_GRAB_TO, new Vector3f());
    }

    /** It has {@code e} in its fist (riding it, held where its hand is: {@link #positionRider}). */
    public void hold(net.minecraft.world.entity.Entity e) {
        e.stopRiding();
        e.startRiding(this, true);
        entityData.set(DATA_HELD, e.getId());
        entityData.set(DATA_CAUGHT, true);
    }

    /** Lets go of whoever it holds. */
    public void letGo() {
        int id = held();
        entityData.set(DATA_HELD, -1);
        net.minecraft.world.entity.Entity e = id >= 0 ? level().getEntity(id) : null;
        if (e != null && e.getVehicle() == this) {
            releasing = true;
            e.stopRiding();
            releasing = false;
        }
    }

    /** Whether it holds {@code e} and is not letting go (no climbing down out of its fist). */
    public boolean holds(net.minecraft.world.entity.Entity e) { return !releasing && held() == e.getId(); }

    /** At the end of La Mano: it stays where it waded to, facing where it reached. */
    private void settleGrab() {
        Vector3f to = entityData.get(DATA_GRAB_TO);
        if (to.lengthSquared() > 0) entityData.set(DATA_BASE, new Vector3f(to));
        entityData.set(DATA_FACING, entityData.get(DATA_GRAB_YAW));
        entityData.set(DATA_GRAB_TO, new Vector3f());
    }

    /**
     * Let go, whoever it held stays where its fist had them (on the floor after the slam, in the air when they wriggle
     * free). The default put them at this entity's own position, the arena's middle (where it is kept for its tracking
     * range), and the client does its own dismount: a slammed player came up in the middle of the arena.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) { return passenger.position(); }

    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger, MoveFunction move) {
        if (passenger.getId() == held()) {
            Vec3 h = hand(grabHand(), 0);
            // hanging out of the bottom of its fist (your eyes clear of its knuckles: you see its face)
            move.accept(passenger, h.x, Math.max(syncedBase().y + 0.05, h.y - 2.6), h.z);
            passenger.fallDistance = 0;
            return;
        }
        super.positionRider(passenger, move);
    }

    @Override
    public boolean shouldRiderSit() { return false; }

    /** The skull's centre (world), bobbing as it breathes, leaning with what it does. */
    public Vec3 head(float partial) {
        double breath = fury() >= 3 ? 0.11 : fury() == 2 ? 0.08 : 0.05;
        double bob = Math.sin((tickCount + partial) * breath) * 0.5;
        return base(partial).add(forward(partial).scale(HEAD_FWD + lean(partial))).add(0, HEAD_Y - sunk(partial) + bob - Math.abs(lean(partial)) * 0.25, 0);
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
            case RISE -> 0.35F * (1 - smooth((t - DIG_HEAVE) / (RISE_T - DIG_HEAVE)));
            case GRAB -> t < GRAB_LOCK ? 0.3F * smooth(t / GRAB_LOCK) : t < GRAB_CLOSE ? 0.3F - 0.75F * smooth((t - GRAB_LOCK) / (GRAB_CLOSE - GRAB_LOCK))
                    : caught() ? caughtPitch(t) : -0.45F * (1 - smooth((t - GRAB_CLOSE - 12) / 26F));
            case LUNGE -> t < LUNGE_GO ? 0.4F * smooth(t / LUNGE_GO) : t < LUNGE_STOP ? 0.4F - 0.8F * smooth((t - LUNGE_GO) / 6F)
                    : -0.4F * (1 - smooth((t - LUNGE_STOP) / (LUNGE_T - LUNGE_STOP)));
            default -> 0;
        };
    }

    /** Caught: looking up at you in its raised fist, square at you as its eyes charge, down into the slam. */
    private static float caughtPitch(float t) {
        if (t < GRAB_LIFT) return -0.45F + 0.95F * smooth((t - GRAB_CLOSE) / (GRAB_LIFT - GRAB_CLOSE));
        if (t < GRAB_BRING) return 0.5F - 0.5F * smooth((t - GRAB_LIFT) / (GRAB_BRING - GRAB_LIFT));
        if (t < GRAB_BLAST) return 0;
        if (t < GRAB_SLAM) return -0.6F * smooth((t - GRAB_BLAST) / (GRAB_SLAM - GRAB_BLAST));
        return -0.6F * (1 - smooth((t - GRAB_T_HELD + 22) / 22F));
    }

    /** Its eyes charging point-blank at whoever it holds (0..1), blazing as they fire (1.6), or -1. */
    public float charge(float partial) {
        if (act() != GRAB || !caught()) return -1;
        float t = actTime(partial);
        if (t < GRAB_BRING - 6 || t > GRAB_BLAST + 10) return -1;
        if (t >= GRAB_BLAST) return 1.6F;
        return Mth.clamp((t - GRAB_BRING + 6) / (GRAB_BLAST - GRAB_BRING + 6F), 0, 1);
    }

    /** How far whoever it holds has struggled free (0..1). */
    public float struggled() { return entityData.get(DATA_STRUGGLE) / 100F; }

    public void setStruggleNeed(int presses) { struggleNeed = Math.max(1, presses); }

    /**
     * One press of jump from whoever is in its fist, up to the moment its eyes fire: enough of them and they wriggle out of it
     * (returns true then; the boss sees it as the fist going empty).
     */
    public boolean struggle(net.minecraft.world.entity.Entity e) {
        if (act() != GRAB || !holds(e) || actTime(0) >= GRAB_BLAST - 1) return false;
        int s = Math.min(100, entityData.get(DATA_STRUGGLE) + Math.max(1, (int) Math.ceil(100.0 / struggleNeed)));
        entityData.set(DATA_STRUGGLE, s);
        if (s < 100) return false;
        letGo();
        return true;
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
        Vec3 f = forward(partial), r = right(partial);
        return head(partial).add(r.scale(x2 * V)).add(0, y1 * V, 0).add(f.scale(z2 * V));
    }

    /** Eye {@code side} (-1 its right, 1 its left): deep in its socket. */
    public Vec3 eye(int side, float partial) { return skull(side * EYE_OUT, -EYE_DOWN, EYE_FWD, partial); }

    /** Shoulder {@code i} (0 its left, 1 its right). */
    public Vec3 shoulder(int i, float partial) {
        double l = lean(partial) * 0.55;
        // bent right over (La Mano) the shoulders come down with the chest
        double down = act() == GRAB ? Math.max(0, lean(partial)) * 0.55 : 0;
        return base(partial).add(right(partial).scale((i == 0 ? 1 : -1) * SHOULDER_OUT)).add(forward(partial).scale(0.6 + l))
                .add(0, SHOULDER_Y - sunk(partial) - Math.abs(l) * 0.2 - down, 0);
    }

    /** A hand's place at rest: down on the floor in front of it, a little out. */
    public Vec3 handRest(int i) { return handRest(i, 0); }

    public Vec3 handRest(int i, float partial) {
        return base(partial).add(forward(partial).scale(12)).add(right(partial).scale((i == 0 ? 1 : -1) * 12)).add(0, 1.2, 0);
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
        Vec3 rest = handRest(i, partial).add(0, -sunk(partial), 0);
        int a = act();
        float t = actTime(partial);
        if (a == RISE) {
            // its hands first: each bursts up through the floor, high, claws open, and slams down flat on it; then it hauls the
            // rest of itself up on them
            Vec3 flat = handRest(i, partial);
            float at = DIG_HANDS + i * DIG_HAND_T;
            if (t < at) return flat.add(0, -16, 0);
            Vec3 burst = flat.add(forward(partial).scale(-2)).add(0, 11 + i, 0);
            if (t < at + 8) return flat.add(0, -16, 0).lerp(burst, smooth((t - at) / 8F));
            if (t < at + 13) return burst.add(0, Math.sin(t * 1.7 + i) * 0.4, 0);
            if (t < at + 18) {
                float k = (t - at - 13) / 5F;
                return burst.lerp(flat, k * k);
            }
            // pressing down as it heaves: a little give in the arms at each pull
            return flat.add(0, -0.4 * Math.abs(Math.sin(Math.PI * Mth.clamp((t - DIG_HEAVE) / 30F, 0, 2))), 0);
        }
        if (a == GRAB && i == grabHand()) {
            Vec3 mark = handMark(i).add(0, 0.8, 0);
            Vec3 high = shoulder(i, partial).add(forward(partial).scale(-3)).add(right(partial).scale((i == 0 ? 1 : -1) * 3)).add(0, 11, 0);
            if (t < GRAB_LOCK) return rest.lerp(high, smooth(t / GRAB_LOCK));
            if (t < GRAB_WADE) return high.add(0, Math.sin(t * 0.5) * 0.4, 0);
            if (t < GRAB_CLOSE) {
                float k = (t - GRAB_WADE) / (GRAB_CLOSE - GRAB_WADE);
                return high.lerp(mark, k * k).add(0, Math.sin(Math.PI * k) * 5, 0);
            }
            if (!caught()) {
                if (t < GRAB_CLOSE + 12) return mark;
                return mark.lerp(rest, smooth((t - GRAB_CLOSE - 12) / (GRAB_T_MISS - GRAB_CLOSE - 12F)));
            }
            // hoisted high over its head; brought round in front of its face; held there trembling as its eyes charge; the
            // recoil of the blast, up, and down into the floor on its new mark; planted there, then back
            Vec3 over = shoulder(i, partial).add(right(partial).scale((i == 0 ? 1 : -1) * 3)).add(forward(partial).scale(2)).add(0, 21, 0);
            Vec3 face = head(partial).add(forward(partial).scale(7.4)).add(0, -0.6, 0);
            Vec3 up = head(partial).add(forward(partial).scale(5)).add(right(partial).scale((i == 0 ? 1 : -1) * 2)).add(0, 8, 0);
            Vec3 slam = handMark(i).add(0, 0.9, 0);
            if (t < GRAB_LIFT) {
                double k = smooth((t - GRAB_CLOSE) / (GRAB_LIFT - GRAB_CLOSE));
                return mark.lerp(over, k).add(forward(partial).scale(Math.sin(Math.PI * k) * 3));
            }
            if (t < GRAB_BRING) return over.lerp(face, smooth((t - GRAB_LIFT) / (GRAB_BRING - GRAB_LIFT)));
            if (t < GRAB_BLAST) {
                double shake = 0.08 + 0.3 * (t - GRAB_BRING) / (GRAB_BLAST - GRAB_BRING);
                return face.add(right(partial).scale(Math.sin(t * 2.9) * shake)).add(0, Math.sin(t * 3.7) * shake, 0);
            }
            if (t < GRAB_SLAM_UP) return face.add(forward(partial).scale(1.2)).lerp(up, smooth((t - GRAB_BLAST) / (GRAB_SLAM_UP - GRAB_BLAST)));
            if (t < GRAB_SLAM) {
                float k = (t - GRAB_SLAM_UP) / (GRAB_SLAM - GRAB_SLAM_UP);
                return up.lerp(slam, k * k);
            }
            if (t < GRAB_T_HELD - 18) return slam;
            return slam.lerp(rest, smooth((t - GRAB_T_HELD + 18) / 18F));
        }
        if (a == LUNGE) {
            // claws up as it rears; raking the floor ahead of it as it throws itself along; planted, then back
            Vec3 up = shoulder(i, partial).add(forward(partial).scale(4)).add(right(partial).scale((i == 0 ? 1 : -1) * 3)).add(0, 9, 0);
            Vec3 rake = base(partial).add(forward(partial).scale(15)).add(right(partial).scale((i == 0 ? 1 : -1) * 6.5)).add(0, 1.0, 0);
            if (t < LUNGE_GO) return rest.lerp(up, smooth(t / LUNGE_GO));
            if (t < LUNGE_GO + 5) return up.lerp(rake, smooth((t - LUNGE_GO) / 5F));
            if (t < LUNGE_STOP + 8) return rake;
            return rake.lerp(rest, smooth((t - LUNGE_STOP - 8) / (LUNGE_T - LUNGE_STOP - 8F)));
        }
        if ((a == SWIPE_L && i == 0) || (a == SWIPE_R && i == 1)) {
            double r = swipeRadius();
            Vec3 f = forward(partial), rt = right(partial), b = base(partial);
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
            Vec3 high = head(partial).add(right(partial).scale((i == 0 ? 1 : -1) * 5.5)).add(forward(partial).scale(-1)).add(0, 9, 0);
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
            Vec3 wide = shoulder(i, partial).add(right(partial).scale((i == 0 ? 1 : -1) * 14)).add(forward(partial).scale(3)).add(0, 8, 0);
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
            Vec3 b = syncedBase();
            float fc = entityData.get(DATA_FACING);
            if (baseN == null) {
                baseN = b;
                facingN = fc;
            }
            baseO = baseN;
            facingO = facingN;
            baseN = b;
            facingN = fc;
            lookYawO = lookYaw;
            lookPitchO = lookPitch;
            float[] want = lookWant();
            float k = act() == LASER ? 0.3F : 0.12F;
            lookYaw += (want[0] - lookYaw) * k;
            lookPitch += (want[1] - lookPitch) * k;
            if (random.nextInt(2) == 0) {
                Vec3 h = head(0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, true, h.x + (random.nextDouble() - 0.5) * 12,
                        h.y + (random.nextDouble() - 0.5) * 12, h.z + (random.nextDouble() - 0.5) * 12, 0, 0.05, 0);
            }
            // (2026-10-06) between its acts it breathes: a deep, slow growl out of its skull, quicker the angrier it is, so you
            // hear where it is when it is behind you
            int breath = fury() >= 3 ? 46 : fury() == 2 ? 62 : 80;
            if (act() == IDLE && risen(0) > 0.9F && (tickCount + getId() * 7) % breath == 0) {
                Vec3 h = head(0);
                level().playLocalSound(h.x, h.y, h.z, SoundEvents.RAVAGER_AMBIENT, SoundSource.HOSTILE, 4.5F, 0.32F + random.nextFloat() * 0.06F, false);
                level().playLocalSound(h.x, h.y - 6, h.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0F, 0.5F, false);
            }
            // where it comes up through the floor: wisps of the dead
            if (risen(0) > 0.05F && random.nextInt(3) == 0) {
                Vec3 w = base();
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL, true, w.x + Math.cos(a) * 7.5, w.y + 0.3, w.z + Math.sin(a) * 5.5,
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
        if (act() == RISE && t < RISE_T) dig(level, (int) t);
        if (act() == LUNGE) {
            if (t >= LUNGE_GO - 2 && t < LUNGE_STOP + 2) plough(level);
            if ((int) t == LUNGE_GO) {
                Vec3 h = head(0);
                level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.3F);
                level.playSound(null, base().x, base().y, base().z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5.0F, 0.35F);
                PaponNetwork.shake(level, base(), 110, 1.0F, 20);
            }
            if (t >= LUNGE_T) {
                settleGrab();
                act(IDLE);
            }
        }
        if (wading > 0 && act() == IDLE) {
            // wading after you: the floor heaving round its waist
            if (tickCount % 3 == 0) {
                Vec3 w = base();
                Fx.dust(level, w.add(forward().scale(5)).add(right().scale((random.nextDouble() - 0.5) * 10)), 3.0, 4);
                Fx.scorch(level, w.add(right().scale((random.nextDouble() - 0.5) * 8)), 3.0F, 300);
            }
            if (tickCount % 12 == 0) {
                Vec3 w = base();
                level.playSound(null, w.x, w.y, w.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 4.0F, 0.35F);
                PaponNetwork.shake(level, w, 60, 0.25F, 6);
            }
        }
        wading = 0;
        if (act() == GRAB) {
            if (t >= GRAB_LOCK && t < GRAB_WADE && tickCount % 2 == 0) plough(level);
            if ((int) t == GRAB_CLOSE) {
                Vec3 at = handMark(grabHand());
                Fx.chunks(level, at.add(0, 0.5, 0), 22, 0.7F, 0x2C2438, 1.0);
                Fx.shock(level, at, 7F, 0x7CF0FF);
                PaponNetwork.shake(level, at, 80, 0.9F, 18);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5.0F, 0.45F);
            }
            if (t >= (caught() ? GRAB_T_HELD : GRAB_T_MISS)) {
                letGo();
                settleGrab();
                act(IDLE);
            }
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
        if (act() == RISE && t >= RISE_T) {
            Vec3 b = base();
            level.playSound(null, b.x, b.y + 20, b.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.35F);
            act(IDLE);
        }
        if (act() == SINK && t >= SINK_T) discard();
    }

    /**
     * The dig: the floor heaving and cracking over it (shakes building), dirt and slabs of the floor itself thrown up as its
     * hands burst through and as its skull and shoulders break the surface, dust pouring off it as it hauls itself out.
     */
    private void dig(ServerLevel level, int t) {
        Vec3 b = base();
        double floor = b.y;
        if (t < DIG_HANDS && t % 3 == 0) {
            float k = t / (float) DIG_HANDS;
            PaponNetwork.shake(level, b, 110, 0.2F + 0.5F * k, 5);
            Fx.dust(level, b.add((random.nextDouble() - 0.5) * 14 * k, 0.2, (random.nextDouble() - 0.5) * 10 * k), 2.5, 4);
            if (t % 9 == 0) level.playSound(null, b.x, floor, b.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 4.0F, 0.4F + k * 0.2F);
        }
        for (int i = 0; i < 2; i++) {
            if (t != DIG_HANDS + i * DIG_HAND_T) continue;
            // a hand bursts up through the floor
            Vec3 at = handRest(i);
            debris(level, new Vec3(at.x, floor, at.z), 7, 0.55);
            Fx.chunks(level, new Vec3(at.x, floor + 0.5, at.z), 26, 0.7F, 0x3A3046, 1.2);
            Fx.dust(level, new Vec3(at.x, floor, at.z), 3.5, 18);
            PaponNetwork.shake(level, at, 110, 0.9F, 14);
            level.playSound(null, at.x, floor, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5.0F, 0.5F);
            level.playSound(null, at.x, floor, at.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 6.0F, 0.5F);
        }
        for (int i = 0; i < 2; i++) {
            if (t != DIG_HANDS + i * DIG_HAND_T + 17) continue;
            // and slams down flat on it
            Vec3 at = handRest(i);
            Fx.shock(level, new Vec3(at.x, floor, at.z), 6F, 0x7CF0FF);
            PaponNetwork.shake(level, at, 90, 0.7F, 10);
            level.playSound(null, at.x, floor, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.3F);
        }
        if (t == DIG_HEAVE || t == DIG_HEAVE + 30) {
            // each heave: the floor round its waist bursts open
            debris(level, new Vec3(b.x, floor, b.z), t == DIG_HEAVE ? 12 : 8, 0.75);
            Fx.chunks(level, b.add(0, 1, 0), 40, 0.9F, 0x3A3046, 1.4);
            PaponNetwork.shake(level, b, 130, 1.1F, 22);
            level.playSound(null, b.x, floor, b.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6.0F, 0.35F);
            level.playSound(null, b.x, floor, b.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 8.0F, 0.4F);
        }
        if (t > DIG_HEAVE && t % 3 == 0) {
            // dirt pouring off its skull and shoulders as they come up
            Vec3 h = head(0);
            Fx.fall(level, h.add((random.nextDouble() - 0.5) * 9, 4, (random.nextDouble() - 0.5) * 9), 3, 1.5, 0.35F, 0x4A3E36);
            Fx.dust(level, b.add((random.nextDouble() - 0.5) * 16, 0.3, (random.nextDouble() - 0.5) * 12), 3.0, 6);
        }
    }

    /** Wading through the floor toward you: the ground torn up and thrown aside ahead of it. */
    private void plough(ServerLevel level) {
        Vec3 b = base();
        Vec3 front = b.add(forward().scale(6));
        Fx.chunks(level, front.add(0, 0.6, 0), 10, 0.7F, 0x3A3046, 1.0);
        Fx.dust(level, front, 3.5, 6);
        if (tickCount % 4 == 0) debris(level, front, 3, 0.5);
        Fx.scorch(level, b.add(right().scale((random.nextDouble() - 0.5) * 9)), 3.5F, 400);
        if (tickCount % 6 == 0) {
            PaponNetwork.shake(level, b, 100, 0.5F, 8);
            level.playSound(null, b.x, b.y, b.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 5.0F, 0.4F);
            level.playSound(null, b.x, b.y, b.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
    }

    /** Slabs of the floor itself, torn up and thrown out round {@code at}, tumbling, shattering where they land. */
    public void debris(ServerLevel level, Vec3 at, int n, double speed) {
        if (!(level.getEntity(ownerId) instanceof PaponBossEntity boss)) return;
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(at.x, at.y - 0.5, at.z);
        net.minecraft.world.level.block.state.BlockState floor = level.getBlockState(below);
        if (floor.isAir()) floor = com.groid.papon.registry.ModBlocks.MUERTOS_STONE.get().defaultBlockState();
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 v = new Vec3(Math.cos(a) * speed * (0.4 + random.nextDouble()), 0.55 + random.nextDouble() * 0.6, Math.sin(a) * speed * (0.4 + random.nextDouble()));
            BossProp p = BossProp.create(level, BossProp.Kind.DEBRIS, boss, at.add(Math.cos(a) * 2, 0.5, Math.sin(a) * 2)).debris(floor, at.y,
                    0.9F + random.nextFloat() * 1.3F).velocity(v).life(80);
            level.addFreshEntity(p);
        }
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
