package com.groid.papon.entity;

import com.groid.papon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Gerbin's present laid on a bed (BodyPillowItem, used on a bed). Two ways to leave her (sneak + right-click switches):
 *   slouched  sat on the mattress at the head of the bed, her top leaning on the wall (client/BodyPillowRenderer).
 *             Right-click her and you climb on the bed with her (you ride her, client/PillowClient; the bed creaks,
 *             hearts; sneak to get off): she lies down on her back and you plank over her ({@link Act#HUMP}); jump and
 *             she rolls over onto her knees, face down and hips up, you kneeling behind her ({@link Act#BEHIND}); jump
 *             to roll her back. Off the bed, she sits back up
 *   on the edge  sat on the side of the bed facing the room, her lap on the mattress, her legs over the side and her feet
 *             just off the floor (the side with room for you: {@link #pickEdge}). Right-click her and you get down on the
 *             floor at her feet and lick them ({@link Act#FLOOR}); jump and you kneel up and lift her feet to your face
 *             instead ({@link Act#LIFT}), jump again to get back down (PaponNetwork.PillowSwap)
 * Punch her to take her back. With someone asleep in the bed she lies along one side of it instead, Makima turned toward
 * the middle, and they sleep on their side facing her (client/PillowClient). If the bed goes, she drops.
 */
public class BodyPillowEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> DATA_BED = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_FACING = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.INT);
    /** {@link #SLOUCH} or {@link #EDGE}. */
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.INT);
    /** The side of the bed she sits on in {@link #EDGE} (2D data value). */
    private static final EntityDataAccessor<Integer> DATA_EDGE = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.INT);
    /** The rider's other way: her feet lifted to their face ({@link Act#LIFT}), or her on her knees ({@link Act#BEHIND}). */
    private static final EntityDataAccessor<Boolean> DATA_LIFT = SynchedEntityData.defineId(BodyPillowEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int SLOUCH = 0, EDGE = 1;
    /** How far off the bed's centre line it lies (the sleeper moves over the other way). */
    public static final double SIDE = 0.27;
    /** The mattress's top, above the bed block's bottom. */
    public static final double MATTRESS = 0.5625;
    /** Sat on the edge: her knees (the second fold) out from the bed's middle, and up from the floor (on the mattress's edge). */
    public static final double KNEE_OUT = 0.46, KNEE_UP = 0.65;

    /**
     * What whoever is with her is doing: planked over her on her back, on the floor at her feet, kneeling with her feet held
     * up, kneeling behind her on her knees.
     */
    public enum Act { HUMP, FLOOR, LIFT, BEHIND }

    /** Laid down: her hips along the bed from its head block's centre, on her back, and on her knees (raised {@link #HIPS_UP}). */
    public static final double LIE_HIPS = 0.75, KNEEL_HIPS = 0.45, HIPS_UP = 0.295;
    /** The rider, along the bed from the head block's centre toward the foot: planked (the eyes over her face), kneeling behind her. */
    public static final double PLANK_AT = 0.35, BEHIND_AT = 0.62;

    /** Ticks someone has been with her (the hint comes after the game's "Press Shift to dismount"). */
    private int with;

    /**
     * 0 her feet down, 1 lifted to the face of whoever kneels there; on the bed 0 on her back, 1 rolled over onto her knees.
     * Eased both sides alike (the rider is placed by it too).
     */
    public float lift, liftO;
    /** 0 slouched against the headboard, 1 laid down for whoever is on the bed with her (eased). */
    public float down, downO;

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

    public BlockPos bed() { return entityData.get(DATA_BED); }

    /** The way the bed points, foot to head. */
    public Direction facing() { return Direction.from2DDataValue(entityData.get(DATA_FACING)); }

    /** The side of the bed it lies on (to the right of someone lying in it, head on the pillow end). */
    public Direction side() { return facing().getCounterClockWise(); }

    /** {@link #SLOUCH} or {@link #EDGE}. */
    public int pose() { return entityData.get(DATA_POSE); }

    /** The side of the bed she sits on, facing out, in {@link #EDGE}. */
    public Direction edge() { return Direction.from2DDataValue(entityData.get(DATA_EDGE)); }

    /** What her rider is doing, or null with nobody there. */
    @Nullable
    public Act act() {
        if (!(getFirstPassenger() instanceof Player)) return null;
        if (pose() == SLOUCH) return entityData.get(DATA_LIFT) ? Act.BEHIND : Act.HUMP;
        return entityData.get(DATA_LIFT) ? Act.LIFT : Act.FLOOR;
    }

    public float lift(float partial) { return Mth.lerp(partial, liftO, lift); }

    public float down(float partial) { return Mth.lerp(partial, downO, down); }

    /** Along the bed from its head block's centre, toward the foot, at the mattress's top. */
    public Vec3 alongBed(double back, double up) {
        Direction f = facing();
        Vec3 head = Vec3.atBottomCenterOf(bed());
        return head.add(-f.getStepX() * back, MATTRESS + up, -f.getStepZ() * back);
    }

    /** On the mattress, halfway along the bed, off to its side (where it lies while someone sleeps in the bed). */
    public Vec3 restingPlace() {
        Direction f = facing(), sd = side();
        Vec3 head = Vec3.atBottomCenterOf(bed());
        return head.add(-f.getStepX() * 0.5 + sd.getStepX() * SIDE, MATTRESS, -f.getStepZ() * 0.5 + sd.getStepZ() * SIDE);
    }

    /** Where it slouches: sat on the mattress at the head of the bed, against the headboard (the bottom of the fold). */
    public Vec3 slouchPlace() {
        Direction f = facing();
        Vec3 head = Vec3.atBottomCenterOf(bed());
        return head.add(f.getStepX() * 0.1, MATTRESS, f.getStepZ() * 0.1);
    }

    /** The middle of the bed (between its halves), on the floor. */
    public Vec3 middle() {
        Direction f = facing();
        return Vec3.atBottomCenterOf(bed()).subtract(f.getStepX() * 0.5, 0, f.getStepZ() * 0.5);
    }

    /** Sat on the edge: her knees, where her lap on the mattress turns down over its side. */
    public Vec3 kneePlace() {
        Direction e = edge();
        return middle().add(e.getStepX() * KNEE_OUT, KNEE_UP, e.getStepZ() * KNEE_OUT);
    }

    /** Sat on the edge: her feet, just off the floor, or lifted ({@code k} 1) up to a kneeling face. */
    public Vec3 feet(float k) {
        Direction e = edge();
        double out = Mth.lerp(k, KNEE_OUT + 0.08, KNEE_OUT + 0.56), up = Mth.lerp(k, 0.14, 0.75);
        return middle().add(e.getStepX() * out, up, e.getStepZ() * out);
    }

    /** Where she is, for her hitbox: on the head of the bed, or on its edge. */
    private Vec3 place() {
        if (pose() == SLOUCH) return slouchPlace();
        Direction e = edge();
        return middle().add(e.getStepX() * 0.22, MATTRESS, e.getStepZ() * 0.22);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_BED, BlockPos.ZERO);
        entityData.define(DATA_FACING, 0);
        entityData.define(DATA_POSE, SLOUCH);
        entityData.define(DATA_EDGE, 0);
        entityData.define(DATA_LIFT, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // the rider's eyes go down to the floor, up to kneeling (eyeHeight)
        if (DATA_LIFT.equals(key) || DATA_POSE.equals(key)) getPassengers().forEach(Entity::refreshDimensions);
    }

    @Override
    public void tick() {
        super.tick();
        liftO = lift;
        downO = down;
        Act act = act();
        float want = act == Act.LIFT || act == Act.BEHIND ? 1 : 0;
        lift += Mth.clamp(want - lift, -0.09F, 0.09F);
        down += Mth.clamp((act == Act.HUMP || act == Act.BEHIND ? 1 : 0) - down, -0.08F, 0.08F);
        if (level().isClientSide) return;
        if (tickCount % 20 == 0 && !level().getBlockState(bed()).is(BlockTags.BEDS)) {
            dropIt();
            return;
        }
        if (!(getFirstPassenger() instanceof Player p) || !(level() instanceof ServerLevel server)) {
            with = 0;
            return;
        }
        if (++with == 4) say(p, pose() == EDGE ? "kneel" : "climb");
        int t = p.tickCount;
        if (act() == Act.HUMP || act() == Act.BEHIND) {
            // the bed creaking in time (client/PillowClient.thrust: a stroke every 7 ticks), hearts, a word now and then
            if (t % 7 == 2) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.PLAYERS, 0.7F, 0.6F + random.nextFloat() * 0.15F);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 0.8F);
            }
            if (act() == Act.BEHIND && t % 7 == 3) server.playSound(null, p.getX(), p.getY() + 0.4, p.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.35F, 1.7F);
            if (t % 20 == 5) server.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
            if (t % 90 == 30) say(p, (act() == Act.BEHIND && random.nextBoolean() ? "behind." : "hump.") + random.nextInt(4));
            return;
        }
        // at her feet: a lick about twice a second (client/PillowClient.lick), drips off her toes, now and then a kick of
        // the heels and a heart, a word
        Vec3 toes = feet(lift);
        if (t % 11 == 5) {
            server.playSound(null, toes.x, toes.y, toes.z, SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.7F, 1.25F + random.nextFloat() * 0.3F);
            server.playSound(null, toes.x, toes.y, toes.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.45F, 1.5F);
            server.sendParticles(ParticleTypes.DRIPPING_WATER, toes.x, toes.y - 0.12, toes.z, 1, 0.1, 0.02, 0.1, 0);
        }
        if (t % 31 == 9) server.sendParticles(ParticleTypes.HEART, p.getX(), p.getEyeY() + 0.4, p.getZ(), 1, 0.25, 0.1, 0.25, 0.02);
        if (t % 70 == 35) say(p, "feet." + random.nextInt(8));
    }

    private static void say(Player p, String key) {
        p.displayClientMessage(Component.translatable("message.papon.body_pillow." + key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
    }

    // ---- sat on the edge of the bed

    /** A side of the bed with room on the floor beside it (both halves) for someone at her feet; null if neither has. */
    @Nullable
    private Direction pickEdge() {
        Direction best = null;
        int bestScore = 0;
        for (Direction e : new Direction[]{side(), side().getOpposite()}) {
            int score = room(e);
            if (score > bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    /** How much room there is beside the bed on side {@code e}: 0 none (the blocks right beside it are not clear), else more is better. */
    private int room(Direction e) {
        Direction f = facing();
        int score = 1;
        for (BlockPos half : new BlockPos[]{bed(), bed().relative(f.getOpposite())}) {
            if (!clear(half.relative(e))) return 0;
            if (clear(half.relative(e, 2))) score++;
        }
        return score;
    }

    private boolean clear(BlockPos pos) {
        return level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()
                && level().getBlockState(pos.above()).getCollisionShape(level(), pos.above()).isEmpty();
    }

    /** Sneak + right-click: sits her on the edge of the bed (feet down, facing the room), or props her back up. */
    private void rearrange(Player player) {
        if (pose() == EDGE) {
            entityData.set(DATA_POSE, SLOUCH);
            say(player, "slouch");
        } else {
            Direction e = pickEdge();
            if (e == null) {
                say(player, "no_room");
                return;
            }
            entityData.set(DATA_EDGE, e.get2DDataValue());
            entityData.set(DATA_POSE, EDGE);
            say(player, "edge");
        }
        Vec3 at = place();
        setPos(at.x, at.y, at.z);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.75F);
    }

    /**
     * Jump (PaponNetwork.PillowSwap): at her feet, kneel up and lift them to your face, or lay them down and get back on the
     * floor; on the bed, roll her over onto her knees and get behind her, or roll her back.
     */
    public void swap(Player player) {
        if (getFirstPassenger() != player) return;
        boolean up = !entityData.get(DATA_LIFT);
        entityData.set(DATA_LIFT, up);
        player.refreshDimensions();
        if (pose() == SLOUCH) {
            say(player, up ? "roll_behind" : "roll_front");
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, up ? 0.9F : 0.75F);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.PLAYERS, 0.8F, 0.55F);
            return;
        }
        say(player, up ? "lift" : "lower");
        Vec3 toes = feet(up ? 1 : 0);
        level().playSound(null, toes.x, toes.y, toes.z, SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.9F, up ? 1.1F : 0.8F);
        level().playSound(null, toes.x, toes.y, toes.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, up ? 1.2F : 0.9F);
    }

    // ---- climbing on the bed with her, getting down at her feet

    /**
     * The eye height of a player with her (Forge's EntityEvent.Size: event/CommonEvents), so the camera is where the body is
     * drawn: kneeling on the mattress, flat on the floor at her feet, kneeling on the floor. NaN for anyone else.
     */
    public static float eyeHeight(Entity entity) {
        if (!(entity instanceof Player p) || !(p.getVehicle() instanceof BodyPillowEntity pillow)) return Float.NaN;
        Act act = pillow.act();
        if (act == null) return Float.NaN;
        return switch (act) {
            case HUMP -> 0.52F;
            case BEHIND -> 1.2F;
            case FLOOR -> 0.45F;
            case LIFT -> 1.05F;
        };
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.refreshDimensions();
        // turned to her: to the headboard, or down at her feet
        float yaw = pose() == EDGE ? edge().getOpposite().toYRot() : facing().toYRot();
        passenger.setYRot(yaw);
        passenger.setYHeadRot(yaw);
        passenger.setXRot(pose() == EDGE ? 8 : 75);
        if (passenger instanceof LivingEntity le) le.setYBodyRot(yaw);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        passenger.refreshDimensions();
        if (!level().isClientSide) entityData.set(DATA_LIFT, false);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;                 // up on the knees, or flat on the floor (client/PillowClient), never sat
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (pose() == EDGE) {
            // on the floor beside the bed, the eyes just past her toes: lying flat, or kneeling a little further back
            Direction e = edge();
            double out = Mth.lerp(lift, 1.0, 1.45);
            Vec3 at = middle().add(e.getStepX() * out, 0, e.getStepZ() * out);
            move.accept(passenger, at.x, at.y, at.z);
            if (passenger instanceof LivingEntity le) le.setYBodyRot(e.getOpposite().toYRot());
            return;
        }
        // on the bed: planked over her (the eyes over her head), or kneeling behind her hips
        Direction f = facing();
        Vec3 at = alongBed(Mth.lerp(lift, PLANK_AT, BEHIND_AT), 0);
        move.accept(passenger, at.x, at.y, at.z);
        if (passenger instanceof LivingEntity le) le.setYBodyRot(f.toYRot());
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (pose() != EDGE) return;
        // at her feet you look at them, not round the room (the body is down on the floor, facing the bed)
        float yaw = edge().getOpposite().toYRot();
        float off = Mth.wrapDegrees(passenger.getYRot() - yaw), kept = Mth.clamp(off, -70, 70);
        passenger.yRotO += kept - off;
        passenger.setYRot(passenger.getYRot() + kept - off);
        passenger.setYHeadRot(passenger.getYRot());
        if (passenger instanceof LivingEntity le) le.setYBodyRot(yaw);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (pose() == EDGE) {
            Direction e = edge();
            Vec3 at = middle().add(e.getStepX() * 1.3, 0, e.getStepZ() * 1.3);
            return new Vec3(at.x, bed().getY() + 0.05, at.z);
        }
        Direction f = facing();
        Vec3 foot = Vec3.atBottomCenterOf(bed()).subtract(f.getStepX() * 2.0, 0, f.getStepZ() * 2.0);
        return new Vec3(foot.x, bed().getY() + 0.6, foot.z);
    }

    private void dropIt() {
        spawnAtLocation(new ItemStack(ModItems.BODY_PILLOW.get()), 0.3F);
        discard();
    }

    /** Back into their arms (their inventory, or at their feet if it is full). */
    private void takeBack(Player player) {
        ejectPassengers();
        ItemStack pillow = new ItemStack(ModItems.BODY_PILLOW.get());
        if (!player.getInventory().add(pillow)) player.drop(pillow, false);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
        say(player, "take");
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            // move her: sat on the edge, or back against the headboard
            if (isVehicle()) return InteractionResult.PASS;
            if (level().isClientSide) return InteractionResult.SUCCESS;
            rearrange(player);
            return InteractionResult.CONSUME;
        }
        if (isVehicle() || player.isPassenger()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (pose() == EDGE && room(edge()) == 0) {
            say(player, "no_room");
            return InteractionResult.CONSUME;
        }
        // onto the bed with her, or down on the floor at her feet
        entityData.set(DATA_LIFT, false);
        player.startRiding(this);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide && isAlive() && source.getEntity() instanceof Player p) takeBack(p);
        return true;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public ItemStack getPickResult() { return new ItemStack(ModItems.BODY_PILLOW.get()); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_BED, BlockPos.of(tag.getLong("Bed")));
        entityData.set(DATA_FACING, tag.getInt("Facing"));
        entityData.set(DATA_POSE, tag.getInt("Pose"));
        entityData.set(DATA_EDGE, tag.getInt("Edge"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Bed", bed().asLong());
        tag.putInt("Facing", entityData.get(DATA_FACING));
        tag.putInt("Pose", pose());
        tag.putInt("Edge", entityData.get(DATA_EDGE));
    }
}
