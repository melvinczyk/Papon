package com.groid.papon.entity;

import com.groid.papon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Die Glocke (tools/gen_glocke.py): the bell of the postwar wonder-weapon legend. No use yet, only the model: placed with its
 * spawn egg, right-click to run it (the two drums spin up the opposite ways, the Xerum slits light, it hums and trembles,
 * violet particles off the window; client/DieGlockeRenderer), again to wind it down; hit it to take it back (the egg, unless
 * in creative). Solid, heavy: falls, nothing pushes it, explosions and fire leave it alone.
 */
public class DieGlockeEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(DieGlockeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.die_glocke.idle");
    /** Drum speed at full run, degrees a tick. */
    private static final float MAX_SPIN = 30F;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client: how fast the drums turn (0..1, eased up over ~3 s, down over ~5 s) and where they are (degrees, unwrapped). */
    public float spin, prevSpin;
    public double angle, prevAngle;
    /** Client: the hum playing (client/DieGlockeRenderer.Hum), or null. */
    public Object hum;

    public DieGlockeEntity(EntityType<?> type, Level level) { super(type, level); }

    @Override
    protected void defineSynchedData() { entityData.define(DATA_ACTIVE, false); }

    public boolean active() { return entityData.get(DATA_ACTIVE); }

    @Override
    public void tick() {
        super.tick();
        if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(onGround() ? getDeltaMovement().multiply(0.5, 0, 0.5) : getDeltaMovement().scale(0.98));
        }
        if (!level().isClientSide) return;
        prevSpin = spin;
        spin += ((active() ? 1F : 0F) - spin) * (active() ? 0.025F : 0.015F);
        if (spin < 0.002F && !active()) spin = 0;
        prevAngle = angle;
        angle += spin * MAX_SPIN;
        if (angle > 3600) { angle -= 3600; prevAngle -= 3600; }
        if (spin > 0.25F && random.nextFloat() < spin * 0.7F) {
            float a = random.nextFloat() * Mth.TWO_PI, r = 0.85F;
            double y = getY() + 1.0 + random.nextFloat() * 0.5;
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Mth.cos(a) * r, y, getZ() + Mth.sin(a) * r,
                    Mth.cos(a) * 0.04, 0.01, Mth.sin(a) * 0.04);
        }
        if (spin > 0.85F && random.nextFloat() < 0.06F) {
            float a = random.nextFloat() * Mth.TWO_PI;
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + Mth.cos(a) * 0.9, getY() + 0.12, getZ() + Mth.sin(a) * 0.9, 0, 0.05, 0);
        }
        if (spin > 0.01F) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.groid.papon.client.ClientHooks.glockeTick(this));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide) {
            boolean on = !active();
            entityData.set(DATA_ACTIVE, on);
            level().playSound(null, getX(), getY() + 1.2, getZ(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.2F, 0.5F);
            player.displayClientMessage(Component.translatable("message.papon.die_glocke." + (on ? "on" : "off"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player player) || isRemoved()) return false;
        if (!level().isClientSide) {
            if (!player.getAbilities().instabuild) spawnAtLocation(ModItems.DIE_GLOCKE_SPAWN_EGG.get());
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean ignoreExplosion() { return true; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(DATA_ACTIVE, tag.getBoolean("Active")); }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { tag.putBoolean("Active", active()); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
