package com.groid.papon.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/** Ground navigation on the surface-only evaluator. Floats across water; never uses doors. */
public class JourneyNavigation extends GroundPathNavigation {
    public JourneyNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanFloat(true);
        setCanOpenDoors(false);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new SurfaceNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(false);
        this.nodeEvaluator.setCanFloat(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
