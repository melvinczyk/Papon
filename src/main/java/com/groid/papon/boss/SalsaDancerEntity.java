package com.groid.papon.boss;

import com.groid.papon.entity.Speech;
import com.groid.papon.entity.npc.TacoNpc;
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
 * The muchachas (phase four: "Hey! Where are the muchachas?"): skeleton Papons in salsa dresses, risen out of the floor in
 * green fire by the band's stage when he claps. They dance salsa to his last song for the rest of the fight, each her own
 * way; the one nearest him gets twirled (TWIRLED: he holds her hand up, she spins under it, he dips her, a few steps
 * together); at his fall they stop and hang their heads. Nothing can hurt them (a TacoNpc). Never saved: he calls them
 * again. Model tools/gen_salsa.py, animations gen_salsa.py (salsa_dancer.animation.json), client/SalsaDancerRenderer.
 */
public class SalsaDancerEntity extends TacoNpc {
    public enum State {
        /** Up out of the floor in green fire, spinning up into a pose (2 s). */
        RISE(40),
        /** Salsa, to the music (a loop: her own of three). */
        DANCE(0),
        /** His partner: hand up, twirled, dipped, a few steps with him (5.5 s, in step with his p4_muchachas). */
        TWIRLED(110),
        /** At his fall: stopped, heads hung. */
        MOURN(0),
        // ---- 2026-10-05 (appended: the ordinal is synced): El Beso de la Muerte
        /** Gone: spun down into the floor in a whirl of petals (to rise again somewhere else: RISE). */
        AWAY(16),
        /** The rose out of her hair, kissed, and thrown (it leaves her hand at {@link #KISS_THROW}). */
        KISS(26);

        public final int duration;

        State(int duration) { this.duration = duration; }
    }

    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(SalsaDancerEntity.class, EntityDataSerializers.INT);
    /** KISS: the tick the rose leaves her hand. */
    public static final int KISS_THROW = 13;
    @Nullable private Vec3 faceTo;
    private int partnerId = -1;

    public SalsaDancerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(false);
    }

    /** A muchacha risen at {@code at}, facing {@code center}; {@code look} picks her dress and her dance. */
    public static SalsaDancerEntity create(ServerLevel level, Vec3 at, Vec3 center, int look) {
        SalsaDancerEntity d = new SalsaDancerEntity(com.groid.papon.registry.ModEntities.SALSA_DANCER.get(), level);
        d.moveTo(at.x, at.y, at.z, 0, 0);
        d.faceTo = center;
        d.face(center);
        d.entityData.set(DATA_LOOK, look);
        d.setStateId(State.RISE.ordinal());
        return d;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_LOOK, 0);
    }

    public State getDancerState() {
        int i = stateId();
        State[] all = State.values();
        return i >= 0 && i < all.length ? all[i] : State.DANCE;
    }

    /** Her dress (0 red, 1 marigold and purple, 2 turquoise and pink, 3 black and gold). */
    public int look() { return entityData.get(DATA_LOOK) & 3; }

    public void dance() { setStateId(State.DANCE.ordinal()); }

    public void twirl(PaponBossEntity with) {
        partnerId = with.getId();
        face(with.position());
        setStateId(State.TWIRLED.ordinal());
    }

    public void mourn() { setStateId(State.MOURN.ordinal()); }

    /** Spins down into the floor (El Beso: off to her place round the arena). */
    public void away() { setStateId(State.AWAY.ordinal()); }

    /** Up out of the floor at {@code at}, facing {@code to}. */
    public void riseAt(Vec3 at, Vec3 to) {
        moveTo(at.x, at.y, at.z, getYRot(), 0);
        faceTo = to;
        face(to);
        setStateId(State.RISE.ordinal());
    }

    /** The rose out of her hair, kissed, and thrown at {@code at}. */
    public void kiss(Vec3 at) {
        faceTo = new Vec3(at.x, getY(), at.z);
        face(faceTo);
        setStateId(State.KISS.ordinal());
    }

    /** Turns her to dance facing {@code to} (back at her place). */
    public void faceTo(Vec3 to) { faceTo = to; }

    /** Where the rose leaves her hand: up at her shoulder, out in front. */
    public Vec3 hand() { return position().add(Vec3.directionFromRotation(0, getYRot()).scale(0.6)).add(0, 1.7, 0); }

    /** Whether she is free to throw (up and dancing). */
    public boolean ready() { return getDancerState() == State.DANCE; }

    @Override
    protected void serverStep(ServerLevel level) {
        getNavigation().stop();
        State s = getDancerState();
        if (s == State.TWIRLED && level.getEntity(partnerId) instanceof PaponBossEntity boss) face(boss.position());
        else if (faceTo != null) face(faceTo);
        if (s == State.RISE && stateTicks == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.6F, 1.2F);
        }
        if (s == State.RISE && stateTicks < 30 && stateTicks % 3 == 0) {
            com.groid.papon.fx.Fx.far(level, net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.3, getZ(), 4, 0.4, 0.2, 0.4, 0.02);
        }
        if (s == State.AWAY && stateTicks < 14 && stateTicks % 2 == 0) {
            com.groid.papon.fx.Fx.far(level, net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1.0, getZ(), 6, 0.5, 0.6, 0.5, 0.05);
        }
        if (s == State.AWAY && stateTicks == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHERRY_LEAVES_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.6F, 0.8F);
        }
        if (s == State.KISS && stateTicks == 8) {
            level.playSound(null, getX(), getY() + 1.6, getZ(), SoundEvents.CHERRY_LEAVES_PLACE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.4F, 1.6F);
        }
        if (s == State.AWAY) return;                    // down under the floor until riseAt
        if (s.duration > 0 && stateTicks >= s.duration) dance();
        // a click of the heels and a whoop now and then
        if (s == State.DANCE && random.nextInt(260) == 0) {
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.SKELETON_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.9F, 1.6F);
        }
    }

    @Override
    protected Speech.Voice voice() { return new Speech.Voice(SoundEvents.SKELETON_AMBIENT, 1.6F, 0.5F, 4); }

    // ------------------------------------------------------------------ animation

    private static final RawAnimation RISE = RawAnimation.begin().thenPlayAndHold("animation.salsa_dancer.rise");
    private static final RawAnimation TWIRLED = RawAnimation.begin().thenPlayAndHold("animation.salsa_dancer.twirled");
    private static final RawAnimation MOURN = RawAnimation.begin().thenLoop("animation.salsa_dancer.mourn");
    private static final RawAnimation VANISH = RawAnimation.begin().thenPlayAndHold("animation.salsa_dancer.vanish");
    private static final RawAnimation KISS = RawAnimation.begin().thenPlayAndHold("animation.salsa_dancer.kiss");
    private static final RawAnimation[] DANCES = {RawAnimation.begin().thenLoop("animation.salsa_dancer.salsa_a"),
            RawAnimation.begin().thenLoop("animation.salsa_dancer.salsa_b"), RawAnimation.begin().thenLoop("animation.salsa_dancer.salsa_c")};

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::animate).setSoundKeyframeHandler(this::onSoundCue));
    }

    private PlayState animate(AnimationState<SalsaDancerEntity> event) {
        RawAnimation next = switch (getDancerState()) {
            case RISE -> RISE;
            case TWIRLED -> TWIRLED;
            case MOURN -> MOURN;
            case AWAY -> VANISH;
            case KISS -> KISS;
            case DANCE -> DANCES[Math.floorMod(entityData.get(DATA_LOOK) * 7 + getId(), DANCES.length)];
        };
        return event.setAndContinue(next);
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
