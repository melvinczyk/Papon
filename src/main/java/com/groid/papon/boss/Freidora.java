package com.groid.papon.boss;

import com.groid.papon.fx.Fx;
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
 * La Freidora: the coliseum's sand gives way to frying oil, kerb to kerb, in a pattern. 1.5 s of warning (the
 * sand cracks and glows: HotSandBlock, steam off it right across the bowl), then 10 s of oil two blocks deep
 * (FryingOilBlock), then the sand comes back. Every pool is at least 2 blocks wide everywhere (no 1-block slivers to
 * trip into). Every pattern runs the whole sand, edge to edge (2026-10-05, the user: the fryer "is supposed to be an
 * arena-wide attack", but its HUNT pattern, a pool under each player, read as a few blocks round you); it always leaves
 * sand standing somewhere to get to, and Chef Papon fights on round it.
 *   RINGS    alternate bands of the ellipse (3 blocks)      HALVES   one side of the sand
 *   CHECKER  a checkerboard of 5-block squares              CROSS    a cross through the middle and the rim
 *   ISLANDS  all of it, but for little islands of sand scattered over it, none under you (2 s to get to one)
 */
public final class Freidora {
    public enum Pattern { RINGS, HALVES, CHECKER, CROSS, ISLANDS }

    public static final int WARN = 30, OPEN = 200;

    private final Map<Long, BlockState> original = new HashMap<>();
    /** The tops of the pools (for the steam over the whole of it: blocks only bubble within 32 blocks of you). */
    private final java.util.ArrayList<BlockPos> tops = new java.util.ArrayList<>();
    private final Vec3 center;
    private final int warn;
    private int t;

    private Freidora(Vec3 center, int warn) {
        this.center = center;
        this.warn = warn;
    }

    /** Opens a fryer in {@code arena}'s sand in {@code p}'s shape (ISLANDS: none of them near {@code players}). */
    public static Freidora start(ServerLevel level, Coliseum arena, Pattern p, List<Vec3> players, RandomSource rng) {
        Freidora f = new Freidora(arena.centerVec(), p == Pattern.ISLANDS ? WARN + 10 : WARN);
        int phase = rng.nextInt(2);
        boolean onX = rng.nextBoolean();
        int shift = rng.nextInt(5);
        BlockState hot = ModBlocks.ARENA_HOT_SAND.get().defaultBlockState();
        BlockPos c = arena.center;
        int A = arena.a, B = arena.b;
        int w = 2 * A + 1, h = 2 * B + 1;
        List<Vec3> islands = p == Pattern.ISLANDS ? islands(arena, players, rng) : List.of();
        boolean[][] want = new boolean[w][h];
        for (int x = -A; x <= A; x++) {
            for (int z = -B; z <= B; z++) {
                double d = Coliseum.offset(x, z, A, B);
                if (d > -1.5) continue;                         // the kerb stays
                want[x + A][z + B] = switch (p) {
                    case RINGS -> Math.floorMod((int) Math.floor(-d / 3.0), 2) == phase;
                    case HALVES -> (onX ? x : z) * (phase == 0 ? 1 : -1) > 1;
                    case CHECKER -> Math.floorMod(Math.floorDiv(x + shift, 5) + Math.floorDiv(z + shift, 5), 2) == phase;
                    case CROSS -> Math.abs(x) <= 2 || Math.abs(z) <= 2 || d > -5;
                    case ISLANDS -> !near(islands, x, z, 2.6);
                };
            }
        }
        // only cells inside a fully open 2x2 square: every pool is at least 2 blocks wide everywhere
        for (int x = -A; x <= A; x++) {
            for (int z = -B; z <= B; z++) {
                if (!square(want, x + A, z + B)) continue;
                BlockPos at = c.offset(x, -1, z);
                BlockState was = level.getBlockState(at);
                if (!ModBlocks.isArena(was) || was.is(ModBlocks.ARENA_BARRIER.get())) continue;
                f.original.put(at.asLong(), was);
                f.tops.add(at);
                level.setBlock(at, hot, Block.UPDATE_CLIENTS);
                BlockPos under = at.below();                    // two deep: the vat goes on under the sand
                f.original.put(under.asLong(), level.getBlockState(under));     // oil too once the warning is over
            }
        }
        level.playSound(null, f.center.x, f.center.y, f.center.z, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 3.0F, 0.6F);
        level.playSound(null, f.center.x, f.center.y, f.center.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.0F, 0.5F);
        // the islands marked out: a gold ring on each (the sand that will stay) while the rest glows
        for (Vec3 i : islands) Fx.zone(level, new Vec3(c.getX() + 0.5 + i.x, c.getY(), c.getZ() + 0.5 + i.z), 2.6F, Fx.POTATO, f.warn + OPEN, 0.5F);
        f.steam(level, 40, true);
        return f;
    }

    /**
     * ISLANDS: where the sand stays, relative to the middle: a jittered grid ~11 blocks apart over the whole floor, so
     * one is always a sprint away, but none within 6 blocks of anyone (you have to move) and none under the kerb.
     */
    private static List<Vec3> islands(Coliseum arena, List<Vec3> players, RandomSource rng) {
        java.util.ArrayList<Vec3> out = new java.util.ArrayList<>();
        double ox = rng.nextDouble() * 11, oz = rng.nextDouble() * 11;
        for (double x = -arena.a + ox; x <= arena.a; x += 11) {
            for (double z = -arena.b + oz; z <= arena.b; z += 11) {
                double jx = x + (rng.nextDouble() - 0.5) * 4, jz = z + (rng.nextDouble() - 0.5) * 4;
                if (Coliseum.offset(jx, jz, arena.a, arena.b) > -4.5) continue;
                Vec3 world = arena.centerVec().add(jx, 0, jz);
                boolean close = false;
                for (Vec3 v : players) close |= Mth.square(v.x - world.x) + Mth.square(v.z - world.z) < 36;
                if (!close) out.add(new Vec3(jx, 0, jz));
            }
        }
        return out;
    }

    /** Steam and spatter off {@code n} of the pools anywhere in the bowl (sent to everyone, however far). */
    private void steam(ServerLevel level, int n, boolean warning) {
        if (tops.isEmpty()) return;
        for (int i = 0; i < n; i++) {
            BlockPos b = tops.get(level.random.nextInt(tops.size()));
            Vec3 at = new Vec3(b.getX() + 0.5, b.getY() + 1.05, b.getZ() + 0.5);
            if (warning) Fx.smoke(level, at, 1, 0.4, 1.4F, 0xE8DCC8);
            else {
                Fx.smoke(level, at.add(0, 0.2, 0), 1, 0.4, 1.8F, 0xF0E6D2);
                Fx.spark(level, at, 3, 0.4, 0.25F, Fx.GREASE);
            }
        }
    }

    /** Whether cell (i, j) is in some 2x2 square of open cells. */
    private static boolean square(boolean[][] g, int i, int j) {
        if (!g[i][j]) return false;
        for (int di = -1; di <= 0; di++) {
            for (int dj = -1; dj <= 0; dj++) {
                int a = i + di, b = j + dj;
                if (a < 0 || b < 0 || a + 1 >= g.length || b + 1 >= g[0].length) continue;
                if (g[a][b] && g[a + 1][b] && g[a][b + 1] && g[a + 1][b + 1]) return true;
            }
        }
        return false;
    }

    private static boolean near(List<Vec3> points, double x, double z, double r) {
        for (Vec3 v : points) if (Mth.square(v.x - x) + Mth.square(v.z - z) < r * r) return true;
        return false;
    }

    /** One tick; false once the sand is back. */
    public boolean tick(ServerLevel level) {
        t++;
        if (t < warn && t % 6 == 0) steam(level, 18, true);
        if (t > warn && t % 4 == 0) steam(level, 10, false);
        if (t == warn) {
            BlockState oil = ModBlocks.ARENA_FRYING_OIL.get().defaultBlockState();
            for (long p : original.keySet()) level.setBlock(BlockPos.of(p), oil, Block.UPDATE_CLIENTS);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 3.0F, 0.5F);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 3.0F, 0.6F);
        }
        if (t == warn) steam(level, 60, false);
        if (t > warn && t % 30 == 0) level.playSound(null, center.x, center.y, center.z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0F, 1.3F);
        if (t >= warn + OPEN) {
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

    public boolean opening() { return t < warn; }
}
