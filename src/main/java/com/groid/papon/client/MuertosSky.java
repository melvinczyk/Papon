package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.ColiseumCrowdEntity;
import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.VoiceLines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Phase four's sky and light (Dia de los Muertos):
 *
 *   the moon     over his coliseum's band stand (PaponBossEntity.moonPos): the potato moon when the clouds part
 *                (moon 1), shaking and going blue under his hammer's lightning, then the dead's: a blue calavera with
 *                glowing eyes (moon 2), a cold halo round it. Drawn over the night's black sky (NightLight.coverSky)
 *   the bolt     P4_STRIKE: blue lightning from his hammer up into the moon, re-struck every other tick
 *   the rays     P4_MOONRAYS: a beam of moonlight down onto each band member in turn (they turn to skeletons as it
 *                reaches them), then a fan of them sweeping round the stands
 *   the petals   P4_PETALOS: the marigold spirals (the same sums as the server: PaponBossEntity.petal)
 *   the blue-out P4_BLUEOUT: the screen floods with the moon's blue and clears on the new coliseum
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MuertosSky {
    private static final ResourceLocation POTATO_MOON = PaponMod.id("textures/environment/potato_moon_phases.png");
    private static final ResourceLocation DEAD_MOON = PaponMod.id("textures/environment/muertos_moon.png");
    private static final double RANGE = 180;

    private MuertosSky() {}

    @Nullable
    static PaponBossEntity boss() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        List<PaponBossEntity> l = mc.level.getEntitiesOfClass(PaponBossEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true);
        return l.isEmpty() ? null : l.get(0);
    }

    // ------------------------------------------------------------------ the moon (called after the night's sky cover)

    /** How far the moon has turned (0 the potato moon .. 1 the calavera), and how hard it is shaking. */
    private static float turn(PaponBossEntity b, float partial) {
        if (b.moon() >= 2) {
            if (b.getBossState() == BossState.P4_STRIKE) {
                float t = b.ticksInState() + partial;
                float at = VoiceLines.beat("p4.turn", 52);
                return Mth.clamp((t - at) / 6F + 1F, 0, 1);
            }
            return 1;
        }
        if (b.getBossState() == BossState.P4_STRIKE) {
            float t = b.ticksInState() + partial;
            float swing = VoiceLines.beat("p4.swing", 30), at = VoiceLines.beat("p4.turn", 52);
            return Mth.clamp((t - swing) / (at - swing), 0, 1) * 0.6F;
        }
        return 0;
    }

    /** Whether the moon shows (and how much: it fades in as the clouds part). */
    private static float shown(PaponBossEntity b, float partial) {
        if (b.moon() <= 0) return 0;
        if (b.getBossState() == BossState.P4_RISE) {
            float t = b.ticksInState() + partial, at = VoiceLines.beat("p4.moon", 34);
            return Mth.clamp((t - at) / 24F, 0, 1);
        }
        return 1;
    }

    static void drawMoon(PoseStack pose, float partial) {
        PaponBossEntity b = boss();
        if (b == null) return;
        float k = shown(b, partial);
        if (k <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 d = b.moonPos().subtract(cam).normalize();
        float turned = turn(b, partial);
        float t = b.tickCount + partial;
        // shaking as the lightning goes into it
        if (b.getBossState() == BossState.P4_STRIKE && turned > 0 && turned < 1) {
            d = d.add((Math.sin(t * 3.1) * 0.004), Math.cos(t * 2.7) * 0.004, 0).normalize();
        }
        Vec3 right = d.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(d).normalize();
        Vec3 c = d.scale(90);
        float r = 15F;
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // the halo: cold blue round the calavera, a pale glow round the potato moon
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float hr = turned > 0.5F ? 0.25F : 0.9F, hg = turned > 0.5F ? 0.85F : 0.92F, hb = 1F;
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.08F);
        for (int ring = 0; ring < 2; ring++) {
            float rr = r * (ring == 0 ? 1.9F : 3.2F);
            float a = k * (ring == 0 ? 0.55F : 0.22F) * pulse * (0.6F + 0.4F * turned);
            for (int i = 0; i < 32; i++) {
                double a0 = Math.PI * 2 * i / 32, a1 = Math.PI * 2 * (i + 1) / 32;
                vtx(buf, m, c, right, up, 0, 0, hr, hg, hb, a);
                vtx(buf, m, c, right, up, (float) Math.cos(a0) * rr, (float) Math.sin(a0) * rr, hr, hg, hb, 0);
                vtx(buf, m, c, right, up, (float) Math.cos(a1) * rr, (float) Math.sin(a1) * rr, hr, hg, hb, 0);
            }
        }
        Tesselator.getInstance().end();
        // the disc: the potato moon, crossfading into the calavera (a white flash between)
        // the potato moon is drawn as the sky draws it, added on (its black is see-through); the calavera has alpha
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        drawDisc(m, c, right, up, r, POTATO_MOON, 0, 0, 0.25F, 0.5F, 1F, 1F - 0.5F * turned, 1F - 0.2F * turned, k * (1 - Math.max(0, turned - 0.6F) / 0.4F));
        RenderSystem.defaultBlendFunc();
        if (turned > 0.6F) drawDisc(m, c, right, up, r * 1.05F, DEAD_MOON, 0, 0, 1, 1, 1, 1, 1, k * (turned - 0.6F) / 0.4F);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawDisc(Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float r, ResourceLocation tex, float u0, float v0, float u1, float v1,
                                 float cr, float cg, float cb, float a) {
        if (a <= 0) return;
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[][] q = {{-r, -r, u0, v1}, {r, -r, u1, v1}, {r, r, u1, v0}, {-r, r, u0, v0}};
        for (float[] p : q) {
            Vec3 at = c.add(right.scale(p[0])).add(up.scale(p[1]));
            buf.vertex(m, (float) at.x, (float) at.y, (float) at.z).uv(p[2], p[3]).color(cr, cg, cb, a).endVertex();
        }
        Tesselator.getInstance().end();
    }

    private static void vtx(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float x, float y, float r, float g, float b, float a) {
        Vec3 p = c.add(right.scale(x)).add(up.scale(y));
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    // ------------------------------------------------------------------ in the world: the bolt, the rays, the petals

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        PaponBossEntity b = boss();
        if (b == null) return;
        BossState s = b.getBossState();
        if (s != BossState.P4_STRIKE && s != BossState.P4_MOONRAYS && s != BossState.P4_PETALOS) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float t = b.ticksInState() + partial;
        if (s == BossState.P4_STRIKE) bolt(vc, m, b, t, partial, cam);
        if (s == BossState.P4_MOONRAYS) rays(vc, m, b, t, partial, cam);
        if (s == BossState.P4_PETALOS) petals(vc, m, b, t, partial, cam);
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    /** His hammer's lightning up into the moon: jagged, re-struck every other tick, white at the core in blue. */
    private static void bolt(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, float partial, Vec3 cam) {
        float swing = VoiceLines.beat("p4.swing", 30);
        if (t < swing || t > swing + 24) return;
        float fade = t > swing + 16 ? 1 - (t - swing - 16) / 8F : 1;
        Vec3 from = b.getPosition(partial).add(0, b.getBbHeight() * 1.35, 0);
        Vec3 to = b.moonPos();
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create((long) (b.tickCount / 2) * 977L + b.getId());
        Vec3 dir = to.subtract(from);
        int n = 18;
        Vec3 prev = from;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) n;
            double jit = (i == n ? 0 : 4 + 18 * Math.sin(k * Math.PI));
            Vec3 p = from.add(dir.scale(k)).add((r.nextDouble() - 0.5) * jit, (r.nextDouble() - 0.5) * jit * 0.5, (r.nextDouble() - 0.5) * jit);
            ribbon(vc, m, prev, p, cam, 1.4F + 2F * (float) k, 60, 200, 255, (int) (120 * fade));
            ribbon(vc, m, prev, p, cam, 0.35F + 0.6F * (float) k, 235, 255, 255, (int) (255 * fade));
            if (r.nextInt(4) == 0) {
                // a fork off it
                Vec3 f = p.add((r.nextDouble() - 0.5) * 14, (r.nextDouble() - 0.5) * 8, (r.nextDouble() - 0.5) * 14);
                ribbon(vc, m, p, f, cam, 0.3F, 140, 230, 255, (int) (160 * fade));
            }
            prev = p;
        }
    }

    /** The moon's rays: onto each band member in turn, then a fan sweeping round the stands. */
    private static void rays(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, float partial, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 moon = b.moonPos();
        AABB box = b.getBoundingBox().inflate(120);
        List<MariachiEntity> band = new java.util.ArrayList<>(mc.level.getEntitiesOfClass(MariachiEntity.class, box, e -> true));
        band.sort(java.util.Comparator.comparingInt(e -> e.getRole().ordinal()));
        for (int i = 0; i < band.size(); i++) {
            float at = PaponBossEntity.rayAt(i);
            float k = t < at - 6 ? 0 : t < at ? (t - at + 6) / 6F : Math.max(0, 1 - (t - at) / 10F);
            if (k <= 0) continue;
            Vec3 to = band.get(i).getPosition(partial).add(0, 1, 0);
            beam(vc, m, moon, to, cam, 2.6F * k, k);
        }
        ColiseumCrowdEntity crowd = mc.level.getEntitiesOfClass(ColiseumCrowdEntity.class, box, e -> true).stream().findFirst().orElse(null);
        if (crowd == null || t < PaponBossEntity.RAYS_STANDS - 4 || t > PaponBossEntity.RAYS_STANDS_END + 8) return;
        float sweep = Mth.clamp((t - PaponBossEntity.RAYS_STANDS) / (PaponBossEntity.RAYS_STANDS_END - PaponBossEntity.RAYS_STANDS), 0, 1);
        float fade = t > PaponBossEntity.RAYS_STANDS_END ? 1 - (t - PaponBossEntity.RAYS_STANDS_END) / 8F : Math.min(1, (t - PaponBossEntity.RAYS_STANDS + 4) / 4F);
        Vec3 c = crowd.getPosition(partial);
        for (int j = 0; j < 7; j++) {
            double phi = ColiseumCrowdRenderer.sweepAngle(sweep) - j * 0.12;
            double a = crowd.sizeA() + 10, bb = crowd.sizeB() + 10;
            Vec3 to = c.add(Math.cos(phi) * a, 12 + j % 3, Math.sin(phi) * bb);
            beam(vc, m, moon, to, cam, 3.0F - j * 0.25F, fade * (1 - j * 0.1F));
        }
    }

    /** A beam of moonlight: a wide cold sheath round a white core, brightest at the end it lands on. */
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 from, Vec3 to, Vec3 cam, float w, float k) {
        Vec3 d = to.subtract(from);
        Vec3 near = from.add(d.scale(0.55));
        ribbon(vc, m, near, to, cam, w, 70, 200, 255, (int) (110 * k));
        ribbon(vc, m, near, to, cam, w * 0.35F, 230, 255, 255, (int) (220 * k));
        ribbon(vc, m, from, near, cam, w * 0.6F, 70, 200, 255, (int) (40 * k));
    }

    /** The marigold spirals: a petal per arm per send, glowing orange, gold and pink as they turn. */
    private static void petals(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, float partial, Vec3 cam) {
        Vec3 c = b.getPosition(partial);
        Vec3 base = new Vec3(c.x, c.y - 1.2 + 1.3, c.z);
        float a0 = b.moveAngle();
        int[][] cols = {{255, 150, 24}, {255, 208, 64}, {236, 46, 140}};
        for (int sent = PaponBossEntity.PETAL_FROM; sent <= Math.min(t, PaponBossEntity.PETAL_TO); sent += PaponBossEntity.PETAL_EVERY) {
            if (t - sent > PaponBossEntity.PETAL_LIFE || t < sent) continue;
            float fade = Math.min(1F, (PaponBossEntity.PETAL_LIFE - (t - sent)) / 10F);
            for (int arm = 0; arm < PaponBossEntity.PETAL_ARMS; arm++) {
                Vec3 p = base.add(PaponBossEntity.petal(a0, arm, sent, t));
                int[] col = cols[(arm + sent / 6) % cols.length];
                float spin = (t - sent) * 0.4F + arm;
                petal(vc, m, p, cam, 0.75F, spin, col[0], col[1], col[2], (int) (230 * fade));
            }
        }
    }

    private static void petal(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam, float s, float spin, int r, int g, int b, int a) {
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 right = toCam.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(toCam).normalize();
        float cs = Mth.cos(spin), sn = Mth.sin(spin);
        Vec3 ax = right.scale(cs).add(up.scale(sn)), ay = right.scale(-sn).add(up.scale(cs));
        // a teardrop: long along one axis, narrow across
        Vec3 tip = p.add(ay.scale(s)), tail = p.add(ay.scale(-s * 0.7)), l = p.add(ax.scale(-s * 0.4)), rr = p.add(ax.scale(s * 0.4));
        // both windings: the render type culls back faces, and which side faces the camera depends on the spin
        for (Vec3[] q : new Vec3[][]{{tip, l, tail, rr}, {tip, rr, tail, l}}) {
            for (Vec3 v : q) vc.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(r, g, b, v == tail ? a / 3 : a).endVertex();
        }
    }

    /** A flat strip from a to b turned to face the camera, {@code w} wide. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int r, int g, int bl, int al) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a.add(d.scale(0.5)))).normalize().scale(w / 2);
        if (Double.isNaN(side.x)) return;
        Vec3 p0 = a.subtract(side), p1 = a.add(side), p2 = b.add(side), p3 = b.subtract(side);
        vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, bl, al).endVertex();
        vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, bl, al).endVertex();
        vc.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, bl, al).endVertex();
        vc.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, bl, al).endVertex();
    }

    // ------------------------------------------------------------------ the blue-out

    /** How far the screen is flooded with the moon's blue (P4_BLUEOUT), 0..1. */
    static float blueout(float partial) {
        PaponBossEntity b = boss();
        if (b == null || b.getBossState() != BossState.P4_BLUEOUT) return 0;
        float t = b.ticksInState() + partial;
        if (t < 14) return t / 14F;
        if (t < 44) return 1;
        return Math.max(0, 1 - (t - 44) / 16F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float a = blueout(event.getPartialTick());
        if (a <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, ((int) (a * 255) & 0xFF) << 24 | (a > 0.85F ? 0xB8F6FF : 0x4FDCFF));
    }
}
