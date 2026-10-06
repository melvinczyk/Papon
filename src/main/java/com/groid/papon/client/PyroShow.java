package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.Coliseum;
import com.groid.papon.boss.ColiseumCrowdEntity;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.Song;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The arena's stage pyrotechnics (2026-10-06, the user: "there aren't enough clearly visible pyrotechnics... the viewer needs to
 * SEE the escalation... choreographed, not random explosions"). Three kinds of effect, all drawn as additive light so they read
 * from across the bowl in any light, none of them on the sand (they stand on the podium wall round it and along the front of
 * the band's stand, out of the way of him, you and his attacks):
 * <ul>
 *   <li>flame columns: a flame projector on the podium firing a column of fire ten-odd blocks up, a white-hot root, orange
 *   body, a red top that lifts off and burns out as the valve shuts; a warm pool of light round its foot</li>
 *   <li>gerbs: fountains of white-gold sparks off the front of the stand, on the guitar</li>
 *   <li>comets: fireballs in the flag's colours climbing out of the podium and bursting high over the sand</li>
 * </ul>
 * Every one is placed on the music: the server asks for a show (ColiseumCrowdEntity.pyro: the first sung word of phase two, N
 * beats) and this conducts it from the song's own data (Song: the beats, the guitar's strums, where the trumpets come in, the
 * singer's phrases): everything at once on the first word, then pairs of columns walking round the bowl on each bar's first and
 * third beats, gerbs on the hardest strums, comets on the trumpets, columns either side of the stand as the singer starts a
 * line, a column chasing all the way round the ring through the last bar and everything again on its last beat. After the
 * show, the music keeps it going, sparingly: a chase round the ring where the song changes section, a pair of columns at the
 * stand where the trumpets come in. The boss's big arena moves get a cue of their own (an accent: the columns on his side).
 * <p>
 * The colours follow his form: fire for phase two, crimson and gold for the potato god, the dead's violet and jade spirit-fire
 * with marigold sparks for the Final Guardian.
 */
public final class PyroShow {
    private static final double RANGE = 120;
    /** Columns round the podium (and the gaps the band's stand leaves). */
    private static final int RING = 16;
    public static final int SHOW = 0, ACCENT = 1, CHASE = 2;

    private record Column(Vec3 at, double start, int len, float height, float width, int palette) {}

    private record Gerb(Vec3 at, double start, int len, float height, int palette, int seed) {}

    private record Comet(Vec3 from, Vec3 to, double start, int rise, int colour) {}

    private static final List<Column> columns = new ArrayList<>();
    private static final List<Gerb> gerbs = new ArrayList<>();
    private static final List<Comet> comets = new ArrayList<>();
    private record Bang(double at, Vec3 where, boolean big, float pitch) {}
    private static final List<Bang> bangs = new ArrayList<>();
    private static int lastPyro = Integer.MIN_VALUE, lastCrowd = -1;
    /** The show running: until this game tick, the beats it covers, and what it has scheduled up to. */
    private static double showUntil = -1;
    private static int lastSegment = -1;
    private static double lastHorn = -1e9, lastStrum = -1e9, lastPhrase = -1e9;
    private static float hornWas, voiceWas;
    private static double lastSongT = Double.NaN;

    private static final int GREEN = 0x1E9A48, WHITE = 0xF6F6F0, RED = 0xD42A2E, GOLD = 0xF2C14E, VIOLET = 0xA05CFF, JADE = 0x3CF0B4,
            MARIGOLD = 0xFF9A1E, CRIMSON = 0xE0203A;
    /** Palettes: fire, the god's crimson and gold, the dead's spirit fire. */
    private static final int FIRE = 0, DIVINE = 1, SPIRIT = 2;

    private PyroShow() {}

    private static SoundEvent ev(String name) { return SoundEvent.createVariableRangeEvent(PaponMod.id(name)); }

    // ------------------------------------------------------------------ the conductor

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            columns.clear();
            gerbs.clear();
            comets.clear();
            bangs.clear();
            return;
        }
        double now = mc.level.getGameTime();
        ColiseumCrowdEntity crowd = mc.level.getEntitiesOfClass(ColiseumCrowdEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        PaponBossEntity boss = crowd == null ? null : mc.level.getEntitiesOfClass(PaponBossEntity.class, crowd.getBoundingBox().inflate(90), e -> true)
                .stream().findFirst().orElse(null);
        int palette = boss == null ? FIRE : boss.isMuertos() || boss.isPhaseFour() ? SPIRIT : boss.isAscended() ? DIVINE : FIRE;
        if (crowd != null) {
            int p = crowd.pyroData();
            if (crowd.getId() != lastCrowd) {
                lastCrowd = crowd.getId();
                lastPyro = p;
            }
            if (p != lastPyro) {
                lastPyro = p;
                if (p >= 0) {
                    int kind = (p >> 8) & 3, n = p & 0xFF;
                    switch (kind) {
                        case ACCENT -> accent(mc, crowd, boss, palette, now);
                        case CHASE -> chase(crowd, now, 0, 9, palette, true);
                        default -> show(mc, crowd, n, palette, now);
                    }
                }
            }
            follow(mc, crowd, boss, palette, now);
        }
        playBangs(mc, now);
        // embers off the columns, a puff of smoke as each burns out, the comets' bursts
        var r = mc.level.random;
        columns.removeIf(c -> now - c.start > c.len + 2);
        for (Column c : columns) {
            double age = now - c.start;
            if (age < 0 || age > c.len) continue;
            if (r.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.LAVA, true, c.at.x, c.at.y + 0.6, c.at.z, 0, 0, 0);
            if ((int) age == c.len - 3) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, true, c.at.x, c.at.y + c.height * 0.9, c.at.z, 0, 0.12, 0);
        }
        gerbs.removeIf(g -> now - g.start > g.len + 16);
        for (var it = comets.iterator(); it.hasNext(); ) {
            Comet c = it.next();
            if (now - c.start < c.rise) continue;
            mc.level.createFireworks(c.to.x, c.to.y, c.to.z, 0, 0, 0, burst(c.colour, palette));
            it.remove();
        }
    }

    /**
     * The show: everything at once now (the first sung word), then {@code beats} beats of it on the song's beats (or every
     * half second without one).
     */
    private static void show(Minecraft mc, ColiseumCrowdEntity crowd, int beats, int palette, double now) {
        List<Double> at = beatsAhead(beats);
        Vec3 c = crowd.position();
        // the first word: every column, the gerbs along the stand, six comets
        for (int j = 0; j < RING; j++) columns.add(new Column(ring(crowd, j), now, 24, 13.5F, 1.35F, palette));
        stageGerbs(mc, crowd, now, 4, 40, palette);
        cometsUp(crowd, now, 6, palette, 0);
        bang(now, c, true, 0.9F);
        showUntil = now + (at.isEmpty() ? 0 : at.get(at.size() - 1)) + 4;
        int bars = Math.max(1, at.size() / 4);
        for (int i = 0; i < at.size(); i++) {
            double t = now + at.get(i);
            int q = i % 4, bar = i / 4;
            boolean last = bar == bars - 1;
            if (last) {
                // the last bar: a column racing round the ring (a quarter of it each beat), and on its last beat all of it
                if (q < 3) chase(crowd, t, q * RING / 4, at.size() > i + 1 ? (int) Math.round(at.get(i + 1) - at.get(i)) : 9, palette, false);
                else {
                    for (int j = 0; j < RING; j++) columns.add(new Column(ring(crowd, j), t, 20, 12.5F, 1.3F, palette));
                    cometsUp(crowd, t, 8, palette, 1);
                    stageGerbs(mc, crowd, t, 4, 30, palette);
                    bang(t, c, true, 1.05F);
                }
                continue;
            }
            if (q == 0 || q == 2) {
                // a pair across the bowl from each other, walking round a step each time
                int k = (bar * 2 + q / 2) * 3 % (RING / 2);
                for (int s = 0; s < 2; s++) {
                    int j = k + s * RING / 2;
                    columns.add(new Column(ring(crowd, j), t, q == 0 ? 14 : 10, q == 0 ? 11 : 9, 1.15F, palette));
                }
                bang(t, ring(crowd, k), false, q == 0 ? 0.85F : 1.0F);
            }
        }
    }

    /** A column racing round the ring from column {@code from}, a quarter of it over {@code ticks}; {@code whole}: all of it. */
    private static void chase(ColiseumCrowdEntity crowd, double t, int from, int ticks, int palette, boolean whole) {
        int n = whole ? RING : RING / 4;
        double step = Math.max(1, ticks) / (double) n;
        for (int j = 0; j < n; j++) columns.add(new Column(ring(crowd, from + j), t + j * step, 10, 9.5F, 1.05F, palette));
        if (whole) bang(t, crowd.position(), false, 0.8F);
    }

    /** His big move: the columns on his side of the bowl (four), a comet over him. */
    private static void accent(Minecraft mc, ColiseumCrowdEntity crowd, PaponBossEntity boss, int palette, double now) {
        Vec3 c = crowd.position();
        Vec3 at = boss != null ? boss.position() : mc.player.position();
        double ang = Math.atan2((at.z - c.z) / crowd.sizeB(), (at.x - c.x) / crowd.sizeA());
        int k = Math.floorMod((int) Math.round(ang / (Math.PI * 2) * RING), RING);
        for (int s = -2; s <= 1; s++) columns.add(new Column(ring(crowd, k + s), now + Math.abs(s + 0.5) * 1.5, 14, 11.5F, 1.2F, palette));
        bang(now, ring(crowd, k), false, 0.75F);
    }

    /**
     * Between shows the song keeps it going, sparingly: a chase round the ring where it changes section; a pair of columns at
     * the band's stand where the trumpets come in (not more than once in eight seconds); a gerb on the hardest strums and
     * columns either side of the stand as the singer starts a line while a show is on.
     */
    private static void follow(Minecraft mc, ColiseumCrowdEntity crowd, PaponBossEntity boss, int palette, double now) {
        Song song = SongPlayer.song();
        double t = SongPlayer.songTime(0);
        if (song == null || Double.isNaN(t) || boss == null || !boss.inColiseum() || boss.getBossState().cutscene()) {
            lastSongT = Double.NaN;
            return;
        }
        boolean showing = now < showUntil;
        boolean jumped = Double.isNaN(lastSongT) || Math.abs(t - lastSongT) > 0.5;
        lastSongT = t;
        int seg = -1;
        for (Song.Segment sg : song.segments) { if (t >= sg.start() && t < sg.end()) seg = sg.index(); }
        if (seg != lastSegment) {
            if (!jumped && lastSegment >= 0 && !showing) chase(crowd, now, (int) (now % RING), 12, palette, true);
            lastSegment = seg;
        }
        float horn = song.hornAt(t), voice = song.voiceAt(t), strum = song.strumAt(t);
        if (horn > 0.5F && hornWas <= 0.5F && now - lastHorn > (showing ? 30 : 160)) {
            lastHorn = now;
            for (int s : new int[]{-1, 1}) columns.add(new Column(stageFlank(crowd, s), now, 12, 10, 1.1F, palette));
            if (showing) cometsUp(crowd, now, 3, palette, 2);
            bang(now, stageFlank(crowd, 1), false, 0.95F);
        }
        hornWas = horn;
        if (showing) {
            if (strum > 0.7F && now - lastStrum > 8) {
                lastStrum = now;
                stageGerbs(mc, crowd, now, 2, 14, palette);
            }
            if (voice > 0.2F && voiceWas <= 0.05F && now - lastPhrase > 40) {
                lastPhrase = now;
                for (int s : new int[]{-1, 1}) columns.add(new Column(stageFlank(crowd, s), now, 10, 8, 1.0F, palette));
            }
        }
        voiceWas = voice;
    }

    /** The beats to come, in ticks from now (the song's, or every half second with no song). */
    private static List<Double> beatsAhead(int beats) {
        List<Double> at = new ArrayList<>();
        Song song = SongPlayer.song();
        double t = SongPlayer.songTime(0);
        if (song != null && !Double.isNaN(t) && song.beats.length > 1) {
            double b = song.beatAt(t + 0.1);
            int k = (int) Math.floor(b) + 1;
            for (int i = 0; i < beats && k + i < song.beats.length; i++) at.add((song.beats[k + i] - t) * 20.0);
        }
        while (at.size() < beats) at.add(10.0 + at.size() * 9.4);
        return at;
    }

    /** Column {@code j} of the ring: on the podium wall round the sand. */
    private static Vec3 ring(ColiseumCrowdEntity crowd, int j) {
        double ang = Math.floorMod(j, RING) * Math.PI * 2 / RING + Math.PI / RING;
        return crowd.position().add(Math.cos(ang) * (crowd.sizeA() + 1.2), Coliseum.PODIUM + 0.15, Math.sin(ang) * (crowd.sizeB() + 1.2));
    }

    /** Either side of the band's stand (north, -Z), on its front. */
    private static Vec3 stageFlank(ColiseumCrowdEntity crowd, int side) {
        return crowd.position().add(side * 7.5, Coliseum.PODIUM + 0.15, -crowd.sizeB() - 1.2);
    }

    private static void stageGerbs(Minecraft mc, ColiseumCrowdEntity crowd, double t, int n, int len, int palette) {
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? 0 : -6 + 12.0 * i / (n - 1);
            Vec3 at = crowd.position().add(x, Coliseum.PODIUM + 0.15, -crowd.sizeB() - 1.0);
            gerbs.add(new Gerb(at, t + i, len, 5.5F + (i % 2), palette, mc.level.random.nextInt()));
        }
        bangs.add(new Bang(t, crowd.position().add(0, Coliseum.PODIUM, -crowd.sizeB()), false, -1));
    }

    private static void cometsUp(ColiseumCrowdEntity crowd, double t, int n, int palette, int salt) {
        int[] cols = palette == SPIRIT ? new int[]{VIOLET, JADE, MARIGOLD} : palette == DIVINE ? new int[]{CRIMSON, GOLD, WHITE}
                : new int[]{GREEN, WHITE, RED};
        for (int i = 0; i < n; i++) {
            double ang = (i + 0.5) * Math.PI * 2 / n + salt * 0.4;
            Vec3 from = crowd.position().add(Math.cos(ang) * (crowd.sizeA() + 1.2), Coliseum.PODIUM + 0.5, Math.sin(ang) * (crowd.sizeB() + 1.2));
            Vec3 to = crowd.position().add(Math.cos(ang) * crowd.sizeA() * 0.4, 20 + (i % 3) * 3, Math.sin(ang) * crowd.sizeB() * 0.4);
            comets.add(new Comet(from, to, t + (i % 3), 18, cols[i % 3]));
        }
    }

    private static void bang(double at, Vec3 where, boolean big, float pitch) { bangs.add(new Bang(at, where, big, pitch)); }

    /** The sounds, as each cue comes up (pitch -1: a gerb's hiss). Not more than three in a tick. */
    private static void playBangs(Minecraft mc, double now) {
        int played = 0;
        for (var it = bangs.iterator(); it.hasNext(); ) {
            Bang b = it.next();
            if (b.at > now) continue;
            it.remove();
            if (now - b.at > 3 || played >= 3) continue;
            played++;
            String name = b.pitch < 0 ? "pyro.gerb" : b.big ? "pyro.burst" : "pyro.whoosh";
            float pitch = b.pitch < 0 ? 0.95F + mc.level.random.nextFloat() * 0.1F : b.pitch + mc.level.random.nextFloat() * 0.06F;
            // under his voice like the rest of the arena, but loud: the whole bowl hears a flame projector
            float vol = (b.big ? 2.2F : 1.5F) * VoiceDuck.get();
            mc.getSoundManager().play(new SimpleSoundInstance(ev(name).getLocation(), SoundSource.AMBIENT, vol, pitch,
                    SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, b.where.x, b.where.y, b.where.z, false));
        }
    }

    private static CompoundTag burst(int colour, int palette) {
        CompoundTag fw = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag e = new CompoundTag();
        e.putByte("Type", (byte) 0);
        e.putIntArray("Colors", new int[]{colour});
        e.putIntArray("FadeColors", new int[]{palette == SPIRIT ? MARIGOLD : GOLD});
        e.putBoolean("Trail", true);
        e.putBoolean("Flicker", true);
        list.add(e);
        fw.put("Explosions", list);
        return fw;
    }

    // ------------------------------------------------------------------ drawing

    /** Drawn after the world (ClientEvents, AFTER_PARTICLES): additive, over the finished stands. */
    public static void render(PoseStack pose, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || columns.isEmpty() && gerbs.isEmpty() && comets.isEmpty()) return;
        double now = mc.level.getGameTime() + partial;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        // positions relative to the camera (floats far from the origin would shake)
        Matrix4f m = pose.last().pose();
        for (Column c : columns) {
            double age = now - c.start;
            if (age >= 0 && age <= c.len) column(vc, m, cam, c, (float) age);
        }
        for (Gerb g : gerbs) gerb(vc, m, cam, g, now);
        for (Comet c : comets) comet(vc, m, cam, c, now);
        buffers.endBatch(PaponRenderTypes.glow());
    }

    /** Colour along a flame (0 at the nozzle .. 1 at the top), by palette. */
    private static int[] flame(int palette, float s) {
        int[][] stops = switch (palette) {
            case DIVINE -> new int[][]{{255, 250, 225}, {255, 196, 70}, {240, 40, 52}, {150, 12, 40}};
            case SPIRIT -> new int[][]{{235, 255, 245}, {90, 255, 190}, {170, 90, 255}, {80, 20, 160}};
            default -> new int[][]{{255, 248, 214}, {255, 186, 60}, {255, 96, 22}, {170, 30, 10}};
        };
        float x = Mth.clamp(s, 0, 1) * 3;
        int i = Math.min(2, (int) x);
        float k = x - i;
        return new int[]{(int) Mth.lerp(k, stops[i][0], stops[i + 1][0]), (int) Mth.lerp(k, stops[i][1], stops[i + 1][1]),
                (int) Mth.lerp(k, stops[i][2], stops[i + 1][2])};
    }

    /**
     * A column of fire: a ribbon turned to face you about its own axis, its width swelling from the nozzle to a rounded top,
     * licking side to side; it shoots up in three ticks, and when the valve shuts its foot lifts off and the rest burns out
     * going up. A soft outer flame round a hot core, and a pool of its light on the stone round its foot.
     */
    private static void column(VertexConsumer vc, Matrix4f m, Vec3 cam, Column c, float age) {
        float up = Mth.clamp(age / 3F, 0, 1);
        up = 1 - (1 - up) * (1 - up);
        float off = Math.max(0, age - (c.len - 6)) / 6F;              // the valve shut: the foot lifting off
        float top = c.height * up + off * 3F;
        float foot = off * off * c.height * 0.85F;
        float fade = 1 - off;
        if (top - foot < 0.2F) return;
        Vec3 at = c.at.subtract(cam);
        Vec3 to = at.reverse();
        double h = Math.hypot(to.x, to.z);
        float rx = h < 1e-3 ? 1 : (float) (to.z / h), rz = h < 1e-3 ? 0 : (float) (-to.x / h);
        int seg = 12;
        float seed = (float) (c.at.x * 1.7 + c.at.z * 0.9);
        for (int layer = 0; layer < 2; layer++) {
            float wk = layer == 0 ? 1F : 0.45F;
            float ak = layer == 0 ? 0.55F : 1F;
            for (int i = 0; i < seg; i++) {
                float s0 = i / (float) seg, s1 = (i + 1) / (float) seg;
                float y0 = Mth.lerp(s0, foot, top), y1 = Mth.lerp(s1, foot, top);
                float w0 = width(s0, c.width * wk), w1 = width(s1, c.width * wk);
                float l0 = lick(seed, s0, age), l1 = lick(seed, s1, age);
                int[] c0 = flame(c.palette, layer == 1 ? s0 * 0.6F : s0), c1 = flame(c.palette, layer == 1 ? s1 * 0.6F : s1);
                int a0 = (int) (255 * ak * fade * alpha(s0)), a1 = (int) (255 * ak * fade * alpha(s1));
                float x0 = (float) at.x + l0 * rx, z0 = (float) at.z + l0 * rz, x1 = (float) at.x + l1 * rx, z1 = (float) at.z + l1 * rz;
                float by0 = (float) at.y + y0, by1 = (float) at.y + y1;
                vc.vertex(m, x0 - rx * w0, by0, z0 - rz * w0).color(c0[0], c0[1], c0[2], a0).endVertex();
                vc.vertex(m, x0 + rx * w0, by0, z0 + rz * w0).color(c0[0], c0[1], c0[2], a0).endVertex();
                vc.vertex(m, x1 + rx * w1, by1, z1 + rz * w1).color(c1[0], c1[1], c1[2], a1).endVertex();
                vc.vertex(m, x1 - rx * w1, by1, z1 - rz * w1).color(c1[0], c1[1], c1[2], a1).endVertex();
            }
        }
        // the light pool on the stone round its foot (and a little up the wall behind)
        int[] warm = flame(c.palette, 0.35F);
        float pr = 2.6F * c.width;
        int pa = (int) (110 * fade * up);
        float y = (float) at.y + 0.04F;
        int n = 14;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (Math.PI * 2 * i / n), a1 = (float) (Math.PI * 2 * (i + 1) / n);
            vc.vertex(m, (float) at.x, y, (float) at.z).color(warm[0], warm[1], warm[2], pa).endVertex();
            vc.vertex(m, (float) at.x + Mth.cos(a1) * pr, y, (float) at.z + Mth.sin(a1) * pr).color(warm[0], warm[1], warm[2], 0).endVertex();
            vc.vertex(m, (float) at.x + Mth.cos(a0) * pr, y, (float) at.z + Mth.sin(a0) * pr).color(warm[0], warm[1], warm[2], 0).endVertex();
            vc.vertex(m, (float) at.x, y, (float) at.z).color(warm[0], warm[1], warm[2], pa).endVertex();
        }
    }

    /** A flame's half-width at {@code s} along it: narrow at the nozzle, fullest two-thirds up, a rounded top. */
    private static float width(float s, float w) {
        return w * (0.22F + 0.78F * (float) Math.pow(Math.sin(Math.PI * Math.min(1, s * 0.82F + 0.06F)), 0.7));
    }

    private static float alpha(float s) {
        return s < 0.75F ? 1F : 1F - (s - 0.75F) / 0.25F;
    }

    /** How far the flame licks sideways at {@code s} (more toward the top). */
    private static float lick(float seed, float s, float age) {
        return s * s * (0.45F * Mth.sin(age * 0.9F + s * 5.1F + seed) + 0.25F * Mth.sin(age * 2.3F - s * 7.7F + seed * 1.3F));
    }

    /**
     * A gerb: a fountain of sparks thrown up and falling back, each a short bright streak along its own path, a fresh one every
     * quarter tick while it burns, each living fourteen ticks. Its sparks are the same every frame (seeded per spark).
     */
    private static void gerb(VertexConsumer vc, Matrix4f m, Vec3 cam, Gerb g, double now) {
        int perTick = 4, life = 14;
        double from = Math.max(g.start, now - life), to = Math.min(g.start + g.len, now);
        if (to <= from) return;
        int i0 = (int) Math.floor((from - g.start) * perTick), i1 = (int) Math.ceil((to - g.start) * perTick);
        float g2 = 0.045F;
        float vUp = (float) Math.sqrt(2 * g2 * g.height);
        for (int i = i0; i < i1; i++) {
            double born = g.start + i / (double) perTick;
            float a = (float) (now - born);
            if (a < 0 || a > life) continue;
            long h = (long) i * 0x9E3779B97F4A7C15L + g.seed;
            float rx = ((h >>> 11) & 1023) / 1023F - 0.5F, rz = ((h >>> 21) & 1023) / 1023F - 0.5F, rv = ((h >>> 31) & 1023) / 1023F;
            float vy = vUp * (0.75F + 0.3F * rv), vx = rx * 0.22F, vz = rz * 0.22F;
            float t0 = Math.max(0, a - 1.6F);
            Vec3 p1 = g.at.add(vx * a, vy * a - 0.5 * g2 * a * a, vz * a), p0 = g.at.add(vx * t0, vy * t0 - 0.5 * g2 * t0 * t0, vz * t0);
            float k = 1 - a / life;
            int[] col = g.palette == SPIRIT ? new int[]{255, 196, 90} : g.palette == DIVINE ? new int[]{255, 226, 150} : new int[]{255, 236, 170};
            streak(vc, m, p0.subtract(cam), p1.subtract(cam), 0.06F, col, (int) (255 * k));
        }
    }

    /** A comet: a fireball climbing to its burst, easing out at the top, a trail of fading balls behind. */
    private static void comet(VertexConsumer vc, Matrix4f m, Vec3 cam, Comet c, double now) {
        double age = now - c.start;
        if (age < 0 || age > c.rise) return;
        int r = (c.colour >> 16) & 255, gg = (c.colour >> 8) & 255, b = c.colour & 255;
        for (int i = 0; i < 9; i++) {
            double k = Math.max(0, (age - i * 0.7) / c.rise);
            double e = 1 - (1 - k) * (1 - k);
            Vec3 p = c.from.lerp(c.to, e);
            float s = (i == 0 ? 0.55F : 0.45F) * (1 - i / 10F);
            int a = (int) (255 * (1 - i / 9F));
            Vec3 q = p.subtract(cam);
            ball(vc, m, q, s, i == 0 ? new int[]{255, 250, 230} : new int[]{r, gg, b}, a);
            if (i == 0) ball(vc, m, q, s * 2.2F, new int[]{r, gg, b}, 120);
        }
    }

    /** A soft ball of light facing the camera ({@code p} relative to it). */
    private static void ball(VertexConsumer vc, Matrix4f m, Vec3 p, float s, int[] col, int a) {
        Vec3 to = p.reverse().normalize();
        Vec3 right = to.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(to).normalize();
        Vec3[] q = {p.add(right.scale(-s)), p.add(up.scale(s)), p.add(right.scale(s)), p.add(up.scale(-s))};
        vc.vertex(m, (float) q[0].x, (float) q[0].y, (float) q[0].z).color(col[0], col[1], col[2], a / 3).endVertex();
        vc.vertex(m, (float) q[1].x, (float) q[1].y, (float) q[1].z).color(col[0], col[1], col[2], a).endVertex();
        vc.vertex(m, (float) q[2].x, (float) q[2].y, (float) q[2].z).color(col[0], col[1], col[2], a / 3).endVertex();
        vc.vertex(m, (float) q[3].x, (float) q[3].y, (float) q[3].z).color(col[0], col[1], col[2], a).endVertex();
    }

    /** A streak from p0 to p1 (relative to the camera), {@code w} wide, turned to face it. */
    private static void streak(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, float w, int[] col, int a) {
        Vec3 d = p1.subtract(p0);
        Vec3 side = d.cross(p1.reverse());
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(w);
        vc.vertex(m, (float) (p0.x - side.x), (float) (p0.y - side.y), (float) (p0.z - side.z)).color(col[0], col[1], col[2], 0).endVertex();
        vc.vertex(m, (float) (p0.x + side.x), (float) (p0.y + side.y), (float) (p0.z + side.z)).color(col[0], col[1], col[2], 0).endVertex();
        vc.vertex(m, (float) (p1.x + side.x), (float) (p1.y + side.y), (float) (p1.z + side.z)).color(col[0], col[1], col[2], a).endVertex();
        vc.vertex(m, (float) (p1.x - side.x), (float) (p1.y - side.y), (float) (p1.z - side.z)).color(col[0], col[1], col[2], a).endVertex();
    }
}
