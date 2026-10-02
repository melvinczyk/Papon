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
import java.util.List;

/**
 * Walks him toward the Taco Truck a short hop at a time. The site is hundreds of blocks off, far
 * beyond any path search, so each step picks a surface waypoint ~20 blocks ahead in a fan around
 * the true bearing and only commits to one the surface-only navigator can actually reach.
 *
 * Getting unstuck, in escalating order:
 *   1. a waypoint he cannot reach, or stops making progress toward, is blacklisted and another picked;
 *   2. after repeated failures he hops a few blocks forward to dry, open ground (a puff of dust);
 *   3. right next to the site, being stuck just counts as arriving.
 * Anything worse (in a cave, a wall, the void) is handled by the entity's own keepSafe check.
 */
public class JourneyGoal extends Goal {
    private static final int[] FAN = {0, 20, -20, 40, -40, 65, -65, 90, -90, 125, -125};
    private static final int[] REACH = {20, 13};
    private static final int MAX_PATH_TRIES = 4;
    private static final int PROGRESS_CHECK = 40;
    private static final double MIN_PROGRESS = 0.75;
    private static final int STUCK_LIMIT = 4;
    private static final int FAILED_PICK_LIMIT = 3;
    private static final double SPEED = 1.0;

    private final WanderingPaponEntity papon;
    private final Deque<BlockPos> blacklist = new ArrayDeque<>();
    @Nullable private BlockPos waypoint;
    private Vec3 lastCheckPos = Vec3.ZERO;
    private int ticksSinceCheck;
    private int stuckChecks;
    private int failedPicks;

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
        if (waypoint == null || papon.getNavigation().isDone() || closeTo(waypoint, 2.0)) {
            pickWaypoint(level);
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
        if (waypoint != null) blacklist(waypoint);
        waypoint = null;
        papon.getNavigation().stop();
        if (papon.remainingDistance() < 10) {
            papon.arriveNearby();
        } else if (stuckChecks >= STUCK_LIMIT) {
            stuckChecks = 0;
            hopForward(level);
        }
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
            if (s != null) candidates.add(new Candidate(s, -1));
        }
        double bearing = Math.atan2(dz, dx);
        for (int r : REACH) {
            double reach = Math.min(r, Math.max(4, dist));
            for (int off : FAN) {
                double a = bearing + off * Mth.DEG_TO_RAD;
                int x = Mth.floor(papon.getX() + Math.cos(a) * reach);
                int z = Mth.floor(papon.getZ() + Math.sin(a) * reach);
                Candidate c = evaluate(level, x, z, Math.abs(off) + (r == REACH[0] ? 0 : 6));
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
        waypoint = null;
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
        return new Candidate(s, baseScore + (water ? 60 : 0) + Math.abs(dy) * 3);
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
