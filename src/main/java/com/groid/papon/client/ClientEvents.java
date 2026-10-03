package com.groid.papon.client;

import com.groid.papon.PaponMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game-bus wiring: cutscenes, camera shake, boss music, the boss bar. */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ScreenShake.tick();
        Cinematic.tick();
        BossMusic.tick();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (Cinematic.isActive()) event.setRoll(event.getRoll() + Cinematic.roll());
        ScreenShake.apply(event);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (Cinematic.isActive()) event.setFOV(Cinematic.fov());
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (Cinematic.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!Cinematic.isActive()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** During a cutscene only the chat, titles, subtitles and the action bar stay up. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!Cinematic.isActive()) return;
        NamedGuiOverlay o = event.getOverlay();
        if (o.id().equals(VanillaGuiOverlay.CHAT_PANEL.id()) || o.id().equals(VanillaGuiOverlay.TITLE_TEXT.id())
                || o.id().equals(VanillaGuiOverlay.SUBTITLES.id()) || o.id().equals(VanillaGuiOverlay.RECORD_OVERLAY.id())) {
            return;
        }
        event.setCanceled(true);
    }

    /** Letterbox bars. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        float k = Cinematic.bars(event.getPartialTick());
        if (k <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        int bar = Math.round(h * 0.11F * k);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        BossBarRenderer.render(event);
    }
}
