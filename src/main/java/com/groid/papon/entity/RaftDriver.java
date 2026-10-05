package com.groid.papon.entity;

import com.groid.papon.journey.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Steers the llama raft. On water there is nothing worth pathfinding through, so he simply
 * paddles along the bearing to the Taco Truck, holding the entity at the water line, and:
 *   - lands as soon as the cell ahead is dry ground he can step onto (at most one block up);
 *   - steers around anything else in the way (cliffs, ice, boats, islands too steep to land on),
 *     trying the nearest open-water heading first and holding it for a few seconds;
 *   - if he still makes no headway, picks a new heading further round.
 */
final class RaftDriver {
    /** Blocks per tick while paddling, before the Jarrito multiplier. About his walking pace. */
    private static final double PADDLE_SPEED = 0.14;
    private static final double PROBE = 1.4;
    private static final int[] STEER = {35, -35, 70, -70, 110, -110, 150, -150};

    private final WanderingPaponEntity papon;
    private int steerOffset;
    private int steerTicks;
    private Vec3 lastCheck = Vec3.ZERO;
    private int checkTicks;

    RaftDriver(WanderingPaponEntity papon) { this.papon = papon; }

    void reset() {
        steerOffset = 0;
        steerTicks = 0;
        checkTicks = 0;
        lastCheck = papon.position();
    }

    /** Top of the water the entity is in or on; NaN if there is none. */
    static double waterSurface(Level level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos.x, pos.y + 0.2, pos.z);
        if (!level.getFluidState(p).is(FluidTags.WATER)) {
            p = p.below();
            if (!level.getFluidState(p).is(FluidTags.WATER)) return Double.NaN;
        }
        for (int i = 0; i < 4 && level.getFluidState(p.above()).is(FluidTags.WATER); i++) p = p.above();
        FluidState f = level.getFluidState(p);
        return p.getY() + f.getHeight(level, p);
    }

    /** Water at least this deep under him (counting the block he floats in). */
    static int waterDepth(Level level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos.x, pos.y + 0.2, pos.z);
        int depth = 0;
        while (depth < 4 && level.getFluidState(p).is(FluidTags.WATER)) {
            depth++;
            p = p.below();
        }
        return depth;
    }

    /**
     * One tick on the raft. {@code paddle} false just floats in place (boarding, lounging).
     * Returns a landing spot when he has reached a shore he can step onto, else null.
     */
    @Nullable
    BlockPos tick(boolean paddle) {
        Level level = papon.level();
        papon.getNavigation().stop();
        double surface = waterSurface(level, papon.position());
        if (Double.isNaN(surface)) {
            // Beached (or the water drained): get off where he is.
            return paddle ? papon.blockPosition() : null;
        }
        Vec3 v = papon.getDeltaMovement();
        double vy = Mth.clamp((surface - papon.getY()) * 0.4, -0.15, 0.15);
        if (!paddle) {
            papon.setDeltaMovement(v.x * 0.7, vy, v.z * 0.7);
            return null;
        }

        BlockPos dest = papon.getDestination();
        if (dest == null) return papon.blockPosition();
        double bearing = Math.atan2(dest.getZ() + 0.5 - papon.getZ(), dest.getX() + 0.5 - papon.getX());
        if (steerTicks > 0) {
            steerTicks--;
            bearing += steerOffset * Mth.DEG_TO_RAD;
        }

        int waterY = Mth.floor(surface - 0.5);
        Vec3 dir = new Vec3(Math.cos(bearing), 0, Math.sin(bearing));
        BlockPos ahead = BlockPos.containing(papon.getX() + dir.x * PROBE, waterY, papon.getZ() + dir.z * PROBE);
        if (!level.getFluidState(ahead).is(FluidTags.WATER)) {
            BlockPos land = landingAt(level, ahead, waterY);
            if (land != null) return land;
            steerAround(level, bearing - (steerTicks > 0 ? steerOffset * Mth.DEG_TO_RAD : 0), waterY);
        }

        if (++checkTicks >= 40) {
            checkTicks = 0;
            if (papon.position().distanceTo(lastCheck) < 0.4) {
                steerOffset = STEER[papon.getRandom().nextInt(STEER.length)];
                steerTicks = 60;
            }
            lastCheck = papon.position();
        }

        double speed = PADDLE_SPEED * papon.speedFactor();
        papon.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        float yaw = (float) (Math.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
        papon.setYRot(Mth.approachDegrees(papon.getYRot(), yaw, 6F));
        papon.yBodyRot = papon.getYRot();
        papon.yHeadRot = papon.getYRot();
        return null;
    }

    /** Dry ground in or just above the cell ahead, reachable by a one-block hop. */
    @Nullable
    private static BlockPos landingAt(Level level, BlockPos ahead, int waterY) {
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos feet = new BlockPos(ahead.getX(), waterY + dy, ahead.getZ());
            if (Terrain.isDryStandable(level, feet) && !Terrain.isUnderground(level, feet)) return feet;
        }
        return null;
    }

    private void steerAround(Level level, double baseBearing, int waterY) {
        for (int off : STEER) {
            double a = baseBearing + off * Mth.DEG_TO_RAD;
            boolean clear = true;
            for (int r = 2; r <= 4 && clear; r++) {
                BlockPos p = BlockPos.containing(papon.getX() + Math.cos(a) * r, waterY, papon.getZ() + Math.sin(a) * r);
                clear = level.getFluidState(p).is(FluidTags.WATER)
                        && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
            }
            if (clear) {
                steerOffset = off;
                steerTicks = 60;
                return;
            }
        }
        steerOffset = 180;
        steerTicks = 40;
    }
}
