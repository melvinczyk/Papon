package com.groid.papon.boss;

import com.groid.papon.entity.Speech;
import com.groid.papon.entity.npc.TacoNpc;
import com.groid.papon.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * One of Chef Papon's mariachis, on the band stand of his coliseum. Scenery with a job, like the
 * Taco Truck's cast (nothing hurts, pushes or moves them; only the coliseum's teardown removes
 * them). He never leaves his place on the stage.
 *
 * The band sleeps until the boss comes over and brings his spatula down on the singer's head
 * ({@link #whack}): the singer goes up in the air off the floor and, back on his feet, dresses
 * under protest; the bang wakes the rest ({@link #wake}), and each, at his own pace, gets up, puts
 * his jacket and sombrero on (the singer his aviators too), picks his instrument up and waits (the
 * guitarron sits straight back down and sleeps until his chair is kicked, {@link #kick}).
 * The singer counts them in ({@link #count}) and they all play: the song itself is the client's
 * (SongPlayer), started from {@link #songStart()}, the server tick of its first downbeat.
 *
 * The third of the coro is the lamplighter: when night falls while they play (or day breaks), he
 * steps down off the riser to the floodlight at the stage's back corner, gives its chain a tug, and
 * both lights come on (or go off) as he walks back ({@link #tickLamp}).
 */
public class MariachiEntity extends TacoNpc {
    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(MariachiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SONG_START =
            SynchedEntityData.defineId(MariachiEntity.class, EntityDataSerializers.INT);

    @Nullable private Vec3 home;
    private float homeYaw;
    /** Extra ticks he lies there stunned before getting up (everyone at his own pace). */
    private int delay;
    private boolean kicked;
    private boolean woken;
    /** The stage's floodlights (the lamplighter's; the first is the one he walks to). */
    private final java.util.List<net.minecraft.core.BlockPos> lamps = new java.util.ArrayList<>();
    /** Lamp: the spot in front of the floodlight he walks to, and how far along the way he is (0..1). */
    @Nullable private Vec3 lampStand;
    private double lampAlong;
    private boolean lampOn;
    private int lampCooldown;

    public MariachiEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(false);
    }

    /** A band member at his place, asleep sitting on the stage floor (Gordo, the guitarron: in his chair). */
    public static MariachiEntity spawn(ServerLevel level, BandRole role, Vec3 at, float yaw, java.util.List<net.minecraft.core.BlockPos> lamps) {
        MariachiEntity m = new MariachiEntity(com.groid.papon.registry.ModEntities.MARIACHI.get(), level);
        m.entityData.set(DATA_ROLE, role.ordinal());
        if (role.lamplighter()) m.lamps.addAll(lamps);
        m.home = at;
        m.homeYaw = yaw;
        m.moveTo(at.x, at.y, at.z, yaw, 0);
        m.setYHeadRot(yaw);
        m.yBodyRot = yaw;
        level.addFreshEntity(m);
        return m;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ROLE, 0);
        entityData.define(DATA_SONG_START, 0);
    }

    public BandRole getRole() {
        return BandRole.byId(entityData.get(DATA_ROLE));
    }

    public BandState getBandState() {
        return BandState.byId(stateId());
    }

    /** The server tick (game time, low 31 bits) the song's first downbeat falls on; 0 before the count. */
    public int songStart() {
        return entityData.get(DATA_SONG_START);
    }

    public void setBandState(BandState s) {
        setStateId(s.ordinal());
    }

    // ------------------------------------------------------------------ what the boss does to them

    /** The clang: everyone asleep jolts awake and starts getting ready. */
    public void wake(int delay) {
        if (!getBandState().asleep()) return;
        this.delay = delay;
        woken = true;
        setBandState(BandState.STARTLE);
        if (getRole().dozer() && !kicked) say("sleepy", 40);
    }

    /** The singer, asleep in the front row: the boss's spatula on his head. Up he goes. */
    public void whack() {
        if (!getBandState().asleep()) return;
        woken = true;
        setBandState(BandState.WHACKED);
    }

    /** His chair kicked out from under him: up he gets, for good this time. */
    public void kick() {
        kicked = true;
        delay = 0;
        setBandState(BandState.STARTLE);
        say("kicked", 50);
    }

    /** Back asleep after the clang (the guitarron): his chair needs kicking. */
    public boolean dozing() {
        return woken && !kicked && getBandState() == BandState.SLEEP && stateTicks > 10;
    }

    /** The singer counts the band in; the first downbeat falls {@link BandState#COUNT} ticks from now. */
    public void count(int songStartTick) {
        entityData.set(DATA_SONG_START, songStartTick);
        if (getRole().singer()) setBandState(BandState.COUNT);
    }

    /** Everyone plays (the song started at {@code songStartTick}). */
    public void play(int songStartTick) {
        entityData.set(DATA_SONG_START, songStartTick);
        if (!getBandState().lamp()) setBandState(BandState.PLAY);      // off at the lamp: back to it when he is home
    }

    public void bow() {
        if (getBandState().lamp()) {
            lampStand = null;
            if (home != null) setPos(home.x, home.y, home.z);
        }
        if (getBandState() == BandState.PLAY || getBandState().ready() || getBandState().lamp()) setBandState(BandState.BOW);
    }

    private void say(String line, int ticks) {
        Speech.say(this, ChatFormatting.GOLD, "speech.papon.band." + line, false, false, ticks);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void serverStep(ServerLevel level) {
        getNavigation().stop();
        if (getBandState().lamp() && home != null && lampStand != null) {
            tickLamp(level);
            return;
        }
        if (home != null) {
            setPos(home.x, Math.max(getY(), home.y - 0.5), home.z);
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        setYRot(homeYaw);
        yBodyRot = homeYaw;
        setYHeadRot(homeYaw);
        BandState s = getBandState();
        if (s.lamp()) setBandState(BandState.PLAY);                    // lost his way (a reload): back to playing
        if (lampCooldown > 0) lampCooldown--;
        if (s == BandState.PLAY && !lamps.isEmpty() && lampCooldown == 0 && home != null && tickCount % 20 == 0) {
            boolean night = isNight(level);
            if (night != lit(level)) {
                lampOn = night;
                Vec3 lamp = Vec3.atBottomCenterOf(lamps.get(0));
                Vec3 away = new Vec3(home.x - lamp.x, 0, home.z - lamp.z).normalize();
                lampStand = new Vec3(lamp.x + away.x * 1.0, lamp.y, lamp.z + away.z * 1.0);
                lampAlong = 0;
                setBandState(BandState.LAMP_GO);
                return;
            }
        }
        int t = stateTicks;
        BandRole role = getRole();
        switch (s) {
            case STARTLE -> {
                if (t >= s.duration + delay) setBandState(BandState.GETUP);
            }
            case WHACKED -> {
                if (t == 4) say("ouch", 26);
                if (t == 32) say("ya_voy", 40);
                if (t >= s.duration) setBandState(BandState.JACKET_SLOW);
            }
            case JACKET_SLOW -> {
                if (t == 40) say("sigh", 34);
                if (t >= s.duration) setBandState(BandState.HAT_SLOW);
            }
            case HAT_SLOW -> {
                if (t >= s.duration) setBandState(BandState.SHADES);
            }
            case GETUP -> {
                if (t == 22 && random.nextInt(3) == 0) say("yawn", 30);
                if (t >= s.duration) setBandState(role.dozer() && !kicked ? BandState.RESLEEP : BandState.JACKET);
            }
            case RESLEEP -> {
                if (t >= s.duration) setBandState(BandState.SLEEP);
            }
            case JACKET -> {
                if (t >= s.duration) setBandState(BandState.HAT);
            }
            case HAT -> {
                if (t >= s.duration) setBandState(role.singer() ? BandState.SHADES : BandState.GRAB);
            }
            case SHADES -> {
                if (t >= s.duration) setBandState(BandState.GRAB);
            }
            case GRAB -> {
                if (t >= s.duration) setBandState(BandState.READY);
            }
            case COUNT -> {
                // "¡Uno...! ¡...dos! ¡Uno, dos, tres!" on the animation's beats
                if (t == 4) say("uno", 16);
                if (t == 23) say("dos", 16);
                if (t == 42) say("count", 36);
            }
            default -> {}
        }
    }

    /** From the stage's point of view: dark enough for the lights (dusk to dawn). */
    private static boolean isNight(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t >= 12600 && t < 23200;
    }

    private boolean lit(ServerLevel level) {
        net.minecraft.world.level.block.state.BlockState b = level.getBlockState(lamps.get(0));
        return b.hasProperty(com.groid.papon.block.StageLampBlock.LIT) && b.getValue(com.groid.papon.block.StageLampBlock.LIT);
    }

    /**
     * The walk to the floodlight and back: a straight line to the spot in front of it (stepping down
     * off the riser on the way), the tug on its chain (both lights switch at SWITCH_TICK, the far one
     * a moment later), then home again and on with the song.
     */
    private void tickLamp(ServerLevel level) {
        BandState s = getBandState();
        double len = Math.max(0.5, home.distanceTo(lampStand));
        if (s == BandState.LAMP_GO || s == BandState.LAMP_BACK) {
            lampAlong = Mth.clamp(lampAlong + (s == BandState.LAMP_GO ? 1 : -1) * 0.085 / len, 0, 1);
            double k = lampAlong;
            // level along the riser, then a step down to the stage floor near the end
            double drop = Mth.clamp((k - 0.55) / 0.2, 0, 1);
            double y = Mth.lerp(drop, home.y, lampStand.y);
            setPos(Mth.lerp(k, home.x, lampStand.x), y, Mth.lerp(k, home.z, lampStand.z));
            Vec3 dir = s == BandState.LAMP_GO ? lampStand.subtract(home) : home.subtract(lampStand);
            face((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F);
            if (s == BandState.LAMP_GO && k >= 1) setBandState(BandState.LAMP_SWITCH);
            if (s == BandState.LAMP_BACK && k <= 0) {
                setPos(home.x, home.y, home.z);
                face(homeYaw);
                lampStand = null;
                lampCooldown = 200;
                setBandState(BandState.PLAY);
            }
        } else if (s == BandState.LAMP_SWITCH) {
            setPos(lampStand.x, lampStand.y, lampStand.z);
            Vec3 lamp = Vec3.atBottomCenterOf(lamps.get(0));
            face((float) (Mth.atan2(lamp.z - lampStand.z, lamp.x - lampStand.x) * Mth.RAD_TO_DEG) - 90F);
            if (stateTicks == SWITCH_TICK) switchLamp(level, 0);
            if (stateTicks == SWITCH_TICK + 7 && lamps.size() > 1) switchLamp(level, 1);
            if (stateTicks == SWITCH_TICK + 2) say(lampOn ? "lamp_on" : "lamp_off", 40);
            if (stateTicks >= s.duration) setBandState(BandState.LAMP_BACK);
        }
        setDeltaMovement(Vec3.ZERO);
    }

    private static final int SWITCH_TICK = 16;

    private void switchLamp(ServerLevel level, int i) {
        net.minecraft.core.BlockPos p = lamps.get(i);
        net.minecraft.world.level.block.state.BlockState b = level.getBlockState(p);
        if (!b.hasProperty(com.groid.papon.block.StageLampBlock.LIT)) return;
        level.setBlock(p, b.setValue(com.groid.papon.block.StageLampBlock.LIT, lampOn), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.playSound(null, p, SoundEvents.LEVER_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, lampOn ? 1.2F : 0.8F);
        level.playSound(null, p, lampOn ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, 1.8F);
        if (lampOn) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.7, p.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.02);
    }

    private void face(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && getBandState().asleep() && tickCount % 30 == 0) {
            // the Z's: a snooze bubble drifting up off whoever is sleeping
            level().addParticle(ModParticles.SNOOZE.get(), getX(), getY() + getBbHeight() * 1.05, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    protected Speech.Voice voice() {
        BandRole r = getRole();
        float pitch = r.singer() ? 1.0F : 1.25F / r.width;
        return new Speech.Voice(SoundEvents.VILLAGER_AMBIENT, pitch, 0.6F, 4);
    }

    // ------------------------------------------------------------------ animation

    private static final RawAnimation[] SLEEPS = {RawAnimation.begin().thenLoop("animation.mariachi.sleep_a"),
            RawAnimation.begin().thenLoop("animation.mariachi.sleep_b"), RawAnimation.begin().thenLoop("animation.mariachi.sleep_c")};

    private static final java.util.Map<String, RawAnimation> ANIMS = new java.util.concurrent.ConcurrentHashMap<>();

    private static RawAnimation once(String name) {
        return ANIMS.computeIfAbsent(name, n -> RawAnimation.begin().thenPlayAndHold("animation.mariachi." + n));
    }

    private static RawAnimation loop(String name) {
        return ANIMS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop("animation.mariachi." + n));
    }

    private static final RawAnimation RESLEEP = once("resleep"), HAT = once("hat"), SHADES = once("shades"), COUNT = once("count"),
            BOW = once("bow"), HAT_SLOW = once("hat_slow");

    /** Gordo has the only chair; everyone else sleeps (and wakes, and dresses) sitting on the stage floor. */
    private String chair(String name) {
        return getRole().dozer() ? name : name + "_floor";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::animate).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<MariachiEntity> event) {
        BandRole role = getRole();
        String kind = role.animKind();
        RawAnimation next = switch (getBandState()) {
            case SLEEP -> role.dozer() ? SLEEPS[1] : loop(chair("sleep_" + "abc".charAt(role.ordinal() % 3)));
            case STARTLE -> once(chair("startle"));
            case GETUP -> once(chair("getup"));
            case RESLEEP -> RESLEEP;
            case JACKET -> once(chair("jacket"));
            case HAT -> HAT;
            case SHADES -> SHADES;
            case GRAB -> once("grab_" + kind);
            case READY -> loop("ready_" + kind);
            case COUNT -> COUNT;
            case PLAY -> loop("play_" + (role.singer() ? "singer" : kind));
            case BOW -> BOW;
            case LAMP_GO, LAMP_BACK -> loop("lamp_walk");
            case LAMP_SWITCH -> once("lamp_switch");
            case WHACKED -> once(chair("whacked"));
            case JACKET_SLOW -> once(chair("jacket_slow"));
            case HAT_SLOW -> HAT_SLOW;
        };
        return event.setAndContinue(next);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Role", entityData.get(DATA_ROLE));
        tag.putInt("Band", stateId());
        tag.putInt("SongStart", songStart());
        tag.putBoolean("Kicked", kicked);
        tag.putBoolean("Woken", woken);
        if (home != null) {
            tag.putDouble("HX", home.x);
            tag.putDouble("HY", home.y);
            tag.putDouble("HZ", home.z);
        }
        tag.putFloat("HYaw", homeYaw);
        long[] l = new long[lamps.size()];
        for (int i = 0; i < l.length; i++) l[i] = lamps.get(i).asLong();
        tag.putLongArray("Lamps", l);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_ROLE, tag.getInt("Role"));
        setStateId(tag.getInt("Band"));
        entityData.set(DATA_SONG_START, tag.getInt("SongStart"));
        kicked = tag.getBoolean("Kicked");
        woken = tag.getBoolean("Woken");
        if (tag.contains("HX")) home = new Vec3(tag.getDouble("HX"), tag.getDouble("HY"), tag.getDouble("HZ"));
        homeYaw = tag.getFloat("HYaw");
        lamps.clear();
        for (long l : tag.getLongArray("Lamps")) lamps.add(net.minecraft.core.BlockPos.of(l));
    }
}
