package com.groid.papon.journey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Terrain questions the journey asks. Everything that reads blocks first checks the chunk is
 * loaded, because a heightmap or light lookup on an unloaded chunk would load it synchronously.
 */
public final class Terrain {
    /** The Taco Truck site keeps at least this many blocks between itself and any water. */
    public static final int INLAND_RADIUS = 12;
    /** Sky light below this, with something overhead, counts as being in a cave. */
    private static final int CAVE_SKY_LIGHT = 8;
    /** Deeper than this below the column's top counts as underground whatever the light says. */
    private static final int MAX_COVER = 8;

    private Terrain() {}

    public static boolean isUnderground(Level level, int x, int y, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) { return true; }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y >= top - 1) { return false; }
        return y < top - MAX_COVER || level.getBrightness(LightLayer.SKY, new BlockPos(x, y, z)) < CAVE_SKY_LIGHT;
    }

    public static boolean isUnderground(Level level, BlockPos pos) {
        return isUnderground(level, pos.getX(), pos.getY(), pos.getZ());
    }

    /** First open block above the top solid-or-fluid, non-leaf block of the column; null if not loaded. */
    @Nullable
    public static BlockPos surfaceAt(Level level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) { return null; }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Dry ground with two blocks of headroom. Rejects leaves, logs (tree tops) and anything wet or hot. */
    public static boolean isDryStandable(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP)) return false;
        if (ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS) || ground.is(BlockTags.FIRE)) return false;
        if (!level.getFluidState(below).isEmpty() || !level.getFluidState(feet).isEmpty()) return false;
        if (ground.getBlock() == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** The column's surface is the top of a body of water. */
    public static boolean isWaterSurface(Level level, BlockPos feet) {
        return level.getFluidState(feet.below()).is(FluidTags.WATER);
    }

    public static boolean isLava(Level level, BlockPos feet) {
        return level.getFluidState(feet.below()).is(FluidTags.LAVA) || level.getFluidState(feet).is(FluidTags.LAVA);
    }

    /**
     * Biome at a position from the generator's biome source: no chunk is loaded, so this can
     * look at land a thousand blocks away.
     */
    public static Holder<Biome> generatedBiome(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z),
                level.getChunkSource().randomState().sampler());
    }

    public static boolean isWetBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    /** Wet, or a beach: nowhere for a Taco Truck. */
    public static boolean isShoreBiome(Holder<Biome> biome) {
        return isWetBiome(biome) || biome.is(BiomeTags.IS_BEACH);
    }

    /**
     * No water surface and no beach within {@code radius} of the spot (sampled every 3 blocks over
     * the loaded surface). Unloaded columns count as not inland, so this never guesses.
     */
    public static boolean isInland(Level level, BlockPos spot, int radius) {
        if (level.getBiome(spot).is(BiomeTags.IS_BEACH)) return false;
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos s = surfaceAt(level, spot.getX() + dx, spot.getZ() + dz);
                if (s == null || isWaterSurface(level, s) || isLava(level, s)) return false;
            }
        }
        return true;
    }

    /** Nearest flat, dry, open-sky spot to (x, z) that is also inland; null if none within radius. */
    @Nullable
    public static BlockPos findInlandSpotNear(Level level, int x, int z, int radius) {
        for (int r = 0; r <= radius; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos s = surfaceAt(level, x + dx, z + dz);
                    if (s == null || !isDryStandable(level, s) || isUnderground(level, s)) continue;
                    if (!isFlat(level, s, 1) || !isInland(level, s, INLAND_RADIUS)) continue;
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Nearest dry, open-sky standing spot to (x, z) within radius, searching outward ring by ring.
     * Prefers flat ground: a spot whose 3x3 neighbourhood varies by more than maxStep is skipped.
     */
    @Nullable
    public static BlockPos findDrySpotNear(Level level, int x, int z, int radius, int maxStep) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos s = surfaceAt(level, x + dx, z + dz);
                    if (s == null || !isDryStandable(level, s) || isUnderground(level, s)) continue;
                    if (maxStep >= 0 && !isFlat(level, s, maxStep)) continue;
                    return s;
                }
            }
        }
        return null;
    }

    private static boolean isFlat(Level level, BlockPos s, int maxStep) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = surfaceAt(level, s.getX() + dx, s.getZ() + dz);
                if (n == null || Math.abs(n.getY() - s.getY()) > maxStep) return false;
            }
        }
        return true;
    }
}
