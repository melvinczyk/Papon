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
 * Gerbin's present laid on a bed (BodyPillowItem, used on a bed): it slouches against the headboard, sat on the
 * mattress with its top leaning on the wall (client/BodyPillowRenderer); with someone asleep in the bed it lies along
 * one side of it instead, Makima turned toward the middle, and they sleep on their side facing her (client/PillowClient).
 * Right-click it and you climb on the bed with it (you ride it: the hump, client/PillowClient; the bed creaks, hearts;
 * sneak to get off); sneak + right-click to take it back. If the bed goes, it drops.
 *
 * Sits at the head of the bed; {@link #side()} is the side it lies on when someone sleeps there.
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
        Vec3 at = e.slouchPlace();
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

    /** On the mattress, halfway along the bed, off to its side (where it lies while someone sleeps in the bed). */
    public Vec3 restingPlace() {
        Direction f = facing(), sd = side();
        Vec3 head = Vec3.atBottomCenterOf(bed());
        return head.add(-f.getStepX() * 0.5 + sd.getStepX() * SIDE, 0.5625, -f.getStepZ() * 0.5 + sd.getStepZ() * SIDE);
    }

    /** Where it slouches: sat on the mattress at the head of the bed, against the headboard (the bottom of the fold). */
    public Vec3 slouchPlace() {
        Direction f = facing();
        Vec3 head = Vec3.atBottomCenterOf(bed());
        return head.add(f.getStepX() * 0.1, 0.5625, f.getStepZ() * 0.1);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_BED, BlockPos.ZERO);
        entityData.define(DATA_FACING, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount % 20 == 0 && !level().getBlockState(bed()).is(BlockTags.BEDS)) {
            dropIt();
            return;
        }
        if (getFirstPassenger() instanceof Player p && level() instanceof net.minecraft.server.level.ServerLevel server) {
            // the bed creaking in time (client/PillowClient.thrust: a stroke every 7 ticks), hearts, a word now and then
            int t = p.tickCount;
            if (t % 7 == 2) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.PLAYERS, 0.7F, 0.6F + random.nextFloat() * 0.15F);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 0.8F);
            }
            if (t % 20 == 5) server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.3, 0.3, 0.02);
            if (t % 90 == 30) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.papon.body_pillow.hump." + random.nextInt(4))
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
            }
        }
    }

    // ---- climbing on the bed with her

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;                 // up on the knees (client/PillowClient sinks them into the mattress), not sat
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Direction f = facing();
        Vec3 at = slouchPlace().subtract(f.getStepX() * 0.62, 0, f.getStepZ() * 0.62);
        move.accept(passenger, at.x, at.y, at.z);
        if (passenger instanceof net.minecraft.world.entity.LivingEntity le) le.setYBodyRot(f.toYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        Direction f = facing();
        Vec3 foot = Vec3.atBottomCenterOf(bed()).subtract(f.getStepX() * 2.0, 0, f.getStepZ() * 2.0);
        return new Vec3(foot.x, bed().getY() + 0.6, foot.z);
    }

    private void dropIt() {
        spawnAtLocation(new ItemStack(ModItems.BODY_PILLOW.get()), 0.3F);
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            // onto the bed with her
            if (isVehicle() || player.isPassenger() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (level().isClientSide) return InteractionResult.SUCCESS;
            player.startRiding(this);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.papon.body_pillow.climb")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
            return InteractionResult.CONSUME;
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ejectPassengers();
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
