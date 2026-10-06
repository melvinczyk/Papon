package com.groid.papon.client;

import com.groid.papon.boss.SpiritGiantEntity;
import com.groid.papon.network.PaponNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * In El Gigante's fist (La Mano, SpiritGiantEntity): your view drawn round to its skull as it brings you to its face, the
 * struggle (each press of jump goes to the server: PaponNetwork.Struggle) with its prompt and meter in the middle of the screen
 * while its eyes charge, and the screen going cold blue and white at the edges as they come up to full.
 */
public final class GiantGrabHud {
    private GiantGrabHud() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof SpiritGiantEntity g) || !g.holds(mc.player)) return;
        float t = g.actTime(0);
        boolean window = g.act() == SpiritGiantEntity.GRAB && t >= SpiritGiantEntity.GRAB_CLOSE && t < SpiritGiantEntity.GRAB_BLAST - 1;
        while (mc.options.keyJump.consumeClick()) {
            if (window) PaponNetwork.CHANNEL.sendToServer(new PaponNetwork.Struggle());
        }
        // brought round to its face: you are made to look it in the eyes
        if (t >= SpiritGiantEntity.GRAB_LIFT && t < SpiritGiantEntity.GRAB_BLAST + 4) {
            Vec3 eye = g.head(0);
            Vec3 d = eye.subtract(mc.player.getEyePosition());
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) -(Mth.atan2(d.y, Math.hypot(d.x, d.z)) * Mth.RAD_TO_DEG);
            float k = t < SpiritGiantEntity.GRAB_BRING ? 0.12F : 0.3F;
            mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * k);
            mc.player.setXRot(mc.player.getXRot() + (pitch - mc.player.getXRot()) * k);
        }
    }

    public static void render(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof SpiritGiantEntity giant) || !giant.holds(mc.player)) return;
        float t = giant.actTime(partial);
        if (giant.act() != SpiritGiantEntity.GRAB || t < SpiritGiantEntity.GRAB_CLOSE || t > SpiritGiantEntity.GRAB_BLAST + 6) return;
        float charge = Math.max(0, giant.charge(partial));
        // the cold of its eyes at the edges of your sight, closing in as they charge
        if (charge > 0) {
            int bands = 10;
            for (int i = 0; i < bands; i++) {
                float k0 = 1F - i / (float) bands;
                int a = (int) (Math.min(1F, charge) * 170 * k0 * k0);
                int col = (a << 24) | (charge >= 1.5F ? 0xFFFFFF : 0x9FF4FF);
                int ix = (int) (w * 0.2F * i / bands), iy = (int) (h * 0.2F * i / bands);
                int sw = (int) (w * 0.2F / bands) + 1, sh = (int) (h * 0.2F / bands) + 1;
                g.fill(ix, iy, ix + sw, h - iy, col);
                g.fill(w - ix - sw, iy, w - ix, h - iy, col);
                g.fill(ix, iy, w - ix, iy + sh, col);
                g.fill(ix, h - iy - sh, w - ix, h - iy, col);
            }
            // the blast: a white flash that clears in a third of a second
            if (charge >= 1.5F) {
                int a = (int) (215 * Mth.clamp(1 - (t - SpiritGiantEntity.GRAB_BLAST) / 7F, 0, 1));
                if (a > 0) g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
            }
        }
        if (t >= SpiritGiantEntity.GRAB_BLAST - 1) return;
        // the prompt and the meter, shaking with your struggle
        float got = giant.struggled();
        float jig = (float) Math.sin((mc.player.tickCount + partial) * 2.7) * (1.5F + 2F * got);
        Component key = mc.options.keyJump.getTranslatedKeyMessage();
        Component text = Component.translatable("hud.papon.struggle", key);
        int tw = mc.font.width(text);
        int cx = w / 2, cy = h / 2 + 34;
        g.pose().pushPose();
        g.pose().translate(cx + jig, cy, 0);
        g.pose().scale(1.5F, 1.5F, 1);
        boolean blink = ((mc.player.tickCount / 4) & 1) == 0;
        g.drawString(mc.font, text, -tw / 2, 0, blink ? 0xFFFFFF : 0x9FF4FF, true);
        g.pose().popPose();
        int bw = 140, bx = cx - bw / 2, by = cy + 18;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xC0000000);
        g.fill(bx, by, bx + (int) (bw * got), by + 6, 0xFF9FF4FF);
        // how long is left before its eyes fire
        float left = 1 - Mth.clamp((t - SpiritGiantEntity.GRAB_CLOSE) / (SpiritGiantEntity.GRAB_BLAST - SpiritGiantEntity.GRAB_CLOSE), 0, 1);
        g.fill(bx, by + 7, bx + (int) (bw * left), by + 8, 0xFFFF3050);
    }
}
