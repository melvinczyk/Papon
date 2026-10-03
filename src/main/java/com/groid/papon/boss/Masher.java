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

    /** The holes' centers, as offsets from the plate's center in the world (only those over the sand). */
    public static List<Vec3> holes(float yaw) {
        List<Vec3> out = new ArrayList<>();
        for (int i = -3; i <= 3; i++) {
            for (int j = -2; j <= 2; j++) {
                Vec3 w = toWorld(i * CELL, j * CELL, yaw);
                if (Coliseum.offset(w.x, w.z) < -HOLE - 0.5) out.add(w);
            }
        }
        return out;
    }

    /** Whether {@code at} (an offset from the plate's center) is under one of the holes. */
    public static boolean safe(float yaw, Vec3 at, double pad) {
        Vec3 l = toLocal(at.x, at.z, yaw);
        double gx = Math.round(l.x / CELL) * CELL, gz = Math.round(l.z / CELL) * CELL;
        if (Math.abs(gx) > 3 * CELL || Math.abs(gz) > 2 * CELL) return false;
        return Math.abs(l.x - gx) < HOLE - pad && Math.abs(l.z - gz) < HOLE - pad;
    }

    /** Whether the plate's own (x, z) is metal (not a hole, inside the plate). */
    public static boolean metal(double lx, double lz) {
        if (Math.abs(lx) > HALF_X || Math.abs(lz) > HALF_Z) return false;
        double gx = Math.round(lx / CELL) * CELL, gz = Math.round(lz / CELL) * CELL;
        boolean hole = Math.abs(gx) <= 3 * CELL && Math.abs(gz) <= 2 * CELL && Math.abs(lx - gx) < HOLE && Math.abs(lz - gz) < HOLE;
        return !hole;
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
