package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.CrowdPotatoEntity;
import com.groid.papon.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A potato from the stands in your face (the Splattered effect, CrowdPotatoEntity): mashed potato
 * over a tenth of the screen, three splats from potato_splat.png (tools/gen_extras.py) dealt out
 * at random, a bit off the middle so you can still fight. They slide down slowly as the five
 * seconds run out and fade over the last one. A fresh hit deals a fresh splatter.
 */
public final class PotatoSplat {
    private static final ResourceLocation TEX = PaponMod.id("textures/gui/potato_splat.png");
    /** Share of the screen each splat's square covers; a splat fills about 55% of its square. */
    private static final float COVER = 0.10F / 3 / 0.55F;

    private static final float[][] splats = new float[3][4];     // x, y (0..1 of the screen), angle, variant
    private static int lastDuration = -1;

    private PotatoSplat() {}

    public static void render(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MobEffectInstance e = mc.player.getEffect(ModEffects.SPLATTERED.get());
        if (e == null) {
            lastDuration = -1;
            return;
        }
        int dur = e.getDuration();
        if (lastDuration < 0 || dur > lastDuration + 2) deal(mc.player.getRandom());
        lastDuration = dur;
        float left = Math.max(0, dur - partial);
        float age = CrowdPotatoEntity.SPLAT_TICKS - left;
        float alpha = Mth.clamp(left / 20F, 0, 1) * Mth.clamp(age / 2F, 0, 1);
        if (alpha <= 0) return;
        int side = (int) Math.sqrt(COVER * w * h);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, alpha);
        for (float[] s : splats) {
            // they slip down a little as it goes on, slowly then a bit faster
            float slide = (age / CrowdPotatoEntity.SPLAT_TICKS);
            slide = slide * slide * 0.06F * h;
            float pop = age < 3 ? 0.7F + 0.1F * age : 1F;      // a splat lands with a squash
            g.pose().pushPose();
            g.pose().translate(s[0] * w, s[1] * h + slide, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(s[2]));
            g.pose().scale(pop, pop * (age < 3 ? 1.15F : 1F), 1);
            g.blit(TEX, -side / 2, -side / 2, side, side, (int) s[3] * 128, 0, 128, 128, 384, 128);
            g.pose().popPose();
        }
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** Three splats: one somewhere near the middle third, two out toward the edges. */
    private static void deal(RandomSource r) {
        for (int i = 0; i < splats.length; i++) {
            float[] s = splats[i];
            if (i == 0) {
                s[0] = 0.32F + r.nextFloat() * 0.36F;
                s[1] = 0.28F + r.nextFloat() * 0.3F;
            } else {
                s[0] = r.nextBoolean() ? 0.08F + r.nextFloat() * 0.22F : 0.7F + r.nextFloat() * 0.22F;
                s[1] = 0.12F + r.nextFloat() * 0.7F;
            }
            s[2] = r.nextFloat() * 360F;
            s[3] = r.nextInt(3);
        }
    }
}
