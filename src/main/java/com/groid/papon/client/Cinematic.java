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

    private static double smooth(double k) {
        return k * k * (3 - 2 * k);
    }

    /** A camera position for a tick; {@code free}: placed in the open on purpose (a wide shot of the stands), not to be moved off walls. */
    private record Shot(Vec3 eye, Vec3 lookAt, float fov, float roll, boolean cut, boolean free) {
        Shot(Vec3 eye, Vec3 lookAt, float fov, float roll, boolean cut) {
            this(eye, lookAt, fov, roll, cut, false);
        }
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
                if (t < 28) {
                    yield new Shot(b.position().add(right.scale(6.5)).add(f.scale(3.0)).add(0, 0.6, 0), face, 55, 0, t == 0);
                }
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
        if (!b.inColiseum() || !(s == BossState.P2_ARRIVE || s.coliseum() || s == BossState.P2_SOLO)) return null;
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
                double push = Math.min(1, t / 70.0);
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

    @Nullable
    private static Shot phaseThreeShot(PaponBossEntity b, BossState s, int t, Vec3 f0, Vec3 right0) {
        if (!s.phaseThree()) return null;
        Minecraft mc = Minecraft.getInstance();
        // the way his body (the model) faces, not where his head was last told to look
        Vec3 f = Vec3.directionFromRotation(0, b.yBodyRot);
        Vec3 right = new Vec3(-f.z, 0, f.x);
        float h = b.getBbHeight();
        Vec3 body = b.position().add(0, h * 0.55, 0);
        Vec3 face = b.position().add(0, h * 0.74, 0);
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
                if (t < 16) {
                    // in front of his face as he is blown away from the lens, down onto the sand
                    Vec3 eye = face.add(f.scale(5.2 + t * 0.15)).add(right.scale(1.2)).add(0, -0.4, 0);
                    return new Shot(eye, face, 50 + (t >= 13 && t < 16 ? 10 : 0), t >= 14 ? (float) Math.sin(t * 2.7) * 5F : 0, t == 0);
                }
                if (t < 34) {
                    // straight down on his face as he lies there, dazed, the dust settling
                    Vec3 head = b.position().subtract(f.scale(h * 0.6)).add(0, 0.5, 0);
                    return new Shot(head.add(0, 3.6, 0).add(f.scale(0.4)), head, 50, 0, t == 16);
                }
                // and up: from the ground in front of him, looking up into his face as he rises
                return new Shot(b.position().add(f.scale(3.6)).add(right.scale(0.6)).add(0, 0.3, 0), face, 52, 0, t == 34);
            }
            case P3_GLARE -> {
                int ns = VoiceLines.beat("glare.notstyle", 40), rp = VoiceLines.beat("glare.realplay", 100);
                if (t < 22) return new Shot(face.add(f.scale(4.4)).add(right.scale(1.0)).add(0, -0.4, 0), face, 46, 0, t == 0);
                int eyes = Math.max(40, ns - 30);
                if (t < eyes) return new Shot(face.add(f.scale(2.9)).add(0, -0.3, 0), face.add(0, -0.2, 0), 22, 0, t == 22);    // his eyes
                if (t < ns) return new Shot(singerFace.add(stageFacing.scale(3.6)).add(stageSide.scale(0.5)), singerFace, 34, 0, t == eyes);
                if (t < rp) {
                    double k = Math.min(1, (t - ns) / (double) Math.max(20, rp - ns));
                    return new Shot(face.add(f.scale(4.4 - 1.0 * k)).add(right.scale(0.5)).add(0, -0.35, 0), face, 40 - (float) k * 6, 0, t == ns);
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
                if (t < 118) {
                    // the stands, from down on the sand: the first lighters coming on, faces in awe
                    // over the middle of the sand, so the camera is always in the open, looking up at the stands
                    Vec3 side = right.scale(b.position().subtract(center).dot(right) > 0 ? -1 : 1);
                    Vec3 eye = center.add(side.scale(-4)).add(0, 3.0, 0);
                    return new Shot(eye, center.add(side.scale(com.groid.papon.boss.Coliseum.A + 6)).add(0, 8, 0), 66, 0, t == 85, true);
                }
                if (t < 150) {
                    // the band, watching him play: the singer's face, the coro behind
                    return new Shot(singerFace.add(stageFacing.scale(4.2)).add(stageSide.scale(-1.6)).add(0, 0.2, 0), singerFace.add(stageSide.scale(1.2)), 50, 0, t == 118);
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
                if (t < 330) {
                    double k = (t - sing) / (double) Math.max(1, 330 - sing);
                    return new Shot(face.add(f.scale(5.2 - 1.6 * k)).add(right.scale(0.6 - 0.4 * k)).add(0, -0.35, 0), face, 46 - (float) k * 8, 0, t == sing);
                }
                if (t < 358) {
                    // the band, in awe of him
                    return new Shot(stage.add(stageFacing.scale(6.5)).add(stageSide.scale(2.5)).add(0, 1.8, 0), stage.add(0, 1.2, 0), 56, 0, t == 330);
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
                // the band, looking at each other: who goes first?
                return new Shot(stage.add(stageFacing.scale(6.5)).add(0, 1.8, 0), stage.add(0, 1.1, 0), 54, 0, t == gl + 4);
            }
            case P3_ASCEND -> {
                int burst = VoiceLines.beat("ascend.burst", 100), roar = VoiceLines.beat("ascend.roar", 108);
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
                if (t < roar + 40) {
                    // round the front, low and wide: Ascended Papon, the wings spreading, the roar
                    double k = Math.min(1, (t - burst - 14) / 30.0);
                    return new Shot(b.position().add(f.scale(10.5 - 2 * k)).add(0, 0.6, 0), body.add(0, h * 0.2, 0), 70 - (float) k * 6,
                            t >= roar && t < roar + 20 ? (float) Math.sin(t * 2.2) * 3F : 0, t == burst + 14);
                }
                // the band and the stands, in awe
                return new Shot(stage.add(stageFacing.scale(6.5)).add(stageSide.scale(-2.5)).add(0, 1.8, 0), stage.add(0, 1.2, 0), 58, 0, t == roar + 40);
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
                // the smile: right in on it
                return new Shot(face.add(f.scale(3.0)).add(0, -0.25, 0), face.add(0, -0.1, 0), 30, 0, t == sm);
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
                if (t < lifted + 6) return new Shot(b.position().add(f.scale(9)).add(right.scale(2)).add(0, 0.5, 0), over.add(0, -1, 0), 66, 0, t == 0);
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
                if (t < down + 4) return new Shot(b.position().add(f.scale(11)).add(right.scale(-3)).add(0, 1.5, 0), b.position().add(f.scale(3)).add(0, h * 0.5, 0), 64, 0, t == ref);
                if (t < ln - 4 && car != null) {
                    // the Skyline: low along its flank, the purple glow under it, him behind
                    Vec3 side = car.left();
                    Vec3 cp = car.position().add(0, 0.6, 0);
                    // the side of the car toward the middle of the sand (there is room there)
                    if (cp.add(side.scale(6.5)).distanceTo(center) > cp.add(side.scale(-6.5)).distanceTo(center)) side = side.scale(-1);
                    return new Shot(cp.add(side.scale(6.5)).add(car.forward().scale(3.5)).add(0, -0.1, 0), cp.add(car.forward().scale(-1)), 54, 0, t == down + 4);
                }
                // by his new car: his face, the evil smile
                return new Shot(face.add(f.scale(5.2)).add(right.scale(0.6)).add(0, -0.4, 0), face, 46, 0, t == ln - 4);
            }
            case P3_GO -> {
                int ln = VoiceLines.beat("go.line", 8);
                double k = Math.min(1, t / 6.0);
                return new Shot(face.add(f.scale(4.0 + k)).add(0, -0.3, 0), face, 48 + (float) k * 6, t < ln + 10 ? (float) Math.sin(t * 2.2) * 3F : 0, t == 0);
            }
            case P3_DANCE -> {
                if (!b.inColiseum() || singer == null) {
                    return new Shot(face.add(f.scale(4.6)).add(0, -0.2, 0), face, 42, 0, t == 0);
                }
                int drop = VoiceLines.beat("dance.drop", 42);
                if (t < drop) return new Shot(singerFace.add(stageFacing.scale(3.6 - t * 0.015)).add(stageSide.scale(-0.7)), singerFace, 44, 0, t == 0);
                if (t < drop + 36) {
                    // the band crashes in and the fire comes back round the whole bowl: high, behind the band, out over it
                    double k = Math.min(1, (t - drop) / 36.0);
                    Vec3 eye = stage.subtract(stageFacing.scale(4 + 2 * k)).add(0, 9 + 3 * k, 0);
                    return new Shot(eye, center.add(0, 2, 0), 76, t < drop + 6 ? (float) Math.sin(t * 2.6) * 4F : 0, t == drop, true);
                }
                int ln = VoiceLines.beat("dance.line", drop + 44);
                if (t < ln - 6) {
                    // it goes through him: his face, low, the crimson fire behind
                    return new Shot(face.add(f.scale(5.4)).add(right.scale(-1.0)).add(0, -1.0, 0), face, 50, 0, t == drop + 36);
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
