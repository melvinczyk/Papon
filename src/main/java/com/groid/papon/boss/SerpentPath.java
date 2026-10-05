package com.groid.papon.boss;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a feathered serpent (SkySerpentEntity) is, as a pure function of how far its head has come along it: the same on
 * the server (its dives hit people) and every client (its body, a long chain of segments, follows the head's path exactly:
 * segment i is where the head was so many blocks ago). Out of its portal of black smoke, down a sweeping curve onto its
 * orbit round the arena, round and round with a slow weave, and now and then a dive: down off the orbit, straight across
 * the floor, and back up onto the orbit where it would have been.
 *
 * Distances {@code s} are blocks along the path, 0 at the portal (before it the serpent is still in the smoke).
 */
public final class SerpentPath {
    /** One dive: off the orbit at {@code s0}, down onto the floor at {@code a}, straight to {@code b}, back up. */
    public record Dive(double s0, Vec3 a, Vec3 b) {}

    /**
     * El Ouroboros: off the orbit at {@code s0}, down onto an ellipse round {@code center} (half-axes {@code ra}, {@code rb}, at
     * height {@code y}), entered at {@code angle0}, round it {@code laps} times in direction {@code dir}, then back up.
     */
    public record Ring(double s0, Vec3 center, double ra, double rb, double y, double angle0, int dir, double laps) {}

    private final Vec3 portal, center;
    private final double radius, height;
    private final int dir;
    private final double entryAngle;
    private final Vec3 entry, entryTangent, approachTangent;
    private final double approachLength;
    private final Curve approachCurve;
    private final List<Piece> dives = new ArrayList<>();
    private final List<RingPiece> rings = new ArrayList<>();

    /** The orbit's weave: up and down this much, once every so many blocks. */
    private static final double WEAVE = 7, WEAVE_LENGTH = 110;

    public SerpentPath(Vec3 portal, Vec3 center, double radius, double height, int dir, List<Dive> dives) {
        this(portal, center, radius, height, dir, dives, List.of());
    }

    public SerpentPath(Vec3 portal, Vec3 center, double radius, double height, int dir, List<Dive> dives, List<Ring> rings) {
        this.portal = portal;
        this.center = center;
        this.radius = radius;
        this.height = height;
        this.dir = dir >= 0 ? 1 : -1;
        double ts = Math.atan2(portal.z - center.z, portal.x - center.x);
        entryAngle = ts + this.dir * 0.9;
        entry = orbitPoint(entryAngle, 0);
        entryTangent = orbitTangent(entryAngle);
        approachTangent = center.add(0, height, 0).subtract(portal).normalize();
        approachCurve = new Curve(this::approach);
        approachLength = approachCurve.length;
        for (Dive d : dives) this.dives.add(new Piece(d));
        for (Ring r : rings) this.rings.add(new RingPiece(r));
    }

    public double approachLength() { return approachLength; }

    // ---- the orbit

    private Vec3 orbitPoint(double angle, double along) {
        double y = height + WEAVE * Math.sin(along * Math.PI * 2 / WEAVE_LENGTH);
        return center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    private Vec3 orbitTangent(double angle) { return new Vec3(-Math.sin(angle) * dir, 0, Math.cos(angle) * dir); }

    /** On the orbit, {@code s} blocks from the portal (as if it had circled all along). */
    public Vec3 orbitAt(double s) {
        double along = s - approachLength;
        return orbitPoint(entryAngle + dir * along / radius, along);
    }

    private Vec3 orbitTangentAt(double s) { return orbitTangent(entryAngle + dir * (s - approachLength) / radius); }

    // ---- the way in

    private Vec3 approach(double t) {
        double k = (portal.distanceTo(entry)) * 1.1;
        return hermite(portal, approachTangent.scale(k), entry, entryTangent.scale(k), t);
    }

    // ---- where it is

    public Vec3 at(double s) {
        if (s <= 0) return portal.add(approachTangent.scale(s));
        if (s < approachLength) return approachCurve.at(s);
        for (Piece p : dives) { if (s >= p.s0 && s < p.s0 + p.total) return p.at(s - p.s0); }
        for (RingPiece p : rings) { if (s >= p.s0 && s < p.s0 + p.total) return p.at(s - p.s0); }
        return orbitAt(s);
    }

    /** Whether {@code s} is inside a dive or a ring (low over the floor: it can hit). */
    public boolean diving(double s) {
        for (Piece p : dives) { if (s >= p.s0 + p.l1 * 0.6 && s < p.s0 + p.l1 + p.line + p.l3 * 0.4) return true; }
        for (RingPiece p : rings) { if (s >= p.s0 + p.l1 * 0.6 && s < p.s0 + p.l1 + p.lap + p.l3 * 0.4) return true; }
        return false;
    }

    /** The length of a dive (or ring) starting there (to its end), or 0 if there is none. */
    public double diveLength(double s0) {
        for (Piece p : dives) if (Math.abs(p.s0 - s0) < 1e-6) return p.total;
        for (RingPiece p : rings) if (Math.abs(p.s0 - s0) < 1e-6) return p.total;
        return 0;
    }

    /** A ring starting there: how far into it the floor ring starts (the way down), and how long the ring itself is. */
    public double[] ringSpan(double s0) {
        for (RingPiece p : rings) if (Math.abs(p.s0 - s0) < 1e-6) return new double[]{p.l1, p.lap};
        return new double[]{0, 0};
    }

    /** Whether {@code s} is on a ring's round itself (not the way down or up). */
    public boolean onRing(double s) {
        for (RingPiece p : rings) { if (s >= p.s0 + p.l1 && s < p.s0 + p.l1 + p.lap) return true; }
        return false;
    }

    private final class RingPiece {
        final double s0, l1, lap;
        double l3, total;
        final Ring ring;
        final Vec3 q0, t0, start, startTangent;
        Vec3 q3, t3, end, endTangent;
        final Curve downCurve, ringCurve;
        Curve upCurve;

        RingPiece(Ring r) {
            ring = r;
            s0 = r.s0();
            q0 = orbitAt(s0);
            t0 = orbitTangentAt(s0);
            start = ringPoint(0);
            startTangent = ringPoint(0.002).subtract(start).normalize();
            downCurve = new Curve(this::down);
            l1 = downCurve.length;
            ringCurve = new Curve(this::ringPoint, Math.max(64, (int) Math.ceil(r.laps() * 160)));
            lap = ringCurve.length;
            end = ringPoint(1);
            endTangent = end.subtract(ringPoint(0.998)).normalize();
            l3 = q0.distanceTo(end);
            for (int i = 0; i < 4; i++) {
                total = l1 + lap + l3;
                q3 = orbitAt(s0 + total);
                t3 = orbitTangentAt(s0 + total);
                upCurve = new Curve(this::up);
                l3 = upCurve.length;
            }
            total = l1 + lap + l3;
        }

        Vec3 ringPoint(double u) {
            double th = ring.angle0() + ring.dir() * u * Math.PI * 2 * ring.laps();
            return ring.center().add(Math.cos(th) * ring.ra(), ring.y(), Math.sin(th) * ring.rb());
        }

        Vec3 down(double t) {
            double k = q0.distanceTo(start) * 1.2;
            return hermite(q0, t0.scale(k), start, startTangent.scale(k * 0.6), t);
        }

        Vec3 up(double t) {
            double k = end.distanceTo(q3) * 1.2;
            return hermite(end, endTangent.scale(k * 0.6), q3, t3.scale(k), t);
        }

        Vec3 at(double s) {
            if (s < l1) return downCurve.at(s);
            s -= l1;
            if (s < lap) return ringCurve.at(s);
            s -= lap;
            return upCurve.at(Math.min(s, l3));
        }
    }


    private final class Piece {
        final double s0;
        final Vec3 a, b, q0, t0, along;
        Vec3 q3, t3;
        final double l1, line;
        double l3, total;
        final Curve downCurve;
        Curve upCurve;

        Piece(Dive d) {
            s0 = d.s0();
            a = d.a();
            b = d.b();
            q0 = orbitAt(s0);
            t0 = orbitTangentAt(s0);
            along = b.subtract(a).normalize();
            line = a.distanceTo(b);
            downCurve = new Curve(this::down);
            l1 = downCurve.length;
            // the rejoin point depends on how long the way back up is: settle it in a few rounds
            l3 = q0.distanceTo(b);
            for (int i = 0; i < 4; i++) {
                total = l1 + line + l3;
                q3 = orbitAt(s0 + total);
                t3 = orbitTangentAt(s0 + total);
                upCurve = new Curve(this::up);
                l3 = upCurve.length;
            }
            total = l1 + line + l3;
        }

        Vec3 down(double t) {
            double k = q0.distanceTo(a) * 1.2;
            return hermite(q0, t0.scale(k), a, along.scale(k), t);
        }

        Vec3 up(double t) {
            double k = b.distanceTo(q3) * 1.2;
            return hermite(b, along.scale(k), q3, t3.scale(k), t);
        }

        Vec3 at(double s) {
            if (s < l1) return downCurve.at(s);
            s -= l1;
            if (s < line) return a.add(along.scale(s));
            s -= line;
            return upCurve.at(Math.min(s, l3));
        }
    }

    // ---- helpers

    private static Vec3 hermite(Vec3 p0, Vec3 m0, Vec3 p1, Vec3 m1, double t) {
        double t2 = t * t, t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1, h10 = t3 - 2 * t2 + t, h01 = -2 * t3 + 3 * t2, h11 = t3 - t2;
        return p0.scale(h00).add(m0.scale(h10)).add(p1.scale(h01)).add(m1.scale(h11));
    }

    /** A curve sampled by arc length, so the body's segments stay evenly spaced along it. */
    private static final class Curve {
        final int N;
        final Vec3[] pts;
        final double[] cum;
        final double length;

        Curve(java.util.function.DoubleFunction<Vec3> f) { this(f, 64); }

        Curve(java.util.function.DoubleFunction<Vec3> f, int n) {
            N = n;
            pts = new Vec3[N + 1];
            cum = new double[N + 1];
            for (int i = 0; i <= N; i++) {
                pts[i] = f.apply(i / (double) N);
                cum[i] = i == 0 ? 0 : cum[i - 1] + pts[i].distanceTo(pts[i - 1]);
            }
            length = Math.max(1e-3, cum[N]);
        }

        Vec3 at(double s) {
            if (s <= 0) return pts[0];
            if (s >= length) return pts[N];
            int lo = 0, hi = N;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cum[mid] <= s) lo = mid;
                else hi = mid;
            }
            double k = (s - cum[lo]) / Math.max(1e-6, cum[hi] - cum[lo]);
            return pts[lo].lerp(pts[hi], k);
        }
    }
}
