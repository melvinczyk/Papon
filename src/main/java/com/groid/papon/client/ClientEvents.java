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

    /** During a cutscene only titles and sound subtitles stay up (dialogue is subtitled in the bars). */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!Cinematic.isActive()) return;
        NamedGuiOverlay o = event.getOverlay();
        if (o.id().equals(VanillaGuiOverlay.TITLE_TEXT.id()) || o.id().equals(VanillaGuiOverlay.SUBTITLES.id())) {
            return;
        }
        event.setCanceled(true);
    }

    /** In the van: no row of a hundred hearts for its health; a speedometer instead. */
    @SubscribeEvent
    public static void onVanHud(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof com.groid.papon.vehicle.CaravanEntity van)) return;
        if (event.getOverlay().id().equals(VanillaGuiOverlay.MOUNT_HEALTH.id())) {
            event.setCanceled(true);
            if (van.getControllingPassenger() != mc.player) return;
            double kmh = CaravanSounds.speedOf(van) * 20 * 3.6;
            var text = net.minecraft.network.chat.Component.translatable("hud.papon.speed", (int) Math.round(kmh))
                    .withStyle(net.minecraft.ChatFormatting.WHITE);
            int w = event.getWindow().getGuiScaledWidth();
            int h = event.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().drawString(mc.font, text, w / 2 + 10, h - 49, 0xFFFFFF, true);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        // the cutscene camera stands where it likes; the player's own body must not block the shot
        if (Cinematic.isActive() && event.getEntity() == Minecraft.getInstance().player) event.setCanceled(true);
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
        // subtitles in the bottom bar: the line as spoken, its translation underneath
        var line = com.groid.papon.entity.Speech.decode(Cinematic.subjectSpeech());
        if (line == null || k < 1) return;
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        String key = line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : null;
        int y = h - bar + 4;
        for (var row : font.split(line, w - 40)) {
            g.drawString(font, row, (w - font.width(row)) / 2, y, 0xFFFFFF, false);
            y += 10;
        }
        if (key != null && net.minecraft.client.resources.language.I18n.exists(key + ".gloss")) {
            var gloss = net.minecraft.network.chat.Component.translatable(key + ".gloss")
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
            for (var row : font.split(gloss, w - 40)) {
                g.drawString(font, row, (w - font.width(row)) / 2, y, 0xAAAAAA, false);
                y += 10;
            }
        }
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        BossBarRenderer.render(event);
    }
}
