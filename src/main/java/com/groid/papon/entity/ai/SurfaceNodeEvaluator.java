package com.groid.papon.entity.ai;

import com.groid.papon.journey.Terrain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Walk evaluator that refuses every node underground, so no path ever leads into a cave or
 * tunnel. Ravines and cliffs are covered separately by the mob's tiny max fall distance: the
 * floor of an open ravine is "surface", but he can never step down into it.
 */
public class SurfaceNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z, mob);
        if (type == BlockPathTypes.BLOCKED || mob == null) {
            return type;
        }
        return Terrain.isUnderground(mob.level(), x, y, z) ? BlockPathTypes.BLOCKED : type;
    }
}
