package com.groid.papon.boss;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * El Machacador's shape, shared by the server (who is safe) and the renderer (what it looks like): a plate
 * of metal as big as the sand, turned by the prop's yaw, with a grid of square holes in it. Under a hole you
 * are safe; anywhere else under the plate you are mashed.
 */
public final class Masher {
    /** Half the plate's size (along its own x and z), the grid's spacing, half a hole's width. */
    public static final double HALF_X = 31, HALF_Z = 23, CELL = 9, HOLE = 2.4;

    private Masher() {}

    /** Layouts: the grid of holes slid half a cell along the plate's x and/or z (each slam a different one). */
    public static final int VARIANTS = 4;

    private static double ox(int variant) {
        return (variant & 1) * CELL / 2;
    }

    private static double oz(int variant) {
        return ((variant >> 1) & 1) * CELL / 2;
    }

    /** The hole nearest the plate's own (lx, lz) in this layout, or null if that cell has none. */
    private static double[] cell(double lx, double lz, int variant) {
        double gx = Math.round((lx - ox(variant)) / CELL) * CELL + ox(variant);
        double gz = Math.round((lz - oz(variant)) / CELL) * CELL + oz(variant);
        if (Math.abs(gx) > HALF_X - HOLE - 0.5 || Math.abs(gz) > HALF_Z - HOLE - 0.5) return null;
        return new double[] {gx, gz};
    }

    /** The holes' centers, as offsets from the plate's center in the world (only those over the sand). */
    public static List<Vec3> holes(float yaw, int variant) {
        List<Vec3> out = new ArrayList<>();
        for (int i = -4; i <= 4; i++) {
            for (int j = -3; j <= 3; j++) {
                double lx = i * CELL + ox(variant), lz = j * CELL + oz(variant);
                if (Math.abs(lx) > HALF_X - HOLE - 0.5 || Math.abs(lz) > HALF_Z - HOLE - 0.5) continue;
                Vec3 w = toWorld(lx, lz, yaw);
                if (Coliseum.offset(w.x, w.z) < -HOLE - 0.5) out.add(w);
            }
        }
        return out;
    }

    /** Whether {@code at} (an offset from the plate's center) is under one of the holes. */
    public static boolean safe(float yaw, int variant, Vec3 at, double pad) {
        Vec3 l = toLocal(at.x, at.z, yaw);
        double[] g = cell(l.x, l.z, variant);
        return g != null && Math.abs(l.x - g[0]) < HOLE - pad && Math.abs(l.z - g[1]) < HOLE - pad;
    }

    /** Whether the plate's own (x, z) is metal (not a hole, inside the plate). */
    public static boolean metal(double lx, double lz, int variant) {
        if (Math.abs(lx) > HALF_X || Math.abs(lz) > HALF_Z) return false;
        double[] g = cell(lx, lz, variant);
        return g == null || Math.abs(lx - g[0]) >= HOLE || Math.abs(lz - g[1]) >= HOLE;
    }

    public static Vec3 toWorld(double lx, double lz, float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(lx * c - lz * s, 0, lx * s + lz * c);
    }

    public static Vec3 toLocal(double x, double z, float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(x * c + z * s, 0, -x * s + z * c);
    }
}
