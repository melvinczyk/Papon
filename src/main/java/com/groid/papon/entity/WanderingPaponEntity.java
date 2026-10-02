package com.groid.papon.entity;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.entity.ai.JourneyGoal;
import com.groid.papon.entity.ai.JourneyNavigation;
import com.groid.papon.journey.JourneyData;
import com.groid.papon.journey.JourneyPlanner;
import com.groid.papon.journey.TacoTruck;
import com.groid.papon.journey.Terrain;
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
 * He cannot be hurt, targeted, pushed, carried off, leashed, put in a boat, or despawned, and
 * he refuses death by any route that reaches him (see the overrides at the bottom).
 */
public class WanderingPaponEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(WanderingPaponEntity.class, EntityDataSerializers.INT);

    private static final String ANIM = "animation.wandering_papon.";
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation SIT_THEN_GRILL = RawAnimation.begin().thenPlay(ANIM + "sit_down").thenLoop(ANIM + "grill");
    private static final RawAnimation GRILL = RawAnimation.begin().thenLoop(ANIM + "grill");
    private static final RawAnimation PACK_UP = RawAnimation.begin().thenPlayAndHold(ANIM + "pack_up");
    private static final RawAnimation RECEIVE = RawAnimation.begin().thenPlayAndHold(ANIM + "receive_coupon");
    private static final RawAnimation CELEBRATE_THEN_IDLE = RawAnimation.begin().thenPlay(ANIM + "celebrate").thenLoop(ANIM + "idle");

    /** Horizontal distance from the site at which he counts as arrived. */
    private static final double ARRIVE_RADIUS = 3.5;
    /** When this close, the site is moved to the nearest flat, dry ground so the truck has somewhere to stand. */
    private static final double REFINE_RADIUS = 48;
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

    public WanderingPaponEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.0F);
        setPathfindingMalus(BlockPathTypes.WATER, 8.0F);
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
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new JourneyNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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

    public PaponState getPaponState() {
        return PaponState.byId(entityData.get(DATA_STATE));
    }

    public void setPaponState(PaponState state) {
        if (state.isSeated() && !getPaponState().isSeated()) {
            seatYaw = getYRot();
        }
        entityData.set(DATA_STATE, state.ordinal());
        stateTicks = 0;
        lonelyTicks = 0;
        if (state != PaponState.WALKING) {
            getNavigation().stop();
        }
        track();
    }

    @Nullable
    public BlockPos getDestination() {
        return destination;
    }

    public boolean isEscorted() {
        return escorted;
    }

    public double remainingDistance() {
        return destination == null ? 0 : Math.sqrt(JourneyPlanner.horizontalDistSqr(blockPosition(), destination));
    }

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
        if (destination == null) {
            beginJourney(null);
        }
        escorted = hasPlayerWithin(PaponConfig.ESCORT_RADIUS.get());
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
                if (hasPlayerWithin(PaponConfig.RESUME_RADIUS.get())) setPaponState(PaponState.PACKING_UP);
            }
            case PACKING_UP -> {
                if (stateTicks >= state.duration) setPaponState(PaponState.WALKING);
            }
            case ARRIVED -> {
                getNavigation().stop();
                if (stateTicks == TRUCK_TICK) raiseTruck(server);
                if (stateTicks >= state.duration) setPaponState(PaponState.WAITING);
            }
            case WAITING -> getNavigation().stop();
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
        if (tickCount % 10 == 0) {
            updateJourneyBar(server);
        }
    }

    private void tickWalking(ServerLevel server) {
        if (destination == null) return;
        double dist = remainingDistance();
        if (!destinationRefined && dist < REFINE_RADIUS) {
            refineDestination(server);
            dist = remainingDistance();
        }
        if (dist <= ARRIVE_RADIUS) {
            arrive(server);
            return;
        }
        if (escorted) {
            lonelyTicks = 0;
        } else if (++lonelyTicks > PaponConfig.LONELY_DELAY_TICKS.get()) {
            setPaponState(PaponState.SITTING_DOWN);
        }
    }

    /** Moves the site onto flat, dry ground near where it was picked, once that land is loaded. */
    private void refineDestination(ServerLevel server) {
        if (destination == null || !level().hasChunkAt(destination)) return;
        BlockPos dry = Terrain.findDrySpotNear(server, destination.getX(), destination.getZ(), 24, 1);
        if (dry == null) dry = Terrain.findDrySpotNear(server, destination.getX(), destination.getZ(), 32, -1);
        if (dry != null) destination = dry;
        destinationRefined = true;
        track();
    }

    /** Called by JourneyGoal when he is stuck right next to the site: close enough. */
    public void arriveNearby() {
        if (level() instanceof ServerLevel server && getPaponState() == PaponState.WALKING) arrive(server);
    }

    private void arrive(ServerLevel server) {
        getNavigation().stop();
        Direction facing = Direction.fromYRot(getYRot());
        setYRot(facing.toYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        destination = blockPosition();
        setPaponState(PaponState.ARRIVED);
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

    private void raiseTruck(ServerLevel server) {
        if (TacoTruck.existingNear(server, blockPosition(), 24) != null) return;
        TacoTruck.place(server, blockPosition(), getDirection(), 2);
        onJourneyComplete(server);
    }

    /**
     * Hook for the boss fight: the Taco Truck stands and Papon is at its window. The fight, its
     * cutscene and its trigger are not built yet.
     */
    protected void onJourneyComplete(ServerLevel server) {
        PaponMod.LOG.info("Wandering Papon {} reached the Taco Truck at {}", getUUID(), blockPosition());
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (getPaponState() == PaponState.WAITING) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.papon.waiting").withStyle(ChatFormatting.GOLD), true);
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
        if (onGround() && !underground && Terrain.isDryStandable(server, here)) {
            lastSafePos = here;
        }
        undergroundTicks = underground && getPaponState() != PaponState.WAITING ? undergroundTicks + 20 : 0;
        inWallTicks = isInWall() ? inWallTicks + 20 : 0;
        if (getY() < server.getMinBuildHeight() || isInLava() || undergroundTicks >= 60 || inWallTicks >= 20) {
            rescueToSurface(server);
        }
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
        if (!PaponConfig.SHOW_JOURNEY_BAR.get() || destination == null) {
            journeyBar.removeAllPlayers();
            return;
        }
        PaponState state = getPaponState();
        int left = (int) Math.ceil(remainingDistance());
        Component name = switch (state) {
            case RECEIVING -> Component.translatable("bar.papon.receiving");
            case WALKING -> Component.translatable("bar.papon.walking", left);
            case SITTING_DOWN, GRILLING -> Component.translatable("bar.papon.grilling", left);
            case PACKING_UP -> Component.translatable("bar.papon.packing", left);
            case ARRIVED, WAITING -> Component.translatable("bar.papon.arrived");
        };
        journeyBar.setName(name);
        journeyBar.setColor(state == PaponState.GRILLING || state == PaponState.SITTING_DOWN
                ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        journeyBar.setProgress(state == PaponState.ARRIVED || state == PaponState.WAITING ? 1F
                : Mth.clamp(1F - (float) (left / journeyLength), 0F, 1F));
        for (ServerPlayer p : new ArrayList<>(journeyBar.getPlayers())) {
            if (p.level() != server || p.isRemoved() || p.distanceToSqr(this) > 56 * 56) journeyBar.removePlayer(p);
        }
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(this) < 40 * 40) journeyBar.addPlayer(p);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED) {
            PaponMod.LOG.warn("Something tried to kill Wandering Papon {}; he declined", getUUID());
            return;
        }
        journeyBar.removeAllPlayers();
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel server) {
            JourneyData.get(server).forget(getUUID());
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ client

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && getPaponState() == PaponState.GRILLING) {
            grillParticles();
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
        if (random.nextInt(40) == 0) {
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, gx, gy + 0.1, gz, 0, 0.02, 0);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animate)
                .setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<WanderingPaponEntity> event) {
        AnimationController<WanderingPaponEntity> controller = event.getController();
        RawAnimation current = controller.getCurrentRawAnimation();
        RawAnimation next = switch (getPaponState()) {
            case RECEIVING -> RECEIVE;
            case WALKING -> event.isMoving() ? WALK : IDLE;
            case SITTING_DOWN -> SIT_THEN_GRILL;
            // A client that watched him sit down is already in the loop that follows; one that
            // arrives later starts straight in the loop.
            case GRILLING -> SIT_THEN_GRILL.equals(current) ? SIT_THEN_GRILL : GRILL;
            case PACKING_UP -> PACK_UP;
            case ARRIVED -> CELEBRATE_THEN_IDLE;
            case WAITING -> CELEBRATE_THEN_IDLE.equals(current) ? CELEBRATE_THEN_IDLE : IDLE;
        };
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
            default -> { return; }
        }
        level().playLocalSound(getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume,
                pitch * (0.95F + random.nextFloat() * 0.1F), false);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

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
        if (origin == null && destination != null) origin = blockPosition();
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
    public UUID journeyId() {
        return getUUID();
    }
}
