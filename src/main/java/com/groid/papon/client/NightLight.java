package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;

import java.lang.reflect.Field;

/**
 * Night over El Coliseo Papa (phase three): the boss puts the lights out round the bowl, the spotlight comes
 * on, and the rest of the fight is at night, lit by the braziers (crimson by then) and his spotlight.
 *
 * Up in the sky the coliseum has full skylight, so "night" is done on the client: the lightmap is darkened
 * where there is no block light (a brazier's glow stays, crimson-tinted once the fire is back; skylight goes
 * to {@code 1 - night} of itself), the sky is covered by a black dome and the fog goes black. The lightmap is
 * rebuilt by vanilla each frame we darken it (its update flag is forced), then darkened again before
 * anything is drawn, so nothing compounds and it comes straight back when the night lifts.
 *
 * How dark: the boss's synced {@link PaponBossEntity#night()}, eased, while he is in his coliseum and you are
 * near it.
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NightLight {
    private static final double RANGE = 96;
    private static float night, nightO;
    private static boolean crimson, dead;
    /** The lightmap was darkened last frame: force one more vanilla rebuild after the night lifts. */
    private static boolean dirty;
    private static Field updateFlag, pixels, texture;
    private static boolean reflectionFailed;

    private NightLight() {}

    /** How dark it is now, 0..1 (eased). */
    public static float night(float partial) {
        return Mth.lerp(partial, nightO, night);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        nightO = night;
        float want = 0;
        crimson = false;
        dead = false;
        if (mc.level != null && mc.player != null) {
            PaponBossEntity boss = mc.level.getEntitiesOfClass(PaponBossEntity.class, mc.player.getBoundingBox().inflate(RANGE),
                    b -> b.inColiseum()).stream().findFirst().orElse(null);
            if (boss != null) {
                want = boss.night();
                crimson = boss.isPhaseThree() && boss.moon() < 2;
                dead = boss.moon() >= 2;
            }
        }
        // the lights go down slowly, come up a little quicker
        night += Mth.clamp(want - night, -0.06F, 0.05F);
        if (night < 0.002F) night = 0;
    }

    /** Before the frame: have vanilla rebuild the lightmap (we darken it after). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (night <= 0 && nightO <= 0 && !dirty) return;
        LightTexture lt = Minecraft.getInstance().gameRenderer.lightTexture();
        if (!reflect()) return;
        try {
            updateFlag.setBoolean(lt, true);
        } catch (IllegalAccessException e) {
            reflectionFailed = true;
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float n = night(event.getPartialTick());
        if (n <= 0) {
            dirty = false;
            return;
        }
        darkenLightmap(n);
        dirty = true;
        coverSky(event.getPoseStack(), n);
        MuertosSky.drawMoon(event.getPoseStack(), event.getPartialTick());
    }

    /** The fog (and with it the horizon) goes black with the night. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float n = night((float) event.getPartialTick());
        if (n <= 0) return;
        float k = 1 - n * 0.96F;
        event.setRed(event.getRed() * k);
        event.setGreen(event.getGreen() * k);
        event.setBlue(event.getBlue() * k);
    }

    private static boolean reflect() {
        if (reflectionFailed) return false;
        if (updateFlag != null) return true;
        try {
            updateFlag = ObfuscationReflectionHelper.findField(LightTexture.class, "f_109873_");
            pixels = ObfuscationReflectionHelper.findField(LightTexture.class, "f_109871_");
            texture = ObfuscationReflectionHelper.findField(LightTexture.class, "f_109870_");
            return true;
        } catch (Exception e) {
            PaponMod.LOG.warn("[night] cannot reach the lightmap: no darkness in phase three", e);
            reflectionFailed = true;
            return false;
        }
    }

    /**
     * Each lightmap cell is (block light x, sky light y). Skylight is taken down to {@code 1 - night} of itself;
     * block light keeps its brightness (a brazier's glow still lights the stones round it), warmed to crimson
     * once the fire has come back.
     */
    private static void darkenLightmap(float n) {
        if (!reflect()) return;
        try {
            LightTexture lt = Minecraft.getInstance().gameRenderer.lightTexture();
            NativeImage img = (NativeImage) pixels.get(lt);
            DynamicTexture tex = (DynamicTexture) texture.get(lt);
            if (img == null || tex == null) return;
            for (int sky = 0; sky < 16; sky++) {
                for (int block = 0; block < 16; block++) {
                    int c = img.getPixelRGBA(block, sky);            // ABGR
                    float r = (c & 0xFF) / 255F, g = (c >> 8 & 0xFF) / 255F, b = (c >> 16 & 0xFF) / 255F;
                    float bl = block / 15F;
                    float keep = 0.04F + 0.96F * (float) Math.pow(bl, 1.5);
                    float f = Mth.lerp(n, 1F, keep);
                    float tr = 1, tg = 1, tb = 1;
                    if (crimson && block > 0 && block < 15) {          // 15 is his spotlight: white
                        float w = bl * n;
                        tg = 1 - 0.38F * w;
                        tb = 1 - 0.45F * w;
                    }
                    if (dead && block > 0) {
                        // the dead's: everything lit by spirit fire and ectoplasm, cold blue
                        float w = bl * n;
                        tr = 1 - 0.45F * w;
                        tg = 1 - 0.08F * w;
                    }
                    r = Mth.clamp(r * f * tr, 0, 1);
                    g = Mth.clamp(g * f * tg, 0, 1);
                    b = Mth.clamp(b * f * tb, 0, 1);
                    img.setPixelRGBA(block, sky, 0xFF000000 | ((int) (b * 255) << 16) | ((int) (g * 255) << 8) | (int) (r * 255));
                }
            }
            tex.upload();
        } catch (IllegalAccessException e) {
            reflectionFailed = true;
        }
    }

    /** A black box round the camera over the sky just drawn, as opaque as the night is deep. */
    private static void coverSky(PoseStack pose, float n) {
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float a = Mth.clamp(n * 1.1F, 0, 1);
        float s = 100;
        float[][] faces = {
                {-s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s},
                {-s, -s, s, -s, s, s, s, s, s, s, -s, s},
                {-s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s},
                {s, -s, -s, s, -s, s, s, s, s, s, s, -s},
                {-s, s, -s, s, s, -s, s, s, s, -s, s, s},
                {-s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                buf.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(0.01F, dead ? 0.02F : 0.01F, dead ? 0.06F : 0.02F, a).endVertex();
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
