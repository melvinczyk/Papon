package com.groid.papon.boss;

import com.groid.papon.fx.Fx;
import com.groid.papon.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Chef Papon throws, summons or leaves lying about, one entity with a {@link Kind}: it flies
 * (server-driven, both sides moving it by its velocity so it looks smooth), and when it touches someone
 * it hands them to the boss ({@link PaponBossEntity#propHit}), who decides what that costs them. Drawn
 * by client/PropRenderer with the models from tools/gen_props.py. Never saved: a reload clears the air.
 */
public class BossProp extends Entity {
    public enum Kind {
        /** A meat cleaver: out on a curve, back to his hand. Hits once each way. */
        CLEAVER,
        /** A giant fry: hangs aimed at you for its warning, then flies straight and fast. */
        FRY,
        /** A tater tot, lobbed: bursts on landing into six little ones that burst in turn. */
        TOT,
        /** A little tot from the big one. */
        TOT_SMALL,
        /** A baked potato from the sky: straight down onto its warning. */
        METEOR,
        /** The burrito rolled round someone (follows them while it lasts). */
        BURRITO,
        /** Potato sprouts out of the ground round someone's ankles. */
        SPROUTS,
        /** The Spud Singularity: hangs where he puts it. */
        GIANT_SPUD,
        /** A wall of mash rolling outward as a ring: radius = {@link #radius()}. */
        MASH,
        /** A cleaver from one of his echoes: hangs aimed for its warning, then flies straight, end over end. */
        CLEAVER_DART,
        /** ¡Abucheo!: a tomato, a potato, a carrot thrown from the stands, lobbed. */
        PRODUCE
    }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(BossProp.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(BossProp.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DELAY = SynchedEntityData.defineId(BossProp.class, EntityDataSerializers.INT);

    @Nullable private UUID ownerId;
    @Nullable private UUID followId;
    private int life = 200;
    /** CLEAVER: the throw's outward velocity and which way it curves; FRY: the flight; METEOR: the landing spot. */
    private Vec3 aim = Vec3.ZERO;
    private float curve;
    private boolean returning;
    private final Set<Integer> hits = new HashSet<>();

    public BossProp(EntityType<? extends BossProp> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static BossProp create(ServerLevel level, Kind kind, PaponBossEntity owner, Vec3 at) {
        BossProp p = new BossProp(ModEntities.BOSS_PROP.get(), level);
        p.entityData.set(DATA_KIND, kind.ordinal());
        p.ownerId = owner.getUUID();
        p.setPos(at.x, at.y, at.z);
        return p;
    }

    // ---- set-up, per kind
    public BossProp life(int ticks) {
        life = ticks;
        return this;
    }

    public BossProp velocity(Vec3 v) {
        setDeltaMovement(v);
        return this;
    }

    public BossProp cleaver(Vec3 out, float curve) {
        this.aim = out;
        this.curve = curve;
        setDeltaMovement(out);
        return this;
    }

    /** A fry: waits {@code delay} ticks aimed along {@code dir}, then flies at {@code speed}. */
    public BossProp fry(Vec3 dir, double speed, int delay) {
        this.aim = dir.normalize().scale(speed);
        entityData.set(DATA_DELAY, delay);
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F);
        setXRot((float) -(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG));
        return this;
    }

    /** A meteor: from where it is now down onto {@code to} in {@code ticks}. */
    public BossProp meteor(Vec3 to, int ticks) {
        this.aim = to;
        this.life = ticks;
        setDeltaMovement(to.subtract(position()).scale(1.0 / ticks));
        return this;
    }

    public BossProp follow(Entity e) {
        followId = e.getUUID();
        return this;
    }

    public Kind kind() {
        return Kind.values()[Mth.clamp(entityData.get(DATA_KIND), 0, Kind.values().length - 1)];
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public void setRadius(float r) {
        entityData.set(DATA_RADIUS, r);
    }

    /** Ticks a fry still hangs before it flies (synced, the renderer shakes it). */
    public int delay() {
        return entityData.get(DATA_DELAY);
    }

    @Nullable
    public PaponBossEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel level)) return null;
        return level.getEntity(ownerId) instanceof PaponBossEntity b && b.isAlive() ? b : null;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_KIND, 0);
        entityData.define(DATA_RADIUS, 0F);
        entityData.define(DATA_DELAY, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Kind k = kind();
        if (level().isClientSide) {
            clientTick(k);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        PaponBossEntity boss = owner();
        if (boss == null || --life <= 0) {
            if (k == Kind.METEOR && boss != null) boss.propLand(this, aim);
            if (k == Kind.TOT || k == Kind.TOT_SMALL) burst(level, boss);
            discard();
            return;
        }
        switch (k) {
            case CLEAVER -> tickCleaver(level, boss);
            case FRY, CLEAVER_DART -> tickFry(level, boss);
            case TOT, TOT_SMALL -> tickTot(level, boss);
            case PRODUCE -> tickProduce(level, boss);
            case METEOR -> move();
            case BURRITO -> {
                Entity f = followId == null ? null : level.getEntity(followId);
                if (!(f instanceof LivingEntity le) || !le.isAlive() || !com.groid.papon.event.StunEvents.burrito(le)) {
                    Fx.chunks(level, position().add(0, 1, 0), 8, 0.25F, 0xEED69C, 0.3);
                    discard();
                    return;
                }
                setPos(f.getX(), f.getY(), f.getZ());
            }
            default -> {}
        }
    }

    private void move() {
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    private void clientTick(Kind k) {
        switch (k) {
            case FRY, CLEAVER_DART -> {
                if (delay() <= 0) move();
            }
            case TOT, TOT_SMALL, PRODUCE -> move();
            case METEOR -> {
                move();
                Vec3 v = getDeltaMovement().normalize();
                // a trail of fire and smoke behind it
                level().addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.FIRE.get(), -1, 0.9F, 1.6F,
                        0xFFFFFF, 1F, 12, 0), getX() - v.x * 0.8, getY() - v.y * 0.8 + 0.4, getZ() - v.z * 0.8, 0, 0.02, 0);
                if (tickCount % 2 == 0) {
                    level().addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.SMOKE.get(), -1, 0.8F, 1.8F,
                            0x4A4440, 0.7F, 20, 0), getX() - v.x * 1.6, getY() - v.y * 1.6 + 0.4, getZ() - v.z * 1.6, 0, 0.01, 0);
                }
            }
            default -> {}
        }
    }

    // ---- the cleaver: out on a curve, then home to his hand
    private void tickCleaver(ServerLevel level, PaponBossEntity boss) {
        int t = tickCount;
        if (!returning) {
            Vec3 v = aim.yRot(curve * t * 0.045F).scale(Math.max(0.15, 1.0 - t / 16.0));
            setDeltaMovement(v);
            if (t >= 16) {
                returning = true;
                hits.clear();
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 1.0F, 1.4F);
            }
        } else {
            Vec3 hand = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
            Vec3 to = hand.subtract(position());
            double d = to.length();
            if (d < 1.6 || t > 70) {
                discard();
                return;
            }
            double speed = Math.min(1.3, 0.35 + (t - 16) * 0.05);
            setDeltaMovement(getDeltaMovement().scale(0.6).add(to.scale(speed / d * 0.4)).normalize().scale(speed));
        }
        move();
        if (t % 5 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.5F, 1.8F);
        touch(level, boss, 0.7);
    }

    // ---- the fry: hangs, aimed, for its warning; then a straight, fast flight
    private void tickFry(ServerLevel level, PaponBossEntity boss) {
        int d = delay();
        if (d > 0) {
            entityData.set(DATA_DELAY, d - 1);
            setDeltaMovement(Vec3.ZERO);
            if (d == 1) {
                setDeltaMovement(aim);
                hasImpulse = true;
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.2F, 0.8F);
            }
            return;
        }
        if (getDeltaMovement().lengthSqr() < 1e-4) {
            return;                                   // stuck in the ground: lies there until it goes
        }
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        HitResult hr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hr.getType() == HitResult.Type.BLOCK) {
            setPos(hr.getLocation().x, hr.getLocation().y, hr.getLocation().z);
            setDeltaMovement(Vec3.ZERO);
            life = Math.min(life, 30);
            if (kind() == Kind.CLEAVER_DART) Fx.spark(level, hr.getLocation(), 5, 0.2, 0.3F, 0xE6EAF0);
            else Fx.chunks(level, hr.getLocation(), 6, 0.2F, Fx.POTATO, 0.3);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1.0F, 0.8F);
            return;
        }
        move();
        touch(level, boss, 0.6);
    }

    // ---- the tot: lobbed, bursts where it lands
    private void tickTot(ServerLevel level, PaponBossEntity boss) {
        Vec3 v = getDeltaMovement().add(0, -0.06, 0);
        setDeltaMovement(v);
        hasImpulse = true;
        Vec3 from = position();
        HitResult hr = level.clip(new ClipContext(from, from.add(v), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hr.getType() == HitResult.Type.BLOCK && tickCount > 3) {
            setPos(hr.getLocation().x, hr.getLocation().y, hr.getLocation().z);
            burst(level, boss);
            discard();
            return;
        }
        move();
        if (getY() < level.getMinBuildHeight()) discard();
    }

    // ---- produce from the stands: lobbed, hits whoever it lands on, splats
    private void tickProduce(ServerLevel level, PaponBossEntity boss) {
        Vec3 v = getDeltaMovement().add(0, -0.06, 0);
        setDeltaMovement(v);
        hasImpulse = true;
        touch(level, boss, 0.35);
        if (isRemoved()) return;
        Vec3 from = position();
        HitResult hr = level.clip(new ClipContext(from, from.add(v), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hr.getType() == HitResult.Type.BLOCK && tickCount > 6) {
            setPos(hr.getLocation().x, hr.getLocation().y, hr.getLocation().z);
            boss.propLand(this, position());
            discard();
            return;
        }
        move();
        if (getY() < level.getMinBuildHeight()) discard();
    }

    private void burst(ServerLevel level, @Nullable PaponBossEntity boss) {
        if (boss == null) return;
        boss.propLand(this, position());
        if (kind() == Kind.TOT) {
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + random.nextDouble() * 0.4;
                double s = 0.32 + random.nextDouble() * 0.12;
                BossProp small = create(level, Kind.TOT_SMALL, boss, position().add(0, 0.4, 0))
                        .velocity(new Vec3(Math.cos(a) * s, 0.55, Math.sin(a) * s)).life(60);
                level.addFreshEntity(small);
            }
        }
    }

    /** Anyone it touches goes to the boss, once (per pass, for the cleaver). */
    private void touch(ServerLevel level, PaponBossEntity boss, double pad) {
        AABB box = getBoundingBox().inflate(pad);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, boss::canHit)) {
            if (!hits.add(e.getId())) continue;
            boss.propHit(this, e);
            if (kind() == Kind.FRY || kind() == Kind.CLEAVER_DART || kind() == Kind.PRODUCE) {
                discard();
                return;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @SuppressWarnings("unused")
    private static BlockPos below(Vec3 v) {
        return BlockPos.containing(v.x, v.y - 0.5, v.z);
    }
}
