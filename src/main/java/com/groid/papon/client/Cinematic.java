package com.groid.papon.client;

import com.groid.papon.PaponConfig;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.VoiceLines;
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
    /** A boss's cutscene reaches everyone in his coliseum (the sand is nearly 60 blocks end to end; the band stand is at its edge). */
    private static final double BOSS_RANGE = 80;
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

    /** Phase three's cutscene is on screen. */
    public static boolean inPhaseThree() {
        return active && subject instanceof PaponBossEntity b && b.getBossState().phaseThree();
    }

    /**
     * Whether {@code e} is kept out of the shot: through his phase-two cutscene on the ground (kneel to
     * scream) every other mob within 10 blocks of him disappears so nothing stands between the camera and
     * him, the van included (not you in his point of view, where you are what he sees).
     */
    public static boolean hidden(Entity e) {
        if (!active || !(subject instanceof PaponBossEntity b) || e == b || !b.getBossState().silent()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player && povActive()) return false;
        return e.distanceToSqr(b) <= 10 * 10;
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
        return active && (qte || duelShot);
    }

    /** The guitar duel's shot: over the challenger's shoulder at him shredding; no letterbox over the fretboard. */
    private static boolean duelShot, duelWas, danceWas;

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

    private static double smooth(double k) {
        return k * k * (3 - 2 * k);
    }

    /** A camera position for a tick; {@code free}: placed in the open on purpose (a wide shot of the stands), not to be moved off walls. */
    private record Shot(Vec3 eye, Vec3 lookAt, float fov, float roll, boolean cut, boolean free) {
        Shot(Vec3 eye, Vec3 lookAt, float fov, float roll, boolean cut) { this(eye, lookAt, fov, roll, cut, false); }
    }

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
        blurWant = 0;
        Shot shot = mc.player.isAlive() ? findShot(mc) : null;
        if (shot != null) {
            lost = 0;
            if (!active) start(mc, shot);
            moveCamera(shot);
        } else if (active && ++lost > GRACE) { stop(mc); }
        bars = Mth.clamp(bars + (active && !duelShot ? 0.12F : -0.15F), 0, 1);
        if (!active) pov = null;
        vignette = Mth.clamp(vignette + (pov != null ? 0.1F : -0.1F), 0, 1);
        if (flashTarget < 0) flash = Math.max(0, flash - 0.08F);
        else flash = flashTarget;
        flashTarget = -1;
        if (!active) blurWant = 0;
        blurNow += (blurWant - blurNow) * (blurWant > blurNow ? 0.5F : 0.25F);
        if (blurNow < 0.2F && blurWant == 0) blurNow = 0;
        BlurFx.set(blurNow);
    }

    private static float flashTarget = -1;
    /** The blur a shot wants this tick (his swimming vision), and what is on screen (eased). */
    private static float blurWant, blurNow;

    @Nullable
    private static Shot findShot(Minecraft mc) {
        Vec3 me = mc.player.position();
        AABB box = new AABB(me, me).inflate(RANGE);
        PaponBossEntity boss = mc.level.getEntitiesOfClass(PaponBossEntity.class, new AABB(me, me).inflate(BOSS_RANGE),
                        b -> b.inColiseum() || b.distanceToSqr(me) < RANGE * RANGE).stream()
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
        if (s.free()) return s;
        int state = subject.getBossState().ordinal();
        if (unblockState != state || s.cut()) {
            // a new shot: look again from its own angle
            unblockState = state;
            unblockPick = 0;
        }
        Vec3 f = Vec3.directionFromRotation(0, subject.getYRot());
        Vec3 right = new Vec3(-f.z, 0, f.x);
        double side = s.eye().subtract(subject.position()).dot(right);
        Vec3 mirrored = s.eye().subtract(right.scale(2 * side));
        Vec3[] options = {s.eye(), mirrored, s.eye().add(0, 3, 0), mirrored.add(0, 3, 0), s.eye().add(0, 6, 0)};
        for (int i = unblockPick; i < options.length; i++) {
            if (clearWithMargin(mc, s.lookAt(), options[i], subject)) {
                boolean changed = i != unblockPick;
                unblockPick = i;
                return i == 0 ? s : new Shot(options[i], s.lookAt(), s.fov(), s.roll(), s.cut() || changed);
            }
        }
        // round the subject at the same distance and height, a twelfth of a turn at a time, then nearer
        Vec3 rel = s.eye().subtract(s.lookAt());
        for (double scale : new double[]{1.0, 0.75, 0.55}) {
            for (int k = 1; k < 12; k++) {
                double a = Math.toRadians(30 * ((k + 1) / 2) * (k % 2 == 0 ? -1 : 1));
                Vec3 r = new Vec3(rel.x * Math.cos(a) - rel.z * Math.sin(a), rel.y, rel.x * Math.sin(a) + rel.z * Math.cos(a)).scale(scale);
                Vec3 eye = s.lookAt().add(r);
                if (clearWithMargin(mc, s.lookAt(), eye, subject)) return new Shot(eye, s.lookAt(), s.fov(), s.roll(), s.cut());
            }
        }
        // nothing clear: just in front of whatever is in the way (never inside it), however close that is
        Vec3 eye = options[0];
        Vec3 hit = firstHit(mc, s.lookAt(), eye, subject);
        Vec3 back = eye.subtract(s.lookAt()).normalize();
        Vec3 in = hit.subtract(back.scale(0.5));
        if (in.distanceTo(s.lookAt()) < 0.8) in = s.lookAt().add(back.scale(0.8));
        return new Shot(in, s.lookAt(), s.fov(), s.roll(), s.cut());
    }

    /** Clear from the subject to the eye, and half a block past it (the near plane must not dip into a wall). */
    private static boolean clearWithMargin(Minecraft mc, Vec3 from, Vec3 eye, Entity subject) {
        Vec3 past = eye.add(eye.subtract(from).normalize().scale(0.5));
        return clear(mc, from, past, subject);
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
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));       // null: the coliseum's player-only railing is no wall to a camera
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
        duelShot = false;
        if (QteClient.duel(b) && Minecraft.getInstance().player != null) {
            // the duel: low over your shoulder, him shredding in front of you, the camera drifting slowly from side to side
            duelShot = true;
            boolean cut = !duelWas;
            duelWas = true;
            var me = Minecraft.getInstance().player;
            Vec3 toHim = b.position().subtract(me.position()).multiply(1, 0, 1);
            toHim = toHim.lengthSqr() < 1e-4 ? f.scale(-1) : toHim.normalize();
            Vec3 side = new Vec3(-toHim.z, 0, toHim.x);
            double drift = Math.sin(b.tickCount * 0.025) * 1.4;
            Vec3 eye = me.position().subtract(toHim.scale(3.2)).add(side.scale(1.5 + drift)).add(0, 2.1, 0);
            return new Shot(eye, b.position().add(0, b.getBbHeight() * 0.55, 0), 64, 0, cut);
        }
        duelWas = false;
        if (QteClient.dancing(b) && Minecraft.getInstance().player != null) {
            // the dance-off: a slow circle round the two of you, a little above, both of you in the frame
            duelShot = true;
            boolean cut = !danceWas;
            danceWas = true;
            var me = Minecraft.getInstance().player;
            Vec3 mid = b.position().lerp(me.position(), 0.5);
            Vec3 across = b.position().subtract(me.position()).multiply(1, 0, 1);
            across = across.lengthSqr() < 1e-4 ? f : across.normalize();
            Vec3 side = new Vec3(-across.z, 0, across.x);
            double ang = Math.toRadians(-35 + 70 * Math.sin(b.tickCount * 0.012));
            Vec3 dir = side.scale(Math.cos(ang)).add(across.scale(Math.sin(ang)));
            double far = 8 + b.getBbHeight() * 1.6;
            return new Shot(mid.add(dir.scale(far)).add(0, 2.4 + b.getBbHeight() * 0.45, 0), mid.add(0, b.getBbHeight() * 0.35, 0), 64, 0, cut);
        }
        danceWas = false;
        Shot p4 = phaseFourShot(b, s, t);
        if (p4 != null) return p4;
        Shot p3 = phaseThreeShot(b, s, t, f, right);
        if (p3 != null) return b.isAscended() ? ascendedRoom(b, p3) : p3;
        Shot col = coliseumShot(b, s, t, f, right);
        if (col != null) return col;
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
     *              the ground where the blood drips, the edges red and pulsing with the heartbeat; across
     *              to his left hand as it comes up (3 s), bleeding too; then up to you (from 4.8 s)
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
                if (t < 28) { yield new Shot(b.position().add(right.scale(6.5)).add(f.scale(3.0)).add(0, 0.6, 0), face, 55, 0, t == 0); }
                pov = b;
                // his eyes: down at the blood on the ground (to 3 s), across to his left hand as it comes up in
                // front of them (3.0-3.6 s): bleeding too; held on it, closer, trembling (to 4.8 s); then up to you
                Vec3 eye = b.position().add(f.scale(1.05)).add(0, 1.35, 0);
                Vec3 down = b.position().add(f.scale(2.4)).add(0, 0.05, 0);
                Vec3 drawn = BossRenderer.handPos(b);       // where it really is, once it is drawn (from 2.8 s)
                Vec3 hand = drawn != null ? drawn : b.position().add(f.scale(1.75)).add(right.scale(-0.3)).add(0, 0.85, 0);
                Vec3 you = mc.player.getEyePosition();
                Vec3 look;
                float fov = 62;
                if (t < 60) {
                    look = down;
                } else if (t < 96) {
                    double k = smooth(Math.min(1, (t - 60) / 12.0));
                    look = down.lerp(hand, k).add(0, t >= 72 ? Math.sin(t * 1.9) * 0.012 : 0, 0);
                    fov = 62 - (float) (smooth(Math.min(1, Math.max(0, (t - 70) / 22.0))) * 16);
                } else {
                    double k = smooth(Math.min(1, (t - 96) / 22.0));
                    look = hand.lerp(you, k);
                    fov = 46 + (float) k * 16;
                }
                float sway = (float) Math.sin(t * 0.12) * 5F;
                yield new Shot(eye.add(0, Math.sin(t * 0.3) * 0.02, 0), look, fov, sway, t == 28);
            }
            case P2_TALK -> {
                int up = VoiceLines.beat("talk.enough", 40);
                double rise = t < up ? 0 : Math.min(1, (t - up) / 18.0);
                // eyes to mouth in frame: aimed between them, far enough back that his 1.5x face fits
                Vec3 at = face.add(0, rise * 0.5 - 0.3, 0);
                double push = t / (double) VoiceLines.beat("talk.end", 70);
                yield new Shot(at.add(f.scale(3.6 - push * 0.5)).add(0, -0.1, 0), at.add(0, 0.05, 0), 42, 0, t == 0);
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
                int out = VoiceLines.beat("scream.end", 70) - 14;          // the white-out as the scream ends
                if (t >= out) flashTarget = Math.min(1F, (t - out) / 12F);
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

    /**
     * El Coliseo Papa, beat by beat:
     *   P2_ARRIVE    from high over the stands, craning down into the bowl as he drops into it; low
     *                on him with the crowd behind; over his shoulder as he points at the band stand
     *   P2_NOTICE    the band, fast asleep; then his face
     *   P2_MARCH     tracking alongside as he strides over
     *   P2_WAKE      over his shoulder as the spatula goes up; side on for the whack and the singer going up
     *                in the air; the band jolting awake from out front; his satisfied face
     *   P2_WAIT      him tapping his foot; a slow pan along the band getting dressed; again
     *   P2_KICK      on the guitarron and the boot
     *   P2_COUNT     close on the singer counting in, pushing in
     *   P2_SHOWTIME  the guitar: in on his hands, tilting up to his face, the shades
     *   P2_APOLOGY   the two of you as he comes over; on him apologising; the aura: a slow low orbit
     *   P2_RESUME    close: "Where were we?"
     *   P2_HYPE      over his shoulder to the roaring stands as he cups his ear; round him as he turns to
     *                the other side; craning up through the raise-the-roof with the bowl behind him; from
     *                behind you as he points you out; the FOV punch as the place erupts and the singing starts
     *   P2_SOLO      (mid-fight) the singer's guitar solo: in on his hands, up to his face, then
     *                from behind him out over the sand at the boss air-guitaring along
     */
    @Nullable
    private static Shot coliseumShot(PaponBossEntity b, BossState s, int t, Vec3 f, Vec3 right) {
        if (!b.inColiseum() || !(s == BossState.P2_ARRIVE || s.coliseum() || s == BossState.P2_SOLO || s == BossState.P2_CUTAWAY)) return null;
        Minecraft mc = Minecraft.getInstance();
        // he is risen (1.75x) by now: his face is high on him
        Vec3 body = b.position().add(0, b.getBbHeight() * 0.55, 0);
        Vec3 face = b.position().add(0, b.getBbHeight() * 0.78, 0);
        java.util.List<com.groid.papon.boss.MariachiEntity> band = mc.level.getEntitiesOfClass(com.groid.papon.boss.MariachiEntity.class,
                b.getBoundingBox().inflate(64), e -> true);
        com.groid.papon.boss.MariachiEntity singer = band.stream().filter(m -> m.getRole().singer()).findFirst().orElse(null);
        Vec3 stage = band.isEmpty() ? b.position().add(f.scale(10))
                : band.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / band.size());
        Vec3 stageFacing = singer != null ? Vec3.directionFromRotation(0, singer.getYRot()) : new Vec3(0, 0, 1);
        Vec3 stageSide = new Vec3(-stageFacing.z, 0, stageFacing.x);
        Vec3 you = mc.player.position().add(0, 1.4, 0);
        switch (s) {
            case P2_ARRIVE -> {
                if (t < 14) flashTarget = 1F - t / 14F;
                if (t < 40) {
                    double k = Math.min(1, t / 40.0);
                    k = k * k * (3 - 2 * k);
                    Vec3 from = stage.add(stageFacing.scale(64)).add(0, 46, 0);
                    Vec3 to = b.position().add(stageFacing.scale(13)).add(0, 4, 0);
                    return new Shot(from.lerp(to, k), body, 62 - (float) k * 8, 0, t == 0);
                }
                int musica = VoiceLines.beat("arrive.musica", 74) - 4;
                if (t < musica) {
                    double orbit = Math.toRadians(-30 + (t - 40) * 1.4 * 30.0 / Math.max(30, musica - 40));
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(b.position().add(dir.scale(7.5)).add(0, 0.6, 0), face, 58, 0, t == 40);
                }
                return new Shot(b.position().subtract(f.scale(4.2)).add(right.scale(1.6)).add(0, 3.4, 0), stage.add(0, 1.2, 0), 56, 0, t == musica);
            }
            case P2_NOTICE -> {
                int cut = VoiceLines.beat("notice.maestro", 6) + 20;      // the band asleep through his first "Maestro...?"
                if (t < cut) return new Shot(stage.add(stageFacing.scale(8.5)).add(0, 2.6, 0), stage.add(0, 1.0, 0), 52, 0, t == 0);
                double len = Math.max(20, VoiceLines.beat("notice.end", 60) - cut);
                return new Shot(face.add(f.scale(4.6 - (t - cut) / len * 0.8)).add(0, -0.1, 0), face, 44, 0, t == cut);
            }
            case P2_MARCH -> {
                return new Shot(body.add(right.scale(6.5)).add(f.scale(1.5)).add(0, 0.8, 0), body, 58, 0, t == 0);
            }
            case P2_WAKE -> {
                Vec3 head = singer != null ? singer.position().add(0, singer.getBbHeight() * 0.9, 0) : stage.add(0, 2, 0);
                if (t < 12) return new Shot(b.position().subtract(f.scale(5.6)).add(right.scale(3.6)).add(0, b.getBbHeight() + 1.4, 0), head, 56, 0, t == 0);
                if (t < 34) {
                    // side on: the whack, and the singer going up in the air
                    Vec3 mid = b.position().lerp(singer != null ? singer.position() : stage, 0.5);
                    Vec3 side = new Vec3(-f.z, 0, f.x);
                    double push = (t - 12) / 22.0;
                    return new Shot(mid.add(side.scale(7.2 - push * 1.2)).add(0, 1.9, 0), mid.add(0, 2.3 + push * 0.4, 0), 54,
                            t >= 14 && t < 18 ? (float) Math.sin(t * 2.5) * 3F : 0, t == 12);
                }
                // the band jolting awake: from out front and off to the side, high, clear of him
                if (t < 58) return new Shot(stage.add(stageFacing.scale(7.0)).add(stageSide.scale(right.dot(stageSide) > 0 ? -7.5 : 7.5)).add(0, 4.2, 0),
                        stage.add(0, 1.2, 0), 60, 0, t == 34);
                int cut = VoiceLines.beat("wake.end", 54) - 10;           // on him through the bellow, then a bleary mariachi
                if (t < cut) return new Shot(stage.subtract(stageFacing.scale(2.5)).add(stageSide.scale(3.5)).add(0, 6.0, 0), face.add(0, 0.4, 0), 62, 0, t == 58);
                com.groid.papon.boss.MariachiEntity m = band.isEmpty() ? null : band.get(Math.floorMod(b.getId(), band.size()));
                Vec3 at = m != null ? m.position().add(0, 1.2, 0) : stage;
                return new Shot(at.add(stageFacing.scale(3.4)).add(stageSide.scale(1.2)), at, 46, 0, t == cut);
            }
            case P2_WAIT -> {
                int phase = (t / 70) % 2;
                int tt = t % 70;
                if (phase == 0) return new Shot(body.add(f.scale(4.8)).add(right.scale(2.2)).add(0, -0.3, 0), body.add(0, -0.4, 0), 54, 0, tt == 0);
                Vec3 eye = stage.add(stageFacing.scale(7.5)).add(stageSide.scale(-9 + tt * 0.26)).add(0, 2.2, 0);
                return new Shot(eye, stage.add(stageSide.scale(-6 + tt * 0.18)).add(0, 1.0, 0), 50, 0, tt == 0);
            }
            case P2_KICK -> {
                return new Shot(body.add(right.scale(-5.5)).add(f.scale(2.5)).add(0, 1.2, 0), b.position().add(f.scale(2)).add(0, 1, 0), 56, 0, t == 0);
            }
            case P2_COUNT -> {
                if (singer == null) return null;
                Vec3 sf = singer.position().add(0, singer.getBbHeight() * 0.68, 0);
                double push = Math.min(1, t / 100.0);
                return new Shot(sf.add(stageFacing.scale(3.4 - push * 1.4)).add(stageSide.scale(0.7)), sf, 44 - (float) push * 6, 0, t == 0);
            }
            case P2_SHOWTIME -> {
                if (singer == null) return null;
                // the requinto: low on his right, under the microphone
                Vec3 guitar = singer.position().add(stageFacing.scale(0.85)).add(stageSide.scale(-0.4)).add(0, 0.3, 0);
                Vec3 sf = singer.position().add(0, singer.getBbHeight() * 0.7, 0);
                // from the side away from the boss (he stands off to one side of the stand)
                double away = b.position().subtract(singer.position()).dot(stageSide) > 0 ? -1 : 1;
                double k = t < 34 ? 0 : Math.min(1, (t - 34) / 30.0);
                k = k * k * (3 - 2 * k);
                Vec3 look = guitar.lerp(sf, k);
                Vec3 eye = guitar.add(stageFacing.scale(2.4 - Math.min(1, t / 30.0) * 0.6)).add(stageSide.scale(1.3 * away)).add(0, 0.5, 0)
                        .lerp(sf.add(stageFacing.scale(3.0)).add(stageSide.scale(1.0 * away)).add(0, -0.4, 0), k);
                return new Shot(eye, look, 46, 0, t == 0);
            }
            case P2_APOLOGY -> {
                int close = VoiceLines.beat("apology.line", 30) + 20;     // the two of you, then on him for the apology
                int aura = VoiceLines.beat("apology.aura", 110);
                if (t < close) {
                    Vec3 mid = body.lerp(you, 0.5);
                    return new Shot(mid.add(right.scale(7.5)).add(0, 1.4, 0), mid, 58, 0, t == 0);
                }
                if (t < aura) return new Shot(body.add(f.scale(4.0)).add(right.scale(1.4)).add(0, -0.8, 0), face, 48, 0, t == close);
                // the aura farm: a slow low orbit, a little slower the longer it has to last
                double span = Math.max(68, VoiceLines.beat("apology.end", 178) - aura);
                double orbit = Math.toRadians(20 + (t - aura) * 1.5 * 68 / span);
                Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                return new Shot(b.position().add(dir.scale(5.2)).add(0, 0.4, 0), body.add(0, 0.5, 0), 50, 0, t == aura);
            }
            case P2_RESUME -> {
                int whip = VoiceLines.beat("resume.total", 36) - 8;        // whip out as the line ends and he sings
                Vec3 mid = face.add(0, -0.3, 0);                            // his eyes and his mouth
                if (t < whip) return new Shot(mid.add(f.scale(4.8)).add(right.scale(0.6)).add(0, -0.1, 0), mid, 42, 0, t == 0);
                double k = Math.min(1, (t - whip) / 6.0);
                return new Shot(face.add(f.scale(4.2 + k * 6)).add(0, k * 1.5, 0), body, 42 + (float) k * 22, 0, false);
            }
            case P2_HYPE -> {
                int left = singer != null && singer.songStart() != 0 ? ticksToSinging(mc, singer) : BossState.P2_HYPE_LENGTH - t;
                int tt = Math.max(0, BossState.P2_HYPE_LENGTH - left);
                if (tt < 44) {
                    // over his shoulder, low, out to the stands he is playing to
                    Vec3 eye = b.position().subtract(f.scale(6.2)).add(right.scale(3.4)).add(0, b.getBbHeight() * 0.7, 0);
                    return new Shot(eye, b.position().add(f.scale(18)).add(0, 7, 0), 64, 0, t == 0);
                }
                if (tt < 80) {
                    double orbit = Math.toRadians(-60 + (tt - 44) * 3.0);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(b.position().add(dir.scale(7.0)).add(0, 0.9, 0), face, 56, 0, tt == 44);
                }
                if (tt < 120) {
                    double k = (tt - 80) / 40.0;
                    double orbit = Math.toRadians(30 + (tt - 80) * 1.6);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(b.position().add(dir.scale(8.5 + k * 3)).add(0, 0.6 + k * 6.5, 0), body.add(0, 0.6, 0), 64 + (float) k * 6, 0, tt == 80);
                }
                if (tt < 150) {
                    // in front of him as he turns to you and points you out, pushing in
                    double push = (tt - 120) / 30.0;
                    Vec3 eye = face.add(f.scale(5.5 - push * 1.5)).add(right.scale(1.6)).add(0, -0.6, 0);
                    return new Shot(eye, face, 50 - (float) push * 8, 0, tt == 120);
                }
                double k = Math.min(1, (tt - 150) / 4.0);
                return new Shot(face.add(f.scale(5.0 + k * 3)).add(0, -1.6, 0), face.add(0, 0.6, 0), tt < 153 ? 96 : 70,
                        (float) Math.sin(tt * 1.9) * 4F, tt == 150);
            }
            case P2_CUTAWAY -> {
                return cutawayShot(b, b.cutaway(), t, band, stageFacing, stageSide);
            }
            case P2_SOLO -> {
                if (singer == null) return null;
                Vec3 sf = singer.position().add(0, singer.getBbHeight() * 0.7, 0);
                Vec3 hands = singer.position().add(stageFacing.scale(0.8)).add(stageSide.scale(-0.25)).add(0, 0.35, 0);
                if (t < 26) {
                    double push = t / 26.0;
                    return new Shot(hands.add(stageFacing.scale(3.0 - push * 0.8)).add(stageSide.scale(-1.5)).add(0, 0.5, 0), hands, 46, 0, t == 0);
                }
                if (t < 44) {
                    double k = (t - 26) / 18.0;
                    k = k * k * (3 - 2 * k);
                    return new Shot(sf.add(stageFacing.scale(3.9)).add(stageSide.scale(-1.3)).add(0, -0.3 + k * 0.3, 0), hands.lerp(sf, k), 50, 0, t == 26);
                }
                Vec3 eye = singer.position().subtract(stageFacing.scale(1.6)).add(stageSide.scale(1.2)).add(0, singer.getBbHeight() + 0.6, 0);
                return new Shot(eye, b.position().add(0, b.getBbHeight() * 0.5, 0), 62, 0, t == 44);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Phase three's cutscene, shot like a western and then like a concert film: the knockback low and in his
     * face as he skids at the lens; the glare in extreme close-ups, his eyes and the singer's; the stalk from
     * the band's side as he looms over them; the snatch over the singer's shoulder; the lights going out round
     * the bowl from high in the stands; the spotlight slamming on, looked up into; a slow orbit and his
     * fingers on the strings; the bowl full of lighters behind him; a slow push in as he sings; straight down
     * on him for the last chord; the guitar's flight; and "Now... let's DANCE." up close.
     */
    /** Ascended Papon is bigger: shots on him (not the band's, not the wide ones) step back to keep his face whole. */
    private static Shot ascendedRoom(PaponBossEntity b, Shot s) {
        Vec3 c = b.position().add(0, b.getBbHeight() * 0.6, 0);
        if (s.lookAt().distanceTo(c) > b.getBbHeight() * 0.8 || s.eye().distanceTo(s.lookAt()) > 9) return s;
        Vec3 eye = s.lookAt().add(s.eye().subtract(s.lookAt()).scale(1.5));
        return new Shot(eye, s.lookAt(), s.fov(), s.roll(), s.cut(), s.free());
    }

    /** A cut for the first few ticks of a shot (a client tick skipped must not turn the cut into a slow swoop through him). */
    private static boolean near(int t, int at) {
        return t >= at && t < at + 3;
    }

    /** Turned {@code deg} degrees round the vertical. */
    private static Vec3 yaw(Vec3 v, double deg) {
        double a = Math.toRadians(deg);
        return new Vec3(v.x * Math.cos(a) - v.z * Math.sin(a), v.y, v.x * Math.sin(a) + v.z * Math.cos(a));
    }

    /**
     * Phase four, the Final Guardian's awakening, beat by beat (the beats p4.* from his lines, VoiceLines; close shots framed on
     * his drawn eyes, fists and scar: BossRenderer.facePos / bonePos):
     *   P4_FALL       side on to the throw; low beside him on his back, panting; down over the bleeding scar; his face
     *   P4_RISEUP     low as he struggles up; close for the cough of blood; pushing in on "how I got this scar?"
     *   P4_IGNIS      over his shoulder as he reaches back; Ignis's head held up, close; the two faces; on the sand where it
     *                 lands; over your shoulder, then his smug face: "met before, no?"
     *   P4_PROPHECY   walking with him in profile; low and wide as the fog rolls in; past his shoulder up at his throne; the
     *                 throne on a long lens; his face looking up at it; over your shoulder; a slow circle; his face
     *   P4_THRONE     wide and low in the fog (Moctezuma); a dolly past him; his face from below; over his shoulder at his
     *                 throne; up at you, hard; his eyes, closer; the moon out through the fog; his face in its light; wide
     *   P4_BRETHREN   behind him to the stands he addresses; low on him; under his raised arms; the moon turning (long lens)
     *   P4_SOULS      high over the sand, the souls pouring into him; a stand of fans turning to bone; the band's; him aglow
     *   P4_HALFBAKED  his face in the green light; the burst; the flash; low round the Final Guardian in the new arena; wide
     *   P4_BOW        the bow forming in his fist; down the arrow at the moon; up the streak; the sky torn open
     *   P4_SERPENTS   their heads out of the smoke; from the sand craning up at Quetzalcoatl; a long lens on Kukulcan; high over it all
     *   P4_TALKING .. P4_GOODTIME  his face; walking to the band; the two-shot with the singer; the trumpets' fanfare; the
     *                 muchachas rising; the twirl and the dip; him striding at you, the smirk, the bowl erupting round him
     */
    @Nullable
    private static Shot phaseFourShot(PaponBossEntity b, BossState s, int t) {
        if (!s.phaseFour()) return null;
        Minecraft mc = Minecraft.getInstance();
        Vec3 f = Vec3.directionFromRotation(0, b.yBodyRot);
        Vec3 right = new Vec3(-f.z, 0, f.x);
        float h = b.getBbHeight();
        Vec3 body = b.position().add(0, h * 0.55, 0);
        Vec3 face = b.position().add(0, h * 0.62, 0);
        Vec3 c = b.fightCenter();
        Vec3 moon = b.moonPos();
        Vec3 toMoon = moon.subtract(b.position()).normalize();
        java.util.List<com.groid.papon.boss.MariachiEntity> band = new java.util.ArrayList<>(mc.level.getEntitiesOfClass(
                com.groid.papon.boss.MariachiEntity.class, b.getBoundingBox().inflate(120), e -> true));
        band.sort(java.util.Comparator.comparingInt(m -> m.getRole().ordinal()));
        com.groid.papon.boss.MariachiEntity singer = band.isEmpty() ? null : band.get(0);
        Vec3 stageFacing = singer != null ? Vec3.directionFromRotation(0, singer.getYRot()) : new Vec3(0, 0, 1);
        Vec3 stage = band.isEmpty() ? c.add(0, 0, -20) : band.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / band.size());
        com.groid.papon.boss.ColiseumCrowdEntity crowd = mc.level.getEntitiesOfClass(com.groid.papon.boss.ColiseumCrowdEntity.class,
                b.getBoundingBox().inflate(160), e -> true).stream().findFirst().orElse(null);
        double arenaA = crowd != null ? crowd.sizeA() : 29, arenaB = crowd != null ? crowd.sizeB() : 21;
        switch (s) {
            case P4_FALL -> {
                int land = VoiceLines.beat("p4.land", 14), elbow = VoiceLines.beat("p4.elbow", 66), look = VoiceLines.beat("p4.look", 86),
                        line = VoiceLines.beat("p4.scar", 112), end = VoiceLines.beat("p4.fall_end", 184);
                if (t < land + 3) {
                    // side on to his flight, wide: thrown flat across the sand
                    Vec3 eye = b.position().add(right.scale(h * 4.2)).add(f.scale(-h * 1.2)).add(0, h * 0.5, 0);
                    return new Shot(eye, b.position().add(0, h * 0.3, 0), 62, 0, near(t, 0));
                }
                if (t < 40) {
                    // straight down on him, splayed on the sand, the camera sinking slowly: not a sound from anywhere
                    double k = (t - land - 3) / (40.0 - land - 3);
                    Vec3 eye = b.position().add(0, h * (3.6 - 1.2 * k), 0).add(f.scale(0.4));
                    return new Shot(eye, b.position().add(0, h * 0.1, 0), 64, (float) (k * 6), near(t, land + 3));
                }
                if (t < elbow) {
                    // down low beside him on his back, heaving: the panting, a handheld wobble
                    float wob = (float) Math.sin(t * 0.21) * 1.5F;
                    Vec3 eye = b.position().add(right.scale(h * 2.3)).add(f.scale(h * 0.9)).add(0, h * 0.55, 0);
                    return new Shot(eye, b.position().add(0, h * 0.25, 0), 58, wob, near(t, 40));
                }
                if (t < look) {
                    // a long lens from across the sand: him small in the middle of his silent arena, the band frozen behind
                    Vec3 away = b.position().subtract(c).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-3 ? f : away.normalize();
                    Vec3 eye = b.position().add(away.scale(-Math.min(arenaA * 0.7, 30))).add(0, 3.5, 0);
                    return new Shot(eye, b.position().add(0, h * 0.4, 0), 24, 0, near(t, elbow), true);
                }
                Vec3 scar = BossRenderer.bonePos(b, "scar");
                Vec3 chest = scar != null ? scar : b.position().add(f.scale(h * 0.35)).add(0, h * 0.3, 0);
                if (t < line + 4) {
                    // the scar: down over his chest, close, pushing in as it bleeds
                    double push = Math.min(1, (t - look) / 40.0);
                    Vec3 eye = chest.add(f.scale(h * (1.35 - push * 0.35))).add(0, h * (0.85 - push * 0.2), 0).add(right.scale(h * 0.3));
                    return new Shot(eye, chest, 48 - (float) push * 10, 0, near(t, look));
                }
                Vec3 fp = BossRenderer.facePos(b);
                if (t < end - 30 || band.isEmpty()) {
                    double push = Math.min(1, (t - line - 4) / 60.0);
                    return new Shot(fp.add(f.scale(h * (1.9 - 0.3 * push))).add(right.scale(h * 0.45)).add(0, h * 0.12, 0), fp, 46, 0, near(t, line + 4));
                }
                // (2026-10-05, the user: "their jaws dropped, surprised the player reopened the scar") the band, every jaw on
                // the floor: from the sand at their eye level, pushing in over the singer to the faces behind him
                double push = Mth.clamp((t - end + 30) / 30.0, 0, 1);
                push = push * push * (3 - 2 * push);
                Vec3 front = singer != null ? singer.position() : stage;
                Vec3 faces = front.lerp(stage, 0.35).add(0, 1.55, 0);
                Vec3 eye = front.add(stageFacing.scale(7.5 - 3.0 * push)).add(0, 1.7, 0);
                return new Shot(eye, faces, 50 - 8 * (float) push, 0, near(t, end - 30), true);
            }
            case P4_RISEUP -> {
                int cough = VoiceLines.beat("p4.cough", 52), line = VoiceLines.beat("p4.howgot", 92);
                Vec3 fp = BossRenderer.facePos(b);
                if (t < cough - 2) {
                    Vec3 eye = b.position().add(f.scale(h * 2.0)).add(right.scale(h * 0.9)).add(0, h * 0.15, 0);
                    return new Shot(eye, b.position().add(0, h * 0.45, 0), 58, 0, near(t, 0));
                }
                if (t < cough + 22) {
                    // side on, close: the cough of blood onto the sand
                    return new Shot(b.position().add(right.scale(-h * 1.9)).add(f.scale(h * 0.9)).add(0, 0.5, 0), fp.add(0, -h * 0.15, 0), 50, 0, near(t, cough - 2));
                }
                if (t < line) {
                    // over his shoulder at you, his breath heaving
                    Vec3 you = mc.player.position().add(0, 1.2, 0);
                    Vec3 eye = b.position().subtract(f.scale(h * 1.6)).add(right.scale(h * 0.75)).add(0, h * 0.9, 0);
                    return new Shot(eye, you, 52, (float) Math.sin(t * 0.2) * 0.8F, near(t, cough + 22));
                }
                double push = Math.min(1, (t - line) / 70.0);
                return new Shot(fp.add(f.scale(h * (2.0 - push * 0.5))).add(0, -h * 0.2, 0), fp, 44 - (float) push * 6, 0, near(t, line));
            }
            case P4_IGNIS -> {
                int out = VoiceLines.beat("p4.head_out", 26), line = VoiceLines.beat("p4.ignis", 50), throwAt = VoiceLines.beat("p4.throw", 171),
                        thud = VoiceLines.beat("p4.thud", 184), met = VoiceLines.beat("p4.met", 210);
                Vec3 fp = BossRenderer.facePos(b);
                Vec3 hand = BossRenderer.bonePos(b, "hand_r");
                if (hand == null) hand = b.position().add(right.scale(-h * 0.4)).add(0, h * 0.8, 0);
                if (t < out) {
                    // over his shoulder as he reaches behind his back
                    Vec3 eye = b.position().subtract(f.scale(h * 1.1)).add(right.scale(h * 1.1)).add(0, h * 0.7, 0);
                    return new Shot(eye, body, 58, 0, near(t, 0));
                }
                if (t < line + 40) {
                    // the head: Ignis, held up by him, close, its fire still flickering
                    Vec3 eye = hand.add(f.scale(h * 1.7)).add(right.scale(h * 0.5)).add(0, h * 0.1, 0);
                    return new Shot(eye, hand.add(0, -h * 0.15, 0), 44, 0, near(t, out));
                }
                if (t < line + 90) {
                    // the two faces, a slow arc round them
                    Vec3 mid = fp.lerp(hand, 0.45);
                    double ang = -20 + (t - line - 40) * 0.6;
                    return new Shot(mid.add(yaw(f, ang).scale(h * 2.4)).add(0, h * 0.05, 0), mid, 48, 0, near(t, line + 40));
                }
                if (t < throwAt) {
                    // his face alone: "nothing notable"
                    return new Shot(fp.add(f.scale(h * 1.6)).add(right.scale(-h * 0.3)).add(0, -h * 0.08, 0), fp, 42, 0, near(t, line + 90));
                }
                com.groid.papon.boss.BossProp head = mc.level.getEntitiesOfClass(com.groid.papon.boss.BossProp.class, b.getBoundingBox().inflate(24),
                        p -> p.kind() == com.groid.papon.boss.BossProp.Kind.IGNIS_HEAD).stream().findFirst().orElse(null);
                Vec3 hp = head != null ? head.position() : b.position().add(f.scale(4.5));
                if (t < met) {
                    // down on the sand where it lands: the thud, him behind it
                    Vec3 away = hp.subtract(b.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? f : away.normalize();
                    Vec3 eye = hp.add(away.scale(4.5)).add(new Vec3(-away.z, 0, away.x).scale(1.8)).add(0, 1.0, 0);
                    return new Shot(eye, hp.lerp(fp, t < thud + 8 ? 0.15 : 0.5), 58, 0, near(t, throwAt));
                }
                // at you: from behind your shoulder, then close on the "no?"
                Vec3 you = mc.player.getEyePosition();
                Vec3 toYou = you.subtract(fp).multiply(1, 0, 1);
                double far = Math.min(toYou.length() * 0.7, h * 4.5);
                toYou = toYou.normalize();
                if (t < met + 30) {
                    Vec3 eye = fp.add(toYou.scale(far)).add(new Vec3(-toYou.z, 0, toYou.x).scale(h * 0.8)).add(0, -h * 0.1, 0);
                    return new Shot(eye, fp, 50, 0, near(t, met));
                }
                return new Shot(fp.add(f.scale(h * 1.7)).add(right.scale(-h * 0.35)), fp, 44, 0, near(t, met + 30));
            }
            case P4_PROPHECY -> {
                int look = VoiceLines.beat("p4.throne_look", 119), line = VoiceLines.beat("p4.throne", 133),
                        carved = VoiceLines.beat("p4.carved", 262), warrior = VoiceLines.beat("p4.warrior", 368);
                Vec3 fp = BossRenderer.facePos(b);
                Vec3 throne = b.throneSpot();
                Vec3 toThrone = throne.subtract(b.position()).multiply(1, 0, 1);
                toThrone = toThrone.lengthSqr() < 1e-4 ? f : toThrone.normalize();
                Vec3 tSide = new Vec3(-toThrone.z, 0, toThrone.x);
                if (t < look - 50) {
                    // walking with him in profile, a little ahead: "Ten thousand years..."
                    Vec3 eye = body.add(right.scale(h * 3.2)).add(f.scale(h * (1.3 + t * 0.004))).add(0, h * 0.15, 0);
                    return new Shot(eye, fp.add(f.scale(0.8)), 50, 0, near(t, 0));
                }
                if (t < look) {
                    // low and wide on the sand, walking on toward the middle
                    Vec3 eye = b.position().add(f.scale(h * 3.6)).add(right.scale(-h * 1.6)).add(0, 0.4, 0);
                    return new Shot(eye, body.add(0, h * 0.2, 0), 62, 0, near(t, look - 50));
                }
                if (t < line + 50) {
                    // behind him and well out to his side (clear of his wings), past him up at the throne in his box
                    Vec3 eye = b.position().subtract(toThrone.scale(h * 3.2)).add(tSide.scale(h * 2.4)).add(0, h * 1.1, 0);
                    return new Shot(eye, throne.lerp(fp, 0.25), 54, 0, near(t, look), true);
                }
                if (t < line + 110) {
                    // the throne itself, a long lens from the sand: gold, empty, kept warm
                    Vec3 eye = b.position().add(0, h * 0.8, 0).add(tSide.scale(h * 1.5));
                    return new Shot(eye, throne.add(0, 0.6, 0), 16 - (float) (t - line - 50) * 0.04F, 0, near(t, line + 50), true);
                }
                if (t < carved - 6) {
                    // his face from in front and below as he looks up at it
                    return new Shot(fp.add(toThrone.scale(h * 1.7)).add(tSide.scale(h * 0.6)).add(0, -h * 0.4, 0), fp, 46, 0, near(t, line + 110));
                }
                Vec3 you = mc.player.getEyePosition();
                Vec3 toYou = you.subtract(fp).multiply(1, 0, 1);
                toYou = toYou.lengthSqr() < 1e-4 ? f : toYou.normalize();
                if (t < warrior) {
                    // round to you: over your shoulder at him
                    Vec3 eye = you.add(toYou.scale(1.4)).add(new Vec3(-toYou.z, 0, toYou.x).scale(1.2)).add(0, 0.3, 0);
                    return new Shot(eye, fp, 44, 0, near(t, carved - 6));
                }
                if (t < warrior + 90) {
                    // the prophecy: a slow circle round him, the fog in the lights
                    double ang = (t - warrior) * 0.55;
                    return new Shot(body.add(yaw(f, ang).scale(h * 3.3)).add(0, h * 0.25, 0), fp, 54, 0, near(t, warrior));
                }
                double push = Math.min(1, (t - warrior - 90) / 80.0);
                return new Shot(fp.add(f.scale(h * (2.0 - 0.5 * push))).add(right.scale(h * 0.25)), fp, 44, 0, near(t, warrior + 90));
            }
            case P4_THRONE -> {
                int moct = VoiceLines.beat("p4.moctezuma", 18), gates = VoiceLines.beat("p4.gates", 249), mistake = VoiceLines.beat("p4.mistake", 395),
                        moonAt = VoiceLines.beat("p4.moon", 573), proph = VoiceLines.beat("p4.prophecy", 617), luna = VoiceLines.beat("p4.lunaknows", 712),
                        lit = VoiceLines.beat("p4.moonlit", moonAt + 120);
                Vec3 fp = BossRenderer.facePos(b);
                Vec3 flat = toMoon.multiply(1, 0, 1).normalize();
                if (t < moct + 60) {
                    // wide and low in the fog: him small in the middle of all that sand, head bowed, remembering
                    Vec3 eye = b.position().add(f.scale(h * 7)).add(right.scale(h * 3)).add(0, 0.8, 0);
                    return new Shot(eye, body, 40, 0, near(t, 0));
                }
                if (t < moct + 150) {
                    // a slow dolly past him, side on, close
                    double k = (t - moct - 60) / 90.0;
                    Vec3 eye = body.add(right.scale(h * 2.0)).add(f.scale(h * (1.2 - 2.0 * k))).add(0, h * 0.1, 0);
                    return new Shot(eye, fp.add(0, -h * 0.05, 0), 48, 0, near(t, moct + 60));
                }
                if (t < gates) {
                    // his face from below, eyes down: the iron men, the captain he called a god
                    return new Shot(fp.add(f.scale(h * 1.5)).add(right.scale(-h * 0.5)).add(0, -h * 0.4, 0), fp, 42, 0, near(t, moct + 150));
                }
                if (t < gates + 80) {
                    // past him, clear of his wings, at his own throne up in the fog, as he tells how his friend gave his away
                    Vec3 throne = b.throneSpot();
                    Vec3 toThrone = throne.subtract(b.position()).multiply(1, 0, 1).normalize();
                    Vec3 eye = b.position().subtract(toThrone.scale(h * 3.0)).add(new Vec3(-toThrone.z, 0, toThrone.x).scale(-h * 2.4)).add(0, h * 1.0, 0);
                    return new Shot(eye, throne.lerp(fp, 0.35), 50, 0, near(t, gates), true);
                }
                if (t < mistake) {
                    double push = Math.min(1, (t - gates - 80) / 70.0);
                    return new Shot(fp.add(f.scale(h * (1.9 - 0.4 * push))).add(right.scale(h * 0.3)).add(0, -h * 0.05, 0), fp, 44, 0, near(t, gates + 80));
                }
                Vec3 you = mc.player.getEyePosition();
                Vec3 toYou = you.subtract(fp).multiply(1, 0, 1);
                toYou = toYou.lengthSqr() < 1e-4 ? f : toYou.normalize();
                if (t < mistake + 70) {
                    // up at you, hard: from your side, his face filling the frame
                    Vec3 eye = fp.add(toYou.scale(h * 1.4)).add(new Vec3(-toYou.z, 0, toYou.x).scale(h * 0.25));
                    return new Shot(eye, fp, 40, 0, near(t, mistake));
                }
                if (t < moonAt) {
                    // his eyes, closer and closer: "...you will have to take it from me"
                    double push = Math.min(1, (t - mistake - 70) / 80.0);
                    Vec3 eye = fp.add(toYou.scale(h * (1.1 - 0.35 * push))).add(0, h * 0.05, 0);
                    return new Shot(eye, fp.add(0, h * 0.06, 0), 34 - (float) push * 6, 0, near(t, mistake + 70));
                }
                if (t < moonAt + 44) {
                    // from behind him up at the moon, there in the day sky, dark
                    Vec3 eye = b.position().subtract(flat.scale(h * 2.4)).add(new Vec3(-flat.z, 0, flat.x).scale(h * 0.9)).add(0, h * 0.8, 0);
                    return new Shot(eye, moon, 52, 0, near(t, moonAt), true);
                }
                if (t < lit - 14) {
                    // wide and low from the rim of the sand: the mist rolling in toward him across it, the light going
                    double k = (t - moonAt - 44) / (double) Math.max(1, lit - 14 - moonAt - 44);
                    Vec3 eye = c.subtract(flat.scale(arenaB * 0.85)).add(new Vec3(-flat.z, 0, flat.x).scale(arenaA * (0.35 - 0.2 * k))).add(0, 1.6 + 2.5 * k, 0);
                    return new Shot(eye, b.position().add(0, h * 0.6, 0).lerp(moon, 0.08 + 0.12 * k), 60, 0, near(t, moonAt + 44), true);
                }
                if (t < lit + 34) {
                    // a long lens on the moon as it lights, the glow spreading in from its rim
                    Vec3 eye = b.position().subtract(flat.scale(h * 1.6)).add(0, h * 1.1, 0);
                    return new Shot(eye, moon, 22, 0, near(t, lit - 14), true);
                }
                if (t < proph + 10) {
                    // him, small under it, the night come down over the arena
                    Vec3 eye = b.position().subtract(flat.scale(h * 5.0)).add(new Vec3(-flat.z, 0, flat.x).scale(h * 1.6)).add(0, h * 0.5, 0);
                    return new Shot(eye, b.position().add(0, h, 0).lerp(moon, 0.25), 56, 0, near(t, lit + 34), true);
                }
                if (t < luna) {
                    // his face in the moonlight, from below: wonder
                    return new Shot(fp.add(flat.scale(h * 1.7)).add(0, -h * 0.45, 0), fp.add(0, h * 0.05, 0), 44, 0, near(t, proph + 10));
                }
                if (t < luna + 60) return new Shot(fp.add(flat.scale(h * 1.9)).add(0, -h * 0.4, 0), fp.add(0, h * 0.05, 0), 46, 0, near(t, luna));
                // wide: the arena in the fog under its moon
                Vec3 eye = c.add(flat.scale(-arenaA * 0.6)).add(0, 7, 0);
                return new Shot(eye, b.position().add(flat.scale(30)).add(0, 20, 0), 64, 0, near(t, luna + 60), true);
            }
            case P4_BRETHREN -> {
                int line = VoiceLines.beat("p4.brethren", 20), grant = VoiceLines.beat("p4.grant", 139), turn = VoiceLines.beat("p4.turn", 189);
                Vec3 fp = BossRenderer.facePos(b);
                if (t < line + 50) {
                    // beside him, a little behind: his arms open to the stands he speaks to
                    Vec3 eye = b.position().add(right.scale(h * 3.4)).subtract(f.scale(h * 1.0)).add(0, h * 0.6, 0);
                    return new Shot(eye, b.position().add(f.scale(12)).add(0, h * 0.7, 0), 70, 0, near(t, 0));
                }
                if (t < line + 90) {
                    // the stands he speaks to, silent, every face on him
                    Vec3 stand = b.position().add(f.scale(arenaB + 8)).add(0, 8, 0);
                    Vec3 eye = b.position().add(f.scale(4)).add(0, h * 0.9, 0);
                    return new Shot(eye, stand, 34, 0, near(t, line + 50), true);
                }
                if (t < grant) return new Shot(b.position().add(f.scale(h * 2.8)).add(right.scale(h * 0.9)).add(0, h * 0.25, 0), fp, 56, 0, near(t, line + 90));
                if (t < turn - 2) {
                    // under him, up past his raised arms to the sky
                    Vec3 eye = b.position().add(f.scale(h * 2.4)).add(right.scale(-h * 0.8)).add(0, 0.3, 0);
                    return new Shot(eye, fp.add(0, h * 0.5, 0), 66, 0, near(t, grant));
                }
                if (t < turn + 34) {
                    // the moon itself, a long lens: it turns
                    Vec3 eye = b.position().add(0, h * 0.5 + 14, 0).add(right.scale(h * 1.6 + 2));
                    return new Shot(eye, moon, 22, 0, near(t, turn - 2), true);
                }
                // low behind him, the dead's moon over him
                Vec3 flat = toMoon.multiply(1, 0, 1).normalize();
                Vec3 eye = b.position().subtract(flat.scale(h * 3.0)).add(0, 0.6, 0);
                return new Shot(eye, b.position().add(flat.scale(20)).add(0, 18, 0), 66, 0, near(t, turn + 34), true);
            }
            case P4_SOULS -> {
                int feel = VoiceLines.beat("p4.feel", 214);
                if (t < 56) {
                    // high over the sand: the souls pouring out of every stand into him
                    Vec3 eye = c.add(f.scale(-arenaA * 0.5)).add(right.scale(arenaA * 0.3)).add(0, 16, 0);
                    return new Shot(eye, body, 80, 0, near(t, 0), true);
                }
                if (t < 112) {
                    // a section of the stands: the fans' souls leaving them, the bone left behind
                    Vec3 dir = f.scale(-1);
                    Vec3 stand = c.add(dir.scale(arenaB + 9)).add(0, 7, 0);
                    Vec3 eye = stand.add(dir.scale(-14)).add(right.scale(4)).add(0, 1.5, 0);
                    return new Shot(eye, stand, 62, 0, near(t, 56), true);
                }
                if (t < 200 && !band.isEmpty()) {
                    // the band giving theirs, one by one
                    Vec3 eye = stage.add(stageFacing.scale(12)).add(0, 3.2, 0);
                    return new Shot(eye, stage.add(0, 1.6, 0), 64, 0, near(t, 112), true);
                }
                Vec3 fp = BossRenderer.facePos(b);
                if (t < feel) {
                    double push = Math.min(1, (t - 200) / 14.0);
                    return new Shot(fp.add(f.scale(h * (2.0 - 0.3 * push))).add(0, -h * 0.2, 0), fp, 46, 0, near(t, 200));
                }
                // the scream: a whip back, the frame shaking
                double k = Math.min(1, (t - feel) / 8.0);
                return new Shot(body.add(f.scale(h * (2.0 + 2.6 * k))).add(0, -h * 0.4, 0), body, 70, (float) Math.sin(t * 1.9) * 3F, near(t, feel));
            }
            case P4_HALFBAKED -> {
                int line = VoiceLines.beat("p4.halfbaked", 16), burst = VoiceLines.beat("p4.burst", 112), flash = VoiceLines.beat("p4.flash", 152),
                        guardian = VoiceLines.beat("p4.guardian", 222);
                Vec3 fp = BossRenderer.facePos(b);
                if (t < burst) {
                    double push = Math.min(1, t / (double) burst);
                    return new Shot(fp.add(f.scale(h * (2.0 - push * 0.4))).add(right.scale(h * 0.3)), fp, 46, 0, near(t, 0));
                }
                if (t < flash + 8) {
                    double k = Math.min(1, (t - burst) / 10.0);
                    k = 1 - (1 - k) * (1 - k);
                    return new Shot(body.add(f.scale(h * (2.2 + 2.8 * k))).add(0, h * 0.2 * k, 0), body, t < burst + 4 ? 96 : 72,
                            (float) Math.sin(t * 1.7) * 4F, near(t, burst));
                }
                if (t < guardian - 6) {
                    // the new arena: low on the Final Guardian, circling
                    double ang = 25 + (t - flash - 8) * 0.7;
                    Vec3 eye = b.position().add(yaw(f, ang).scale(h * 3.2)).add(0, h * 0.2, 0);
                    return new Shot(eye, body.add(0, h * 0.15, 0), 62, 0, near(t, flash + 8));
                }
                if (t < guardian + 54) {
                    // his name: from below, his face and the penacho against the sky, pushing in
                    double push = Math.min(1, (t - guardian + 6) / 60.0);
                    return new Shot(fp.add(f.scale(h * (1.9 - 0.4 * push))).add(0, -h * 0.5, 0), fp.add(0, h * 0.15, 0), 50, 0, near(t, guardian - 6));
                }
                // out wide: him in the middle of El Coliseo de los Muertos
                Vec3 eye = c.add(f.scale(arenaA * 0.55)).add(right.scale(arenaA * 0.2)).add(0, 9, 0);
                return new Shot(eye, b.position().add(0, h * 0.5, 0), 66, 0, near(t, guardian + 54), true);
            }
            case P4_BOW -> {
                int form = PaponBossEntity.BOW_FORM, loose = PaponBossEntity.BOW_LOOSE, hit = PaponBossEntity.BOW_HIT, smoke = PaponBossEntity.BOW_SMOKE,
                        call = VoiceLines.beat("p4.bowline", 112);
                Vec3 handL = BossRenderer.bonePos(b, "hand_l");
                if (handL == null) handL = b.position().add(right.scale(h * 0.4)).add(0, h * 0.6, 0);
                Vec3 flat = toMoon.multiply(1, 0, 1).normalize();
                Vec3 side = new Vec3(-flat.z, 0, flat.x);
                if (t < 30) return new Shot(handL.add(f.scale(h * 1.8)).add(right.scale(h * 0.7)).add(0, h * 0.1, 0), handL, 48, 0, near(t, 0));
                if (t < loose) {
                    // over his shoulder down the arrow at the moon, as he draws
                    Vec3 eye = b.position().subtract(flat.scale(h * 2.3)).add(side.scale(-h * 1.3)).add(0, h * 1.25, 0);
                    Vec3 aim = BossRenderer.facePos(b).add(toMoon.scale(24));
                    return new Shot(eye, aim, 58 - (t - 30) * 0.3F, 0, near(t, 30), true);
                }
                if (t < smoke) {
                    // beside him, up the streak into the moon: the hit
                    Vec3 eye = b.position().add(side.scale(h * 2.2)).add(0, h * 0.4, 0);
                    return new Shot(eye, moon, t < hit ? 46 - (t - loose) * 1.6F : 26, t >= hit && t < hit + 8 ? (float) Math.sin(t * 2.3) * 2F : 0,
                            t == loose, true);
                }
                com.groid.papon.boss.SkySerpentEntity sp = mc.level.getEntitiesOfClass(com.groid.papon.boss.SkySerpentEntity.class,
                        b.getBoundingBox().inflate(200), e -> true).stream().findFirst().orElse(null);
                Vec3 look = sp != null ? sp.portal() : moon;
                if (t < call - 4) {
                    // the sky torn open: black smoke over the moon's side
                    Vec3 eye = c.add(flat.scale(-arenaA * 0.4)).add(0, 6, 0);
                    return new Shot(eye, look, 64, 0, near(t, smoke), true);
                }
                // calling them down: from low behind him, the bow up, the smoke beyond
                Vec3 eye = b.position().subtract(flat.scale(h * 1.8)).add(side.scale(h * 1.1)).add(0, 0.5, 0);
                return new Shot(eye, b.position().add(flat.scale(10)).add(0, h * 1.6, 0), 68, 0, near(t, call - 4), true);
            }
            case P4_SERPENTS -> {
                java.util.List<com.groid.papon.boss.SkySerpentEntity> sps = mc.level.getEntitiesOfClass(com.groid.papon.boss.SkySerpentEntity.class,
                        b.getBoundingBox().inflate(200), e -> true);
                com.groid.papon.boss.SkySerpentEntity a = sps.stream().filter(e -> e.kind() == 0).findFirst().orElse(null);
                com.groid.papon.boss.SkySerpentEntity bb = sps.stream().filter(e -> e.kind() == 1).findFirst().orElse(a);
                float partial = mc.getFrameTime();
                Vec3 flat = toMoon.multiply(1, 0, 1).normalize();
                if (a == null) return new Shot(b.position().add(f.scale(h * 3)).add(0, h * 0.5, 0), body, 60, 0, near(t, 0));
                Vec3 headA = a.path().at(a.head(partial)), headB = bb.path().at(bb.head(partial));
                if (t < 56) {
                    // out of the smoke: their heads coming through
                    Vec3 eye = c.add(flat.scale(-arenaA * 0.3)).add(0, 8, 0);
                    return new Shot(eye, headA.lerp(headB, 0.5), 54, 0, near(t, 0), true);
                }
                if (t < 124) {
                    // from the sand beside him, craning up as it comes over: how big it is
                    Vec3 eye = b.position().add(right.scale(h * 1.8)).add(f.scale(-h * 0.6)).add(0, 0.8, 0);
                    return new Shot(eye, headA, 78, 0, near(t, 56), true);
                }
                if (t < 190) {
                    // a long lens on Kukulcan's gold mask
                    Vec3 eye = c.add(0, 34, 0).add(headB.subtract(c).multiply(1, 0, 1).scale(0.3));
                    return new Shot(eye, headB, 30, 0, near(t, 124), true);
                }
                // high over it all: the two of them circling the coliseum of the dead
                Vec3 eye = c.add(flat.scale(arenaA * 2.0)).add(0, 70, 0);
                return new Shot(eye, c.add(0, 18, 0), 74, 0, near(t, 190), true);
            }
            case P4_TALKING -> {
                Vec3 fp = BossRenderer.facePos(b);
                if (t < 14) return new Shot(fp.add(f.scale(h * 1.8)).add(0, -h * 0.3, 0), fp, 50, 0, near(t, 0));
                return new Shot(body.add(f.scale(h * 2.9)).add(right.scale(h * 0.8)).add(0, -h * 0.35, 0), body.add(0, h * 0.3, 0), 64, 0, near(t, 14));
            }
            case P4_TOBAND -> {
                Vec3 eye = body.subtract(f.scale(h * 2.2)).add(right.scale(h * 1.3)).add(0, h * 0.25, 0);
                return new Shot(eye, body.add(f.scale(5)), 58, 0, near(t, 0));
            }
            case P4_HONOR -> {
                // (2026-10-05, the user: "the camera needs to be steady instead of constantly switching") one unbroken shot: side
                // on to the two of them from the sand, pushing in slowly while he asks for one last song; when the brass comes in
                // it pans, unhurried, onto the trumpets. It used to cut four times (one shot under a second), and its side was
                // chosen against the arena's middle, which lies almost on the line between him and the singer: the camera flipped
                // from one side of them to the other as he swayed, cutting every time.
                int song = VoiceLines.beat("p4.song", 128);
                Vec3 sf = singer != null ? singer.position().add(0, singer.getBbHeight() * 0.72, 0) : stage.add(0, 1.5, 0);
                Vec3 hf = b.position().add(0, h * 0.62, 0);               // his face's height off his feet (the tracked bone sways)
                Vec3 mid = hf.lerp(sf, 0.5);
                Vec3 across = sf.subtract(hf).multiply(1, 0, 1);
                Vec3 perp = across.lengthSqr() < 1e-4 ? right : new Vec3(-across.z, 0, across.x).normalize();
                Vec3 fixedSide = new Vec3(-stageFacing.z, 0, stageFacing.x);  // the stage's own side: never changes in the scene
                if (perp.dot(fixedSide) < 0) perp = perp.scale(-1);
                float push = Mth.clamp(t / (float) Math.max(1, song), 0F, 1F);
                push = push * push * (3 - 2 * push);
                // wide enough for both of them and his wings, a little round toward the singer's side so his face shows
                Vec3 toSinger = across.lengthSqr() < 1e-4 ? f : across.normalize();
                Vec3 eye = mid.add(perp.scale(14.0 - 3.0 * push)).add(toSinger.scale(2.5)).add(0, 3.2 - 0.6 * push, 0);
                Vec3 look = mid;
                if (t > song) {
                    java.util.List<com.groid.papon.boss.MariachiEntity> horns = band.stream().filter(m -> m.getRole().instrument.equals("trumpet")).toList();
                    Vec3 at = horns.isEmpty() ? stage : horns.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / horns.size());
                    float pan = Mth.clamp((t - song) / 40F, 0F, 1F);
                    pan = pan * pan * (3 - 2 * pan);
                    look = mid.lerp(at.add(0, 1.4, 0), pan);
                    eye = eye.add(at.subtract(mid).multiply(1, 0, 1).scale(0.12 * pan));
                }
                return new Shot(eye, look, 50 - 4 * push, 0, near(t, 0), true);
            }
            case P4_MUCHACHAS -> {
                int clap = VoiceLines.beat("p4.clap", 50), twirl = VoiceLines.beat("p4.twirl", 112), dip = VoiceLines.beat("p4.dip", 170);
                Vec3 fp = BossRenderer.facePos(b);
                java.util.List<com.groid.papon.boss.SalsaDancerEntity> ds = mc.level.getEntitiesOfClass(com.groid.papon.boss.SalsaDancerEntity.class,
                        b.getBoundingBox().inflate(80), e -> true);
                Vec3 toC = c.subtract(fp).multiply(1, 0, 1).normalize();
                if (t < clap + 6 || ds.isEmpty()) return new Shot(fp.add(toC.scale(h * 2.2)).add(new Vec3(-toC.z, 0, toC.x).scale(h * 0.5)), fp, 50, 0, near(t, 0));
                Vec3 dc = ds.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / ds.size());
                if (t < twirl) {
                    // the muchachas rising by the stage in green fire
                    Vec3 in = c.subtract(dc).multiply(1, 0, 1).normalize();
                    return new Shot(dc.add(in.scale(15)).add(0, 3.5, 0), dc.add(0, 1.6, 0), 62, 0, near(t, clap + 6), true);
                }
                com.groid.papon.boss.SalsaDancerEntity d = ds.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(b))).orElse(ds.get(0));
                Vec3 mid = b.position().lerp(d.position(), 0.5);
                Vec3 across = d.position().subtract(b.position()).multiply(1, 0, 1);
                Vec3 toHer = across.lengthSqr() < 1e-4 ? f : across.normalize();
                Vec3 perp = new Vec3(-toHer.z, 0, toHer.x);
                if (perp.dot(c.subtract(mid)) < 0) perp = perp.scale(-1);
                // side on from the sand (his wings are folded for the dance): the twirl, then the dip
                Vec3 dir = perp.add(toHer.scale(0.25)).normalize();
                double ang = (t - twirl) * 0.2;
                if (t < dip) return new Shot(mid.add(yaw(dir, ang).scale(10.5)).add(0, 2.6, 0), mid.add(0, h * 0.4, 0), 56, 0, near(t, twirl), true);
                return new Shot(mid.add(yaw(dir, ang - 25).scale(8.0)).add(0, 1.4, 0), mid.add(0, h * 0.25, 0), 54, 0, near(t, dip), true);
            }
            case P4_GOODTIME -> {
                int line = VoiceLines.beat("p4.goodtime", 40), erupt = VoiceLines.beat("p4.erupt", 103);
                Vec3 fp = BossRenderer.facePos(b);
                Vec3 you = mc.player.getEyePosition();
                int dance = VoiceLines.beat("p4.dance", 24);
                if (t < dance + 30) {
                    // over your shoulder: him striding at you
                    Vec3 toYou = you.subtract(fp).multiply(1, 0, 1);
                    double far = Math.min(toYou.length() * 0.8, h * 5);
                    toYou = toYou.normalize();
                    return new Shot(fp.add(toYou.scale(far)).add(new Vec3(-toYou.z, 0, toYou.x).scale(h * 0.6)).add(0, -h * 0.15, 0), fp, 54, 0, near(t, 0));
                }
                if (t < line) {
                    // "This was never a fight, amigo. It is a dance.": a slow push on his face, from a touch below
                    double push = Math.min(1, (t - dance - 30) / 70.0);
                    return new Shot(fp.add(f.scale(h * (2.2 - 0.6 * push))).add(right.scale(-h * 0.25)).add(0, -h * 0.25, 0), fp, 46, 0, near(t, dance + 30));
                }
                if (t < erupt + 4) {
                    // close: the scream, punched in
                    float punch = (float) Math.min(1, (t - line) / 6.0);
                    return new Shot(fp.add(f.scale(h * (1.7 - 0.3 * punch))).add(right.scale(h * 0.2)).add(0, -h * 0.2, 0), fp, 48 - 12 * punch,
                            (float) Math.sin(t * 2.1) * 1.5F * punch, near(t, line));
                }
                // round him as the bowl erupts, till the first word
                double ang = (t - erupt) * 1.4;
                return new Shot(body.add(yaw(f, ang).scale(h * 3.2)).add(0, h * 0.25, 0), body.add(0, h * 0.2, 0), 70, 0, near(t, erupt + 4));
            }
            default -> {
                return null;
            }
        }
    }

    @Nullable
    private static Shot phaseThreeShot(PaponBossEntity b, BossState s, int t, Vec3 f0, Vec3 right0) {
        if (!s.phaseThree()) return null;
        Minecraft mc = Minecraft.getInstance();
        // the way his body (the model) faces, not where his head was last told to look
        Vec3 f = Vec3.directionFromRotation(0, b.yBodyRot);
        Vec3 right = new Vec3(-f.z, 0, f.x);
        float h = b.getBbHeight();
        Vec3 body = b.position().add(0, h * 0.55, 0);
        // his face: the ascended model sits lower in its (bigger) box, so his eyes are lower down it
        Vec3 face = b.position().add(0, h * (b.isAscended() ? 0.62 : 0.69), 0);
        java.util.List<com.groid.papon.boss.MariachiEntity> band = mc.level.getEntitiesOfClass(com.groid.papon.boss.MariachiEntity.class,
                b.getBoundingBox().inflate(64), e -> true);
        com.groid.papon.boss.MariachiEntity singer = band.stream().filter(m -> m.getRole().singer()).findFirst().orElse(null);
        Vec3 stageFacing = singer != null ? Vec3.directionFromRotation(0, singer.getYRot()) : f.scale(-1);
        Vec3 stageSide = new Vec3(-stageFacing.z, 0, stageFacing.x);
        Vec3 stage = band.isEmpty() ? b.position().add(f.scale(10))
                : band.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / band.size());
        Vec3 center = singer != null ? singer.position().add(stageFacing.scale(com.groid.papon.boss.Coliseum.B - 5.6)).add(0, -1, 0) : b.position();
        Vec3 singerFace = singer != null ? singer.position().add(0, singer.getBbHeight() * 0.72, 0) : stage.add(0, 1.5, 0);
        switch (s) {
            case P3_KNOCKBACK -> {
                int fall = VoiceLines.beat("knock.fall", 54), knee = VoiceLines.beat("knock.knee", 76),
                        up = VoiceLines.beat("knock.up", 130);
                if (t < 16) {
                    // in front of his face as he is blown away from the lens, down onto the sand
                    Vec3 eye = face.add(f.scale(5.2 + t * 0.15)).add(right.scale(1.2)).add(0, -0.4, 0);
                    return new Shot(eye, face, 50 + (t >= 13 && t < 16 ? 10 : 0), t >= 14 ? (float) Math.sin(t * 2.7) * 5F : 0, t == 0);
                }
                if (t < fall - 4) {
                    // straight down on him, flat on his back, his chest heaving: a slow push in
                    Vec3 head = b.position().subtract(f.scale(h * 0.6)).add(0, 0.5, 0);
                    double k = (t - 16) / (double) Math.max(1, fall - 20);
                    return new Shot(head.add(0, 4.4 - 1.3 * k, 0).add(f.scale(0.4)), head, 50, (float) Math.sin(t * 0.05) * 3F, t == 16);
                }
                if (t < knee) {
                    // low on the sand at his side: an arm pushes him up, and gives way
                    Vec3 mid = b.position().add(0, 0.6, 0);
                    return new Shot(mid.add(right.scale(4.6)).add(f.scale(0.8)).add(0, -0.2, 0), mid, 52, 0, t == fall - 4);
                }
                if (t < up) {
                    // from the ground in front of him, on one knee: "Hhh... that one... HURT.", the lip wiped
                    Vec3 kneel = b.position().add(0, h * 0.46, 0);
                    double k = (t - knee) / (double) Math.max(1, up - knee);
                    return new Shot(kneel.add(f.scale(5.2 - 0.8 * k)).add(right.scale(0.9)).add(0, -0.5, 0), kneel, 48, 0, t == knee);
                }
                // up, swaying on his feet: from below, his face
                return new Shot(b.position().add(f.scale(4.0)).add(right.scale(-0.6)).add(0, 0.3, 0), face, 52, (float) Math.sin(t * 0.2) * 3F, t == up);
            }
            case P3_GLARE -> {
                int noise = VoiceLines.beat("glare.noise", 34), focus = VoiceLines.beat("glare.focus", 84),
                        scare = VoiceLines.beat("glare.scare", 114), faceAt = VoiceLines.beat("glare.face", 148),
                        ns = VoiceLines.beat("glare.notstyle", 158), rp = VoiceLines.beat("glare.realplay", 230);
                if (t < faceAt) {
                    // through his eyes: everything swimming, the band's song muffled in his ears (client/SongPlayer); his head
                    // lolls round the bowl, drags round to the band stand, and the blur pulls into focus on them as his eyes
                    // zoom in; they see him and freeze
                    pov = b;
                    Vec3 eye = b.position().add(f.scale(b.getBbWidth() * 0.55)).add(0, h * 0.74, 0);
                    Vec3 bandAt = singer != null ? stage.add(0, 1.3, 0) : eye.add(f.scale(10));
                    double yaw0 = Math.sin(noise * 0.06) * 0.9 + 0.3, pitch0 = 0.15 + Math.sin(noise * 0.11) * 0.12;
                    Vec3 lolled = eye.add(f.scale(Math.cos(yaw0)).add(right.scale(Math.sin(yaw0))).add(0, pitch0, 0).scale(12));
                    if (t < noise) {
                        double yaw = Math.sin(t * 0.06) * 0.9 + 0.3, pitch = 0.15 + Math.sin(t * 0.11) * 0.12;
                        Vec3 dir = f.scale(Math.cos(yaw)).add(right.scale(Math.sin(yaw))).add(0, pitch, 0);
                        blurWant = 9F;
                        return new Shot(eye.add(0, Math.sin(t * 0.21) * 0.04, 0), eye.add(dir.scale(12)), 74, (float) Math.sin(t * 0.08) * 7F, t == 0);
                    }
                    if (t < focus) {
                        double k = smooth(Math.min(1, (t - noise) / (double) Math.max(1, focus - noise)));
                        blurWant = 9F;
                        return new Shot(eye, lolled.lerp(bandAt, k), 74, (float) ((1 - k) * Math.sin(t * 0.08) * 7F), false);
                    }
                    double k = smooth(Math.min(1, (t - focus) / 22.0));
                    blurWant = (float) (9 * Math.max(0, 1 - (t - focus) / 12.0));
                    float jolt = t >= scare && t < scare + 6 ? (float) Math.sin(t * 2.5) * 2F : 0;
                    return new Shot(eye, bandAt, (float) (74 - 52 * k), jolt, false);
                }
                if (t < rp) {
                    // his face, furious, close; pushing in through "...who do you think I AM?!"
                    double k = Math.min(1, (t - faceAt) / (double) Math.max(20, rp - faceAt));
                    return new Shot(face.add(f.scale(5.2 - 1.2 * k)).add(right.scale(0.5)).add(0, -0.35, 0), face, 42 - (float) k * 6,
                            t >= ns && t < ns + 10 ? (float) Math.sin(t * 2.2) * 2F : 0, t == faceAt);
                }
                // "Let me show you how to REALLY play.": low, in front of him, up into his face
                return new Shot(face.add(f.scale(4.0)).add(right.scale(-1.1)).add(0, -1.1, 0), face, 46, 0, t == rp);
            }
            case P3_STALK -> {
                // backing away in front of him as he comes: his face, and the band waiting behind the lens
                double toBand = singer != null ? b.position().distanceTo(singer.position()) : 8;
                double d = Mth.clamp(toBand - 1.2, 3.2, 5.5);
                return new Shot(face.add(f.scale(d)).add(right.scale(0.8)).add(0, -0.5, 0), face, 46, 0, t == 0);
            }
            case P3_SNATCH -> {
                int grab = VoiceLines.beat("snatch.grab", 22);
                if (t < grab - 4) {
                    // over the singer's shoulder, up into his face
                    Vec3 eye = singer != null ? singer.position().subtract(stageFacing.scale(0.4)).add(stageSide.scale(1.9)).add(0, singer.getBbHeight() + 0.9, 0)
                            : face.add(f.scale(4));
                    return new Shot(eye, face, 46, 0, t == 0);
                }
                if (t < grab + 26) {
                    // his face as he rips it away and holds it up
                    return new Shot(face.add(f.scale(4.6)).add(right.scale(1.8)).add(0, -0.2, 0), face.add(0, 0.5, 0), 52,
                            t >= grab && t < grab + 4 ? (float) Math.sin(t * 2.4) * 3F : 0, t == grab - 4);
                }
                // the band cowering
                return new Shot(stage.add(stageFacing.scale(7.5)).add(stageSide.scale(-3)).add(0, 2.2, 0), stage.add(0, 1.0, 0), 58, 0, t == grab + 26);
            }
            case P3_RETURN -> {
                if (t < 40) {
                    // high in the south stands: the bowl, the lights going out round it
                    Vec3 eye = center.add(stageFacing.scale(com.groid.papon.boss.Coliseum.B + 8)).add(stageSide.scale(-6 + t * 0.03)).add(0, 17, 0);
                    return new Shot(eye, b.position().add(0, 1.2, 0), 66, 0, t == 0);
                }
                // backing away in front of his face as he walks into the middle, the bowl going dark behind him
                return new Shot(face.add(f.scale(4.8)).add(right.scale(-0.7)).add(0, -0.3, 0), face, 54, 0, t == 40);
            }
            case P3_BLACKOUT -> {
                if (t < 30) return new Shot(face.add(f.scale(4.2)).add(right.scale(0.6)).add(0, -0.6, 0), face, 44, 0, t == 0);
                // the spotlight slams on: from the sand in front of him, up the beam, then down onto his face
                double k = Math.min(1, (t - 30) / 20.0);
                k = k * k * (3 - 2 * k);
                Vec3 eye = face.add(f.scale(4.6)).add(0, -1.2, 0);
                return new Shot(eye, b.position().add(0, 9, 0).lerp(face, k), 62 - (float) k * 14, 0, t == 30);
            }
            case P3_SERENADE -> {
                // the guitar intro (to his first sung word, about 11 s in) is his and the bowl's: him from every side, the
                // stands lighting up, the band watching; then a slow push in on his face as he sings; the last chord from below
                Vec3 hands = b.position().add(f.scale(0.95)).add(0, h * 0.36, 0);
                int sing = VoiceLines.beat("serenade.start", 4) + 220;
                if (t < 50) {
                    double orbit = Math.toRadians(-50 + t * 1.2);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(face.add(dir.scale(4.6)).add(0, -0.5, 0), face.add(0, -0.4, 0), 48, 0, t == 0);
                }
                if (t < 85) return new Shot(hands.add(f.scale(4.0)).add(right.scale(2.2)).add(0, -0.3, 0), hands.lerp(face, 0.3), 40, 0, t == 50);
                if (t < 108) {
                    // the stands, from down on the sand: the first lighters coming on (a glance away from him, no more)
                    Vec3 side = right.scale(b.position().subtract(center).dot(right) > 0 ? -1 : 1);
                    Vec3 eye = b.position().add(side.scale(-11)).add(f.scale(-4)).add(0, 3.0, 0);
                    return new Shot(eye, center.add(side.scale(com.groid.papon.boss.Coliseum.A + 6)).add(f.scale(10)).add(0, 8, 0), 66, 0, t == 85, true);
                }
                if (t < 150) {
                    // (the user: he is the main focus) low and round him, his fingers on the strings, up to his face
                    double k = (t - 108) / 42.0;
                    double orbit = Math.toRadians(-70 + k * 60);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(hands.add(dir.scale(3.6)).add(0, -0.2, 0), hands.lerp(face, smooth(k) * 0.7), 44, 0, t == 108);
                }
                if (t < 185) {
                    // over his shoulder, out over the sand to the bowl of lighters
                    double k = (t - 150) / 35.0;
                    Vec3 eye = b.position().subtract(f.scale(3.6)).add(right.scale(1.4)).add(0, h * 0.9 + k * 1.5, 0);
                    return new Shot(eye, b.position().add(f.scale(30)).add(0, 9, 0), 70, 0, t == 150, true);
                }
                if (t < sing) {
                    // his face in profile, three-quarters, as his head comes up for the first line
                    double orbit = Math.toRadians(55 - (t - 185) * 1.0);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(face.add(dir.scale(4.2)).add(0, -0.4, 0), face, 42, 0, t == 185);
                }
                // his mouth is low on him (the potato's face is all of him): the singing is framed on mouth and eyes together,
                // from a touch above, so the requinto on his hip stays under it
                Vec3 mouth = b.position().add(0, h * (b.isAscended() ? 0.42 : 0.5), 0);
                Vec3 sung = mouth.lerp(face, 0.4);
                int tight = sing + 64;
                if (t < tight) {
                    double k = (t - sing) / (double) Math.max(1, tight - sing);
                    return new Shot(sung.add(f.scale(4.8 - 1.0 * k)).add(right.scale(0.7 - 0.3 * k)).add(0, 0.45, 0), sung, 46 - (float) k * 4, 0, t == sing);
                }
                if (t < 330) {
                    // in close on his lips, three-quarters, drifting round as he holds the long notes
                    double k = (t - tight) / (double) Math.max(1, 330 - tight);
                    double orbit = Math.toRadians(-28 + 40 * k);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(mouth.add(dir.scale(2.9)).add(0, 0.5, 0), mouth.add(0, 0.15, 0), 44, 0, t == tight);
                }
                if (t < 358) {
                    // round to his other side, still on his face as the line ends
                    double orbit = Math.toRadians(-35 + (t - 330) * 0.8);
                    Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                    return new Shot(face.add(dir.scale(4.4)).add(0, -0.5, 0), face, 44, 0, t == 330);
                }
                // the last chord: from below, up into his face, the hand flung to the sky, the wings open behind him
                return new Shot(face.add(f.scale(5.4)).add(right.scale(-0.8)).add(0, -1.6, 0), face.add(0, 0.6, 0), 60, 0, t == 358);
            }
            case P3_VERDICT -> {
                int end = VoiceLines.beat("verdict.end", 80);
                if (t < end - 12) return new Shot(face.add(f.scale(4.8)).add(right.scale(1.3)).add(0, -0.4, 0), face, 46, 0, t == 0);
                double k = Math.min(1, (t - (end - 12)) / 12.0);
                return new Shot(face.add(f.scale(4.8 + 5 * k)).add(0, k * 2, 0), body, 46 + (float) k * 20, 0, false);
            }
            case P3_TOSS -> {
                int thr = VoiceLines.beat("toss.throw", 20), cat = VoiceLines.beat("toss.catch", 42), gl = VoiceLines.beat("toss.glance", 58);
                if (t < thr + 2) return new Shot(face.add(f.scale(4.8)).add(right.scale(-1.4)).add(0, -0.3, 0), face, 50, 0, t == 0);
                if (t < cat + 2) {
                    // the requinto spinning through the beam, end over end, all the way to the singer's hands
                    com.groid.papon.boss.BossProp g = mc.level.getEntitiesOfClass(com.groid.papon.boss.BossProp.class, b.getBoundingBox().inflate(40),
                            p -> p.kind() == com.groid.papon.boss.BossProp.Kind.GUITAR).stream().findFirst().orElse(null);
                    Vec3 at = g != null ? g.position() : body.lerp(singerFace, (t - thr) / (double) Math.max(1, cat - thr));
                    Vec3 dir = singerFace.subtract(body).multiply(1, 0, 1).normalize();
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    return new Shot(at.add(side.scale(5.0)).add(dir.scale(-1.5)).add(0, 0.6, 0), at, 54, 0, t == thr + 2);
                }
                if (t < gl + 4) return new Shot(singerFace.add(stageFacing.scale(3.4)).add(stageSide.scale(0.9)), singerFace.add(0, -0.4, 0), 44, 0, t == cat + 2);
                // (the user) the band, scared, whispering to each other: a tight two-shot of whoever is talking
                int talk2 = VoiceLines.beat("toss.talk2", gl + 30);
                com.groid.papon.boss.MariachiEntity a = role(band, t < talk2 ? com.groid.papon.boss.BandRole.GUITAR : null, singer);
                com.groid.papon.boss.MariachiEntity c = role(band, com.groid.papon.boss.BandRole.VIHUELA, singer);
                if (a != null && c != null) {
                    Vec3 pa = a.position().add(0, a.getBbHeight() * 0.7, 0), pc = c.position().add(0, c.getBbHeight() * 0.7, 0);
                    Vec3 mid = pa.lerp(pc, 0.5);
                    double apart = pa.distanceTo(pc);
                    return new Shot(mid.add(stageFacing.scale(2.4 + apart * 0.7)).add(0, -0.2, 0), mid, 40, 0, t == gl + 4 || t == talk2);
                }
                return new Shot(stage.add(stageFacing.scale(6.5)).add(0, 1.8, 0), stage.add(0, 1.1, 0), 54, 0, t == gl + 4);
            }
            case P3_ASCEND -> {
                int burst = VoiceLines.beat("ascend.burst", 100), roar = VoiceLines.beat("ascend.roar", 108);
                int cut = VoiceLines.beat("ascend.cut", 30), crowd = VoiceLines.beat("ascend.crowd", roar + 40),
                        bandAt = VoiceLines.beat("ascend.band", crowd + 34);
                if (t >= cut && t < cut + 22 && cut + 22 < burst - 26) {
                    // the band clutching each other as he groans
                    return new Shot(stage.add(stageFacing.scale(8.0)).add(stageSide.scale(1.5)).add(0, 0.6, 0), stage.add(0, 1.4, 0), 52, 0, t == cut);
                }
                if (t >= crowd && t < bandAt) {
                    // the stands on their feet, screaming: from the sand, up at them
                    Vec3 side = right.scale(b.position().subtract(center).dot(right) > 0 ? -1 : 1);
                    Vec3 eye = b.position().add(side.scale(-10)).add(f.scale(-3)).add(0, 2.5, 0);
                    return new Shot(eye, center.add(side.scale(com.groid.papon.boss.Coliseum.A + 5)).add(f.scale(6)).add(0, 7, 0), 68, 0, t == crowd, true);
                }
                if (t >= bandAt) {
                    // the band backing away from him, then struck dumb
                    return new Shot(stage.add(stageFacing.scale(7.5)).add(stageSide.scale(-2.0)).add(0, 0.6, 0), stage.add(0, 1.4, 0), 50, 0, t == bandAt);
                }
                int god = VoiceLines.beat("ascend.god", 162);
                if (t >= god - 2) {
                    // (main) "No more games, amigo. Behold... the POTATO GOD.": from the sand at his feet, up into his face, pushing in
                    double k = Math.min(1, (t - god + 2) / 70.0);
                    return new Shot(b.position().add(f.scale(5.2 - 1.2 * k)).add(right.scale(0.8 - 0.5 * k)).add(0, 0.3 + 0.4 * k, 0), face.add(0, 0.2, 0),
                            52 - (float) k * 10, 0, t == god - 2);
                }
                if (t < burst - 26) {
                    // bent double in the spotlight, groaning: close on his face, shaking
                    return new Shot(face.add(f.scale(4.0)).add(right.scale(1.0)).add(0, -0.6, 0), face, 40,
                            (float) Math.sin(t * 2.3) * (1.5F + 3F * t / burst), t == 0);
                }
                if (t < burst + 14) {
                    // behind him, low: his back heaving under the flag, and then the wings tear out of it
                    if (t >= burst && t < burst + 8) flashTarget = 1F - (t - burst) / 8F;
                    Vec3 back = b.position().add(0, h * 0.6, 0);
                    return new Shot(back.subtract(f.scale(5.5)).add(right.scale(1.6)).add(0, -0.4, 0), back, 54,
                            t > burst - 10 && t < burst + 6 ? (float) Math.sin(t * 2.7) * 4F : 0, t == burst - 26);
                }
                // round the front, low and wide: Ascended Papon, the wings spreading, the roar
                double k = Math.min(1, (t - burst - 14) / 30.0);
                return new Shot(b.position().add(f.scale(10.5 - 2 * k)).add(0, 0.6, 0), body.add(0, h * 0.2, 0), 70 - (float) k * 6,
                        t >= roar && t < roar + 20 ? (float) Math.sin(t * 2.2) * 3F : 0, t == burst + 14);
            }
            case P3_POWER -> {
                int line = VoiceLines.beat("power.line", 22), grin = VoiceLines.beat("power.grin", 60);
                if (t < line - 2) {
                    // side on, wide: the power goes through him and he staggers back, a foot catching him
                    return new Shot(body.add(right.scale(7.0)).add(f.scale(3.0)).add(0, 0.2, 0), body, 58,
                            t < 12 ? (float) Math.sin(t * 2.4) * 3F : 0, t == 0);
                }
                if (t < grin) {
                    // close: looking at his hands, "Whoa, WHOA... so much POWER."
                    return new Shot(face.add(f.scale(5.0)).add(right.scale(1.0)).add(0, -0.8, 0), face.add(0, -0.3, 0), 46, 0, t == line - 2);
                }
                // the grin, in tight
                Vec3 grinAt = b.position().add(0, h * 0.44, 0);
                return new Shot(grinAt.add(f.scale(3.6)).add(0, -0.1, 0), grinAt, 44, 0, t == grin);
            }
            case P3_FELT -> {
                int ln = VoiceLines.beat("felt.line", 14), sm = VoiceLines.beat("felt.smile", 100);
                if (t < ln) {
                    // all of him: Ascended Papon, the wings open, in his light
                    return new Shot(body.add(f.scale(8.5)).add(right.scale(1.5)).add(0, -0.6, 0), body.add(0, h * 0.15, 0), 60, 0, t == 0);
                }
                if (t < sm) {
                    double k = (t - ln) / (double) Math.max(1, sm - ln);
                    return new Shot(face.add(f.scale(6.0 - 2.6 * k)).add(right.scale(0.5)).add(0, -0.4, 0), face, 44 - (float) k * 8, 0, t == ln);
                }
                // the smile: in on his face, mouth and eyes both
                Vec3 grin = b.position().add(0, h * (b.isAscended() ? 0.42 : 0.6), 0);
                return new Shot(grin.add(f.scale(3.4)).add(0, -0.2, 0), grin, 46, 0, t == sm);
            }
            case P3_TO_CAR -> {
                return new Shot(face.add(f.scale(5.5)).add(right.scale(2.0)).add(0, -0.5, 0), face, 50, 0, t == 0);
            }
            case P3_CRUSH -> {
                com.groid.papon.vehicle.CaravanEntity car = mc.level.getEntitiesOfClass(com.groid.papon.vehicle.CaravanEntity.class,
                        b.getBoundingBox().inflate(24), c -> true).stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(b))).orElse(null);
                int lifted = VoiceLines.beat("crush.lifted", 14), ball = VoiceLines.beat("crush.ball", 42), ref = VoiceLines.beat("crush.reform", 52),
                        down = VoiceLines.beat("crush.down", 66), ln = VoiceLines.beat("crush.end", 96) - 14;
                Vec3 over = b.position().add(0, h + 0.6, 0);
                if (t < lifted - 14) {
                    // his face over the old van's roof, his hand on it: "It's about time I gave this thing an UPGRADE."
                    Vec3 mid = car != null ? face.lerp(car.position().add(0, 1.4, 0), 0.3) : face;
                    return new Shot(mid.add(f.scale(5.6)).add(right.scale(1.6)).add(0, -0.4, 0), mid, 48, 0, t == 0);
                }
                if (t < lifted + 6) return new Shot(b.position().add(f.scale(9)).add(right.scale(2)).add(0, 0.5, 0), over.add(0, -1, 0), 66, 0, t == lifted - 14);
                if (t < ball) {
                    // crushing it: in close under it, his hands, the metal folding
                    return new Shot(face.add(f.scale(5.5)).add(right.scale(2.5)).add(0, -0.8, 0), over, 50,
                            (float) Math.sin(t * 1.9) * 2F, t == lifted + 6);
                }
                if (t < ref) {
                    // the ball, glowing in his fist
                    Vec3 fist = b.position().add(f.scale(0.6)).add(0, h + 0.2, 0);
                    return new Shot(fist.add(f.scale(3.6)).add(right.scale(1.2)).add(0, -0.4, 0), fist, 40, 0, t == ball);
                }
                if (t < ref + 6) flashTarget = 1F - (t - ref) / 6F;
                // down it comes, and the slam: wide, the dust going up round it
                return new Shot(b.position().add(f.scale(11)).add(right.scale(-3)).add(0, 1.5, 0), b.position().add(f.scale(3)).add(0, h * 0.5, 0), 64,
                        t >= down && t < down + 5 ? (float) Math.sin(t * 2.5) * 3F : 0, t == ref);
            }
            case P3_SHOWCAR -> {
                com.groid.papon.vehicle.CaravanEntity car = mc.level.getEntitiesOfClass(com.groid.papon.vehicle.CaravanEntity.class,
                        b.getBoundingBox().inflate(24), c -> true).stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(b))).orElse(null);
                return car == null ? new Shot(face.add(f.scale(5)).add(0, -0.3, 0), face, 48, 0, t == 0) : showcarShot(b, car, t, face, h);
            }
            case P3_GO -> {
                int ln = VoiceLines.beat("go.line", 8);
                double k = Math.min(1, t / 6.0);
                return new Shot(face.add(f.scale(4.0 + k)).add(0, -0.3, 0), face, 48 + (float) k * 6, t < ln + 10 ? (float) Math.sin(t * 2.2) * 3F : 0, t == 0);
            }
            case P3_DANCE -> {
                if (!b.inColiseum() || singer == null) { return new Shot(face.add(f.scale(4.6)).add(0, -0.2, 0), face, 42, 0, t == 0); }
                int drop = VoiceLines.beat("dance.drop", 42);
                if (t < drop) return new Shot(singerFace.add(stageFacing.scale(3.6 - t * 0.015)).add(stageSide.scale(-0.7)), singerFace, 44, 0, t == 0);
                if (t < drop + 22) {
                    // the band crashes in and the fire comes back round the whole bowl: high, behind the band, out over it
                    double k = Math.min(1, (t - drop) / 22.0);
                    Vec3 eye = stage.subtract(stageFacing.scale(4 + 2 * k)).add(0, 9 + 3 * k, 0);
                    return new Shot(eye, center.add(0, 2, 0), 76, t < drop + 6 ? (float) Math.sin(t * 2.6) * 4F : 0, t == drop, true);
                }
                int ln = VoiceLines.beat("dance.line", drop + 34);
                if (t < ln - 6) {
                    // it goes through him: his face, low, the crimson fire behind
                    return new Shot(face.add(f.scale(5.4)).add(right.scale(-1.0)).add(0, -1.0, 0), face, 50, 0, t == drop + 22);
                }
                // "Now... let's DANCE.": close, and closer
                double k = Math.min(1, (t - (ln - 6)) / 50.0);
                return new Shot(face.add(f.scale(5.0 - 1.0 * k)).add(right.scale(0.4)).add(0, -0.3, 0), face, 44 - (float) k * 6, 0, t == ln - 6);
            }
            default -> {
                return null;
            }
        }
    }

    /** The band member playing {@code role} (or, null, the singer), else {@code fallback}. */
    @Nullable
    private static com.groid.papon.boss.MariachiEntity role(java.util.List<com.groid.papon.boss.MariachiEntity> band,
                                                            @Nullable com.groid.papon.boss.BandRole role,
                                                            @Nullable com.groid.papon.boss.MariachiEntity fallback) {
        for (com.groid.papon.boss.MariachiEntity m : band) if (role == null ? m.getRole().singer() : m.getRole() == role) return m;
        return fallback;
    }

    /**
     * The cut to the band mid-fight (P2_CUTAWAY, 1.8 s, two quick shots):
     *   TRUMPETS   low in front of the three of them as the horns come up; then along the row, the bells flaring
     *   VIOLINS    in tight on a bow arm flying over the strings; then gliding along the three fiddles
     *   GUITARRON  from the floor up the big guitarron to Gordo; then his face, deep in it
     *   STRINGS    the vihuela's strumming hand; then the guitarist's face
     *   CORO       the three of them lifting their mics; then in on the middle one singing
     *   STANDS     from the sand up at the fans going wild, flags waving; then from the top rows down over their heads
     */
    @Nullable
    private static Shot cutawayShot(PaponBossEntity b, PaponBossEntity.Cutaway c, int t, java.util.List<com.groid.papon.boss.MariachiEntity> band,
                                    Vec3 sf, Vec3 ss) {
        boolean second = t >= 18;
        int tt = second ? t - 18 : t;
        double k = tt / 18.0;
        boolean cut = t == 0 || t == 18;
        if (c == PaponBossEntity.Cutaway.STANDS) {
            // the long side of the bowl nearest him: up at the stands from the sand, then down over the fans from the top rows
            com.groid.papon.boss.ColiseumCrowdEntity crowd = Minecraft.getInstance().level.getEntitiesOfClass(
                    com.groid.papon.boss.ColiseumCrowdEntity.class, b.getBoundingBox().inflate(80), e -> true).stream().findFirst().orElse(null);
            Vec3 ctr = crowd != null ? crowd.position() : b.position();
            Vec3 out = new Vec3(b.getX() >= ctr.x ? 1 : -1, 0, 0);
            Vec3 along = new Vec3(0, 0, 1);
            int a = com.groid.papon.boss.Coliseum.A;
            if (!second) {
                Vec3 eye = ctr.add(out.scale(a - 7)).add(along.scale(-6 + 5 * k)).add(0, 1.2, 0);
                return new Shot(eye, ctr.add(out.scale(a + 9)).add(along.scale(2 + 4 * k)).add(0, 9, 0), 72, 0, cut, true);
            }
            Vec3 eye = ctr.add(out.scale(a + 11)).add(along.scale(4 - 6 * k)).add(0, 15, 0);
            return new Shot(eye, b.position().add(0, b.getBbHeight() * 0.5, 0), 70, 0, cut);
        }
        Shot sh = bandCut(c, second, k, cut, band, sf, ss);
        return sh == null ? null : new Shot(clearOfBand(sh.eye(), band), sh.lookAt(), sh.fov(), sh.roll(), sh.cut(), sh.free());
    }

    /** A camera inside one of the band (they stand close on the stage): up over his head instead. */
    private static Vec3 clearOfBand(Vec3 eye, java.util.List<com.groid.papon.boss.MariachiEntity> band) {
        for (int pass = 0; pass < 2; pass++) {
            for (com.groid.papon.boss.MariachiEntity m : band) {
                net.minecraft.world.phys.AABB box = m.getBoundingBox().inflate(0.45, 0.2, 0.45);
                if (box.contains(eye)) eye = new Vec3(eye.x, box.maxY + 0.35, eye.z);
            }
        }
        return eye;
    }

    @Nullable
    private static Shot bandCut(PaponBossEntity.Cutaway c, boolean second, double k, boolean cut,
                                java.util.List<com.groid.papon.boss.MariachiEntity> band, Vec3 sf, Vec3 ss) {
        com.groid.papon.boss.MariachiEntity m1 = member(band, c, 0), m2 = member(band, c, 1), m3 = member(band, c, 2);
        if (m1 == null) return null;
        if (c == PaponBossEntity.Cutaway.VIOLINS) {
            // the front-most fiddler for the close-up (the others stand behind him)
            com.groid.papon.boss.MariachiEntity front = m1;
            for (com.groid.papon.boss.MariachiEntity m : new com.groid.papon.boss.MariachiEntity[]{m2, m3}) {
                if (m != null && m.position().dot(sf) > front.position().dot(sf)) front = m;
            }
            if (!second) {
                Vec3 f = Vec3.directionFromRotation(0, front.getYRot());
                Vec3 belly = front.position().add(f.scale(0.7)).add(0, front.getBbHeight() * 0.42, 0);
                return new Shot(belly.add(sf.scale(2.1)).add(ss.scale(-0.9 + 0.8 * k)).add(0, 0.45, 0), belly.add(0, 0.15, 0), 46, 0, cut);
            }
        }
        Vec3 f1 = Vec3.directionFromRotation(0, m1.getYRot());
        Vec3 s1 = new Vec3(-f1.z, 0, f1.x);
        Vec3 face1 = m1.position().add(0, m1.getBbHeight() * 0.72, 0);
        Vec3 belly1 = m1.position().add(f1.scale(0.7)).add(0, m1.getBbHeight() * 0.38, 0);
        switch (c) {
            case TRUMPETS -> {
                Vec3 mid = m3 != null ? m1.position().lerp(m3.position(), 0.5) : m1.position();
                if (!second) {
                    // over the heads of the fiddles in front of them, pushing in on the horns as they come up
                    Vec3 at = mid.add(0, m1.getBbHeight() * 0.8, 0);
                    return new Shot(mid.add(sf.scale(3.4 - 0.8 * k)).add(0, m1.getBbHeight() + 1.0, 0), at, 54 - (float) k * 6, 0, cut);
                }
                // along the row from the end, the bells in a line
                Vec3 end = m3 != null ? m3.position() : m1.position();
                Vec3 dir = end.subtract(m1.position()).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1e-3 ? ss : dir.normalize();
                Vec3 eye = m1.position().subtract(dir.scale(2.4)).add(sf.scale(1.2 + 0.4 * k)).add(0, m1.getBbHeight() * 1.05, 0);
                return new Shot(eye, end.add(0, m1.getBbHeight() * 0.8, 0), 50, 0, cut);
            }
            case VIOLINS -> {
                if (!second) {
                    // the bow arm, close, sliding past
                    return new Shot(belly1.add(sf.scale(1.9)).add(s1.scale(-1.4 + 1.0 * k)).add(0, 0.5, 0), belly1.add(0, 0.2, 0), 44, 0, cut);
                }
                Vec3 end = m3 != null ? m3.position() : m1.position().add(ss.scale(3));
                Vec3 at = m1.position().lerp(end, k).add(0, m1.getBbHeight() * 0.6, 0);
                return new Shot(at.add(sf.scale(3.2)).add(0, 0.4, 0), at, 52, 0, cut);
            }
            case GUITARRON -> {
                if (!second) {
                    // from the floor, up the big guitarron to him
                    return new Shot(m1.position().add(f1.scale(2.4)).add(s1.scale(-0.9)).add(0, 0.25, 0), belly1.lerp(face1, 0.3 * k), 60, 0, cut);
                }
                return new Shot(face1.add(f1.scale(2.6 - 0.5 * k)).add(s1.scale(-0.6)).add(0, -0.1, 0), face1, 44, 0, cut);
            }
            case STRINGS -> {
                if (!second) return new Shot(belly1.add(f1.scale(1.8)).add(s1.scale(1.2)).add(0, 0.4, 0), belly1, 44, 0, cut);
                com.groid.papon.boss.MariachiEntity g = m2 != null ? m2 : m1;
                Vec3 gf = Vec3.directionFromRotation(0, g.getYRot());
                Vec3 face = g.position().add(0, g.getBbHeight() * 0.72, 0);
                return new Shot(face.add(gf.scale(2.8 - 0.4 * k)).add(new Vec3(-gf.z, 0, gf.x).scale(0.7)), face, 46, 0, cut);
            }
            case CORO -> {
                Vec3 mid = m2 != null ? m2.position() : m1.position();
                if (!second) {
                    Vec3 at = mid.add(0, m1.getBbHeight() * 0.7, 0);
                    return new Shot(at.add(sf.scale(4.4 - 0.6 * k)).add(0, -0.2, 0), at, 56, 0, cut);
                }
                com.groid.papon.boss.MariachiEntity g = m2 != null ? m2 : m1;
                Vec3 face = g.position().add(0, g.getBbHeight() * 0.72, 0);
                return new Shot(face.add(sf.scale(2.6 - 0.3 * k)).add(ss.scale(0.6)), face, 42, 0, cut);
            }
            default -> {
                return null;
            }
        }
    }

    /** The {@code i}th member of the band playing what a cutaway is about (stage order). */
    @Nullable
    private static com.groid.papon.boss.MariachiEntity member(java.util.List<com.groid.papon.boss.MariachiEntity> band, PaponBossEntity.Cutaway c, int i) {
        java.util.List<com.groid.papon.boss.MariachiEntity> l = band.stream().filter(m -> switch (c) {
            case TRUMPETS -> m.getRole().instrument.equals("trumpet");
            case VIOLINS -> m.getRole().instrument.equals("violin");
            case GUITARRON -> m.getRole() == com.groid.papon.boss.BandRole.GUITARRON;
            case STRINGS -> m.getRole() == com.groid.papon.boss.BandRole.VIHUELA || m.getRole() == com.groid.papon.boss.BandRole.GUITAR;
            case CORO -> m.getRole().coro();
            default -> false;
        }).sorted(Comparator.comparingInt(m -> m.getRole().ordinal())).toList();
        return i < l.size() ? l.get(i) : null;
    }

    /**
     * The Skyline shown off (P3_SHOWCAR), a quick-cut montage on its rev (beats showcar.*):
     *   dark          low and wide off its nose in the dust of the slam, the car still dark
     *   lamps         in tight on a headlamp as it blinks on with the engine
     *   glow          on the sand by the side skirt, looking down the flank as the underglow stutters on front to back
     *   rev           the rear three-quarters, low at the twin pipes: blip, pop, blip, pop
     *   +16           a whip round the nose, high enough for the flaming eagle on the hood
     *   +30           straight down over the roof, turning, the purple pool all round it
     *   +44           racing along the flank at wheel height as it screams to the limiter
     *   bov           high behind the GT wing, the overrun crackling fire out of the pipes
     *   line - 4      the hero shot: low off the far corner, the car and him by it, "...and in with the NEW."
     * Every shot is placed on purpose (free: never pushed off the car's own box).
     */
    private static Shot showcarShot(PaponBossEntity b, com.groid.papon.vehicle.CaravanEntity car, int t, Vec3 face, float h) {
        int lamps = VoiceLines.beat("showcar.lamps", 16), glow = VoiceLines.beat("showcar.glow", 34), rev = VoiceLines.beat("showcar.rev", 56),
                bov = VoiceLines.beat("showcar.bov", 110), line = VoiceLines.beat("showcar.line", 128);
        double hl = com.groid.papon.vehicle.CaravanEntity.HALF_LENGTH, hw = com.groid.papon.vehicle.CaravanEntity.HALF_WIDTH;
        Vec3 cp = car.position();
        if (t < lamps) {
            double k = t / (double) lamps;
            return new Shot(car.local(3.4, 0.5, hl + 5.5 - 0.6 * k), car.local(0, 0.55, 0), 60, 0, t == 0, true);
        }
        if (t < glow - 2) {
            double k = (t - lamps) / (double) (glow - 2 - lamps);
            return new Shot(car.local(hw + 0.7 - 0.2 * k, 0.95, hl + 1.9 - 0.4 * k), car.local(0.75, 0.78, hl + 0.05), 38, 0, t == lamps, true);
        }
        if (t < rev - 2) {
            double k = (t - glow + 2) / (double) (rev - glow);
            return new Shot(car.local(hw + 1.25, 0.14, hl + 0.9 - 0.8 * k), car.local(hw * 0.5, 0.06, -hl), 62, 0, t == glow - 2, true);
        }
        int r = t - rev;
        if (r < 16) {
            float kick = r >= 8 && r < 11 || r >= 20 ? 4F : 0;
            return new Shot(car.local(-1.7, 0.4, -hl - 2.3 + r * 0.03), car.local(0, 0.36, -hl - 0.1), 50 + kick, 0, r == -2, true);
        }
        if (r < 30) {
            double k = (r - 16) / 14.0;
            k = k * k * (3 - 2 * k);
            double a = Math.toRadians(-55 + 125 * k);
            Vec3 at = car.local(Math.sin(a) * 4.8, 1.9, hl * 0.4 + Math.cos(a) * 4.8);
            return new Shot(at, car.local(0, 0.7, hl * 0.45), 56, (float) (Math.sin(k * Math.PI) * -6), r == 16, true);
        }
        if (r < 44) {
            double a = Math.toRadians((r - 30) * 7.0);
            Vec3 off = car.forward().scale(Math.cos(a) * 0.35).add(car.left().scale(Math.sin(a) * 0.35));
            return new Shot(cp.add(0, 8.5, 0).add(off), cp.add(0, 0.5, 0), 62, 0, r == 30, true);
        }
        if (t < bov) {
            double k = (r - 44) / (double) Math.max(1, bov - rev - 44);
            double z = hl + 0.6 - (2 * hl + 1.2) * k;
            return new Shot(car.local(-(hw + 1.05), 0.32, z), car.local(-hw, 0.36, z - 1.4), 46, (float) Math.sin(t * 2.1) * 1.5F, r == 44, true);
        }
        if (t < line - 4) {
            double k = (t - bov) / (double) Math.max(1, line - 4 - bov);
            return new Shot(car.local(0.4, 2.8 + 0.3 * k, -hl - 2.7 - 0.5 * k), car.local(0, 0.9, -0.5), 58, 0, t == bov, true);
        }
        // the hero shot: off the car's far corner from him, low, the car big in front and him by it
        Vec3 away = cp.subtract(b.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-3 ? car.forward() : away.normalize();
        Vec3 across = new Vec3(-away.z, 0, away.x);
        double k = Math.min(1, (t - line + 4) / 60.0);
        Vec3 look = b.position().add(0, h * 0.5, 0).lerp(cp.add(0, 0.6, 0), 0.4);
        return new Shot(cp.add(away.scale(4.4 - 0.7 * k)).add(across.scale(2.6)).add(0, 0.45, 0), look, 58 - (float) k * 6, 0, t == line - 4, true);
    }

    /** Client ticks to the singer's first word, from the band's synced downbeat. */
    private static int ticksToSinging(Minecraft mc, com.groid.papon.boss.MariachiEntity singer) {
        com.groid.papon.boss.Song song = com.groid.papon.boss.Song.get();
        long now = mc.level.getGameTime() & 0x7FFFFFFF;
        long since = (now - (singer.songStart() & 0x7FFFFFFFL)) & 0x7FFFFFFFL;
        if (since >= (1L << 30)) since = 0;
        return (int) ((song != null ? song.fightStartTicks() : 300) - since);
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
        // a cut is a cut whether or not the shot's tick was seen (t == n can be skipped when the clock follows the server):
        // a new shot far from the camera, or looking somewhere else entirely, is cut to, not swooped to
        boolean cut = shot.cut() || jumped(shot);
        place(shot, cut);
        if (cut) fov = shot.fov();
        else fov += (shot.fov() - fov) * 0.3F;
        roll += (shot.roll() - roll) * 0.4F;
    }

    private static boolean jumped(Shot shot) {
        if (shot.eye().distanceTo(camera.position()) > 4.0) return true;
        Vec3 look = shot.lookAt().subtract(shot.eye());
        if (look.lengthSqr() < 1e-6) return false;
        return look.normalize().dot(Vec3.directionFromRotation(camera.getXRot(), camera.getYRot())) < 0.55;
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
