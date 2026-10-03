package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.entity.Speech;
import com.groid.papon.entity.npc.SoundCues;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModDamage;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

/**
 * Chef Papon, El Calvo: the taquero after the unmasking, and the final boss of the Groid Pack.
 *
 * Built the Cataclysm way: a synced {@link BossState} plus a server tick counter; each attack is
 * a state whose length is its animation's, and its hits land on exact ticks of it (the spatula on
 * 12 and 26, the belly flop where the animation lands, the fire between 16 and 52...).
 *
 * The fight: he leaps out of the truck's window, downs a whole bottle of tequila, roars (boss bar
 * and music on). On foot he swings his giant spatula up close, belly-flops onto you from mid range
 * (shockwave), sprays burning tequila, and lobs molotovs at anyone keeping their distance. And
 * whenever his van attack is ready and his van is close and empty, he walks to it jingling his
 * keys, downs a whole handle of tequila by the driver's door (the warning: 2.5 s, you can watch it
 * empty), gets in, revs, and rams you: forwards, then backs into you too; it cannot be blocked.
 * He falls out of the van plastered and stumbles about for four seconds (he takes extra damage).
 * Take the van yourself and he cannot use it; drive it into him for good measure.
 * At half health (phase two) he is faster, angrier, throws three molotovs and rams twice.
 */
public class PaponBossEntity extends Monster implements GeoEntity, Speech.Speaker {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SPEECH =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_PHASE2 =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String ANIM = "animation.papon_boss.";
    private static final RawAnimation LEAP_OUT = once("leap_out");
    private static final RawAnimation CHUG = once("chug");
    private static final RawAnimation ROAR = once("roar");
    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation WALK_KEYS = loop("walk_keys");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation SWIPE = once("swipe");
    private static final RawAnimation SLAM = once("slam");
    private static final RawAnimation BREATH = once("breath");
    private static final RawAnimation THROW = once("throw");
    private static final RawAnimation DRINK_HANDLE = once("drink_handle");
    private static final RawAnimation ENTER_VAN = RawAnimation.begin().thenPlay(ANIM + "enter_car").thenLoop(ANIM + "drive");
    private static final RawAnimation DRIVE = loop("drive");
    private static final RawAnimation EXIT_STUMBLE = once("exit_stumble");
    private static final RawAnimation PHASE = once("phase");
    private static final RawAnimation DEATH = once("death");

    private static final UUID PHASE2_SPEED = UUID.fromString("2b4a6c5e-9f0d-4d2b-9c3e-7a1f0e5d4c3b");
    public static final String BAR_KEY = "bossbar.papon.chef";
    private static final double ARENA_RADIUS = 44;
    private static final double TARGET_RANGE = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable(BAR_KEY),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private int stateTicks;
    private int speechTicks;
    @Nullable private Vec3 home;
    @Nullable private Vec3 leapFrom;
    @Nullable private Vec3 leapTo;
    @Nullable private UUID vanId;
    private int swipeCd;
    private int slamCd = 60;
    private int breathCd = 80;
    private int throwCd = 40;
    private int vanCd = 200;
    private int think;
    private boolean phaseDue;
    private int noTargetTicks;
    @Nullable private Vec3 lockedAim;
    private int shockwave = -1;
    @Nullable private Vec3 shockAt;
    private boolean landed;
    private boolean stolenLine;
    // the van routine
    private enum Drive { REV, LEG, BRAKE }
    private Drive drive = Drive.REV;
    private int driveTicks;
    private int legsLeft;
    private boolean legReverse;
    private int stall;
    @Nullable private Vec3 ramAim;
    // client
    private int clientStateTicks;
    private int babbleTicks;

    public PaponBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.1F);
        xpReward = 500;
        bossEvent.setVisible(false);
    }

    private static RawAnimation once(String name) {
        return RawAnimation.begin().thenPlayAndHold(ANIM + name);
    }

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop(ANIM + name);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, BossState.IDLE.ordinal());
        entityData.define(DATA_SPEECH, "");
        entityData.define(DATA_PHASE2, false);
    }

    @Override
    protected void registerGoals() {
        // all of it is in customServerAiStep: a state machine, not goals
    }

    // ------------------------------------------------------------------ state

    public BossState getBossState() {
        return BossState.byId(entityData.get(DATA_STATE));
    }

    public void setBossState(BossState s) {
        if (WanderingDebug.on() && s != getBossState()) {
            PaponMod.LOG.info("[debug] boss {} -> {} (hp {}/{})", getBossState(), s, (int) getHealth(), (int) getMaxHealth());
        }
        entityData.set(DATA_STATE, s.ordinal());
        stateTicks = 0;
        getNavigation().stop();
    }

    /** Ticks in the current state, on either side. */
    public int ticksInState() {
        return level().isClientSide ? clientStateTicks : stateTicks;
    }

    public boolean isPhaseTwo() {
        return entityData.get(DATA_PHASE2);
    }

    /** Set up by TacoBoss: where he jumps from and to, his arena and his van. */
    public void begin(Vec3 from, Vec3 to, @Nullable CaravanEntity van) {
        leapFrom = from;
        leapTo = to;
        home = to;
        vanId = van == null ? null : van.getUUID();
        setBossState(BossState.LEAP_OUT);
    }

    /** /papon boss: no window to jump out of, straight to the bottle. */
    public void beginHere(@Nullable CaravanEntity van) {
        home = position();
        vanId = van == null ? null : van.getUUID();
        setBossState(BossState.CHUG);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide) {
            if (DATA_STATE.equals(key)) clientStateTicks = 0;
            if (DATA_SPEECH.equals(key)) babbleTicks = getSpeech().isEmpty() ? 0 : Speech.babbleLength(getSpeech());
        }
    }

    // ------------------------------------------------------------------ speech

    @Override
    public String getSpeech() {
        return entityData.get(DATA_SPEECH);
    }

    @Override
    public void setSpeech(String encoded, int ticks) {
        entityData.set(DATA_SPEECH, encoded);
        speechTicks = ticks;
    }

    private void say(String line, int ticks) {
        Speech.say(this, ChatFormatting.DARK_RED, "speech.papon.boss." + line, true, true, ticks);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientStateTicks++;
            if (babbleTicks > 0) {
                babbleTicks--;
                if (babbleTicks % 3 == 0) {
                    level().playLocalSound(getX(), getEyeY(), getZ(), SoundEvents.WANDERING_TRADER_AMBIENT, SoundSource.HOSTILE,
                            0.8F, 0.5F * (0.88F + random.nextFloat() * 0.24F), false);
                }
            }
            return;
        }
        if (speechTicks > 0 && --speechTicks == 0) entityData.set(DATA_SPEECH, "");
        bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        if (tickCount % 10 == 0) updateBossBarPlayers();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        stateTicks++;
        tickCooldowns();
        tickShockwave(level);
        LivingEntity target = pickTarget(level);
        switch (getBossState()) {
            case LEAP_OUT -> tickLeap(level);
            case CHUG -> tickChug(level);
            case ROAR -> tickRoar(level);
            case IDLE -> tickIdle(level, target);
            case SWIPE -> tickSwipe(level, target);
            case SLAM -> tickSlam(level, target);
            case BREATH -> tickBreath(level, target);
            case THROW -> tickThrow(level, target);
            case TO_VAN -> tickToVan(level, target);
            case DRINK_HANDLE -> tickDrinkHandle(level, target);
            case ENTER_VAN -> {
                if (stateTicks >= BossState.ENTER_VAN.duration) startDriving();
            }
            case DRIVING -> tickDriving(level, target);
            case EXIT_STUMBLE -> tickStumble(level);
            case PHASE -> tickPhase(level);
            case DEATH -> {}
        }
    }

    private void tickCooldowns() {
        swipeCd--;
        slamCd--;
        breathCd--;
        throwCd--;
        if (getBossState() != BossState.DRIVING && getBossState() != BossState.EXIT_STUMBLE) vanCd--;
        think--;
    }

    @Nullable
    private LivingEntity pickTarget(ServerLevel level) {
        LivingEntity current = getTarget();
        if (valid(current) && current.distanceToSqr(this) < TARGET_RANGE * TARGET_RANGE * 1.4) {
            noTargetTicks = 0;
            return current;
        }
        Player best = null;
        double bestD = TARGET_RANGE * TARGET_RANGE;
        for (Player p : level.players()) {
            if (!valid(p)) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        LivingEntity found = best;
        if (found == null && WanderingDebug.on()) {
            // headless testing (/papon escort true): an armor stand stands in for a player
            found = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    getBoundingBox().inflate(TARGET_RANGE), LivingEntity::isAlive).stream()
                    .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
        setTarget(found);
        noTargetTicks = found == null ? noTargetTicks + 1 : 0;
        return found;
    }

    private static boolean valid(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ------------------------------------------------------------------ the intro

    private void tickLeap(ServerLevel level) {
        int t = stateTicks;
        if (leapFrom == null || leapTo == null) {
            setBossState(BossState.CHUG);
            return;
        }
        if (t < 7) {
            setNoGravity(true);
            noPhysics = true;
            setDeltaMovement(Vec3.ZERO);
            face(leapTo);
        } else if (t <= 20) {
            double p = (t - 7) / 13.0;
            Vec3 at = leapFrom.lerp(leapTo, p).add(0, Math.sin(Math.PI * p) * 1.5 + (1 - p) * 0.3, 0);
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            if (t == 20) {
                setNoGravity(false);
                noPhysics = false;
                landImpact(level, position(), 4.0F, 0F, 0.45F);
            }
        }
        if (t >= BossState.LEAP_OUT.duration) setBossState(BossState.CHUG);
    }

    private void tickChug(ServerLevel level) {
        int t = stateTicks;
        LivingEntity target = getTarget();
        if (target != null) face(target.position());
        if (t == 6) say("salud", 40);
        if (t == 89) {
            Vec3 at = position().add(getLookAngle().scale(1.2)).add(0, 0.3, 0);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GLASS_BOTTLE)), at.x, at.y, at.z,
                    16, 0.2, 0.1, 0.2, 0.15);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5F, 0.9F);
        }
        if (t >= BossState.CHUG.duration) setBossState(BossState.ROAR);
    }

    private void tickRoar(ServerLevel level) {
        int t = stateTicks;
        if (t == 1) {
            bossEvent.setVisible(true);
            Component title = Component.translatable("title.papon.boss").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            Component sub = Component.translatable("title.papon.boss.subtitle").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 64 * 64) {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 20));
                    p.connection.send(new ClientboundSetTitleTextPacket(title));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
                }
            }
        }
        if (t == 6) {
            say("intro", 60);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.75F);
            PaponNetwork.shake(level, position(), 24, 0.6F, 30);
            knockAway(level, 6, 1.0, 0);
        }
        if (t >= BossState.ROAR.duration) {
            think = 10;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ deciding

    private void tickIdle(ServerLevel level, @Nullable LivingEntity target) {
        if (phaseDue) {
            phaseDue = false;
            setBossState(BossState.PHASE);
            return;
        }
        if (home != null && distanceToSqr(home) > ARENA_RADIUS * ARENA_RADIUS) {
            getNavigation().moveTo(home.x, home.y, home.z, 1.2);
            return;
        }
        if (target == null) {
            if (noTargetTicks > 200 && tickCount % 20 == 0) heal(getMaxHealth() * 0.01F);
            if (home != null && distanceToSqr(home) > 16 && getNavigation().isDone()) getNavigation().moveTo(home.x, home.y, home.z, 0.8);
            return;
        }
        getLookControl().setLookAt(target, 30, 30);
        double d = distanceTo(target);
        if (tickCount % 200 == 0 && random.nextInt(3) == 0) say("taunt" + random.nextInt(4), 50);
        if (think > 0) {
            chase(target, d);
            return;
        }
        boolean p2 = isPhaseTwo();
        CaravanEntity van = van(level);
        if (vanCd <= 0 && van != null && !van.isVehicle() && distanceTo(van) < 40 && d < 40) {
            setBossState(BossState.TO_VAN);
            say("keys", 40);
            return;
        }
        if (d < 4.4 && swipeCd <= 0) {
            setBossState(BossState.SWIPE);
            swipeCd = p2 ? 22 : 34;
        } else if (d >= 4.5 && d < 13 && slamCd <= 0 && random.nextInt(p2 ? 2 : 3) == 0) {
            setBossState(BossState.SLAM);
            slamCd = p2 ? 100 : 150;
        } else if (d >= 2.5 && d < 8 && breathCd <= 0) {
            setBossState(BossState.BREATH);
            breathCd = p2 ? 120 : 180;
        } else if (d >= 7 && throwCd <= 0) {
            setBossState(BossState.THROW);
            throwCd = p2 ? 60 : 100;
        } else {
            chase(target, d);
        }
        if (getBossState() != BossState.IDLE) think = p2 ? 6 : 12;
    }

    private void chase(LivingEntity target, double d) {
        if (d < 2.6) {
            getNavigation().stop();
            return;
        }
        if (getNavigation().isDone() || tickCount % 10 == 0) {
            getNavigation().moveTo(target, isPhaseTwo() && d > 8 ? 1.45 : 1.1);
        }
    }

    @Nullable
    public CaravanEntity van(ServerLevel level) {
        if (vanId == null) return null;
        Entity e = level.getEntity(vanId);
        return e instanceof CaravanEntity v && v.isAlive() ? v : null;
    }

    // ------------------------------------------------------------------ on foot

    private void tickSwipe(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 10) face(target.position());
        if (t == 12 || t == 26) hitArc(level, 4.6, 75, 12F, 1.3, ModDamage.SPATULA);
        if (t >= BossState.SWIPE.duration) setBossState(BossState.IDLE);
    }

    private void tickSlam(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t < 12) {
            if (target != null) {
                face(target.position());
                lockedAim = target.position().add(target.getDeltaMovement().scale(10));
            }
            landed = false;
        } else if (t == 12) {
            Vec3 aim = lockedAim != null ? lockedAim : position().add(getLookAngle().scale(6));
            Vec3 d = aim.subtract(position());
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            double v = Math.min(flat / 18.0, 1.1);
            Vec3 dir = flat < 1e-3 ? Vec3.ZERO : new Vec3(d.x / flat, 0, d.z / flat);
            setDeltaMovement(dir.x * v, 0.72, dir.z * v);
            hasImpulse = true;
        } else if (!landed && t > 16 && (onGround() || t >= 34)) {
            landed = true;
            landImpact(level, position(), 6.0F, 16F, 0.7F);
        }
        if (t >= BossState.SLAM.duration) setBossState(BossState.IDLE);
    }

    private void tickBreath(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null) {
            if (t < 16) face(target.position());
            else turnToward(target.position(), 2.5F);
        }
        if (t >= 16 && t <= 52) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            Vec3 mouth = position().add(look.scale(1.1)).add(0, 1.15, 0);
            for (int i = 0; i < 6; i++) {
                Vec3 v = look.add((random.nextDouble() - 0.5) * 0.45, (random.nextDouble() - 0.35) * 0.25, (random.nextDouble() - 0.5) * 0.45)
                        .normalize();
                double sp = 0.45 + random.nextDouble() * 0.35;
                level.sendParticles(random.nextInt(4) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        mouth.x, mouth.y, mouth.z, 0, v.x, v.y, v.z, sp);
            }
            if (t % 6 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, 2, 0.2, 0.2, 0.2, 0.02);
            if (t % 8 == 0) level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.7F);
            if (t % 4 == 0) {
                for (LivingEntity e : inCone(level, mouth, look, 8.5, 24)) {
                    if (e.hurt(source(ModDamage.TEQUILA_FIRE), 4F * dmg())) e.setSecondsOnFire(5);
                }
            }
        }
        if (t >= BossState.BREATH.duration) setBossState(BossState.IDLE);
    }

    private void tickThrow(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t <= 22) face(target.position());
        if (t == 22) {
            Vec3 aim = target != null ? target.position().add(target.getDeltaMovement().scale(12)) : position().add(getLookAngle().scale(10));
            int n = isPhaseTwo() ? 3 : 1;
            for (int i = 0; i < n; i++) {
                float spread = (i - (n - 1) / 2F) * 14F;
                MolotovEntity m = new MolotovEntity(level, this);
                Vec3 hand = position().add(Vec3.directionFromRotation(0, getYRot() + 40).scale(0.9)).add(0, 2.2, 0);
                m.setPos(hand);
                Vec3 d = aim.subtract(hand);
                double flat = Math.sqrt(d.x * d.x + d.z * d.z);
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F + spread;
                Vec3 dir = Vec3.directionFromRotation(0, yaw);
                m.shoot(dir.x * flat, d.y + flat * 0.32, dir.z * flat, (float) Mth.clamp(flat / 14.0 + 0.5, 0.7, 1.6), 1.5F);
                level.addFreshEntity(m);
            }
        }
        if (t >= BossState.THROW.duration) setBossState(BossState.IDLE);
    }

    private void tickPhase(ServerLevel level) {
        int t = stateTicks;
        if (t == 1) {
            entityData.set(DATA_PHASE2, true);
            bossEvent.setName(Component.translatable(BAR_KEY + ".phase2"));
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(PHASE2_SPEED) == null) {
                speed.addPermanentModifier(new AttributeModifier(PHASE2_SPEED, "Papon phase two", 0.25,
                        AttributeModifier.Operation.MULTIPLY_BASE));
            }
            say("phase", 60);
        }
        if (t == 9 || t == 15) landImpact(level, position(), 3.0F, 0F, 0.3F);
        if (t == 24) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
            landImpact(level, position(), 7.0F, 8F, 0.8F);
        }
        if (t >= BossState.PHASE.duration) {
            vanCd = Math.min(vanCd, 60);
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ the van

    private void tickToVan(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || stateTicks > 260) {
            if (van != null && van.getControllingPassenger() instanceof Player && !stolenLine) {
                stolenLine = true;
                say("stolen", 50);
            }
            vanCd = 100;
            setBossState(BossState.IDLE);
            return;
        }
        Vec3 door = van.driverDoor();
        // the van's hitbox is a square shorter than the van, so he can end up against its nose
        // instead of at the door: close enough to the van is close enough
        if (horizontalDist(door) < 2.2 || distanceTo(van) < 3.4) {
            setBossState(BossState.DRINK_HANDLE);
            say("handle", 50);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 48 * 48) {
                    p.displayClientMessage(Component.translatable("status.papon.handle").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
            return;
        }
        if (horizontalDist(door) < 4) {
            getMoveControl().setWantedPosition(door.x, door.y, door.z, isPhaseTwo() ? 1.4 : 1.15);
        } else if (getNavigation().isDone() || tickCount % 10 == 0) {
            getNavigation().moveTo(door.x, door.y, door.z, isPhaseTwo() ? 1.4 : 1.15);
        }
    }

    private void tickDrinkHandle(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        if (target != null) face(target.position());
        if (stateTicks == 46) {
            Vec3 back = position().subtract(getLookAngle().scale(1.5)).add(0, 0.5, 0);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GLASS_BOTTLE)), back.x, back.y, back.z,
                    14, 0.2, 0.1, 0.2, 0.12);
            level.playSound(null, back.x, back.y, back.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 0.8F);
        }
        if (stateTicks >= BossState.DRINK_HANDLE.duration) {
            CaravanEntity van = van(level);
            if (van != null && !van.isVehicle() && distanceTo(van) < 5 && startRiding(van, true)) {
                setBossState(BossState.ENTER_VAN);
            } else {
                if (van != null && van.getControllingPassenger() instanceof Player) say("stolen", 50);
                vanCd = 100;
                setBossState(BossState.IDLE);
            }
        }
    }

    private void startDriving() {
        drive = Drive.REV;
        driveTicks = 0;
        legsLeft = isPhaseTwo() ? 4 : 2;
        setBossState(BossState.DRIVING);
    }

    /**
     * The van attack: rev (the burnout), then legs: each one straight at the target, forwards if
     * it is ahead of the van and in reverse if it is behind, steering locked for the last few
     * blocks so it can be dodged sideways. Two legs (four in phase two), braking between them.
     */
    private void tickDriving(ServerLevel level, @Nullable LivingEntity target) {
        if (!(getVehicle() instanceof CaravanEntity van)) {
            exitVan(level, null);
            return;
        }
        driveTicks++;
        boolean crashed = van.consumeCrash();
        Vec3 f = van.forward();
        switch (drive) {
            case REV -> {
                van.brake();
                if (driveTicks == 1) {
                    van.burnout(20);
                    van.honk();
                    say("ram", 40);
                }
                if (driveTicks >= 20) {
                    if (target == null) exitVan(level, van);
                    else startLeg(van, target, true);
                }
            }
            case LEG -> {
                Vec3 aim = target != null ? target.position() : ramAim;
                if (aim == null) aim = van.position().add(f.scale(legReverse ? -10 : 10));
                double toGo = aim.subtract(van.position()).dot(f) * (legReverse ? -1 : 1);
                boolean lock = van.position().distanceTo(aim) < (legReverse ? 6 : 7);
                if (!lock) ramAim = aim;
                van.autopilot(lock && ramAim != null ? ramAim : aim, legReverse, lock);
                stall = driveTicks > 25 && Math.abs(van.speed()) < 0.06 ? stall + 1 : 0;
                if (toGo < -4 || driveTicks > (legReverse ? 60 : 90) || crashed || stall > 15) {
                    van.brake();
                    nextDrive(Drive.BRAKE);
                }
            }
            case BRAKE -> {
                van.brake();
                if (Math.abs(van.speed()) < 0.05 || driveTicks > 30) {
                    if (legsLeft > 0 && target != null && target.distanceTo(van) < 26) startLeg(van, target, false);
                    else exitVan(level, van);
                }
            }
        }
    }

    private void startLeg(CaravanEntity van, LivingEntity target, boolean first) {
        legsLeft--;
        legReverse = target.position().subtract(van.position()).dot(van.forward()) < 0;
        ramAim = target.position();
        stall = 0;
        if (legReverse) {
            say("reverse", 40);
            van.honk();
        } else if (!first) {
            say("again", 40);
            van.honk();
        }
        nextDrive(Drive.LEG);
    }

    private void nextDrive(Drive d) {
        if (WanderingDebug.on() && getVehicle() instanceof CaravanEntity van) {
            PaponMod.LOG.info("[debug] boss drive {} -> {} after {}t (van at {} speed {}, target {})", drive, d, driveTicks,
                    van.blockPosition().toShortString(), String.format("%.2f", van.speed()),
                    getTarget() == null ? "none" : getTarget().blockPosition().toShortString());
        }
        drive = d;
        driveTicks = 0;
    }

    private void exitVan(ServerLevel level, @Nullable CaravanEntity van) {
        if (van != null) {
            van.releaseAutopilot();
            Vec3 door = van.driverDoor();
            stopRiding();
            moveTo(door.x, van.getY(), door.z, getYRot(), 0);
        } else {
            stopRiding();
        }
        vanCd = isPhaseTwo() ? PaponConfig.VAN_COOLDOWN.get() * 2 / 3 : PaponConfig.VAN_COOLDOWN.get();
        if (WanderingDebug.on()) PaponMod.LOG.info("[debug] boss out of the van ({} after {}t)", drive, driveTicks);
        setBossState(BossState.EXIT_STUMBLE);
        say("stumble", 50);
    }

    private void tickStumble(ServerLevel level) {
        if (stateTicks % 12 == 0 && stateTicks < 64 && onGround()) {
            float yaw = getYRot() + (random.nextFloat() - 0.5F) * 140F;
            Vec3 d = Vec3.directionFromRotation(0, yaw).scale(0.25);
            setDeltaMovement(d.x, 0.1, d.z);
        }
        if (stateTicks % 16 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 2.6, getZ(), 3, 0.3, 0.1, 0.3, 0.02);
        }
        if (stateTicks >= BossState.EXIT_STUMBLE.duration) {
            think = 6;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ hits

    private float dmg() {
        return PaponConfig.BOSS_DAMAGE.get().floatValue();
    }

    private DamageSource source(ResourceKey<DamageType> type) {
        return ModDamage.of((ServerLevel) level(), type, this, this);
    }

    private void hitArc(ServerLevel level, double range, double halfAngle, float damage, double knock, ResourceKey<DamageType> type) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        Vec3 from = position().add(0, 1.0, 0);
        for (LivingEntity e : inCone(level, from, look, range, halfAngle)) {
            if (e.hurt(source(type), damage * dmg())) {
                Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.scale(knock)).add(0, 0.35, 0));
                e.hurtMarked = true;
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
        Vec3 sw = from.add(look.scale(2.2));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, sw.x, sw.y, sw.z, 3, 0.8, 0.2, 0.8, 0);
    }

    private List<LivingEntity> inCone(ServerLevel level, Vec3 from, Vec3 look, double range, double halfAngle) {
        double cos = Math.cos(Math.toRadians(halfAngle));
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range), e -> {
            if (e == this || !e.isAlive() || e.isAlliedTo(this) || e instanceof CaravanEntity) return false;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
            if (e instanceof com.groid.papon.entity.npc.TacoNpc || e instanceof com.groid.papon.entity.WanderingPaponEntity) return false;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(from);
            double len = to.length();
            if (len > range + e.getBbWidth() / 2) return false;
            if (len < 1.2) return true;
            Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
            return flat.dot(look) >= cos && Math.abs(to.y) < 3.0;
        });
    }

    /** A landing: shockwave ring, damage falling off with distance, camera shake. */
    private void landImpact(ServerLevel level, Vec3 at, float radius, float damage, float shake) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.55F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
        PaponNetwork.shake(level, at, radius * 3, shake, 20);
        shockAt = at;
        shockwave = 0;
        if (damage > 0) knockAway(level, radius, 1.5, damage);
    }

    private void knockAway(ServerLevel level, double radius, double knock, float damage) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2, radius),
                e -> e != this && e.isAlive() && !(e instanceof CaravanEntity) && !(e instanceof com.groid.papon.entity.npc.TacoNpc)
                        && !(e instanceof com.groid.papon.entity.WanderingPaponEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double d = e.distanceTo(this);
            if (d > radius) continue;
            float k = (float) (1 - d / radius * 0.5);
            if (damage > 0) e.hurt(source(ModDamage.BELLY_FLOP), damage * k * dmg());
            Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
            e.setDeltaMovement(away.scale(knock * k).add(0, 0.55 * k + 0.2, 0));
            e.hurtMarked = true;
        }
    }

    /** The shockwave ring runs outward a block a tick, kicking up the ground. */
    private void tickShockwave(ServerLevel level) {
        if (shockwave < 0 || shockAt == null) return;
        int r = ++shockwave;
        int n = 10 + r * 6;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = shockAt.x + Math.cos(a) * r;
            double z = shockAt.z + Math.sin(a) * r;
            BlockPos below = BlockPos.containing(x, shockAt.y - 0.5, z);
            BlockState bs = level.getBlockState(below);
            if (bs.isAir()) continue;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), x, shockAt.y + 0.1, z, 3, 0.15, 0.25, 0.15, 0.15);
        }
        if (r >= 7) shockwave = -1;
    }

    // ------------------------------------------------------------------ steering himself

    private void face(Vec3 at) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void turnToward(Vec3 at, float maxStep) {
        float want = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90F;
        float yaw = getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -maxStep, maxStep);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private double horizontalDist(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ taking damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        BossState s = getBossState();
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (s.intro() || s == BossState.ROAR || s == BossState.DEATH || s == BossState.PHASE) return false;
        if (source.getEntity() == this || source.getDirectEntity() instanceof MolotovEntity) return false;
        if (s == BossState.EXIT_STUMBLE) amount *= 1.5F;
        if (isPassenger()) amount *= 0.5F;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !isPhaseTwo() && !phaseDue && getHealth() <= getMaxHealth() / 2 && getHealth() > 0) phaseDue = true;
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (getBossState() == BossState.DEATH) return;
        stopRiding();
        setBossState(BossState.DEATH);
        say("death", 90);
        bossEvent.setProgress(0);
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel level) {
            if (deathTime % 10 == 0 && deathTime > 40 && deathTime < 90) {
                level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 2.0, getZ(), 6, 0.4, 0.1, 0.4, 0);
            }
            if (deathTime >= BossState.DEATH.duration) {
                level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 60, 1.0, 1.0, 1.0, 0.05);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1, getZ(), 40, 1.5, 1.0, 1.5, 0);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 2.0F, 0.6F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 2.0F, 1.0F);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void checkDespawn() {}
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isPushable() { return getBossState() != BossState.LEAP_OUT; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
    @Override public boolean canRiderInteract() { return false; }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        // his own van, and nothing else (no boats, no minecarts)
        return vehicle instanceof CaravanEntity && super.startRiding(vehicle, force);
    }

    @Override
    public double getMyRidingOffset() {
        return -0.15;
    }

    // ------------------------------------------------------------------ boss bar

    private void updateBossBarPlayers() {
        for (ServerPlayer p : List.copyOf(bossEvent.getPlayers())) {
            if (!p.isAlive() || p.level() != level() || p.distanceToSqr(this) > 72 * 72) bossEvent.removePlayer(p);
        }
        for (ServerPlayer p : ((ServerLevel) level()).players()) {
            if (p.distanceToSqr(this) < 56 * 56) bossEvent.addPlayer(p);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animate).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<PaponBossEntity> event) {
        RawAnimation current = event.getController().getCurrentRawAnimation();
        RawAnimation next = switch (getBossState()) {
            case LEAP_OUT -> LEAP_OUT;
            case CHUG -> CHUG;
            case ROAR -> ROAR;
            case IDLE -> event.isMoving() ? (isPhaseTwo() && getDeltaMovement().horizontalDistanceSqr() > 0.02 ? RUN : WALK) : IDLE;
            case SWIPE -> SWIPE;
            case SLAM -> SLAM;
            case BREATH -> BREATH;
            case THROW -> THROW;
            case TO_VAN -> event.isMoving() ? WALK_KEYS : IDLE;
            case DRINK_HANDLE -> DRINK_HANDLE;
            case ENTER_VAN -> ENTER_VAN;
            case DRIVING -> ENTER_VAN.equals(current) ? ENTER_VAN : DRIVE;
            case EXIT_STUMBLE -> EXIT_STUMBLE;
            case PHASE -> PHASE;
            case DEATH -> DEATH;
        };
        return event.setAndContinue(next);
    }

    private void onSoundCue(SoundKeyframeEvent<PaponBossEntity> event) {
        SoundCues.play(this, event.getKeyframeData().getSound());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BossState s = getBossState();
        // a reload mid-attack or mid-intro picks up from deciding again
        tag.putString("BossState", (s == BossState.DEATH ? BossState.DEATH : BossState.IDLE).name());
        tag.putBoolean("Phase2", isPhaseTwo());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(BlockPos.containing(home)));
        if (vanId != null) tag.putUUID("Van", vanId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_PHASE2, tag.getBoolean("Phase2"));
        if (isPhaseTwo()) bossEvent.setName(Component.translatable(BAR_KEY + ".phase2"));
        if (tag.contains("Home")) home = Vec3.atBottomCenterOf(NbtUtils.readBlockPos(tag.getCompound("Home")));
        vanId = tag.hasUUID("Van") ? tag.getUUID("Van") : null;
        entityData.set(DATA_STATE, BossState.IDLE.ordinal());
        bossEvent.setVisible(true);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    /** Debug logging follows the Wandering Papon's /papon escort switch. */
    private static final class WanderingDebug {
        static boolean on() {
            return com.groid.papon.entity.WanderingPaponEntity.debugEscort;
        }
    }
}
