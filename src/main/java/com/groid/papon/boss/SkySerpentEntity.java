package com.groid.papon.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ouros and Boros, the Final Guardian's feathered serpents (phase four): he shoots the moon with his bow, black smoke tears
 * open in the sky, and out of it they come, colossal, down onto an orbit round the arena. The entity stands still at the
 * arena's middle; the serpent itself is a path ({@link SerpentPath}) both sides work out from the synced numbers, its head
 * {@link #SPEED} blocks a tick along it since it was born, its body the path behind. The boss sends it diving across the
 * floor ({@link #dive}); drawn by client/SkySerpentRenderer (Dungeon Now Loading's Fairkeeper serpents, repainted:
 * tools/gen_serpents.py). Never saved: the boss calls them again.
 */
public class SkySerpentEntity extends Entity {
    public static final int OUROS = 0, BOROS = 1;
    /** Blocks a tick its head travels. */
    public static final double SPEED = 1.6;
    /** The body: segments this far apart (blocks), this many and the tail; the model's scale. */
    public static final double SCALE = 4.6, SEGMENT = 47 / 16.0 * SCALE * 0.94, HEAD = 44 / 16.0 * SCALE;
    public static final int SEGMENTS = 15;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(SkySerpentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BORN = SynchedEntityData.defineId(SkySerpentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_PORTAL = SynchedEntityData.defineId(SkySerpentEntity.class, EntityDataSerializers.VECTOR3);
    /** Orbit radius, height over the entity, direction (+1 / -1). */
    private static final EntityDataAccessor<Vector3f> DATA_ORBIT = SynchedEntityData.defineId(SkySerpentEntity.class, EntityDataSerializers.VECTOR3);
    /** The last two dives: [s0, ax, ay, az, bx, by, bz] each. */
    private static final EntityDataAccessor<CompoundTag> DATA_DIVES = SynchedEntityData.defineId(SkySerpentEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** The game tick it last roared (its jaws open wide). */
    private static final EntityDataAccessor<Integer> DATA_ROAR = SynchedEntityData.defineId(SkySerpentEntity.class, EntityDataSerializers.INT);

    @Nullable private SerpentPath path;
    private CompoundTag pathKey;
    private int life = 20 * 60 * 30;

    public SkySerpentEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** A serpent out of {@code portal}, to circle {@code center} at {@code radius}, {@code height} over it. */
    public static SkySerpentEntity create(ServerLevel level, int kind, Vec3 center, Vec3 portal, double radius, double height, int dir) {
        SkySerpentEntity s = new SkySerpentEntity(com.groid.papon.registry.ModEntities.SKY_SERPENT.get(), level);
        s.moveTo(center.x, center.y, center.z, 0, 0);
        s.entityData.set(DATA_KIND, kind);
        s.entityData.set(DATA_BORN, (int) (level.getGameTime() & 0x7FFFFFFF));
        s.entityData.set(DATA_PORTAL, portal.toVector3f());
        s.entityData.set(DATA_ORBIT, new Vector3f((float) radius, (float) height, dir));
        return s;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_KIND, OUROS);
        entityData.define(DATA_BORN, 0);
        entityData.define(DATA_PORTAL, new Vector3f());
        entityData.define(DATA_ORBIT, new Vector3f(100, 50, 1));
        entityData.define(DATA_DIVES, new CompoundTag());
        entityData.define(DATA_ROAR, -1000);
    }

    public int kind() {
        return entityData.get(DATA_KIND);
    }

    /** Ticks since it came out of its portal. */
    public double age(float partial) {
        long now = level().getGameTime() & 0x7FFFFFFFL;
        long d = (now - entityData.get(DATA_BORN)) & 0x7FFFFFFFL;
        return d > (1L << 30) ? 0 : d + partial;
    }

    /** How far along its path its head is. */
    public double head(float partial) {
        return age(partial) * SPEED;
    }

    /** Puts it out of its portal long ago (already circling: after a reload). */
    public void circling() {
        entityData.set(DATA_BORN, (int) ((level().getGameTime() - 20 * 40) & 0x7FFFFFFF));
    }

    public Vec3 portal() {
        return new Vec3(entityData.get(DATA_PORTAL));
    }

    public SerpentPath path() {
        CompoundTag key = entityData.get(DATA_DIVES);
        if (path == null || pathKey != key) {
            Vector3f o = entityData.get(DATA_ORBIT);
            List<SerpentPath.Dive> dives = new ArrayList<>();
            for (Tag t : key.getList("d", Tag.TAG_LIST)) {
                ListTag l = (ListTag) t;
                dives.add(new SerpentPath.Dive(l.getDouble(0), new Vec3(l.getDouble(1), l.getDouble(2), l.getDouble(3)),
                        new Vec3(l.getDouble(4), l.getDouble(5), l.getDouble(6))));
            }
            path = new SerpentPath(portal(), position(), o.x(), o.y(), (int) o.z(), dives);
            pathKey = key;
        }
        return path;
    }

    /**
     * Sends it down across the floor from {@code a} to {@code b}: it leaves its orbit {@code lead} blocks of path from now.
     * Returns the tick (from now) its head reaches {@code a}.
     */
    public int dive(Vec3 a, Vec3 b, double lead) {
        double s0 = head(0) + lead;
        CompoundTag old = entityData.get(DATA_DIVES);
        ListTag list = new ListTag();
        ListTag prev = old.getList("d", Tag.TAG_LIST);
        if (!prev.isEmpty()) list.add(prev.get(prev.size() - 1));
        ListTag d = new ListTag();
        for (double v : new double[]{s0, a.x, a.y, a.z, b.x, b.y, b.z}) d.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        list.add(d);
        CompoundTag tag = new CompoundTag();
        tag.put("d", list);
        entityData.set(DATA_DIVES, tag);
        SerpentPath p = path();
        // how far into the dive the floor line starts: the way down's length
        double down = 0;
        for (double s = s0; s < s0 + p.diveLength(s0); s += 1) {
            if (p.at(s).distanceTo(a) < 1.5) {
                down = s - s0;
                break;
            }
        }
        return (int) Math.ceil((lead + down) / SPEED);
    }

    /** Whether the last dive is still going (its body is still low). */
    public boolean busy() {
        SerpentPath p = path();
        double h = head(0);
        CompoundTag key = entityData.get(DATA_DIVES);
        ListTag list = key.getList("d", Tag.TAG_LIST);
        if (list.isEmpty()) return false;
        double s0 = ((ListTag) list.get(list.size() - 1)).getDouble(0);
        double end = s0 + p.diveLength(s0) + HEAD + SEGMENT * (SEGMENTS + 1);
        return h < end;
    }

    /** Points down its length now (head first), for its dives' hits. */
    public List<Vec3> body(float partial) {
        SerpentPath p = path();
        double h = head(partial);
        List<Vec3> out = new ArrayList<>();
        for (double s = h; s > h - HEAD - SEGMENT * (SEGMENTS + 1); s -= 3) {
            if (s < 0) break;
            out.add(p.at(s));
        }
        return out;
    }

    public void roar() {
        entityData.set(DATA_ROAR, (int) (level().getGameTime() & 0x7FFFFFFF));
        if (level() instanceof ServerLevel level) {
            Vec3 h = path().at(head(0));
            level.playSound(null, h.x, h.y, h.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, kind() == OUROS ? 0.55F : 0.42F);
        }
    }

    /** How wide its jaws are open (0..1): a roar, then the jaws working slowly. */
    public float jaw(float partial) {
        long now = level().getGameTime() & 0x7FFFFFFFL;
        long d = (now - entityData.get(DATA_ROAR)) & 0x7FFFFFFFL;
        float t = d > (1L << 30) ? 999 : d + partial;
        float roar = t < 50 ? (float) Math.sin(Math.min(1, t / 8.0) * Math.PI / 2) * (t > 34 ? 1 - (t - 34) / 16F : 1) : 0;
        float idle = 0.12F + 0.1F * (float) Math.sin((tickCount + partial) * 0.05 + kind() * 2);
        return Math.max(roar, idle);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (--life <= 0) discard();
        double h = head(0);
        // the hiss as it comes out of the smoke, the beat of it overhead
        if (tickCount % 70 == 0 && h > 0) {
            Vec3 at = path().at(h);
            level().playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 4.0F, 0.4F);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(400);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
