package com.groid.papon.client;

import com.groid.papon.boss.SpiritGiantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * El Gigante's attack tells (2026-10-06, the user: "I need to be able to tell when gigante is either about to do his grab attack or
 * laser attack etc. it needs to be far more obvious. like big tells the player can see"): each attack, from the moment it begins to
 * the moment it lands, is named across your screen in its own colour with a bar closing on the impact and the edges of your sight
 * tinted as it nears; the world carries the rest (SpiritGiantRenderer.tells: pillars of light over where hands will land, the
 * eyes' orbs and the lines to where the beams will go) and every attack opens with its own cue (tools/gen_giant_sounds.py).
 */
public final class GiantTells {
    private GiantTells() {}

    /** What it is doing: a lang key under {@code hud.papon.giant.}, a colour, how far it is from landing (0..1; 1 and over is landing or live). */
    public record Tell(String key, int rgb, float progress) {}

    /** How long its eyes charge before they fire (they vary a little: 20..26 ticks; the bar is the longest). */
    public static final float LASER_CHARGE = 24F;

    @Nullable
    public static Tell of(SpiritGiantEntity g, float partial) {
        float at = g.actTime(partial);
        return switch (g.act()) {
            case SpiritGiantEntity.HEED -> new Tell("heed", 0xFFD24A, at / SpiritGiantEntity.HEED_T);
            case SpiritGiantEntity.CHARGE -> new Tell("laser", 0x40E2FF, at / LASER_CHARGE);
            case SpiritGiantEntity.LASER -> new Tell("laser_live", 0xFF4A4A, 1.5F);
            case SpiritGiantEntity.SWIPE_L, SpiritGiantEntity.SWIPE_R ->
                    at < SpiritGiantEntity.SWIPE_TO + 2 ? new Tell("swipe", 0xFF9A2A, at / SpiritGiantEntity.SWIPE_FROM) : null;
            case SpiritGiantEntity.SLAM_L, SpiritGiantEntity.SLAM_R ->
                    at < SpiritGiantEntity.SLAM_HIT + 4 ? new Tell("slam", 0xC050FF, at / SpiritGiantEntity.SLAM_HIT) : null;
            case SpiritGiantEntity.SLAM_BOTH ->
                    at < SpiritGiantEntity.SLAM2_HIT + 4 ? new Tell("slam2", 0xC050FF, at / SpiritGiantEntity.SLAM2_HIT) : null;
            case SpiritGiantEntity.GRAB ->
                    !g.caught() && at < SpiritGiantEntity.GRAB_CLOSE + 4 ? new Tell("grab", 0xFF2046, at / SpiritGiantEntity.GRAB_CLOSE) : null;
            case SpiritGiantEntity.LUNGE ->
                    at < SpiritGiantEntity.LUNGE_STOP + 2 ? new Tell("lunge", 0xFF5A1E, at / SpiritGiantEntity.LUNGE_GO) : null;
            default -> null;
        };
    }

    /** Two colours mixed, channel by channel. */
    private static int mixRgb(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), gr = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | gr << 8 | bl;
    }

    public static void render(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || Cinematic.isActive()) return;
        if (mc.player.getVehicle() instanceof SpiritGiantEntity) return;                  // in its fist: GiantGrabHud
        Tell tell = null;
        for (SpiritGiantEntity e : mc.level.getEntitiesOfClass(SpiritGiantEntity.class, mc.player.getBoundingBox().inflate(220))) {
            Tell t = of(e, partial);
            if (t != null && (tell == null || t.progress < tell.progress)) tell = t;
        }
        if (tell == null) return;
        float time = mc.player.tickCount + partial;
        boolean live = tell.progress >= 1F;
        float k = Mth.clamp(tell.progress, 0F, 1F);
        // the pulse quickens as it comes
        float pulse = 0.5F + 0.5F * Mth.sin(time * (live ? 1.9F : 0.5F + 1.1F * k));
        int rgb = tell.rgb;
        // the edges of your sight in its colour, closing in over the last half of the wind-up, strongest as it lands
        float edge = live ? 0.85F : Mth.clamp((k - 0.35F) / 0.65F, 0F, 1F) * 0.7F;
        if (!tell.key.equals("heed") && edge > 0) {
            int bands = 9;
            for (int i = 0; i < bands; i++) {
                float k0 = 1F - i / (float) bands;
                int a = (int) (edge * (110 + 70 * pulse) * k0 * k0);
                int col = (a << 24) | rgb;
                int ix = (int) (w * 0.16F * i / bands), iy = (int) (h * 0.16F * i / bands);
                int sw = (int) (w * 0.16F / bands) + 1, sh = (int) (h * 0.16F / bands) + 1;
                g.fill(ix, iy, ix + sw, h - iy, col);
                g.fill(w - ix - sw, iy, w - ix, h - iy, col);
                g.fill(ix, iy, w - ix, iy + sh, col);
                g.fill(ix, h - iy - sh, w - ix, h - iy, col);
            }
        }
        // the banner: its name, large, in its colour, flashing white; under it what to do; the bar closing on the hit
        Component name = Component.translatable("hud.papon.giant." + tell.key);
        Component hint = Component.translatable("hud.papon.giant." + tell.key + ".hint");
        float scale = tell.key.equals("heed") ? 1.6F : 2.6F + (live ? 0.3F * pulse : 0.25F * k);
        int cx = w / 2, y = Math.max(86, (int) (h * 0.2F));
        int shown = mixRgb(rgb, 0xFFFFFF, live ? 1F : pulse * 0.6F);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        float shake = live ? 0F : k * k * 1.6F;
        g.pose().translate(Mth.sin(time * 5.1F) * shake, Mth.cos(time * 4.3F) * shake, 0);
        g.pose().scale(scale, scale, 1);
        int tw = mc.font.width(name);
        g.drawString(mc.font, name, -tw / 2, 0, 0xFF000000 | shown, true);
        g.pose().popPose();
        // chevrons either side, pointing in at it, flashing
        if (pulse > 0.25F) {
            String ch = live ? "!!" : ">>";
            String chl = live ? "!!" : "<<";
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(scale, scale, 1);
            int off = tw / 2 + 8;
            g.drawString(mc.font, ch, -off - mc.font.width(ch), 0, 0xFF000000 | rgb, true);
            g.drawString(mc.font, chl, off, 0, 0xFF000000 | rgb, true);
            g.pose().popPose();
        }
        int hy = y + (int) (9 * scale) + 3;
        g.pose().pushPose();
        g.pose().translate(cx, hy, 0);
        g.pose().scale(1.2F, 1.2F, 1);
        g.drawString(mc.font, hint, -mc.font.width(hint) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        if (!tell.key.equals("laser_live")) {
            int bw = 190, bx = cx - bw / 2, by = hy + 15;
            g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xC0000000);
            g.fill(bx, by, bx + (int) (bw * k), by + 6, 0xFF000000 | (live ? 0xFFFFFF : rgb));
            if (!live) g.fill(bx + (int) (bw * k) - 1, by - 2, bx + (int) (bw * k) + 1, by + 8, 0xFFFFFFFF);
        }
    }
}
