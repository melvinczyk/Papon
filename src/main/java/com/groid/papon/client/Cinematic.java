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
     *   P2_SHOWTIME  the guitar: in on his hands, tilting up to his face, the shades, the aura
     *   P2_APOLOGY   the two of you as he comes over; on him apologising; the aura: a slow low orbit
     *   P2_RESUME    close: "Where were we?"
     *   P2_HYPE      over his shoulder to the roaring stands as he cups his ear; round him as he turns to
     *                the other side; craning up through the raise-the-roof with the bowl behind him; from
     *                behind you as he points you out; the FOV punch as the place erupts and the singing starts
     *   P2_SOLO      (mid-fight) the singer's guitar solo: in on his hands, up to his face in the aura, then
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
