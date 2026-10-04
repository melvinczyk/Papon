package com.groid.papon.entity;

import com.groid.papon.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Gerbin's present laid on a bed (BodyPillowItem, used on a bed): it lies along one side of the mattress,
 * Makima turned toward the middle, and whoever sleeps in that bed sleeps on their side, facing her
 * (client/PillowClient). Right-click it to pick it back up; if the bed goes, it drops.
 *
 * Sits at the middle of the bed's length, {@link #SIDE} over from its centre line; {@link #side()} is the
 * direction it lies on (the sleeper is rolled to face it).
 */
public class BodyPillowEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> DATA_BED = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_FACING = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.INT);
    /** How far off the bed's centre line it lies (the sleeper moves over the other way). */
    public static final double SIDE = 0.27;

    public BodyPillowEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Lays a pillow on the bed at {@code pos} (either half); null if there is no bed there, or one already has a pillow. */
    @Nullable
    public static BodyPillowEntity place(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BedBlock)) return null;
        Direction facing = s.getValue(BedBlock.FACING);
        BlockPos head = s.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(facing);
        if (onBed(level, head) != null) return null;
        BodyPillowEntity e = new BodyPillowEntity(com.groid.papon.registry.ModEntities.BODY_PILLOW.get(), level);
        e.entityData.set(DATA_BED, head);
        e.entityData.set(DATA_FACING, facing.get2DDataValue());
        Vec3 at = e.restingPlace();
        e.moveTo(at.x, at.y, at.z, facing.toYRot(), 0);
        level.addFreshEntity(e);
        return e;
    }

    /** The pillow on the bed whose head half is at {@code head}, if there is one. */
    @Nullable
    public static BodyPillowEntity onBed(Level level, BlockPos head) {
        return level.getEntitiesOfClass(BodyPillowEntity.class, new net.minecraft.world.phys.AABB(head).inflate(2.5),
                e -> e.bed().equals(head)).stream().findFirst().orElse(null);
    }

    public BlockPos bed() {
        return entityData.get(DATA_BED);
    }

    /** The way the bed points, foot to head. */
    public Direction facing() {
        return Direction.from2DDataValue(entityData.get(DATA_FACING));
    }

    /** The side of the bed it lies on (to the right of someone lying in it, head on the pillow end). */
    public Direction side() {
        return facing().getCounterClockWise();
    }

    /** On the mattress, halfway along the bed, off to its side. */
    public Vec3 restingPlace() {
        Direction f = facing(), sd = side();
        Vec3 head = Vec3.atBottomCenterOf(bed());
        return head.add(-f.getStepX() * 0.5 + sd.getStepX() * SIDE, 0.5625, -f.getStepZ() * 0.5 + sd.getStepZ() * SIDE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_BED, BlockPos.ZERO);
        entityData.define(DATA_FACING, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && !level().getBlockState(bed()).is(BlockTags.BEDS)) {
            dropIt();
        }
    }

    private void dropIt() {
        spawnAtLocation(new ItemStack(ModItems.BODY_PILLOW.get()), 0.3F);
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack pillow = new ItemStack(ModItems.BODY_PILLOW.get());
        if (!player.getInventory().add(pillow)) player.drop(pillow, false);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide && source.getEntity() instanceof Player) dropIt();
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.BODY_PILLOW.get());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_BED, BlockPos.of(tag.getLong("Bed")));
        entityData.set(DATA_FACING, tag.getInt("Facing"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Bed", bed().asLong());
        tag.putInt("Facing", entityData.get(DATA_FACING));
    }
}
