package com.groid.papon.boss;

import com.groid.papon.fx.Fx;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * One of Chef Papon's echoes (Los Tres Chefs): a see-through copy of him that blinks in high in the
 * air beside or behind you, cocks a cleaver over its shoulder and throws it at where you are going
 * (in phase two, the other one after it), then goes. A red line on the ground and a glint in its fist
 * give the throw away. Hit one before it throws and it shatters, throw and all.
 */
public class PaponEchoEntity extends Entity implements GeoEntity {
    /** The echo's animation (papon_boss.animation.json echo_throw): appear, two throws, fade. */
    public static final int LIFE = 44;
    public static final int THROW_1 = 16, THROW_2 = 28;
    private static final RawAnimation THROW = RawAnimation.begin().thenPlayAndHold("animation.papon_boss.echo_throw");
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(PaponEchoEntity.class, EntityDataSerializers.INT);
    /** 0 an echo with cleavers; 1 a skeleton dancer (phase four's La Danza: moved by the boss); 2 the dancer spinning through. */
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(PaponEchoEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation DANCE = RawAnimation.begin().thenLoop("animation.papon_boss.d_dancer");
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.papon_boss.d_dancer_spin");
    private Vec3 lunge = Vec3.ZERO;
    private final java.util.Set<Integer> struck = new java.util.HashSet<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int ownerId = -1;
    private int throwsLeft = 1;
    private Vec3 aim1;

    public PaponEchoEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public static PaponEchoEntity create(ServerLevel level, PaponBossEntity boss, LivingEntity target, Vec3 at, int throwsN) {
        PaponEchoEntity e = new PaponEchoEntity(com.groid.papon.registry.ModEntities.PAPON_ECHO.get(), level);
        e.ownerId = boss.getId();
        e.throwsLeft = throwsN;
        e.entityData.set(DATA_TARGET, target.getId());
        e.setPos(at.x, at.y, at.z);
        e.face(target.position());
        return e;
    }

    /** A skeleton dancer of La Danza de los Muertos: the boss moves it (round you, then through you). */
    public static PaponEchoEntity dancer(ServerLevel level, PaponBossEntity boss, LivingEntity target, Vec3 at) {
        PaponEchoEntity e = create(level, boss, target, at, 0);
        e.entityData.set(DATA_MODE, 1);
        return e;
    }

    public boolean isDancer() { return entityData.get(DATA_MODE) > 0; }

    public boolean spinning() { return entityData.get(DATA_MODE) == 2; }

    public void faceTo(Vec3 at) { face(at); }

    /** It is about to spin straight through along {@code dir}. */
    public void lungeDir(Vec3 dir) {
        lunge = dir;
        entityData.set(DATA_MODE, 2);
        face(position().add(dir));
    }

    public Vec3 lungeDirection() { return lunge; }

    /** True the first time it touches {@code id} on its way through. */
    public boolean hitOnce(int id) { return struck.add(id); }

    public void vanishNow(ServerLevel level) { vanish(level, false); }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_TARGET, -1);
        entityData.define(DATA_MODE, 0);
    }

    @Nullable
    private LivingEntity target() { return level().getEntity(entityData.get(DATA_TARGET)) instanceof LivingEntity le && le.isAlive() ? le : null; }

    @Nullable
    private PaponBossEntity owner() { return level().getEntity(ownerId) instanceof PaponBossEntity b && b.isAlive() ? b : null; }

    private void face(Vec3 at) {
        float yaw = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yRotO = yaw;
    }

    /** 0..1: how solid it looks (it flickers in, and fades out at the end). */
    public float presence(float partialTick) {
        float t = tickCount + partialTick;
        return Mth.clamp(Math.min(t / 6F, (LIFE - t) / 8F), 0F, 1F);
    }

    @Override
    public void tick() {
        super.tick();
        if (isDancer()) {
            if (level().isClientSide) {
                if (tickCount % 3 == 0) {
                    level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5),
                            getY() + random.nextDouble() * 2.6, getZ() + (random.nextDouble() - 0.5), 0, 0.02, 0);
                }
                return;
            }
            if (owner() == null || tickCount > 240) vanish((ServerLevel) level(), false);
            return;
        }
        LivingEntity target = target();
        if (target != null) face(target.position());
        // a slow bob, hanging in the air
        setPos(getX(), getY() + Mth.sin(tickCount * 0.25F) * 0.02, getZ());
        if (level().isClientSide) {
            if (tickCount < 3 || tickCount % 6 == 0) {
                level().addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.SMOKE.get(), -1, 0.7F, 1.6F,
                        0xFFD27A, 0.5F, 14, 0), getX() + (random.nextDouble() - 0.5), getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - 0.5),
                        0, 0.02, 0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        PaponBossEntity boss = owner();
        if (boss == null || target == null || tickCount >= LIFE) {
            vanish(level, false);
            return;
        }
        if (tickCount == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.6F, 0.8F);
            Fx.flash(level, position().add(0, 1.2, 0), 1.6F);
        }
        for (int at : new int[] {THROW_1, THROW_2}) {
            if (at == THROW_2 && throwsLeft < 2) continue;
            if (tickCount == at - 10) {
                // the tell: where it will land, drawn on the ground, and a glint in its fist
                aim1 = boss.predict(target, 14).add(0, 0.9, 0);
                Vec3 g = new Vec3(aim1.x, target.getY(), aim1.z);
                Fx.warnLine(level, new Vec3(getX(), target.getY(), getZ()), g, 0.6F, Fx.DANGER, 10);
                Fx.glint(level, hand(), 0.9F, 0xFFFFFF);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.HOSTILE, 0.8F, 1.8F);
            }
            if (tickCount == at && aim1 != null) {
                Vec3 from = hand();
                Vec3 aim = boss.predict(target, 8).add(0, 0.9, 0).lerp(aim1, 0.4).subtract(from);
                level.addFreshEntity(BossProp.create(level, BossProp.Kind.CLEAVER_DART, boss, from).fry(aim, 1.6, 2).life(50));
                level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.4F, 1.3F);
            }
        }
    }

    private Vec3 hand() {
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        return position().add(f.scale(0.6)).add(new Vec3(-f.z, 0, f.x).scale(0.7)).add(0, 2.4, 0);
    }

    private void vanish(ServerLevel level, boolean shattered) {
        Fx.smoke(level, position().add(0, 1.2, 0), shattered ? 8 : 4, 0.6, 0.9F, isDancer() ? 0x7FE8FF : 0xFFD27A);
        if (shattered) {
            Fx.spark(level, position().add(0, 1.4, 0), 10, 0.6, 0.35F, 0xFFD27A);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 0.7F);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || source.getEntity() instanceof PaponBossEntity) return false;
        if (level() instanceof ServerLevel level && !isRemoved()) vanish(level, true);
        return true;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 128 * 128; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, e -> e.setAndContinue(spinning() ? SPIN : isDancer() ? DANCE : THROW)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
