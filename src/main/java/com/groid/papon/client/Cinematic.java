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
    private static int lastBossState = -1;

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

    private record Shot(Vec3 eye, Vec3 lookAt, float fov, float roll, boolean cut) {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        barsO = bars;
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
    }

    @Nullable
    private static Shot findShot(Minecraft mc) {
        Vec3 me = mc.player.position();
        AABB box = new AABB(me, me).inflate(RANGE);
        PaponBossEntity boss = mc.level.getEntitiesOfClass(PaponBossEntity.class, box).stream()
                .min(Comparator.comparingDouble(b -> b.distanceToSqr(me))).orElse(null);
        if (boss != null) {
            Shot s = bossShot(boss);
            if (s != null) return s;
        }
        TacoVendorEntity vendor = mc.level.getEntitiesOfClass(TacoVendorEntity.class, box).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(me))).orElse(null);
        return vendor == null ? null : vendorShot(vendor);
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
            case REALIZE -> new Shot(face.add(f.scale(2.7 - t / 60.0 * 0.6)).add(right.scale(0.25)), face.add(0, 0.2, 0),
                    40, (float) Math.sin(t * 0.15) * 4F, t == 0);
            case RAGE -> t < 34
                    ? new Shot(face.add(f.scale(2.4 - t / 34.0 * 0.4)), face.add(0, 0.1, 0), 40, (float) Math.sin(t * 0.9) * t * 0.12F, t == 0)
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
        return switch (s) {
            case LEAP_OUT -> {
                Vec3 base = leapStart != null ? leapStart : b.position();
                Vec3 eye = base.add(f.scale(4.5)).add(right.scale(8.5)).add(0, 2.2, 0);
                yield new Shot(eye, body, 60, 0, t == 0);
            }
            case CHUG -> {
                double orbit = Math.toRadians(-25 + t * 0.4);
                Vec3 dir = f.scale(Math.cos(orbit)).add(right.scale(Math.sin(orbit)));
                yield new Shot(b.position().add(dir.scale(5.2)).add(0, 0.5, 0), body.add(0, 0.5, 0), 55, 0, t == 0);
            }
            case ROAR -> t < 30
                    ? new Shot(b.position().add(f.scale(4.0 - Math.min(1, t / 6.0) * 1.2)).add(0, 1.0, 0), body.add(0, 0.6, 0), 55, 0, t == 0)
                    : null;
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
