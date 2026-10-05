package com.groid.papon.entity;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.entity.ai.JourneyGoal;
import com.groid.papon.entity.ai.JourneyNavigation;
import com.groid.papon.entity.npc.SoundCues;
import com.groid.papon.entity.npc.TacoVendorEntity;
import com.groid.papon.journey.JourneyData;
import com.groid.papon.journey.JourneyPlanner;
import com.groid.papon.journey.TacoLine;
import com.groid.papon.journey.TacoTruckScene;
import com.groid.papon.journey.TacoTruck;
import com.groid.papon.journey.Terrain;
import com.groid.papon.item.ChanclaItem;
import com.groid.papon.item.JarritoItem;
import com.groid.papon.registry.ModParticles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
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
import java.util.Comparator;
import java.util.UUID;

/**
 * The Wandering Papon: a Walking Crops Papon who accepted a Taco Coupon and is walking, at his
 * own pace, to the nearest Taco Truck. He only walks while a player is with him; left alone he
 * sits down and grills tacos until someone comes back.
 *
 * At the truck he gets in line (the player is meant to stand behind him) and, at the window,
 * proudly hands over the Taco Coupon. It is a fraud. That is what starts the boss fight.
 *
 * He cannot be hurt, targeted, pushed, carried off, leashed, put in a boat, or despawned, and
 * he refuses death by any route that reaches him (see the overrides at the bottom).
 */
public class WanderingPaponEntity extends PathfinderMob implements GeoEntity, Speech.Speaker {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(WanderingPaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SPEECH =
            SynchedEntityData.defineId(WanderingPaponEntity.class, EntityDataSerializers.STRING);
    private static final Speech.Voice VOICE = new Speech.Voice(SoundEvents.WANDERING_TRADER_AMBIENT, 1.4F, 0.45F, 4);
    /** Jarrito flavour in his hand (JarritoItem.Flavor ordinal), -1 for none. Picks the bottle bone. */
    private static final EntityDataAccessor<Integer> DATA_FLAVOR =
            SynchedEntityData.defineId(WanderingPaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BOOSTED =
            SynchedEntityData.defineId(WanderingPaponEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String ANIM = "animation.wandering_papon.";
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation SIT_THEN_GRILL = RawAnimation.begin().thenPlay(ANIM + "sit_down").thenLoop(ANIM + "grill");
    private static final RawAnimation GRILL = RawAnimation.begin().thenLoop(ANIM + "grill");
    private static final RawAnimation PACK_UP = RawAnimation.begin().thenPlayAndHold(ANIM + "pack_up");
    private static final RawAnimation RECEIVE = RawAnimation.begin().thenPlayAndHold(ANIM + "receive_coupon");
    private static final RawAnimation CELEBRATE_THEN_IDLE = RawAnimation.begin().thenPlay(ANIM + "celebrate").thenLoop(ANIM + "idle");
    private static final RawAnimation QUEUE_IDLE = RawAnimation.begin().thenLoop(ANIM + "queue_idle");
    private static final RawAnimation PRESENT = RawAnimation.begin().thenPlayAndHold(ANIM + "present_coupon");
    private static final RawAnimation BUSTED_THEN_NERVOUS = RawAnimation.begin().thenPlay(ANIM + "busted").thenLoop(ANIM + "nervous");
    private static final RawAnimation NERVOUS = RawAnimation.begin().thenLoop(ANIM + "nervous");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(ANIM + "run");
    private static final RawAnimation BOARD_THEN_ROW = RawAnimation.begin().thenPlay(ANIM + "raft_board").thenLoop(ANIM + "raft");
    private static final RawAnimation ROW = RawAnimation.begin().thenLoop(ANIM + "raft");
    private static final RawAnimation LOUNGE = RawAnimation.begin().thenLoop(ANIM + "raft_idle");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlayAndHold(ANIM + "raft_land");
    private static final RawAnimation DRINK = RawAnimation.begin().thenPlayAndHold(ANIM + "drink");
    private static final RawAnimation SMASH = RawAnimation.begin().thenPlayAndHold(ANIM + "smash");
    private static final RawAnimation TIRED_OUT = RawAnimation.begin().thenPlayAndHold(ANIM + "tired");
    private static final RawAnimation SETUP_THEN_SLEEP = RawAnimation.begin().thenPlay(ANIM + "sleep_setup").thenLoop(ANIM + "sleep");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop(ANIM + "sleep");
    private static final RawAnimation FLINCH_THEN_SLEEP = RawAnimation.begin().thenPlay(ANIM + "sleep_flinch").thenLoop(ANIM + "sleep");
    private static final RawAnimation WAKE = RawAnimation.begin().thenPlayAndHold(ANIM + "wake_up");
    private static final RawAnimation WAKE_ANGRY = RawAnimation.begin().thenPlayAndHold(ANIM + "angry_wake");

    /** Jarrito boost: 1.5x speed for two minutes. */
    private static final int BOOST_TICKS = 20 * 120;
    private static final double BOOST_MULTIPLIER = 1.5;
    private static final UUID BOOST_ID = UUID.fromString("6b7f3c52-3a0e-4c55-9d7e-7a1f0c7e2a11");
    /** Tick of SMASHING on which the bottle hits the ground (the smash animation's impact frame, 1.4s). */
    private static final int SMASH_TICK = 28;
    /** Chance per chancla whack that a sleeping Papon wakes up (angry). */
    private static final float CHANCLA_WAKE_CHANCE = 0.02F;
    /** Fewest ticks between chancla whacks that count, so spam-clicking does not roll 20 times a second. */
    private static final int CHANCLA_COOLDOWN = 8;
    /** Ticks in deep water before he gives up wading and gets the llama out. */
    private static final int DEEP_WATER_TICKS = 6;

    /** Horizontal distance from the site at which he counts as arrived. */
    private static final double ARRIVE_RADIUS = 3.5;
    /** When this close, the site is moved to the nearest flat, dry ground so the truck has somewhere to stand. */
    private static final double REFINE_RADIUS = 48;
    /** How far from the picked site to look for inland ground once it loads. */
    private static final int REFINE_SEARCH = 40;
    /** Tick of the ARRIVED state at which the truck appears (mid-celebration, after his first jump). */
    private static final int TRUCK_TICK = 22;

    /** Set by /papon escort: treat him as escorted with nobody around, for testing on a server. */
    public static boolean debugEscort = false;
    private static final TicketType<ChunkPos> DEBUG_TICKET =
            TicketType.create("papon_debug_escort", Comparator.comparingLong(ChunkPos::toLong), 60);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent journeyBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.NOTCHED_10);

    @Nullable private BlockPos origin;
    @Nullable private BlockPos destination;
    private boolean destinationRefined;
    private double journeyLength = 1;
    private int stateTicks;
    private int lonelyTicks;
    private float seatYaw;
    @Nullable private BlockPos lastSafePos;
    private int undergroundTicks;
    private int inWallTicks;
    private boolean escorted;
    // In line at the truck: set by the vendor every tick (see TacoVendorEntity#assignSlots).
    @Nullable private UUID vendorId;
    @Nullable private TacoLine line;
    private int lineSlot;
    private int lineTotal;
    private int speechTicks;
    private int babbleTicks;
    private final RaftDriver raft = new RaftDriver(this);
    private int boostTicks;
    private boolean pendingTired;
    /** Woken by the chancla: no more sleep until morning. */
    private boolean awakeUntilDay;
    private int lastChanclaTick = -100;
    private int deepWaterTicks;
    @Nullable private PaponState clientLastState;

    public WanderingPaponEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.0F);
        setPathfindingMalus(BlockPathTypes.WATER, 4.0F);
        setPathfindingMalus(BlockPathTypes.WATER_BORDER, 2.0F);
        setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.DANGER_OTHER, 16.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, -1.0F);
        setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1.0F);
        setPathfindingMalus(BlockPathTypes.DANGER_POWDER_SNOW, -1.0F);
        setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
        setPathfindingMalus(BlockPathTypes.LEAVES, -1.0F);
        journeyBar.setDarkenScreen(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, PaponState.WALKING.ordinal());
        entityData.define(DATA_SPEECH, "");
        entityData.define(DATA_FLAVOR, -1);
        entityData.define(DATA_BOOSTED, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) { return new JourneyNavigation(this, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this) {
            @Override public boolean canUse() { return !getPaponState().isRafting() && super.canUse(); }
        });
        goalSelector.addGoal(1, new JourneyGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F) {
            @Override public boolean canUse() { return !getPaponState().isSeated() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !getPaponState().isSeated() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return getPaponState() == PaponState.WAITING && super.canUse(); }
        });
    }

    // ------------------------------------------------------------------ journey

    /** Starts the walk from where he stands. {@code giver} is who handed over the coupon, if anyone. */
    public void beginJourney(@Nullable Player giver) {
        if (!(level() instanceof ServerLevel server)) return;
        origin = blockPosition();
        destination = JourneyPlanner.pickDestination(server, origin, random);
        destinationRefined = false;
        journeyLength = Math.max(1, Math.sqrt(JourneyPlanner.horizontalDistSqr(origin, destination)));
        if (giver != null) {
            lookAt(giver, 360, 360);
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        setPaponState(PaponState.RECEIVING);
        PaponMod.LOG.info("Wandering Papon {} heading from {} to the Taco Truck at {} ({} blocks)", getUUID(),
                origin, destination, (int) journeyLength);
    }

    public PaponState getPaponState() { return PaponState.byId(entityData.get(DATA_STATE)); }

    public void setPaponState(PaponState state) {
        if (state.isSeated() && !getPaponState().isSeated()) { seatYaw = getYRot(); }
        entityData.set(DATA_STATE, state.ordinal());
        stateTicks = 0;
        lonelyTicks = 0;
        if (state != PaponState.WALKING) { getNavigation().stop(); }
        if (state.isRafting()) { raft.reset(); }
        if (state != PaponState.DRINKING && state != PaponState.SMASHING) { entityData.set(DATA_FLAVOR, -1); }
        track();
    }

    /** 1.5 while a Jarrito is in him. Applied to walking through the speed attribute, to rafting directly. */
    public double speedFactor() { return boostTicks > 0 ? BOOST_MULTIPLIER : 1.0; }

    public int getFlavorId() { return entityData.get(DATA_FLAVOR); }

    public boolean isBoosted() { return entityData.get(DATA_BOOSTED); }

    /** Night in a dimension with a day cycle, on dry land, and not woken by a chancla tonight. */
    private boolean shouldSleep() {
        return !awakeUntilDay && level().dimensionType().hasSkyLight() && !level().dimensionType().hasFixedTime()
                && level().isNight() && onGround() && !isInWater();
    }

    @Nullable
    public BlockPos getDestination() { return destination; }

    /** Re-aims the journey at (x, z); for /papon target. */
    public void setDestination(BlockPos dest) {
        origin = blockPosition();
        destination = dest;
        destinationRefined = false;
        journeyLength = Math.max(1, Math.sqrt(JourneyPlanner.horizontalDistSqr(origin, destination)));
        track();
    }

    public boolean isEscorted() { return escorted; }

    public double remainingDistance() { return destination == null ? 0 : Math.sqrt(JourneyPlanner.horizontalDistSqr(blockPosition(), destination)); }

    private boolean hasPlayerWithin(double radius) {
        if (debugEscort) return true;
        double r2 = radius * radius;
        for (Player p : level().players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double dx = p.getX() - getX();
            double dz = p.getZ() - getZ();
            if (dx * dx + dz * dz <= r2 && Math.abs(p.getY() - getY()) <= 24) return true;
        }
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel server = (ServerLevel) level();
        stateTicks++;
        if (destination == null) { beginJourney(null); }
        escorted = hasPlayerWithin(PaponConfig.ESCORT_RADIUS.get());
        if (level().isDay()) awakeUntilDay = false;
        tickBoost();
        PaponState state = getPaponState();

        if (state.isSeated()) {
            setYRot(seatYaw);
            yBodyRot = seatYaw;
            yHeadRot = seatYaw;
            getNavigation().stop();
        }

        switch (state) {
            case RECEIVING -> {
                getNavigation().stop();
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
            case WALKING -> tickWalking(server);
            case SITTING_DOWN -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.GRILLING);
            }
            case GRILLING -> {
                if (hasPlayerWithin(PaponConfig.RESUME_RADIUS.get()) || shouldSleep()) setPaponState(PaponState.PACKING_UP);
            }
            case PACKING_UP -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
            case ARRIVED -> {
                getNavigation().stop();
                if (stateTicks == TRUCK_TICK) raiseTruck(server);
                if (stateTicks >= state.duration) setPaponState(vendorId != null ? PaponState.QUEUED : PaponState.WAITING);
            }
            case WAITING -> getNavigation().stop();
            case QUEUED -> tickQueued();
            case PRESENTING, BUSTED -> {
                getNavigation().stop();
                if (line != null) faceCounter();
            }
            case RAFT_BOARDING -> {
                raft.tick(false);
                if (stateTicks >= state.duration) setPaponState(PaponState.RAFTING);
            }
            case RAFTING -> {
                if (escorted) {
                    lonelyTicks = 0;
                } else if (++lonelyTicks > PaponConfig.LONELY_DELAY_TICKS.get()) {
                    setPaponState(PaponState.RAFT_IDLE);
                    break;
                }
                // A site that turns out to be on or by the water is moved inland as soon as it loads.
                if (!destinationRefined && remainingDistance() < REFINE_RADIUS) refineDestination(server);
                BlockPos shore = raft.tick(true);
                if (shore != null) landOn(shore);
            }
            case RAFT_IDLE -> {
                raft.tick(false);
                if (hasPlayerWithin(PaponConfig.RESUME_RADIUS.get())) setPaponState(PaponState.RAFTING);
            }
            case RAFT_LANDING -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
            case DRINKING -> {
                if (stateTicks >= state.duration) {
                    startBoost();
                    setPaponState(PaponState.WALKING);
                }
            }
            case SMASHING -> {
                if (stateTicks == SMASH_TICK) smashBottle(server);
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
            case TIRED -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
            case SLEEP_SETUP -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.SLEEPING);
            }
            case SLEEPING -> {
                if (level().isDay()) setPaponState(PaponState.WAKING);
            }
            case SLEEP_FLINCH -> {
                if (stateTicks >= state.duration) setPaponState(level().isDay() ? PaponState.WAKING : PaponState.SLEEPING);
            }
            case WAKING, ANGRY_WAKE -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
        }

        if (tickCount % 20 == 0) {
            keepSafe(server);
            track();
            if (debugEscort) {
                // Stand-in for a player: keep his surroundings loaded so he can walk an empty server.
                ChunkPos here = chunkPosition();
                server.getChunkSource().addRegionTicket(DEBUG_TICKET, here, 3, here);
            }
        }
        if (debugEscort && tickCount % 200 == 0) {
            PaponMod.LOG.info("[debug] Papon {} {} at {} - {} blocks left", getUUID().toString().substring(0, 8),
                    getPaponState(), blockPosition().toShortString(), (int) remainingDistance());
        }
        if (tickCount % 10 == 0) { updateJourneyBar(server); }
    }

    private void tickWalking(ServerLevel server) {
        if (destination == null) return;
        double dist = remainingDistance();
        if (!destinationRefined && dist < REFINE_RADIUS) {
            refineDestination(server);
            dist = remainingDistance();
        }
        if (dist <= ARRIVE_RADIUS && destinationRefined) {
            arrive(server);
            return;
        }
        if (isInWater() && RaftDriver.waterDepth(server, position()) >= 2) {
            if (++deepWaterTicks >= DEEP_WATER_TICKS) {
                deepWaterTicks = 0;
                setPaponState(PaponState.RAFT_BOARDING);
                return;
            }
        } else { deepWaterTicks = 0; }
        if (shouldSleep()) {
            setPaponState(PaponState.SLEEP_SETUP);
            return;
        }
        if (pendingTired) {
            pendingTired = false;
            setPaponState(PaponState.TIRED);
            return;
        }
        if (escorted) {
            lonelyTicks = 0;
        } else if (++lonelyTicks > PaponConfig.LONELY_DELAY_TICKS.get()) { setPaponState(PaponState.SITTING_DOWN); }
    }

    /**
     * Once the site's land is loaded, pins it to flat, dry ground well away from any water, so the
     * Taco Truck never stands on a beach or a riverbank. If there is no such ground nearby (the
     * site was on the sea, say, from /papon target), the journey is re-aimed at the nearest inland
     * ground on the biome map and refined again when he gets close to that.
     */
    private void refineDestination(ServerLevel server) {
        if (destination == null || !level().hasChunkAt(destination)) return;
        BlockPos spot = Terrain.findInlandSpotNear(server, destination.getX(), destination.getZ(), REFINE_SEARCH);
        if (spot != null) {
            destination = spot;
            destinationRefined = true;
        } else {
            BlockPos inland = JourneyPlanner.nearestInland(server, destination, blockPosition());
            PaponMod.LOG.info("Wandering Papon {}: no inland ground near {}, re-aiming at {}", getUUID(), destination, inland);
            destination = inland;
            destinationRefined = false;
            journeyLength = Math.max(journeyLength, Math.sqrt(JourneyPlanner.horizontalDistSqr(blockPosition(), destination)));
        }
        track();
    }

    /** Called by JourneyGoal when he is stuck right next to the site: close enough. */
    public void arriveNearby() {
        if (level() instanceof ServerLevel server && getPaponState() == PaponState.WALKING && destinationRefined
                && Terrain.isInland(server, blockPosition(), Terrain.INLAND_RADIUS)) {
            arrive(server);
        }
    }

    private void arrive(ServerLevel server) {
        getNavigation().stop();
        // turn to whichever way has the flattest, driest ground for the truck and its line
        Direction facing = TacoTruck.bestFacing(server, blockPosition(), Direction.fromYRot(getYRot()),
                TacoTruckScene.windowGap());
        setYRot(facing.toYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        destination = blockPosition();
        setPaponState(PaponState.ARRIVED);
        journeyBar.removeAllPlayers();
        Component title = Component.translatable("title.papon.arrived").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("title.papon.arrived.subtitle").withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(this) < 64 * 64) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
                p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
        }
    }

    /** The truck appears ahead of him with its line already queued; he joins the back of it. */
    private void raiseTruck(ServerLevel server) {
        TacoVendorEntity open = TacoTruckScene.vendorNear(server, position(), 48);
        if (open != null && open.acceptsCustomers()) {
            open.join(this);
        } else if (TacoTruck.existingNear(server, blockPosition(), 24) == null) {
            TacoTruckScene.open(server, blockPosition(), getDirection(), this);
        } else { return; }
        onJourneyComplete(server);
    }

    // ------------------------------------------------------------------ in line

    /** Called by the vendor every tick while he is in its line. */
    public void setLineSlot(UUID vendor, TacoLine line, int slot, int served, int total) {
        this.vendorId = vendor;
        this.line = line;
        this.lineSlot = slot;
        this.lineTotal = total - served;
        if (getPaponState() == PaponState.WAITING) setPaponState(PaponState.QUEUED);
    }

    @Nullable
    public TacoLine getLine() { return line; }

    /** For /papon tacoline: skip the walk, he is already at the truck and getting in line. */
    public void skipToTruck() {
        origin = blockPosition();
        destination = blockPosition();
        destinationRefined = true;
        journeyLength = 1;
        setPaponState(PaponState.QUEUED);
    }

    /** At the window: out comes the coupon. */
    public void presentCoupon() {
        setPaponState(PaponState.PRESENTING);
        Speech.say(this, ChatFormatting.YELLOW, "speech.papon.wandering.present", true, true, 46);
    }

    /** The vendor has called it a fraud. */
    public void busted() {
        setPaponState(PaponState.BUSTED);
        Speech.say(this, ChatFormatting.YELLOW, "speech.papon.wandering.busted", true, true, 50);
    }

    private void tickQueued() {
        if (line == null) {
            getNavigation().stop();
            return;
        }
        Vec3 target = line.slot(lineSlot);
        double dx = target.x - getX();
        double dz = target.z - getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 > 0.3 * 0.3) {
            if (d2 < 4) {
                getMoveControl().setWantedPosition(target.x, target.y, target.z, 0.8);
            } else if (getNavigation().isDone() || tickCount % 20 == 0) { getNavigation().moveTo(target.x, target.y, target.z, 1.0); }
        } else {
            getNavigation().stop();
            faceCounter();
        }
    }

    private void faceCounter() {
        Vec3 c = line.counter();
        float yaw = (float) (Mth.atan2(c.z - getZ(), c.x - getX()) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ------------------------------------------------------------------ speech

    @Override
    public String getSpeech() { return entityData.get(DATA_SPEECH); }

    @Override
    public void setSpeech(String encoded, int ticks) {
        entityData.set(DATA_SPEECH, encoded);
        speechTicks = ticks;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SPEECH.equals(key) && level().isClientSide) { babbleTicks = getSpeech().isEmpty() ? 0 : Speech.babbleLength(getSpeech()); }
    }

    /**
     * The Taco Truck stands and he is in its line. What follows is the line (TacoVendorEntity),
     * then the fraud coupon at the window, then TacoBoss.onFraud: the fight hooks in there.
     */
    protected void onJourneyComplete(ServerLevel server) {
        PaponMod.LOG.info("Wandering Papon {} reached the Taco Truck at {}", getUUID(), blockPosition());
    }

    /** Steps off the raft onto the shore spot RaftDriver found. */
    private void landOn(BlockPos shore) {
        moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5, getYRot(), 0);
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 30, 0.6, 0.2, 0.6, 0.1);
        }
        setPaponState(PaponState.RAFT_LANDING);
    }

    private void tickBoost() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (boostTicks > 0) {
            boostTicks--;
            if (speed != null && speed.getModifier(BOOST_ID) == null) {
                speed.addTransientModifier(new AttributeModifier(BOOST_ID, "Jarrito", BOOST_MULTIPLIER - 1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (boostTicks == 0) { pendingTired = true; }
        } else if (speed != null && speed.getModifier(BOOST_ID) != null) { speed.removeModifier(BOOST_ID); }
        entityData.set(DATA_BOOSTED, boostTicks > 0);
    }

    private void startBoost() {
        boostTicks = BOOST_TICKS;
        pendingTired = false;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 12, 0.5, 0.5, 0.5, 0);
        }
    }

    /** A player hands him a Jarrito. Returns true if he took it. */
    private boolean offerJarrito(Player player, ItemStack stack, JarritoItem jarrito) {
        PaponState state = getPaponState();
        if (state != PaponState.WALKING) {
            player.displayClientMessage(Component.translatable("message.papon.jarrito_busy").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        boolean yellow = jarrito.flavor() == JarritoItem.Flavor.YELLOW;
        if (!yellow && boostTicks > 0) {
            player.displayClientMessage(Component.translatable("message.papon.jarrito_full").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        lookAt(player, 360, 360);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        setPaponState(yellow ? PaponState.SMASHING : PaponState.DRINKING);
        entityData.set(DATA_FLAVOR, jarrito.flavor().ordinal());
        player.displayClientMessage(Component.translatable(yellow ? "message.papon.jarrito_yellow" : "message.papon.jarrito_drink")
                .withStyle(yellow ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        return true;
    }

    /** The impact frame of the smash animation: glass and yellow soda everywhere. */
    private void smashBottle(ServerLevel server) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double x = getX() - Mth.sin(yaw) * 0.7;
        double z = getZ() + Mth.cos(yaw) * 0.7;
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GLASS_BOTTLE)),
                x, getY() + 0.15, z, 24, 0.25, 0.1, 0.25, 0.15);
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.15F), 1.3F),
                x, getY() + 0.1, z, 30, 0.45, 0.1, 0.45, 0.0);
        server.sendParticles(ParticleTypes.SPLASH, x, getY() + 0.1, z, 20, 0.4, 0.1, 0.4, 0.2);
        server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.7, getZ(), 3, 0.3, 0.2, 0.3, 0);
        server.playSound(null, x, getY(), z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2F, 0.9F);
    }

    /** Whacked with a chancla (see CommonEvents). Only a sleeping Papon cares. */
    public void onChancla(Player player) {
        if (!(level() instanceof ServerLevel server) || tickCount - lastChanclaTick < CHANCLA_COOLDOWN) return;
        lastChanclaTick = tickCount;
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 1.3F);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 1.6F);
        server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.9, getZ(), 8, 0.4, 0.3, 0.4, 0.1);
        PaponState state = getPaponState();
        if (state != PaponState.SLEEPING && state != PaponState.SLEEP_FLINCH) return;
        if (random.nextFloat() < CHANCLA_WAKE_CHANCE) {
            awakeUntilDay = true;
            setPaponState(PaponState.ANGRY_WAKE);
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.4, getZ(), 6, 0.5, 0.3, 0.5, 0);
            player.displayClientMessage(Component.translatable("message.papon.chancla_woke").withStyle(ChatFormatting.RED), true);
        } else { setPaponState(PaponState.SLEEP_FLINCH); }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof JarritoItem jarrito) {
            if (!level().isClientSide) offerJarrito(player, held, jarrito);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        PaponState state = getPaponState();
        if (state == PaponState.WAITING || state == PaponState.QUEUED || state == PaponState.PRESENTING
                || state == PaponState.BUSTED) {
            if (!level().isClientSide) {
                String key = state == PaponState.WAITING ? "message.papon.waiting"
                        : state == PaponState.QUEUED ? "message.papon.queued" : "message.papon.busted";
                player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------ staying safe

    /** Pulls him back to the surface if he has somehow ended up below it, in a wall, in lava or in the void. */
    private void keepSafe(ServerLevel server) {
        BlockPos here = blockPosition();
        boolean underground = Terrain.isUnderground(server, here);
        if (onGround() && !underground && Terrain.isDryStandable(server, here)) { lastSafePos = here; }
        undergroundTicks = underground && !getPaponState().atTruck() ? undergroundTicks + 20 : 0;
        inWallTicks = isInWall() ? inWallTicks + 20 : 0;
        if (getY() < server.getMinBuildHeight() || isInLava() || undergroundTicks >= 60 || inWallTicks >= 20) { rescueToSurface(server); }
    }

    public void rescueToSurface(ServerLevel server) {
        BlockPos target = Terrain.surfaceAt(server, getBlockX(), getBlockZ());
        if (target == null || !Terrain.isDryStandable(server, target) || Terrain.isUnderground(server, target)) {
            target = Terrain.findDrySpotNear(server, getBlockX(), getBlockZ(), 12, -1);
        }
        if (target == null) target = lastSafePos;
        if (target == null) target = server.getSharedSpawnPos();
        hopTo(target);
        undergroundTicks = 0;
        inWallTicks = 0;
    }

    /** Teleport with a little puff of dust. Used to unstick him; never more than a few blocks in normal play. */
    public void hopTo(BlockPos target) {
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 16, 0.3, 0.4, 0.3, 0.02);
        getNavigation().stop();
        teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        setDeltaMovement(Vec3.ZERO);
        server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 16, 0.3, 0.4, 0.3, 0.02);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.FOX_TELEPORT, SoundSource.NEUTRAL, 0.8F, 1.3F);
    }

    private void track() {
        if (!(level() instanceof ServerLevel server) || origin == null || destination == null) return;
        JourneyData.get(server).track(getUUID(), origin, destination, blockPosition(), getPaponState().name(),
                server.getGameTime());
    }

    // ------------------------------------------------------------------ journey bar

    private void updateJourneyBar(ServerLevel server) {
        PaponState state = getPaponState();
        // The bar is for the journey: once he is at the Taco Truck it goes away for good.
        if (!PaponConfig.SHOW_JOURNEY_BAR.get() || destination == null || state.atTruck()) {
            journeyBar.removeAllPlayers();
            return;
        }
        int left = (int) Math.ceil(remainingDistance());
        Component name = switch (state) {
            case RECEIVING -> Component.translatable("bar.papon.receiving");
            case WALKING -> Component.translatable(isBoosted() ? "bar.papon.zooming" : "bar.papon.walking", left);
            case SITTING_DOWN, GRILLING -> Component.translatable("bar.papon.grilling", left);
            case PACKING_UP -> Component.translatable("bar.papon.packing", left);
            case ARRIVED, WAITING -> Component.translatable("bar.papon.arrived");
            case QUEUED -> Component.translatable("bar.papon.queued", lineSlot);
            case PRESENTING -> Component.translatable("bar.papon.presenting");
            case BUSTED -> Component.translatable("bar.papon.busted");
            case RAFT_BOARDING, RAFTING, RAFT_LANDING -> Component.translatable("bar.papon.rafting", left);
            case RAFT_IDLE -> Component.translatable("bar.papon.raft_idle", left);
            case DRINKING -> Component.translatable("bar.papon.drinking", left);
            case SMASHING -> Component.translatable("bar.papon.smashing", left);
            case TIRED -> Component.translatable("bar.papon.tired", left);
            case SLEEP_SETUP, SLEEPING, SLEEP_FLINCH -> Component.translatable("bar.papon.sleeping", left);
            case WAKING -> Component.translatable("bar.papon.waking", left);
            case ANGRY_WAKE -> Component.translatable("bar.papon.angry", left);
        };
        journeyBar.setName(name);
        journeyBar.setColor(switch (state) {
            case SITTING_DOWN, GRILLING, RAFT_IDLE, SMASHING, ANGRY_WAKE, BUSTED -> BossEvent.BossBarColor.RED;
            case SLEEP_SETUP, SLEEPING, SLEEP_FLINCH, WAKING -> BossEvent.BossBarColor.PURPLE;
            case RAFT_BOARDING, RAFTING, RAFT_LANDING -> BossEvent.BossBarColor.BLUE;
            case QUEUED, PRESENTING -> BossEvent.BossBarColor.GREEN;
            default -> isBoosted() ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW;
        });
        journeyBar.setProgress(switch (state) {
            case ARRIVED, WAITING, PRESENTING, BUSTED -> 1F;
            // the line is the countdown to the fight: it fills as the people ahead of him are served
            case QUEUED -> lineTotal <= 1 ? 1F : Mth.clamp(1F - (float) lineSlot / (lineTotal - 1), 0F, 1F);
            default -> Mth.clamp(1F - (float) (left / journeyLength), 0F, 1F);
        });
        for (ServerPlayer p : new ArrayList<>(journeyBar.getPlayers())) {
            if (p.level() != server || p.isRemoved() || p.distanceToSqr(this) > 56 * 56) journeyBar.removePlayer(p);
        }
        for (ServerPlayer p : server.players()) { if (p.distanceToSqr(this) < 40 * 40) journeyBar.addPlayer(p); }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED) {
            PaponMod.LOG.warn("Something tried to kill Wandering Papon {}; he declined", getUUID());
            return;
        }
        journeyBar.removeAllPlayers();
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel server) { JourneyData.get(server).forget(getUUID()); }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ client

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (speechTicks > 0 && --speechTicks == 0) entityData.set(DATA_SPEECH, "");
            return;
        }
        if (babbleTicks > 0) VOICE.blip(this, --babbleTicks);
        PaponState state = getPaponState();
        if (state == PaponState.GRILLING) {
            grillParticles();
        } else if (state == PaponState.SLEEPING || state == PaponState.SLEEP_FLINCH) {
            if (tickCount % 40 == 0) {
                // His head is at the far end of the sleeping bag, behind where he was standing.
                float yaw = yBodyRot * Mth.DEG_TO_RAD;
                level().addParticle(ModParticles.SNOOZE.get(), getX() + Mth.sin(yaw) * 1.35, getY() + 1.15,
                        getZ() - Mth.cos(yaw) * 1.35, 0, 0.02, 0);
            }
        } else if (state == PaponState.RAFTING && tickCount % 5 == 0) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            double side = (tickCount / 5) % 2 == 0 ? 0.9 : -0.9;
            level().addParticle(ParticleTypes.SPLASH, getX() + Mth.cos(yaw) * side, getY() + 0.1,
                    getZ() + Mth.sin(yaw) * side, 0, 0.1, 0);
        } else if (state == PaponState.WALKING && isBoosted() && tickCount % 3 == 0
                && getDeltaMovement().horizontalDistanceSqr() > 1.0E-3) {
            level().addParticle(ParticleTypes.POOF, getX(), getY() + 0.05, getZ(), 0, 0.01, 0);
        }
    }

    /** Smoke and the odd spark off the grill, which sits 15px in front of him. */
    private void grillParticles() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double gx = getX() - Mth.sin(yaw) * 0.94;
        double gz = getZ() + Mth.cos(yaw) * 0.94;
        double gy = getY() + 0.42;
        if (tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.SMOKE, gx + (random.nextDouble() - 0.5) * 0.4, gy,
                    gz + (random.nextDouble() - 0.5) * 0.3, 0, 0.03, 0);
        }
        if (random.nextInt(14) == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME, gx + (random.nextDouble() - 0.5) * 0.4, gy - 0.05,
                    gz + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
        if (random.nextInt(40) == 0) { level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, gx, gy + 0.1, gz, 0, 0.02, 0); }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animate)
                .setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<WanderingPaponEntity> event) {
        AnimationController<WanderingPaponEntity> controller = event.getController();
        RawAnimation current = controller.getCurrentRawAnimation();
        PaponState state = getPaponState();
        RawAnimation next = switch (state) {
            case RECEIVING -> RECEIVE;
            case WALKING -> event.isMoving() ? (isBoosted() ? RUN : WALK) : IDLE;
            case SITTING_DOWN -> SIT_THEN_GRILL;
            // A client that watched him sit down is already in the loop that follows; one that
            // arrives later starts straight in the loop.
            case GRILLING -> SIT_THEN_GRILL.equals(current) ? SIT_THEN_GRILL : GRILL;
            case PACKING_UP -> PACK_UP;
            case ARRIVED -> CELEBRATE_THEN_IDLE;
            case WAITING -> CELEBRATE_THEN_IDLE.equals(current) ? CELEBRATE_THEN_IDLE : IDLE;
            case QUEUED -> event.isMoving() ? WALK : QUEUE_IDLE;
            case PRESENTING -> PRESENT;
            case BUSTED -> PRESENT.equals(current) || BUSTED_THEN_NERVOUS.equals(current) ? BUSTED_THEN_NERVOUS : NERVOUS;
            case RAFT_BOARDING -> BOARD_THEN_ROW;
            case RAFTING -> BOARD_THEN_ROW.equals(current) ? BOARD_THEN_ROW : ROW;
            case RAFT_IDLE -> LOUNGE;
            case RAFT_LANDING -> LAND;
            case DRINKING -> DRINK;
            case SMASHING -> SMASH;
            case TIRED -> TIRED_OUT;
            case SLEEP_SETUP -> SETUP_THEN_SLEEP;
            case SLEEPING -> SETUP_THEN_SLEEP.equals(current) || FLINCH_THEN_SLEEP.equals(current) ? current : SLEEP;
            case SLEEP_FLINCH -> FLINCH_THEN_SLEEP;
            case WAKING -> WAKE;
            case ANGRY_WAKE -> WAKE_ANGRY;
        };
        // Re-entering a one-shot (a second chancla flinch) must restart it, not continue it.
        if (state != clientLastState && state.duration > 0 && next.equals(current)) { controller.forceAnimationReset(); }
        clientLastState = state;
        return event.setAndContinue(next);
    }

    /** Animation sound keys name a cue; the cue picks a vanilla sound so it can be retuned here. */
    private void onSoundCue(SoundKeyframeEvent<WanderingPaponEntity> event) {
        String cue = event.getKeyframeData().getSound();
        SoundEvent sound;
        float volume = 0.7F;
        float pitch = 1.0F;
        switch (cue) {
            case "sigh" -> { sound = SoundEvents.WANDERING_TRADER_AMBIENT; pitch = 1.45F; volume = 0.5F; }
            case "plop" -> { sound = SoundEvents.SLIME_SQUISH; volume = 0.6F; pitch = 0.8F; }
            case "rummage" -> { sound = SoundEvents.ARMOR_EQUIP_LEATHER; volume = 0.8F; }
            case "clank" -> { sound = SoundEvents.LANTERN_PLACE; volume = 0.9F; pitch = 0.9F; }
            case "pop" -> { sound = SoundEvents.ITEM_PICKUP; volume = 0.5F; pitch = 1.6F; }
            case "flip" -> { sound = SoundEvents.ITEM_PICKUP; volume = 0.5F; pitch = 0.75F; }
            case "sizzle" -> { sound = SoundEvents.FIRE_EXTINGUISH; volume = 0.18F; pitch = 1.9F; }
            case "shake" -> { sound = SoundEvents.SAND_HIT; volume = 0.6F; pitch = 1.7F; }
            case "hum", "notice" -> { sound = SoundEvents.WANDERING_TRADER_YES; volume = 0.55F; pitch = 1.5F; }
            case "cheer" -> { sound = SoundEvents.VILLAGER_CELEBRATE; volume = 0.75F; pitch = 1.45F; }
            case "paper" -> { sound = SoundEvents.BOOK_PAGE_TURN; volume = 1.0F; }
            case "gasp" -> { sound = SoundEvents.VILLAGER_TRADE; volume = 0.8F; pitch = 1.6F; }
            case "jump" -> { sound = SoundEvents.SLIME_JUMP_SMALL; volume = 0.7F; }
            case "determined" -> { sound = SoundEvents.VILLAGER_YES; volume = 0.7F; pitch = 1.3F; }
            case "boing" -> { sound = SoundEvents.SLIME_JUMP; volume = 0.8F; pitch = 1.7F; }
            case "llama" -> { sound = SoundEvents.LLAMA_AMBIENT; volume = 0.8F; }
            case "splash" -> { sound = SoundEvents.BOAT_PADDLE_WATER; volume = 0.6F; }
            case "shake_off" -> { sound = SoundEvents.WOLF_SHAKE; volume = 0.8F; pitch = 1.3F; }
            case "uncap" -> { sound = SoundEvents.LANTERN_HIT; volume = 0.5F; pitch = 1.8F; }
            case "gulp" -> { sound = SoundEvents.GENERIC_DRINK; volume = 0.6F; pitch = 1.2F; }
            case "toss" -> { sound = SoundEvents.EGG_THROW; volume = 0.6F; }
            case "burp" -> { sound = SoundEvents.PLAYER_BURP; volume = 1.0F; pitch = 0.8F; }
            case "grumble", "huff" -> { sound = SoundEvents.VILLAGER_NO; volume = 0.8F; pitch = 0.85F; }
            case "angry" -> { sound = SoundEvents.VILLAGER_NO; volume = 1.0F; pitch = 0.7F; }
            case "smash" -> { sound = SoundEvents.GLASS_BREAK; volume = 0.6F; pitch = 1.2F; }
            case "stomp" -> { sound = SoundEvents.GRAVEL_STEP; volume = 1.0F; pitch = 0.8F; }
            case "pant" -> { sound = SoundEvents.WOLF_PANT; volume = 0.6F; pitch = 1.3F; }
            case "wipe" -> { sound = SoundEvents.ARMOR_EQUIP_LEATHER; volume = 0.5F; pitch = 1.4F; }
            case "yawn" -> { sound = SoundEvents.FOX_SLEEP; volume = 0.8F; pitch = 0.75F; }
            case "thud" -> { sound = SoundEvents.WOOD_PLACE; volume = 0.7F; }
            case "unroll" -> { sound = SoundEvents.WOOL_PLACE; volume = 0.9F; }
            case "rustle" -> { sound = SoundEvents.WOOL_HIT; volume = 0.9F; }
            case "snore" -> { sound = SoundEvents.FOX_SLEEP; volume = 0.6F; pitch = 0.6F; }
            case "snore_out" -> { sound = SoundEvents.FOX_SLEEP; volume = 0.4F; pitch = 0.9F; }
            case "snort" -> { sound = SoundEvents.PIG_AMBIENT; volume = 0.7F; pitch = 1.4F; }
            default -> {
                SoundCues.play(this, cue);
                return;
            }
        }
        level().playLocalSound(getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume,
                pitch * (0.95F + random.nextFloat() * 0.1F), false);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("PaponState", getPaponState().name());
        tag.putInt("StateTicks", stateTicks);
        tag.putFloat("SeatYaw", seatYaw);
        tag.putDouble("JourneyLength", journeyLength);
        tag.putBoolean("DestinationRefined", destinationRefined);
        if (origin != null) tag.put("Origin", NbtUtils.writeBlockPos(origin));
        if (destination != null) tag.put("Destination", NbtUtils.writeBlockPos(destination));
        if (lastSafePos != null) tag.put("LastSafePos", NbtUtils.writeBlockPos(lastSafePos));
        if (vendorId != null) tag.putUUID("Vendor", vendorId);
        if (line != null) tag.put("Line", line.save());
        tag.putInt("LineSlot", lineSlot);
        tag.putInt("BoostTicks", boostTicks);
        tag.putBoolean("PendingTired", pendingTired);
        tag.putBoolean("AwakeUntilDay", awakeUntilDay);
        tag.putInt("Flavor", getFlavorId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            entityData.set(DATA_STATE, PaponState.valueOf(tag.getString("PaponState")).ordinal());
        } catch (IllegalArgumentException ignored) {
            entityData.set(DATA_STATE, PaponState.WALKING.ordinal());
        }
        stateTicks = tag.getInt("StateTicks");
        seatYaw = tag.getFloat("SeatYaw");
        journeyLength = Math.max(1, tag.getDouble("JourneyLength"));
        destinationRefined = tag.getBoolean("DestinationRefined");
        origin = tag.contains("Origin") ? NbtUtils.readBlockPos(tag.getCompound("Origin")) : null;
        destination = tag.contains("Destination") ? NbtUtils.readBlockPos(tag.getCompound("Destination")) : null;
        lastSafePos = tag.contains("LastSafePos") ? NbtUtils.readBlockPos(tag.getCompound("LastSafePos")) : null;
        boostTicks = tag.getInt("BoostTicks");
        pendingTired = tag.getBoolean("PendingTired");
        awakeUntilDay = tag.getBoolean("AwakeUntilDay");
        entityData.set(DATA_FLAVOR, tag.contains("Flavor") ? tag.getInt("Flavor") : -1);
        if (origin == null && destination != null) origin = blockPosition();
        vendorId = tag.hasUUID("Vendor") ? tag.getUUID("Vendor") : null;
        line = tag.contains("Line") ? TacoLine.load(tag.getCompound("Line")) : null;
        lineSlot = tag.getInt("LineSlot");
    }

    // ------------------------------------------------------------------ he does not die

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public void die(DamageSource source) { setHealth(getMaxHealth()); }
    @Override public void kill() {}
    @Override public void setHealth(float health) { super.setHealth(Math.max(health, getMaxHealth())); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeSeenAsEnemy() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean ignoreExplosion() { return true; }
    @Override public void thunderHit(ServerLevel level, LightningBolt bolt) {}
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canFreeze() { return false; }
    @Override public boolean isOnFire() { return false; }
    @Override public boolean isSensitiveToWater() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public void push(Entity entity) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean startRiding(Entity vehicle, boolean force) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public void checkDespawn() {}
    @Override public int getMaxFallDistance() { return 2; }
    @Override public boolean shouldDropExperience() { return false; }

    /** Stable id for logging and commands. */
    public UUID journeyId() { return getUUID(); }
}
