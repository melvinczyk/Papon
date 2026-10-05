package com.groid.papon.boss;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Final Guardian's grand moves, worked out the same on the server (who gets hurt) and on every client (what is drawn:
 * client/MuertosArena): pure functions of a synced seed, the arena's size and the ticks into the move, so nothing more than
 * the move's start has to be sent.
 *
 *   La Pista de los Muertos   the floor as a dance floor of {@link #TILE}-block tiles; each measure a pattern of them flashes
 *                             in one colour on the band's beat, then erupts in spirit fire
 *   El Camino de Cempasúchil  a winding path of marigolds across the floor; the ectoplasm floods everything else, and the
 *                             path wilts from its start
 *   El Castillo               a fireworks tower in the middle: its wheels light tier by tier and spin spirals of sparks out
 *                             across the floor
 *
 * Offsets are from the arena's middle (x, z), the arena an ellipse of half-axes a, b.
 */
public final class Fiesta {
    private Fiesta() {}

    /** The fiesta's colours: marigold, rosa mexicano, turquoise, violet, lime, gold. */
    public static final int[] COLOURS = {0xFF9618, 0xEC2E8C, 0x28DCE6, 0x9646E6, 0x96F03C, 0xFFD63C};

    public static int colour(int i) { return COLOURS[Math.floorMod(i, COLOURS.length)]; }

    /** A beat of the band's last song (Persona Ideal, about 129 bpm), in ticks. */
    public static final double BEAT = 9.28;

    public static boolean onSand(double x, double z, double a, double b, double margin) {
        double ra = a - margin, rb = b - margin;
        return ra > 0 && rb > 0 && (x * x) / (ra * ra) + (z * z) / (rb * rb) <= 1;
    }

    // ------------------------------------------------------------------ La Pista de los Muertos

    public static final double TILE = 6;
    /** Ticks before its first beat (he lands in the middle and the floor lights up). */
    public static final int PISTA_LEAD = 26;
    public static final int PISTA_MEASURES = 9;

    /** The tick (into the move) of beat {@code k}. */
    public static int pistaBeat(double k) { return PISTA_LEAD + (int) Math.round(k * BEAT); }

    /** Measure m: its tiles flash from this beat ... */
    public static int warnBeat(int m) { return eruptBeat(m) - (m < 4 ? 2 : 1); }

    /** ... and erupt on this one (every other beat; the last three come every beat). */
    public static int eruptBeat(int m) { return m < 6 ? 2 + m * 2 : 12 + (m - 5); }

    /** How long a tile's fire lasts (ticks). */
    public static final int ERUPT_TICKS = 6;

    /** The cell of a point (offsets from the middle). */
    public static int cell(double v) { return (int) Math.floor(v / TILE + 0.5); }

    /** Whether cell (i, j) is on the floor at all. */
    public static boolean tile(int i, int j, double a, double b) { return onSand(i * TILE, j * TILE, a, b, TILE * 0.55); }

    /**
     * Whether cell (i, j) goes up in measure m: a pattern per measure (checks, rings, stripes, diagonals, a lattice, big
     * checks). Never more than about half the floor, and a safe tile always within one tile of any other (the big checks
     * only while there are two beats of warning).
     */
    public static boolean pistaLit(int seed, int m, int i, int j) {
        Random r = new Random(seed * 31L + m * 7919L);
        int kind = r.nextInt(m < 4 ? 7 : 5);
        int p = r.nextInt(4);
        if (m == 0) kind = 0;
        return switch (kind) {
            case 0 -> ((i + j + p) & 1) == 0;
            case 1 -> (((int) Math.round(Math.sqrt(i * i + j * j)) + p) & 1) == 0;
            case 2 -> Math.floorMod(i + p, 2) == 0;
            case 3 -> Math.floorMod(j + p, 2) == 0;
            case 4 -> Math.floorMod(i + j + p, 4) < 2;
            case 5 -> Math.floorMod(Math.floorDiv(i + p, 2) + Math.floorDiv(j, 2), 2) == 0;
            default -> Math.floorMod(Math.abs(i), 3) == p % 3 || Math.floorMod(Math.abs(j), 3) == p % 3;
        };
    }

    public static int pistaColour(int seed, int m) { return colour(seed + m * 5); }

    // ------------------------------------------------------------------ El Camino de Cempasúchil

    public static final int CAMINO_BLOOM = 40, CAMINO_FLOOD = 34, CAMINO_FULL = 64, CAMINO_WILT = 92, CAMINO_DRAIN = 232;
    public static final double CAMINO_WIDTH = 2.6, WILT_SPEED = 0.8;

    /** The path: points (x, z offsets) a block apart, from one end of the floor to the other, winding. Cached by key. */
    public static double[][] camino(int seed, double a, double b) {
        long key = seed * 1000003L + Math.round(a * 10) * 1009L + Math.round(b * 10);
        Cached got = cached;
        if (got != null && got.key == key) return got.pts;
        Random r = new Random(seed);
        int n = 7;
        double dir = r.nextBoolean() ? 1 : -1;
        List<double[]> ctrl = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double x = (-0.82 + 1.64 * k / (n - 1)) * a * dir;
            double z = (k % 2 == 0 ? 1 : -1) * (0.22 + 0.42 * r.nextDouble()) * b * (r.nextDouble() < 0.15 ? -1 : 1);
            if (k == 0 || k == n - 1) z *= 0.5;
            ctrl.add(new double[]{x, z});
        }
        List<double[]> pts = new ArrayList<>();
        for (int k = 0; k < n - 1; k++) {
            double[] p0 = ctrl.get(Math.max(0, k - 1)), p1 = ctrl.get(k), p2 = ctrl.get(k + 1), p3 = ctrl.get(Math.min(n - 1, k + 2));
            for (int s = 0; s < 40; s++) {
                double t = s / 40.0, t2 = t * t, t3 = t2 * t;
                double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                double z = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                pts.add(new double[]{x, z});
            }
        }
        pts.add(ctrl.get(n - 1));
        // resampled a block apart
        List<double[]> out = new ArrayList<>();
        out.add(pts.get(0));
        double carry = 0;
        for (int i = 1; i < pts.size(); i++) {
            double[] p = pts.get(i - 1), q = pts.get(i);
            double d = Math.hypot(q[0] - p[0], q[1] - p[1]);
            double s = 1 - carry;
            while (s <= d) {
                double k = s / d;
                out.add(new double[]{p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k});
                s += 1;
            }
            carry = d - (s - 1);
        }
        double[][] pts2 = out.toArray(new double[0][]);
        cached = new Cached(key, pts2);
        return pts2;
    }

    /** The last path worked out (the server and the client thread both ask: one object, swapped whole). */
    private record Cached(long key, double[][] pts) {}

    private static volatile Cached cached;

    /** How much of the path has bloomed (blocks from its start), t ticks into the move. */
    public static double bloomed(double t, int len) { return Math.min(len, t / CAMINO_BLOOM * len); }

    /** Where the wilting has reached (blocks from its start). */
    public static double wilted(double t) { return Math.max(0, (t - CAMINO_WILT) * WILT_SPEED); }

    /** How high the flood is, 0..1. */
    public static float flood(double t) {
        if (t < CAMINO_FLOOD) return 0;
        if (t < CAMINO_FULL) return (float) ((t - CAMINO_FLOOD) / (CAMINO_FULL - CAMINO_FLOOD));
        if (t < CAMINO_DRAIN) return 1;
        return (float) Math.max(0, 1 - (t - CAMINO_DRAIN) / 16.0);
    }

    /** Whether a point (offsets) is on the living path. */
    public static boolean onCamino(double[][] path, double x, double z, double t) {
        int from = (int) Math.floor(wilted(t));
        double w = CAMINO_WIDTH;
        for (int i = Math.max(0, from); i < path.length; i++) {
            double dx = path[i][0] - x, dz = path[i][1] - z;
            if (dx * dx + dz * dz <= w * w) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ El Castillo

    public static final int CASTILLO_TIERS = 4, TIER_EVERY = 36, TIER_START = 34, TIER_RUN = 92, SPARK_EVERY = 3;
    public static final int CROWN = 182, BURST = 238;
    public static final double SPARK_Y = 1.1, SPARK_HIT = 0.95;

    public static int tierStart(int tier) { return TIER_START + tier * TIER_EVERY; }

    public static int arms(int tier) { return 5 + tier; }

    /** Each tier's wheel: its height on the tower (blocks over the floor). */
    public static double tierHeight(int tier) { return 3.5 + tier * 3.6; }

    /** A spark from tier {@code tier}'s wheel, sent at tick {@code emit}, arm {@code arm}, at tick {@code t}: x, z offsets (or null). */
    public static double[] spark(int seed, int tier, int emit, int arm, double t, double a, double b) {
        double age = t - emit;
        if (age < 0) return null;
        double v = 0.5 + 0.09 * tier;
        double r = 1.2 + age * v;
        int spin = (tier & 1) == 0 ? 1 : -1;
        double base = (seed % 360) * Math.PI / 180 + tier * 0.6;
        double ang = base + spin * (emit - tierStart(tier)) * 0.045 + arm * Math.PI * 2 / arms(tier) - spin * age * 0.006;
        double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
        if (!onSand(x, z, a, b, 0.5)) return null;
        return new double[]{x, z};
    }

    /** Every live spark at tick t: [x, z, tier, id]. */
    public static List<double[]> sparks(int seed, double t, double a, double b) {
        List<double[]> out = new ArrayList<>();
        for (int tier = 0; tier < CASTILLO_TIERS; tier++) {
            int s0 = tierStart(tier);
            for (int emit = s0; emit <= Math.min(t, s0 + TIER_RUN); emit += SPARK_EVERY) {
                for (int arm = 0; arm < arms(tier); arm++) {
                    double[] p = spark(seed, tier, emit, arm, t, a, b);
                    if (p != null) out.add(new double[]{p[0], p[1], tier, tier * 100000 + emit * 16 + arm});
                }
            }
        }
        return out;
    }
}
