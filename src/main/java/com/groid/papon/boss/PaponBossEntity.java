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
import com.groid.papon.registry.ModBlocks;
import com.groid.papon.registry.ModSounds;
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
import net.minecraft.world.level.block.Block;
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
    /** Phase three (half health, "Para Siempre"), from the band's drop on. */
    private static final EntityDataAccessor<Boolean> DATA_PHASE3 =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** How dark his coliseum is (phase three): 0 day .. 1 black. */
    private static final EntityDataAccessor<Float> DATA_NIGHT =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.FLOAT);
    /** The spotlight on him (phase three). */
    private static final EntityDataAccessor<Boolean> DATA_SPOT =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** Back from the band stand with the guitarist's guitar (the duel's fetch): the flight with it slung. */
    private static final EntityDataAccessor<Boolean> DATA_GUITAR =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** The server's ticks in the current state: the client's clock follows it, so a cutscene's shots stay on the server's
     *  beats however the server is running (a lagging server, a late packet). */
    private static final EntityDataAccessor<Integer> DATA_TICKS =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** How a running squeeze or duel is going for whoever is in it (0..1, from their client): his grip, their struggle. */
    private static final EntityDataAccessor<Float> DATA_METER =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.FLOAT);
    /** Mid-move and untouchable (a golden shimmer): hits clang off until the move is done. See {@link #armored}. */
    private static final EntityDataAccessor<Boolean> DATA_ARMOR =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** Who the camera cuts to in P2_CUTAWAY (a Cutaway's id). */
    private static final EntityDataAccessor<Integer> DATA_CUTAWAY =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);

    /** Phase four (a quarter health, Dia de los Muertos), from the band of the dead striking up on. */
    private static final EntityDataAccessor<Boolean> DATA_PHASE4 =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.BOOLEAN);
    /** The moon over his fight: 0 not shown, 1 the potato moon, 2 the blue calavera (phase four). */
    private static final EntityDataAccessor<Integer> DATA_MOON =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** The fog over his phase-four awakening (0..1, client/ClientEvents thickens the fog). */
    private static final EntityDataAccessor<Float> DATA_FOG =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.FLOAT);
    /** How much he glows with the souls he took (0..1: the green aura, client/BossRenderer). */
    private static final EntityDataAccessor<Float> DATA_GLOW =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.FLOAT);
    /** What he holds out of thin air in phase four: 0 nothing, 1 the spirit hammer, 2 the spirit guitar, 3 his bow, 4 Ignis's head. */
    private static final EntityDataAccessor<Integer> DATA_P4PROP =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** An angle for the move he is doing (the petal spirals start from it). */
    private static final EntityDataAccessor<Float> DATA_P4ANGLE =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.FLOAT);
    /** The seed of the grand move he is doing (boss/Fiesta: the dance floor's patterns, the marigold path, the castillo's sparks). */
    private static final EntityDataAccessor<Integer> DATA_P4SEED =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);
    /** His arena as the clients need it for the grand moves: half-axes a, b and the floor's height. */
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_FIELD =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.VECTOR3);
    /** The middle of his arena (the sand's floor), for the clients: the moon, the grand moves and the throne hang off it. */
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_CENTER =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.VECTOR3);
    /** Phase four's stage reached (1 the guardian, 2 the storm, 3 the end of the world), for the clients' sky and aura. */
    private static final EntityDataAccessor<Integer> DATA_P4STAGE =
            SynchedEntityData.defineId(PaponBossEntity.class, EntityDataSerializers.INT);

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
    // phase three's cutscene (gen_boss_anims b_p3_*)
    private static final RawAnimation A_P3_KNOCKBACK = once("p3_knockback");
    private static final RawAnimation A_P3_GLARE = once("p3_glare");
    private static final RawAnimation A_P3_GLARE_HOLD = loop("p3_glare_hold");
    private static final RawAnimation A_P3_STALK = loop("p3_stalk");
    private static final RawAnimation A_P3_SNATCH = once("p3_snatch");
    private static final RawAnimation A_P3_CARRY = loop("p3_carry");
    private static final RawAnimation A_P3_CARRY_HOLD = loop("p3_carry_hold");
    private static final RawAnimation A_P3_BLACKOUT = once("p3_blackout");
    private static final RawAnimation A_P3_SERENADE = once("p3_serenade");
    private static final RawAnimation A_P3_VERDICT = once("p3_verdict");
    private static final RawAnimation A_P3_TOSS = once("p3_toss");
    private static final RawAnimation A_P3_DANCE = once("p3_dance");
    private static final RawAnimation A_P3_FELT = once("p3_felt");
    private static final RawAnimation A_P3_CRUSH = once("p3_crush");
    private static final RawAnimation A_P3_SHOWCAR = once("p3_showcar");
    private static final RawAnimation A_P3_GO = once("p3_go");
    private static final RawAnimation A_P3_ASCEND = once("p3_ascend");
    private static final RawAnimation A_P3_POWER = once("p3_power");
    private static final RawAnimation A_WINDED = once("winded");
    private static final RawAnimation A_A_SOMBRERO = once("a_sombrero");
    private static final RawAnimation A_A_VOLCAN = once("a_volcan");
    private static final RawAnimation A_A_CALAVERAS = once("a_calaveras");
    private static final RawAnimation A_A_PINATA_SWING = once("a_pinata_swing");
    // Ascended Papon's moves (gen_boss_anims b_a_*)
    private static final RawAnimation A_A_COMBO = once("a_combo");
    private static final RawAnimation A_A_FLASH = loop("a_flash");
    private static final RawAnimation A_A_COMET = loop("a_comet");
    private static final RawAnimation A_A_COMET_LAND = once("a_comet_land");
    private static final RawAnimation A_A_LASER = once("a_laser");
    private static final RawAnimation A_A_SOLAR = once("a_solar");
    private static final RawAnimation A_A_CONDUCT = once("a_conduct");
    private static final RawAnimation A_A_DUEL_FLY = loop("a_duel_fly");
    private static final RawAnimation A_A_SHRED = loop("a_shred");
    private static final RawAnimation A_A_DUEL_SMASH = once("a_duel_smash");
    private static final RawAnimation A_A_DUEL_SHAME = once("a_duel_shame");
    private static final RawAnimation A_A_PINATA = once("a_pinata");
    private static final RawAnimation A_A_BRAZIERS = once("a_braziers");
    private static final RawAnimation A_A_NOVA = once("a_nova");
    private static final RawAnimation A_A_SPENT = once("a_spent");
    private static final RawAnimation A_A_FLY = loop("a_fly");
    private static final RawAnimation F_WINGS_SPROUT = RawAnimation.begin().thenPlay(ANIM + "a_wings_sprout").thenLoop(ANIM + "a_wings_idle");
    // Ascended Papon's wings (the "form" controller)
    private static final RawAnimation F_WINGS_UNFURL = RawAnimation.begin().thenPlay(ANIM + "a_wings_unfurl").thenLoop(ANIM + "a_wings_idle");
    private static final RawAnimation F_WINGS_IDLE = loop("a_wings_idle");
    private static final RawAnimation F_WINGS_FLY = loop("a_wings_fly");
    // phase four (gen_boss_anims b_p4_*, b_d_*)
    // phase four's awakening (gen_boss_anims PHASE_FOUR)
    private static final RawAnimation A_P4_FALL = once("p4_fall");
    private static final RawAnimation A_P4_RISEUP = once("p4_riseup");
    private static final RawAnimation A_P4_IGNIS = once("p4_ignis");
    private static final RawAnimation A_P4_PROPHECY = once("p4_prophecy");
    private static final RawAnimation A_P4_BRETHREN = once("p4_brethren");
    private static final RawAnimation A_P4_SOULS = loop("p4_souls");
    private static final RawAnimation A_P4_HALFBAKED = once("p4_halfbaked");
    private static final RawAnimation A_P4_BOW = once("p4_bow");
    private static final RawAnimation A_P4_WATCH = loop("p4_watch");
    private static final RawAnimation A_P4_TALKING = once("p4_talking");
    private static final RawAnimation A_P4_WALK = loop("p4_walk");
    private static final RawAnimation A_P4_HONOR = once("p4_honor");
    private static final RawAnimation A_P4_MUCHACHAS = once("p4_muchachas");
    private static final RawAnimation A_P4_GOODTIME = once("p4_goodtime");
    private static final RawAnimation A_D_SERPIENTES = once("d_serpientes");
    private static final RawAnimation A_D_FLECHAS = once("d_flechas");
    private static final RawAnimation A_D_FLY = loop("d_fly");
    private static final RawAnimation A_D_MARTILLO = once("d_martillo");
    private static final RawAnimation A_D_GIGANTE = loop("d_gigante");
    private static final RawAnimation A_D_GRIETA = once("d_grieta");
    private static final RawAnimation A_D_PETALOS = loop("d_petalos");
    private static final RawAnimation A_D_CONDUCT = loop("d_conduct");
    private static final RawAnimation A_D_DANZA = loop("d_danza");
    private static final RawAnimation A_D_LUNA = loop("d_luna");
    private static final RawAnimation A_P4_THRONE = once("p4_throne");
    private static final RawAnimation A_D_OUROBOROS = loop("d_ouroboros");
    private static final RawAnimation A_D_SALSA = loop("d_salsa");
    private static final RawAnimation A_D_CAMINO = loop("d_camino");
    private static final RawAnimation A_D_CASTILLO = loop("d_castillo");
    private static final RawAnimation A_D_TANGO = loop("d_tango");
    private static final RawAnimation A_D_TANGO_WIN = once("d_tango_win");
    private static final RawAnimation A_D_TANGO_LOSE = once("d_tango_lose");
    private static final RawAnimation A_D_RAGE = once("d_rage");
    private static final RawAnimation A_D_JUICIO = loop("d_juicio");
    private static final RawAnimation F_BONE_UNFURL = RawAnimation.begin().thenPlay(ANIM + "a_wings_unfurl").thenLoop(ANIM + "a_wings_idle");
    private static final RawAnimation F_WINGS_FOLDED = RawAnimation.begin().thenLoop(ANIM + "a_wings_folded");
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
    private static final RawAnimation A_ARENA_OLA = once("arena_ola");
    private static final RawAnimation A_ARENA_STAMPEDE = once("arena_stampede");
    private static final RawAnimation A_P2_MASHER = once("p2_masher");
    private static final RawAnimation A_EVADE = once("evade");
    private static final RawAnimation A_CLEAVER_TOSS = once("cleaver_toss");
    private static final RawAnimation A_BURRITO_TOSS = once("burrito_toss");
    private static final RawAnimation A_DRUNK_SWIG = once("drunk_swig");
    private static final RawAnimation A_DRUNK_FLURRY = once("drunk_flurry");
    private static final RawAnimation A_HAYMAKER = once("haymaker");
    private static final RawAnimation A_COMAL_SLAM = once("comal_slam");
    private static final RawAnimation A_BRICK = once("brick");
    private static final RawAnimation[] A_QTE_HOLD = {loop("qte_hold_cleaver"), loop("qte_hold_bottle"), loop("qte_hold_suplex"), loop("qte_hold_crush")};
    private static final RawAnimation[] A_QTE_FAIL = {once("qte_fail_cleaver"), once("qte_fail_bottle"), once("qte_fail_suplex"), once("qte_fail_crush")};
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
    /** ...and phase three ("Para Siempre"). */
    private static final float PHASE_THREE_AT = 0.5F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable(BAR_KEY),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private int stateTicks;
    private int speechTicks;
    private static final String VOICE2 = "speech.papon.boss2.";
    /** Ticks before he may make another remark in the fight (see quip). */
    private int quipCd;
    /** tickCount from which he may start a fight line again (the last one is over, plus a breath). */
    private int voiceFree, voiceEnd;
    /** Client: the phase-two line he is saying (lip sync) and the tick it started. */
    @Nullable private VoiceLines.Line talk;
    private int talkStart;
    @Nullable private Vec3 home;
    /** Where the portal back into his coliseum opens (the truck window), and the way it faces; its entity. */
    @Nullable private Vec3 portalAt;
    /** The Taco Truck he came out of (null for /papon boss): beaten, he goes back to work there, friendly. */
    @Nullable private com.groid.papon.journey.TacoLine truck;
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
    /** When he last went for the van in any way; phase two waits {@link #VAN_GAP} between van moves. */
    private int lastVanMove = -10000;
    private static final int VAN_GAP = 500;
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
    /** The song the band plays (Song.CANCION, from phase three Song.PARA_SIEMPRE). */
    private int songId;
    // phase three: the hit that knocked him down came from here; his flight back (start, touchdown, end of the skid)
    private boolean phaseThreeDue;
    @Nullable private Vec3 p3From;
    @Nullable private Vec3 p3Start, p3Land, p3End;
    private float p3Yaw;
    /** The coliseum's braziers round the bowl (phase three puts them out in this order) and how many are out. */
    private List<BlockPos> braziers = new ArrayList<>();
    private int braziersOut;
    /** How far he had to walk back to the middle with the guitar (the lights go out as he covers it). */
    private double returnFrom;
    /** The mariachi whose chair he is off to kick (entity id), or -1. */
    private int kickWho = -1;
    /** Waiting on the band, he paces the front of the stand: which end he is headed for. */
    private int paceSide = 1;
    /** A guitar solo has started (a client heard it): ticks left to stop and listen; the cooldown after. */
    private int soloDue;
    private int soloCd = 400;
    /** A cut to the band asked for (P2_CUTAWAY: which, and how long it may still wait), and the gap before another. */
    private int cutawayDue, cutawayKind;
    private int cutawayCd = 300;
    private final int[] cutawayLast = new int[Cutaway.values().length];
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
    /** ¡La Ola! and ¡Estampida!: their cooldowns, and how far into the waves / the volleys they are (-1: none running). */
    private int olaCd = 260, stampedeCd = 340, olaT = -1, stampedeT = -1;
    private final int[][] olaWaves = new int[3][];
    /** Each wave's one gap (across it from the middle) and the wall as drawn (BossProp SALSA_WAVE). */
    private final double[] olaGap = new double[3];
    private final UUID[] olaProps = new UUID[3];
    private final java.util.Set<Integer> olaHit = new java.util.HashSet<>();
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
        entityData.define(DATA_PHASE3, false);
        entityData.define(DATA_NIGHT, 0F);
        entityData.define(DATA_SPOT, false);
        entityData.define(DATA_GUITAR, false);
        entityData.define(DATA_CUTAWAY, 0);
        entityData.define(DATA_TICKS, 0);
        entityData.define(DATA_METER, 0F);
        entityData.define(DATA_ARMOR, false);
        entityData.define(DATA_PHASE4, false);
        entityData.define(DATA_MOON, 0);
        entityData.define(DATA_P4PROP, 0);
        entityData.define(DATA_FOG, 0F);
        entityData.define(DATA_GLOW, 0F);
        entityData.define(DATA_P4ANGLE, 0F);
        entityData.define(DATA_P4SEED, 0);
        entityData.define(DATA_FIELD, new org.joml.Vector3f(30, 22, 0));
        entityData.define(DATA_CENTER, new org.joml.Vector3f());
        entityData.define(DATA_P4STAGE, 0);
    }

    /** Client: the held player's meter in a squeeze or duel (0..1). */
    public float qteMeterValue() { return entityData.get(DATA_METER); }

    /** Untouchable right now (the shimmer). */
    public boolean isArmored() { return entityData.get(DATA_ARMOR); }

    public int qteKind() { return entityData.get(DATA_QTE); }

    /**
     * The hold the QTE uses: 0 La Última Cena (the cleaver), 1 Last Call (the bottle), 2 La Papa Suplex. The
     * newer ones borrow them: 3 ¡Apaga el Fuego! (mash a key while he pours hot sauce, the bottle's hold) and
     * 4 El Tango del Papa (arrow keys on the song's beat while he dances you round, the suplex's).
     */
    public int qteBase() {
        int k = qteKind();
        return k == 3 ? 1 : k == 4 ? 2 : k == 5 ? 3 : Mth.clamp(k, 0, 2);
    }

    /** The entity id of whoever he holds, or -1. */
    public int heldId() { return entityData.get(DATA_HELD); }

    public int form() { return entityData.get(DATA_FORM); }

    /** Baked golden (the texture swap). */
    public boolean isBaked() { return form() >= 1; }

    /** Fully risen: fry wings, crown, butter; he flies. */
    public boolean isRisen() { return form() >= 2; }

    public boolean isFlying() { return entityData.get(DATA_FLYING); }

    private void setFlying(boolean f) {
        entityData.set(DATA_FLYING, f);
        setNoGravity(f);
    }

    @Nullable
    public Coliseum arena() { return arena; }

    /** In his coliseum (both sides: the band plays the music there, not the old score). */
    public boolean inColiseum() { return entityData.get(DATA_ARENA); }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        var d = super.getDimensions(pose);
        return isMuertos() ? d.scale(1.45F) : isAscended() ? d.scale(1.35F) : isRisen() ? d.scale(1.15F) : d;
    }

    public boolean isGhost() { return entityData.get(DATA_GHOST); }

    private void setGhost(boolean g) {
        entityData.set(DATA_GHOST, g);
        noPhysics = g;
    }

    @Override
    protected void registerGoals() {
        // all of it is in customServerAiStep: a state machine, not goals
    }

    // ------------------------------------------------------------------ state

    public BossState getBossState() { return BossState.byId(entityData.get(DATA_STATE)); }

    public void setBossState(BossState s) {
        BossState old = getBossState();
        if (WanderingDebug.on() && s != old) {
            PaponMod.LOG.info("[debug] boss {} -> {} (hp {}/{})", old, s, (int) getHealth(), (int) getMaxHealth());
        }
        if (s == BossState.IDLE && attack(old)) {
            misses = swingHit ? 0 : misses + 1;
            if (misses == 3) retort("miss", 500, 0.7F, "miss0", "miss1");
            // a breath between attacks (none in a frenzy: see choose); in phase two none after one that landed: he presses
            think = isPhaseTwo() ? (swingHit ? 0 : 2) : 4;
        }
        if (s == BossState.IDLE && winds(old) && !phaseThreeDue && !phaseDue && !phaseFourDue && isAlive()) {
            // a big move he channelled (untouchable through it) leaves him winded: the window to hit him back
            s = BossState.WINDED;
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
        if (old != s && level() instanceof ServerLevel sl) {
            leaveAscended(sl, old, s);
            leaveMuertos(sl, old, s);
        }
        entityData.set(DATA_STATE, s.ordinal());
        entityData.set(DATA_TICKS, 0);
        stateTicks = 0;
        getNavigation().stop();
    }

    /**
     * The windows (the user, 2026-10-04: "set windows where he is invincible and some where he can take damage"): while he
     * channels one of his big moves (the arena turning on you, the beams, the sun, the band, the supernova, the sombreros,
     * the volcano, the skulls, the piñata, high in the sky for a comet or a dive, the charge) nothing touches him: a golden
     * shimmer, and hits clang off. After it he is {@link BossState#WINDED}: down on the sand, panting, taking half again.
     * Between the rest of his attacks he can always be hit.
     */
    static boolean armored(BossState s, int t) {
        return switch (s) {
            case ARENA_FRYER, ARENA_HECKLE, ARENA_OLA, ARENA_STAMPEDE, P2_MASHER, P2_GRAVITY, P2_METEOR, CHARGE,
                 P3_LASER, P3_SOLAR, P3_MARIACHI, P3_BRAZIERS, P3_NOVA, P3_COMET, P3_FLASH, P3_SOMBRERO, P3_VOLCAN, P3_CALAVERAS,
                 P3_PINATA_SWING, P3_PINATA, P4_GIGANTE, P4_GRIETA, P4_PETALOS, P4_PAPEL, P4_VELAS, P4_DANZA, P4_LUNA, P4_OUROBOROS, P4_PISTA,
                 P4_CEMPASUCHIL, P4_CASTILLO, P4_TANGO, P4_ALIENTO, P4_FUSION, P4_RAGE, P4_JUICIO -> true;
            default -> false;
        };
    }

    /** The channelled moves that leave him winded when they are done. */
    private static boolean winds(BossState s) {
        return switch (s) {
            case ARENA_FRYER, ARENA_OLA, ARENA_STAMPEDE, P2_MASHER, P2_GRAVITY, P2_METEOR, P3_LASER, P3_SOLAR, P3_MARIACHI,
                 P3_BRAZIERS, P3_COMET_LAND, P3_SOMBRERO, P3_VOLCAN, P3_CALAVERAS, P3_PINATA_SWING, P3_PINATA, P4_GIGANTE, P4_GRIETA, P4_PETALOS,
                 P4_PAPEL, P4_VELAS, P4_DANZA, P4_LUNA, P4_OUROBOROS, P4_PISTA, P4_CEMPASUCHIL, P4_CASTILLO, P4_ALIENTO, P4_FUSION, P4_JUICIO -> true;
            default -> false;
        };
    }

    /** Winded (2.5 s): down out of the air, hands on his knees, heaving, steaming; open, and he takes half again. */
    private void tickWinded(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (target != null) turnToward(target.position(), 3F);
        if (t % 12 == 2) {
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.HORSE_BREATHE, SoundSource.HOSTILE, 1.3F, 0.5F + random.nextFloat() * 0.1F);
            Fx.smoke(level, position().add(0, getBbHeight() * 0.85, 0), 2, 0.35, 0.9F, 0xF2EEE8);
        }
        if (t == 6 && random.nextInt(3) == 0) quip("hit" + random.nextInt(2), 1F);
        if (t >= BossState.WINDED.duration + juicioWinded) {
            juicioWinded = 0;                // (El Juicio Final leaves him spent for longer)
            think = 2;
            setBossState(BossState.IDLE);
        }
    }

    /** Whatever an Ascended move left lying about when it ends or is cut short: beams, the daylight, the guitar, noPhysics. */
    private void leaveAscended(ServerLevel level, BossState old, BossState s) {
        if (!beams.isEmpty() && s != BossState.P3_LASER && s != BossState.P3_SOLAR) clearBeams(level);
        if (old == BossState.P3_SOLAR && isPhaseThree() && inColiseum()) entityData.set(DATA_NIGHT, NIGHT_P3);
        if (old == BossState.P3_SOLAR && s != old) clearMonoliths(level);
        if (old == BossState.P3_FLASH || old == BossState.P3_COMET || old == BossState.P3_COMBO || old == BossState.P3_PINATA
                || old == BossState.P3_VOLCAN || old == BossState.P3_PINATA_SWING) noPhysics = false;
        boolean duel = s == BossState.P3_DUEL_FETCH || s == BossState.P3_DUEL || s == BossState.P3_DUEL_SMASH || s == BossState.P3_DUEL_SHAME;
        if (old == BossState.P3_DUEL && duelResult < 0 && duelWho != null && level.getEntity(duelWho) instanceof ServerPlayer sp) {
            PaponNetwork.qteEnd(sp, false);
        }
        if (!duel && duelGuitarist >= 0) giveGuitarBack(level, false);
        if ((old == BossState.P3_PINATA || old == BossState.P3_PINATA_SWING) && s != old && spudId != null) {
            Entity p = level.getEntity(spudId);
            if (p instanceof BossProp bp && bp.kind() == BossProp.Kind.PINATA) bp.discard();
        }
    }

    /** States he can keep flying through in phase two (the rest bring him down to the ground). */
    private static boolean airborne(BossState s) {
        return switch (s) {
            case IDLE, GATLING, THROW, P2_METEOR, P2_DIVE, VAN_GRAB, VAN_THROW, P2_ARRIVE, P2_FRY_VOLLEY, P2_TOT, P2_GRAVITY,
                 P3_COMBO, P3_FLASH, P3_COMET, P3_LASER, P3_SOLAR, P3_MARIACHI, P3_DUEL_FETCH, P3_PINATA, P3_BRAZIERS, P3_NOVA, P3_NITRO,
                 P3_SOMBRERO, P3_VOLCAN, P3_CALAVERAS, P3_PINATA_SWING, P4_SOULS, P4_HALFBAKED,
                 P4_GIGANTE, P4_GRIETA, P4_PETALOS, P4_PAPEL, P4_VELAS, P4_DANZA, P4_LUNA, P4_MARTILLO, P4_OUROBOROS, P4_PISTA, P4_CEMPASUCHIL,
                 P4_CASTILLO, P4_TANGO, P4_ALIENTO, P4_FUSION -> true;
            default -> false;
        };
    }

    /** Ticks in the current state, on either side. */
    public int ticksInState() { return level().isClientSide ? clientStateTicks : stateTicks; }

    public boolean isPhaseTwo() { return entityData.get(DATA_PHASE2); }

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

    public void setTruck(com.groid.papon.journey.TacoLine line) { truck = line; }

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
            if (DATA_TICKS.equals(key)) {
                // the server's clock: follow it when ours has drifted (a lagging server runs the beats slower)
                int server = entityData.get(DATA_TICKS);
                if (Math.abs(server - clientStateTicks) > 2) clientStateTicks = server;
            }
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
    public String getSpeech() { return entityData.get(DATA_SPEECH); }

    @Override
    public void setSpeech(String encoded, int ticks) {
        entityData.set(DATA_SPEECH, encoded, true);      // forced: the same line again restarts the lip sync
        speechTicks = ticks;
    }

    private void say(String line, int ticks) { speak(line, ticks, true); }

    /** A line with no translation under it (it is already in English, or a scream). */
    private void sayPlain(String line, int ticks) { speak(line, ticks, false); }

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
        // in a QTE he talks to you as it goes: a line only has to wait for the last to finish, not to settle
        boolean qte = st.qte() || st == BossState.P3_DUEL;
        if (!scripted && tickCount < (qte ? voiceEnd : voiceFree)) return;
        voiceFree = tickCount + (v != null ? v.ticks() : ticks) + 30;
        voiceEnd = tickCount + (v != null ? v.ticks() : ticks) + 4;
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
        if (muertosFight() && line.startsWith("hit")) line = "p4_hit" + random.nextInt(5);
        else if (muertosFight() && line.startsWith("charm")) line = "p4_charm" + random.nextInt(12);
        else if (ascendedFight() && (line.startsWith("hit") || line.startsWith("landed") || line.startsWith("charm"))) line = "p3_" + line;
        say(line, 40);
    }

    /**
     * What you do, he answers (any phase): steal his van, run him over, honk at him, hide behind a shield, plink at him
     * from afar, have a snack, eat one of his tacos, pop a totem, fly up out of reach, run off, make him miss, nearly
     * die, die. Each thing has its own pool of lines; he never says the same one twice running, waits a while before
     * the same reaction again ({@code cooldown} ticks) and a few seconds between any two, and drops it if he is busy
     * talking (an important one, {@code force}, still waits only for his voice to be free).
     */
    private boolean retort(String kind, int cooldown, float chance, String... lines) {
        BossState st = getBossState();
        if (lines.length == 0 || st.cutscene() || st.intro() || st.qte() || st == BossState.DEATH || st == BossState.PHASE) return false;
        if (retortCd > 0 || speechTicks > 0 || tickCount < voiceFree || random.nextFloat() >= chance) return false;
        Integer next = retortAt.get(kind);
        if (next != null && tickCount < next) return false;
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (String l : lines) if (!l.equals(lastRetort) && VoiceLines.line(l) != null) pool.add(l);
        if (pool.isEmpty()) for (String l : lines) if (VoiceLines.line(l) != null) pool.add(l);
        if (pool.isEmpty()) return false;
        String line = pool.get(random.nextInt(pool.size()));
        lastRetort = line;
        retortAt.put(kind, tickCount + cooldown);
        retortCd = 100;
        say(line, 50);
        return true;
    }

    private final java.util.Map<String, Integer> retortAt = new java.util.HashMap<>();
    private String lastRetort = "";
    private int retortCd = 160;
    /** The reactions' watchers: who was driving his car, who was using what, who was down, where you were. */
    @Nullable private UUID carThief;
    private final java.util.Map<UUID, Integer> usingSince = new java.util.HashMap<>();
    private final java.util.Set<UUID> downed = new java.util.HashSet<>();
    private int kiteTicks, highTicks;

    /** Someone at the wheel of his van, or his Skyline. */
    private void retortStolen(boolean skyline) {
        if (skyline) retort("stolen", 400, 1F, "sky_stolen0", "sky_stolen1");
        else retort("stolen", 400, 1F, "stolen", "stolen1", "stolen2", "stolen3");
    }

    /** A player honked his own horn at him. */
    public void heardHonk(Player p) { if (p.distanceTo(this) < 40) retort("honk", 300, 0.8F, "honk0", "honk1"); }

    /** A player's totem saved them. */
    public void sawTotem(Player p) { if (p.distanceTo(this) < 48) retort("totem", 600, 1F, "totem"); }

    /**
     * Every second, what the players in his fight are up to: in his car, eating (his tacos!), drinking a potion,
     * dying or nearly, up out of reach, running off.
     */
    private void watchPlayers(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        Entity driver = van != null ? van.getControllingPassenger() : null;
        UUID thief = driver instanceof Player p ? p.getUUID() : null;
        if (thief != null && !thief.equals(carThief)) {
            retortStolen(van.isSkyline());
            stolenLine = true;
        }
        if (thief == null && carThief != null && van == null) retort("vangone", 600, 0.8F, "vangone");     // pocketed with the keys
        carThief = thief;
        if (tickCount % 10 != 0) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || p.distanceToSqr(this) > 48 * 48) continue;
            UUID id = p.getUUID();
            if (p.isDeadOrDying()) {
                if (downed.add(id)) retort("kill", 200, 1F, "kill0", "kill1");
                continue;
            }
            downed.remove(id);
            if (p.isUsingItem()) {
                net.minecraft.world.item.ItemStack use = p.getUseItem();
                Integer since = usingSince.get(id);
                if (since == null) usingSince.put(id, tickCount);
                else if (tickCount - since >= 10) {
                    usingSince.put(id, Integer.MAX_VALUE / 2);            // once per bite
                    if (com.groid.papon.registry.ModItems.TACOS.stream().anyMatch(t -> use.is(t.get()))) {
                        retort("mytaco", 400, 1F, "mytaco");
                    } else if (use.getItem() instanceof net.minecraft.world.item.PotionItem) {
                        retort("potion", 500, 0.7F, "potion");
                    } else if (use.isEdible()) { retort("snack", 500, 0.6F, "snack0", "snack1"); }
                }
            } else { usingSince.remove(id); }
            if (p.getHealth() < p.getMaxHealth() * 0.3F) retort("lowhp", 700, 0.5F, "lowhp0", "lowhp1");
        }
        if (target instanceof Player p) {
            double up = p.getY() - groundY(p.position());
            highTicks = up > 5 && !p.onGround() ? highTicks + 10 : 0;
            if (highTicks >= 40) {
                highTicks = 0;
                if (isRisen()) retort("high", 500, 0.8F, "high0", "high1");
                else retort("high", 500, 0.8F, "high0");
            }
            kiteTicks = p.distanceTo(this) > 20 && p.getVehicle() == null ? kiteTicks + 10 : 0;
            if (kiteTicks >= 80) {
                kiteTicks = 0;
                if (inColiseum()) retort("kite", 500, 0.8F, "kite0", "kite1");
                else retort("kite", 500, 0.8F, "kite0");
            }
        }
    }

    /** Client: the phase-two line he is saying and how far into it he is (seconds), or null. */
    @Nullable
    public VoiceLines.Line talking() { return talk; }

    public double talkTime(float partial) { return (tickCount - talkStart + partial) / 20.0; }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientStateTicks++;
            if (isArmored() && tickCount % 3 == 0) {
                // the shimmer round him while nothing can touch him
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(net.minecraft.core.particles.ParticleTypes.WAX_OFF, getX() + Math.cos(a) * getBbWidth() * 0.7,
                        getY() + random.nextDouble() * getBbHeight(), getZ() + Math.sin(a) * getBbWidth() * 0.7, 0, 0.02, 0);
            }
            if (inColiseum() && spotlit() && (getBossState() != BossState.P3_BLACKOUT || ticksInState() >= 30)) {
                // dust turning in his spotlight
                for (int i = 0; i < 2; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 2.6;
                    level().addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, getX() + Math.cos(a) * r,
                            getY() + random.nextDouble() * 9, getZ() + Math.sin(a) * r, 0, -0.01, 0);
                }
            }
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
        // the state's clock to the clients: every tick through a cutscene (their shots are cut to it), now and then otherwise
        BossState now = getBossState();
        if (now.cutscene() || now.intro() || now == BossState.DEATH || now == BossState.ROAR || tickCount % 10 == 0) {
            entityData.set(DATA_TICKS, stateTicks);
        }
        entityData.set(DATA_ARMOR, armored(now, stateTicks));
        if (retortCd > 0) retortCd--;
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
        p4Watchdog(level);
        tickCooldowns();
        if (soloDue > 0) soloDue--;
        if (soloCd > 0) soloCd--;
        if (cutawayDue > 0) cutawayDue--;
        if (cutawayCd > 0) cutawayCd--;
        tickShockwave(level);
        LivingEntity target = pickTarget(level);
        record(target);
        tickFires(level);
        tickLineWave(level);
        tickArena(level);
        watchPlayers(level, getTarget());
        tickFoils(level);
        tickThrownVan(level);
        tickGriddle(level);
        tickMashWaves(level);
        tickSnares(level);
        tickBrickVan(level, target);
        tickBandWaves(level);
        tickSombreros(level);
        tickGeysers(level);
        tickCalaveras(level);
        if (!salsaRings.isEmpty()) {
            salsaRings.removeIf(id -> {
                if (!(level.getEntity(id) instanceof BossProp p)) return true;
                p.setRadius(Math.min(7.5F, p.radius() + 0.85F));
                return false;
            });
        }
        entityData.set(DATA_GUITAR, getBossState() == BossState.P3_DUEL_FETCH && duelPhase == 1);
        if (!phaseThreeDue && isPhaseTwo() && !isPhaseThree() && getHealth() <= getMaxHealth() * PHASE_THREE_AT && getHealth() > 0
                && getBossState() == BossState.IDLE) {
            phaseThreeDue = true;           // a reload in the middle of it: from the top
        }
        if (phaseThreeDue && canStartPhaseThree()) {
            startPhaseThree(level);
            return;
        }
        tickMuertosCooldowns();
        tickStrikes(level);
        tickSerpentDives(level);
        tickBreaths(level);
        tickPassiveBreath(level, target);
        tickFusionRings(level);
        tickFlung(level);
        tickGiantLayer(level, target);
        tickSerpentLayer(level, target);
        tickTrails(level);
        tickChaos(level, target);
        if (stageDue()) {
            startRage(level);
            return;
        }
        if ((isPhaseFour() || inColiseum()) && tickCount % 10 == 0) syncField();
        if (!phaseFourDue && isPhaseThree() && !isPhaseFour() && getHealth() <= getMaxHealth() * PHASE_FOUR_AT && getHealth() > 0
                && getBossState() == BossState.IDLE) {
            phaseFourDue = true;
        }
        if (phaseFourDue && canStartPhaseFour()) {
            startPhaseFour(level);
            return;
        }
        if (muertosFight() && !getBossState().phaseFour() && tickCount % 40 == 0) keepGuardian(level);
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
            case P2_CUTAWAY -> tickCutaway(level);
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
            case ARENA_OLA -> tickArenaOla(level, target);
            case ARENA_STAMPEDE -> tickArenaStampede(level, target);
            case P2_MASHER -> tickMasher(level, target);
            case P3_KNOCKBACK -> tickP3Knockback(level);
            case P3_GLARE -> tickP3Glare(level);
            case P3_STALK -> tickP3Stalk(level);
            case P3_SNATCH -> tickP3Snatch(level);
            case P3_RETURN -> tickP3Return(level);
            case P3_BLACKOUT -> tickP3Blackout(level);
            case P3_SERENADE -> tickP3Serenade(level);
            case P3_VERDICT -> tickP3Verdict(level);
            case P3_TOSS -> tickP3Toss(level);
            case P3_DANCE -> tickP3Dance(level);
            case P3_FELT -> tickP3Felt(level);
            case P3_TO_CAR -> tickP3ToCar(level);
            case P3_CRUSH -> tickP3Crush(level);
            case P3_SHOWCAR -> tickP3ShowCar(level);
            case P3_GO -> tickP3Go(level);
            case P3_ASCEND -> tickP3Ascend(level);
            case P3_COMBO -> tickP3Combo(level, target);
            case P3_FLASH -> tickP3Flash(level, target);
            case P3_COMET -> tickP3Comet(level, target);
            case P3_COMET_LAND -> tickP3CometLand(level, target);
            case P3_LASER -> tickP3Laser(level, target);
            case P3_SOLAR -> tickP3Solar(level, target);
            case P3_MARIACHI -> tickP3Mariachi(level, target);
            case P3_DUEL_FETCH -> tickP3DuelFetch(level, target);
            case P3_DUEL -> tickP3Duel(level, target);
            case P3_DUEL_SMASH -> tickP3DuelSmash(level, target);
            case P3_DUEL_SHAME -> tickP3DuelShame(level, target);
            case P3_PINATA -> tickP3Pinata(level, target);
            case P3_BRAZIERS -> tickP3Braziers(level, target);
            case P3_NOVA -> tickP3Nova(level, target);
            case P3_SPENT -> tickP3Spent(level);
            case P3_NITRO -> tickP3Nitro(level, target);
            case P3_POWER -> tickP3Power(level);
            case WINDED -> tickWinded(level, target);
            case P3_SOMBRERO -> tickP3Sombrero(level, target);
            case P3_VOLCAN -> tickP3Volcan(level, target);
            case P3_CALAVERAS -> tickP3Calaveras(level, target);
            case P3_PINATA_SWING -> tickP3PinataSwing(level, target);
            case P4_FALL -> tickP4Fall(level);
            case P4_RISEUP -> tickP4RiseUp(level);
            case P4_IGNIS -> tickP4Ignis(level);
            case P4_PROPHECY -> tickP4Prophecy(level);
            case P4_BRETHREN -> tickP4Brethren(level);
            case P4_SOULS -> tickP4Souls(level);
            case P4_HALFBAKED -> tickP4HalfBaked(level);
            case P4_BOW -> tickP4Bow(level);
            case P4_SERPENTS -> tickP4Serpents(level);
            case P4_TALKING -> tickP4Talking(level);
            case P4_TOBAND -> tickP4ToBand(level);
            case P4_HONOR -> tickP4Honor(level);
            case P4_MUCHACHAS -> tickP4Muchachas(level);
            case P4_GOODTIME -> tickP4GoodTime(level);
            case P4_SERPIENTES -> tickP4Serpientes(level, target);
            case P4_FLECHAS -> tickP4Flechas(level, target);
            case P4_MARTILLO -> tickP4Martillo(level, target);
            case P4_GIGANTE -> tickP4Gigante(level, target);
            case P4_GRIETA -> tickP4Grieta(level, target);
            case P4_PETALOS -> tickP4Petalos(level, target);
            case P4_PAPEL -> tickP4Papel(level, target);
            case P4_VELAS -> tickP4Velas(level, target);
            case P4_DANZA -> tickP4Danza(level, target);
            case P4_LUNA -> tickP4Luna(level, target);
            case P4_THRONE -> tickP4Throne(level);
            case P4_OUROBOROS -> tickP4Ouroboros(level, target);
            case P4_ALIENTO -> tickP4Aliento(level, target);
            case P4_FUSION -> tickP4Fusion(level, target);
            case P4_PISTA -> tickP4Pista(level, target);
            case P4_CEMPASUCHIL -> tickP4Camino(level, target);
            case P4_CASTILLO -> tickP4Castillo(level, target);
            case P4_SALSA -> tickP4Salsa(level, target);
            case P4_TANGO -> tickP4Tango(level, target);
            case P4_TANGO_WIN -> tickP4TangoWin(level, target);
            case P4_TANGO_LOSE -> tickP4TangoLose(level, target);
            case P4_RAGE -> tickP4Rage(level, target);
            case P4_JUICIO -> tickP4Juicio(level, target);
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
        olaCd--;
        stampedeCd--;
        heckleCd--;
        shoveCd--;
        if (getBossState() != BossState.DRIVING && getBossState() != BossState.EXIT_STUMBLE) vanCd--;
        think--;
        tickAscendedCooldowns();
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
        if (cutawayDue > 0 && inColiseum()) {
            cutawayDue = 0;
            entityData.set(DATA_CUTAWAY, cutawayKind);
            setBossState(BossState.P2_CUTAWAY);
            return;
        }
        if (isRisen() && arena == null && !arenaFailed && arenaRetryCd <= 0 && target != null && noTargetTicks == 0) {
            // back for more after the coliseum came down: up it goes again
            planArena((ServerLevel) level());
            setBossState(BossState.P2_SCREAM);
            return;
        }
        // the leash: not in a coliseum (it keeps him on the sand itself; the dead one is wider than ARENA_RADIUS, and a
        // leash there left him hanging in the air forever: a ground path never starts while flying), measured flat
        if (home != null && !(inColiseum() && arena != null)
                && Mth.square(getX() - home.x) + Mth.square(getZ() - home.z) > ARENA_RADIUS * ARENA_RADIUS) {
            if (isFlying()) flyTo(home.add(0, 4, 0), 0.8);
            else getNavigation().moveTo(home.x, home.y, home.z, 1.2);
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
        // phase one goes for the van whenever it is ready; phase two only when the picker draws it (choose)
        if (!isPhaseTwo() && vanCd <= 0 && van != null && !van.isVehicle() && distanceTo(van) < 40 && d < 40) {
            if (isFlying()) setFlying(false);
            setBossState(BossState.TO_VAN);
            say("keys", 40);
            return;
        }
        // a close-range move he has not used in a while is ready: he walks in for it (3 s at most) before choosing
        if (!ascendedFight() && d > 4.5 && closeIn < 60 && closeMoveDue(target)) {
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
        if (muertosFight()) return "p4_charm" + random.nextInt(12);
        if (ascendedFight()) return "p3_charm" + random.nextInt(10);
        if (isPhaseTwo() && random.nextInt(10) < 7) return "charm" + random.nextInt(10);
        return "taunt" + random.nextInt(4);
    }

    private BossState choose(ServerLevel level, LivingEntity target, double d) {
        ctxTarget = target;
        if (muertosFight()) {
            misses = 0;
            return choosePhaseFour(level, target, d);
        }
        if (ascendedFight()) {
            if (misses >= FRENZY) {
                // a miss streak: no pause, the lightning
                misses = 0;
                think = 0;
                say(taunt(), 40);
                flashLeft = 0;
                return aFlashCd <= 0 ? BossState.P3_FLASH : BossState.P3_COMBO;
            }
            return chooseAscended(level, target, d);
        }
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
            if (!risen && vanFree && hopCd <= 0 && distanceTo(van) < 12 && (!p2 || tickCount - lastVanMove > VAN_GAP)) {
                lastVanMove = tickCount;
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
        // the van, on foot (in phase two one van move at most every 25 s, and drawn like any other: VAN_GAP)
        boolean vanOk = vanFree && (!p2 || tickCount - lastVanMove > VAN_GAP);
        if (p2 && !isAscended() && vanOk && vanCd <= 0 && distanceTo(van) < 40 && d < 40) options.add(BossState.TO_VAN);
        if (vanOk && !risen && kickCd <= 0 && distanceTo(van) < 16 && target.distanceTo(van) < 28 && target.distanceTo(van) > 5) {
            options.add(BossState.PUNT_RUN);
        }
        if (vanOk && brickCd <= 0 && distanceTo(van) < 24 && target.distanceTo(van) > 6) options.add(BossState.BRICK);
        if (risen) {
            options.remove(BossState.LEAP);
            if (d < 6 && barrageCd <= 0) options.add(BossState.P2_BARRAGE);
            if (d >= 5 && meteorCd <= 0) options.add(BossState.P2_METEOR);
            if (d >= 4 && diveCd <= 0) options.add(BossState.P2_DIVE);
            if (vanOk && vanThrowCd <= 0 && distanceTo(van) < 36 && target.distanceTo(van) < 30) options.add(BossState.VAN_GRAB);
            if (d >= 5 && fryCd <= 0) options.add(BossState.P2_FRY_VOLLEY);
            if (mashCd <= 0) options.add(BossState.P2_MASH);
            if (d >= 6 && totCd <= 0) options.add(BossState.P2_TOT);
            if (snareCd <= 0) options.add(BossState.P2_SNARE);
            if (gravityCd <= 0) options.add(BossState.P2_GRAVITY);
            if (inColiseum() && arena != null) {
                if (fryerCd <= 0 && freidora == null) options.add(BossState.ARENA_FRYER);
                if (heckleCd <= 0 && heckleTicks <= 0) options.add(BossState.ARENA_HECKLE);
                if (masherCd <= 0) options.add(BossState.P2_MASHER);
                if (olaCd <= 0 && olaT < 0) options.add(BossState.ARENA_OLA);
                if (stampedeCd <= 0 && stampedeT < 0) options.add(BossState.ARENA_STAMPEDE);
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
        for (int i = 0; i < moves.length; i++) { if (cds[i] <= 0 && tickCount - lastUsed.getOrDefault(moves[i], fightStart) > 400) return true; }
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
            case TO_VAN, VAN_GRAB, BRICK, PUNT_RUN -> 0.5F;              // the van: a spice, not the meal
            case LEAP -> 0.7F;
            case QTE_GRAB, CLEAVER_TOSS, HAYMAKER, BURRITO_TOSS, P2_GRAVITY, P2_METEOR -> 1.6F;   // the signature moves
            case ARENA_FRYER, ARENA_HECKLE, P2_MASHER, ARENA_OLA, ARENA_STAMPEDE -> 2.0F;        // his coliseum: he plays to it
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
            if (since > 600 && since > oldest && !(isPhaseTwo() && vanMove(s))) {     // the van is never owed in phase two
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
            w[i] = (muertosFight() ? muertosWeight(s) : ascendedFight() ? ascendedWeight(s) : baseWeight(s)) * context(s)
                    * (1F + Math.min(since, 900) / 200F);
            total += w[i];
        }
        float r = random.nextFloat() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) return fresh.get(i);
        }
        return fresh.get(fresh.size() - 1);
    }

    private static boolean vanMove(BossState s) {
        return s == BossState.TO_VAN || s == BossState.VAN_GRAB || s == BossState.BRICK || s == BossState.PUNT_RUN;
    }

    private void startAttack(BossState pick, LivingEntity target) {
        boolean p2 = isPhaseTwo();
        swingHit = false;
        if (pick != lastAttack) prevAttack = lastAttack;
        lastAttack = pick;
        lastUsed.put(pick, tickCount);
        if (vanMove(pick)) lastVanMove = tickCount;
        if (muertosFight()) {
            startMuertos(pick, target);
            setBossState(pick);
            return;
        }
        if (ascendedFight()) {
            startAscended(pick, target);
            if (pick == BossState.P3_FLASH && flashLeft <= 0) flashLeft = 5;
            if (isFlying() && !airborne(pick)) {
                setDeltaMovement(getDeltaMovement().x * 0.3, -1.3, getDeltaMovement().z * 0.3);
                hasImpulse = true;
            }
            setBossState(pick);
            return;
        }
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
            case VAN_GRAB -> vanThrowCd = 600;
            case TO_VAN -> say("keys", 40);
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
            case ARENA_FRYER -> fryerCd = 360;
            case ARENA_HECKLE -> heckleCd = 440;
            case P2_MASHER -> masherCd = 560;
            case ARENA_OLA -> olaCd = 480;
            case ARENA_STAMPEDE -> stampedeCd = 500;
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
                 P2_SNARE, P2_GRAVITY, BRICK, VAN_HOP, P3_COMBO, P3_FLASH, P3_COMET, P3_LASER, P3_SOLAR, P3_MARIACHI, P3_DUEL_FETCH,
                 P3_PINATA, P3_BRAZIERS, P3_NOVA, P3_NITRO, P3_SOMBRERO, P3_VOLCAN, P3_CALAVERAS, P3_PINATA_SWING, P4_MARTILLO, P4_GIGANTE,
                 P4_GRIETA, P4_PETALOS, P4_PAPEL, P4_VELAS, P4_DANZA, P4_LUNA, P4_SERPIENTES, P4_FLECHAS, P4_OUROBOROS, P4_PISTA,
                 P4_CEMPASUCHIL, P4_CASTILLO, P4_SALSA, P4_TANGO, P4_ALIENTO, P4_FUSION, P4_RAGE, P4_JUICIO -> true;
            default -> false;
        };
        if (s == BossState.P4_FALL) {
            // phase four from the top, as if a hit had just taken him to a quarter (phase three first, if he was not in it)
            entityData.set(DATA_PHASE2, true);
            entityData.set(DATA_PHASE3, true);
            if (form() < 3) entityData.set(DATA_FORM, 3);
            refreshDimensions();
            setHealth(Math.min(getHealth(), getMaxHealth() * PHASE_FOUR_AT - 1));
            entityData.set(DATA_NIGHT, NIGHT_P3);         // phase three's night, as if he had come through it
            phaseFourDue = true;
            setBossState(BossState.IDLE);
            return;
        }
        if (s.muertosMove() && !muertosFight()) {
            // one of his truest form's moves asked for before phase four (dev and testing): straight into the form
            entityData.set(DATA_PHASE2, true);
            entityData.set(DATA_PHASE3, true);
            entityData.set(DATA_PHASE4, true);
            p4StageReached = 1;
            entityData.set(DATA_P4STAGE, 1);
            entityData.set(DATA_FORM, 4);
            entityData.set(DATA_P4PROP, 1);
            entityData.set(DATA_MOON, 2);
            refreshDimensions();
        }
        if (s.ordinal() >= BossState.P3_COMBO.ordinal() && s != BossState.WINDED && !ascendedFight()) {
            // an Ascended move asked for before phase three: straight into his form (dev and testing)
            entityData.set(DATA_PHASE2, true);
            entityData.set(DATA_PHASE3, true);
            entityData.set(DATA_FORM, 3);
            refreshDimensions();
        }
        if (s == BossState.P4_RAGE || s == BossState.P4_JUICIO) {
            // a stage of phase four from its break (dev and testing): his health to just under it, the stage before it reached
            boolean end = s == BossState.P4_JUICIO;
            setHealth(Math.min(getHealth(), getMaxHealth() * (end ? STAGE_THREE_AT : STAGE_TWO_AT) - 1));
            p4StageReached = end ? 2 : 1;
            entityData.set(DATA_P4STAGE, p4StageReached);
            setBossState(BossState.IDLE);
            return;
        }
        if (s == BossState.P3_KNOCKBACK) {
            // phase three from the top, as if a hit had just taken him to half health
            setHealth(Math.min(getHealth(), getMaxHealth() * PHASE_THREE_AT - 1));
            entityData.set(DATA_PHASE3, false);
            p3From = target != null ? target.position() : null;
            phaseThreeDue = true;
            setBossState(BossState.IDLE);
            return;
        }
        if (startable && target != null) startAttack(s, target);
        else setBossState(s);
    }

    private static boolean attack(BossState s) {
        return switch (s) {
            case SWIPE, SWIPE_OVERHEAD, SLAM, BREATH, THROW, LEAP, LEAP_LAND, CHARGE_WINDUP, CHARGE, UPPERCUT, AIR_SPIKE,
                 BLINK, GATLING, DASH_COMBO, VAN_KICK, BAILOUT, P2_BARRAGE, P2_METEOR, P2_DIVE, VAN_THROW, GRAB, GRAB_COMBO,
                 RIPOSTE, CLEAVER_TOSS, BURRITO_TOSS, DRUNK_FLURRY, HAYMAKER, COMAL_SLAM, QTE_GRAB, QTE_FAIL, P2_FRY_VOLLEY, P2_MASH,
                 P2_TOT, P2_SNARE, P2_GRAVITY, P3_COMBO, P3_FLASH, P3_COMET_LAND, P3_LASER, P3_SOLAR, P3_MARIACHI, P3_DUEL_SMASH,
                 P3_PINATA, P3_BRAZIERS, P3_NOVA, P3_SOMBRERO, P3_VOLCAN, P3_CALAVERAS, P3_PINATA_SWING, P4_MARTILLO, P4_GIGANTE, P4_GRIETA,
                 P4_PETALOS, P4_PAPEL, P4_VELAS, P4_DANZA, P4_LUNA, P4_OUROBOROS, P4_PISTA, P4_CEMPASUCHIL, P4_CASTILLO, P4_SALSA,
                 P4_TANGO_WIN, P4_ALIENTO, P4_FUSION, P4_JUICIO -> true;
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
            } else { setBossState(swingHit ? BossState.IDLE : BossState.STAGGER); }
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

    private float yawTo(Vec3 at) { return (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90F; }

    /**
     * Every hit goes through here: a heavy one breaks a raised shield (Cataclysm's disableShield) and
     * lands anyway at 60%. Marks the attack as having connected.
     */
    private boolean strike(LivingEntity e, float damage, ResourceKey<DamageType> type, boolean heavy) {
        if (heavy && e instanceof Player p && p.isBlocking()) {
            p.disableShield(true);
            damage *= 0.6F;
            retort("shield", 600, 0.6F, "shield0", "shield1");
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
            if (e.isAlive() && inColiseum()) quip("landed" + random.nextInt(4), 0.3F);
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

    private int clangCd;

    /** A hit on him mid-move (armored): a ring like a bell, gold sparks; it does nothing. */
    private void armorClang(@Nullable Entity attacker) {
        if (!(level() instanceof ServerLevel level) || clangCd > tickCount) return;
        clangCd = tickCount + 4;
        Vec3 at = attacker != null ? position().add(0, getBbHeight() * 0.55, 0).lerp(attacker.position().add(0, 1.2, 0), 0.25)
                : position().add(0, getBbHeight() * 0.55, 0);
        Fx.spark(level, at, 7, 0.4, 0.35F, 0xFFE07A);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.7F, 1.9F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 0.5F, 1.6F);
    }

    /** A hit that glanced off his composure: a clank and gold sparks, and now and then a word to the one hitting. */
    private void composure(@Nullable Entity attacker) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = position().add(0, getBbHeight() * 0.6, 0);
        Fx.spark(level, at, 5, 0.5, 0.3F, 0xFFE07A);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.8F);
    }

    /** Counts {@code p}'s melee hit; true when it is the fourth in 2.5 s and he can burst now. */
    private boolean pressed(Player p, BossState s) {
        java.util.ArrayDeque<Integer> q = pressure.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
        q.addLast(tickCount);
        while (!q.isEmpty() && tickCount - q.peekFirst() > 50) q.pollFirst();
        boolean free = s == BossState.IDLE || s == BossState.SWIPE || s == BossState.SWIPE_OVERHEAD || s == BossState.UPPERCUT
                || s == BossState.RIPOSTE || s == BossState.EVADE || s == BossState.TO_VAN || s == BossState.DRINK_HANDLE
                || s == BossState.THROW || s == BossState.BREATH || s == BossState.GATLING || s == BossState.CLEAVER_TOSS;
        return q.size() >= 6 && saltBurstCd <= 0 && free && !isFlying();
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
                retortStolen(van.isSkyline());
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
            return;
        }
        // a sprint to the door (he cannot be stopped on the way: half damage, no knockback), shoving anyone in his way
        shoveNear(level);
        if (horizontalDist(door) < 4) {
            getMoveControl().setWantedPosition(door.x, door.y, door.z, isPhaseTwo() ? 2.0 : 1.75);
        } else if (getNavigation().isDone() || tickCount % 6 == 0) { getNavigation().moveTo(door.x, door.y, door.z, isPhaseTwo() ? 2.0 : 1.75); }
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
                if (van != null && van.getControllingPassenger() instanceof Player) retortStolen(van.isSkyline());
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
        boolean god = ascendedFight();
        if (t < 10 && t % 2 == 0) Fx.spark(level, position().add(0, getBbHeight() * 0.5, 0), 6, 0.6, 0.3F, god ? 0xFFE07A : 0xE0301C);
        if (t == 10 && god) {
            // Ascended: not salsa, a burst of his own light
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 3F);
            Fx.shock(level, position(), 8F, 0xFFE07A);
            for (int i = 0; i < 3; i++) {
                double a = Math.PI * 2 * i / 3 + random.nextDouble();
                bolt(level, position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4));
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0F, 0.8F);
            PaponNetwork.shake(level, position(), 18, 0.7F, 16);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.5), this::hittable)) {
                Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(away.scale(1.9).add(0, 0.8, 0));
                e.hurtMarked = true;
                strike(e, 6F, ModDamage.DEMIGOD, false);
                Mechanics.trueHit(e, 0.14F, this, true);
            }
            pressure.clear();
        } else if (t == 10) {
            Vec3 c = position().add(0, getBbHeight() * 0.45, 0);
            Fx.blast(level, c, 2.6F);
            Fx.shock(level, position(), 7F, 0xD8261A);
            // a ring of salsa thrown out round him (drawn: client/PropRenderer)
            BossProp ring = BossProp.create(level, BossProp.Kind.SALSA_RING, this, new Vec3(getX(), groundY(position()), getZ())).life(30);
            ring.setRadius(1F);
            level.addFreshEntity(ring);
            salsaRings.add(ring.getUUID());
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
        legsLeft = 3;
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
     * manoeuvres (three):
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
                if (driveTicks == 1 && nitro) {
                    level.playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.5F);
                    Fx.spark(level, van.position().subtract(van.forward().scale(CaravanEntity.HALF_LENGTH)).add(0, 0.4, 0), 20, 0.5, 0.45F, 0xB050FF);
                }
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
                if (nitro && driveTicks % 2 == 0) {
                    // a ring of purple fire laid down round you
                    Vec3 tail = van.position().subtract(van.forward().scale(CaravanEntity.HALF_LENGTH + 0.5));
                    addFire(level, tail.add(0, 0.6, 0));
                    Fx.spark(level, tail.add(0, 0.3, 0), 3, 0.4, 0.4F, 0xB050FF);
                }
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
                } else if (driveTicks > 90) { nextDrive(Drive.BRAKE); }
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
        vanCd = isPhaseTwo() ? PaponConfig.VAN_COOLDOWN.get() * 2 : PaponConfig.VAN_COOLDOWN.get();     // phase two has better things to do
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
        if (!nitro && !bailUsed && legsLeft <= 1 && dist > 10 && r < 0.5F) {
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
        if (nitro || driveTicks % every != every / 2) return;
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
        if (nitro) {
            // Ascended does not stumble out: up through the roof, wings out
            nitro = false;
            if (van != null) {
                van.releaseAutopilot();
                van.setGhost(false);
                van.setRamShare(0);
                van.settle();
            }
            stopRiding();
            if (van != null) moveTo(van.getX(), van.getY() + 2.4, van.getZ(), getYRot(), 0);
            setFlying(true);
            setDeltaMovement(0, 0.9, 0);
            hasImpulse = true;
            Fx.shock(level, position(), 4F, 0xB050FF);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0F, 0.8F);
            aNitroCd = 760;
            think = 8;
            setBossState(BossState.IDLE);
            return;
        }
        if (van != null) {
            van.releaseAutopilot();
            van.setGhost(false);
            van.settle();
            Vec3 door = van.driverDoor();
            stopRiding();
            moveTo(door.x, van.getY(), door.z, getYRot(), 0);
            unstick();
        } else { stopRiding(); }
        vanCd = isPhaseTwo() ? PaponConfig.VAN_COOLDOWN.get() * 2 : PaponConfig.VAN_COOLDOWN.get();     // phase two has better things to do
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
        if (stateTicks % 16 == 0) { Fx.chunks(level, position().add(0, getBbHeight() * 0.8, 0), 2, 0.12F, Fx.TEQUILA, 0.15); }
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
        if (t >= 32 && t <= 44 && t % 4 == 0) { Fx.slash(level, position().add(0, 1.2, 0), getYRot() + t * 50F, 2.8F, Fx.WHITE); }
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
        } else if (getNavigation().isDone() || tickCount % 6 == 0) { getNavigation().moveTo(spot.x, spot.y, spot.z, 1.5); }
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
            } else { setBossState(BossState.IDLE); }
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
    public boolean holds(Entity e) { return grabbed != null && !releasing && e.getUUID().equals(grabbed); }

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
    public boolean shouldRiderSit() { return false; }

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
            cutSpeech(level);                           // the music stops dead, and so does whatever he was saying
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

    /** Whatever he is saying stops now: the bubble, and the sound for everyone who hears it. */
    private void cutSpeech(ServerLevel level) {
        String was = getSpeech();
        if (was.isEmpty()) return;
        net.minecraft.resources.ResourceLocation voice = Speech.voiceOf(was.split("\u001f")[0]);
        if (voice != null) {
            var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(voice, SoundSource.HOSTILE);
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 96 * 96) p.connection.send(stop);
        }
        entityData.set(DATA_SPEECH, "");
        speechTicks = 0;
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
        for (ServerPlayer p : level.players()) { if (!p.isSpectator() && p.distanceToSqr(this) <= 48 * 48) in.add(p); }
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
                MariachiEntity.spawn(level, role, arena.bandSpot(role.ordinal()), arena.bandYaw(role.ordinal()), arena.lamps());
            }
        }
        if (bandWoken) {
            songStart = (int) ((level.getGameTime() + 10) & 0x7FFFFFFF);
            for (MariachiEntity m : band(level)) m.play(songStart, songId);
        }
        if (isPhaseThree()) lightsCrimson(level);
    }

    /** The coliseum comes down: everyone and everything in it back to the ground, him included. */
    private void leaveArena(ServerLevel level) {
        clearGuardian(level);
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
        olaT = -1;
        stampedeT = -1;
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

    /** The whole bowl on its feet: a roar heard over anything, him talking included. */
    private void crowdRoar(ServerLevel level, int ticks) {
        ColiseumCrowdEntity c = crowd(level);
        if (c != null) c.react(CrowdMood.ROAR, ticks, true);
    }

    /** Fireworks in the flag's colours (client/FlagFireworks), a volley on each of the song's next {@code beats} beats. */
    private void salvo(ServerLevel level, int beats) {
        ColiseumCrowdEntity c = crowd(level);
        if (c != null) c.salvo(beats);
    }

    /** Fireworks off the podium inside the bowl, bursting over the sand (client/FlagFireworks.inside). */
    private void salvoInside(ServerLevel level, int beats) {
        ColiseumCrowdEntity c = crowd(level);
        if (c != null) c.salvoInside(beats);
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
            if (olaT >= 0) tickOla(level);
            if (stampedeT >= 0) tickStampede(level);
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
            if (c != null && !c.reacting()) {
                c.react(m, 180);
                if (m == CrowdMood.CHANT) {
                    c.salvo(4);                                  // "PA-PÓN!": the walls light up with it
                    if (random.nextBoolean()) cutawayHeard(Cutaway.STANDS.id());
                }
            }
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
    private boolean outOfArena(Vec3 p) { return !arena.inArena(p) || p.y < arena.floorY() - 1.5; }

    /** The stand's front, where he stands to have words with the band. */
    private Vec3 standFront() { return arena.centerVec().add(0, 0, -Coliseum.B + 9.6); }

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
        // the stands: a roar as he comes down, another as he lands; "WELCOME... to MY COLISEUM!!!" and they answer;
        // "ARE YOU READY?!" and they answer louder (each roar after his line, so neither covers the other)
        int welcome = beat("arrive.welcome", 46);
        if (t == 2) crowdRoar(level, 40);
        if (t == 36) {
            crowdRoar(level, 40);
            salvo(level, 4);
        }
        if (t == welcome) {
            say("welcome", 50);
            title(level, "title.papon.coliseum", 50);
        }
        if (t == beat("arrive.roar1", 84)) {
            crowdRoar(level, 40);
            salvo(level, 6);
        }
        int musica = beat("arrive.musica", 74);
        if (t == beat("arrive.listos", 46)) say("listos", 40);
        if (t == beat("arrive.roar2", 110)) {
            crowdRoar(level, 40);
            salvo(level, 8);
        }
        if (t > 36 && t < musica - 4) turnToward(c.add(Math.sin(t * 0.09) * 20, 0, Math.cos(t * 0.09) * 20), 8F);
        if (bandWoken) {
            if (t >= beat("arrive.roar1", 84) + 8) finishCutscene(level);
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
            if (p.distanceToSqr(this) < 120 * 120) {
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
            for (MariachiEntity m : band(level)) { if (!m.getRole().singer()) m.wake(m.getRole().dozer() ? 0 : random.nextInt(30)); }
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

    /** Rockets off the top of the walls in the flag's colours, on the beat (a salvo: client/FlagFireworks). */
    private void fireworks(ServerLevel level) { salvo(level, 8); }

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
            } else { getNavigation().stop(); }
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
            crowdRoar(level, 26);
            salvoInside(level, 2);
            PaponNetwork.shake(level, position(), 48, 0.2F, 14);
        }
        if (t == 60) {
            crowdRoar(level, 12);
            salvoInside(level, 2);
            PaponNetwork.shake(level, position(), 48, 0.25F, 12);
        }
        if (t == 74) {
            crowdRoar(level, 10);
            salvo(level, 2);
            PaponNetwork.shake(level, position(), 48, 0.3F, 16);
        }
        if (t == 78) say("landed1", 70);           // "And the crowd... goes WILD!!!" over the raise-the-roof
        if (t == 80) {
            crowdReact(level, CrowdMood.CHANT, 50);
            salvoInside(level, 4);                 // a volley on each pump of the raise-the-roof, inside the bowl
        }
        if (t == 104) crowdRoar(level, 14);
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
            crowdRoar(level, 70);
            fireworks(level);
            salvoInside(level, 6);
            PaponNetwork.shake(level, position(), 48, 0.7F, 24);
            Fx.shock(level, position(), 7F, 0xFFE07A);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.9F);
        }
        if (left <= 0 || stateTicks > BossState.P2_HYPE_LENGTH + 120) {
            // the first sung word: fire all round the bowl, and the stands lose their minds
            crowdMood(level, CrowdMood.FIESTA);
            crowdRoar(level, 60);
            ColiseumCrowdEntity c = crowd(level);
            if (c != null) c.pyro(12);
            salvoInside(level, 4);
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
            cutawayCd = Math.max(cutawayCd, 300);
            think = 4;
            crowdReact(level, CrowdMood.ROAR, 20);
            setBossState(BossState.IDLE);
        }
    }

    /**
     * The moments in the band's music the camera cuts to mid-fight (P2_CUTAWAY), like the guitar solo: the client hears
     * them in its score (SongPlayer) and asks; the stands' chant is the server's own. Synced as the ordinal + 1.
     */
    public enum Cutaway {
        /** The trumpets come in: horns up, cheeks puffed. */
        TRUMPETS,
        /** The violins' big run (the chorus, the climax): the bows flying. */
        VIOLINS,
        /** The breakdown: Gordo's guitarron, low and fat. */
        GUITARRON,
        /** The guitar and the vihuela driving it along. */
        STRINGS,
        /** The coro's harmonies: three mics up. */
        CORO,
        /** "PA-PÓN! PA-PÓN!": the stands on their feet, the flags going. */
        STANDS;

        public static Cutaway byId(int id) {
            Cutaway[] all = values();
            return id >= 1 && id <= all.length ? all[id - 1] : TRUMPETS;
        }

        public int id() { return ordinal() + 1; }
    }

    /** Client: who P2_CUTAWAY is on. */
    public Cutaway cutaway() { return Cutaway.byId(entityData.get(DATA_CUTAWAY)); }

    /**
     * A moment in the music (or the stands) worth a cut to the band: if the fight can spare 1.8 s (he is between moves,
     * nothing else cut away lately, not this one in the last minute), he stops for it.
     */
    public void cutawayHeard(int kind) {
        BossState s = getBossState();
        if (kind < 1 || kind > Cutaway.values().length) return;
        if (!inColiseum() || !bandWoken || cutawayCd > 0 || soloDue > 0 || s.cutscene() || s == BossState.DEATH || s.qte()) return;
        if (tickCount - cutawayLast[kind - 1] < 1200 && cutawayLast[kind - 1] != 0) return;
        cutawayDue = 50;
        cutawayKind = kind;
    }

    /** Testing (/papon cutaway): the cut, now. */
    public void devCutaway(Cutaway c) {
        entityData.set(DATA_CUTAWAY, c.id());
        force(BossState.P2_CUTAWAY);
    }

    /** The cut to the band (1.8 s): he stops where he is, turns to them and grooves along; the stands cheer. */
    private void tickCutaway(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        if (isFlying()) setDeltaMovement(getDeltaMovement().scale(0.4));
        else setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
        Cutaway c = cutaway();
        MariachiEntity singer = singer(level);
        if (singer != null) turnToward(singer.position(), 8F);
        if (t == 2) {
            if (c == Cutaway.STANDS) {
                crowdRoar(level, 30);
                salvo(level, 3);
            } else { crowdReact(level, CrowdMood.CHEER, 30); }
        }
        if (t == 10 && quipCd <= 0 && speechTicks <= 0 && random.nextInt(3) == 0) {
            // a word for his public, or "OLÉ!!!" for his band
            String line = c == Cutaway.STANDS ? "charm" + (6 + random.nextInt(4)) : "charm5";
            say(ascendedFight() ? "p3_" + (c == Cutaway.STANDS ? "charm" + (6 + random.nextInt(4)) : "charm0") : line, 40);
        }
        if (t >= BossState.P2_CUTAWAY.duration) {
            cutawayLast[c.ordinal()] = tickCount;
            cutawayCd = 500 + random.nextInt(200);
            soloCd = Math.max(soloCd, 240);
            think = 4;
            setBossState(BossState.IDLE);
        }
    }

    // ------------------------------------------------------------------ phase three: "Para Siempre"

    /** Whether the hit that took him to half health can knock him out of what he is doing now. */
    private boolean canStartPhaseThree() {
        BossState s = getBossState();
        if (s.cutscene() || s.intro() || s.qte() || s == BossState.DEATH || s == BossState.ROAR) return false;
        if (isPassenger() || s == BossState.ENTER_VAN || s == BossState.DRIVING) return false;
        // his coliseum's own beats come first (the band has to be up and playing)
        return !inColiseum() || bandWoken;
    }

    /**
     * Half health: whatever he was doing, the hit knocks him out of it (and out of the air): flung back from
     * whoever landed it, down on his back on the sand, a skid. The band's song grinds to a halt under it.
     */
    private void startPhaseThree(ServerLevel level) {
        phaseThreeDue = false;
        release();
        getNavigation().stop();
        setFlying(false);
        p3Start = position();
        Vec3 from = p3From != null ? p3From : position().add(Vec3.directionFromRotation(0, getYRot()).scale(3));
        Vec3 away = position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()).scale(-1) : away.normalize();
        double floor = inColiseum() && arena != null ? arena.floorY() : groundBelow(level, position());
        Vec3 end = position().add(away.scale(9.5));
        if (inColiseum() && arena != null) {
            // flung toward the podium wall: swing the line round toward the middle until the flight fits on the sand
            for (int i = 1; i <= 12 && horizontalDist(arena.clamp(end, 5, 0, 0)) < 7.5; i++) {
                double turn = Math.toRadians(15 * ((i + 1) / 2)) * (i % 2 == 0 ? -1 : 1);
                Vec3 a2 = new Vec3(away.x * Math.cos(turn) - away.z * Math.sin(turn), 0, away.x * Math.sin(turn) + away.z * Math.cos(turn));
                end = position().add(a2.scale(9.5));
                if (horizontalDist(arena.clamp(end, 5, 0, 0)) >= 7.5) away = a2;
            }
            end = arena.clamp(end, 5, 0, 0);
        }
        p3End = new Vec3(end.x, floor, end.z);
        p3Land = p3Start.lerp(p3End, 0.55);
        p3Land = new Vec3(p3Land.x, floor, p3Land.z);
        p3Yaw = (float) (Mth.atan2(-away.z, -away.x) * Mth.RAD_TO_DEG) - 90F;      // facing back the way he flies from
        // (the band plays on, muffled in his ears, until he stares them down in the glare)
        crowdReact(level, CrowdMood.GASP, 40);
        level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.4F);
        Fx.hit(level, position().add(0, getBbHeight() * 0.6, 0), 2.2F, Fx.WHITE);
        PaponNetwork.shake(level, position(), 48, 0.6F, 14);
        setBossState(BossState.P3_KNOCKBACK);
        setNoGravity(true);
        noPhysics = true;
        PaponMod.LOG.info("[boss] phase three: knocked back from {} to {}", p3Start, p3End);
    }

    /** The ground under {@code p} (no coliseum): the first solid block within 24 below. */
    private double groundBelow(ServerLevel level, Vec3 p) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(p.add(0, 0.5, 0), p.add(0, -24, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? p.y : hit.getLocation().y;
    }

    /** Facing back the way the hit came from (he is flung away from it, looking at it). */
    private void holdYaw() {
        setYRot(p3Yaw);
        yBodyRot = p3Yaw;
        yHeadRot = p3Yaw;
    }

    /**
     * The knockback (about 7 s; the user: "make him look weaker, like he is taking a beating"): flung back through the air
     * (0-0.7 s, the arc is his motion), down on his back on the sand (a crater of dust, the ground shakes), a skid that
     * carves a furrow (to 1.3 s); flat out, his chest heaving; an arm pushes him up and gives way (knock.fall); he rolls
     * onto his side, spits blood, gets a knee under him (knock.knee): "Hhh... that one... HURT."; and up, swaying.
     */
    private void tickP3Knockback(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (p3Start == null || p3Land == null || p3End == null) {
            p3Start = position();
            p3Land = position();
            p3End = position();
        }
        holdYaw();
        int fall = beat("knock.fall", 54), knee = beat("knock.knee", 76), hurt = beat("knock.hurt", 84), end = beat("knock.end", 150);
        if (t <= 14) {
            double k = t / 14.0;
            double e = 1 - (1 - k) * (1 - k);
            Vec3 at = p3Start.lerp(p3Land, e);
            double y = Mth.lerp(k * k, p3Start.y, p3Land.y) + 1.6 * 4 * k * (1 - k);
            setPos(at.x, y, at.z);
        } else if (t <= 26) {
            double k = (t - 14) / 12.0;
            double e = 1 - (1 - k) * (1 - k) * (1 - k);
            Vec3 at = p3Land.lerp(p3End, e);
            setPos(at.x, p3End.y, at.z);
            if (t % 2 == 0) {
                Fx.dust(level, position(), 0.8, 3);
                Fx.chunks(level, position().add(0, 0.2, 0), 2, 0.25F, 0xE8CF94, 0.15);
            }
            if (t % 4 == 1) level.playSound(null, getX(), getY(), getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
        }
        if (t == 14) {
            landImpact(level, position(), 4.5F, 0F, 0.9F);
            Fx.dust(level, position(), 3.2, 16);
            Fx.chunks(level, position().add(0, 0.4, 0), 14, 0.35F, 0xE8CF94, 0.4);
            crowdReact(level, CrowdMood.GASP, 30);
        }
        if (t == 26) {
            noPhysics = false;
            setNoGravity(false);
        }
        if (t == 34) crowdMood(level, CrowdMood.HUSH);
        // flat out: his breath heaving, a groan
        if (t > 26 && t < end && t % 18 == 8) {
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.HORSE_BREATHE, SoundSource.HOSTILE, 1.4F, 0.55F);
        }
        if (t == 40) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 1.0F, 0.6F);
        if (t == fall) {
            // the arm gives way: back down on the sand
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4F, 0.5F);
            Fx.dust(level, position(), 1.6, 8);
            PaponNetwork.shake(level, position(), 32, 0.2F, 6);
            crowdReact(level, CrowdMood.GASP, 16);
        }
        if (t == knee - 6 || t == hurt + 30) {
            // he spits blood onto the sand
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            Vec3 at = position().add(f.scale(1.4));
            Fx.splat(level, new Vec3(at.x, floorNear(at) + 0.02, at.z), 0.5F, 0x8A0E0E, 220);
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1.0F, 0.5F);
        }
        if (t == knee) level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.2F, 0.6F);
        if (t == hurt) sayPlain("p3_hurt", 60);
        if (t >= end) {
            noPhysics = false;
            setNoGravity(false);
            boolean band = inColiseum() && singer(level) != null;
            setBossState(band ? BossState.P3_GLARE : BossState.P3_DANCE);
        }
    }

    /**
     * Up, and through his eyes: everything swimming, the band's song muffled in his ears; "What... is that... NOISE?"; his
     * eyes find the band stand and pull it into focus, zooming in on them (glare.focus); they see him staring and stop
     * dead, scared (glare.scare), a maraca slips and rolls. Then his face, furious: "This is NOT my style... who do you
     * think I AM?!" A beat. "Let me show you how to REALLY play."
     */
    private void tickP3Glare(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        MariachiEntity singer = singer(level);
        int noise = beat("glare.noise", 34), focus = beat("glare.focus", 84), scare = beat("glare.scare", 114),
                face = beat("glare.face", 148), ns = beat("glare.notstyle", 158);
        if (t < noise) {
            // unsteady on his feet, his head lolling round the bowl
            float sway = Mth.sin(t * 0.09F) * 25F;
            setYRot(p3Yaw + sway);
            yHeadRot = getYRot();
        } else if (singer != null) { turnToward(singer.position(), t < focus ? 2.5F : 6F); }
        if (t % (t < scare ? 26 : 20) == 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4F, 0.8F);
        }
        if (t == noise) sayPlain("p3_noise", 60);
        if (t == scare) {
            // they see him staring: the song stops dead, instruments frozen, then they cower
            for (MariachiEntity m : band(level)) {
                m.stop();
                m.scare(6 + random.nextInt(16));
            }
            if (singer != null) singer.blurt("p3_stop", 50);
            crowdMood(level, CrowdMood.HUSH);
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        if (t == beat("glare.maraca", scare + 14)) {
            MariachiEntity coro = null;
            for (MariachiEntity m : band(level)) if (m.getRole().coro()) coro = m;
            Vec3 at = coro != null ? coro.position() : standFront();
            level.playSound(null, at.x, at.y + 1, at.z, SoundEvents.BAMBOO_WOOD_FALL, SoundSource.NEUTRAL, 1.0F, 1.4F);
            for (int i = 0; i < 4; i++) {
                level.playSound(null, at.x, at.y + 0.3, at.z, SoundEvents.NOTE_BLOCK_SNARE.value(), SoundSource.NEUTRAL, 0.35F - i * 0.07F, 1.8F);
            }
        }
        if (t == face) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.3F);
        if (t == ns) {
            sayPlain("notstyle", 80);
            PaponNetwork.shake(level, position(), 40, 0.25F, 10);
            crowdReact(level, CrowdMood.GASP, 20);
        }
        if (t == beat("glare.realplay", ns + 60)) sayPlain("realplay", 50);
        if (t >= beat("glare.end", BossState.P3_GLARE.duration)) setBossState(BossState.P3_STALK);
    }

    /** Over to the band stand, slow and certain, every step a thud the stands can feel. */
    private void tickP3Stalk(ServerLevel level) {
        Vec3 to = wakeSpot();
        double d = horizontalDist(to);
        MariachiEntity singer = singer(level);
        if (d < 0.6 || stateTicks > 300) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            setBossState(BossState.P3_SNATCH);
            return;
        }
        getNavigation().stop();
        getMoveControl().setWantedPosition(to.x, to.y, to.z, d > 2.5 ? 1.05 : 0.45 + d * 0.24);
        if (singer != null && d < 5) turnToward(singer.position(), 8F);
        if (stateTicks % 12 == 6) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.6F, 0.55F);
            PaponNetwork.shake(level, position(), 40, 0.14F, 5);
            Fx.dust(level, position(), 0.6, 2);
        }
        if (stateTicks == 30) for (MariachiEntity m : band(level)) if (!m.getRole().singer()) m.scare(10 + random.nextInt(40));
    }

    /** Looming over the singer: "Give me THAT." and the requinto ripped out of his hands on the word. */
    private void tickP3Snatch(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        MariachiEntity singer = singer(level);
        if (singer != null) face(singer.position());
        if (t == beat("snatch.gimme", 6)) sayPlain("gimme", 30);
        if (t == beat("snatch.grab", 22)) {
            if (singer != null) {
                singer.rob();
                Vec3 at = singer.position().add(0, 1.0, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.NEUTRAL, 1.6F, 0.6F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.NEUTRAL, 1.2F, 0.75F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.6F);
            }
            for (MariachiEntity m : band(level)) if (!m.getRole().singer()) m.scare(random.nextInt(6));
            crowdReact(level, CrowdMood.GASP, 30);
            PaponNetwork.shake(level, position(), 40, 0.2F, 8);
        }
        if (t >= beat("snatch.end", BossState.P3_SNATCH.duration)) {
            braziers = arena != null ? arena.braziers() : new ArrayList<>();
            braziersOut = 0;
            returnFrom = horizontalDist(arena != null ? arena.centerVec() : position());
            setBossState(BossState.P3_RETURN);
        }
    }

    /**
     * Back to the middle of the sand, the guitar hanging from his fist by the neck. As he goes the braziers
     * go out one after another round the bowl, a wave of dark following him in: by the time he gets there
     * the bowl is lit only by what is left of the day.
     */
    private void tickP3Return(ServerLevel level) {
        Vec3 c = arena != null ? arena.centerVec() : position();
        Vec3 to = new Vec3(c.x, arena != null ? arena.floorY() : getY(), c.z);
        double d = horizontalDist(to);
        double f = returnFrom <= 0.1 ? 1 : Mth.clamp(1 - d / returnFrom, 0, 1);
        lightsOut(level, (int) Math.floor(f * 0.8 * braziers.size()));
        entityData.set(DATA_NIGHT, (float) (f * 0.6));
        if (d < 0.6 || stateTicks > 400) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            MariachiEntity singer = singer(level);
            if (singer != null) face(singer.position());
            setBossState(BossState.P3_BLACKOUT);
            return;
        }
        getNavigation().stop();
        getMoveControl().setWantedPosition(to.x, to.y, to.z, d > 2 ? 1.0 : 0.4 + d * 0.3);
        if (stateTicks % 12 == 6) level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    /** Puts braziers out in order round the bowl until {@code upTo} are out, each with a hiss and a puff of smoke. */
    private void lightsOut(ServerLevel level, int upTo) {
        upTo = Math.min(upTo, braziers.size());
        while (braziersOut < upTo) {
            BlockPos p = braziers.get(braziersOut++);
            BlockState s = level.getBlockState(p);
            if (!s.is(ModBlocks.ARENA_BRAZIER.get())) continue;
            level.setBlock(p, s.setValue(com.groid.papon.block.ArenaBrazierBlock.LIT, false), Block.UPDATE_CLIENTS);
            level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.6F, 0.6F + random.nextFloat() * 0.2F);
            Fx.smoke(level, Vec3.atCenterOf(p).add(0, 0.6, 0), 3, 0.2, 1.0F, 0x3A3A3A);
        }
    }

    /** Every brazier back, crimson: the fire roars back up round the whole bowl at once. */
    private void lightsCrimson(ServerLevel level) {
        if (arena == null) return;
        if (braziers.isEmpty()) braziers = arena.braziers();
        int i = 0;
        for (BlockPos p : braziers) {
            BlockState s = level.getBlockState(p);
            if (!s.is(ModBlocks.ARENA_BRAZIER.get())) continue;
            level.setBlock(p, s.setValue(com.groid.papon.block.ArenaBrazierBlock.LIT, true).setValue(com.groid.papon.block.ArenaBrazierBlock.CRIMSON, true),
                    Block.UPDATE_CLIENTS);
            if (i++ % 3 == 0) {
                Fx.spark(level, Vec3.atCenterOf(p).add(0, 0.8, 0), 6, 0.3, 0.4F, 0xFF2A34);
                level.playSound(null, p, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.6F + random.nextFloat() * 0.3F);
            }
        }
        braziersOut = 0;
    }

    /**
     * The last braziers out and black (0-0.8 s); a second of nothing; then the spotlight slams on from high
     * over the sand (1.5 s: a heavy clunk), straight down on him: head bowed, the guitar coming round on its
     * strap into his hands. The stands hold their breath.
     */
    private void tickP3Blackout(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        if (t == 1) {
            lightsOut(level, braziers.size());
            crowdMood(level, CrowdMood.HUSH);
        }
        entityData.set(DATA_NIGHT, Math.min(1F, 0.6F + t / 16F * 0.4F));
        if (t == 30) {
            entityData.set(DATA_SPOT, true);
            level.playSound(null, getX(), getY() + 8, getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, getX(), getY() + 8, getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
            level.playSound(null, getX(), getY() + 8, getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 2.0F, 0.5F);
            PaponNetwork.shake(level, position(), 48, 0.18F, 6);
        }
        if (t >= BossState.P3_BLACKOUT.duration) setBossState(BossState.P3_ASCEND);
    }

    /**
     * In the spotlight he plays the song's opening on the singer's requinto and sings its first lines
     * himself (the record's own guitar and voice: client/SongPlayer plays it, BossModel and BossFace put it
     * in his hands and on his lips). The stands light up a lighter at a time, row by row, until the whole
     * bowl is a sky of little flames; at the first sung word they sway.
     */
    private void tickP3Serenade(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        entityData.set(DATA_NIGHT, Math.max(0.85F, 1F - t / 120F * 0.15F));
        ColiseumCrowdEntity crowd = crowd(level);
        if (crowd != null) {
            if (t == 2) crowd.setMood(CrowdMood.LIGHTERS);
            crowd.setLighters(Mth.clamp((t - 30) / 170F, 0F, 1F));
        }
        if (t >= beat("serenade.end", BossState.P3_SERENADE.duration)) setBossState(BossState.P3_VERDICT);
    }

    /**
     * The ascension, in the spotlight before his song: he doubles over, the guitar swinging, and groans (ascend.groan1),
     * worse (groan2), his back heaving, the light coming out of him in cracks, lightning round the bowl; then the
     * wings tear out of his back (ascend.burst): a flash, a thunderclap, his new form; and the roar (ascend.roar),
     * the wings spreading. The band and the stands can only watch.
     */
    private void tickP3Ascend(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        int g1 = beat("ascend.groan1", 14), g2 = beat("ascend.groan2", 60), burst = beat("ascend.burst", 100), roar = beat("ascend.roar", 108);
        if (t == 2) {
            crowdReact(level, CrowdMood.GASP, 60);
            for (MariachiEntity m : band(level)) m.scare(random.nextInt(8));
        }
        if (t == g1) {
            sayPlain("groan1", 40);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 1.6F, 0.5F);
        }
        if (t == g2) {
            sayPlain("groan2", 40);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.6F, 0.5F);
        }
        if (t < burst && t % 7 == 3) {
            // his back heaving: a crunch, sparks off it, the shake growing
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F + t / (float) burst * 0.3F);
            Fx.spark(level, position().add(0, getBbHeight() * 0.6, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(-0.9)), 4, 0.4, 0.3F, 0xFFE07A);
            PaponNetwork.shake(level, position(), 48, 0.05F + 0.3F * t / burst, 6);
        }
        if (t > g1 && t < burst && t % 18 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            bolt(level, position().add(Math.cos(a) * (6 + random.nextDouble() * 10), 0, Math.sin(a) * (6 + random.nextDouble() * 10)));
        }
        if (t == burst) {
            entityData.set(DATA_FORM, 3);
            refreshDimensions();
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 4.0F, 0.8F);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.5F);
            Fx.shock(level, position(), 10F, 0xFFE07A);
            Fx.spark(level, position().add(0, getBbHeight() * 0.6, 0), 50, 2.2, 0.6F, 0xFFF2C0);
            Fx.chunks(level, position().add(0, getBbHeight() * 0.7, 0), 16, 0.35F, 0xC88A3A, 0.5);
            PaponNetwork.shake(level, position(), 64, 1.0F, 26);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i + 0.4;
                bolt(level, position().add(Math.cos(a) * 5, 0, Math.sin(a) * 5));
            }
            crowdReact(level, CrowdMood.GASP, 40);
            PaponMod.LOG.info("[boss] Ascended Papon");
        }
        if (t == roar) {
            sayPlain("ascend", 50);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5F, 0.7F);
            PaponNetwork.shake(level, position(), 64, 0.7F, 30);
        }
        if (t == beat("ascend.cut", g1 + 20)) {
            // the band clutching each other as he groans
            for (MariachiEntity m : band(level)) m.scare(random.nextInt(6));
            List<MariachiEntity> b = band(level);
            if (!b.isEmpty()) b.get(random.nextInt(b.size())).blurt("p3_dios", 40);
        }
        int crowdAt = beat("ascend.crowd", roar + 40), bandAt = beat("ascend.band", crowdAt + 34);
        if (t == crowdAt) {
            // the stands on their feet, screaming
            crowdReact(level, CrowdMood.ROAR, 50);
            fireworks(level);
        }
        if (t == bandAt) {
            List<MariachiEntity> b = band(level);
            for (MariachiEntity m : b) m.scare(random.nextInt(4));
            if (b.size() > 1) {
                b.get(random.nextInt(b.size())).blurt("p3_wings", 40);
                b.get(random.nextInt(b.size())).blurt("p3_mama", 30);
            }
        }
        if (t == bandAt + 24) {
            crowdReact(level, CrowdMood.SWOON, 60);
            for (MariachiEntity m : band(level)) m.awe();
        }
        if (t == beat("ascend.god", 162)) {
            // risen, and done playing: "No more games, amigo. Behold... the POTATO GOD."
            sayPlain("p3_god", 80);
            crowdMood(level, CrowdMood.HUSH);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (t >= beat("ascend.end", BossState.P3_ASCEND.duration)) setBossState(BossState.P3_POWER);
    }

    /**
     * Just ascended, the power goes through him and nearly puts him down: he staggers back, catches himself on one foot,
     * looks at his hands, "Whoa, WHOA... so much POWER.", and the grin spreads; then his serenade.
     */
    private void tickP3Power(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (t == 2) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.6F, 1.4F);
            Fx.shock(level, position(), 4F, 0xFFE07A);
            PaponNetwork.shake(level, position(), 40, 0.4F, 10);
        }
        if (t == 8 || t == 16 || t == 26) {
            // stumbling steps, the sand cracking under each
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4F, 0.6F + t * 0.01F);
            Fx.dust(level, position(), 1.0, 5);
            PaponNetwork.shake(level, position(), 32, 0.15F, 5);
        }
        if (t < 30 && t % 3 == 0) Fx.spark(level, position().add(0, getBbHeight() * 0.5, 0), 3, 0.7, 0.35F, 0xFFF2C0);
        if (t == beat("power.line", 22)) sayPlain("p3_power", 60);
        if (t == beat("power.grin", 60)) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.WARDEN_NEARBY_CLOSER, SoundSource.HOSTILE, 1.2F, 0.8F);
            crowdReact(level, CrowdMood.GASP, 20);
        }
        if (t >= beat("power.end", BossState.P3_POWER.duration)) setBossState(BossState.P3_SERENADE);
    }

    /** Ascended Papon (phase three, from his serenade on): the better potato, white eyes, eagle wings. */
    public boolean isAscended() { return form() >= 3; }

    /** The chord dying, the guitar lowered from his chest: "THIS... is more my style. Learn, muchachos." The stands erupt. */
    private void tickP3Verdict(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        LivingEntity target = getTarget();
        // round to his audience, back to the band (the toss goes over his shoulder)
        if (t > 4 && target != null) turnToward(target.position(), 5F);
        if (t == beat("verdict.line", 10)) sayPlain("mystyle", 70);
        if (t == beat("verdict.end", 80) - 8) {
            crowdReact(level, CrowdMood.ROAR, 40);
            ColiseumCrowdEntity crowd = crowd(level);
            if (crowd != null) crowd.setLighters(0.5F);
        }
        if (t >= beat("verdict.end", BossState.P3_VERDICT.duration)) setBossState(BossState.P3_TOSS);
    }

    /**
     * Over his shoulder without a look: the requinto spins through the beam of the spotlight to the singer, who
     * juggles it twice and has it. He whirls round on the band: "What are you WAITING for?! GO!!!"
     */
    private void tickP3Toss(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        MariachiEntity singer = singer(level);
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 4F);
        int flight = Math.max(8, beat("toss.catch", 42) - beat("toss.throw", 20));
        if (t == beat("toss.throw", 20) && singer != null) {
            Vec3 from = position().add(0, getBbHeight() * 0.85, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(-0.6));
            Vec3 to = singer.position().add(0, singer.getBbHeight() * 0.6, 0);
            BossProp g = BossProp.create(level, BossProp.Kind.GUITAR, this, from).toss(to, flight);
            level.addFreshEntity(g);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.8F);
        }
        if (t == beat("toss.catch", 42) && singer != null) {
            singer.catchGuitar();
            level.playSound(null, singer.getX(), singer.getY() + 1, singer.getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
            crowdReact(level, CrowdMood.CHEER, 10);
        }
        if (t == beat("toss.glance", 58)) {
            // the requinto is back... and nobody moves. The stands go quiet; the band look at each other.
            for (MariachiEntity m : band(level)) m.nervous();
            crowdMood(level, CrowdMood.HUSH);
        }
        if (t == beat("toss.talk1", 66) || t == beat("toss.talk2", 88)) {
            // whispering to each other, scared stiff (their own shot: client/Cinematic)
            boolean first = t == beat("toss.talk1", 66);
            for (MariachiEntity m : band(level)) {
                if (first ? m.getRole() == BandRole.GUITAR : m.getRole().singer()) m.blurt(first ? "p3_what" : "p3_play", 36);
                if (!first && m.getRole() == BandRole.VIHUELA) m.blurt("p3_loco", 36);
            }
        }
        if (t >= beat("toss.end", BossState.P3_TOSS.duration)) setBossState(BossState.P3_FELT);
    }

    /**
     * Ascended, he turns to you, slowly, the power going through him; a long breath in: "Hmmm... I haven't felt
     * this way... in a LOOOOONG time." And the smile, evil, held.
     */
    private void tickP3Felt(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 3F);
        if (t == 2) level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (t == beat("felt.line", 14)) sayPlain("felt", 90);
        if (t == beat("felt.smile", 100)) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 1.4F, 0.7F);
            crowdReact(level, CrowdMood.GASP, 20);
        }
        if (t >= beat("felt.end", BossState.P3_FELT.duration)) {
            CaravanEntity van = van(level);
            setBossState(van != null && !van.isVehicle() ? BossState.P3_TO_CAR : BossState.P3_GO);
        }
    }

    /** Over to his van, unhurried. */
    private void tickP3ToCar(ServerLevel level) {
        CaravanEntity van = van(level);
        if (van == null) {
            setBossState(BossState.P3_GO);
            return;
        }
        Vec3 side = van.position().add(van.left().scale(CaravanEntity.HALF_WIDTH + 2.2));
        double d = horizontalDist(side);
        if (stateTicks > 90 && d > 1.2) {
            // could not get there (the van against the wall, his wings in the way): he is simply there
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 1.6F);
            setFlying(false);
            noPhysics = false;
            setPos(side.x, van.getY(), side.z);
            setDeltaMovement(Vec3.ZERO);
            d = 0;
        }
        if (d > 3) {
            // a few beats of his new wings: low over the sand straight to it
            setFlying(true);
            getNavigation().stop();
            Vec3 to = new Vec3(side.x, Math.max(side.y, van.getY()) + 1.5, side.z);
            Vec3 dv = to.subtract(position());
            double len = dv.length();
            setDeltaMovement(getDeltaMovement().scale(0.5).add(dv.scale(Math.min(1.0, len * 0.25) / Math.max(1e-3, len)).scale(0.5)));
            face(van.position());
            return;
        }
        setFlying(false);
        if (d < 1.2 || stateTicks > 200) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            face(van.position());
            liftFrom = van.position();
            van.ejectPassengers();
            van.setCarried(true);
            setBossState(BossState.P3_CRUSH);
            return;
        }
        getNavigation().stop();
        getMoveControl().setWantedPosition(side.x, side.y, side.z, d > 2.5 ? 0.9 : 0.5 + d * 0.15);
        if (d < 5) face(van.position());
        if (stateTicks % 12 == 6) level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4F, 0.55F);
    }

    /**
     * His car: up over his head (to crush.lifted), then crushed in his hands into a ball, metal shrieking, glass
     * bursting (to crush.ball); held up, glowing purple; and reformed in a flash (crush.reform) into a black
     * Nissan Skyline with a flaming eagle on the hood, which grows back to size in his hands as he brings it
     * down and slams it on its wheels in front of him (crush.down), the underglow coming on; it revs; and, by it,
     * very quietly: "Now... let's DANCE."
     */
    private void tickP3Crush(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        CaravanEntity van = van(level);
        if (van == null) {
            setBossState(BossState.P3_GO);
            return;
        }
        int lifted = beat("crush.lifted", 14), squeeze = beat("crush.squeeze", 18), ball = beat("crush.ball", 42),
                reform = beat("crush.reform", 52), down = beat("crush.down", 66);
        if (t == beat("crush.upgrade", 6)) {
            // a hand on the old van's roof: "It's about time I gave this thing an UPGRADE."
            sayPlain("upgrade", 70);
            level.playSound(null, van.getX(), van.getY() + 1, van.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.0F, 1.4F);
        }
        if (t == beat("crush.out", 40)) sayPlain("p3_out", 40);
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        Vec3 over = position().add(0, getBbHeight() + 0.6, 0);
        Vec3 fist = position().add(f.scale(0.6)).add(0, getBbHeight() + 0.2, 0);
        Vec3 ground = position().add(f.scale(5.0));
        ground = new Vec3(ground.x, inColiseum() && arena != null ? arena.floorY() : groundBelow(level, ground), ground.z);
        Vec3 at;
        int liftAt = Math.max(0, lifted - 14);           // after the line: up it goes
        if (t <= lifted) {
            double k = Mth.clamp((t - liftAt) / (double) Math.max(1, lifted - liftAt), 0, 1);
            k = k * k * (3 - 2 * k);
            Vec3 from = liftFrom != null ? liftFrom : van.position();
            at = from.lerp(over, k);
            if (t == liftAt + 6) level.playSound(null, van.getX(), van.getY(), van.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.5F, 0.5F);
        } else if (t < reform) {
            at = t < squeeze ? over : over.lerp(fist, Mth.clamp((t - squeeze) / (double) (ball - squeeze), 0, 1));
            if (t >= squeeze && t <= ball) {
                double k = (t - squeeze) / (double) (ball - squeeze);
                van.setSquash((float) (1 - 0.86 * k * k * (3 - 2 * k)));
                if (t % 5 == 0) {
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.6F + random.nextFloat() * 0.6F);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 0.5F + random.nextFloat() * 0.3F);
                    Fx.chunks(level, at, 4, 0.3F, 0x283E70, 0.35);
                    PaponNetwork.shake(level, position(), 40, 0.22F, 5);
                }
                if (t % 9 == 0) {
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2F, 0.8F + random.nextFloat() * 0.4F);
                    Fx.chunks(level, at, 5, 0.2F, 0xCFE6F0, 0.45);
                }
            }
            if (t > ball && t % 3 == 0) Fx.spark(level, at, 4, 0.3, 0.35F, 0xB050FF);
        } else {
            if (t == reform) {
                van.setSkyline(true);
                van.setUnderglow(false);             // dark until he shows it off (P3_SHOWCAR)
                level.playSound(null, at(fist), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 1.5F);
                level.playSound(null, at(fist), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 1.3F);
                Fx.shock(level, fist, 6F, 0xB050FF);
                Fx.spark(level, fist, 30, 0.8, 0.5F, 0xD8A0FF);
                PaponNetwork.shake(level, position(), 48, 0.5F, 12);
                crowdReact(level, CrowdMood.ROAR, 50);
            }
            double k = Mth.clamp((t - reform) / (double) (down - reform), 0, 1);
            double e = k * k * (3 - 2 * k);
            van.setSquash((float) (0.14 + 0.86 * e));
            at = fist.lerp(ground, k * k);
            if (t == down) {
                van.setCarried(false);
                van.setPos(ground.x, ground.y + 0.05, ground.z);
                van.setDeltaMovement(Vec3.ZERO);
                van.setSquash(1F);
                level.playSound(null, ground.x, ground.y, ground.z, ModSounds.CARAVAN_CRASH.get(), SoundSource.HOSTILE, 1.6F, 1.3F);
                level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 1.4F);
                Fx.dust(level, ground, 3.0, 12);
                PaponNetwork.shake(level, ground, 48, 0.6F, 14);
            }
        }
        if (t <= down) {
            van.setPos(at.x, at.y, at.z);
            van.setYRot(getYRot() + 90F);
            van.setDeltaMovement(Vec3.ZERO);
        }
        LivingEntity target = getTarget();
        if (t > down + 6 && target != null) turnToward(target.position(), 4F);
        if (t >= beat("crush.end", 96)) setBossState(BossState.P3_SHOWCAR);
    }

    /**
     * The Skyline, shown off (beats showcar.*; client/Cinematic cuts round it on the rev): in the dust of the slam it
     * sits dark; the fob chirps, the engine catches and the lamps come on; the underglow stutters on front to back;
     * blip, blip and flat out, flames out of the twin pipes, the blow-off, crackling on the overrun; and him, by it,
     * with a hand swept out to it: "...and in with the NEW." The stands go up.
     */
    private void tickP3ShowCar(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        CaravanEntity van = van(level);
        if (van == null) {
            setBossState(BossState.P3_GO);
            return;
        }
        face(van.position());
        int keys = beat("showcar.keys", 10), lamps = beat("showcar.lamps", 16), glow = beat("showcar.glow", 34),
                rev = beat("showcar.rev", 56), bov = beat("showcar.bov", 110), line = beat("showcar.line", 128);
        if (t == 1) crowdMood(level, CrowdMood.HUSH);
        if (t == keys) level.playSound(null, van.getX(), van.getY() + 1, van.getZ(), ModSounds.SKYLINE_CHIRP.get(), SoundSource.HOSTILE, 1.6F, 1.0F);
        if (t == lamps) {
            van.idleFor(600);                     // it idles on, lamps lit, through the rest of the scene
            level.playSound(null, van.getX(), van.getY(), van.getZ(), ModSounds.SKYLINE_START.get(), SoundSource.HOSTILE, 2.2F, 1.0F);
            PaponNetwork.shake(level, van.position(), 32, 0.12F, 8);
        }
        if (t == glow) {
            van.setUnderglow(true);
            level.playSound(null, van.getX(), van.getY(), van.getZ(), ModSounds.SKYLINE_GLOW.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
            crowdReact(level, CrowdMood.GASP, 20);
        }
        if (t > glow && t < glow + 12 && t % 2 == 0) {
            // sparks off the skirts as the tubes catch, front to back
            double k = (t - glow) / 12.0;
            for (double side : new double[]{-1, 1}) {
                Fx.spark(level, van.local(side * (CaravanEntity.HALF_WIDTH + 0.1), 0.15, CaravanEntity.HALF_LENGTH * (1 - 2 * k)), 3, 0.15, 0.2F, 0xB050FF);
            }
        }
        if (t == rev) { level.playSound(null, van.getX(), van.getY(), van.getZ(), ModSounds.SKYLINE_REV.get(), SoundSource.HOSTILE, 3.0F, 1.0F); }
        // the pops after the blips, the flames on the pull and all through the overrun (the rev sound's own timing)
        int r = t - rev;
        if (r == 8 || r == 20 || (r >= 58 && r <= 84 && r % 4 == 2)) backfire(level, van, r >= 58 ? 0.7F : 1F);
        if (r >= 29 && r < 54 && r % 3 == 0) {
            PaponNetwork.shake(level, van.position(), 32, 0.05F + 0.25F * (r - 29) / 25F, 4);
            Fx.dust(level, van.local(0, 0, -CaravanEntity.HALF_LENGTH - 0.6), 0.8, 2);
        }
        if (r == 54) PaponNetwork.shake(level, van.position(), 40, 0.4F, 8);
        if (t == bov) crowdReact(level, CrowdMood.ROAR, 16);
        if (t == line) sayPlain("p3_new", 50);
        if (t == beat("showcar.roar", 158)) {
            crowdRoar(level, 50);
            salvo(level, 4);
            level.playSound(null, van.getX(), van.getY(), van.getZ(), ModSounds.SKYLINE_BOV.get(), SoundSource.HOSTILE, 1.4F, 1.1F);
        }
        if (t >= beat("showcar.end", BossState.P3_SHOWCAR.duration)) {
            crowdMood(level, CrowdMood.CHEER);
            setBossState(BossState.P3_GO);
        }
    }

    /** A bang out of the Skyline's twin pipes: a tongue of fire (purple at its heart) and the pop. */
    private void backfire(ServerLevel level, CaravanEntity van, float size) {
        Vec3 back = van.forward().scale(-1);
        for (double side : new double[]{-0.42, 0.42}) {
            Vec3 pipe = van.local(side, 0.32, -CaravanEntity.HALF_LENGTH - 0.15);
            level.sendParticles(ParticleTypes.FLAME, pipe.x, pipe.y, pipe.z, 0, back.x, 0.02, back.z, 0.35 * size);
            for (int i = 0; i < 5; i++) {
                double k = 0.15 + 0.12 * i;
                level.sendParticles(ParticleTypes.FLAME, pipe.x + back.x * k, pipe.y, pipe.z + back.z * k, 1, 0.04, 0.04, 0.04, 0.02);
            }
            Fx.spark(level, pipe.add(back.scale(0.3)), 3, 0.1, 0.25F, 0xB050FF);
            level.sendParticles(ParticleTypes.SMOKE, pipe.x + back.x * 0.6, pipe.y, pipe.z + back.z * 0.6, 2, 0.05, 0.05, 0.05, 0.01);
        }
        level.playSound(null, van.getX(), van.getY(), van.getZ(), ModSounds.SKYLINE_BACKFIRE.get(), SoundSource.HOSTILE, 1.6F * size,
                0.85F + random.nextFloat() * 0.3F);
    }

    private static net.minecraft.core.BlockPos at(Vec3 v) {
        return net.minecraft.core.BlockPos.containing(v);
    }

    /** He whirls round on the band: "What are you WAITING for?! GO!!!", and the singer takes the song back. */
    private void tickP3Go(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        MariachiEntity singer = singer(level);
        if (singer != null) turnToward(singer.position(), 30F);
        if (t == beat("go.line", 8)) {
            sayPlain("go", 50);
            for (MariachiEntity m : band(level)) m.ready();
            PaponNetwork.shake(level, position(), 48, 0.35F, 10);
        }
        if (t >= beat("go.end", BossState.P3_GO.duration)) {
            // the singer takes the song back from its pickup, now; the band comes in on the downbeat after it
            songStart = (int) (level.getGameTime() & 0x7FFFFFFF);
            songId = Song.PARA_SIEMPRE;
            for (MariachiEntity m : band(level)) m.play(songStart, Song.PARA_SIEMPRE);
            setBossState(BossState.P3_DANCE);
        }
    }

    /**
     * The singer's pickup, alone (2 s); the band crashes in on the downbeat and with it the fire, crimson, round
     * the whole bowl at once; fireworks, the place shaking. He rolls his shoulders, cracks his neck, and very
     * quietly: "Now... let's DANCE." The rest of the fight is at night, in his spotlight.
     */
    private void tickP3Dance(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        LivingEntity target = getTarget();
        boolean band = inColiseum() && !band(level).isEmpty() && songId == Song.PARA_SIEMPRE;
        int drop = band ? beat("dance.drop", 42) : 4;
        int line = band ? beat("dance.line", drop + 44) : 20;
        int end = band ? beat("dance.end", BossState.P3_DANCE.duration) : line + 60;
        if (target != null) turnToward(target.position(), t < drop ? 3F : 8F);
        if (t == drop) {
            entityData.set(DATA_PHASE3, true);
            bossEvent.setName(Component.translatable(BAR_KEY + ".phase3"));
            if (!isAscended()) {
                // no coliseum, so no ascension scene: he takes his form here, in a flash, all the same
                entityData.set(DATA_FORM, 3);
                refreshDimensions();
                Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 4F);
            }
            if (inColiseum()) {
                lightsCrimson(level);
                entityData.set(DATA_NIGHT, NIGHT_P3);
                entityData.set(DATA_SPOT, true);
                fireworks(level);
                crowdMood(level, CrowdMood.CHEER);
                crowdReact(level, CrowdMood.ROAR, 70);
                ColiseumCrowdEntity crowd = crowd(level);
                if (crowd != null) crowd.setLighters(0F);
            }
            PaponNetwork.shake(level, position(), 48, 0.8F, 26);
            Fx.shock(level, position(), 8F, 0xFF2A34);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.8F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (t == line) sayPlain("dance", 50);
        if (t == line + 20) title(level, "title.papon.para_siempre", 40);
        if (t >= end) {
            if (!isPhaseThree()) {
                entityData.set(DATA_PHASE3, true);
                bossEvent.setName(Component.translatable(BAR_KEY + ".phase3"));
            }
            finishCutscene(level);
        }
    }

    /** How dark the coliseum stays through phase three (the braziers and his spotlight light it). */
    private static final float NIGHT_P3 = 0.8F;

    public boolean isPhaseThree() { return entityData.get(DATA_PHASE3); }

    /** How dark it is round him (client: the lightmap, the sky, the fog), 0 day .. 1 pitch black. */
    public float night() { return entityData.get(DATA_NIGHT); }

    /** The spotlight is on him (from the blackout on, through phase three). */
    public boolean spotlit() { return entityData.get(DATA_SPOT); }

    /** Client: the serenade's place in the song (seconds into the recording), or NaN outside it. */
    public double serenadeTime(float partial) {
        if (getBossState() != BossState.P3_SERENADE) return Double.NaN;
        return (ticksInState() + partial - VoiceLines.beat("serenade.start", 4)) / 20.0;
    }

    private void finishCutscene(ServerLevel level) {
        think = 6;
        vanCd = Math.max(vanCd, 240);
        crowdCd = 12;
        if (foils.isEmpty()) spawnFoils(level);
        setBossState(BossState.IDLE);
    }

    // ------------------------------------------------------------------ phase three: Ascended Papon's moves

    /*
     * From the band's drop on he is Ascended Papon, a demigod, and fights like one: none of the chef's tricks (no
     * spatula, no cleavers, no comal, no tequila), only what a buff golden potato god who flies twice as fast does.
     * The big ones take a share of your health (Mechanics.trueHit) and each has a way out: the Skyline is cover from
     * his eyes and from the sun, his own shadow from the supernova, a jump from the trumpets, a duck from the violins.
     */

    // cooldowns (ticks); each move's own state between ticks
    private int aComboCd = 30, aFlashCd = 60, aCometCd = 140, aLaserCd = 160, aSolarCd = 520, aMariachiCd = 320, aDuelCd = 500,
            aPinataCd = 380, aBrazierCd = 240, aNovaCd = 400, aNitroCd = 520, aSpamCd = 300, aTangoCd = 380, aSombreroCd = 200,
            aVolcanCd = 300, aCalaveraCd = 260;
    private int flashLeft, flashT;
    private double laserPhase;
    @Nullable private Vec3 flashFrom, flashTo;
    private int cometsLeft;
    @Nullable private Vec3 cometAt;
    /** The beams in the air (BeamEntity), and where each is headed on the ground. */
    private final List<UUID> beams = new ArrayList<>();
    private final List<Vec3> beamAims = new ArrayList<>();
    @Nullable private Vec3 solarFrom;
    /** The band's blasts rolling across the sand: x, z, radius, kind (0 a trumpet's: jump it, 1 a violin's: duck it), heading, id. */
    private final List<double[]> bandWaves = new ArrayList<>();
    /** Salsa rings thrown out by the salsa burst, growing until they are gone. */
    private final List<UUID> salsaRings = new ArrayList<>();
    /** The band's blasts as drawn (BossProp BAND_WAVE), by wave id. */
    private final java.util.Map<Integer, UUID> bandWaveProps = new java.util.HashMap<>();
    private final java.util.Set<Long> bandWaveHits = new java.util.HashSet<>();
    private int bandWaveIds, beatsFired;
    private List<BlockPos> podiumBraziers = new ArrayList<>();
    private int brazierNext;
    /** Ascended at the wheel of the Skyline: the nitro on (no molotovs; purple fire behind the donuts; rams take a share). */
    private boolean nitro;
    /** The guitar duel: the challenger, the chart's length (ticks), the robbed guitarist (entity id), which leg of the fetch,
     *  the result (-1 waiting, 0 he won, 1 you won) and your score (0..100). */
    @Nullable private UUID duelWho;
    private int duelLen, duelGuitarist = -1, duelPhase, duelResult = -1, duelScore;

    /** Ascended Papon (phase three's fight, after the cutscene): his own moveset. */
    private boolean ascendedFight() { return isAscended() && isPhaseThree(); }

    /**
     * What Ascended Papon does next, by range and what is ready (and then the same weighted deck as ever): up close the
     * golden fists or the squeeze; at any range the lightning dashes, the comet, the eye lasers, the braziers, the piñata;
     * in his coliseum the sun stone, the mariachi's fire and the guitar duel; the Skyline when it is free; and once he is
     * under 40%, the supernova.
     */
    @Nullable
    private BossState chooseAscended(ServerLevel level, LivingEntity target, double d) {
        java.util.List<BossState> o = new java.util.ArrayList<>();
        boolean player = target instanceof Player;
        boolean col = inColiseum() && arena != null;
        CaravanEntity van = van(level);
        boolean vanFree = van != null && !van.isVehicle() && !van.isSliding() && brickTicks <= 0 && !van.isFlying();
        if (d < 8 && aComboCd <= 0) o.add(BossState.P3_COMBO);
        if (d < 9 && player && !target.isPassenger() && (aSpamCd <= 0 || (col && aTangoCd <= 0))) o.add(BossState.QTE_GRAB);
        if (d >= 4 && aFlashCd <= 0) o.add(BossState.P3_FLASH);
        if (d >= 5 && aCometCd <= 0) o.add(BossState.P3_COMET);
        if (aLaserCd <= 0) o.add(BossState.P3_LASER);
        if (aBrazierCd <= 0) o.add(BossState.P3_BRAZIERS);
        if (aPinataCd <= 0) o.add(BossState.P3_PINATA_SWING);
        if (aSombreroCd <= 0) o.add(BossState.P3_SOMBRERO);
        if (aVolcanCd <= 0) o.add(BossState.P3_VOLCAN);
        if (aCalaveraCd <= 0 && d < 30) o.add(BossState.P3_CALAVERAS);
        if (col && aSolarCd <= 0) o.add(BossState.P3_SOLAR);
        if (col && aMariachiCd <= 0 && !band(level).isEmpty()) o.add(BossState.P3_MARIACHI);
        if (col && aDuelCd <= 0 && player && !target.isPassenger() && guitarist(level) != null) o.add(BossState.P3_DUEL_FETCH);
        if (aNovaCd <= 0 && getHealth() < getMaxHealth() * 0.4F) o.add(BossState.P3_NOVA);
        if (vanFree && aNitroCd <= 0 && distanceTo(van) < 40 && d > 6) o.add(BossState.P3_NITRO);
        return pickWeighted(o);
    }

    /** Ascended's weights: the spectacles are what the crowd paid for. */
    private static float ascendedWeight(BossState s) {
        return switch (s) {
            case P3_SOLAR, P3_DUEL_FETCH, P3_MARIACHI, P3_NOVA -> 2.0F;
            case P3_PINATA_SWING, P3_SOMBRERO, P3_VOLCAN, P3_CALAVERAS -> 1.9F;
            case P3_PINATA, P3_LASER, P3_COMET -> 1.6F;
            case QTE_GRAB -> 1.4F;
            case P3_NITRO -> 0.5F;
            default -> 1.0F;
        };
    }

    /** The cooldowns Ascended's moves start, and their set-up. */
    private void startAscended(BossState pick, LivingEntity target) {
        switch (pick) {
            case P3_COMBO -> aComboCd = 90;
            case P3_FLASH -> {
                aFlashCd = 170;
                flashLeft = 5;
                flashT = 0;
            }
            case P3_COMET -> {
                aCometCd = 280;
                cometsLeft = 2;
            }
            case P3_LASER -> aLaserCd = 320;
            case P3_SOLAR -> aSolarCd = 900;
            case P3_BRAZIERS -> aBrazierCd = 420;
            case P3_PINATA, P3_PINATA_SWING -> aPinataCd = 640;
            case P3_SOMBRERO -> aSombreroCd = 560;
            case P3_VOLCAN -> aVolcanCd = 720;
            case P3_CALAVERAS -> aCalaveraCd = 620;
            case P3_MARIACHI -> {
                aMariachiCd = 640;
                beatsFired = 0;
            }
            case P3_DUEL_FETCH -> {
                aDuelCd = 1200;
                duelPhase = 0;
                duelGuitarist = -1;
                duelResult = -1;
                duelWho = target.getUUID();
            }
            case P3_NOVA -> aNovaCd = 1300;
            case P3_NITRO -> aNitroCd = 760;
            case QTE_GRAB -> {
                // the squeeze (the spam QTE), or in his coliseum now and then the tango, on the band's beat
                boolean tango = inColiseum() && aTangoCd <= 0 && (aSpamCd > 0 || random.nextFloat() < 0.4F);
                entityData.set(DATA_QTE, tango ? 4 : 5);
                if (tango) aTangoCd = 460;
                else aSpamCd = 380;
            }
            default -> {}
        }
    }

    private void tickAscendedCooldowns() {
        aComboCd--;
        aFlashCd--;
        aCometCd--;
        aLaserCd--;
        aSolarCd--;
        aMariachiCd--;
        aDuelCd--;
        aPinataCd--;
        aBrazierCd--;
        aNovaCd--;
        aNitroCd--;
        aSombreroCd--;
        aVolcanCd--;
        aCalaveraCd--;
        aSpamCd--;
        aTangoCd--;
    }

    /** The floor he fights on: the coliseum's sand, or the ground under {@code near}. */
    private double floorNear(@Nullable Vec3 near) {
        if (inColiseum() && arena != null) return arena.floorY();
        return groundY(near != null ? near : position());
    }

    /** Flies at {@code to}, up to {@code max} blocks a tick, easing in (Ascended flies twice as fast as he did). */
    private void flyTo(Vec3 to, double max) {
        setFlying(true);
        getNavigation().stop();
        Vec3 d = to.subtract(position());
        double len = d.length();
        Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(max, len * 0.25) / len);
        setDeltaMovement(getDeltaMovement().scale(0.4).add(v.scale(0.6)));
    }

    private Vec3 inSand(Vec3 p, double margin, double yMin, double yMax) {
        if (inColiseum() && arena != null && arena.built()) return arena.clamp(p, margin, yMin, yMax);
        return p;
    }

    /** The shortest distance from {@code p} to the segment a-b. */
    private static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double k = len2 < 1e-8 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** Where a segment first meets the van's body (its real footprint, not the square hitbox), or null. */
    @Nullable
    private static Vec3 vanHit(@Nullable CaravanEntity van, Vec3 a, Vec3 b) {
        if (van == null || !van.isAlive()) return null;
        Vec3 o = van.position();
        Vec3 l = van.left(), f = van.forward();
        Vec3 la = new Vec3(a.subtract(o).dot(l), a.y - o.y, a.subtract(o).dot(f));
        Vec3 lb = new Vec3(b.subtract(o).dot(l), b.y - o.y, b.subtract(o).dot(f));
        AABB box = new AABB(-CaravanEntity.HALF_WIDTH, 0, -CaravanEntity.HALF_LENGTH, CaravanEntity.HALF_WIDTH, van.isSkyline() ? 1.5 : 2.0,
                CaravanEntity.HALF_LENGTH);
        if (box.contains(la)) return a;
        var hit = box.clip(la, lb);
        if (hit.isEmpty()) return null;
        double k = hit.get().distanceTo(la) / Math.max(1e-6, lb.distanceTo(la));
        return a.lerp(b, k);
    }

    /** Where a beam from {@code from} toward {@code aim} stops: the ground (or a wall), or the Skyline if it is in the way. */
    private Vec3 traceBeam(ServerLevel level, Vec3 from, Vec3 aim, @Nullable CaravanEntity van) {
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1e-6) return aim;
        Vec3 far = from.add(dir.normalize().scale(90));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        Vec3 end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : hit.getLocation();
        Vec3 v = vanHit(van, from, end);
        return v != null ? v : end;
    }

    /**
     * Whether the sun at {@code sun} can see {@code e} (eyes and chest both in its light). The shade is behind the potato
     * monoliths he raises out of the sand for it (or a wall, or the car's body; no longer inside the car: the user did not
     * want the car to be the way out, it holds too few and can glitch).
     */
    private boolean sunSees(ServerLevel level, Vec3 sun, LivingEntity e, @Nullable CaravanEntity van) {
        for (Vec3 p : new Vec3[]{e.getEyePosition(), e.position().add(0, e.getBbHeight() * 0.5, 0)}) {
            if (vanHit(van, sun, p) != null) continue;
            if (behindMonolith(level, sun, p)) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(sun, p, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, null));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            return true;
        }
        return false;
    }

    /** The potato monoliths standing for the sun stone (BossProp MONOLITH). */
    private final List<UUID> monoliths = new ArrayList<>();

    /** Whether a monolith stands between the sun and {@code p} (each a column 1.4 wide, 5.5 tall). */
    private boolean behindMonolith(ServerLevel level, Vec3 sun, Vec3 p) {
        for (UUID id : monoliths) {
            if (!(level.getEntity(id) instanceof BossProp m) || m.tickCount < 16) continue;
            Vec3 base = m.position();
            Vec3 ab = p.subtract(sun);
            // the segment's nearest approach to the column's axis, in the horizontal plane
            Vec3 h = new Vec3(ab.x, 0, ab.z);
            double len2 = h.lengthSqr();
            if (len2 < 1e-6) continue;
            double k = Mth.clamp(((base.x - sun.x) * ab.x + (base.z - sun.z) * ab.z) / len2, 0, 1);
            Vec3 q = sun.add(ab.scale(k));
            double dx = q.x - base.x, dz = q.z - base.z;
            if (dx * dx + dz * dz < 1.45 * 1.45 && q.y >= base.y - 0.5 && q.y <= base.y + 5.5) return true;
        }
        return false;
    }

    private void clearMonoliths(ServerLevel level) {
        for (UUID id : monoliths) {
            Entity e = level.getEntity(id);
            if (e != null) {
                Fx.dust(level, e.position(), 1.2, 8);
                e.discard();
            }
        }
        monoliths.clear();
    }

    private BeamEntity beam(ServerLevel level, int i) {
        if (i >= beams.size()) return null;
        return level.getEntity(beams.get(i)) instanceof BeamEntity b && b.isAlive() ? b : null;
    }

    private void addBeam(ServerLevel level, Vec3 from, Vec3 aim, float width, int color) { addBeam(level, from, aim, width, color, 0); }

    /** ...out of his eye ({@code eye} 1 right, -1 left; 0 none): the client draws it from the eye itself. */
    private void addBeam(ServerLevel level, Vec3 from, Vec3 aim, float width, int color, int eye) {
        BeamEntity b = BeamEntity.create(level, from, aim, width, color).life(400);
        if (eye != 0) b.eye(this, eye);
        level.addFreshEntity(b);
        beams.add(b.getUUID());
        beamAims.add(aim);
    }

    private void clearBeams(ServerLevel level) {
        for (int i = 0; i < beams.size(); i++) {
            BeamEntity b = beam(level, i);
            if (b != null) b.discard();
        }
        beams.clear();
        beamAims.clear();
    }

    /**
     * His eyes (the ascended model at its render scale, 2x): the eye bones' pivots (4.5 px either side, 19.5 up, 8 forward),
     * where the beams leave from for what they hit; the client draws them from the eyes as rendered.
     */
    private Vec3 eyePos(int side) {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 r = new Vec3(-f.z, 0, f.x);
        double s = isAscended() ? 2.0 : isRisen() ? 1.75 : 1.5;
        return position().add(0, 19.5 / 16 * s, 0).add(f.scale(8.4 / 16 * s)).add(r.scale(4.5 / 16 * s * side));
    }

    /** His golden statues over the gates: the eyes in their faces, looking in over the sand. */
    private List<Vec3> statueEyes() {
        List<Vec3> out = new ArrayList<>();
        if (!inColiseum() || arena == null) return out;
        Vec3 c = arena.centerVec();
        for (int side : new int[]{-1, 1}) out.add(c.add(side * (Coliseum.A + 2.4), Coliseum.PODIUM + 9.6, 0));
        return out;
    }

    @Nullable
    private MariachiEntity guitarist(ServerLevel level) {
        for (MariachiEntity m : band(level)) if (m.getRole() == BandRole.GUITAR && !m.robbed()) return m;
        return null;
    }

    // ---- Puños de Oro

    /**
     * Puños de Oro (2.8 s): in like a thunderbolt (0-0.35), three golden jabs (0.45 / 0.7 / 0.95), an uppercut that
     * launches you (1.4), and he is above you (1.9) and drives you back into the sand (2.3), a ring rolling out.
     */
    private void tickP3Combo(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        setFlying(true);
        if (t == 2) say("p3_combo", 40);
        if (target != null && t < 30) turnToward(target.position(), 30F);
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        if (t < 8 && target != null) {
            Vec3 to = target.position().subtract(flatTo(target.position()).scale(2.0)).add(0, 0.3, 0);
            Vec3 dv = to.subtract(position());
            setDeltaMovement(dv.scale(0.5));
            if (t % 2 == 0) Fx.spark(level, position().add(0, getBbHeight() * 0.5, 0), 3, 0.4, 0.3F, 0xFFE07A);
        } else if (t < 38) { setDeltaMovement(getDeltaMovement().scale(0.3)); }
        if (t == 9 || t == 14 || t == 19) {
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 4.8, 65)) {
                e.invulnerableTime = 0;
                if (strike(e, 5F, ModDamage.DEMIGOD, false)) {
                    e.setDeltaMovement(look.scale(0.35).add(0, 0.12, 0));
                    e.hurtMarked = true;
                }
            }
            Vec3 at = position().add(look.scale(2.2)).add(0, getBbHeight() * 0.5, 0);
            Fx.hit(level, at, 1.3F, 0xFFE07A);
            Fx.shock(level, at, 1.6F, 0xFFE07A);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.4F, 0.6F + t * 0.02F);
            PaponNetwork.shake(level, position(), 16, 0.25F, 5);
        }
        if (t == 28) {
            juggled = null;
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 5.0, 70)) {
                e.invulnerableTime = 0;
                if (strike(e, 8F, ModDamage.DEMIGOD, false)) {
                    e.setDeltaMovement(look.scale(0.2).add(0, 1.3, 0));
                    e.hurtMarked = true;
                    juggled = e.getUUID();
                }
            }
            Fx.hit(level, position().add(look.scale(2)).add(0, getBbHeight() * 0.7, 0), 1.8F, 0xFFF2C0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0F, 0.5F);
            PaponNetwork.shake(level, position(), 16, 0.5F, 8);
        }
        if (t == 38) {
            // a flash, and he is over them
            Entity j = juggled != null ? level.getEntity(juggled) : null;
            Vec3 over = (j != null ? j.position() : target != null ? target.position() : position()).add(0, 3.6, 0);
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 2F);
            noPhysics = true;
            setPos(over.x, over.y, over.z);
            setDeltaMovement(Vec3.ZERO);
            Fx.flash(level, over, 2F);
            level.playSound(null, over.x, over.y, over.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4F, 1.4F);
        }
        if (t > 38 && t < 46) setDeltaMovement(Vec3.ZERO);
        if (t == 46) {
            for (LivingEntity e : around(level, 3.4)) {
                e.invulnerableTime = 0;
                if (strike(e, 12F, ModDamage.DEMIGOD, true)) {
                    e.setDeltaMovement(0, -1.8, 0);
                    e.hurtMarked = true;
                }
            }
            noPhysics = false;
            setFlying(false);
            setDeltaMovement(0, -2.2, 0);
            hasImpulse = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (t > 46 && !landed && (onGround() || t >= 52)) {
            landed = true;
            landImpact(level, position(), 4.5F, 0F, 0.7F);
            ring(level, position(), 3.5);
            for (LivingEntity e : around(level, 4.6)) {
                if (e.onGround() && strike(e, 6F, ModDamage.DEMIGOD, false)) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(0.9).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (t == 1) landed = false;
        if (t >= BossState.P3_COMBO.duration) setBossState(BossState.IDLE);
    }

    // ---- Relámpago Dorado

    /**
     * Relámpago Dorado: five times, a golden line across the sand through where you are going (0.4 s), then he is
     * down it like lightning (4 ticks), through anything, and a bolt where he stops. Sideways is the only way out.
     */
    private void tickP3Flash(ServerLevel level, @Nullable LivingEntity target) {
        setFlying(true);
        if (target == null) {
            noPhysics = false;
            setBossState(BossState.IDLE);
            return;
        }
        int t = flashT++;
        if (t == 0) {
            if (flashLeft == 5 || random.nextInt(4) == 0) say("p3_flash", 30);
            Vec3 aim = predict(target, 6);
            Vec3 dir = aim.subtract(position()).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : dir.normalize();
            double floor = floorNear(aim);
            Vec3 to = aim.add(dir.scale(7));
            to = inSand(new Vec3(to.x, floor + 1.2, to.z), 3, 1.0, 6);
            flashFrom = position();
            flashTo = to;
            face(to);
            Fx.warnLine(level, new Vec3(flashFrom.x, floor, flashFrom.z), new Vec3(to.x, floor, to.z), 1.8F, 0xFFE07A, 9);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4F, 1.8F);
            chargeHits.clear();
        }
        if (flashFrom == null || flashTo == null) {
            flashT = 0;
            return;
        }
        if (t < 8) {
            setDeltaMovement(Vec3.ZERO);
            if (t % 2 == 0) Fx.spark(level, position().add(0, getBbHeight() * 0.5, 0), 4, 0.5, 0.3F, 0xFFF2C0);
        } else if (t < 12) {
            noPhysics = true;
            Vec3 a = flashFrom.lerp(flashTo, (t - 8) / 4.0), b = flashFrom.lerp(flashTo, (t - 7) / 4.0);
            setPos(b.x, b.y, b.z);
            setDeltaMovement(b.subtract(a));
            if (t == 8) level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.2F, 1.6F);
            for (int i = 0; i < 6; i++) Fx.spark(level, a.lerp(b, i / 6.0).add(0, getBbHeight() * 0.5, 0), 2, 0.2, 0.35F, 0xFFE07A);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(2.2), this::hittable)) {
                if (segDist(e.position().add(0, e.getBbHeight() * 0.5, 0), a.add(0, getBbHeight() * 0.4, 0), b.add(0, getBbHeight() * 0.4, 0))
                        > 1.7 + e.getBbWidth() / 2 || !chargeHits.add(e.getId())) continue;
                if (strike(e, 9F, ModDamage.DEMIGOD, true)) {
                    Vec3 dir = b.subtract(a).normalize();
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    if (e.position().subtract(b).dot(side) < 0) side = side.scale(-1);
                    e.setDeltaMovement(side.scale(1.1).add(dir.scale(0.6)).add(0, 0.45, 0));
                    e.hurtMarked = true;
                    Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.4F, 0xFFE07A);
                }
            }
        } else {
            noPhysics = false;
            setDeltaMovement(Vec3.ZERO);
            if (t == 12) {
                bolt(level, new Vec3(getX(), floorNear(position()), getZ()));
                Fx.shock(level, new Vec3(getX(), floorNear(position()), getZ()), 3F, 0xFFE07A);
            }
            if (t >= 18) {
                flashT = 0;
                if (--flashLeft <= 0) {
                    think = 14;
                    setBossState(BossState.IDLE);
                }
            }
        }
    }

    // ---- Cometa de Papa

    /** Up and out of sight (0.7 s), a red mark on you that follows and then locks (1.1-1.8 s), and down he comes, on fire. */
    private void tickP3Comet(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        setFlying(true);
        double floor = floorNear(target != null ? target.position() : position());
        if (t == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2.0F, 0.5F);
            Fx.shock(level, new Vec3(getX(), floor, getZ()), 4F, 0xFF9A3C);
            PaponNetwork.shake(level, position(), 24, 0.3F, 8);
        }
        if (t < 14) {
            noPhysics = true;
            setDeltaMovement(0, 1.9, 0);
            Fx.fire(level, position(), 3, 0.4, 1.0F);
            return;
        }
        if (t == 14) {
            say("p3_comet", 40);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t < 36) {
            if (target != null && t <= 28) {
                Vec3 p = predict(target, 8);
                cometAt = new Vec3(p.x, floor, p.z);
            }
            if (cometAt == null) cometAt = new Vec3(getX(), floor, getZ());
            if (t % 3 == 0) Fx.warn(level, cometAt, 5.5F, t > 28 ? 0xFF1A10 : Fx.DANGER, 4);
            if (t == 29) level.playSound(null, cometAt.x, cometAt.y, cometAt.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
            setPos(cometAt.x, Math.max(getY(), floor + 24), cometAt.z);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 at = cometAt != null ? cometAt : new Vec3(getX(), floor, getZ());
        noPhysics = true;
        double y = Math.max(floor, getY() - 2.6);
        setPos(at.x, y, at.z);
        setDeltaMovement(0, -2.6, 0);
        Fx.fire(level, position().add(0, 1, 0), 6, 0.6, 1.6F);
        Fx.smoke(level, position().add(0, 2.5, 0), 2, 0.5, 1.4F, 0x3A3430);
        if (y <= floor + 0.01 || t > 70) {
            noPhysics = false;
            setFlying(false);
            setBossState(BossState.P3_COMET_LAND);
        }
    }

    /** The crater: a share of your health falling off over six blocks, fire round it, and three rings rolling out (jump them). */
    private void tickP3CometLand(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        Vec3 c = position();
        if (t == 1) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7, 3, 7), this::hittable)) {
                double d = Math.sqrt(e.distanceToSqr(c.x, e.getY(), c.z));
                if (d > 6.5 + e.getBbWidth() / 2) continue;
                if (Mechanics.trueHit(e, 0.30F * (float) (1 - Math.min(d, 6.5) / 6.5 * 0.75), this, true)) {
                    swingHit = true;
                    Vec3 away = e.position().subtract(c).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(1.6).add(0, 0.8, 0));
                    e.hurtMarked = true;
                    e.setSecondsOnFire(4);
                }
            }
            Fx.blast(level, c, 3.5F);
            Fx.shock(level, c, 9F, 0xFF7A2A);
            Fx.scorch(level, c, 5F, 260);
            Fx.chunks(level, c.add(0, 0.5, 0), 24, 0.4F, 0xE8CF94, 0.9);
            PaponNetwork.shake(level, c, 48, 1.0F, 26);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0F, 0.6F);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + random.nextDouble() * 0.3;
                addFire(level, c.add(Math.cos(a) * 3.4, 0.6, Math.sin(a) * 3.4));
            }
            crowdReact(level, CrowdMood.GASP, 30);
        }
        for (int k = 0; k < 3; k++) {
            int at = 6 + k * 5;
            double r = 4 + k * 2.2;
            if (t != at) continue;
            ring(level, c, r);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 2, 2, r + 2), this::hittable)) {
                double d = Math.sqrt(e.distanceToSqr(c.x, e.getY(), c.z));
                if (Math.abs(d - r) > 1.0 || !e.onGround()) continue;
                if (Mechanics.trueHit(e, 0.06F, this, true)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (t >= BossState.P3_COMET_LAND.duration) {
            if (--cometsLeft > 0 && target != null) setBossState(BossState.P3_COMET);
            else {
                think = 10;
                setBossState(BossState.IDLE);
            }
        }
    }

    // ---- Ojos de Papa

    /**
     * Ojos de Papa (5.5 s): up over the sand facing you; his eyes charge (0.7-1.7 s: two thin lines aim in from either side
     * of you, and in his coliseum his golden statues' eyes light up too), then they fire (1.7-4.8 s): white-hot beams that
     * chase you across the sand, scorching it, a share of your health every fifth of a second. The Skyline stops them.
     */
    private void tickP3Laser(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = floorNear(target != null ? target.position() : position());
        Vec3 want = position();
        if (target != null) {
            Vec3 back = position().subtract(target.position()).multiply(1, 0, 1);
            back = back.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()).scale(-1) : back.normalize();
            want = target.position().add(back.scale(10));
        }
        flyTo(inSand(new Vec3(want.x, floor + 7.5, want.z), 3, 5, 10), 0.5);
        if (target != null) turnToward(target.position(), 8F);
        if (t == 2) say("p3_laser", 40);
        CaravanEntity van = van(level);
        if (t == 14 && target != null) {
            clearBeams(level);
            Vec3 tp = target.position();
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            Vec3 r = new Vec3(-f.z, 0, f.x);
            addBeam(level, eyePos(1), new Vec3(tp.x, floor, tp.z).add(r.scale(6)), 0.55F, 0xFF3A2A, 1);
            addBeam(level, eyePos(-1), new Vec3(tp.x, floor, tp.z).add(r.scale(-6)), 0.55F, 0xFF3A2A, -1);
            for (Vec3 s : statueEyes()) addBeam(level, s, new Vec3(s.x, floor, s.z), 1.0F, 0xFFC23A);
            laserPhase = random.nextDouble() * Math.PI * 2;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.6F);
            crowdReact(level, CrowdMood.GASP, 20);
        }
        if (beams.isEmpty() || t < 14) {
            if (t >= BossState.P3_LASER.duration) setBossState(BossState.IDLE);
            return;
        }
        boolean firing = t >= 34 && t < 96;
        if (t == 34) {
            for (int i = 0; i < beams.size(); i++) {
                BeamEntity b = beam(level, i);
                if (b != null) b.setMode(BeamEntity.FIRE);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.6F);
            PaponNetwork.shake(level, position(), 40, 0.35F, 10);
        }
        if (t < 34 && t % 5 == 0) {
            Fx.glint(level, eyePos(1), 0.7F, 0xFF4030);
            Fx.glint(level, eyePos(-1), 0.7F, 0xFF4030);
        }
        List<Vec3> statues = statueEyes();
        for (int i = 0; i < beams.size(); i++) {
            BeamEntity b = beam(level, i);
            if (b == null) continue;
            boolean eye = i < 2;
            Vec3 from = eye ? eyePos(i == 0 ? 1 : -1) : statues.get(Math.min(i - 2, statues.size() - 1));
            Vec3 aim = beamAims.get(i);
            if (eye && target != null) {
                // his eyes chase you across the sand, slower while they only aim
                Vec3 want2 = predict(target, 4);
                want2 = new Vec3(want2.x, floor, want2.z);
                double speed = firing ? 0.5 : 0.3;
                Vec3 d = want2.subtract(aim);
                if (d.length() > speed) d = d.normalize().scale(speed);
                aim = aim.add(d);
                beamAims.set(i, aim);
            } else if (!eye && arena != null) {
                // (2026-10-04, harder) his statues rake the sand: back and forth across it, closing in from both ends toward
                // the middle, a trail of fire behind each
                Vec3 c = arena.centerVec();
                double side = Math.signum(from.x - c.x);
                double k = Mth.clamp((t - 14) / 86.0, 0, 1);
                double x = c.x + side * Coliseum.A * (0.85 - 0.6 * k);
                double z = c.z + Coliseum.B * 0.9 * Math.sin((t - 14) * Math.PI * 2 / 56 + laserPhase + (i - 2) * Math.PI);
                Vec3 want2 = new Vec3(x, floor, z);
                Vec3 d = want2.subtract(aim);
                double speed = firing ? 1.6 : 2.5;
                if (d.length() > speed) d = d.normalize().scale(speed);
                aim = aim.add(d);
                beamAims.set(i, aim);
            }
            Vec3 end = traceBeam(level, from, aim, van);
            b.set(from, end);
            if (!firing) continue;
            boolean onVan = vanHit(van, from, end.add(end.subtract(from).normalize().scale(0.2))) != null && end.distanceTo(aim) > 0.6;
            if (onVan && t % 3 == 0) {
                Fx.spark(level, end, 5, 0.3, 0.35F, 0xFFF2C0);
                level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.6F);
            }
            if (!onVan && t % (eye ? 3 : 2) == 0) addFire(level, end.add(0, 0.5, 0));
            if (t % 4 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.6), this::hittable)) {
                    double dd = Math.min(segDist(e.position().add(0, e.getBbHeight() * 0.5, 0), from, end), e.position().distanceTo(end));
                    if (dd > 0.9 + e.getBbWidth() / 2 || e.getVehicle() instanceof CaravanEntity) continue;
                    if (Mechanics.trueHit(e, eye ? 0.05F : 0.06F, this, true)) {
                        swingHit = true;
                        e.setSecondsOnFire(3);
                        Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 0.9F, 0xFF6A3A);
                    }
                }
            }
        }
        if (firing && t % 6 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 1.8F);
        if (t == 96) clearBeams(level);
        if (t >= BossState.P3_LASER.duration) setBossState(BossState.IDLE);
    }

    // ---- El Sol Azteca

    /** Where the sun stone hangs: behind his back, a little over his head (client/BossRenderer draws it there too). */
    private Vec3 sunPos() {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(0, getBbHeight() * 0.8, 0).subtract(f.scale(3.2));
    }

    /**
     * El Sol Azteca (8.5 s): up over the far end of the sand from you; a sun stone wheels up behind him (1.5-4.5 s, the
     * stands gasping, the warning on your screen), and its light floods the bowl (4.5-6.8 s): day in the middle of his
     * night, a beam sweeping the sand, and anyone the sun can see burns, a share of their health every quarter second.
     * The only shade on the sand is the Skyline: get behind it (or in it).
     */
    private void tickP3Solar(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = floorNear(target != null ? target.position() : position());
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        if (t == 1) {
            Vec3 away = target != null ? c.subtract(target.position()).multiply(1, 0, 1) : Vec3.directionFromRotation(0, getYRot());
            away = away.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : away.normalize();
            solarFrom = inSand(c.add(away.scale(inColiseum() ? 16 : 10)), 4, 0, 40);
            solarFrom = new Vec3(solarFrom.x, floor + 12, solarFrom.z);
            say("p3_solar", 60);
            crowdReact(level, CrowdMood.GASP, 60);
            // four giant potatoes push up out of the sand: the only shade there will be
            clearMonoliths(level);
            double a0 = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 4; i++) {
                double a = a0 + Math.PI / 2 * i + (random.nextDouble() - 0.5) * 0.5;
                double rr = inColiseum() ? 7 + random.nextDouble() * 8 : 5 + random.nextDouble() * 5;
                Vec3 at = inSand(c.add(Math.cos(a) * rr * (inColiseum() ? Coliseum.A / Coliseum.B * 0.8 : 1), 0, Math.sin(a) * rr), 3, 0, 0);
                BossProp m = BossProp.create(level, BossProp.Kind.MONOLITH, this, new Vec3(at.x, floor, at.z)).life(200);
                level.addFreshEntity(m);
                monoliths.add(m.getUUID());
                Fx.dust(level, new Vec3(at.x, floor, at.z), 1.4, 10);
                Fx.chunks(level, new Vec3(at.x, floor + 0.5, at.z), 10, 0.3F, 0xE8CF94, 0.5);
            }
            level.playSound(null, c.x, floor, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
            PaponNetwork.shake(level, c, 48, 0.4F, 16);
        }
        if (solarFrom != null) flyTo(solarFrom, 0.8);
        if (target != null) turnToward(target.position(), 6F);
        Vec3 sun = sunPos();
        CaravanEntity van = van(level);
        if (t == 40) {
        }
        if (t == 62) say("p3_hide", 50);
        if (t >= 30 && t < 90) {
            if (t % 8 == 0) level.playSound(null, sun.x, sun.y, sun.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.6F + (t - 30) / 60F);
            if (t % 3 == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                Fx.spark(level, sun.add(Math.cos(a) * 4, Math.sin(a) * 4, 0), 2, 0.2, 0.4F, 0xFFC23A);
            }
            if (t % 10 == 0) PaponNetwork.shake(level, position(), 60, 0.05F + 0.25F * (t - 30) / 60F, 10);
        }
        if (t == 90) {
            clearBeams(level);
            Vec3 aim = target != null ? new Vec3(target.getX(), floor, target.getZ()) : new Vec3(c.x, floor, c.z);
            addBeam(level, sun, aim, 3.0F, 0xFFC23A);
            BeamEntity b = beam(level, 0);
            if (b != null) b.setMode(BeamEntity.FIRE);
            if (inColiseum()) entityData.set(DATA_NIGHT, 0.1F);
            level.playSound(null, sun.x, sun.y, sun.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0F, 0.5F);
            level.playSound(null, sun.x, sun.y, sun.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.5F);
            PaponNetwork.shake(level, position(), 64, 0.7F, 20);
            crowdReact(level, CrowdMood.ROAR, 40);
        }
        if (t >= 90 && t < 136) {
            // the beam sweeps across the sand and back; the light is everywhere
            double k = (t - 90) / 46.0;
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            Vec3 side = new Vec3(-f.z, 0, f.x);
            Vec3 mid = target != null ? new Vec3(target.getX(), floor, target.getZ()) : new Vec3(c.x, floor, c.z);
            Vec3 aim = mid.add(side.scale(Math.sin(k * Math.PI * 2) * 14)).add(f.scale(Math.cos(k * Math.PI * 3) * 4));
            BeamEntity b = beam(level, 0);
            Vec3 end = traceBeam(level, sun, aim, van);
            if (b != null) b.set(sun, end);
            if (t % 2 == 0) addFire(level, end.add(0, 0.5, 0));
            if ((t - 90) % 5 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(56, 30, 56), this::hittable)) {
                    if (inColiseum() && arena != null && !arena.inArena(e.position())) continue;
                    if (!sunSees(level, sun, e, van)) {
                        if (van != null && e.distanceTo(van) < 5 && t % 10 == 0) Fx.spark(level, e.getEyePosition(), 2, 0.2, 0.2F, 0x7CF0FF);
                        continue;
                    }
                    if (Mechanics.trueHit(e, 0.07F, this, true)) {
                        swingHit = true;
                        e.setSecondsOnFire(2);
                        Fx.spark(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 5, 0.4, 0.35F, 0xFFC23A);
                    }
                }
            }
            if (van != null && t % 3 == 0) {
                // the Skyline taking the light: sparks off its sun-facing side
                Vec3 hit = vanHit(van, sun, van.position().add(0, 0.8, 0));
                if (hit != null) Fx.spark(level, hit, 3, 0.4, 0.35F, 0xFFF2C0);
            }
        }
        if (t == 136) {
            clearBeams(level);
            level.playSound(null, sun.x, sun.y, sun.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (t > 136 && inColiseum()) entityData.set(DATA_NIGHT, Math.min(NIGHT_P3, 0.1F + (t - 136) / 20F * (NIGHT_P3 - 0.1F)));
        if (t >= BossState.P3_SOLAR.duration) {
            clearMonoliths(level);
            if (inColiseum()) entityData.set(DATA_NIGHT, NIGHT_P3);
            think = 10;
            setBossState(BossState.IDLE);
        }
    }

    // ---- ¡Fuego, Mariachi!

    /**
     * ¡Fuego, Mariachi! (8 s): he flies to the front of the band stand and conducts them at you. On the song's beat the
     * trumpets blast rings along the ground (jump them) and on the off-beat the violins slash rings at head height (duck
     * them): a share of your health each.
     */
    private void tickP3Mariachi(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 front = standFront();
        double floor = floorNear(front);
        flyTo(new Vec3(front.x, floor + 3.5, front.z), 0.7);
        if (target != null) turnToward(target.position(), 10F);
        if (t == 2) {
            say("p3_mariachi", 60);
            crowdMood(level, CrowdMood.CLAP);
        }
        int beat = Math.max(8, tangoBeat() - 3);
        if (t >= 30 && t <= 130 && (t - 30) % beat == 0 && target != null) {
            int k = beatsFired++;
            boolean horn = k % 2 == 0;
            List<MariachiEntity> who = new ArrayList<>();
            for (MariachiEntity m : band(level)) if (m.getRole().kind.equals(horn ? "trumpet" : "violin")) who.add(m);
            Vec3 from = who.isEmpty() ? front : who.get((k / 2) % who.size()).position();
            Vec3 head = target.position().subtract(from).multiply(1, 0, 1);
            double heading = Math.atan2(head.z, head.x);
            bandWaves.add(new double[]{from.x, from.z, 1.5, horn ? 0 : 1, heading, ++bandWaveIds, floor});
            // the blast drawn as it rolls (client/PropRenderer: a wall of gold, or a crimson ribbon), notes riding it
            BossProp wave = BossProp.create(level, BossProp.Kind.BAND_WAVE, this, new Vec3(from.x, floor, from.z)).life(120);
            wave.setYRot((float) Math.toDegrees(heading));
            wave.setRadius(1.5F);
            wave.setVariant(horn ? 0 : 1);
            level.addFreshEntity(wave);
            bandWaveProps.put(bandWaveIds, wave.getUUID());
            Vec3 dir = new Vec3(Math.cos(heading), 0, Math.sin(heading));
            Vec3 bell = from.add(0, 1.4, 0).add(dir.scale(0.8));
            if (horn) {
                level.playSound(null, from.x, from.y + 1, from.z, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.HOSTILE, 3.0F, 1.6F);
                level.playSound(null, from.x, from.y + 1, from.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 2.0F, 1.2F);
                level.playSound(null, from.x, from.y + 1, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.6F, 0.8F);
                // fire out of the bell
                for (int j = 0; j < 6; j++) Fx.jet(level, bell, dir.scale(0.5 + j * 0.12).add(0, 0.05, 0), 1.3F, 0xFFB43A);
                Fx.blast(level, bell.add(dir.scale(1.2)), 1.2F);
                Fx.shock(level, new Vec3(from.x, floor, from.z), 2.5F, 0xFFD24A);
            } else {
                level.playSound(null, from.x, from.y + 1, from.z, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.HOSTILE, 3.0F, 1.8F);
                level.playSound(null, from.x, from.y + 1, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 1.4F);
                // the bow slashing a crimson crescent off the strings
                Fx.slash(level, from.add(0, 1.9, 0).add(dir.scale(1.2)), (float) Math.toDegrees(heading) - 90F, 3.0F, 0xFF4A70);
                Fx.spark(level, from.add(0, 1.6, 0), 10, 0.5, 0.4F, 0xFF5A7A);
            }
            crowdReact(level, CrowdMood.CHEER, 8);
        }
        if (t >= BossState.P3_MARIACHI.duration) setBossState(BossState.IDLE);
    }

    /** The band's blasts rolling out across the sand (a ring segment facing where it was aimed), every tick. */
    private void tickBandWaves(ServerLevel level) {
        if (bandWaves.isEmpty()) return;
        java.util.Iterator<double[]> it = bandWaves.iterator();
        while (it.hasNext()) {
            double[] w = it.next();
            w[2] += 0.7;
            UUID pid = bandWaveProps.get((int) w[5]);
            Entity pe = pid != null ? level.getEntity(pid) : null;
            if (w[2] > 62) {
                it.remove();
                if (pe != null) pe.discard();
                bandWaveProps.remove((int) w[5]);
                continue;
            }
            boolean horn = w[3] == 0;
            double r = w[2], floor = w[6];
            if (pe instanceof BossProp bp) bp.setRadius((float) r);
            if (tickCount % 6 == 0) {
                // embers off the blast as it goes
                double a = w[4] + (random.nextDouble() - 0.5) * Math.toRadians(120);
                Vec3 p = new Vec3(w[0] + Math.cos(a) * r, floor + (horn ? 0.4 : 1.9), w[1] + Math.sin(a) * r);
                Fx.spark(level, p, 2, 0.2, horn ? 0.45F : 0.35F, horn ? 0xFFD24A : 0xFF5A7A);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(w[0], floor - 1, w[1], w[0], floor + 3, w[1]).inflate(r + 1.5, 0, r + 1.5),
                    this::hittable)) {
                double dx = e.getX() - w[0], dz = e.getZ() - w[1];
                double d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - r) > 0.7 + e.getBbWidth() / 2) continue;
                if (Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx) - w[4]))) > 72) continue;
                boolean caught = horn ? e.getY() - floor < 0.75
                        : e.getY() + e.getBbHeight() > floor + 1.6 && e.getY() < floor + 2.3;     // sneaking (1.5 tall) ducks under it
                if (!caught || !bandWaveHits.add(((long) w[5] << 32) | e.getId())) continue;
                if (Mechanics.trueHit(e, 0.10F, this, true)) {
                    swingHit = true;
                    Vec3 away = new Vec3(dx, 0, dz).normalize();
                    e.setDeltaMovement(away.scale(horn ? 0.6 : 0.9).add(0, horn ? 0.7 : 0.25, 0));
                    e.hurtMarked = true;
                    Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.0F, horn ? 0xFFD24A : 0xFF5A7A);
                }
            }
        }
        if (bandWaves.isEmpty()) bandWaveHits.clear();
    }

    // ---- Duelo de Guitarras

    /**
     * The duel's fetch: "A DUEL, amigo!", over to the band stand, the guitarist's guitar out of his hands (he cowers
     * without it), and back down in front of you with it.
     */
    private void tickP3DuelFetch(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (t == 2) {
            say("p3_duel", 80);
            crowdReact(level, CrowdMood.GASP, 30);
        }
        Entity ge = duelGuitarist >= 0 ? level.getEntity(duelGuitarist) : guitarist(level);
        if (!(ge instanceof MariachiEntity g) || target == null) {
            setBossState(BossState.IDLE);
            return;
        }
        if (duelPhase == 0) {
            Vec3 gf = Vec3.directionFromRotation(0, g.getYRot());
            Vec3 at = g.position().add(gf.scale(2.4)).add(0, 1.0, 0);
            flyTo(at, 1.0);
            face(g.position());
            if (horizontalDist(at) < 1.6 || t > 80) {
                g.snatch();
                duelGuitarist = g.getId();
                duelPhase = 1;
                stateTicks = 1;
                level.playSound(null, g.getX(), g.getY() + 1, g.getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.NEUTRAL, 1.6F, 0.6F);
                level.playSound(null, g.getX(), g.getY() + 1, g.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.6F);
                crowdReact(level, CrowdMood.GASP, 20);
            }
            return;
        }
        Vec3 back = position().subtract(target.position()).multiply(1, 0, 1);
        back = back.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : back.normalize();
        double floor = floorNear(target.position());
        Vec3 to = inSand(target.position().add(back.scale(5.5)), 3, 0, 20);
        to = new Vec3(to.x, floor, to.z);
        flyTo(to.add(0, t < 20 ? 3 : 0.2, 0), 1.0);
        face(target.position());
        if ((horizontalDist(to) < 1.2 && getY() - floor < 1.2) || t > 90) {
            setFlying(false);
            noPhysics = false;
            setPos(to.x, floor, to.z);
            setDeltaMovement(Vec3.ZERO);
            face(target.position());
            PaponNetwork.shake(level, position(), 24, 0.3F, 8);
            Fx.dust(level, position(), 1.2, 6);
            setBossState(BossState.P3_DUEL);
        }
    }

    /** How long the duel's chart runs: 14 s. */
    private static final int DUEL_TICKS = 280;

    /**
     * The duel: the challenger's screen becomes a fretboard (client/QteClient, guitar-hero style on the band's song, keys
     * D F J K, pro speed) while he shreds in front of them; they cannot move. It ends when their client says so (or never
     * answers: then it is his).
     */
    private void tickP3Duel(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        Entity we = duelWho != null ? level.getEntity(duelWho) : null;
        LivingEntity who = we instanceof LivingEntity le && le.isAlive() ? le : null;
        if (t == 1) {
            duelResult = -1;
            duelScore = 0;
            qteSaid = 0;
            entityData.set(DATA_METER, 0.6F);
            duelLen = DUEL_TICKS;
            if (who instanceof ServerPlayer sp) {
                PaponNetwork.qteStart(sp, getId(), 6, new int[]{random.nextInt(1 << 20), duelLen, songId}, duelLen);
                Mechanics.root(sp, duelLen + 60);
            }
            crowdMood(level, CrowdMood.CHANT);
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        if (who != null) face(who.position());
        if (who instanceof Player p && t % 20 == 0) Mechanics.root(p, 40);
        if (t % 4 == 0) {
            Vec3 at = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.0)).add(0, getBbHeight() * 0.4, 0);
            level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 2, 0.6, 0.4, 0.6, 1.0);
        }
        if (t % 6 == 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.HOSTILE, 0.8F,
                    0.8F + random.nextFloat() * 1.0F);
        }
        boolean gone = who == null || !(who instanceof Player) && t > 40;
        if (duelResult >= 0 || gone || t > duelLen + 80) {
            if (who instanceof ServerPlayer sp) PaponNetwork.qteEnd(sp, duelResult == 1);
            if (duelResult < 0) duelResult = 0;
            if (duelResult == 1) setBossState(BossState.P3_DUEL_SHAME);
            else {
                if (duelResult < 0) duelScore = 0;
                setBossState(BossState.P3_DUEL_SMASH);
            }
        }
    }

    /** From the challenger's client: the spam meter (kind 5) or the duel (kind 6) is over. */
    public void qteResult(ServerPlayer player, int kind, boolean won, int score) {
        if (WanderingDebug.on()) PaponMod.LOG.info("[debug] QTE {} result from {}: {} ({}%)", kind, player.getName().getString(), won ? "won" : "lost", score);
        if (kind == 7) {
            if (getBossState() == BossState.P4_TANGO && tangoResult < 0 && tangoWho != null && tangoWho.equals(player.getUUID())) {
                tangoResult = won ? 1 : 0;
                tangoScore = Mth.clamp(score, 0, 100);
            }
            return;
        }
        if (kind == 6) {
            if (getBossState() == BossState.P3_DUEL && duelResult < 0 && duelWho != null && duelWho.equals(player.getUUID())) {
                duelResult = won ? 1 : 0;
                duelScore = Mth.clamp(score, 0, 100);
            }
            return;
        }
        if (kind == 5 && getBossState() == BossState.QTE_HOLD && !qteResolved && heldId() == player.getId() && qteKind() == 5) endQte(won, false);
    }

    /** He won the duel: "And THAT... is how a legend plays.", and the guitar comes down over your head (worse the worse you played). */
    private void tickP3DuelSmash(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        Entity we = duelWho != null ? level.getEntity(duelWho) : null;
        LivingEntity who = we instanceof LivingEntity le && le.isAlive() ? le : null;
        if (who != null) face(who.position());
        if (t == 2) {
            say("p3_duel_won", 50);
            crowdReact(level, CrowdMood.ROAR, 40);
        }
        if (t == 10 && who != null && distanceTo(who) > 3.5) {
            Vec3 to = who.position().subtract(flatTo(who.position()).scale(2.2));
            setPos(to.x, getY(), to.z);
        }
        if (t == 24 && who != null && distanceTo(who) < 6) {
            if (Mechanics.trueHit(who, 0.30F + 0.30F * (1 - duelScore / 100F), this, true)) {
                swingHit = true;
                Mechanics.stun(who);
            }
            Vec3 at = who.position().add(0, who.getBbHeight(), 0);
            Fx.chunks(level, at, 20, 0.3F, 0xBE8040, 0.6);
            Fx.hit(level, at, 2.0F, 0xFFFFFF);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 0.8F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
            PaponNetwork.shake(level, position(), 24, 0.8F, 16);
        }
        if (t == 40) giveGuitarBack(level, false);
        if (t >= BossState.P3_DUEL_SMASH.duration) setBossState(BossState.IDLE);
    }

    /** You won: the stands cheer YOU; "Impossible... They cheer for YOU?!"; he stands there (taking double), then sends it back. */
    private void tickP3DuelShame(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        if (t == 2) {
            say("p3_duel_lost", 50);
            crowdReact(level, CrowdMood.CHEER, 60);
            fireworks(level);
        }
        if (t == 30) crowdReact(level, CrowdMood.BOO, 40);
        if (t == 56) giveGuitarBack(level, true);
        if (t >= BossState.P3_DUEL_SHAME.duration) {
            think = 6;
            setBossState(BossState.IDLE);
        }
    }

    /** The guitar back to the band's guitarist: thrown over (or, smashed, a spare from behind the stand). */
    private void giveGuitarBack(ServerLevel level, boolean toss) {
        Entity ge = duelGuitarist >= 0 ? level.getEntity(duelGuitarist) : null;
        duelGuitarist = -1;
        if (!(ge instanceof MariachiEntity g)) return;
        if (toss) {
            Vec3 from = position().add(0, getBbHeight() * 0.8, 0);
            Vec3 to = g.position().add(0, g.getBbHeight() * 0.6, 0);
            level.addFreshEntity(BossProp.create(level, BossProp.Kind.GUITAR, this, from).toss(to, 22));
        }
        g.giveBack(toss ? 22 : 0);
    }

    // ---- La Piñata

    /**
     * La Piñata (7 s): a giant potato piñata comes down on its rope over the middle of the sand (to 1.5 s); he flies up
     * beside it, winds up, and kicks it open (2.2 s): candy everywhere, and two dozen candy bombs rain down on their
     * pink warnings over the next two seconds, half of them on you; a share of your health each.
     */
    private void tickP3Pinata(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        Vec3 hang = new Vec3(c.x, floor + 7.5, c.z);
        Vec3 toT = target != null ? target.position().subtract(hang).multiply(1, 0, 1) : new Vec3(1, 0, 0);
        toT = toT.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toT.normalize();
        Vec3 side = new Vec3(-toT.z, 0, toT.x).scale(6.5).add(hang).add(0, 0.5, 0);
        if (t == 1) {
            BossProp p = BossProp.create(level, BossProp.Kind.PINATA, this, hang.add(0, 22, 0)).velocity(new Vec3(0, -2.2, 0)).life(160);
            level.addFreshEntity(p);
            spudId = p.getUUID();
            say("p3_pinata", 40);
            crowdReact(level, CrowdMood.CHEER, 40);
            level.playSound(null, hang.x, hang.y, hang.z, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (t < 40) {
            flyTo(side, 0.8);
            face(hang);
        } else if (t < 45) {
            // the kick: straight through it
            noPhysics = true;
            Vec3 through = hang.add(hang.subtract(side).multiply(1, 0, 1).scale(0.6));
            Vec3 at = side.lerp(through, (t - 39) / 5.0);
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
        } else {
            noPhysics = false;
            setFlying(true);
            setDeltaMovement(getDeltaMovement().scale(0.5));
            if (target != null) turnToward(target.position(), 8F);
        }
        if (t == 44) {
            Entity pe = spudId != null ? level.getEntity(spudId) : null;
            if (pe != null) pe.discard();
            spudId = null;
            for (int i = 0; i < 6; i++) Fx.spark(level, hang, 12, 1.6, 0.5F, PINATA_COLORS[i]);
            Fx.chunks(level, hang, 30, 0.35F, 0xFF4AB0, 1.0);
            Fx.chunks(level, hang, 30, 0.35F, 0xFFCE30, 1.0);
            Fx.flash(level, hang, 3F);
            level.playSound(null, hang.x, hang.y, hang.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 3.0F, 0.8F);
            level.playSound(null, hang.x, hang.y, hang.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.HOSTILE, 3.0F, 1.0F);
            level.playSound(null, hang.x, hang.y, hang.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 1.4F);
            PaponNetwork.shake(level, hang, 40, 0.5F, 12);
            crowdReact(level, CrowdMood.ROAR, 40);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(40, 20, 40),
                    e -> hittable(e) && e instanceof Player);
            for (int i = 0; i < 24; i++) {
                Vec3 to;
                if (i % 2 == 0 && !victims.isEmpty()) {
                    LivingEntity v = victims.get((i / 2) % victims.size());
                    Vec3 p = predict(v, 14 + i);
                    to = p.add(random.nextGaussian() * 2.2, 0, random.nextGaussian() * 2.2);
                } else { to = c.add((random.nextDouble() - 0.5) * 2 * Coliseum.A, 0, (random.nextDouble() - 0.5) * 2 * Coliseum.B); }
                to = inSand(to, 1.5, 0, 0);
                to = new Vec3(to.x, floor, to.z);
                int flight = 14 + (int) (i * 1.4) + random.nextInt(4);
                level.addFreshEntity(BossProp.create(level, BossProp.Kind.CANDY, this, hang).toss(to.add(0, 0.2, 0), flight));
                Fx.warn(level, to, 2.3F, 0xFF4AB0, flight);
            }
        }
        if (t >= BossState.P3_PINATA.duration) setBossState(BossState.IDLE);
    }

    private static final int[] PINATA_COLORS = {0xEC4896, 0xFFCE30, 0x40C460, 0x3696EC, 0xFA7828, 0xAA54DC};

    /** A candy bomb going off: a puff of every colour; a share of your health within 2.3 blocks. */
    private void candyBlast(ServerLevel level, Vec3 at) {
        Fx.spark(level, at.add(0, 0.4, 0), 10, 0.8, 0.45F, PINATA_COLORS[random.nextInt(PINATA_COLORS.length)]);
        Fx.blast(level, at, 1.1F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.6F, 0.9F + random.nextFloat() * 0.4F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 1.5F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5, 2.2, 2.5), this::hittable)) {
            if (e.position().distanceTo(at) > 2.3 + e.getBbWidth() / 2) continue;
            if (Mechanics.trueHit(e, 0.12F, this, true)) {
                swingHit = true;
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(away.scale(0.7).add(0, 0.5, 0));
                e.hurtMarked = true;
            }
        }
    }

    // ---- El Sombrero Asesino (2026-10-04)

    /** The sombreros in the air, and when each last hit whom (prop index << 32 | entity id -> tick). */
    private final List<UUID> sombreros = new ArrayList<>();
    private final java.util.Map<Long, Integer> sombreroHits = new java.util.HashMap<>();

    /**
     * El Sombrero Asesino (8.5 s): over the middle of the sand, spinning, he whips giant sombreros off his head and flings
     * them flat like saw blades (two, three under 40%), 1.2 s apart, at where you are going; they ricochet round the sand,
     * faster off every wall, curling after you, until he calls them home. A share of your health each time one catches you.
     */
    private void tickP3Sombrero(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        flyTo(new Vec3(c.x, floor + 4.5, c.z), 0.8);
        if (target != null) turnToward(target.position(), 12F);
        if (t == 2) {
            say("p3_sombrero", 50);
            crowdReact(level, CrowdMood.CHEER, 30);
            sombreroHits.clear();
        }
        int n = getHealth() < getMaxHealth() * 0.4F ? 3 : 2;
        for (int i = 0; i < n; i++) {
            if (t != 24 + i * 24) continue;
            Vec3 from = position().add(0, getBbHeight() * 0.9, 0);
            Vec3 aim = target != null ? predict(target, 12) : from.add(Vec3.directionFromRotation(0, getYRot()).scale(10));
            Vec3 d = new Vec3(aim.x - from.x, 0, aim.z - from.z);
            d = d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : d.normalize();
            d = d.yRot((float) Math.toRadians((i - (n - 1) / 2.0) * 22));
            BossProp p = BossProp.create(level, BossProp.Kind.SOMBRERO, this, new Vec3(from.x, floor + 0.7, from.z)).life(260).velocity(d.scale(0.75));
            level.addFreshEntity(p);
            sombreros.add(p.getUUID());
            level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.6F, 0.6F);
            Fx.slash(level, from, getYRot(), 3F, 0xFFD24A);
        }
        if (t >= BossState.P3_SOMBRERO.duration && sombreros.isEmpty()) setBossState(BossState.IDLE);
        else if (t >= BossState.P3_SOMBRERO.duration + 60) {
            clearSombreros(level);
            setBossState(BossState.IDLE);
        }
    }

    /** The sombreros on the move (every tick): home on whoever is nearest, ricochet off the edge of the sand, cut. */
    private void tickSombreros(ServerLevel level) {
        if (sombreros.isEmpty()) return;
        boolean recall = getBossState() != BossState.P3_SOMBRERO || stateTicks >= BossState.P3_SOMBRERO.duration - 20;
        Vec3 c = arena != null && inColiseum() ? arena.centerVec() : (home != null ? home : position());
        for (int i = sombreros.size() - 1; i >= 0; i--) {
            if (!(level.getEntity(sombreros.get(i)) instanceof BossProp p)) {
                sombreros.remove(i);
                continue;
            }
            Vec3 v = p.getDeltaMovement();
            Vec3 pos = p.position();
            if (recall) {
                Vec3 to = position().add(0, getBbHeight() * 0.85, 0).subtract(pos);
                if (to.length() < 2.0) {
                    p.discard();
                    sombreros.remove(i);
                    level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.HOSTILE, 1.4F, 0.7F);
                    continue;
                }
                v = v.scale(0.6).add(to.normalize().scale(0.55));
            } else {
                LivingEntity near = null;
                double best = 30 * 30;
                for (Player pl : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(30), this::hittable)) {
                    double d = pl.distanceToSqr(p);
                    if (d < best) {
                        best = d;
                        near = pl;
                    }
                }
                double speed = v.length();
                if (near != null) {
                    Vec3 want = near.position().subtract(pos).multiply(1, 0, 1);
                    if (want.lengthSqr() > 1e-4) v = v.normalize().lerp(want.normalize(), 0.035).normalize().scale(speed);
                }
                Vec3 next = pos.add(v);
                boolean bounce;
                Vec3 nrm;
                if (arena != null && inColiseum()) {
                    bounce = Coliseum.offset(next.x - c.x, next.z - c.z) > -1.8;
                    nrm = new Vec3((next.x - c.x) / (Coliseum.A * Coliseum.A), 0, (next.z - c.z) / (Coliseum.B * Coliseum.B));
                } else {
                    bounce = next.subtract(c).multiply(1, 0, 1).length() > 18;
                    nrm = next.subtract(c).multiply(1, 0, 1);
                }
                if (bounce && nrm.lengthSqr() > 1e-8 && v.dot(nrm) > 0) {
                    nrm = nrm.normalize();
                    v = v.subtract(nrm.scale(2 * v.dot(nrm))).scale(1.08);
                    if (v.length() > 1.35) v = v.normalize().scale(1.35);
                    level.playSound(null, next.x, next.y, next.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7F, 1.7F);
                    Fx.spark(level, next.add(0, 0.4, 0), 8, 0.4, 0.35F, 0xFFD24A);
                    PaponNetwork.shake(level, next, 10, 0.15F, 4);
                }
            }
            Vec3 at = pos.add(v);
            p.setPos(at.x, recall ? at.y : Mth.lerp(0.3, at.y, floorNear(at) + 0.7), at.z);
            p.setDeltaMovement(v);
            if (tickCount % 5 == i % 5) level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.8F, 1.4F);
            if (recall) continue;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(2.2, 1.0, 2.2), this::hittable)) {
                double dx = e.getX() - at.x, dz = e.getZ() - at.z;
                if (dx * dx + dz * dz > Math.pow(1.9 + e.getBbWidth() / 2, 2) || e.getY() > at.y + 0.55 || e.getY() + e.getBbHeight() < at.y - 0.3) continue;
                long key = ((long) i << 32) | e.getId();
                Integer last = sombreroHits.get(key);
                if (last != null && tickCount - last < 16) continue;
                sombreroHits.put(key, tickCount);
                if (Mechanics.trueHit(e, 0.15F, this, true)) {
                    swingHit = true;
                    Vec3 push = v.normalize();
                    e.setDeltaMovement(push.scale(1.0).add(0, 0.5, 0));
                    e.hurtMarked = true;
                    Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 1.4F, 0xFFD24A);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.4F, 0.8F);
                }
            }
        }
    }

    private void clearSombreros(ServerLevel level) {
        for (UUID id : sombreros) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        sombreros.clear();
    }

    // ---- El Volcán (2026-10-04)

    /** The geysers erupting: x, z, floor, ticks left, the beam's index in {@link #volcanBeams}. */
    private final List<double[]> geysers = new ArrayList<>();
    private final List<UUID> volcanBeams = new ArrayList<>();
    private final java.util.Set<Long> geyserHits = new java.util.HashSet<>();
    private double volcanAngle;

    /**
     * El Volcán (9 s): up, and down into the middle of the sand like a meteor; it splits along six cracks (their lines
     * glowing); salsa bursts up out of them in geysers, from the middle outward, a ring at a time (2.3-4.5 s); the cracks
     * turn and it comes back the other way, from the edge inward (5.6-7.5 s); and the crater spits burning rocks at you.
     * A share of your health per geyser that catches you, and up you go.
     */
    private void tickP3Volcan(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        if (t == 2) {
            say("p3_volcan", 50);
            geyserHits.clear();
            volcanAngle = random.nextDouble() * Math.PI;
        }
        if (t < 16) {
            flyTo(new Vec3(c.x, floor + 9, c.z), 1.0);
        } else if (t < 22) {
            noPhysics = true;
            setFlying(true);
            double k = (t - 16) / 6.0;
            setPos(c.x, Mth.lerp(k * k, floor + 9, floor), c.z);
            setDeltaMovement(Vec3.ZERO);
        } else {
            noPhysics = false;
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (target != null) turnToward(target.position(), 6F);
        }
        if (t == 22) {
            setFlying(false);
            landImpact(level, position(), 6F, 0F, 1.0F);
            Fx.shock(level, new Vec3(c.x, floor, c.z), 10F, 0xFF7A2A);
            Fx.scorch(level, new Vec3(c.x, floor, c.z), 4F, 300);
            PaponNetwork.shake(level, c, 64, 1.0F, 24);
            crowdReact(level, CrowdMood.GASP, 30);
            volcanCracks(level, c, floor, volcanAngle, 26);
        }
        if (t == 92) {
            volcanAngle += Math.PI / 6;
            volcanCracks(level, c, floor, volcanAngle, 24);
        }
        double reach = inColiseum() ? Coliseum.A : 18;
        if (t >= 46 && t < 90 && (t - 46) % 3 == 0) {
            double d = 2.5 + (t - 46) / 3.0 * 2.0;
            if (d <= reach) eruptRing(level, c, floor, volcanAngle, d);
        }
        if (t >= 112 && t < 156 && (t - 112) % 3 == 0) {
            double d = reach - (t - 112) / 3.0 * 2.0;
            if (d >= 2.5) eruptRing(level, c, floor, volcanAngle, d);
        }
        if (t >= 150 && t < 176 && t % 5 == 0) {
            // the crater spits burning rocks
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(40, 20, 40), e -> hittable(e) && e instanceof Player);
            for (int i = 0; i < 2; i++) {
                Vec3 to = !victims.isEmpty() && i == 0 ? predict(victims.get(random.nextInt(victims.size())), 16)
                        : c.add((random.nextDouble() - 0.5) * 2 * (reach - 3), 0, (random.nextDouble() - 0.5) * 2 * (inColiseum() ? Coliseum.B - 3 : reach));
                to = inSand(to, 1.5, 0, 0);
                to = new Vec3(to.x, floor, to.z);
                level.addFreshEntity(BossProp.create(level, BossProp.Kind.METEOR, this, new Vec3(c.x, floor + 14, c.z)).meteor(to, 18));
                Fx.warn(level, to, 2.6F, 0xFF4A1A, 18);
            }
            level.playSound(null, c.x, floor, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.6F);
            Fx.blast(level, new Vec3(c.x, floor + 1, c.z), 1.6F);
        }
        if (t >= BossState.P3_VOLCAN.duration) setBossState(BossState.IDLE);
    }

    /** The six cracks out from the middle, glowing along their length for a second (where the salsa will come up). */
    private void volcanCracks(ServerLevel level, Vec3 c, double floor, double angle, double len) {
        for (int k = 0; k < 6; k++) {
            double a = angle + Math.PI / 3 * k;
            Vec3 end = inSand(c.add(Math.cos(a) * len, 0, Math.sin(a) * len), 1.0, 0, 0);
            Fx.warnLine(level, new Vec3(c.x, floor, c.z), new Vec3(end.x, floor, end.z), 1.4F, 0xFF5A1A, 26);
        }
        level.playSound(null, c.x, floor, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3.0F, 0.4F);
        level.playSound(null, c.x, floor, c.z, SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    /** Geysers of salsa out of every crack at distance {@code d} from the middle. */
    private void eruptRing(ServerLevel level, Vec3 c, double floor, double angle, double d) {
        for (int k = 0; k < 6; k++) {
            double a = angle + Math.PI / 3 * k;
            Vec3 at = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            if (inColiseum() && arena != null && Coliseum.offset(at.x - c.x, at.z - c.z) > -0.8) continue;
            BeamEntity b = BeamEntity.create(level, new Vec3(at.x, floor, at.z), new Vec3(at.x, floor + 7.5, at.z), 1.7F, 0xFF3A14).life(16);
            b.setMode(BeamEntity.FIRE);
            level.addFreshEntity(b);
            geysers.add(new double[]{at.x, at.z, floor, 12, geysers.size() + tickCount * 7L % 100000});
            Fx.jet(level, new Vec3(at.x, floor + 0.2, at.z), new Vec3(0, 0.9, 0), 1.8F, 0xC0261A);
            Fx.splat(level, new Vec3(at.x, floor + 0.02, at.z), 1.3F, 0xA81E14, 160);
        }
        level.playSound(null, c.x + Math.cos(angle) * d, floor, c.z + Math.sin(angle) * d, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 2.5F, 0.6F);
        level.playSound(null, c.x, floor, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.6F, 0.5F);
    }

    /** The geysers burning (every tick): anyone in one goes up in it. */
    private void tickGeysers(ServerLevel level) {
        if (geysers.isEmpty()) return;
        for (int i = geysers.size() - 1; i >= 0; i--) {
            double[] g = geysers.get(i);
            if (--g[3] <= 0) {
                geysers.remove(i);
                continue;
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(g[0] - 1.6, g[2] - 0.5, g[1] - 1.6, g[0] + 1.6, g[2] + 7.5, g[1] + 1.6),
                    this::hittable)) {
                double dx = e.getX() - g[0], dz = e.getZ() - g[1];
                if (dx * dx + dz * dz > Math.pow(1.25 + e.getBbWidth() / 2, 2)) continue;
                if (!geyserHits.add(((long) g[4] << 20) ^ e.getId())) continue;
                if (Mechanics.trueHit(e, 0.14F, this, true)) {
                    swingHit = true;
                    Mechanics.heat(e, 1, this);
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 1.25, 0));
                    e.hurtMarked = true;
                    e.setSecondsOnFire(2);
                }
            }
        }
        if (geysers.isEmpty()) geyserHits.clear();
    }

    // ---- Las Calaveras (2026-10-04)

    /** The skulls: their prop, whom they are after (entity id), their angle round them, their phase (0 rising, 1 circling,
     *  2 aiming, 3 diving), ticks in it, and the dive's direction (x, z, y). */
    private final List<UUID> calaveras = new ArrayList<>();
    private final List<double[]> calaveraData = new ArrayList<>();
    private int calaveraNext;

    /**
     * Las Calaveras (9.5 s): "My ancestors would like to MEET you, amigo." Sugar-skull potatoes push up out of the sand in
     * a ring round you (eight; split between two of you, five each), rise to head height and circle you; then one after
     * another they stop, a line marks where each is aimed, and it dives straight through you and shatters on the sand. A
     * share of your health per skull that finds you. Keep moving across their lines.
     */
    private void tickP3Calaveras(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        flyTo(new Vec3(c.x, floor + 6, c.z), 0.6);
        if (target != null) turnToward(target.position(), 8F);
        if (t == 2) {
            say("p3_calaveras", 60);
            crowdMood(level, CrowdMood.HUSH);
            clearCalaveras(level);
            calaveraNext = 0;
        }
        if (t == 12) {
            List<Player> who = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48, 24, 48), this::hittable);
            List<LivingEntity> marks = new ArrayList<>();
            for (int i = 0; i < Math.min(2, who.size()); i++) marks.add(who.get(i));
            if (marks.isEmpty() && target != null) marks.add(target);
            int each = marks.size() > 1 ? 5 : 8;
            for (LivingEntity m : marks) {
                double a0 = random.nextDouble() * Math.PI * 2;
                for (int i = 0; i < each; i++) {
                    double a = a0 + Math.PI * 2 * i / each;
                    Vec3 at = m.position().add(Math.cos(a) * 6.5, 0, Math.sin(a) * 6.5);
                    at = inSand(at, 1.0, 0, 0);
                    BossProp p = BossProp.create(level, BossProp.Kind.CALAVERA, this, new Vec3(at.x, floor - 1.2, at.z)).life(320);
                    level.addFreshEntity(p);
                    calaveras.add(p.getUUID());
                    calaveraData.add(new double[]{m.getId(), a, 0, 0, 0, 0, 0});
                    Fx.dust(level, new Vec3(at.x, floor, at.z), 0.8, 5);
                }
            }
            level.playSound(null, c.x, floor, c.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 3.0F, 0.6F);
            level.playSound(null, c.x, floor, c.z, SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        // from 3 s, one dives every 0.45 s
        if (t >= 60 && (t - 60) % 9 == 0) {
            for (int k = 0; k < calaveraData.size(); k++) {
                int i = (calaveraNext + k) % calaveraData.size();
                double[] d = calaveraData.get(i);
                if (d[2] != 1) continue;
                d[2] = 2;
                d[3] = 0;
                calaveraNext = i + 1;
                break;
            }
        }
        boolean allDone = t > 60 && calaveras.isEmpty();
        if (t >= BossState.P3_CALAVERAS.duration || allDone) {
            clearCalaveras(level);
            setBossState(BossState.IDLE);
        }
    }

    /** The skulls rising, circling, aiming and diving (every tick). */
    private void tickCalaveras(ServerLevel level) {
        if (calaveras.isEmpty()) return;
        if (getBossState() != BossState.P3_CALAVERAS) {
            clearCalaveras(level);
            return;
        }
        for (int i = calaveras.size() - 1; i >= 0; i--) {
            double[] d = calaveraData.get(i);
            if (!(level.getEntity(calaveras.get(i)) instanceof BossProp p)) {
                calaveras.remove(i);
                calaveraData.remove(i);
                continue;
            }
            Entity me = level.getEntity((int) d[0]);
            LivingEntity mark = me instanceof LivingEntity le && le.isAlive() ? le : null;
            double floor = floorNear(p.position());
            d[3]++;
            Vec3 pos = p.position();
            if (d[2] == 0 || d[2] == 1) {
                // rising out of the sand, then circling at head height
                d[1] += d[2] == 1 ? 0.045 : 0.02;
                Vec3 centre = mark != null ? mark.position() : pos;
                double h = d[2] == 0 ? Mth.lerp(Math.min(1, d[3] / 20.0), -1.2, 1.7) : 1.7 + Math.sin(tickCount * 0.12 + i) * 0.25;
                Vec3 to = new Vec3(centre.x + Math.cos(d[1]) * 6.5, floor + h, centre.z + Math.sin(d[1]) * 6.5);
                to = inSand(to, 1.0, floor - 2, floor + 6);
                p.setDeltaMovement(to.subtract(pos));
                p.setPos(to.x, to.y, to.z);
                if (d[2] == 0 && d[3] >= 20) {
                    d[2] = 1;
                    d[3] = 0;
                }
                if (d[2] == 0 && d[3] % 4 == 0) Fx.dust(level, new Vec3(to.x, floor, to.z), 0.5, 2);
            } else if (d[2] == 2) {
                // stops dead, shivering, a line through you to where it is going
                if (d[3] == 1 && mark != null) {
                    Vec3 aim = predict(mark, 8).add(0, mark.getBbHeight() * 0.5, 0);
                    Vec3 dir = aim.subtract(pos).normalize();
                    d[4] = dir.x;
                    d[5] = dir.z;
                    d[6] = dir.y;
                    Vec3 end = pos.add(dir.scale(16));
                    Fx.warnLine(level, new Vec3(pos.x, floor, pos.z), new Vec3(end.x, floor, end.z), 1.4F, 0xFF4AB0, 12);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.4F, 0.5F);
                }
                p.setDeltaMovement(Vec3.ZERO);
                p.setPos(pos.x + (random.nextDouble() - 0.5) * 0.06, pos.y, pos.z + (random.nextDouble() - 0.5) * 0.06);
                if (d[3] >= 11) {
                    d[2] = 3;
                    d[3] = 0;
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.6F, 1.2F);
                }
            } else {
                // the dive: straight down its line, through whoever is on it, shattering on the sand
                Vec3 v = new Vec3(d[4], d[6], d[5]).scale(1.25);
                Vec3 at = pos.add(v);
                p.setDeltaMovement(v);
                p.setPos(at.x, at.y, at.z);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.2), this::hittable)) {
                    if (Mechanics.trueHit(e, 0.10F, this, true)) {
                        swingHit = true;
                        e.setDeltaMovement(v.normalize().scale(0.8).add(0, 0.4, 0));
                        e.hurtMarked = true;
                    }
                    d[3] = 99;
                }
                if (d[3] > 22 || at.y < floor + 0.2 || (arena != null && inColiseum()
                        && Coliseum.offset(at.x - arena.centerVec().x, at.z - arena.centerVec().z) > -0.5)) {
                    Fx.chunks(level, at, 12, 0.3F, 0xF6F0E6, 0.5);
                    Fx.spark(level, at, 8, 0.4, 0.4F, 0xF04AA0);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 1.0F, 1.4F);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 1.3F);
                    p.discard();
                    calaveras.remove(i);
                    calaveraData.remove(i);
                }
            }
        }
    }

    private void clearCalaveras(ServerLevel level) {
        for (UUID id : calaveras) {
            Entity e = level.getEntity(id);
            if (e != null) {
                Fx.chunks(level, e.position(), 6, 0.25F, 0xF6F0E6, 0.3);
                e.discard();
            }
        }
        calaveras.clear();
        calaveraData.clear();
    }

    // ---- La Piñata, swung (2026-10-04)

    @Nullable private Vec3 pinataAnchor;
    private double pinataPlane;
    private final java.util.Map<Integer, Integer> pinataHits = new java.util.HashMap<>();

    /**
     * La Piñata, reworked (the user: it just exploded, too easy): the potato piñata drops on its rope from high over the
     * middle of the sand and he sends it swinging like a wrecking ball (1-7.5 s), the line of each swing turned from the
     * last, candy bombs dropping off it as it goes; anyone it sweeps up loses a share of their health and goes flying. Then
     * he catches it at the bottom of a swing and smashes it open (8.5 s): the candy flies out in a spiral, ring after ring,
     * and the rest at you.
     */
    private void tickP3PinataSwing(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        double len = 20;
        Vec3 anchor = new Vec3(c.x, floor + 3 + len, c.z);
        pinataAnchor = anchor;
        BossProp pin = spudId != null && level.getEntity(spudId) instanceof BossProp bp && bp.kind() == BossProp.Kind.PINATA ? bp : null;
        if (t == 1) {
            BossProp p = BossProp.create(level, BossProp.Kind.PINATA, this, anchor.add(0, -2, 0)).life(260);
            level.addFreshEntity(p);
            spudId = p.getUUID();
            pinataPlane = target != null ? Math.atan2(target.getZ() - c.z, target.getX() - c.x) : random.nextDouble() * Math.PI * 2;
            pinataHits.clear();
            say("p3_swing", 50);
            crowdReact(level, CrowdMood.CHEER, 40);
            level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        // the swing: a pendulum (period 2.8 s, 40 degrees out), its plane turning 55 degrees a half swing
        int swingFrom = 22, swingTo = 152;
        double theta = 0;
        if (t < swingFrom) {
            // dropping into place on its rope, pulled out to the side ready to go
            double k = Mth.clamp(t / (double) swingFrom, 0, 1);
            theta = Math.toRadians(40) * k;
            placePinata(pin, anchor, Mth.lerp(1 - (1 - k) * (1 - k), 3, len), pinataPlane, theta);
        } else if (t < swingTo) {
            double ph = (t - swingFrom) * Math.PI * 2 / 56.0;
            theta = Math.toRadians(40) * Math.cos(ph);
            int half = (int) ((t - swingFrom) / 28.0);
            double plane = pinataPlane + half * Math.toRadians(55);
            if ((t - swingFrom) % 28 == 0 && t > swingFrom) {
                pinataPlane += 0;      // (the plane turns at the end of each half swing: from `half`)
                level.playSound(null, anchor.x, anchor.y - len, anchor.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 1.6F, 1.4F);
            }
            placePinata(pin, anchor, len, plane, theta);
            if (pin != null) {
                Vec3 bob = pin.position().add(0, 3.0, 0);
                if (t % 10 == 0) {
                    // a candy bomb shaken loose, straight down onto its warning
                    Vec3 to = new Vec3(bob.x, floor, bob.z);
                    level.addFreshEntity(BossProp.create(level, BossProp.Kind.CANDY, this, bob).toss(to.add(0, 0.2, 0), 14));
                    Fx.warn(level, to, 2.3F, 0xFF4AB0, 14);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(bob, bob).inflate(3.2), this::hittable)) {
                    if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(bob) > 2.8 + e.getBbWidth() / 2) continue;
                    Integer last = pinataHits.get(e.getId());
                    if (last != null && tickCount - last < 20) continue;
                    pinataHits.put(e.getId(), tickCount);
                    if (Mechanics.trueHit(e, 0.20F, this, true)) {
                        swingHit = true;
                        Vec3 dir = new Vec3(Math.cos(plane), 0, Math.sin(plane)).scale(-Math.signum(Math.sin((t - swingFrom) * Math.PI * 2 / 56.0)));
                        e.setDeltaMovement(dir.scale(1.6).add(0, 0.8, 0));
                        e.hurtMarked = true;
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.6F, 0.7F);
                        Fx.hit(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.6F, 0xFF4AB0);
                    }
                }
            }
        } else {
            // the swing dies away; he comes for it
            double k = Mth.clamp((t - swingTo) / 18.0, 0, 1);
            theta = 0;
            placePinata(pin, anchor, len, pinataPlane, theta * (1 - k));
        }
        if (t < swingTo) {
            if (target != null) turnToward(target.position(), 8F);
            hover(target);
        } else if (t < 170) {
            Vec3 bob = anchor.add(0, -len, 0);
            flyTo(bob.add(Math.cos(pinataPlane) * 4.5, -0.5, Math.sin(pinataPlane) * 4.5), 0.9);
            face(bob);
        }
        if (t == 170) {
            Vec3 hang = anchor.add(0, -len, 0);
            if (pin != null) pin.discard();
            spudId = null;
            for (int i = 0; i < 6; i++) Fx.spark(level, hang, 12, 1.6, 0.5F, PINATA_COLORS[i]);
            Fx.chunks(level, hang, 30, 0.35F, 0xFF4AB0, 1.0);
            Fx.chunks(level, hang, 30, 0.35F, 0xFFCE30, 1.0);
            Fx.flash(level, hang, 3F);
            level.playSound(null, hang.x, hang.y, hang.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 3.0F, 0.8F);
            level.playSound(null, hang.x, hang.y, hang.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 1.4F);
            PaponNetwork.shake(level, hang, 40, 0.6F, 14);
            crowdReact(level, CrowdMood.ROAR, 40);
            // the spiral: five arms of candy, ring after ring outward, each a little later and further round
            for (int arm = 0; arm < 5; arm++) {
                for (int i = 0; i < 7; i++) {
                    double a = Math.PI * 2 * arm / 5 + i * 0.38;
                    double r = 2.5 + i * 2.6;
                    Vec3 to = inSand(c.add(Math.cos(a) * r, 0, Math.sin(a) * r), 1.2, 0, 0);
                    to = new Vec3(to.x, floor, to.z);
                    int flight = 10 + i * 4;
                    level.addFreshEntity(BossProp.create(level, BossProp.Kind.CANDY, this, hang).toss(to.add(0, 0.2, 0), flight));
                    Fx.warn(level, to, 2.3F, 0xFF4AB0, flight);
                }
            }
            // and the rest at you
            for (Player pl : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48, 24, 48), this::hittable)) {
                for (int i = 0; i < 3; i++) {
                    Vec3 to = inSand(predict(pl, 18 + i * 6), 1.2, 0, 0);
                    to = new Vec3(to.x, floor, to.z);
                    level.addFreshEntity(BossProp.create(level, BossProp.Kind.CANDY, this, hang).toss(to.add(0, 0.2, 0), 18 + i * 6));
                    Fx.warn(level, to, 2.3F, 0xFF4AB0, 18 + i * 6);
                }
            }
        }
        if (t > 170 && target != null) turnToward(target.position(), 8F);
        if (t >= BossState.P3_PINATA_SWING.duration) setBossState(BossState.IDLE);
    }

    /** The piñata at the end of its rope: {@code theta} out from straight down, in the plane at maths angle {@code plane}. */
    private void placePinata(@Nullable BossProp pin, Vec3 anchor, double len, double plane, double theta) {
        if (pin == null) return;
        Vec3 dir = new Vec3(Math.cos(plane), 0, Math.sin(plane));
        Vec3 bob = anchor.add(dir.scale(Math.sin(theta) * len)).add(0, -Math.cos(theta) * len, 0);
        Vec3 base = bob.add(0, -3.0, 0);
        pin.setPos(base.x, base.y, base.z);
        pin.setDeltaMovement(Vec3.ZERO);           // placed here every tick (its own tick must not move it on)
        // its tilt for the renderer: the yaw whose facing is the plane, the pitch the swing
        pin.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        pin.setXRot((float) Math.toDegrees(theta));
    }

    // ---- Llamas del Coliseo

    /**
     * Llamas del Coliseo (5.5 s): he rises over the middle of the sand, arms up, and the crimson braziers round the
     * podium hurl burning potatoes at you one after another, on their warnings (normal damage, and fire where they land).
     */
    private void tickP3Braziers(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        flyTo(new Vec3(c.x, floor + 6, c.z), 0.7);
        if (target != null) turnToward(target.position(), 6F);
        if (t == 2) {
            say("p3_braziers", 40);
            crowdReact(level, CrowdMood.ROAR, 30);
            podiumBraziers = new ArrayList<>();
            if (inColiseum() && arena != null) {
                for (BlockPos p : arena.braziers()) if (Math.abs(p.getY() - (floor + Coliseum.PODIUM)) <= 2.5) podiumBraziers.add(p);
            }
            brazierNext = podiumBraziers.isEmpty() ? 0 : random.nextInt(podiumBraziers.size());
        }
        if (t >= 20 && t < 100 && t % 4 == 0 && target != null) {
            Vec3 from;
            if (!podiumBraziers.isEmpty()) {
                BlockPos p = podiumBraziers.get(brazierNext++ % podiumBraziers.size());
                from = Vec3.atCenterOf(p).add(0, 0.9, 0);
            } else {
                double a = random.nextDouble() * Math.PI * 2;
                from = position().add(Math.cos(a) * 10, 9, Math.sin(a) * 10);
            }
            Vec3 aim = predict(target, 14).add(random.nextGaussian() * 1.4, 0, random.nextGaussian() * 1.4);
            aim = new Vec3(aim.x, floor, aim.z);
            level.addFreshEntity(BossProp.create(level, BossProp.Kind.METEOR, this, from).meteor(aim, 16));
            Fx.warn(level, aim, 2.0F, 0xFF2A34, 16);
            Fx.spark(level, from, 8, 0.4, 0.45F, 0xFF2A34);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.6F, 0.7F);
        }
        if (t >= BossState.P3_BRAZIERS.duration) setBossState(BossState.IDLE);
    }

    // ---- La Supernova

    /**
     * La Supernova (7.5 s): up over the middle of the sand; he curls into a star that grows and grows (1-6.5 s), the whole
     * sand marked red but for a golden ring under him: his shadow. When it goes (6.5 s), everyone outside it loses 70% of
     * their health. Then he drops out of the sky, spent (P3_SPENT: he takes double).
     */
    private void tickP3Nova(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : (home != null ? home : position());
        double floor = floorNear(c);
        Vec3 hold = new Vec3(c.x, floor + 9, c.z);
        flyTo(hold, 0.9);
        Vec3 ground = new Vec3(getX(), floor, getZ());
        if (t == 2) {
            say("p3_nova", 90);
            crowdReact(level, CrowdMood.GASP, 100);
            title(level, "title.papon.supernova", 40);
        }
        if (t >= 20 && t < 130) {
            if (t % 5 == 0) {
                Fx.zone(level, ground, 3.4F, 0xFFE07A, 6, 0.55F);
                Fx.warn(level, ground, 3.4F, 0xFFE07A, 6);
            }
            if (t % 10 == 0) {
                Fx.zone(level, new Vec3(c.x, floor, c.z), 30F, Fx.DANGER, 11, 0.12F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F + (t - 20) / 110F);
                PaponNetwork.shake(level, position(), 60, 0.05F + 0.4F * (t - 20) / 110F, 10);
            }
            if (t % 2 == 0) {
                double a = random.nextDouble() * Math.PI * 2, b = random.nextDouble() * Math.PI - Math.PI / 2;
                Vec3 off = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(7);
                Fx.spark(level, position().add(0, getBbHeight() * 0.5, 0).add(off), 2, 0.1, 0.4F, 0xFFF2C0);
            }
            if (t == 40 || t == 90) {
            }
        }
        if (t == 130) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(56, 30, 56), this::hittable)) {
                double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
                if (d <= 3.5 + e.getBbWidth() / 2) continue;
                if (Mechanics.trueHit(e, 0.70F, this, true)) {
                    swingHit = true;
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(2.0).add(0, 0.9, 0));
                    e.hurtMarked = true;
                    e.setSecondsOnFire(5);
                }
            }
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 8F);
            Fx.shock(level, ground, 30F, 0xFFF2C0);
            Fx.blast(level, position(), 5F);
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 3 * i;
                bolt(level, ground.add(Math.cos(a) * 12, 0, Math.sin(a) * 12));
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5.0F, 0.4F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5.0F, 0.6F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 1.4F);
            PaponNetwork.shake(level, position(), 72, 1.0F, 40);
            crowdReact(level, CrowdMood.ROAR, 60);
        }
        if (t >= BossState.P3_NOVA.duration) {
            setFlying(false);
            setBossState(BossState.P3_SPENT);
        }
    }

    /** Spent after the supernova: down out of the sky onto one knee, steaming, panting: four seconds of taking double. */
    private void tickP3Spent(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (t == 12) say("p3_siesta", 50);
        if (t % 8 == 0) Fx.smoke(level, position().add(0, getBbHeight() * 0.9, 0), 2, 0.4, 1.2F, 0xF2EEE8);
        if (t >= BossState.P3_SPENT.duration) {
            think = 6;
            setBossState(BossState.IDLE);
        }
    }

    // ---- Nitro

    /** "Get in, amigo! Oh, wait... you are the ROAD.": onto the Skyline's roof and in at the wheel, the nitro on. */
    private void tickP3Nitro(ServerLevel level, @Nullable LivingEntity target) {
        CaravanEntity van = van(level);
        if (van == null || van.isVehicle() || van.isFlying() || stateTicks > 120) {
            aNitroCd = 200;
            setBossState(BossState.IDLE);
            return;
        }
        if (stateTicks == 2) say("p3_nitro", 50);
        Vec3 roof = van.position().add(0, 2.6, 0);
        flyTo(roof, 1.1);
        face(van.position().add(van.forward().scale(4)));
        if (position().distanceTo(roof) < 2.0 || stateTicks > 70) {
            setFlying(false);
            nitro = true;
            if (!startRiding(van, true)) {
                nitro = false;
                setBossState(BossState.IDLE);
                return;
            }
            van.setRamShare(0.35F);
            level.playSound(null, van.getX(), van.getY(), van.getZ(), com.groid.papon.registry.ModSounds.SKYLINE_REV.get(), SoundSource.HOSTILE, 2.5F, 1.1F);
            level.playSound(null, van.getX(), van.getY(), van.getZ(), com.groid.papon.registry.ModSounds.SKYLINE_BOV.get(), SoundSource.HOSTILE, 1.6F, 1.0F);
            Fx.spark(level, van.position().add(0, 0.3, 0), 30, 1.8, 0.45F, 0xB050FF);
            startDriving();
        }
    }

    // ------------------------------------------------------------------ phase two: flight

    /** Flying: circles you a few blocks up, bobbing, always facing you, kept over the sand. */
    private void hover(@Nullable LivingEntity target) {
        setFlying(true);
        getNavigation().stop();
        Vec3 c = target != null ? target.position() : (home != null ? home : position());
        boolean god = isAscended();
        orbitAngle += (god ? 0.05F : 0.03F) * orbitDir;
        double r = target == null ? 0 : god ? 7.5 : 6.5;
        double h = (god ? 4.2 : 3.4) + Math.sin(tickCount * 0.07) * 0.8;
        Vec3 pt = clampToArena(c.add(Math.cos(orbitAngle) * r, h, Math.sin(orbitAngle) * r));
        Vec3 d = pt.subtract(position());
        double len = d.length();
        // Ascended flies twice as fast as the risen potato did
        boolean dead = isMuertos();
        Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(dead ? 1.5 : god ? 1.1 : 0.5, len * (dead ? 0.3 : god ? 0.24 : 0.14)) / len);
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
                    } else { setBossState(BossState.IDLE); }
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

    public int foilsLeft() { return foils.size(); }

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
    public boolean canHit(LivingEntity e) { return hittable(e); }

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
        if (ascendedFight()) {
            // Ascended does not stagger aside: a flash, and he is somewhere else
            Vec3 to = inSand(position().add(side.scale(5)).add(away.scale(3)), 3, 0.5, 12);
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 1.6F);
            noPhysics = false;
            setPos(to.x, Math.max(to.y, getY()), to.z);
            setDeltaMovement(Vec3.ZERO);
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 1.6F);
            face(from.position());
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 1.6F);
            if (random.nextInt(3) == 0) say("p3_flash", 30);
            return;
        }
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
            } else { setBossState(BossState.IDLE); }
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
        if (stateTicks == 2) { level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 1.5F, 0.6F); }
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
    /** Ticks for each key (1 s). */
    public static final int QTE_WINDOW = 20;
    /** The tango's steps: the arrow keys. */
    private static final int[] TANGO_KEYS = {org.lwjgl.glfw.GLFW.GLFW_KEY_UP, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT};
    /** This QTE's ticks per key. */
    private int qteWindow = QTE_WINDOW;

    /** Ticks a step of the tango gets: a beat of the song (and a little for the network). */
    private int tangoBeat() {
        Song song = Song.get(songId);           // phase three dances to Para Siempre
        if (song == null || song.beats.length < 2) return 12;
        double beat = (song.beats[song.beats.length - 1] - song.beats[0]) / (song.beats.length - 1);
        return (int) Math.round(beat * 20) + 1;
    }
    /** Ticks at the start when presses do not count (you were mid-fight, mashing keys); the first key's clock starts after. */
    public static final int QTE_GRACE = 10;
    /** The spam QTE (kind 5): ticks to fill the meter, after the grace. */
    public static final int SPAM_TICKS = 90;

    private void startQte(ServerLevel level, LivingEntity e) {
        int kind = entityData.get(DATA_QTE);
        hold(e);
        setDeltaMovement(Vec3.ZERO);
        int n = switch (kind) {
            case 0 -> 4;
            case 1 -> 5;
            case 3 -> 20;              // mash it
            case 4 -> 9;               // the tango: nine steps
            case 5 -> 2;               // the squeeze: K and L, mashed in turn (always those two)
            default -> 6;
        };
        qteWindow = kind == 3 ? 6 : kind == 4 ? tangoBeat() : kind == 5 ? SPAM_TICKS : QTE_WINDOW;
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
            } else if (kind == 5) {
                // the same two keys every time (the user: it is button mashing, the keys must never change)
                k = i == 0 ? org.lwjgl.glfw.GLFW.GLFW_KEY_K : org.lwjgl.glfw.GLFW.GLFW_KEY_L;
            } else {
                do {
                    k = QTE_KEYS[random.nextInt(QTE_KEYS.length)];
                } while (i > 0 && k == qteKeys[i - 1]);
            }
            qteKeys[i] = k;
        }
        qteStep = 0;
        qteResolved = false;
        qteSaid = 0;
        entityData.set(DATA_METER, kind == 5 ? 0.2F : 0F);
        say(kind <= 2 ? "qte" + kind : kind == 3 ? "qte1" : kind == 5 ? "p3_squeeze" : ascendedFight() ? "p3_tango" : "charm0", 60);
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
        entityData.set(DATA_METER, qteStep / (float) Math.max(1, qteKeys.length));
        // his charm as you keep up: impressed halfway through
        if ((qteSaid & 1) == 0 && qteStep == Math.max(2, qteKeys.length / 2) && qteKeys.length > 2) {
            qteSaid |= 1;
            say(qteKind() == 4 ? "tango_good" : "qte_quick", 40);
        }
        if (qteStep >= qteKeys.length) endQte(true, false);
    }

    /** Lines said in this QTE so far (bits): halfway impressed, strong, weak. */
    private int qteSaid;

    /**
     * From the held player's client, a few times a second: how the squeeze (its meter) or the duel (its rock meter) is going.
     * He feels it: his grip (synced, the arms and the held player's body follow it), and what he says.
     */
    private int tangoSaid = -999;

    public void qteMeter(ServerPlayer player, float v) {
        BossState s = getBossState();
        if (s == BossState.QTE_HOLD && qteKind() == 5 && heldId() == player.getId() && !qteResolved) {
            entityData.set(DATA_METER, v);
            if ((qteSaid & 2) == 0 && v > 0.72F) {
                qteSaid |= 2;
                say("qte_strong", 40);
            } else if ((qteSaid & 4) == 0 && v < 0.12F && stateTicks > QTE_GRACE + 30) {
                qteSaid |= 4;
                say("qte_weak", 40);
            }
        } else if (s == BossState.P4_TANGO && tangoWho != null && tangoWho.equals(player.getUUID())) {
            entityData.set(DATA_METER, v);
            if (stateTicks > 120 && stateTicks - tangoSaid > 110) {
                if (v > 0.7F) {
                    tangoSaid = stateTicks;
                    say("p4_tango_good", 40);
                } else if (v < 0.3F) {
                    tangoSaid = stateTicks;
                    say("p4_tango_bad", 40);
                }
            }
        } else if (s == BossState.P3_DUEL && duelWho != null && duelWho.equals(player.getUUID())) {
            entityData.set(DATA_METER, v);
            if (stateTicks > 90 && (qteSaid & 8) == 0) {
                if (v > 0.78F) {
                    qteSaid |= 8;
                    say("duel_good", 40);
                } else if (v < 0.3F) {
                    qteSaid |= 8;
                    say("duel_bad", 40);
                }
            }
        }
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
            int kind = qteKind();
            say(kind == 5 ? "qte_slip" : kind == 4 ? "tango_good" : random.nextInt(3) == 0 ? "qte_escape" : "qte_broken", 50);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.6F);
            if (level() instanceof ServerLevel level) com.groid.papon.fx.Fx.hit(level, position().add(0, 1.6, 0), 1.6F, 0xFFFFFF);
            setBossState(BossState.QTE_BROKEN);
        } else {
            int kind = qteKind();
            if (kind != 5) say(kind == 4 ? "tango_bad" : "qte_slow", 40);     // the squeeze's line comes with the crush
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
        int limit = qteKind() == 5 ? 30 + QTE_GRACE + qteWindow : 20 + QTE_GRACE + qteKeys.length * (qteWindow + 8);
        if (stateTicks > limit || (!(v instanceof Player) && stateTicks > 50)) endQte(false, false);
    }

    private void tickQteFail(ServerLevel level) {
        int t = stateTicks;
        LivingEntity v = held(level);
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        int kind = qteBase();
        if (t == 24 && v != null) {
            Mechanics.trueHit(v, qteKind() == 5 ? 0.45F : qteKind() == 3 ? 0.30F : qteKind() == 4 ? 0.45F : kind == 0 ? 0.35F : kind == 1 ? 0.25F : 0.40F,
                    this, true);
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
                case 3 -> {
                    // the squeeze: crushed in his fists, a crack of golden light
                    if (qteKind() == 5) say("qte_crush", 40);
                    Mechanics.stun(v);
                    com.groid.papon.fx.Fx.flash(level, at, 2F);
                    com.groid.papon.fx.Fx.hit(level, at, 2.0F, 0xFFE07A);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.5F);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.6F);
                    PaponNetwork.shake(level, position(), 16, 0.9F, 20);
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
            } else { setBossState(BossState.IDLE); }
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
        if (warning && t % 6 == 0) { for (Vec3 hole : Masher.holes(yaw, variant)) com.groid.papon.fx.Fx.warn(level, c.add(hole), 2.2F, 0x6CFF6A, 8); }
        if (t % 14 == 0 && warning) level.playSound(null, c.x, c.y + h, c.z, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 3.0F, 0.5F);
        if (t == 22) {
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

    /** ¡La Ola!: he whips the stands into the wave (1.8 s); as it goes round, the salsa goes across the sand. */
    private void tickArenaOla(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        if (t == 2) crowdReact(level, CrowdMood.WAVE, 220);
        if (t == 14 && arena != null) {
            // three waves, 2 s apart, each across a different way: the long way, the short way, the long way back
            boolean first = random.nextBoolean();
            int s0 = random.nextBoolean() ? 1 : -1;
            olaWaves[0] = new int[] {24, first ? 0 : 1, s0};
            olaWaves[1] = new int[] {64, first ? 1 : 0, random.nextBoolean() ? 1 : -1};
            olaWaves[2] = new int[] {104, first ? 0 : 1, -s0};
            for (int w = 0; w < 3; w++) {
                double across = olaWaves[w][1] == 0 ? Coliseum.B : Coliseum.A;
                olaGap[w] = (random.nextDouble() * 2 - 1) * (across * 0.6);
                olaProps[w] = null;
            }
            olaT = 0;
            olaHit.clear();
            say("charm5", 30);
        }
        if (target != null && t > 16) turnToward(target.position(), 10F);
        if (t >= BossState.ARENA_OLA.duration) {
            think = 2;
            setBossState(BossState.IDLE);
        }
    }

    /**
     * Waves of salsa in progress (reworked 2026-10-04, the user: "a threatening wave of salsa hurling at you", not
     * particles): each warned along its starting edge for 1.2 s, its one gap marked green; then a curling wall of salsa
     * three and a half blocks high (BossProp SALSA_WAVE, drawn by client/PropRenderer) rolls across the sand at 0.8 b/t.
     * Too tall to jump: get to the gap.
     */
    private void tickOla(ServerLevel level) {
        if (arena == null) {
            olaT = -1;
            return;
        }
        int t = olaT++;
        Vec3 c = arena.centerVec();
        double floor = arena.floorY();
        boolean any = false;
        for (int w = 0; w < olaWaves.length; w++) {
            int[] wave = olaWaves[w];
            if (wave == null) continue;
            boolean onX = wave[1] == 0;
            int dir = wave[2];
            double half = onX ? Coliseum.A : Coliseum.B, across = onX ? Coliseum.B : Coliseum.A;
            double front = -dir * half + dir * 0.8 * (t - wave[0]);
            double gap = olaGap[w];
            boolean done = t >= wave[0] + (int) (2 * half / 0.8) + 2;
            if (!done) any = true;
            if (t >= wave[0] - 24 && t < wave[0] && (t - wave[0]) % 6 == 0) {
                Vec3 a = olaPoint(c, floor, onX, -dir * (half - 1), -across), b = olaPoint(c, floor, onX, -dir * (half - 1), across);
                com.groid.papon.fx.Fx.warnLine(level, a, b, 1.6F, 0xE0301E, 8);
                // the way through: the gap, in green, right across the sand
                Vec3 g0 = olaPoint(c, floor, onX, -dir * (half - 1), gap), g1 = olaPoint(c, floor, onX, dir * (half - 1), gap);
                com.groid.papon.fx.Fx.warnLine(level, g0, g1, 2.6F, 0x40E060, 8);
                level.playSound(null, a.x, floor, a.z, SoundEvents.BUCKET_FILL_LAVA, SoundSource.HOSTILE, 2.0F, 0.6F);
            }
            Entity pe = olaProps[w] != null ? level.getEntity(olaProps[w]) : null;
            if (done || t < wave[0] || Math.abs(front) > half + 2) {
                if (done && pe != null) {
                    pe.discard();
                    olaProps[w] = null;
                }
                continue;
            }
            Vec3 mid = olaPoint(c, floor, onX, front, 0);
            Vec3 v = onX ? new Vec3(dir * 0.8, 0, 0) : new Vec3(0, 0, dir * 0.8);
            if (pe == null) {
                BossProp p = BossProp.create(level, BossProp.Kind.SALSA_WAVE, this, mid).life(200);
                p.setYRot(onX ? (dir > 0 ? 0F : 180F) : (dir > 0 ? 90F : -90F));
                p.setRadius((float) across);
                p.setGap(gap);
                level.addFreshEntity(p);
                olaProps[w] = p.getUUID();
                level.playSound(null, mid.x, floor, mid.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 3.0F, 0.5F);
                PaponNetwork.shake(level, mid, 40, 0.25F, 10);
            } else {
                pe.setPos(mid.x, floor, mid.z);
                pe.setDeltaMovement(v);
            }
            // spray off the crest, here and there
            if (t % 4 == 0) {
                for (double k = -across + random.nextDouble() * 3; k <= across; k += 3) {
                    if (Math.abs(k - gap) < 2) continue;
                    Vec3 at = olaPoint(c, floor, onX, front + dir * 0.4, k).add(0, 3.2, 0);
                    if (Coliseum.offset(at.x - c.x, at.z - c.z) > -0.5) continue;
                    com.groid.papon.fx.Fx.jet(level, at, v.scale(0.4).add(0, 0.25, 0), 1.0F, 0xC0261A);
                }
            }
            if (t % 5 == 0) level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.5F, 0.6F);
            // anyone in its way but in the gap (it is too tall to jump)
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, arena.bounds(), this::hittable)) {
                double along = onX ? e.getX() - c.x : e.getZ() - c.z;
                double side = onX ? e.getZ() - c.z : e.getX() - c.x;
                if (Math.abs(along - front) > 1.2 + e.getBbWidth() * 0.5 || e.getY() - floor > 3.2) continue;
                if (Math.abs(side - gap) < 1.6) continue;
                if (!olaHit.add(w * 100000 + e.getId())) continue;
                if (Mechanics.trueHit(e, 0.18F, this, true)) {
                    swingHit = true;
                    Mechanics.heat(e, 2, this);
                    e.setDeltaMovement(onX ? dir * 1.1 : 0, 0.55, onX ? 0 : dir * 1.1);
                    e.hurtMarked = true;
                    com.groid.papon.fx.Fx.splat(level, e.position(), 1.2F, 0xA81E14, 120);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
                }
            }
        }
        if (!any) {
            olaT = -1;
            for (int w = 0; w < olaProps.length; w++) {
                Entity pe = olaProps[w] != null ? level.getEntity(olaProps[w]) : null;
                if (pe != null) pe.discard();
                olaProps[w] = null;
            }
        }
    }

    private static Vec3 olaPoint(Vec3 c, double floor, boolean onX, double along, double across) {
        return onX ? new Vec3(c.x + along, floor, c.z + across) : new Vec3(c.x + across, floor, c.z + along);
    }

    /** ¡Estampida!: a whistle and a point to each side of the stands (1.8 s): the potatoes come. */
    private void tickArenaStampede(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        if (t == 2) crowdReact(level, CrowdMood.ROAR, 60);
        if (t == 10) level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.PARROT_IMITATE_PIGLIN_BRUTE, SoundSource.HOSTILE, 3.0F, 1.6F);
        if (t == 16 && arena != null) {
            stampedeT = 0;
            say("catch", 30);
        }
        if (target != null && t > 18) turnToward(target.position(), 10F);
        if (t >= BossState.ARENA_STAMPEDE.duration) {
            think = 2;
            setBossState(BossState.IDLE);
        }
    }

    /**
     * The volleys (0, 1.75, 3.5 s): five potatoes along one long side of the sand, then the other, each lane
     * half a gap over from the last, so the gap you stood in is a lane now. Each waits 1.1 s at the edge (its
     * lane marked across the sand), then rolls over at 0.6 b/t.
     */
    private void tickStampede(ServerLevel level) {
        if (arena == null) {
            stampedeT = -1;
            return;
        }
        int t = stampedeT++;
        int v = t / 35;
        if (v > 2) {
            stampedeT = -1;
            return;
        }
        if (t % 35 != 0) return;
        Vec3 c = arena.centerVec();
        double floor = arena.floorY();
        int side = (v % 2 == 0) == (getZ() < c.z) ? 1 : -1;        // the first volley comes from his side of the bowl
        double shift = v == 1 ? 4.5 : (v == 2 ? 2.25 : 0) + random.nextInt(2) * 1.0;
        for (int k = -2; k <= 2; k++) {
            double x = k * 9 + shift;
            if (Math.abs(x) > Coliseum.A - 6) continue;
            double edge = Coliseum.B * Math.sqrt(1 - (x / Coliseum.A) * (x / Coliseum.A)) - 2.5;
            Vec3 from = new Vec3(c.x + x, floor, c.z - side * edge), to = new Vec3(c.x + x, floor, c.z + side * edge);
            int delay = 22;
            double speed = 0.6;
            BossProp p = BossProp.create(level, BossProp.Kind.ROLLER, this, from).fry(new Vec3(0, 0, side), speed, delay)
                    .life(delay + (int) (2 * edge / speed) + 2);
            level.addFreshEntity(p);
            com.groid.papon.fx.Fx.warnLine(level, from, to, 1.8F, 0xFF8A2A, delay);
            com.groid.papon.fx.Fx.chunks(level, from.add(0, 2.0, 0), 10, 0.5F, 0xEED69C, 0.5);
        }
        level.playSound(null, c.x, floor + 6, c.z - side * Coliseum.B, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 1.3F);
        crowdReact(level, CrowdMood.ROAR, 40);
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
            case ROLLER -> {
                if (Mechanics.trueHit(e, 0.22F, this, true)) {
                    swingHit = true;
                    Vec3 v = p.getDeltaMovement().normalize();
                    e.setDeltaMovement(v.x * 1.3, 0.55, v.z * 1.3);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, true));
                    com.groid.papon.fx.Fx.hit(level, at, 1.2F, 0xEED69C);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.5F);
                    if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.9F, 12);
                }
            }
            case CANDY -> {
                candyBlast(level, p.position());
                p.discard();
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
        if (p.kind() == BossProp.Kind.CANDY) {
            candyBlast(level, at);
            return;
        }
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
        } else { com.groid.papon.fx.Fx.chunks(level, at.add(0, 0.3, 0), big ? 10 : 5, 0.3F, 0xE9B85A, 0.4); }
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

    private float dmg() { return PaponConfig.BOSS_DAMAGE.get().floatValue() * (isRisen() ? 1.35F : 1F); }

    private DamageSource source(ResourceKey<DamageType> type) { return ModDamage.of((ServerLevel) level(), type, this, this); }

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
        if (armored(s, stateTicks)) {
            // mid-move: it clangs off the shimmer round him
            armorClang(attacker);
            return false;
        }
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
            if (melee && evadeCd <= 0 && random.nextFloat() < (isPhaseTwo() ? 0.18F : 0.12F)) {
                evade(level, (LivingEntity) source.getDirectEntity());
                return false;
            }
            boolean shot = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
            float share = Math.min(amount, getMaxHealth() * PaponConfig.HIT_CAP.get().floatValue()) / getMaxHealth();
            if (!ascendedFight() && guardCd <= 0 && !isFlying() && ((shot && random.nextInt(4) == 0) || recentHurt + share > 0.12F)) {
                if (attacker != null) face(attacker.position());
                guardCd = isPhaseTwo() ? 240 : 320;
                setBossState(BossState.GUARD);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.4F, 0.6F);
                return false;
            }
        }
        if (s == BossState.EXIT_STUMBLE || s == BossState.WINDED) amount *= 1.5F;
        if (s == BossState.STAGGER) amount *= 1.75F;
        if (s == BossState.QTE_BROKEN || s == BossState.P3_SPENT || s == BossState.P3_DUEL_SHAME || s == BossState.P4_TANGO_LOSE) amount *= 2F;
        if (s.unstoppable()) amount *= 0.5F;
        if (isPassenger()) amount *= 0.5F;
        if (isRisen() && foilsLeft() > 0) {
            // the foil potatoes soak some of it up
            amount *= 0.6F;
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.0F, 1.4F);
                com.groid.papon.fx.Fx.spark(level, position().add(0, getBbHeight() * 0.6, 0), 4, 0.4, 0.3F, 0xDDE6F0);
            }
        }
        float cap = getMaxHealth() * PaponConfig.HIT_CAP.get().floatValue() * (s == BossState.QTE_BROKEN ? 2F : 1F);
        amount = Math.min(amount, cap);
        // his composure: past a burst's worth, hits glance off (big damage does not skip the mechanics)
        boolean open = s == BossState.STAGGER || s == BossState.EXIT_STUMBLE || s == BossState.QTE_BROKEN || s == BossState.P3_SPENT
                || s == BossState.P3_DUEL_SHAME || s == BossState.WINDED || s == BossState.P4_TANGO_LOSE;
        float room = Math.max(0, getMaxHealth() * PaponConfig.BURST_CAP.get().floatValue() * (open ? 3F : 1F) - burst);
        if (amount > room) {
            capHits += 1;
            composure(attacker);
            amount = room;
            if (amount < 0.05F) return false;
        }
        // face-tanking him in melee: the hit lands, and then he bursts (the salsa, or his light) to push you off
        boolean burstNow = attacker instanceof Player p && p.distanceTo(this) < 4.5 && !open && pressed(p, s);
        boolean hurt = super.hurt(source, amount);
        if (burstNow && isAlive()) {
            setBossState(BossState.SALSA_BURST);
            saltBurstCd = isPhaseTwo() ? 160 : 220;
        }
        if (hurt) {
            recentHurt += amount / getMaxHealth();
            burst += amount;
        }
        if (hurt && attacker instanceof Player && crowdBooCd <= 0 && amount > getMaxHealth() * 0.008F && level() instanceof ServerLevel level) {
            crowdBooCd = 70;
            crowdReact(level, CrowdMood.BOO, 45);
            // what hit him: his own van, an arrow from afar, a real wallop, or just a hit
            boolean byVan = source.getDirectEntity() instanceof CaravanEntity && attacker instanceof Player;
            boolean ranged = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile && attacker instanceof Player;
            if (byVan) retort("runover", 300, 1F, inColiseum() ? new String[]{"runover0", "runover1", "runover2"} : new String[]{"runover0", "runover1"});
            else if (ranged) retort("ranged", 500, 0.5F, "ranged0", "ranged1");
            else if (amount > getMaxHealth() * 0.03F) retort("bighit", 600, 0.5F, ascendedFight() ? "p3_hit3" : "bighit");
            else quip("hit" + random.nextInt(4), 0.2F);
        }
        if (hurt && !isPhaseTwo() && !phaseDue && getHealth() <= getMaxHealth() * PHASE_TWO_AT && getHealth() > 0) phaseDue = true;
        if (hurt && isPhaseThree() && !isPhaseFour() && !phaseFourDue && !getBossState().phaseFour()
                && getHealth() <= getMaxHealth() * PHASE_FOUR_AT && getHealth() > 0) {
            phaseFourDue = true;
        }
        if (hurt && isPhaseTwo() && !isPhaseThree() && !phaseThreeDue && getHealth() <= getMaxHealth() * PHASE_THREE_AT && getHealth() > 0) {
            phaseThreeDue = true;
            p3From = attacker != null ? attacker.position() : from;
        }
        return hurt;
    }

    /** No knockback while he cannot be stopped (he has full resistance anyway; this is for the mods that ignore it). */
    @Override
    public void knockback(double strength, double x, double z) {
        if (!getBossState().unstoppable() && !getBossState().qte()) super.knockback(strength, x, z);
    }

    public boolean isGuarding() { return getBossState() == BossState.GUARD; }

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

    // ------------------------------------------------------------------ phase four: Dia de los Muertos

    /*
     * At a quarter of his health the potato god does not fall: he rises over the sand laughing, the clouds part on the moon,
     * a spirit hammer forms in his hand out of blue fire and he strikes the moon with its lightning. It turns: a blue
     * calavera. Its rays come down on the band, one by one, and round the stands: everyone in the bowl turned to the
     * skeletons they truly are. Then the moon floods the bowl blue, and behind it El Coliseo Papa becomes El Coliseo de los
     * Muertos, twice the size (Coliseum.dead, swapFrom). He comes down into the middle of it, looks round ("centuries..."),
     * and burns down to his truest form: a sugar-skull skeleton with bone wings, El Rey de los Muertos. His moves from there
     * on are all new, faster and harder than the god's, bright as the fiesta (choosePhaseFour).
     */

    /** Phase four starts when he is down to this share of his health. */
    private static final float PHASE_FOUR_AT = 0.25F;
    /** Where the moon hangs over his coliseum: this far away, this high (radians), over the band stand (north). */
    public static final double MOON_DIST = 220, MOON_ELEV = Math.toRadians(38);
    private boolean phaseFourDue;
    /** The coliseum being turned into the dead's (the old one, while the swap runs). */
    @Nullable private Coliseum p4Old;
    @Nullable private Vec3 p4Rise;
    /** P4_FALL: where he lands on his back, and where the skid stops. */
    @Nullable private Vec3 p4Land, p4Skid;
    /** The Final Guardian's company: Quetzalcoatl and Kukulcan, the muchachas, and Ignis's head on the sand. */
    private final List<UUID> serpentIds = new ArrayList<>();
    private final List<UUID> muchachas = new ArrayList<>();
    @Nullable private UUID ignisHead;
    // cooldowns of his moves, and their state between ticks
    private int dMartilloCd = 20, dGiganteCd = 260, dGrietaCd = 340, dPetalosCd = 160, dPapelCd = 200, dVelasCd = 240, dDanzaCd = 300,
            dLunaCd = 120, dSerpientesCd = 200, dFlechasCd = 90;
    @Nullable private UUID giantId;
    @Nullable private Vec3 giantAim;
    /** La Grieta: the rift's cells (x, z: world block coordinates) in the order they tear open, and where it starts. */
    private final List<int[]> rift = new ArrayList<>();
    private int riftOpen;
    @Nullable private Vec3 riftA, riftB;
    private final List<UUID> souls = new ArrayList<>();
    /** The petal spirals: when each petal left him (tick in the move) and its arm's angle (synced: the base angle and the turn). */
    private final java.util.Map<UUID, Integer> petalHit = new java.util.HashMap<>();
    /** Papel picado banners in the air: prop id, its line (x, z of the middle), heading, half length, high/low, ticks alive. */
    private final List<double[]> papel = new ArrayList<>();
    private final List<UUID> papelProps = new ArrayList<>();
    private final java.util.Set<Long> papelHits = new java.util.HashSet<>();
    /** La Danza: the dancers (echoes), and which one is spinning through you now. */
    private final List<UUID> dancers = new ArrayList<>();
    @Nullable private UUID danzaTarget;
    private int danzaNext;
    /** La Luna and the hammer's lightning: where each strike will land and when (game tick), and its radius. */
    private final List<double[]> strikes = new ArrayList<>();
    private final java.util.Set<Long> velasHits = new java.util.HashSet<>();

    /** His truest form: the skeleton (form 4). */
    public boolean isMuertos() { return form() >= 4; }

    /** The fight in phase four (after the cutscene). */
    public boolean muertosFight() { return isMuertos() && entityData.get(DATA_PHASE4); }

    public boolean isPhaseFour() { return entityData.get(DATA_PHASE4); }

    /** What the moon is: 0 not shown, 1 the potato moon (the clouds parted), 2 the dead's blue calavera. */
    public int moon() { return entityData.get(DATA_MOON); }

    /** What he holds out of thin air: 0 nothing, 1 the spirit hammer, 2 a spirit guitar. */
    public int spiritProp() { return entityData.get(DATA_P4PROP); }

    /** The fog over his awakening (phase four's cutscene), 0..1. */
    public float fog() { return entityData.get(DATA_FOG); }

    /** The souls' green light in him, 0..1. */
    public float glow() { return entityData.get(DATA_GLOW); }

    /** A synced angle for the move he is doing (the petal spirals' start). */
    public float moveAngle() { return entityData.get(DATA_P4ANGLE); }

    /** Where the moon hangs (both sides): over his coliseum's band stand, or over where he fights. */
    public Vec3 moonPos() {
        Vec3 c = fightCenter();
        return c.add(0, Math.sin(MOON_ELEV) * MOON_DIST, -Math.cos(MOON_ELEV) * MOON_DIST);
    }

    /** Client: the arena's middle (or his home), the moon's rays aim from there. */
    public Vec3 fightCenter() {
        if (level().isClientSide) {
            org.joml.Vector3f v = entityData.get(DATA_CENTER);
            if (v.lengthSquared() > 0) return new Vec3(v.x(), v.y(), v.z());
        }
        return inColiseum() && arena != null ? arena.centerVec() : home != null ? home : position();
    }

    private boolean canStartPhaseFour() {
        BossState s = getBossState();
        if (s.cutscene() || s.intro() || s.qte() || s == BossState.DEATH || s == BossState.ROAR) return false;
        if (s == BossState.P3_DUEL || isPassenger() || s == BossState.ENTER_VAN || s == BossState.DRIVING) return false;
        return isPhaseThree();
    }

    /** A quarter health: thrown flat on his back out of whatever he was doing; the stands gasp, the band stops dead. */
    private void startPhaseFour(ServerLevel level) {
        phaseFourDue = false;
        release();
        getNavigation().stop();
        clearBeams(level);
        bandWaves.clear();
        if (duelGuitarist >= 0) giveGuitarBack(level, false);
        discardFoils(level);
        clearMuertos(level);
        clearGuardian(level);
        if (getVehicle() != null) stopRiding();
        LivingEntity hitter = getTarget();
        Vec3 from = hitter != null ? hitter.position() : position().add(Vec3.directionFromRotation(0, yBodyRot).scale(4));
        Vec3 away = position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = Vec3.directionFromRotation(0, yBodyRot + 180);
        away = away.normalize();
        p4Rise = position();
        p4Land = onSand(position().add(away.scale(9)));
        p4Skid = onSand(p4Land.add(away.scale(2.4)));
        face(from);
        // (2026-10-04, the user: "the crowd needs to be silent") not a sound from the stands from here to the souls
        crowdMood(level, CrowdMood.HUSH);
        for (MariachiEntity m : band(level)) {
            m.stop();
            m.scare(4 + random.nextInt(10));
        }
        level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.4F);
        level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
        Fx.hit(level, position().add(0, getBbHeight() * 0.6, 0), 2.4F, 0xD02020);
        PaponNetwork.shake(level, position(), 64, 0.6F, 16);
        entityData.set(DATA_FOG, 0F);
        entityData.set(DATA_GLOW, 0F);
        entityData.set(DATA_P4PROP, 0);
        setBossState(BossState.P4_FALL);
        setFlying(false);
        noPhysics = true;
        PaponMod.LOG.info("[boss] phase four: the Final Guardian (hp {}/{})", (int) getHealth(), (int) getMaxHealth());
    }

    private Vec3 p4Center() { return inColiseum() && arena != null ? arena.centerVec() : home != null ? home : position(); }

    /** A point kept on the sand (well inside its ellipse), at the floor. */
    private Vec3 onSand(Vec3 p) {
        if (!inColiseum() || arena == null) return new Vec3(p.x, groundY(p), p.z);
        Vec3 c = arena.centerVec();
        double dx = p.x - c.x, dz = p.z - c.z;
        double k = Math.sqrt(dx * dx / (arena.a * arena.a * 0.72 * 0.72) + dz * dz / (arena.b * arena.b * 0.72 * 0.72));
        if (k > 1) {
            dx /= k;
            dz /= k;
        }
        return new Vec3(c.x + dx, arena.floorY(), c.z + dz);
    }

    /** P4_FALL: flung back through the air onto his back, a skid, the dust; panting; up on an elbow; the scar. */
    private void tickP4Fall(ServerLevel level) {
        int t = stateTicks;
        int land = beat("p4.land", 14);
        Vec3 from = p4Rise != null ? p4Rise : position(), to = p4Land != null ? p4Land : position(),
                skid = p4Skid != null ? p4Skid : to;
        setDeltaMovement(Vec3.ZERO);
        if (t <= land) {
            double k = t / (double) land;
            Vec3 at = from.lerp(to, 1 - (1 - k) * (1 - k)).add(0, Math.sin(k * Math.PI) * 3.2, 0);
            setPos(at.x, at.y, at.z);
        } else if (t <= land + 10) {
            double k = (t - land) / 10.0;
            Vec3 at = to.lerp(skid, 1 - (1 - k) * (1 - k));
            setPos(at.x, at.y, at.z);
            if (t % 2 == 0) Fx.dust(level, position(), 0.9, 3);
        } else { noPhysics = false; }
        if (t == land) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.4F);
            Fx.land(level, position(), 4F);
            Fx.dust(level, position(), 2.5, 24);
            PaponNetwork.shake(level, position(), 64, 0.8F, 18);
        }
        // he pants: a breath in and out, hard and ragged on his back, then slower
        if (t > land + 4 && (t - land) % (t < 100 ? 11 : 16) == 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), com.groid.papon.registry.ModSounds.PANT.get(), SoundSource.HOSTILE, 1.2F, 0.9F + random.nextFloat() * 0.1F);
        }
        int look = beat("p4.look", 58);
        if (t >= look && t % 9 == 0) {
            // the scar weeps
            Vec3 chest = position().add(0, getBbHeight() * 0.32, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(getBbWidth() * 0.5));
            Fx.fall(level, chest, 1, 0.3, 0.1F, 0x7A0A10);
        }
        if (t == look) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.HONEY_DRINK, SoundSource.HOSTILE, 1.0F, 0.5F);
        if (t == beat("p4.scar", 76)) sayPlain("p4_scar", 60);
        if (t >= beat("p4.fall_end", BossState.P4_FALL.duration)) setBossState(BossState.P4_RISEUP);
    }

    /** P4_RISEUP: up in two goes, a hand on his knee; he coughs blood: "Do you want to know... how I got this scar?" */
    private void tickP4RiseUp(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        LivingEntity target = getTarget();
        if (target != null && t > 20) turnToward(target.position(), 4F);
        if (t == beat("p4.knee", 16)) level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.2F, 1.3F);
        int cough = beat("p4.cough", 36);
        if (t == cough || t == cough + 7) {
            Vec3 mouth = position().add(0, getBbHeight() * 0.42, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(getBbWidth() * 0.55));
            Fx.fall(level, mouth, 6, 0.2, 0.12F, 0x8A0A12);
            Fx.splat(level, new Vec3(mouth.x, groundY(mouth), mouth.z).add(Vec3.directionFromRotation(0, yBodyRot).scale(0.8)), 0.35F, 0x6A0A10, 160);
            level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        if ((t < cough - 4 || t > cough + 12) && t % 15 == 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), com.groid.papon.registry.ModSounds.PANT.get(), SoundSource.HOSTILE, 1.1F, 0.9F + random.nextFloat() * 0.08F);
        }
        if (t == beat("p4.howgot", 52)) sayPlain("p4_howgot", 60);
        if (t >= beat("p4.riseup_end", BossState.P4_RISEUP.duration)) setBossState(BossState.P4_IGNIS);
    }

    /** P4_IGNIS: Ignis's head from behind his back, held up for you to see, thrown down at his feet; then at you. */
    private void tickP4Ignis(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 3F);
        int out = beat("p4.head_out", 20), throwAt = beat("p4.throw", 109), thud = beat("p4.thud", 122);
        if (t == 4) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.HOSTILE, 1.2F, 0.6F);
        if (t == out) {
            entityData.set(DATA_P4PROP, 4);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        if (t == beat("p4.ignis", 34)) sayPlain("p4_ignis", 80);
        if (t == throwAt) {
            entityData.set(DATA_P4PROP, 0);
            Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
            Vec3 hand = position().add(0, getBbHeight() * 0.7, 0).add(f.scale(getBbWidth() * 0.6));
            Vec3 lands = onSand(position().add(f.scale(4.5)).add(new Vec3(-f.z, 0, f.x).scale(1.2)));
            BossProp head = BossProp.create(level, BossProp.Kind.IGNIS_HEAD, this, hand).throwDown(lands, thud - throwAt).life(20 * 60 * 20);
            head.setYRot(yBodyRot + 160);
            level.addFreshEntity(head);
            ignisHead = head.getUUID();
            level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.6F);
        }
        if (t == thud) {
            Entity h = ignisHead != null ? level.getEntity(ignisHead) : null;
            Vec3 at = h != null ? h.position() : position();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.45F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 6, 0.4, 0.05, 0.4, 0.02);
            PaponNetwork.shake(level, at, 40, 0.3F, 8);
            crowdReact(level, CrowdMood.GASP, 30);
        }
        if (t == beat("p4.met", 138)) sayPlain("p4_met", 60);
        if (t % 24 == 12 && speechTicks <= 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), com.groid.papon.registry.ModSounds.PANT.get(), SoundSource.HOSTILE, 0.8F, 0.95F);
        }
        if (t >= beat("p4.ignis_end", BossState.P4_IGNIS.duration)) setBossState(BossState.P4_PROPHECY);
    }

    /** Where his throne is (his box over the band stand), or a point high over the stage without a coliseum. */
    public Vec3 throneSpot() {
        if (inColiseum() && arena != null) return arena.throne();
        if (level().isClientSide && inColiseum()) {
            return fightCenter().add(0, Coliseum.rowHeight(3) + 2, -Math.round(fieldB() + 2 + 3.5));
        }
        return fightCenter().add(0, 12, -30);
    }

    /**
     * P4_PROPHECY: a slow walk toward the middle, his hand over the scar: "Ten thousand years..."; the fog rolls in over the
     * sand; he stops and looks up at his throne in his box: "You see that throne up there? I never sat on it to rule. I sat on
     * it to keep it warm."; round to you: the stone the oldest potatoes carved, the warrior it promised.
     */
    private void tickP4Prophecy(ServerLevel level) {
        int t = stateTicks;
        int years = beat("p4.years", 10), fog = beat("p4.fog", 40), look = beat("p4.throne_look", 119), carved = beat("p4.carved", 262);
        Vec3 c = onSand(p4Center());
        if (t < look && horizontalDist(c) > 3) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(c.x, c.y, c.z, 0.26);
        } else {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        LivingEntity target = getTarget();
        if (t >= look && t < carved - 10) turnToward(throneSpot(), 3F);
        else if (t >= carved - 10 && target != null) turnToward(target.position(), 3F);
        if (t == years) sayPlain("p4_years", 100);
        if (t >= fog) entityData.set(DATA_FOG, Math.min(1F, (t - fog) / 110F));
        if (t == fog) level.playSound(null, getX(), getY() + 4, getZ(), SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
        if (t == beat("p4.throne", 133)) sayPlain("p4_throne", 100);
        if (t == carved) sayPlain("p4_carved", 100);
        if (t == beat("p4.warrior", 368)) sayPlain("p4_warrior", 170);
        if (t % 30 == 15 && speechTicks <= 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), com.groid.papon.registry.ModSounds.PANT.get(), SoundSource.HOSTILE, 0.6F, 1.0F);
        }
        if (t >= beat("p4.prophecy_end", BossState.P4_PROPHECY.duration)) setBossState(BossState.P4_THRONE);
    }

    /**
     * P4_THRONE: Moctezuma. Head bowed, remembering: the iron men across the sea, his old friend who saw a god in their captain
     * and gave him everything; up at you, hard: "I will not make his mistake... you will have to take it from me." The clouds
     * part on the moon: "After all this time... the prophecy might finally be fulfilled."; the smile up at it: "la luna knows."
     */
    private void tickP4Throne(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        LivingEntity target = getTarget();
        int mistake = beat("p4.mistake", 395), moon = beat("p4.moon", 573);
        if (t < mistake - 10) {
            if (target != null) turnToward(target.position().add(Vec3.directionFromRotation(0, yBodyRot + 70).scale(6)), 1.5F);
        } else if (t < moon && target != null) turnToward(target.position(), 5F);
        if (t == beat("p4.moctezuma", 18)) sayPlain("p4_moctezuma", 220);
        if (t == beat("p4.gates", 249)) sayPlain("p4_gates", 120);
        if (t == mistake) sayPlain("p4_mistake", 160);
        if (t == mistake + 2) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (t == moon) {
            entityData.set(DATA_MOON, 1);
            entityData.set(DATA_NIGHT, 0.92F);
            entityData.set(DATA_SPOT, false);
            level.playSound(null, getX(), getY() + 20, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
        if (t >= moon) face(moonPos());
        if (t == beat("p4.prophecy", 617)) sayPlain("p4_prophecy", 90);
        if (t == beat("p4.lunaknows", 712)) sayPlain("p4_lunaknows", 60);
        if (t >= beat("p4.throne_end", BossState.P4_THRONE.duration)) setBossState(BossState.P4_BRETHREN);
    }

    /** P4_BRETHREN: arms open to the stands of his own; the moon turns to the dead's on "...STRENGTH!" */
    private void tickP4Brethren(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        // to the stands behind you, then up to the moon
        int turn = beat("p4.turn", 194);
        Vec3 c = p4Center();
        if (t < beat("p4.grant", 114)) {
            LivingEntity target = getTarget();
            Vec3 stands = target != null ? c.add(target.position().subtract(c).multiply(1, 0, 1).normalize().scale(60)) : c.add(0, 0, 60);
            turnToward(stands, 3F);
        } else { turnToward(moonPos(), 4F); }
        if (t == beat("p4.brethren", 14)) {
            sayPlain("p4_brethren", 100);
            crowdMood(level, CrowdMood.HUSH);
        }
        if (t == beat("p4.grant", 114)) sayPlain("p4_grant", 90);
        if (t == turn) {
            entityData.set(DATA_MOON, 2);
            cine(level, ModSounds.CINE_MOON.get(), position().add(0, 12, 0), 1.0F);
            level.playSound(null, getX(), getY() + 20, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5.0F, 0.5F);
            level.playSound(null, getX(), getY() + 20, getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.6F);
            PaponNetwork.shake(level, position(), 120, 0.7F, 40);
            crowdReact(level, CrowdMood.GASP, 40);
        }
        if (t >= beat("p4.brethren_end", BossState.P4_BRETHREN.duration)) setBossState(BossState.P4_SOULS);
    }

    /** P4_SOULS: the stands' souls stream into him, the band's one by one; he rises off the sand glowing green. */
    public static final int SOULS_STANDS = 10, SOULS_STANDS_END = 110;

    /** The tick in P4_SOULS band member {@code i}'s soul leaves him (stage order). */
    public static int soulAt(int i) {
        return 124 + i * 7;
    }

    private void tickP4Souls(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        Vec3 c = p4Center();
        Vec3 ground = p4Land != null ? new Vec3(getX(), inColiseum() && arena != null ? arena.floorY() : getY(), getZ()) : position();
        if (t == 1) p4Rise = ground;
        double lift = Math.min(3.0, t / 200.0 * 3.0);
        hold((p4Rise != null ? p4Rise : ground).add(0, lift, 0));
        entityData.set(DATA_GLOW, Math.min(1F, t / 230F));
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 2F);
        ColiseumCrowdEntity crowd = crowd(level);
        // (the user: "no sound effects, it's all silent") the choir of the souls rising out of the stands, each one
        // whooshing out of its seat, and striking him as it arrives
        if (t == SOULS_STANDS) cine(level, ModSounds.CINE_SOULS.get(), position().add(0, 2, 0), 1.0F);
        if (t >= SOULS_STANDS && t <= SOULS_STANDS_END && t % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2, ra = arena != null ? arena.a * 1.05 : 30, rb = arena != null ? arena.b * 1.05 : 22;
            Vec3 c0 = p4Center();
            cine(level, ModSounds.CINE_SOUL.get(), c0.add(Math.cos(a) * ra, 6 + random.nextDouble() * 6, Math.sin(a) * rb), 0.8F + random.nextFloat() * 0.5F);
        }
        if (t >= SOULS_STANDS + 16 && t <= SOULS_STANDS_END + 24 && t % 7 == 0) {
            cine(level, ModSounds.CINE_ABSORB.get(), position().add(0, 1.5, 0), 0.75F + 0.5F * (t - SOULS_STANDS) / (SOULS_STANDS_END - SOULS_STANDS + 24F));
        }
        if (crowd != null && t >= SOULS_STANDS && t <= SOULS_STANDS_END) {
            crowd.setDead((t - SOULS_STANDS) / (float) (SOULS_STANDS_END - SOULS_STANDS));
            if (t % 5 == 0) level.playSound(null, crowd.getX(), crowd.getY() + 10, crowd.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE,
                    4.0F, 0.5F + random.nextFloat() * 0.5F);
        }
        if (t % 8 == 0) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F + t * 0.004F);
        List<MariachiEntity> band = band(level);
        for (int i = 0; i < band.size(); i++) {
            MariachiEntity m = band.get(i);
            if (t == soulAt(i)) {
                cine(level, ModSounds.CINE_SOUL.get(), m.position().add(0, 1.5, 0), 1.1F + 0.04F * i);
                level.playSound(null, m.getX(), m.getY() + 1, m.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.8F);
                Fx.spark(level, m.position().add(0, 1, 0), 10, 0.5, 0.35F, 0x52FF84);
            }
            if (t == soulAt(i) + 14) cine(level, ModSounds.CINE_ABSORB.get(), position().add(0, 1.5, 0), 1.0F + 0.05F * i);
            if (t == soulAt(i) + 8) {
                m.setDead(true);
                level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.8F);
            }
        }
        if (t == beat("p4.feel", 214)) {
            sayPlain("p4_feel", 100);
            PaponNetwork.shake(level, position(), 96, 0.5F, 60);
        }
        if (t >= beat("p4.souls_end", BossState.P4_SOULS.duration)) setBossState(BossState.P4_HALFBAKED);
    }

    /**
     * P4_HALFBAKED: blazing green, the line ("You know... it's not right to go into a fight half baked."); the burst of green
     * fire; the flash, and behind it the coliseum turns into the dead's (twice the size, Coliseum.dead); the fog gone with it;
     * down on the floor of the new arena: Papon, the Final Guardian.
     */
    private void tickP4HalfBaked(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        int line = beat("p4.halfbaked", 12), burst = beat("p4.burst", 106), flash = beat("p4.flash", 136), clear = beat("p4.clear", 162);
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 4F);
        if (t < flash) hold(p4Rise != null ? p4Rise.add(0, 3, 0) : position());
        if (t == line) sayPlain("p4_halfbaked", 90);
        if (t == Math.max(line + 1, flash - 64)) cine(level, ModSounds.CINE_SWELL.get(), position().add(0, 2, 0), 1.0F);
        if (t >= burst && t < flash) {
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 at = position().add(Math.cos(a) * getBbWidth(), random.nextDouble() * getBbHeight(), Math.sin(a) * getBbWidth());
                Fx.spark(level, at, 2, 0.3, 0.5F, 0x52FF84);
            }
            if (t % 5 == 0) level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.5F + (t - burst) * 0.02F);
            if (t == burst) {
                sayPlain("p4_burst", 60);
                level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 1.0F);
                PaponNetwork.shake(level, position(), 96, 0.6F, flash - burst);
            }
        }
        if (t == flash) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5.0F, 0.7F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4.0F, 0.5F);
            PaponNetwork.shake(level, position(), 120, 1.0F, 30);
            entityData.set(DATA_FORM, 4);
            refreshDimensions();
            entityData.set(DATA_PHASE4, true);
            p4StageReached = 1;
            entityData.set(DATA_P4STAGE, 1);
            bossEvent.setName(Component.translatable(BAR_KEY + ".phase4"));
            entityData.set(DATA_FOG, 0F);
            if (arena != null && !arena.dead) {
                p4Old = arena;
                arena = p4Old.dead(level);
                arena.swapFrom(p4Old);
                floorChecked = true;
            }
        }
        if (t > flash && t < clear && arena != null && !arena.built()) arena.buildStep(level, 24000);
        if (t == flash + 8) {
            if (arena != null) {
                while (!arena.built()) arena.buildStep(level, 24000);
                p4Old = null;
                enterDeadArena(level);
            }
            Vec3 c = p4Center();
            setFlying(false);
            noPhysics = false;
            Coliseum.send(level, this, c);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == clear) {
            Fx.shock(level, position(), 14F, 0x52FF84);
            Fx.flash(level, position().add(0, getBbHeight() * 0.5, 0), 4F);
            Fx.chunks(level, position().add(0, getBbHeight() * 0.6, 0), 18, 0.35F, 0xEDE4CE, 0.6);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0F, 0.6F);
            crowdMood(level, CrowdMood.FIESTA);
            crowdRoar(level, 60);
            salvo(level, 8);
        }
        if (t == beat("p4.guardian", 222)) sayPlain("p4_guardian", 60);
        if (t == beat("p4.title", 170)) title(level, "title.papon.guardian", 70);
        if (t >= beat("p4.halfbaked_end", BossState.P4_HALFBAKED.duration)) {
            entityData.set(DATA_GLOW, 0.6F);
            setBossState(BossState.P4_BOW);
        }
    }

    /** P4_BOW: the bow out of the light; an arrow of it nocked, drawn and loosed at the moon; black smoke tears the sky open. */
    public static final int BOW_FORM = 8, BOW_LOOSE = 66, BOW_HIT = 76, BOW_SMOKE = 86;

    private void tickP4Bow(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        turnToward(moonPos(), t < 20 ? 6F : 10F);
        if (t == BOW_FORM) {
            entityData.set(DATA_P4PROP, 3);
            Vec3 hand = position().add(0, getBbHeight() * 0.6, 0);
            Fx.flash(level, hand, 2F);
            Fx.spark(level, hand, 16, 0.6, 0.4F, 0x52FF84);
            level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (t == 30) level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.HOSTILE, 2.0F, 0.5F);
        if (t == 40) level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.HOSTILE, 2.0F, 0.4F);
        if (t == BOW_LOOSE) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 4.0F, 0.4F);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.HOSTILE, 3.0F, 0.6F);
            PaponNetwork.shake(level, position(), 64, 0.4F, 10);
        }
        if (t == BOW_HIT) {
            level.playSound(null, getX(), getY() + 40, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 6.0F, 0.4F);
            PaponNetwork.shake(level, position(), 128, 0.7F, 30);
            crowdReact(level, CrowdMood.GASP, 40);
        }
        if (t == BOW_SMOKE) {
            spawnSerpents(level, false);
            level.playSound(null, getX(), getY() + 60, getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6.0F, 0.4F);
        }
        if (t == beat("p4.bowline", 112)) sayPlain("p4_bow", 80);
        if (t >= beat("p4.bow_end", BossState.P4_BOW.duration)) setBossState(BossState.P4_SERPENTS);
    }

    /** Quetzalcoatl and Kukulcan out of their smoke over the moon's side of the sky, to circle the arena (or straight onto it). */
    private void spawnSerpents(ServerLevel level, boolean circling) {
        for (UUID id : serpentIds) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        serpentIds.clear();
        Vec3 c = p4Center();
        boolean col = inColiseum() && arena != null;
        double big = col ? Math.max(arena.a, arena.b) : 30;
        Vec3 toMoon = moonPos().subtract(c).multiply(1, 0, 1).normalize();
        double moonYaw = Math.atan2(toMoon.z, toMoon.x);
        for (int k = 0; k < 2; k++) {
            double a = moonYaw + (k == 0 ? -0.6 : 0.6);
            Vec3 portal = c.add(Math.cos(a) * (big + 95), big * 0.6 + 62, Math.sin(a) * (big + 95));
            double radius = big + (col ? 62 : 40) + k * 16, height = big * 0.6 + (col ? 34 : 26) + k * 14;
            SkySerpentEntity s = SkySerpentEntity.create(level, k, c, portal, radius, height, k == 0 ? 1 : -1);
            if (circling) s.circling();
            level.addFreshEntity(s);
            serpentIds.add(s.getUUID());
        }
    }

    @Nullable
    private SkySerpentEntity serpent(ServerLevel level, int kind) {
        for (UUID id : serpentIds) { if (level.getEntity(id) instanceof SkySerpentEntity s && s.kind() == kind) return s; }
        return null;
    }

    /** P4_SERPENTS: he watches them come, colossal, out of the smoke and down round the arena; their roars. */
    private void tickP4Serpents(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        SkySerpentEntity a = serpent(level, SkySerpentEntity.QUETZAL), b = serpent(level, SkySerpentEntity.KUKULCAN);
        if (a != null) turnToward(a.path().at(a.head(0)), 3F);
        if (t == 8 && a != null) a.roar();
        if (t == 40 && b != null) b.roar();
        if (t == 30) crowdReact(level, CrowdMood.GASP, 60);
        if (t == 150 && a != null) a.roar();
        if (t == 200) {
            crowdRoar(level, 50);
            salvo(level, 6);
        }
        if (t >= BossState.P4_SERPENTS.duration) setBossState(BossState.P4_TALKING);
    }

    /** P4_TALKING: "Now we're TALKING!", a fist pumped at the sky. */
    private void tickP4Talking(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        LivingEntity target = getTarget();
        if (target != null) turnToward(target.position(), 8F);
        if (t == beat("p4.talking", 6)) {
            sayPlain("p4_talking", 40);
            crowdRoar(level, 40);
        }
        if (t >= beat("p4.talking_end", BossState.P4_TALKING.duration)) {
            entityData.set(DATA_P4PROP, 0);
            setBossState(inColiseum() && singer(level) != null ? BossState.P4_TOBAND : BossState.P4_GOODTIME);
        }
    }

    /** Where he stands to talk to the band: on the sand just off the stage, in front of the singer. */
    private Vec3 bandSpot() { return wakeSpot(); }

    /** P4_TOBAND: a walk over to the band, quiet now. */
    private void tickP4ToBand(ServerLevel level) {
        Vec3 to = bandSpot();
        double d = horizontalDist(to);
        MariachiEntity singer = singer(level);
        if (d < 0.8 || stateTicks > 240) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            setBossState(BossState.P4_HONOR);
            return;
        }
        getNavigation().stop();
        getMoveControl().setWantedPosition(to.x, to.y, to.z, d > 3 ? 0.85 : 0.4 + d * 0.15);
        if (singer != null && d < 6) turnToward(singer.position(), 6F);
        if (stateTicks % 14 == 7) level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    /** P4_HONOR: softly, to the band, a hand on his heart: one last song. The singer nods; they strike up Persona Ideal. */
    private void tickP4Honor(ServerLevel level) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        MariachiEntity singer = singer(level);
        if (singer != null) face(singer.position());
        if (t == beat("p4.honor", 10)) {
            sayPlain("p4_honor", 110);
            crowdMood(level, CrowdMood.HUSH);
        }
        if (t == beat("p4.nod", 112) && singer != null) singer.blurt("p4_listo", 40);
        if (t == beat("p4.song", 128)) {
            songId = Song.PERSONA_IDEAL;
            songStart = (int) (level.getGameTime() & 0x7FFFFFFF);
            for (MariachiEntity m : band(level)) m.play(songStart, songId);
            crowdMood(level, CrowdMood.CHEER);
        }
        if (t >= beat("p4.honor_end", BossState.P4_HONOR.duration)) setBossState(BossState.P4_MUCHACHAS);
    }

    /** Where the muchachas dance: on the sand in front of the stage, two each side of it. */
    private List<Vec3> muchachaSpots(ServerLevel level) {
        List<Vec3> out = new ArrayList<>();
        List<MariachiEntity> band = band(level);
        Vec3 c = p4Center();
        Vec3 stage = band.isEmpty() ? c.add(0, 0, -20) : band.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / band.size());
        Vec3 in = c.subtract(stage).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-in.z, 0, in.x);
        for (double s : new double[]{-12, -6, 6, 12}) out.add(onSand(stage.add(in.scale(17 + Math.abs(s) * 0.2)).add(side.scale(s))));
        return out;
    }

    /** P4_MUCHACHAS: "Hey! Where are the muchachas?"; a clap; up they come in green fire; he twirls one, dips her, dances. */
    private void tickP4Muchachas(ServerLevel level) {
        int t = stateTicks;
        int clap = beat("p4.clap", 50), rise = beat("p4.rise", 60), twirl = beat("p4.twirl", 112);
        if (t == beat("p4.muchachas", 4)) sayPlain("p4_muchachas", 50);
        if (t < rise) turnToward(p4Center(), 8F);          // round from the band to the sand, looking for them
        if (t == clap || t == clap + 6) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.6F, 1.6F);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.HOSTILE, 2.0F, 0.8F);
        }
        if (t == rise) {
            for (UUID id : muchachas) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            muchachas.clear();
            List<Vec3> spots = muchachaSpots(level);
            Vec3 c = p4Center();
            for (int i = 0; i < spots.size(); i++) {
                Vec3 at = spots.get(i);
                SalsaDancerEntity d = SalsaDancerEntity.create(level, at, c, i);
                level.addFreshEntity(d);
                muchachas.add(d.getUUID());
                Fx.shock(level, at, 2.5F, 0x52FF84);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.5, at.z, 30, 0.4, 0.8, 0.4, 0.03);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 2.0F, 1.2F);
            crowdRoar(level, 30);
        }
        SalsaDancerEntity partner = partner(level);
        if (t > rise && t < twirl && partner != null) {
            // over to her, to stand an arm's length in front of her
            Vec3 to = partner.position().add(partner.position().subtract(position()).multiply(1, 0, 1).normalize().scale(-2.4));
            double d = horizontalDist(to);
            getNavigation().stop();
            if (d > 0.4) getMoveControl().setWantedPosition(to.x, to.y, to.z, 0.7);
            turnToward(partner.position(), 10F);
        } else {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (t == twirl && partner != null) {
            face(partner.position());
            partner.twirl(this);
        }
        if (t > twirl && partner != null) face(partner.position());
        if (t == beat("p4.twirl_line", 126)) sayPlain("p4_twirl", 50);
        if (t == beat("p4.dip", 170)) crowdReact(level, CrowdMood.CHEER, 40);
        if (t >= beat("p4.muchachas_end", BossState.P4_MUCHACHAS.duration)) {
            if (partner != null) partner.dance();
            setBossState(BossState.P4_GOODTIME);
        }
    }

    @Nullable
    private SalsaDancerEntity partner(ServerLevel level) {
        SalsaDancerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (UUID id : muchachas) {
            if (level.getEntity(id) instanceof SalsaDancerEntity d) {
                double dist = d.distanceToSqr(this);
                if (dist < bd) {
                    bd = dist;
                    best = d;
                }
            }
        }
        return best;
    }

    /** When the song's first sung word comes (game tick), from the band's downbeat and the brass intro's passes. */
    private long fightAt(ServerLevel level) {
        Song song = Song.get(Song.PERSONA_IDEAL);
        if (song == null || songStart == 0 || songId != Song.PERSONA_IDEAL) return -1;
        long now = level.getGameTime();
        long base = now & ~0x7FFFFFFFL;
        long start = base | (songStart & 0x7FFFFFFFL);
        if (start > now + (1L << 30)) start -= 1L << 31;
        return start + Math.round(song.intro().length() * 20 * VoiceLines.beat("p4.passes", 2));
    }

    /** P4_GOODTIME: round to you, close, the smirk: "COME ON!!! SHOW ME A GOOD TIME!!!" The bowl erupts; the first word: go. */
    private void tickP4GoodTime(ServerLevel level) {
        int t = stateTicks;
        LivingEntity target = getTarget();
        int line = beat("p4.goodtime", 40), erupt = beat("p4.erupt", 103);
        if (target != null) {
            double d = horizontalDist(target.position());
            getNavigation().stop();
            if (t < line && d > 6) getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 0.45);
            else setDeltaMovement(0, getDeltaMovement().y, 0);
            turnToward(target.position(), 10F);
        }
        if (t == beat("p4.dance", 24)) sayPlain("p4_dance", 90);
        if (t == line) sayPlain("p4_goodtime", 70);
        if (t == erupt) {
            crowdMood(level, CrowdMood.FIESTA);
            crowdRoar(level, 80);
            salvo(level, 12);
            Fx.shock(level, position(), 12F, 0x52FF84);
            PaponNetwork.shake(level, position(), 80, 0.6F, 20);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.7F);
        }
        long at = fightAt(level);
        boolean go = at > 0 ? level.getGameTime() >= at && t > erupt : t >= erupt + 40;
        if (go || t > 600) {
            think = 4;
            vanCd = Math.max(vanCd, 99999);
            crowdCd = 10;
            entityData.set(DATA_P4PROP, 1);
            setBossState(BossState.IDLE);
        }
    }

    /** In the fight: Quetzalcoatl and Kukulcan circling, the muchachas dancing (back after a reload, which does not keep them). */
    private void keepGuardian(ServerLevel level) {
        if (serpent(level, SkySerpentEntity.QUETZAL) == null || serpent(level, SkySerpentEntity.KUKULCAN) == null) spawnSerpents(level, true);
        boolean any = false;
        for (UUID id : muchachas) any |= level.getEntity(id) instanceof SalsaDancerEntity;
        if (!any && inColiseum() && singer(level) != null) {
            muchachas.clear();
            List<Vec3> spots = muchachaSpots(level);
            for (int i = 0; i < spots.size(); i++) {
                SalsaDancerEntity d = SalsaDancerEntity.create(level, spots.get(i), p4Center(), i);
                d.dance();
                level.addFreshEntity(d);
                muchachas.add(d.getUUID());
            }
        }
    }

    /** Quetzalcoatl, Kukulcan, the muchachas and Ignis's head: gone (his fall, a removal). */
    private void clearGuardian(ServerLevel level) {
        for (List<UUID> l : List.of(serpentIds, muchachas)) {
            for (UUID id : l) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            l.clear();
        }
        if (ignisHead != null && level.getEntity(ignisHead) instanceof BossProp p) p.discard();
        ignisHead = null;
        breaths.clear();
        fusionRings.clear();
        clearFusion(level);
        trails.clear();
        if (giantId != null && level.getEntity(giantId) instanceof SpiritGiantEntity g) g.discard();
        giantId = null;
        clearGiantBeams(level);
    }

    /** Everyone to their places in the dead's coliseum: the band on its stage, the crowd re-seated, the challengers, himself. */
    private void enterDeadArena(ServerLevel level) {
        if (arena == null) return;
        Vec3 c = arena.centerVec();
        for (MariachiEntity m : band(level)) {
            int i = m.getRole().ordinal();
            Vec3 at = arena.bandSpot(i);
            m.moveTo(at.x, at.y, at.z, arena.bandYaw(i), 0);
            m.setDead(true);
            m.home(at, arena.bandYaw(i), arena.lamps());
        }
        ColiseumCrowdEntity crowd = crowd(level);
        if (crowd != null) {
            crowd.setSize(arena.a, arena.b);
            crowd.setDead(1F);
        }
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : level.players()) { if (!p.isSpectator() && arena.contains(p.position())) in.add(p); }
        for (int i = 0; i < in.size(); i++) {
            ServerPlayer p = in.get(i);
            p.stopRiding();
            Vec3 to = arena.playerSpot(i, in.size());
            Coliseum.send(level, p, to);
            p.connection.teleport(to.x, to.y, to.z, 180F, -8F);
        }
        CaravanEntity van = van(level);
        if (van != null && arena.contains(van.position())) {
            Coliseum.send(level, van, arena.vanSpot());
            van.setFloor(arena.floorY());
        }
        // Ignis's head stays behind in the old coliseum (the user: gone when the new arena appears)
        if (ignisHead != null && level.getEntity(ignisHead) instanceof BossProp head) head.discard();
        ignisHead = null;
        home = c;
        p4Rise = c;
        syncField();
        entityData.set(DATA_NIGHT, 0.92F);
        PaponMod.LOG.info("[boss] into El Coliseo de los Muertos at {} ({} x {})", arena.center, arena.a, arena.b);
    }

    /** Holds him where he is put (the cutscene's hovering). */
    private void hold(Vec3 at) {
        setFlying(true);
        noPhysics = true;
        Vec3 d = at.subtract(position());
        setPos(getX() + d.x * 0.2, getY() + d.y * 0.2 + Math.sin(tickCount * 0.1) * 0.02, getZ() + d.z * 0.2);
        setDeltaMovement(Vec3.ZERO);
    }

    // ---- his moves

    /** What El Rey de los Muertos does next: the hammer up close, the rest from anywhere; never the god's moves. */
    @Nullable
    private BossState choosePhaseFour(ServerLevel level, LivingEntity target, double d) {
        List<BossState> o = new ArrayList<>();
        boolean col = inColiseum() && arena != null && arena.dead;
        if (dMartilloCd <= 0) o.add(BossState.P4_MARTILLO);
        if (dLunaCd <= 0) o.add(BossState.P4_LUNA);
        if (dPetalosCd <= 0) o.add(BossState.P4_PETALOS);
        if (dPapelCd <= 0) o.add(BossState.P4_PAPEL);
        if (dDanzaCd <= 0 && d < 40) o.add(BossState.P4_DANZA);
        if (dGrietaCd <= 0 && target.onGround()) o.add(BossState.P4_GRIETA);
        if (col && dGiganteCd <= 0) o.add(BossState.P4_GIGANTE);
        if (col && dVelasCd <= 0 && !arena.cracks().isEmpty()) o.add(BossState.P4_VELAS);
        if (dSerpientesCd <= 0 && serpent(level, SkySerpentEntity.QUETZAL) != null && serpent(level, SkySerpentEntity.KUKULCAN) != null
                && serpentDives.isEmpty()) o.add(BossState.P4_SERPIENTES);
        if (dFlechasCd <= 0) o.add(BossState.P4_FLECHAS);
        // (2026-10-04) the grand moves: most of what he does in an arena this size
        boolean serpents = serpent(level, SkySerpentEntity.QUETZAL) != null && serpent(level, SkySerpentEntity.KUKULCAN) != null && serpentDives.isEmpty();
        if (col && dOuroCd <= 0 && serpents) o.add(BossState.P4_OUROBOROS);
        if (dPistaCd <= 0) o.add(BossState.P4_PISTA);
        if (col && dCaminoCd <= 0) o.add(BossState.P4_CEMPASUCHIL);
        if (dCastilloCd <= 0) o.add(BossState.P4_CASTILLO);
        if (dSalsaCd <= 0 && d < 16) o.add(BossState.P4_SALSA);
        if (dTangoCd <= 0 && target instanceof Player && !target.isPassenger() && d < 30) o.add(BossState.P4_TANGO);
        // (2026-10-05) the serpents in the fight: their green fire, and the two of them fused into one
        boolean free = serpents && breaths.isEmpty() && !serpent(level, SkySerpentEntity.QUETZAL).busy() && !serpent(level, SkySerpentEntity.KUKULCAN).busy();
        if (dAlientoCd <= 0 && free) o.add(BossState.P4_ALIENTO);
        if (col && dFusionCd <= 0 && free) o.add(BossState.P4_FUSION);
        // (2026-10-05) the end of the world: El Juicio Final again now and then
        if (col && p4StageReached >= 3 && dJuicioCd <= 0 && serpents && !serpent(level, SkySerpentEntity.QUETZAL).busy()
                && !serpent(level, SkySerpentEntity.KUKULCAN).busy()) o.add(BossState.P4_JUICIO);
        return pickWeighted(o);
    }

    private static float muertosWeight(BossState s) {
        return switch (s) {
            case P4_JUICIO -> 4.0F;
            case P4_FUSION -> 3.4F;
            case P4_OUROBOROS, P4_GIGANTE, P4_CASTILLO -> 3.0F;
            case P4_ALIENTO -> 2.6F;
            case P4_PISTA, P4_CEMPASUCHIL -> 2.8F;
            case P4_GRIETA, P4_SERPIENTES -> 2.4F;
            case P4_VELAS, P4_PETALOS -> 2.0F;
            case P4_TANGO -> 1.8F;
            case P4_PAPEL, P4_LUNA -> 1.6F;
            case P4_DANZA, P4_SALSA -> 1.3F;
            case P4_FLECHAS -> 1.1F;
            default -> 0.8F;
        };
    }

    private void startMuertos(BossState pick, LivingEntity target) {
        switch (pick) {
            case P4_MARTILLO -> dMartilloCd = 70;
            case P4_LUNA -> dLunaCd = 330;
            case P4_PETALOS -> dPetalosCd = 420;
            case P4_PAPEL -> dPapelCd = 400;
            case P4_DANZA -> dDanzaCd = 520;
            case P4_GRIETA -> dGrietaCd = 620;
            case P4_GIGANTE -> dGiganteCd = 700;
            case P4_VELAS -> dVelasCd = 520;
            case P4_SERPIENTES -> dSerpientesCd = 640;
            case P4_FLECHAS -> dFlechasCd = 380;
            case P4_OUROBOROS -> dOuroCd = 900;
            case P4_ALIENTO -> dAlientoCd = 560;
            case P4_FUSION -> dFusionCd = 1200;
            case P4_PISTA -> dPistaCd = 700;
            case P4_CEMPASUCHIL -> dCaminoCd = 860;
            case P4_CASTILLO -> dCastilloCd = 900;
            case P4_SALSA -> dSalsaCd = 260;
            case P4_TANGO -> {
                dTangoCd = 1500;
                tangoWho = target.getUUID();
            }
            case P4_JUICIO -> dJuicioCd = 1500;
            default -> {}
        }
        danzaTarget = target.getUUID();
        // (2026-10-05) from the storm on the serpents are part of his moves: one comes down across the floor in the middle of
        // whatever he is doing up close or on the floor (always at the end of the world)
        boolean assist = switch (pick) {
            case P4_MARTILLO, P4_SALSA, P4_FLECHAS, P4_LUNA, P4_PETALOS, P4_PISTA, P4_PAPEL, P4_VELAS, P4_DANZA, P4_GRIETA, P4_CASTILLO -> true;
            default -> false;
        };
        if (assist && p4StageReached >= 2 && (p4StageReached >= 3 || random.nextBoolean())) {
            serpentQueue = Math.max(serpentQueue, 1);
            serpentLayerCd = Math.min(serpentLayerCd, 18 + random.nextInt(20));
        }
    }

    private void tickMuertosCooldowns() {
        // (2026-10-05) faster by stage: half again in the storm, twice as fast at the end of the world
        int k = p4StageReached >= 3 ? 2 : p4StageReached == 2 && (tickCount & 1) == 0 ? 2 : 1;
        dMartilloCd -= k;
        dGiganteCd -= k;
        dGrietaCd -= k;
        dPetalosCd -= k;
        dPapelCd -= k;
        dVelasCd -= k;
        dDanzaCd -= k;
        dLunaCd -= k;
        dSerpientesCd -= k;
        dFlechasCd -= k;
        dOuroCd -= k;
        dPistaCd -= k;
        dCaminoCd -= k;
        dCastilloCd -= k;
        dSalsaCd -= k;
        dTangoCd--;
        dAlientoCd -= k;
        dFusionCd -= k;
        dJuicioCd--;
    }

    private int dAlientoCd = 240, dFusionCd = 700;
    private int dOuroCd = 200, dPistaCd = 60, dCaminoCd = 300, dCastilloCd = 420, dSalsaCd = 120, dTangoCd = 600;

    private int p4IdleTicks;

    /** A cutscene sound (ModSounds.CINE_*): heard across the whole of the dead's coliseum, never ducked by SoundMix. */
    private void cine(ServerLevel level, net.minecraft.sounds.SoundEvent sound, Vec3 at, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, 8.0F, pitch);
    }

    /**
     * Never stuck in the finale: a move of his (or WINDED, the tango's ends) running far past its length, a cutscene beat
     * long past its own, or IDLE with a target and no move for 15 s puts him back down on the sand and thinking again.
     */
    private void p4Watchdog(ServerLevel level) {
        BossState s = getBossState();
        if (!isPhaseFour() && !s.phaseFour()) return;
        boolean move = s.muertosMove() || s == BossState.WINDED;
        boolean stuck = move && stateTicks > Math.max(2 * s.duration, s.duration + 200)
                || s.phaseFour() && isPhaseFour() && stateTicks > s.duration + 600;      // a beat after his transformation
        if (s == BossState.IDLE && muertosFight() && getTarget() != null) p4IdleTicks++;
        else p4IdleTicks = 0;
        if (p4IdleTicks > 300) {
            p4IdleTicks = 0;
            stuck = true;
        }
        if (!stuck) return;
        PaponMod.LOG.warn("[papon] boss stuck in {} for {} ticks: back on the sand", s, stateTicks);
        noPhysics = false;
        setFlying(false);
        Vec3 at = onSand(position());
        moveTo(at.x, at.y, at.z, getYRot(), 0);
        setDeltaMovement(Vec3.ZERO);
        clearMuertos(level);
        think = 0;
        setBossState(BossState.IDLE);
    }

    /** Whatever a move of his left behind (the giant, the souls, the dancers, the banners, the rift), gone. */
    private void clearMuertos(ServerLevel level) {
        if (giantId != null && level.getEntity(giantId) instanceof SpiritGiantEntity g) g.discard();
        giantId = null;
        clearGiantBeams(level);
        giantLaserPending = false;
        trails.clear();
        juicioSafe.clear();
        for (UUID id : souls) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        souls.clear();
        for (UUID id : dancers) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        dancers.clear();
        for (UUID id : papelProps) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        papelProps.clear();
        papel.clear();
        closeRift(level, false);
        strikes.clear();
        breaths.clear();
        fusionRings.clear();
        fusionOn = 0;
        clearFusion(level);
        entityData.set(DATA_P4PROP, isPhaseFour() ? 1 : 0);
    }

    /** Leaving one of his moves (or cut short): its leftovers cleared. */
    private void leaveMuertos(ServerLevel level, BossState old, BossState s) {
        if (old == BossState.P4_GIGANTE || old == BossState.P4_GRIETA || old == BossState.P4_DANZA || old == BossState.P4_PAPEL) {
            // (from the storm on El Gigante stays in the arena: tickGiantLayer)
            if (p4StageReached < 2 && old == BossState.P4_GIGANTE) {
                if (giantId != null && level.getEntity(giantId) instanceof SpiritGiantEntity g) g.sink();
                giantId = null;
            }
            for (UUID id : souls) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            souls.clear();
            for (UUID id : dancers) { if (level.getEntity(id) instanceof PaponEchoEntity e) e.discard(); }
            dancers.clear();
            if (old == BossState.P4_GRIETA) closeRift(level, false);
            if (isPhaseFour()) entityData.set(DATA_P4PROP, 1);
        }
        if (old == BossState.P4_FLECHAS && isPhaseFour()) entityData.set(DATA_P4PROP, 1);
        if (old == BossState.P4_OUROBOROS) clearOuroboros(level);
        if (old == BossState.P4_FUSION && s != old) clearFusion(level);
        // back to the hammer between moves (the dances put it away)
        if ((s == BossState.IDLE || s == BossState.WINDED) && muertosFight() && entityData.get(DATA_P4PROP) == 0) entityData.set(DATA_P4PROP, 1);
        if (old == BossState.P4_GIGANTE && s != old) clearBeams(level);
        if (old == BossState.P4_JUICIO && s != old) {
            clearOuroboros(level);
            juicioSafe.clear();
        }
        if (old == BossState.P4_TANGO && tangoResult < 0 && tangoWho != null && level.getEntity(tangoWho) instanceof ServerPlayer sp) {
            PaponNetwork.qteEnd(sp, false);
        }
        if (old.muertosMove() || old.phaseFour()) noPhysics = false;
    }

    /** The share of your health each of his hits takes (true damage), harder than the god's. */
    private boolean deadHit(LivingEntity e, float frac) {
        if (!hittable(e)) return false;
        boolean hit = Mechanics.trueHit(e, frac, this, true);
        if (hit) {
            swingHit = true;
            if (e instanceof Player && crowdRoarCd <= 0 && level() instanceof ServerLevel level) {
                crowdRoarCd = 50;
                crowdReact(level, e.isAlive() ? CrowdMood.ROAR : CrowdMood.CHANT, 40);
                if (e.isAlive()) quip("p4_landed" + random.nextInt(5), 0.35F);
            }
        }
        return hit;
    }

    private List<LivingEntity> victims(ServerLevel level, double r) {
        Vec3 c = p4Center();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 30, r), this::hittable);
    }

    private double deadFloor() { return floorNear(position()); }

    // ---- El Martillo

    /**
     * El Martillo (3.2 s): a ring of blue where he is coming, and he is there (beside you, behind you); a forehand (0.5 s) and
     * a backhand (1.0 s) of the spirit hammer; up, and down onto the floor (2.0 s): three rings rolling out (jump them) and
     * the hammer's lightning onto where you are going.
     */
    private void tickP4Martillo(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        if (t == 1 && target != null) {
            // where he will be: beside you, on your blind side
            Vec3 side = target.getLookAngle().multiply(1, 0, 1).normalize();
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            Vec3 at = target.position().add(new Vec3(-side.z, 0, side.x).scale(random.nextBoolean() ? 2.6 : -2.6)).add(side.scale(-1.2));
            at = inSand(at, 2, 0, 0);
            blinkTo = new Vec3(at.x, floor, at.z);
            Fx.warnClosing(level, blinkTo, 1.4F, 0x40E2FF, 6);
            say("p4_martillo", 40);
        }
        if (t == 6 && blinkTo != null) {
            Fx.flash(level, position().add(0, 1.5, 0), 1.6F);
            setFlying(false);
            noPhysics = false;
            teleportTo(blinkTo.x, blinkTo.y, blinkTo.z);
            setDeltaMovement(Vec3.ZERO);
            Fx.flash(level, blinkTo.add(0, 1.5, 0), 2.0F);
            level.playSound(null, blinkTo.x, blinkTo.y, blinkTo.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.6F, 0.6F);
        }
        if (target != null && t > 6 && t < 36) turnToward(target.position(), 24F);
        if (t == 10 || t == 20) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            if (target != null && t == 20) lunge(target, 0.35, 0.3, 4.0);
            for (LivingEntity e : inCone(level, position().add(0, 1, 0), look, 5.5, 75)) {
                if (deadHit(e, 0.12F)) {
                    e.setDeltaMovement(look.scale(0.9).add(0, 0.45, 0));
                    e.hurtMarked = true;
                }
            }
            Fx.slash(level, position().add(0, 1.6, 0).add(look.scale(2.4)), getYRot() + (t == 10 ? 30 : -30), 3.0F, 0x40E2FF);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.8F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.4F, 0.6F);
        }
        if (t == 26 && target != null) {
            // the lightning's marks: where you are going, and round him
            for (int i = 0; i < 6; i++) {
                Vec3 at = i < 2 ? predict(target, 18 + i * 4) : position().add(Math.cos(i * 1.2 + tickCount) * (4 + i * 1.5), 0,
                        Math.sin(i * 1.2 + tickCount) * (4 + i * 1.5));
                at = inSand(at, 1.5, 0, 0);
                at = new Vec3(at.x, floor, at.z);
                strikes.add(new double[]{at.x, at.y, at.z, level.getGameTime() + 16, 2.2, 0.14});
                Fx.warn(level, at, 2.2F, 0x40E2FF, 16);
            }
        }
        if (t == 28) {
            setDeltaMovement(0, 0.9, 0);
            hasImpulse = true;
        }
        if (t == 40) {
            setDeltaMovement(0, -1.6, 0);
            hasImpulse = true;
        }
        if (t == 44) {
            Vec3 at = new Vec3(getX(), floor, getZ());
            Fx.shock(level, at, 8F, 0x40E2FF);
            Fx.flash(level, at.add(0, 0.5, 0), 2.4F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.4F);
            PaponNetwork.shake(level, at, 40, 0.8F, 18);
            for (LivingEntity e : around(level, 3.5)) {
                if (deadHit(e, 0.16F)) {
                    e.setDeltaMovement(e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2).add(0, 0.7, 0));
                    e.hurtMarked = true;
                }
            }
            ringAt = at;
        }
        if (ringAt != null && (t == 48 || t == 52 || t == 56)) {
            double r = (t - 44) / 4.0 * 3.2;
            Fx.shock(level, ringAt, (float) r, 0xFF9A1F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ringAt, ringAt).inflate(r + 1, 2, r + 1), this::hittable)) {
                double dd = Math.sqrt(e.distanceToSqr(ringAt.x, e.getY(), ringAt.z));
                if (Math.abs(dd - r) < 1.1 && e.onGround() && deadHit(e, 0.1F)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (t >= BossState.P4_MARTILLO.duration) {
            ringAt = null;
            setBossState(BossState.IDLE);
        }
    }

    /** The marked strikes landing (the hammer's lightning, the moon's beams): x, y, z, when, radius, share. */
    private void tickStrikes(ServerLevel level) {
        if (strikes.isEmpty()) return;
        long now = level.getGameTime();
        for (java.util.Iterator<double[]> it = strikes.iterator(); it.hasNext(); ) {
            double[] s = it.next();
            if (now < (long) s[3]) continue;
            it.remove();
            Vec3 at = new Vec3(s[0], s[1], s[2]);
            boolean moon = s.length > 6 && s[6] == 1;
            boolean arrow = s.length > 6 && s[6] == 2;
            if (s.length > 6 && s[6] == 3) {
                fiestaBurst(level, s);
                continue;
            }
            if (arrow) {
                // an arrow of the souls' light out of the sky, at a slant
                BeamEntity b = BeamEntity.create(level, at.add(-6, 40, 9), at, 0.7F, 0x52FF84).life(4);
                b.setMode(BeamEntity.FIRE);
                level.addFreshEntity(b);
                Fx.shock(level, at, (float) s[4] + 0.5F, 0x52FF84);
                Fx.spark(level, at.add(0, 0.3, 0), 8, 0.4, 0.3F, 0x52FF84);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.HOSTILE, 2.0F, 0.5F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.4F, 0.7F);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(s[4] + 0.5, 4, s[4] + 0.5), this::hittable)) {
                    if (e.distanceToSqr(at.x, e.getY(), at.z) > (s[4] + e.getBbWidth() / 2) * (s[4] + e.getBbWidth() / 2)) continue;
                    deadHit(e, (float) s[5]);
                }
                continue;
            }
            BeamEntity b = BeamEntity.create(level, at.add(0, moon ? 60 : 30, 0), at, moon ? 2.8F : 1.4F, moon ? 0x9FF4FF : 0x40E2FF).life(moon ? 8 : 5);
            b.setMode(BeamEntity.FIRE);
            level.addFreshEntity(b);
            Fx.shock(level, at, (float) s[4] + 1, 0x40E2FF);
            Fx.flash(level, at.add(0, 0.4, 0), 1.6F);
            Fx.scorch(level, at, (float) s[4], 120);
            level.playSound(null, at.x, at.y, at.z, moon ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE,
                    2.0F, moon ? 0.5F : 1.2F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(s[4] + 0.5, 4, s[4] + 0.5), this::hittable)) {
                if (e.distanceToSqr(at.x, e.getY(), at.z) > (s[4] + e.getBbWidth() / 2) * (s[4] + e.getBbWidth() / 2)) continue;
                if (deadHit(e, (float) s[5])) {
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.7, 0));
                    e.hurtMarked = true;
                    e.setSecondsOnFire(2);
                }
            }
        }
    }

    // ---- Las Serpientes, Flechas del Sol (2026-10-04)

    /** Quetzalcoatl's and Kukulcan's dives in flight: the serpent, the tick it ends, and who it has hit. */
    private final List<SerpentDive> serpentDives = new ArrayList<>();

    private record SerpentDive(UUID serpent, long until, java.util.Set<Integer> hit) {}

    /**
     * Las Serpientes (8 s): "My serpents... FEAST!", arms flung up; Quetzalcoatl peels off its orbit and comes down out of the sky
     * across the floor, straight through where you are (a long green warning line the width of it, from the moment it
     * turns); Kukulcan a second and a half later, crossing it. Its head or any of its body: 22% and flung aside. The dives go
     * on after he has moved on (tickSerpentDives).
     */
    private void tickP4Serpientes(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        flyTo(new Vec3(getX(), floor + 6, getZ()), 0.4);
        if (target != null) turnToward(target.position(), 6F);
        if (t == 2) say("p4_serpientes", 50);
        int st = Math.max(1, p4StageReached);
        if (target != null && (t == 4 || t == 34)) serpentDive(level, t == 4 ? 0 : 1, target, t == 34 ? Math.PI / 2 : 0, st >= 2);
        if (t == 36) {
            // (2026-10-05) the hunt goes on after he has moved on: two more dives in the storm, four at the end of the world, each
            // the moment a serpent is free again (tickSerpentLayer), every one leaving the floor burning
            serpentQueue = Math.max(serpentQueue, st >= 3 ? 4 : st == 2 ? 2 : 0);
            serpentLayerCd = 30;
        }
        if (t >= BossState.P4_SERPIENTES.duration) setBossState(BossState.IDLE);
    }

    /** The serpents' dives, every tick: anyone their low head or body touches. */
    private void tickSerpentDives(ServerLevel level) {
        if (serpentDives.isEmpty()) return;
        long now = level.getGameTime();
        double floor = deadFloor();
        serpentDives.removeIf(d -> now > d.until() || !(level.getEntity(d.serpent()) instanceof SkySerpentEntity));
        for (SerpentDive d : serpentDives) {
            SkySerpentEntity sp = (SkySerpentEntity) level.getEntity(d.serpent());
            if (sp == null) continue;
            List<Vec3> pts = sp.body(0);
            for (int i = 0; i < pts.size(); i++) {
                Vec3 p = pts.get(i);
                if (p.y > floor + 9) continue;
                if (i == 0 && now % 3 == 0) {
                    Fx.dust(level, new Vec3(p.x, floor, p.z), 3.0, 4);
                    if (now % 9 == 0) PaponNetwork.shake(level, p, 30, 0.35F, 6);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(5, 6, 5), this::hittable)) {
                    if (d.hit().contains(e.getId())) continue;
                    if (e.position().distanceTo(new Vec3(p.x, Math.max(e.getY(), p.y - 3), p.z)) > 4.6 + e.getBbWidth() / 2) continue;
                    if (deadHit(e, 0.22F)) {
                        d.hit().add(e.getId());
                        Vec3 away = e.position().subtract(p).multiply(1, 0, 1);
                        away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                        e.setDeltaMovement(away.scale(1.6).add(0, 0.9, 0));
                        e.hurtMarked = true;
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0F, 0.6F);
                    }
                }
            }
        }
    }

    /**
     * Flechas del Sol (6.5 s): the bow out; three times he looses arrows of the souls' light up into the sky, and they come
     * back down a second later onto green rings round you, ahead of you and anywhere on the floor (14% each); then three
     * straight at you, a ring closing on each.
     */
    private void tickP4Flechas(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        flyTo(new Vec3(getX(), floor + 5, getZ()), 0.5);
        if (t == 1) entityData.set(DATA_P4PROP, 3);
        if (t == 2) say("p4_flechas", 50);
        if (target != null) turnToward(target.position(), 8F);
        Vec3 c = p4Center();
        if (t == 14 || t == 38 || t == 62) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 3.0F, 0.5F);
            BeamEntity up = BeamEntity.create(level, position().add(0, getBbHeight() * 0.7, 0), position().add(0, 60, 0), 0.5F, 0x52FF84).life(6);
            up.setMode(BeamEntity.FIRE);
            level.addFreshEntity(up);
            double reach = inColiseum() && arena != null ? arena.a * 0.85 : 24;
            for (LivingEntity v : victims(level, reach + 20)) {
                if (!(v instanceof Player)) continue;
                for (int i = 0; i < 4; i++) {
                    Vec3 at = i == 0 ? v.position() : predict(v, 8 + i * 6).add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4);
                    at = inSand(at, 1.5, 0, 0);
                    arrowStrike(level, new Vec3(at.x, floor, at.z), 20, 0.14F);
                }
            }
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * reach;
                Vec3 at = inSand(c.add(Math.cos(a) * r, 0, Math.sin(a) * r * 0.72), 1.5, 0, 0);
                arrowStrike(level, new Vec3(at.x, floor, at.z), 20, 0.14F);
            }
        }
        if (t == 90 && target != null) {
            for (int i = 0; i < 3; i++) {
                Vec3 at = inSand(predict(target, 6 + i * 5), 1.5, 0, 0);
                arrowStrike(level, new Vec3(at.x, floor, at.z), 10 + i * 4, 0.14F);
            }
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (t >= BossState.P4_FLECHAS.duration) setBossState(BossState.IDLE);
    }

    private void arrowStrike(ServerLevel level, Vec3 at, int warn, float share) {
        strikes.add(new double[]{at.x, at.y, at.z, level.getGameTime() + warn, 1.8, share, 2});
        Fx.warnClosing(level, at, 1.8F, 0x52FF84, warn);
    }

    // ---- La Luna

    /** La Luna (6.5 s): his arms up to the moon; four volleys of blue rings on the floor (round you, ahead of you, anywhere), the moon's beams down into each. */
    private void tickP4Luna(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        flyTo(new Vec3(getX(), floor + 7, getZ()), 0.5);
        face(moonPos());
        if (t == 2) say("p4_luna", 50);
        if (t >= 12 && t <= 90 && (t - 12) % 26 == 0) {
            int volley = (t - 12) / 26;
            double reach = inColiseum() && arena != null ? arena.a * 0.85 : 24;
            for (LivingEntity v : victims(level, reach + 20)) {
                if (!(v instanceof Player)) continue;
                for (int i = 0; i < 3; i++) {
                    Vec3 at = i == 0 ? v.position() : predict(v, 10 + i * 6).add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
                    at = inSand(at, 1.5, 0, 0);
                    moonStrike(level, new Vec3(at.x, floor, at.z), 16 - volley, 0.18F);
                }
            }
            for (int i = 0; i < 6 + volley * 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * reach;
                Vec3 at = inSand(c.add(Math.cos(a) * r, 0, Math.sin(a) * r * 0.72), 1.5, 0, 0);
                moonStrike(level, new Vec3(at.x, floor, at.z), 16 - volley, 0.18F);
            }
            level.playSound(null, getX(), getY() + 10, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.6F + volley * 0.1F);
        }
        if (t >= BossState.P4_LUNA.duration) setBossState(BossState.IDLE);
    }

    private void moonStrike(ServerLevel level, Vec3 at, int warn, float share) {
        strikes.add(new double[]{at.x, at.y, at.z, level.getGameTime() + warn, 2.3, share, 1});
        Fx.warnClosing(level, at, 2.3F, 0x7FE8FF, warn);
    }

    // ---- El Gigante

    /**
     * El Gigante (about 16 s; reworked 2026-10-04, the user: "it needs to be actually in the arena... a giant threat"): he flies
     * to one end of the floor and plays a spirit guitar; at the end you are at, a giant skeleton of blue light tears up out
     * of the floor itself, waist deep in it, its skull 26 blocks over you. Its eyes find you, and two lasers as thick as a
     * doorway come out of them, dragged across the floor after you; then its hands: the left sweeps across the floor in a
     * great arc (the band it will sweep marked first, right through where you stand: get inside it or out past it), the right
     * sweeps back further out; whoever a hand catches is smacked across the floor into the wall (SpiritGiantEntity, fling).
     * Then the lasers again, swept right across the floor, and it sinks back into it.
     */
    private void tickP4Gigante(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (arena == null) {
            setBossState(BossState.IDLE);
            return;
        }
        Vec3 c = arena.centerVec();
        double floor = arena.floorY();
        SpiritGiantEntity g = giant(level);
        if (t == 1) {
            entityData.set(DATA_P4PROP, 2);
            say("p4_gigante", 60);
            // (2026-10-05) in the storm it is already here: it roars for him instead of rising
            if (g == null) g = summonGiant(level, target);
            else if (g.act() == SpiritGiantEntity.IDLE) g.act(SpiritGiantEntity.ROAR);
        }
        Vec3 perch = new Vec3(c.x - giantEnd * (arena.a - 10), floor + 9, c.z);
        flyTo(perch, 1.0);
        if (target != null) turnToward(target.position(), 10F);
        if (t % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.HOSTILE, 2.0F,
                0.6F + random.nextInt(6) * 0.15F);
        if (g == null) {
            if (t > 5) setBossState(BossState.IDLE);
            return;
        }
        // what it does is the giant's own (tickGiantHazards hits with it): here only when
        int st = p4StageReached;
        if (t == 56) giantLaser(g, 26, 62, GIANT_TRACK);
        if (t == 150) giantSwipe(g, target, SpiritGiantEntity.SWIPE_L);
        if (t == 200) giantSwipe(g, target, SpiritGiantEntity.SWIPE_R);
        if (t == 222) giantLaser(g, 26, 40, GIANT_SWEEP);
        if (t == 292) {
            if (st >= 2 && target != null) giantSlam(level, g, target, true);       // in the storm it stays: both hands down instead
            else g.sink();
        }
        if (t >= (st >= 2 ? 350 : 330)) setBossState(BossState.IDLE);
    }

    private boolean giantSweep;
    private int giantEnd = 1;
    private final java.util.Set<Integer> swipeHit = new java.util.HashSet<>();

    // ------------------------------------------------------------------ the Final Guardian's grand moves (2026-10-04)
    // (the user: "since the arena is bigger... more opportunity for grand dramatic ultimate attacks, which need to be a majority
    // of what he does; the serpents circling the entire arena; dancing salsa themes; a dance-off". The floor-wide patterns are
    // pure functions of a synced seed (boss/Fiesta), drawn by client/MuertosArena.)

    /** The arena as the clients see it: its half-axes and its floor. */
    public float fieldA() { return entityData.get(DATA_FIELD).x(); }

    public float fieldB() { return entityData.get(DATA_FIELD).y(); }

    public float fieldFloor() { return entityData.get(DATA_FIELD).z(); }

    /** The seed of the grand move he is doing (its patterns, its path, its sparks). */
    public int p4Seed() { return entityData.get(DATA_P4SEED); }

    private double fa() { return inColiseum() && arena != null ? arena.a : 30; }

    private double fb() { return inColiseum() && arena != null ? arena.b : 22; }

    private void syncField() {
        org.joml.Vector3f want = new org.joml.Vector3f((float) fa(), (float) fb(), (float) deadFloor());
        if (!want.equals(entityData.get(DATA_FIELD))) entityData.set(DATA_FIELD, want);
        Vec3 c = inColiseum() && arena != null ? arena.centerVec() : home != null ? home : null;
        org.joml.Vector3f cv = c != null ? c.toVector3f() : new org.joml.Vector3f();
        if (!cv.equals(entityData.get(DATA_CENTER))) entityData.set(DATA_CENTER, cv);
    }

    /** A strike of the fiesta's (a footprint of fire, a rocket, a sugar skull): x, y, z, when, radius, share, 3, colour. */
    private void fiestaStrike(ServerLevel level, Vec3 at, int warn, double r, float share, int colour) {
        strikes.add(new double[]{at.x, at.y, at.z, level.getGameTime() + warn, r, share, 3, colour});
        Fx.warnClosing(level, at, (float) r, colour, warn);
    }

    /** The fiesta's strikes going off (tickStrikes, mode 3): a burst of colour where it was marked. */
    private void fiestaBurst(ServerLevel level, double[] s) {
        Vec3 at = new Vec3(s[0], s[1], s[2]);
        int col = s.length > 7 ? (int) s[7] : 0xFF9618;
        Fx.shock(level, at, (float) s[4] + 0.8F, col);
        Fx.flash(level, at.add(0, 0.6, 0), 1.8F);
        Fx.spark(level, at.add(0, 0.6, 0), 18, 0.9, 0.45F, col);
        Fx.spark(level, at.add(0, 1.2, 0), 8, 0.6, 0.35F, 0xFFF4C0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 2.0F, 0.8F + random.nextFloat() * 0.3F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(s[4] + 0.5, 4, s[4] + 0.5), this::hittable)) {
            if (e.distanceToSqr(at.x, e.getY(), at.z) > (s[4] + e.getBbWidth() / 2) * (s[4] + e.getBbWidth() / 2)) continue;
            if (deadHit(e, (float) s[5])) {
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.65, 0));
                e.hurtMarked = true;
                e.setSecondsOnFire(2);
            }
        }
    }

    // ---- El Ouroboros

    private final UUID[] ouroBeam = new UUID[2];
    private final int[][] ouroRing = new int[2][];
    private int ouroEnd;
    private final java.util.Map<Integer, Integer> breathHit = new java.util.HashMap<>();

    /**
     * El Ouroboros (about 14 s): "Around and around...!" He hangs high over the middle, arms out, and calls them down: Quetzalcoatl
     * and Kukulcan come out of the sky onto a ring right round the floor, on opposite sides, each chasing the other's tail: the
     * serpent that eats its own tail, the whole arena walled in by them (their bodies hurt). As they go round their heads turn
     * in and breathe jade fire low across the floor, two great hands of a clock sweeping round (a thin line first): jump it.
     * Three breaths, then they rise back into the sky.
     */
    private void tickP4Ouroboros(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        flyTo(new Vec3(c.x, floor + 14, c.z), 0.9);
        if (target != null) turnToward(target.position(), 5F);
        // (2026-10-05) longer and fiercer by stage: three breaths for the guardian, four in the storm, five, closer, at the end
        int st = Math.max(1, p4StageReached);
        int[][] windows = ringWindows(st, st + 2);
        if (t == 1) {
            say("p4_ouroboros", 60);
            breathHit.clear();
            if (!closeRing(level, t, windows[windows.length - 1][2] + 20)) {
                setBossState(BossState.IDLE);
                return;
            }
            crowdReact(level, CrowdMood.GASP, 40);
            PaponNetwork.shake(level, c, 120, 0.4F, 30);
        }
        tickRingBreaths(level, t, windows);
        if (t > 20 && t % 72 == 0) level.playSound(null, c.x, floor + 3, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 1.2F, 0.35F);
        if (t >= ouroEnd || t > 480) setBossState(BossState.IDLE);
    }

    private void clearOuroboros(ServerLevel level) {
        for (int k = 0; k < 2; k++) {
            if (ouroBeam[k] != null && level.getEntity(ouroBeam[k]) instanceof BeamEntity b) b.discard();
            ouroBeam[k] = null;
        }
    }

    // ---- Quetzalcoatl and Kukulcan's green fire (2026-10-05, the user: "they need to breathe green fire out of their mouths,
    // and maybe in an attack they combine and make a super fusion attack")

    /** A sweep of green fire from a serpent's jaws across the floor, from a to b, between start and until (game ticks). */
    private record Breath(int serpent, long start, long until, Vec3 a, Vec3 b, java.util.Map<Integer, Long> hit) {}

    private static final int JADE = 0x52FF84;
    private final List<Breath> breaths = new ArrayList<>();
    private int passiveBreathCd = 240, breathTurn;

    /** Serpent {@code k} breathes a sweep across the floor from a to b: a green line warns for {@code warn} ticks, then the
     *  fire runs down it over {@code len}. */
    private void breathe(ServerLevel level, int k, Vec3 a, Vec3 b, int warn, int len) {
        SkySerpentEntity sp = serpent(level, k);
        if (sp == null) return;
        double floor = deadFloor();
        a = inSand(new Vec3(a.x, floor, a.z), 1, 0, 0);
        b = inSand(new Vec3(b.x, floor, b.z), 1, 0, 0);
        long now = level.getGameTime();
        Fx.warnLine(level, a, b, 3.4F, JADE, warn + len);
        breaths.add(new Breath(k, now + warn, now + warn + len, a, b, new java.util.HashMap<>()));
        sp.roar();
    }

    /** Where a serpent's fire comes from: just in front of its jaws. */
    private static Vec3 mouth(SkySerpentEntity sp) {
        return sp.headAt(0).add(sp.heading(0).scale(SkySerpentEntity.HEAD * 0.45));
    }

    private void tickBreaths(ServerLevel level) {
        if (breaths.isEmpty()) return;
        long now = level.getGameTime();
        breaths.removeIf(br -> now > br.until() || serpent(level, br.serpent()) == null);
        double floor = deadFloor();
        for (Breath br : breaths) {
            if (now < br.start()) continue;
            SkySerpentEntity sp = serpent(level, br.serpent());
            float u = (float) (now - br.start()) / Math.max(1, br.until() - br.start());
            Vec3 m = mouth(sp);
            Vec3 at = br.a().lerp(br.b(), u);
            at = new Vec3(at.x, floor, at.z);
            Vec3 d = at.subtract(m);
            double len = Math.max(1, d.length());
            Vec3 dir = d.scale(1 / len);
            if (now == br.start()) {
                level.playSound(null, m.x, m.y, m.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 6.0F, 0.45F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 4.0F, 0.4F);
            }
            // the stream: licks of green fire thrown out of the jaws all the way down, and soul fire along it
            for (int i = 0; i < 6; i++) {
                Vec3 from = m.add(dir.scale(random.nextDouble() * len * 0.25));
                Vec3 v = dir.scale(len / 12.0).add((random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
                Fx.jet(level, from, v, 2.0F + random.nextFloat() * 1.4F, JADE);
            }
            for (int i = 0; i < 3; i++) {
                Vec3 p = m.lerp(at, random.nextDouble());
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 3, 0.7, 0.7, 0.7, 0.03);
            }
            // where it strikes the floor: a splash of flame, a scorch trail, the roar of it
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Fx.jet(level, at.add(0, 0.3, 0), new Vec3(Math.cos(a) * 0.35, 0.18 + random.nextDouble() * 0.25, Math.sin(a) * 0.35), 2.4F, JADE);
            }
            if (now % 3 == 0) Fx.scorch(level, at, 2.6F, 160);
            if (now % 5 == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.5F + random.nextFloat() * 0.2F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6, 8, 6).expandTowards(m.subtract(at)), this::hittable)) {
                Long last = br.hit().get(e.getId());
                if (last != null && now - last < 12) continue;
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                boolean inPool = Mth.square(e.getX() - at.x) + Mth.square(e.getZ() - at.z) < Mth.square(3.0 + e.getBbWidth() / 2) && e.getY() < floor + 4;
                boolean inStream = segDist(c, m, at) < 1.6 + e.getBbWidth() / 2;
                if ((inPool || inStream) && deadHit(e, 0.09F)) {
                    br.hit().put(e.getId(), now);
                    e.setSecondsOnFire(4);
                }
            }
        }
    }

    /** Between his moves, now and then one of them breathes a sweep through where you are going (alternating). */
    private void tickPassiveBreath(ServerLevel level, @Nullable LivingEntity target) {
        int st = p4StageReached;
        // (2026-10-05) by stage: one sweep at a time and none over a dive for the guardian; over the dives in the storm; two
        // at once at the end of the world
        if (!muertosFight() || target == null || breaths.size() >= (st >= 3 ? 2 : 1)) return;
        BossState s = getBossState();
        if (s.phaseFour() || s == BossState.P4_OUROBOROS || s == BossState.P4_SERPIENTES || s == BossState.P4_ALIENTO || s == BossState.P4_FUSION
                || s == BossState.P4_JUICIO || s == BossState.P4_RAGE || s == BossState.DEATH
                || s == BossState.P4_TANGO || s == BossState.P4_TANGO_WIN || s == BossState.P4_TANGO_LOSE || (st < 2 && !serpentDives.isEmpty())) return;
        if (--passiveBreathCd > 0) return;
        passiveBreathCd = st >= 3 ? 80 + random.nextInt(60) : st == 2 ? 170 + random.nextInt(90) : 300 + random.nextInt(180);
        int k = breathTurn++ % 2;
        SkySerpentEntity sp = serpent(level, k);
        if (sp == null || sp.busy()) return;
        Vec3 aim = predict(target, 30);
        double a = random.nextDouble() * Math.PI * 2;
        Vec3 side = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(11);
        breathe(level, k, aim.subtract(side), aim.add(side), 26, 40);
        if (random.nextInt(2) == 0) quip("p4_fire" + random.nextInt(2), 1F);
    }

    /**
     * Aliento de Jade (7.5 s): "Quetzalcoatl, Kukulcan... let them taste the fire of the old gods!" He conducts from over the
     * floor; the serpents take turns breathing sweeps of green fire through where you are going, each across the last; then
     * both at once, crossing on you. Each touch: 9% and set alight (step out of the line while it is still only green).
     */
    private void tickP4Aliento(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        Vec3 c = p4Center();
        flyTo(new Vec3(Mth.lerp(0.5, getX(), c.x), floor + 8, Mth.lerp(0.5, getZ(), c.z)), 0.4);
        if (target != null) turnToward(target.position(), 5F);
        if (t == 2) say("p4_aliento", 50);
        if (target != null) {
            // (2026-10-05) more sweeps, closer together, by stage; at the end of the world they are twice as long
            int st = Math.max(1, p4StageReached);
            int[] at = st >= 3 ? new int[]{10, 28, 46, 64, 82, 100} : st == 2 ? new int[]{12, 34, 56, 78, 100} : new int[]{12, 40, 68, 96};
            double reach = st >= 3 ? 20 : 13;
            for (int i = 0; i < at.length; i++) {
                if (t != at[i]) continue;
                Vec3 aim = predict(target, 24);
                double ang = (i % 2 == 0 ? 0 : Math.PI / 2) + Math.atan2(target.getZ() - c.z, target.getX() - c.x);
                Vec3 side = new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(reach);
                breathe(level, i % 2, aim.subtract(side), aim.add(side), st >= 3 ? 16 : 18, st >= 3 ? 38 : 30);
            }
            if (t == 120) {
                Vec3 aim = predict(target, 22);
                double ang = random.nextDouble() * Math.PI;
                for (int k = 0; k < 2; k++) {
                    double g = ang + k * Math.PI / 2;
                    Vec3 side = new Vec3(Math.cos(g), 0, Math.sin(g)).scale(14);
                    breathe(level, k, aim.subtract(side), aim.add(side), 16, 24);
                }
                crowdReact(level, CrowdMood.GASP, 30);
            }
        }
        if (t >= BossState.P4_ALIENTO.duration) setBossState(BossState.IDLE);
    }

    /** La Gran Serpiente: when the two are coiled on their knot (game ticks), the beam, the rings out of where it strikes. */
    private long fusionOn, fusionEnd;
    @Nullable private UUID fusionBeam;
    @Nullable private Vec3 fusionHit;
    private final List<double[]> fusionRings = new ArrayList<>();
    private final java.util.Map<Integer, Long> fusionHits = new java.util.HashMap<>();
    private static final double FUSION_UP = 30, FUSION_R = 8;

    /**
     * La Gran Serpiente (up to 15 s): "Brothers of stone... become ONE!" Both serpents leave their orbit and coil round each
     * other on one tight ring high over the middle, faster and faster, until they are one knot of feathers and green fire; a
     * sun of it swells between their jaws (2.5 s, a ring closing on you), and it pours down: one huge beam that hunts you
     * across the floor (sprint and it cannot keep up; walk and it will), 10% a touch, and every 1.2 s a shockwave rolling out
     * of where it strikes (jump it, 12%). Then it bursts, and they come apart.
     */
    private void tickP4Fusion(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        long now = level.getGameTime();
        Vec3 c = p4Center();
        double floor = deadFloor();
        Vec3 knot = new Vec3(c.x, floor + FUSION_UP, c.z);
        flyTo(new Vec3(c.x, floor + 9, c.z), 0.8);
        if (target != null) turnToward(target.position(), 4F);
        if (t == 1) {
            say("p4_fusion", 60);
            fusionHits.clear();
            fusionRings.clear();
            fusionHit = null;
            double per = 2 * Math.PI * FUSION_R;
            double laps = 190 * SkySerpentEntity.SPEED / per;
            double ang0 = random.nextDouble() * Math.PI * 2;
            int dir = random.nextBoolean() ? 1 : -1;
            int[][] got = new int[2][];
            for (int k = 0; k < 2; k++) {
                SkySerpentEntity sp = serpent(level, k);
                if (sp != null) got[k] = sp.ring(new Vec3(c.x, floor, c.z), FUSION_R, FUSION_R, FUSION_UP, ang0 + k * Math.PI, dir, laps, 8);
            }
            if (got[0] == null || got[1] == null) {
                setBossState(BossState.IDLE);
                return;
            }
            if (got[0][0] != got[1][0]) {
                int late = got[0][0] > got[1][0] ? 0 : 1, early = 1 - late;
                got[early] = serpent(level, early).ring(new Vec3(c.x, floor, c.z), FUSION_R, FUSION_R, FUSION_UP, ang0 + early * Math.PI, dir, laps,
                        8 + (got[late][0] - got[early][0]) * SkySerpentEntity.SPEED);
            }
            fusionOn = now + Math.max(got[0][0], got[1][0]);
            fusionEnd = now + Math.min(got[0][0] + got[0][1], got[1][0] + got[1][1]);
            for (int k = 0; k < 2; k++) serpent(level, k).roar();
            crowdReact(level, CrowdMood.GASP, 50);
        }
        if (fusionOn == 0) return;
        long charge = fusionOn + 50, stop = Math.max(charge + 60, fusionEnd - 8);
        // the knot: green fire wound round it, swelling as they coil
        if (now >= fusionOn - 20 && now < stop) {
            float g = Mth.clamp((now - (fusionOn - 20)) / 70F, 0F, 1F);
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = (1.5 + 4.5 * g) * (0.6 + random.nextDouble() * 0.4);
                Vec3 p = knot.add(Math.cos(a) * r, (random.nextDouble() - 0.5) * r, Math.sin(a) * r);
                Fx.jet(level, p, knot.subtract(p).scale(0.12), 2.2F + 2F * g, JADE);
            }
            if (now % 6 == 0) Fx.flash(level, knot, 3F + 6F * g);
            if (now % 10 == 0) level.playSound(null, knot.x, knot.y, knot.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 6.0F, 0.5F + g);
        }
        if (now == fusionOn) {
            cine(level, ModSounds.CINE_SWELL.get(), knot, 0.8F);
            PaponNetwork.shake(level, c, 120, 0.4F, 50);
        }
        if (now == fusionOn + 20 && target != null) {
            fusionHit = new Vec3(target.getX(), floor, target.getZ());
            Fx.warnClosing(level, fusionHit, 5F, JADE, 30);
        }
        if (now >= charge && now < stop && fusionHit != null) {
            if (now == charge) {
                for (int k = 0; k < 2; k++) {
                    SkySerpentEntity sp = serpent(level, k);
                    if (sp != null) sp.roar();
                }
                level.playSound(null, knot.x, knot.y, knot.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 8.0F, 0.5F);
                level.playSound(null, fusionHit.x, fusionHit.y, fusionHit.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6.0F, 0.5F);
                crowdRoar(level, 40);
                salvo(level, 4);
            }
            // it hunts the nearest of you: a sprint gets away, a walk does not
            LivingEntity hunt = target;
            if (hunt != null) {
                Vec3 want = new Vec3(hunt.getX(), floor, hunt.getZ()).subtract(fusionHit);
                double step = Math.min(want.length(), 0.24);
                if (step > 1e-3) fusionHit = fusionHit.add(want.normalize().scale(step));
            }
            fusionHit = inSand(fusionHit, 1, 0, 0);
            fusionHit = new Vec3(fusionHit.x, floor, fusionHit.z);
            BeamEntity b = fusionBeam != null && level.getEntity(fusionBeam) instanceof BeamEntity be && be.isAlive() ? be : null;
            if (b == null) {
                b = BeamEntity.create(level, knot, fusionHit, 4.5F, JADE).life(600);
                level.addFreshEntity(b);
                fusionBeam = b.getUUID();
            }
            b.set(knot, fusionHit);
            b.setMode(BeamEntity.FIRE);
            b.setWidth(4.2F + 0.5F * Mth.sin(now * 0.6F));
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Fx.jet(level, fusionHit.add(0, 0.4, 0), new Vec3(Math.cos(a) * 0.6, 0.25 + random.nextDouble() * 0.4, Math.sin(a) * 0.6), 3.2F, JADE);
            }
            if (now % 2 == 0) Fx.scorch(level, fusionHit, 4.2F, 220);
            if (now % 4 == 0) level.playSound(null, fusionHit.x, fusionHit.y, fusionHit.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3.0F, 0.4F);
            if (now % 24 == 0) {
                fusionRings.add(new double[]{fusionHit.x, fusionHit.z, now});
                Fx.shock(level, fusionHit, 20F, JADE);
                PaponNetwork.shake(level, fusionHit, 40, 0.3F, 8);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(fusionHit, fusionHit).inflate(6, 10, 6), this::hittable)) {
                Long last = fusionHits.get(e.getId());
                if (last != null && now - last < 8) continue;
                if (Mth.square(e.getX() - fusionHit.x) + Mth.square(e.getZ() - fusionHit.z) > Mth.square(3.6 + e.getBbWidth() / 2)) continue;
                if (deadHit(e, 0.10F)) {
                    fusionHits.put(e.getId(), now);
                    e.setSecondsOnFire(5);
                }
            }
        }
        if (now == stop) {
            clearFusion(level);
            Fx.flash(level, knot, 12F);
            Fx.shock(level, new Vec3(c.x, floor, c.z), 30F, JADE);
            level.playSound(null, knot.x, knot.y, knot.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8.0F, 0.6F);
            level.playSound(null, knot.x, knot.y, knot.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0F, 0.6F);
            PaponNetwork.shake(level, c, 120, 0.7F, 25);
            crowdReact(level, CrowdMood.ROAR, 40);
        }
        if (now >= stop + 16 || t > 360) {
            fusionOn = 0;
            setBossState(BossState.IDLE);
        }
    }

    /** The fused beam's shockwaves, rolling out at 2 blocks a tick to 20 (jump them: only the grounded are caught). */
    private void tickFusionRings(ServerLevel level) {
        if (fusionRings.isEmpty()) return;
        long now = level.getGameTime();
        double floor = deadFloor();
        fusionRings.removeIf(r -> (now - (long) r[2]) * 2.0 > 20);
        for (double[] r : fusionRings) {
            double rad = (now - (long) r[2]) * 2.0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(r[0], floor, r[1], r[0], floor, r[1]).inflate(rad + 2, 3, rad + 2), this::hittable)) {
                if (e.getY() > floor + 1.1) continue;                   // jumped it
                double dd = Math.sqrt(Mth.square(e.getX() - r[0]) + Mth.square(e.getZ() - r[1]));
                if (Math.abs(dd - rad) > 1.1) continue;
                Long last = fusionHits.get(-e.getId() - 1);
                if (last != null && last == (long) r[2]) continue;
                if (deadHit(e, 0.12F)) {
                    fusionHits.put(-e.getId() - 1, (long) r[2]);
                    Vec3 away = new Vec3(e.getX() - r[0], 0, e.getZ() - r[1]);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    e.setDeltaMovement(away.scale(1.1).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
        }
    }

    private void clearFusion(ServerLevel level) {
        if (fusionBeam != null && level.getEntity(fusionBeam) instanceof BeamEntity b) b.discard();
        fusionBeam = null;
    }

    // ------------------------------------------------------------------ phase four in stages (2026-10-05)
    // (the user: "make the final phase feel like a major escalation and the true climax of the fight: more frequent, more intense
    // attacks that overlap; the serpents lasting longer, covering more of the arena, part of his patterns, not occasional
    // standalone attacks; El Gigante a meaningful part of the fight alongside the serpents and him; dangerous from the start,
    // increasingly chaotic toward the end, as if he were losing control; the final portion combining him, the serpents and the
    // giant into a challenging, visually spectacular climax".)
    //   Stage 1, the guardian (a quarter of his health down to STAGE_TWO_AT): his moves as they were, a little quicker.
    //   Stage 2, the storm: P4_RAGE, and El Gigante rises to stay: it fights beside him on its own (tickGiantLayer), the serpents
    //     hunt on their own between and during his moves (tickSerpentLayer), every dive leaves the floor burning behind it.
    //   Stage 3, the end of the world (STAGE_THREE_AT): P4_RAGE into P4_JUICIO, the climax; after it everything at once and
    //     faster, the dead erupting round you on their own (tickChaos), El Juicio Final again now and then.

    /** Where the stages turn (his share of health): the guardian down to the first, the storm down to the second. */
    private static final float STAGE_TWO_AT = 0.17F, STAGE_THREE_AT = 0.085F;
    /** The stage he has reached (its rage played); saved. */
    private int p4StageReached = 1;
    private int dJuicioCd = 0, giantNext = 80, giantRespawn = 0, giantRelocateCd = 400, lastGiantMove = -1;
    private int serpentLayerCd = 160, serpentQueue, serpentTurn, chaosCd = 160, juicioWinded;
    private boolean giantLaserPending;
    private int giantChargeT, giantLaserLen, giantLaserMode;
    private final UUID[] giantBeams = new UUID[2];
    /** The fire a dive leaves on the floor: ax, az, bx, bz, the tick its head reaches a, blocks a tick, the tick it goes out. */
    private final List<double[]> trails = new ArrayList<>();
    private final java.util.Map<Integer, Long> trailHit = new java.util.HashMap<>();
    /** El Juicio Final's last blast: the marigold circles that survive it. */
    private final List<Vec3> juicioSafe = new ArrayList<>();
    private static final int GIANT_TRACK = 0, GIANT_SWEEP = 1;
    private static final int GIANT_BLUE = 0x40E2FF;

    /** Phase four's stage by his health: 1 the guardian, 2 the storm, 3 the end of the world (0 before his fight). */
    public int p4Stage() {
        if (!muertosFight()) return 0;
        float f = getHealth() / getMaxHealth();
        return f > STAGE_TWO_AT ? 1 : f > STAGE_THREE_AT ? 2 : 3;
    }

    /** Client: the stage reached (the sky and his aura go wilder with it). */
    public int p4StageShown() { return entityData.get(DATA_P4STAGE); }

    /** A stage to break into, the next tick he is free. */
    private boolean stageDue() {
        if (!muertosFight() || p4StageReached >= p4Stage() || getHealth() <= 0) return false;
        BossState s = getBossState();
        return s == BossState.IDLE || s == BossState.WINDED;
    }

    private void startRage(ServerLevel level) {
        p4StageReached++;
        entityData.set(DATA_P4STAGE, p4StageReached);
        think = 0;
        lastUsed.put(BossState.P4_RAGE, tickCount);
        PaponMod.LOG.info("[boss] phase four, stage {}", p4StageReached);
        setBossState(BossState.P4_RAGE);
    }

    /**
     * P4_RAGE (4 s): it is getting away from him. Hanging over the floor he clutches his skull, shaking; the ground trembles; he
     * flings his arms wide and screams, and the arena answers: in the storm El Gigante tears up out of the floor at your end to
     * stay, and the serpents peel off to hunt; at the end of the world everything roars at once and he goes straight into El
     * Juicio Final.
     */
    private void tickP4Rage(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        int st = p4StageReached;
        Vec3 c = p4Center();
        double floor = deadFloor();
        flyTo(new Vec3(Mth.lerp(0.35, getX(), c.x), floor + 10, Mth.lerp(0.35, getZ(), c.z)), 0.6);
        if (target != null) turnToward(target.position(), 6F);
        if (t == 1) {
            cine(level, ModSounds.CINE_SWELL.get(), position(), st >= 3 ? 0.6F : 0.75F);
            crowdReact(level, CrowdMood.GASP, 60);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 4.0F, 0.4F);
        }
        if (t == 4) say(st >= 3 ? "p4_stage3" : "p4_stage2", 90);
        if (t >= 6 && t < 28 && t % 3 == 0) {
            PaponNetwork.shake(level, c, 140, 0.2F + 0.08F * st, 5);
            Fx.spark(level, position().add(0, getBbHeight() * 0.6, 0), 6, 0.8, 0.4F, st >= 3 ? 0xFF3A3A : JADE);
        }
        if (t == 28) {
            // the scream, and the arena answers it
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.6F);
            PaponNetwork.shake(level, c, 160, 0.9F, 30);
            Fx.flash(level, position().add(0, getBbHeight() * 0.6, 0), 9F);
            Fx.shock(level, new Vec3(getX(), floor, getZ()), 26F, st >= 3 ? 0xFF3A3A : JADE);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                Fx.jet(level, position().add(0, getBbHeight() * 0.6, 0), new Vec3(Math.cos(a) * 0.9, 0.2, Math.sin(a) * 0.9), 3F, i % 2 == 0 ? JADE : 0xFF8A1E);
            }
            crowdReact(level, CrowdMood.ROAR, 50);
            for (int k = 0; k < 2; k++) {
                SkySerpentEntity sp = serpent(level, k);
                if (sp != null) sp.roar();
            }
            SpiritGiantEntity g = giant(level);
            if (g == null && inColiseum() && arena != null) g = summonGiant(level, target);
            else if (g != null && g.act() == SpiritGiantEntity.IDLE) g.act(SpiritGiantEntity.ROAR);
            if (g != null) {
                g.fury(st);
                g.keep(600);
            }
            serpentLayerCd = 50;
            giantNext = 90;
        }
        if (t >= BossState.P4_RAGE.duration) {
            SkySerpentEntity q = serpent(level, 0), k = serpent(level, 1);
            if (st >= 3 && inColiseum() && arena != null && q != null && k != null && target != null) {
                // the serpents finish any dive first (a ring laid over a dive in flight would jerk them across the sky)
                if ((q.busy() || k.busy()) && t < BossState.P4_RAGE.duration + 100) return;
                startAttack(BossState.P4_JUICIO, target);
                return;
            }
            think = 0;
            setBossState(BossState.IDLE);
        }
    }

    // ---- El Gigante, in the fight

    @Nullable
    private SpiritGiantEntity giant(ServerLevel level) {
        return giantId != null && level.getEntity(giantId) instanceof SpiritGiantEntity g && g.isAlive() && g.act() != SpiritGiantEntity.SINK ? g : null;
    }

    /** El Gigante up out of the floor at the end you are at (with nobody to go by, the end he is not at). */
    private SpiritGiantEntity summonGiant(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 c = arena.centerVec();
        double floor = arena.floorY();
        giantEnd = target != null ? (target.getX() < c.x ? -1 : 1) : (getX() < c.x ? 1 : -1);
        Vec3 gAt = new Vec3(c.x + giantEnd * (arena.a - 13), floor, c.z);
        SpiritGiantEntity g = SpiritGiantEntity.create(level, this, gAt, new Vec3(c.x, floor, c.z), new Vec3(c.x, floor, c.z));
        g.fury(Math.max(1, p4StageReached));
        if (p4StageReached >= 2) g.keep(600);
        level.addFreshEntity(g);
        giantId = g.getUUID();
        giantAim = new Vec3(c.x, floor, c.z);
        giantLaserPending = false;
        giantRelocateCd = 500;
        crowdReact(level, CrowdMood.GASP, 60);
        PaponNetwork.shake(level, gAt, 120, 0.7F, 40);
        return g;
    }

    /** It charges its eyes for {@code charge} ticks, then fires for {@code len}: after you, or swept across the floor. */
    private void giantLaser(SpiritGiantEntity g, int charge, int len, int mode) {
        g.act(SpiritGiantEntity.CHARGE);
        giantLaserPending = true;
        giantChargeT = charge;
        giantLaserLen = len;
        giantLaserMode = mode;
        giantSweep = random.nextBoolean();
    }

    /** One hand (or both) down on where you will be: the marks, warned on the floor. */
    private void giantSlam(ServerLevel level, SpiritGiantEntity g, LivingEntity target, boolean both) {
        double floor = arena.floorY();
        Vec3 at = inSand(predict(target, both ? 26 : 18), 3, 0, 0);
        at = new Vec3(at.x, floor, at.z);
        if (both) {
            Vec3 side = g.right().scale(3.6);
            Vec3 l = inSand(at.add(side), 2, 0, 0), r = inSand(at.subtract(side), 2, 0, 0);
            g.handTarget(0, new Vec3(l.x, floor, l.z));
            g.handTarget(1, new Vec3(r.x, floor, r.z));
            Fx.warnClosing(level, at, 9.5F, GIANT_BLUE, SpiritGiantEntity.SLAM2_HIT);
            g.act(SpiritGiantEntity.SLAM_BOTH);
        } else {
            // the hand on the side you are on (0 its left, on the -right side)
            int i = at.subtract(g.base()).dot(g.right()) > 0 ? 1 : 0;
            g.handTarget(i, at);
            Fx.warnClosing(level, at, 5.5F, GIANT_BLUE, SpiritGiantEntity.SLAM_HIT);
            g.act(i == 0 ? SpiritGiantEntity.SLAM_L : SpiritGiantEntity.SLAM_R);
        }
    }

    private void giantSwipe(SpiritGiantEntity g, @Nullable LivingEntity target, int act) {
        double r = target != null ? Mth.clamp(Math.sqrt(target.distanceToSqr(g.base().x, target.getY(), g.base().z)), 12, 38) : 22;
        g.swipe(act, r);
        swipeHit.clear();
    }

    /**
     * El Gigante on its own (the storm on): every few seconds (faster at the end of the world) a sweep of a hand through where you
     * stand, its eyes' lasers after you or across the floor, a hand or both down on you; between them its skull follows you. Run
     * off to the far end and it sinks and comes up again at yours. Not while he directs it himself (El Gigante, El Juicio Final,
     * his rage) or in the dance-off.
     */
    private void tickGiantLayer(ServerLevel level, @Nullable LivingEntity target) {
        if (!muertosFight() || arena == null || !inColiseum()) return;
        SpiritGiantEntity g = giant(level);
        boolean stays = p4StageReached >= 2;
        if (g != null) {
            g.fury(p4StageReached);
            if (stays) g.keep(400);
        }
        tickGiantHazards(level, g, target);
        if (!stays) return;
        BossState s = getBossState();
        if (s == BossState.P4_GIGANTE || s == BossState.P4_JUICIO || s == BossState.P4_RAGE || s.phaseFour() || s == BossState.DEATH
                || s == BossState.P4_TANGO || s == BossState.P4_TANGO_WIN || s == BossState.P4_TANGO_LOSE) return;
        if (giantRelocateCd > 0) giantRelocateCd--;
        if (g == null) {
            if (target != null && --giantRespawn <= 0) {
                summonGiant(level, target);
                giantNext = 75;
                giantRespawn = 160;
            }
            return;
        }
        if (g.act() != SpiritGiantEntity.IDLE) return;
        if (target != null && tickCount % 4 == 0) g.aim(target.position().add(0, 1, 0));
        // you ran off to the far end of the arena: it sinks and rises again where you are
        if (target != null && giantRelocateCd <= 0 && Math.sqrt(target.distanceToSqr(g.base().x, target.getY(), g.base().z)) > arena.a * 1.45) {
            g.sink();
            giantId = null;
            giantRespawn = 50;
            return;
        }
        if (--giantNext > 0 || target == null) return;
        int st = p4StageReached;
        // while he is winded in the storm it leaves you your window; at the end of the world nothing does
        if (s == BossState.WINDED && st < 3) {
            giantNext = 20;
            return;
        }
        giantNext = st >= 3 ? 64 + random.nextInt(36) : 120 + random.nextInt(60);
        List<Integer> moves = new ArrayList<>(List.of(0, 1, 2));
        if (st >= 3) moves.add(3);
        moves.remove(Integer.valueOf(lastGiantMove));
        int m = moves.get(random.nextInt(moves.size()));
        lastGiantMove = m;
        switch (m) {
            case 0 -> giantSwipe(g, target, random.nextBoolean() ? SpiritGiantEntity.SWIPE_L : SpiritGiantEntity.SWIPE_R);
            case 1 -> giantLaser(g, st >= 3 ? 20 : 26, st >= 3 ? 56 : 46, random.nextInt(3) == 0 ? GIANT_SWEEP : GIANT_TRACK);
            case 2 -> giantSlam(level, g, target, false);
            default -> giantSlam(level, g, target, true);
        }
        if (random.nextInt(4) == 0) quip("p4_crush", 1F);
    }

    /** The giant's beams out of its eyes (drawn from its eyes as the client sees them: BeamEntity.eye). */
    @Nullable
    private BeamEntity giantBeam(ServerLevel level, int i) {
        return giantBeams[i] != null && level.getEntity(giantBeams[i]) instanceof BeamEntity b && b.isAlive() ? b : null;
    }

    private void clearGiantBeams(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            BeamEntity b = giantBeam(level, i);
            if (b != null) b.discard();
            giantBeams[i] = null;
        }
    }

    /**
     * Everything El Gigante hits, every tick, whoever told it to: its eyes finding you and the lasers (16%, set alight), the sweep
     * of a hand (20% and flung into the wall), a hand coming down (24% where it lands, a ring rolling out), both hands (28% each,
     * three rings: jump them). Its warnings on the floor as each comes.
     */
    private void tickGiantHazards(ServerLevel level, @Nullable SpiritGiantEntity g, @Nullable LivingEntity target) {
        if (g == null) {
            if (giantBeams[0] != null || giantBeams[1] != null) clearGiantBeams(level);
            giantLaserPending = false;
            return;
        }
        Vec3 c = arena.centerVec();
        double floor = arena.floorY();
        int act = g.act();
        float at = g.actTime(0);
        int st = Math.max(1, p4StageReached);
        if (giantAim == null) giantAim = new Vec3(c.x, floor, c.z);
        // the eyes: finding you, then the lasers
        if (act == SpiritGiantEntity.CHARGE) {
            if (target != null) giantAim = giantAim.lerp(new Vec3(target.getX(), floor, target.getZ()), 0.12);
            g.aim(giantAim);
            if ((int) at % 4 == 0) {
                for (int s : new int[]{-1, 1}) {
                    Vec3 eye = g.eye(s, 0);
                    Fx.warnLine(level, new Vec3(eye.x, floor, eye.z), giantAim, 1.4F, GIANT_BLUE, 5);
                }
            }
            if (giantLaserPending && at >= giantChargeT) {
                giantLaserPending = false;
                g.act(SpiritGiantEntity.LASER);
                act = SpiritGiantEntity.LASER;
                at = 0;
                clearGiantBeams(level);
                for (int i = 0; i < 2; i++) {
                    int side = i == 0 ? -1 : 1;
                    BeamEntity b = BeamEntity.create(level, g.eye(side, 0), giantAim, 3.4F + 0.3F * (st - 1), GIANT_BLUE).life(400).eye(g, side);
                    b.setMode(BeamEntity.FIRE);
                    level.addFreshEntity(b);
                    giantBeams[i] = b.getUUID();
                }
                Vec3 h = g.head(0);
                level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 6.0F, 0.4F);
                level.playSound(null, h.x, h.y, h.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4.0F, 0.5F);
                PaponNetwork.shake(level, c, 120, 0.7F, 36);
            }
        }
        if (act == SpiritGiantEntity.LASER) {
            if (at >= giantLaserLen) {
                g.act(SpiritGiantEntity.IDLE);
                clearGiantBeams(level);
            } else {
                if (giantLaserMode == GIANT_SWEEP) {
                    // swept right across the floor, side to side, from its end to the far one
                    double k = at / Math.max(1.0, giantLaserLen);
                    double z = c.z + (k * 2 - 1) * arena.b * 0.9 * (giantSweep ? 1 : -1);
                    double x = c.x + giantEnd * (arena.a * 0.15 - arena.a * 0.65 * Math.sin(k * Math.PI));
                    giantAim = new Vec3(x, floor, z);
                } else if (target != null) {
                    Vec3 want = new Vec3(target.getX(), floor, target.getZ());
                    Vec3 d = want.subtract(giantAim);
                    double step = 0.4 + 0.08 * st;
                    giantAim = d.length() < step ? want : giantAim.add(d.normalize().scale(step));
                }
                giantAim = inSand(giantAim, 1, 0, 0);
                giantAim = new Vec3(giantAim.x, floor, giantAim.z);
                g.aim(giantAim);
                for (int i = 0; i < 2; i++) {
                    BeamEntity b = giantBeam(level, i);
                    if (b != null) b.set(g.eye(i == 0 ? -1 : 1, 0), giantAim);
                }
                if ((int) at % 3 == 0) {
                    Fx.shock(level, giantAim, 3.4F, GIANT_BLUE);
                    Fx.scorch(level, giantAim, 3.2F, 220);
                    Fx.spark(level, giantAim.add(0, 0.5, 0), 8, 1.5, 0.55F, 0x9FF4FF);
                }
                if ((int) at % 8 == 0) level.playSound(null, giantAim.x, giantAim.y, giantAim.z, SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F);
                if ((int) at % 5 == 0) {
                    for (LivingEntity e : victims(level, arena.a + 6)) {
                        Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
                        double dist = Math.min(segDist(mid, g.eye(-1, 0), giantAim), segDist(mid, g.eye(1, 0), giantAim));
                        double flat = Math.sqrt(e.distanceToSqr(giantAim.x, e.getY(), giantAim.z));
                        if (dist < 1.9 + e.getBbWidth() / 2 || flat < 3.3) {
                            if (deadHit(e, 0.16F)) {
                                e.setSecondsOnFire(3);
                                e.setDeltaMovement(e.getDeltaMovement().add(e.position().subtract(giantAim).multiply(1, 0, 1).normalize().scale(0.6)).add(0, 0.35, 0));
                                e.hurtMarked = true;
                            }
                        }
                    }
                }
            }
        } else if (giantBeams[0] != null || giantBeams[1] != null) {
            clearGiantBeams(level);
        }
        // the swipes: the band swept this tick
        if (act == SpiritGiantEntity.SWIPE_L || act == SpiritGiantEntity.SWIPE_R) {
            int i = act == SpiritGiantEntity.SWIPE_L ? 0 : 1;
            double r = g.swipeRadius();
            Vec3 f = g.forward(), rt = g.right(), base = g.base();
            if (at < SpiritGiantEntity.SWIPE_FROM && (int) at % 4 == 0) {
                for (int k = 0; k <= 12; k++) {
                    double ang = SpiritGiantEntity.swipeAngle(i, 0) + (SpiritGiantEntity.swipeAngle(i, 99) - SpiritGiantEntity.swipeAngle(i, 0)) * k / 12.0;
                    Vec3 p = base.add(f.scale(Math.cos(ang) * r)).add(rt.scale(-Math.sin(ang) * r));
                    if (arena.contains(p)) Fx.warn(level, new Vec3(p.x, floor, p.z), 4.8F, GIANT_BLUE, 5);
                }
            }
            if (at >= SpiritGiantEntity.SWIPE_FROM - 1 && at <= SpiritGiantEntity.SWIPE_TO + 1) {
                double a0 = SpiritGiantEntity.swipeAngle(i, at - 1.2F), a1 = SpiritGiantEntity.swipeAngle(i, at);
                double lo = Math.min(a0, a1) - 0.08, hi = Math.max(a0, a1) + 0.08;
                Vec3 hand = g.hand(i, 0), handWas = g.hand(i, -1.2F);
                Vec3 motion = hand.subtract(handWas).multiply(1, 0, 1);
                motion = motion.lengthSqr() < 1e-4 ? rt : motion.normalize();
                if ((int) at == SpiritGiantEntity.SWIPE_FROM + 4) {
                    level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.4F);
                    PaponNetwork.shake(level, hand, 90, 0.7F, 14);
                }
                if ((int) at % 2 == 0) Fx.dust(level, new Vec3(hand.x, floor, hand.z), 4.0, 10);
                for (LivingEntity e : victims(level, arena.a + 6)) {
                    if (e.getY() > floor + 6 || swipeHit.contains(e.getId())) continue;
                    Vec3 rel = e.position().subtract(base);
                    double fc = rel.dot(f), rc = rel.dot(rt);
                    double dist = Math.sqrt(fc * fc + rc * rc), ang = Math.atan2(-rc, fc);
                    if (Math.abs(dist - r) > 5.2 + e.getBbWidth() / 2 || ang < lo || ang > hi) continue;
                    swipeHit.add(e.getId());
                    if (deadHit(e, 0.2F)) {
                        Vec3 out = rel.multiply(1, 0, 1).normalize();
                        fling(e, motion.scale(2.2).add(out.scale(0.9)).normalize().scale(2.7).add(0, 0.55, 0));
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 3.0F, 0.4F);
                        Fx.hit(level, e.position().add(0, 1, 0), 2.6F, GIANT_BLUE);
                        if (random.nextInt(2) == 0) quip("p4_gigante_swat", 1F);
                    }
                }
            }
        }
        // a hand down on you: where it lands, and a ring rolling out of it
        if ((act == SpiritGiantEntity.SLAM_L || act == SpiritGiantEntity.SLAM_R) && (int) at == SpiritGiantEntity.SLAM_HIT) {
            Vec3 mark = g.handMark(act == SpiritGiantEntity.SLAM_L ? 0 : 1);
            giantImpact(level, mark, 5.0, 0.24F);
            fusionRings.add(new double[]{mark.x, mark.z, level.getGameTime()});
            Fx.shock(level, new Vec3(mark.x, floor, mark.z), 20F, GIANT_BLUE);
        }
        if (act == SpiritGiantEntity.SLAM_BOTH && (int) at == SpiritGiantEntity.SLAM2_HIT) {
            Vec3 mid = g.handMark(0).lerp(g.handMark(1), 0.5);
            for (int i = 0; i < 2; i++) giantImpact(level, g.handMark(i), 5.5, 0.28F);
            long now = level.getGameTime();
            for (int k = 0; k < 3; k++) fusionRings.add(new double[]{mid.x, mid.z, now + k * 7});
            Fx.shock(level, new Vec3(mid.x, floor, mid.z), 22F, GIANT_BLUE);
            crowdReact(level, CrowdMood.GASP, 30);
        }
    }

    private void giantImpact(ServerLevel level, Vec3 mark, double r, float share) {
        double floor = arena.floorY();
        Vec3 at = new Vec3(mark.x, floor, mark.z);
        Fx.shock(level, at, (float) r + 1.5F, GIANT_BLUE);
        Fx.scorch(level, at, (float) r, 200);
        Fx.spark(level, at.add(0, 0.6, 0), 20, 1.6, 0.5F, 0x9FF4FF);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 1, 5, r + 1), this::hittable)) {
            if (e.distanceToSqr(at.x, e.getY(), at.z) > (r + e.getBbWidth() / 2) * (r + e.getBbWidth() / 2) || e.getY() > floor + 5) continue;
            if (deadHit(e, share)) {
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.setDeltaMovement(away.scale(1.2).add(0, 0.8, 0));
                e.hurtMarked = true;
                Mechanics.stun(e);
            }
        }
    }

    // ---- the serpents, hunting

    /**
     * Serpent {@code k} dives across the floor through where you will be ({@code turn} radians off its own line: a second one
     * crossing the first). From the storm on it leaves the floor burning behind it. Returns whether it went.
     */
    private boolean serpentDive(ServerLevel level, int k, LivingEntity target, double turn, boolean burn) {
        SkySerpentEntity sp = serpent(level, k);
        if (sp == null || sp.busy()) return false;
        double floor = deadFloor();
        Vec3 aim = inSand(predict(target, 50), 2, 0, 0);
        Vec3 head = sp.path().at(sp.head(0) + 70 * SkySerpentEntity.SPEED);
        Vec3 in = aim.subtract(new Vec3(head.x, aim.y, head.z)).multiply(1, 0, 1);
        in = in.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : in.normalize();
        if (turn != 0) in = new Vec3(in.x * Math.cos(turn) - in.z * Math.sin(turn), 0, in.x * Math.sin(turn) + in.z * Math.cos(turn));
        double reach = inColiseum() && arena != null ? Math.max(arena.a, arena.b) * 0.95 : 26;
        Vec3 a = new Vec3(aim.x, floor + 2.6, aim.z).subtract(in.scale(reach));
        Vec3 b = new Vec3(aim.x, floor + 2.6, aim.z).add(in.scale(reach));
        int arrive = sp.dive(a, b, 6);
        int len = (int) Math.ceil((a.distanceTo(b) + SkySerpentEntity.HEAD + SkySerpentEntity.SEGMENT * (SkySerpentEntity.SEGMENTS + 1))
                / SkySerpentEntity.SPEED);
        Fx.warnLine(level, new Vec3(a.x, floor, a.z), new Vec3(b.x, floor, b.z), 9F, JADE, arrive + 10);
        long now = level.getGameTime();
        serpentDives.add(new SerpentDive(sp.getUUID(), now + arrive + len, new java.util.HashSet<>()));
        if (burn) {
            double line = a.distanceTo(b);
            trails.add(new double[]{a.x, a.z, b.x, b.z, now + arrive, SkySerpentEntity.SPEED, now + arrive + line / SkySerpentEntity.SPEED + 80 + 20 * p4StageReached});
        }
        sp.roar();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.5F);
        return true;
    }

    /**
     * The serpents on their own (from the storm on, or dives owed by one of his moves: {@code serpentQueue}): a free one dives
     * through where you are going every few seconds, more often at the end of the world, and there now and then both at once,
     * crossing on you. Not while they are his to command (El Ouroboros, the fusion, El Juicio Final) or in the dance-off.
     */
    private void tickSerpentLayer(ServerLevel level, @Nullable LivingEntity target) {
        if (!muertosFight() || target == null) return;
        BossState s = getBossState();
        if (s.phaseFour() || s == BossState.P4_OUROBOROS || s == BossState.P4_FUSION || s == BossState.P4_JUICIO || s == BossState.P4_RAGE
                || s == BossState.P4_TANGO || s == BossState.P4_TANGO_WIN || s == BossState.P4_TANGO_LOSE || s == BossState.DEATH) return;
        if (serpentLayerCd > 0) {
            serpentLayerCd--;
            return;
        }
        int st = p4StageReached;
        if (st < 2 && serpentQueue <= 0) return;
        if (s == BossState.WINDED && st < 3 && serpentQueue <= 0) return;
        int k = serpentTurn++ % 2;
        boolean went = serpentDive(level, k, target, 0, st >= 2) || serpentDive(level, 1 - k, target, 0, st >= 2);
        if (went && st >= 3 && serpentQueue <= 0 && random.nextInt(3) == 0 && serpentDive(level, 1 - k, target, Math.PI / 2, true)) {
            crowdReact(level, CrowdMood.GASP, 30);                  // both: the second across the first
        }
        if (!went) {
            serpentLayerCd = 10;
            return;
        }
        if (serpentQueue > 0) serpentQueue--;
        serpentLayerCd = serpentQueue > 0 ? 34 : st >= 3 ? 90 + random.nextInt(50) : 170 + random.nextInt(80);
    }

    /** The fire a dive leaves behind: it catches along the line as the head passes, and burns (6%, alight) until it dies down. */
    private void tickTrails(ServerLevel level) {
        if (trails.isEmpty()) return;
        long now = level.getGameTime();
        trails.removeIf(tr -> now > (long) tr[6]);
        double floor = deadFloor();
        for (double[] tr : trails) {
            if (now < (long) tr[4]) continue;
            double ax = tr[0], az = tr[1], bx = tr[2], bz = tr[3];
            double len = Math.max(1, Math.hypot(bx - ax, bz - az));
            double lit = Math.min(len, (now - tr[4]) * tr[5]);
            double out = Math.max(0, (now - (tr[6] - 60)) * tr[5] * 0.7);          // dying down from its start at the end
            if (lit - out < 0.5) continue;
            for (int i = 0; i < 4; i++) {
                double u = (out + random.nextDouble() * (lit - out)) / len;
                Vec3 p = new Vec3(ax + (bx - ax) * u, floor, az + (bz - az) * u);
                Fx.jet(level, p.add((random.nextDouble() - 0.5) * 1.6, 0.2, (random.nextDouble() - 0.5) * 1.6), new Vec3(0, 0.12 + random.nextDouble() * 0.1, 0),
                        1.6F + random.nextFloat(), JADE);
                if (i == 0 && now % 2 == 0) Fx.scorch(level, p, 1.8F, 60);
            }
            if (now % 3 == 0) {
                double u = (out + random.nextDouble() * (lit - out)) / len;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ax + (bx - ax) * u, floor + 0.4, az + (bz - az) * u, 4, 0.6, 0.2, 0.6, 0.01);
            }
            for (LivingEntity e : victims(level, Math.max(fa(), fb()) + 4)) {
                if (e.getY() > floor + 1.2) continue;
                double ex = e.getX() - ax, ez = e.getZ() - az;
                double u = (ex * (bx - ax) + ez * (bz - az)) / (len * len);
                if (u < 0 || u > 1 || u * len < out || u * len > lit) continue;
                double px = ax + (bx - ax) * u - e.getX(), pz = az + (bz - az) * u - e.getZ();
                if (px * px + pz * pz > Mth.square(1.7 + e.getBbWidth() / 2)) continue;
                Long last = trailHit.get(e.getId());
                if (last != null && now - last < 16) continue;
                if (deadHit(e, 0.06F)) {
                    trailHit.put(e.getId(), now);
                    e.setSecondsOnFire(3);
                }
            }
        }
    }

    // ---- the end of the world

    /**
     * Losing control (the end of the world, between and under his moves): every few seconds the dead burst up out of the floor
     * round you on their own, rings of jade and marigold fire (9% each, a beat to get out), and he cries out.
     */
    private void tickChaos(ServerLevel level, @Nullable LivingEntity target) {
        if (!muertosFight() || p4StageReached < 3 || target == null) return;
        BossState s = getBossState();
        if (s.phaseFour() || s == BossState.P4_JUICIO || s == BossState.P4_RAGE || s == BossState.P4_TANGO || s == BossState.P4_TANGO_WIN
                || s == BossState.P4_TANGO_LOSE || s == BossState.DEATH) return;
        if (--chaosCd > 0) return;
        chaosCd = 120 + random.nextInt(70);
        double floor = deadFloor();
        int n = 4 + random.nextInt(3);
        double a0 = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = a0 + i * Math.PI * 2 / n;
            double r = i == 0 ? 0 : 3.5 + random.nextDouble() * 4;
            Vec3 at = inSand(predict(target, 14).add(Math.cos(a) * r, 0, Math.sin(a) * r), 1.5, 0, 0);
            fiestaStrike(level, new Vec3(at.x, floor, at.z), 22 + i * 3, 2.4, 0.09F, i % 2 == 0 ? JADE : 0xFF8A1E);
        }
        PaponNetwork.shake(level, target.position(), 30, 0.25F, 10);
        if (random.nextInt(3) == 0) quip("p4_chaos" + random.nextInt(2), 1F);
    }

    /** The ring breaths of El Ouroboros (and El Juicio Final): each serpent's head on its ring breathes in these windows. */
    private void tickRingBreaths(ServerLevel level, int t, int[][] windows) {
        Vec3 c = p4Center();
        double floor = deadFloor();
        for (int k = 0; k < 2; k++) {
            SkySerpentEntity sp = serpent(level, k);
            int[] ring = ouroRing[k];
            if (sp == null || ring == null) continue;
            int on = t - ring[0];
            boolean aim = false, fire = false;
            for (int[] w : windows) {
                aim |= on >= w[0] && on < w[1];
                fire |= on >= w[1] && on < w[2];
            }
            if (t > ring[1] - 6) aim = fire = false;
            Vec3 head = sp.headAt(0);
            Vec3 from = new Vec3(head.x, floor + 0.55, head.z);
            Vec3 in = new Vec3(c.x - head.x, 0, c.z - head.z);
            Vec3 to = from.add(in.normalize().scale(in.length() + 8));
            BeamEntity b = ouroBeam[k] != null && level.getEntity(ouroBeam[k]) instanceof BeamEntity be && be.isAlive() ? be : null;
            if (aim || fire) {
                if (b == null) {
                    b = BeamEntity.create(level, from, to, 0.35F, JADE).life(600);
                    level.addFreshEntity(b);
                    ouroBeam[k] = b.getUUID();
                }
                b.set(from, to);
                b.setMode(fire ? BeamEntity.FIRE : BeamEntity.AIM);
                b.setWidth(fire ? 1.5F : 0.35F);
                for (int[] w : windows) {
                    if (on == w[1]) {
                        sp.roar();
                        level.playSound(null, head.x, head.y, head.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 6.0F, 0.5F);
                        level.playSound(null, head.x, head.y, head.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 4.0F, 0.4F);
                    }
                }
                if (fire) {
                    if (t % 3 == 0) {
                        Vec3 p = from.lerp(to, random.nextDouble());
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.3, p.z, 4, 0.4, 0.3, 0.4, 0.02);
                        Fx.scorch(level, new Vec3(p.x, floor, p.z), 1.2F, 120);
                    }
                    for (LivingEntity e : victims(level, Math.max(fa(), fb()) + 4)) {
                        if (e.getY() > floor + 1.05) continue;                  // jumped it
                        double dd = segDist(new Vec3(e.getX(), floor + 0.55, e.getZ()), from, to);
                        if (dd > 0.9 + e.getBbWidth() / 2) continue;
                        Integer last = breathHit.get(e.getId());
                        if (last != null && t - last < 14) continue;
                        if (deadHit(e, 0.12F)) {
                            breathHit.put(e.getId(), t);
                            e.setDeltaMovement(sp.heading(0).multiply(1, 0, 1).scale(0.9).add(0, 0.6, 0));
                            e.hurtMarked = true;
                            e.setSecondsOnFire(3);
                        }
                    }
                }
            } else if (b != null) {
                b.discard();
                ouroBeam[k] = null;
            }
        }
    }

    /** Both serpents down onto the ring round the floor for about {@code ticks} (from tick {@code t} of the move); false if they
     *  could not go. Sets ouroRing and ouroEnd. */
    private boolean closeRing(ServerLevel level, int t, int ticks) {
        Vec3 c = p4Center();
        double a = fa() - 5.5, b = fb() - 5.5;
        double per = Math.PI * (3 * (a + b) - Math.sqrt((3 * a + b) * (a + 3 * b)));
        double laps = ticks * SkySerpentEntity.SPEED / per;
        double ang0 = random.nextDouble() * Math.PI * 2;
        int dir = random.nextBoolean() ? 1 : -1;
        int[][] got = new int[2][];
        ouroEnd = 0;
        for (int k = 0; k < 2; k++) {
            SkySerpentEntity sp = serpent(level, k);
            ouroRing[k] = null;
            if (sp != null) got[k] = sp.ring(c, a, b, 2.6, ang0 + k * Math.PI, dir, laps, 8);
        }
        // both onto the ring together: the one that would be early leaves its orbit later
        if (got[0] != null && got[1] != null && got[0][0] != got[1][0]) {
            int late = got[0][0] > got[1][0] ? 0 : 1, early = 1 - late;
            got[early] = serpent(level, early).ring(c, a, b, 2.6, ang0 + early * Math.PI, dir, laps,
                    8 + (got[late][0] - got[early][0]) * SkySerpentEntity.SPEED);
        }
        for (int k = 0; k < 2; k++) {
            if (got[k] == null) continue;
            SkySerpentEntity sp = serpent(level, k);
            ouroRing[k] = new int[]{t + got[k][0], t + got[k][0] + got[k][1]};
            ouroEnd = Math.max(ouroEnd, ouroRing[k][1] + 30);
            long len = got[k][0] + got[k][1] + (long) Math.ceil((SkySerpentEntity.HEAD + SkySerpentEntity.SEGMENT
                    * (SkySerpentEntity.SEGMENTS + 1)) / SkySerpentEntity.SPEED);
            serpentDives.add(new SerpentDive(sp.getUUID(), level.getGameTime() + len, new java.util.HashSet<>()));
            sp.roar();
        }
        if (ouroEnd == 0) return false;
        double floor = deadFloor();
        for (int i = 0; i < 56; i++) {
            double th = i * Math.PI * 2 / 56;
            Fx.warn(level, new Vec3(c.x + Math.cos(th) * a, floor, c.z + Math.sin(th) * b), 3.8F, JADE, 50);
        }
        return true;
    }

    /** The ring's breath windows (ticks from its head on the ring: aim, fire, stop): more, and closer together, by stage. */
    private static int[][] ringWindows(int stage, int count) {
        int gap = stage >= 3 ? 64 : stage == 2 ? 78 : 90;
        int fire = stage >= 3 ? 52 : 56;
        int[][] w = new int[count][];
        for (int i = 0; i < count; i++) {
            int s0 = 8 + i * gap;
            w[i] = new int[]{s0, s0 + 14, s0 + 14 + fire};
        }
        return w;
    }

    /**
     * El Juicio Final (25 s; the climax: it opens the end of the world and comes back now and then after). "Then let the whole
     * world burn with me!" He hangs high over the middle, arms out, the power tearing through him. Quetzalcoatl and Kukulcan
     * come down and wall the whole floor in, breathing jade fire across it again and again (jump it); El Gigante roars, sweeps
     * its lasers across the floor and after you, slams both hands down (rings: jump them), swipes; he looses arrows of the sun
     * onto rings round you, calls the moon down, and the floor erupts in a spiral of the dead's fire. Last, everything goes red:
     * only the marigold circles that bloom across the floor (2.5 s, one near each of you) survive the blast. Then he falls out of
     * the sky, spent (a long winded window).
     */
    private void tickP4Juicio(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (arena == null || !inColiseum()) {
            setBossState(BossState.IDLE);
            return;
        }
        Vec3 c = p4Center();
        double floor = deadFloor();
        int end = BossState.P4_JUICIO.duration;
        if (t < end - 24) flyTo(new Vec3(c.x, floor + 18, c.z), 0.9);
        else flyTo(new Vec3(c.x, floor + 1, c.z), 1.4);
        if (target != null) turnToward(target.position(), 4F);
        SpiritGiantEntity g = giant(level);
        if (t == 1) {
            say("p4_juicio", 80);
            breathHit.clear();
            juicioSafe.clear();
            cine(level, ModSounds.CINE_SWELL.get(), c, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 8.0F, 0.5F);
            crowdReact(level, CrowdMood.GASP, 80);
            PaponNetwork.shake(level, c, 160, 0.6F, 40);
            if (g == null) g = summonGiant(level, target);
            closeRing(level, t, 400);
        }
        if (t == 30 && g != null && g.act() == SpiritGiantEntity.IDLE) g.act(SpiritGiantEntity.ROAR);
        if (g != null) g.keep(400);
        // the serpents' breaths across the floor from the ring, five times
        tickRingBreaths(level, t, ringWindows(3, 5));
        // El Gigante
        if (g != null && target != null) {
            if (t == 90 || t == 330) giantLaser(g, 22, t == 90 ? 70 : 60, t == 90 ? GIANT_SWEEP : GIANT_TRACK);
            if ((t == 200 || t == 400) && g.act() == SpiritGiantEntity.IDLE) giantSlam(level, g, target, true);
            if (t == 262 && g.act() == SpiritGiantEntity.IDLE) giantSwipe(g, target, random.nextBoolean() ? SpiritGiantEntity.SWIPE_L : SpiritGiantEntity.SWIPE_R);
        }
        // his arrows, the moon
        if (target != null && (t == 60 || t == 140 || t == 236 || t == 300)) {
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 3.0F, 0.5F);
            BeamEntity up = BeamEntity.create(level, position().add(0, getBbHeight() * 0.7, 0), position().add(0, 60, 0), 0.5F, JADE).life(6);
            up.setMode(BeamEntity.FIRE);
            level.addFreshEntity(up);
            for (LivingEntity v : victims(level, fa() + 20)) {
                if (!(v instanceof Player)) continue;
                for (int i = 0; i < 3; i++) {
                    Vec3 at = i == 0 ? v.position() : predict(v, 10 + i * 6).add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4);
                    at = inSand(at, 1.5, 0, 0);
                    arrowStrike(level, new Vec3(at.x, floor, at.z), 22, 0.12F);
                }
            }
        }
        if (target != null && t == 176) {
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * fa() * 0.8;
                Vec3 at = inSand(c.add(Math.cos(a) * r, 0, Math.sin(a) * r * 0.72), 1.5, 0, 0);
                moonStrike(level, new Vec3(at.x, floor, at.z), 18, 0.14F);
            }
            moonStrike(level, new Vec3(target.getX(), floor, target.getZ()), 18, 0.14F);
            level.playSound(null, getX(), getY() + 10, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        // the floor erupting in a spiral out from under him
        if (t >= 350 && t < 420 && t % 3 == 0) {
            int i = (t - 350) / 3;
            double a = i * 0.62, r = 3 + i * 1.35;
            for (int arm = 0; arm < 3; arm++) {
                double aa = a + arm * Math.PI * 2 / 3;
                Vec3 at = inSand(c.add(Math.cos(aa) * r, 0, Math.sin(aa) * r * 0.72), 1.5, 0, 0);
                fiestaStrike(level, new Vec3(at.x, floor, at.z), 16, 2.2, 0.09F, arm == 0 ? JADE : arm == 1 ? 0xFF8A1E : 0xFF3AA0);
            }
        }
        // the last blast: the floor red, and the marigold circles that survive it
        int bloom = end - 80, blast = end - 30;
        if (t == bloom) {
            juicioSafe.clear();
            for (LivingEntity v : victims(level, fa() + 20)) {
                if (!(v instanceof Player)) continue;
                double a = random.nextDouble() * Math.PI * 2;
                double r = 7 + random.nextDouble() * 4;
                Vec3 s = inSand(v.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), 4, 0, 0);
                juicioSafe.add(new Vec3(s.x, floor, s.z));
            }
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + random.nextDouble() * 0.6, r = fa() * (0.35 + random.nextDouble() * 0.3);
                Vec3 s = inSand(c.add(Math.cos(a) * r, 0, Math.sin(a) * r * 0.72), 4, 0, 0);
                juicioSafe.add(new Vec3(s.x, floor, s.z));
            }
            Fx.zone(level, new Vec3(c.x, floor, c.z), (float) Math.max(fa(), fb()), 0xFF2A2A, blast - bloom + 4, 0.55F);
            for (Vec3 s : juicioSafe) {
                Fx.zone(level, s, 3.4F, 0xFFB020, blast - bloom + 4, 0.9F);
                Fx.sigil(level, s, 3.6F, 0xFFD040, blast - bloom + 4);
            }
            level.playSound(null, c.x, floor + 4, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 6.0F, 0.4F);
            cine(level, ModSounds.CINE_SWELL.get(), c, 0.9F);
        }
        if (t > bloom && t < blast) {
            if (t % 4 == 0) {
                for (Vec3 s : juicioSafe) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Fx.jet(level, s.add(Math.cos(a) * 3.2, 0.2, Math.sin(a) * 3.2), new Vec3(0, 0.2, 0), 1.6F, 0xFFB020);
                }
            }
            if (t % 5 == 0) PaponNetwork.shake(level, c, 140, 0.15F + 0.5F * (t - bloom) / (blast - bloom), 6);
        }
        if (t == blast) {
            Fx.flash(level, new Vec3(c.x, floor + 6, c.z), 30F);
            Fx.shock(level, new Vec3(c.x, floor, c.z), (float) Math.max(fa(), fb()) + 6, 0xFF3A3A);
            level.playSound(null, c.x, floor + 4, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 10.0F, 0.4F);
            level.playSound(null, c.x, floor + 4, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0F, 0.5F);
            PaponNetwork.shake(level, c, 160, 1.2F, 40);
            for (int i = 0; i < 40; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * fa();
                Fx.jet(level, new Vec3(c.x + Math.cos(a) * r, floor + 0.3, c.z + Math.sin(a) * r * 0.72),
                        new Vec3(0, 0.5 + random.nextDouble() * 0.5, 0), 3.5F, random.nextBoolean() ? 0xFF3A3A : JADE);
            }
            for (LivingEntity e : victims(level, Math.max(fa(), fb()) + 6)) {
                boolean safe = false;
                for (Vec3 s : juicioSafe) safe |= e.distanceToSqr(s.x, e.getY(), s.z) < Mth.square(3.4 + e.getBbWidth() / 2);
                if (safe) continue;
                if (deadHit(e, 0.32F)) {
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 1.0, 0));
                    e.hurtMarked = true;
                    e.setSecondsOnFire(4);
                }
            }
            if (g != null && g.act() == SpiritGiantEntity.IDLE) g.act(SpiritGiantEntity.ROAR);
            for (int k = 0; k < 2; k++) {
                SkySerpentEntity sp = serpent(level, k);
                if (sp != null) sp.roar();
            }
            crowdReact(level, CrowdMood.ROAR, 60);
            juicioSafe.clear();
        }
        if (t == end - 4) {
            Fx.land(level, new Vec3(getX(), floor, getZ()), 4F);
            PaponNetwork.shake(level, position(), 60, 0.8F, 14);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (t >= end) {
            dJuicioCd = 1500;
            juicioWinded = 110;
            if (target != null && target.isAlive()) quip("p4_juicio_end", 1F);
            setBossState(BossState.IDLE);
        }
    }

    // ---- La Pista de los Muertos

    private final java.util.Set<Long> pistaHits = new java.util.HashSet<>();

    /**
     * La Pista de los Muertos (about 9 s): "The floor is yours, amigo!" He drops into the middle of the floor and it lights up
     * under everyone, a dance floor of coloured tiles pulsing on the band's beat. He dances salsa in the middle while, measure
     * after measure, a pattern of tiles flashes in one colour (checks, rings, stripes, diagonals) and then erupts in spirit
     * fire (14% and thrown up); the warning is two beats at first, then one, the last three measures on every beat.
     */
    private void tickP4Pista(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        if (t == 1) {
            entityData.set(DATA_P4SEED, random.nextInt(1 << 20));
            entityData.set(DATA_P4PROP, 0);
            pistaHits.clear();
            say("p4_pista", 50);
            crowdReact(level, CrowdMood.CHEER, 60);
        }
        Vec3 spot = new Vec3(c.x, floor, c.z);
        if (horizontalDist(spot) > 0.6 || getY() > floor + 0.4) flyTo(spot, 1.4);
        else {
            setFlying(false);
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (target != null) turnToward(target.position(), 4F);
        int seed = p4Seed();
        double a = fa(), b = fb();
        if (t == Fiesta.PISTA_LEAD) {
            level.playSound(null, c.x, floor + 2, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 5.0F, 1.4F);
            PaponNetwork.shake(level, c, 90, 0.3F, 10);
        }
        int lastBeat = Fiesta.eruptBeat(Fiesta.PISTA_MEASURES - 1);
        for (int k = 0; k <= lastBeat; k++) {
            if (t == Fiesta.pistaBeat(k)) {
                level.playSound(null, c.x, floor + 1, c.z, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 3.0F, 0.7F);
                Fx.spark(level, position().add(0, 0.3, 0), 6, 0.6, 0.3F, Fiesta.colour(k));
            }
        }
        for (int m = 0; m < Fiesta.PISTA_MEASURES; m++) {
            int warn = Fiesta.pistaBeat(Fiesta.warnBeat(m)), erupt = Fiesta.pistaBeat(Fiesta.eruptBeat(m));
            if (t == warn) level.playSound(null, c.x, floor + 2, c.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 4.0F, 0.7F + m * 0.08F);
            if (t < erupt || t >= erupt + Fiesta.ERUPT_TICKS) continue;
            if (t == erupt) {
                level.playSound(null, c.x, floor + 2, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 5.0F, 0.6F);
                level.playSound(null, c.x, floor + 2, c.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 4.0F, 0.6F);
                PaponNetwork.shake(level, c, 100, 0.35F, 8);
                if (m % 3 == 2) crowdReact(level, CrowdMood.ROAR, 12);
            }
            for (LivingEntity e : victims(level, Math.max(a, b) + 4)) {
                if (e.getY() > floor + 4.5) continue;
                int i = Fiesta.cell(e.getX() - c.x), j = Fiesta.cell(e.getZ() - c.z);
                if (!Fiesta.tile(i, j, a, b) || !Fiesta.pistaLit(seed, m, i, j)) continue;
                if (!pistaHits.add(((long) e.getId() << 8) | m)) continue;
                if (deadHit(e, 0.14F)) {
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.4, 0, 0.4).add(0, 0.85, 0));
                    e.hurtMarked = true;
                    e.setSecondsOnFire(2);
                }
            }
        }
        if (t >= Fiesta.pistaBeat(lastBeat) + 22) setBossState(BossState.IDLE);
    }

    // ---- El Camino de Cempasúchil

    private int caminoNext;

    /**
     * El Camino de Cempasúchil (about 12 s): "Follow the marigolds, amigo..." A path of marigold petals blooms across the
     * floor from one end to the other, winding (the flowers that guide the souls home); then the ectoplasm wells up out of
     * the cracks and floods everything else (3.5% every quarter second and slowed, standing in it). The path wilts from its
     * start, so you walk it, and from over its far end he lobs sugar skulls onto it ahead of you.
     */
    private void tickP4Camino(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        double a = fa(), b = fb();
        if (t == 1) {
            entityData.set(DATA_P4SEED, random.nextInt(1 << 20));
            say("p4_cempasuchil", 60);
            caminoNext = Fiesta.CAMINO_FULL + 8;
        }
        double[][] path = Fiesta.camino(p4Seed(), a, b);
        double[] end = path[path.length - 1];
        flyTo(new Vec3(c.x + end[0] * 1.05, floor + 9, c.z + end[1]), 0.9);
        if (target != null) turnToward(target.position(), 6F);
        if (t == 2) level.playSound(null, c.x, floor + 2, c.z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.HOSTILE, 4.0F, 0.6F);
        if (t == Fiesta.CAMINO_FLOOD) {
            level.playSound(null, c.x, floor + 2, c.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 5.0F, 0.4F);
            level.playSound(null, c.x, floor + 2, c.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.HOSTILE, 5.0F, 0.6F);
            crowdReact(level, CrowdMood.GASP, 30);
        }
        if (t > Fiesta.CAMINO_FLOOD && t < Fiesta.CAMINO_DRAIN && t % 8 == 0) {
            double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble());
            level.playSound(null, c.x + Math.cos(ang) * a * r, floor, c.z + Math.sin(ang) * b * r, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (Fiesta.flood(t) >= 1 && t % 5 == 0) {
            for (LivingEntity e : victims(level, Math.max(a, b) + 4)) {
                if (e.getY() > floor + 2.0) continue;
                if (Fiesta.onCamino(path, e.getX() - c.x, e.getZ() - c.z, t)) continue;
                if (deadHit(e, 0.035F)) {
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                    level.sendParticles(ParticleTypes.SOUL, e.getX(), floor + 0.4, e.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
                }
            }
        }
        // sugar skulls onto the path ahead of whoever is on it
        if (t >= caminoNext && t < Fiesta.CAMINO_DRAIN - 24) {
            caminoNext = t + 24;
            int wilt = (int) Math.floor(Fiesta.wilted(t));
            for (LivingEntity v : victims(level, Math.max(a, b) + 4)) {
                if (!(v instanceof Player)) continue;
                int near = wilt;
                double best = Double.MAX_VALUE;
                for (int i = Math.max(0, wilt); i < path.length; i++) {
                    double dx = path[i][0] - (v.getX() - c.x), dz = path[i][1] - (v.getZ() - c.z);
                    double dd = dx * dx + dz * dz;
                    if (dd < best) {
                        best = dd;
                        near = i;
                    }
                }
                int at = Math.min(path.length - 1, near + 5 + random.nextInt(7));
                Vec3 to = new Vec3(c.x + path[at][0], floor, c.z + path[at][1]);
                int col = Fiesta.colour(random.nextInt(6));
                fiestaStrike(level, to, 24, 2.3, 0.12F, col);
                BossProp skull = BossProp.create(level, BossProp.Kind.CALAVERA, this, position().add(0, getBbHeight() * 0.7, 0)).toss(to.add(0, 0.5, 0), 24);
                skull.life(24);
                level.addFreshEntity(skull);
                level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 2.0F, 0.5F);
            }
        }
        if (t >= Fiesta.CAMINO_DRAIN + 18) setBossState(BossState.IDLE);
    }

    // ---- El Castillo

    private final java.util.Set<Long> sparkHits = new java.util.HashSet<>();

    /**
     * El Castillo (about 13 s): "Light the castillo!" A fiesta's fireworks tower rises out of the middle of the floor with him
     * on top of it; its wheels light one tier after another from the bottom, each spinning out arms of coloured sparks that
     * spiral across the floor (8% a spark, at chest height: weave through, or hop one), the tiers turning opposite ways; then
     * its crown shoots rockets up that come down onto rings all over the floor and round you; then the whole thing bursts.
     */
    private void tickP4Castillo(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        double a = fa(), b = fb();
        if (t == 1) {
            entityData.set(DATA_P4SEED, random.nextInt(360) + 360 * random.nextInt(1000));
            sparkHits.clear();
            say("p4_castillo", 50);
            level.playSound(null, c.x, floor, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 4.0F, 1.3F);
            PaponNetwork.shake(level, c, 100, 0.4F, 26);
        }
        double top = floor + Fiesta.tierHeight(Fiesta.CASTILLO_TIERS - 1) + 6.2;
        Vec3 perch = new Vec3(c.x, t < 26 ? floor + 2 + (top - floor - 2) * t / 26.0 : top, c.z);
        flyTo(perch, 1.2);
        if (target != null) turnToward(target.position(), 5F);
        int seed = p4Seed();
        for (int tier = 0; tier < Fiesta.CASTILLO_TIERS; tier++) {
            int s0 = Fiesta.tierStart(tier);
            if (t == s0 - 8) level.playSound(null, c.x, floor + Fiesta.tierHeight(tier), c.z, SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 3.0F, 1.2F);
            if (t == s0) {
                level.playSound(null, c.x, floor + Fiesta.tierHeight(tier), c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 5.0F, 0.7F);
                level.playSound(null, c.x, floor + Fiesta.tierHeight(tier), c.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 4.0F, 0.5F);
                crowdReact(level, CrowdMood.CHEER, 20);
            }
            if (t > s0 && t < s0 + Fiesta.TIER_RUN && t % 5 == 0) {
                level.playSound(null, c.x, floor + Fiesta.tierHeight(tier), c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.HOSTILE, 2.0F,
                        0.8F + tier * 0.15F);
            }
        }
        if (t >= Fiesta.TIER_START) {
            List<double[]> sp = Fiesta.sparks(seed, t, a, b);
            for (LivingEntity e : victims(level, Math.max(a, b) + 4)) {
                if (e.getY() > floor + Fiesta.SPARK_Y + 0.15 || e.getY() + e.getBbHeight() < floor + Fiesta.SPARK_Y - 0.2) continue;
                double ex = e.getX() - c.x, ez = e.getZ() - c.z;
                double rr = Fiesta.SPARK_HIT + e.getBbWidth() / 2;
                for (double[] s : sp) {
                    double dx = s[0] - ex, dz = s[1] - ez;
                    if (dx * dx + dz * dz > rr * rr) continue;
                    if (!sparkHits.add(((long) e.getId() << 32) | (long) s[3])) continue;
                    if (deadHit(e, 0.08F)) {
                        e.setSecondsOnFire(2);
                        Vec3 out = new Vec3(ex, 0, ez).normalize();
                        e.setDeltaMovement(e.getDeltaMovement().add(out.scale(0.35)).add(0, 0.25, 0));
                        e.hurtMarked = true;
                    }
                    break;
                }
            }
        }
        if (t == Fiesta.CROWN) {
            // the crown: rockets up, raining down onto rings round you and all over
            level.playSound(null, c.x, top, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 6.0F, 0.5F);
            level.playSound(null, c.x, top, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 1.4F);
            Vec3 crown = new Vec3(c.x, top + 1, c.z);
            int i = 0;
            for (LivingEntity v : victims(level, Math.max(a, b) + 4)) {
                if (!(v instanceof Player)) continue;
                for (int k = 0; k < 3; k++) {
                    Vec3 at = inSand(k == 0 ? v.position() : predict(v, 14 + k * 8).add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4), 2, 0, 0);
                    castilloRocket(level, crown, new Vec3(at.x, floor, at.z), 30 + i++ * 3);
                }
            }
            for (int k = 0; k < 14; k++) {
                double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 0.9;
                Vec3 at = inSand(new Vec3(c.x + Math.cos(ang) * a * r, floor, c.z + Math.sin(ang) * b * r), 2, 0, 0);
                castilloRocket(level, crown, new Vec3(at.x, floor, at.z), 26 + k * 2);
            }
        }
        if (t == Fiesta.BURST) {
            Vec3 at = new Vec3(c.x, floor + Fiesta.tierHeight(2), c.z);
            Fx.flash(level, at, 8F);
            for (int k = 0; k < 6; k++) Fx.spark(level, at.add(0, k * 2, 0), 30, 4.0, 0.6F, Fiesta.colour(k));
            Fx.shock(level, new Vec3(c.x, floor, c.z), 18F, 0xFF9618);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.HOSTILE, 8.0F, 0.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 5.0F, 0.6F);
            PaponNetwork.shake(level, at, 120, 0.8F, 24);
            crowdRoar(level, 50);
            salvo(level, 6);
        }
        if (t >= Fiesta.BURST + 22) setBossState(BossState.IDLE);
    }

    private void castilloRocket(ServerLevel level, Vec3 from, Vec3 to, int ticks) {
        int col = Fiesta.colour(random.nextInt(6));
        fiestaStrike(level, to, ticks, 2.8, 0.15F, col);
        level.addFreshEntity(BossProp.create(level, BossProp.Kind.CANDY, this, from).toss(to.add(0, 0.2, 0), ticks));
    }

    // ---- Salsa Caliente

    @Nullable private Vec3 salsaFrom, salsaTo;
    private int salsaDash;

    /**
     * Salsa Caliente (about 7.5 s, close): "Uno, dos, tres... and turn!" He dances at you on the band's beat: forward up to
     * you, back, forward; every fourth beat a cross-body lead that whips him past you to your other side. Every step leaves a
     * footprint of coloured fire behind him that bursts on the next beat; on the 8th and 16th beat a spin kick, a ring round
     * him at ankle height (jump it). He is open the whole time: dance with him and hit him.
     */
    private void tickP4Salsa(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        if (t == 1) {
            say("p4_salsa", 40);
            entityData.set(DATA_P4PROP, 0);
            salsaTo = null;
            salsaDash = 99;
        }
        if (target == null) {
            if (t > 10) setBossState(BossState.IDLE);
            return;
        }
        if (getY() > floor + 0.4 && salsaDash >= 3) flyTo(new Vec3(getX(), floor, getZ()), 1.2);
        else setFlying(false);
        face(target.position());
        double k0 = (t - 8) / Fiesta.BEAT, k1 = (t - 9) / Fiesta.BEAT;
        int beat = (int) Math.floor(k0);
        if (t >= 8 && Math.floor(k0) != Math.floor(k1) && beat < 16) {
            Vec3 me = position();
            Vec3 f = flatTo(target.position());
            Vec3 side = new Vec3(-f.z, 0, f.x);
            int k = beat % 4;
            Vec3 step;
            if (k == 3) step = target.position().add(side.scale((beat / 4) % 2 == 0 ? 3.0 : -3.0)).add(f.scale(1.6));
            else if (k == 1) step = me.subtract(f.scale(1.4));
            else step = target.position().subtract(f.scale(2.2));
            step = inSand(new Vec3(step.x, floor, step.z), 2, 0, 0);
            salsaFrom = me;
            salsaTo = step;
            salsaDash = 0;
            fiestaStrike(level, new Vec3(me.x, floor, me.z), (int) Math.round(Fiesta.BEAT), 2.4, 0.12F, Fiesta.colour(beat));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 2.5F, 0.8F);
            level.playSound(null, getX(), getY() + 1, getZ(), k == 3 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.SKELETON_STEP, SoundSource.HOSTILE,
                    1.6F, 0.7F + 0.1F * k);
            if (beat == 7 || beat == 15) salsaSpin = t + 4;
        }
        if (salsaTo != null && salsaDash < 3) {
            salsaDash++;
            Vec3 p = salsaFrom.lerp(salsaTo, salsaDash / 3.0);
            setPos(p.x, p.y, p.z);
            setDeltaMovement(Vec3.ZERO);
            if (salsaDash == 3) Fx.dust(level, position(), 1.0, 4);
        }
        if (t == salsaSpin) {
            // the spin kick: a ring at ankle height
            Fx.shock(level, position(), 5F, Fiesta.colour(beat + 2));
            Fx.slash(level, position().add(0, 0.5, 0), getYRot(), 4.5F, Fiesta.colour(beat + 2));
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5F, 0.5F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.5, 2, 5.5), this::hittable)) {
                if (e.distanceToSqr(getX(), e.getY(), getZ()) > 5.0 * 5.0 || e.getY() > floor + 1.0) continue;
                if (deadHit(e, 0.15F)) {
                    e.setDeltaMovement(flatTo(e.position()).scale(1.3).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (t >= 8 + (int) Math.ceil(16 * Fiesta.BEAT) + 10) setBossState(BossState.IDLE);
    }

    private int salsaSpin = -1;

    // ---- El Último Tango (the dance-off, QTE kind 7)

    /** How long the dance-off runs (ticks): client/DanceOff charts the same length. */
    public static final int TANGO_TICKS = 440;
    @Nullable private UUID tangoWho;
    private int tangoResult = -1, tangoScore;

    /**
     * El Último Tango: "You and me, amigo. One last tango...!" He lands in front of you and the floor between you is a stage;
     * nobody moves but on the beat. The dance-off itself is the challenger's (client/DanceOff): he dances phrases, you dance
     * them back on the band's beat, the embrace (follow his hand with your mouse), the dip; the stands' applause decides. He
     * wins: P4_TANGO_WIN. You win: P4_TANGO_LOSE.
     */
    private void tickP4Tango(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        Entity we = tangoWho != null ? level.getEntity(tangoWho) : null;
        LivingEntity who = we instanceof LivingEntity le && le.isAlive() ? le : null;
        if (t == 1) {
            entityData.set(DATA_P4PROP, 0);
            tangoResult = -1;
            tangoScore = 0;
            qteSaid = 0;
            say("p4_tango", 60);
            crowdReact(level, CrowdMood.CHEER, 40);
        }
        if (who == null) {
            if (t > 5) setBossState(BossState.IDLE);
            return;
        }
        Vec3 dir = who.position().subtract(position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : dir.normalize();
        Vec3 spot = inSand(who.position().subtract(dir.scale(5.5)), 2, 0, 0);
        if (t < 28) flyTo(new Vec3(spot.x, floor, spot.z), 1.6);
        else {
            setFlying(false);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (t == 28) {
                Fx.shock(level, position(), 4F, 0xEC2E8C);
                Fx.dust(level, position(), 1.6, 10);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.9F);
            }
        }
        face(who.position());
        if (t == 30) {
            entityData.set(DATA_METER, 0.5F);
            if (who instanceof ServerPlayer sp) {
                PaponNetwork.qteStart(sp, getId(), 7, new int[]{random.nextInt(1 << 20), TANGO_TICKS, songId}, TANGO_TICKS);
                Mechanics.root(sp, TANGO_TICKS + 60);
            }
            crowdMood(level, CrowdMood.CLAP);
        }
        if (who instanceof Player p && t % 20 == 0) Mechanics.root(p, 40);
        if (t > 30 && t % 9 == 0) level.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.3, getZ(), 1, 0.4, 0.2, 0.4, 1.0);
        boolean gone = !(who instanceof Player) && t > 40;
        if (t > 30 && (tangoResult >= 0 || gone || t > 30 + TANGO_TICKS + 80)) {
            if (who instanceof ServerPlayer sp) PaponNetwork.qteEnd(sp, tangoResult == 1);
            if (tangoResult < 0) tangoScore = 0;
            setBossState(tangoResult == 1 ? BossState.P4_TANGO_LOSE : BossState.P4_TANGO_WIN);
        }
    }

    /** He won the dance: a dip, a long look, and he lets go (worse the worse you danced). */
    private void tickP4TangoWin(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Entity we = tangoWho != null ? level.getEntity(tangoWho) : null;
        LivingEntity who = we instanceof LivingEntity le && le.isAlive() ? le : null;
        if (who != null) face(who.position());
        if (t == 2) {
            say("p4_tango_won", 50);
            crowdReact(level, CrowdMood.ROAR, 40);
        }
        if (t == 8 && who != null && distanceTo(who) > 3.0) {
            Vec3 to = who.position().subtract(flatTo(who.position()).scale(2.0));
            setPos(to.x, getY(), to.z);
        }
        if (t == 30 && who != null && distanceTo(who) < 6) {
            if (Mechanics.trueHit(who, 0.28F + 0.3F * (1 - tangoScore / 100F), this, true)) {
                swingHit = true;
                Mechanics.stun(who);
            }
            Fx.shock(level, who.position(), 4F, 0xEC2E8C);
            Fx.dust(level, who.position(), 1.6, 14);
            level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.6F);
            PaponNetwork.shake(level, who.position(), 24, 0.8F, 14);
        }
        if (t >= BossState.P4_TANGO_WIN.duration) setBossState(BossState.IDLE);
    }

    /** You won the dance: the stands cheer you; he stands there flustered and dizzy, open to anything (double damage). */
    private void tickP4TangoLose(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (t == 2) {
            say("p4_tango_lost", 60);
            crowdRoar(level, 50);
            salvo(level, 4);
        }
        if (t % 10 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.2, getZ(), 4, 0.5, 0.1, 0.5, 0.05);
        if (t >= BossState.P4_TANGO_LOSE.duration) {
            think = 2;
            setBossState(BossState.IDLE);
        }
    }

    // ---- the swipes' victims: flung across the floor into the wall

    /** Whoever a swipe sent flying, and until when it counts if they hit the wall. */
    private final java.util.Map<UUID, Long> flung = new java.util.HashMap<>();

    private void fling(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        flung.put(e.getUUID(), level().getGameTime() + 40);
    }

    /** Each tick: anyone flung who reaches the edge of the floor is slammed into the wall (12% and stunned). */
    private void tickFlung(ServerLevel level) {
        if (flung.isEmpty()) return;
        long now = level.getGameTime();
        Vec3 c = p4Center();
        double a = fa(), b = fb();
        flung.entrySet().removeIf(en -> {
            if (now > en.getValue()) return true;
            if (!(level.getEntity(en.getKey()) instanceof LivingEntity e) || !e.isAlive()) return true;
            double x = e.getX() - c.x, z = e.getZ() - c.z;
            double k = (x * x) / (a * a) + (z * z) / (b * b);
            boolean wall = inColiseum() && arena != null ? k > 0.9 : e.horizontalCollision;
            if (!wall) return false;
            e.invulnerableTime = 0;
            if (deadHit(e, 0.12F)) Mechanics.stun(e);
            Vec3 at = e.position().add(0, 1, 0);
            Fx.hit(level, at, 2.4F, 0x40E2FF);
            Fx.chunks(level, at, 16, 0.35F, 0x2C2438, 0.6);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0F, 0.5F);
            if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.9F, 16);
            e.setDeltaMovement(e.getDeltaMovement().multiply(-0.2, 0.2, -0.2));
            e.hurtMarked = true;
            return true;
        });
    }

    // ---- La Grieta de las Almas

    /**
     * La Grieta de las Almas (10 s): he dives down in front of you and slams the floor; down on his knees, both hands into the
     * crack, and he tears it open: a rift across the floor through where you stand, two blocks deep and full of souls
     * (block/SoulVoidBlock). They reach out of it: the floor round it drags you in, and souls rise out of it and come for you
     * (a hand on you pulls you to the edge). He claps it shut at the end: anyone still in it, crushed.
     */
    private void tickP4Grieta(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        double floor = deadFloor();
        if (t == 1 && target != null) {
            say("p4_grieta", 50);
            Vec3 dir = flatTo(target.position());
            if (dir.lengthSqr() < 1e-4) dir = new Vec3(1, 0, 0);
            Vec3 land = target.position().subtract(dir.scale(5));
            land = inSand(land, 3, 0, 0);
            blinkTo = new Vec3(land.x, floor, land.z);
            riftA = blinkTo.add(dir.scale(-4));
            riftB = blinkTo.add(dir.scale(34));
            planRift(level, riftA, riftB);
            riftOpen = 0;
        }
        if (t < 14 && blinkTo != null) {
            noPhysics = true;
            setFlying(true);
            double k = t / 14.0;
            Vec3 from = position();
            Vec3 at = from.lerp(blinkTo, k * k);
            setPos(at.x, at.y, at.z);
            if (target != null) face(target.position());
        }
        if (t == 14 && blinkTo != null) {
            setFlying(false);
            noPhysics = false;
            setPos(blinkTo.x, blinkTo.y, blinkTo.z);
            Fx.shock(level, blinkTo, 7F, 0x9B4DFF);
            Fx.chunks(level, blinkTo.add(0, 0.4, 0), 16, 0.35F, 0x2C2438, 0.6);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.2F, 0.5F);
            PaponNetwork.shake(level, blinkTo, 40, 0.8F, 16);
            for (LivingEntity e : around(level, 4.0)) {
                if (deadHit(e, 0.12F)) {
                    e.setDeltaMovement(e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.8).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
        }
        if (riftA != null && riftB != null && t > 14 && t < 44) face(riftB);
        if (t > 20 && t < 44 && t % 6 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 2.0F, 0.4F);
            if (riftA != null && riftB != null) Fx.warnLine(level, riftA, riftB, 3.0F, 0x9B4DFF, 8);
        }
        if (t >= 44 && riftOpen < rift.size()) {
            // torn open: three cells a tick out from his hands
            if (t == 44) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0F, 0.6F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0F, 0.4F);
                PaponNetwork.shake(level, position(), 60, 1.0F, 30);
                crowdReact(level, CrowdMood.GASP, 40);
            }
            for (int k = 0; k < 9 && riftOpen < rift.size(); k++) {
                int[] cell = rift.get(riftOpen++);
                BlockPos top = new BlockPos(cell[0], (int) Math.floor(floor) - 1, cell[1]);
                level.setBlock(top.below(), com.groid.papon.registry.ModBlocks.SOUL_VOID.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(top, com.groid.papon.registry.ModBlocks.SOUL_VOID.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                if (k == 0) Fx.chunks(level, Vec3.atCenterOf(top).add(0, 1, 0), 3, 0.3F, 0x2C2438, 0.5);
            }
        }
        // the souls: out of the rift and after you
        if (t >= 60 && t <= 150 && t % 15 == 0 && riftA != null && riftB != null && souls.size() < 8) {
            Vec3 at = riftA.lerp(riftB, 0.15 + random.nextDouble() * 0.7);
            BossProp s = BossProp.create(level, BossProp.Kind.SOUL, this, new Vec3(at.x, floor + 0.3, at.z)).life(140);
            level.addFreshEntity(s);
            souls.add(s.getUUID());
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.5F + random.nextFloat() * 0.3F);
        }
        tickSouls(level);
        // the pull: the floor round it drags you toward it
        if (t > 50 && t < 186 && riftA != null && riftB != null) {
            for (LivingEntity e : victims(level, 80)) {
                Vec3 p = e.position();
                Vec3 ab = riftB.subtract(riftA).multiply(1, 0, 1);
                double k = Mth.clamp(p.subtract(riftA).multiply(1, 0, 1).dot(ab) / ab.lengthSqr(), 0, 1);
                Vec3 near = riftA.add(ab.scale(k));
                Vec3 to = near.subtract(p).multiply(1, 0, 1);
                double dist = to.length();
                if (dist < 0.6 || dist > 11) continue;
                double pull = 0.055 * (1 - dist / 11) + 0.012;
                e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(pull)));
                e.hurtMarked = true;
                if (t % 10 == 0 && e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.12F, 6);
            }
        }
        if (target != null && t > 46 && t < 180) turnToward(target.position(), 6F);
        if (t == 178) say("p4_cierra", 30);
        if (t == 186) {
            // he claps it shut
            for (int[] cell : rift) {
                BlockPos top = new BlockPos(cell[0], (int) Math.floor(floor) - 1, cell[1]);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(top).inflate(0.3, 2.5, 0.3), this::hittable)) {
                    if (e.getY() < floor + 0.2 && deadHit(e, 0.3F)) {
                        e.setDeltaMovement(0, 1.1, 0);
                        e.hurtMarked = true;
                    }
                }
            }
            closeRift(level, true);
        }
        if (t >= BossState.P4_GRIETA.duration) setBossState(BossState.IDLE);
    }

    /** The rift's cells, jagged, from a to b: three wide in the middle, narrowing to its ends; only the floor of the sand. */
    private void planRift(ServerLevel level, Vec3 a, Vec3 b) {
        rift.clear();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        Vec3 ab = b.subtract(a);
        double len = ab.horizontalDistance();
        Vec3 dir = ab.multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double floor = deadFloor();
        double wob = random.nextDouble() * 6;
        for (double s = 0; s <= len; s += 0.5) {
            double k = s / len;
            double half = 0.6 + 1.3 * Math.sin(k * Math.PI);
            double off = Math.sin(s * 0.45 + wob) * 1.2 + Math.sin(s * 1.3 + wob * 2) * 0.4;
            for (double w = -half; w <= half; w += 0.5) {
                Vec3 p = a.add(dir.scale(s)).add(side.scale(off + w));
                int x = Mth.floor(p.x), z = Mth.floor(p.z);
                if (arena != null && arena.edge(x + 0.5 - arena.centerVec().x, z + 0.5 - arena.centerVec().z) > -1.5) continue;
                if (!seen.add(Coliseum.pack(x, z))) continue;
                BlockPos top = new BlockPos(x, (int) Math.floor(floor) - 1, z);
                if (level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir()) continue;
                rift.add(new int[]{x, z});
            }
        }
    }

    /** The rift shut: the floor back as it was (and a puff of dust and souls), or just its blocks (cut short). */
    private void closeRift(ServerLevel level, boolean loud) {
        if (rift.isEmpty()) return;
        double floor = arena != null ? arena.floorY() : deadFloor();
        for (int i = 0; i < Math.min(riftOpen, rift.size()); i++) {
            int[] cell = rift.get(i);
            BlockPos top = new BlockPos(cell[0], (int) Math.floor(floor) - 1, cell[1]);
            BlockState want = arena != null ? arena.floorAt(cell[0] - arena.center.getX(), cell[1] - arena.center.getZ())
                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if (level.getBlockState(top.below()).is(com.groid.papon.registry.ModBlocks.SOUL_VOID.get())) {
                level.setBlock(top.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (level.getBlockState(top).is(com.groid.papon.registry.ModBlocks.SOUL_VOID.get())) level.setBlock(top, want, Block.UPDATE_CLIENTS);
            if (loud && i % 4 == 0) Fx.dust(level, Vec3.atCenterOf(top).add(0, 0.6, 0), 0.5, 2);
        }
        if (loud && riftA != null) {
            level.playSound(null, riftA.x, riftA.y, riftA.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 3.0F, 0.4F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.6F);
            PaponNetwork.shake(level, position(), 60, 0.9F, 18);
            for (int i = 0; i < rift.size(); i += 6) {
                int[] cell = rift.get(i);
                Fx.spark(level, new Vec3(cell[0] + 0.5, floor + 0.3, cell[1] + 0.5), 4, 0.4, 0.4F, 0x7FE8FF);
            }
        }
        rift.clear();
        riftOpen = 0;
    }

    /** The souls out of the rift: each drifts at the nearest of you; one that touches you drags you back to the edge. */
    private void tickSouls(ServerLevel level) {
        if (souls.isEmpty()) return;
        double floor = deadFloor();
        for (java.util.Iterator<UUID> it = souls.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!(level.getEntity(id) instanceof BossProp s) || !s.isAlive()) {
                it.remove();
                continue;
            }
            LivingEntity near = null;
            double best = 14 * 14;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(14), this::hittable)) {
                double d2 = e.distanceToSqr(s);
                if (d2 < best) {
                    best = d2;
                    near = e;
                }
            }
            Vec3 want = near != null ? near.position().add(0, 0.9, 0) : s.position().add(0, 0.02, 0);
            Vec3 d = want.subtract(s.position());
            double sp = s.tickCount < 20 ? 0.08 : 0.24;
            Vec3 v = d.length() < sp ? d : d.normalize().scale(sp);
            s.setPos(s.getX() + v.x, Math.max(floor + 0.2, s.getY() + v.y), s.getZ() + v.z);
            s.setDeltaMovement(v);
            s.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F);
            if (near != null && best < 1.3 * 1.3 && riftA != null && riftB != null) {
                // it has you: dragged to the rift
                Vec3 ab = riftB.subtract(riftA);
                double k = Mth.clamp(near.position().subtract(riftA).dot(ab) / ab.lengthSqr(), 0, 1);
                Vec3 to = riftA.add(ab.scale(k)).subtract(near.position()).multiply(1, 0, 1);
                deadHit(near, 0.05F);
                near.setDeltaMovement(to.normalize().scale(Math.min(1.1, to.length() * 0.35)).add(0, 0.25, 0));
                near.hurtMarked = true;
                level.playSound(null, near.getX(), near.getY(), near.getZ(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 1.6F, 0.5F);
                Fx.smoke(level, s.position(), 4, 0.4, 0.8F, 0x7FE8FF);
                s.discard();
                it.remove();
            }
        }
    }

    // ---- Lluvia de Cempasuchil

    /** The petals: arms, a petal per arm every PETAL_EVERY ticks, flying out at PETAL_SPEED, from t PETAL_FROM to PETAL_TO. */
    public static final int PETAL_ARMS = 6, PETAL_EVERY = 2, PETAL_FROM = 20, PETAL_TO = 150, PETAL_LIFE = 60;
    public static final double PETAL_SPEED = 0.75;

    /** The petal spirals' turn at tick {@code t} of the move (radians from the synced start angle): one way, then back faster. */
    public static double petalTurn(double t) {
        double turn = Math.toRadians(4);
        if (t < 85) return turn * t;
        return turn * 85 - Math.toRadians(7) * (t - 85);
    }

    /** Where a petal is (relative to the centre, flat): sent at {@code sent} on arm {@code arm}, now at {@code t}. */
    public static Vec3 petal(float base, int arm, int sent, double t) {
        double ang = base + petalTurn(sent) + arm * Math.PI * 2 / PETAL_ARMS + (arm % 2 == 0 ? 0 : 0.15);
        double r = 1.5 + (t - sent) * PETAL_SPEED;
        return new Vec3(Math.cos(ang) * r, 0, Math.sin(ang) * r);
    }

    /**
     * Lluvia de Cempasuchil (8.5 s): over the middle of the floor he spins, and out of him spiral six arms of marigold petals,
     * glowing, one way and then back the other faster, out across the whole floor; a petal through you takes a share. Keep
     * moving round between the arms. Both sides work the petals out the same way (petal()); the client draws them.
     */
    private void tickP4Petalos(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        flyTo(new Vec3(c.x, floor + 1.2, c.z), 1.0);
        if (t == 2) {
            say("p4_petalos", 50);
            entityData.set(DATA_P4ANGLE, (float) (random.nextDouble() * Math.PI * 2));
            petalHit.clear();
            crowdMood(level, CrowdMood.FIESTA);
        }
        setYRot(getYRot() + 24F);
        yBodyRot = getYRot();
        if (t >= PETAL_FROM && t % 6 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
        if (t >= PETAL_FROM && t < PETAL_TO + PETAL_LIFE) {
            float base = moveAngle();
            Vec3 me = new Vec3(getX(), floor, getZ());
            for (LivingEntity e : victims(level, 100)) {
                Integer last = petalHit.get(e.getUUID());
                if (last != null && t - last < 10) continue;
                if (e.getY() > floor + 2.6) continue;
                Vec3 rel = e.position().subtract(me);
                boolean hit = false;
                for (int sent = PETAL_FROM; sent < Math.min(t, PETAL_TO) && !hit; sent += PETAL_EVERY) {
                    if (t - sent > PETAL_LIFE) continue;
                    for (int arm = 0; arm < PETAL_ARMS; arm++) {
                        Vec3 p = petal(base, arm, sent, t);
                        double dx = p.x - rel.x, dz = p.z - rel.z;
                        if (dx * dx + dz * dz < 0.85 * 0.85) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (hit && deadHit(e, 0.07F)) {
                    petalHit.put(e.getUUID(), t);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.HOSTILE, 1.2F, 0.7F);
                }
            }
        }
        if (t >= BossState.P4_PETALOS.duration) setBossState(BossState.IDLE);
    }

    // ---- Papel Picado

    /**
     * Papel Picado (8.5 s): giant strings of cut-paper banners (pink, marigold, purple, green, the skulls cut out of them)
     * sweep the floor from one end to the other, one after another, fast: low ones (jump them) and high ones at head height
     * (crouch under them), from both ends, crossing. Each that catches you, a share.
     */
    private void tickP4Papel(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        Vec3 c = p4Center();
        double floor = deadFloor();
        flyTo(new Vec3(c.x, floor + 12, c.z), 0.8);
        if (target != null) turnToward(target.position(), 8F);
        if (t == 2) {
            say("p4_papel", 50);
            papel.clear();
            papelHits.clear();
        }
        double a = inColiseum() && arena != null ? arena.a : 30, b = inColiseum() && arena != null ? arena.b : 22;
        if (t >= 14 && t <= 130 && (t - 14) % 20 == 0) {
            int k = (t - 14) / 20;
            boolean high = k % 3 == 1 || (k > 3 && random.nextBoolean());
            int dir = k % 2 == 0 ? 1 : -1;
            boolean along = k % 4 < 2;                   // along the long axis, or across it
            double half = along ? b + 2 : a + 2;
            double start = along ? -dir * (a + 2) : -dir * (b + 2);
            double heading = along ? (dir > 0 ? 0 : Math.PI) : (dir > 0 ? Math.PI / 2 : -Math.PI / 2);
            Vec3 mid = along ? c.add(start, 0, 0) : c.add(0, 0, start);
            BossProp p = BossProp.create(level, BossProp.Kind.PAPEL, this, new Vec3(mid.x, floor, mid.z)).life(200);
            p.setRadius((float) half);
            p.setYRot((float) Math.toDegrees(heading));
            p.setVariant(high ? 1 : 0);
            level.addFreshEntity(p);
            papel.add(new double[]{mid.x, mid.z, heading, half, high ? 1 : 0, 0, papel.size()});
            papelProps.add(p.getUUID());
            level.playSound(null, mid.x, floor + 2, mid.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.8F, 1.6F);
        }
        double speed = 0.95;
        for (int i = 0; i < papel.size(); i++) {
            double[] w = papel.get(i);
            Entity pe = i < papelProps.size() ? level.getEntity(papelProps.get(i)) : null;
            if (w[5] < 0) continue;
            w[5]++;
            double hx = Math.cos(w[2]), hz = Math.sin(w[2]);
            w[0] += hx * speed;
            w[1] += hz * speed;
            if (pe != null) pe.setPos(w[0], floor, w[1]);
            double travelled = Math.abs((w[0] - c.x) * hx + (w[1] - c.z) * hz);
            if (w[5] > 20 && travelled > (Math.abs(hx) > 0.5 ? a : b) + 3) {
                if (pe != null) pe.discard();
                w[5] = -1;
                continue;
            }
            double lo = w[4] > 0 ? 1.55 : 0.0, hi = w[4] > 0 ? 3.2 : 1.05;
            for (LivingEntity e : victims(level, 100)) {
                double along = (e.getX() - w[0]) * hx + (e.getZ() - w[1]) * hz;
                double across = -(e.getX() - w[0]) * hz + (e.getZ() - w[1]) * hx;
                if (Math.abs(along) > 0.9 || Math.abs(across) > w[3]) continue;
                double y0 = e.getY() - floor, y1 = y0 + e.getBbHeight();
                if (y1 < lo || y0 > hi) continue;
                if (!papelHits.add(((long) w[6] << 32) | e.getId())) continue;
                if (deadHit(e, 0.15F)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(hx * 0.9, 0.35, hz * 0.9));
                    e.hurtMarked = true;
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.HOSTILE, 1.6F, 0.6F);
                }
            }
        }
        if (t >= BossState.P4_PAPEL.duration) {
            for (UUID id : papelProps) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            papelProps.clear();
            papel.clear();
            setBossState(BossState.IDLE);
        }
    }

    // ---- Las Velas de Animas

    /** How far along the cracks (blocks from the skull) the eruption front is at tick {@code t} of Las Velas: out, then back. */
    public static double velasFront(int t) {
        if (t < 30) return -10;
        if (t < 80) return (t - 30) * 1.4;
        if (t < 92) return -10;
        return 70 - (t - 92) * 1.4;
    }

    /**
     * Las Velas de Animas (8 s): he raises his hands and the ectoplasm in the floor's cracks wakes: candles of blue fire burst
     * up out of it, a front running out from the great skull to the wall, then back in from the wall. The tiles between are
     * safe: stand on them, not on a crack. The front glows a second ahead of the fire.
     */
    private void tickP4Velas(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        if (arena == null) {
            setBossState(BossState.IDLE);
            return;
        }
        Vec3 c = arena.centerVec();
        double floor = arena.floorY();
        flyTo(new Vec3(c.x, floor + 8, c.z), 0.8);
        if (target != null) turnToward(target.position(), 6F);
        if (t == 2) {
            say("p4_velas", 50);
            velasHits.clear();
            level.playSound(null, c.x, floor, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        double front = velasFront(t);
        if (front < -5) return;
        boolean out = t < 85;
        var cracks = arena.cracks();
        // the warning a second ahead, and the fire at the front
        double warn = out ? front + 14 : front - 14;
        int n = 0;
        for (var en : cracks.entrySet()) {
            int ord = en.getValue();
            int x = Coliseum.unpackX(en.getKey()), z = Coliseum.unpackZ(en.getKey());
            Vec3 at = new Vec3(arena.center.getX() + x + 0.5, floor, arena.center.getZ() + z + 0.5);
            if (Math.abs(ord - warn) < 0.7 && (x + z) % 2 == 0) Fx.spark(level, at.add(0, 0.2, 0), 1, 0.2, 0.3F, 0x9FF4FF);
            if (Math.abs(ord - front) < 0.71) {
                if (n++ % 3 == 0) {
                    // a candle of blue soul fire up out of the crack
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 6, 0.12, 0.6, 0.12, 0.05);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, at.x, at.y + 0.5, at.z, 1, 0.1, 0.4, 0.1, 0.02);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(0.9, 3.5, 0.9), this::hittable)) {
                    if (!velasHits.add(((long) (out ? 1 : 2) << 40) ^ e.getId())) continue;
                    if (deadHit(e, 0.16F)) {
                        e.setDeltaMovement(e.getDeltaMovement().multiply(0.4, 0, 0.4).add(0, 1.05, 0));
                        e.hurtMarked = true;
                        e.setSecondsOnFire(3);
                    }
                }
            }
        }
        if (t % 4 == 0) level.playSound(null, c.x, floor, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.5F, 0.5F + random.nextFloat() * 0.2F);
        if (t == 85) velasHits.clear();
        if (t >= BossState.P4_VELAS.duration) setBossState(BossState.IDLE);
    }

    // ---- La Danza de los Muertos

    /**
     * La Danza de los Muertos (8.5 s): six skeleton dancers of blue light (his echoes, turned) blink in round you, dancing,
     * circling you as the ring closes; then, one after another, each marks its line (pink) and spins straight through you.
     * Hit one and it shatters.
     */
    private void tickP4Danza(ServerLevel level, @Nullable LivingEntity target) {
        int t = stateTicks;
        LivingEntity who = danzaTarget != null && level.getEntity(danzaTarget) instanceof LivingEntity le && le.isAlive() ? le : target;
        if (who == null) {
            setBossState(BossState.IDLE);
            return;
        }
        double floor = deadFloor();
        flyTo(who.position().add(0, 7, 0).add(flatTo(who.position()).scale(-6)), 0.6);
        turnToward(who.position(), 10F);
        if (t == 2) {
            say("p4_danza", 50);
            for (UUID id : dancers) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            dancers.clear();
            danzaNext = 0;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 at = who.position().add(Math.cos(a) * 7, 0, Math.sin(a) * 7);
                PaponEchoEntity e = PaponEchoEntity.dancer(level, this, who, new Vec3(at.x, floor, at.z));
                level.addFreshEntity(e);
                dancers.add(e.getUUID());
            }
            level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2.0F, 0.6F);
            crowdMood(level, CrowdMood.CLAP);
        }
        // circling, closing in
        int lunge0 = 64;
        for (int i = 0; i < dancers.size(); i++) {
            if (!(level.getEntity(dancers.get(i)) instanceof PaponEchoEntity e) || !e.isAlive()) continue;
            int start = lunge0 + i * 14;
            if (t < start - 10) {
                double r = 7 - Math.min(3.0, t / 20.0);
                double a = i * Math.PI / 3 + t * 0.06;
                Vec3 at = who.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                e.setPos(at.x, floor, at.z);
                e.faceTo(who.position());
            } else if (t == start - 10) {
                Vec3 through = who.position().subtract(e.position()).multiply(1, 0, 1).normalize();
                e.lungeDir(through);
                Fx.warnLine(level, new Vec3(e.getX(), floor, e.getZ()), new Vec3(e.getX(), floor, e.getZ()).add(through.scale(12)), 1.2F, 0xFF2E9A, 10);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.HOSTILE, 1.4F, 1.4F);
            } else if (t >= start && t < start + 10) {
                Vec3 v = e.lungeDirection().scale(1.25);
                e.setPos(e.getX() + v.x, floor, e.getZ() + v.z);
                for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.8), this::hittable)) {
                    if (e.hitOnce(hit.getId()) && deadHit(hit, 0.12F)) {
                        hit.setDeltaMovement(v.scale(0.8).add(0, 0.5, 0));
                        hit.hurtMarked = true;
                        level.playSound(null, hit.getX(), hit.getY(), hit.getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.6F, 0.6F);
                    }
                }
            } else if (t == start + 10) { e.vanishNow(level); }
        }
        if (t >= BossState.P4_DANZA.duration) setBossState(BossState.IDLE);
    }

    @Override
    public void die(DamageSource source) {
        if (getBossState() == BossState.DEATH) return;
        release();
        stopRiding();
        if (level() instanceof ServerLevel level) {
            discardFoils(level);
            clearBeams(level);
            bandWaves.clear();
            if (duelGuitarist >= 0) giveGuitarBack(level, false);
            clearMuertos(level);
            for (UUID id : muchachas) if (level.getEntity(id) instanceof SalsaDancerEntity d) d.mourn();
            crowdReact(level, CrowdMood.GASP, 30);
            crowdMood(level, CrowdMood.MOURN);
        }
        setFlying(false);
        boolean guardian = isPhaseFour();
        setBossState(BossState.DEATH);
        say(guardian ? "p4_death" : "death", 140);
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
                // the Skyline he made out of his van stays where it is: its fob, tied to it, for whoever beat him
                CaravanEntity car = van(level);
                if ((car != null && car.isSkyline()) || isAscended()) {
                    ItemStack fob = new ItemStack(com.groid.papon.registry.ModItems.SKYLINE_FOB.get());
                    if (car != null && car.isSkyline()) fob.getOrCreateTag().putUUID("Van", car.getUUID());
                    spawnAtLocation(fob);
                }
                // back behind his counter, a changed Papon; then Gerbin's presents (and everyone back to the truck)
                if (truck != null && portalAt != null) com.groid.papon.entity.npc.TacoVendorEntity.reform(level, portalAt, truck);
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
    public double getMyRidingOffset() { return -0.15; }

    // ------------------------------------------------------------------ boss bar

    private void updateBossBarPlayers() {
        for (ServerPlayer p : List.copyOf(bossEvent.getPlayers())) {
            if (!p.isAlive() || p.level() != level() || p.distanceToSqr(this) > 72 * 72) bossEvent.removePlayer(p);
        }
        for (ServerPlayer p : ((ServerLevel) level()).players()) { if (p.distanceToSqr(this) < 56 * 56) bossEvent.addPlayer(p); }
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
        if (level() instanceof ServerLevel level) clearBeams(level);
        if (reason.shouldDestroy() && level() instanceof ServerLevel level) {
            if (duelGuitarist >= 0) giveGuitarBack(level, false);
            leaveArena(level);
            discardFoils(level);
            clearGuardian(level);
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
        if (isAscended()) {
            if (s == BossState.P3_ASCEND) return event.setAndContinue(F_WINGS_SPROUT);
            if (s == BossState.P4_HALFBAKED && isMuertos()) return event.setAndContinue(F_BONE_UNFURL);
            // beaten, on the ground: the wings folded down behind him
            if (s == BossState.P4_FALL || s == BossState.P4_RISEUP || s == BossState.P4_IGNIS) return event.setAndContinue(F_WINGS_FOLDED);
            // with the band and dancing with the muchachas: folded away
            if (s == BossState.P4_TOBAND || s == BossState.P4_HONOR || s == BossState.P4_MUCHACHAS) return event.setAndContinue(F_WINGS_FOLDED);
            boolean beat = isFlying() || s == BossState.P2_DIVE || s == BossState.P3_FLASH || s == BossState.P3_COMET || s == BossState.P3_DUEL_FETCH;
            return event.setAndContinue(beat ? F_WINGS_FLY : F_WINGS_IDLE);
        }
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
            case IDLE -> isFlying() ? (isMuertos() ? A_D_FLY : isAscended() ? A_A_FLY : A_FLY)
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
            case P2_CUTAWAY -> cutaway() == Cutaway.STRINGS || cutaway() == Cutaway.GUITARRON ? A_P2_SOLO : A_P2_GROOVE;
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
            case ARENA_OLA -> A_ARENA_OLA;
            case ARENA_STAMPEDE -> A_ARENA_STAMPEDE;
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
            case P3_KNOCKBACK -> A_P3_KNOCKBACK;
            case P3_GLARE -> A_P3_GLARE;
            case P3_STALK -> event.isMoving() ? A_P3_STALK : A_P3_GLARE_HOLD;
            case P3_SNATCH -> A_P3_SNATCH;
            case P3_RETURN -> event.isMoving() ? A_P3_CARRY : A_P3_CARRY_HOLD;
            case P3_BLACKOUT -> A_P3_BLACKOUT;
            case P3_SERENADE -> A_P3_SERENADE;
            case P3_VERDICT -> A_P3_VERDICT;
            case P3_TOSS -> A_P3_TOSS;
            case P3_DANCE -> A_P3_DANCE;
            case P3_FELT -> A_P3_FELT;
            case P3_TO_CAR -> event.isMoving() ? A_P3_STALK : A_P3_GLARE_HOLD;
            case P3_CRUSH -> A_P3_CRUSH;
            case P3_SHOWCAR -> A_P3_SHOWCAR;
            case P3_GO -> A_P3_GO;
            case P3_ASCEND -> A_P3_ASCEND;
            case P3_COMBO -> A_A_COMBO;
            case P3_FLASH -> A_A_FLASH;
            case P3_COMET -> A_A_COMET;
            case P3_COMET_LAND -> A_A_COMET_LAND;
            case P3_LASER -> A_A_LASER;
            case P3_SOLAR -> A_A_SOLAR;
            case P3_MARIACHI -> A_A_CONDUCT;
            case P3_DUEL_FETCH -> duelPhaseClient() ? A_A_DUEL_FLY : A_A_FLY;
            case P3_DUEL -> A_A_SHRED;
            case P3_DUEL_SMASH -> A_A_DUEL_SMASH;
            case P3_DUEL_SHAME -> A_A_DUEL_SHAME;
            case P3_PINATA -> A_A_PINATA;
            case P3_BRAZIERS -> A_A_BRAZIERS;
            case P3_NOVA -> A_A_NOVA;
            case P3_SPENT -> A_A_SPENT;
            case P3_NITRO -> A_A_FLY;
            case P3_POWER -> A_P3_POWER;
            case WINDED -> A_WINDED;
            case P3_SOMBRERO -> A_A_SOMBRERO;
            case P3_VOLCAN -> A_A_VOLCAN;
            case P3_CALAVERAS -> A_A_CALAVERAS;
            case P3_PINATA_SWING -> A_A_PINATA_SWING;
            case P4_FALL -> A_P4_FALL;
            case P4_RISEUP -> A_P4_RISEUP;
            case P4_IGNIS -> A_P4_IGNIS;
            case P4_PROPHECY -> A_P4_PROPHECY;
            case P4_BRETHREN -> A_P4_BRETHREN;
            case P4_SOULS -> A_P4_SOULS;
            case P4_HALFBAKED -> A_P4_HALFBAKED;
            case P4_BOW -> A_P4_BOW;
            case P4_SERPENTS -> A_P4_WATCH;
            case P4_TALKING -> A_P4_TALKING;
            case P4_TOBAND -> A_P4_WALK;
            case P4_HONOR -> A_P4_HONOR;
            case P4_MUCHACHAS -> A_P4_MUCHACHAS;
            case P4_GOODTIME -> A_P4_GOODTIME;
            case P4_SERPIENTES -> A_D_SERPIENTES;
            case P4_FLECHAS -> A_D_FLECHAS;
            case P4_MARTILLO -> A_D_MARTILLO;
            case P4_GIGANTE -> A_D_GIGANTE;
            case P4_GRIETA -> A_D_GRIETA;
            case P4_PETALOS -> A_D_PETALOS;
            case P4_PAPEL, P4_VELAS -> A_D_CONDUCT;
            case P4_DANZA -> A_D_DANZA;
            case P4_LUNA -> A_D_LUNA;
            case P4_THRONE -> A_P4_THRONE;
            case P4_OUROBOROS -> A_D_OUROBOROS;
            case P4_ALIENTO -> A_D_CONDUCT;
            case P4_FUSION -> A_D_LUNA;
            case P4_PISTA, P4_SALSA -> A_D_SALSA;
            case P4_CEMPASUCHIL -> A_D_CAMINO;
            case P4_CASTILLO -> A_D_CASTILLO;
            case P4_TANGO -> A_D_TANGO;
            case P4_TANGO_WIN -> A_D_TANGO_WIN;
            case P4_TANGO_LOSE -> A_D_TANGO_LOSE;
            case P4_RAGE -> A_D_RAGE;
            case P4_JUICIO -> A_D_JUICIO;
        };
        return event.setAndContinue(next);
    }

    /** Client: on the way back from the band stand with the guitar (the duel's fetch, second leg). */
    private boolean duelPhaseClient() { return entityData.get(DATA_GUITAR); }

    private void onSoundCue(SoundKeyframeEvent<PaponBossEntity> event) { SoundCues.play(this, event.getKeyframeData().getSound()); }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

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
        tag.putInt("Song", songId);
        tag.putBoolean("Phase3", isPhaseThree());
        tag.putBoolean("Phase4", isPhaseFour());
        tag.putInt("P4Stage", p4StageReached);
        tag.putInt("Moon", moon());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(BlockPos.containing(home)));
        if (truck != null) tag.put("Truck", truck.save());
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
        songId = tag.getInt("Song");
        boolean p3 = tag.getBoolean("Phase3");
        entityData.set(DATA_PHASE3, p3);
        entityData.set(DATA_NIGHT, p3 ? NIGHT_P3 : 0F);
        entityData.set(DATA_SPOT, p3);
        if (isRisen()) foilCd = 40;
        boolean p4 = tag.getBoolean("Phase4");
        entityData.set(DATA_PHASE4, p4);
        entityData.set(DATA_MOON, tag.getInt("Moon"));
        entityData.set(DATA_P4PROP, p4 ? 1 : 0);
        if (p4) entityData.set(DATA_NIGHT, 0.92F);
        p4StageReached = Math.max(1, tag.getInt("P4Stage"));
        entityData.set(DATA_P4STAGE, p4 ? p4StageReached : 0);
        if (isPhaseTwo()) bossEvent.setName(Component.translatable(BAR_KEY + (p4 ? ".phase4" : p3 ? ".phase3" : ".phase2")));
        if (tag.contains("Home")) home = Vec3.atBottomCenterOf(NbtUtils.readBlockPos(tag.getCompound("Home")));
        if (tag.contains("PortalX")) {
            portalAt = new Vec3(tag.getDouble("PortalX"), tag.getDouble("PortalY"), tag.getDouble("PortalZ"));
            portalYaw = tag.getFloat("PortalYaw");
        }
        if (tag.contains("Truck")) truck = com.groid.papon.journey.TacoLine.load(tag.getCompound("Truck"));
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
