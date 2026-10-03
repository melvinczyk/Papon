package com.groid.papon.entity.ai;

import com.groid.papon.journey.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Plans his route across the loaded land around him: A* over a coarse grid of 4x4-block cells
 * (world-aligned, so successive plans agree with each other), reading only heightmaps.
 *
 * A cell can be crossed if its surface is dry ground or water (the raft takes the water); lava
 * and unloaded land are what they are: lava never, unloaded land optimistically, at a premium, so
 * the route heads out of what is loaded toward the destination and is refined as he goes. Between
 * neighbouring cells the ground is checked at the halfway point too, and no half-step may climb
 * more than he can walk up (one block per block) or drop more than his fall limit, so a cliff or a
 * steep mountain face is a wall and the route goes round it (or over the gentle side). Slope costs
 * extra either way, and so do cells marked as dead ends, where he got stuck before.
 *
 * Beyond the planning window the rest of the way is estimated as a straight line, so the result
 * is the best known route toward the destination even when the destination is far away. Plans
 * are deterministic and slightly prefer the previous route, so replanning never flips him from
 * one side of an obstacle to the other.
 */
public final class RoutePlanner {
    public static final int CELL = 4;
    /** Window radius in cells (96 blocks). */
    private static final int RADIUS = 24;
    private static final int MAX_RISE_HALF = 2;   // over a 2-block half-step: 1 block per block
    private static final int MAX_DROP_HALF = 2;   // his fall limit
    private static final double SLOPE_COST = 1.5;
    private static final double WATER_COST = 1.4;
    private static final double SHORE_COST = 6;
    private static final double UNKNOWN_COST = 1.5;
    private static final double DEAD_END_COST = 80;
    private static final double OFF_ROUTE_COST = 1.0;
    private static final int MAX_EXPANSIONS = 6000;

    private static final int LAND = 0, WATER = 1, BLOCKED = 2, UNKNOWN = 3;

    private RoutePlanner() {}

    /** Route points (cell centres on the surface) from his cell to the destination, or null. */
    @Nullable
    public static List<BlockPos> plan(ServerLevel level, BlockPos from, BlockPos dest, Set<Long> deadEnds,
                                      Set<Long> previous) {
        int sx = Math.floorDiv(from.getX(), CELL), sz = Math.floorDiv(from.getZ(), CELL);
        int gx = Math.floorDiv(dest.getX(), CELL), gz = Math.floorDiv(dest.getZ(), CELL);
        boolean destInside = Math.abs(gx - sx) <= RADIUS && Math.abs(gz - sz) <= RADIUS;
        Map<Long, Cell> cells = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> came = new HashMap<>();
        long start = key(sx, sz);
        best.put(start, 0.0);
        open.add(new Node(sx, sz, 0, h(sx, sz, dest), false));
        Node finish = null;
        Node closest = null;     // if the destination cannot be reached: as close as he can get
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
            Node n = open.poll();
            if (n.virtualGoal) {
                finish = n;
                break;
            }
            long k = key(n.x, n.z);
            if (n.g > best.getOrDefault(k, Double.MAX_VALUE) + 1e-9) continue;
            if (closest == null || n.f - n.g < closest.f - closest.g) closest = n;
            if (destInside && n.x == gx && n.z == gz) {
                finish = n;
                break;
            }
            boolean edge = Math.abs(n.x - sx) == RADIUS || Math.abs(n.z - sz) == RADIUS;
            if (edge && !destInside) {
                // the rest of the way, beyond the window, as the crow flies (at the unknown-land rate)
                double rest = h(n.x, n.z, dest) * UNKNOWN_COST;
                open.add(new Node(n.x, n.z, n.g + rest, n.g + rest, true));
            }
            Cell here = cell(level, cells, n.x, n.z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int x = n.x + dx, z = n.z + dz;
                    if (Math.abs(x - sx) > RADIUS || Math.abs(z - sz) > RADIUS) continue;
                    Cell next = cell(level, cells, x, z);
                    double step = stepCost(level, here, next, dx != 0 && dz != 0);
                    if (step < 0) continue;
                    long nk = key(x, z);
                    if (deadEnds.contains(nk)) step += DEAD_END_COST;
                    // hysteresis: leaving the route he is already on costs a little, so newly loaded
                    // land only changes his mind when it is clearly better
                    if (!previous.isEmpty() && !previous.contains(nk)) step += OFF_ROUTE_COST;
                    double g = n.g + step;
                    if (g < best.getOrDefault(nk, Double.MAX_VALUE)) {
                        best.put(nk, g);
                        came.put(nk, k);
                        open.add(new Node(x, z, g, g + h(x, z, dest), false));
                    }
                }
            }
        }
        if (finish == null) finish = closest;
        if (finish == null || (finish.x == sx && finish.z == sz)) return null;
        List<BlockPos> route = new ArrayList<>();
        long k = key(finish.x, finish.z);
        while (true) {
            Cell c = cells.get(k);
            if (c != null && c.surface != null) route.add(c.surface);
            Long prev = came.get(k);
            if (prev == null) break;
            k = prev;
        }
        Collections.reverse(route);
        return route.size() < 2 ? null : route;
    }

    public static long cellKey(BlockPos pos) {
        return key(Math.floorDiv(pos.getX(), CELL), Math.floorDiv(pos.getZ(), CELL));
    }

    /** Cost of the step between two neighbouring cells, or -1 if it cannot be walked. */
    private static double stepCost(ServerLevel level, Cell a, Cell b, boolean diagonal) {
        if (b.type == BLOCKED) return -1;
        double len = diagonal ? CELL * Mth.SQRT_OF_TWO : CELL;
        if (a.type == UNKNOWN || b.type == UNKNOWN) return len * UNKNOWN_COST;
        // the ground halfway between, so a cliff between two cell centres is not missed
        int mx = (a.surface.getX() + b.surface.getX()) >> 1;
        int mz = (a.surface.getZ() + b.surface.getZ()) >> 1;
        BlockPos mid = Terrain.surfaceAt(level, mx, mz);
        int ha = a.surface.getY(), hb = b.surface.getY();
        int hm = mid == null ? (ha + hb) / 2 : mid.getY();
        if (mid != null && Terrain.isLava(level, mid)) return -1;
        int scale = diagonal ? 2 : 1;   // a diagonal half-step is ~3 blocks: allow a little more
        if (!halfOk(hm - ha, scale) || !halfOk(hb - hm, scale)) return -1;
        double cost = len + SLOPE_COST * (Math.abs(hm - ha) + Math.abs(hb - hm));
        if (b.type == WATER) cost = cost * WATER_COST + (a.type == WATER ? 0 : SHORE_COST);
        else if (a.type == WATER) cost += SHORE_COST;
        return cost;
    }

    private static boolean halfOk(int rise, int scale) {
        return rise <= MAX_RISE_HALF + scale - 1 && -rise <= MAX_DROP_HALF + scale - 1;
    }

    private static Cell cell(ServerLevel level, Map<Long, Cell> cells, int cx, int cz) {
        return cells.computeIfAbsent(key(cx, cz), k -> {
            int x = cx * CELL + CELL / 2, z = cz * CELL + CELL / 2;
            BlockPos s = Terrain.surfaceAt(level, x, z);
            if (s == null) return new Cell(null, UNKNOWN);
            if (Terrain.isLava(level, s)) return new Cell(s, BLOCKED);
            if (Terrain.isWaterSurface(level, s)) return new Cell(s, WATER);
            return new Cell(s, LAND);
        });
    }

    private static double h(int cx, int cz, BlockPos dest) {
        double dx = cx * CELL + CELL / 2.0 - dest.getX();
        double dz = cz * CELL + CELL / 2.0 - dest.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private record Cell(@Nullable BlockPos surface, int type) {}

    private record Node(int x, int z, double g, double f, boolean virtualGoal) {}
}
