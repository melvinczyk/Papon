package com.groid.papon.journey;

import com.groid.papon.PaponMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Taco Truck. Never part of world generation: it is placed from
 * data/papon/structures/taco_truck.nbt only when a Wandering Papon arrives (or by /papon).
 *
 * The template is authored with its serving window on the +Z side. {@link #WINDOW} is the
 * ordering spot, the ground cell right in front of the counter, and is what gets pinned to the
 * requested window position. {@link #VENDOR} is the floor cell the Papon working the window
 * stands on, {@link #GRIDDLE} the stove behind him. To replace the truck, save a new build with
 * a structure block as papon:taco_truck, window on the south side, and update these three.
 *
 * The line runs straight out from the window; {@link #place} levels a lane for it too.
 */
public final class TacoTruck {
    public static final ResourceLocation TEMPLATE = PaponMod.id("taco_truck");
    /** Template-local ordering spot, in front of the counter. */
    private static final BlockPos WINDOW = new BlockPos(4, 0, 7);
    /** Template-local cell the vendor stands in (feet), behind the counter. */
    private static final BlockPos VENDOR = new BlockPos(4, 1, 4);
    /** Template-local griddle (campfire) at the back of the kitchen. */
    private static final BlockPos GRIDDLE = new BlockPos(3, 1, 1);
    /** Headroom cleared above the footprint before placing, so trees and tall grass do not poke through. */
    private static final int CLEAR_ABOVE = 4;
    private static final int MAX_FILL_BELOW = 8;

    private TacoTruck() {}

    /** Where a placed truck put things. */
    public record Placement(BoundingBox box, TacoLine line, Vec3 vendorPos, float vendorYaw, Vec3 griddle) {}

    /**
     * Places the truck with its ordering spot at {@code window} and the window facing
     * {@code out}, and levels a lane {@code laneLength} blocks long for the line. Returns empty if
     * the template is missing.
     */
    public static Optional<Placement> place(ServerLevel level, BlockPos window, Direction out, int laneLength) {
        Optional<StructureTemplate> found = level.getStructureManager().get(TEMPLATE);
        if (found.isEmpty()) {
            PaponMod.LOG.error("Taco Truck template {} is missing", TEMPLATE);
            return Optional.empty();
        }
        StructureTemplate template = found.get();
        Rotation rotation = rotationFacing(out);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .setIgnoreEntities(true);

        BlockPos origin = window.subtract(StructureTemplate.calculateRelativePosition(settings, WINDOW));
        BoundingBox box = template.getBoundingBox(settings, origin);
        TacoLine line = new TacoLine(window.immutable(), out);

        prepareGround(level, box, window.getY());
        prepareLane(level, line, laneLength);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);

        BlockPos vendorCell = origin.offset(StructureTemplate.calculateRelativePosition(settings, VENDOR));
        Vec3 vendorPos = Vec3.atBottomCenterOf(vendorCell).add(out.getStepX() * 0.4, 0, out.getStepZ() * 0.4);
        BlockPos griddleCell = origin.offset(StructureTemplate.calculateRelativePosition(settings, GRIDDLE));

        arrivalEffects(level, box, line, laneLength);
        PaponMod.LOG.info("Taco Truck placed at {}, window {} facing {}", origin, window, out);
        return Optional.of(new Placement(box, line, vendorPos, out.toYRot(), Vec3.atCenterOf(griddleCell)));
    }

    /** Rotation that turns the template's +Z (south) window to face {@code dir}. */
    private static Rotation rotationFacing(Direction dir) {
        for (Rotation r : Rotation.values()) {
            if (r.rotate(Direction.SOUTH) == dir) return r;
        }
        return Rotation.NONE;
    }

    /**
     * Of the four ways a truck could stand in front of {@code spot} (window {@code gap} blocks ahead,
     * truck beyond it), the one over the flattest, driest, loaded ground. {@code preferred} wins ties.
     */
    public static Direction bestFacing(ServerLevel level, BlockPos spot, Direction preferred, int gap) {
        Direction best = preferred;
        double bestScore = Double.MAX_VALUE;
        Direction d = preferred;
        for (int i = 0; i < 4; i++, d = d.getClockWise()) {
            double score = footprintScore(level, spot, d, gap) + (d == preferred ? 0 : 6);
            if (score < bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    private static double footprintScore(ServerLevel level, BlockPos spot, Direction ahead, int gap) {
        Direction side = ahead.getClockWise();
        double score = 0;
        for (int along = -3; along <= gap + 9; along += 2) {
            for (int lat = -6; lat <= 6; lat += 2) {
                int x = spot.getX() + ahead.getStepX() * along + side.getStepX() * lat;
                int z = spot.getZ() + ahead.getStepZ() * along + side.getStepZ() * lat;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    score += 40;
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                score += Math.min(12, Math.abs(y - spot.getY()));
                if (level.getFluidState(new BlockPos(x, y - 1, z)).is(FluidTags.WATER)) score += 8;
            }
        }
        return score;
    }

    /** Levels the footprint (plus a one-block apron) to {@code groundY}: fills dips, clears above. */
    private static void prepareGround(ServerLevel level, BoundingBox box, int groundY) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                levelColumn(level, x, z, groundY, box.maxY() + CLEAR_ABOVE, grass);
            }
        }
    }

    /** A flat strip for the line: a dirt path down the middle, grass for the walkway beside it. */
    private static void prepareLane(ServerLevel level, TacoLine line, int length) {
        Direction out = line.out();
        Direction side = line.side();
        BlockPos w = line.window();
        for (int along = 0; along <= length; along++) {
            for (int lat = -1; lat <= 3; lat++) {
                int x = w.getX() + out.getStepX() * along + side.getStepX() * lat;
                int z = w.getZ() + out.getStepZ() * along + side.getStepZ() * lat;
                BlockState top = lat == 0 && along >= 1 ? Blocks.DIRT_PATH.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState();
                levelColumn(level, x, z, w.getY(), w.getY() + CLEAR_ABOVE, top);
            }
        }
    }

    private static void levelColumn(ServerLevel level, int x, int z, int groundY, int clearTo, BlockState top) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, groundY, z);
        for (int y = groundY; y <= clearTo; y++) {
            p.setY(y);
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        p.setY(groundY - 1);
        level.setBlock(p, top, Block.UPDATE_CLIENTS);
        for (int y = groundY - 2, n = 0; n < MAX_FILL_BELOW; y--, n++) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (s.isSolidRender(level, p)) break;
            level.setBlock(p, dirt, Block.UPDATE_CLIENTS);
        }
    }

    private static void arrivalEffects(ServerLevel level, BoundingBox box, TacoLine line, int laneLength) {
        Vec3i c = box.getCenter();
        for (int i = 0; i < 6; i++) {
            level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, box.minY() + 1 + i * 0.6, c.getZ() + 0.5,
                    40, box.getXSpan() * 0.3, 0.4, box.getZSpan() * 0.3, 0.02);
        }
        for (int i = 0; i <= laneLength; i += 2) {
            Vec3 s = line.slot(0).add(line.out().getStepX() * i, 0, line.out().getStepZ() * i);
            level.sendParticles(ParticleTypes.CLOUD, s.x, s.y + 0.8, s.z, 12, 0.6, 0.6, 0.6, 0.02);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX() + 0.5, box.minY() + 2, c.getZ() + 0.5,
                40, box.getXSpan() * 0.4, 1.5, box.getZSpan() * 0.4, 0.0);
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(),
                SoundSource.NEUTRAL, 3.0F, 1.4F);
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 2.0F, 1.0F);
    }

    /** A truck already stands within {@code radius} of the spot. */
    @Nullable
    public static BlockPos existingNear(ServerLevel level, BlockPos spot, int radius) {
        for (BlockPos t : JourneyData.get(level).trucks()) {
            if (JourneyPlanner.horizontalDistSqr(t, spot) <= (double) radius * radius) return t;
        }
        return null;
    }
}
