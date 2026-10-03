package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.network.PaponNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The QTE on the held player's screen: one key at a time in a big keycap, a bar running out under
 * it (a second), the keys still to come as dots. Press the right one in time and the next comes up;
 * the wrong one, or too late, and it is over. While it runs, the presses are swallowed (no inventory
 * opening, no mod hotkeys firing), and the camera is on him in slow motion (Cinematic).
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, value = Dist.CLIENT)
public final class QteClient {
    private static boolean active;
    private static boolean locked;
    private static int bossId = -1;
    private static int kind;
    private static int[] keys = new int[0];
    private static int window = 20;
    private static int step;
    private static int stepTicks;
    /** Ticks since the last press, and whether it was right (the keycap flashes). */
    private static int flashTicks = 99;
    private static boolean flashGood;
    /** After it ends: ticks the result stays up, and which it was. */
    private static int endTicks;
    private static boolean endWon;
    private static int beat;

    private QteClient() {}

    public static void start(int boss, int k, int[] ks, int w) {
        active = true;
        locked = false;
        bossId = boss;
        kind = k;
        keys = ks;
        window = Math.max(4, w);
        step = 0;
        stepTicks = 0;
        flashTicks = 99;
        endTicks = 0;
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BELL.get(), 0.6F));
    }

    public static void end(boolean won) {
        active = false;
        locked = true;
        endWon = won;
        endTicks = 40;
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND, won ? 1.4F : 0.6F));
    }

    /** True while the camera should be on this boss for the QTE (the sequence, and a moment after it). */
    public static boolean focus(PaponBossEntity boss) {
        return boss.getId() == bossId && (active || endTicks > 14);
    }

    public static boolean active() {
        return active;
    }

    /** Dev (AutoShot): press the right key now, as a player would. */
    public static void devPress() {
        if (!active || locked || step >= keys.length) return;
        PaponNetwork.qteInput(bossId, step, true);
        step++;
        stepTicks = 0;
        flashGood = true;
        flashTicks = 0;
        if (step >= keys.length) locked = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        flashTicks++;
        if (endTicks > 0) endTicks--;
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(bossId) instanceof PaponBossEntity boss)
                || boss.getBossState() == com.groid.papon.boss.BossState.DEATH || (locked && stepTicks++ > 60)) {
            active = false;
            return;
        }
        // slow motion: a slow heartbeat under it all
        if (++beat % 16 == 0) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.6F));
        }
        if (locked) return;
        if (++stepTicks > window) {
            PaponNetwork.qteInput(bossId, step, false);
            locked = true;
            flashGood = false;
            flashTicks = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        if (!active || Minecraft.getInstance().screen != null) return;
        int key = event.getKey();
        if (event.getAction() != GLFW.GLFW_PRESS) {
            swallow();
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F1 || key == GLFW.GLFW_KEY_F2 || key == GLFW.GLFW_KEY_F3
                || (key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER)) return;
        if (!locked && step < keys.length) {
            boolean ok = key == keys[step];
            PaponNetwork.qteInput(bossId, step, ok);
            flashGood = ok;
            flashTicks = 0;
            if (ok) {
                step++;
                stepTicks = 0;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.NOTE_BLOCK_PLING.get(), 1.0F + step * 0.15F));
                if (step >= keys.length) locked = true;
            } else {
                locked = true;
            }
        }
        swallow();
    }

    /** Nothing else gets the key: no inventory, no hotbar, no mod hotkeys. */
    private static void swallow() {
        for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
            while (m.consumeClick()) {
                // drained
            }
        }
        KeyMapping.releaseAll();
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!active && endTicks <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        float pt = event.getPartialTick();
        int cx = w / 2;
        int cy = (int) (h * 0.62F);
        if (active || endTicks > 14) vignette(g, w, h, active ? 1F : (endTicks - 14) / 26F);
        if (!active) {
            // the result
            Component r = Component.translatable(endWon ? "qte.papon.won" : "qte.papon.failed")
                    .withStyle(endWon ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            float s = 3F + Math.max(0, (endTicks - 30) / 10F);
            g.pose().scale(s, s, 1);
            g.drawString(font, r, -font.width(r) / 2, -4, 0xFFFFFF, true);
            g.pose().popPose();
            return;
        }
        // the title
        Component title = Component.translatable("qte.papon.title." + kind).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.pose().pushPose();
        g.pose().translate(cx, cy - 46, 0);
        g.pose().scale(1.5F, 1.5F, 1);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFFFFF, true);
        g.pose().popPose();
        if (step < keys.length) {
            // the keycap: grows in, shakes as time runs out, flashes on a press
            float left = 1F - Mth.clamp((stepTicks + pt) / window, 0, 1);
            int size = 34;
            float pop = Math.min(1F, (stepTicks + pt) / 3F);
            int sz = (int) (size * (0.7F + 0.3F * pop));
            int jx = left < 0.35F ? (int) ((mc.level.random.nextFloat() - 0.5F) * 4) : 0;
            int x0 = cx - sz / 2 + jx, y0 = cy - sz / 2;
            int edge = flashTicks < 4 ? (flashGood ? 0xFF55FF55 : 0xFFFF4444) : (left < 0.35F ? 0xFFFF6A3A : 0xFFFFD27A);
            g.fill(x0 - 2, y0 - 2, x0 + sz + 2, y0 + sz + 2, edge);
            g.fill(x0, y0, x0 + sz, y0 + sz, 0xE0201812);
            g.fill(x0 + 2, y0 + sz - 5, x0 + sz - 2, y0 + sz - 2, 0xFF3A2C20);
            String name = InputConstants.Type.KEYSYM.getOrCreate(keys[step]).getDisplayName().getString().toUpperCase();
            g.pose().pushPose();
            g.pose().translate(cx + jx, cy, 0);
            g.pose().scale(2.4F * (0.7F + 0.3F * pop), 2.4F * (0.7F + 0.3F * pop), 1);
            g.drawString(font, name, -font.width(name) / 2, -4, 0xFFFFFF, true);
            g.pose().popPose();
            // the time running out
            int bw = 80;
            int by = cy + size / 2 + 8;
            g.fill(cx - bw / 2 - 1, by - 1, cx + bw / 2 + 1, by + 5, 0xC0000000);
            int col = left > 0.5F ? 0xFFFFD27A : left > 0.25F ? 0xFFFF9A3C : 0xFFFF3A2A;
            g.fill(cx - bw / 2, by, cx - bw / 2 + (int) (bw * left), by + 4, col);
        }
        // the keys done and to come
        int n = keys.length;
        int dx = 12;
        int sx = cx - (n - 1) * dx / 2;
        int dy = cy + 34;
        for (int i = 0; i < n; i++) {
            int c = i < step ? 0xFF55FF55 : i == step ? 0xFFFFD27A : 0xFF6A5A4A;
            g.fill(sx + i * dx - 3, dy - 3, sx + i * dx + 3, dy + 3, c);
        }
    }

    private static final net.minecraft.resources.ResourceLocation VIGNETTE =
            new net.minecraft.resources.ResourceLocation("textures/misc/vignette.png");

    /** Slow motion: the world drained cold and dark round the edges (vanilla's vignette, much harder), so the keys are all you see. */
    private static void vignette(GuiGraphics g, int w, int h, float k) {
        g.fill(0, 0, w, h, (int) (0x30 * k) << 24 | 0x101830);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        float v = 0.9F * k;
        g.setColor(v, v, v, 1F);
        g.blit(VIGNETTE, 0, 0, -90, 0F, 0F, w, h, w, h);
        g.blit(VIGNETTE, 0, 0, -90, 0F, 0F, w, h, w, h);
        g.setColor(1F, 1F, 1F, 1F);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }
}
