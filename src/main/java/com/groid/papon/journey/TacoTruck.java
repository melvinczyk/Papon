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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Taco Truck. Never part of world generation: it is placed from
 * data/papon/structures/taco_truck.nbt only when a Wandering Papon arrives, right in front of him.
 *
 * The template is authored with its serving window on the +Z side. {@link #WINDOW} is the ground
 * block just outside that window, and is what gets pinned to the spot in front of Papon. To
 * replace the truck, save a new build with a structure block as papon:taco_truck, keeping the
 * window on the south side, and update WINDOW to match.
 */
public final class TacoTruck {
    public static final ResourceLocation TEMPLATE = PaponMod.id("taco_truck");
    /** Template-local ground cell outside the serving window. */
    private static final BlockPos WINDOW = new BlockPos(4, 0, 8);
    /** Headroom cleared above the footprint before placing, so trees and tall grass do not poke through. */
    private static final int CLEAR_ABOVE = 4;
    private static final int MAX_FILL_BELOW = 8;

    private TacoTruck() {}

    /**
     * Places the truck so its window faces back toward {@code customerSpot}, with the window
     * {@code gap} blocks along {@code facing} from it. Returns the bounding box, or empty if the
     * template is missing.
     */
    public static Optional<BoundingBox> place(ServerLevel level, BlockPos customerSpot, Direction facing, int gap) {
        Optional<StructureTemplate> found = level.getStructureManager().get(TEMPLATE);
        if (found.isEmpty()) {
            PaponMod.LOG.error("Taco Truck template {} is missing", TEMPLATE);
            return Optional.empty();
        }
        StructureTemplate template = found.get();
        Rotation rotation = rotationFacing(facing.getOpposite());
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .setIgnoreEntities(true);

        BlockPos windowWorld = customerSpot.relative(facing, gap);
        BlockPos windowOffset = StructureTemplate.calculateRelativePosition(settings, WINDOW);
        BlockPos origin = windowWorld.subtract(windowOffset);
        BoundingBox box = template.getBoundingBox(settings, origin);

        prepareGround(level, box, customerSpot.getY());
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
        arrivalEffects(level, box);
        JourneyData.get(level).addTruck(customerSpot);
        PaponMod.LOG.info("Taco Truck placed at {} facing {}", origin, facing.getOpposite());
        return Optional.of(box);
    }

    /** Rotation that turns the template's +Z (south) window to face {@code dir}. */
    private static Rotation rotationFacing(Direction dir) {
        for (Rotation r : Rotation.values()) {
            if (r.rotate(Direction.SOUTH) == dir) return r;
        }
        return Rotation.NONE;
    }

    /** Levels the footprint (plus a one-block apron) to {@code groundY}: fills dips, clears above. */
    private static void prepareGround(ServerLevel level, BoundingBox box, int groundY) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, groundY, z);
                for (int y = groundY; y <= box.maxY() + CLEAR_ABOVE; y++) {
                    p.setY(y);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                p.setY(groundY - 1);
                level.setBlock(p, grass, Block.UPDATE_CLIENTS);
                for (int y = groundY - 2, n = 0; n < MAX_FILL_BELOW; y--, n++) {
                    p.setY(y);
                    BlockState s = level.getBlockState(p);
                    if (s.isSolidRender(level, p)) break;
                    level.setBlock(p, dirt, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void arrivalEffects(ServerLevel level, BoundingBox box) {
        Vec3i c = box.getCenter();
        for (int i = 0; i < 6; i++) {
            level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, box.minY() + 1 + i * 0.6, c.getZ() + 0.5,
                    40, box.getXSpan() * 0.3, 0.4, box.getZSpan() * 0.3, 0.02);
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
