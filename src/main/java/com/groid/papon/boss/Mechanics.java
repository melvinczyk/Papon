package com.groid.papon.boss;

import com.groid.papon.PaponConfig;
import com.groid.papon.fx.Fx;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModDamage;
import com.groid.papon.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Chef Papon's mechanics: what hurts whatever your stats. The pack's players hit for tens of
 * thousands and wear thousands of armor, so his big moments do not deal points of damage but shares
 * of your maximum health, through armor, enchantments and shields (papon:true_hit), and take things
 * away from you: your armor (Brittle), your healing (Food Poisoning), your feet (Rooted), your hands
 * (Wrapped), your footing (Stunned), your time (Habanero burning and boiling over). All of it is
 * avoidable: every one of these is telegraphed.
 */
public final class Mechanics {
    /** Stacks of Habanero that boil over. */
    public static final int HEAT_MAX = 5;

    private Mechanics() {}

    private static float scale() {
        return PaponConfig.TRUE_DAMAGE.get().floatValue();
    }

    /** {@code frac} of the target's maximum health, through armor; ignores the pause between hits when {@code pierce}. */
    public static boolean trueHit(LivingEntity e, float frac, @Nullable Entity attacker, boolean pierce) {
        if (!(e.level() instanceof ServerLevel level) || frac <= 0) return false;
        if (pierce) e.invulnerableTime = 0;
        return e.hurt(ModDamage.of(level, ModDamage.TRUE_HIT, attacker, attacker), e.getMaxHealth() * frac * scale());
    }

    /**
     * Adds Habanero stacks (each refreshes the six-second fuse); at {@link #HEAT_MAX} it boils over: a
     * third of their maximum health at once, a burst of flame, and the stacks are gone.
     */
    public static void heat(LivingEntity e, int stacks, @Nullable Entity attacker) {
        if (!(e.level() instanceof ServerLevel level)) return;
        MobEffectInstance cur = e.getEffect(ModEffects.HABANERO.get());
        int have = cur == null ? 0 : cur.getAmplifier() + 1;
        int now = have + stacks;
        if (now >= HEAT_MAX) {
            e.removeEffect(ModEffects.HABANERO.get());
            trueHit(e, 0.33F, attacker, true);
            e.setSecondsOnFire(4);
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Fx.blast(level, at, 1.4F);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5F, 0.6F);
            if (e instanceof ServerPlayer sp) {
                PaponNetwork.shake(sp, 0.8F, 16);
                sp.displayClientMessage(Component.translatable("status.papon.picante").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
            return;
        }
        e.addEffect(new MobEffectInstance(ModEffects.HABANERO.get(), 120, now - 1, false, true, true));
        if (e instanceof ServerPlayer sp && now >= HEAT_MAX - 1) {
            sp.displayClientMessage(Component.translatable("status.papon.heat", now, HEAT_MAX).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }

    public static void poison(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(ModEffects.FOOD_POISONING.get(), ticks, 0, false, true, true));
    }

    public static void wrap(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(ModEffects.WRAPPED.get(), ticks, 0, false, false, true));
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
    }

    public static void root(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(ModEffects.ROOTED.get(), ticks, 0, false, false, true));
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
    }

    public static void stun(LivingEntity e) {
        int stun = PaponConfig.VAN_STUN_TICKS.get();
        if (stun > 0) e.addEffect(new MobEffectInstance(ModEffects.STUNNED.get(), stun, 0, false, false, true));
    }

    public static void brittle(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(ModEffects.BRITTLE.get(), ticks, 0, false, true, true));
    }
}
