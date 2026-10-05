package com.groid.papon.boss;

import com.groid.papon.PaponMod;
import com.groid.papon.block.ArenaFlagBlockEntity;
import com.groid.papon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * El Coliseo Papa: Chef Papon's own arena, where he fights his challengers. A Roman coliseum in
 * potato travertine, built high in the sky straight above the fight (the second phase's "pocket
 * dimension"); everyone in the fight is pulled up into it at the end of his scream.
 *
 * <pre>
 *  the arena      an ellipse of raked sand, {@link #a} by {@link #b} blocks (semi-axes, long axis
 *                 east-west), a golden Papon mosaic in the middle, the gates at the east and west ends
 *  the podium     a wall 6 high all round, tricolour drapes on it, braziers along its top, and over
 *                 it an invisible railing only players bump into (the stands are the fans')
 *  the stands     three tiers of burlap-cushioned seats with aisles and walkways, the fans (drawn by
 *                 ColiseumCrowdRenderer, not blocks); his box over the band stage on the north side;
 *                 golden statues of him over both gates
 *  the facade     three storeys of arches between engaged columns, flags in some of them, an attic
 *                 with windows and braziers on top
 *  the band stand a half-moon stage against the north wall, where the mariachis sleep
 * </pre>
 *
 * El Coliseo de los Muertos (phase four, {@link #dead}): the same bowl twice the size each way, turned to the Day of
 * the Dead: night-dark stone, bone columns, sugar skulls for keystones, a great calavera laid in the floor, candles and
 * spirit-fire braziers everywhere, an ofrenda behind the band, and blue ectoplasm running through cracks in the floor
 * ({@link #cracks}). The old coliseum becomes it in place ({@link #swapFrom}), behind the moon's blue-out.
 *
 * Every ring is an offset of the arena's ellipse ({@link #edge}: how far outside the sand a
 * point is), so the rows keep a constant depth all the way round. The geometry is a pure function
 * of the centre, the size and the style, so the build, the teardown (which removes only the planned
 * positions that still hold an arena block) and the crowd's seats ({@link #seats}, on the client too) all agree.
 *
 * It is built only into air (nothing anyone made is touched) a few hundred blocks a tick while the
 * cutscene runs, and taken down when the fight ends (his death, his removal, or nobody left in it):
 * players, the van and anything dropped in it go back down to where they were relative to the
 * fight. Its blocks cannot be broken.
 */
public final class Coliseum {
    /** El Coliseo Papa's sand: its semi-axes (east-west, north-south). */
    public static final int A = 29;
    public static final int B = 21;
    /** El Coliseo de los Muertos is this many times bigger each way. */
    public static final int DEAD_SCALE = 2;
    public static final int PODIUM = 6;
    /** Above the floor: the facade's top. */
    public static final int HEIGHT = 34;
    /** Offsets (blocks outside the sand) where things are. */
    private static final double CAVEA = 2, GALLERY = 18, WALL_IN = 19, WALL_OUT = 20, OUTSIDE = 22;
    private static final int PER_TICK = 3200;          // up behind the white-out of his scream, ~13 ticks
    private static final int AISLES = 16;

    /** The centre of the floor's top face (where you stand). */
    public final BlockPos center;
    /** The middle of the fight on the ground, where everyone goes back to. */
    public final Vec3 ground;
    /** This one's sand: its semi-axes. */
    public final int a, b;
    /** El Coliseo de los Muertos. */
    public final boolean dead;
    @Nullable private List<long[]> pending;
    private boolean started;
    /** Rebuilding over the old coliseum (phase four): arena blocks in the way are replaced, not skipped. */
    private boolean swapping;
    @Nullable private Map<Long, Integer> cracks;

    private Coliseum(BlockPos center, Vec3 ground, int a, int b, boolean dead) {
        this.center = center;
        this.ground = ground;
        this.a = a;
        this.b = b;
        this.dead = dead;
    }

    /** A coliseum over {@code arena}, as high as it fits under the build limit; null if the sky has no room. */
    @Nullable
    public static Coliseum plan(ServerLevel level, Vec3 arena) {
        int floor = floorFor(level, arena, A);
        if (floor == Integer.MIN_VALUE) return null;
        return new Coliseum(new BlockPos(Mth.floor(arena.x), floor, Mth.floor(arena.z)), arena, A, B, false);
    }

    /** The floor's height over {@code arena} for a coliseum this wide, or MIN_VALUE if the terrain is too high under it. */
    private static int floorFor(ServerLevel level, Vec3 arena, int a) {
        int reach = (int) (a + OUTSIDE + 1);
        int highest = level.getMinBuildHeight();
        for (int dx = -reach; dx <= reach; dx += 8) {
            for (int dz = -reach; dz <= reach; dz += 8) {
                highest = Math.max(highest, terrainTop(level, Mth.floor(arena.x) + dx, Mth.floor(arena.z) + dz));
            }
        }
        int floor = level.getMaxBuildHeight() - HEIGHT - 6;
        return floor < highest + 12 ? Integer.MIN_VALUE : floor;
    }

    /**
     * The top of the ground in a column, looking through our own blocks: the coliseum already standing there (when the
     * dead's is planned over it) is in the heightmap, and would always leave no room.
     */
    private static int terrainTop(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        while (p.getY() > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !ModBlocks.isArena(s)) break;
            p.move(Direction.DOWN);
        }
        return p.getY() + 1;
    }

    /**
     * El Coliseo de los Muertos where this one stands: twice the size, same floor (it is built over this one); the same
     * size if the terrain under the bigger one comes up too close.
     */
    public Coliseum dead(ServerLevel level) {
        boolean room = floorFor(level, ground, A * DEAD_SCALE) != Integer.MIN_VALUE;
        int k = room ? DEAD_SCALE : 1;
        return new Coliseum(center, ground, A * k, B * k, true);
    }

    public double floorY() {
        return center.getY();
    }

    public Vec3 centerVec() {
        return Vec3.atBottomCenterOf(center);
    }

    /** How many times El Coliseo Papa's size this one is. */
    public int scale() {
        return a / A;
    }

    // ------------------------------------------------------------------ the shape

    /**
     * How far outside the sand's edge a point is (negative inside), measured as the offset ellipse
     * through it: the d for which (x/(a+d))^2 + (z/(b+d))^2 = 1. El Coliseo Papa's size.
     */
    public static double offset(double x, double z) {
        return offset(x, z, A, B);
    }

    public static double offset(double x, double z, int a, int b) {
        if (Math.abs(x) < 1e-6 && Math.abs(z) < 1e-6) return -b;
        double lo = -b + 1e-4, hi = 80;
        for (int i = 0; i < 26; i++) {
            double d = (lo + hi) * 0.5;
            double u = x / (a + d), v = z / (b + d);
            if (u * u + v * v > 1) lo = d;
            else hi = d;
        }
        return (lo + hi) * 0.5;
    }

    /** {@link #offset} for this coliseum's size. */
    public double edge(double x, double z) {
        return offset(x, z, a, b);
    }

    /** The angle round the ellipse (0 = east, PI/2 = south) of a point at offset d. */
    private static double angle(double x, double z, double d, int a, int b) {
        return Math.atan2(z / (b + d), x / (a + d));
    }

    /** Blocks of arc per radian at that angle on the ring at offset d. */
    private static double arcRate(double phi, double d, int a, int b) {
        double aa = a + d, bb = b + d;
        return Math.sqrt(Math.pow(aa * Math.sin(phi), 2) + Math.pow(bb * Math.cos(phi), 2));
    }

    /** Height of a cavea row's seat block above the floor; -1 for walkways' marker rows. */
    public static int rowHeight(int r) {
        if (r <= 4) return PODIUM + r;               // the first tier: 6..10
        if (r == 5) return 11;                       // a walkway
        if (r <= 10) return 13 + (r - 6);            // the second tier, over a two-block wall: 13..17
        if (r == 11) return 18;                      // a walkway
        return 20 + (r - 12);                        // the third tier: 20..23
    }

    public static boolean isWalkway(int r) {
        return r == 5 || r == 11;
    }

    public static final int ROWS = 16;

    private static boolean inAisle(double phi, double d, int a, int b) {
        int aisles = AISLES * a / A;
        double step = Math.PI * 2 / aisles;
        double k = Math.floorMod((long) Math.floor((phi - step / 2) / step), aisles);
        double c = step / 2 + k * step;
        double off = Math.abs(Mth.wrapDegrees((phi - c) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
        off = Math.min(off, Math.abs(Mth.wrapDegrees((phi - c - step) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD);
        return off * arcRate(phi, d, a, b) < 0.8;
    }

    /** His box in the stands, over the band stage (north side, the first tier). */
    private static boolean inBox(double x, double z, int r) {
        return z < 0 && Math.abs(x) < 6.5 && r <= 4;
    }

    /** The statues over the gates (both ends of the long axis). */
    private static boolean inStatue(double x, double z, double d, int a) {
        return Math.abs(z) < 4.5 && Math.abs(x) > a && d < 8;
    }

    // ------------------------------------------------------------------ the materials

    /** What it is built of: El Coliseo Papa's potato travertine, or the dead's night stone and bone. */
    private Supplier<Block> stone() {
        return dead ? ModBlocks.MUERTOS_STONE::get : ModBlocks.ARENA_STONE::get;
    }

    private Supplier<Block> bricks() {
        return dead ? ModBlocks.MUERTOS_BRICKS::get : ModBlocks.ARENA_BRICKS::get;
    }

    private Supplier<Block> darkBlock() {
        return dead ? ModBlocks.MUERTOS_DARK::get : ModBlocks.ARENA_DARK::get;
    }

    private Supplier<Block> brazier() {
        return dead ? ModBlocks.SPIRIT_BRAZIER::get : ModBlocks.ARENA_BRAZIER::get;
    }

    private Supplier<Block> seat() {
        return dead ? ModBlocks.MUERTOS_SEAT::get : ModBlocks.ARENA_SEAT::get;
    }

    private BlockState column(Direction.Axis axis) {
        return (dead ? ModBlocks.BONE_COLUMN.get() : ModBlocks.ARENA_COLUMN.get()).defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** A sugar skull facing {@code facing} (the dead's keystones, gates, ofrenda). */
    private static BlockState skull(Direction facing) {
        return ModBlocks.MUERTOS_SKULL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /** The horizontal direction nearest to the angle {@code rad} (0 = east, PI/2 = south). */
    private static Direction toward(double rad) {
        return Direction.fromYRot(rad * Mth.RAD_TO_DEG - 90);
    }

    // ------------------------------------------------------------------ building

    private final class Plan {
        final List<long[]> out = new ArrayList<>();
        /** One flag per key (a stretch of the podium, an arch): the best cell for it so far. */
        final java.util.Map<Long, double[]> flags = new java.util.HashMap<>();

        void put(int x, int y, int z, BlockState s) {
            out.add(new long[]{center.offset(x, y, z).asLong(), Block.getId(s)});
        }

        void put(int x, int y, int z, Supplier<Block> b) {
            put(x, y, z, b.get().defaultBlockState());
        }

        /** A flag block (with its kind and the way its cloth faces) for the block entity once it is placed. */
        void flag(int x, int y, int z, int kind, double normal) {
            special(x, y, z, ModBlocks.ARENA_FLAG.get().defaultBlockState(), kind | (dead ? ArenaFlagBlockEntity.DEAD : 0), normal);
        }

        /** A block whose block entity is told something once it is placed (a flag's kind and facing, a lamp's aim). */
        void special(int x, int y, int z, BlockState s, int kind, double angle) {
            out.add(new long[]{center.offset(x, y, z).asLong(), Block.getId(s), kind, Double.doubleToLongBits(angle)});
        }

        /** A candidate cell for flag {@code key}; the one with the lowest {@code score} gets it. */
        void flagCandidate(long key, int x, int y, int z, int kind, double normal, double score) {
            double[] best = flags.get(key);
            if (best == null || score < best[0]) flags.put(key, new double[]{score, x, y, z, kind, normal});
        }

        void placeFlags() {
            for (double[] f : flags.values()) flag((int) f[1], (int) f[2], (int) f[3], (int) f[4], f[5]);
        }
    }

    /** Every block of the coliseum, in build order (the floor and the wall first). */
    private List<long[]> blocks() {
        Plan p = new Plan();
        RandomSource rng = RandomSource.create(center.asLong());
        int reach = (int) Math.ceil(a + OUTSIDE);
        int reachZ = (int) Math.ceil(b + OUTSIDE);
        // the facade's bays, by arc length round the outer wall
        double outer = WALL_OUT + 0.5;
        int samples = 4096;
        double[] arc = new double[samples + 1];
        for (int i = 1; i <= samples; i++) {
            double phi = -Math.PI + (i - 0.5) * Math.PI * 2 / samples;
            arc[i] = arc[i - 1] + arcRate(phi, outer, a, b) * Math.PI * 2 / samples;
        }
        int bays = (int) Math.round(arc[samples] / 5.0);
        double bayW = arc[samples] / bays;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reachZ; z <= reachZ; z++) {
                double d = edge(x, z);
                if (d >= OUTSIDE) continue;
                double phi = angle(x, z, d, a, b);
                if (d < 0) {
                    floor(p, x, z, d);
                } else if (d < CAVEA) {
                    podium(p, x, z, d, phi);
                } else if (d < GALLERY) {
                    stands(p, x, z, d, phi);
                } else if (d < WALL_IN) {
                    for (int y = 22; y <= 24; y++) p.put(x, y, z, y == 24 ? stone() : bricks());
                } else {
                    int i = Mth.clamp((int) ((phi + Math.PI) / (Math.PI * 2) * samples), 0, samples - 1);
                    double s = arc[i] / bayW;
                    int bay = (int) Math.floor(s);
                    double u = (s - bay) * bayW;
                    facade(p, x, z, d, u, bayW, bay, phi, rng);
                }
            }
        }
        statues(p);
        box(p);
        stage(p);
        // the flags replace the ashlar of the cells they were given
        java.util.Set<Long> flagged = new java.util.HashSet<>();
        for (double[] f : p.flags.values()) flagged.add(center.offset((int) f[1], (int) f[2], (int) f[3]).asLong());
        p.out.removeIf(bl -> flagged.contains(bl[0]));
        p.placeFlags();
        // the floor and the wall go up first, so it is whole by the time anyone stands in it
        List<long[]> ordered = p.out;
        ordered.sort((u, v) -> Integer.compare(BlockPos.of(v[0]).getY(), BlockPos.of(u[0]).getY()));
        return ordered;
    }

    private void floor(Plan p, int x, int z, double d) {
        if (dead) {
            p.put(x, -1, z, deadFloor(x, z, d));
            return;
        }
        double r = Math.sqrt(x * x + z * z);
        Supplier<Block> bl = ModBlocks.ARENA_SAND::get;
        if (d > -1) bl = ModBlocks.ARENA_BRICKS::get;                        // the kerb along the wall
        else if (Math.abs(d + 9) < 0.5) bl = ModBlocks.ARENA_STONE::get;      // a ring marked in the sand
        else if (r < 7.5) bl = mosaic(x, z, r);
        p.put(x, -1, z, bl);
    }

    /** The middle of the sand: a golden ring round a Papon's face, upright for whoever looks north. */
    private static Supplier<Block> mosaic(int x, int z, double r) {
        if (r >= 6.4) return ModBlocks.ARENA_GOLD::get;
        if (z == -3 && (Math.abs(x + 2.5) <= 1 || Math.abs(x - 2.5) <= 1)) return ModBlocks.ARENA_DARK::get;  // brows
        if (z == -1 && (x == -2 || x == 3)) return ModBlocks.ARENA_DARK::get;                                 // eyes
        if (z == -1 && (x == -3 || x == 2)) return ModBlocks.POTATO_FLESH::get;
        if (z == 3 && Math.abs(x + 0.5) <= 2.5) return ModBlocks.ARENA_DARK::get;                            // the mouth bar
        return (x * 7 + z * 3) % 11 == 0 ? ModBlocks.POTATO_EYE::get : ModBlocks.POTATO_SKIN::get;
    }

    /**
     * The dead's floor: night-stone tiles, a kerb of the dark bricks, a ring of bone set in it, the great sugar skull in
     * the middle ({@link #DEAD_SKULL}), and the cracks full of ectoplasm running out from it to the wall.
     */
    private Supplier<Block> deadFloor(int x, int z, double d) {
        if (d > -1) return ModBlocks.MUERTOS_BRICKS::get;
        Supplier<Block> m = deadMosaic(x, z);
        if (m != null) return m;
        if (cracks().containsKey(pack(x, z))) return ModBlocks.ECTOPLASM::get;
        if (Math.abs(d + b * 0.42) < 0.5) return ModBlocks.MUERTOS_BONE::get;          // a ring of bone, half way out
        return ModBlocks.MUERTOS_TILE::get;
    }

    @Nullable
    private static Supplier<Block> deadMosaic(int x, int z) {
        int r = DEAD_SKULL.length / 2;
        if (Math.abs(x) > r || Math.abs(z) > r) return null;
        return switch (DEAD_SKULL[z + r].charAt(x + r)) {
            case 'B' -> ModBlocks.MUERTOS_BONE::get;
            case 'K' -> ModBlocks.MUERTOS_DARK::get;
            case 'C' -> ModBlocks.ECTOPLASM::get;
            case 'P' -> ModBlocks.MUERTOS_PINK::get;
            case 'M' -> ModBlocks.MUERTOS_MARIGOLD::get;
            case 'V' -> ModBlocks.MUERTOS_PURPLE::get;
            case 'G' -> ModBlocks.ARENA_GOLD::get;
            default -> null;
        };
    }

    /** The great calavera in the middle of the dead's floor (tools: scratch skull.py), north at the top. */
    private static final String[] DEAD_SKULL = {
            ".......................................",
            "...............GGGGGGGGG...............",
            "............GGGGMMMPPPPGGGG............",
            "..........GGGPPPMMMPPPPMMMGGG..........",
            ".........GGMPPPPVVVVVVVMMMMPGG.........",
            ".......GGMMMMVVVVVVVVVVVVVPPPPGG.......",
            "......GGPMMVVVVVKKKKKKKVVVVVPPMGG......",
            ".....GGPPPVVVVKKBBBBBBBKKVVVVMMMGG.....",
            ".....GPPPVVVKKBBBBBMMBBBBKKVVVMMMG.....",
            "....GMMPVVVKBBBBBMBPBBBBBBBKVVVMPPG....",
            "...GGMMVVVKBBBBBBMPPPMBBBBBBKVVVPPGG...",
            "...GMMVVVVKBBCBBBBBPBMBBBCBBKVVVVPPG...",
            "..GGPMVVVKBBCBBBBBMMBBBBBBCBBKVVVPMGG..",
            "..GPPVVVVKBCBBBBBBBBBBBBBBBCBKVVVVMMG..",
            "..GPPVVVKBBCBBBBBBBBBBBBBBBCBBKVVVMMG..",
            ".GGPPVVVKBBBCPPPBBBBBBBPPPCBBBKVVVMMGG.",
            ".GMMVVVVKBBMMMPMMMBBBMMMPMMMBBKVVVVPPG.",
            ".GMMVVVVKBPPKKKKKPPBPPKKKKKPPBKVVVVPPG.",
            ".GMMVVVVKBPKKKCKKKPBPKKKCKKKPBKVVVVPPG.",
            ".GMMVVVVKBPKKCCCKKMBPKKCCCKKMBKVVVVMMG.",
            ".GPPVVVVVKMKKKCKKKMBMKKKCKKKMKVVVVVMMG.",
            ".GPPVVVVVKMMKKKKKMMBMMKKKKKMMKVVVVVMMG.",
            ".GPPVVVVVVKVPPMPPPBBBPPPMPPVKVVVVVVMMG.",
            ".GGMMVVVVVVBBMMMBKKKKKBMMMBBVVVVVVPPGG.",
            "..GMMVVVVVVVBBBBBBKKKBBBBBBVVVVVVVPPG..",
            "..GMMVVVVVVVKBBBBBBKBBBBBBKVVVVVVVPPG..",
            "..GGMPVVVVVVKBBBBBBBBBBBBBKVVVVVVMPGG..",
            "...GPPVVVVVVKKBKBKBKBKBKBKKVVVVVVMMG...",
            "...GGPPVVVVVKKKKKKKKKKKKKKKVVVVVMMGG...",
            "....GPPMVVVVVKBKBKBKBGBKBKVVVVVPMMG....",
            ".....GMMMVVVVVKBBBBBBBBBKVVVVVPPPG.....",
            ".....GGMMMVVVVVKBBBBBBBKVVVVVPPPGG.....",
            "......GGMPPVVVVVKKKKKKKVVVVVMMPGG......",
            ".......GGPPPPVVVVVVVVVVVVVMMMMGG.......",
            ".........GGPMMMMVVVVVVVPPPPMGG.........",
            "..........GGGMMMPPPPMMMPPPGGG..........",
            "............GGGGPPPPMMMGGGG............",
            "...............GGGGGGGGG...............",
            ".......................................",
    };

    public static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long k) {
        return (int) (k >> 32);
    }

    public static int unpackZ(long k) {
        return (int) k;
    }

    /**
     * The dead's floor cracks (relative x, z packed), each with how far along its crack it is (blocks from where it starts
     * at the skull): fourteen run out from the great calavera to the wall, wandering, branching; two broken rings cross
     * them. Ectoplasm runs in all of them (and Las Velas de Ánimas erupt along them in that order). Empty for El Coliseo Papa.
     */
    public Map<Long, Integer> cracks() {
        if (!dead) return Map.of();
        if (cracks != null) return cracks;
        Map<Long, Integer> out = new HashMap<>();
        RandomSource rng = RandomSource.create(center.asLong() ^ 0x5EED_DEADL);
        double start = DEAD_SKULL.length / 2.0 + 0.5;
        int mains = 14;
        for (int i = 0; i < mains; i++) {
            double ang = i * Math.PI * 2 / mains + (rng.nextDouble() - 0.5) * 0.3;
            crack(out, rng, Math.cos(ang) * start, Math.sin(ang) * start, ang, 0, 400, true);
        }
        // the rings: broken arcs part way out, wobbling
        for (double ring : new double[]{0.62, 0.3}) {
            double d = -b * ring;
            int n = 720;
            boolean on = true;
            int run = 0;
            for (int i = 0; i < n; i++) {
                double phi = i * Math.PI * 2 / n;
                if (--run <= 0) {
                    on = rng.nextFloat() < 0.68F;
                    run = 20 + rng.nextInt(50);
                }
                if (!on) continue;
                double w = 0.9 * Math.sin(phi * 7 + ring * 13) + 0.6 * Math.sin(phi * 17 + 2);
                double x = (a + d + w) * Math.cos(phi), z = (b + d + w) * Math.sin(phi);
                mark(out, (int) Math.round(x), (int) Math.round(z), (int) (b + d) + 8);
            }
        }
        cracks = out;
        return out;
    }

    /** One crack walking out from (x, z) heading {@code ang}, branching now and then, until the wall or {@code left} runs out. */
    private void crack(Map<Long, Integer> out, RandomSource rng, double x, double z, double ang, int step, int left, boolean main) {
        double heading = ang;
        for (int i = 0; i < left; i++) {
            x += Math.cos(ang);
            z += Math.sin(ang);
            ang += (rng.nextDouble() - 0.5) * 0.55 + (heading - ang) * 0.12;
            int cx = (int) Math.round(x), cz = (int) Math.round(z);
            if (edge(cx, cz) > -2.2) return;
            mark(out, cx, cz, step + i);
            if (main && i < 9) {
                // wide where it leaves the skull
                mark(out, (int) Math.round(x - Math.sin(ang)), (int) Math.round(z + Math.cos(ang)), step + i);
            }
            if (i > 4 && rng.nextFloat() < (main ? 0.07F : 0.03F)) {
                double turn = (0.55 + rng.nextDouble() * 0.5) * (rng.nextBoolean() ? 1 : -1);
                crack(out, rng, x, z, ang + turn, step + i, 5 + rng.nextInt(main ? 16 : 6), false);
            }
        }
    }

    private void mark(Map<Long, Integer> out, int x, int z, int order) {
        if (deadMosaic(x, z) != null || edge(x, z) > -1.6) return;
        out.merge(pack(x, z), order, Math::min);
    }

    private void podium(Plan p, int x, int z, double d, double phi) {
        boolean gate = Math.abs(z) < 2.5;
        boolean front = d < 1;
        for (int y = -1; y < PODIUM; y++) {
            if (gate && y >= 0 && y <= 4) {
                // the gates: a dark tunnel into the wall
                if (!front) p.put(x, y, z, darkBlock());
                continue;
            }
            Supplier<Block> bl = y == PODIUM - 1 ? stone() : bricks();
            if (gate && y == 5) bl = dead ? ModBlocks.MUERTOS_BONE::get : ModBlocks.ARENA_GOLD::get;
            if (dead && gate && y == 5 && z == 0 && front) {
                p.put(x, y, z, skull(toward(Math.atan2(-z, -x))));         // a sugar skull over each gate, looking in
                continue;
            }
            p.put(x, y, z, bl);
            if (front && y == 4 && !gate) podiumFlag(p, x, z, d, phi);
        }
        if (!front && !gate && z > -b + 1) {
            // braziers along the top of the wall, one every 11 blocks or so (the dead's: candles between them)
            double s = phi * arcRate(phi, 1.5, a, b);
            double m = Math.abs(s - Math.round(s / 11.0) * 11.0);
            boolean stage = z < 0 && Math.abs(x) < 9;
            if (m < 0.5 && !stage) p.put(x, PODIUM, z, brazier());
            else if (dead && Math.abs(m - 5.5) < 0.5 && !stage) p.put(x, PODIUM, z, ModBlocks.MUERTOS_CANDLES::get);
        }
        if (dead && !front && z < 0 && Math.abs(x) <= 9 && d > 1) ofrenda(p, x, z);
        if (front) {
            // the invisible railing over it
            for (int y = PODIUM; y <= HEIGHT + 6; y++) p.put(x, y, z, ModBlocks.ARENA_BARRIER::get);
        }
    }

    /** The dead's ofrenda on the wall behind the band: candles, marigolds and sugar skulls along its top. */
    private void ofrenda(Plan p, int x, int z) {
        int k = Math.floorMod(x, 3);
        if (k == 0) p.put(x, PODIUM, z, ModBlocks.MUERTOS_CANDLES::get);
        else if (k == 1) p.put(x, PODIUM, z, ModBlocks.MUERTOS_MARIGOLD::get);
        else p.put(x, PODIUM, z, skull(Direction.SOUTH));
    }

    /**
     * La Bandera Papa on the podium (block/ArenaFlagBlock): the big one behind the band, and one every
     * 14 blocks round the wall, all facing the sand. Each gets the front cell nearest its mark.
     */
    private void podiumFlag(Plan p, int x, int z, double d, double phi) {
        double aa = a + d, bb = b + d;
        double normal = Math.atan2(-z / (bb * bb), -x / (aa * aa));         // in, toward the sand
        if (z < 0 && Math.abs(x) <= 9) {
            if (x == 0 && z == -b) p.flagCandidate(Long.MIN_VALUE, x, 4, z, ArenaFlagBlockEntity.BACKDROP, Math.PI / 2, 0);
            return;
        }
        if (Math.abs(z) < 5) return;                                     // clear of the gates
        double s = phi * arcRate(phi, 0.5, a, b);
        long key = (long) Math.floor(s / 14.0);
        double mark = s - key * 14.0 - 7.0;
        p.flagCandidate(key, x, 4, z, ArenaFlagBlockEntity.DRAPE, normal, Math.abs(mark) + Math.abs(d - 0.5) * 0.5);
    }

    private void stands(Plan p, int x, int z, double d, double phi) {
        int r = Mth.clamp((int) Math.floor(d - CAVEA), 0, ROWS - 1);
        int h = rowHeight(r);
        int prev = r == 0 ? PODIUM - 1 : rowHeight(r - 1);
        boolean aisle = !isWalkway(r) && inAisle(phi, d, a, b);
        // the seat (or step, or walkway) and enough under it to close the riser from the row below
        for (int y = Math.min(prev, h - 2); y <= h; y++) {
            Supplier<Block> bl = stone();
            if (y == h && !isWalkway(r) && !aisle) bl = seat();
            else if (y < h) bl = bricks();
            // the doorways out of the walls between the tiers, where the aisles meet them
            if ((r == 6 || r == 12) && y > prev && y <= prev + 2 && inAisle(phi, d + 1, a, b)) bl = darkBlock();
            p.put(x, y, z, bl);
        }
        // the dead's walkways: a candle now and then at the foot of the wall behind them
        if (dead && isWalkway(r) && Math.floorMod(x * 7 + z * 13, 23) == 0) p.put(x, h + 1, z, ModBlocks.MUERTOS_CANDLES::get);
    }

    private void facade(Plan p, int x, int z, double d, double u, double bayW, int bay, double phi, RandomSource rng) {
        boolean inner = d < WALL_OUT;
        boolean proud = d >= WALL_OUT + 1;              // the engaged columns and cornices stand out from the wall
        double mid = 2 + (bayW - 2) / 2;
        boolean pier = u < 2;
        boolean opening = !pier && Math.abs(u - mid) < 1.6;
        boolean archTop = !pier && Math.abs(u - mid) < 0.8;
        int[] bases = {-1, 8, 17};
        for (int y = -1; y <= 33; y++) {
            int story = y < 8 ? 0 : y < 17 ? 1 : y < 26 ? 2 : 3;
            Supplier<Block> bl = bricks();
            boolean hole = false;
            if (story < 3) {
                int yb = bases[story];
                boolean cornice = y == yb + 8;
                boolean inArch = (y >= yb + 1 && y <= yb + 5 && opening) || (y == yb + 6 && archTop);
                if (proud) {
                    if (cornice) bl = dead ? stone() : bricks();
                    else if (pier && Math.abs(u - 1) < 0.6 && y > yb && y < yb + 8) {
                        p.put(x, y, z, column(Direction.Axis.Y));
                        continue;
                    } else continue;
                } else if (inArch) {
                    if (!inner) hole = true;
                    else bl = darkBlock();
                    if (inner && story == 1 && bay % 4 == 0 && y == yb + 6) {
                        double aa = a + d, bb = b + d;
                        p.flagCandidate(1L << 40 | bay, x, y, z, ArenaFlagBlockEntity.ARCH, Math.atan2(z / (bb * bb), x / (aa * aa)),
                                Math.abs(u - mid) + Math.abs(d - (WALL_IN + 0.5)) * 0.5);
                    }
                } else if (!inner && y == yb + 7 && archTop) {
                    if (dead) {
                        p.put(x, y, z, skull(toward(Math.atan2(z, x))));         // the keystone: a sugar skull looking out
                        continue;
                    }
                    bl = ModBlocks.ARENA_GOLD::get;                 // the keystone
                }
            } else {
                // the attic: windows right through, a coping on top
                if (proud) {
                    if (y == 26) bl = bricks();
                    else continue;
                }
                if (y >= 28 && y <= 30 && !pier && Math.abs(u - mid) < 1.1) hole = true;
                if (y == 33) bl = stone();
            }
            if (!hole) p.put(x, y, z, bl);
        }
        if (!inner && !proud && Math.abs(u - mid) < 0.5) {
            if (bay % 2 == 0) p.put(x, 34, z, brazier());
            else if (dead) p.put(x, 34, z, ModBlocks.MUERTOS_CANDLES::get);
        }
    }

    /**
     * Golden Papons over both gates, on plinths in the stands, one arm up with his spatula. The dead's: bone Papons with
     * black eye sockets and ectoplasm eyes, the raised arm holding up a spirit-fire torch.
     */
    private void statues(Plan p) {
        Supplier<Block> body = dead ? ModBlocks.MUERTOS_BONE::get : ModBlocks.ARENA_GOLD::get;
        Supplier<Block> face = darkBlock();
        for (int side : new int[]{-1, 1}) {
            int cx = side * (a + 5);
            int base = PODIUM + 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int y = PODIUM; y <= base; y++) {
                        p.put(cx + dx, y, dz, y == base ? stone() : bricks());
                    }
                }
            }
            double rx = 2.4, ry = 3.6, rz = 2.2;
            int cy = base + 4;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        double e = Math.pow(dx / rx, 2) + Math.pow(dy / ry, 2) + Math.pow(dz / rz, 2);
                        if (e > 1) continue;
                        Supplier<Block> bl = body;
                        // his face looks in at the sand: brows and eyes on that side
                        int inward = -side;
                        boolean onFace = dx * inward >= 2 && e > 0.55;
                        if (onFace && dy == 2 && Math.abs(dz) == 1) bl = dead ? body : face;
                        if (onFace && dy == 1 && Math.abs(dz) == 1) bl = dead ? ModBlocks.ECTOPLASM::get : face;
                        if (onFace && dy == -1 && dz == 0) bl = face;
                        if (dead && onFace && dy == 2 && Math.abs(dz) == 1) bl = face;      // the sockets round the glowing eyes
                        p.put(cx + dx, cy + dy, dz, bl);
                    }
                }
            }
            // stubby legs, and the right arm raised with the spatula (the dead's: a torch of spirit fire)
            p.put(cx, base + 1, -1, body);
            p.put(cx, base + 1, 1, body);
            int arm = side;          // the arm on his right as he faces in
            for (int k = 0; k <= 4; k++) p.put(cx, cy + 1 + k, arm * (3 + (k >= 2 ? 1 : 0)), body);
            if (dead) {
                for (int k = 0; k <= 1; k++) p.put(cx, cy + 6 + k, arm * 4, ModBlocks.MUERTOS_MARIGOLD::get);
                p.put(cx, cy + 8, arm * 4, ModBlocks.SPIRIT_BRAZIER::get);
            } else {
                for (int k = 0; k <= 2; k++) p.put(cx, cy + 6 + k, arm * 4, ModBlocks.ARENA_STONE::get);
                p.put(cx, cy + 8, arm * 4, ModBlocks.ARENA_DARK::get);
            }
        }
    }

    /** His box over the band stage: a golden floor, a throne, a canopy on columns. */
    private void box(Plan p) {
        for (int x = -6; x <= 6; x++) {
            for (int r = 0; r <= 4; r++) {
                double d = CAVEA + r + 0.5;
                // the row's z at this x on the north side
                double aa = a + d, bb = b + d;
                double zz = -bb * Math.sqrt(Math.max(0, 1 - (x * x) / (aa * aa)));
                int z = (int) Math.round(zz);
                p.put(x, rowHeight(r), z, (x + r) % 2 == 0 ? ModBlocks.ARENA_GOLD::get : stone());
            }
        }
        double b2 = b + CAVEA + 3.5;
        int zt = (int) Math.round(-b2);
        int ht = rowHeight(3) + 1;
        p.put(0, ht, zt, ModBlocks.ARENA_GOLD::get);
        p.put(0, ht + 1, zt - 1, ModBlocks.ARENA_GOLD::get);
        p.put(0, ht + 2, zt - 1, ModBlocks.ARENA_GOLD::get);
        p.put(-1, ht, zt, ModBlocks.ARENA_GOLD::get);
        p.put(1, ht, zt, ModBlocks.ARENA_GOLD::get);
        if (dead) p.put(0, ht + 3, zt - 1, skull(Direction.SOUTH));          // a skull crowning the throne
        int top = rowHeight(4) + 5;
        for (int cx : new int[]{-6, 6}) {
            int z0 = (int) Math.round(-(b + CAVEA + 0.5) * Math.sqrt(1 - (cx * cx) / Math.pow(a + CAVEA + 0.5, 2)));
            for (int y = rowHeight(0) + 1; y < top; y++) p.put(cx, y, z0, column(Direction.Axis.Y));
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = (int) Math.round(-(b + CAVEA + 5.5)); z <= (int) Math.round(-(b + CAVEA)); z++) {
                int k = Math.floorMod(x + 6, 3);
                Supplier<Block> c = dead
                        ? (k == 0 ? ModBlocks.MUERTOS_MARIGOLD::get : k == 1 ? ModBlocks.MUERTOS_PINK::get : ModBlocks.MUERTOS_PURPLE::get)
                        : (k == 0 ? ModBlocks.ARENA_DRAPE_GREEN::get : k == 1 ? ModBlocks.ARENA_DRAPE_WHITE::get : ModBlocks.ARENA_DRAPE_RED::get);
                p.put(x, top, z, c);
            }
        }
    }

    /** The band stand: a half-moon stage against the north wall, gold-edged, a riser at the back for the trumpets and the coro. */
    private void stage(Plan p) {
        Supplier<Block> edge = dead ? ModBlocks.MUERTOS_MARIGOLD::get : ModBlocks.ARENA_GOLD::get;
        for (int x = -10; x <= 10; x++) {
            for (int z = -b; z <= -b + 8; z++) {
                double e = Math.pow(x / 10.0, 2) + Math.pow((z + b) / 7.5, 2);
                if (e > 1 || edge(x, z) >= 0) continue;
                p.put(x, 0, z, e > 0.8 ? edge : stone());
                if (Math.abs(x) <= 7 && z <= -b + 2) p.put(x, 1, z, stone());
            }
        }
        // the floodlights at its back corners, aimed in over the band at the singer (off until nightfall)
        Vec3 singer = bandSpot(0).subtract(Vec3.atLowerCornerOf(center));
        for (BlockPos l : lampCells()) {
            double aim = Math.atan2(singer.z - (l.getZ() + 0.5), singer.x - (l.getX() + 0.5));
            Direction facing = Direction.fromYRot(aim * Mth.RAD_TO_DEG - 90);
            p.special(l.getX(), l.getY(), l.getZ(), ModBlocks.STAGE_LAMP.get().defaultBlockState()
                    .setValue(com.groid.papon.block.StageLampBlock.FACING, facing), -1, aim);
        }
    }

    /** The band stand's floodlights, relative to the centre: stage left first (the one the lamplighter switches). */
    private BlockPos[] lampCells() {
        return new BlockPos[]{new BlockPos(9, 1, -b + 2), new BlockPos(-9, 1, -b + 2)};
    }

    /** Where the floodlights stand (boss/MariachiEntity: the lamplighter goes to the first and switches both). */
    public List<BlockPos> lamps() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos l : lampCells()) out.add(center.offset(l));
        return out;
    }

    // ------------------------------------------------------------------ the band and the crowd

    /** The band's places on the stage (centre of the stage's floor = y 1), in Band.Role order. */
    public Vec3 bandSpot(int i) {
        double[][] spots = {
                {0, 1, 5.6},      // the singer, front and centre, at his microphone
                {-5.6, 2, 1.4},   // trumpets on the riser, stage right
                {-3.4, 2, 1.0},
                {-1.2, 2, 1.0},
                {-6.6, 1, 3.4},   // violins
                {-5.0, 1, 4.6},
                {-3.3, 1, 3.8},
                {3.4, 1, 3.8},    // guitar
                {5.0, 1, 4.6},    // vihuela
                {6.8, 1, 3.3},    // guitarron
                {1.2, 2, 1.0},    // the coro on the riser, stage left
                {3.4, 2, 1.0},
                {5.6, 2, 1.4},
        };
        double[] s = spots[Mth.clamp(i, 0, spots.length - 1)];
        return Vec3.atBottomCenterOf(center).add(s[0], s[1], -b + s[2]);
    }

    /** Where everyone faces from the stage: the middle of the sand. */
    public float bandYaw(int i) {
        Vec3 at = bandSpot(i);
        Vec3 to = centerVec().add(0, 0, 4);
        return (float) (Mth.atan2(to.z - at.z, to.x - at.x) * Mth.RAD_TO_DEG) - 90F;
    }

    /** A seat in the stands (crowd rendering): where the fan sits, which way he faces, his dice. */
    public record Seat(float x, float y, float z, float yaw, int seed, int row) {}

    /** Every occupied seat of El Coliseo Papa (see {@link #seats(long, int, int)}). */
    public static List<Seat> seats(long seed) {
        return seats(seed, A, B);
    }

    /**
     * Every occupied seat, relative to the floor's centre, of a coliseum this size. About six in ten seats are taken; the
     * aisles, the walkways, his box and the statues' plinths are kept clear.
     */
    public static List<Seat> seats(long seed, int a, int b) {
        List<Seat> out = new ArrayList<>();
        java.util.Random rng = new java.util.Random(seed);
        for (int r = 0; r < ROWS; r++) {
            if (isWalkway(r)) continue;
            double d = CAVEA + r + 0.5;
            double aa = a + d, bb = b + d;
            int n = 2048 * a / A;
            double len;
            double[] arc = new double[n + 1];
            for (int i = 1; i <= n; i++) {
                double phi = (i - 0.5) * Math.PI * 2 / n;
                arc[i] = arc[i - 1] + arcRate(phi, d, a, b) * Math.PI * 2 / n;
            }
            len = arc[n];
            double step = 1.38;
            int count = (int) (len / step);
            int j = 0;
            for (int k = 0; k < count; k++) {
                double s = (k + 0.5 + (rng.nextDouble() - 0.5) * 0.25) * len / count;
                while (j < n && arc[j + 1] < s) j++;
                double phi = (j + 0.5) * Math.PI * 2 / n;
                double x = aa * Math.cos(phi), z = bb * Math.sin(phi);
                boolean take = rng.nextDouble() < 0.62;
                int fanSeed = rng.nextInt();
                if (!take || inAisle(phi, d, a, b) || inBox(x, z, r) || inStatue(x, z, d, a)) continue;
                // facing in, along the ellipse's normal
                double nx = -x / (aa * aa), nz = -z / (bb * bb);
                float yaw = (float) (Mth.atan2(nz, nx) * Mth.RAD_TO_DEG) - 90F;
                out.add(new Seat((float) x, rowHeight(r) + 1, (float) z, yaw, fanSeed, r));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ build and teardown

    /** Works out every block; {@link #buildStep} then places them. */
    public void startBuild(ServerLevel level) {
        started = true;
        pending = blocks();
        PaponMod.LOG.info("[boss] {} goes up at {}: {} blocks", dead ? "El Coliseo de los Muertos" : "El Coliseo Papa", center, pending.size());
    }

    /**
     * Phase four: this (the dead's) is built over {@code old} where it stands: every block of this one goes in (over old
     * arena blocks as well as air), lowest first, so the floor under everyone is never missing a block; then whatever of
     * the old one this does not cover comes out. {@link #buildStep} does it, a batch a tick.
     */
    public void swapFrom(Coliseum old) {
        started = true;
        swapping = true;
        List<long[]> add = blocks();
        java.util.Set<Long> mine = new java.util.HashSet<>();
        for (long[] bl : add) mine.add(bl[0]);
        List<long[]> out = new ArrayList<>();
        for (long[] bl : old.blocks()) if (!mine.contains(bl[0])) out.add(new long[]{bl[0], -1});
        // consumed from the end: the new blocks (lowest first), then the old ones' removal
        out.addAll(add);
        pending = out;
        PaponMod.LOG.info("[boss] El Coliseo Papa at {} becomes El Coliseo de los Muertos ({}x): {} blocks in, {} out",
                center, scale(), add.size(), out.size() - add.size());
    }

    /** Places the next batch; true once the whole coliseum is up. */
    public boolean buildStep(ServerLevel level) {
        return buildStep(level, PER_TICK);
    }

    public boolean buildStep(ServerLevel level, int perTick) {
        if (!started) startBuild(level);
        if (pending == null) return true;
        int n = 0;
        while (!pending.isEmpty() && n < perTick) {
            long[] bl = pending.remove(pending.size() - 1);
            BlockPos p = BlockPos.of(bl[0]);
            if (!level.isInWorldBounds(p)) continue;
            BlockState now = level.getBlockState(p);
            if (bl[1] < 0) {
                // the old coliseum's, where this one has nothing: out
                if (ModBlocks.isArena(now)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                n++;
                continue;
            }
            BlockState want = Block.stateById((int) bl[1]);
            if (!now.isAir() && !(swapping && ModBlocks.isArena(now))) continue;
            if (now != want) level.setBlock(p, want, Block.UPDATE_CLIENTS);
            if (bl.length > 3 && level.getBlockEntity(p) instanceof ArenaFlagBlockEntity flag) {
                flag.setup((int) bl[2], (float) Double.longBitsToDouble(bl[3]));
            }
            if (bl.length > 3 && level.getBlockEntity(p) instanceof com.groid.papon.block.StageLampBlockEntity lamp) {
                lamp.setAim((float) Double.longBitsToDouble(bl[3]));
            }
            n++;
        }
        if (pending.isEmpty()) {
            pending = null;
            swapping = false;
            return true;
        }
        return false;
    }

    public boolean built() {
        return started && pending == null;
    }

    /** Whether the blocks have started going up (it is only planned until the white-out of his scream). */
    public boolean started() {
        return started;
    }

    /**
     * Every brazier in the plan (podium, walls, top), in order round the bowl starting from the band stand (north)
     * and going clockwise seen from above: phase three puts them out in this order, a wave of dark running round.
     */
    public List<BlockPos> braziers() {
        Block brazier = brazier().get();
        List<BlockPos> out = new ArrayList<>();
        for (long[] bl : blocks()) {
            if (Block.stateById((int) bl[1]).is(brazier)) out.add(BlockPos.of(bl[0]));
        }
        out.sort(java.util.Comparator.comparingDouble(p -> {
            double ang = Math.atan2(p.getX() - center.getX(), -(p.getZ() - center.getZ()));
            return ang < 0 ? ang + Math.PI * 2 : ang;
        }));
        return out;
    }

    /** Takes down every planned block that is still an arena block, and the crowd and the band with it. */
    public void teardown(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long[] bl : blocks()) {
            m.set(bl[0]);
            if (ModBlocks.isArena(level.getBlockState(m))) {
                level.setBlock(m, air, Block.UPDATE_CLIENTS);
                removed++;
            }
        }
        if (dead) {
            // anything a fight left in the floor under it (the rift's void, the old coliseum's oil)
            for (int x = -a; x <= a; x++) {
                for (int z = -b; z <= b; z++) {
                    for (int y = -3; y <= -1; y++) {
                        m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        if (ModBlocks.isArena(level.getBlockState(m))) level.setBlock(m, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        for (Entity e : level.getEntities((Entity) null, bounds(), e -> e instanceof ColiseumCrowdEntity || e instanceof MariachiEntity)) {
            e.discard();
        }
        pending = null;
        PaponMod.LOG.info("[boss] {} at {} is gone ({} blocks)", dead ? "El Coliseo de los Muertos" : "El Coliseo Papa", center, removed);
    }

    // ------------------------------------------------------------------ getting in and out

    public AABB bounds() {
        return new AABB(center).inflate(a + OUTSIDE + 1, 0, b + OUTSIDE + 1).expandTowards(0, HEIGHT + 8, 0).move(0, -4, 0);
    }

    public boolean contains(Vec3 p) {
        return bounds().contains(p);
    }

    /** On the sand (or just over it): inside the podium wall and below the top of the barrier. */
    public boolean inArena(Vec3 p) {
        double y = p.y - floorY();
        return y > -3 && y < HEIGHT + 6 && edge(p.x - centerVec().x, p.z - centerVec().z) < 0.2;
    }

    /** {@code p} pulled inside the sand by {@code margin} blocks and between the two heights over the floor. */
    public Vec3 clamp(Vec3 p, double margin, double yMin, double yMax) {
        Vec3 c = centerVec();
        double x = p.x - c.x, z = p.z - c.z;
        double d = edge(x, z);
        if (d > -margin) {
            double k = (b - margin) / (b + d);
            double ka = (a - margin) / (a + d);
            // shrink along the ellipse: scale x and z by each axis' own ratio
            x *= ka;
            z *= k;
        }
        return new Vec3(c.x + x, Mth.clamp(p.y, floorY() + yMin, floorY() + yMax), c.z + z);
    }

    /** Where the i-th of n challengers lands: the south half of the sand, facing the band stand. */
    public Vec3 playerSpot(int i, int n) {
        double spread = Math.min(14, 3.0 * (n - 1));
        double x = n <= 1 ? 0 : -spread / 2 + spread * i / (n - 1);
        return centerVec().add(x, 0, 7 * scale() + (i % 2) * 1.5);
    }

    /** The van's parking place: the east end of the sand, nose to the north. */
    public Vec3 vanSpot() {
        return centerVec().add(a - 9, 0, 6);
    }

    /** The way back down: same offset from the middle, on top of whatever is there. */
    public Vec3 outside(ServerLevel level, Vec3 from) {
        double x = ground.x + (from.x - center.getX() - 0.5);
        double z = ground.z + (from.z - center.getZ() - 0.5);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (y >= center.getY() - 6) y = (int) Math.floor(ground.y);
        return new Vec3(x, y, z);
    }

    /** Everyone and everything still in the coliseum goes back to the ground. */
    public void sendEveryoneHome(ServerLevel level) {
        for (Entity e : level.getEntities((Entity) null, bounds(), e -> e instanceof Player || e instanceof ItemEntity
                || e instanceof ExperienceOrb || e instanceof com.groid.papon.vehicle.CaravanEntity || e instanceof PaponBossEntity)) {
            if (e.isPassenger()) e.stopRiding();
            send(level, e, outside(level, e.position()));
        }
    }

    public static void send(ServerLevel level, Entity e, Vec3 to) {
        e.fallDistance = 0;
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(level, to.x, to.y, to.z, sp.getYRot(), sp.getXRot());
        } else {
            e.teleportTo(to.x, to.y, to.z);
        }
        e.setDeltaMovement(Vec3.ZERO);
    }

    /** The floor back as planned wherever a fryer (boss/Freidora) or the rift (La Grieta) was left open, e.g. by a save mid-fry. */
    public void restoreFloor(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -a; x <= a; x++) {
            for (int z = -b; z <= b; z++) {
                double d = edge(x, z);
                if (d >= 0) continue;
                for (int y = -3; y <= -2; y++) {
                    m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState under = level.getBlockState(m);
                    if (under.is(ModBlocks.ARENA_FRYING_OIL.get()) || under.is(ModBlocks.SOUL_VOID.get())) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                m.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                BlockState s = level.getBlockState(m);
                if (!s.is(ModBlocks.ARENA_HOT_SAND.get()) && !s.is(ModBlocks.ARENA_FRYING_OIL.get()) && !s.is(ModBlocks.SOUL_VOID.get())
                        && !s.isAir()) continue;
                level.setBlock(m, floorAt(x, z), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The floor block planned at (x, z) of the sand. */
    public BlockState floorAt(int x, int z) {
        Plan p = new Plan();
        floor(p, x, z, edge(x, z));
        return Block.stateById((int) p.out.get(0)[1]);
    }

    // ------------------------------------------------------------------ keeping the fight in, and everything else out

    /** The coliseums up right now, per dimension: the game time their boss last vouched for them, and their size. */
    private static final Map<String, Map<Long, long[]>> LIVE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Called every tick by its boss while it stands: for {@link #near} (spawns and strays). */
    public void keepAlive(ServerLevel level) {
        LIVE.computeIfAbsent(level.dimension().location().toString(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(center.asLong(), new long[]{level.getGameTime(), a, b});
    }

    public void retire(ServerLevel level) {
        var m = LIVE.get(level.dimension().location().toString());
        if (m != null) m.remove(center.asLong());
    }

    /** The coliseum standing within {@code margin} blocks of its bounds around {@code p}, or null. */
    @Nullable
    public static Coliseum near(ServerLevel level, Vec3 p, double margin) {
        var m = LIVE.get(level.dimension().location().toString());
        if (m == null) return null;
        long now = level.getGameTime();
        for (var e : m.entrySet()) {
            long[] v = e.getValue();
            if (now - v[0] > 100) continue;                 // its boss stopped ticking (unloaded, gone)
            Coliseum c = new Coliseum(BlockPos.of(e.getKey()), Vec3.ZERO, (int) v[1], (int) v[2], v[1] > A);
            if (c.bounds().inflate(margin).contains(p)) return c;
        }
        return null;
    }

    /**
     * Whether {@code e} belongs in a coliseum: anything that is not a mob (players, items, arrows, bolts,
     * fireworks), anything of ours (the boss, his van, props, the band, the crowd, echoes, foil potatoes),
     * and tamed pets. Every other mob (natural spawns, flyers from other mods like opposing_force's
     * skyverns) is kept out.
     */
    public static boolean belongs(Entity e) {
        if (!(e instanceof net.minecraft.world.entity.Mob)) return true;
        if (PaponMod.MOD_ID.equals(net.minecraft.world.entity.EntityType.getKey(e.getType()).getNamespace())) return true;
        return e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwnerUUID() != null;
    }

    /** A spawn (or anything else) arriving in or by a coliseum that does not belong there: refused. */
    @Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
    public static final class Gate {
        @SubscribeEvent
        public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level) || belongs(event.getEntity())) return;
            if (near(level, event.getEntity().position(), 32) != null) event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Center", center.asLong());
        t.putDouble("GX", ground.x);
        t.putDouble("GY", ground.y);
        t.putDouble("GZ", ground.z);
        t.putInt("A", a);
        t.putInt("B", b);
        t.putBoolean("Dead", dead);
        return t;
    }

    public static Coliseum load(CompoundTag t) {
        int a = t.contains("A") ? t.getInt("A") : A, b = t.contains("B") ? t.getInt("B") : B;
        Coliseum c = new Coliseum(BlockPos.of(t.getLong("Center")), new Vec3(t.getDouble("GX"), t.getDouble("GY"), t.getDouble("GZ")),
                a, b, t.getBoolean("Dead"));
        c.started = true;               // a saved coliseum was standing (or is finished from where it stopped)
        return c;
    }

    /**
     * An old save's potato dome (before the coliseum): every potato block in its bounds, gone.
     * Called once when such a boss loads.
     */
    public static void tearDownOldDome(ServerLevel level, CompoundTag dome) {
        BlockPos c = BlockPos.of(dome.getLong("Center"));
        int r = dome.getInt("Radius") + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -3; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.is(ModBlocks.POTATO_SKIN.get()) || s.is(ModBlocks.POTATO_FLESH.get()) || s.is(ModBlocks.POTATO_EYE.get())) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Logged out in the coliseum and back after it came down: put them on the ground, not in the sky. */
    @Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
    public static final class Strays {
        private Strays() {}

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return;
            ServerLevel level = sp.serverLevel();
            if (sp.getY() < level.getMaxBuildHeight() - HEIGHT - 24) return;
            BlockPos at = sp.blockPosition();
            for (int dy = 1; dy <= 48; dy++) {
                if (!level.getBlockState(at.below(dy)).isAir()) return;           // something under them: leave them be
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            if (y >= sp.getY() - 48) return;
            send(level, sp, new Vec3(sp.getX(), y, sp.getZ()));
            PaponMod.LOG.info("[boss] {} logged in where a coliseum used to be: put back on the ground", sp.getName().getString());
        }
    }
}
