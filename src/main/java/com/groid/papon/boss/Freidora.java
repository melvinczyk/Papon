package com.groid.papon.boss;

import com.groid.papon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La Freidora: patches of the coliseum's sand give way to frying oil, in a pattern. 1.5 s of warning (the
 * sand cracks and glows: HotSandBlock), then 5.5 s of oil (FryingOilBlock), then the sand comes back. The
 * pattern always leaves a good share of the sand standing, and Chef Papon fights on round it.
 *   RINGS    alternate bands of the ellipse (3 blocks)      HALVES   one side of the sand
 *   CHECKER  a checkerboard of 5-block squares              CROSS    a cross through the middle and the rim
 *   HUNT     a pool under each of you
 */
public final class Freidora {
    public enum Pattern { RINGS, HALVES, CHECKER, CROSS, HUNT }

    public static final int WARN = 30, OPEN = 110;

    private final Map<Long, BlockState> original = new HashMap<>();
    private final Vec3 center;
    private int t;

    private Freidora(Vec3 center) {
        this.center = center;
    }

    /** Opens a fryer in {@code arena}'s sand in {@code p}'s shape (HUNT: round each of {@code players}). */
    public static Freidora start(ServerLevel level, Coliseum arena, Pattern p, List<Vec3> players, RandomSource rng) {
        Freidora f = new Freidora(arena.centerVec());
        int phase = rng.nextInt(2);
        boolean onX = rng.nextBoolean();
        int shift = rng.nextInt(5);
        BlockState hot = ModBlocks.ARENA_HOT_SAND.get().defaultBlockState();
        BlockPos c = arena.center;
        for (int x = -Coliseum.A; x <= Coliseum.A; x++) {
            for (int z = -Coliseum.B; z <= Coliseum.B; z++) {
                double d = Coliseum.offset(x, z);
                if (d > -1.5) continue;                         // the kerb stays
                boolean open = switch (p) {
                    case RINGS -> Math.floorMod((int) Math.floor(-d / 3.0), 2) == phase;
                    case HALVES -> (onX ? x : z) * (phase == 0 ? 1 : -1) > 1;
                    case CHECKER -> Math.floorMod(Math.floorDiv(x + shift, 5) + Math.floorDiv(z + shift, 5), 2) == phase;
                    case CROSS -> Math.abs(x) <= 2 || Math.abs(z) <= 2 || d > -5;
                    case HUNT -> near(players, c.getX() + 0.5 + x, c.getZ() + 0.5 + z);
                };
                if (!open) continue;
                BlockPos at = c.offset(x, -1, z);
                BlockState was = level.getBlockState(at);
                if (!ModBlocks.isArena(was) || was.is(ModBlocks.ARENA_BARRIER.get())) continue;
                f.original.put(at.asLong(), was);
                level.setBlock(at, hot, Block.UPDATE_CLIENTS);
            }
        }
        level.playSound(null, f.center.x, f.center.y, f.center.z, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 3.0F, 0.6F);
        level.playSound(null, f.center.x, f.center.y, f.center.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.0F, 0.5F);
        return f;
    }

    private static boolean near(List<Vec3> players, double x, double z) {
        for (Vec3 v : players) if (Mth.square(v.x - x) + Mth.square(v.z - z) < 16) return true;
        return false;
    }

    /** One tick; false once the sand is back. */
    public boolean tick(ServerLevel level) {
        t++;
        if (t == WARN) {
            BlockState oil = ModBlocks.ARENA_FRYING_OIL.get().defaultBlockState();
            for (long p : original.keySet()) level.setBlock(BlockPos.of(p), oil, Block.UPDATE_CLIENTS);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 3.0F, 0.5F);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 3.0F, 0.6F);
        }
        if (t > WARN && t % 30 == 0) level.playSound(null, center.x, center.y, center.z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0F, 1.3F);
        if (t >= WARN + OPEN) {
            close(level);
            return false;
        }
        return true;
    }

    /** The sand back where it was (also when the fight ends mid-fry). */
    public void close(ServerLevel level) {
        for (var e : original.entrySet()) level.setBlock(BlockPos.of(e.getKey()), e.getValue(), Block.UPDATE_CLIENTS);
        original.clear();
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 2.0F, 0.7F);
    }

    public boolean opening() {
        return t < WARN;
    }
}
