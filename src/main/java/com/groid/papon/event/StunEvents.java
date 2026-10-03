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

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player p && stunned(p)) {
            p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1));
        }
    }
}
