package com.groid.papon.event;

import com.groid.papon.PaponMod;
import com.groid.papon.registry.ModEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What {@link com.groid.papon.effect.StunnedEffect} cannot do with an attribute: a stunned player
 * lies in the crawl pose (set after the game picks the pose each tick, on both sides, so their
 * own view and everyone else's agree) and cannot jump. Walking is already stopped by the effect.
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID)
public final class StunEvents {
    private StunEvents() {}

    public static boolean stunned(Player p) {
        return p.hasEffect(ModEffects.STUNNED.get());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player p = event.player;
        if (!stunned(p) || p.isPassenger() || p.isSpectator()) return;
        p.setSprinting(false);
        if (p.getPose() != Pose.SWIMMING) p.setPose(Pose.SWIMMING);
    }

    /** Wrapped in a tortilla or rooted by sprouts: feet stuck. */
    public static boolean bound(net.minecraft.world.entity.LivingEntity e) {
        return e.hasEffect(ModEffects.WRAPPED.get()) || e.hasEffect(ModEffects.ROOTED.get());
    }

    /** Wrapped: hands stuck too. */
    public static boolean wrapped(net.minecraft.world.entity.LivingEntity e) {
        return e.hasEffect(ModEffects.WRAPPED.get());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if ((event.getEntity() instanceof Player p && stunned(p)) || bound(event.getEntity())) {
            var e = event.getEntity();
            e.setDeltaMovement(e.getDeltaMovement().multiply(0, 0, 0));
        }
    }

    // ---- wrapped: no swinging, no using, no building
    @SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (wrapped(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUse(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (wrapped(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (event.isCancelable() && wrapped(event.getEntity())) event.setCanceled(true);
    }

    /** Food Poisoning: no healing of any kind. */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.FOOD_POISONING.get())) event.setCanceled(true);
    }
}
