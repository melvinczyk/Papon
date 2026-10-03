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
    /** Ghost mode (the tequila rush): through anything, half alive and half dead. */
    private static final EntityDataAccessor<Boolean> DATA_GHOST =
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
    private static final RawAnimation A_OVERHEAD = once("swipe_overhead");
    private static final RawAnimation A_LEAP = once("leap");
    private static final RawAnimation A_LEAP_LAND = once("leap_land");
    private static final RawAnimation A_WINDUP = once("charge_windup");
    private static final RawAnimation A_CHARGE = loop("charge");
    private static final RawAnimation A_STAGGER = once("stagger");
    private static final RawAnimation A_UPPERCUT = once("uppercut");
    private static final RawAnimation A_AIR_SPIKE = once("air_spike");
    private static final RawAnimation A_BLINK = loop("blink");
    private static final RawAnimation A_GATLING = once("gatling");

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
    private enum Drive { REV, LEG, BRAKE, DONUT, AIR }
    private Drive drive = Drive.REV;
    private int driveTicks;
    private int legsLeft;
    private boolean legReverse;
    private int stall;
    private boolean refunded;
    @Nullable private Vec3 ramAim;
    private boolean airUsed;
    private int orbitDir = 1;
    // phase one, reworked: cooldowns, chains, prediction, hazards
    private int leapCd = 100;
    private int chargeCd = 60;
    private int uppercutCd;
    private int blinkCd = 80;
    private int gatlingCd = 120;
    private int blinksLeft;
    @Nullable private Vec3 blinkTo;
    private int blinkHover = 14;
    /** Something was hit during the current attack (combos continue on a hit; misses build rage). */
    private boolean swingHit;
    /** Attacks in a row that hit nothing: at {@link #FRENZY} he stops pausing and charges. */
    private int misses;
    private static final int FRENZY = 4;
    @Nullable private BossState lastAttack;
    private int chargesLeft;
    private final java.util.Set<Integer> chargeHits = new java.util.HashSet<>();
    @Nullable private Vec3 chargeLast;
    private int chargeStuck;
    private boolean hovering;
    private boolean diving;
    private int hoverStart;
    private boolean bigLanding;
    @Nullable private Vec3 ringAt;
    @Nullable private UUID juggled;
    private boolean spiked;
    private float sweepYaw;
    private int sweepDir = 1;
    @Nullable private Vec3 waveFrom;
    @Nullable private Vec3 waveDir;
    private int waveStep = -1;
    /** Where the target was, one entry a tick (the server cannot trust a player's velocity). */
    private final Vec3[] hist = new Vec3[11];
    private int histN;
    /** Burning tequila on the ground: x, y, z, ticks left. */
    private final List<double[]> fires = new java.util.ArrayList<>();
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
                .add(Attributes.MOVEMENT_SPEED, 0.35)
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
        entityData.define(DATA_GHOST, false);
    }

    public boolean isGhost() {
        return entityData.get(DATA_GHOST);
    }

    private void setGhost(boolean g) {
        entityData.set(DATA_GHOST, g);
        noPhysics = g;
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
        BossState old = getBossState();
        if (WanderingDebug.on() && s != old) {
            PaponMod.LOG.info("[debug] boss {} -> {} (hp {}/{})", old, s, (int) getHealth(), (int) getMaxHealth());
        }
        if (s == BossState.IDLE && attack(old)) {
            misses = swingHit ? 0 : misses + 1;
            think = isPhaseTwo() ? 2 : 4;      // a breath between attacks (none in a frenzy: see choose)
        }
        if (s != BossState.LEAP && s != BossState.LEAP_OUT && s != BossState.AIR_SPIKE) {
            setNoGravity(false);
            hovering = false;
        }
        if (s != BossState.CHARGE && isGhost()) {
            setGhost(false);
            unstick();
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
        record(target);
        tickFires(level);
        tickLineWave(level);
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
            case SWIPE_OVERHEAD -> tickOverhead(level, target);
            case LEAP -> tickLeapAttack(level, target);
            case LEAP_LAND -> tickLeapLand(level);
            case CHARGE_WINDUP -> tickWindup(level, target);
            case CHARGE -> tickCharge(level, target);
            case STAGGER -> tickStagger(level);
            case UPPERCUT -> tickUppercut(level, target);
            case AIR_SPIKE -> tickAirSpike(level, target);
            case BLINK -> tickBlink(level, target);
            case GATLING -> tickGatling(level, target);
        }
    }

    private void tickCooldowns() {
        swipeCd--;
        slamCd--;
        breathCd--;
        throwCd--;
        leapCd--;
        chargeCd--;
        uppercutCd--;
        blinkCd--;
        gatlingCd--;
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
        CaravanEntity van = van(level);
        if (vanCd <= 0 && van != null && !van.isVehicle() && distanceTo(van) < 40 && d < 40) {
            setBossState(BossState.TO_VAN);
            say("keys", 40);
            return;
        }
        BossState pick = choose(target, d);
        if (pick != null) startAttack(pick, target);
        else chase(target, d);
    }

    /**
     * What to do next, by range and cooldown (Cataclysm style): point blank, the uppercut or the
     * flurry (the flurry lunges, so it reaches from 7 blocks); mid range, the leap, the charge or the
     * fire; far, molotovs or the charge. Anyone above him (pillaring) or far away gets the leap. The
     * same attack twice in a row is avoided when there is another; four misses in a row and he
     * stops pausing and charges (twice).
     */
    @Nullable
    private BossState choose(LivingEntity target, double d) {
        if (misses >= FRENZY) {
            misses = 0;
            think = 0;
            chargesLeft = isPhaseTwo() ? 2 : 1;
            say("taunt" + random.nextInt(4), 40);
            return BossState.CHARGE_WINDUP;
        }
        double dy = target.getY() - getY();
        java.util.List<BossState> options = new java.util.ArrayList<>();
        if ((dy > 3.0 || d > 18) && leapCd <= 0) return BossState.LEAP;
        if (d < 3.8 && uppercutCd <= 0) options.add(BossState.UPPERCUT);
        if (d < 7.0 && swipeCd <= 0) options.add(BossState.SWIPE);
        if (d >= 5 && d < 16 && leapCd <= 0) options.add(BossState.LEAP);
        if (d >= 6 && d < 24 && chargeCd <= 0) options.add(BossState.CHARGE_WINDUP);
        if (d >= 3 && d < 9 && breathCd <= 0) options.add(BossState.BREATH);
        if (d >= 9 && throwCd <= 0) options.add(BossState.THROW);
        if (d >= 4 && d < 20 && blinkCd <= 0) options.add(BossState.BLINK);
        if (d >= 7 && d < 30 && gatlingCd <= 0) options.add(BossState.GATLING);
        if (options.isEmpty()) return null;
        if (options.size() > 1) options.remove(lastAttack);
        // up close the flurry is the bread and butter
        if (options.contains(BossState.SWIPE) && d < 4.5 && random.nextFloat() < 0.5F) return BossState.SWIPE;
        return options.get(random.nextInt(options.size()));
    }

    private void startAttack(BossState pick, LivingEntity target) {
        boolean p2 = isPhaseTwo();
        swingHit = false;
        lastAttack = pick;
        switch (pick) {
            case SWIPE -> swipeCd = p2 ? 16 : 24;
            case UPPERCUT -> uppercutCd = p2 ? 60 : 80;
            case LEAP -> leapCd = p2 ? 100 : 140;
            case CHARGE_WINDUP -> {
                chargeCd = p2 ? 80 : 120;
                if (chargesLeft <= 0) chargesLeft = p2 ? 1 : 0;
            }
            case BREATH -> breathCd = p2 ? 120 : 160;
            case THROW -> throwCd = p2 ? 50 : 70;
            case BLINK -> {
                blinkCd = p2 ? 110 : 160;
                blinksLeft = p2 ? 6 : 4;
            }
            case GATLING -> gatlingCd = p2 ? 140 : 200;
            default -> {}
        }
        setBossState(pick);
    }

    private static boolean attack(BossState s) {
        return switch (s) {
            case SWIPE, SWIPE_OVERHEAD, SLAM, BREATH, THROW, LEAP, LEAP_LAND, CHARGE_WINDUP, CHARGE, UPPERCUT, AIR_SPIKE,
                 BLINK, GATLING -> true;
            default -> false;
        };
    }

    private void chase(LivingEntity target, double d) {
        if (d < 2.6) {
            getNavigation().stop();
            return;
        }
        if (getNavigation().isDone() || tickCount % 6 == 0) {
            getNavigation().moveTo(target, isPhaseTwo() ? (d > 8 ? 1.6 : 1.4) : (d > 8 ? 1.4 : 1.25));
        }
    }

    @Nullable
    public CaravanEntity van(ServerLevel level) {
        if (vanId == null) return null;
        Entity e = level.getEntity(vanId);
        return e instanceof CaravanEntity v && v.isAlive() ? v : null;
    }

    // ------------------------------------------------------------------ on foot

    /**
     * The spatula flurry (Maledictus' spin slashes): tracks you, lunges by however far away you are
     * (0.5 s: a forehand), re-aims while winding back, lunges again (1.0 s: a backhand that breaks
     * shields). If either landed, or you are stepping back in to punish, the overhead follows.
     */
    private void tickSwipe(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && (t < 6 || (t >= 12 && t < 16))) turnToward(target.position(), 30F);
        if (target != null && (t == 6 || t == 16)) lunge(target, 0.33, 1.0, 6.0);
        if (t == 10) hitArc(level, 4.6, 90, 12F, 1.0, ModDamage.SPATULA, false);
        if (t == 20) hitArc(level, 4.8, 70, 13F, 1.4, ModDamage.SPATULA, true);
        if (t >= BossState.SWIPE.duration) {
            boolean follow = target != null && (swingHit || (distanceTo(target) < 7 && approaching(target))
                    || (isPhaseTwo() && random.nextBoolean()));
            setBossState(follow ? BossState.SWIPE_OVERHEAD : BossState.IDLE);
        }
    }

    /**
     * The flurry's finisher: both hands up (he tracks you, slower, until 0.8 s), then straight down:
     * the hit (breaks shields, pops you up) and a shockwave that races 10 blocks along the ground
     * where he faces. Step aside, do not back off.
     */
    private void tickOverhead(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 16) turnToward(target.position(), 12F);
        if (t == 20) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 4.2, 40)) {
                if (strike(e, 16F, ModDamage.SPATULA, true)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                    e.hurtMarked = true;
                }
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.5F);
            PaponNetwork.shake(level, position(), 18, 0.45F, 16);
            waveFrom = position().add(look.scale(1.5));
            waveDir = look;
            waveStep = 0;
        }
        if (t >= BossState.SWIPE_OVERHEAD.duration) setBossState(BossState.IDLE);
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

    /**
     * Burning tequila, swept like a laser (the Harbinger's): it starts beside you and sweeps across
     * you at a fixed rate, so you run with it or through it, and it leaves burning puddles behind.
     */
    private void tickBreath(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 15) face(target.position());
        if (t == 15) {
            sweepDir = random.nextBoolean() ? 1 : -1;
            float to = target != null ? yawTo(target.position()) : getYRot();
            sweepYaw = to - sweepDir * 45F;
        }
        if (t >= 16 && t <= 52) {
            sweepYaw += sweepDir * (isPhaseTwo() ? 3.6F : 2.8F);
            setYRot(sweepYaw);
            yBodyRot = sweepYaw;
            yHeadRot = sweepYaw;
            Vec3 look = Vec3.directionFromRotation(0, sweepYaw);
            Vec3 mouth = position().add(look.scale(1.1)).add(0, 1.15, 0);
            for (int i = 0; i < 6; i++) {
                Vec3 v = look.add((random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.35) * 0.2, (random.nextDouble() - 0.5) * 0.3)
                        .normalize();
                double sp = 0.55 + random.nextDouble() * 0.35;
                level.sendParticles(random.nextInt(4) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        mouth.x, mouth.y, mouth.z, 0, v.x, v.y, v.z, sp);
            }
            if (t % 6 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, 2, 0.2, 0.2, 0.2, 0.02);
            if (t % 8 == 0) level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.7F);
            if (t % 4 == 0) {
                for (LivingEntity e : inCone(level, mouth, look, 9.5, 16)) {
                    if (strike(e, 4F, ModDamage.TEQUILA_FIRE, false)) e.setSecondsOnFire(5);
                }
            }
            if (t % 5 == 0) {
                Vec3 at = position().add(look.scale(4 + random.nextDouble() * 4));
                addFire(level, at);
            }
        }
        if (t >= BossState.BREATH.duration) setBossState(BossState.IDLE);
    }

    /** A fan of molotovs (three, five in phase two) at where you will be, not where you are. */
    private void tickThrow(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t <= 22) face(target.position());
        if (t == 22) {
            Vec3 aim = target != null ? predict(target, 14) : position().add(getLookAngle().scale(10));
            int n = isPhaseTwo() ? 5 : 3;
            for (int i = 0; i < n; i++) {
                float spread = (i - (n - 1) / 2F) * 9F;
                MolotovEntity m = new MolotovEntity(level, this);
                Vec3 hand = position().add(Vec3.directionFromRotation(0, getYRot() + 40).scale(0.9)).add(0, 2.2, 0);
                m.setPos(hand);
                Vec3 d = aim.subtract(hand);
                double flat = Math.sqrt(d.x * d.x + d.z * d.z) * (1 + Math.abs(i - (n - 1) / 2F) * 0.08);
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F + spread;
                Vec3 dir = Vec3.directionFromRotation(0, yaw);
                m.shoot(dir.x * flat, d.y + flat * 0.32, dir.z * flat, (float) Mth.clamp(flat / 14.0 + 0.5, 0.7, 1.6), 1.0F);
                level.addFreshEntity(m);
            }
        }
        if (t >= BossState.THROW.duration) setBossState(BossState.IDLE);
    }

    // ------------------------------------------------------------------ the leap

    /**
     * Up, hover, dive (Maledictus' flying smash): crouches tracking you, launches high (higher if you
     * are up on something), hangs at the top drifting over you while a ring on the ground marks where
     * he will come down, then dives at where you are at that instant. The landing (LEAP_LAND) sends
     * three shockwave rings out that only hit what is on the ground: jump them.
     */
    private void tickLeapAttack(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t == 1) {
            diving = false;
            hovering = false;
            blinkHover = isPhaseTwo() ? 10 : 14;
        }
        if (t < 10) {
            if (target != null) face(target.position());
            return;
        }
        if (t == 10) {
            double up = 1.25 + (target == null ? 0 : Mth.clamp((target.getY() - getY()) * 0.08, 0, 0.6));
            Vec3 drift = target == null ? Vec3.ZERO : flatTo(target.position()).scale(0.05);
            setDeltaMovement(drift.x, up, drift.z);
            hasImpulse = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2.0F, 0.6F);
            return;
        }
        if (!hovering && !diving && (getDeltaMovement().y <= 0.05 || t >= 24)) {
            hovering = true;
            hoverStart = t;
            setNoGravity(true);
        }
        if (hovering) {
            Vec3 v = Vec3.ZERO;
            if (target != null) {
                Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
                v = to.length() > 0.4 ? to.normalize().scale(Math.min(0.35, to.length() * 0.12)) : Vec3.ZERO;
                warnRing(level, target.position());
            }
            setDeltaMovement(v.x, 0, v.z);
            if (t - hoverStart >= blinkHover) {
                hovering = false;
                diving = true;
                setNoGravity(false);
                Vec3 aim = target != null ? target.position() : position().add(0, -4, 0);
                setDeltaMovement((aim.x - getX()) * 0.16, -1.8, (aim.z - getZ()) * 0.16);
                hasImpulse = true;
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0F, 0.6F);
            }
            return;
        }
        if (diving && (onGround() || t > 90)) {
            bigLanding = true;
            setBossState(BossState.LEAP_LAND);
        }
        if (t > 120) {
            bigLanding = false;
            setBossState(BossState.LEAP_LAND);
        }
    }

    /** The impact, then the rings: radius 3.5, 5.5, 7.5 at 0.2, 0.4, 0.6 s, grounded targets only. */
    private void tickLeapLand(ServerLevel level) {
        int t = stateTicks;
        if (t == 1) {
            ringAt = position();
            float radius = bigLanding ? 3.6F : 2.6F;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.45F);
            PaponNetwork.shake(level, position(), radius * 4, bigLanding ? 0.8F : 0.5F, 24);
            shockAt = position();
            shockwave = 0;
            for (LivingEntity e : around(level, radius)) {
                float k = (float) (1 - e.distanceTo(this) / radius * 0.4);
                if (strike(e, (bigLanding ? 16F : 10F) * k, ModDamage.BELLY_FLOP, true)) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(1.6 * k).add(0, 0.55, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (bigLanding && ringAt != null && (t == 4 || t == 8 || t == 12)) {
            double r = 3.5 + (t - 4) / 4.0 * 2.0;
            ring(level, ringAt, r);
            for (LivingEntity e : around(level, r + 1.2)) {
                double d = Math.sqrt(e.distanceToSqr(ringAt.x, e.getY(), ringAt.z));
                if (Math.abs(d - r) > 1.1 || !e.onGround()) continue;
                if (strike(e, 8F, ModDamage.BELLY_FLOP, false)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.65, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (t >= BossState.LEAP_LAND.duration) setBossState(BossState.IDLE);
    }

    // ------------------------------------------------------------------ the tequila rush

    /** Paws the ground, head down: tracks you until 0.6 s, then commits to his line. */
    private void tickWindup(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 12) face(target.position());
        if (t % 4 == 0) {
            BlockState below = level.getBlockState(blockPosition().below());
            if (!below.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.1, getZ(),
                        8, 0.5, 0.05, 0.5, 0.1);
            }
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 2.4, getZ(), 2, 0.3, 0.1, 0.3, 0.02);
        }
        if (t >= BossState.CHARGE_WINDUP.duration) {
            chargeHits.clear();
            chargeLast = null;
            chargeStuck = 0;
            setBossState(BossState.CHARGE);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 1.2F);
        }
    }

    /**
     * The tequila rush, flat out (the Leviathan's): steers toward you at no more than 2.5 degrees a
     * tick, so the dodge is late and sideways. Drunk enough to be half a ghost, he goes straight
     * through trees and walls, riding the lie of the land. Everything in front of him is hit once
     * (shields break) and flung aside. A charge that hits nothing leaves him reeling (STAGGER: the
     * punish window); phase two and a frenzy run it again.
     */
    private void tickCharge(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t == 1) setGhost(true);
        if (target != null) turnToward(target.position(), 2.5F);
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        double speed = isPhaseTwo() ? 0.82 : 0.7;
        double vy = Mth.clamp((groundY(position()) - getY()) * 0.5, -0.8, 0.9);
        setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        getNavigation().stop();
        AABB box = getBoundingBox().inflate(0.4, 0, 0.4).expandTowards(dir.scale(1.2));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::hittable)) {
            if (!chargeHits.add(e.getId())) continue;
            if (strike(e, 14F, ModDamage.BELLY_FLOP, true)) {
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                double sd = e.position().subtract(position()).dot(side) >= 0 ? 1 : -1;
                e.setDeltaMovement(side.scale(2.0 * sd).add(dir.scale(0.8)).add(0, 0.55, 0));
                e.hurtMarked = true;
                if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.7F, 14);
            }
        }
        for (int i = 0; i < 2; i++) {
            level.sendParticles(random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL,
                    getX() + (random.nextDouble() - 0.5) * 1.6, getY() + random.nextDouble() * 3.0, getZ() + (random.nextDouble() - 0.5) * 1.6,
                    1, 0, 0.03, 0, 0.01);
        }
        if (t % 5 == 0) PaponNetwork.shake(level, position(), 12, 0.08F, 5);
        boolean passed = target != null && target.position().subtract(position()).dot(dir) < -5;
        if (t >= 32 || passed || (target == null && t > 10)) {
            setDeltaMovement(dir.scale(0.25));
            if (chargesLeft > 0 && target != null) {
                chargesLeft--;
                setBossState(BossState.CHARGE_WINDUP);
                stateTicks = 7;          // a shorter wind-up for the second run
            } else {
                setBossState(swingHit ? BossState.IDLE : BossState.STAGGER);
            }
        }
    }

    /** The surface under him, ghosting: the median of five samples, so a lone tree trunk does not lift him. */
    private double groundY(Vec3 at) {
        double[] ys = new double[5];
        int i = 0;
        for (double[] o : new double[][]{{0, 0}, {1.2, 0}, {-1.2, 0}, {0, 1.2}, {0, -1.2}}) {
            ys[i++] = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(at.x + o[0]), Mth.floor(at.z + o[1]));
        }
        java.util.Arrays.sort(ys);
        return ys[2];
    }

    /** After ghosting: if he came to a stop inside something, step out to the nearest open ground. */
    private void unstick() {
        if (level().noCollision(this)) return;
        for (int r = 1; r <= 6; r++) {
            for (int k = 0; k < 8 * r; k++) {
                double a = Math.PI * 2 * k / (8 * r);
                Vec3 p = position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                p = new Vec3(p.x, groundY(p), p.z);
                if (Math.abs(p.y - getY()) > 4) continue;
                if (level().noCollision(this, getBoundingBox().move(p.subtract(position())))) {
                    setPos(p.x, p.y, p.z);
                    return;
                }
            }
        }
        setPos(getX(), groundY(position()), getZ());
    }

    // ------------------------------------------------------------------ the blink combo

    /**
     * The teleport fist combo: a mark appears on the ground beside or behind you, he vanishes and
     * steps out of a puff right there (half a cycle later), and throws a straight right. Four times
     * (six in phase two, at 1.33x speed), then a mark above you: he appears up there and dives (LEAP's
     * finish, a short hover). The tells are short, and he picks your blind side.
     */
    private void tickBlink(ServerLevel level, @Nullable LivingEntity target) {
        if (target == null) {
            setBossState(BossState.IDLE);
            return;
        }
        int cycle = isPhaseTwo() ? 9 : 12;
        int t = (stateTicks - 1) % cycle;
        int half = cycle / 2;
        if (t == 0) {
            blinkTo = blinksLeft <= 0 ? target.position().add(0, 7, 0) : blinkSpot(target);
            level.playSound(null, blinkTo.x, blinkTo.y, blinkTo.z, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.4F, 1.8F);
        }
        if (blinkTo != null && t < half) {
            Vec3 m = blinksLeft <= 0 ? new Vec3(blinkTo.x, target.getY(), blinkTo.z) : blinkTo;
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 0.15F, 0.1F), 1.6F),
                    m.x, m.y + 0.1, m.z, 8, 0.5, 0.05, 0.5, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y + 1.2, m.z, 4, 0.3, 0.6, 0.3, 0.02);
        }
        if (t == half && blinkTo != null) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.5, getZ(), 24, 0.6, 1.0, 0.6, 0.05);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.POTATO)), getX(), getY() + 1.5, getZ(),
                    12, 0.5, 0.8, 0.5, 0.15);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4F, 0.6F);
            setPos(blinkTo.x, blinkTo.y, blinkTo.z);
            setDeltaMovement(Vec3.ZERO);
            face(target.position());
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.5, getZ(), 24, 0.6, 1.0, 0.6, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4F, 0.8F);
            if (blinksLeft <= 0) {
                // the finisher: up there, a short hover, then down on you
                setBossState(BossState.LEAP);
                stateTicks = 11;
                hovering = true;
                diving = false;
                hoverStart = 11;
                blinkHover = isPhaseTwo() ? 5 : 7;
                setNoGravity(true);
                return;
            }
            blinksLeft--;
        }
        if (t == half + 3) {
            face(target.position());
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 3.6, 70)) {
                if (strike(e, 9F, ModDamage.SPATULA, false)) {
                    e.setDeltaMovement(look.scale(0.9).add(0, 0.3, 0));
                    e.hurtMarked = true;
                }
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.4F, 0.6F);
            PaponNetwork.shake(level, position(), 8, 0.15F, 6);
        }
    }

    /** Somewhere 2-2.5 blocks from the target, mostly behind them, with room for him to stand. */
    private Vec3 blinkSpot(LivingEntity target) {
        for (int i = 0; i < 8; i++) {
            float yaw = target.getYRot() + 180F + (random.nextFloat() - 0.5F) * 200F;
            Vec3 p = target.position().add(Vec3.directionFromRotation(0, yaw).scale(2.0 + random.nextDouble() * 0.5));
            p = new Vec3(p.x, groundY(p), p.z);
            if (Math.abs(p.y - target.getY()) < 3 && level().noCollision(this, getBoundingBox().move(p.subtract(position())))) return p;
        }
        return target.position().add(Vec3.directionFromRotation(0, target.getYRot() + 180F).scale(2.0));
    }

    // ------------------------------------------------------------------ the potato gatling

    /**
     * The potato gatling: pulls it, spins it up (0.75 s), then hoses you for two seconds, a round a
     * tick (two in phase two). The barrel follows where you are going at no more than 6 degrees a
     * tick, so the stream sweeps after you: change direction to slip it, or hide.
     */
    private void tickGatling(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 15) face(target.position());
        if (t == 12) level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.HOSTILE, 1.5F, 0.6F);
        if (t >= 15 && t <= 55 && target != null) {
            Vec3 aim = predict(target, 6).add(0, target.getBbHeight() * 0.6, 0);
            turnToward(aim, 6F);
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            Vec3 muzzle = position().add(look.scale(1.9)).add(Vec3.directionFromRotation(0, getYRot() - 90).scale(0.6)).add(0, 1.9, 0);
            Vec3 d = aim.subtract(muzzle);
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            int n = isPhaseTwo() ? 2 : 1;
            for (int i = 0; i < n; i++) {
                PotatoShotEntity shot = new PotatoShotEntity(level, this);
                shot.setPos(muzzle);
                shot.shoot(look.x * flat, d.y, look.z * flat, 2.3F, 3.5F);
                level.addFreshEntity(shot);
            }
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.02);
            if (t % 2 == 0) level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 0.7F, 1.7F);
            if (t % 10 == 0) PaponNetwork.shake(level, position(), 10, 0.1F, 6);
        }
        if (t >= BossState.GATLING.duration) setBossState(BossState.IDLE);
    }

    private void tickStagger(ServerLevel level) {
        if (stateTicks % 4 == 0) {
            double a = stateTicks * 0.6;
            level.sendParticles(ParticleTypes.CRIT, getX() + Math.cos(a) * 0.9, getY() + 3.0, getZ() + Math.sin(a) * 0.9,
                    2, 0.05, 0.05, 0.05, 0);
        }
        if (stateTicks >= BossState.STAGGER.duration) {
            think = 4;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ the uppercut and the juggle

    /** Crouched, tracking (to 0.4 s); the uppercut at 0.6 s scoops you up. If it lands, he follows. */
    private void tickUppercut(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 8) face(target.position());
        if (t == 12) {
            juggled = null;
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 3.6, 55)) {
                if (strike(e, 11F, ModDamage.SPATULA, false)) {
                    e.setDeltaMovement(look.scale(0.25).add(0, 1.1, 0));
                    e.hurtMarked = true;
                    if (juggled == null || e instanceof Player) juggled = e.getUUID();
                }
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        if (t == 15 && juggled != null) {
            spiked = false;
            setBossState(BossState.AIR_SPIKE);
            return;
        }
        if (t >= BossState.UPPERCUT.duration) setBossState(BossState.IDLE);
    }

    /** Leaps after whoever he launched and spikes them back down at the top; lands with a thud. */
    private void tickAirSpike(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Entity j = juggled == null ? null : level.getEntity(juggled);
        LivingEntity victim = j instanceof LivingEntity le && le.isAlive() ? le : null;
        if (t == 1) {
            Vec3 to = victim != null ? victim.position().subtract(position()) : Vec3.ZERO;
            setDeltaMovement(to.x * 0.14, 1.15, to.z * 0.14);
            hasImpulse = true;
        }
        if (victim != null && t < 9) face(victim.position());
        if (!spiked && t >= 7 && t <= 13 && victim != null && victim.distanceTo(this) < 3.6) {
            spiked = true;
            if (strike(victim, 14F, ModDamage.SPATULA, true)) {
                victim.setDeltaMovement(0, -1.6, 0);
                victim.hurtMarked = true;
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.8F);
            PaponNetwork.shake(level, position(), 14, 0.5F, 12);
        }
        if ((t > 4 && onGround()) || t > 60) {
            bigLanding = false;
            setBossState(BossState.LEAP_LAND);
        }
    }

    // ------------------------------------------------------------------ helpers for the above

    private void record(@Nullable LivingEntity target) {
        if (target == null) {
            histN = 0;
            return;
        }
        hist[histN % hist.length] = target.position();
        histN++;
    }

    /** Where the target will be in {@code ahead} ticks, from where it has been (Ignis' predictive dash). */
    private Vec3 predict(LivingEntity target, int ahead) {
        if (histN < hist.length) return target.position();
        Vec3 old = hist[histN % hist.length];
        Vec3 v = target.position().subtract(old).scale(1.0 / (hist.length - 1));
        v = new Vec3(Mth.clamp(v.x, -0.6, 0.6), 0, Mth.clamp(v.z, -0.6, 0.6));
        return target.position().add(v.scale(ahead));
    }

    /** Coming toward him (to punish a greedy re-entry). */
    private boolean approaching(LivingEntity target) {
        if (histN < 4) return false;
        Vec3 before = hist[(histN - 4) % hist.length];
        Vec3 v = target.position().subtract(before);
        return v.dot(position().subtract(target.position())) > 0.05;
    }

    /** A lunge that covers the gap, whatever it is: you sidestep it, you do not outrun it. */
    private void lunge(LivingEntity target, double k, double lo, double hi) {
        Vec3 to = flatTo(target.position());
        double d = Math.sqrt(horizontalDistanceSqr(target.position()));
        double v = k * Mth.clamp(d - 1.5, lo, hi);
        setDeltaMovement(to.x * v, getDeltaMovement().y, to.z * v);
        hasImpulse = true;
    }

    private Vec3 flatTo(Vec3 at) {
        Vec3 d = at.subtract(position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-6 ? Vec3.ZERO : d.normalize();
    }

    private double horizontalDistanceSqr(Vec3 at) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        return dx * dx + dz * dz;
    }

    private float yawTo(Vec3 at) {
        return (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90F;
    }

    /**
     * Every hit goes through here: a heavy one breaks a raised shield (Cataclysm's disableShield) and
     * lands anyway at 60%. Marks the attack as having connected.
     */
    private boolean strike(LivingEntity e, float damage, ResourceKey<DamageType> type, boolean heavy) {
        if (heavy && e instanceof Player p && p.isBlocking()) {
            p.disableShield(true);
            damage *= 0.6F;
        }
        boolean hit = e.hurt(source(type), damage * dmg());
        if (hit) swingHit = true;
        return hit;
    }

    private boolean hittable(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isAlliedTo(this) || e instanceof CaravanEntity) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return !(e instanceof com.groid.papon.entity.npc.TacoNpc) && !(e instanceof com.groid.papon.entity.WanderingPaponEntity);
    }

    private List<LivingEntity> around(ServerLevel level, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 2.5, r),
                e -> hittable(e) && e.distanceTo(this) <= r + e.getBbWidth() / 2);
    }

    /** The landing marker: a ring of flame on the ground under the target. */
    private void warnRing(ServerLevel level, Vec3 at) {
        if (stateTicks % 2 != 0) return;
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14 + stateTicks * 0.1;
            level.sendParticles(ParticleTypes.FLAME, at.x + Math.cos(a) * 1.8, at.y + 0.15, at.z + Math.sin(a) * 1.8,
                    1, 0, 0.02, 0, 0);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 1, 0.3, 0, 0.3, 0.01);
    }

    private void ring(ServerLevel level, Vec3 at, double r) {
        int n = (int) (r * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = at.x + Math.cos(a) * r;
            double z = at.z + Math.sin(a) * r;
            BlockState bs = level.getBlockState(BlockPos.containing(x, at.y - 0.5, z));
            if (!bs.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), x, at.y + 0.2, z, 3, 0.1, 0.3, 0.1, 0.2);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.CLOUD, x, at.y + 0.3, z, 1, 0, 0.05, 0, 0.02);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.2F);
    }

    /** The overhead's shockwave: a block a tick along the ground for ten blocks, grounded targets only. */
    private void tickLineWave(ServerLevel level) {
        if (waveStep < 0 || waveFrom == null || waveDir == null) return;
        int k = waveStep++;
        Vec3 p = waveFrom.add(waveDir.scale(k));
        BlockState bs = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
        if (!bs.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), p.x, p.y + 0.2, p.z, 12, 0.4, 0.5, 0.4, 0.25);
        level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.4, p.z, 4, 0.3, 0.3, 0.3, 0.1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.1, 1.0, 1.1), this::hittable)) {
            if (!e.onGround() || !chargeHits.add(e.getId() + 1_000_000)) continue;
            if (strike(e, 9F, ModDamage.SPATULA, false)) {
                e.setDeltaMovement(e.getDeltaMovement().add(waveDir.scale(0.5)).add(0, 0.6, 0));
                e.hurtMarked = true;
            }
        }
        if (k >= 10) {
            waveStep = -1;
            chargeHits.clear();
        }
    }

    private void addFire(ServerLevel level, Vec3 at) {
        BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
        if (level.getBlockState(below).isAir()) return;
        if (fires.size() > 40) fires.remove(0);
        fires.add(new double[]{at.x, below.getY() + 1.0, at.z, 100});
    }

    /** Burning puddles: flames, and a burn for anyone standing in one. */
    private void tickFires(ServerLevel level) {
        if (fires.isEmpty()) return;
        java.util.Iterator<double[]> it = fires.iterator();
        while (it.hasNext()) {
            double[] f = it.next();
            if (--f[3] <= 0) {
                it.remove();
                continue;
            }
            if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.FLAME, f[0], f[1] + 0.05, f[2], 2, 0.45, 0.02, 0.45, 0.01);
            if (tickCount % 10 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(f[0] - 1.2, f[1] - 0.2, f[2] - 1.2, f[0] + 1.2, f[1] + 1.2, f[2] + 1.2), this::hittable)) {
                    if (e.hurt(source(ModDamage.TEQUILA_FIRE), 3F * dmg())) e.setSecondsOnFire(3);
                }
            }
        }
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
        legsLeft = isPhaseTwo() ? 5 : 3;
        refunded = false;
        airUsed = false;
        setBossState(BossState.DRIVING);
        // a whole handle down: half alive, half dead, and so is the van
        if (getVehicle() instanceof CaravanEntity van) van.setGhost(true);
    }

    /**
     * The van attack, ghosting: nothing stops it (trees, walls, water), it rides the land at up to
     * 1.35 blocks a tick, and every move aims where you will be. After the burnout, a run of
     * manoeuvres (three, five in phase two):
     *   RAM    at your predicted spot, forwards (or backing into you if you are close behind), the
     *          steering locked only in the last four blocks so you have to dodge late
     *   DONUT  circles you at eight blocks, tyres smoking, lobbing molotovs out of the window, then
     *          cuts straight in
     *   AIR    (once a drive) takes off and comes down on you: a crater, everyone near stunned
     * Molotovs fly out of the window on the straights too.
     */
    private void tickDriving(ServerLevel level, @Nullable LivingEntity target) {
        if (!(getVehicle() instanceof CaravanEntity van)) {
            exitVan(level, null);
            return;
        }
        driveTicks++;
        van.consumeCrash();
        Vec3 f = van.forward();
        switch (drive) {
            case REV -> {
                van.brake();
                if (driveTicks == 1) {
                    van.burnout(isPhaseTwo() ? 12 : 20);
                    van.honk();
                    say("ram", 40);
                }
                if (driveTicks >= (isPhaseTwo() ? 12 : 20)) nextManeuver(level, van, target);
            }
            case LEG -> {
                if (target == null) {
                    exitVan(level, van);
                    return;
                }
                double dist = target.distanceTo(van);
                Vec3 aim = predict(target, (int) Mth.clamp(dist / 1.2, 0, 20));
                double toGo = aim.subtract(van.position()).dot(f) * (legReverse ? -1 : 1);
                boolean lock = van.position().distanceTo(aim) < (legReverse ? 5 : 4);
                if (!lock) ramAim = aim;
                van.autopilot(lock && ramAim != null ? ramAim : aim, legReverse, lock);
                driveBy(level, van, target, 25);
                if (toGo < -6 || driveTicks > 70) {
                    van.brake();
                    nextDrive(Drive.BRAKE);
                }
            }
            case DONUT -> {
                if (target == null) {
                    exitVan(level, van);
                    return;
                }
                Vec3 c = target.position();
                Vec3 off = van.position().subtract(c).multiply(1, 0, 1);
                if (off.lengthSqr() < 1) off = f;
                double a = Math.atan2(off.z, off.x) + orbitDir * 0.55;
                Vec3 pt = c.add(Math.cos(a) * 8, 0, Math.sin(a) * 8);
                van.autopilot(pt, false, false);
                driveBy(level, van, target, 12);
                if (driveTicks >= (isPhaseTwo() ? 40 : 50)) {
                    say("again", 40);
                    van.honk();
                    legReverse = false;
                    ramAim = target.position();
                    nextDrive(Drive.LEG);
                }
            }
            case AIR -> {
                if (driveTicks == 1) {
                    Vec3 aim = target != null ? predict(target, 22) : van.position().add(f.scale(12));
                    van.launch(aim, 22);
                    van.honk();
                    level.playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0F, 0.5F);
                }
                if (van.consumeLanding()) {
                    vanImpact(level, van);
                    nextDrive(Drive.BRAKE);
                } else if (driveTicks > 90) {
                    nextDrive(Drive.BRAKE);
                }
            }
            case BRAKE -> {
                van.brake();
                if (Math.abs(van.speed()) < 0.08 || driveTicks > 12) nextManeuver(level, van, target);
            }
        }
    }

    private void nextManeuver(ServerLevel level, CaravanEntity van, @Nullable LivingEntity target) {
        if (legsLeft <= 0 || target == null || target.distanceTo(van) > 48) {
            exitVan(level, van);
            return;
        }
        legsLeft--;
        double dist = target.distanceTo(van);
        float r = random.nextFloat();
        if (!airUsed && dist > 8 && r < 0.3F) {
            airUsed = true;
            say("again", 40);
            nextDrive(Drive.AIR);
            return;
        }
        if (r < 0.6F) {
            orbitDir = random.nextBoolean() ? 1 : -1;
            nextDrive(Drive.DONUT);
            return;
        }
        legReverse = dist < 10 && target.position().subtract(van.position()).dot(van.forward()) < 0;
        ramAim = target.position();
        if (legReverse) {
            say("reverse", 40);
            van.honk();
        }
        nextDrive(Drive.LEG);
    }

    /** A molotov out of the driver's window every so often, at where the target is headed. */
    private void driveBy(ServerLevel level, CaravanEntity van, LivingEntity target, int every) {
        if (driveTicks % every != every / 2) return;
        double d = target.distanceTo(van);
        if (d < 5 || d > 22) return;
        Vec3 from = van.local(CaravanEntity.HALF_WIDTH + 0.3, 1.6, 0.6);
        Vec3 aim = predict(target, 12);
        Vec3 dv = aim.subtract(from);
        double flat = Math.sqrt(dv.x * dv.x + dv.z * dv.z);
        MolotovEntity m = new MolotovEntity(level, this);
        m.setPos(from);
        m.shoot(dv.x, dv.y + flat * 0.3, dv.z, (float) Mth.clamp(flat / 14.0 + 0.5, 0.7, 1.6), 2.0F);
        level.addFreshEntity(m);
    }

    /** The van comes down out of the sky: everyone within 4.5 blocks is crushed and stunned. */
    private void vanImpact(ServerLevel level, CaravanEntity van) {
        Vec3 at = van.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, com.groid.papon.registry.ModSounds.CARAVAN_CRASH.get(), SoundSource.HOSTILE, 2.5F, 0.7F);
        PaponNetwork.shake(level, at, 24, 1.0F, 30);
        ring(level, at, 3.0);
        ring(level, at, 5.0);
        float stun = PaponConfig.VAN_STUN_TICKS.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, van.getBoundingBox().inflate(4.5, 3, 4.5), this::hittable)) {
            double d = Math.sqrt(e.distanceToSqr(at.x, e.getY(), at.z));
            if (d > 4.5 + e.getBbWidth() / 2) continue;
            float k = (float) (1 - d / 4.5 * 0.5);
            if (strike(e, (float) (PaponConfig.VAN_RAM_DAMAGE.get() * 0.9 * k), ModDamage.CARAVAN, true)) {
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(away.scale(1.8 * k).add(0, 0.6, 0));
                e.hurtMarked = true;
                if (stun > 0) e.addEffect(new MobEffectInstance(com.groid.papon.registry.ModEffects.STUNNED.get(), (int) stun, 0, false, false, true));
            }
        }
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
            van.setGhost(false);
            van.settle();
            Vec3 door = van.driverDoor();
            stopRiding();
            moveTo(door.x, van.getY(), door.z, getYRot(), 0);
            unstick();
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

    private void hitArc(ServerLevel level, double range, double halfAngle, float damage, double knock, ResourceKey<DamageType> type,
                        boolean heavy) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        Vec3 from = position().add(0, 1.0, 0);
        for (LivingEntity e : inCone(level, from, look, range, halfAngle)) {
            if (strike(e, damage, type, heavy)) {
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
        if (s == BossState.STAGGER) amount *= 1.75F;
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
    @Override public boolean isPushable() { return getBossState() != BossState.LEAP_OUT && getBossState() != BossState.CHARGE; }
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
        event.getController().setAnimationSpeed(getBossState() == BossState.BLINK && isPhaseTwo() ? 1.333 : 1.0);
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
            case SWIPE_OVERHEAD -> A_OVERHEAD;
            case LEAP -> A_LEAP;
            case LEAP_LAND -> A_LEAP_LAND;
            case CHARGE_WINDUP -> A_WINDUP;
            case CHARGE -> A_CHARGE;
            case STAGGER -> A_STAGGER;
            case UPPERCUT -> A_UPPERCUT;
            case AIR_SPIKE -> A_AIR_SPIKE;
            case BLINK -> A_BLINK;
            case GATLING -> A_GATLING;
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
        setNoGravity(false);
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
