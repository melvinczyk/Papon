package com.groid.papon.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crowd of El Coliseo Papa: one invisible entity at the middle of the sand that stands for
 * every fan in the stands. It holds their mood ({@link CrowdMood}) and the seed their seats and
 * looks are dealt from (Coliseum.seats); client/ColiseumCrowdRenderer draws them all and
 * client/CrowdSounds makes their noise. Removed with the coliseum.
 *
 * The boss sets a standing mood ({@link #setMood}) and fires short reactions over it
 * ({@link #react}: a roar when he lands one on you, boos when you land one on him).
 *
 * They are on his side, and they let you know it: get near the stands and the fans closest to you
 * throw potatoes at you ({@link #pelt}): a wind-up the renderer plays on that fan (synced as
 * {@link #throwSeat}), the potato away a few ticks later, every few seconds per challenger.
 */
public class ColiseumCrowdEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_MOOD = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    /** The latest throw: (a counter << 16) | the thrower's seat index (seats(seed) order); -1 none yet. */
    private static final EntityDataAccessor<Integer> DATA_THROW = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    /** Phase three's serenade: the share of the fans with a lighter up (0..1), spreading through the stands. */
    private static final EntityDataAccessor<Float> DATA_LIGHTERS = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.FLOAT);
    /**
     * Their noise: (a counter << 6) | (loud ? 32 : 0) | the mood's ordinal. Every reaction bumps it, so the client
     * (CrowdSounds) plays one each time, even a roar over a roar; a loud one cuts through him talking.
     */
    private static final EntityDataAccessor<Integer> DATA_PULSE = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    /** Fireworks in the flag's colours on the song's beats: (a counter << 8) | how many beats (client/FlagFireworks). */
    private static final EntityDataAccessor<Integer> DATA_SALVO = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    /** Phase four: how far round the bowl the moon's rays have turned the fans to skeletons (0..1; 1 all of them). */
    private static final EntityDataAccessor<Float> DATA_DEAD = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.FLOAT);
    /** Fire pyrotechnics round the podium on the song's beats: (a counter << 8) | how many beats (client/FlagFireworks). */
    private static final EntityDataAccessor<Integer> DATA_PYRO = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    /** The coliseum's size (its sand's semi-axes, a << 8 | b): El Coliseo de los Muertos is twice El Coliseo Papa's. */
    private static final EntityDataAccessor<Integer> DATA_SIZE = SynchedEntityData.defineId(ColiseumCrowdEntity.class, EntityDataSerializers.INT);
    /** Ticks from the fan's wind-up starting to the potato leaving his hand (the renderer's throw pose matches). */
    public static final int WINDUP = 6;
    /** How close to the stands (blocks in from the edge of the sand) a challenger has to be to get pelted. */
    private static final double NEAR = 7.0;

    private CrowdMood base = CrowdMood.CHEER;
    private int reactTicks;
    /** Client: ticks since the mood last changed (for blending poses and sounds). */
    private int moodTicks;
    private int lastMood = -1;
    private int prevMood;
    /** Server: seats, potatoes on their way out of hands, and when each challenger is due another. */
    @Nullable private List<Coliseum.Seat> seats;
    private final List<long[]> windups = new ArrayList<>();
    private final Map<UUID, Long> nextAt = new HashMap<>();
    private int throwCount;
    /** Client: when each seat last started a throw (client tick), for the arm. */
    private final Map<Integer, Integer> throwsSeen = new HashMap<>();
    private int lastThrow = -1;

    public ColiseumCrowdEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_MOOD, 0);
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_THROW, -1);
        entityData.define(DATA_LIGHTERS, 0F);
        entityData.define(DATA_PULSE, -1);
        entityData.define(DATA_SALVO, -1);
        entityData.define(DATA_PYRO, -1);
        entityData.define(DATA_DEAD, 0F);
        entityData.define(DATA_SIZE, Coliseum.A << 8 | Coliseum.B);
    }

    public float dead() { return entityData.get(DATA_DEAD); }

    public void setDead(float k) { entityData.set(DATA_DEAD, k); }

    public int sizeA() { return entityData.get(DATA_SIZE) >> 8; }

    public int sizeB() { return entityData.get(DATA_SIZE) & 0xFF; }

    public void setSize(int a, int b) {
        entityData.set(DATA_SIZE, a << 8 | b);
        seats = null;
    }

    /** Its seats (the coliseum's size). */
    public List<Coliseum.Seat> seatList() { return Coliseum.seats(seed(), sizeA(), sizeB()); }

    /** Client: ticks since the fan in seat {@code i} started a throw, or -1 if he is not throwing. */
    public int throwTicks(int i) {
        Integer at = throwsSeen.get(i);
        if (at == null) return -1;
        int d = tickCount - at;
        return d > WINDUP + 10 ? -1 : d;
    }

    /**
     * Every tick of the fight: anyone on the sand within {@link #NEAR} blocks of the stands, and not
     * pelted in the last few seconds, gets a potato from a fan in the nearest rows (a seat within
     * reach, chosen at random); it flies in an arc at where they will be, a little off at times.
     */
    public void pelt(ServerLevel level, @Nullable Coliseum arena) {
        if (arena == null || !arena.built()) return;
        if (seats == null) seats = seatList();
        long now = level.getGameTime();
        for (java.util.Iterator<long[]> it = windups.iterator(); it.hasNext(); ) {
            long[] w = it.next();
            if (now < w[0]) continue;
            it.remove();
            Entity e = level.getEntity((int) w[2]);
            if (e instanceof ServerPlayer p && p.isAlive()) launch(level, seats.get((int) w[1]), p);
        }
        if (now % 5 != 0) return;
        Vec3 c = position();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive() || !arena.inArena(p.position())) continue;
            double edge = Coliseum.offset(p.getX() - c.x, p.getZ() - c.z, sizeA(), sizeB());
            if (edge < -NEAR) continue;
            Long due = nextAt.get(p.getUUID());
            if (due != null && now < due) continue;
            // a fan near him: one of the closest dozen seats (the front rows), any of them
            int best = -1;
            List<int[]> near = new ArrayList<>();
            for (int i = 0; i < seats.size(); i++) {
                Coliseum.Seat s = seats.get(i);
                double dx = c.x + s.x() - p.getX(), dz = c.z + s.z() - p.getZ();
                double d2 = dx * dx + dz * dz;
                if (d2 < 16 * 16) near.add(new int[]{i, (int) d2});
            }
            if (near.isEmpty()) continue;
            near.sort(java.util.Comparator.comparingInt(a -> a[1]));
            best = near.get(random.nextInt(Math.min(12, near.size())))[0];
            windups.add(new long[]{now + WINDUP, best, p.getId()});
            throwCount = (throwCount + 1) & 0x7FFF;
            entityData.set(DATA_THROW, (throwCount << 16) | best);
            nextAt.put(p.getUUID(), now + 50 + random.nextInt(50));
        }
    }

    private void launch(ServerLevel level, Coliseum.Seat s, ServerPlayer p) {
        Vec3 from = position().add(s.x(), s.y() + 1.5, s.z());
        Vec3 lead = p.position().add(p.getDeltaMovement().multiply(1, 0, 1).scale(10)).add(0, p.getBbHeight() * 0.7, 0);
        if (random.nextInt(4) == 0) lead = lead.add((random.nextDouble() - 0.5) * 2.4, 0, (random.nextDouble() - 0.5) * 2.4);
        Vec3 d = lead.subtract(from);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        double ticks = Mth.clamp(flat / 0.85, 12, 32);
        double g = 0.04;
        Vec3 v = new Vec3(d.x / ticks, d.y / ticks + 0.5 * g * ticks, d.z / ticks).scale(1.04);     // a hair extra for the air drag
        level.addFreshEntity(new CrowdPotatoEntity(level, from, v));
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.9F, 0.7F + random.nextFloat() * 0.3F);
    }

    public float lighters() { return entityData.get(DATA_LIGHTERS); }

    public void setLighters(float f) { entityData.set(DATA_LIGHTERS, f); }

    public void setSeed(int seed) { entityData.set(DATA_SEED, seed); }

    public int seed() { return entityData.get(DATA_SEED); }

    public CrowdMood mood() { return CrowdMood.byId(entityData.get(DATA_MOOD)); }

    /** Client: the mood before this one, and how long this one has lasted. */
    public CrowdMood previousMood() { return CrowdMood.byId(prevMood); }

    public int moodTicks() { return moodTicks; }

    /** The standing mood (what they go back to after a reaction). */
    public void setMood(CrowdMood m) {
        base = m;
        if (reactTicks <= 0) entityData.set(DATA_MOOD, m.ordinal());
    }

    /** A burst of something for a while, then back to the standing mood. */
    public void react(CrowdMood m, int ticks) { react(m, ticks, false); }

    /** As {@link #react(CrowdMood, int)}; {@code loud}: the whole bowl, over anything (his entrance, the big moments). */
    public void react(CrowdMood m, int ticks, boolean loud) {
        reactTicks = ticks;
        entityData.set(DATA_MOOD, m.ordinal());
        pulse(m, loud);
    }

    /** Their noise once more (a roar, boos, a gasp) without changing what they are doing. */
    public void pulse(CrowdMood m, boolean loud) {
        pulses = (pulses + 1) & 0xFFFFFF;
        entityData.set(DATA_PULSE, (pulses << 6) | (loud ? 32 : 0) | m.ordinal());
    }

    private int pulses;
    private int salvos;

    /** Client: the latest pulse (see DATA_PULSE), -1 before the first. */
    public int pulseData() { return entityData.get(DATA_PULSE); }

    /** Fireworks off the top of the walls in green, white and red, a volley on each of the next {@code beats} beats of the song. */
    public void salvo(int beats) {
        salvos = (salvos + 1) & 0x7FFFFF;
        entityData.set(DATA_SALVO, (salvos << 8) | Math.min(127, beats));
    }

    /** (2026-10-04) Fireworks launched from the podium inside the bowl, bursting over the sand, on the next {@code beats} beats. */
    public void salvoInside(int beats) {
        salvos = (salvos + 1) & 0x7FFFFF;
        entityData.set(DATA_SALVO, (salvos << 8) | 0x80 | Math.min(127, beats));
    }

    private int pyros;

    /** The stage pyrotechnics' cues (client/PyroShow): the choreographed show, the columns on his side, a chase round the ring. */
    public static final int PYRO_SHOW = 0, PYRO_ACCENT = 1, PYRO_CHASE = 2;

    /** Fire pyrotechnics (client/PyroShow): {@code kind} a PYRO_ cue; a show runs over the next {@code beats} beats of the song. */
    public void pyro(int kind, int beats) {
        pyros = (pyros + 1) & 0x1FFFFF;
        entityData.set(DATA_PYRO, (pyros << 10) | (kind << 8) | Math.min(255, beats));
    }

    public int pyroData() { return entityData.get(DATA_PYRO); }

    /** Client: the latest salvo (see DATA_SALVO), -1 before the first. */
    public int salvoData() { return entityData.get(DATA_SALVO); }

    public boolean reacting() { return reactTicks > 0; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int th = entityData.get(DATA_THROW);
            if (th != lastThrow) {
                lastThrow = th;
                if (th >= 0) throwsSeen.put(th & 0xFFFF, tickCount);
                throwsSeen.values().removeIf(at -> tickCount - at > 40);
            }
        }
        int now = entityData.get(DATA_MOOD);
        if (now != lastMood) {
            prevMood = lastMood < 0 ? now : lastMood;
            lastMood = now;
            moodTicks = 0;
        } else { moodTicks++; }
        if (!level().isClientSide && reactTicks > 0 && --reactTicks == 0) entityData.set(DATA_MOOD, base.ordinal());
    }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(sizeA() + 24, Coliseum.HEIGHT + 4, sizeB() + 24); }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 200 * 200; }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_SEED, tag.getInt("Seed"));
        base = CrowdMood.byId(tag.getInt("Mood"));
        entityData.set(DATA_MOOD, base.ordinal());
        entityData.set(DATA_DEAD, tag.getFloat("Dead"));
        if (tag.contains("Size")) entityData.set(DATA_SIZE, tag.getInt("Size"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Seed", seed());
        tag.putInt("Mood", base.ordinal());
        tag.putFloat("Dead", dead());
        tag.putInt("Size", entityData.get(DATA_SIZE));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }
}
