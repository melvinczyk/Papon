package com.groid.papon.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.groid.papon.client.mesh.SkinnedMesh;
import com.groid.papon.entity.MakimaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makima's face (2026-10-07, the user: "different facial expressions ... eyes opening and closing, her well known look ...
 * the way she tilts her head ... like she's really in the game"). tools/makima_face.py builds the rig into her mesh: lids
 * that slide over each eye on tracks laid on the eye, the lashes riding their edges, morphs for the smile, the open mouth
 * and the brows, the irises on bones of their own, a blush. Played here:
 *
 *   moods      what she is doing picks the targets ({@link Mood}): calm on her own; when you come near, a small smile and her
 *              eyes on you; when you stare at her, her look: heavy lids, the smile, the head tilted and the chin down, her
 *              eyes up at you, and she stops blinking; pleased when you get on; with you, heavy-lidded, brows up, the mouth
 *              open on every stroke, blushing as it builds; at the end eyes shut tight, then dazed; after, satisfied
 *   blinks     every few seconds (a double now and then, after a big glance, slow when her lids are heavy, rarely when
 *              she is staring you down); she opens her eyes when she first appears
 *   eyes       on you (the camera, so she meets your eyes in any view), darting and never quite still; wandering when
 *              nobody is there; rolled up in the climax
 *   head       her tilt eases in with a little overshoot and swaps sides now and then
 *
 * Every channel eases toward its target at its own pace (lids fast, the smile slower, the blush slowest). tools/makima_view.py
 * previews the same moods (FACES) with the same rig.
 */
public final class MakimaFace {
    private MakimaFace() {}

    public enum Mood { CALM, NEAR, LOOK, PLEASED, PLEASURE, CLIMAX, DAZED, AFTERGLOW, WAKING }

    // ---------------------------------------------------------------- the rig (meshes/makima.json "face" and "hands")

    /** Read once per mesh: everything in the mesh's own space (blocks, x mirrored as SkinnedMesh loads positions). */
    static final class Rig {
        final String[] morphs = {"smile", "open", "brow_worry", "brow_down", "brow_raise"};
        final int[][] morphV = new int[morphs.length][];
        final float[][] morphD = new float[morphs.length][];
        final int cols, rows, samples;
        final Lid[] lids;
        final Follower[] followers;
        final float[] eyeL, eyeR, ex, ey, fwd;
        final float gazeX, gazeY;
        /** Per finger bone: animation degrees per degree of curl (MakimaRenderer adds a clench this way). */
        final Map<String, float[]> curl = new HashMap<>();

        Rig(JsonObject face, @Nullable JsonObject hands) {
            JsonObject m = face.getAsJsonObject("morphs");
            for (int i = 0; i < morphs.length; i++) {
                JsonObject o = m.getAsJsonObject(morphs[i]);
                morphV[i] = o == null ? new int[0] : ints(o.getAsJsonArray("v"));
                morphD[i] = o == null ? new float[0] : points(o.getAsJsonArray("d"));
            }
            cols = face.get("cols").getAsInt();
            rows = face.get("rows").getAsInt();
            samples = face.get("samples").getAsInt();
            JsonArray ls = face.getAsJsonArray("lids");
            lids = new Lid[ls.size()];
            for (int i = 0; i < lids.length; i++) {
                JsonObject o = ls.get(i).getAsJsonObject();
                String name = o.get("name").getAsString();
                lids[i] = new Lid(name.startsWith("up"), name.endsWith("_l") ? 0 : 1, o.get("base").getAsInt(),
                        points(o.getAsJsonArray("track")), points(o.getAsJsonArray("cap")), (cols + 1) * 3);
            }
            JsonArray fs = face.getAsJsonArray("followers");
            followers = new Follower[fs.size()];
            for (int i = 0; i < followers.length; i++) {
                JsonObject o = fs.get(i).getAsJsonObject();
                followers[i] = new Follower(o.get("lid").getAsInt(), ints(o.getAsJsonArray("v")), floats(o.getAsJsonArray("col")),
                        o.get("w").getAsFloat());
            }
            JsonObject eyes = face.getAsJsonObject("eyes");
            eyeL = points(eyes.getAsJsonArray("l"));
            eyeR = points(eyes.getAsJsonArray("r"));
            ex = dir(face.getAsJsonArray("ex"));
            ey = dir(face.getAsJsonArray("ey"));
            fwd = dir(face.getAsJsonArray("fwd"));
            JsonArray g = face.getAsJsonArray("gaze");
            gazeX = g.get(0).getAsFloat() / 16F;
            gazeY = g.get(1).getAsFloat() / 16F;
            if (hands != null) {
                for (var side : hands.entrySet()) {
                    for (var finger : side.getValue().getAsJsonObject().entrySet()) {
                        float[] a = floats(finger.getValue().getAsJsonObject().getAsJsonArray("curl"));
                        for (int k = 1; k <= 3; k++) curl.put("f_" + finger.getKey() + k + "_" + side.getKey(), new float[]{-a[0], a[1], -a[2]});
                    }
                }
            }
        }

        private static int[] ints(JsonArray a) {
            int[] out = new int[a.size()];
            for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
            return out;
        }

        private static float[] floats(JsonArray a) {
            float[] out = new float[a.size()];
            for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
            return out;
        }

        /** Rig pixels to the mesh's space: blocks, x mirrored (positions; offsets the same). */
        private static float[] points(JsonArray a) {
            float[] out = floats(a);
            for (int i = 0; i < out.length; i += 3) {
                out[i] = -out[i] / 16F;
                out[i + 1] /= 16F;
                out[i + 2] /= 16F;
            }
            return out;
        }

        private static float[] dir(JsonArray a) {
            float[] v = floats(a);
            v[0] = -v[0];
            return v;
        }
    }

    record Lid(boolean upper, int side, int base, float[] track, float[] cap, float[] edge) {
        Lid(boolean upper, int side, int base, float[] track, float[] cap, int n) { this(upper, side, base, track, cap, new float[n]); }
    }

    record Follower(int lid, int[] v, float[] col, float w) {}

    private static final Map<SkinnedMesh, Rig> RIGS = new WeakHashMap<>();

    @Nullable
    static Rig rig(SkinnedMesh mesh) {
        JsonObject face = mesh.extras.get("face");
        if (face == null) return null;
        return RIGS.computeIfAbsent(mesh, k -> new Rig(face, mesh.extras.get("hands")));
    }

    /**
     * The face put on: {@code src} (the mesh's rest positions) into {@code dst} with the morphs, the lids and the lashes
     * riding them as {@code s} has them.
     */
    static void apply(Rig rig, State s, float[] src, float[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
        float[] w = {s.smile, s.openNow(), s.worry, s.frown, s.raise};
        for (int i = 0; i < w.length; i++) {
            if (Math.abs(w[i]) < 1.0E-3F) continue;
            int[] v = rig.morphV[i];
            float[] d = rig.morphD[i];
            for (int k = 0; k < v.length; k++) {
                int o = v[k] * 3;
                dst[o] += w[i] * d[k * 3];
                dst[o + 1] += w[i] * d[k * 3 + 1];
                dst[o + 2] += w[i] * d[k * 3 + 2];
            }
        }
        int n = rig.samples, cols = rig.cols, rows = rig.rows;
        for (Lid lid : rig.lids) {
            float up = s.lidUFinal(lid.side), lo = s.lidLow(lid.side);
            if (up + lo > 0.98F) {
                float k = 0.98F / (up + lo);
                up *= k;
                lo *= k;
            }
            float c = lid.upper ? up : lo;
            float lift = Math.min(1, c * 12);
            for (int j = 0; j <= cols; j++) {
                int t0 = j * n * 3;
                for (int r = 0; r <= rows; r++) {
                    float x = c * r / rows * (n - 1);
                    int i = Math.min((int) x, n - 2);
                    float f = x - i;
                    int a = t0 + i * 3, b = a + 3, o = (lid.base + r * (cols + 1) + j) * 3;
                    float px = lid.track[a] + (lid.track[b] - lid.track[a]) * f;
                    float py = lid.track[a + 1] + (lid.track[b + 1] - lid.track[a + 1]) * f;
                    float pz = lid.track[a + 2] + (lid.track[b + 2] - lid.track[a + 2]) * f;
                    if (r == 0) {
                        // the root lifts to the cap as soon as it moves: what the lash hid at rest is covered
                        px += (lid.cap[j * 3] - px) * lift;
                        py += (lid.cap[j * 3 + 1] - py) * lift;
                        pz += (lid.cap[j * 3 + 2] - pz) * lift;
                    }
                    dst[o] = px;
                    dst[o + 1] = py;
                    dst[o + 2] = pz;
                    if (r == rows) {
                        lid.edge[j * 3] = px - lid.track[t0];
                        lid.edge[j * 3 + 1] = py - lid.track[t0 + 1];
                        lid.edge[j * 3 + 2] = pz - lid.track[t0 + 2];
                    }
                }
            }
        }
        for (Follower fo : rig.followers) {
            float[] e = rig.lids[fo.lid].edge;
            for (int k = 0; k < fo.v.length; k++) {
                float col = fo.col[k];
                int i = Math.min((int) col, cols - 1);
                float f = col - i;
                int o = fo.v[k] * 3, a = i * 3, b = a + 3;
                dst[o] += fo.w * (e[a] + (e[b] - e[a]) * f);
                dst[o + 1] += fo.w * (e[a + 1] + (e[b + 1] - e[a + 1]) * f);
                dst[o + 2] += fo.w * (e[a + 2] + (e[b + 2] - e[a + 2]) * f);
            }
        }
    }

    // ---------------------------------------------------------------- what she feels

    /** One Makima's face now (client, per entity). */
    static final class State {
        final float[] lidU = {0.2F, 0.2F}, lidL = {0.06F, 0.06F};
        float smile, open, worry, frown, raise, blush;
        /** The irises: -1..1 toward her left / up. */
        float gx, gy;
        /** Her head's tilt and the dip of her chin (degrees, added to the head; the neck takes part). */
        float tilt, tiltV, chin;
        Mood mood = Mood.WAKING;

        private float last = Float.NaN;
        // pulses on top of the eased channels (each stroke), and the blink
        private float pulseOpen, pulseLid, blink;
        private float blinkAt = 40, blinkStart = -100, blinkLen = 6;
        // where she means to look: a target (aim, from the renderer) or wandering, plus small darts
        float aimX, aimY;
        boolean aiming;
        private float wanderX, wanderY, wanderAt, dartX, dartY, dartAt, goalX, goalY;
        // her look: how long you have stared, until when she holds it, which way her head goes
        private float watched, lookUntil, tiltSwapAt;
        private int tiltSide = 1;
        private float mountedAt = -1000, climaxAt = -1000;
        private boolean mounted;

        float lidUp(int side) { return lidU[side] + (1 - lidU[side]) * blink; }

        float lidLow(int side) { return lidL[side] + 0.12F * blink; }

        /** Gaze offset in the mesh's space for eye bone placement. */
        float offX(Rig r) { return gx * r.gazeX; }

        float offY(Rig r) { return gy * r.gazeY; }

        void update(MakimaEntity e, float partial) {
            float t = e.tickCount + partial;
            float dt = Float.isNaN(last) ? 1 : Mth.clamp(t - last, 0, 3);
            last = t;
            if (Minecraft.getInstance().isPaused()) dt = 0;
            RandomSource rnd = e.getRandom();
            choose(e, t, dt);
            Targets g = targets(e, t);
            // easing, each at its own pace
            for (int i = 0; i < 2; i++) {
                float asym = i == 0 ? 1F : 0.96F;        // never quite symmetric
                lidU[i] = ease(lidU[i], g.lidU * asym, 0.3F, dt);
                lidL[i] = ease(lidL[i], g.lidL, 0.25F, dt);
            }
            smile = ease(smile, g.smile, 0.1F, dt);
            open = ease(open, g.open, 0.3F, dt);
            worry = ease(worry, g.worry, 0.15F, dt);
            frown = ease(frown, g.frown, 0.12F, dt);
            raise = ease(raise, g.raise, 0.15F, dt);
            blush = ease(blush, g.blush, 0.03F, dt);
            chin = ease(chin, g.chin, 0.06F, dt);
            // the tilt: a soft spring, so it eases in with a little overshoot and settles
            float k = 0.035F, damp = 0.24F;
            for (float h = dt; h > 0; h -= 0.5F) {
                float step = Math.min(0.5F, h);
                tiltV += ((g.tilt - tilt) * k - tiltV * damp) * step;
                tilt += tiltV * step;
            }
            // each stroke: the mouth opens and the lids drop with it
            float stroke = 0;
            if (mood == Mood.PLEASURE || mood == Mood.CLIMAX) stroke = Math.max(0, MakimaRider.thrust(e, partial));
            float force = mood == Mood.CLIMAX ? 0.25F : 0.12F + 0.05F * e.tempo();
            pulseOpen = stroke * force * 1.6F;
            pulseLid = stroke * force * 0.5F + (mood == Mood.PLEASURE ? 0.04F * Mth.sin(t * 0.63F) : 0);
            open = Mth.clamp(open, 0, 1);
            blinks(e, t, dt, rnd);
            gaze(t, dt, rnd);
        }

        /** The channels as drawn this frame (the pulses on top). */
        float openNow() { return Mth.clamp(open + pulseOpen, 0, 1); }

        private void choose(MakimaEntity e, float t, float dt) {
            Minecraft mc = Minecraft.getInstance();
            boolean with = e.act() != null;
            if (with && !mounted) mountedAt = t;
            mounted = with;
            if (e.climax() > 0) climaxAt = t;
            String forced = System.getProperty("papon.makima.face");
            if (forced != null && !forced.isEmpty()) {
                try {
                    mood = Mood.valueOf(forced.toUpperCase());
                    return;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (with) {
                if (e.climax() > MakimaEntity.CLIMAX_HOLD) mood = Mood.CLIMAX;
                else if (e.holding()) mood = Mood.DAZED;
                else if (t - mountedAt < 34) mood = Mood.PLEASED;
                else mood = Mood.PLEASURE;
                watched = 0;
                return;
            }
            if (e.tickCount < 46) {
                mood = Mood.WAKING;
                return;
            }
            Player p = mc.player;
            double dist = p == null ? 99 : p.distanceTo(e);
            // stared at: your view on her face, close enough to see it
            boolean stared = false;
            if (p != null && dist < 7) {
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 head = e.position().add(0, 1.75, 0).subtract(cam);
                Vec3 look = Vec3.directionFromRotation(mc.gameRenderer.getMainCamera().getXRot(), mc.gameRenderer.getMainCamera().getYRot());
                stared = head.lengthSqr() > 1.0E-4 && look.dot(head.normalize()) > 0.965;
            }
            watched = stared ? watched + dt : Math.max(0, watched - dt * 2);
            if (watched > 14) lookUntil = t + 70;
            if (t - climaxAt < 260) mood = Mood.AFTERGLOW;
            else if (t < lookUntil) mood = Mood.LOOK;
            else if (dist < 8) mood = Mood.NEAR;
            else mood = Mood.CALM;
            if ((mood == Mood.LOOK || mood == Mood.AFTERGLOW) && t > tiltSwapAt) {
                // her head goes the other way now and then (through the middle, the spring carries it)
                tiltSide = -tiltSide;
                tiltSwapAt = t + 160 + e.getRandom().nextFloat() * 140;
            }
        }

        private record Targets(float lidU, float lidL, float smile, float open, float worry, float frown, float raise, float blush,
                               float tilt, float chin) {}

        private Targets targets(MakimaEntity e, float t) {
            float h = e.heat(), k = e.tempo() / 3F;
            return switch (mood) {
                case WAKING -> {
                    // she opens her eyes: shut, then slowly open (her first few seconds)
                    float u = Mth.clamp((e.tickCount - 12) / 30F, 0, 1);
                    yield new Targets(1 - 0.8F * u, 0.1F * (1 - u), 0.15F * u, 0, 0, 0, 0.2F * u, 0, 0, 3 * (1 - u));
                }
                case CALM -> new Targets(0.2F, 0.06F, 0.12F, 0, 0, 0, 0, 0, 0, 0);
                case NEAR -> new Targets(0.24F, 0.1F, 0.3F, 0, 0, 0, 0, 0, 3 * tiltSide, 2);
                case LOOK -> new Targets(0.42F, 0.2F, 0.55F, 0, 0, 0.18F, 0, 0, 15 * tiltSide, 7);
                case PLEASED -> new Targets(0.64F, 0.36F, 0.9F, 0, 0, 0, 0.3F, 0.5F, 5 * tiltSide, 0);
                case PLEASURE -> new Targets(0.3F + 0.32F * h + 0.08F * k, 0.08F + 0.1F * h, 0.35F * (1 - h), 0.1F + 0.3F * h + 0.08F * k,
                        0.25F + 0.65F * h, 0, 0, 0.35F + 0.65F * h, 0, 0);
                case CLIMAX -> new Targets(0.93F, 0.3F, 0, 0.72F, 1, 0, 0, 1, 0, 0);
                case DAZED -> new Targets(0.55F, 0.14F, 0.1F, 0.55F, 0.8F, 0, 0, 1, 0, 0);
                case AFTERGLOW -> new Targets(0.38F, 0.24F, 0.8F, 0, 0, 0, 0, Mth.clamp(1 - (t - climaxAt) / 260F, 0.25F, 1),
                        10 * tiltSide, 4);
            };
        }

        private void blinks(MakimaEntity e, float t, float dt, RandomSource rnd) {
            if (mood == Mood.CLIMAX || mood == Mood.WAKING) {
                blink = 0;
                blinkAt = Math.max(blinkAt, t + 20);
                return;
            }
            if (Boolean.getBoolean("papon.makima.blink")) {
                blink = 1;
                return;
            }
            if (t >= blinkAt && t - blinkStart > blinkLen) start(t, rnd);
            float u = (t - blinkStart) / blinkLen;
            if (u < 0 || u > 1) blink = 0;
            else if (u < 0.35F) blink = smooth(u / 0.35F);
            else if (u < 0.5F) blink = 1;
            else blink = 1 - smooth((u - 0.5F) / 0.5F);
        }

        private void start(float t, RandomSource rnd) {
            blinkStart = t;
            blinkLen = switch (mood) {
                case DAZED -> 13;
                case PLEASURE, AFTERGLOW -> 8;
                default -> 6;
            };
            float wait = switch (mood) {
                case LOOK -> 150 + rnd.nextFloat() * 130;          // staring you down: hardly ever
                case PLEASURE -> 35 + rnd.nextFloat() * 50;
                case DAZED -> 70 + rnd.nextFloat() * 70;
                default -> 45 + rnd.nextFloat() * 75;
            };
            blinkAt = rnd.nextFloat() < 0.18F ? t + blinkLen + 3 : t + wait;    // a double blink now and then
        }

        private void gaze(float t, float dt, RandomSource rnd) {
            float bx, by;
            if (mood == Mood.CLIMAX || mood == Mood.DAZED) {
                // rolled up, wandering a little
                bx = 0.15F * Mth.sin(t * 0.05F);
                by = mood == Mood.CLIMAX ? 0.75F : 0.55F;
            } else if (aiming) {
                bx = aimX;
                by = aimY;
            } else {
                if (t > wanderAt) {
                    wanderX = (rnd.nextFloat() - 0.5F) * 1.2F;
                    wanderY = (rnd.nextFloat() - 0.6F) * 0.7F;
                    wanderAt = t + 30 + rnd.nextFloat() * 70;
                }
                bx = wanderX;
                by = wanderY;
            }
            if (t > dartAt) {
                // small darts round the target: eyes are never quite still
                dartX = (rnd.nextFloat() - 0.5F) * 0.14F;
                dartY = (rnd.nextFloat() - 0.5F) * 0.1F;
                dartAt = t + 12 + rnd.nextFloat() * 30;
            }
            float nx = Mth.clamp(bx + dartX, -1, 1), ny = Mth.clamp(by + dartY, -1, 1);
            float jump = Math.abs(nx - goalX) + Math.abs(ny - goalY);
            if (jump > 0.45F && blink == 0 && blinkAt - t > 20 && rnd.nextFloat() < 0.35F) blinkAt = t;   // a blink with a big glance
            goalX = nx;
            goalY = ny;
            // saccades: quick to a new spot, then still
            gx = ease(gx, goalX, 0.55F, dt);
            gy = ease(gy, goalY, 0.55F, dt);
        }

        private static float ease(float x, float target, float rate, float dt) {
            return x + (target - x) * (1 - (float) Math.exp(-rate * dt * 2.5F));
        }

        private static float smooth(float u) {
            u = Mth.clamp(u, 0, 1);
            return u * u * (3 - 2 * u);
        }

        // the face's final channel values, as MakimaFace.apply reads them
        float lidUFinal(int side) { return Mth.clamp(lidUp(side) + pulseLid, 0, 1); }
    }

    private static final Map<MakimaEntity, State> STATES = new WeakHashMap<>();

    static State state(MakimaEntity e) { return STATES.computeIfAbsent(e, k -> new State()); }

    /** For the dev tour: her mood and channels now. */
    public static String debug(MakimaEntity e) {
        State s = STATES.get(e);
        return s == null ? "none" : String.format("%s lids %.2f/%.2f smile %.2f open %.2f worry %.2f blush %.2f gaze %.2f %.2f%s tilt %.1f chin %.1f",
                s.mood, s.lidUp(0), s.lidLow(0), s.smile, s.openNow(), s.worry, s.blush, s.gx, s.gy, s.aiming ? " (on you)" : "", s.tilt, s.chin);
    }
}
