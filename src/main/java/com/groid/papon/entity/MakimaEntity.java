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
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Makima, the real thing (the user's model: tools/gen_makima_mesh.py, client/MakimaRenderer). From her spawn egg she stands
 * where she was put, watching you, her mouth watering when you come close (MakimaRenderer's drool). Sneak + right-click and
 * she follows you (a walk of her own; she keeps up, and comes to you if you get far ahead); again and she stays.
 *
 * Right-click her and you are hers ({@link Position#MISSIONARY} first; you ride her, client/MakimaRider poses you and puts
 * your camera where your eyes are). With her:
 *   jump        the next position; 1-6 pick one (client/MakimaRider sends {@link #control})
 *   W / S       faster / slower ({@link #TEMPO}: slow, steady, fast, frantic); the faster, the sooner you finish
 *   sneak       get off
 * Each position is a held pose (gen_makima_anims.py) fitted to you ({@link #FIT}: your hips on hers, whatever your size and
 * hers). Her heat ({@link #heat}) is how far along you are (PostNutClarityEffect.hump); at the top she takes over:
 * {@link #CLIMAX} ticks of it (frantic, then held), then Post Nut Clarity and you are off. Punch her and she goes back in the
 * egg. Nothing else hurts her (only /kill), and she never despawns.
 */
public class MakimaEntity extends PathfinderMob implements GeoEntity {
    /** Synced as an ordinal: append new positions at the end. */
    public enum Position {
        STAND, MISSIONARY, DOGGY, BENT, COWGIRL, REVERSE, CARRY;

        /** The rider's act, for what is shared with the body pillow (PillowClient: hands hidden, eye heights). */
        @Nullable
        public BodyPillowEntity.Act act() {
            return switch (this) {
                case STAND -> null;
                case MISSIONARY -> BodyPillowEntity.Act.HUMP;
                case DOGGY -> BodyPillowEntity.Act.BEHIND;
                case BENT, CARRY -> BodyPillowEntity.Act.SIDE_KNEES;
                case COWGIRL, REVERSE -> BodyPillowEntity.Act.UNDER;
            };
        }

        public Fit fit() { return FIT[ordinal()]; }
    }

    /**
     * Where the rider goes (tools/gen_makima_anims.py RIDER: the same numbers, and her pose is fitted to them): their hip joint
     * {@code at} blocks along her facing from her and {@code up} off the ground, the body tipped {@code tilt} degrees forward
     * about the hips (negative: leaning back; -90 on their back), the legs straight down, kneeling (angled back to the ground)
     * or lying along the body.
     */
    public record Fit(double at, double up, float tilt, Legs legs) {
        /** The rider's eyes: up the body from the hips (Steve's are 0.87 above them), off the ground. */
        public float eye() { return (float) Math.max(0.12, up + 0.87 * Math.cos(Math.toRadians(tilt))); }
    }

    public enum Legs { STAND, KNEEL, LIE }

    public static final Fit[] FIT = {
            new Fit(1.0, 0.75, 0, Legs.STAND),
            new Fit(0.16, 0.25, 55, Legs.KNEEL),        // MISSIONARY: leaning over her on straight arms
            new Fit(0.2, 0.58, -6, Legs.KNEEL),         // DOGGY: kneeling up behind her
            new Fit(0.2, 0.75, -4, Legs.STAND),         // BENT: standing behind her
            new Fit(0.0, 0.125, -90, Legs.LIE),         // COWGIRL: on your back under her
            new Fit(0.0, 0.125, -90, Legs.LIE),         // REVERSE: the same, her back to you
            new Fit(0.2, 0.75, -8, Legs.STAND),         // CARRY: standing, holding her up
    };

    /** The pace: strokes as fast as PillowClient.thrust's (one per 7 ticks) times this; and how much each tick counts to finish. */
    public static final float[] TEMPO = {0.6F, 1.0F, 1.45F, 1.95F};
    public static final float[] HEAT = {0.55F, 1.0F, 1.5F, 2.1F};
    public static final int STEADY = 1, FRANTIC = 3;
    /** The climax: frantic for the first part, then held deep for the rest. */
    public static final int CLIMAX = 70, CLIMAX_HOLD = 26;

    private static final EntityDataAccessor<Integer> DATA_POSITION = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.INT);
    /** Her yaw while someone is with her: turned to face where they came at her from, held there. */
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TEMPO = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.FLOAT);
    /** Ticks of the climax left (0: none). */
    private static final EntityDataAccessor<Integer> DATA_CLIMAX = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOW = SynchedEntityData.defineId(MakimaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.makima.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.makima.walk");
    private static final RawAnimation[] POSES = {IDLE, RawAnimation.begin().thenLoop("animation.makima.missionary"),
            RawAnimation.begin().thenLoop("animation.makima.doggy"), RawAnimation.begin().thenLoop("animation.makima.bent"),
            RawAnimation.begin().thenLoop("animation.makima.cowgirl"), RawAnimation.begin().thenLoop("animation.makima.reverse"),
            RawAnimation.begin().thenLoop("animation.makima.carry")};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int with;
    @Nullable private UUID follow;
    /** The stroke's clock (ticks of a steady pace, PillowClient.thrust's t), run on both sides from the synced tempo. */
    private float stroke, strokeO;
    /** Client: the tick of her last drop of drool (MakimaRenderer), and of her last hearts. */
    public int drooled, hearted;

    public MakimaEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FollowGoal());
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10F, 1F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_POSITION, 0);
        entityData.define(DATA_YAW, 0F);
        entityData.define(DATA_TEMPO, STEADY);
        entityData.define(DATA_HEAT, 0F);
        entityData.define(DATA_CLIMAX, 0);
        entityData.define(DATA_FOLLOW, false);
    }

    public Position posture() { return Position.values()[Mth.clamp(entityData.get(DATA_POSITION), 0, Position.values().length - 1)]; }

    /** What her rider is doing, or null with nobody there. */
    @Nullable
    public BodyPillowEntity.Act act() {
        return getFirstPassenger() instanceof Player ? posture().act() : null;
    }

    /** The pace, 0 slow .. 3 frantic (the climax is always frantic, then held). */
    public int tempo() { return climax() > CLIMAX_HOLD ? FRANTIC : Mth.clamp(entityData.get(DATA_TEMPO), 0, TEMPO.length - 1); }

    /** How far along her rider is, 0..1. */
    public float heat() { return entityData.get(DATA_HEAT); }

    /** Ticks of the climax left (0: none). */
    public int climax() { return entityData.get(DATA_CLIMAX); }

    /** In the climax's last part: held deep, no more strokes. */
    public boolean holding() { int c = climax(); return c > 0 && c <= CLIMAX_HOLD; }

    public boolean following() { return entityData.get(DATA_FOLLOW); }

    /** The stroke's clock now (for PillowClient.thrust and squash), between ticks. */
    public float stroke(float partial) { return Mth.lerp(partial, strokeO, stroke); }

    /** Her facing while held (unit, horizontal). */
    public Vec3 facing() {
        float yaw = entityData.get(DATA_YAW) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    public float heldYaw() { return entityData.get(DATA_YAW); }

    /** The eye height of a player with her (CommonEvents.onSize), NaN for anyone else or standing. */
    public static float eyeHeight(Entity entity) {
        if (!(entity instanceof Player p) || !(p.getVehicle() instanceof MakimaEntity m) || m.posture() == Position.STAND) return Float.NaN;
        return m.posture().fit().eye();
    }

    /** Lying down or lifted she reaches well past her standing box: drawn whenever any of her is on screen. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return posture() == Position.STAND ? getBoundingBox().inflate(0.3) : getBoundingBox().inflate(2.2, 0.8, 2.2);
    }

    @Override
    public void tick() {
        super.tick();
        strokeO = stroke;
        // the clock wraps every few minutes (a whole number of strokes: 7 ticks at the steady pace, 2 pi / 0.9)
        if (stroke > 7000 * Mth.TWO_PI / 0.9F) {
            stroke -= 7000 * Mth.TWO_PI / 0.9F;
            strokeO = stroke;
        }
        if (isVehicle()) {
            if (!holding()) stroke += TEMPO[tempo()];
            // held where she lay down for you
            float yaw = heldYaw();
            setYRot(yaw);
            yRotO = yaw;
            yBodyRot = yaw;
            yBodyRotO = yaw;
            yHeadRot = yaw;
            yHeadRotO = yaw;
            setXRot(0);
            getNavigation().stop();
        }
        if (level().isClientSide) {
            if (isVehicle()) strokeSounds();
            return;
        }
        if (!(getFirstPassenger() instanceof ServerPlayer p) || !(level() instanceof ServerLevel server)) {
            with = 0;
            if (posture() != Position.STAND) entityData.set(DATA_POSITION, Position.STAND.ordinal());
            if (climax() != 0) entityData.set(DATA_CLIMAX, 0);
            if (heat() != 0) entityData.set(DATA_HEAT, 0F);
            return;
        }
        if (++with == 4) say(p, "start");
        int c = climax();
        if (c > 0) {
            climaxTick(p, server, c);
            return;
        }
        float heat = PostNutClarityEffect.hump(p, HEAT[tempo()]);
        entityData.set(DATA_HEAT, heat);
        if (heat >= 1F) {
            entityData.set(DATA_CLIMAX, CLIMAX);
            say(p, "climax");
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, 0.8F);
            return;
        }
        if (with % 90 == 30) say(p, posture().name().toLowerCase() + "." + random.nextInt(4));
    }

    /** The climax, on the server: frantic, then held deep (a flash on the rider's screen: client/MakimaRider), then done. */
    private void climaxTick(ServerPlayer p, ServerLevel server, int c) {
        entityData.set(DATA_CLIMAX, c - 1);
        if (c == CLIMAX_HOLD) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 0.55F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8F, 1.0F);
            Vec3 at = position().add(0, 0.9, 0);
            server.sendParticles(ParticleTypes.HEART, at.x, at.y, at.z, 16, 0.8, 0.5, 0.8, 0.1);
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 24, 0.5, 0.4, 0.5, 0.05);
            say(p, "climax2");
        }
        if (c == 1) {
            entityData.set(DATA_HEAT, 0F);
            PostNutClarityEffect.finish(p);
            say(p, "done");
            ejectPassengers();
        }
    }

    /** Client: each stroke's sounds, in time with what you see (every client keeps the clock itself). */
    private void strokeSounds() {
        // a stroke lands where sin(0.9 t) peaks: when the clock passes a quarter turn
        float a = strokeO * 0.9F / Mth.TWO_PI - 0.25F, b = stroke * 0.9F / Mth.TWO_PI - 0.25F;
        if (Mth.floor(a) == Mth.floor(b) || holding()) return;
        int tempo = tempo();
        float vol = 0.35F + 0.12F * tempo;
        Vec3 at = position().add(0, 0.5, 0);
        play(at, SoundEvents.SLIME_SQUISH_SMALL, vol, 1.45F + random.nextFloat() * 0.25F);
        play(at, SoundEvents.WOOL_STEP, vol + 0.1F, 0.75F + random.nextFloat() * 0.1F);
        if (tempo >= 2 && random.nextInt(2) == 0) play(at, SoundEvents.SLIME_ATTACK, vol * 0.6F, 1.6F + random.nextFloat() * 0.2F);
        if (random.nextInt(6) == 0) play(at, SoundEvents.PLAYER_BREATH, 0.3F + 0.1F * tempo, 1.5F + random.nextFloat() * 0.2F);
    }

    private void play(Vec3 at, SoundEvent sound, float vol, float pitch) {
        level().playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, vol, pitch, false);
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
        Vec3 pos = where.subtract(f.scale(at.fit().at()));
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
        Position[] all = Position.values();
        control(player, all[posture().ordinal() % (all.length - 1) + 1].ordinal());
    }

    /** Controls from her rider (PaponNetwork.MakimaControl, client/MakimaRider). */
    public static final int FASTER = 100, SLOWER = 101;

    /** From her rider: a position (its ordinal, 1..6), or {@link #FASTER} / {@link #SLOWER}. Nothing changes in the climax. */
    public void control(Player player, int action) {
        if (getFirstPassenger() != player || climax() > 0) return;
        if (action == FASTER || action == SLOWER) {
            int was = entityData.get(DATA_TEMPO), now = Mth.clamp(was + (action == FASTER ? 1 : -1), 0, TEMPO.length - 1);
            if (now == was) return;
            entityData.set(DATA_TEMPO, now);
            say(player, "tempo." + now);
            return;
        }
        if (action < 1 || action >= Position.values().length || action == posture().ordinal()) return;
        Position next = Position.values()[action];
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
        if (player.isShiftKeyDown()) {
            // she follows you, or stays
            boolean on = !(following() && player.getUUID().equals(follow));
            follow = on ? player.getUUID() : null;
            entityData.set(DATA_FOLLOW, on);
            say(player, on ? "follow" : "stay");
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, 1.3F);
            if (!on) getNavigation().stop();
            return InteractionResult.CONSUME;
        }
        if (PostNutClarityEffect.refractory(player)) return InteractionResult.CONSUME;
        // she turns to face you and lies back
        Vec3 d = player.position().subtract(position());
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        getNavigation().stop();
        entityData.set(DATA_YAW, yaw);
        entityData.set(DATA_POSITION, Position.MISSIONARY.ordinal());
        entityData.set(DATA_TEMPO, STEADY);
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
        if (!level().isClientSide) {
            entityData.set(DATA_POSITION, Position.STAND.ordinal());
            entityData.set(DATA_CLIMAX, 0);
            entityData.set(DATA_HEAT, 0F);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_POSITION.equals(key)) getPassengers().forEach(e -> {
            e.refreshDimensions();
            turnToHer(e);
        });
    }

    /** The rider turned toward her (every position has them facing back along her facing), looking at her. */
    private void turnToHer(Entity passenger) {
        float yaw = heldYaw() + 180;
        passenger.setYRot(yaw);
        passenger.setYHeadRot(yaw);
        passenger.setXRot(switch (posture()) {
            case MISSIONARY -> 78;
            case DOGGY, BENT -> 40;
            case COWGIRL, REVERSE -> -50;
            case CARRY -> 8;
            default -> 30;
        });
        if (passenger instanceof LivingEntity le) le.setYBodyRot(yaw);
    }

    @Override
    public boolean shouldRiderSit() { return false; }

    /** The rider's feet go on the ground under their hips ({@link Fit}); client/MakimaRider draws the rest from there. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 p = position().add(facing().scale(posture().fit().at()));
        move.accept(passenger, p.x, p.y, p.z);
        if (passenger instanceof LivingEntity le) le.setYBodyRot(heldYaw() + 180);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position().add(facing().scale(1.2)).add(0, 0.05, 0);
    }

    // ---- following you

    /** Sneak + right-click: after you at a walk; too far behind (or another world's worth away) she is simply there. */
    private class FollowGoal extends Goal {
        @Nullable private Player target;
        private int repath;

        FollowGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Nullable
        private Player find() {
            if (follow == null || isVehicle()) return null;
            Player p = level().getPlayerByUUID(follow);
            return p != null && p.isAlive() && !p.isSpectator() ? p : null;
        }

        @Override
        public boolean canUse() {
            target = find();
            return target != null && distanceToSqr(target) > 3.5 * 3.5;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target == find() && distanceToSqr(target) > 2.2 * 2.2;
        }

        @Override
        public void start() { repath = 0; }

        @Override
        public void stop() {
            target = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) return;
            getLookControl().setLookAt(target, 30F, 30F);
            if (--repath > 0) return;
            repath = 8;
            if (distanceToSqr(target) > 24 * 24 && target.onGround()) {
                // left behind: she is simply there, just behind you
                Vec3 back = target.position().subtract(target.getLookAngle().multiply(2, 0, 2));
                moveTo(back.x, target.getY(), back.z, getYRot(), 0);
                getNavigation().stop();
                return;
            }
            getNavigation().moveTo(target, distanceToSqr(target) > 8 * 8 ? 1.25 : 1.0);
        }
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
        if (follow != null) tag.putUUID("Follow", follow);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_YAW, tag.getFloat("HeldYaw"));
        follow = tag.hasUUID("Follow") ? tag.getUUID("Follow") : null;
        entityData.set(DATA_FOLLOW, follow != null);
    }

    // ---- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8, state -> {
            Position at = posture();
            if (at == Position.STAND && state.isMoving()) {
                // the walk's stride matched to her speed (a stride of 0.36 a step, two steps in its second)
                double v = getDeltaMovement().horizontalDistance() * 20;
                state.getController().setAnimationSpeed(Mth.clamp(v / 0.72, 0.6, 2.2));
                return state.setAndContinue(WALK);
            }
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(POSES[at.ordinal()]);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
