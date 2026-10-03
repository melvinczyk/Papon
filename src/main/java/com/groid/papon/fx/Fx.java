package com.groid.papon.fx;

import com.groid.papon.registry.ModParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The boss fight's visual language, spawned from the server. Few particles, each one meaning
 * something: a warning on the ground lives exactly as long as the wait before the hit, in the colour
 * of what is coming (orange fire, white salt, red "do not stand here", gold potato); hits get one
 * cut, landings one ring and a little dust. Colours are 0xRRGGBB.
 */
public final class Fx {
    public static final int FIRE = 0xFF9A3C, SOUL = 0x7CF0FF, DANGER = 0xFF2A1E, SALT = 0xF4F1E8, POTATO = 0xF2C24E,
            MASH = 0xF3E6C2, TEQUILA = 0xFFC24A, WHITE = 0xFFFFFF, SMOKE = 0x6E6A66, DUST = 0xB8A88C, GREASE = 0xE8D27A,
            SALSA = 0xE0321E, GREEN = 0x6BE24A;

    /** Decal variants (particles/decal.json order). */
    public static final int DECAL_RING = 0, DECAL_RING_SOFT = 1, DECAL_SIGIL = 2, DECAL_CROSS = 3, DECAL_SCORCH = 4,
            DECAL_GLOW = 5, DECAL_HALO = 6, DECAL_SPLAT = 7;

    private Fx() {}

    private static void send(ServerLevel level, ParticleType<FxOptions> type, int sprite, double x, double y, double z, float size,
                             float grow, int color, float alpha, int life, float yaw, int count, double spread, double vx,
                             double vy, double vz) {
        FxOptions o = new FxOptions(type, sprite, size, grow, color, alpha, life, yaw);
        if (count <= 0) {
            level.sendParticles(o, x, y, z, 0, vx, vy, vz, 1.0);
            return;
        }
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            level.sendParticles(o, x + (r.nextDouble() - 0.5) * 2 * spread, y + (r.nextDouble() - 0.5) * spread,
                    z + (r.nextDouble() - 0.5) * 2 * spread, 0, vx + (r.nextDouble() - 0.5) * 0.04, vy, vz + (r.nextDouble() - 0.5) * 0.04, 1.0);
        }
    }

    // ------------------------------------------------------------ warnings and impacts on the ground

    /** A ground warning of radius r that lasts {@code life} ticks: the hit comes when it goes. */
    public static void warn(ServerLevel level, Vec3 at, float r, int color, int life) {
        send(level, ModParticles.DECAL.get(), DECAL_RING, at.x, at.y + 0.03, at.z, r, 1F, color, 0.95F, life, 0, 0, 0, 0, 0, 0);
        send(level, ModParticles.DECAL.get(), DECAL_GLOW, at.x, at.y + 0.02, at.z, r * 0.95F, 1F, color, 0.35F, life, 0, 0, 0, 0, 0, 0);
    }

    /** A warning that closes in: a ring shrinking onto the spot as the moment comes. */
    public static void warnClosing(ServerLevel level, Vec3 at, float r, int color, int life) {
        send(level, ModParticles.DECAL.get(), DECAL_RING, at.x, at.y + 0.04, at.z, r * 1.8F, 1F / 1.8F, color, 0.9F, life, 0, 0, 0, 0, 0, 0);
        send(level, ModParticles.DECAL.get(), DECAL_GLOW, at.x, at.y + 0.02, at.z, r, 1F, color, 0.3F, life, 0, 0, 0, 0, 0, 0);
    }

    /** A turning sigil: something is being summoned here. */
    public static void sigil(ServerLevel level, Vec3 at, float r, int color, int life) {
        send(level, ModParticles.DECAL.get(), DECAL_SIGIL, at.x, at.y + 0.03, at.z, r, 1F, color, 0.9F, life, 0, 0, 0, 0, 0, 0);
    }

    /** A lasting zone (a hot griddle, a puddle): a soft disc. */
    public static void zone(ServerLevel level, Vec3 at, float r, int color, int life, float alpha) {
        send(level, ModParticles.DECAL.get(), DECAL_RING_SOFT, at.x, at.y + 0.03, at.z, r, 1F, color, alpha, life, 0, 0, 0, 0, 0, 0);
        send(level, ModParticles.DECAL.get(), DECAL_GLOW, at.x, at.y + 0.02, at.z, r, 1F, color, alpha * 0.45F, life, 0, 0, 0, 0, 0, 0);
    }

    /** A line warning from a to b (the charge's path, a fry's flight): marks along it. */
    public static void warnLine(ServerLevel level, Vec3 a, Vec3 b, float width, int color, int life) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() / (width * 1.6)));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            send(level, ModParticles.DECAL.get(), DECAL_GLOW, p.x, p.y + 0.03, p.z, width, 1F, color, 0.55F, life, 0, 0, 0, 0, 0, 0);
        }
    }

    /** A scorch left on the ground. */
    public static void scorch(ServerLevel level, Vec3 at, float r, int life) {
        send(level, ModParticles.DECAL.get(), DECAL_SCORCH, at.x, at.y + 0.025, at.z, r, 1F, 0x241A14, 0.8F, life,
                level.random.nextFloat() * 360F, 0, 0, 0, 0, 0);
    }

    /** A splat (mash, salsa) on the ground. */
    public static void splat(ServerLevel level, Vec3 at, float r, int color, int life) {
        send(level, ModParticles.DECAL.get(), DECAL_SPLAT, at.x, at.y + 0.03, at.z, r, 1F, color, 0.9F, life,
                level.random.nextFloat() * 360F, 0, 0, 0, 0, 0);
    }

    /** A shockwave: one ring blown out to radius r. */
    public static void shock(ServerLevel level, Vec3 at, float r, int color) {
        send(level, ModParticles.SHOCK.get(), -1, at.x, at.y + 0.1, at.z, r * 0.35F, 2.9F, color, 0.95F, 10, 0, 0, 0, 0, 0, 0);
    }

    /** A landing: the ring and a little dust. */
    public static void land(ServerLevel level, Vec3 at, float r) {
        shock(level, at, r, WHITE);
        dust(level, at, r * 0.5, Math.max(3, (int) (r * 1.5)));
    }

    public static void dust(ServerLevel level, Vec3 at, double spread, int count) {
        send(level, ModParticles.DUST.get(), -1, at.x, at.y + 0.3, at.z, 0.9F, 1.8F, DUST, 0.8F, 18, 0, count, spread, 0, 0.02, 0);
    }

    // ------------------------------------------------------------ fire and smoke

    public static void fire(ServerLevel level, Vec3 at, int count, double spread, float size) {
        send(level, ModParticles.FIRE.get(), -1, at.x, at.y, at.z, size, 1.2F, WHITE, 1F, 16, 0, count, spread, 0, 0.04, 0);
    }

    /** One lick of flame flying with velocity v (breath, the blowtorch). */
    public static void jet(ServerLevel level, Vec3 at, Vec3 v, float size, int color) {
        send(level, ModParticles.FIRE.get(), -1, at.x, at.y, at.z, size, 2.2F, color, 1F, 14, 0, 0, 0, v.x, v.y, v.z);
    }

    /** Flames standing on the ground for a while (a burning puddle). */
    public static void groundFlame(ServerLevel level, Vec3 at, float size) {
        send(level, ModParticles.FLAME.get(), -1, at.x, at.y + size * 0.9, at.z, size, 1F, WHITE, 1F, 22, 0, 0, 0, 0, 0, 0);
    }

    public static void blast(ServerLevel level, Vec3 at, float size) {
        send(level, ModParticles.BLAST.get(), -1, at.x, at.y + size * 0.6, at.z, size, 1.4F, WHITE, 1F, 18, 0, 0, 0, 0, 0, 0);
        send(level, ModParticles.FLASH.get(), -1, at.x, at.y + size * 0.6, at.z, size * 0.9F, 1.3F, WHITE, 0.9F, 7, 0, 0, 0, 0, 0, 0);
    }

    public static void burst(ServerLevel level, Vec3 at, float size, int count, double spread) {
        send(level, ModParticles.BURST.get(), -1, at.x, at.y, at.z, size, 1.2F, WHITE, 1F, 10, 0, count, spread, 0, 0.02, 0);
    }

    public static void smoke(ServerLevel level, Vec3 at, int count, double spread, float size, int color) {
        send(level, ModParticles.SMOKE.get(), -1, at.x, at.y, at.z, size, 1.6F, color, 0.85F, 24, 0, count, spread, 0, 0.03, 0);
    }

    public static void flash(ServerLevel level, Vec3 at, float size) {
        send(level, ModParticles.FLASH.get(), -1, at.x, at.y, at.z, size, 1.4F, WHITE, 1F, 8, 0, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------ hits

    /** A swing: one arc at that yaw and height. */
    public static void slash(ServerLevel level, Vec3 at, float yaw, float size, int color) {
        send(level, ModParticles.SLASH.get(), -1, at.x, at.y, at.z, size, 1.15F, color, 0.95F, 7, yaw, 0, 0, 0, 0, 0);
    }

    /** Where a hit lands. */
    public static void hit(ServerLevel level, Vec3 at, float size, int color) {
        send(level, ModParticles.HIT.get(), -1, at.x, at.y, at.z, size, 1.3F, color, 1F, 6, 0, 0, 0, 0, 0, 0);
    }

    public static void spark(ServerLevel level, Vec3 at, int count, double spread, float size, int color) {
        RandomSource r = level.random;
        FxOptions o = new FxOptions(ModParticles.SPARK.get(), -1, size, 0.3F, color, 1F, 12, 0);
        for (int i = 0; i < count; i++) {
            level.sendParticles(o, at.x, at.y, at.z, 0, (r.nextDouble() - 0.5) * 0.6, r.nextDouble() * 0.45, (r.nextDouble() - 0.5) * 0.6, 1.0);
        }
    }

    /** One glint (a tell: an eye catching the light, a blade). */
    public static void glint(ServerLevel level, Vec3 at, float size, int color) {
        send(level, ModParticles.SPARK.get(), 3, at.x, at.y, at.z, size, 0.2F, color, 1F, 8, 0, 0, 0, 0, 0, 0);
    }

    /** Chunks thrown out (mash, salt, grit). */
    public static void chunks(ServerLevel level, Vec3 at, int count, float size, int color, double speed) {
        RandomSource r = level.random;
        FxOptions o = new FxOptions(ModParticles.CHUNK.get(), -1, size, 0.8F, color, 1F, 26, 0);
        for (int i = 0; i < count; i++) {
            level.sendParticles(o, at.x, at.y, at.z, 0, (r.nextDouble() - 0.5) * speed, (0.4 + r.nextDouble()) * speed * 0.8,
                    (r.nextDouble() - 0.5) * speed, 1.0);
        }
    }

    /** Falling flakes over an area (salt). */
    public static void fall(ServerLevel level, Vec3 at, int count, double spread, float size, int color) {
        RandomSource r = level.random;
        FxOptions o = new FxOptions(ModParticles.CHUNK.get(), 2, size, 0.7F, color, 1F, 24, 0);
        for (int i = 0; i < count; i++) {
            level.sendParticles(o, at.x + (r.nextDouble() - 0.5) * 2 * spread, at.y, at.z + (r.nextDouble() - 0.5) * 2 * spread, 0,
                    0, -0.05, 0, 1.0);
        }
    }
}
