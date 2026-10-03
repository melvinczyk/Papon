package com.groid.papon.client;

import com.groid.papon.PaponConfig;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
import com.groid.papon.entity.npc.VendorState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * The cutscenes, Block Factory's Bosses style: while the taquero unmasks (EXPOSED to RAGE) and
 * while the boss makes his entrance (LEAP_OUT, CHUG, the first second and a half of ROAR), every
 * player within {@link #RANGE} watches through a scripted camera with letterbox bars, the HUD and
 * hand hidden and their movement held.
 *
 * Shots are worked out each tick from the subject's synced state and its ticks in that state, so
 * the camera follows the same clock as the animations and needs no packets. The camera is a
 * client-only marker entity made the camera entity (so the player's own body is in the shot);
 * between ticks it is interpolated like any entity.
 */
public final class Cinematic {
    private static final double RANGE = 40;
    /** Ticks a cutscene survives without a subject (the vendor turning into the boss). */
    private static final int GRACE = 8;

    private static Marker camera;
    private static boolean active;
    private static int lost;
    @Nullable private static CameraType savedType;
    private static float bars;
    private static float barsO;
    private static float fov = 70;
    private static float roll;
    @Nullable private static Vec3 leapStart;
    /** Whoever the camera is on, for the subtitles. */
    @Nullable private static Entity subject;
    private static int lastBossState = -1;
    private static int lastQteState = -1;
    /** The boss whose eyes the camera is (the phase-two point-of-view shot), or null. */
    @Nullable private static PaponBossEntity pov;
    private static float vignette;
    private static float vignetteO;
    private static float flash;
    private static float flashO;

    private Cinematic() {}

    public static boolean isActive() {
        return active;
    }

    public static float bars(float partial) {
        return Mth.lerp(partial, barsO, bars);
    }

    public static float fov() {
        return fov;
    }

    public static float roll() {
        return roll;
    }

    /** True while the camera looks out of this boss's eyes (he is not drawn; the player is). */
    public static boolean isPovOf(Entity e) {
        return active && pov != null && pov == e;
    }

    /** The QTE shot: over the held player's shoulder onto his face (the player stays in shot). */
    private static boolean qte;

    public static boolean qteActive() {
        return active && qte;
    }

    public static boolean povActive() {
        return active && pov != null;
    }

    /** The red, pulsing edge of the point-of-view shot, 0..1. */
    public static float vignette(float partial) {
        return Mth.lerp(partial, vignetteO, vignette);
    }

    /** The white-out over the jump into the potato dome, 0..1. */
    public static float flash(float partial) {
        return Mth.lerp(partial, flashO, flash);
    }

    /** The encoded speech line of whoever the cutscene is about ("" when quiet). */
    public static String subjectSpeech() {
        if (!active || subject == null || !subject.isAlive()) return "";
        return subject instanceof com.groid.papon.entity.Speech.Speaker s ? s.getSpeech() : "";
    }

    private record Shot(Vec3 eye, Vec3 lookAt, float fov, float roll, boolean cut) {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        barsO = bars;
        vignetteO = vignette;
        flashO = flash;
        pov = null;
        if (mc.level == null || mc.player == null || !PaponConfig.CINEMATICS.get()) {
            if (active) stop(mc);
            bars = Math.max(0, bars - 0.1F);
            return;
        }
        Shot shot = mc.player.isAlive() ? findShot(mc) : null;
        if (shot != null) {
            lost = 0;
            if (!active) start(mc, shot);
            moveCamera(shot);
        } else if (active && ++lost > GRACE) {
            stop(mc);
        }
        bars = Mth.clamp(bars + (active ? 0.12F : -0.08F), 0, 1);
        if (!active) pov = null;
        vignette = Mth.clamp(vignette + (pov != null ? 0.1F : -0.1F), 0, 1);
        if (flashTarget < 0) flash = Math.max(0, flash - 0.08F);
        else flash = flashTarget;
        flashTarget = -1;
    }

    private static float flashTarget = -1;

    @Nullable
    private static Shot findShot(Minecraft mc) {
        Vec3 me = mc.player.position();
        AABB box = new AABB(me, me).inflate(RANGE);
        PaponBossEntity boss = mc.level.getEntitiesOfClass(PaponBossEntity.class, box).stream()
                .min(Comparator.comparingDouble(b -> b.distanceToSqr(me))).orElse(null);
        if (boss != null) {
            Shot s = bossShot(boss);
            if (s != null) {
                subject = boss;
                return pov != null ? s : unblock(mc, s, boss);
            }
        }
        TacoVendorEntity vendor = mc.level.getEntitiesOfClass(TacoVendorEntity.class, box).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(me))).orElse(null);
        Shot s = vendor == null ? null : vendorShot(vendor);
        if (s != null) subject = vendor;
        return s;
    }

    /** The state the current fallback angle was picked in, and which one it was (see {@link #unblock}). */
    private static int unblockState = -1;
    private static int unblockPick;

    /**
     * Keeps the subject in view: if a wall, a tree or the van stands between the camera and him, the
     * same shot from the other side of him, then craned up, then craned up on the other side, then
     * high above; only if all of those are blocked does the camera come in to just short of whatever
     * is in the way (never closer than 2.5 blocks). The pick sticks for the rest of the state, so the
     * camera does not hop between angles.
     */
    private static Shot unblock(Minecraft mc, Shot s, PaponBossEntity subject) {
        int state = subject.getBossState().ordinal();
        if (unblockState != state) {
            unblockState = state;
            unblockPick = 0;
        }
        Vec3 f = Vec3.directionFromRotation(0, subject.getYRot());
        Vec3 right = new Vec3(-f.z, 0, f.x);
        double side = s.eye().subtract(subject.position()).dot(right);
        Vec3 mirrored = s.eye().subtract(right.scale(2 * side));
        Vec3[] options = {s.eye(), mirrored, s.eye().add(0, 3, 0), mirrored.add(0, 3, 0), s.eye().add(0, 6, 0)};
        for (int i = unblockPick; i < options.length; i++) {
            if (clear(mc, s.lookAt(), options[i], subject)) {
                boolean changed = i != unblockPick;
                unblockPick = i;
                return i == 0 ? s : new Shot(options[i], s.lookAt(), s.fov(), s.roll(), s.cut() || changed);
            }
        }
        Vec3 eye = options[Math.min(unblockPick, options.length - 1)];
        Vec3 hit = firstHit(mc, s.lookAt(), eye, subject);
        Vec3 back = eye.subtract(s.lookAt()).normalize();
        Vec3 in = hit.subtract(back.scale(0.4));
        if (in.distanceTo(s.lookAt()) < 2.5) in = s.lookAt().add(back.scale(2.5));
        return new Shot(in, s.lookAt(), s.fov(), s.roll(), s.cut());
    }

    private static Vec3 toVan(com.groid.papon.vehicle.CaravanEntity van, Vec3 p) {
        Vec3 r = p.subtract(van.position());
        return new Vec3(r.dot(van.left()), r.y, r.dot(van.forward()));
    }

    private static boolean clear(Minecraft mc, Vec3 from, Vec3 to, Entity subject) {
        return firstHit(mc, from, to, subject) == to;
    }

    /** The first block or van between the two points (from the subject outward), or {@code to} if none. */
    private static Vec3 firstHit(Minecraft mc, Vec3 from, Vec3 to, Entity subject) {
        Vec3 best = to;
        double bestD = from.distanceToSqr(to);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && from.distanceToSqr(hit.getLocation()) < bestD) {
            best = hit.getLocation();
            bestD = from.distanceToSqr(best);
        }
        for (var van : mc.level.getEntitiesOfClass(com.groid.papon.vehicle.CaravanEntity.class, new AABB(from, to).inflate(4))) {
            if (van == subject.getVehicle()) continue;
            // the van's real footprint: its box in van space (x left, z forward), the segment taken there
            Vec3 lf = toVan(van, from);
            Vec3 lt = toVan(van, to);
            AABB box = new AABB(-com.groid.papon.vehicle.CaravanEntity.HALF_WIDTH, 0, -com.groid.papon.vehicle.CaravanEntity.HALF_LENGTH,
                    com.groid.papon.vehicle.CaravanEntity.HALF_WIDTH, 2.1, com.groid.papon.vehicle.CaravanEntity.HALF_LENGTH);
            if (box.contains(lf)) continue;
            var at = box.clip(lf, lt);
            if (at.isEmpty()) continue;
            double k = at.get().distanceTo(lf) / Math.max(1e-6, lt.distanceTo(lf));
            Vec3 world = from.lerp(to, k);
            if (from.distanceToSqr(world) < bestD) {
                best = world;
                bestD = from.distanceToSqr(best);
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the shots

    @Nullable
    private static Shot vendorShot(TacoVendorEntity v) {
        VendorState s = v.getVendorState();
        int t = v.ticksInState();
        Vec3 f = Vec3.directionFromRotation(0, v.getYRot());
        Vec3 right = new Vec3(-f.z, 0, f.x);
        Vec3 face = v.position().add(0, 1.55, 0);
        Vec3 up = new Vec3(0, 1, 0);
        return switch (s) {
            case EXPOSED -> new Shot(face.add(f.scale(5.4 - 1.4 * t / 60.0)).add(right.scale(0.7)).add(0, 0.1, 0), face,
                    50, 0, t == 0);
            case UNMASK -> t < 46
                    ? new Shot(face.add(f.scale(3.3)).add(right.scale(-0.9)).add(0, -0.1, 0), face.add(0, -0.15, 0), 45, 0, t == 0)
                    : new Shot(face.add(f.scale(3.4 + (t - 46) / 54.0 * 1.6)).add(up.scale(0.2 + (t - 46) / 54.0 * 0.5)),
                    face.add(0, 0.35 + Math.min(1, (t - 46) / 20.0) * 0.6, 0), 50, 0, t == 46);
            case REALIZE -> new Shot(face.add(f.scale(3.7 - t / 60.0 * 0.6)).add(right.scale(0.25)), face.add(0, 0.2, 0),
                    42, (float) Math.sin(t * 0.15) * 4F, t == 0);
            case RAGE -> t < 34
                    ? new Shot(face.add(f.scale(3.4 - t / 34.0 * 0.4)), face.add(0, 0.1, 0), 42, (float) Math.sin(t * 0.9) * t * 0.12F, t == 0)
                    : new Shot(face.add(f.scale(2.0 + Math.min(1, (t - 34) / 5.0) * 4.8)).add(up.scale(0.4)), face.add(0, 0.2, 0),
                    60, 0, false);
            default -> null;
        };
    }

    @Nullable
    private static Shot bossShot(PaponBossEntity b) {
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (s.ordinal() != lastBossState) {
            lastBossState = s.ordinal();
            if (s == BossState.LEAP_OUT) leapStart = b.position();
        }
        Vec3 f = Vec3.directionFromRotation(0, b.getYRot());
        Vec3 right = new Vec3(-f.z, 0, f.x);
        Vec3 body = b.position().add(0, 1.3, 0);
        qte = false;
        if (QteClient.focus(b)) {
            // in slow motion, over your shoulder, his face filling the frame; pushing in as it goes on
            qte = true;
            float k = b.isRisen() ? 1.75F / 1.5F : 1F;
            // from off his shoulder, three-quarters on: his face and you in his fist both in frame, clear of your head
            Vec3 face = b.position().add(f.scale(0.5 * k)).add(0, 1.9 * k, 0);
            Vec3 eye = b.position().add(f.scale(4.2 * k)).add(right.scale(-3.4 * k)).add(0, 2.8 * k, 0);
            float push = Math.min(1F, t / 80F);
            eye = eye.lerp(face, 0.2 * push);
            boolean cut = s.ordinal() != lastQteState;
            lastQteState = s.ordinal();
            return new Shot(eye, face, 46F - 10F * push, 0, cut);
        }
        lastQteState = -1;
        Shot p2 = phaseTwoShot(b, s, t, f, right);
        if (p2 != null) return p2;
        return switch (s) {
            case LEAP_OUT -> {
                Vec3 base = leapStart != null ? leapStart : b.position();
                Vec3 eye = base.add(f.scale(4.5)).add(right.scale(8.5)).add(0, 2.2, 0);
                yield new Shot(eye, body, 60, 0, t == 0);
            }
            case CHUG -> {
                double orbit = Math.toRadians(55 + t * 0.25);     // from his side, clear of the line
                Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                yield new Shot(b.position().add(dir.scale(6.0)).add(0, 0.6, 0), body.add(0, 0.5, 0), 55, 0, t == 0);
            }
            case ROAR -> t < 30
                    ? new Shot(b.position().add(f.scale(6.2 - Math.min(1, t / 6.0) * 1.4)).add(0, 1.2, 0), body.add(0, 0.7, 0), 55, 0, t == 0)
                    : null;
            default -> null;
        };
    }

    /**
     * Phase two's cutscene, beat by beat:
     *   P2_KNEEL   a low wide shot as he drops to his knee; then (from 1.4 s) his eyes: looking down at
     *              the ground where the blood drips, the edges red and pulsing with the heartbeat, until
     *              his head comes up to you (from 2.8 s)
     *   P2_TALK    close on his face, the blood on his lip: "Okay... enough playing around."; tilting up
     *              as he gets to his feet
     *   P2_SEASON  a slow orbit as the shaker comes down and salts him, pushing in when he bakes
     *   P2_SCREAM  in tight, then a whip back as he screams; a wide low angle on the wings; white-out
     *   P2_ARRIVE  the white clears on him hanging in the air of his potato dimension
     */
    @Nullable
    private static Shot phaseTwoShot(PaponBossEntity b, BossState s, int t, Vec3 f, Vec3 right) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 face = b.position().add(0, 1.1, 0);
        return switch (s) {
            case P2_KNEEL -> {
                if (t < 28) {
                    yield new Shot(b.position().add(right.scale(6.5)).add(f.scale(3.0)).add(0, 0.6, 0), face, 55, 0, t == 0);
                }
                pov = b;
                Vec3 eye = b.position().add(f.scale(1.05)).add(0, 1.35, 0);
                Vec3 down = b.position().add(f.scale(2.4)).add(0, 0.05, 0);
                Vec3 you = mc.player.getEyePosition();
                double up = t < 56 ? 0 : Math.min(1, (t - 56) / 20.0);
                up = up * up * (3 - 2 * up);
                Vec3 look = down.lerp(you, up);
                float sway = (float) Math.sin(t * 0.12) * 5F;
                yield new Shot(eye.add(0, Math.sin(t * 0.3) * 0.02, 0), look, 62, sway, t == 28);
            }
            case P2_TALK -> {
                double rise = t < 40 ? 0 : Math.min(1, (t - 40) / 18.0);
                Vec3 at = face.add(0, rise * 0.5, 0);
                yield new Shot(at.add(f.scale(2.6 - t / 70.0 * 0.5)).add(0, -0.25, 0), at.add(0, 0.05, 0), 42, 0, t == 0);
            }
            case P2_SEASON -> {
                double orbit = Math.toRadians(-40 + t * 0.9);
                Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                double dist = t < 70 ? 6.5 : 6.5 - Math.min(1, (t - 70) / 12.0) * 2.5;
                Vec3 lookAt = b.position().add(0, t < 66 ? 2.4 : 1.3, 0);
                if (t >= 80 && t < 87) flashTarget = 0.85F * (1F - (t - 80) / 7F);
                yield new Shot(b.position().add(dir.scale(dist)).add(0, 1.0, 0), lookAt, t >= 80 && t < 84 ? 80 : 58,
                        t >= 80 && t < 90 ? (float) Math.sin(t * 2.1) * 4F : 0, t == 0);
            }
            case P2_SCREAM -> {
                if (t >= 56) flashTarget = Math.min(1F, (t - 56) / 12F);
                if (t < 15) yield new Shot(face.add(f.scale(2.2)).add(0, 0.2, 0), face.add(0, 0.25, 0), 40, 0, t == 0);
                if (t < 40) {
                    double k = Math.min(1, (t - 15) / 8.0);
                    k = 1 - (1 - k) * (1 - k);
                    // the FOV punches out with the scream, then settles
                    yield new Shot(face.add(f.scale(2.2 + k * 7.5)).add(0, 0.2 + k * 1.2, 0), face.add(0, 0.6, 0), t < 19 ? 105 : 72,
                            (float) Math.sin(t * 1.7) * 5F, false);
                }
                double orbit = Math.toRadians(30 + (t - 40) * 1.2);
                Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                yield new Shot(b.position().add(dir.scale(9.0)).add(0, -0.2, 0), b.position().add(0, 2.2, 0), 64, 0, t == 40);
            }
            case P2_ARRIVE -> {
                if (t < 14) flashTarget = 1F - t / 14F;
                Vec3 body = b.position().add(0, 1.4, 0);
                yield new Shot(body.add(f.scale(9.0 - t / 50.0 * 2.0)).add(0, -2.5, 0), body, 60, 0, t == 0);
            }
            default -> null;
        };
    }

    // ------------------------------------------------------------------ the camera

    private static void start(Minecraft mc, Shot shot) {
        active = true;
        savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        camera = new Marker(EntityType.MARKER, mc.level);
        place(shot, true);
        mc.setCameraEntity(camera);
    }

    private static void stop(Minecraft mc) {
        active = false;
        leapStart = null;
        lastBossState = -1;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        camera = null;
        subject = null;
        fov = 70;
        roll = 0;
    }

    private static void moveCamera(Shot shot) {
        if (camera == null) return;
        place(shot, shot.cut());
        fov += (shot.fov() - fov) * 0.3F;
        roll += (shot.roll() - roll) * 0.4F;
    }

    private static void place(Shot shot, boolean cut) {
        Vec3 eye = shot.eye();
        Vec3 d = shot.lookAt().subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        Entity c = camera;
        float prevYaw = c.getYRot();
        // keep yaw continuous so the interpolation never spins the long way round
        yaw = prevYaw + Mth.wrapDegrees(yaw - prevYaw);
        if (cut) {
            c.setPos(eye.x, eye.y, eye.z);
            c.xo = eye.x;
            c.yo = eye.y;
            c.zo = eye.z;
            c.xOld = eye.x;
            c.yOld = eye.y;
            c.zOld = eye.z;
            c.setYRot(yaw);
            c.setXRot(pitch);
            c.yRotO = yaw;
            c.xRotO = pitch;
        } else {
            c.xo = c.getX();
            c.yo = c.getY();
            c.zo = c.getZ();
            c.xOld = c.getX();
            c.yOld = c.getY();
            c.zOld = c.getZ();
            c.yRotO = c.getYRot();
            c.xRotO = c.getXRot();
            Vec3 now = c.position().lerp(eye, 0.5);
            c.setPos(now.x, now.y, now.z);
            c.setYRot(Mth.lerp(0.5F, c.getYRot(), yaw));
            c.setXRot(Mth.lerp(0.5F, c.getXRot(), pitch));
        }
    }
}
