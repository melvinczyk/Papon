package com.groid.papon.boss;

import com.groid.papon.PaponMod;
import com.groid.papon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
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

/**
 * Chef Papon's pocket dimension, "La Dimensión Papa": what it really is is a dome, built high in
 * the sky straight above the arena, that everyone in the fight is pulled up into at the end of his
 * scream. A flat floor of potato flesh, a hemisphere of potato skin lit by glowing potato eyes,
 * and four giant potatoes to hide behind.
 *
 * It is built only into air (nothing anyone made is touched) a few hundred blocks at a tick while
 * the cutscene runs, and taken down when the fight ends (his death, his removal, or nobody left
 * in it): players, the van and anything dropped in it go back down to where they were relative to
 * the arena. The blocks cannot be broken, so the fight stays in it. Anyone who logs back in after
 * the dome is gone, still standing where it was, is put back on the ground ({@link #onLogin}).
 */
public final class PotatoDome {
    public static final int RADIUS = 30;
    private static final int MIN_RADIUS = 18;
    private static final int PER_TICK = 700;

    /** The centre of the floor's top face (where you stand). */
    public final BlockPos center;
    public final int radius;
    /** The middle of the arena on the ground, where everyone goes back to. */
    public final Vec3 ground;
    @Nullable private List<long[]> pending;

    private PotatoDome(BlockPos center, int radius, Vec3 ground) {
        this.center = center;
        this.radius = radius;
        this.ground = ground;
    }

    /** A dome over {@code arena}, as high as it fits under the build limit; null if the sky has no room. */
    @Nullable
    public static PotatoDome plan(ServerLevel level, Vec3 arena) {
        int top = level.getMaxBuildHeight() - 4;
        int highest = level.getMinBuildHeight();
        for (int dx = -RADIUS; dx <= RADIUS; dx += 6) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += 6) {
                highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(arena.x) + dx, Mth.floor(arena.z) + dz));
            }
        }
        int r = RADIUS;
        int floor = top - r - 2;
        if (floor < highest + 10) {
            floor = highest + 10;
            r = top - floor - 2;
        }
        if (r < MIN_RADIUS) return null;
        return new PotatoDome(new BlockPos(Mth.floor(arena.x), floor, Mth.floor(arena.z)), r, arena);
    }

    public double floorY() {
        return center.getY();
    }

    public Vec3 centerVec() {
        return Vec3.atBottomCenterOf(center);
    }

    // ------------------------------------------------------------------ building

    /** Works out every block of the dome; {@link #buildStep} then places them. */
    public void startBuild(ServerLevel level) {
        List<long[]> out = new ArrayList<>();
        RandomSource rng = RandomSource.create(center.asLong());
        int r = radius;
        int skin = Block.getId(ModBlocks.POTATO_SKIN.get().defaultBlockState());
        int flesh = Block.getId(ModBlocks.POTATO_FLESH.get().defaultBlockState());
        int eye = Block.getId(ModBlocks.POTATO_EYE.get().defaultBlockState());
        // the floor, a layer of skin under it so it is solid from below too
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.4) continue;
                int block = flesh;
                if (d > r - 3.5) block = skin;                                   // a russet rim
                else if (Math.abs(d - r * 0.5) < 0.5) block = skin;             // a ring in the middle
                else if (dx % 7 == 0 && dz % 7 == 0 && d < r - 5) block = eye;  // lights in the floor
                out.add(new long[]{center.offset(dx, -1, dz).asLong(), block});
                out.add(new long[]{center.offset(dx, -2, dz).asLong(), skin});
            }
        }
        // the shell
        for (int dy = 0; dy <= r + 1; dy++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < r - 1.6 || d >= r + 0.4) continue;
                    out.add(new long[]{center.offset(dx, dy, dz).asLong(), rng.nextFloat() < 0.045F ? eye : skin});
                }
            }
        }
        // four giant potatoes for cover, on the diagonals halfway out
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2 + rng.nextFloat() * 0.3;
            int cx = (int) Math.round(Math.cos(a) * r * 0.55);
            int cz = (int) Math.round(Math.sin(a) * r * 0.55);
            double rx = 3.2, ry = 2.6, rz = 2.4;
            double yaw = rng.nextFloat() * Math.PI;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy <= 5; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        double u = dx * Math.cos(yaw) + dz * Math.sin(yaw);
                        double w = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
                        double v = dy - 2.2;
                        if ((u * u) / (rx * rx) + (v * v) / (ry * ry) + (w * w) / (rz * rz) > 1) continue;
                        out.add(new long[]{center.offset(cx + dx, dy, cz + dz).asLong(), rng.nextFloat() < 0.06F ? eye : skin});
                    }
                }
            }
        }
        pending = out;
    }

    /** Places the next batch; true once the whole dome is up. */
    public boolean buildStep(ServerLevel level) {
        if (pending == null) return true;
        int n = 0;
        while (!pending.isEmpty() && n < PER_TICK) {
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

    /** Takes every dome block in its bounds down again (and only those). */
    public void teardown(ServerLevel level) {
        int r = radius + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        for (int dy = -3; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (ModBlocks.isDome(level.getBlockState(p))) {
                        level.setBlock(p, air, Block.UPDATE_CLIENTS);
                        removed++;
                    }
                }
            }
        }
        pending = null;
        PaponMod.LOG.info("[boss] the potato dome at {} is gone ({} blocks)", center, removed);
    }

    // ------------------------------------------------------------------ getting in and out

    public AABB bounds() {
        return new AABB(center).inflate(radius + 1, 0, radius + 1).expandTowards(0, radius + 2, 0).move(0, -3, 0);
    }

    public boolean contains(Vec3 p) {
        return bounds().contains(p);
    }

    /** Where someone at {@code from} on the ground lands in the dome: same bearing, pulled inside. */
    public Vec3 inside(Vec3 from, double minFromCenter) {
        Vec3 off = new Vec3(from.x - ground.x, 0, from.z - ground.z);
        double len = off.length();
        double max = radius - 6;
        if (len < 1e-3) off = new Vec3(0, 0, minFromCenter);
        else if (len > max) off = off.scale(max / len);
        else if (len < minFromCenter) off = off.scale(minFromCenter / len);
        return centerVec().add(off);
    }

    /** The way back down: same offset from the arena's middle, on top of whatever is there. */
    public Vec3 outside(ServerLevel level, Vec3 from) {
        double x = ground.x + (from.x - center.getX() - 0.5);
        double z = ground.z + (from.z - center.getZ() - 0.5);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (y >= center.getY() - 3) y = (int) Math.floor(ground.y);
        return new Vec3(x, y, z);
    }

    /** Everyone and everything still in the dome goes back to the ground. */
    public void sendEveryoneHome(ServerLevel level) {
        for (Entity e : level.getEntities((Entity) null, bounds(), e -> e instanceof Player || e instanceof ItemEntity
                || e instanceof ExperienceOrb || e instanceof com.groid.papon.vehicle.CaravanEntity || e instanceof PaponBossEntity)) {
            if (e.isPassenger()) e.stopRiding();
            Vec3 to = outside(level, e.position());
            send(level, e, to);
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

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Center", center.asLong());
        t.putInt("Radius", radius);
        t.putDouble("GX", ground.x);
        t.putDouble("GY", ground.y);
        t.putDouble("GZ", ground.z);
        return t;
    }

    public static PotatoDome load(CompoundTag t) {
        return new PotatoDome(BlockPos.of(t.getLong("Center")), t.getInt("Radius"),
                new Vec3(t.getDouble("GX"), t.getDouble("GY"), t.getDouble("GZ")));
    }

    /** Logged out in the dome and back after it came down: put them on the ground, not in the sky. */
    @Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
    public static final class Strays {
        private Strays() {}

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return;
            ServerLevel level = sp.serverLevel();
            if (sp.getY() < level.getMaxBuildHeight() - RADIUS * 2 - 16) return;
            BlockPos at = sp.blockPosition();
            for (int dy = 1; dy <= 48; dy++) {
                if (!level.getBlockState(at.below(dy)).isAir()) return;           // something under them: leave them be
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            if (y >= sp.getY() - 48) return;
            send(level, sp, new Vec3(sp.getX(), y, sp.getZ()));
            PaponMod.LOG.info("[boss] {} logged in where a potato dome used to be: put back on the ground", sp.getName().getString());
        }
    }
}
