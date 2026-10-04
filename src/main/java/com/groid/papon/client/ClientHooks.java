package com.groid.papon.client;

import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/** Client-only calls from common code (reached through DistExecutor only). */
public final class ClientHooks {
    private ClientHooks() {}

    /** The driver is holding jump (the van's horn). */
    public static boolean isJumping(Player player) {
        return player instanceof LocalPlayer lp && lp.input.jumping;
    }

    public static void caravanTick(CaravanEntity van) {
        CaravanSounds.tick(van);
    }

    /** The reformed chef's free menu. */
    public static void openTacoMenu(int vendorId) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new TacoMenuScreen(vendorId));
    }
}
