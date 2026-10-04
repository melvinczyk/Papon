package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.entity.Speech;
import com.groid.papon.fx.Fx;
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

import java.util.ArrayList;
import java.util.Arrays;
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
    /** Phase two's form: 0 as he was, 1 baked golden (the seasoning), 2 risen (fry wings, crown, butter). */
    private static final EntityDataAccessor<Integer> DATA_FORM =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** Airborne under his own power (phase two): the fly animation, the wings beating. */
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);

    /** Whoever is in his fist (the grab, the QTEs), by entity id, so both sides hold them in front of him. */
    private static final EntityDataAccessor<Integer> DATA_HELD =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** Which QTE attack (0 La Última Cena, 1 Last Call, 2 La Papa Suplex): picks the hold and fail animations. */
    private static final EntityDataAccessor<Integer> DATA_QTE =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** Up in El Coliseo Papa. */
    private static final EntityDataAccessor<Boolean> DATA_ARENA =
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
    private static final RawAnimation A_DASH = once("dash_combo");
    private static final RawAnimation A_VAN_KICK = once("van_kick");
    private static final RawAnimation A_P2_KNEEL = once("p2_kneel");
    private static final RawAnimation A_P2_TALK = once("p2_talk");
    private static final RawAnimation A_P2_SEASON = once("p2_season");
    private static final RawAnimation A_P2_SCREAM = once("p2_scream");
    private static final RawAnimation A_P2_ARRIVE = once("p2_arrive");
    private static final RawAnimation A_P2_ARRIVE_SKY = once("p2_arrive_sky");
    private static final RawAnimation A_P2_NOTICE = once("p2_notice");
    private static final RawAnimation A_P2_MARCH = loop("p2_march");
    private static final RawAnimation A_P2_WAKE = once("p2_wake");
    private static final RawAnimation A_P2_WAIT = loop("p2_wait");
    private static final RawAnimation A_P2_KICK = once("p2_kick");
    private static final RawAnimation A_P2_COUNT = once("p2_count");
    private static final RawAnimation A_P2_GROOVE = loop("p2_groove");
    private static final RawAnimation A_P2_APOLOGY = once("p2_apology");
    private static final RawAnimation A_P2_RESUME = once("p2_resume");
    private static final RawAnimation A_P2_HYPE = once("p2_hype");
    private static final RawAnimation A_P2_SOLO = once("p2_solo");
    private static final RawAnimation A_FLY = loop("fly");
    private static final RawAnimation A_BARRAGE = once("p2_barrage");
    private static final RawAnimation A_METEOR = once("p2_meteor");
    private static final RawAnimation A_VAN_THROW = once("van_throw");
    private static final RawAnimation A_P2_DIVE = loop("p2_dive");
    private static final RawAnimation A_GRAB = once("grab_lunge");
    private static final RawAnimation A_GRAB_COMBO = once("grab_combo");
    private static final RawAnimation A_GUARD = once("guard");
    private static final RawAnimation A_RIPOSTE = once("riposte");
    private static final RawAnimation A_SALSA_BURST = once("salsa_burst");
    private static final RawAnimation A_ARENA_FRYER = once("arena_fryer");
    private static final RawAnimation A_ARENA_HECKLE = once("arena_heckle");
    private static final RawAnimation A_P2_MASHER = once("p2_masher");
    private static final RawAnimation A_EVADE = once("evade");
    private static final RawAnimation A_CLEAVER_TOSS = once("cleaver_toss");
    private static final RawAnimation A_BURRITO_TOSS = once("burrito_toss");
    private static final RawAnimation A_DRUNK_SWIG = once("drunk_swig");
    private static final RawAnimation A_DRUNK_FLURRY = once("drunk_flurry");
    private static final RawAnimation A_HAYMAKER = once("haymaker");
    private static final RawAnimation A_COMAL_SLAM = once("comal_slam");
    private static final RawAnimation A_BRICK = once("brick");
    private static final RawAnimation[] A_QTE_HOLD = {loop("qte_hold_cleaver"), loop("qte_hold_bottle"), loop("qte_hold_suplex")};
    private static final RawAnimation[] A_QTE_FAIL = {once("qte_fail_cleaver"), once("qte_fail_bottle"), once("qte_fail_suplex")};
    private static final RawAnimation A_QTE_BROKEN = once("qte_broken");
    private static final RawAnimation A_FRY_VOLLEY = once("p2_fry_volley");
    private static final RawAnimation A_MASH = once("p2_mash_slam");
    private static final RawAnimation A_TOT = once("p2_tot_throw");
    private static final RawAnimation A_STOMP = once("p2_stomp");
    private static final RawAnimation A_GRAVITY = once("p2_gravity");
    // the "form" controller (phase two): the fry wings, the crown, the butter
    private static final RawAnimation F_SPROUT = once("p2_sprout");
    private static final RawAnimation F_IDLE = loop("p2_form_idle");
    private static final RawAnimation F_FLY = loop("p2_form_fly");
    private static final RawAnimation F_AURA = once("p2_form_aura");
    private static final RawAnimation F_ATTACK = loop("p2_form_attack");
    private static final RawAnimation F_LIFT = once("p2_form_lift");

    private static final UUID PHASE2_SPEED = UUID.fromString("2b4a6c5e-9f0d-4d2b-9c3e-7a1f0e5d4c3b");
    public static final String BAR_KEY = "bossbar.papon.chef";
    private static final double ARENA_RADIUS = 44;
    private static final double TARGET_RANGE = 40;
    /** Phase two starts when he is down to this share of his health. */
    private static final float PHASE_TWO_AT = 0.75F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable(BAR_KEY),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private int stateTicks;
    private int speechTicks;
    private static final String VOICE2 = "speech.papon.boss2.";
    /** Ticks before he may make another remark in the fight (see quip). */
    private int quipCd;
    /** tickCount from which he may start a fight line again (the last one is over, plus a breath). */
    private int voiceFree;
    /** Client: the phase-two line he is saying (lip sync) and the tick it started. */
    @Nullable private VoiceLines.Line talk;
    private int talkStart;
    @Nullable private Vec3 home;
    /** Where the portal back into his coliseum opens (the truck window), and the way it faces; its entity. */
    @Nullable private Vec3 portalAt;
    private float portalYaw;
    @Nullable private java.util.UUID portalId;
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
    private enum Drive { REV, LEG, BRAKE, DONUT, AIR, DRIFT, BAIL }
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
    /** The one before lastAttack: neither comes up again straight away. */
    @Nullable private BossState prevAttack;
    /** tickCount when each attack last started (the picker favours what he has not done in a while). */
    private final java.util.EnumMap<BossState, Integer> lastUsed = new java.util.EnumMap<>(BossState.class);
    /** Which QTE comes next: they take turns, so every fight shows all three. */
    private int nextQte;
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
    // phase one, more: the dash combo, the punted van, the bail-out, the drift, the door
    private int dashCd = 60;
    private int grabCd = 120;
    /** Whoever he is holding (GRAB_COMBO): his passenger, and they cannot get down. */
    @Nullable private UUID grabbed;
    /** Set while he lets go himself, so the dismount goes through. */
    private boolean releasing;
    private int kickCd = 160;
    private int doorCd;
    private boolean bailUsed;
    private boolean drifted;
    // phase two
    private int barrageCd = 30;
    private int meteorCd = 140;
    private int vanThrowCd = 160;
    private int diveCd = 40;
    /** His coliseum (phase two), up in the sky over the fight; null on the ground. */
    @Nullable private Coliseum arena;
    private boolean arenaFailed;
    private int arenaLonely;
    /** Ticks before he may raise the coliseum again after it came down empty. */
    private int arenaRetryCd;
    /** The band has been woken and has played: a second visit skips all that. */
    private boolean bandWoken;
    /** Game tick (low 31 bits) of the song's first downbeat (the band's count-in); 0 before. */
    private int songStart;
    /** The mariachi whose chair he is off to kick (entity id), or -1. */
    private int kickWho = -1;
    /** Waiting on the band, he paces the front of the stand: which end he is headed for. */
    private int paceSide = 1;
    /** A guitar solo has started (a client heard it): ticks left to stop and listen; the cooldown after. */
    private int soloDue;
    private int soloCd = 400;
    /** The side of the stands he works first in the hype (+1 east, -1 west). */
    private int hypeSide = 1;
    /** Seconds (x20 ticks) until the stands get going on their own again. */
    private int crowdCd = 12;
    /** An old save's potato dome, to be taken down on the first tick. */
    @Nullable private CompoundTag oldDome;
    private int crowdRoarCd;
    private int crowdBooCd;
    private float orbitAngle;
    private int divesLeft;
    /** Dives for the next P2_DIVE (0: the usual three). */
    private int pendingDives;
    private int diveStage;
    private int diveTicks;
    private Vec3 diveDir = Vec3.ZERO;
    private boolean vanThrown;
    @Nullable private Vec3 liftFrom;
    private final List<UUID> foils = new java.util.ArrayList<>();
    private int foilCd = -1;
    private float foilSpin;
    // the rework
    private int guardCd = 60;
    private int guardBlocks;
    /** His recent losses, as a share of his health, decaying: a burst of damage puts the comal up. */
    private float recentHurt;
    /** His composure: health lost lately (drains 2.5% a tick); past PaponConfig.BURST_CAP hits glance off. */
    private float burst;
    /** Hits that glanced off his composure lately (decays): the sign of someone overpowered (more QTEs). */
    private float capHits;
    private int composureMsgCd;
    /** Per attacker, the ticks of their last melee hits on him: four in 2.5 s and he bursts (SALSA_BURST). */
    private final java.util.Map<java.util.UUID, java.util.ArrayDeque<Integer>> pressure = new java.util.HashMap<>();
    private int saltBurstCd;
    /** The arena turned on you: the fryer open in the sand, and the stands throwing things. */
    @Nullable private Freidora freidora;
    private int fryerCd = 200, heckleCd = 160, heckleTicks, masherCd = 300;
    @Nullable private java.util.UUID masherId;
    private boolean floorChecked;
    private int shoveCd;
    /** Ticks the current target has stayed out of reach (kiting with a bow, flying about). */
    private int farTicks;
    private int evadeCd = 40;
    private int cleaverCd = 60;
    private int burritoCd = 140;
    private int drunkCd = 160;
    private int haymakerCd = 200;
    private int comalCd = 180;
    private int brickCd = 400;
    private int hopCd = 300;
    private int qteCd = 300;
    /** Ticks spent walking in for a close-range move (he gives up and picks from range after 3 s). */
    private int closeIn;
    /** tickCount when the fight proper began (the roar): the picker counts unused attacks from here. */
    private int fightStart;
    private int fryCd = 60;
    private int mashCd = 120;
    private int totCd = 80;
    private int snareCd = 160;
    private int gravityCd = 500;
    /** He is going to the van to put a brick on the pedal (not to drive it). */
    private boolean forBrick;
    /** Ticks the van keeps driving itself at the target, brick on the pedal. */
    private int brickTicks;
    private int strafeTurn;
    /** The hot griddle round him (COMAL_SLAM): where, how much longer. */
    @Nullable private Vec3 griddleAt;
    private int griddleLeft;
    /** Mash waves rolling out: the prop, its radius, who it already got. */
    private final List<Object[]> mashWaves = new java.util.ArrayList<>();
    @Nullable private UUID spudId;
    @Nullable private Vec3 spudFloor;
    @Nullable private Vec3 burritoAt;
    private final List<double[]> snares = new java.util.ArrayList<>();
    // the QTEs
    private int[] qteKeys = new int[0];
    private int qteStep;
    private boolean qteResolved;
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
                .add(Attributes.MOVEMENT_SPEED, 0.42)
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
        entityData.define(DATA_FORM, 0);
        entityData.define(DATA_ARENA, false);
        entityData.define(DATA_FLYING, false);
        entityData.define(DATA_HELD, -1);
        entityData.define(DATA_QTE, 0);
    }

    public int qteKind() {
        return entityData.get(DATA_QTE);
    }

    /**
     * The hold the QTE uses: 0 La Última Cena (the cleaver), 1 Last Call (the bottle), 2 La Papa Suplex. The
     * newer ones borrow them: 3 ¡Apaga el Fuego! (mash a key while he pours hot sauce, the bottle's hold) and
     * 4 El Tango del Papa (arrow keys on the song's beat while he dances you round, the suplex's).
     */
    public int qteBase() {
        int k = qteKind();
        return k == 3 ? 1 : k == 4 ? 2 : Mth.clamp(k, 0, 2);
    }

    /** The entity id of whoever he holds, or -1. */
    public int heldId() {
        return entityData.get(DATA_HELD);
    }

    public int form() {
        return entityData.get(DATA_FORM);
    }

    /** Baked golden (the texture swap). */
    public boolean isBaked() {
        return form() >= 1;
    }

    /** Fully risen: fry wings, crown, butter; he flies. */
    public boolean isRisen() {
        return form() >= 2;
    }

    public boolean isFlying() {
        return entityData.get(DATA_FLYING);
    }

    private void setFlying(boolean f) {
        entityData.set(DATA_FLYING, f);
        setNoGravity(f);
    }

    @Nullable
    public Coliseum arena() {
        return arena;
    }

    /** In his coliseum (both sides: the band plays the music there, not the old score). */
    public boolean inColiseum() {
        return entityData.get(DATA_ARENA);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        var d = super.getDimensions(pose);
        return isRisen() ? d.scale(1.15F) : d;
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
            // a breath between attacks (none in a frenzy: see choose); in phase two none after one that landed: he presses
            think = isPhaseTwo() ? (swingHit ? 0 : 2) : 4;
        }
        boolean air = isRisen() && isFlying() && airborne(s);
        if (s != BossState.LEAP && s != BossState.LEAP_OUT && s != BossState.AIR_SPIKE && !air) {
            setNoGravity(false);
            hovering = false;
            entityData.set(DATA_FLYING, false);
        }
        if (getVehicle() instanceof CaravanEntity van && s != BossState.ENTER_VAN && s != BossState.DRIVING) {
            // whatever pulled him out of the driving (a command, a cutscene): out of the van, which stops
            van.releaseAutopilot();
            van.setGhost(false);
            stopRiding();
        }
                if (s != BossState.CHARGE && isGhost()) {
            setGhost(false);
            unstick();
        }
        if ((old == BossState.GRAB_COMBO || old == BossState.QTE_HOLD || old == BossState.QTE_FAIL)
                && s != BossState.GRAB_COMBO && s != BossState.QTE_HOLD && s != BossState.QTE_FAIL) {
            release();
            if (old == BossState.QTE_HOLD) endQte(false, true);
        }
        if (s == BossState.GUARD) guardBlocks = 0;
        entityData.set(DATA_STATE, s.ordinal());
        stateTicks = 0;
        getNavigation().stop();
    }

    /** States he can keep flying through in phase two (the rest bring him down to the ground). */
    private static boolean airborne(BossState s) {
        return switch (s) {
            case IDLE, GATLING, THROW, P2_METEOR, P2_DIVE, VAN_GRAB, VAN_THROW, P2_ARRIVE, P2_FRY_VOLLEY, P2_TOT, P2_GRAVITY -> true;
            default -> false;
        };
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
        portalAt = from;                            // the window he jumped out of: the way back into his coliseum
        portalYaw = (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90F;
        leapFrom = from;
        leapTo = to;
        home = to;
        vanId = van == null ? null : van.getUUID();
        setBossState(BossState.LEAP_OUT);
    }

    /** /papon boss: no window to jump out of, straight to the bottle. */
    public void beginHere(@Nullable CaravanEntity van) {
        home = position();
        portalAt = position().add(Vec3.directionFromRotation(0, getYRot()).scale(-4));
        portalYaw = getYRot();
        vanId = van == null ? null : van.getUUID();
        setBossState(BossState.CHUG);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide) {
            if (DATA_STATE.equals(key)) clientStateTicks = 0;
            if (DATA_SPEECH.equals(key)) {
                String sp = getSpeech();
                babbleTicks = sp.isEmpty() || Speech.voiced(sp) ? 0 : Speech.babbleLength(sp);
                // a line in his phase-two voice: the lip sync starts now, with the sound (sent the same tick)
                if (sp.startsWith(VOICE2)) {
                    talk = VoiceLines.line(sp.substring(VOICE2.length()));
                    talkStart = tickCount;
                }
            }
        }
        if (DATA_FORM.equals(key)) refreshDimensions();
    }

    // ------------------------------------------------------------------ speech

    @Override
    public String getSpeech() {
        return entityData.get(DATA_SPEECH);
    }

    @Override
    public void setSpeech(String encoded, int ticks) {
        entityData.set(DATA_SPEECH, encoded, true);      // forced: the same line again restarts the lip sync
        speechTicks = ticks;
    }

    private void say(String line, int ticks) {
        speak(line, ticks, true);
    }

    /** A line with no translation under it (it is already in English, or a scream). */
    private void sayPlain(String line, int ticks) {
        speak(line, ticks, false);
    }

    /**
     * He talks in his own voice (gen_boss_voice.py: English, lip-synced by client/BossFace): a line
     * recorded for it is said as speech.papon.boss2.*, its bubble held for as long as it plays. Anything
     * not recorded falls back to his old Spanish line.
     */
    private void speak(String line, int ticks, boolean gloss) {
        VoiceLines.Line v = VoiceLines.line(line);
        // in the fight a line waits for the last one to finish and settle (the cutscenes are timed by hand)
        BossState st = getBossState();
        boolean scripted = st.cutscene() || st.intro() || st == BossState.DEATH || st == BossState.PHASE;
        if (!scripted && tickCount < voiceFree) return;
        voiceFree = tickCount + (v != null ? v.ticks() : ticks) + 30;
        if (v != null) {
            Speech.say(this, ChatFormatting.DARK_RED, VOICE2 + line, true, false, Math.max(ticks, v.ticks() + 12));
            quipCd = Math.max(quipCd, v.ticks() + 60);
            return;
        }
        Speech.say(this, ChatFormatting.DARK_RED, "speech.papon.boss." + line, true, gloss, ticks);
    }

    /** A moment of the phase-two cutscene, timed to his lines (VoiceLines.beat; {@code fallback} with no voice). */
    private static int beat(String key, int fallback) {
        return VoiceLines.beat(key, fallback);
    }

    /** A remark in the fight (phase two, never over another line or in a cutscene), now and then. */
    private void quip(String line, float chance) {
        if (!isPhaseTwo() || quipCd > 0 || speechTicks > 0 || getBossState().cutscene() || random.nextFloat() >= chance) return;
        say(line, 40);
    }

    /** Client: the phase-two line he is saying and how far into it he is (seconds), or null. */
    @Nullable
    public VoiceLines.Line talking() {
        return talk;
    }

    public double talkTime(float partial) {
        return (tickCount - talkStart + partial) / 20.0;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientStateTicks++;
            if (isBaked() && tickCount % 14 == 0) {
                // fresh out of the oven: a wisp of steam off the top of him
                level().addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.SMOKE.get(), -1, 0.5F, 1.8F,
                        0xF2EEE8, 0.45F, 26, 0), getX() + (random.nextDouble() - 0.5) * getBbWidth() * 0.5,
                        getY() + getBbHeight() * 0.95, getZ() + (random.nextDouble() - 0.5) * getBbWidth() * 0.5, 0, 0.03, 0);
            }
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
        if (quipCd > 0) quipCd--;
        bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        if (tickCount % 10 == 0) updateBossBarPlayers();
    }

    /** Dev (AutoShot pose mode): no thinking, no moving, the state held where it is, for screenshots of a pose. */
    public static volatile boolean devFrozen;

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (devFrozen) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        stateTicks++;
        tickCooldowns();
        if (soloDue > 0) soloDue--;
        if (soloCd > 0) soloCd--;
        tickShockwave(level);
        LivingEntity target = pickTarget(level);
        record(target);
        tickFires(level);
        tickLineWave(level);
        tickArena(level);
        tickFoils(level);
        tickThrownVan(level);
        tickGriddle(level);
        tickMashWaves(level);
        tickSnares(level);
        tickBrickVan(level, target);
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
            case DASH_COMBO -> tickDash(level, target);
            case PUNT_RUN -> tickPuntRun(level, target);
            case VAN_KICK -> tickVanKick(level, target);
            case BAILOUT -> tickBailout(level, target);
            case P2_KNEEL -> tickKneel(level);
            case P2_TALK -> tickTalk(level);
            case P2_SEASON -> tickSeason(level);
            case P2_SCREAM -> tickScream(level);
            case P2_ARRIVE -> tickArrive(level);
            case P2_NOTICE -> tickNotice(level);
            case P2_MARCH -> tickMarch(level);
            case P2_WAKE -> tickWake(level);
            case P2_WAIT -> tickWait(level);
            case P2_KICK -> tickKick(level);
            case P2_COUNT -> tickCount(level);
            case P2_SHOWTIME -> tickShowtime(level);
            case P2_APOLOGY -> tickApology(level);
            case P2_RESUME -> tickResume(level);
            case P2_HYPE -> tickHype(level);
            case P2_SOLO -> tickSolo(level);
            case P2_BARRAGE -> tickBarrage(level, target);
            case P2_METEOR -> tickMeteor(level, target);
            case VAN_GRAB -> tickVanGrab(level, target);
            case VAN_THROW -> tickVanThrow(level, target);
            case P2_DIVE -> tickDive(level, target);
            case GRAB -> tickGrab(level, target);
            case GRAB_COMBO -> tickGrabCombo(level);
            case GUARD -> tickGuard(level, target);
            case RIPOSTE -> tickRiposte(level, target);
            case EVADE -> tickEvade(level, target);
            case CLEAVER_TOSS -> tickCleaverToss(level, target);
            case BURRITO_TOSS -> tickBurritoToss(level, target);
            case DRUNK_SWIG -> tickDrunkSwig(level, target);
            case DRUNK_FLURRY -> tickDrunkFlurry(level, target);
            case HAYMAKER -> tickHaymaker(level, target);
            case COMAL_SLAM -> tickComalSlam(level, target);
            case BRICK -> tickBrick(level, target);
            case VAN_HOP -> tickVanHop(level, target);
            case QTE_GRAB -> tickQteGrab(level, target);
            case QTE_HOLD -> tickQteHold(level);
            case QTE_FAIL -> tickQteFail(level);
            case QTE_BROKEN -> tickQteBroken(level);
            case P2_FRY_VOLLEY -> tickFryVolley(level, target);
            case P2_MASH -> tickMash(level, target);
            case P2_TOT -> tickTot(level, target);
            case P2_SNARE -> tickSnare(level, target);
            case P2_GRAVITY -> tickGravity(level, target);
            case SALSA_BURST -> tickSalsaBurst(level);
            case ARENA_FRYER -> tickArenaFryer(level, target);
            case ARENA_HECKLE -> tickArenaHeckle(level, target);
            case P2_MASHER -> tickMasher(level, target);
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
        dashCd--;
        grabCd--;
        kickCd--;
        doorCd--;
        barrageCd--;
        meteorCd--;
        vanThrowCd--;
        diveCd--;
        arenaRetryCd--;
        crowdRoarCd--;
        crowdBooCd--;
        guardCd--;
        evadeCd--;
        cleaverCd--;
        burritoCd--;
        drunkCd--;
        haymakerCd--;
        comalCd--;
        brickCd--;
        hopCd--;
        qteCd--;
        fryCd--;
        mashCd--;
        totCd--;
        snareCd--;
        gravityCd--;
        recentHurt *= 0.94F;
        burst *= 0.975F;
        capHits *= 0.995F;
        composureMsgCd--;
        saltBurstCd--;
        fryerCd--;
        masherCd--;
        heckleCd--;
        shoveCd--;
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
            Fx.chunks(level, at, 8, 0.18F, 0xD8EEE6, 0.35);
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
            fightStart = tickCount;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ deciding

    private void tickIdle(ServerLevel level, @Nullable LivingEntity target) {
        if (phaseDue) {
            phaseDue = false;
            setBossState(BossState.P2_KNEEL);
            return;
        }
        if (soloDue > 0 && inColiseum()) {
            soloDue = 0;
            setBossState(BossState.P2_SOLO);
            return;
        }
        if (isRisen() && arena == null && !arenaFailed && arenaRetryCd <= 0 && target != null && noTargetTicks == 0) {
            // back for more after the coliseum came down: up it goes again
            planArena((ServerLevel) level());
            setBossState(BossState.P2_SCREAM);
            return;
        }
        if (home != null && distanceToSqr(home) > ARENA_RADIUS * ARENA_RADIUS) {
            getNavigation().moveTo(home.x, home.y, home.z, 1.2);
            return;
        }
        if (target == null) {
            if (noTargetTicks > 200 && tickCount % 20 == 0) heal(getMaxHealth() * 0.01F);
            if (isRisen()) {
                hover(null);
                return;
            }
            if (home != null && distanceToSqr(home) > 16 && getNavigation().isDone()) getNavigation().moveTo(home.x, home.y, home.z, 0.8);
            return;
        }
        getLookControl().setLookAt(target, 30, 30);
        double d = distanceTo(target);
        farTicks = d > 9 ? farTicks + 1 : 0;
        if (tickCount % 200 == 0 && random.nextInt(3) == 0) say(taunt(), 50);
        if (think > 0) {
            if (isRisen()) hover(target);
            else chase(target, d);
            return;
        }
        CaravanEntity van = van(level);
        if (vanCd <= 0 && van != null && !van.isVehicle() && distanceTo(van) < 40 && d < 40) {
            if (isFlying()) setFlying(false);
            setBossState(BossState.TO_VAN);
            say("keys", 40);
            return;
        }
        // a close-range move he has not used in a while is ready: he walks in for it (3 s at most) before choosing
        if (d > 4.5 && closeIn < 60 && closeMoveDue(target)) {
            closeIn++;
            if (isRisen()) hover(target);
            else chase(target, d);
            return;
        }
        BossState pick = choose(level, target, d);
        if (pick != null) {
            closeIn = 0;
            startAttack(pick, target);
        }
        else if (isRisen()) hover(target);
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
    /** One of his taunts; in phase two mostly the new, charming ones. */
    private String taunt() {
        if (isPhaseTwo() && random.nextInt(10) < 7) return "charm" + random.nextInt(6);
        return "taunt" + random.nextInt(4);
    }

    private BossState choose(ServerLevel level, LivingEntity target, double d) {
        ctxTarget = target;
        if (misses >= FRENZY) {
            misses = 0;
            think = 0;
            if (drunkCd <= 0 && d < 10) return BossState.DRUNK_SWIG;          // a miss streak makes him drink
            chargesLeft = isPhaseTwo() ? 2 : 1;
            say(taunt(), 40);
            return BossState.CHARGE_WINDUP;
        }
        double dy = target.getY() - getY();
        boolean p2 = isPhaseTwo();
        boolean risen = isRisen();
        CaravanEntity van = van(level);
        boolean vanFree = van != null && !van.isVehicle() && !van.isSliding() && !vanThrown && brickTicks <= 0;
        // pillaring or kiting: up after you, off the van's roof if it is handy; otherwise whichever way of
        // closing the gap he has not used lately (the leap, the blink, the dash, the charge, the dive)
        if (dy > 3.0 || d > 18) {
            if (!risen && vanFree && hopCd <= 0 && distanceTo(van) < 12) {
                hopCd = 360;
                return BossState.VAN_HOP;
            }
            java.util.List<BossState> closers = new java.util.ArrayList<>();
            if (risen ? diveCd <= 0 : leapCd <= 0) closers.add(risen ? BossState.P2_DIVE : BossState.LEAP);
            if (blinkCd <= 0) closers.add(BossState.BLINK);
            if (dy <= 3.0 && chargeCd <= 0) closers.add(BossState.CHARGE_WINDUP);
            if (dy <= 3.0 && d < 24 && dashCd <= 0) closers.add(BossState.DASH_COMBO);
            if (d < 30 && gatlingCd <= 0) closers.add(BossState.GATLING);
            BossState c = pickWeighted(closers);
            if (c != null) return c;
        }
        java.util.List<BossState> options = new java.util.ArrayList<>();
        // close: the flurry, the uppercut, the grab, the drunk set, the QTE
        if (d < 3.8 && uppercutCd <= 0) options.add(BossState.UPPERCUT);
        if (d < 7.0 && swipeCd <= 0) options.add(BossState.SWIPE);
        if (d < 5.5 && grabCd <= 0 && !target.isPassenger()) options.add(BossState.GRAB);
        if (d < 8.0 && qteCd <= 0 && target instanceof Player && !target.isPassenger()) options.add(BossState.QTE_GRAB);
        if (d < 9 && drunkCd <= 0) options.add(BossState.DRUNK_SWIG);
        if (d >= 3 && d < 10 && haymakerCd <= 0) options.add(BossState.HAYMAKER);
        if (d < 7 && comalCd <= 0 && !risen) options.add(BossState.COMAL_SLAM);
        // middle: dashes, the leap, the charge, the fire, the cleavers, the burrito
        if (d >= 5 && d < 16 && leapCd <= 0) options.add(BossState.LEAP);
        if (d >= 6 && d < 24 && chargeCd <= 0) options.add(BossState.CHARGE_WINDUP);
        if (d >= 3 && d < 9 && breathCd <= 0) options.add(BossState.BREATH);
        if (d >= 4 && d < 20 && blinkCd <= 0) options.add(BossState.BLINK);
        if (d >= 3 && d < 13 && dashCd <= 0) options.add(BossState.DASH_COMBO);
        if (d >= 5 && d < 18 && cleaverCd <= 0) options.add(BossState.CLEAVER_TOSS);
        if (d >= 4 && d < 20 && burritoCd <= 0) options.add(BossState.BURRITO_TOSS);
        // far: molotovs, the gatling
        if (d >= 9 && throwCd <= 0) options.add(BossState.THROW);
        if (d >= 7 && d < 30 && gatlingCd <= 0) options.add(BossState.GATLING);
        // the van, on foot
        if (vanFree && !risen && kickCd <= 0 && distanceTo(van) < 16 && target.distanceTo(van) < 28 && target.distanceTo(van) > 5) {
            options.add(BossState.PUNT_RUN);
        }
        if (vanFree && brickCd <= 0 && distanceTo(van) < 24 && target.distanceTo(van) > 6) options.add(BossState.BRICK);
        if (risen) {
            options.remove(BossState.LEAP);
            if (d < 6 && barrageCd <= 0) options.add(BossState.P2_BARRAGE);
            if (d >= 5 && meteorCd <= 0) options.add(BossState.P2_METEOR);
            if (d >= 4 && diveCd <= 0) options.add(BossState.P2_DIVE);
            if (vanFree && vanThrowCd <= 0 && distanceTo(van) < 36 && target.distanceTo(van) < 30) options.add(BossState.VAN_GRAB);
            if (d >= 5 && fryCd <= 0) options.add(BossState.P2_FRY_VOLLEY);
            if (mashCd <= 0) options.add(BossState.P2_MASH);
            if (d >= 6 && totCd <= 0) options.add(BossState.P2_TOT);
            if (snareCd <= 0) options.add(BossState.P2_SNARE);
            if (gravityCd <= 0) options.add(BossState.P2_GRAVITY);
            if (inColiseum() && arena != null) {
                if (fryerCd <= 0 && freidora == null) options.add(BossState.ARENA_FRYER);
                if (heckleCd <= 0 && heckleTicks <= 0) options.add(BossState.ARENA_HECKLE);
                if (masherCd <= 0) options.add(BossState.P2_MASHER);
            }
        }
        return pickWeighted(options);
    }

    /** Whether a close-range move (one you only see if he is on top of you) is ready and has waited 20 s. */
    private boolean closeMoveDue(LivingEntity target) {
        boolean risen = isRisen();
        int[] cds = {uppercutCd, grabCd, drunkCd, risen ? 1 : comalCd, breathCd, target instanceof Player ? qteCd : 1, risen ? barrageCd : 1};
        BossState[] moves = {BossState.UPPERCUT, BossState.GRAB, BossState.DRUNK_SWIG, BossState.COMAL_SLAM, BossState.BREATH,
                BossState.QTE_GRAB, BossState.P2_BARRAGE};
        for (int i = 0; i < moves.length; i++) {
            if (cds[i] <= 0 && tickCount - lastUsed.getOrDefault(moves[i], fightStart) > 400) return true;
        }
        return false;
    }

    @Nullable private LivingEntity ctxTarget;

    /**
     * How much more he likes an attack for what the target is doing: a raised shield gets the grabs and the
     * shield-breakers; someone in the air gets the guns, the leap and the cleavers; someone hitting him point
     * blank gets the close punishes and the blink away; someone kiting him gets the gap closers; someone
     * nearly dead, or overpowered (hits glancing off his composure, a wall of armor), gets the QTE.
     */
    private float context(BossState s) {
        LivingEntity t = ctxTarget;
        if (t == null) return 1F;
        float k = 1F;
        boolean blocking = t instanceof Player p && p.isBlocking();
        boolean airborne = t instanceof Player p && (p.isFallFlying() || p.getAbilities().flying)
                || (!t.onGround() && t.getY() - groundY(t.position()) > 3);
        boolean grabby = s == BossState.GRAB || s == BossState.QTE_GRAB;
        if (blocking && (grabby || s == BossState.HAYMAKER || s == BossState.SWIPE || s == BossState.COMAL_SLAM)) k *= 3F;
        if (airborne && (s == BossState.GATLING || s == BossState.LEAP || s == BossState.P2_DIVE || s == BossState.CLEAVER_TOSS
                || s == BossState.THROW || s == BossState.BLINK)) k *= 3F;
        if (underPressure(t) && (grabby || s == BossState.UPPERCUT || s == BossState.DRUNK_SWIG || s == BossState.BREATH
                || s == BossState.P2_BARRAGE || s == BossState.BLINK || s == BossState.P2_MASH)) k *= 2F;
        if (farTicks > 100 && (s == BossState.CHARGE_WINDUP || s == BossState.DASH_COMBO || s == BossState.BLINK || s == BossState.LEAP
                || s == BossState.P2_DIVE || s == BossState.BURRITO_TOSS || s == BossState.P2_GRAVITY)) k *= 2F;
        boolean overpowered = capHits > 2.5F || t.getArmorValue() >= 20;
        if (s == BossState.QTE_GRAB && (overpowered || t.getHealth() < t.getMaxHealth() * 0.35F)) k *= 3F;
        return k;
    }

    /** How much he likes each attack when it is on offer (before how long it has been). */
    private static float baseWeight(BossState s) {
        return switch (s) {
            case SWIPE, UPPERCUT, THROW -> 0.6F;                         // the cheap ones: short cooldowns, they would crowd out the rest
            case LEAP -> 0.7F;
            case QTE_GRAB, CLEAVER_TOSS, HAYMAKER, BURRITO_TOSS, P2_GRAVITY, P2_METEOR, ARENA_FRYER, ARENA_HECKLE, P2_MASHER -> 1.6F;   // the signature moves
            default -> 1.0F;
        };
    }

    /**
     * The picker (a weighted draw, Cataclysm's goals by way of a deck): the last two attacks are out,
     * anything he has not done for 30 s and can do now he does, and otherwise each attack's chance grows
     * with the time since he last used it, so nothing is never seen and nothing comes up all the time.
     */
    @Nullable
    private BossState pickWeighted(java.util.List<BossState> options) {
        if (options.isEmpty()) return null;
        java.util.List<BossState> fresh = new java.util.ArrayList<>(options);
        fresh.remove(lastAttack);
        if (fresh.size() > 1) fresh.remove(prevAttack);
        if (fresh.isEmpty()) fresh = options;
        BossState overdue = null;
        int oldest = -1;
        for (BossState s : fresh) {
            int since = tickCount - lastUsed.getOrDefault(s, fightStart);
            if (since > 600 && since > oldest) {
                oldest = since;
                overdue = s;
            }
        }
        if (overdue != null) return overdue;
        float total = 0;
        float[] w = new float[fresh.size()];
        for (int i = 0; i < w.length; i++) {
            BossState s = fresh.get(i);
            int since = tickCount - lastUsed.getOrDefault(s, fightStart);
            w[i] = baseWeight(s) * context(s) * (1F + Math.min(since, 900) / 200F);
            total += w[i];
        }
        float r = random.nextFloat() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) return fresh.get(i);
        }
        return fresh.get(fresh.size() - 1);
    }

    private void startAttack(BossState pick, LivingEntity target) {
        boolean p2 = isPhaseTwo();
        swingHit = false;
        if (pick != lastAttack) prevAttack = lastAttack;
        lastAttack = pick;
        lastUsed.put(pick, tickCount);
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
            case DASH_COMBO -> dashCd = p2 ? 90 : 130;
            case GRAB -> grabCd = p2 ? 220 : 300;
            case PUNT_RUN -> kickCd = 120;
            case P2_BARRAGE -> barrageCd = 90;
            case P2_METEOR -> meteorCd = 260;
            case P2_DIVE -> diveCd = 140;
            case VAN_GRAB -> vanThrowCd = 200;
            case CLEAVER_TOSS -> cleaverCd = p2 ? 110 : 150;
            case BURRITO_TOSS -> burritoCd = p2 ? 260 : 340;
            case DRUNK_SWIG -> drunkCd = p2 ? 280 : 380;
            case HAYMAKER -> haymakerCd = p2 ? 200 : 260;
            case COMAL_SLAM -> comalCd = 320;
            case QTE_GRAB -> {
                qteCd = (p2 ? 260 : 340) / (capHits > 2.5F || target.getArmorValue() >= 20 ? 2 : 1);
                // they take turns: La Última Cena, Last Call, La Papa Suplex, and round again
                if (nextQte == 4 && !inColiseum()) nextQte = 0;
                entityData.set(DATA_QTE, nextQte);
                nextQte = (nextQte + 1) % 5;
            }
            case P2_FRY_VOLLEY -> fryCd = 160;
            case P2_MASH -> mashCd = 240;
            case P2_TOT -> totCd = 180;
            case P2_SNARE -> snareCd = 300;
            case P2_GRAVITY -> gravityCd = 700;
            case ARENA_FRYER -> fryerCd = 420;
            case ARENA_HECKLE -> heckleCd = 520;
            case P2_MASHER -> masherCd = 760;
            case BRICK -> {
                brickCd = p2 ? 560 : 700;
                forBrick = true;
                pick = BossState.TO_VAN;
            }
            default -> {}
        }
        if (isFlying() && !airborne(pick)) {
            // come down out of the air to do it
            setDeltaMovement(getDeltaMovement().x * 0.3, -1.3, getDeltaMovement().z * 0.3);
            hasImpulse = true;
        }
        setBossState(pick);
    }

    /** /papon bossstate: an attack starts as if he had picked it (cooldowns, its set-up), anything else is just set. */
    public void force(BossState s) {
        LivingEntity target = level() instanceof ServerLevel level ? pickTarget(level) : null;
        boolean startable = switch (s) {
            case SWIPE, UPPERCUT, LEAP, CHARGE_WINDUP, BREATH, THROW, BLINK, GATLING, DASH_COMBO, GRAB, PUNT_RUN, P2_BARRAGE, P2_METEOR,
                 P2_DIVE, VAN_GRAB, CLEAVER_TOSS, BURRITO_TOSS, DRUNK_SWIG, HAYMAKER, COMAL_SLAM, QTE_GRAB, P2_FRY_VOLLEY, P2_MASH, P2_TOT,
                 P2_SNARE, P2_GRAVITY, BRICK, VAN_HOP -> true;
            default -> false;
        };
        if (startable && target != null) startAttack(s, target);
        else setBossState(s);
    }

    private static boolean attack(BossState s) {
        return switch (s) {
            case SWIPE, SWIPE_OVERHEAD, SLAM, BREATH, THROW, LEAP, LEAP_LAND, CHARGE_WINDUP, CHARGE, UPPERCUT, AIR_SPIKE,
                 BLINK, GATLING, DASH_COMBO, VAN_KICK, BAILOUT, P2_BARRAGE, P2_METEOR, P2_DIVE, VAN_THROW, GRAB, GRAB_COMBO,
                 RIPOSTE, CLEAVER_TOSS, BURRITO_TOSS, DRUNK_FLURRY, HAYMAKER, COMAL_SLAM, QTE_GRAB, QTE_FAIL, P2_FRY_VOLLEY, P2_MASH,
                 P2_TOT, P2_SNARE, P2_GRAVITY -> true;
            default -> false;
        };
    }

    /**
     * Between attacks he does not just walk at you: in the middle distance he circles, changing
     * direction now and then (and when he bumps into something), closing in as he goes.
     */
    private void chase(LivingEntity target, double d) {
        if (d < 2.6) {
            getNavigation().stop();
            return;
        }
        if (think > 0 && d > 3.5 && d < 12) {
            if (--strafeTurn <= 0 || horizontalCollision) {
                strafeTurn = 25 + random.nextInt(30);
                orbitDir = random.nextBoolean() ? 1 : -1;
            }
            Vec3 off = position().subtract(target.position()).multiply(1, 0, 1);
            double a = Math.atan2(off.z, off.x) + orbitDir * 0.6;
            double r = Math.max(3.0, d - 1.2);
            Vec3 pt = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            getMoveControl().setWantedPosition(pt.x, pt.y, pt.z, isPhaseTwo() ? 1.5 : 1.3);
            getLookControl().setLookAt(target, 30, 30);
            return;
        }
        if (getNavigation().isDone() || tickCount % 6 == 0) {
            getNavigation().moveTo(target, isPhaseTwo() ? (d > 8 ? 1.75 : 1.5) : (d > 8 ? 1.55 : 1.35));
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
            for (int i = 0; i < 2; i++) {
                Vec3 v = look.add((random.nextDouble() - 0.5) * 0.18, (random.nextDouble() - 0.35) * 0.12, (random.nextDouble() - 0.5) * 0.18)
                        .normalize().scale(0.6 + random.nextDouble() * 0.3);
                Fx.jet(level, mouth, v, 0.55F, Fx.WHITE);
            }
            if (t % 8 == 0) Fx.smoke(level, mouth.add(look.scale(3)), 1, 0.3, 0.8F, Fx.SMOKE);
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
        if (target != null && t < 7) face(target.position());
        if (t == 1 && target != null) {
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            Fx.warnLine(level, position().add(f.scale(1.5)), position().add(f.scale(16)), 0.9F, Fx.DANGER, BossState.CHARGE_WINDUP.duration);
        }
        if (t % 4 == 0) Fx.dust(level, position(), 0.4, 1);
        if (t >= BossState.CHARGE_WINDUP.duration) {
            chargeHits.clear();
            chargeLast = null;
            chargeStuck = 0;
            setBossState(BossState.CHARGE);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 1.2F);
        }
    }

    /**
     * The tequila rush, flat out (the Leviathan's): almost a block a tick, steering toward you at no
     * more than 3.5 degrees a tick, so the dodge is late and sideways. Drunk enough to be half a ghost, he goes straight
     * through trees and walls, riding the lie of the land. Everything in front of him is hit once
     * (shields break) and flung aside. A charge that hits nothing leaves him reeling (STAGGER: the
     * punish window); phase two and a frenzy run it again.
     */
    private void tickCharge(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t == 1) setGhost(true);
        if (target != null) turnToward(target.position(), 3.5F);
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        double speed = isPhaseTwo() ? 1.1 : 0.95;
        double vy = Mth.clamp((groundY(position()) - getY()) * 0.5, -0.8, 0.9);
        setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        getNavigation().stop();
        AABB box = getBoundingBox().inflate(0.4, 0, 0.4).expandTowards(dir.scale(1.2));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::hittable)) {
            if (!chargeHits.add(e.getId())) continue;
            if (e.hasEffect(com.groid.papon.registry.ModEffects.BURRITO.get())) {
                // burrito bowling: through the tortilla and out the other side
                e.removeEffect(com.groid.papon.registry.ModEffects.BURRITO.get());
                if (Mechanics.trueHit(e, isPhaseTwo() ? 0.3F : 0.25F, this, true)) {
                    swingHit = true;
                    e.setDeltaMovement(dir.scale(2.2).add(0, 0.9, 0));
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.chunks(level, e.position().add(0, 1, 0), 14, 0.3F, 0xEED69C, 0.5);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.6F);
                    if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.0F, 20);
                }
                continue;
            }
            if (strike(e, 14F, ModDamage.BELLY_FLOP, true)) {
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                double sd = e.position().subtract(position()).dot(side) >= 0 ? 1 : -1;
                e.setDeltaMovement(side.scale(2.0 * sd).add(dir.scale(0.8)).add(0, 0.55, 0));
                e.hurtMarked = true;
                if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.7F, 14);
            }
        }
        if (t % 2 == 0) Fx.smoke(level, position().add(0, 1.2, 0).subtract(dir.scale(1.2)), 1, 0.5, 0.8F, Fx.SOUL);
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
        if (arena != null && arena.built() && arena.contains(at)) return arena.floorY();
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
            Vec3 m = blinksLeft <= 0 ? new Vec3(blinkTo.x, target.getY(), blinkTo.z) : blinkTo;
            Fx.warnClosing(level, m, 0.9F, Fx.DANGER, half);
        }
        if (t == half && blinkTo != null) {
            Fx.smoke(level, position().add(0, 1.4, 0), 3, 0.5, 1.0F, 0xE6DDCC);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4F, 0.6F);
            setPos(blinkTo.x, blinkTo.y, blinkTo.z);
            setDeltaMovement(Vec3.ZERO);
            face(target.position());
            Fx.smoke(level, position().add(0, 1.4, 0), 3, 0.5, 1.0F, 0xE6DDCC);
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
            if (t % 2 == 0) Fx.burst(level, muzzle, 0.35F, 0, 0);
            if (t % 6 == 0) Fx.smoke(level, muzzle, 1, 0.05, 0.4F, Fx.SMOKE);
            if (t % 2 == 0) level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 0.7F, 1.7F);
            if (t % 10 == 0) PaponNetwork.shake(level, position(), 10, 0.1F, 6);
        }
        if (t >= BossState.GATLING.duration) setBossState(BossState.IDLE);
    }

    private void tickStagger(ServerLevel level) {
        if (stateTicks % 8 == 0) {
            double a = stateTicks * 0.6;
            Fx.glint(level, new Vec3(getX() + Math.cos(a) * 0.9, getY() + getBbHeight() + 0.3, getZ() + Math.sin(a) * 0.9), 0.35F, 0xFFE070);
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

    /** Where the target will be in {@code ahead} ticks, from where it has been (Ignis' predictive dash; his echoes aim with it too). */
    public Vec3 predict(LivingEntity target, int ahead) {
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
        // a heavy hit also takes a share of your maximum health through armor: no amount of armor lets you trade with him
        if (hit && heavy && e.isAlive()) {
            Mechanics.trueHit(e, PaponConfig.ARMOR_PIERCE.get().floatValue() * (isPhaseTwo() ? 1.35F : 1F), this, true);
        }
        if (hit && e instanceof Player && crowdRoarCd <= 0 && level() instanceof ServerLevel level) {
            crowdRoarCd = 50;
            crowdReact(level, e.isAlive() ? CrowdMood.ROAR : CrowdMood.CHANT, e.isAlive() ? 40 : 200);
            if (e.isAlive() && inColiseum()) quip("landed" + random.nextInt(2), 0.3F);
        }
        return hit;
    }

    private boolean hittable(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isAlliedTo(this) || e instanceof CaravanEntity || e instanceof FoilPotatoEntity) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return !(e instanceof com.groid.papon.entity.npc.TacoNpc) && !(e instanceof com.groid.papon.entity.WanderingPaponEntity);
    }

    private List<LivingEntity> around(ServerLevel level, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 2.5, r),
                e -> hittable(e) && e.distanceTo(this) <= r + e.getBbWidth() / 2);
    }

    /** The landing marker: a red ring on the ground under the target, following them. */
    private void warnRing(ServerLevel level, Vec3 at) {
        if (stateTicks % 4 != 0) return;
        Fx.warn(level, new Vec3(at.x, groundY(at), at.z), 2.0F, Fx.DANGER, 5);
    }

    private void ring(ServerLevel level, Vec3 at, double r) {
        Fx.shock(level, at, (float) r, Fx.WHITE);
        Fx.dust(level, at, r * 0.4, 2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.2F);
    }

    /** The overhead's shockwave: a block a tick along the ground for ten blocks, grounded targets only. */
    private void tickLineWave(ServerLevel level) {
        if (waveStep < 0 || waveFrom == null || waveDir == null) return;
        int k = waveStep++;
        Vec3 p = waveFrom.add(waveDir.scale(k));
        if (k % 2 == 0) Fx.dust(level, p, 0.3, 1);
        Fx.splat(level, p, 0.7F, 0x5A4A3A, 30);
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
            if ((tickCount + (int) (f[0] * 7)) % 12 == 0) Fx.groundFlame(level, new Vec3(f[0], f[1], f[2]), 0.45F);
            if (tickCount % 10 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(f[0] - 1.2, f[1] - 0.2, f[2] - 1.2, f[0] + 1.2, f[1] + 1.2, f[2] + 1.2), this::hittable)) {
                    if (e.hurt(source(ModDamage.TEQUILA_FIRE), 3F * dmg())) {
                        e.setSecondsOnFire(3);
                        if (tickCount % 20 == 0) Mechanics.heat(e, 1, this);
                    }
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

    /** A hit that glanced off his composure: a clank and gold sparks, and now and then a word to the one hitting. */
    private void composure(@Nullable Entity attacker) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = position().add(0, getBbHeight() * 0.6, 0);
        Fx.spark(level, at, 5, 0.5, 0.3F, 0xFFE07A);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.8F);
        if (composureMsgCd <= 0 && attacker instanceof ServerPlayer sp) {
            composureMsgCd = 80;
            sp.displayClientMessage(Component.translatable("status.papon.composure").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }

    /** Counts {@code p}'s melee hit; true when it is the fourth in 2.5 s and he can burst now. */
    private boolean pressed(Player p, BossState s) {
        java.util.ArrayDeque<Integer> q = pressure.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
        q.addLast(tickCount);
        while (!q.isEmpty() && tickCount - q.peekFirst() > 50) q.pollFirst();
        boolean free = s == BossState.IDLE || s == BossState.SWIPE || s == BossState.SWIPE_OVERHEAD || s == BossState.UPPERCUT
                || s == BossState.RIPOSTE || s == BossState.EVADE || s == BossState.TO_VAN || s == BossState.DRINK_HANDLE
                || s == BossState.THROW || s == BossState.BREATH || s == BossState.GATLING || s == BossState.CLEAVER_TOSS;
        return q.size() >= 4 && saltBurstCd <= 0 && free && !isFlying();
    }

    /** The current target has been hitting him in melee lately (two or more hits in 3 s). */
    private boolean underPressure(LivingEntity target) {
        java.util.ArrayDeque<Integer> q = pressure.get(target.getUUID());
        if (q == null) return false;
        int n = 0;
        for (int t : q) if (tickCount - t < 60) n++;
        return n >= 2;
    }

    private void tickToVan(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || stateTicks > 260 || (forBrick && van.isBricked())) {
            forBrick = false;
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
        if ((horizontalDist(door) < 2.2 || distanceTo(van) < 3.4) && forBrick) {
            forBrick = false;
            setBossState(BossState.BRICK);
            return;
        }
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
        // a sprint to the door (he cannot be stopped on the way: half damage, no knockback), shoving anyone in his way
        shoveNear(level);
        if (horizontalDist(door) < 4) {
            getMoveControl().setWantedPosition(door.x, door.y, door.z, isPhaseTwo() ? 2.0 : 1.75);
        } else if (getNavigation().isDone() || tickCount % 6 == 0) {
            getNavigation().moveTo(door.x, door.y, door.z, isPhaseTwo() ? 2.0 : 1.75);
        }
    }

    private void tickDrinkHandle(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        if (target != null) face(target.position());
        shoveNear(level);
        if (stateTicks == 46) {
            Vec3 back = position().subtract(getLookAngle().scale(1.5)).add(0, 0.5, 0);
            Fx.chunks(level, back, 8, 0.18F, 0xD8EEE6, 0.35);
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

    /**
     * On his way to the van and downing the handle he is not open: anyone who gets within reach is backhanded
     * away (and takes a share of their health through armor), once a second.
     */
    private void shoveNear(ServerLevel level) {
        if (shoveCd > 0) return;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.6), this::hittable)) {
            if (!(e instanceof Player)) continue;
            shoveCd = 20;
            face(e.position());
            Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
            e.setDeltaMovement(away.scale(1.3).add(0, 0.45, 0));
            e.hurtMarked = true;
            strike(e, 5F, ModDamage.SPATULA, true);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.4F, 0.7F);
            Fx.spark(level, e.position().add(0, 1, 0), 6, 0.3, 0.3F, 0xFFD27A);
        }
    }

    /**
     * Face-tanked (four melee hits from one player in 2.5 s): he balls up, shaking (0.45 s), and bursts in a
     * blast of habanero salsa: everyone within 6.5 blocks thrown off him, a share of their health through
     * armor, a Habanero stack. Not fire: fire immunity does not help.
     */
    private void tickSalsaBurst(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        if (t == 1) level.playSound(null, getX(), getY(), getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (t < 10 && t % 2 == 0) Fx.spark(level, position().add(0, getBbHeight() * 0.5, 0), 6, 0.6, 0.3F, 0xE0301C);
        if (t == 10) {
            Vec3 c = position().add(0, getBbHeight() * 0.45, 0);
            Fx.blast(level, c, 2.6F);
            Fx.shock(level, position(), 7F, 0xD8261A);
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2 * i / 14;
                Vec3 at = position().add(Math.cos(a) * (2 + random.nextDouble() * 4), 0, Math.sin(a) * (2 + random.nextDouble() * 4));
                Fx.splat(level, new Vec3(at.x, groundY(at), at.z), 0.6F, 0xB81C14, 120);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.8F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0F, 0.5F);
            PaponNetwork.shake(level, position(), 18, 0.7F, 16);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.5), this::hittable)) {
                Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(away.scale(1.8).add(0, 0.7, 0));
                e.hurtMarked = true;
                strike(e, 6F, ModDamage.SPATULA, false);
                Mechanics.trueHit(e, isPhaseTwo() ? 0.14F : 0.10F, this, true);
                Mechanics.heat(e, 1, this);
            }
            pressure.clear();
        }
        if (t >= BossState.SALSA_BURST.duration) {
            think = 6;
            setBossState(BossState.IDLE);
        }
    }

    private void startDriving() {
        drive = Drive.REV;
        driveTicks = 0;
        legsLeft = isPhaseTwo() ? 5 : 3;
        refunded = false;
        airUsed = false;
        bailUsed = false;
        drifted = false;
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
                // lead the target by the time this van, at the speed it is really doing, needs to get there
                double eta = dist / Math.max(Math.abs(van.speed()), 0.6);
                Vec3 aim = predict(target, (int) Mth.clamp(eta, 0, 25));
                double toGo = aim.subtract(van.position()).dot(f) * (legReverse ? -1 : 1);
                boolean lock = van.position().distanceTo(aim) < (legReverse ? 3.5 : 2.5);
                if (!lock) ramAim = aim;
                van.autopilot(lock && ramAim != null ? ramAim : aim, legReverse, lock);
                driveBy(level, van, target, 25);
                doorCheck(level, van, target);
                // close and off to one side at speed: yank the handbrake and slide the van's flank into them
                Vec3 rel = target.position().subtract(van.position());
                double lat = rel.dot(van.left());
                double along = rel.dot(f);
                if (!drifted && !legReverse && van.speed() > 0.8 && along > 0 && along < 7 && Math.abs(lat) > 2.2) {
                    drifted = true;
                    Vec3 v = van.planarVelocity().lengthSqr() > 0.04 ? van.planarVelocity() : f.scale(van.speed());
                    van.shove(v, (float) (-Math.signum(lat) * 16), 12, 0.9F);
                    level.playSound(null, van.getX(), van.getY(), van.getZ(), com.groid.papon.registry.ModSounds.CARAVAN_SCREECH.get(),
                            SoundSource.HOSTILE, 2.5F, 0.9F);
                    nextDrive(Drive.DRIFT);
                    return;
                }
                // too close and too far round to make the turn: stop circling, brake and come again
                // (the next manoeuvre reverses into them if they are behind)
                Vec3 toAim = aim.subtract(van.position()).multiply(1, 0, 1);
                double off = toAim.lengthSqr() < 1e-4 ? 0 : Math.toDegrees(Math.acos(Mth.clamp(
                        toAim.normalize().dot(legReverse ? f.scale(-1) : f), -1, 1)));
                if (!lock && dist < 8 && off > 75 && Math.abs(van.speed()) > 0.3 && driveTicks > 10) {
                    van.brake();
                    nextDrive(Drive.BRAKE);
                    return;
                }
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
                doorCheck(level, van, target);
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
            case DRIFT -> {
                if (target == null) {
                    exitVan(level, van);
                    return;
                }
                if (!van.isSliding() || driveTicks > 20) {
                    // out of the slide: whichever end faces them goes in
                    legReverse = target.position().subtract(van.position()).dot(van.forward()) < 0;
                    ramAim = target.position();
                    if (legReverse) say("reverse", 30);
                    nextDrive(Drive.LEG);
                }
            }
            case BAIL -> {
                if (target == null) {
                    exitVan(level, van);
                    return;
                }
                double dist = target.distanceTo(van);
                Vec3 aim = predict(target, (int) Mth.clamp(dist / Math.max(Math.abs(van.speed()), 0.6), 0, 25));
                van.autopilot(aim, false, dist < 6);
                if (dist < 9 || driveTicks > 60) bailOut(level, van, target);
            }
        }
    }

    /**
     * The stuntman: floors it at you and jumps out of the driver's door at the last moment. The van
     * rolls on at you on its own, and he lands on you from the other side, straight into the flurry.
     */
    private void bailOut(ServerLevel level, CaravanEntity van, LivingEntity target) {
        Vec3 vel = van.forward().scale(Math.max(0.8, van.speed()));
        van.releaseAutopilot();
        Vec3 door = van.driverDoor();
        stopRiding();
        moveTo(door.x, van.getY() + 0.5, door.z, getYRot(), 0);
        van.shove(vel, 0F, 40, 1.0F);
        van.slamDoor();
        Vec3 to = target.position().subtract(position());
        setDeltaMovement(to.x * 0.11, 0.75, to.z * 0.11);
        hasImpulse = true;
        landed = false;
        vanCd = isPhaseTwo() ? PaponConfig.VAN_COOLDOWN.get() * 2 / 3 : PaponConfig.VAN_COOLDOWN.get();
        setBossState(BossState.BAILOUT);
        say("bail", 40);
    }

    /** Driving past someone on the driver's side: the door flies open into them. */
    private void doorCheck(ServerLevel level, CaravanEntity van, LivingEntity target) {
        if (doorCd > 0 || Math.abs(van.speed()) < 0.35) return;
        Vec3 rel = target.position().subtract(van.position());
        double lat = rel.dot(van.left());
        double along = rel.dot(van.forward());
        if (lat < CaravanEntity.HALF_WIDTH + 0.1 || lat > CaravanEntity.HALF_WIDTH + 2.6 || along < -1.2 || along > 2.4
                || Math.abs(target.getY() - van.getY()) > 2) return;
        doorCd = 30;
        van.slamDoor();
        if (strike(target, 10F, ModDamage.CARAVAN, true)) {
            target.setDeltaMovement(van.left().scale(1.3).add(van.forward().scale(van.speed() * 0.6)).add(0, 0.45, 0));
            target.hurtMarked = true;
            if (target instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.7F, 14);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 1.5F);
        if (random.nextInt(3) == 0) say("door", 30);
    }

    private void nextManeuver(ServerLevel level, CaravanEntity van, @Nullable LivingEntity target) {
        if (legsLeft <= 0 || target == null || target.distanceTo(van) > 48) {
            exitVan(level, van);
            return;
        }
        legsLeft--;
        double dist = target.distanceTo(van);
        float r = random.nextFloat();
        drifted = false;
        if (!bailUsed && legsLeft <= 1 && dist > 10 && r < 0.5F) {
            bailUsed = true;
            say("ram", 30);
            van.honk();
            nextDrive(Drive.BAIL);
            return;
        }
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
            Fx.chunks(level, position().add(0, getBbHeight() * 0.8, 0), 2, 0.12F, Fx.TEQUILA, 0.15);
        }
        if (stateTicks >= BossState.EXIT_STUMBLE.duration) {
            think = 6;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ phase one: more combos

    /**
     * The Taco Tornado: three dashes, each ending in a swing (forehand, backhand, a chop from over
     * his head that breaks shields), re-aimed between them, then two turns on the spot with the
     * spatula held out. The dashes cover whatever gap there is in 4 ticks: dodge sideways.
     */
    private void tickDash(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null) {
            for (int start : new int[]{4, 14, 24}) {
                if (t == start) {
                    Vec3 aim = predict(target, 4);
                    face(aim);
                    double d = Math.sqrt(horizontalDistanceSqr(aim));
                    dashVel = flatTo(aim).scale(Mth.clamp((d - 1.6) / 4.0, 0.2, 1.1));
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.8F, 1.6F);
                }
                if (t >= start && t < start + 4) {
                    setDeltaMovement(dashVel.x, getDeltaMovement().y, dashVel.z);
                    if (t == start) Fx.dust(level, position(), 0.3, 2);
                }
            }
            if (t < 4 || (t > 8 && t < 14) || (t > 18 && t < 24)) turnToward(target.position(), 25F);
        }
        if (t == 8 || t == 18 || t == 28) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            boolean heavy = t == 28;
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 3.8, 60)) {
                if (strike(e, heavy ? 10F : 8F, ModDamage.SPATULA, heavy)) {
                    e.setDeltaMovement(look.scale(heavy ? 0.9 : 0.35).add(0, heavy ? 0.45 : 0.2, 0));
                    e.hurtMarked = true;
                }
            }
            Vec3 sw = position().add(look.scale(2)).add(0, 1.2, 0);
            Fx.slash(level, sw, getYRot() + (t == 18 ? -30 : 30), 2.0F, Fx.WHITE);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        if (t >= 32 && t <= 44 && t % 4 == 0) {
            Fx.slash(level, position().add(0, 1.2, 0), getYRot() + t * 50F, 2.8F, Fx.WHITE);
        }
        if (t == 38) {
            for (LivingEntity e : around(level, 4.6)) {
                if (strike(e, 12F, ModDamage.SPATULA, true)) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(1.4).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
            PaponNetwork.shake(level, position(), 10, 0.3F, 10);
        }
        if (t >= BossState.DASH_COMBO.duration) setBossState(BossState.IDLE);
    }

    private Vec3 dashVel = Vec3.ZERO;

    /** Runs round behind his parked van (the side away from you) to boot it at you. */
    private void tickPuntRun(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || van.isSliding() || target == null || stateTicks > 100) {
            setBossState(BossState.IDLE);
            return;
        }
        Vec3 toT = target.position().subtract(van.position()).multiply(1, 0, 1);
        if (toT.lengthSqr() < 1e-4) toT = van.forward();
        Vec3 dir = toT.normalize();
        Vec3 spot = van.position().subtract(dir.scale(3.4));
        boolean behind = position().subtract(van.position()).dot(dir) < -1.0;
        if (horizontalDist(spot) < 1.4 || (behind && distanceTo(van) < 3.8)) {
            face(van.position());
            setBossState(BossState.VAN_KICK);
            say("kick", 30);
            return;
        }
        if (horizontalDist(spot) < 4) {
            getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.5);
        } else if (getNavigation().isDone() || tickCount % 6 == 0) {
            getNavigation().moveTo(spot.x, spot.y, spot.z, 1.5);
        }
    }

    /** The kick (0.55 s): the van goes skidding and spinning at where you will be. */
    private void tickVanKick(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        CaravanEntity van = van(level);
        if (van != null && t < 10) face(van.position());
        if (t == 11 && van != null && !van.isVehicle()) {
            Vec3 aim = target != null ? predict(target, 12) : van.position().add(getLookAngle().scale(10));
            Vec3 d = aim.subtract(van.position()).multiply(1, 0, 1);
            Vec3 dir = d.lengthSqr() < 1e-4 ? getLookAngle().multiply(1, 0, 1).normalize() : d.normalize();
            float spin = (random.nextBoolean() ? 1 : -1) * (5F + random.nextFloat() * 4F);
            van.setGhost(false);
            van.shove(dir.scale(isPhaseTwo() ? 1.35 : 1.15), spin, 45, 0.8F);
            level.playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.6F);
            level.playSound(null, van.getX(), van.getY(), van.getZ(), com.groid.papon.registry.ModSounds.CARAVAN_CRASH.get(),
                    SoundSource.HOSTILE, 2.0F, 1.1F);
            Vec3 hit = position().add(getLookAngle().scale(1.5)).add(0, 0.8, 0);
            Fx.hit(level, hit, 1.4F, Fx.WHITE);
            Fx.dust(level, van.position(), 1.2, 4);
            PaponNetwork.shake(level, position(), 14, 0.4F, 12);
            kickCd = isPhaseTwo() ? 220 : 300;
        }
        if (t >= BossState.VAN_KICK.duration) setBossState(BossState.IDLE);
    }

    /** Out of the moving van, through the air at you; lands with a thud and goes straight into the flurry. */
    private void tickBailout(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 8) face(target.position());
        if (t > 4 && !landed && onGround()) {
            landed = true;
            landImpact(level, position(), 3.2F, 10F, 0.5F);
        }
        if ((landed && t >= 18) || t >= BossState.BAILOUT.duration) {
            if (target != null) {
                swipeCd = 0;
                startAttack(BossState.SWIPE, target);
            } else {
                setBossState(BossState.IDLE);
            }
        }
    }

    // ------------------------------------------------------------------ the grab

    /**
     * The grab (Ignis' grab, with seasoning): crouches (0.4 s), throws himself at you with his arms
     * out; touch him in that half second and he has you. A miss leaves him stumbling forward.
     */
    private void tickGrab(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target == null) {
            setBossState(BossState.IDLE);
            return;
        }
        if (t < 8) {
            face(target.position());
            return;
        }
        if (t == 8) {
            Vec3 aim = predict(target, 6);
            double d = Math.sqrt(horizontalDistanceSqr(aim));
            dashVel = flatTo(aim).scale(Mth.clamp((d - 1.0) / 5.0, 0.3, 1.3));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.5F, 0.8F);
        }
        if (t < 14) setDeltaMovement(dashVel.x, getDeltaMovement().y, dashVel.z);
        for (LivingEntity e : around(level, getBbWidth() * 0.5 + 1.3)) {
            if (e.isPassenger() || e.isVehicle() || (e instanceof Player p && p.getAbilities().flying)) continue;
            if (e.getBbHeight() > 3) continue;
            grab(level, e);
            return;
        }
        if (t > 22) {
            setBossState(BossState.IDLE);
            think = 14;          // he overshot: a moment to punish him
        }
    }

    private void grab(ServerLevel level, LivingEntity e) {
        hold(e);
        setDeltaMovement(Vec3.ZERO);
        sayPlain("grab", 40);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.5F, 0.6F);
        if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.5F, 10);
        setBossState(BossState.GRAB_COMBO);
    }

    @Nullable
    private LivingEntity held(ServerLevel level) {
        if (grabbed == null) return null;
        Entity e = level.getEntity(grabbed);
        return e instanceof LivingEntity le && le.isAlive() && le.getVehicle() == this ? le : null;
    }

    /** True while {@code e} is in his fist: they cannot climb down (see {@link GrabHold}). */
    public boolean holds(Entity e) {
        return grabbed != null && !releasing && e.getUUID().equals(grabbed);
    }

    /** Takes {@code e} into his right fist: his passenger, held out in front of him (positionRider). */
    private void hold(LivingEntity e) {
        grabbed = e.getUUID();
        releasing = false;
        e.stopRiding();
        e.startRiding(this, true);
        entityData.set(DATA_HELD, e.getId());
    }

    /** Lets go of whoever he is holding (thrown, or he went down). */
    private void release() {
        entityData.set(DATA_HELD, -1);
        if (grabbed == null) return;
        releasing = true;
        if (level() instanceof ServerLevel level) {
            Entity e = level.getEntity(grabbed);
            if (e != null && e.getVehicle() == this) e.stopRiding();
        }
        grabbed = null;
        releasing = false;
    }

    /**
     * Where the held one hangs: out in front of his belly at the end of his right arm, a little to
     * his right (that fist), feet just off the ground; computed on both sides from the synced state
     * and its clock, so the held player sees themselves where everyone else sees them. Scaled with
     * him once he has risen.
     */
    private Vec3 holdPoint() {
        int t = ticksInState();
        BossState s = getBossState();
        float k = isRisen() ? 1.75F / 1.5F : 1F;
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 right = new Vec3(-f.z, 0, f.x);
        double out = 1.45 * k;
        double lift = (0.1 + Math.min(1, t / 10.0) * 0.2) * k;
        double side = 0.45 * k;
        if (s == BossState.GRAB_COMBO && t >= 70 && t < 79) {
            double w = (t - 70) / 9.0;                       // swung up and back for the throw
            lift += w * 1.6;
            out -= w * 0.9;
        } else if (s == BossState.QTE_FAIL) {
            int kind = qteBase();
            if (kind == 2) {                                  // the suplex: up over his head, then down behind him
                double up = Mth.clamp(t / 18.0, 0, 1);
                double over = Mth.clamp((t - 18) / 6.0, 0, 1);
                lift = (0.1 + up * 2.6 - over * 2.6) * k;
                out = (1.45 - up * 1.3 - over * 2.4) * k;
                side = 0;
            } else if (kind == 0 && t >= 24) {
                lift = 0;                                     // chopped down
            }
        }
        return position().add(f.scale(out)).add(right.scale(side)).add(0, lift, 0);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        BossState s = getBossState();
        if ((s == BossState.GRAB_COMBO || s == BossState.QTE_HOLD || s == BossState.QTE_FAIL) && passenger.getId() == heldId()) {
            Vec3 at = holdPoint();
            move.accept(passenger, at.x, at.y, at.z);
            passenger.fallDistance = 0;
            if (passenger instanceof LivingEntity le) {
                // facing him, the way you would be held
                float yaw = yBodyRot + 180F;
                le.setYBodyRot(yaw);
                if (!(passenger instanceof Player)) le.setYRot(yaw);
            }
            return;
        }
        super.positionRider(passenger, move);
    }

    /** Held ones stand in his fist; they do not sit on him. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /**
     * Held up in his fist, no way out: browned with a blowtorch for a second and a half (fire, the
     * hits ignore the usual pause between them), salted (Brittle: armor and toughness down 30% for
     * 20 s), then hurled. Half the time he charges straight after you.
     */
    private void tickGrabCombo(ServerLevel level) {
        int t = stateTicks;
        LivingEntity v = held(level);
        if (v == null && t < 79) {
            setBossState(BossState.IDLE);
            return;
        }
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        Vec3 left = new Vec3(f.z, 0, -f.x);
        if (t == 12) sayPlain("torch", 40);
        if (t >= 16 && t <= 44 && v != null) {
            // the flame: a jet from the nozzle (his left fist, in front) onto whoever he holds
            Vec3 nozzle = position().add(f.scale(getBbWidth() * 0.5 + 0.6)).add(left.scale(0.5)).add(0, 0.95, 0);
            Vec3 to = v.position().add(0, v.getBbHeight() * 0.5, 0).subtract(nozzle).normalize();
            Fx.torch(level, nozzle, to.scale(0.35), 0.3F);
            if (t % 4 == 0) {
                v.invulnerableTime = 0;
                if (v.hurt(source(ModDamage.TEQUILA_FIRE), 2.0F * dmg())) v.setSecondsOnFire(4);
                Fx.smoke(level, v.position().add(0, v.getBbHeight() * 0.6, 0), 1, 0.2, 0.5F, Fx.SMOKE);
                level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 1.6F);
                if (v instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.2F, 4);
            }
        }
        if (t == 49) sayPlain("season", 40);
        if (t >= 52 && t <= 68 && v != null) {
            // the salt: out of the shaker's cap (his left fist, held straight out) in spurts at them
            Vec3 cap = position().add(f.scale(getBbWidth() * 0.5 + 0.7)).add(left.scale(0.45)).add(0, 1.25, 0);
            Vec3 to = v.position().add(0, v.getBbHeight() * 0.6, 0).subtract(cap).normalize();
            Fx.flake(level, cap, to.scale(0.3), 0.12F, Fx.SALT);
            if (t % 2 == 0) Fx.fall(level, v.position().add(0, v.getBbHeight() + 0.2, 0), 2, 0.3, 0.1F, Fx.SALT);
            if (t % 4 == 0) level.playSound(null, cap.x, cap.y, cap.z, SoundEvents.SAND_HIT, SoundSource.HOSTILE, 1.0F, 1.6F);
        }
        if (t == 66 && v != null) {
            v.addEffect(new MobEffectInstance(com.groid.papon.registry.ModEffects.BRITTLE.get(), 400, 0, false, true, true));
            if (v instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("status.papon.brittle").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
            }
            level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        if (t == 74) sayPlain("toss", 30);
        if (t == 79) {
            LivingEntity thrown = v;
            release();
            if (thrown != null) {
                Vec3 at = position().add(f.scale(getBbWidth() * 0.5 + 1.2)).add(0, 1.2, 0);
                thrown.teleportTo(at.x, at.y, at.z);
                strike(thrown, 8F, ModDamage.BELLY_FLOP, true);
                thrown.setDeltaMovement(f.scale(1.8).add(0, 0.7, 0));
                thrown.hurtMarked = true;
                if (thrown instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.9F, 20);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
                if (random.nextBoolean() && !isFlying()) {
                    // and after them, before they have landed
                    chargesLeft = 0;
                    setBossState(BossState.CHARGE_WINDUP);
                    stateTicks = 6;
                    return;
                }
            }
        }
        if (t >= BossState.GRAB_COMBO.duration) setBossState(BossState.IDLE);
    }

    /** Held in his fist, you cannot climb down; only he lets go. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
    public static final class GrabHold {
        private GrabHold() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onDismount(net.minecraftforge.event.entity.EntityMountEvent event) {
            if (event.isDismounting() && event.getEntityBeingMounted() instanceof PaponBossEntity boss
                    && boss.isAlive() && boss.holds(event.getEntityMounting())) {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------ phase two: the cutscene

    /** On one knee, coughing blood (the cutscene's first beat; the music has stopped). */
    private void tickKneel(ServerLevel level) {
        int t = stateTicks;
        if (t == 1) {
            entityData.set(DATA_PHASE2, true);
            bossEvent.setName(Component.translatable(BAR_KEY + ".phase2"));
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(PHASE2_SPEED) == null) {
                speed.addPermanentModifier(new AttributeModifier(PHASE2_SPEED, "Papon phase two", 0.25,
                        AttributeModifier.Operation.MULTIPLY_BASE));
            }
            setFlying(false);
            getNavigation().stop();
            fires.clear();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.0F, 0.6F);
            if (getTarget() != null) face(getTarget().position());   // so his point of view finds you
            // where his coliseum goes; it only goes up behind the white-out at the end of the scream
            if (arena == null && !arenaFailed) planArena(level);
        }
        LivingEntity target = getTarget();
        if (target != null && t > 100) turnToward(target.position(), 3F);
        if (t == 76 || t == 84 || t == 92) {
            // his left hand, held out in front of his eyes: it is bleeding too, drops off the fist
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            Vec3 hand = position().add(f.scale(1.75)).add(-f.z * -0.3, 0.8, f.x * -0.3);
            Fx.fall(level, hand, 1, 0.04, 0.08F, 0x7A0A10);
            Fx.splat(level, new Vec3(hand.x, groundY(hand), hand.z), 0.2F, 0x6A0A10, 100);
        }
        if (t == 24 || t == 40 || t == 56) {
            // out in front of his face (the camera is his eyes from 1.4 s): drops spattering the ground below
            Vec3 drip = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.8)).add(0, 0.7, 0);
            Fx.fall(level, drip, 3, 0.08, 0.1F, 0x7A0A10);
            Fx.splat(level, new Vec3(drip.x, groundY(drip), drip.z), 0.35F, 0x6A0A10, 100);
        }
        if (t >= BossState.P2_KNEEL.duration) setBossState(BossState.P2_TALK);
    }

    /**
     * Still on one knee, he chuckles at the blood, wipes it off: "Heh... heh heh. You made me... BLEED,
     * amigo." Then up he gets on "Okay... OKAY. Enough... playing around." and cracks his neck. Timed
     * to the lines (beats talk.*).
     */
    private void tickTalk(ServerLevel level) {
        int t = stateTicks;
        if (t == beat("talk.bleed", -1)) sayPlain("bleed", 70);
        if (t == beat("talk.enough", 4)) sayPlain("enough", 70);
        if (t >= beat("talk.end", BossState.P2_TALK.duration)) setBossState(BossState.P2_SEASON);
    }

    /**
     * Seasoned by the heavens: a huge salt shaker appears over his head and shakes itself over him
     * (the salt comes down from 1.2 to 3.2 s) inside a flowing aura (drawn by BossRenderer); it
     * bursts, the aura flares and he bakes golden (4.0 s).
     */
    private void tickSeason(ServerLevel level) {
        int t = stateTicks;
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 3F);
        if (t == beat("season.salt", 4)) say("salt", 50);
        if (t == 18) level.playSound(null, getX(), getY() + 4, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.6F);
        Vec3 shaker = position().add(0, getBbHeight() + 1.2, 0);
        if (t >= 24 && t <= 64) {
            Fx.fall(level, shaker, 4, 0.5, 0.13F, Fx.SALT);
            if (t % 6 == 0) level.playSound(null, shaker.x, shaker.y, shaker.z, SoundEvents.SAND_FALL, SoundSource.HOSTILE, 1.4F, 1.4F);
        }
        if (t >= 16 && t <= 96 && t % 6 == 0) {
            // a glint now and then in the aura, curling up round him
            double a = t * 0.35;
            double r = getBbWidth() * 0.8;
            Fx.glint(level, new Vec3(getX() + Math.cos(a) * r, getY() + (t % 30) / 30.0 * getBbHeight(), getZ() + Math.sin(a) * r), 0.4F, 0xFFE8A0);
        }
        if (t == 66) {
            Fx.chunks(level, shaker, 12, 0.25F, 0xD8EEE6, 0.5);
            Fx.smoke(level, shaker, 3, 0.6, 1.2F, 0xF4F1E8);
            Fx.flash(level, shaker, 2F);
            level.playSound(null, shaker.x, shaker.y, shaker.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (t == 80) {
            entityData.set(DATA_FORM, Math.max(1, form()));
            bolt(level, position());                        // struck golden
            Fx.flash(level, position().add(0, 1.5, 0), 3.0F);
            Fx.blast(level, position(), 2.0F);
            Fx.smoke(level, position().add(0, 1.2, 0), 4, 0.8, 1.4F, 0xEDE6DA);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.6F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 2.0F, 0.8F);
            PaponNetwork.shake(level, position(), 24, 0.5F, 20);
        }
        if (t == beat("season.golden", 84)) say("golden", 50);
        if (t >= beat("season.end", BossState.P2_SEASON.duration)) setBossState(BossState.P2_SCREAM);
    }

    /**
     * The scream (0.75 s): the shake, the fry wings, the crown, the butter (form 2); the coliseum
     * (going up in the sky since he knelt) is finished, and at the end everyone in the fight is
     * pulled up into it.
     */
    private void tickScream(ServerLevel level) {
        int t = stateTicks;
        if (t == 1 && arena == null && !arenaFailed) planArena(level);
        if (t == 15) {
            entityData.set(DATA_FORM, 2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 1.4F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.8F);
            PaponNetwork.shake(level, position(), 40, 1.1F, 55);
            knockAway(level, 7, 1.2, 0);
            Fx.blast(level, position().add(0, 0.5, 0), 3.0F);
            Fx.shock(level, position(), 9F, 0xFFE07A);
            Fx.dust(level, position(), 3.0, 8);
        }
        if (t == 15 || t == 21 || t == 28 || t == 36 || t == 45) {
            // the sky answers
            double a = random.nextDouble() * Math.PI * 2;
            double r = 5 + random.nextDouble() * 8;
            bolt(level, position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        if (t > 15 && t < 60 && t % 8 == 0) Fx.shock(level, position(), (float) (2 + (t - 15) / 4.0), 0xFFE07A);
        // the white-out (Cinematic: from 14 ticks before the end): now the coliseum goes up, unseen
        if (arena != null && !arena.started() && t >= beat("scream.end", BossState.P2_SCREAM.duration) - 13) arena.startBuild(level);
        if (t >= beat("scream.end", BossState.P2_SCREAM.duration)) {
            enterArena(level);
            setBossState(BossState.P2_ARRIVE);
        }
    }

    /** A lightning bolt for show: the flash and the thunder, no fire, no damage. */
    private void bolt(ServerLevel level, Vec3 at) {
        net.minecraft.world.entity.LightningBolt b = EntityType.LIGHTNING_BOLT.create(level);
        if (b == null) return;
        b.moveTo(at.x, groundY(at), at.z);
        b.setVisualOnly(true);
        level.addFreshEntity(b);
    }

    // ------------------------------------------------------------------ phase two: El Coliseo Papa

    /** Where the coliseum goes (in the sky over the fight); it goes up in the white-out of the scream (tickScream). */
    private void planArena(ServerLevel level) {
        arena = Coliseum.plan(level, home != null ? home : position());
        if (arena == null) {
            arenaFailed = true;
            PaponMod.LOG.info("[boss] no room in the sky for his coliseum over {}: phase two stays on the ground", blockPosition());
        }
    }

    /** Everyone in the fight, the van and himself, up into the coliseum (if there is one); the crowd and the band in their places. */
    private void enterArena(ServerLevel level) {
        if (arena == null) return;
        while (!arena.buildStep(level, 4000)) {
            // finish it now (it is normally done well before the scream ends)
        }
        Vec3 c = arena.centerVec();
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(this) <= 48 * 48) in.add(p);
        }
        for (int i = 0; i < in.size(); i++) {
            ServerPlayer p = in.get(i);
            p.stopRiding();
            Vec3 to = arena.playerSpot(i, in.size());
            Coliseum.send(level, p, to);
            p.connection.teleport(to.x, to.y, to.z, 180F, -8F);        // facing north: him, and the band stand behind him
        }
        CaravanEntity van = van(level);
        if (van != null && van.distanceToSqr(this) < 64 * 64) {
            van.ejectPassengers();
            Coliseum.send(level, van, arena.vanSpot());
            van.setFloor(arena.floorY());
        }
        Coliseum.send(level, this, c.add(0, 12, 0));
        home = c;
        fires.clear();
        setFlying(true);
        entityData.set(DATA_ARENA, true);
        populate(level);
        PaponMod.LOG.info("[boss] into El Coliseo Papa at {}", arena.center);
    }

    /** The crowd and the band (asleep), unless they are there already; back for a second round, the band just plays. */
    private void populate(ServerLevel level) {
        ColiseumCrowdEntity crowd = crowd(level);
        if (crowd == null) {
            crowd = new ColiseumCrowdEntity(com.groid.papon.registry.ModEntities.COLISEUM_CROWD.get(), level);
            Vec3 c = arena.centerVec();
            crowd.moveTo(c.x, c.y, c.z, 0, 0);
            crowd.setSeed((int) arena.center.asLong());
            level.addFreshEntity(crowd);
        }
        crowd.setMood(CrowdMood.CHEER);
        if (band(level).isEmpty()) {
            for (BandRole role : BandRole.values()) {
                MariachiEntity.spawn(level, role, arena.bandSpot(role.ordinal()), arena.bandYaw(role.ordinal()));
            }
        }
        if (bandWoken) {
            songStart = (int) ((level.getGameTime() + 10) & 0x7FFFFFFF);
            for (MariachiEntity m : band(level)) m.play(songStart);
        }
    }

    /** The coliseum comes down: everyone and everything in it back to the ground, him included. */
    private void leaveArena(ServerLevel level) {
        if (arena == null) return;
        Coliseum d = arena;
        arena = null;
        entityData.set(DATA_ARENA, false);
        CaravanEntity van = van(level);
        if (van != null) van.setFloor(null);
        d.retire(level);
        closePortal(level);
        if (freidora != null) freidora.close(level);
        freidora = null;
        heckleTicks = 0;
        d.teardown(level);
        d.sendEveryoneHome(level);
        home = d.ground;
        fires.clear();
    }

    @Nullable
    private ColiseumCrowdEntity crowd(ServerLevel level) {
        if (arena == null) return null;
        List<ColiseumCrowdEntity> l = level.getEntitiesOfClass(ColiseumCrowdEntity.class, arena.bounds(), Entity::isAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    /** The band, in stage order (singer first). */
    private List<MariachiEntity> band(ServerLevel level) {
        if (arena == null) return List.of();
        List<MariachiEntity> l = new ArrayList<>(level.getEntitiesOfClass(MariachiEntity.class, arena.bounds(), Entity::isAlive));
        l.sort(java.util.Comparator.comparingInt(m -> m.getRole().ordinal()));
        return l;
    }

    @Nullable
    private MariachiEntity singer(ServerLevel level) {
        for (MariachiEntity m : band(level)) if (m.getRole().singer()) return m;
        return null;
    }

    private void crowdMood(ServerLevel level, CrowdMood m) {
        ColiseumCrowdEntity c = crowd(level);
        if (c != null) c.setMood(m);
    }

    private void crowdReact(ServerLevel level, CrowdMood m, int ticks) {
        ColiseumCrowdEntity c = crowd(level);
        if (c != null) c.react(m, ticks);
    }

    /**
     * Every tick: build what is left of the coliseum, keep the van on its floor, keep challengers
     * out of the stands, get the crowd going now and then, and bring it all down if nobody is left
     * in it.
     */
    private void tickArena(ServerLevel level) {
        if (oldDome != null) {
            Coliseum.tearDownOldDome(level, oldDome);
            oldDome = null;
        }
        if (arena == null) return;
        arena.keepAlive(level);                         // spawns refused around it from the first block
        if (!floorChecked && arena.built() && freidora == null) {
            floorChecked = true;
            arena.restoreFloor(level);
        }
        if (!arena.built()) {
            if (arena.started()) arena.buildStep(level);         // not before the white-out: its shadow would fall on the cutscene
            return;
        }
        CaravanEntity van = van(level);
        if (van != null) van.setFloor(arena.contains(van.position()) ? arena.floorY() : null);
        BossState s = getBossState();
        if (inColiseum()) {
            containArena(level, van, s);
            if (s != BossState.DEATH) keepPortal(level);
            if (freidora != null && !freidora.tick(level)) freidora = null;
            if (heckleTicks > 0) tickHeckleBarrage(level);
        }
        boolean anyone = false;
        AABB pull = arena.bounds().inflate(48, 64, 48);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || !pull.contains(p.position())) continue;
            anyone = true;
            if (!inColiseum() || p.isCreative() || arena.inArena(p.position()) || holds(p)) continue;
            // in the stands, over the wall, fallen off or flown out over the top (pearls, elytra, pillars): back on the sand
            Vec3 to = arena.clamp(p.position(), 3, 0, 0);
            Coliseum.send(level, p, new Vec3(to.x, arena.floorY(), to.z));
        }
        if (s.cutscene() || s == BossState.DEATH) return;
        ColiseumCrowdEntity crowd = crowd(level);
        if (crowd != null) crowd.pelt(level, arena);
        if (tickCount % 20 == 0 && --crowdCd <= 0) {
            // the stands get going on their own: a chant, the wave, clapping along
            crowdCd = 18 + random.nextInt(14);
            CrowdMood m = switch (random.nextInt(3)) {
                case 0 -> CrowdMood.CHANT;
                case 1 -> CrowdMood.WAVE;
                default -> CrowdMood.CLAP;
            };
            ColiseumCrowdEntity c = crowd(level);
            if (c != null && !c.reacting()) c.react(m, 180);
        }
        arenaLonely = anyone ? 0 : arenaLonely + 1;
        if (arenaLonely > 200) {
            PaponMod.LOG.info("[boss] nobody left in his coliseum: it comes down");
            setFlying(false);
            leaveArena(level);
            arenaLonely = 0;
            arenaRetryCd = 600;
        }
    }

    /**
     * Nothing gets in or out: he and his van are kept on the sand (the van could be driven or launched over
     * the podium and drop out of the sky), and any mob that does not belong (Coliseum.belongs: spawns,
     * flyers) is gone in a puff of smoke. Spawns are refused outright by Coliseum.Gate.
     */
    private void containArena(ServerLevel level, @Nullable CaravanEntity van, BossState s) {
        boolean scripted = s == BossState.P2_ARRIVE && !bandWoken;          // he is lowered in by hand then
        if (!scripted && !isPassenger() && outOfArena(position())) {
            Vec3 to = arena.clamp(position(), 3, 0, Coliseum.HEIGHT - 6);
            setPos(to.x, Math.max(to.y, arena.floorY()), to.z);
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
        }
        if (van != null && outOfArena(van.position())) {
            Vec3 to = arena.clamp(van.position(), 5, 0, 0);
            van.teleportTo(to.x, arena.floorY() + 0.1, to.z);
            van.setDeltaMovement(Vec3.ZERO);
            van.fallDistance = 0;
        }
        if (tickCount % 10 == 0) {
            for (Entity e : level.getEntities((Entity) null, arena.bounds().inflate(32), e -> !Coliseum.belongs(e))) {
                Fx.smoke(level, e.position().add(0, e.getBbHeight() / 2, 0), 2, 0.3, 0.8F, 0xEDEDED);
                e.discard();
            }
        }
    }

    /** The portal in the truck window stays open while the coliseum does, and sends you to the middle of the sand. */
    private void keepPortal(ServerLevel level) {
        if (portalAt == null) return;
        ArenaPortalEntity p = portalId != null && level.getEntity(portalId) instanceof ArenaPortalEntity e && e.isAlive() ? e : null;
        if (p == null) {
            p = new ArenaPortalEntity(com.groid.papon.registry.ModEntities.ARENA_PORTAL.get(), level);
            p.moveTo(portalAt.x, portalAt.y, portalAt.z, portalYaw, 0);
            level.addFreshEntity(p);
            portalId = p.getUUID();
            level.playSound(null, portalAt.x, portalAt.y + 1, portalAt.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        Vec3 c = arena.centerVec();
        p.hold(new Vec3(c.x, arena.floorY(), c.z));
    }

    private void closePortal(ServerLevel level) {
        if (portalId != null && level.getEntity(portalId) instanceof ArenaPortalEntity e) e.discard();
        portalId = null;
    }

    /** Off the sand: past the podium wall, below the floor, or up over the top of the barrier. */
    private boolean outOfArena(Vec3 p) {
        return !arena.inArena(p) || p.y < arena.floorY() - 1.5;
    }

    /** The stand's front, where he stands to have words with the band. */
    private Vec3 standFront() {
        return arena.centerVec().add(0, 0, -Coliseum.B + 9.6);
    }

    /**
     * In his coliseum (5 s): the white clears on him coming down out of the sky into the middle of
     * the sand to the roar of the stands; he lands, throws his arms up to them ("¿ESTÁN LISTOS?"),
     * the title; and, ready to go on, he points at the band stand: "¡MÚSICA, MAESTRO!"... and
     * nothing. Silence. With no coliseum (no room in the sky) he just hangs there and the fight
     * goes on.
     */
    private void tickArrive(ServerLevel level) {
        int t = stateTicks;
        LivingEntity target = getTarget();
        if (arena == null) {
            setFlying(true);
            setDeltaMovement(0, t < 30 ? -0.03 : 0, 0);
            if (target != null) turnToward(target.position(), 6F);
            if (t == 2) title(level, "title.papon.dome", 50);
            if (t >= 50) finishCutscene(level);
            return;
        }
        Vec3 c = arena.centerVec();
        if (t <= 36) {
            double k = t / 36.0;
            k = k * k * (3 - 2 * k);
            setFlying(true);
            setPos(c.x, c.y + 12 * (1 - k), c.z);
            setDeltaMovement(Vec3.ZERO);
            face(c.add(0, 0, 8));
        }
        if (t == 36) {
            setFlying(false);
            setPos(c.x, c.y, c.z);
            landImpact(level, position(), 4.0F, 0F, 0.6F);
            Fx.dust(level, position(), 3.0, 10);
        }
        if (t == 2) crowdReact(level, CrowdMood.ROAR, 90);
        if (t == 40) title(level, "title.papon.coliseum", 34);
        int musica = beat("arrive.musica", 74);
        if (t == beat("arrive.listos", 46)) {
            say("listos", 40);
            crowdReact(level, CrowdMood.ROAR, 40);
        }
        if (t > 36 && t < musica - 4) turnToward(c.add(Math.sin(t * 0.09) * 20, 0, Math.cos(t * 0.09) * 20), 8F);
        if (bandWoken) {
            if (t >= 60) finishCutscene(level);
            return;
        }
        if (t >= musica - 4) turnToward(standFront().add(0, 0, -6), 12F);
        if (t == musica) {
            say("musica", 40);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 2.0F, 1.6F);
        }
        if (t == beat("arrive.hush", 86)) crowdMood(level, CrowdMood.HUSH);
        if (t >= beat("arrive.end", BossState.P2_ARRIVE.duration)) setBossState(BossState.P2_NOTICE);
    }

    private void title(ServerLevel level, String key, int stay) {
        Component title = Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component sub = Component.translatable(key + ".subtitle").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 72 * 72) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, stay, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
                p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    /** "...¿Maestro?": the band is asleep. He holds the point, looks round at them, back at you, and fumes. */
    private void tickNotice(ServerLevel level) {
        int t = stateTicks;
        int fume = beat("notice.fume", 48);
        if (t >= 18 && t < fume - 18) turnToward(standFront().add(0, 0, -6), 10F);
        if (t >= fume - 18 && t < fume - 6 && getTarget() != null) turnToward(getTarget().position(), 14F);
        if (t == beat("notice.maestro", 34)) say("maestro", 30);
        if (t == fume) {
            Fx.smoke(level, position().add(0, getBbHeight() + 0.3, 0), 3, 0.3, 0.8F, 0xEDEDED);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        if (t >= beat("notice.end", BossState.P2_NOTICE.duration)) setBossState(BossState.P2_MARCH);
    }

    /** Where he stands to whack the singer: on the sand just off the stage, in front of him. */
    private Vec3 wakeSpot() {
        MariachiEntity singer = singer((ServerLevel) level());
        if (singer == null || arena == null) return standFront();
        Vec3 out = Vec3.directionFromRotation(0, singer.getYRot());
        Vec3 at = singer.position().add(out.scale(2.6));
        return new Vec3(at.x, arena.floorY(), at.z);
    }

    /**
     * Strides straight over to the singer, sleeping in the front row: one steady walk (steered at
     * the spot every tick, no path stops), slowing for the last step so he arrives already winding
     * up, and goes straight into the whack.
     */
    private void tickMarch(ServerLevel level) {
        Vec3 to = wakeSpot();
        double d = horizontalDist(to);
        if (d < 0.5 || stateTicks > 200) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
            setBossState(BossState.P2_WAKE);
            return;
        }
        getNavigation().stop();
        getMoveControl().setWantedPosition(to.x, to.y, to.z, d > 2.5 ? 1.15 : 0.6 + d * 0.2);
        MariachiEntity singer = singer(level);
        if (d < 4 && singer != null) turnToward(singer.position(), 12F);
        if (stateTicks % 7 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    /**
     * The whack (3.5 s): the big spatula up behind his head as he plants his feet (0-0.6 s), and down
     * on the sleeping singer's head (0.7 s): BONK. The singer goes up in the air out of his chair;
     * the bang jolts the rest of the band awake; a beat of shocked silence from the stands, then
     * they fall about laughing. "¡¡DESPIERTEN, FLOJOS!!" at the band (1.2 s), and he rests the
     * spatula on his shoulder, satisfied.
     */
    private void tickWake(ServerLevel level) {
        int t = stateTicks;
        MariachiEntity singer = singer(level);
        getNavigation().stop();
        if (singer != null) face(singer.position());
        if (t == 14) {
            Vec3 at = singer != null ? singer.position().add(0, singer.getBbHeight() * 0.95, 0)
                    : position().add(Vec3.directionFromRotation(0, getYRot()).scale(2.4)).add(0, 2.6, 0);
            if (singer != null) singer.whack();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 1.7F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 2.5F, 0.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 2.0F, 0.6F);
            Fx.spark(level, at, 14, 0.5, 0.35F, 0xFFD27A);
            Fx.hit(level, at, 1.4F, Fx.WHITE);
            PaponNetwork.shake(level, at, 20, 0.35F, 8);
            crowdReact(level, CrowdMood.GASP, 18);
        }
        if (t == 17) {
            for (MariachiEntity m : band(level)) {
                if (!m.getRole().singer()) m.wake(m.getRole().dozer() ? 0 : random.nextInt(30));
            }
        }
        if (t == 30) crowdReact(level, CrowdMood.ROAR, 30);
        if (t == Math.max(26, beat("wake.line", 42))) sayPlain("despierten", 50);
        if (t >= Math.max(70, beat("wake.end", BossState.P2_WAKE.duration))) {
            paceSide = random.nextBoolean() ? 1 : -1;
            setBossState(BossState.P2_WAIT);
        }
    }

    /**
     * Waits on them: he paces the front of the stand from end to end, stopping at each end to tap
     * his foot at them (and with a finger up to you: "Un momentito..."); the guitarron has gone
     * straight back to sleep, so he goes and kicks his chair. When they are all ready the singer
     * counts them in.
     */
    private void tickWait(ServerLevel level) {
        int t = stateTicks;
        LivingEntity target = getTarget();
        List<MariachiEntity> band = band(level);
        if (t == beat("wait.momentito", 22)) say("momentito", 40);
        for (MariachiEntity m : band) {
            if (m.dozing() && kickWho < 0 && t > beat("wait.ay", 40)) {
                kickWho = m.getId();
                say("ay", 20);
            }
        }
        if (kickWho >= 0) {
            Entity e = level.getEntity(kickWho);
            if (!(e instanceof MariachiEntity m) || !m.dozing()) {
                kickWho = -1;
            } else {
                Vec3 to = m.position().add(Vec3.directionFromRotation(0, m.getYRot()).scale(2.2));
                to = new Vec3(to.x, arena.floorY(), to.z);
                if (horizontalDist(to) < 1.0 || t > 400) {
                    getNavigation().stop();
                    face(m.position());
                    setBossState(BossState.P2_KICK);
                    return;
                }
                getNavigation().stop();
                getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
                return;
            }
        }
        boolean ready = !band.isEmpty();
        for (MariachiEntity m : band) ready &= m.getBandState() == BandState.READY;
        if (t > 900 && !ready) {
            // never wait forever: whoever is still dawdling is ready now
            for (MariachiEntity m : band) if (!m.getBandState().ready()) m.setBandState(BandState.READY);
            ready = true;
        }
        Vec3 front = standFront();
        if (ready) {
            // off to the side of the stand for the count, so nobody's view of the band is him
            Vec3 aside = front.add(paceSide * 4.5, 0, -1.5);
            if (horizontalDist(aside) < 1.2) {
                setBossState(BossState.P2_COUNT);
                return;
            }
            getNavigation().stop();
            getMoveControl().setWantedPosition(aside.x, aside.y, aside.z, 0.9);
            return;
        }
        // pacing: to one end, a foot-tapping stop, back to the other
        Vec3 end = front.add(paceSide * 3.2, 0, 0);
        if (horizontalDist(end) < 0.6) {
            getNavigation().stop();
            if (t >= 16 && t < 40 && target != null) turnToward(target.position(), 12F);
            else turnToward(standFront().add(0, 0, -6), 8F);
            if (t % 50 == 49) paceSide = -paceSide;
        } else {
            getNavigation().stop();
            getMoveControl().setWantedPosition(end.x, end.y, end.z, 0.55);
        }
    }

    /** The chair: a boot to it and the guitarron is up, for good this time. */
    private void tickKick(ServerLevel level) {
        int t = stateTicks;
        Entity e = level.getEntity(kickWho);
        if (e != null) face(e.position());
        if (t == beat("kick.gordo", 2)) sayPlain("gordo", 30);
        if (t == beat("kick.boot", 12) && e instanceof MariachiEntity m) {
            m.kick();
            level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.5F, 1.2F);
            Fx.dust(level, m.position(), 1.0, 4);
            crowdReact(level, CrowdMood.ROAR, 25);
        }
        if (t >= beat("kick.end", BossState.P2_KICK.duration)) {
            kickWho = -1;
            setBossState(BossState.P2_WAIT);
            stateTicks = beat("wait.ay", 40) + 1;
        }
    }

    /**
     * The singer counts them in (4 s): "¡Uno...! ¡...dos! ¡Uno, dos, tres!", the stands clapping
     * along; on the downbeat the band hits it, fireworks go up off the walls and the place erupts.
     */
    private void tickCount(ServerLevel level) {
        int t = stateTicks;
        MariachiEntity singer = singer(level);
        if (t == 1) {
            songStart = (int) ((level.getGameTime() + BossState.P2_COUNT.duration - 1) & 0x7FFFFFFF);
            for (MariachiEntity m : band(level)) m.count(songStart);
        }
        if (singer != null) turnToward(singer.position(), 6F);
        if (t == 40) crowdMood(level, CrowdMood.CLAP);
        if (t >= BossState.P2_COUNT.duration) {
            for (MariachiEntity m : band(level)) m.play(songStart);
            bandWoken = true;
            crowdMood(level, CrowdMood.CHEER);
            crowdReact(level, CrowdMood.ROAR, 70);
            fireworks(level);
            setBossState(BossState.P2_SHOWTIME);
        }
    }

    /** Rockets off the top of the walls, in the flag's colours and gold, bursting high over the sand. */
    private void fireworks(ServerLevel level) {
        if (arena == null) return;
        int[][] colours = {{0x1E8A3E, 0xFFFFFF}, {0xC4282C, 0xFFFFFF}, {0xF2C14E, 0xFFE9A0}};
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + random.nextDouble() * 0.2;
            Vec3 at = arena.centerVec().add(Math.cos(a) * (Coliseum.A + 20), Coliseum.HEIGHT + 1, Math.sin(a) * (Coliseum.B + 20));
            net.minecraft.world.item.ItemStack rocket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
            CompoundTag fw = rocket.getOrCreateTagElement("Fireworks");
            fw.putByte("Flight", (byte) 2);
            net.minecraft.nbt.ListTag ex = new net.minecraft.nbt.ListTag();
            CompoundTag e = new CompoundTag();
            int[] col = colours[i % colours.length];
            e.putByte("Type", (byte) (i % 2 == 0 ? 1 : 4));
            e.putIntArray("Colors", new int[]{col[0]});
            e.putIntArray("FadeColors", new int[]{col[1]});
            e.putBoolean("Trail", true);
            e.putBoolean("Flicker", i % 3 == 0);
            ex.add(e);
            fw.put("Explosions", ex);
            level.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, at.x, at.y, at.z, rocket));
        }
    }

    /** The guitar intro (the camera on the singer's hands): he grooves along, nodding, facing the band. */
    private void tickShowtime(ServerLevel level) {
        MariachiEntity singer = singer(level);
        if (singer != null) turnToward(singer.position(), 6F);
        if (stateTicks >= BossState.P2_SHOWTIME.duration) setBossState(BossState.P2_APOLOGY);
    }

    /**
     * He turns to you and comes over: "My apologies, amigo. Such poor hospitality... making my guest
     * wait like that." Then he just stands there being magnificent (from 5.5 s): half turned away, the
     * wings opening slowly with the music, a hand over his bald head, a look over his shoulder; the
     * stands swoon.
     */
    private void tickApology(ServerLevel level) {
        int t = stateTicks;
        int aura = beat("apology.aura", 110);
        LivingEntity target = getTarget();
        if (target != null) {
            double d = horizontalDist(target.position());
            if (t < 50 && d > 5.5) {
                if (t % 10 == 1) getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.9);
            } else {
                getNavigation().stop();
            }
            if (t < aura) turnToward(target.position(), 8F);
            else turnToward(target.position().add(Vec3.directionFromRotation(0, getYRot() + 90).scale(6)), 2.5F);
        }
        if (t == beat("apology.line", 30)) sayPlain("apology", 100);
        if (t == aura) {
            crowdReact(level, CrowdMood.SWOON, 90);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (t > aura && t % 6 == 0) {
            double a = t * 0.4;
            Fx.glint(level, position().add(Math.cos(a) * 1.4, 0.4 + (t % 30) / 30.0 * getBbHeight(), Math.sin(a) * 1.4), 0.5F, 0xFFE8A0);
        }
        // the aura farm lasts until "Now... where... were we?" would end right on the singer's first word
        if (t >= beat("apology.end", BossState.P2_APOLOGY.duration) || (t > aura && songSince(level) >= songFight() - beat("resume.total", 0)))
            setBossState(BossState.P2_RESUME);
    }

    /** Ticks since the band's downbeat. */
    private long songSince(ServerLevel level) {
        long now = level.getGameTime() & 0x7FFFFFFF;
        return (now - (songStart & 0x7FFFFFFFL)) & 0x7FFFFFFFL;
    }

    /** Ticks from the downbeat to the first sung word: the fight. */
    private static int songFight() {
        Song song = Song.get();
        return song != null ? song.fightStartTicks() : 300;
    }

    /** Server ticks until the singer's first word (the song's intro passes), from the downbeat. */
    private int ticksToSinging(ServerLevel level) {
        Song song = Song.get();
        long now = level.getGameTime() & 0x7FFFFFFF;
        long start = songStart & 0x7FFFFFFFL;
        long since = (now - start) & 0x7FFFFFFFL;
        if (since >= (1L << 30)) since = 0;
        int fight = song != null ? song.fightStartTicks() : 300;
        return (int) (fight - since);
    }

    /** "...Now. Where were we?": he turns back round to you and cracks his knuckles; the hype takes it from there. */
    private void tickResume(ServerLevel level) {
        int t = stateTicks;
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 10F);
        if (t == beat("resume.line", 2)) sayPlain("resume", 50);
        if (ticksToSinging(level) <= BossState.P2_HYPE_LENGTH || t > 300) {
            hypeSide = arena != null && getX() > arena.centerVec().x ? 1 : -1;
            setBossState(BossState.P2_HYPE);
        }
    }

    /** Where in the stands he plays to: the long side he is nearer to, up in the rows. */
    private Vec3 stands(int side) {
        Vec3 c = arena != null ? arena.centerVec() : position();
        return c.add(side * (Coliseum.A + 8), 6, 0);
    }

    /**
     * Before the singing (8 s, ending exactly on the first sung word): he turns to the stands on his
     * side, a hand cupped to his ear: "Listen to them... They LOVE me." (0.5 s) and they roar; not loud
     * enough, he shakes his head and turns to the other side, the other ear, and they roar louder;
     * then raise-the-roof on the beat with the whole bowl chanting his name (4.0-6.0), gold sparks off
     * him with every pump, "And the crowd... goes WILD!!!"; round to you, finger out (6.4); fists
     * together, down into his stance, "OLÉ!!!" as the place erupts (7.5: fireworks, the ground
     * shaking), and the singer comes in: the fight is on. His lines are his recorded voice (boss2).
     */
    private void tickHype(ServerLevel level) {
        int left = ticksToSinging(level);
        int t = BossState.P2_HYPE_LENGTH - Math.max(0, left);
        LivingEntity target = getTarget();
        getNavigation().stop();
        if (isFlying()) setDeltaMovement(getDeltaMovement().scale(0.5));
        if (t < 44) turnToward(stands(hypeSide), 9F);
        else if (t < 120) turnToward(stands(-hypeSide), 7F);
        else if (target != null) turnToward(target.position(), 12F);
        // his own voice (recorded lines): "Listen to them... They LOVE me." with a hand to his ear
        if (t == 10) say("charm1", 60);
        if (t == 32) {
            crowdReact(level, CrowdMood.ROAR, 26);
            PaponNetwork.shake(level, position(), 48, 0.2F, 14);
        }
        if (t == 60) {
            crowdReact(level, CrowdMood.ROAR, 12);
            PaponNetwork.shake(level, position(), 48, 0.25F, 12);
        }
        if (t == 74) {
            crowdReact(level, CrowdMood.ROAR, 10);
            PaponNetwork.shake(level, position(), 48, 0.3F, 16);
        }
        if (t == 78) say("landed1", 70);           // "And the crowd... goes WILD!!!" over the raise-the-roof
        if (t == 80) crowdReact(level, CrowdMood.CHANT, 50);
        if (t >= 80 && t < 120 && (t - 80) % 10 == 0) {
            // raise the roof, on the beat
            Fx.spark(level, position().add(0, getBbHeight() + 0.4, 0), 10, 0.5, 0.3F, 0xFFE07A);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 1.4F, 0.7F);
            PaponNetwork.shake(level, position(), 48, 0.15F, 6);
        }
        if (t == 128) crowdReact(level, CrowdMood.GASP, 10);      // the finger, at you
        if (t == 138) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.4F, 0.6F);
        if (t == 148) say("charm5", 44);           // "OLÉ!!!"
        if (t == 150) {
            crowdReact(level, CrowdMood.ROAR, 70);
            fireworks(level);
            PaponNetwork.shake(level, position(), 48, 0.7F, 24);
            Fx.shock(level, position(), 7F, 0xFFE07A);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.9F);
        }
        if (left <= 0 || stateTicks > BossState.P2_HYPE_LENGTH + 120) {
            crowdMood(level, CrowdMood.CHEER);
            finishCutscene(level);
        }
    }

    /** A client heard the band go into a guitar solo: if the fight can spare it, he stops for a moment to listen. */
    public void soloHeard() {
        BossState s = getBossState();
        if (!inColiseum() || !bandWoken || soloCd > 0 || s.cutscene() || s == BossState.DEATH || s.qte()) return;
        soloDue = 60;
    }

    /**
     * The singer's guitar solo (3 s): the camera cuts to him; the boss stops where he is, turns to
     * the stand, and air-guitars along with his eyes shut; the stands swoon. Then straight back to it.
     */
    private void tickSolo(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        if (isFlying()) setDeltaMovement(getDeltaMovement().scale(0.4));
        else setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
        MariachiEntity singer = singer(level);
        if (singer != null) turnToward(singer.position(), 10F);
        if (t == 4) {
            say("charm5", 40);                     // "OLÉ!!!"
            crowdReact(level, CrowdMood.SWOON, 50);
        }
        if (t >= BossState.P2_SOLO.duration) {
            soloCd = 900;
            think = 4;
            crowdReact(level, CrowdMood.ROAR, 20);
            setBossState(BossState.IDLE);
        }
    }

    private void finishCutscene(ServerLevel level) {
        think = 6;
        vanCd = Math.max(vanCd, 240);
        crowdCd = 12;
        if (foils.isEmpty()) spawnFoils(level);
        setBossState(BossState.IDLE);
    }

    // ------------------------------------------------------------------ phase two: flight

    /** Flying: circles you a few blocks up, bobbing, always facing you, kept over the sand. */
    private void hover(@Nullable LivingEntity target) {
        setFlying(true);
        getNavigation().stop();
        Vec3 c = target != null ? target.position() : (home != null ? home : position());
        orbitAngle += 0.03F * orbitDir;
        double r = target == null ? 0 : 6.5;
        double h = 3.4 + Math.sin(tickCount * 0.07) * 0.8;
        Vec3 pt = clampToArena(c.add(Math.cos(orbitAngle) * r, h, Math.sin(orbitAngle) * r));
        Vec3 d = pt.subtract(position());
        double len = d.length();
        Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(0.5, len * 0.14) / len);
        setDeltaMovement(getDeltaMovement().scale(0.55).add(v.scale(0.45)));
        if (target != null) turnToward(target.position(), 15F);
        if (horizontalCollision && tickCount % 40 == 0) orbitDir = -orbitDir;
    }

    private Vec3 clampToArena(Vec3 p) {
        if (arena == null || !arena.built()) return p;
        return arena.clamp(p, 4, 1.5, 14);
    }

    // ------------------------------------------------------------------ phase two: attacks

    /** Lunges in and pounds away (a hit every 3 ticks, past i-frames), then a double-fisted slam. */
    private void tickBarrage(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 4) face(target.position());
        if (t == 2 && target != null) lunge(target, 0.4, 0.5, 6.0);
        if (t >= 4 && t <= 31 && t % 3 == 1) {
            if (target != null) turnToward(target.position(), 20F);
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 4.4, 70)) {
                e.invulnerableTime = 0;
                if (strike(e, 2.5F, ModDamage.SPATULA, false)) {
                    e.setDeltaMovement(look.scale(0.12).add(0, 0.08, 0));
                    e.hurtMarked = true;
                }
            }
            Vec3 at = position().add(look.scale(2.0)).add(0, 1.3, 0);
            Fx.hit(level, at, 0.7F, Fx.WHITE);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.8F + random.nextFloat() * 0.5F);
        }
        if (t == 37) {
            for (LivingEntity e : around(level, 4.8)) {
                if (strike(e, 14F, ModDamage.BELLY_FLOP, true)) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(1.3).add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
            }
            ring(level, position(), 3.0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
            PaponNetwork.shake(level, position(), 18, 0.6F, 16);
        }
        if (t >= BossState.P2_BARRAGE.duration) setBossState(BossState.IDLE);
    }

    /** Up high, arms raised: burning potatoes rain on where you are and where you are going. */
    private void tickMeteor(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        setFlying(true);
        double want = (arena != null ? arena.floorY() : (target != null ? target.getY() : getY())) + 10;
        if (arena != null) want = Math.min(want, arena.floorY() + 14);
        setDeltaMovement(0, Mth.clamp((want - getY()) * 0.2, -0.3, 0.5), 0);
        if (target != null) turnToward(target.position(), 6F);
        if (t == 8) say("meteor", 50);
        if (t >= 14 && t <= 58 && t % 3 == 0) {
            LivingEntity victim = target;
            List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32), this::hittable);
            if (!near.isEmpty() && random.nextInt(3) == 0) victim = near.get(random.nextInt(near.size()));
            if (victim != null) {
                Vec3 base = random.nextBoolean() ? predict(victim, 16) : victim.position();
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * 4.5;
                Vec3 at = base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                Vec3 land = new Vec3(at.x, groundY(at), at.z);
                double sa = random.nextDouble() * Math.PI * 2;
                Vec3 from = land.add(Math.cos(sa) * 4, 20, Math.sin(sa) * 4);
                level.addFreshEntity(BossProp.create(level, BossProp.Kind.METEOR, this, from).meteor(land, 18));
                com.groid.papon.fx.Fx.warn(level, land, 2.6F, 0xFF7A2A, 18);
            }
        }
        if (t >= BossState.P2_METEOR.duration) setBossState(BossState.IDLE);
    }

    /** Flies down to his van to pick it up. */
    private void tickVanGrab(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || van.isSliding() || stateTicks > 90) {
            vanThrowCd = 100;
            setBossState(BossState.IDLE);
            return;
        }
        setFlying(true);
        Vec3 side = van.position().add(van.left().scale(CaravanEntity.HALF_WIDTH + 1.4)).add(0, 0.2, 0);
        Vec3 d = side.subtract(position());
        double len = d.length();
        setDeltaMovement(len < 0.1 ? Vec3.ZERO : d.scale(Math.min(0.8, len * 0.25) / len));
        face(van.position());
        if (len < 2.0) {
            liftFrom = van.position();
            van.ejectPassengers();
            van.setCarried(true);
            say("catch", 40);
            setBossState(BossState.VAN_THROW);
        }
    }

    /** The van over his head (0.2-1.2 s), a heave, and he throws it at you (1.8 s): it comes down like the air slam. */
    private void tickVanThrow(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        CaravanEntity van = van(level);
        if (van == null) {
            setBossState(BossState.IDLE);
            return;
        }
        setFlying(true);
        setDeltaMovement(0, t < 24 ? 0.05 : 0, 0);
        if (target != null && t < 34) turnToward(target.position(), 8F);
        if (t < 36) {
            double k = Mth.clamp(t / 22.0, 0, 1);
            k = k * k * (3 - 2 * k);
            Vec3 over = position().add(0, getBbHeight() + 0.3, 0);
            Vec3 from = liftFrom != null ? liftFrom : van.position();
            Vec3 at = from.lerp(over, k);
            if (t >= 22) at = at.add(0, Math.sin(t * 1.3) * 0.08, 0);
            van.setPos(at.x, at.y, at.z);
            van.setYRot(getYRot() + 90F);
            van.setDeltaMovement(Vec3.ZERO);
            if (t == 8) level.playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        if (t == 36) {
            Vec3 aim = target != null ? predict(target, 20) : position().add(getLookAngle().scale(12));
            van.setCarried(false);
            van.setGhost(true);
            van.launch(aim, 18);
            vanThrown = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5F, 0.4F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 0.6F);
            PaponNetwork.shake(level, position(), 16, 0.4F, 12);
            vanThrowCd = 420;
        }
        if (t >= BossState.VAN_THROW.duration) setBossState(BossState.IDLE);
    }

    /** A thrown van in the air: when it lands, the crater. */
    private void tickThrownVan(ServerLevel level) {
        if (!vanThrown) return;
        CaravanEntity van = van(level);
        if (van == null) {
            vanThrown = false;
            return;
        }
        if (van.consumeLanding()) {
            vanImpact(level, van);
            vanThrown = false;
            van.setGhost(false);
            van.settle();
        } else if (!van.isFlying()) {
            vanThrown = false;
            van.setGhost(false);
            van.settle();
        }
    }

    /**
     * Dive bombs: up over you with a ring of fire marking the spot, then straight down at where you
     * will be (the impact a shockwave), back up, again: three times (the first warning the longest).
     */
    private void tickDive(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t == 1) {
            divesLeft = pendingDives > 0 ? pendingDives : 3;
            pendingDives = 0;
            diveStage = 0;
            diveTicks = 0;
        }
        if (target == null) {
            setBossState(BossState.IDLE);
            return;
        }
        diveTicks++;
        switch (diveStage) {
            case 0 -> {
                setFlying(true);
                Vec3 pt = clampToArena(target.position().add(0, 7.5, 0));
                Vec3 d = pt.subtract(position());
                double len = d.length();
                setDeltaMovement(len < 0.1 ? Vec3.ZERO : d.scale(Math.min(0.9, len * 0.3) / len));
                warnRing(level, target.position());
                face(target.position());
                if (diveTicks >= (divesLeft == 3 ? 14 : 9)) {
                    diveStage = 1;
                    diveTicks = 0;
                    Vec3 aim = predict(target, 5);
                    diveDir = aim.subtract(position()).normalize();
                    entityData.set(DATA_FLYING, false);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0F, 0.6F);
                }
            }
            case 1 -> {
                setDeltaMovement(diveDir.scale(1.7));
                for (LivingEntity e : around(level, 1.6)) {
                    if (strike(e, 10F, ModDamage.BELLY_FLOP, true)) {
                        e.setDeltaMovement(diveDir.multiply(1, 0, 1).scale(1.0).add(0, 0.6, 0));
                        e.hurtMarked = true;
                    }
                }
                if (diveTicks % 2 == 0) Fx.fire(level, position().add(0, 1, 0), 1, 0.3, 0.7F);
                boolean floor = arena != null && getY() <= arena.floorY() + 0.05;
                if (onGround() || verticalCollision || floor || diveTicks > 25) {
                    landImpact(level, position(), 3.4F, 12F, 0.6F);
                    ring(level, position(), 3.0);
                    divesLeft--;
                    diveStage = 2;
                    diveTicks = 0;
                    setDeltaMovement(0, 0.9, 0);
                }
            }
            default -> {
                if (diveTicks >= 6) {
                    if (divesLeft > 0) {
                        diveStage = 0;
                        diveTicks = 0;
                    } else {
                        setBossState(BossState.IDLE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ phase two: the foil potatoes

    private void spawnFoils(ServerLevel level) {
        discardFoils(level);
        for (int i = 0; i < 3; i++) {
            FoilPotatoEntity f = com.groid.papon.registry.ModEntities.FOIL_POTATO.get().create(level);
            if (f == null) continue;
            f.moveTo(getX(), getY() + 1, getZ(), 0, 0);
            level.addFreshEntity(f);
            foils.add(f.getUUID());
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 2.0F, 0.7F);
        Fx.spark(level, position().add(0, 1.5, 0), 8, 1.0, 0.35F, 0xDDE6F0);
        foilCd = -1;
    }

    private void discardFoils(ServerLevel level) {
        for (UUID id : foils) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        foils.clear();
    }

    public int foilsLeft() {
        return foils.size();
    }

    /** Keeps the foil potatoes on their orbit; wraps a new set 45 s after the last one is broken. */
    private void tickFoils(ServerLevel level) {
        if (!isRisen() || getBossState() == BossState.DEATH) return;
        boolean had = !foils.isEmpty();
        foils.removeIf(id -> {
            Entity e = level.getEntity(id);
            return !(e instanceof FoilPotatoEntity f) || !f.isAlive();
        });
        if (had && foils.isEmpty()) {
            foilCd = 900;
            say("foil", 40);
        }
        if (foils.isEmpty()) {
            if (foilCd > 0 && --foilCd == 0 && !getBossState().cutscene()) spawnFoils(level);
            return;
        }
        foilSpin += 4F;
        double r = getBbWidth() * 0.5 + 1.7;
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        for (int i = 0; i < foils.size(); i++) {
            if (!(level.getEntity(foils.get(i)) instanceof FoilPotatoEntity f)) continue;
            double a = Math.toRadians(foilSpin + i * 360.0 / foils.size());
            f.hold(c.add(Math.cos(a) * r, Math.sin(a * 2 + tickCount * 0.05) * 0.4, Math.sin(a) * r));
        }
    }

    // ================================================================== the rework

    /** Anything he may hit (his props ask this too). */
    public boolean canHit(LivingEntity e) {
        return hittable(e);
    }

    private static float pct(boolean p2, float p1, float p2v) {
        return p2 ? p2v : p1;
    }

    // ------------------------------------------------------------------ the comal: guard and riposte

    /**
     * Behind the comal (up to 2 s): it turns to keep facing you; everything from the front clangs off
     * (arrows fly back). Two blocked hits, or the time running out with you close, and he bashes back.
     */
    private void tickGuard(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (target != null) turnToward(target.position(), 9F);
        if (t == 2) com.groid.papon.fx.Fx.glint(level, position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.3)).add(0, 1.6, 0),
                0.6F, 0xFFE8B0);
        boolean close = target != null && distanceTo(target) < 5;
        if (guardBlocks >= 1 || (t >= 8 && close)) {
            setBossState(BossState.RIPOSTE);
            return;
        }
        if (t >= 24) setBossState(BossState.IDLE);
    }

    /** The comal into your face (0.25 s), then the spatula down (0.6 s): a share of your health, through any armor. */
    private void tickRiposte(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 4) face(target.position());
        if (t == 4 && target != null) lunge(target, 0.35, 0.5, 4.0);
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        if (t == 5) {
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 3.6, 60)) {
                if (strike(e, 8F, ModDamage.SPATULA, true)) {
                    e.setDeltaMovement(look.scale(0.4).add(0, 0.25, 0));
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 0.9F, 0xFFD27A);
                }
            }
        }
        if (t == 12) {
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 4.2, 50)) {
                if (e instanceof Player p && p.isBlocking()) p.disableShield(true);
                if (Mechanics.trueHit(e, 0.12F, this, true)) {
                    swingHit = true;
                    e.setDeltaMovement(look.scale(1.0).add(0, 0.5, 0));
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.2F, 0xFFFFFF);
                    if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.6F, 12);
                }
            }
            com.groid.papon.fx.Fx.slash(level, position().add(look.scale(2)).add(0, 1.0, 0), getYRot(), 2.2F, 0xFFFFFF);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.7F);
        }
        if (t >= BossState.RIPOSTE.duration) setBossState(BossState.IDLE);
    }

    /** Out of the way of a swing: a hop to the side and back, then straight into a counter. */
    private void evade(ServerLevel level, LivingEntity from) {
        evadeCd = isPhaseTwo() ? 50 : 80;
        Vec3 away = position().subtract(from.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot() + 180) : away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x).scale(random.nextBoolean() ? 1 : -1);
        setDeltaMovement(side.scale(0.85).add(away.scale(0.45)).add(0, 0.38, 0));
        hasImpulse = true;
        face(from.position());
        com.groid.papon.fx.Fx.dust(level, position(), 0.4, 3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1.2F, 0.6F);
        if (random.nextInt(3) == 0) say("dodge", 30);
        setBossState(BossState.EVADE);
    }

    private void tickEvade(ServerLevel level, @Nullable LivingEntity target) {
        if (target != null) face(target.position());
        if (stateTicks >= BossState.EVADE.duration) {
            if (target == null) {
                setBossState(BossState.IDLE);
                return;
            }
            double d = distanceTo(target);
            // the counter: whatever reaches from here, no pause
            BossState pick = d < 4.5 ? BossState.SWIPE : (haymakerCd <= 0 && random.nextBoolean() ? BossState.HAYMAKER : BossState.DASH_COMBO);
            if (pick == BossState.SWIPE) swipeCd = 0;
            if (pick == BossState.DASH_COMBO) dashCd = 0;
            startAttack(pick, target);
        }
    }

    // ------------------------------------------------------------------ chef: cleavers, the burrito, the griddle

    /**
     * Los Tres Chefs: the cleavers crossed over his head and three echoes of him blink into the air round
     * you (0.1, 0.4, 0.7 s): left, right, behind, high up, so they are never all in front of you. Each
     * marks where its cleaver will land with a red line, glints, and throws (phase two: twice). Hit an
     * echo before it throws and it shatters. Then he comes for you himself.
     */
    private void tickCleaverToss(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        if (target != null) face(target.position());
        if (t == 1) say("echoes", 40);
        if ((t == 2 || t == 8 || t == 14) && target != null) {
            int i = (t - 2) / 6;
            Vec3 in = flatTo(target.position()).scale(-1);           // from the target back toward him
            if (in.lengthSqr() < 1e-4) in = Vec3.directionFromRotation(0, getYRot()).scale(-1);
            float turn = new float[] {75F, -75F, 180F}[i] + (random.nextFloat() - 0.5F) * 20F;
            Vec3 out = in.yRot(turn * Mth.DEG_TO_RAD);
            Vec3 at = target.position().add(out.scale(6.5)).add(0, 4.5 + i * 0.6, 0);
            level.addFreshEntity(PaponEchoEntity.create(level, this, target, at, isPhaseTwo() ? 2 : 1));
            com.groid.papon.fx.Fx.glint(level, position().add(0, 3.2, 0), 1.0F, 0xFF6A4A);
        }
        if (t >= BossState.CLEAVER_TOSS.duration) {
            if (target != null && distanceTo(target) < 14) {
                dashCd = 0;
                startAttack(BossState.DASH_COMBO, target);
            } else {
                setBossState(BossState.IDLE);
            }
        }
    }

    /**
     * The burrito: a giant tortilla spun over his head and flung (1.0 s) at where you are going; a ring
     * on the ground closes on the spot. Still in it when it lands (1.3 s) and you are rolled up in it:
     * no moving, no swinging, no drinking, for 2.5 s; and he comes bowling at you.
     */
    private void tickBurritoToss(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 20) face(target.position());
        if (t == 6 && target != null) {
            Vec3 at = predict(target, 18);
            burritoAt = new Vec3(at.x, groundY(at), at.z);
            com.groid.papon.fx.Fx.warnClosing(level, burritoAt, 2.4F, 0xF0D49A, 20);
        }
        if (t == 20) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.5F);
        if (t == 26 && burritoAt != null) {
            LivingEntity rolled = null;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(burritoAt, burritoAt).inflate(2.4, 2.5, 2.4), this::hittable)) {
                if (Math.sqrt(e.distanceToSqr(burritoAt.x, e.getY(), burritoAt.z)) > 2.4 + e.getBbWidth() / 2 || e.isPassenger()) continue;
                Mechanics.burrito(e, 50);
                level.addFreshEntity(BossProp.create(level, BossProp.Kind.BURRITO, this, e.position()).follow(e).life(60));
                if (e instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("status.papon.burrito").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                }
                if (rolled == null || e instanceof Player) rolled = e;
            }
            com.groid.papon.fx.Fx.dust(level, burritoAt, 1.2, 4);
            level.playSound(null, burritoAt.x, burritoAt.y, burritoAt.z, SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 2.0F, 0.6F);
            if (rolled != null) {
                say("burrito", 40);
                setTarget(rolled);
                chargesLeft = 0;
                swingHit = true;
                setBossState(BossState.CHARGE_WINDUP);
                stateTicks = 4;
                return;
            }
        }
        if (t >= BossState.BURRITO_TOSS.duration) setBossState(BossState.IDLE);
    }

    /** The comal slammed flat (0.9 s): the ground round him a hot griddle for six seconds. */
    private void tickComalSlam(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 12) face(target.position());
        if (t == 2) com.groid.papon.fx.Fx.warn(level, position(), 5.5F, 0xFF7A2A, 16);
        if (t == 18) {
            griddleAt = position();
            griddleLeft = 120;
            com.groid.papon.fx.Fx.shock(level, position(), 5.5F, 0xFFB060);
            com.groid.papon.fx.Fx.zone(level, position(), 5.5F, 0xFF5A1E, 120, 0.5F);
            PaponNetwork.shake(level, position(), 14, 0.4F, 12);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.5F, 0.7F);
            for (LivingEntity e : around(level, 5.5)) {
                if (!e.onGround()) continue;
                if (strike(e, 8F, ModDamage.TEQUILA_FIRE, false)) Mechanics.heat(e, 1, this);
            }
        }
        if (t >= BossState.COMAL_SLAM.duration) setBossState(BossState.IDLE);
    }

    /** The hot griddle: anyone standing on it heats up (a Habanero stack a second) and sizzles. */
    private void tickGriddle(ServerLevel level) {
        if (griddleAt == null) return;
        if (--griddleLeft <= 0) {
            griddleAt = null;
            return;
        }
        if (tickCount % 6 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 5;
            Vec3 p = griddleAt.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            com.groid.papon.fx.Fx.smoke(level, p, 1, 0.1, 0.4F, 0xD8D0C8);
        }
        if (tickCount % 20 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(griddleAt, griddleAt).inflate(5.5, 1.5, 5.5), this::hittable)) {
                if (!e.onGround() || Math.sqrt(e.distanceToSqr(griddleAt.x, e.getY(), griddleAt.z)) > 5.5) continue;
                e.hurt(source(ModDamage.TEQUILA_FIRE), 3F * dmg());
                Mechanics.heat(e, 1, this);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.4F);
            }
        }
    }

    // ------------------------------------------------------------------ the drunken attacks

    /** The tell: a swig (0.7 s). Then the flurry, and nothing stops it. */
    private void tickDrunkSwig(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        if (target != null) face(target.position());
        if (stateTicks == 2) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 1.5F, 0.6F);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16))) {
                p.displayClientMessage(Component.translatable("status.papon.drunk").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
        if (stateTicks >= BossState.DRUNK_SWIG.duration) {
            say("borracho", 40);
            setBossState(BossState.DRUNK_FLURRY);
        }
    }

    /**
     * Unstoppable (half damage, no knockback, no stagger): five lurching haymakers, each a lunge that
     * re-aims at where you are going (hits at 0.5 / 0.85 / 1.2 / 1.55 / 1.9 s, a share of your health
     * each, through armor), then a spinning hammer of both fists (2.5 s) that flattens everything round
     * him. Get out of reach; there is no blocking it.
     */
    private void tickDrunkFlurry(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        int[] hits = {10, 17, 24, 31, 38};
        for (int k = 0; k < hits.length; k++) {
            int h = hits[k];
            if (target != null && t >= h - 4 && t < h) turnToward(predict(target, 3), 30F);
            if (t == h - 3 && target != null) {
                // a lurch: sideways a little first, the drunk's angle
                float yaw = getYRot() + (k % 2 == 0 ? 18 : -18) * (random.nextBoolean() ? 1 : 0.4F);
                Vec3 dir = Vec3.directionFromRotation(0, yaw);
                double d = Math.sqrt(horizontalDistanceSqr(target.position()));
                double v = Mth.clamp((d - 1.4) * 0.3, 0.25, 1.4);
                setDeltaMovement(dir.x * v, getDeltaMovement().y, dir.z * v);
                hasImpulse = true;
            }
            if (t == h) {
                Vec3 look = Vec3.directionFromRotation(0, getYRot());
                for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 3.8, 65)) {
                    if (e instanceof Player p && p.isBlocking()) p.disableShield(true);
                    if (Mechanics.trueHit(e, pct(isPhaseTwo(), 0.06F, 0.075F), this, true)) {
                        swingHit = true;
                        e.setDeltaMovement(look.scale(0.6).add(0, 0.25, 0));
                        e.hurtMarked = true;
                        com.groid.papon.fx.Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.0F, 0xFFC24A);
                        if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.35F, 8);
                    }
                }
                com.groid.papon.fx.Fx.slash(level, position().add(look.scale(1.8)).add(0, 1.2, 0), getYRot() + (k % 2 == 0 ? 25 : -25), 1.8F,
                        0xFFB040);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.5F, 0.55F);
            }
        }
        if (t == 50) {
            for (LivingEntity e : around(level, 4.8)) {
                if (e instanceof Player p && p.isBlocking()) p.disableShield(true);
                if (Mechanics.trueHit(e, pct(isPhaseTwo(), 0.14F, 0.18F), this, true)) {
                    swingHit = true;
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(1.5).add(0, 0.7, 0));
                    e.hurtMarked = true;
                }
            }
            com.groid.papon.fx.Fx.land(level, position(), 4.8F);
            PaponNetwork.shake(level, position(), 18, 0.6F, 16);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.6F);
        }
        if (t >= BossState.DRUNK_FLURRY.duration) setBossState(BossState.IDLE);
    }

    /**
     * The feint: he wobbles about looking finished (to 1.4 s, still turning to you); his eye glints
     * (1.5 s) and he lunges eight blocks with a haymaker (1.8 s) that takes a fifth of your health
     * through anything and sends you flying. Then he falls on his face: hit him.
     */
    private void tickHaymaker(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target != null && t < 30) turnToward(target.position(), 6F);
        if (t < 28 && t % 7 == 0) {
            Vec3 wob = Vec3.directionFromRotation(0, getYRot() + (random.nextFloat() - 0.5F) * 160F).scale(0.12);
            setDeltaMovement(wob.x, getDeltaMovement().y, wob.z);
        }
        if (t == 30) {
            com.groid.papon.fx.Fx.glint(level, position().add(Vec3.directionFromRotation(0, getYRot()).scale(0.9)).add(0, 2.4, 0), 1.0F, 0xFFFFFF);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 1.6F);
            if (target != null) face(predict(target, 6));
        }
        if (t == 34 && target != null) {
            Vec3 aim = predict(target, 3);
            face(aim);
            double d = Math.sqrt(horizontalDistanceSqr(aim));
            Vec3 dir = flatTo(aim);
            double v = Mth.clamp((d - 1.2) / 2.6, 0.4, 3.1);       // up to eight blocks in three ticks
            dashVel = dir.scale(v);
        }
        if (t >= 34 && t < 37) setDeltaMovement(dashVel.x, getDeltaMovement().y, dashVel.z);
        if (t == 36) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            boolean hit = false;
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 4.0, 60)) {
                if (e instanceof Player p && p.isBlocking()) p.disableShield(true);
                if (Mechanics.trueHit(e, pct(isPhaseTwo(), 0.22F, 0.28F), this, true)) {
                    hit = true;
                    swingHit = true;
                    e.setDeltaMovement(look.scale(2.6).add(0, 0.9, 0));
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.6F, 0xFFFFFF);
                    if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.0F, 20);
                }
            }
            com.groid.papon.fx.Fx.slash(level, position().add(look.scale(2)).add(0, 1.4, 0), getYRot(), 2.6F, 0xFFFFFF);
            level.playSound(null, getX(), getY(), getZ(), hit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 2.0F, 0.5F);
            if (hit) say("haymaker", 40);
        }
        if (t >= BossState.HAYMAKER.duration) {
            boolean missed = !swingHit;
            setBossState(BossState.IDLE);
            if (missed) think = 18;              // face down in the dirt after a miss
        }
    }

    // ------------------------------------------------------------------ the van, as a tool

    /** At the van's window: the brick on the pedal (1.0 s). The van goes for you for six seconds. */
    private void tickBrick(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle()) {
            setBossState(BossState.IDLE);
            return;
        }
        face(van.position());
        if (stateTicks == 20) {
            brickTicks = isPhaseTwo() ? 160 : 120;
            van.setBricked(true);
            van.burnout(10);
            van.honk();
            level.playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.5F, 0.7F);
            say("brick", 40);
        }
        if (stateTicks >= BossState.BRICK.duration) {
            think = 2;
            setBossState(BossState.IDLE);
        }
    }

    /** The bricked van, driving itself at the target (it runs over whatever is in the way); then it rolls to a stop. */
    private void tickBrickVan(ServerLevel level, @Nullable LivingEntity target) {
        if (brickTicks <= 0) return;
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || target == null || --brickTicks == 0) {
            brickTicks = 0;
            if (van != null) van.setBricked(false);
            return;
        }
        double dist = target.distanceTo(van);
        Vec3 aim = predict(target, (int) Mth.clamp(dist / Math.max(Math.abs(van.speed()), 0.6), 0, 20));
        van.autopilot(aim, false, van.position().distanceTo(aim) < 3);
        if (tickCount % 10 == 0) com.groid.papon.fx.Fx.warnLine(level, van.position(), van.position().add(van.forward().scale(9)), 0.9F,
                com.groid.papon.fx.Fx.DANGER, 10);
    }

    /**
     * Onto the van's roof (a running jump) and off it, higher than he can jump from the ground: the
     * leap, with a longer hang and a bigger landing.
     */
    private void tickVanHop(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || van.isSliding() || stateTicks > 80 || target == null) {
            setBossState(BossState.IDLE);
            return;
        }
        Vec3 roof = van.position().add(0, 2.2, 0);
        if (horizontalDist(van.position()) < 2.6) {
            setPos(roof.x, roof.y, roof.z);
            com.groid.papon.fx.Fx.dust(level, roof, 0.6, 3);
            level.playSound(null, roof.x, roof.y, roof.z, SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.6F, 0.6F);
            leapCd = 0;
            startAttack(BossState.LEAP, target);
            stateTicks = 7;
            return;
        }
        if (getNavigation().isDone() || tickCount % 6 == 0) getNavigation().moveTo(van.getX(), van.getY(), van.getZ(), 1.6);
    }

    // ------------------------------------------------------------------ the QTEs

    /**
     * The QTEs (three of them): he lunges; touch him and he has you in his fist, the world goes slow
     * and the camera finds his face, and keys come up on your screen one at a time, 1.5 s each (presses in
     * the first 0.6 s do not count: you were mid-fight)
     * (three, four or five of them). Press each one in time: you break free and he goes over on his
     * back, open, taking double. Miss one, or press the wrong key, and he does it:
     *   0  La Última Cena    the cleaver: 35% of your health through armor, and no healing for 10 s
     *   1  Last Call         the handle down your throat: 25%, three Habanero stacks, Brittle
     *   2  La Papa Suplex    over his head and into the floor: 40%, stunned and rooted
     * He cannot be hurt while he holds you.
     */
    private void tickQteGrab(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (target == null) {
            setBossState(BossState.IDLE);
            return;
        }
        if (t < 8) {
            face(target.position());
            if (t == 1) com.groid.papon.fx.Fx.glint(level, position().add(0, 2.4, 0), 1.2F, 0xFF4030);
            return;
        }
        if (t == 8) {
            Vec3 aim = predict(target, 5);
            double d = Math.sqrt(horizontalDistanceSqr(aim));
            dashVel = flatTo(aim).scale(Mth.clamp((d - 1.0) / 4.0, 0.3, 1.5));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        if (t < 13) setDeltaMovement(dashVel.x, getDeltaMovement().y, dashVel.z);
        for (LivingEntity e : around(level, getBbWidth() * 0.5 + 1.2)) {
            if (e.isPassenger() || e.isVehicle() || e.getBbHeight() > 3 || (e instanceof Player p && p.getAbilities().flying)) continue;
            startQte(level, e);
            return;
        }
        if (t > 22) {
            if (WanderingDebug.on()) PaponMod.LOG.info("[debug] QTE grab missed: target {} at {}", target, String.format("%.2f", distanceTo(target)));
            setBossState(BossState.IDLE);
            think = 16;                          // he lunged at nothing: a moment to punish him
            qteCd = Math.min(qteCd, 160);        // and he tries it again soon; the same one comes round next
            nextQte = (nextQte + 4) % 5;
        }
    }

    private static final int[] QTE_KEYS = {
            org.lwjgl.glfw.GLFW.GLFW_KEY_G, org.lwjgl.glfw.GLFW.GLFW_KEY_H, org.lwjgl.glfw.GLFW.GLFW_KEY_J, org.lwjgl.glfw.GLFW.GLFW_KEY_K,
            org.lwjgl.glfw.GLFW.GLFW_KEY_U, org.lwjgl.glfw.GLFW.GLFW_KEY_I, org.lwjgl.glfw.GLFW.GLFW_KEY_O, org.lwjgl.glfw.GLFW.GLFW_KEY_Z,
            org.lwjgl.glfw.GLFW.GLFW_KEY_V, org.lwjgl.glfw.GLFW.GLFW_KEY_B, org.lwjgl.glfw.GLFW.GLFW_KEY_N, org.lwjgl.glfw.GLFW.GLFW_KEY_M,
            org.lwjgl.glfw.GLFW.GLFW_KEY_R, org.lwjgl.glfw.GLFW.GLFW_KEY_Y};
    /** Ticks for each key (1.5 s). */
    public static final int QTE_WINDOW = 30;
    /** The tango's steps: the arrow keys. */
    private static final int[] TANGO_KEYS = {org.lwjgl.glfw.GLFW.GLFW_KEY_UP, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT};
    /** This QTE's ticks per key. */
    private int qteWindow = QTE_WINDOW;

    /** Ticks a step of the tango gets: a beat of the song (and a little for the network). */
    private static int tangoBeat() {
        Song song = Song.get();
        if (song == null || song.beats.length < 2) return 12;
        double beat = (song.beats[song.beats.length - 1] - song.beats[0]) / (song.beats.length - 1);
        return (int) Math.round(beat * 20) + 3;
    }
    /** Ticks at the start when presses do not count (you were mid-fight, mashing keys); the first key's clock starts after. */
    public static final int QTE_GRACE = 12;

    private void startQte(ServerLevel level, LivingEntity e) {
        int kind = entityData.get(DATA_QTE);
        hold(e);
        setDeltaMovement(Vec3.ZERO);
        int n = switch (kind) {
            case 0 -> 3;
            case 1 -> 4;
            case 3 -> 14;              // mash it
            case 4 -> 7;               // the tango: seven steps
            default -> 5;
        };
        qteWindow = kind == 3 ? 9 : kind == 4 ? tangoBeat() : QTE_WINDOW;
        qteKeys = new int[n];
        int mash = QTE_KEYS[random.nextInt(QTE_KEYS.length)];
        for (int i = 0; i < n; i++) {
            int k;
            if (kind == 3) {
                k = mash;
            } else if (kind == 4) {
                do {
                    k = TANGO_KEYS[random.nextInt(TANGO_KEYS.length)];
                } while (i > 0 && k == qteKeys[i - 1]);
            } else {
                do {
                    k = QTE_KEYS[random.nextInt(QTE_KEYS.length)];
                } while (i > 0 && k == qteKeys[i - 1]);
            }
            qteKeys[i] = k;
        }
        qteStep = 0;
        qteResolved = false;
        say(kind <= 2 ? "qte" + kind : kind == 3 ? "qte1" : "charm0", 60);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.6F, 0.5F);
        if (e instanceof ServerPlayer sp) {
            PaponNetwork.qteStart(sp, getId(), kind, qteKeys, qteWindow);
            PaponNetwork.shake(sp, 0.5F, 10);
        }
        setBossState(BossState.QTE_HOLD);
    }

    /** From the held player's client: key {@code step} pressed right ({@code ok}) or wrong / too late. */
    public void qteInput(ServerPlayer player, int step, boolean ok) {
        if (WanderingDebug.on()) PaponMod.LOG.info("[debug] QTE input from {}: key {} {}", player.getName().getString(), step, ok ? "right" : "wrong");
        if (getBossState() != BossState.QTE_HOLD || qteResolved || heldId() != player.getId() || step != qteStep) return;
        if (!ok) {
            endQte(false, false);
            return;
        }
        qteStep++;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 0.8F + qteStep * 0.15F);
        if (qteStep >= qteKeys.length) endQte(true, false);
    }

    /** The QTE is over: broke free, or failed (he does it). {@code aborted}: he lost hold of them. */
    private void endQte(boolean won, boolean aborted) {
        if (qteResolved) return;
        qteResolved = true;
        Entity held = heldId() < 0 ? null : level().getEntity(heldId());
        if (held instanceof ServerPlayer sp) PaponNetwork.qteEnd(sp, won);
        if (aborted) return;
        if (won) {
            LivingEntity v = held instanceof LivingEntity le ? le : null;
            release();
            if (v != null) {
                Vec3 back = v.position().subtract(position()).multiply(1, 0, 1).normalize();
                v.setDeltaMovement(back.scale(0.8).add(0, 0.4, 0));
                v.hurtMarked = true;
            }
            say("qte_broken", 50);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.6F);
            if (level() instanceof ServerLevel level) com.groid.papon.fx.Fx.hit(level, position().add(0, 1.6, 0), 1.6F, 0xFFFFFF);
            setBossState(BossState.QTE_BROKEN);
        } else {
            setBossState(BossState.QTE_FAIL);
        }
    }

    private void tickQteHold(ServerLevel level) {
        LivingEntity v = held(level);
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (v == null) {
            endQte(false, true);
            setBossState(BossState.IDLE);
            return;
        }
        // the whole sequence plus some slack for the network; anyone who is not a player just fails
        int limit = 20 + QTE_GRACE + qteKeys.length * (qteWindow + 8);
        if (stateTicks > limit || (!(v instanceof Player) && stateTicks > 50)) endQte(false, false);
    }

    private void tickQteFail(ServerLevel level) {
        int t = stateTicks;
        LivingEntity v = held(level);
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        int kind = qteBase();
        if (t == 24 && v != null) {
            Mechanics.trueHit(v, qteKind() == 3 ? 0.30F : qteKind() == 4 ? 0.45F : kind == 0 ? 0.35F : kind == 1 ? 0.25F : 0.40F, this, true);
            swingHit = true;
            Vec3 at = v.position().add(0, v.getBbHeight() * 0.5, 0);
            switch (kind) {
                case 0 -> {
                    Mechanics.poison(v, 200);
                    com.groid.papon.fx.Fx.hit(level, at, 1.8F, 0xFFFFFF);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.5F);
                }
                case 1 -> {
                    Mechanics.heat(v, 3, this);
                    Mechanics.brittle(v, 300);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 2.0F, 0.5F);
                }
                default -> {
                    Mechanics.stun(v);
                    Mechanics.root(v, 40);
                    com.groid.papon.fx.Fx.land(level, v.position(), 3.5F);
                    PaponNetwork.shake(level, position(), 16, 0.9F, 20);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.6F);
                }
            }
            if (v instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.0F, 20);
        }
        if (t == 30 && v != null) {
            release();
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            v.setDeltaMovement(kind == 2 ? f.scale(-0.3).add(0, 0.2, 0) : f.scale(1.4).add(0, 0.5, 0));
            v.hurtMarked = true;
        }
        if (t >= BossState.QTE_FAIL.duration) setBossState(BossState.IDLE);
    }

    private void tickQteBroken(ServerLevel level) {
        getNavigation().stop();
        if (stateTicks % 10 == 0 && stateTicks < 64) {
            double a = stateTicks * 0.5;
            com.groid.papon.fx.Fx.glint(level, position().add(Math.cos(a) * 0.8, 1.0, Math.sin(a) * 0.8), 0.4F, 0xFFE070);
        }
        if (stateTicks >= BossState.QTE_BROKEN.duration) {
            think = 4;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ phase two: potatoes

    /**
     * Fry volley: seven fries leave his wings and hang round him, each aimed (a line on the ground shows
     * where); from 1.0 s they fire one after another at where you are going. A share of your health
     * each, through armor: keep moving across them.
     */
    private void tickFryVolley(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (isFlying()) hover(target);
        if (target != null) turnToward(target.position(), 10F);
        if (t >= 6 && t <= 18 && t % 2 == 0 && target != null) {
            int i = (t - 6) / 2;
            float side = i % 2 == 0 ? 1 : -1;
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            Vec3 right = new Vec3(-f.z, 0, f.x);
            Vec3 at = position().add(right.scale(side * (1.6 + i * 0.35))).add(f.scale(-0.6)).add(0, getBbHeight() * 0.8 + i * 0.25, 0);
            Vec3 aim = predict(target, 14 + i * 2).add(0, 0.9, 0);
            Vec3 dir = aim.subtract(at);
            int delay = 20 - (t - 6) + i * 4;
            level.addFreshEntity(BossProp.create(level, BossProp.Kind.FRY, this, at).fry(dir, 1.9, delay).life(delay + 50));
            Vec3 ground = new Vec3(aim.x, groundY(aim), aim.z);
            com.groid.papon.fx.Fx.warnLine(level, ground.subtract(dir.multiply(1, 0, 1).normalize().scale(3)), ground.add(dir.multiply(1, 0, 1)
                    .normalize().scale(3)), 0.6F, 0xFFD66A, delay);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        if (t >= BossState.P2_FRY_VOLLEY.duration) setBossState(BossState.IDLE);
    }

    /**
     * The mash wave: down on both fists (0.8 s, again at 1.6 s); each slam sends a wall of mashed potato
     * rolling outward as a ring across the floor. On the ground when it reaches you: a share of your
     * health and you are slowed to a crawl. Jump it.
     */
    private void tickMash(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (isFlying()) setFlying(false);
        if (target != null && t < 14) face(target.position());
        if (t == 2) com.groid.papon.fx.Fx.warn(level, position(), 3F, 0xF3E6C2, 14);
        if (t == 16 || t == 32) {
            BossProp wave = BossProp.create(level, BossProp.Kind.MASH, this, new Vec3(getX(), groundY(position()), getZ())).life(60);
            wave.setRadius(1.5F);
            level.addFreshEntity(wave);
            mashWaves.add(new Object[]{wave, 1.5, new java.util.HashSet<Integer>(), t == 16 ? 0.55 : 0.7});
            com.groid.papon.fx.Fx.land(level, position(), 3F);
            PaponNetwork.shake(level, position(), 20, 0.5F, 14);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, 2.0F, 0.4F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        if (t >= BossState.P2_MASH.duration) setBossState(BossState.IDLE);
    }

    @SuppressWarnings("unchecked")
    private void tickMashWaves(ServerLevel level) {
        if (mashWaves.isEmpty()) return;
        java.util.Iterator<Object[]> it = mashWaves.iterator();
        while (it.hasNext()) {
            Object[] w = it.next();
            BossProp prop = (BossProp) w[0];
            double r = (double) w[1] + (double) w[3];
            w[1] = r;
            if (!prop.isAlive() || r > 28) {
                prop.discard();
                it.remove();
                continue;
            }
            prop.setRadius((float) r);
            java.util.Set<Integer> hit = (java.util.Set<Integer>) w[2];
            Vec3 c = prop.position();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1, 2, r + 1), this::hittable)) {
                double d = Math.sqrt(e.distanceToSqr(c.x, e.getY(), c.z));
                if (Math.abs(d - r) > 0.9 || e.getY() > c.y + 1.2 || hit.contains(e.getId())) continue;
                if (!e.onGround() && e.getY() > c.y + 0.4) continue;      // jumped it
                hit.add(e.getId());
                if (Mechanics.trueHit(e, 0.15F, this, true)) {
                    e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, true, true));
                    Vec3 away = e.position().subtract(c).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(0.9).add(0, 0.4, 0));
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.chunks(level, e.position().add(0, 0.6, 0), 8, 0.3F, 0xF3E6C2, 0.35);
                }
            }
        }
    }

    /** A tater tot the size of his head, lobbed at where you are going (1.1 s): it bursts, and six little ones burst after. */
    private void tickTot(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (isFlying()) hover(target);
        if (target != null && t < 22) turnToward(target.position(), 15F);
        if (t == 22 && target != null) {
            Vec3 hand = position().add(Vec3.directionFromRotation(0, getYRot() + 40).scale(0.9)).add(0, getBbHeight() * 0.9, 0);
            Vec3 aim = predict(target, 18);
            Vec3 d = aim.subtract(hand);
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            int air = (int) Mth.clamp(flat / 0.75, 10, 30);
            Vec3 v = new Vec3(d.x / air, (d.y + 0.5 * 0.06 * air * air) / air, d.z / air);
            level.addFreshEntity(BossProp.create(level, BossProp.Kind.TOT, this, hand).velocity(v).life(100));
            com.groid.papon.fx.Fx.warn(level, new Vec3(aim.x, groundY(aim), aim.z), 2.6F, 0xFF9A3C, air);
            level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        if (t >= BossState.P2_TOT.duration) setBossState(BossState.IDLE);
    }

    /**
     * The stomp (0.7 s): a ring closes under everyone near; when it closes (1.4 s), potato sprouts burst
     * out of the floor and hold whoever is still standing there by the ankles (2 s) — and he dives on
     * the first of them.
     */
    private void tickSnare(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (isFlying()) setFlying(false);
        if (target != null && t < 14) face(target.position());
        if (t == 14) {
            PaponNetwork.shake(level, position(), 14, 0.35F, 10);
            com.groid.papon.fx.Fx.land(level, position(), 2.5F);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::hittable)) {
                Vec3 at = new Vec3(p.getX(), groundY(p.position()), p.getZ());
                com.groid.papon.fx.Fx.warnClosing(level, at, 1.8F, 0x9CCB5A, 14);
                snares.add(new double[]{at.x, at.y, at.z, 14});
            }
            if (snares.isEmpty() && target != null) {
                Vec3 at = new Vec3(target.getX(), groundY(target.position()), target.getZ());
                com.groid.papon.fx.Fx.warnClosing(level, at, 1.8F, 0x9CCB5A, 14);
                snares.add(new double[]{at.x, at.y, at.z, 14});
            }
        }
        if (t >= BossState.P2_SNARE.duration) {
            LivingEntity rooted = null;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(32), this::hittable)) {
                if (e.hasEffect(com.groid.papon.registry.ModEffects.ROOTED.get())) {
                    rooted = e;
                    break;
                }
            }
            if (rooted != null) {
                setTarget(rooted);
                diveCd = 0;
                pendingDives = 1;
                startAttack(BossState.P2_DIVE, rooted);
            } else {
                setBossState(BossState.IDLE);
            }
        }
    }

    private void tickSnares(ServerLevel level) {
        if (snares.isEmpty()) return;
        java.util.Iterator<double[]> it = snares.iterator();
        while (it.hasNext()) {
            double[] s = it.next();
            if (--s[3] > 0) continue;
            it.remove();
            Vec3 at = new Vec3(s[0], s[1], s[2]);
            level.addFreshEntity(BossProp.create(level, BossProp.Kind.SPROUTS, this, at).life(44));
            com.groid.papon.fx.Fx.chunks(level, at.add(0, 0.3, 0), 10, 0.3F, 0x8A6A3E, 0.4);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.8, 1.5, 1.8), this::hittable)) {
                if (Math.sqrt(e.distanceToSqr(at.x, e.getY(), at.z)) > 1.8 + e.getBbWidth() / 2) continue;
                Mechanics.root(e, 40);
                strike(e, 8F, ModDamage.BELLY_FLOP, false);
                if (e instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("status.papon.rooted").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                }
            }
        }
    }

    /**
     * The Spud Singularity: high over the middle of the arena a giant potato forms (0.4 s) and drags
     * everyone toward the circle under it (1.0-4.0 s: walk out against it); then it comes down (4.2 s)
     * on everything inside the circle: two fifths of your health through anything.
     */
    /**
     * El Gran Meteoro (9 s): he rises over the middle of the sand, arms up, and calls down a potato almost as
     * big as his coliseum. It swells in high overhead (2 s), blazing, and looms lower and lower while
     * everything on the sand is dragged toward the middle, harder the closer you are (sprint for the wall);
     * the sky goes blood-orange. At 7 s he brings his arms down: it falls (0.5 s). The impact is worst under
     * it (nearly half your health, through armor), less the further out, and three shockwave rings race out
     * to the walls: jump them.
     */
    private void tickGravity(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t == 1) {
            Vec3 c = arena != null && arena.built() ? arena.centerVec() : (target != null ? target.position() : position());
            spudFloor = new Vec3(c.x, groundY(c), c.z);
            BossProp spud = BossProp.create(level, BossProp.Kind.GIANT_SPUD, this, spudFloor.add(0, METEOR_HIGH, 0))
                    .life(BossState.P2_GRAVITY.duration + 4);
            level.addFreshEntity(spud);
            spudId = spud.getUUID();
            com.groid.papon.fx.Fx.sigil(level, spudFloor, 16F, 0xF2C24E, 150);
            say("gravity", 50);
            crowdReact(level, CrowdMood.GASP, 60);
            level.playSound(null, spudFloor.x, spudFloor.y + 20, spudFloor.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0F, 0.4F);
            level.playSound(null, spudFloor.x, spudFloor.y + 20, spudFloor.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (spudFloor == null) {
            setBossState(BossState.IDLE);
            return;
        }
        // he holds it up from over the middle of the sand
        setFlying(true);
        Vec3 perch = spudFloor.add(0, 6, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(-3));
        Vec3 to = perch.subtract(position());
        setDeltaMovement(to.lengthSqr() > 0.04 ? to.scale(0.12) : Vec3.ZERO);
        if (target != null) turnToward(target.position(), 4F);
        Entity spud = spudId == null ? null : level.getEntity(spudId);
        if (spud != null && t < METEOR_FALL) {
            // looming lower and lower
            double k = Mth.clamp((t - 40) / (double) (METEOR_FALL - 40), 0, 1);
            spud.setPos(spudFloor.x, spudFloor.y + METEOR_HIGH - (METEOR_HIGH - METEOR_LOW) * k * k, spudFloor.z);
        }
        if (t >= 30 && t < METEOR_FALL + 10) {
            double reach = arena != null ? Coliseum.A + 6 : 34;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(spudFloor, spudFloor).inflate(reach, 12, reach), this::hittable)) {
                Vec3 in = spudFloor.subtract(e.position()).multiply(1, 0, 1);
                double d = in.length();
                if (d < 0.6) continue;
                // gentle at the wall, a current in the middle: sprinting outward holds you near the edge, not near him
                double pull = 0.05 + 0.13 * Math.max(0, 1 - d / reach) * Math.min(1, (t - 30) / 30.0);
                e.setDeltaMovement(e.getDeltaMovement().add(in.normalize().scale(pull)));
                e.hurtMarked = true;
            }
            if (t % 8 == 0) com.groid.papon.fx.Fx.shock(level, spudFloor.add(0, 0.1, 0), 10F + (t % 24), 0xF2C24E);
            if (t % 3 == 0) {
                // dust streaming in from the walls
                double a = random.nextDouble() * Math.PI * 2;
                double r = 14 + random.nextDouble() * 12;
                com.groid.papon.fx.Fx.dust(level, spudFloor.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1.5, 3);
            }
            if (t % 14 == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                bolt(level, spudFloor.add(Math.cos(a) * (Coliseum.B - 2), 0, Math.sin(a) * (Coliseum.B - 2)));
            }
            if (t % 20 == 0) level.playSound(null, spudFloor.x, spudFloor.y + 10, spudFloor.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 2.5F, 0.4F);
        }
        if (t == METEOR_FALL - 6) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(spudFloor, spudFloor).inflate(48), this::hittable)) {
                p.displayClientMessage(Component.translatable("status.papon.meteor").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
        if (spud != null && t >= METEOR_FALL && t <= METEOR_FALL + 10) {
            double k = (t - METEOR_FALL) / 10.0;
            spud.setPos(spudFloor.x, spudFloor.y + METEOR_LOW - (METEOR_LOW - 3) * k * k, spudFloor.z);
        }
        if (t == METEOR_FALL + 10) {
            com.groid.papon.fx.Fx.flash(level, spudFloor.add(0, 3, 0), 8F);
            com.groid.papon.fx.Fx.blast(level, spudFloor.add(0, 1, 0), 7F);
            com.groid.papon.fx.Fx.land(level, spudFloor, 18F);
            com.groid.papon.fx.Fx.chunks(level, spudFloor.add(0, 3, 0), 70, 0.9F, 0xD9AA51, 1.6);
            com.groid.papon.fx.Fx.smoke(level, spudFloor.add(0, 2, 0), 12, 4, 3F, 0xEDE6DA);
            PaponNetwork.shake(level, spudFloor, 72, 1.6F, 44);
            level.playSound(null, spudFloor.x, spudFloor.y, spudFloor.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.4F);
            level.playSound(null, spudFloor.x, spudFloor.y, spudFloor.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.5F);
            level.playSound(null, spudFloor.x, spudFloor.y, spudFloor.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0F, 0.6F);
            crowdReact(level, CrowdMood.ROAR, 80);
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                bolt(level, spudFloor.add(Math.cos(a) * 9, 0, Math.sin(a) * 9));
            }
            // the whole sand goes up: blasts, mash and dust in rings out to the walls
            for (int ring = 0; ring < 3; ring++) {
                double r = 5 + ring * 7;
                int n = 8 + ring * 6;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + ring * 0.3;
                    Vec3 at = spudFloor.add(Math.cos(a) * r * 1.3, 0.3, Math.sin(a) * r);
                    com.groid.papon.fx.Fx.blast(level, at, 2.5F - ring * 0.5F);
                    com.groid.papon.fx.Fx.chunks(level, at.add(0, 0.5, 0), 4, 0.5F, 0xD9AA51, 0.9);
                    com.groid.papon.fx.Fx.dust(level, at, 2.5, 5);
                }
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(spudFloor, spudFloor).inflate(40, 10, 40), this::hittable)) {
                double d = Math.sqrt(e.distanceToSqr(spudFloor.x, e.getY(), spudFloor.z));
                float frac = d < 10 ? 0.45F : d < 18 ? 0.25F : e.onGround() ? 0.10F : 0F;
                if (frac > 0 && Mechanics.trueHit(e, frac, this, true)) {
                    swingHit = true;
                    Vec3 away = e.position().subtract(spudFloor).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(d < 10 ? 2.0 : 1.2).add(0, d < 10 ? 1.0 : 0.6, 0));
                    e.hurtMarked = true;
                    if (d < 10) Mechanics.stun(e);
                }
            }
            if (spud != null) spud.discard();
            meteorRings.clear();
            for (int i = 0; i < 3; i++) meteorRings.add(new double[]{-i * 7.0});
        }
        if (t > METEOR_FALL + 10 && !meteorRings.isEmpty()) tickMeteorRings(level);
        if (t >= BossState.P2_GRAVITY.duration) {
            spudFloor = null;
            meteorRings.clear();
            setFlying(isRisen());
            setBossState(BossState.IDLE);
        }
    }

    private static final int METEOR_HIGH = 44, METEOR_LOW = 20, METEOR_FALL = 140;
    /** The shockwave rings after the impact: each its radius (starting below 0 to stagger them). */
    private final List<double[]> meteorRings = new java.util.ArrayList<>();
    private final java.util.Set<java.util.UUID> ringHit = new java.util.HashSet<>();

    /** Three rings racing out across the sand to the walls: anyone standing where one passes is hit. */
    private void tickMeteorRings(ServerLevel level) {
        for (double[] r : meteorRings) {
            double was = r[0];
            r[0] += 1.4;
            if (r[0] <= 0 || was > Coliseum.A + 2) continue;
            if (was <= 0) ringHit.clear();
            com.groid.papon.fx.Fx.shock(level, spudFloor.add(0, 0.15, 0), (float) r[0], 0xFFD27A);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(spudFloor, spudFloor).inflate(r[0] + 1, 3, r[0] + 1), this::hittable)) {
                double d = Math.sqrt(e.distanceToSqr(spudFloor.x, e.getY(), spudFloor.z));
                if (d < was - 0.8 || d > r[0] + 0.8 || !e.onGround() || !ringHit.add(e.getUUID())) continue;
                if (Mechanics.trueHit(e, 0.08F, this, true)) {
                    Vec3 away = e.position().subtract(spudFloor).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(0.9).add(0, 0.55, 0));
                    e.hurtMarked = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ his props report in

    /**
     * La Freidora: up off the sand and down in a double stomp (0.7 s, 1.2 s): at the first the sand cracks and
     * glows in a pattern (rings, halves, a checkerboard, a cross, or a pool under each of you), and 1.5 s later
     * it is frying oil for 5.5 s while he carries on. "¡A LA PARRILLA!"
     */
    private void tickArenaFryer(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        if (target != null) turnToward(target.position(), 8F);
        if (t == 14 && arena != null && freidora == null) {
            List<Vec3> players = level.getEntitiesOfClass(ServerPlayer.class, arena.bounds(), this::hittable).stream().map(p -> p.position()).toList();
            Freidora.Pattern[] all = Freidora.Pattern.values();
            Freidora.Pattern p = farTicks > 80 ? Freidora.Pattern.HUNT : all[random.nextInt(all.length)];
            if (p == Freidora.Pattern.HUNT && players.isEmpty()) p = Freidora.Pattern.RINGS;     // nobody to put a pool under
            freidora = Freidora.start(level, arena, p, players, random);
            landImpact(level, position(), 3.0F, 0F, 0.4F);
            PaponNetwork.shake(level, position(), 40, 0.6F, 14);
            say("toss", 40);
        }
        if (t == 24) {
            landImpact(level, position(), 2.5F, 0F, 0.3F);
            PaponNetwork.shake(level, position(), 40, 0.4F, 10);
        }
        if (t >= BossState.ARENA_FRYER.duration) {
            think = 4;
            setBossState(BossState.IDLE);
        }
    }

    /** El Machacador's slams: the tick each one lands (a 4-tick drop before it), then 4 held, 8 lifting to the next. */
    static final int[] MASHER_SLAMS = {50, 82, 112};

    /**
     * El Machacador: he flies up over the sand and hauls down a potato masher as big as it (looming over
     * the sand by 1 s; its holes glow green on the sand). Three slams, faster each time (2.5 s, 4.1 s, 5.6 s),
     * and between them, as it lifts, the holes move: the grid slides half a cell and the plate turns, so the
     * hole you stood in is metal now. Anyone under the metal is mashed: 30% of their health through armor,
     * whatever they wear, and slowed (a heavy hit, so the next slam is harder to run from); under a hole,
     * nothing. It lifts away for good from 5.8 s.
     */
    private void tickMasher(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = arena != null ? new Vec3(arena.centerVec().x, arena.floorY(), arena.centerVec().z) : position();
        setFlying(true);
        Vec3 perch = c.add(0, 18, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(-6));
        Vec3 to = perch.subtract(position());
        setDeltaMovement(to.lengthSqr() > 0.04 ? to.scale(0.15) : Vec3.ZERO);
        if (target != null) turnToward(target.position(), 4F);
        BossProp masher = masherId != null && level.getEntity(masherId) instanceof BossProp b ? b : null;
        if (t == 1) {
            masher = BossProp.create(level, BossProp.Kind.MASHER, this, c.add(0, 46, 0)).life(BossState.P2_MASHER.duration + 4);
            masher.setYRot(random.nextFloat() * 90F);
            masher.setVariant(random.nextInt(Masher.VARIANTS));
            masher.setRadius(46F);
            level.addFreshEntity(masher);
            masherId = masher.getUUID();
            say("qte2", 50);
            crowdReact(level, CrowdMood.GASP, 50);
            level.playSound(null, c.x, c.y + 30, c.z, SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 3.0F, 0.4F);
        }
        if (masher == null) {
            setBossState(BossState.IDLE);
            return;
        }
        int last = MASHER_SLAMS[MASHER_SLAMS.length - 1];
        // where it is: down from the sky, then for each slam a hang, a 4-tick drop, 4 held, 8 lifting
        double h = 14 + Math.sin(t * 0.9) * 0.15;
        if (t < 20) {
            double k = t / 20.0;
            h = 46 - 32 * (1 - (1 - k) * (1 - k));
        }
        for (int slam : MASHER_SLAMS) {
            if (t >= slam - 4 && t < slam) {
                double k = (t - (slam - 4)) / 4.0;
                h = 14 - 13.7 * k * k;
            } else if (t >= slam && t < slam + 4) {
                h = 0.3;
            } else if (t >= slam + 4 && t < slam + 12 && slam != last) {
                double k = (t - slam - 4) / 8.0;
                h = 0.3 + 13.7 * (1 - (1 - k) * (1 - k));
            }
        }
        if (t >= last + 4) {
            double k = (t - last - 4) / 14.0;
            h = 0.3 + 50 * k * k;
        }
        float yaw = masher.getYRot();
        int variant = masher.delay();
        for (int i = 0; i + 1 < MASHER_SLAMS.length; i++) {
            if (t == MASHER_SLAMS[i] + 4) {
                // up it goes, and the holes are somewhere else: the grid slides, the plate turns
                variant = (variant + 1 + random.nextInt(Masher.VARIANTS - 1)) % Masher.VARIANTS;
                masher.setVariant(variant);
                yaw = Mth.wrapDegrees(yaw + (random.nextBoolean() ? 1 : -1) * (12F + random.nextFloat() * 18F));
                masher.setYRot(yaw);
                level.playSound(null, c.x, c.y + 8, c.z, SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 3.0F, 0.5F);
                level.playSound(null, c.x, c.y + 8, c.z, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 3.0F, 0.6F);
            }
        }
        masher.setPos(c.x, c.y + h, c.z);
        masher.setRadius((float) h);
        boolean warning = t >= 10 && t < last - 4 && Arrays.stream(MASHER_SLAMS).noneMatch(sl -> t >= sl - 4 && t < sl + 4);
        if (warning && t % 6 == 0) {
            for (Vec3 hole : Masher.holes(yaw, variant)) com.groid.papon.fx.Fx.warn(level, c.add(hole), 2.2F, 0x6CFF6A, 8);
        }
        if (t % 14 == 0 && warning) level.playSound(null, c.x, c.y + h, c.z, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 3.0F, 0.5F);
        if (t == 22) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(40, 12, 40), this::hittable)) {
                p.displayClientMessage(Component.translatable("status.papon.masher").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            }
        }
        for (int slam : MASHER_SLAMS) {
            if (t != slam) continue;
            PaponNetwork.shake(level, c, 72, slam == last ? 1.5F : 1.1F, 24);
            com.groid.papon.fx.Fx.land(level, c, 16F);
            com.groid.papon.fx.Fx.chunks(level, c.add(0, 1, 0), 40, 0.8F, 0xEED69C, 1.4);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.4F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
            crowdReact(level, CrowdMood.ROAR, 50);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(Masher.HALF_X + 4, 6, Masher.HALF_X + 4), this::hittable)) {
                Vec3 off = e.position().subtract(c);
                Vec3 local = Masher.toLocal(off.x, off.z, yaw);
                if (Math.abs(local.x) > Masher.HALF_X || Math.abs(local.z) > Masher.HALF_Z) continue;
                if (Masher.safe(yaw, variant, off, e.getBbWidth() * 0.3)) {
                    com.groid.papon.fx.Fx.dust(level, e.position(), 1.2, 6);
                    continue;
                }
                if (Mechanics.trueHit(e, MASHER_SHARE, this, true)) {
                    swingHit = true;
                    e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 24, 0, false, true));
                    com.groid.papon.fx.Fx.splat(level, e.position(), 1.6F, 0xEED69C, 160);
                    if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.0F, 14);
                }
            }
        }
        if (t >= BossState.P2_MASHER.duration) {
            masher.discard();
            masherId = null;
            setFlying(isRisen());
            setBossState(BossState.IDLE);
        }
    }

    /** What a slam of the masher takes, of the maximum health, through armor. */
    private static final float MASHER_SHARE = 0.30F;

    /** ¡Abucheo!: he turns to the stands, arms up, thumbs down (1.0 s), and they start throwing (7 s). */
    private void tickArenaHeckle(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        if (t == 2) crowdReact(level, CrowdMood.BOO, 160);
        if (t == 20) {
            heckleTicks = 140;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.6F);
        }
        if (target != null && t > 22) turnToward(target.position(), 10F);
        if (t >= BossState.ARENA_HECKLE.duration) {
            think = 2;
            setBossState(BossState.IDLE);
        }
    }

    /** The stands throwing produce at whoever is on the sand, lobbed from all round the bowl. */
    private void tickHeckleBarrage(ServerLevel level) {
        heckleTicks--;
        if (arena == null || heckleTicks % 3 != 0) return;
        List<ServerPlayer> on = level.getEntitiesOfClass(ServerPlayer.class, arena.bounds(), this::hittable);
        if (on.isEmpty()) return;
        ServerPlayer p = on.get(random.nextInt(on.size()));
        double a = random.nextDouble() * Math.PI * 2;
        double d = 4 + random.nextDouble() * 10;
        Vec3 c = arena.centerVec();
        Vec3 from = new Vec3(c.x + Math.cos(a) * (Coliseum.A + d), arena.floorY() + 3 + d * 0.9, c.z + Math.sin(a) * (Coliseum.B + d));
        int time = 22 + random.nextInt(12);
        Vec3 to = predict(p, time).add((random.nextDouble() - 0.5) * 2.5, 0, (random.nextDouble() - 0.5) * 2.5);
        double g = 0.06;
        Vec3 v = new Vec3((to.x - from.x) / time, (to.y + 0.5 - from.y + 0.5 * g * time * time) / time, (to.z - from.z) / time);
        level.addFreshEntity(BossProp.create(level, BossProp.Kind.PRODUCE, this, from).velocity(v).life(time + 30));
    }

    /** A prop of his touched {@code e}: what that costs them. */
    public void propHit(BossProp p, LivingEntity e) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
        switch (p.kind()) {
            case PRODUCE -> {
                if (Mechanics.trueHit(e, 0.025F, this, true)) {
                    e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, true));
                    com.groid.papon.fx.Fx.splat(level, e.position(), 0.6F, 0xC0281C, 60);
                    com.groid.papon.fx.Fx.chunks(level, at, 5, 0.2F, 0xC0281C, 0.3);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.2F, 1.2F);
                }
            }
            case CLEAVER, CLEAVER_DART -> {
                if (strike(e, 9F, ModDamage.SPATULA, false)) {
                    Mechanics.poison(e, 120);
                    com.groid.papon.fx.Fx.hit(level, at, 0.9F, 0xE0E4EA);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.5F);
                }
            }
            case FRY -> {
                if (e instanceof Player pl && pl.isBlocking()) {
                    pl.disableShield(true);
                    return;
                }
                if (Mechanics.trueHit(e, 0.06F, this, true)) {
                    swingHit = true;
                    Vec3 v = p.getDeltaMovement().normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(v.scale(0.5)).add(0, 0.2, 0));
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.hit(level, at, 0.9F, 0xFFD66A);
                }
            }
            default -> {}
        }
    }

    /** A prop of his came down at {@code at} (a tot burst, a meteor landing). */
    public void propLand(BossProp p, Vec3 at) {
        if (!(level() instanceof ServerLevel level)) return;
        if (p.kind() == BossProp.Kind.PRODUCE) {
            com.groid.papon.fx.Fx.splat(level, at, 0.7F, Math.floorMod(p.getId(), 4) == 0 ? 0xC0281C : 0xD9AA51, 80);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.3F);
            return;
        }
        boolean big = p.kind() == BossProp.Kind.TOT || p.kind() == BossProp.Kind.METEOR;
        float r = p.kind() == BossProp.Kind.METEOR ? 2.6F : big ? 2.4F : 1.6F;
        com.groid.papon.fx.Fx.blast(level, at, big ? 1.5F : 0.9F);
        if (p.kind() == BossProp.Kind.METEOR) {
            com.groid.papon.fx.Fx.scorch(level, at, 2.2F, 200);
            addFire(level, at.add(0, 0.5, 0));
        } else {
            com.groid.papon.fx.Fx.chunks(level, at.add(0, 0.3, 0), big ? 10 : 5, 0.3F, 0xE9B85A, 0.4);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, big ? 1.2F : 0.7F, big ? 1.0F : 1.5F);
        PaponNetwork.shake(level, at, big ? 8 : 4, big ? 0.25F : 0.1F, 8);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 2.0, r), this::hittable)) {
            if (e.position().distanceTo(at) > r + e.getBbWidth() / 2) continue;
            if (strike(e, big ? 8F : 5F, ModDamage.MOLOTOV, false)) {
                Mechanics.heat(e, 1, this);
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(away.scale(0.6).add(0, 0.45, 0));
                e.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ hits

    private float dmg() {
        return PaponConfig.BOSS_DAMAGE.get().floatValue() * (isRisen() ? 1.2F : 1F);
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
        Fx.slash(level, sw, getYRot() + (random.nextBoolean() ? 25 : -25), 2.3F, Fx.WHITE);
    }

    private List<LivingEntity> inCone(ServerLevel level, Vec3 from, Vec3 look, double range, double halfAngle) {
        double cos = Math.cos(Math.toRadians(halfAngle));
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range), e -> {
            if (e == this || !e.isAlive() || e.isAlliedTo(this) || e instanceof CaravanEntity || e instanceof FoilPotatoEntity) return false;
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
                e -> e != this && e.isAlive() && !(e instanceof CaravanEntity) && !(e instanceof FoilPotatoEntity)
                        && !(e instanceof com.groid.papon.entity.npc.TacoNpc)
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

    /** A landing's shockwave: one ring blown outward and a little dust. */
    private void tickShockwave(ServerLevel level) {
        if (shockwave < 0 || shockAt == null) return;
        Fx.land(level, shockAt, 6.5F);
        shockwave = -1;
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

    /**
     * Taking damage, and his defences against a pack where players hit for tens of thousands:
     *   the cap         no hit takes more than {@code hitCapFraction} of his health (doubled while he is
     *                   down after a broken QTE), so nobody one-shots him or skips a phase
     *   the comal       a burst of damage (or an arrow) and he snaps his griddle up: anything from the front
     *                   clangs off it, arrows fly back; hit him from behind instead (that breaks it)
     *   the sidestep    swing at him up close while he is between attacks and he may just not be there
     *   unstoppable     through his drunken attacks he takes half and nothing stops him
     * And he is open: stumbling out of the van, staggered after a missed charge, flat on his back after
     * a QTE you won.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        BossState s = getBossState();
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (s.intro() || s == BossState.ROAR || s == BossState.DEATH || s == BossState.PHASE || s.cutscene() || s.qte()
                || s == BossState.GRAB || s == BossState.GRAB_COMBO) return false;
        if (source.getEntity() == this || source.getDirectEntity() instanceof MolotovEntity || source.getDirectEntity() instanceof BossProp) {
            return false;
        }
        Entity attacker = source.getEntity();
        Vec3 from = source.getSourcePosition();
        boolean front = from != null && flatTo(from).dot(Vec3.directionFromRotation(0, yBodyRot)) > 0.25;
        if (s == BossState.GUARD) {
            if (front) {
                guardBlocks++;
                if (level() instanceof ServerLevel level) {
                    Vec3 at = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(1.2)).add(0, 1.3, 0);
                    com.groid.papon.fx.Fx.spark(level, at, 6, 0.3, 0.35F, 0xFFD27A);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.0F, 1.6F);
                    if (attacker instanceof LivingEntity le && le.distanceTo(this) < 5) {
                        Vec3 away = le.position().subtract(position()).multiply(1, 0, 1).normalize();
                        le.setDeltaMovement(le.getDeltaMovement().add(away.scale(0.5)).add(0, 0.15, 0));
                        le.hurtMarked = true;
                    }
                }
                return false;
            }
            // round the back of it: the guard breaks
            amount *= 1.5F;
            setBossState(BossState.STAGGER);
            stateTicks = 16;
        } else if (s == BossState.IDLE && level() instanceof ServerLevel level) {
            boolean melee = source.getDirectEntity() instanceof Player p && p.distanceTo(this) < 4.5;
            if (melee && evadeCd <= 0 && random.nextFloat() < (isPhaseTwo() ? 0.4F : 0.28F)) {
                evade(level, (LivingEntity) source.getDirectEntity());
                return false;
            }
            boolean shot = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
            float share = Math.min(amount, getMaxHealth() * PaponConfig.HIT_CAP.get().floatValue()) / getMaxHealth();
            if (guardCd <= 0 && !isFlying() && ((shot && random.nextBoolean()) || recentHurt + share > 0.07F)) {
                if (attacker != null) face(attacker.position());
                guardCd = isPhaseTwo() ? 140 : 200;
                setBossState(BossState.GUARD);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.4F, 0.6F);
                return false;
            }
        }
        if (s == BossState.EXIT_STUMBLE) amount *= 1.5F;
        if (s == BossState.STAGGER) amount *= 1.75F;
        if (s == BossState.QTE_BROKEN) amount *= 2F;
        if (s.unstoppable()) amount *= 0.5F;
        if (isPassenger()) amount *= 0.5F;
        if (isRisen() && foilsLeft() > 0) {
            // the foil potatoes soak it up
            amount *= 0.33F;
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.0F, 1.4F);
                com.groid.papon.fx.Fx.spark(level, position().add(0, getBbHeight() * 0.6, 0), 4, 0.4, 0.3F, 0xDDE6F0);
            }
        }
        float cap = getMaxHealth() * PaponConfig.HIT_CAP.get().floatValue() * (s == BossState.QTE_BROKEN ? 2F : 1F);
        amount = Math.min(amount, cap);
        // his composure: past a burst's worth, hits glance off (big damage does not skip the mechanics)
        boolean open = s == BossState.STAGGER || s == BossState.EXIT_STUMBLE || s == BossState.QTE_BROKEN;
        float room = Math.max(0, getMaxHealth() * PaponConfig.BURST_CAP.get().floatValue() * (open ? 2.5F : 1F) - burst);
        if (amount > room) {
            capHits += 1;
            composure(attacker);
            amount = room;
            if (amount < 0.05F) return false;
        }
        if (attacker instanceof Player p && p.distanceTo(this) < 4.5 && level() instanceof ServerLevel level && pressed(p, s)) {
            setBossState(BossState.SALSA_BURST);
            saltBurstCd = isPhaseTwo() ? 120 : 180;
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            recentHurt += amount / getMaxHealth();
            burst += amount;
        }
        if (hurt && attacker instanceof Player && crowdBooCd <= 0 && amount > getMaxHealth() * 0.008F && level() instanceof ServerLevel level) {
            crowdBooCd = 70;
            crowdReact(level, CrowdMood.BOO, 45);
            quip("hit" + random.nextInt(2), 0.2F);
        }
        if (hurt && !isPhaseTwo() && !phaseDue && getHealth() <= getMaxHealth() * PHASE_TWO_AT && getHealth() > 0) phaseDue = true;
        return hurt;
    }

    /** No knockback while he cannot be stopped (he has full resistance anyway; this is for the mods that ignore it). */
    @Override
    public void knockback(double strength, double x, double z) {
        if (!getBossState().unstoppable() && !getBossState().qte()) super.knockback(strength, x, z);
    }

    public boolean isGuarding() {
        return getBossState() == BossState.GUARD;
    }

    /** Arrows into the comal's face fly back where they came from. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
    public static final class Deflect {
        private Deflect() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
            if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)
                    || !(hit.getEntity() instanceof PaponBossEntity boss) || !boss.isGuarding()) return;
            var proj = event.getProjectile();
            Vec3 to = proj.position().subtract(boss.position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(Vec3.directionFromRotation(0, boss.yBodyRot)) < 0.25) return;
            event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            proj.setDeltaMovement(proj.getDeltaMovement().scale(-0.9));
            proj.setOwner(boss);
            proj.hasImpulse = true;
            boss.guardBlocks++;
            boss.level().playSound(null, boss.getX(), boss.getY() + 1.4, boss.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.0F, 1.8F);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (getBossState() == BossState.DEATH) return;
        release();
        stopRiding();
        if (level() instanceof ServerLevel level) {
            discardFoils(level);
            crowdReact(level, CrowdMood.GASP, 30);
            crowdMood(level, CrowdMood.MOURN);
        }
        setFlying(false);
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
                Fx.fall(level, position().add(0, getBbHeight() * 0.7, 0), 2, 0.3, 0.1F, 0x7FB8FF);
            }
            if (deathTime == 60) for (MariachiEntity m : band(level)) m.bow();
            if (deathTime >= BossState.DEATH.duration) {
                List<ServerPlayer> fighters = new ArrayList<>(bossEvent.getPlayers());
                for (ServerPlayer p : level.players()) {
                    if (!fighters.contains(p) && !p.isSpectator() && p.distanceToSqr(this) < 64 * 64) fighters.add(p);
                }
                leaveArena(level);
                com.groid.papon.entity.npc.GerbinEntity.reward(level, home != null ? home : position(), fighters);
                Fx.flash(level, position().add(0, 1, 0), 2.5F);
                Fx.smoke(level, position().add(0, 1, 0), 6, 0.8, 1.4F, 0xEDE6DA);
                Fx.chunks(level, position().add(0, 1, 0), 16, 0.35F, Fx.POTATO, 0.6);
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
        if (reason.shouldDestroy() && level() instanceof ServerLevel level) {
            leaveArena(level);
            discardFoils(level);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animate).setSoundKeyframeHandler(this::onSoundCue));
        // after "main", so where both key a bone this one wins: it shows the phase-two parts main pins away
        controllers.add(new AnimationController<>(this, "form", 4, this::animateForm).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animateForm(AnimationState<PaponBossEntity> event) {
        BossState s = getBossState();
        if (s == BossState.P2_SCREAM) return event.setAndContinue(F_SPROUT);
        if (s == BossState.P2_APOLOGY && ticksInState() > beat("apology.aura", 100) || s == BossState.P2_HYPE) return event.setAndContinue(F_AURA);
        if (!isRisen()) return PlayState.STOP;
        RawAnimation next = switch (s) {
            case VAN_GRAB, VAN_THROW, P2_METEOR -> F_LIFT;
            case P2_FRY_VOLLEY, P2_GRAVITY -> F_LIFT;
            case SWIPE, SWIPE_OVERHEAD, DASH_COMBO, P2_BARRAGE, UPPERCUT, AIR_SPIKE, BLINK, CHARGE, CHARGE_WINDUP, BREATH, GATLING,
                 THROW, LEAP, LEAP_LAND, VAN_KICK, BAILOUT, GRAB, GRAB_COMBO, GUARD, RIPOSTE, EVADE, CLEAVER_TOSS, BURRITO_TOSS,
                 DRUNK_SWIG, DRUNK_FLURRY, HAYMAKER, COMAL_SLAM, QTE_GRAB, QTE_HOLD, QTE_FAIL, P2_MASH, P2_TOT, P2_SNARE -> F_ATTACK;
            default -> isFlying() || s == BossState.P2_DIVE || s == BossState.P2_ARRIVE ? F_FLY : F_IDLE;
        };
        return event.setAndContinue(next);
    }

    private PlayState animate(AnimationState<PaponBossEntity> event) {
        RawAnimation current = event.getController().getCurrentRawAnimation();
        BossState st = getBossState();
        // the QTE is slow motion: he moves at a quarter speed while you press
        event.getController().setAnimationSpeed(st == BossState.QTE_HOLD ? 0.25 : st == BossState.BLINK && isPhaseTwo() ? 1.333 : 1.0);
        RawAnimation next = switch (getBossState()) {
            case LEAP_OUT -> LEAP_OUT;
            case CHUG -> CHUG;
            case ROAR -> ROAR;
            case IDLE -> isFlying() ? A_FLY
                    : event.isMoving() ? (isPhaseTwo() && getDeltaMovement().horizontalDistanceSqr() > 0.02 ? RUN : WALK) : IDLE;
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
            case DASH_COMBO -> A_DASH;
            case PUNT_RUN -> event.isMoving() ? RUN : IDLE;
            case VAN_KICK -> A_VAN_KICK;
            case BAILOUT -> LEAP_OUT;
            case P2_KNEEL -> A_P2_KNEEL;
            case P2_TALK -> A_P2_TALK;
            case P2_SEASON -> A_P2_SEASON;
            case P2_SCREAM -> A_P2_SCREAM;
            case P2_ARRIVE -> inColiseum() ? A_P2_ARRIVE_SKY : A_P2_ARRIVE;
            case P2_NOTICE -> A_P2_NOTICE;
            case P2_MARCH -> event.isMoving() ? A_P2_MARCH : A_P2_WAIT;
            case P2_WAKE -> A_P2_WAKE;
            case P2_WAIT -> event.isMoving() ? A_P2_MARCH : A_P2_WAIT;
            case P2_KICK -> A_P2_KICK;
            case P2_COUNT -> A_P2_COUNT;
            case P2_SHOWTIME -> A_P2_GROOVE;
            case P2_APOLOGY -> A_P2_APOLOGY;
            case P2_RESUME -> A_P2_RESUME;
            case P2_HYPE -> A_P2_HYPE;
            case P2_SOLO -> A_P2_SOLO;
            case P2_BARRAGE -> A_BARRAGE;
            case P2_METEOR -> A_METEOR;
            case VAN_GRAB -> A_FLY;
            case VAN_THROW -> A_VAN_THROW;
            case P2_DIVE -> isFlying() ? A_FLY : A_P2_DIVE;
            case GRAB -> A_GRAB;
            case GRAB_COMBO -> A_GRAB_COMBO;
            case GUARD -> A_GUARD;
            case RIPOSTE -> A_RIPOSTE;
            case SALSA_BURST -> A_SALSA_BURST;
            case ARENA_FRYER -> A_ARENA_FRYER;
            case ARENA_HECKLE -> A_ARENA_HECKLE;
            case P2_MASHER -> A_P2_MASHER;
            case EVADE -> A_EVADE;
            case CLEAVER_TOSS -> A_CLEAVER_TOSS;
            case BURRITO_TOSS -> A_BURRITO_TOSS;
            case DRUNK_SWIG -> A_DRUNK_SWIG;
            case DRUNK_FLURRY -> A_DRUNK_FLURRY;
            case HAYMAKER -> A_HAYMAKER;
            case COMAL_SLAM -> A_COMAL_SLAM;
            case BRICK -> A_BRICK;
            case VAN_HOP -> event.isMoving() ? RUN : A_LEAP;
            case QTE_GRAB -> A_GRAB;
            case QTE_HOLD -> A_QTE_HOLD[qteBase()];
            case QTE_FAIL -> A_QTE_FAIL[qteBase()];
            case QTE_BROKEN -> A_QTE_BROKEN;
            case P2_FRY_VOLLEY -> A_FRY_VOLLEY;
            case P2_MASH -> A_MASH;
            case P2_TOT -> A_TOT;
            case P2_SNARE -> A_STOMP;
            case P2_GRAVITY -> A_GRAVITY;
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
        tag.putInt("Form", form());
        if (arena != null) tag.put("Arena", arena.save());
        tag.putBoolean("BandWoken", bandWoken);
        tag.putInt("SongStart", songStart);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(BlockPos.containing(home)));
        if (portalAt != null) {
            tag.putDouble("PortalX", portalAt.x);
            tag.putDouble("PortalY", portalAt.y);
            tag.putDouble("PortalZ", portalAt.z);
            tag.putFloat("PortalYaw", portalYaw);
        }
        if (vanId != null) tag.putUUID("Van", vanId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_PHASE2, tag.getBoolean("Phase2"));
        entityData.set(DATA_FORM, tag.getInt("Form"));
        arena = tag.contains("Arena") ? Coliseum.load(tag.getCompound("Arena")) : null;
        entityData.set(DATA_ARENA, arena != null);
        oldDome = tag.contains("Dome") ? tag.getCompound("Dome") : null;
        bandWoken = tag.getBoolean("BandWoken");
        songStart = tag.getInt("SongStart");
        if (isRisen()) foilCd = 40;
        if (isPhaseTwo()) bossEvent.setName(Component.translatable(BAR_KEY + ".phase2"));
        if (tag.contains("Home")) home = Vec3.atBottomCenterOf(NbtUtils.readBlockPos(tag.getCompound("Home")));
        if (tag.contains("PortalX")) {
            portalAt = new Vec3(tag.getDouble("PortalX"), tag.getDouble("PortalY"), tag.getDouble("PortalZ"));
            portalYaw = tag.getFloat("PortalYaw");
        }
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
