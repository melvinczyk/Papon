package com.groid.papon.entity.ai;

import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/**
 * Ground navigation on the surface-only evaluator. Floats across water; never uses doors.
 *
 * Path following is made speed-aware: vanilla only counts a node as reached within ~half a block,
 * so a mob moving fast (his Jarrito run) overshoots nodes and turns back for them, and a fresh path
 * often starts with a node just behind him. Here a node also counts as reached once he is within
 * a couple of ticks' travel of it, or once he is already past it toward the node after.
 */
public class JourneyNavigation extends GroundPathNavigation {
    public JourneyNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanFloat(true);
        setCanOpenDoors(false);
    }

    @Override
    protected void followThePath() {
        super.followThePath();
        if (path == null) return;
        double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedModifier;
        double reach = Math.max(0.75, speed * 3.0);
        while (!path.isDone()) {
            Vec3i n = path.getNextNodePos();
            double nx = n.getX() + 0.5 - mob.getX(), nz = n.getZ() + 0.5 - mob.getZ();
            if (Math.abs(n.getY() - mob.getY()) >= 1.5) break;
            if (nx * nx + nz * nz < reach * reach) {
                path.advance();
                continue;
            }
            int i = path.getNextNodeIndex();
            if (i + 1 >= path.getNodeCount() || nx * nx + nz * nz > 9) break;
            // already past this node toward the next one?
            Vec3i after = path.getNodePos(i + 1);
            double ax = after.getX() - n.getX(), az = after.getZ() - n.getZ();
            if (-nx * ax - nz * az > 0) {
                path.advance();
                continue;
            }
            break;
        }
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new SurfaceNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(false);
        this.nodeEvaluator.setCanFloat(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
