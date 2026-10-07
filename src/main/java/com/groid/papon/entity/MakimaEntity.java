package com.groid.papon.entity;

import com.groid.papon.effect.PostNutClarityEffect;
import com.groid.papon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Makima, the real thing (the user's model: tools/gen_makima_mesh.py, client/MakimaRenderer). From her spawn egg she stands
 * where she was put, watching you, her mouth watering when you come close (drool off her lips: MakimaRenderer.drool).
 * Right-click her and you are hers ({@link Position#MISSIONARY} first; you ride her, client/PillowClient poses you); jump for
 * the next position, sneak to get off. Each position is a held pose (gen_makima_anims.py) with you placed against it
 * ({@link #positionRider}); she drools the whole time. Long enough at it and you finish: Post Nut Clarity. Punch her and she
 * goes back in the egg. Nothing else hurts her (only /kill), and she never despawns.
 */
public class MakimaEntity extends PathfinderMob implements GeoEntity {
    /** Synced as an ordinal: append new positions at the end. */
    public enum Position {
        STAND, MISSIONARY, DOGGY, BENT, COWGIRL;

        /** The rider's act, for the poses and eye heights shared with the body pillow (client/PillowClient). */
        @Nullable
        public BodyPillowEntity.Act act() {
            return switch (this) {
                case STAND -> null;
                case MISSIONARY -> BodyPillowEntity.Act.HUMP;
                case DOGGY -> BodyPillowEntity.Act.BEHIND;
                case BENT -> BodyPillowEntity.Act.SIDE_KNEES;
                case COWGIRL -> BodyPillowEntity.Act.UNDER;
            };
        }
    }

    private static final EntityDataAccessor<Integer> DATA_POSITION = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.INT);
    /** Her yaw while someone is with her: turned to face where they came at her from, held there. */
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.FLOAT);

    /**
     * The rider, out from her feet along her facing (blocks; negative behind her): planked over her face (she lies back, her
     * head behind her), kneeling behind her hips, standing behind her, on your back under her with your head past her knees.
     */
    public static final double MISSIONARY_AT = -1.44, DOGGY_AT = 0.36, BENT_AT = 0.42, COWGIRL_AT = 0.87;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.makima.idle");
    private static final RawAnimation[] POSES = {IDLE, RawAnimation.begin().thenLoop("animation.makima.missionary"),
            RawAnimation.begin().thenLoop("animation.makima.doggy"), RawAnimation.begin().thenLoop("animation.makima.bent"),
            RawAnimation.begin().thenLoop("animation.makima.cowgirl")};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int with;
    /** Client: the tick of her last drop of drool (MakimaRenderer). */
    public int drooled;

    public MakimaEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10F, 1F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_POSITION, 0);
        entityData.define(DATA_YAW, 0F);
    }

    public Position posture() { return Position.values()[Mth.clamp(entityData.get(DATA_POSITION), 0, Position.values().length - 1)]; }

    /** What her rider is doing, or null with nobody there. */
    @Nullable
    public BodyPillowEntity.Act act() {
        return getFirstPassenger() instanceof Player ? posture().act() : null;
    }

    /** Her facing while held (unit, horizontal). */
    public Vec3 facing() {
        float yaw = entityData.get(DATA_YAW) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    public float heldYaw() { return entityData.get(DATA_YAW); }

    /** The eye height of a player with her (CommonEvents.onSize), NaN for anyone else or standing. */
    public static float eyeHeight(Entity entity) {
        if (!(entity instanceof Player p) || !(p.getVehicle() instanceof MakimaEntity m)) return Float.NaN;
        return switch (m.posture()) {
            case MISSIONARY -> 0.68F;
            case DOGGY -> 1.42F;
            case COWGIRL -> 0.2F;
            default -> Float.NaN;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (isVehicle()) {
            // held where she lay down for you
            float yaw = heldYaw();
            setYRot(yaw);
            yRotO = yaw;
            yBodyRot = yaw;
            yBodyRotO = yaw;
            yHeadRot = yaw;
            yHeadRotO = yaw;
            setXRot(0);
        }
        if (level().isClientSide) return;
        if (!(getFirstPassenger() instanceof ServerPlayer p) || !(level() instanceof ServerLevel server)) {
            with = 0;
            if (posture() != Position.STAND) entityData.set(DATA_POSITION, Position.STAND.ordinal());
            return;
        }
        if (++with == 4) say(p, "start");
        if (PostNutClarityEffect.humped(p)) {
            say(p, "done");
            ejectPassengers();
            return;
        }
        // in time with the thrust (client/PillowClient.thrust: a stroke every 7 ticks), hearts, a word from her now and then
        int t = p.tickCount;
        if (t % 7 == 2) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.45F, 1.5F + random.nextFloat() * 0.2F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.5F, 0.8F);
        }
        if (t % 20 == 5) server.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.9, getZ(), 2, 0.3, 0.2, 0.3, 0.02);
        if (t % 90 == 30) say(p, posture().name().toLowerCase() + "." + random.nextInt(4));
    }

    private static void say(Player p, String key) {
        p.displayClientMessage(Component.translatable("message.papon.makima." + key).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
    }

    /** One thrust in this many into the body pillow (in hand or on a bed) wakes her up as the real thing ({@link #awaken}). */
    public static final int AWAKEN_ODDS = 5000;

    /** A thrust into the body pillow: true once in {@link #AWAKEN_ODDS} (-Dpapon.makima.odds to test). */
    public static boolean rollAwaken(ServerPlayer player) {
        return player.getRandom().nextInt(Math.max(1, Integer.getInteger("papon.makima.odds", AWAKEN_ODDS))) == 0;
    }

    /**
     * The body pillow becomes Makima, mid-thrust: she appears against whoever was humping it, in {@code at} (the position
     * nearest what they were doing), facing them, and they are with her (they stay {@code where} they were, on the bed or
     * off it; she is put where that position needs her). A flash, the sound of something waking, her name on their screen.
     */
    public static void awaken(ServerLevel level, ServerPlayer player, Position at, Vec3 where) {
        if (player.isPassenger()) player.stopRiding();
        Vec3 look = player.getLookAngle();
        Vec3 f = new Vec3(-look.x, 0, -look.z);
        f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();        // her facing: back at them
        double back = switch (at) {
            case MISSIONARY -> MISSIONARY_AT;
            case DOGGY -> DOGGY_AT;
            case BENT -> BENT_AT;
            case COWGIRL -> COWGIRL_AT;
            default -> 1.0;
        };
        Vec3 pos = where.subtract(f.scale(back));
        MakimaEntity m = new MakimaEntity(com.groid.papon.registry.ModEntities.MAKIMA.get(), level);
        float yaw = (float) (Mth.atan2(-f.x, f.z) * Mth.RAD_TO_DEG);
        m.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        m.entityData.set(DATA_YAW, yaw);
        m.entityData.set(DATA_POSITION, at.ordinal());
        level.addFreshEntity(m);
        player.startRiding(m, true);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.8, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 0.8, pos.z, 80, 0.5, 0.7, 0.5, 0.1);
        level.sendParticles(ParticleTypes.HEART, pos.x, pos.y + 1.2, pos.z, 12, 0.6, 0.5, 0.6, 0.05);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 20));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.translatable("message.papon.makima.awaken.title").withStyle(ChatFormatting.RED)));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.translatable("message.papon.makima.awaken").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
    }

    /** Jump (PaponNetwork.PillowSwap): the next position. */
    public void swap(Player player) {
        if (getFirstPassenger() != player) return;
        Position[] all = Position.values();
        Position next = all[posture().ordinal() % (all.length - 1) + 1];
        entityData.set(DATA_POSITION, next.ordinal());
        player.refreshDimensions();
        turnToHer(player);
        say(player, "to_" + next.name().toLowerCase());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8F, 1.1F);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || isVehicle() || player.isPassenger()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (PostNutClarityEffect.refractory(player)) return InteractionResult.CONSUME;
        // she turns to face you and lies back
        Vec3 d = player.position().subtract(position());
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        entityData.set(DATA_YAW, yaw);
        entityData.set(DATA_POSITION, Position.MISSIONARY.ordinal());
        player.startRiding(this, true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 0.9F);
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.refreshDimensions();
        turnToHer(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        passenger.refreshDimensions();
        if (!level().isClientSide) entityData.set(DATA_POSITION, Position.STAND.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_POSITION.equals(key)) getPassengers().forEach(e -> {
            e.refreshDimensions();
            turnToHer(e);
        });
    }

    /** The rider turned toward her (every position has them facing back along her facing), looking down at her, or up from under her. */
    private void turnToHer(Entity passenger) {
        float yaw = heldYaw() + 180;
        passenger.setYRot(yaw);
        passenger.setYHeadRot(yaw);
        passenger.setXRot(switch (posture()) {
            case MISSIONARY -> 70;
            case COWGIRL -> -35;
            default -> 45;
        });
        if (passenger instanceof LivingEntity le) le.setYBodyRot(yaw);
    }

    @Override
    public boolean shouldRiderSit() { return false; }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        double at = switch (posture()) {
            case MISSIONARY -> MISSIONARY_AT;
            case DOGGY -> DOGGY_AT;
            case BENT -> BENT_AT;
            case COWGIRL -> COWGIRL_AT;
            default -> 1.0;
        };
        Vec3 p = position().add(facing().scale(at));
        move.accept(passenger, p.x, p.y, p.z);
        if (passenger instanceof LivingEntity le) le.setYBodyRot(heldYaw() + 180);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position().add(facing().scale(1.2)).add(0, 0.05, 0);
    }

    // ---- never hurt, never gone (but by /kill or back in the egg)

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);
        if (!level().isClientSide && isAlive() && !isVehicle() && source.getEntity() instanceof Player p && source.getDirectEntity() == p) {
            takeBack(p);
        }
        return false;
    }

    /** Back into the egg, into their inventory (or at their feet). */
    public void takeBack(Player player) {
        ItemStack egg = new ItemStack(ModItems.MAKIMA_SPAWN_EGG.get());
        if (!player.getInventory().add(egg)) player.drop(egg, false);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 0.8F, 0.9F);
        say(player, "take");
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean canBeLeashed(Player player) { return false; }

    @Override
    public ItemStack getPickResult() { return new ItemStack(ModItems.MAKIMA_SPAWN_EGG.get()); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("HeldYaw", heldYaw());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_YAW, tag.getFloat("HeldYaw"));
    }

    // ---- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8, state -> state.setAndContinue(POSES[posture().ordinal()])));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
