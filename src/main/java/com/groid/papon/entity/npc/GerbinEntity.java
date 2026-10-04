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
 *
 * When Chef Papon is beaten, Gerbin comes out from behind the counter with a present for each
 * winner ({@link #reward}): a body pillow, hugged to his chest all the way over, held out at arm's
 * length with his eyes on the floor. Then back to the dishes.
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
    private static final RawAnimation CARRY = RawAnimation.begin().thenLoop(ANIM + "carry");
    private static final RawAnimation GIVE = RawAnimation.begin().thenPlayAndHold(ANIM + "give");
    /** How far he will go to find a truck's winners, and how long he tries to reach one. */
    private static final double REWARD_RADIUS = 160;
    private static final int DELIVER_TIMEOUT = 900;

    /** Snap onto a station from this close. */
    private static final double ARRIVE = 0.35;
    private static final int WALK_TIMEOUT = 140;
    /** How far he looks for trouble (an angry chef, a loose boss). */
    private static final double TROUBLE_RADIUS = 40;

    @Nullable private TacoLine line;
    private boolean chopNext = true;
    private int calmTicks;
    private boolean troubleSeen;
    /** Winners still waiting for their pillow, in order. */
    private final java.util.ArrayDeque<java.util.UUID> owed = new java.util.ArrayDeque<>();
    private double lastDeliverDist = Double.MAX_VALUE;

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
        if (s != GerbinState.TO_CHOP && s != GerbinState.TO_SINK && s != GerbinState.DELIVER) getNavigation().stop();
    }

    /** Carrying the pillow (the renderer draws it in his arms). */
    public boolean carryingPillow() {
        GerbinState s = getGerbinState();
        return s == GerbinState.DELIVER || s == GerbinState.GIVE && stateTicks < 30;
    }

    /**
     * Chef Papon has fallen: the nearest Gerbin (within {@value #REWARD_RADIUS} blocks of where it
     * happened) brings every one of the fighters a body pillow. With no Gerbin about, it turns up
     * in their inventory with a note.
     */
    public static void reward(ServerLevel level, Vec3 near, java.util.List<net.minecraft.server.level.ServerPlayer> fighters) {
        if (fighters.isEmpty()) return;
        GerbinEntity g = level.getEntitiesOfClass(GerbinEntity.class, new net.minecraft.world.phys.AABB(near, near).inflate(REWARD_RADIUS),
                GerbinEntity::isAlive).stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(near))).orElse(null);
        for (var p : fighters) {
            if (g != null) {
                if (!g.owed.contains(p.getUUID())) g.owed.add(p.getUUID());
            } else {
                hand(p);
                p.displayClientMessage(Component.translatable("message.papon.pillow_note").withStyle(ChatFormatting.LIGHT_PURPLE), false);
                TacoVendorEntity.welcomeBack(level, p, 80);
            }
        }
        if (g != null && !g.getGerbinState().equals(GerbinState.DELIVER) && g.getGerbinState() != GerbinState.GIVE) {
            g.setGerbinState(GerbinState.DELIVER);
        }
    }

    private static void hand(Player p) {
        ItemStack pillow = new ItemStack(com.groid.papon.registry.ModItems.BODY_PILLOW.get());
        if (!p.getInventory().add(pillow)) p.drop(pillow, false);
    }

    @Nullable
    private Player owedPlayer(ServerLevel level) {
        while (!owed.isEmpty()) {
            Player p = level.getPlayerByUUID(owed.peek());
            if (p != null && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < REWARD_RADIUS * REWARD_RADIUS * 4) return p;
            owed.poll();
        }
        return null;
    }

    /** Off to the next winner with a pillow under his arms. */
    private void deliver(ServerLevel level) {
        Player p = owedPlayer(level);
        if (p == null) {
            backToWork();
            return;
        }
        if (distanceToSqr(p) < 2.2 * 2.2) {
            getNavigation().stop();
            setGerbinState(GerbinState.GIVE);
            return;
        }
        if (stateTicks > DELIVER_TIMEOUT) {
            // could not get there: the present turns up anyway
            hand(p);
            p.displayClientMessage(Component.translatable("message.papon.pillow_note").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) TacoVendorEntity.welcomeBack(level, sp, 60);
            owed.poll();
            setGerbinState(GerbinState.DELIVER);
            return;
        }
        if (stateTicks == 1) Speech.say(this, ChatFormatting.DARK_GRAY, "speech.papon.gerbin.coming", false, false, 40);
        if (getNavigation().isDone() || stateTicks % 20 == 0) getNavigation().moveTo(p, 0.9);
        if (stateTicks % 60 == 0) {
            // stuck behind the counter (or anywhere): he slips out and is just there, behind them
            double d = distanceTo(p);
            if (d > lastDeliverDist - 0.5) {
                Vec3 back = p.position().subtract(p.getLookAngle().multiply(2.5, 0, 2.5));
                net.minecraft.core.BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        net.minecraft.core.BlockPos.containing(back));
                getNavigation().stop();
                moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, getYRot(), 0);
                level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            }
            lastDeliverDist = distanceTo(p);
        }
    }

    private void give(ServerLevel level) {
        Player p = owedPlayer(level);
        getNavigation().stop();
        if (p != null) {
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        if (stateTicks == 6) Speech.say(this, ChatFormatting.DARK_GRAY, "speech.papon.gerbin.pillow", false, false, 60);
        if (stateTicks == 30 && p != null) {
            hand(p);
            owed.poll();
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.8F);
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight() + 0.3, getZ(), 3, 0.3, 0.2, 0.3, 0.02);
            p.displayClientMessage(Component.translatable("message.papon.pillow_given").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            // ...and then back to the truck, where the chef is waiting (TacoVendorEntity: he is a friend now)
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) TacoVendorEntity.welcomeBack(level, sp, 50);
        }
        if (stateTicks >= GerbinState.GIVE.duration) {
            if (!owed.isEmpty()) setGerbinState(GerbinState.DELIVER);
            else backToWork();
        }
    }

    private void backToWork() {
        if (line != null) setGerbinState(chopNext ? GerbinState.TO_CHOP : GerbinState.TO_SINK);
        else setGerbinState(GerbinState.IDLE);
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
        if (state == GerbinState.DELIVER) {
            if (stateTicks <= 1) lastDeliverDist = Double.MAX_VALUE;
            deliver(level);
            return;
        }
        if (state == GerbinState.GIVE) {
            give(level);
            return;
        }
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
            case DELIVER, GIVE -> {}
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
            case DELIVER -> CARRY;
            case GIVE -> GIVE;
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
        net.minecraft.nbt.ListTag ow = new net.minecraft.nbt.ListTag();
        for (java.util.UUID u : owed) ow.add(net.minecraft.nbt.NbtUtils.createUUID(u));
        tag.put("Owed", ow);
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
        owed.clear();
        for (net.minecraft.nbt.Tag u : tag.getList("Owed", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) owed.add(net.minecraft.nbt.NbtUtils.loadUUID(u));
    }
}
