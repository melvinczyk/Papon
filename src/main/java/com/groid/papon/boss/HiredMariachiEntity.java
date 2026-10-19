package com.groid.papon.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One of the band out of work after Chef Papon's fall, hired by whoever holds El Requinto (item/RequintoItem): the
 * coliseum's mariachi (same model, voice and animations), walking behind his new jefe instead of standing on a stage.
 *
 * Cheap on purpose: no pathfinding. {@link HiredBand} gives each member a place in a formation behind the owner, and he
 * walks straight there (stepping up blocks, hopping a ledge), and is simply there again when he falls too far behind or
 * gets stuck. He is never saved with the chunk: the roster lives on the player and {@link HiredBand} brings him back.
 *
 * Nothing targets him and players' blows pass through him; an explosion or a monster's blow knocks him out: "¡Ay, Dios
 * mío!", he runs off and his buff with him, back after {@code knockoutSeconds} ({@link HiredBand#knockedOut}).
 */
public class HiredMariachiEntity extends MariachiEntity {
    @Nullable private UUID owner;
    /** Dismissed, knocked out or scared off: walking away from {@link #awayFrom}, then gone. */
    private int leaving = -1;
    @Nullable private Vec3 awayFrom;
    private boolean moving;
    private int stuck;
    private double lastDist = Double.MAX_VALUE;
    private int hypedFor;

    public HiredMariachiEntity(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    static HiredMariachiEntity create(ServerLevel level, Player owner, BandRole role, Vec3 at, float yaw) {
        HiredMariachiEntity m = new HiredMariachiEntity(com.groid.papon.registry.ModEntities.HIRED_MARIACHI.get(), level);
        m.owner = owner.getUUID();
        m.setRole(role);
        m.setBandState(BandState.FOLLOW);
        m.moveTo(at.x, at.y, at.z, yaw, 0);
        m.setYHeadRot(yaw);
        m.yBodyRot = yaw;
        level.addFreshEntity(m);
        return m;
    }

    @Override public boolean hired() { return true; }

    @Nullable public UUID owner() { return owner; }

    /** Up, dressed, here and holding his instrument: he counts toward the band's buffs. */
    public boolean playing() {
        BandState s = getBandState();
        return isAlive() && leaving < 0 && (s == BandState.FOLLOW || s == BandState.READY || s == BandState.PLAY || s == BandState.COUNT
                || s == BandState.HYPED);
    }

    public boolean leaving() { return leaving >= 0; }

    // ------------------------------------------------------------------ what the band manager asks of him

    /** Sent home: a bow, then off he walks. */
    void dismiss(Vec3 from) {
        if (leaving >= 0) return;
        setBandState(BandState.BOW);
        awayFrom = from;
        leaving = 0;
    }

    /** Off at once (the boss is near: "Este señor está LOCO..."). */
    void flee(Vec3 from, @Nullable String line) {
        if (leaving >= 0) return;
        if (line != null) say(line, 40);
        setBandState(BandState.FOLLOW);
        awayFrom = from;
        leaving = BandState.BOW.duration;
    }

    /** Jumping up and down for whoever beat something big. */
    void cheer(int ticks) {
        if (leaving >= 0 || !getBandState().ready()) return;
        setBandState(BandState.HYPED);
        hypedFor = ticks;
    }

    /** Gordo: off to sleep on the ground (the jacket off, the sombrero down) while nothing happens. */
    void doze() {
        if (getBandState() == BandState.READY || getBandState() == BandState.PLAY) {
            say("sleepy", 50);
            setBandState(BandState.SLEEP);
        }
    }

    /** ...and woken: up he gets and dresses all over again ("¡AY! ¡Ya voy, ya voy!"). */
    void rouse() {
        if (!getBandState().asleep()) return;
        say("kicked", 50);
        setBandState(BandState.STARTLE);
    }

    void line(String line, int ticks) { say(line, ticks); }

    void startCount(int songStart) { count(songStart); }

    void startSong(int songStart) {
        play(songStart, Song.CANCION);
        if (moving) setBandState(BandState.FOLLOW);
    }

    void stopSong() {
        clearSong();
        if (getBandState() == BandState.COUNT) setBandState(BandState.READY);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void serverStep(ServerLevel level) {
        getNavigation().stop();
        Player p = owner == null ? null : level.getPlayerByUUID(owner);
        if (p == null || p.isSpectator()) {
            discard();               // logged off, or another dimension: HiredBand brings him back beside them
            return;
        }
        if (leaving >= 0) {
            walkAway(level, p);
            return;
        }
        BandState s = getBandState();
        int t = stateTicks;
        switch (s) {
            case SLEEP, RESLEEP -> { hold(p); return; }
            case STARTLE -> { hold(p); if (t >= s.duration) setBandState(BandState.GETUP); return; }
            case GETUP -> { hold(p); if (t >= s.duration) setBandState(BandState.JACKET); return; }
            case JACKET -> { hold(p); if (t >= s.duration) setBandState(BandState.HAT); return; }
            case HAT -> { hold(p); if (t >= s.duration) setBandState(BandState.GRAB); return; }
            case GRAB -> { hold(p); if (t >= s.duration) setBandState(BandState.READY); return; }
            case COUNT -> {
                // the singer counts them in where he stands; "¡Un, dos, tres, cuatro!" (the downbeat is HiredBand's)
                hold(p);
                if (t == 34) say("count", 74);
                return;
            }
            case HYPED -> { if (--hypedFor <= 0) setBandState(BandState.READY); }
            default -> {}
        }
        follow(level, p, HiredBand.slot(p, getRole()));
    }

    /** Walk to his place in the formation; once there, turn to his jefe and play (or wait, instrument ready). */
    private void follow(ServerLevel level, Player p, Vec3 slot) {
        double dx = slot.x - getX(), dz = slot.z - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double far = Math.max(d, Math.abs(slot.y - getY()) * 2);
        if (far > 24 || stuck > 60) {
            arrive(level, slot);
            return;
        }
        // hysteresis: off at 0.7 from his place, settled at 0.3
        if (d > 0.7) moving = true;
        else if (d < 0.3) moving = false;
        if (moving) {
            double speed = Mth.clamp(Math.max(HiredBand.ownerSpeed(p) * 1.08, 0.05 + d * 0.06), 0.06, 0.48);
            speed = Math.min(speed, d);
            setDeltaMovement(dx / d * speed, getDeltaMovement().y, dz / d * speed);
            turn((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F, 20F);
            if (horizontalCollision && onGround()) jumpFromGround();
            stuck = d > lastDist - 0.02 ? stuck + 1 : Math.max(0, stuck - 2);
            lastDist = d;
            BandState s = getBandState();
            if (s != BandState.FOLLOW && s != BandState.HYPED) setBandState(BandState.FOLLOW);
            return;
        }
        stuck = 0;
        lastDist = Double.MAX_VALUE;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        turn((float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * Mth.RAD_TO_DEG) - 90F, 9F);
        BandState want = HiredBand.performing(p) ? BandState.PLAY : BandState.READY;
        BandState s = getBandState();
        if ((s == BandState.FOLLOW || s == BandState.READY || s == BandState.PLAY) && s != want) {
            if (want == BandState.PLAY) play(songStart(), Song.CANCION);
            else setBandState(want);
        }
    }

    /** Stays where he is (asleep, dressing, counting in), turned his jefe's way. */
    private void hold(Player p) {
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (!getBandState().asleep()) turn((float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * Mth.RAD_TO_DEG) - 90F, 6F);
        if (distanceToSqr(p) > 40 * 40) arrive((ServerLevel) level(), HiredBand.slot(p, getRole()));
    }

    /** Fallen behind, or stuck: he slips round and is just there, at his place. */
    private void arrive(ServerLevel level, Vec3 slot) {
        Vec3 at = HiredBand.ground(level, slot);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        teleportTo(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.8, at.z, 6, 0.3, 0.5, 0.3, 0.02);
        stuck = 0;
        lastDist = Double.MAX_VALUE;
        moving = false;
    }

    /** Bowing, then walking off away from {@link #awayFrom}; gone in a puff once out of the way. */
    private void walkAway(ServerLevel level, Player p) {
        leaving++;
        if (getBandState() == BandState.BOW && leaving < BandState.BOW.duration) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }
        if (getBandState() != BandState.FOLLOW) setBandState(BandState.FOLLOW);
        Vec3 from = awayFrom != null ? awayFrom : p.position();
        Vec3 dir = new Vec3(getX() - from.x, 0, getZ() - from.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, getYRot()) : dir.normalize();
        setDeltaMovement(dir.x * 0.22, getDeltaMovement().y, dir.z * 0.22);
        turn((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F, 25F);
        if (horizontalCollision && onGround()) jumpFromGround();
        if (leaving > BandState.BOW.duration + 60) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            discard();
        }
    }

    private void turn(float want, float step) {
        float yaw = Mth.approachDegrees(getYRot(), want, step);
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    // ------------------------------------------------------------------ knocked out

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || leaving >= 0 || amount < 2 || owner == null) return false;
        Entity by = source.getEntity();
        // his own side never knocks him out (sweeps, arrows, a jefe's explosion); nor falling, burning, drowning
        if (by instanceof Player || by != null && by.getUUID().equals(owner)) return false;
        boolean boom = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!boom && by == null && source.getDirectEntity() == null) return false;
        // a blast scatters the band, it does not floor all of it: each caught has a 40% chance to run (a creeper took all thirteen)
        if (boom && (amount < 4 || random.nextFloat() > 0.4F)) return false;
        Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : position();
        say(random.nextBoolean() ? "ay_dios" : "p3_mama", 40);
        awayFrom = from;
        leaving = BandState.BOW.duration;
        setBandState(BandState.FOLLOW);
        HiredBand.knockedOut((ServerLevel) level(), owner, getRole());
        return false;
    }

    @Override public boolean ignoreExplosion() { return false; }
    @Override public boolean isPickable() { return false; }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof Player || other instanceof HiredMariachiEntity || super.isAlliedTo(other);
    }

    /** The roster lives on the player (HiredBand): never written to the chunk. */
    @Override public boolean shouldBeSaved() { return false; }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
    }
}
