package com.groid.papon.client;

import com.groid.papon.PaponMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * His vision swimming (phase three's cutscene, through his eyes after the knockback): vanilla's own blur post-shader,
 * its radius turned up and down as the shot asks. Loaded and put away round the cutscene through GameRenderer's
 * private loadEffect; if anything about that fails the shot simply stays sharp.
 */
final class BlurFx {
    private static final ResourceLocation BLUR = new ResourceLocation("shaders/post/blur.json");
    private static boolean on, broken;
    private static Method load;
    private static Field passes;

    private BlurFx() {}

    /** The blur's radius in pixels now (0 = off). */
    static void set(float radius) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gr = mc.gameRenderer;
        try {
            if (radius > 0.25F) {
                if (!on || gr.currentEffect() == null) {
                    if (load == null) {
                        load = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "m_109128_", ResourceLocation.class);
                        passes = ObfuscationReflectionHelper.findField(PostChain.class, "f_110009_");
                    }
                    load.invoke(gr, BLUR);
                    on = true;
                }
                PostChain chain = gr.currentEffect();
                if (chain != null) {
                    @SuppressWarnings("unchecked")
                    List<PostPass> list = (List<PostPass>) passes.get(chain);
                    for (PostPass p : list) p.getEffect().safeGetUniform("Radius").set(radius);
                }
            } else if (on) {
                on = false;
                gr.shutdownEffect();
                gr.checkEntityPostEffect(mc.getCameraEntity());
            }
        } catch (Throwable e) {
            broken = true;
            on = false;
            PaponMod.LOG.warn("[papon] no blur for the cutscene: {}", e.toString());
        }
    }
}
