package com.groid.papon.entity.ai;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.PaponState;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.journey.JourneyPlanner;
import com.groid.papon.journey.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks him toward the Taco Truck. The site is hundreds of blocks off, far beyond any path search,
 * so there are two levels:
 *
 *   route  RoutePlanner's coarse A* over the loaded heightmap (4-block cells, ~96 blocks around
 *          him): goes round cliffs and steep mountain faces, over gentle slopes, and across water
 *          (the raft) when that is shorter. Replanned every few seconds as land loads, preferring
 *          the route he is on, and never re-routed through a cell where he got stuck before.
 *   legs   he walks the route a leg at a time: the farthest route point up to ~28 blocks ahead
 *          that the surface-only navigator can reach. Progress along the route only ever moves
 *          forward, and the next leg is chosen a few blocks before the current one ends, so he
 *          neither stops between legs nor turns back.
 *
 * Without a route (nothing loaded yet), a leg is picked from a fan around the bearing instead.
 *
 * Getting unstuck, in escalating order:
 *   1. a leg he cannot reach, or stops making progress on, marks that spot a dead end and replans;
 *   2. after repeated failures he hops a few blocks forward to dry, open ground (a puff of dust);
 *   3. right next to the site, being stuck just counts as arriving.
 * Anything worse (in a cave, a wall, the void) is handled by the entity's own keepSafe check.
 */
public class JourneyGoal extends Goal {
    private static final int[] FAN = {0, 20, -20, 40, -40, 65, -65, 90, -90, 125, -125};
    private static final int[] REACH = {28, 20, 12};
    private static final int MAX_PATH_TRIES = 4;
    /** Pick the next leg this far before the end of the current one. */
    private static final double LOOKAHEAD = 6.0;
    /** After a pick that found nothing, wait this long before searching again. */
    private static final int REPICK_COOLDOWN = 10;
    private static final double WATER_PENALTY = 12;
    private static final int PROGRESS_CHECK = 40;
    private static final double MIN_PROGRESS = 0.75;
    private static final int STUCK_LIMIT = 4;
    private static final int FAILED_PICK_LIMIT = 3;
    private static final double SPEED = 1.0;
    private static final int REPLAN_TICKS = 100;
    private static final double LEG = 28;

    private final WanderingPaponEntity papon;
    private final Deque<BlockPos> blacklist = new ArrayDeque<>();
    @Nullable private BlockPos waypoint;
    private Vec3 lastCheckPos = Vec3.ZERO;
    private int ticksSinceCheck;
    private int stuckChecks;
    private int failedPicks;
    private int repickCooldown;
    @Nullable private List<BlockPos> route;
    private int routeIndex;
    private int replanIn;
    private final Set<Long> deadEnds = new HashSet<>();
    private final Deque<Long> deadEndOrder = new ArrayDeque<>();

    public JourneyGoal(WanderingPaponEntity papon) {
        this.papon = papon;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return papon.getPaponState() == PaponState.WALKING && papon.isEscorted() && papon.getDestination() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        waypoint = null;
        route = null;
        lastCheckPos = papon.position();
        ticksSinceCheck = 0;
        stuckChecks = 0;
    }

    @Override
    public void stop() {
        papon.getNavigation().stop();
        waypoint = null;
    }

    @Override
    public void tick() {
        if (!(papon.level() instanceof ServerLevel level)) return;
        if (repickCooldown > 0) repickCooldown--;
        if (route == null || --replanIn <= 0) replan(level);
        boolean needLeg = waypoint == null || papon.getNavigation().isDone() || closeTo(waypoint, LOOKAHEAD);
        if (needLeg && repickCooldown == 0) {
            boolean ok = route != null && followRoute();
            if (!ok && route == null) {      // the planned stretch ran out or was blocked: plan again
                replan(level);
                ok = route != null && followRoute();
            }
            if (!ok) pickWaypoint(level);
        }
        if (++ticksSinceCheck >= PROGRESS_CHECK) {
            ticksSinceCheck = 0;
            Vec3 now = papon.position();
            if (now.distanceTo(lastCheckPos) < MIN_PROGRESS) {
                onNoProgress(level);
            } else {
                stuckChecks = 0;
                if (blacklist.size() > 8) blacklist.pollFirst();
            }
            lastCheckPos = now;
        }
    }

    private void onNoProgress(ServerLevel level) {
        stuckChecks++;
        if (waypoint != null) {
            blacklist(waypoint);
            deadEnd(waypoint);
        }
        waypoint = null;
        route = null;
        papon.getNavigation().stop();
        if (papon.remainingDistance() < 10) {
            papon.arriveNearby();
        } else if (stuckChecks >= STUCK_LIMIT) {
            stuckChecks = 0;
            hopForward(level);
        }
    }

    private void replan(ServerLevel level) {
        replanIn = REPLAN_TICKS;
        BlockPos dest = papon.getDestination();
        if (dest == null) return;
        Set<Long> previous = new HashSet<>();
        if (route != null) {
            for (int i = routeIndex; i < route.size(); i++) previous.add(RoutePlanner.cellKey(route.get(i)));
        }
        route = RoutePlanner.plan(level, papon.blockPosition(), dest, deadEnds, previous);
        routeIndex = 0;
        if (route != null && WanderingPaponEntity.debugEscort) {
            PaponMod.LOG.info("[debug] Papon route: {} cells from {} toward {}", route.size(),
                    papon.blockPosition().toShortString(), route.get(route.size() - 1).toShortString());
        }
    }

    /** Starts the next leg along the route; false if the route has no reachable leg left. */
    private boolean followRoute() {
        // progress along the route only moves forward: the nearest point ahead of where we were
        int nearest = routeIndex;
        double nd = Double.MAX_VALUE;
        for (int i = routeIndex; i < Math.min(route.size(), routeIndex + 16); i++) {
            double d = distSqr(route.get(i));
            if (d < nd) {
                nd = d;
                nearest = i;
            }
        }
        routeIndex = nearest;
        int far = routeIndex;
        while (far + 1 < route.size() && distSqr(route.get(far + 1)) <= LEG * LEG) far++;
        if (far == routeIndex) {
            if (routeIndex + 1 >= route.size()) {   // end of the planned stretch: plan the next
                route = null;
                return false;
            }
            far = routeIndex + 1;
        }
        int tries = 0;
        for (int i = far; i > routeIndex && tries < MAX_PATH_TRIES; i -= Math.max(1, (far - routeIndex) / 3), tries++) {
            BlockPos target = route.get(i);
            Path path = papon.getNavigation().createPath(target, 1);
            if (path != null && path.canReach()) {
                papon.getNavigation().moveTo(path, SPEED);
                waypoint = target;
                failedPicks = 0;
                return true;
            }
        }
        // the route runs somewhere the navigator cannot get to: avoid it and plan again
        deadEnd(route.get(Math.min(routeIndex + 1, route.size() - 1)));
        route = null;
        replanIn = 0;
        return false;
    }

    private void deadEnd(BlockPos pos) {
        long k = RoutePlanner.cellKey(pos);
        if (deadEnds.add(k)) {
            deadEndOrder.addLast(k);
            while (deadEndOrder.size() > 64) deadEnds.remove(deadEndOrder.pollFirst());
        }
    }

    private double distSqr(BlockPos p) {
        double dx = p.getX() + 0.5 - papon.getX();
        double dz = p.getZ() + 0.5 - papon.getZ();
        return dx * dx + dz * dz;
    }

    private void pickWaypoint(ServerLevel level) {
        BlockPos dest = papon.getDestination();
        if (dest == null) return;
        double dx = dest.getX() + 0.5 - papon.getX();
        double dz = dest.getZ() + 0.5 - papon.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        List<Candidate> candidates = new ArrayList<>();
        if (dist <= REACH[0]) {
            BlockPos s = Terrain.surfaceAt(level, dest.getX(), dest.getZ());
            if (s != null) candidates.add(new Candidate(s, -1000));
        }
        double bearing = Math.atan2(dz, dx);
        for (int i = 0; i < REACH.length; i++) {
            double reach = Math.min(REACH[i], Math.max(4, dist));
            for (int off : FAN) {
                double a = bearing + off * Mth.DEG_TO_RAD;
                int x = Mth.floor(papon.getX() + Math.cos(a) * reach);
                int z = Mth.floor(papon.getZ() + Math.sin(a) * reach);
                // Rank by distance still to go from there; shorter legs cost a little extra
                // because each one is a stop-start risk.
                double left = Math.sqrt(JourneyPlanner.horizontalDistSqr(new BlockPos(x, 0, z), dest));
                Candidate c = evaluate(level, x, z, left + i * 3);
                if (c != null) candidates.add(c);
            }
        }
        candidates.sort(Comparator.comparingDouble(c -> c.score));

        int tries = 0;
        for (Candidate c : candidates) {
            if (tries++ >= MAX_PATH_TRIES) break;
            Path path = papon.getNavigation().createPath(c.pos, 1);
            if (path != null && path.canReach()) {
                papon.getNavigation().moveTo(path, SPEED);
                waypoint = c.pos;
                failedPicks = 0;
                return;
            }
            blacklist(c.pos);
        }
        if (papon.getNavigation().isDone()) waypoint = null;
        repickCooldown = REPICK_COOLDOWN;
        if (++failedPicks >= FAILED_PICK_LIMIT) {
            failedPicks = 0;
            if (dist < 10) papon.arriveNearby();
            else hopForward(level);
        }
    }

    @Nullable
    private Candidate evaluate(ServerLevel level, int x, int z, double baseScore) {
        BlockPos s = Terrain.surfaceAt(level, x, z);
        if (s == null || isBlacklisted(s)) return null;
        int dy = s.getY() - papon.getBlockY();
        if (Math.abs(dy) > 8 || Terrain.isLava(level, s) || Terrain.isUnderground(level, s)) return null;
        boolean water = Terrain.isWaterSurface(level, s);
        if (!water && !Terrain.isDryStandable(level, s)) return null;
        return new Candidate(s, baseScore + (water ? WATER_PENALTY : 0) + Math.abs(dy) * 2);
    }

    /** Last resort: puff over whatever is in the way onto dry ground a little further on. */
    private void hopForward(ServerLevel level) {
        BlockPos dest = papon.getDestination();
        if (dest == null) return;
        double bearing = Math.atan2(dest.getZ() + 0.5 - papon.getZ(), dest.getX() + 0.5 - papon.getX());
        for (int r : new int[]{16, 12, 8, 5}) {
            for (int off : FAN) {
                double a = bearing + off * Mth.DEG_TO_RAD;
                int x = Mth.floor(papon.getX() + Math.cos(a) * r);
                int z = Mth.floor(papon.getZ() + Math.sin(a) * r);
                BlockPos s = Terrain.surfaceAt(level, x, z);
                if (s != null && Terrain.isDryStandable(level, s) && !Terrain.isUnderground(level, s)
                        && Math.abs(s.getY() - papon.getBlockY()) <= 12) {
                    PaponMod.LOG.debug("Wandering Papon {} hopped from {} to {}", papon.getUUID(), papon.blockPosition(), s);
                    papon.hopTo(s);
                    blacklist.clear();
                    return;
                }
            }
        }
        papon.rescueToSurface(level);
    }

    private void blacklist(BlockPos pos) {
        blacklist.addLast(pos);
        while (blacklist.size() > 24) blacklist.pollFirst();
    }

    private boolean isBlacklisted(BlockPos pos) {
        for (BlockPos b : blacklist) {
            if (JourneyPlanner.horizontalDistSqr(b, pos) < 9) return true;
        }
        return false;
    }

    private boolean closeTo(BlockPos pos, double r) {
        double dx = pos.getX() + 0.5 - papon.getX();
        double dz = pos.getZ() + 0.5 - papon.getZ();
        return dx * dx + dz * dz < r * r;
    }

    private record Candidate(BlockPos pos, double score) {}
}
