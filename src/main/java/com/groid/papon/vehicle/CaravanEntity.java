package com.groid.papon.vehicle;

import com.groid.papon.PaponConfig;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.TacoNpc;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModDamage;
import com.groid.papon.registry.ModEffects;
import com.groid.papon.registry.ModItems;
import com.groid.papon.registry.ModSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Papon's 2001 Dodge Caravan. A real vehicle: anyone can climb in (right-click), and whoever sits
 * in the driver's seat drives it with the movement keys (W/S throttle and reverse, A/D steer, jump
 * honks). Chef Papon drives it too, through {@link #autopilot}: his ram attack, forwards and in
 * reverse. Nothing breaks it; only /kill (or sneak-using its keys on it) removes it.
 *
 * Physics are a simple bicycle model run by whoever controls it (the driving player's client,
 * or the server for Papon and for a parked van): a signed speed along the van's heading,
 * accelerated, braked and coasted; the yaw rate follows the steering input and the speed. It
 * climbs steps up to a block and a half. The server measures the real speed from position changes (players
 * drive client-side) and uses it for everything with consequences: running people over, crashes,
 * tyre screech.
 *
 * Getting hit by a moving van ignores shields ({@link ModDamage#CARAVAN} is in
 * minecraft:bypasses_shield) and scales with speed. Every hit stuns ({@link ModEffects#STUNNED}: flat on
 * the ground, no moving, a hard screen shake): the front bumper runs you over where you stand, the
 * rear bumper (backing into you) launches you a long way first.
 */
public class CaravanEntity extends LivingEntity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_ENGINE =
            SynchedEntityData.defineId(CaravanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DOOR =
            SynchedEntityData.defineId(CaravanEntity.class, EntityDataSerializers.BOOLEAN);

    /** Half the van's length and width in blocks (the model is 89 x 34 px). */
    public static final double HALF_LENGTH = 2.78;
    public static final double HALF_WIDTH = 1.08;
    /**
     * Seats in van space: x to the left, z forward (driver first; US van, driver on the left). The
     * model faces -Z, so its front buckets (cushions z -14..-6 px) are at +0.62 here, the middle
     * bench at -0.47 and the back bench at -1.75.
     */
    private static final double[][] SEATS = {{0.5, 0.62}, {-0.5, 0.62}, {0.5, -0.47}, {-0.5, -0.47}, {0.0, -1.75}};
    public static final double SEAT_HEIGHT = 0.5;

    private static final double PLAYER_TOP = 0.78;
    private static final double BOSS_TOP = 1.0;
    private static final double PLAYER_REVERSE = 0.3;
    private static final double BOSS_REVERSE = 0.62;
    private static final double ACCEL = 0.03;
    private static final double BRAKE = 0.075;
    private static final double COAST = 0.972;
    private static final float MAX_TURN = 4.4F;
    private static final double WHEEL_RADIUS = 6.5 / 16.0;

    private static final RawAnimation PARKED = RawAnimation.begin().thenLoop("animation.caravan.parked");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.caravan.engine_idle");
    private static final RawAnimation DOOR_OPEN = RawAnimation.begin().thenPlayAndHold("animation.caravan.door_open");
    private static final RawAnimation DOOR_CLOSE = RawAnimation.begin().thenPlayAndHold("animation.caravan.door_close");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Signed speed along the heading, blocks per tick, for whoever runs the physics. */
    private double speed;

    // autopilot (server, Chef Papon)
    @Nullable private Vec3 autoTarget;
    private boolean autoReverse;
    private boolean autoLock;
    private boolean autoBrake;

    // server bookkeeping
    @Nullable private Vec3 lastPos;
    private double measured;
    private double measuredPrev;
    private float lastYaw;
    private int doorTicks;
    private int screechCooldown;
    private int hornCooldown;
    private int burnout;
    private boolean crashed;
    /** Set by the physics when it drove into a wall fast; turned into a crash by the server tick. */
    private boolean wallHit;
    private final Map<Integer, Integer> hitCooldown = new HashMap<>();

    // client visuals
    public float wheelSpin;
    public float wheelSpinO;
    public float steerAngle;
    public float bodyRoll;
    public float bodyPitch;
    private double clientAlongPrev;
    private boolean doorWasOpened;
    private boolean wasJumping;

    public CaravanEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        setMaxUpStep(1.55F);     // drives up a block and a half (a slab on a block, a path edge)
        lastYaw = getYRot();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ENGINE, false);
        entityData.define(DATA_DOOR, false);
    }

    public boolean isEngineOn() {
        return entityData.get(DATA_ENGINE);
    }

    public boolean isDoorOpen() {
        return entityData.get(DATA_DOOR);
    }

    // ------------------------------------------------------------------ geometry

    public Vec3 forward() {
        float r = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    public Vec3 left() {
        float r = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(r), 0, Mth.sin(r));
    }

    /** A point in van space (x left, z forward) in the world, at the van's base. */
    public Vec3 local(double x, double y, double z) {
        Vec3 f = forward();
        Vec3 l = left();
        return position().add(f.x * z + l.x * x, y, f.z * z + l.z * x);
    }

    /** Where to stand to get in behind the wheel: outside the driver's door. */
    public Vec3 driverDoor() {
        return local(HALF_WIDTH + 1.0, 0, 0.6);
    }

    /** The van's real speed along its heading, blocks per tick (measured on the server). */
    public double speed() {
        return level().isClientSide ? speed : measured;
    }

    // ------------------------------------------------------------------ driving

    /** Chef Papon at the wheel: drive at (or, in reverse, back into) {@code target}. */
    public void autopilot(Vec3 target, boolean reverse, boolean lockSteering) {
        autoTarget = target;
        autoReverse = reverse;
        autoLock = lockSteering;
        autoBrake = false;
    }

    /** Stand on the brakes. */
    public void brake() {
        autoBrake = true;
    }

    public void releaseAutopilot() {
        autoTarget = null;
        autoBrake = false;
    }

    /** True once after the van hit a wall hard. */
    public boolean consumeCrash() {
        boolean c = crashed;
        crashed = false;
        return c;
    }

    /** Revs the engine and spins the back wheels where it stands (the boss's wind-up). */
    public void burnout(int ticks) {
        burnout = ticks;
        level().playSound(null, getX(), getY(), getZ(), ModSounds.CARAVAN_REV.get(), SoundSource.HOSTILE, 2.5F, 1.0F);
    }

    public void honk() {
        if (hornCooldown > 0) return;
        hornCooldown = 12;
        level().playSound(null, getX(), getY() + 1, getZ(), ModSounds.CARAVAN_HORN.get(), SoundSource.NEUTRAL, 3.0F, 1.0F);
    }

    @Override
    public void travel(Vec3 input) {
        if (!isControlledByLocalInstance()) return;
        LivingEntity driver = getControllingPassenger();
        float throttle = 0;
        float steer = 0;
        boolean brake = false;
        double top = PLAYER_TOP;
        double back = PLAYER_REVERSE;
        if (driver instanceof Player p) {
            throttle = p.zza;
            steer = p.xxa;
        } else if (driver instanceof PaponBossEntity) {
            top = BOSS_TOP;
            back = BOSS_REVERSE;
            if (autoBrake || autoTarget == null) {
                brake = true;
            } else {
                double yawTo = Mth.atan2(autoTarget.z - getZ(), autoTarget.x - getX()) * Mth.RAD_TO_DEG - 90.0;
                double want = autoReverse ? yawTo + 180.0 : yawTo;
                double diff = Mth.wrapDegrees(want - getYRot());
                steer = autoLock ? 0F : (float) Mth.clamp(-diff / 18.0 * (autoReverse ? -1 : 1), -1, 1);
                throttle = autoReverse ? -1F : 1F;
            }
        } else {
            brake = true;
        }
        if (burnout > 0) {
            throttle = 0;
            brake = true;
        }

        if (throttle > 0.01F) {
            speed += speed < -0.02 ? BRAKE : ACCEL * throttle * Math.max(0.12, 1 - speed / top);
        } else if (throttle < -0.01F) {
            speed -= speed > 0.02 ? BRAKE : ACCEL * 0.8 * -throttle * Math.max(0.12, 1 + speed / back);
        } else {
            speed *= COAST;
        }
        if (brake) speed *= 0.8;
        if (isInWater()) speed *= 0.86;
        if (!onGround()) speed *= 0.995;
        speed = Mth.clamp(speed, -back, top);
        if (Math.abs(speed) < 0.004 && Math.abs(throttle) < 0.01F) speed = 0;

        double grip = Math.min(1.0, Math.abs(speed) / 0.2);
        float yawRate = (float) (steer * MAX_TURN * grip * Math.signum(speed));
        setYRot(getYRot() - yawRate);
        yBodyRot = getYRot();
        yHeadRot = getYRot();

        Vec3 f = forward();
        double vy = getDeltaMovement().y;
        vy = (vy - 0.08) * 0.98;
        Vec3 vel = new Vec3(f.x * speed, vy, f.z * speed);
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);
        if (horizontalCollision) {
            if (Math.abs(speed) > 0.35 && !level().isClientSide) wallHit = true;
            speed = Math.abs(speed) > 0.35 ? -speed * 0.25 : speed * 0.4;
        }
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        if (level().isClientSide) {
            // jump is the horn
            boolean jumping = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> com.groid.papon.client.ClientHooks.isJumping(player)) == Boolean.TRUE;
            if (jumping && !wasJumping) PaponNetwork.CHANNEL.sendToServer(new PaponNetwork.Horn());
            wasJumping = jumping;
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        return Vec3.ZERO;
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (level().isClientSide) {
            clientVisuals();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.groid.papon.client.ClientHooks.caravanTick(this));
            return;
        }
        ServerLevel level = (ServerLevel) level();
        LivingEntity driver = getControllingPassenger();
        entityData.set(DATA_ENGINE, driver != null);
        if (doorTicks > 0 && --doorTicks == 0) entityData.set(DATA_DOOR, false);
        if (hornCooldown > 0) hornCooldown--;
        if (screechCooldown > 0) screechCooldown--;
        hitCooldown.replaceAll((k, v) -> v - 1);
        hitCooldown.values().removeIf(v -> v <= 0);

        Vec3 pos = position();
        measuredPrev = measured;
        measured = lastPos == null ? 0 : pos.subtract(lastPos).dot(forward());
        float yawDelta = Mth.wrapDegrees(getYRot() - lastYaw);
        lastPos = pos;
        lastYaw = getYRot();

        // hard stop against something: a crash (the driving player's client cannot tell us, so
        // a sudden loss of speed counts too)
        boolean crash = wallHit || (driver instanceof Player && Math.abs(measuredPrev) > 0.45 && Math.abs(measured) < 0.08);
        wallHit = false;
        if (crash) {
            crashed = true;
            level.playSound(null, getX(), getY(), getZ(), ModSounds.CARAVAN_CRASH.get(), SoundSource.NEUTRAL, 2.5F, 0.9F + random.nextFloat() * 0.2F);
            Vec3 nose = local(0, 0.8, HALF_LENGTH * Math.signum(measuredPrev == 0 ? 1 : measuredPrev));
            level.sendParticles(ParticleTypes.CRIT, nose.x, nose.y, nose.z, 20, 0.6, 0.4, 0.6, 0.3);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, nose.x, nose.y, nose.z, 8, 0.4, 0.3, 0.4, 0.02);
            PaponNetwork.shake(level, pos, 10, 0.25F, 12);
        }
        if (screechCooldown == 0 && Math.abs(measured) > 0.42 && Math.abs(yawDelta) > 3.0F) {
            screechCooldown = 30;
            level.playSound(null, getX(), getY(), getZ(), ModSounds.CARAVAN_SCREECH.get(), SoundSource.NEUTRAL, 2.0F, 1.0F);
        }
        if (Math.abs(yawDelta) > 3.0F && Math.abs(measured) > 0.4 || burnout > 0) tyreSmoke(level);
        if (burnout > 0) burnout--;
        if (driver != null && tickCount % 8 == 0) {
            Vec3 pipe = local(-0.7, 0.25, -HALF_LENGTH - 0.1);
            level.sendParticles(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
        if (Math.abs(measured) > 0.16) runOver(level, measured, driver);
    }

    private void tyreSmoke(ServerLevel level) {
        for (double side : new double[]{0.9, -0.9}) {
            Vec3 w = local(side, 0.1, -1.7);
            level.sendParticles(ParticleTypes.CLOUD, w.x, w.y, w.z, 2, 0.15, 0.05, 0.15, 0.02);
        }
    }

    /** Anyone the van's body sweeps through gets hit, harder the faster it goes. */
    private void runOver(ServerLevel level, double v, @Nullable LivingEntity driver) {
        Vec3 f = forward();
        Vec3 l = left();
        AABB box = getBoundingBox().inflate(HALF_LENGTH, 0.5, HALF_LENGTH);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !hasPassenger(e) && !(e instanceof CaravanEntity))) {
            if (e instanceof Player p && (p.isSpectator() || p.isCreative())) continue;
            if (e instanceof TacoNpc || e instanceof WanderingPaponEntity) continue;
            if (hitCooldown.containsKey(e.getId())) continue;
            Vec3 r = e.position().subtract(position());
            double along = r.x * f.x + r.z * f.z;
            double lat = r.x * l.x + r.z * l.z;
            double pad = e.getBbWidth() / 2;
            if (Math.abs(lat) > HALF_WIDTH + pad || Math.abs(along) > HALF_LENGTH + pad || r.y > 2.0 || r.y < -1.6) continue;
            // the bumper doing the work: the end the van is moving toward
            if (along * Math.signum(v) < -HALF_LENGTH * 0.4) continue;
            ram(level, e, v, lat, driver);
        }
    }

    private void ram(ServerLevel level, LivingEntity e, double v, double lat, @Nullable LivingEntity driver) {
        hitCooldown.put(e.getId(), 16);
        float top = (float) (driver instanceof PaponBossEntity ? BOSS_TOP : PLAYER_TOP);
        float damage = (float) (PaponConfig.VAN_RAM_DAMAGE.get() * Mth.clamp(Math.abs(v) / top, 0.25, 1.15));
        if (driver instanceof Player && e instanceof PaponBossEntity) damage *= 0.8F;
        DamageSource source = ModDamage.caravan(level, this, driver);
        e.hurt(source, damage);
        Vec3 f = forward().scale(Math.signum(v));
        Vec3 side = left().scale(Math.signum(lat == 0 ? 1 : lat));
        boolean rear = v < 0;
        Vec3 push = rear
                // backed into: the rear bumper sends you flying
                ? f.scale(2.2 + Math.abs(v) * 3.0).add(side.scale(0.3)).add(0, 0.75 + Math.abs(v) * 0.4, 0)
                // run over: bowled over and flattened where you stood, a short shove and down
                : f.scale(0.35 + Math.abs(v) * 0.6).add(side.scale(0.5)).add(0, 0.12, 0);
        e.setDeltaMovement(push);
        e.hurtMarked = true;
        e.hasImpulse = true;
        int stun = PaponConfig.VAN_STUN_TICKS.get();
        if (stun > 0) e.addEffect(new MobEffectInstance(ModEffects.STUNNED.get(), stun, 0, false, false, true));
        if (e instanceof Player p) p.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) PaponNetwork.shake(sp, rear ? 1.1F : 0.9F, 30);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.CARAVAN_HIT.get(), SoundSource.NEUTRAL, 2.0F,
                0.9F + random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 16, 0.4, 0.4, 0.4, 0.4);
        PaponNetwork.shake(level, e.position(), 8, 0.18F, 8);
        if (driver == null || driver instanceof PaponBossEntity) speed *= 0.85;
    }

    private void clientVisuals() {
        wheelSpinO = wheelSpin;
        Vec3 d = new Vec3(getX() - xo, 0, getZ() - zo);
        double along = d.dot(forward());
        wheelSpin += (float) (along / WHEEL_RADIUS);
        float yawDelta = Mth.wrapDegrees(getYRot() - yRotO);
        float wantSteer = Math.abs(along) < 0.01 ? steerAngle * 0.9F
                : Mth.clamp(yawDelta / (float) Math.max(0.15, Math.abs(along)) * 3.0F * (float) -Math.signum(along), -30F, 30F);
        steerAngle += (wantSteer - steerAngle) * 0.35F;
        bodyRoll += (Mth.clamp(yawDelta * (float) Math.abs(along) * 2.2F, -6F, 6F) - bodyRoll) * 0.25F;
        double accel = along - clientAlongPrev;
        clientAlongPrev = along;
        bodyPitch += (Mth.clamp((float) accel * 60F, -4F, 4F) - bodyPitch) * 0.2F;
        if (isDoorOpen()) doorWasOpened = true;
    }

    // ------------------------------------------------------------------ passengers

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < SEATS.length;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        return first instanceof Player || first instanceof PaponBossEntity ? (LivingEntity) first : null;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        int i = getPassengers().indexOf(passenger);
        if (i < 0) return;
        double[] seat = SEATS[Math.min(i, SEATS.length - 1)];
        Vec3 at = local(seat[0], SEAT_HEIGHT + passenger.getMyRidingOffset(), seat[1]);
        move.accept(passenger, at.x, at.y, at.z);
        if (passenger instanceof PaponBossEntity boss) {
            boss.setYRot(getYRot());
            boss.setYBodyRot(getYRot());
            boss.setYHeadRot(getYRot());
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (passenger instanceof PaponBossEntity) passenger.setYRot(getYRot());
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        openDoor();
        if (!level().isClientSide && getPassengers().size() == 1) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.CARAVAN_START.get(), SoundSource.NEUTRAL, 2.0F, 1.0F);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        openDoor();
        if (getPassengers().isEmpty()) {
            speed *= 0.5;
            releaseAutopilot();
        }
    }

    private void openDoor() {
        if (level().isClientSide) return;
        entityData.set(DATA_DOOR, true);
        doorTicks = 14;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int i = getPassengers().indexOf(passenger);
        double side = (i < 0 || i % 2 == 0) ? 1 : -1;
        Vec3 out = local(side * (HALF_WIDTH + 0.9), 0, i >= 2 ? -0.5 : 0.6);
        return new Vec3(out.x, getY(), out.z);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;     // keys: sneak-use to pocket the van
        if (hasPassenger(player)) return InteractionResult.PASS;
        if (getControllingPassenger() instanceof PaponBossEntity) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.CARAVAN_KEYS.get());
    }

    // ------------------------------------------------------------------ it is a van

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!level().isClientSide && source.getEntity() instanceof Player) {
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.3F, 1.6F);
        }
        return false;
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public void push(Entity entity) {}
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canFreeze() { return false; }
    @Override public boolean shouldShowName() { return hasCustomName() && isCustomNameVisible(); }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isAffectedByPotions() { return false; }
    @Override public boolean attackable() { return false; }
    @Override protected float tickHeadTurn(float yaw, float walked) { yBodyRot = getYRot(); return walked; }

    @Override public Iterable<ItemStack> getArmorSlots() { return NonNullList.withSize(4, ItemStack.EMPTY); }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lastYaw = getYRot();
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "engine", 4, this::animateEngine));
        controllers.add(new AnimationController<>(this, "door", 0, this::animateDoor).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animateEngine(AnimationState<CaravanEntity> event) {
        return event.setAndContinue(isEngineOn() ? IDLE : PARKED);
    }

    private PlayState animateDoor(AnimationState<CaravanEntity> event) {
        if (isDoorOpen()) return event.setAndContinue(DOOR_OPEN);
        if (doorWasOpened) return event.setAndContinue(DOOR_CLOSE);
        return PlayState.STOP;
    }

    private void onSoundCue(SoundKeyframeEvent<CaravanEntity> event) {
        String cue = event.getKeyframeData().getSound();
        var sound = switch (cue) {
            case "door_open" -> ModSounds.CARAVAN_DOOR_OPEN.get();
            case "door_close" -> ModSounds.CARAVAN_DOOR_CLOSE.get();
            default -> null;
        };
        if (sound != null) level().playLocalSound(getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F, false);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
