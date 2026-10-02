package com.groid.papon.journey;

import com.groid.papon.PaponConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

/**
 * Picks where the "nearest Taco Truck" is. The truck does not exist yet; it is built when he
 * gets there (see TacoTruck). The site is chosen from the generator's biome map without loading
 * a single chunk: candidates on a ring are scored by how much ocean and river lies on the
 * straight line to them, and the driest line wins.
 */
public final class JourneyPlanner {
    private static final int CANDIDATES = 48;
    private static final int LINE_SAMPLES = 24;

    private JourneyPlanner() {}

    public static BlockPos pickDestination(ServerLevel level, BlockPos from, RandomSource random) {
        int min = PaponConfig.JOURNEY_MIN_DISTANCE.get();
        int max = Math.max(min, PaponConfig.JOURNEY_MAX_DISTANCE.get());

        if (PaponConfig.REUSE_EXISTING_TRUCKS.get()) {
            BlockPos nearest = null;
            double best = (double) max * max;
            for (BlockPos t : JourneyData.get(level).trucks()) {
                double d = horizontalDistSqr(from, t);
                if (d < best) {
                    best = d;
                    nearest = t;
                }
            }
            if (nearest != null) return nearest;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < CANDIDATES; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double dist = min + random.nextDouble() * (max - min);
            int x = from.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (!level.getWorldBorder().isWithinBounds(x, z)) continue;

            double score = random.nextDouble();
            for (int k = 1; k <= LINE_SAMPLES; k++) {
                double f = (double) k / LINE_SAMPLES;
                Holder<Biome> b = Terrain.generatedBiome(level, Mth.floor(Mth.lerp(f, from.getX(), x)),
                        Mth.floor(Mth.lerp(f, from.getZ(), z)));
                if (Terrain.isWetBiome(b)) score += 10;
                else if (b.is(BiomeTags.IS_MOUNTAIN)) score += 3;
            }
            if (!isInlandOnMap(level, x, z)) score += 1000;
            if (score < bestScore) {
                bestScore = score;
                best = new BlockPos(x, level.getSeaLevel(), z);
            }
        }
        if (best == null) {
            // Everything was outside the world border: walk inward instead.
            best = new BlockPos(Mth.floor(from.getX() * 0.5), level.getSeaLevel(), Mth.floor(from.getZ() * 0.5));
        }
        return best;
    }

    /** Land on the biome map, with no sea, river or beach within ~48 blocks in any direction. */
    public static boolean isInlandOnMap(ServerLevel level, int x, int z) {
        if (Terrain.isShoreBiome(Terrain.generatedBiome(level, x, z))) return false;
        for (int i = 0; i < 8; i++) {
            double a = i * Mth.TWO_PI / 8;
            for (int r : new int[]{24, 48}) {
                if (Terrain.isShoreBiome(Terrain.generatedBiome(level, x + (int) (Math.cos(a) * r),
                        z + (int) (Math.sin(a) * r)))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Nearest inland ground to {@code around} on the biome map, searching outward ring by ring;
     * among equally near rings it prefers the side facing {@code from} (where he is coming from).
     */
    public static BlockPos nearestInland(ServerLevel level, BlockPos around, BlockPos from) {
        double toward = Math.atan2(from.getZ() - around.getZ(), from.getX() - around.getX());
        for (int r = 32; r <= 1024; r += 32) {
            int steps = Math.max(16, r / 8);
            for (int k = 0; k < steps; k++) {
                // alternate either side of the "toward him" bearing, nearest angle first
                int n = (k + 1) / 2 * (k % 2 == 0 ? 1 : -1);
                double a = toward + n * Mth.TWO_PI / steps;
                int x = around.getX() + (int) Math.round(Math.cos(a) * r);
                int z = around.getZ() + (int) Math.round(Math.sin(a) * r);
                if (level.getWorldBorder().isWithinBounds(x, z) && isInlandOnMap(level, x, z)) {
                    return new BlockPos(x, level.getSeaLevel(), z);
                }
            }
        }
        return from;
    }

    public static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
