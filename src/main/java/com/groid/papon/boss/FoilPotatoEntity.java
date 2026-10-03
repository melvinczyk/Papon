package com.groid.papon.boss;

import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Phase two's shields (the Twilight Lich's idea, Chef Papon's way): baked potatoes in foil that
 * circle him. While any is left he takes only a third of every hit, and each blow glances off with
 * a clank; break them (a few hits each) to get at him. They are his: {@link PaponBossEntity} puts
 * them on their orbit every tick and wraps new ones when they are all gone and some time has passed.
 * Not saved: the boss wraps a fresh set after a reload.
 */
public class FoilPotatoEntity extends LivingEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.foil_potato.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks since the boss last placed it; it goes if he stops (he is gone). */
    private int orphan;

    public FoilPotatoEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Called by the boss every tick: here is where you are now. */
    public void hold(Vec3 at) {
        orphan = 0;
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 20 == 0) {
                level().addParticle(new com.groid.papon.fx.FxOptions(com.groid.papon.registry.ModParticles.SPARK.get(), 3, 0.25F, 0.2F,
                        0xEEF4FF, 1F, 8, 0), getX(), getY() + 0.7, getZ(), 0, 0, 0);
            }
            return;
        }
        if (++orphan > 40) discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof PaponBossEntity || source.getDirectEntity() instanceof MolotovEntity) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.2F, 0.7F + random.nextFloat() * 0.3F);
            com.groid.papon.fx.Fx.spark(level, position().add(0, 0.4, 0), 4, 0.3, 0.25F, 0xDDE6F0);
        }
        return hurt;
    }

    @Override
    protected void tickDeath() {
        if (level() instanceof ServerLevel level && deathTime == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9F, 1.8F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.2F, 0.8F);
            com.groid.papon.fx.Fx.chunks(level, position().add(0, 0.4, 0), 8, 0.22F, 0xD8DEE6, 0.4);
            com.groid.papon.fx.Fx.chunks(level, position().add(0, 0.4, 0), 6, 0.3F, com.groid.papon.fx.Fx.POTATO, 0.35);
        }
        remove(RemovalReason.KILLED);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(net.minecraft.world.entity.Entity e) {}
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public Iterable<ItemStack> getArmorSlots() { return NonNullList.withSize(4, ItemStack.EMPTY); }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, e -> e.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
