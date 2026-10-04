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
 * ({@link #whack}): the singer goes up in the air out of his chair and, back on his feet, dresses
 * under protest; the bang wakes the rest ({@link #wake}), and each, at his own pace, gets up, puts
 * his jacket and sombrero on (the singer his aviators too), picks his instrument up and waits (the
 * guitarron sits straight back down and sleeps until his chair is kicked, {@link #kick}).
 * The singer counts them in ({@link #count}) and they all play: the song itself is the client's
 * (SongPlayer), started from {@link #songStart()}, the server tick of its first downbeat.
 */
public class MariachiEntity extends TacoNpc {
    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(MariachiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SONG_START =
            SynchedEntityData.defineId(MariachiEntity.class, EntityDataSerializers.INT);
    /** Which song they play (Song.CANCION, Song.PARA_SIEMPRE). */
    private static final EntityDataAccessor<Integer> DATA_SONG =
            SynchedEntityData.defineId(MariachiEntity.class, EntityDataSerializers.INT);
    /** The singer, his requinto in the boss's hands (it is not drawn on him). */
    private static final EntityDataAccessor<Boolean> DATA_ROBBED =
            SynchedEntityData.defineId(MariachiEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable private Vec3 home;
    private float homeYaw;
    /** Extra ticks he lies there stunned before getting up (everyone at his own pace). */
    private int delay;
    private boolean kicked;
    private boolean woken;

    public MariachiEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(false);
    }

    /** A band member at his place, asleep in his chair. */
    public static MariachiEntity spawn(ServerLevel level, BandRole role, Vec3 at, float yaw) {
        MariachiEntity m = new MariachiEntity(com.groid.papon.registry.ModEntities.MARIACHI.get(), level);
        m.entityData.set(DATA_ROLE, role.ordinal());
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
        entityData.define(DATA_SONG, 0);
        entityData.define(DATA_ROBBED, false);
    }

    /** The song they play: Song.CANCION through phase two, Song.PARA_SIEMPRE from phase three. */
    public int songId() {
        return entityData.get(DATA_SONG);
    }

    public boolean robbed() {
        return entityData.get(DATA_ROBBED);
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
        setBandState(BandState.PLAY);
    }

    /** ...a given song: phase three's starts at its pickup, the singer alone, at {@code songStartTick}. */
    public void play(int songStartTick, int song) {
        entityData.set(DATA_SONG, song);
        play(songStartTick);
    }

    // ------------------------------------------------------------------ phase three

    /** The boss knocked down: the music stops dead, instruments still in hand. */
    public void stop() {
        if (getBandState() == BandState.PLAY || getBandState() == BandState.COUNT) setBandState(BandState.READY);
    }

    /** The boss over them, the requinto torn away: everyone cowers ({@code delay} ticks late, each his own way). */
    public void scare(int delay) {
        if (getRole().singer()) return;
        this.delay = delay;
        scareDue = true;
    }

    /** The singer: his requinto out of his hands. */
    public void rob() {
        entityData.set(DATA_ROBBED, true);
        setBandState(BandState.ROBBED);
        say("hey", 30);
    }

    /** The spotlight on the boss: they forget to be scared and watch. */
    public void awe() {
        if (getBandState() == BandState.SCARED || getBandState() == BandState.READY) setBandState(BandState.AWED);
    }

    /** The singer: the requinto back at him through the air (it lands in his hands now). */
    public void catchGuitar() {
        entityData.set(DATA_ROBBED, false);
        setBandState(BandState.CATCH);
    }

    /** Nobody dares start: they look at each other (the singer once he has the requinto in his hands). */
    public void nervous() {
        if (getBandState() == BandState.CATCH) nervousDue = true;
        else setBandState(BandState.NERVOUS);
    }

    private boolean nervousDue;

    /** "What are you waiting for?!": instruments up. */
    public void ready() {
        nervousDue = false;
        if (getBandState() != BandState.CATCH) setBandState(BandState.READY);
    }

    private boolean scareDue;

    public void bow() {
        if (getBandState() == BandState.PLAY || getBandState().ready()) setBandState(BandState.BOW);
    }

    private void say(String line, int ticks) {
        Speech.say(this, ChatFormatting.GOLD, "speech.papon.band." + line, false, false, ticks);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void serverStep(ServerLevel level) {
        if (home != null) {
            setPos(home.x, Math.max(getY(), home.y - 0.5), home.z);
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        setYRot(homeYaw);
        yBodyRot = homeYaw;
        setYHeadRot(homeYaw);
        getNavigation().stop();
        BandState s = getBandState();
        int t = stateTicks;
        BandRole role = getRole();
        if (scareDue && delay-- <= 0) {
            scareDue = false;
            setBandState(BandState.SCARED);
            if (random.nextInt(3) == 0) say("ay_dios", 30);
        }
        switch (s) {
            case ROBBED -> {
                if (t >= s.duration) setBandState(BandState.SCARED);
            }
            case CATCH -> {
                if (t == 20) say("got_it", 26);
                if (t >= s.duration) setBandState(nervousDue ? BandState.NERVOUS : BandState.READY);
                if (t >= s.duration) nervousDue = false;
            }
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

    private static final RawAnimation STARTLE = once("startle"), GETUP = once("getup"), RESLEEP = once("resleep"),
            JACKET = once("jacket"), HAT = once("hat"), SHADES = once("shades"), COUNT = once("count"), BOW = once("bow"),
            WHACKED = once("whacked"), JACKET_SLOW = once("jacket_slow"), HAT_SLOW = once("hat_slow"),
            SCARED = loop("scared"), ROBBED = once("robbed"), AWED = loop("awed"), CATCH = once("catch");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::animate).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<MariachiEntity> event) {
        BandRole role = getRole();
        String kind = role.animKind();
        RawAnimation next = switch (getBandState()) {
            case SLEEP -> role.dozer() && kicked ? SLEEPS[1] : SLEEPS[(role.ordinal() + (role.dozer() ? 1 : 0)) % 3];
            case STARTLE -> STARTLE;
            case GETUP -> GETUP;
            case RESLEEP -> RESLEEP;
            case JACKET -> JACKET;
            case HAT -> HAT;
            case SHADES -> SHADES;
            case GRAB -> once("grab_" + kind);
            case READY -> loop("ready_" + kind);
            case COUNT -> COUNT;
            case PLAY -> loop("play_" + (role.singer() ? "singer" : kind));
            case BOW -> BOW;
            case WHACKED -> WHACKED;
            case JACKET_SLOW -> JACKET_SLOW;
            case HAT_SLOW -> HAT_SLOW;
            case SCARED -> SCARED;
            case ROBBED -> ROBBED;
            case AWED -> AWED;
            case CATCH -> CATCH;
            case NERVOUS -> loop(role.ordinal() % 2 == 0 ? "nervous_a" : "nervous_b");
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
        tag.putInt("Song", songId());
        tag.putBoolean("Robbed", robbed());
        tag.putBoolean("Kicked", kicked);
        tag.putBoolean("Woken", woken);
        if (home != null) {
            tag.putDouble("HX", home.x);
            tag.putDouble("HY", home.y);
            tag.putDouble("HZ", home.z);
        }
        tag.putFloat("HYaw", homeYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_ROLE, tag.getInt("Role"));
        setStateId(tag.getInt("Band"));
        entityData.set(DATA_SONG_START, tag.getInt("SongStart"));
        entityData.set(DATA_SONG, tag.getInt("Song"));
        entityData.set(DATA_ROBBED, tag.getBoolean("Robbed"));
        kicked = tag.getBoolean("Kicked");
        woken = tag.getBoolean("Woken");
        if (tag.contains("HX")) home = new Vec3(tag.getDouble("HX"), tag.getDouble("HY"), tag.getDouble("HZ"));
        homeYaw = tag.getFloat("HYaw");
    }
}
