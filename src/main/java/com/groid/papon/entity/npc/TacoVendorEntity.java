package com.groid.papon.entity.npc;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.boss.TacoBoss;
import com.groid.papon.entity.Speech;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.journey.TacoLine;
import com.groid.papon.journey.TacoTruckScene;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Papon who works the Taco Truck window, and the boss to come. He runs the line: takes the
 * order of whoever is at the front, cooks, hands the taco over, and calls the next one up, but
 * only while a player is standing in line ({@link #isServing}). The Wandering Papon is in that line
 * too; when he reaches the window he hands over his Taco Coupon, the vendor inspects it, finds it
 * is a fake, and {@link TacoBoss#onFraud} takes it from there.
 *
 * The queue is a list of entity UUIDs in line order (customers, then the Wandering Papon). It is
 * the authority on who stands where: every tick each member is told its slot.
 */
public class TacoVendorEntity extends TacoNpc {
    private static final String ANIM = "animation.papon_npc.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "vendor_idle");
    private static final RawAnimation TAKE_ORDER = RawAnimation.begin().thenLoop(ANIM + "vendor_take_order");
    private static final RawAnimation COOK = RawAnimation.begin().thenLoop(ANIM + "vendor_cook");
    private static final RawAnimation SERVE = RawAnimation.begin().thenPlayAndHold(ANIM + "vendor_serve");
    private static final RawAnimation INSPECT = RawAnimation.begin().thenPlayAndHold(ANIM + "vendor_inspect");
    private static final RawAnimation FRAUD_THEN_FURIOUS =
            RawAnimation.begin().thenPlay(ANIM + "vendor_fraud").thenLoop(ANIM + "vendor_furious");
    private static final RawAnimation FURIOUS = RawAnimation.begin().thenLoop(ANIM + "vendor_furious");
    private static final RawAnimation EXPOSED = RawAnimation.begin().thenPlayAndHold(ANIM + "vendor_exposed");
    private static final RawAnimation UNMASK = RawAnimation.begin().thenPlayAndHold(ANIM + "vendor_unmask");
    private static final RawAnimation REALIZE = RawAnimation.begin().thenPlayAndHold(ANIM + "vendor_realize");
    private static final RawAnimation RAGE = RawAnimation.begin().thenPlayAndHold(ANIM + "vendor_rage");

    /** A queued entity not seen for this long (unloaded for good, or removed) leaves the line. */
    private static final int MISSING_LIMIT = 400;
    /** How close the one at the front must be to the ordering spot to be served. */
    private static final double AT_WINDOW = 0.75;

    @Nullable private TacoLine line;
    private final List<UUID> queue = new ArrayList<>();
    private final Map<UUID, Integer> missing = new HashMap<>();
    private int served;
    private int total;
    private boolean serving;

    public TacoVendorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(false);
    }

    @Override
    protected Speech.Voice voice() {
        return new Speech.Voice(SoundEvents.WANDERING_TRADER_AMBIENT, 0.9F, 0.55F, 4);
    }

    public VendorState getVendorState() {
        return VendorState.byId(stateId());
    }

    private void setVendorState(VendorState s) {
        if (WanderingPaponEntity.debugEscort && s != getVendorState()) {
            PaponMod.LOG.info("[debug] vendor {} {} -> {} (queue {}, served {}/{})", getUUID().toString().substring(0, 8),
                    getVendorState(), s, queue.size(), served, total);
        }
        setStateId(s.ordinal());
    }

    @Nullable
    public TacoLine getLine() {
        return line;
    }

    /** Opens for business at a truck, with these customers already in line, front first. */
    public void open(TacoLine line, List<? extends Entity> customers) {
        this.line = line;
        queue.clear();
        for (Entity e : customers) queue.add(e.getUUID());
        total = queue.size();
        served = 0;
        setVendorState(VendorState.IDLE);
    }

    /** Puts someone at the back of the line. */
    public void join(Entity e) {
        if (!queue.contains(e.getUUID())) {
            queue.add(e.getUUID());
            total++;
        }
    }

    public boolean acceptsCustomers() {
        VendorState s = getVendorState();
        return line != null && !s.unmasking() && s != VendorState.INSPECTING;
    }

    /** Line position of someone, or -1. */
    public int positionOf(UUID id) {
        return queue.indexOf(id);
    }

    public int queueSize() {
        return queue.size();
    }

    public int servedCount() {
        return served;
    }

    public int totalCount() {
        return total;
    }

    /** True while a player is in line, so the line is moving. */
    public boolean isServing() {
        return serving;
    }

    // ------------------------------------------------------------------ the line

    @Override
    protected void serverStep(ServerLevel level) {
        getNavigation().stop();
        if (line == null) return;
        setYRot(line.out().toYRot());
        yBodyRot = getYRot();
        if (getVendorState() != VendorState.COOKING) yHeadRot = getYRot();

        List<Entity> members = resolveQueue(level);
        serving = playerInLine(level);
        Entity head = members.isEmpty() ? null : members.get(0);

        switch (getVendorState()) {
            case IDLE -> {
                if (stateTicks < 12 || head == null || !serving) break;
                if (head.position().distanceToSqr(line.slot(0)) > AT_WINDOW * AT_WINDOW) break;
                if (head instanceof TacoCustomerEntity c && c.isWaitingInLine()) {
                    setVendorState(VendorState.TAKING_ORDER);
                    c.beginOrder();
                } else if (head instanceof WanderingPaponEntity w) {
                    setVendorState(VendorState.INSPECTING);
                    w.presentCoupon();
                }
            }
            case TAKING_ORDER -> {
                if (!(head instanceof TacoCustomerEntity c)) {
                    setVendorState(VendorState.IDLE);
                    break;
                }
                if (stateTicks == c.kind().orderTicks - 22) {
                    Speech.say(this, ChatFormatting.GOLD, "speech.papon.vendor.order" + random.nextInt(3), false, true, 40);
                }
                if (stateTicks >= c.kind().orderTicks) {
                    setVendorState(VendorState.COOKING);
                    c.waitForFood();
                }
            }
            case COOKING -> {
                cookingEffects(level);
                if (!(head instanceof TacoCustomerEntity c)) {
                    setVendorState(VendorState.IDLE);
                    break;
                }
                if (stateTicks >= Customer.COOK * c.kind().cookCycles) {
                    setVendorState(VendorState.SERVING);
                    c.receive();
                }
            }
            case SERVING -> {
                if (stateTicks == 18) {
                    Speech.say(this, ChatFormatting.GOLD, "speech.papon.vendor.serve" + random.nextInt(3), false, true, 36);
                }
                if (stateTicks >= Customer.SERVE) {
                    if (head instanceof TacoCustomerEntity c) {
                        c.leave();
                        queue.remove(c.getUUID());
                        served++;
                    }
                    setVendorState(VendorState.IDLE);
                }
            }
            case INSPECTING -> {
                if (!(head instanceof WanderingPaponEntity w)) {
                    setVendorState(VendorState.IDLE);
                    break;
                }
                if (stateTicks == 34) {
                    Speech.say(this, ChatFormatting.GOLD, "speech.papon.vendor.inspect", false, true, 50);
                }
                if (stateTicks == 92) {
                    Speech.say(this, ChatFormatting.GOLD, "speech.papon.vendor.suspicious", false, true, 40);
                }
                if (stateTicks >= VendorState.INSPECTING.duration) {
                    setVendorState(VendorState.FRAUD);
                    fraud(level, w);
                }
            }
            case FRAUD -> {
                if (stateTicks == 12) {
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.6, getZ(), 12, 0.8, 0.4, 0.8, 0);
                }
                if (stateTicks >= VendorState.FRAUD.duration) setVendorState(VendorState.EXPOSED);
            }
            case FURIOUS -> {
                // the old dead end, from saves made before the fight existed: on with the show
                if (stateTicks >= 40) setVendorState(VendorState.EXPOSED);
            }
            case EXPOSED, UNMASK, REALIZE, RAGE -> unmasking(level);
        }
        if (line != null && tickCount % 200 == 7 && !getVendorState().unmasking()) TacoTruckScene.ensureGerbin(level, line);

        assignSlots(members);
        if (tickCount % 20 == 0) statusToPlayers(level, members);
    }

    /** The queue as live entities, dropping anyone gone for good. */
    private List<Entity> resolveQueue(ServerLevel level) {
        List<Entity> out = new ArrayList<>();
        for (UUID id : new ArrayList<>(queue)) {
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) {
                int m = missing.merge(id, 1, Integer::sum);
                if (m > MISSING_LIMIT || (e != null && !e.isAlive())) {
                    queue.remove(id);
                    missing.remove(id);
                }
                continue;
            }
            missing.remove(id);
            out.add(e);
        }
        return out;
    }

    private void assignSlots(List<Entity> members) {
        int wanderAt = -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) instanceof WanderingPaponEntity) wanderAt = i;
        }
        for (int i = 0; i < members.size(); i++) {
            Entity e = members.get(i);
            if (e instanceof TacoCustomerEntity c) {
                c.setLineSlot(getUUID(), line, i);
            } else if (e instanceof WanderingPaponEntity w) {
                w.setLineSlot(getUUID(), line, i, served, total);
            }
        }
    }

    /** Where the player should stand: right behind the last one in line. */
    public Vec3 playerSlot() {
        return line == null ? position() : line.slot(queue.size());
    }

    private boolean playerInLine(ServerLevel level) {
        if (WanderingPaponEntity.debugEscort) return true;
        Vec3 spot = playerSlot();
        double r = PaponConfig.LINE_SLACK.get();
        for (Player p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (!PaponConfig.REQUIRE_PLAYER_IN_LINE.get()) {
                if (p.distanceToSqr(line.slot(0)) < 32 * 32) return true;
                continue;
            }
            double dx = p.getX() - spot.x;
            double dz = p.getZ() - spot.z;
            // anywhere from your spot back down the lane counts as being in line
            double along = line.along(p.position()) - line.along(spot);
            double lateral = Math.abs(dx * line.side().getStepX() + dz * line.side().getStepZ());
            if (Math.abs(p.getY() - spot.y) < 4 && lateral <= r && along >= -r && along <= r + 6) return true;
        }
        return false;
    }

    /** Action bar for everyone near the truck, plus a marker on the spot while nobody is in line. */
    private void statusToPlayers(ServerLevel level, List<Entity> members) {
        VendorState s = getVendorState();
        if (s.unmasking() || s == VendorState.INSPECTING) return;
        int ahead = members.size();
        boolean papon = false;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) instanceof WanderingPaponEntity) {
                ahead = i;
                papon = true;
            }
        }
        Component msg = serving
                ? Component.translatable(papon ? "status.papon.line.papon" : "status.papon.line.you", ahead)
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable("status.papon.line.join").withStyle(ChatFormatting.RED);
        Vec3 spot = playerSlot();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(spot) < 48 * 48) p.displayClientMessage(msg, true);
        }
        if (!serving) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.2, spot.z, 10, 0.45, 0.05, 0.45, 0);
        }
    }

    private void cookingEffects(ServerLevel level) {
        if (tickCount % 6 != 0) return;
        Vec3 g = position().add(-line.out().getStepX() * 2.6, 1.2, -line.out().getStepZ() * 2.6);
        level.sendParticles(ParticleTypes.SMOKE, g.x, g.y, g.z, 3, 0.25, 0.05, 0.25, 0.01);
        if (random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, g.x, g.y + 0.3, g.z, 1, 0.1, 0.05, 0.1, 0.01);
        }
    }

    // ------------------------------------------------------------------ the fraud

    private void fraud(ServerLevel level, WanderingPaponEntity wanderer) {
        Speech.say(this, ChatFormatting.RED, "speech.papon.vendor.fraud", true, true, 70);
        wanderer.busted();
        Component title = Component.translatable("title.papon.fraud").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("title.papon.fraud.subtitle").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 64 * 64) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 50, 16));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
                p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 1.3F);
        // Everyone still around scatters.
        for (Entity e : level.getEntitiesOfClass(TacoCustomerEntity.class, getBoundingBox().inflate(48))) {
            ((TacoCustomerEntity) e).flee();
        }
        PaponMod.LOG.info("Taco vendor {} caught Wandering Papon {} with a fraud coupon", getUUID(), wanderer.getUUID());
        TacoBoss.onFraud(level, this, wanderer);
    }

    // ------------------------------------------------------------------ the unmasking

    /**
     * After the fraud: caught out, the mustache and the toque come off (Norwood 7), the horror of
     * being seen, the rage, the boss. Effects land on the beats of the animations.
     */
    private void unmasking(ServerLevel level) {
        int t = stateTicks;
        double top = getY() + 2.3;
        switch (getVendorState()) {
            case EXPOSED -> {
                if (t == 18) Speech.say(this, ChatFormatting.GOLD, "speech.papon.vendor.exposed", true, false, 40);
                if (t >= VendorState.EXPOSED.duration) setVendorState(VendorState.UNMASK);
            }
            case UNMASK -> {
                if (t == 4) Speech.say(this, ChatFormatting.RED, "speech.papon.vendor.unmask", true, true, 50);
                if (t == 19) {      // the rip
                    Vec3 lip = position().add(getLookAngle().scale(0.8)).add(0, 1.05, 0);
                    level.sendParticles(ParticleTypes.CRIT, lip.x, lip.y, lip.z, 10, 0.2, 0.1, 0.2, 0.2);
                }
                if (t == 57) {      // the toque comes up: the reveal
                    level.sendParticles(ParticleTypes.END_ROD, getX(), top, getZ(), 30, 0.5, 0.2, 0.5, 0.06);
                    level.sendParticles(ParticleTypes.FLASH, getX(), top + 0.3, getZ(), 1, 0, 0, 0, 0);
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.REVEAL.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
                    // the caption: a bubble over the shiny head, and the cutscene's subtitle
                    Speech.say(this, ChatFormatting.GRAY, "status.papon.norwood", false, false, 40);
                }
                if (t >= 60 && t < 90 && t % 6 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, getX(), top, getZ(), 2, 0.4, 0.05, 0.4, 0.01);
                }
                if (t >= VendorState.UNMASK.duration) setVendorState(VendorState.REALIZE);
            }
            case REALIZE -> {
                if (t == 4) level.sendParticles(ParticleTypes.CLOUD, getX(), top, getZ(), 4, 0.6, 0.05, 0.6, 0.02);
                if (t == 40) Speech.say(this, ChatFormatting.RED, "speech.papon.vendor.realize", true, true, 40);
                if (t >= VendorState.REALIZE.duration) setVendorState(VendorState.RAGE);
            }
            case RAGE -> {
                if (t < 34 && t % 5 == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, getX(), top, getZ(), 4, 0.3, 0.05, 0.3, 0.03);
                }
                if (t == 34) {      // the roar
                    Speech.say(this, ChatFormatting.DARK_RED, "speech.papon.vendor.rage", true, true, 50);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 0.8F);
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), top, getZ(), 16, 0.9, 0.5, 0.9, 0);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), top + 0.5, getZ(), 8, 0.4, 0.2, 0.4, 0.06);
                    PaponNetwork.shake(level, position(), 16, 0.45F, 30);
                }
                if (t > 34 && t % 3 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, getX(), top + 0.2, getZ(), 2, 0.2, 0.1, 0.2, 0.05);
                }
                if (t >= VendorState.RAGE.duration) TacoBoss.startFight(level, this);
            }
            default -> {}
        }
    }

    /** Admin skip (/papon reveal): straight to the moment the coupon is found to be a fake. */
    public void skipToFraud(ServerLevel level) {
        for (Entity e : resolveQueue(level)) {
            if (e instanceof WanderingPaponEntity w) {
                setVendorState(VendorState.FRAUD);
                fraud(level, w);
                return;
            }
        }
        setVendorState(VendorState.FRAUD);
        Speech.say(this, ChatFormatting.RED, "speech.papon.vendor.fraud", true, true, 70);
    }

    /** Admin skip (/papon serve): finish whatever is happening at the window right now. */
    public void hurryUp() {
        switch (getVendorState()) {
            case TAKING_ORDER, COOKING -> stateTicks = 10_000;
            case IDLE -> stateTicks = Math.max(stateTicks, 12);
            default -> {}
        }
    }

    /** Admin skip (/papon fraud): everyone ahead of the Wandering Papon gets fed instantly and leaves. */
    public void skipToPapon(ServerLevel level) {
        if (getVendorState().unmasking()) return;
        for (Entity e : resolveQueue(level)) {
            if (e instanceof WanderingPaponEntity) break;
            if (e instanceof TacoCustomerEntity c) {
                c.leaveWithoutTalking();
                queue.remove(c.getUUID());
                served++;
            }
        }
        setVendorState(VendorState.IDLE);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            String key = getVendorState().unmasking() ? "message.papon.vendor_furious" : "message.papon.vendor_busy";
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animate)
                .setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<TacoVendorEntity> event) {
        RawAnimation current = event.getController().getCurrentRawAnimation();
        RawAnimation next = switch (getVendorState()) {
            case IDLE -> IDLE;
            case TAKING_ORDER -> TAKE_ORDER;
            case COOKING -> COOK;
            case SERVING -> SERVE;
            case INSPECTING -> INSPECT;
            case FRAUD -> FRAUD_THEN_FURIOUS;
            case FURIOUS -> FRAUD_THEN_FURIOUS.equals(current) ? FRAUD_THEN_FURIOUS : FURIOUS;
            case EXPOSED -> EXPOSED;
            case UNMASK -> UNMASK;
            case REALIZE -> REALIZE;
            case RAGE -> RAGE;
        };
        return event.setAndContinue(next);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("VendorState", getVendorState().name());
        if (line != null) tag.put("Line", line.save());
        ListTag q = new ListTag();
        for (UUID id : queue) q.add(NbtUtils.createUUID(id));
        tag.put("Queue", q);
        tag.putInt("Served", served);
        tag.putInt("Total", total);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int keep = stateTicks;
        try {
            setVendorState(VendorState.valueOf(tag.getString("VendorState")));
        } catch (IllegalArgumentException ignored) {
            setVendorState(VendorState.IDLE);
        }
        stateTicks = keep;
        line = tag.contains("Line") ? TacoLine.load(tag.getCompound("Line")) : null;
        queue.clear();
        for (Tag t : tag.getList("Queue", Tag.TAG_INT_ARRAY)) queue.add(NbtUtils.loadUUID(t));
        served = tag.getInt("Served");
        total = tag.getInt("Total");
    }
}
