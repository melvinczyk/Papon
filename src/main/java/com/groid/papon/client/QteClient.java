package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.network.PaponNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The QTE on the held player's screen: one key at a time in a big keycap, a bar running out under
 * it (a second), the keys still to come as dots. Press the right one in time and the next comes up;
 * the wrong one, or too late, and it is over. While it runs a blank {@link QteScreen} is up, so the keys
 * go to it and never to a keybind (no map on J, no inventory, no mod hotkeys), and the camera is on
 * him in slow motion (Cinematic).
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, value = Dist.CLIENT)
public final class QteClient {
    private static boolean active;
    private static boolean locked;
    private static int bossId = -1;
    private static int kind;
    private static int[] keys = new int[0];
    private static int window = 30;
    /** Ticks left before presses count: whatever you were pressing as he grabbed you is ignored. */
    private static int grace;
    private static int step;
    private static int stepTicks;
    /** Ticks since the last press, and whether it was right (the keycap flashes). */
    private static int flashTicks = 99;
    private static boolean flashGood;
    /** After it ends: ticks the result stays up, and which it was. */
    private static int endTicks;
    private static boolean endWon;
    private static int beat;
    /** The squeeze (kind 5): the meter (0..1, draining), presses in a row on the current pair, ticks left. */
    private static float meter;
    private static int spamCount, spamLeft;
    /** The guitar duel (kind 6), while it runs. */
    @org.jetbrains.annotations.Nullable private static GuitarDuel duel;

    /** The squeeze's meter: how much a right press adds, and how much drains a tick (7-8 presses a second to fill it in time). */
    private static final float SPAM_GAIN = 0.096F, SPAM_DRAIN = 0.025F;

    private QteClient() {}

    public static void start(int boss, int k, int[] ks, int w) {
        active = true;
        locked = false;
        bossId = boss;
        kind = k;
        keys = ks;
        window = Math.max(4, w);
        grace = PaponBossEntity.QTE_GRACE;
        step = 0;
        stepTicks = 0;
        flashTicks = 99;
        endTicks = 0;
        meter = 0.2F;
        spamCount = 0;
        spamLeft = window;
        duel = k == 6 && ks.length >= 3 ? new GuitarDuel(boss, ks[0], ks[1], ks[2]) : null;
        if (k == 6) grace = 0;
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BELL.get(), 0.6F));
    }

    /** The guitar duel is on (its own camera, no slow motion). */
    public static boolean duel(PaponBossEntity boss) {
        return active && duel != null && boss.getId() == bossId;
    }

    /** The squeeze's pair of keys right now: the meter moves you through the three pairs. */
    private static int spamPair() {
        return meter < 0.45F ? 0 : meter < 0.75F ? 1 : 2;
    }

    public static void end(boolean won) {
        duel = null;
        active = false;
        locked = true;
        endWon = won;
        endTicks = 40;
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND, won ? 1.4F : 0.6F));
    }

    /** True while the camera should be on this boss for the QTE (the sequence, and a moment after it). */
    public static boolean focus(PaponBossEntity boss) {
        return boss.getId() == bossId && (active || endTicks > 14) && duel == null;
    }

    public static boolean active() {
        return active;
    }

    /** Dev (AutoShot): press the right key now, as a player would. */
    public static void devPress() {
        if (duel != null) {
            duel.devPlay();
            return;
        }
        if (active && !locked && grace <= 0 && kind == 5) {
            press(keys[spamPair() * 2 + spamCount % 2]);
            return;
        }
        if (!active || locked || grace > 0 || step >= keys.length) return;
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
        if (!active) {
            if (mc.screen instanceof QteScreen) mc.setScreen(null);
            return;
        }
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(bossId) instanceof PaponBossEntity boss)
                || boss.getBossState() == com.groid.papon.boss.BossState.DEATH || (locked && stepTicks++ > 60)) {
            active = false;
            return;
        }
        if (mc.screen == null) mc.setScreen(new QteScreen());
        if (duel != null) {
            duel.tick();
            if (duel.over() && !locked) locked = true;
            return;
        }
        // slow motion: a slow heartbeat under it all
        if (++beat % (kind == 5 ? 8 : 16) == 0) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.6F));
        }
        if (locked) return;
        if (grace > 0) {
            grace--;
            return;
        }
        if (kind == 5) {
            // the squeeze: the meter drains; full, you are out; time up, he crushes you
            meter -= SPAM_DRAIN;
            if (meter < 0) meter = 0;
            if (--spamLeft <= 0) {
                PaponNetwork.qteResult(bossId, 5, false, Math.round(meter * 100));
                locked = true;
                flashGood = false;
                flashTicks = 0;
            }
            return;
        }
        if (++stepTicks > window) {
            PaponNetwork.qteInput(bossId, step, false);
            locked = true;
            flashGood = false;
            flashTicks = 0;
        }
    }

    /** A key let go (the duel's frets light while held). */
    static void release(int key) {
        if (duel != null) duel.release(key);
    }

    /** A key pressed while the QTE's screen is up (the only way presses reach the QTE). */
    static void press(int key) {
        if (!active) return;
        if (key == GLFW.GLFW_KEY_F1 || key == GLFW.GLFW_KEY_F2 || key == GLFW.GLFW_KEY_F3
                || (key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER)) return;
        if (grace > 0) return;                       // still the fight's presses: they do not count
        if (duel != null) {
            duel.press(key);
            return;
        }
        if (kind == 5) {
            if (locked) return;
            int pair = spamPair();
            int want = keys[pair * 2 + spamCount % 2];
            boolean ok = key == want;
            flashGood = ok;
            flashTicks = 0;
            if (ok) {
                spamCount++;
                meter = Math.min(1F, meter + SPAM_GAIN);
                if (spamPair() != pair) spamCount = 0;           // a new pair: start it from its first key
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.NOTE_BLOCK_HAT.get(), 1.0F + meter * 0.8F, 0.6F));
                if (meter >= 1F) {
                    PaponNetwork.qteResult(bossId, 5, true, 100);
                    locked = true;
                }
            } else if (key != keys[pair * 2 + (spamCount + 1) % 2]) {
                meter = Math.max(0, meter - 0.08F);              // a key that is not in the pair at all
            }
            return;
        }
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
        if (active && duel != null) {
            Component dt = Component.translatable("qte.papon.title.6").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            g.pose().pushPose();
            g.pose().translate(cx, 18, 0);
            g.pose().scale(1.6F, 1.6F, 1);
            g.drawString(font, dt, -font.width(dt) / 2, 0, 0xFFFFFF, true);
            g.pose().popPose();
            duel.render(g, w, h, pt);
            return;
        }
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
        if (kind >= 3) {
            // what to do: mash the one key / dance on the beat / spam the pair
            Component how = Component.translatable("qte.papon.how." + kind).withStyle(ChatFormatting.YELLOW);
            g.drawString(font, how, cx - font.width(how) / 2, cy - 32, 0xFFFFFF, true);
        }
        if (kind == 5) {
            spamHud(g, font, cx, cy, pt);
            return;
        }
        if (step < keys.length) {
            // the keycap: grows in, shakes as time runs out, flashes on a press
            float left = 1F - Mth.clamp((stepTicks + pt) / window, 0, 1);
            int size = 34;
            float pop = Math.min(1F, (stepTicks + pt) / 3F);
            int sz = (int) (size * (0.7F + 0.3F * pop));
            int jx = left < 0.35F ? (int) ((mc.level.random.nextFloat() - 0.5F) * 4) : 0;
            int x0 = cx - sz / 2 + jx, y0 = cy - sz / 2;
            int edge = grace > 0 ? 0xFF8A7A6A                        // not live yet: greyed until the grace runs out
                    : flashTicks < 4 ? (flashGood ? 0xFF55FF55 : 0xFFFF4444) : (left < 0.35F ? 0xFFFF6A3A : 0xFFFFD27A);
            g.fill(x0 - 2, y0 - 2, x0 + sz + 2, y0 + sz + 2, edge);
            g.fill(x0, y0, x0 + sz, y0 + sz, 0xE0201812);
            g.fill(x0 + 2, y0 + sz - 5, x0 + sz - 2, y0 + sz - 2, 0xFF3A2C20);
            String name = switch (keys[step]) {
                case GLFW.GLFW_KEY_UP -> "\u2191";
                case GLFW.GLFW_KEY_DOWN -> "\u2193";
                case GLFW.GLFW_KEY_LEFT -> "\u2190";
                case GLFW.GLFW_KEY_RIGHT -> "\u2192";
                default -> InputConstants.Type.KEYSYM.getOrCreate(keys[step]).getDisplayName().getString().toUpperCase();
            };
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

    /** The squeeze: the pair of keys (the one to press next jumping), the meter filling and draining, the time running out. */
    private static void spamHud(GuiGraphics g, net.minecraft.client.gui.Font font, int cx, int cy, float pt) {
        Minecraft mc = Minecraft.getInstance();
        int pair = spamPair();
        for (int i = 0; i < 2; i++) {
            int key = keys[Math.min(keys.length - 1, pair * 2 + i)];
            boolean next = spamCount % 2 == i;
            int sz = next ? 34 : 28;
            int x0 = cx + (i == 0 ? -44 : 10) + (next ? 0 : 3), y0 = cy - sz / 2 - (next && grace <= 0 ? (int) (3 * Math.abs(Mth.sin((mc.level.getGameTime() + pt) * 1.4F))) : 0);
            int edge = grace > 0 ? 0xFF8A7A6A : flashTicks < 3 ? (flashGood ? 0xFF55FF55 : 0xFFFF4444) : next ? 0xFFFFD27A : 0xFF8A7A6A;
            g.fill(x0 - 2, y0 - 2, x0 + sz + 2, y0 + sz + 2, edge);
            g.fill(x0, y0, x0 + sz, y0 + sz, 0xE0201812);
            String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase();
            g.pose().pushPose();
            g.pose().translate(x0 + sz / 2F, y0 + sz / 2F, 0);
            g.pose().scale(next ? 2.4F : 1.8F, next ? 2.4F : 1.8F, 1);
            g.drawString(font, name, -font.width(name) / 2, -4, 0xFFFFFF, true);
            g.pose().popPose();
        }
        // the meter: three bands for the three pairs, the fill, the line to reach
        int bw = 140, by = cy + 30;
        g.fill(cx - bw / 2 - 2, by - 2, cx + bw / 2 + 2, by + 12, 0xFF000000);
        int fill = (int) (bw * Mth.clamp(meter, 0, 1));
        int col = meter > 0.75F ? 0xFF55FF55 : meter > 0.45F ? 0xFFFFD27A : 0xFFFF7A3A;
        g.fill(cx - bw / 2, by, cx - bw / 2 + fill, by + 10, col);
        g.fill(cx - bw / 2 + (int) (bw * 0.45F), by, cx - bw / 2 + (int) (bw * 0.45F) + 1, by + 10, 0x80FFFFFF);
        g.fill(cx - bw / 2 + (int) (bw * 0.75F), by, cx - bw / 2 + (int) (bw * 0.75F) + 1, by + 10, 0x80FFFFFF);
        // the time
        float left = Mth.clamp(spamLeft / (float) Math.max(1, window), 0, 1);
        g.fill(cx - bw / 2, by + 16, cx - bw / 2 + (int) (bw * left), by + 19, left > 0.3F ? 0xFFFFD27A : 0xFFFF3A2A);
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
