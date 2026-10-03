package com.groid.papon.boss;

import com.groid.papon.PaponMod;
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
import java.util.List;
import java.util.function.Supplier;

/**
 * El Coliseo Papa: Chef Papon's own arena, where he fights his challengers. A Roman coliseum in
 * potato travertine, built high in the sky straight above the fight (the second phase's "pocket
 * dimension"); everyone in the fight is pulled up into it at the end of his scream.
 *
 * <pre>
 *  the arena      an ellipse of raked sand, {@link #A} by {@link #B} blocks (semi-axes, long axis
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
 * Every ring is an offset of the arena's ellipse ({@link #offset}: how far outside the sand a
 * point is), so the rows keep a constant depth all the way round. The geometry is a pure function
 * of the centre, so the build, the teardown (which removes only the planned positions that still
 * hold an arena block) and the crowd's seats ({@link #seats}, on the client too) all agree.
 *
 * It is built only into air (nothing anyone made is touched) a few hundred blocks a tick while the
 * cutscene runs, and taken down when the fight ends (his death, his removal, or nobody left in it):
 * players, the van and anything dropped in it go back down to where they were relative to the
 * fight. Its blocks cannot be broken.
 */
public final class Coliseum {
    /** The sand's semi-axes (east-west, north-south). */
    public static final int A = 29;
    public static final int B = 21;
    public static final int PODIUM = 6;
    /** Above the floor: the facade's top. */
    public static final int HEIGHT = 34;
    /** Offsets (blocks outside the sand) where things are. */
    private static final double CAVEA = 2, GALLERY = 18, WALL_IN = 19, WALL_OUT = 20, OUTSIDE = 22;
    private static final int PER_TICK = 450;
    private static final int AISLES = 16;

    /** The centre of the floor's top face (where you stand). */
    public final BlockPos center;
    /** The middle of the fight on the ground, where everyone goes back to. */
    public final Vec3 ground;
    @Nullable private List<long[]> pending;

    private Coliseum(BlockPos center, Vec3 ground) {
        this.center = center;
        this.ground = ground;
    }

    /** A coliseum over {@code arena}, as high as it fits under the build limit; null if the sky has no room. */
    @Nullable
    public static Coliseum plan(ServerLevel level, Vec3 arena) {
        int reach = (int) (A + OUTSIDE + 1);
        int highest = level.getMinBuildHeight();
        for (int dx = -reach; dx <= reach; dx += 8) {
            for (int dz = -reach; dz <= reach; dz += 8) {
                highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(arena.x) + dx, Mth.floor(arena.z) + dz));
            }
        }
        int floor = level.getMaxBuildHeight() - HEIGHT - 6;
        if (floor < highest + 12) return null;
        return new Coliseum(new BlockPos(Mth.floor(arena.x), floor, Mth.floor(arena.z)), arena);
    }

    public double floorY() {
        return center.getY();
    }

    public Vec3 centerVec() {
        return Vec3.atBottomCenterOf(center);
    }

    // ------------------------------------------------------------------ the shape

    /**
     * How far outside the sand's edge a point is (negative inside), measured as the offset ellipse
     * through it: the d for which (x/(A+d))^2 + (z/(B+d))^2 = 1.
     */
    public static double offset(double x, double z) {
        if (Math.abs(x) < 1e-6 && Math.abs(z) < 1e-6) return -B;
        double lo = -B + 1e-4, hi = 80;
        for (int i = 0; i < 26; i++) {
            double d = (lo + hi) * 0.5;
            double u = x / (A + d), v = z / (B + d);
            if (u * u + v * v > 1) lo = d;
            else hi = d;
        }
        return (lo + hi) * 0.5;
    }

    /** The angle round the ellipse (0 = east, PI/2 = south) of a point at offset d. */
    private static double angle(double x, double z, double d) {
        return Math.atan2(z / (B + d), x / (A + d));
    }

    /** Blocks of arc per radian at that angle on the ring at offset d. */
    private static double arcRate(double phi, double d) {
        double a = A + d, b = B + d;
        return Math.sqrt(Math.pow(a * Math.sin(phi), 2) + Math.pow(b * Math.cos(phi), 2));
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

    private static boolean inAisle(double phi, double d) {
        double step = Math.PI * 2 / AISLES;
        double k = Math.floorMod((long) Math.floor((phi - step / 2) / step), AISLES);
        double c = step / 2 + k * step;
        double off = Math.abs(Mth.wrapDegrees((phi - c) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
        off = Math.min(off, Math.abs(Mth.wrapDegrees((phi - c - step) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD);
        return off * arcRate(phi, d) < 0.8;
    }

    /** His box in the stands, over the band stage (north side, the first tier). */
    private static boolean inBox(double x, double z, int r) {
        return z < 0 && Math.abs(x) < 6.5 && r <= 4;
    }

    /** The statues over the gates (both ends of the long axis). */
    private static boolean inStatue(double x, double z, double d) {
        return Math.abs(z) < 4.5 && Math.abs(x) > A && d < 8;
    }

    // ------------------------------------------------------------------ building

    private final class Plan {
        final List<long[]> out = new ArrayList<>();

        void put(int x, int y, int z, BlockState s) {
            out.add(new long[]{center.offset(x, y, z).asLong(), Block.getId(s)});
        }

        void put(int x, int y, int z, Supplier<Block> b) {
            put(x, y, z, b.get().defaultBlockState());
        }
    }

    /** Every block of the coliseum, in build order (the floor and the wall first). */
    private List<long[]> blocks() {
        Plan p = new Plan();
        RandomSource rng = RandomSource.create(center.asLong());
        int reach = (int) Math.ceil(A + OUTSIDE);
        int reachZ = (int) Math.ceil(B + OUTSIDE);
        // the facade's bays, by arc length round the outer wall
        double outer = WALL_OUT + 0.5;
        int samples = 4096;
        double[] arc = new double[samples + 1];
        for (int i = 1; i <= samples; i++) {
            double phi = -Math.PI + (i - 0.5) * Math.PI * 2 / samples;
            arc[i] = arc[i - 1] + arcRate(phi, outer) * Math.PI * 2 / samples;
        }
        int bays = (int) Math.round(arc[samples] / 5.0);
        double bayW = arc[samples] / bays;
        Supplier<Block> stone = ModBlocks.ARENA_STONE::get, bricks = ModBlocks.ARENA_BRICKS::get, dark = ModBlocks.ARENA_DARK::get;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reachZ; z <= reachZ; z++) {
                double d = offset(x, z);
                if (d >= OUTSIDE) continue;
                double phi = angle(x, z, d);
                if (d < 0) {
                    floor(p, x, z, d);
                } else if (d < CAVEA) {
                    podium(p, x, z, d, phi);
                } else if (d < GALLERY) {
                    stands(p, x, z, d, phi);
                } else if (d < WALL_IN) {
                    for (int y = 22; y <= 24; y++) p.put(x, y, z, y == 24 ? stone : bricks);
                } else {
                    int i = Mth.clamp((int) ((phi + Math.PI) / (Math.PI * 2) * samples), 0, samples - 1);
                    double s = arc[i] / bayW;
                    int bay = (int) Math.floor(s);
                    double u = (s - bay) * bayW;
                    facade(p, x, z, d, u, bayW, bay, rng);
                }
            }
        }
        statues(p);
        box(p);
        stage(p);
        // the floor and the wall go up first, so it is whole by the time anyone stands in it
        List<long[]> ordered = p.out;
        ordered.sort((a, b) -> Integer.compare(BlockPos.of(b[0]).getY(), BlockPos.of(a[0]).getY()));
        return ordered;
    }

    private void floor(Plan p, int x, int z, double d) {
        double r = Math.sqrt(x * x + z * z);
        Supplier<Block> b = ModBlocks.ARENA_SAND::get;
        if (d > -1) b = ModBlocks.ARENA_BRICKS::get;                        // the kerb along the wall
        else if (Math.abs(d + 9) < 0.5) b = ModBlocks.ARENA_STONE::get;      // a ring marked in the sand
        else if (r < 7.5) b = mosaic(x, z, r);
        p.put(x, -1, z, b);
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

    private void podium(Plan p, int x, int z, double d, double phi) {
        boolean gate = Math.abs(z) < 2.5;
        boolean front = d < 1;
        for (int y = -1; y < PODIUM; y++) {
            if (gate && y >= 0 && y <= 4) {
                // the gates: a dark tunnel into the wall
                if (!front) p.put(x, y, z, ModBlocks.ARENA_DARK::get);
                continue;
            }
            Supplier<Block> b = y == PODIUM - 1 ? ModBlocks.ARENA_STONE::get : ModBlocks.ARENA_BRICKS::get;
            if (gate && y == 5) b = ModBlocks.ARENA_GOLD::get;
            if (front && y >= 1 && y <= 4) {
                Supplier<Block> drape = drape(x, z, phi, y);
                if (drape != null) b = drape;
            }
            p.put(x, y, z, b);
        }
        if (!front && !gate && z > -B + 1) {
            // braziers along the top of the wall, one every 11 blocks or so
            double s = phi * arcRate(phi, 1.5);
            if (Math.abs(s - Math.round(s / 11.0) * 11.0) < 0.5 && !(z < 0 && Math.abs(x) < 9)) {
                p.put(x, PODIUM, z, ModBlocks.ARENA_BRAZIER::get);
            }
        }
        if (front) {
            // the invisible railing over it
            for (int y = PODIUM; y <= HEIGHT + 6; y++) p.put(x, y, z, ModBlocks.ARENA_BARRIER::get);
        }
    }

    /** The tricolour on the podium: a flag behind the band, and a drape every so often round the wall. */
    @Nullable
    private static Supplier<Block> drape(int x, int z, double phi, int y) {
        if (z < 0 && Math.abs(x) <= 7) {
            if (x < -2) return ModBlocks.ARENA_DRAPE_GREEN::get;
            if (x > 2) return ModBlocks.ARENA_DRAPE_RED::get;
            return x == 0 && y == 3 ? ModBlocks.ARENA_DRAPE_EMBLEM::get : ModBlocks.ARENA_DRAPE_WHITE::get;
        }
        double s = phi * arcRate(phi, 0.5);
        double k = s - Math.floor(s / 14.0) * 14.0;
        if (k < 1) return ModBlocks.ARENA_DRAPE_GREEN::get;
        if (k < 2) return y == 3 ? ModBlocks.ARENA_DRAPE_EMBLEM::get : ModBlocks.ARENA_DRAPE_WHITE::get;
        if (k < 3) return ModBlocks.ARENA_DRAPE_RED::get;
        return null;
    }

    private void stands(Plan p, int x, int z, double d, double phi) {
        int r = Mth.clamp((int) Math.floor(d - CAVEA), 0, ROWS - 1);
        int h = rowHeight(r);
        int prev = r == 0 ? PODIUM - 1 : rowHeight(r - 1);
        boolean aisle = !isWalkway(r) && inAisle(phi, d);
        // the seat (or step, or walkway) and enough under it to close the riser from the row below
        for (int y = Math.min(prev, h - 2); y <= h; y++) {
            Supplier<Block> b = ModBlocks.ARENA_STONE::get;
            if (y == h && !isWalkway(r) && !aisle) b = ModBlocks.ARENA_SEAT::get;
            else if (y < h) b = ModBlocks.ARENA_BRICKS::get;
            // the doorways out of the walls between the tiers, where the aisles meet them
            if ((r == 6 || r == 12) && y > prev && y <= prev + 2 && inAisle(phi, d + 1)) b = ModBlocks.ARENA_DARK::get;
            p.put(x, y, z, b);
        }
    }

    private void facade(Plan p, int x, int z, double d, double u, double bayW, int bay, RandomSource rng) {
        boolean inner = d < WALL_OUT;
        boolean proud = d >= WALL_OUT + 1;              // the engaged columns and cornices stand out from the wall
        double mid = 2 + (bayW - 2) / 2;
        boolean pier = u < 2;
        boolean opening = !pier && Math.abs(u - mid) < 1.6;
        boolean archTop = !pier && Math.abs(u - mid) < 0.8;
        int[] bases = {-1, 8, 17};
        for (int y = -1; y <= 33; y++) {
            int story = y < 8 ? 0 : y < 17 ? 1 : y < 26 ? 2 : 3;
            Supplier<Block> b = ModBlocks.ARENA_BRICKS::get;
            boolean hole = false;
            if (story < 3) {
                int yb = bases[story];
                boolean cornice = y == yb + 8;
                boolean inArch = (y >= yb + 1 && y <= yb + 5 && opening) || (y == yb + 6 && archTop);
                if (proud) {
                    if (cornice) b = ModBlocks.ARENA_BRICKS::get;
                    else if (pier && Math.abs(u - 1) < 0.6 && y > yb && y < yb + 8) b = ModBlocks.ARENA_COLUMN::get;
                    else continue;
                } else if (inArch) {
                    if (!inner) hole = true;
                    else if (story == 1 && bay % 4 == 0) b = flag(u, mid, y, yb);
                    else b = ModBlocks.ARENA_DARK::get;
                } else if (!inner && y == yb + 7 && archTop) {
                    b = ModBlocks.ARENA_GOLD::get;                 // the keystone
                }
            } else {
                // the attic: windows right through, a coping on top
                if (proud) {
                    if (y == 26) b = ModBlocks.ARENA_BRICKS::get;
                    else continue;
                }
                if (y >= 28 && y <= 30 && !pier && Math.abs(u - mid) < 1.1) hole = true;
                if (y == 33) b = ModBlocks.ARENA_STONE::get;
            }
            if (!hole) p.put(x, y, z, b);
        }
        if (!inner && !proud && bay % 2 == 0 && Math.abs(u - mid) < 0.5) p.put(x, 34, z, ModBlocks.ARENA_BRAZIER::get);
    }

    /** A flag hung in an arch: green, white (the emblem in the middle), red, left to right. */
    private static Supplier<Block> flag(double u, double mid, int y, int yb) {
        double k = u - mid;
        if (k < -0.5) return ModBlocks.ARENA_DRAPE_GREEN::get;
        if (k > 0.5) return ModBlocks.ARENA_DRAPE_RED::get;
        return y == yb + 3 ? ModBlocks.ARENA_DRAPE_EMBLEM::get : ModBlocks.ARENA_DRAPE_WHITE::get;
    }

    /** Golden Papons over both gates, on plinths in the stands, one arm up with his spatula. */
    private void statues(Plan p) {
        for (int side : new int[]{-1, 1}) {
            int cx = side * (A + 5);
            int base = PODIUM + 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int y = PODIUM; y <= base; y++) {
                        p.put(cx + dx, y, dz, y == base ? ModBlocks.ARENA_STONE::get : ModBlocks.ARENA_BRICKS::get);
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
                        Supplier<Block> b = ModBlocks.ARENA_GOLD::get;
                        // his face looks in at the sand: brows and eyes on that side
                        int inward = -side;
                        boolean face = dx * inward >= 2 && e > 0.55;
                        if (face && dy == 2 && Math.abs(dz) == 1) b = ModBlocks.ARENA_DARK::get;
                        if (face && dy == 1 && Math.abs(dz) == 1) b = ModBlocks.ARENA_DARK::get;
                        if (face && dy == -1 && dz == 0) b = ModBlocks.ARENA_DARK::get;
                        p.put(cx + dx, cy + dy, dz, b);
                    }
                }
            }
            // stubby legs, and the right arm raised with the spatula
            p.put(cx, base + 1, -1, ModBlocks.ARENA_GOLD::get);
            p.put(cx, base + 1, 1, ModBlocks.ARENA_GOLD::get);
            int arm = side;          // the arm on his right as he faces in
            for (int k = 0; k <= 4; k++) p.put(cx, cy + 1 + k, arm * (3 + (k >= 2 ? 1 : 0)), ModBlocks.ARENA_GOLD::get);
            for (int k = 0; k <= 2; k++) p.put(cx, cy + 6 + k, arm * 4, ModBlocks.ARENA_STONE::get);
            p.put(cx, cy + 8, arm * 4, ModBlocks.ARENA_DARK::get);
        }
    }

    /** His box over the band stage: a golden floor, a throne, a canopy on columns. */
    private void box(Plan p) {
        for (int x = -6; x <= 6; x++) {
            for (int r = 0; r <= 4; r++) {
                double d = CAVEA + r + 0.5;
                // the row's z at this x on the north side
                double a = A + d, b = B + d;
                double zz = -b * Math.sqrt(Math.max(0, 1 - (x * x) / (a * a)));
                int z = (int) Math.round(zz);
                p.put(x, rowHeight(r), z, (x + r) % 2 == 0 ? ModBlocks.ARENA_GOLD::get : ModBlocks.ARENA_STONE::get);
            }
        }
        double b2 = B + CAVEA + 3.5;
        int zt = (int) Math.round(-b2);
        int ht = rowHeight(3) + 1;
        p.put(0, ht, zt, ModBlocks.ARENA_GOLD::get);
        p.put(0, ht + 1, zt - 1, ModBlocks.ARENA_GOLD::get);
        p.put(0, ht + 2, zt - 1, ModBlocks.ARENA_GOLD::get);
        p.put(-1, ht, zt, ModBlocks.ARENA_GOLD::get);
        p.put(1, ht, zt, ModBlocks.ARENA_GOLD::get);
        int top = rowHeight(4) + 5;
        for (int cx : new int[]{-6, 6}) {
            int z0 = (int) Math.round(-(B + CAVEA + 0.5) * Math.sqrt(1 - (cx * cx) / Math.pow(A + CAVEA + 0.5, 2)));
            for (int y = rowHeight(0) + 1; y < top; y++) {
                p.put(cx, y, z0, ModBlocks.ARENA_COLUMN.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = (int) Math.round(-(B + CAVEA + 5.5)); z <= (int) Math.round(-(B + CAVEA)); z++) {
                Supplier<Block> c = Math.floorMod(x + 6, 3) == 0 ? ModBlocks.ARENA_DRAPE_GREEN::get
                        : Math.floorMod(x + 6, 3) == 1 ? ModBlocks.ARENA_DRAPE_WHITE::get : ModBlocks.ARENA_DRAPE_RED::get;
                p.put(x, top, z, c);
            }
        }
    }

    /** The band stand: a half-moon stage against the north wall, gold-edged, a riser at the back for the trumpets. */
    private void stage(Plan p) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -B; z <= -B + 8; z++) {
                double e = Math.pow(x / 10.0, 2) + Math.pow((z + B) / 7.5, 2);
                if (e > 1 || offset(x, z) >= 0) continue;
                p.put(x, 0, z, e > 0.8 ? ModBlocks.ARENA_GOLD::get : ModBlocks.ARENA_STONE::get);
                if (Math.abs(x) <= 5 && z <= -B + 2) p.put(x, 1, z, ModBlocks.ARENA_STONE::get);
            }
        }
    }

    // ------------------------------------------------------------------ the band and the crowd

    /** The band's places on the stage (centre of the stage's floor = y 1), in Band.Role order. */
    public Vec3 bandSpot(int i) {
        double[][] spots = {
                {0, 1, 5.6},      // the singer, front and centre, at his microphone
                {-2.2, 2, 1.3},   // trumpets on the riser
                {0, 2, 1.0},
                {2.2, 2, 1.3},
                {-6.6, 1, 3.4},   // violins
                {-5.0, 1, 4.6},
                {-3.3, 1, 3.8},
                {3.4, 1, 3.8},    // guitar
                {5.0, 1, 4.6},    // vihuela
                {6.8, 1, 3.3},    // guitarron
        };
        double[] s = spots[Mth.clamp(i, 0, spots.length - 1)];
        return Vec3.atBottomCenterOf(center).add(s[0], s[1], -B + s[2]);
    }

    /** Where everyone faces from the stage: the middle of the sand. */
    public float bandYaw(int i) {
        Vec3 at = bandSpot(i);
        Vec3 to = centerVec().add(0, 0, 4);
        return (float) (Mth.atan2(to.z - at.z, to.x - at.x) * Mth.RAD_TO_DEG) - 90F;
    }

    /** A seat in the stands (crowd rendering): where the fan sits, which way he faces, his dice. */
    public record Seat(float x, float y, float z, float yaw, int seed, int row) {}

    /**
     * Every occupied seat, relative to the floor's centre. About six in ten seats are taken; the
     * aisles, the walkways, his box and the statues' plinths are kept clear.
     */
    public static List<Seat> seats(long seed) {
        List<Seat> out = new ArrayList<>();
        java.util.Random rng = new java.util.Random(seed);
        for (int r = 0; r < ROWS; r++) {
            if (isWalkway(r)) continue;
            double d = CAVEA + r + 0.5;
            double a = A + d, b = B + d;
            int n = 2048;
            double len = 0;
            double[] arc = new double[n + 1];
            for (int i = 1; i <= n; i++) {
                double phi = (i - 0.5) * Math.PI * 2 / n;
                arc[i] = arc[i - 1] + arcRate(phi, d) * Math.PI * 2 / n;
            }
            len = arc[n];
            double step = 1.38;
            int count = (int) (len / step);
            int j = 0;
            for (int k = 0; k < count; k++) {
                double s = (k + 0.5 + (rng.nextDouble() - 0.5) * 0.25) * len / count;
                while (j < n && arc[j + 1] < s) j++;
                double phi = (j + 0.5) * Math.PI * 2 / n;
                double x = a * Math.cos(phi), z = b * Math.sin(phi);
                boolean take = rng.nextDouble() < 0.62;
                int fanSeed = rng.nextInt();
                if (!take || inAisle(phi, d) || inBox(x, z, r) || inStatue(x, z, d)) continue;
                // facing in, along the ellipse's normal
                double nx = -x / (a * a), nz = -z / (b * b);
                float yaw = (float) (Mth.atan2(nz, nx) * Mth.RAD_TO_DEG) - 90F;
                out.add(new Seat((float) x, rowHeight(r) + 1, (float) z, yaw, fanSeed, r));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ build and teardown

    /** Works out every block; {@link #buildStep} then places them. */
    public void startBuild(ServerLevel level) {
        pending = blocks();
        PaponMod.LOG.info("[boss] El Coliseo Papa goes up at {}: {} blocks", center, pending.size());
    }

    /** Places the next batch; true once the whole coliseum is up. */
    public boolean buildStep(ServerLevel level) {
        return buildStep(level, PER_TICK);
    }

    public boolean buildStep(ServerLevel level, int perTick) {
        if (pending == null) return true;
        int n = 0;
        while (!pending.isEmpty() && n < perTick) {
            long[] b = pending.remove(pending.size() - 1);
            BlockPos p = BlockPos.of(b[0]);
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, Block.stateById((int) b[1]), Block.UPDATE_CLIENTS);
            n++;
        }
        if (pending.isEmpty()) {
            pending = null;
            return true;
        }
        return false;
    }

    public boolean built() {
        return pending == null;
    }

    /** Takes down every planned block that is still an arena block, and the crowd and the band with it. */
    public void teardown(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long[] b : blocks()) {
            m.set(b[0]);
            if (ModBlocks.isArena(level.getBlockState(m))) {
                level.setBlock(m, air, Block.UPDATE_CLIENTS);
                removed++;
            }
        }
        for (Entity e : level.getEntities((Entity) null, bounds(), e -> e instanceof ColiseumCrowdEntity || e instanceof MariachiEntity)) {
            e.discard();
        }
        pending = null;
        PaponMod.LOG.info("[boss] El Coliseo Papa at {} is gone ({} blocks)", center, removed);
    }

    // ------------------------------------------------------------------ getting in and out

    public AABB bounds() {
        return new AABB(center).inflate(A + OUTSIDE + 1, 0, B + OUTSIDE + 1).expandTowards(0, HEIGHT + 8, 0).move(0, -4, 0);
    }

    public boolean contains(Vec3 p) {
        return bounds().contains(p);
    }

    /** On the sand (or just over it): inside the podium wall and below the top of the barrier. */
    public boolean inArena(Vec3 p) {
        double y = p.y - floorY();
        return y > -3 && y < HEIGHT + 6 && offset(p.x - centerVec().x, p.z - centerVec().z) < 0.2;
    }

    /** {@code p} pulled inside the sand by {@code margin} blocks and between the two heights over the floor. */
    public Vec3 clamp(Vec3 p, double margin, double yMin, double yMax) {
        Vec3 c = centerVec();
        double x = p.x - c.x, z = p.z - c.z;
        double d = offset(x, z);
        if (d > -margin) {
            double k = (B - margin) / (B + d);
            double ka = (A - margin) / (A + d);
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
        return centerVec().add(x, 0, 7 + (i % 2) * 1.5);
    }

    /** The van's parking place: the east end of the sand, nose to the north. */
    public Vec3 vanSpot() {
        return centerVec().add(A - 9, 0, 6);
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

    /** The sand back as planned wherever a fryer (boss/Freidora) was left open, e.g. by a save mid-fry. */
    public void restoreFloor(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -A; x <= A; x++) {
            for (int z = -B; z <= B; z++) {
                double d = offset(x, z);
                if (d >= 0) continue;
                m.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                BlockState s = level.getBlockState(m);
                if (!s.is(ModBlocks.ARENA_HOT_SAND.get()) && !s.is(ModBlocks.ARENA_FRYING_OIL.get())) continue;
                Plan p = new Plan();
                floor(p, x, z, d);
                level.setBlock(m, Block.stateById((int) p.out.get(0)[1]), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------ keeping the fight in, and everything else out

    /** The coliseums up right now, per dimension, with the game time their boss last vouched for them. */
    private static final java.util.Map<String, java.util.Map<Long, Long>> LIVE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Called every tick by its boss while it stands: for {@link #near} (spawns and strays). */
    public void keepAlive(ServerLevel level) {
        LIVE.computeIfAbsent(level.dimension().location().toString(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(center.asLong(), level.getGameTime());
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
            if (now - e.getValue() > 100) continue;                 // its boss stopped ticking (unloaded, gone)
            Coliseum c = new Coliseum(BlockPos.of(e.getKey()), Vec3.ZERO);
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
        return t;
    }

    public static Coliseum load(CompoundTag t) {
        return new Coliseum(BlockPos.of(t.getLong("Center")), new Vec3(t.getDouble("GX"), t.getDouble("GY"), t.getDouble("GZ")));
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
