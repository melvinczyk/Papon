package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chef Papon's boss bars, drawn in place of the vanilla one: a bar of its own for each phase (2026-10-06, the user: "each phase
 * needs its own distinct, creative, tasteful health-bar artwork... part of the game's art direction").
 * <ul>
 *   <li>phase one, El Calvo (textures/gui/boss_bar.png, tools/gen_ui.py): a taco shell filled with salsa, the taco at the
 *   empty end, Chef Papon himself riding the tip of the fill</li>
 *   <li>phase two, El Papa Horneado (bar_p2.png, tools/gen_boss_bars.py): baked potato skin under papel picado, a salt shaker,
 *   melted butter for the fill with a glint riding its tip; his baked face in a crown of fries</li>
 *   <li>phase three, Para Siempre (bar_p3.png): a gold blade, a crimson channel of divine fire, rays rising off it to a sun stone
 *   whose rays turn; a crimson-gold light breathing round it</li>
 *   <li>phase four, the Final Guardian (bar_p4.png): obsidian and bone, marigolds, a candle burning at its end, his sugar skull
 *   in a ring of marigold petals breathing the light of the dead; petals falling off the fill's edge; the stage breaks marked</li>
 * </ul>
 * Every one of them shows what the last hits took (a pale trail that drains after a moment), flashes and shakes its medallion
 * as he is hit, and pulses as he nears his end. The art is two texels to a GUI pixel, drawn at half scale.
 */
public final class BossBarRenderer {
    private static final ResourceLocation SHEET = PaponMod.id("textures/gui/boss_bar.png");
    private static final ResourceLocation[] SHEETS = {null, null, PaponMod.id("textures/gui/bar_p2.png"),
            PaponMod.id("textures/gui/bar_p3.png"), PaponMod.id("textures/gui/bar_p4.png")};
    private static final int FRAME_W = 220;
    private static final int FRAME_H = 24;
    private static final int FILL_W = 204;
    private static final int FILL_H = 10;
    /** Phase four's stage breaks (PaponBossEntity.p4Stage), as fractions of his health. */
    private static final float[] P4_STAGES = {0.17F, 0.085F};

    /** Per bar: the trail's level, how long it holds, the hit flash, the last level seen, the last frame's clock. */
    private static final class Track {
        float lag = 1, hold, flash, last = -1;
        long at = Util.getMillis();
        int phase;
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private record Petal(float x, float y, float vx, float vy, float spin, long born) {}

    private static final List<Petal> PETALS = new ArrayList<>();

    private BossBarRenderer() {}

    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bar = event.getBossEvent();
        if (!(bar.getName().getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith(PaponBossEntity.BAR_KEY)) return;
        event.setCanceled(true);
        String key = tc.getKey();
        int phase = key.endsWith("phase4") ? 4 : key.endsWith("phase3") ? 3 : key.endsWith("phase2") ? 2 : 1;
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int y = event.getY();
        float progress = Mth.clamp(bar.getProgress(), 0, 1);
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        float partial = mc.getFrameTime();
        Track tr = TRACKS.computeIfAbsent(bar.getId(), k -> new Track());
        track(tr, progress, phase);
        if (phase == 1) {
            event.setIncrement(40);
            drawTaco(g, mc, bar, cx, y, progress, time);
            return;
        }
        event.setIncrement(54);
        drawPhase(g, mc, bar, tr, phase, cx, y, progress, time + partial);
    }

    /** The trail and the flash, on the real clock (frame-rate free): it holds 0.7 s after a hit, then drains. */
    private static void track(Track tr, float progress, int phase) {
        long now = Util.getMillis();
        float dt = Math.min(0.25F, (now - tr.at) / 1000F);
        tr.at = now;
        if (tr.last < 0 || tr.phase != phase) {
            tr.lag = progress;
            tr.last = progress;
            tr.phase = phase;
        }
        if (progress < tr.last - 1e-4F) {
            tr.flash = Math.min(1F, tr.flash + 0.6F + (tr.last - progress) * 20);
            tr.hold = 0.7F;
        }
        tr.last = progress;
        if (progress > tr.lag) tr.lag = progress;
        if (tr.hold > 0) tr.hold -= dt;
        else tr.lag = Math.max(progress, tr.lag - dt * 0.35F);
        tr.flash = Math.max(0, tr.flash - dt * 3.5F);
    }

    // ------------------------------------------------------------------ phase one: the taco

    private static void drawTaco(GuiGraphics g, Minecraft mc, LerpingBossEvent bar, int cx, int y, float progress, long time) {
        Component name = bar.getName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(mc.font, name, cx - mc.font.width(name) / 2, y - 1, 0xFFFFFF, true);
        int fx = cx - FRAME_W / 2;
        int fy = y + 9;
        g.blit(SHEET, fx, fy, 0, 0, FRAME_W, FRAME_H, 256, 64);
        int fillX = fx + 8;
        int fillY = fy + 7;
        int w = Math.round(FILL_W * progress);
        if (w > 0) g.blit(SHEET, fillX, fillY, 0, 24, w, FILL_H, 256, 64);
        // the taco waits at the empty end; the potato rides the tip of the fill
        g.blit(SHEET, fx - 10, fy + 4, 16, 44, 16, 16, 256, 64);
        int bob = (int) (Math.sin(time * 0.3) * 0.8);
        g.blit(SHEET, fillX + w - 8, fy + 3 + bob, 0, 44, 16, 16, 256, 64);
    }

    // ------------------------------------------------------------------ phases two to four

    private static void drawPhase(GuiGraphics g, Minecraft mc, LerpingBossEvent bar, Track tr, int phase, int cx, int y, float progress, float t) {
        ResourceLocation tex = SHEETS[phase];
        float fx = cx - 128, fy = y + 10;
        float chX = fx + 40, chY = fy + 19, chW = 204, chH = 10;
        float w = chW * progress, lagW = chW * tr.lag;
        float low = progress < 0.12F ? 0.5F + 0.5F * Mth.sin(t * 0.35F) : 0;
        RenderSystem.enableBlend();
        // the light round the god and the guardian, breathing (behind everything)
        if (phase >= 3) {
            float br = 0.55F + 0.45F * Mth.sin(t * 0.08F);
            int c = phase == 3 ? 0xFF3A28 : 0x7A3CFF;
            int a = (int) (60 * br + 70 * tr.flash);
            gradientBand(g, chX - 6, chY - 7, chX + chW + 6, chY + chH + 7, c, a);
        }
        g.pose().pushPose();
        g.pose().translate(fx, fy, 0);
        g.pose().scale(0.5F, 0.5F, 1);
        g.blit(tex, 0, 0, 0, 0, 512, 80, 512, 256);
        g.pose().popPose();
        // the trail of the last hits, then the fill over it
        if (lagW > w + 0.25F) fillRect(g, chX + w, chY, chX + lagW, chY + chH, trail(phase), (int) (200 * Math.min(1, 0.55F + tr.hold)));
        if (w > 0.25F) {
            int scroll = (int) ((t * (phase == 3 ? 1.6F : phase == 4 ? 0.9F : 0.6F)) % 64);
            g.pose().pushPose();
            g.pose().translate(chX, chY, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.blit(tex, 0, 0, scroll, 80, Math.round(w * 2), 20, 512, 256);
            g.pose().popPose();
            // a sheen sweeping along it every few seconds, the flash of a hit over it
            float sweep = (t % 70) / 70F * (chW + 40) - 20;
            float s0 = Math.max(0, sweep), s1 = Math.min(w, sweep + 14);
            if (s1 > s0) fillRect(g, chX + s0, chY + 1, chX + s1, chY + chH - 1, 0xFFFFFF, 42);
            fillRect(g, chX, chY, chX + w, chY + 1, 0xFFFFFF, 50);
            if (tr.flash > 0) fillRect(g, chX, chY, chX + w, chY + chH, 0xFFFFFF, (int) (150 * tr.flash));
            if (low > 0) fillRect(g, chX, chY, chX + w, chY + chH, phase == 4 ? 0xFF2050 : 0xFF2020, (int) (90 * low));
        }
        // phase four: the stage breaks, bone ticks with a dot over them
        if (phase == 4) {
            for (float k : P4_STAGES) {
                float x = chX + chW * k;
                fillRect(g, x - 0.5F, chY - 1, x + 0.5F, chY + chH + 1, 0xF2E6C8, progress > k ? 230 : 110);
                fillRect(g, x - 1, chY - 2.5F, x + 1, chY - 0.5F, 0xF2E6C8, progress > k ? 230 : 110);
            }
        }
        tip(g, tex, phase, chX + w, chY, chH, t, progress);
        // the medallion, shaken by a hit, and what is drawn over it
        float shake = tr.flash * 1.6F;
        float mx = fx + 20 + Mth.sin(t * 7.1F) * shake, my = fy + 24 + Mth.cos(t * 8.3F) * shake;
        if (phase == 3) {
            // the sun's rays behind the disc, turning
            g.pose().pushPose();
            g.pose().translate(mx, my, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(t * 0.6F));
            g.pose().scale(0.5F, 0.5F, 1);
            g.blit(tex, -48, -48, 96, 104, 96, 96, 512, 256);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(mx - 24, my - 24, 0);
        g.pose().scale(0.5F, 0.5F, 1);
        g.blit(tex, 0, 0, 0, 104, 96, 96, 512, 256);
        if (phase == 4) {
            // the light of the dead round the skull, breathing
            RenderSystem.setShaderColor(1, 1, 1, 0.35F + 0.35F * Mth.sin(t * 0.09F) + 0.3F * tr.flash);
            g.blit(tex, 0, 0, 96, 104, 96, 96, 512, 256);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        g.pose().popPose();
        if (phase == 4) candle(g, tex, fx, fy, t);
        drawName(g, mc.font, bar.getName().getString(), phase, cx, y - 1, t);
        RenderSystem.disableBlend();
    }

    /** What rides the tip of the fill: two, a twinkling glint; three, a white-hot spark; four, marigold petals falling off it. */
    private static void tip(GuiGraphics g, ResourceLocation tex, int phase, float x, float y, float h, float t, float progress) {
        if (progress <= 0) return;
        if (phase == 2) {
            float s = 0.35F + 0.15F * Mth.sin(t * 0.4F);
            g.pose().pushPose();
            g.pose().translate(x, y + h / 2, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(t * 3));
            g.pose().scale(s, s, 1);
            g.blit(tex, -16, -16, 192, 104, 32, 32, 512, 256);
            g.pose().popPose();
        } else if (phase == 3) {
            fillRect(g, x - 1, y - 1, x + 0.5F, y + h + 1, 0xFFF4D0, (int) (200 + 55 * Mth.sin(t * 0.7F)));
        } else {
            long now = Util.getMillis();
            if (PETALS.size() < 14 && Math.random() < 0.06) {
                PETALS.add(new Petal(x, y + (float) Math.random() * h, (float) (Math.random() - 0.7) * 6, 8 + (float) Math.random() * 6,
                        (float) (Math.random() - 0.5) * 300, now));
            }
            PETALS.removeIf(p -> now - p.born > 1600);
            for (Petal p : PETALS) {
                float s = (now - p.born) / 1000F;
                float a = 1 - s / 1.6F;
                RenderSystem.setShaderColor(1, 1, 1, a);
                g.pose().pushPose();
                g.pose().translate(p.x + p.vx * s + Mth.sin(s * 5 + p.spin) * 2, p.y + p.vy * s * (1 + s), 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(p.spin * s));
                g.pose().scale(0.25F, 0.25F, 1);
                g.blit(tex, -16, -10, 256, 108, 32, 20, 512, 256);
                g.pose().popPose();
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    /** Phase four's candle, its flame flickering between its two frames and leaning. */
    private static void candle(GuiGraphics g, ResourceLocation tex, float fx, float fy, float t) {
        int frame = Mth.sin(t * 0.9F) + Mth.sin(t * 2.3F) * 0.5F > 0 ? 0 : 1;
        float s = 1 + 0.08F * Mth.sin(t * 1.7F);
        g.pose().pushPose();
        g.pose().translate(fx + 251.6F, fy + 5.2F, 0);
        g.pose().scale(s, s, 1);
        g.pose().scale(0.5F, 0.5F, 1);
        g.blit(tex, -16, -28, 192 + frame * 32, 104 - 0, 32, 32, 512, 256);
        g.pose().popPose();
    }

    /** The trail's colour: what is left of the hit, pale. */
    private static int trail(int phase) {
        return switch (phase) {
            case 2 -> 0xFFF0C0;
            case 3 -> 0xFFE9A8;
            default -> 0xB8FFE4;
        };
    }

    /**
     * The name over the bar, set in two parts (the lang key's "Name, epithet"): two, gold and bold, the epithet orange; three,
     * spaced capitals in pale gold, the epithet crimson; four, spaced capitals in bone, the epithet marigold, a breath of the
     * dead's light behind it.
     */
    private static void drawName(GuiGraphics g, Font font, String full, int phase, int cx, int y, float t) {
        int comma = full.indexOf(", ");
        String name = comma < 0 ? full : full.substring(0, comma), epithet = comma < 0 ? "" : full.substring(comma + 2);
        boolean caps = phase >= 3;
        if (caps) name = name.toUpperCase(java.util.Locale.ROOT);
        int spacing = phase == 2 ? 0 : 1;
        int nameCol = phase == 2 ? 0xFFD45A : phase == 3 ? 0xFFE9A8 : 0xF2E6C8;
        int epiCol = phase == 2 ? 0xFF9A3C : phase == 3 ? 0xFF4A5A : 0xFFA030;
        Style bold = Style.EMPTY.withBold(true);
        int nameW = spacedWidth(font, name, bold, spacing);
        float epiScale = 0.8F;
        int sep = epithet.isEmpty() ? 0 : 6;
        int epiW = epithet.isEmpty() ? 0 : Math.round(font.width(Component.literal(epithet).withStyle(Style.EMPTY.withItalic(true))) * epiScale);
        int x = cx - (nameW + sep + epiW) / 2;
        drawSpaced(g, font, name, bold, spacing, x, y, nameCol);
        if (!epithet.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + nameW + sep, y + 1.6F, 0);
            g.pose().scale(epiScale, epiScale, 1);
            g.drawString(font, Component.literal(epithet).withStyle(Style.EMPTY.withItalic(true)), 0, 0, epiCol, true);
            g.pose().popPose();
            fillRect(g, x + nameW + 2, y + 3.5F, x + nameW + 3.5F, y + 5, epiCol, 200);
        }
    }

    private static int spacedWidth(Font font, String s, Style style, int spacing) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += font.width(Component.literal(String.valueOf(s.charAt(i))).withStyle(style)) + (i > 0 ? spacing : 0);
        return w;
    }

    private static void drawSpaced(GuiGraphics g, Font font, String s, Style style, int spacing, int x, int y, int col) {
        for (int i = 0; i < s.length(); i++) {
            Component c = Component.literal(String.valueOf(s.charAt(i))).withStyle(style);
            g.drawString(font, c, x, y, col, true);
            x += font.width(c) + spacing;
        }
    }

    /** A filled rectangle at fractional GUI coordinates, colour {@code rgb} at {@code alpha}. */
    private static void fillRect(GuiGraphics g, float x0, float y0, float x1, float y1, int rgb, int alpha) {
        if (alpha <= 0 || x1 <= x0) return;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().scale((x1 - x0) / 64F, (y1 - y0) / 64F, 1);
        g.fill(0, 0, 64, 64, (Math.min(255, alpha) << 24) | (rgb & 0xFFFFFF));
        g.pose().popPose();
    }

    /** A soft band of light round the bar: fading out above and below its middle. */
    private static void gradientBand(GuiGraphics g, float x0, float y0, float x1, float y1, int rgb, int alpha) {
        float mid = (y0 + y1) / 2;
        int c = (Math.min(255, alpha) << 24) | (rgb & 0xFFFFFF), clear = rgb & 0xFFFFFF;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().scale((x1 - x0) / 64F, (mid - y0) / 64F, 1);
        g.fillGradient(0, 0, 64, 64, clear, c);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(x0, mid, 0);
        g.pose().scale((x1 - x0) / 64F, (y1 - mid) / 64F, 1);
        g.fillGradient(0, 0, 64, 64, c, clear);
        g.pose().popPose();
    }
}
