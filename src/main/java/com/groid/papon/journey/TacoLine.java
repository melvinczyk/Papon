package com.groid.papon.journey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.Vec3;

/**
 * Where the line for a Taco Truck stands. Slot 0 is the ordering spot right at the serving
 * window; slot i is i * {@link #SPACING} blocks straight out from it along {@code out}. Customers
 * who have their taco walk back down the walkway {@link #WALKWAY} blocks to the {@link #side()}
 * of the line, so they pass whoever is waiting in it.
 */
public record TacoLine(BlockPos window, Direction out) {
    public static final double SPACING = 1.5;
    public static final double WALKWAY = 2.0;

    public Vec3 slot(int i) {
        return Vec3.atBottomCenterOf(window).add(out.getStepX() * i * SPACING, 0, out.getStepZ() * i * SPACING);
    }

    /** Right-hand side of the line, looking out from the truck. */
    public Direction side() {
        return out.getClockWise();
    }

    /** A point on the walkway {@code along} blocks out from the window. */
    public Vec3 walkway(double along) {
        Direction s = side();
        return Vec3.atBottomCenterOf(window).add(out.getStepX() * along + s.getStepX() * WALKWAY, 0,
                out.getStepZ() * along + s.getStepZ() * WALKWAY);
    }

    /** What people in line look at: the counter, a block and a half up. */
    public Vec3 counter() {
        return Vec3.atBottomCenterOf(window).add(-out.getStepX() * 1.2, 1.5, -out.getStepZ() * 1.2);
    }

    /** Distance along the line from the window, for a position. */
    public double along(Vec3 pos) {
        Vec3 w = Vec3.atBottomCenterOf(window);
        return (pos.x - w.x) * out.getStepX() + (pos.z - w.z) * out.getStepZ();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Window", NbtUtils.writeBlockPos(window));
        t.putInt("Out", out.get3DDataValue());
        return t;
    }

    public static TacoLine load(CompoundTag t) {
        Direction d = Direction.from3DDataValue(t.getInt("Out"));
        return new TacoLine(NbtUtils.readBlockPos(t.getCompound("Window")), d.getAxis().isHorizontal() ? d : Direction.SOUTH);
    }
}
