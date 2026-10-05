package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.SerpentPath;
import com.groid.papon.boss.SkySerpentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ouros and Boros in the sky: the head where the path says, each segment of the body laid along the path behind it
 * (oriented down its own chord, so the body bends with the path), the collar of feathers on the first, the tail's fan on
 * the last; the crest's feathers ruffling, the jaws working. While it is still coming out, its portal: a churning cloud of
 * black smoke torn in the sky.
 */
public class SkySerpentRenderer extends EntityRenderer<SkySerpentEntity> {
    private static final ResourceLocation SMOKE = PaponMod.id("textures/entity/black_smoke.png");

    public SkySerpentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(SkySerpentEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SkySerpentEntity e) {
        return tex(e, "head");
    }

    private static ResourceLocation tex(SkySerpentEntity e, String part) {
        return PaponMod.id("textures/entity/serpent_" + (e.kind() == SkySerpentEntity.OUROS ? "ouros" : "boros") + "_" + part + ".png");
    }

    /** Its own buffers, drawn at once with the distance fog pushed out: it is a hundred blocks off and as big as a hill. */
    private static final MultiBufferSource.BufferSource OWN = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));

    @Override
    public void render(SkySerpentEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource ignored, int light) {
        float fogStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
        float fogEnd = com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Math.max(fogStart, 600F));
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(Math.max(fogEnd, 900F));
        try {
            draw(e, partial, pose, OWN);
            OWN.endBatch();
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart);
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private void draw(SkySerpentEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
        String kind = e.kind() == SkySerpentEntity.OUROS ? "ouros" : "boros";
        SerpentModel head = SerpentModel.get(kind + "_head"), body = SerpentModel.get(kind + "_body");
        if (head == null || body == null) return;
        SerpentPath path = e.path();
        double s = e.head(partial);
        Vec3 origin = e.getPosition(partial);
        float time = e.tickCount + partial;
        int lit = LightTexture.FULL_BRIGHT;
        float scale = (float) SkySerpentEntity.SCALE;

        // the portal: black smoke churning where it comes out, for as long as any of it is still coming through
        double len = SkySerpentEntity.HEAD + SkySerpentEntity.SEGMENT * (SkySerpentEntity.SEGMENTS + 1);
        double through = s / len;
        if (through < 1.6) smoke(e, pose, buffers, origin, (float) Math.min(1, Math.min(e.age(partial) / 20.0, (1.6 - through) / 0.4)), time);

        // the head: its back at the path's point, its snout ahead
        if (s > 0) {
            animateHead(e, head, partial, time);
            place(pose, path, origin, s, s - SkySerpentEntity.HEAD, () -> {
                pose.translate(0, 0, 0.0);
                draw(head.root, pose, buffers, tex(e, "head"), lit, scale);
            });
        }
        // the segments
        ModelPart seg = body.part(kind + "_segment"), main = body.part("body"), tail = body.part("tail"), collar = body.part("collar");
        for (int i = 0; i <= SkySerpentEntity.SEGMENTS; i++) {
            double a = s - SkySerpentEntity.HEAD - i * SkySerpentEntity.SEGMENT;
            double b = a - SkySerpentEntity.SEGMENT;
            if (a <= 0) break;
            boolean last = i == SkySerpentEntity.SEGMENTS;
            if (main != null) main.visible = !last;
            if (tail != null) tail.visible = last;
            if (collar != null) collar.visible = !last && i % 4 == 0;     // a ruff of feathers at the neck and every few segments
            ruffle(body, "collar_", time + i * 3);
            ruffle(body, "fan_", time);
            if (seg != null) seg.zRot = 0.06F * Mth.sin(time * 0.07F - i * 0.5F);
            int index = i;
            place(pose, path, origin, a, Math.max(0, b), () -> draw(body.root, pose, buffers, tex(e, "body"), lit, scale));
        }
    }

    /** Lays a piece from {@code from} (its front) to {@code to} along the path: centred on the chord, facing down it. */
    private static void place(PoseStack pose, SerpentPath path, Vec3 origin, double from, double to, Runnable draw) {
        Vec3 front = path.at(from), back = path.at(to);
        Vec3 d = front.subtract(back);
        if (d.lengthSqr() < 1e-6) d = new Vec3(0, 0, -1);
        d = d.normalize();
        Vec3 mid = front.add(back).scale(0.5).subtract(origin);
        pose.pushPose();
        pose.translate(mid.x, mid.y, mid.z);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(-d.x, -d.z)));
        pose.mulPose(Axis.XP.rotation((float) Math.asin(Mth.clamp(d.y, -1, 1))));
        draw.run();
        pose.popPose();
    }

    private static void draw(ModelPart root, PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, int light, float scale) {
        pose.pushPose();
        pose.scale(-scale, -scale, scale);
        // the models stand on y 24 (vanilla's feet): their middle up at about y 12
        pose.translate(0, -1.0, 0);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        root.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(new ResourceLocation(tex.getNamespace(), tex.getPath().replace(".png", "_glow.png"))));
        root.render(pose, glow, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void animateHead(SkySerpentEntity e, SerpentModel head, float partial, float time) {
        ModelPart jaw = head.part("lower_jaw");
        if (jaw != null) jaw.xRot = 0.75F * e.jaw(partial);
        ruffle(head, "crest_", time);
        ModelPart tongue = head.part("tongue");
        if (tongue != null) tongue.zRot = 0.1F * Mth.sin(time * 0.6F);
    }

    /** Feathers stirring in the wind of its flight. */
    private static void ruffle(SerpentModel m, String prefix, float time) {
        for (String n : m.names()) {
            if (!n.startsWith(prefix)) continue;
            ModelPart p = m.part(n);
            int k = n.hashCode();
            p.resetPose();
            p.xRot += 0.06F * Mth.sin(time * 0.31F + (k & 31));
            p.yRot += 0.04F * Mth.sin(time * 0.23F + (k >> 5 & 31));
        }
    }

    /** The black smoke it comes out of: puffs turning round the portal, swelling as it opens, dark as night. */
    private static void smoke(SkySerpentEntity e, PoseStack pose, MultiBufferSource buffers, Vec3 origin, float k, float time) {
        if (k <= 0) return;
        Vec3 c = e.portal().subtract(origin);
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SMOKE));
        java.util.Random r = new java.util.Random(e.getId() * 31L);
        for (int i = 0; i < 70; i++) {
            double ang = r.nextDouble() * Math.PI * 2 + time * (0.004 + r.nextDouble() * 0.01) * (i % 2 == 0 ? 1 : -1);
            double rad = (8 + r.nextDouble() * 38) * (0.5 + 0.5 * k);
            Vec3 at = c.add(Math.cos(ang) * rad, (r.nextDouble() - 0.5) * 30 * k, Math.sin(ang) * rad);
            float size = (float) ((16 + r.nextDouble() * 22) * k);
            float spin = time * 0.01F * (r.nextBoolean() ? 1 : -1) + r.nextFloat() * 6;
            int shade = 10 + r.nextInt(18);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(cam.rotation());
            pose.mulPose(Axis.ZP.rotation(spin));
            Matrix4f m = pose.last().pose();
            int a = (int) (235 * k);
            quad(vc, m, pose, size, shade, a);
            pose.popPose();
        }
        // the summoning light inside it: a sick green glow through the smoke at its heart
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(SMOKE));
        for (int i = 0; i < 14; i++) {
            double ang = r.nextDouble() * Math.PI * 2 + time * 0.02;
            double rad = r.nextDouble() * 14 * k;
            Vec3 at = c.add(Math.cos(ang) * rad, (r.nextDouble() - 0.5) * 10 * k, Math.sin(ang) * rad);
            float size = (float) ((10 + r.nextDouble() * 12) * k);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(cam.rotation());
            pose.mulPose(Axis.ZP.rotation(time * 0.02F + i));
            float pulse = 0.6F + 0.4F * Mth.sin(time * 0.15F + i);
            quadColor(glow, pose.last().pose(), pose, size, 50, 230, 110, (int) (120 * k * pulse));
            pose.popPose();
        }
    }

    private static void quadColor(VertexConsumer vc, Matrix4f m, PoseStack pose, float s, int r, int g, int b, int a) {
        var n = pose.last().normal();
        vc.vertex(m, -s, -s, 0).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, -s, 0).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, s, 0).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, -s, s, 0).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack pose, float s, int shade, int a) {
        var n = pose.last().normal();
        vc.vertex(m, -s, -s, 0).color(shade, shade, shade + 4, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, -s, 0).color(shade, shade, shade + 4, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, s, 0).color(shade, shade, shade + 4, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, -s, s, 0).color(shade, shade, shade + 4, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }
}
