package com.groid.papon.client;

import com.groid.papon.boss.Coliseum;
import com.groid.papon.boss.ColiseumCrowdEntity;
import com.groid.papon.boss.Song;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fireworks in the colours of the flag, on the music: whenever he gets the stands going (his entrance, the band
 * hitting it, his OLÉ, the big moments of the fight) the boss asks the crowd for a salvo (ColiseumCrowdEntity.salvo:
 * N beats), and here every one of the song's next N beats gets a volley off the top of the walls: three rockets side
 * by side, green, white, red, launched so they burst exactly on the beat (each climbs for {@link #RISE} ticks); the
 * last of a salvo is a big gold star over the middle. Volleys walk round the bowl, opposite you first.
 * Client-only (no entities): rising sparks, then vanilla's firework bursts and their bangs.
 */
public final class FlagFireworks {
    private static final int RISE = 16;
    private static final int GREEN = 0x1E9A48, WHITE = 0xF6F6F0, RED = 0xD42A2E, GOLD = 0xF2C14E;
    private static final double RANGE = 110;

    private record Rocket(Vec3 from, Vec3 to, long launch, int colour, int fade, int type, boolean big) {}

    private static final List<Rocket> rockets = new ArrayList<>();
    private static int lastSalvo = -1;
    private static int lastCrowd = -1;

    private FlagFireworks() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            rockets.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ColiseumCrowdEntity crowd = mc.level.getEntitiesOfClass(ColiseumCrowdEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (crowd != null) {
            int s = crowd.salvoData();
            if (crowd.getId() != lastCrowd) {
                lastCrowd = crowd.getId();
                lastSalvo = s;
            }
            if (s != lastSalvo) {
                lastSalvo = s;
                if (s >= 0) schedule(mc, crowd, s & 0xFF, now);
            }
        }
        for (var it = rockets.iterator(); it.hasNext(); ) {
            Rocket r = it.next();
            long age = now - r.launch;
            if (age < 0) continue;
            if (age == 0) {
                mc.level.playLocalSound(r.from.x, r.from.y, r.from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT,
                        3.0F, 0.9F + mc.level.random.nextFloat() * 0.2F, false);
            }
            if (age < RISE) {
                // the climb: a fizzing trail, slowing toward the top
                double k = age / (double) RISE, k2 = (age + 1) / (double) RISE;
                Vec3 a = path(r, k), b = path(r, k2);
                for (int i = 0; i < 3; i++) {
                    Vec3 p = a.lerp(b, i / 3.0);
                    mc.level.addParticle(ParticleTypes.FIREWORK, p.x, p.y, p.z,
                            mc.level.random.nextGaussian() * 0.02, -0.05, mc.level.random.nextGaussian() * 0.02);
                }
                continue;
            }
            mc.level.createFireworks(r.to.x, r.to.y, r.to.z, 0, 0, 0, explosion(r));
            it.remove();
        }
    }

    /** Up from the wall, easing out (a rocket slowing at the top of its climb), with a little drift. */
    private static Vec3 path(Rocket r, double k) {
        double e = 1 - (1 - k) * (1 - k);
        return r.from.lerp(r.to, e);
    }

    private static CompoundTag explosion(Rocket r) {
        CompoundTag fw = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag e = new CompoundTag();
        e.putByte("Type", (byte) r.type);
        e.putIntArray("Colors", new int[]{r.colour});
        e.putIntArray("FadeColors", new int[]{r.fade});
        e.putBoolean("Trail", true);
        e.putBoolean("Flicker", r.big || r.colour == WHITE);
        list.add(e);
        fw.put("Explosions", list);
        return fw;
    }

    /** A volley on each of the next {@code beats} beats of the song playing (or every half second with no song). */
    private static void schedule(Minecraft mc, ColiseumCrowdEntity crowd, int beats, long now) {
        if (beats <= 0) return;
        List<Double> at = new ArrayList<>();             // ticks from now to each burst
        Song song = SongPlayer.song();
        double t = SongPlayer.songTime(0);
        if (song != null && !Double.isNaN(t) && song.beats.length > 1) {
            double b = song.beatAt(t + RISE / 20.0 + 0.05);
            int k = (int) Math.floor(b) + 1;
            for (int i = 0; i < beats && k + i < song.beats.length; i++) at.add((song.beats[k + i] - t) * 20.0);
        }
        while (at.size() < beats) at.add(RISE + 2 + at.size() * 9.4);
        Vec3 c = crowd.position();
        Vec3 me = mc.player.position();
        double away = Math.atan2(c.z - me.z, c.x - me.x);          // the far side of the bowl from you
        double rim = Coliseum.HEIGHT + 1;
        for (int i = 0; i < at.size(); i++) {
            long burst = now + Math.round(at.get(i));
            long launch = burst - RISE;
            if (launch < now) continue;
            boolean last = i == at.size() - 1 && beats >= 3;
            if (last) {
                // the big one: a gold star high over the middle of the sand, from the top of the far wall
                Vec3 from = rimPoint(c, away, rim);
                rockets.add(new Rocket(from, c.add(0, rim + 22, 0), launch, GOLD, WHITE, 2, true));
                continue;
            }
            // round the bowl, opposite you first, then either side of that, further each time
            double a = away + (i == 0 ? 0 : ((i + 1) / 2) * 0.55 * (i % 2 == 0 ? 1 : -1));
            int[] col = {GREEN, WHITE, RED};
            for (int j = 0; j < 3; j++) {
                double aj = a + (j - 1) * 0.11;
                Vec3 from = rimPoint(c, aj, rim);
                Vec3 in = new Vec3(c.x - from.x, 0, c.z - from.z).normalize();
                Vec3 to = from.add(in.scale(6 + mc.level.random.nextDouble() * 3)).add(0, 14 + (j == 1 ? 3 : 0) + mc.level.random.nextDouble() * 2, 0);
                rockets.add(new Rocket(from, to, launch + j, col[j], j == 1 ? GOLD : WHITE, j == 1 ? 1 : 0, false));
            }
        }
    }

    /** A point on the top of the outer wall at angle {@code a} round the middle. */
    private static Vec3 rimPoint(Vec3 c, double a, double rim) {
        return c.add(Math.cos(a) * (Coliseum.A + 20), rim, Math.sin(a) * (Coliseum.B + 20));
    }
}
