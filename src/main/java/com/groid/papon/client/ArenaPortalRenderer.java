package com.groid.papon.client;

import com.groid.papon.boss.ArenaPortalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The coliseum's portal in the Taco Truck window: an upright oval of the end gateway's starfield, rimmed in gold. */
public class ArenaPortalRenderer extends EntityRenderer<ArenaPortalEntity> {
    private static final int SEGMENTS = 28;
    private static final float RX = 0.6F, RY = 1.05F, CY = 1.1F;

    public ArenaPortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ArenaPortalEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float k = e.open(partial);
        k = k * k * (3 - 2 * k);
        if (k <= 0.01F) return;
        float time = e.tickCount + partial;
        pose.pushPose();
        pose.translate(0, CY, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        float wob = 1 + 0.03F * Mth.sin(time * 0.15F);
        pose.scale(k * wob, k / wob, 1);
        Matrix4f m = pose.last().pose();
        VertexConsumer stars = buffers.getBuffer(RenderType.endGateway());
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float x0 = Mth.cos(a0) * RX, y0 = Mth.sin(a0) * RY, x1 = Mth.cos(a1) * RX, y1 = Mth.sin(a1) * RY;
            // both faces
            stars.vertex(m, 0, 0, 0).endVertex();
            stars.vertex(m, x0, y0, 0).endVertex();
            stars.vertex(m, x1, y1, 0).endVertex();
            stars.vertex(m, 0, 0, 0).endVertex();
            stars.vertex(m, 0, 0, 0).endVertex();
            stars.vertex(m, x1, y1, 0).endVertex();
            stars.vertex(m, x0, y0, 0).endVertex();
            stars.vertex(m, 0, 0, 0).endVertex();
        }
        VertexConsumer rim = buffers.getBuffer(RenderType.lightning());
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.3F);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float in = 1F, out = 1.12F;
            float[][] q = {{Mth.cos(a0) * RX * in, Mth.sin(a0) * RY * in}, {Mth.cos(a0) * RX * out, Mth.sin(a0) * RY * out},
                    {Mth.cos(a1) * RX * out, Mth.sin(a1) * RY * out}, {Mth.cos(a1) * RX * in, Mth.sin(a1) * RY * in}};
            for (int side = 0; side < 2; side++) {
                for (int v = 0; v < 4; v++) {
                    float[] p = q[side == 0 ? v : 3 - v];
                    rim.vertex(m, p[0], p[1], 0.002F * (side == 0 ? 1 : -1)).color(1F, 0.78F, 0.25F, 0.85F * pulse).endVertex();
                }
            }
        }
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ArenaPortalEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
