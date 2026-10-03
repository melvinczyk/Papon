package com.groid.papon.entity.npc;

import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.entity.Speech;
import com.groid.papon.journey.TacoLine;
import com.groid.papon.journey.TacoTruck;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Gerbin, the chort who washes up in the back of the Taco Truck, behind the chef. He works a
 * loop: chops lettuce at the prep counter, washes tomatoes in the sink, now and then peeks out
 * at the customers and waves. When the coupon turns out to be a fraud he jumps out of his skin,
 * and he spends the boss fight cowering behind the counter, going back to work once it is over.
 *
 * His stations come from the truck's line ({@link TacoTruck#gerbinChop}, {@link TacoTruck#gerbinSink}),
 * his props (board, lettuce, sink suds...) are in his own model, placed to line up with the
 * truck's blocks when he stands on his spot. He wears his name like a name tag, always.
 */
public class GerbinEntity extends TacoNpc {
    private static final String ANIM = "animation.gerbin.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation CHOP = RawAnimation.begin().thenLoop(ANIM + "chop");
    private static final RawAnimation WASH = RawAnimation.begin().thenLoop(ANIM + "wash");
    private static final RawAnimation LOOK = RawAnimation.begin().thenPlayAndHold(ANIM + "look");
    private static final RawAnimation SHOCKED = RawAnimation.begin().thenPlay(ANIM + "shocked").thenLoop(ANIM + "cower");
    private static final RawAnimation COWER = RawAnimation.begin().thenLoop(ANIM + "cower");

    /** Snap onto a station from this close. */
    private static final double ARRIVE = 0.35;
    private static final int WALK_TIMEOUT = 140;
    /** How far he looks for trouble (an angry chef, a loose boss). */
    private static final double TROUBLE_RADIUS = 40;

    @Nullable private TacoLine line;
    private boolean chopNext = true;
    private int calmTicks;
    private boolean troubleSeen;

    public GerbinEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (!level.isClientSide) {
            setCustomName(Component.literal("Gerbin"));
            setCustomNameVisible(true);
        }
    }

    @Override
    protected Speech.Voice voice() {
        return new Speech.Voice(SoundEvents.FOX_AMBIENT, 1.5F, 0.5F, 3);
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    public GerbinState getGerbinState() {
        return GerbinState.byId(stateId());
    }

    private void setGerbinState(GerbinState s) {
        setStateId(s.ordinal());
        if (s != GerbinState.TO_CHOP && s != GerbinState.TO_SINK) getNavigation().stop();
    }

    /** Clocks in at a truck. */
    public void hire(TacoLine line) {
        this.line = line;
        chopNext = true;
        setGerbinState(GerbinState.TO_CHOP);
    }

    // ------------------------------------------------------------------ the shift

    @Override
    protected void serverStep(ServerLevel level) {
        GerbinState state = getGerbinState();
        boolean trouble = trouble(level);
        if (trouble && !state.scared()) {
            setGerbinState(GerbinState.SHOCKED);
            Speech.say(this, ChatFormatting.DARK_GRAY, "speech.papon.gerbin.shocked", false, false, 40);
            return;
        }
        if (line == null) {
            getNavigation().stop();
            return;
        }
        switch (state) {
            case IDLE -> setGerbinState(chopNext ? GerbinState.TO_CHOP : GerbinState.TO_SINK);
            case TO_CHOP -> walkTo(TacoTruck.gerbinChop(line), GerbinState.CHOP);
            case TO_SINK -> walkTo(TacoTruck.gerbinSink(line), GerbinState.WASH);
            case CHOP -> {
                hold(TacoTruck.gerbinChop(line));
                if (stateTicks % 120 > 18 && stateTicks % 120 < 80 && stateTicks % 4 == 0) chopBits(level);
                if (stateTicks >= GerbinState.CHOP.duration) next(level, false);
            }
            case WASH -> {
                hold(TacoTruck.gerbinSink(line));
                int t = stateTicks % 100;
                if (t > 20 && t < 64 && t % 6 == 0) splash(level);
                if (stateTicks >= GerbinState.WASH.duration) next(level, true);
            }
            case LOOK -> {
                if (stateTicks == 18) Speech.say(this, ChatFormatting.DARK_GRAY, "speech.papon.gerbin.wave" + random.nextInt(3), false, false, 40);
                if (stateTicks >= GerbinState.LOOK.duration) setGerbinState(chopNext ? GerbinState.TO_CHOP : GerbinState.TO_SINK);
            }
            case SHOCKED -> {
                if (stateTicks >= GerbinState.SHOCKED.duration) setGerbinState(GerbinState.COWER);
            }
            case COWER -> {
                calmTicks = trouble ? 0 : calmTicks + 1;
                if (calmTicks > 200) setGerbinState(chopNext ? GerbinState.TO_CHOP : GerbinState.TO_SINK);
            }
        }
    }

    /** Done at one station: maybe a peek at the customers, then off to the other one. */
    private void next(ServerLevel level, boolean chop) {
        chopNext = chop;
        setGerbinState(random.nextInt(3) == 0 ? GerbinState.LOOK : (chop ? GerbinState.TO_CHOP : GerbinState.TO_SINK));
    }

    private void walkTo(TacoTruck.Station s, GerbinState then) {
        Vec3 p = s.pos();
        if (horizontalDistSqr(p) < ARRIVE * ARRIVE || stateTicks > WALK_TIMEOUT) {
            moveTo(p.x, p.y, p.z, s.yaw(), 0);
            setGerbinState(then);
            return;
        }
        if (horizontalDistSqr(p) < 1.5 * 1.5) {
            getMoveControl().setWantedPosition(p.x, p.y, p.z, 0.6);
        } else if (getNavigation().isDone() || tickCount % 20 == 0) {
            getNavigation().moveTo(p.x, p.y, p.z, 0.8);
        }
    }

    private void hold(TacoTruck.Station s) {
        getNavigation().stop();
        if (horizontalDistSqr(s.pos()) > 0.25) moveTo(s.pos().x, s.pos().y, s.pos().z, s.yaw(), 0);
        setYRot(s.yaw());
        yBodyRot = s.yaw();
        yHeadRot = s.yaw();
    }

    private double horizontalDistSqr(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        return dx * dx + dz * dz;
    }

    /** The chef has lost it, or there is a boss loose: time to hide. */
    private boolean trouble(ServerLevel level) {
        if (tickCount % 10 != 0) return troubleSeen;
        var box = getBoundingBox().inflate(TROUBLE_RADIUS);
        troubleSeen = !level.getEntitiesOfClass(PaponBossEntity.class, box, PaponBossEntity::isAlive).isEmpty()
                || !level.getEntitiesOfClass(TacoVendorEntity.class, box, v -> v.getVendorState().unmasking()).isEmpty();
        return troubleSeen;
    }

    private void chopBits(ServerLevel level) {
        Vec3 at = position().add(getLookAngle().multiply(0.8, 0, 0.8)).add(0, 1.1, 0);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.KELP)), at.x, at.y, at.z,
                2, 0.12, 0.05, 0.12, 0.05);
    }

    private void splash(ServerLevel level) {
        Vec3 at = position().add(getLookAngle().multiply(0.9, 0, 0.9)).add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 6, 0.2, 0.05, 0.2, 0.1);
        if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 3, 0.2, 0.05, 0.2, 0.02);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            String key = getGerbinState().scared() ? "message.papon.gerbin_scared" : "message.papon.gerbin_busy";
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::animate).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<GerbinEntity> event) {
        RawAnimation current = event.getController().getCurrentRawAnimation();
        RawAnimation next = switch (getGerbinState()) {
            case IDLE -> IDLE;
            case TO_CHOP, TO_SINK -> event.isMoving() ? WALK : IDLE;
            case CHOP -> CHOP;
            case WASH -> WASH;
            case LOOK -> LOOK;
            case SHOCKED -> SHOCKED;
            case COWER -> SHOCKED.equals(current) ? SHOCKED : COWER;
        };
        return event.setAndContinue(next);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("GerbinState", getGerbinState().name());
        if (line != null) tag.put("Line", line.save());
        tag.putBoolean("ChopNext", chopNext);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int keep = stateTicks;
        try {
            setGerbinState(GerbinState.valueOf(tag.getString("GerbinState")));
        } catch (IllegalArgumentException ignored) {
            setGerbinState(GerbinState.IDLE);
        }
        stateTicks = keep;
        line = tag.contains("Line") ? TacoLine.load(tag.getCompound("Line")) : null;
        chopNext = tag.getBoolean("ChopNext");
    }
}
