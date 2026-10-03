package com.groid.papon.entity.npc;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.Speech;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.journey.TacoLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Locale;
import java.util.UUID;

/**
 * Someone in line at the Taco Truck: one of the internet's finest (Don Pollo, EDP445, Badlands
 * Chugs, Reviewbrah) or just a hungry Papon. The vendor drives the window side of things
 * (beginOrder, waitForFood, receive, leave); this class walks the line, then takes the taco back
 * down the walkway to whoever is waiting, reacts to it in character and wanders off for good.
 *
 * Spawned from a spawn egg with no truck, a customer just stands about doing its idle.
 */
public class TacoCustomerEntity extends TacoNpc {
    private static final EntityDataAccessor<Integer> DATA_HAT =
            SynchedEntityData.defineId(TacoCustomerEntity.class, EntityDataSerializers.INT);

    /** A customer with no word from its vendor for this long gives up and goes home. */
    private static final int ORPHAN_LIMIT = 600;
    private static final double SLOT_TOLERANCE = 0.3;
    private static final double TALK_DISTANCE = 2.4;
    private static final int LEAVE_TIMEOUT = 400;
    private static final int EXIT_TIMEOUT = 500;
    private static final double EXIT_DISTANCE = 22;

    private final Customer kind;
    private final RawAnimation walk;
    private final RawAnimation idle;
    private final RawAnimation order;
    private final RawAnimation receive;
    private final RawAnimation walkTaco;
    private final RawAnimation holdTaco;
    private final RawAnimation react;

    @Nullable private UUID vendor;
    @Nullable private TacoLine line;
    private int slot;
    private int orphanTicks;
    @Nullable private UUID audience;
    @Nullable private Vec3 exit;
    /** Papons say one of several lines; Reviewbrah's rating. Picked once so a reload does not change them. */
    private int lineVariant;
    private String rating = "";

    public TacoCustomerEntity(EntityType<? extends PathfinderMob> type, Level level, Customer kind) {
        super(type, level);
        this.kind = kind;
        walk = loop("walk");
        idle = loop(kind.idleAnim());
        order = loop("order");
        receive = RawAnimation.begin().thenPlayAndHold(kind.anim("receive"));
        walkTaco = loop("walk_taco");
        holdTaco = loop("hold_taco");
        react = RawAnimation.begin().thenPlayAndHold(kind.anim(kind.reactAnim()));
        if (!level.isClientSide) {
            setHat(random.nextInt(3));
            lineVariant = random.nextInt(Customer.PAPON_LINES);
            rating = String.format(Locale.ROOT, "%.1f", 7.4 + random.nextInt(20) / 10.0);
        }
    }

    private RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop(kind.anim(name));
    }

    public Customer kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_HAT, 0);
    }

    /** Papon customers only: 0 none, 1 sombrero, 2 cap. */
    public int getHat() {
        return entityData.get(DATA_HAT);
    }

    public void setHat(int hat) {
        entityData.set(DATA_HAT, Mth.clamp(hat, 0, 2));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.025F) {
            @Override public boolean canUse() { return getCustomerState() == CustomerState.QUEUED && super.canUse(); }
        });
    }

    @Override
    protected Speech.Voice voice() {
        return kind.voice;
    }

    /** The internet's finest always show their names; the Papons only when named. */
    @Override
    public boolean shouldShowName() {
        return kind != Customer.PAPON || super.shouldShowName();
    }

    public CustomerState getCustomerState() {
        return CustomerState.byId(stateId());
    }

    private void setCustomerState(CustomerState s) {
        if (WanderingPaponEntity.debugEscort && s != getCustomerState()) {
            PaponMod.LOG.info("[debug] {} {} -> {} at {}", kind.id, getCustomerState(), s, blockPosition().toShortString());
        }
        setStateId(s.ordinal());
        if (s != CustomerState.QUEUED) getNavigation().stop();
    }

    // ------------------------------------------------------------------ called by the vendor

    public void setLineSlot(UUID vendorId, TacoLine line, int slot) {
        this.vendor = vendorId;
        this.line = line;
        this.slot = slot;
        this.orphanTicks = 0;
    }

    public boolean isWaitingInLine() {
        return getCustomerState() == CustomerState.QUEUED;
    }

    public void beginOrder() {
        setCustomerState(CustomerState.ORDERING);
        Speech.say(this, kind.color, kind.key("order"), false, kind.spanish, kind.orderTicks - 10);
    }

    public void waitForFood() {
        setCustomerState(CustomerState.WAITING_FOOD);
    }

    public void receive() {
        setCustomerState(CustomerState.RECEIVING);
    }

    public void leave() {
        setCustomerState(CustomerState.LEAVING);
        vendor = null;
    }

    /** Fed and gone without the walk-up (admin skip). */
    public void leaveWithoutTalking() {
        vendor = null;
        startExit();
    }

    /** The fraud: everybody out. */
    public void flee() {
        if (getCustomerState() == CustomerState.FLEEING) return;
        vendor = null;
        setCustomerState(CustomerState.FLEEING);
        Vec3 away = line != null ? Vec3.atLowerCornerOf(line.out().getNormal()) : getLookAngle().reverse();
        Vec3 sideways = line != null ? Vec3.atLowerCornerOf(line.side().getNormal()).scale(random.nextBoolean() ? 1 : -1)
                : Vec3.ZERO;
        exit = position().add(away.scale(EXIT_DISTANCE)).add(sideways.scale(6 + random.nextInt(6)));
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + getBbHeight() + 0.2, getZ(), 6, 0.2, 0.1, 0.2, 0.1);
        }
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    protected void serverStep(ServerLevel level) {
        CustomerState state = getCustomerState();
        switch (state) {
            case QUEUED, ORDERING, WAITING_FOOD, RECEIVING -> holdPlace();
            case LEAVING -> tickLeaving(level);
            case TALKING -> tickTalking(level);
            case EXITING, FLEEING -> tickExit(state == CustomerState.FLEEING ? 1.45 : 1.0);
        }
    }

    /** Stand on our slot facing the counter; shuffle up when the line moves. */
    private void holdPlace() {
        if (line == null) {
            getNavigation().stop();
            return;
        }
        if (vendor != null && ++orphanTicks > ORPHAN_LIMIT) {
            // our vendor is gone (removed, or unloaded for good): give up and go home
            vendor = null;
            startExit();
            return;
        }
        Vec3 target = line.slot(getCustomerState() == CustomerState.QUEUED ? slot : 0);
        double d2 = horizontalDistSqr(target);
        if (d2 > SLOT_TOLERANCE * SLOT_TOLERANCE) {
            if (d2 < 2.0 * 2.0) {
                getMoveControl().setWantedPosition(target.x, target.y, target.z, 0.8);
            } else if (getNavigation().isDone() || tickCount % 20 == 0) {
                getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            }
        } else {
            getNavigation().stop();
            if (getCustomerState() != CustomerState.QUEUED || !getLookControl().isLookingAtTarget()) {
                face(line.counter());
            } else {
                Vec3 c = line.counter();
                float yaw = (float) (Math.atan2(c.z - getZ(), c.x - getX()) * (180F / Math.PI)) - 90F;
                setYRot(yaw);
                yBodyRot = yaw;
            }
        }
    }

    private void tickLeaving(ServerLevel level) {
        Player p = audience(level);
        if (p == null || stateTicks > LEAVE_TIMEOUT) {
            startTalking(p);
            return;
        }
        // Walk to the player's side that faces the walkway, so we pass along the line.
        Vec3 spot = p.position();
        if (line != null) {
            Vec3 s = Vec3.atLowerCornerOf(line.side().getNormal());
            spot = spot.add(s.scale(TacoLine.WALKWAY * 0.75));
        }
        if (horizontalDistSqr(p.position()) < TALK_DISTANCE * TALK_DISTANCE) {
            startTalking(p);
            return;
        }
        if (getNavigation().isDone() || tickCount % 10 == 0) {
            getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }
    }

    /** Whoever we walk up to: the player nearest the line, within reason. */
    @Nullable
    private Player audience(ServerLevel level) {
        if (audience != null) {
            Player p = level.getPlayerByUUID(audience);
            if (p != null && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 48 * 48) return p;
        }
        Vec3 from = line != null ? line.slot(4) : position();
        Player best = null;
        double bestD = 40 * 40;
        for (Player p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        audience = best == null ? null : best.getUUID();
        return best;
    }

    private void startTalking(@Nullable Player p) {
        audience = p == null ? null : p.getUUID();
        setCustomerState(CustomerState.TALKING);
    }

    private void tickTalking(ServerLevel level) {
        getNavigation().stop();
        Player p = audience == null ? null : level.getPlayerByUUID(audience);
        if (p != null) face(p.position());
        for (Customer.Line l : kind.lines) {
            if (stateTicks == l.at() + 1) speak(l);
        }
        reactionEffects(level);
        if (stateTicks >= kind.reactTicks) startExit();
    }

    private void speak(Customer.Line l) {
        String key = kind.key(l.name());
        if (kind == Customer.PAPON) key = key + lineVariant;
        if (kind == Customer.REVIEWBRAH && l.name().equals("leave")) {
            Speech.say(this, kind.color, key, true, kind.spanish, l.ticks(), rating);
        } else {
            Speech.say(this, kind.color, key, true, kind.spanish, l.ticks());
        }
    }

    /** Particles the animation cannot do on its own. */
    private void reactionEffects(ServerLevel level) {
        int t = stateTicks;
        double top = getY() + getBbHeight();
        switch (kind) {
            case DON_POLLO -> {
                if (t >= 22 && t <= 62 && t % 4 == 0) {   // the salsa: steam out of his ears
                    level.sendParticles(ParticleTypes.POOF, getX(), top - 0.3, getZ(), 2, 0.35, 0.05, 0.35, 0.02);
                    if (t % 12 == 0) level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), top - 0.4, getZ(), 1, 0.1, 0, 0.1, 0.01);
                }
            }
            case EDP445 -> {
                if (t >= 29 && t <= 100 && t % 10 == 0) {   // the heat hits (react_edp445 1.45 s)
                    level.sendParticles(ParticleTypes.FLAME, getX(), top - 0.2, getZ(), 4, 0.4, 0.2, 0.4, 0.02);
                }
                if (t >= 145 && t <= 155 && t % 3 == 0) {   // E-A-G-L-E-S!
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), top + 0.3, getZ(), 3, 0.5, 0.3, 0.5, 0);
                }
            }
            case BADLANDS_CHUGS -> {
                if (t == 88) {   // the burp (react_badlands_chugs 4.4 s)
                    Vec3 f = getLookAngle().scale(0.8);
                    level.sendParticles(ParticleTypes.CLOUD, getX() + f.x, getEyeY(), getZ() + f.z, 14, 0.2, 0.2, 0.2, 0.08);
                }
            }
            case REVIEWBRAH -> {
                if (t == 40) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), top + 0.2, getZ(), 6, 0.3, 0.2, 0.3, 0);
                }
            }
            case PAPON -> {
                if (t == 12) level.sendParticles(ParticleTypes.HEART, getX(), top + 0.2, getZ(), 2, 0.3, 0.1, 0.3, 0);
            }
        }
    }

    private void startExit() {
        setCustomerState(CustomerState.EXITING);
        Vec3 away = line != null ? Vec3.atLowerCornerOf(line.out().getNormal()) : getLookAngle().reverse();
        Vec3 drift = line != null ? Vec3.atLowerCornerOf(line.side().getNormal()).scale(1.5) : Vec3.ZERO;
        exit = position().add(away.scale(EXIT_DISTANCE)).add(drift);
    }

    private void tickExit(double speed) {
        if (exit == null) startExit();
        if (horizontalDistSqr(exit) < 2.5 * 2.5 || stateTicks > EXIT_TIMEOUT) {
            discard();
            return;
        }
        if (getNavigation().isDone() || tickCount % 20 == 0) {
            getNavigation().moveTo(exit.x, exit.y, exit.z, speed);
        }
    }

    private double horizontalDistSqr(Vec3 v) {
        double dx = v.x - getX();
        double dz = v.z - getZ();
        return dx * dx + dz * dz;
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animate)
                .setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<TacoCustomerEntity> event) {
        boolean moving = event.isMoving();
        RawAnimation next = switch (getCustomerState()) {
            case QUEUED -> moving ? walk : idle;
            case ORDERING -> order;
            case WAITING_FOOD -> idle;
            case RECEIVING -> receive;
            case LEAVING -> moving ? walkTaco : holdTaco;
            case TALKING -> react;
            case EXITING, FLEEING -> moving ? walk : idle;
        };
        return event.setAndContinue(next);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CustomerState", getCustomerState().name());
        tag.putInt("Hat", getHat());
        tag.putInt("LineVariant", lineVariant);
        tag.putString("Rating", rating);
        tag.putInt("Slot", slot);
        if (vendor != null) tag.putUUID("Vendor", vendor);
        if (line != null) tag.put("Line", line.save());
        if (audience != null) tag.putUUID("Audience", audience);
        if (exit != null) tag.put("Exit", NbtUtils.writeBlockPos(BlockPos.containing(exit)));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int keep = stateTicks;
        try {
            setStateId(CustomerState.valueOf(tag.getString("CustomerState")).ordinal());
        } catch (IllegalArgumentException ignored) {
            setStateId(CustomerState.QUEUED.ordinal());
        }
        stateTicks = keep;
        setHat(tag.getInt("Hat"));
        lineVariant = tag.getInt("LineVariant");
        if (tag.contains("Rating")) rating = tag.getString("Rating");
        slot = tag.getInt("Slot");
        vendor = tag.hasUUID("Vendor") ? tag.getUUID("Vendor") : null;
        line = tag.contains("Line") ? TacoLine.load(tag.getCompound("Line")) : null;
        audience = tag.hasUUID("Audience") ? tag.getUUID("Audience") : null;
        exit = tag.contains("Exit") ? Vec3.atBottomCenterOf(NbtUtils.readBlockPos(tag.getCompound("Exit"))) : null;
    }

    /** Lets the spawner choose who this is without a separate entity class per character. */
    public static EntityType.EntityFactory<TacoCustomerEntity> factory(Customer kind) {
        return (type, level) -> new TacoCustomerEntity(type, level, kind);
    }

}
