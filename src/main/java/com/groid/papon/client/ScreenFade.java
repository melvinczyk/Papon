package com.groid.papon.client;

import com.groid.papon.PaponMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A fade to black and back over everything, HUD included (PaponNetwork.Fade: brought back to the Taco Truck). */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScreenFade {
    private static long start = -1;
    private static int in, hold, out;

    private ScreenFade() {}

    public static void start(int fadeIn, int black, int fadeOut) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        start = mc.level.getGameTime();
        in = Math.max(1, fadeIn);
        hold = black;
        out = Math.max(1, fadeOut);
    }

    /** How black the screen is right now, 0..1. */
    private static float alpha(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (start < 0 || mc.level == null) return 0;
        float t = mc.level.getGameTime() - start + partial;
        if (t < 0) return 0;
        if (t < in) return t / in;
        if (t < in + hold) return 1;
        if (t < in + hold + out) return 1 - (t - in - hold) / out;
        start = -1;
        return 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float a = alpha(event.getPartialTick());
        if (a <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int argb = ((int) (a * 255) & 0xFF) << 24;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb);
    }
}
