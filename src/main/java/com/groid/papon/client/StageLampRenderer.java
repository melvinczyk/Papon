package com.groid.papon.client;

import com.groid.papon.block.StageLampBlock;
import com.groid.papon.block.StageLampBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * A lit floodlight's beam (block/StageLampBlock): a soft cone of warm light from the lens, tilted down
 * over the band, fading out along its length, with a faint flicker. Additive, so it only ever brightens.
 */
public class StageLampRenderer implements BlockEntityRenderer<StageLampBlockEntity> {
    private static final int SIDES = 14;
    private static final float LENGTH = 9.5F, RADIUS = 2.4F, TILT = 9F;

    public StageLampRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(StageLampBlockEntity lamp, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState s = lamp.getBlockState();
        if (!(s.getBlock() instanceof StageLampBlock) || !s.getValue(StageLampBlock.LIT) || lamp.getLevel() == null) return;
        float t = lamp.getLevel().getGameTime() + partial;
        float flicker = 0.92F + 0.05F * Mth.sin(t * 0.7F) + 0.03F * Mth.sin(t * 2.3F + 1.1F);
        ps.pushPose();
        // the lens: up on the can, on the side it faces
        ps.translate(0.5, 1.62, 0.5);
        float aim = lamp.aim();
        float yaw = Float.isNaN(aim) ? -s.getValue(StageLampBlock.FACING).toYRot() : (float) Math.toDegrees(Mth.HALF_PI - aim);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(0, 0, 0.3);
        ps.mulPose(Axis.XP.rotationDegrees(TILT));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        int a0 = (int) (64 * flicker), a1 = 0;
        for (int i = 0; i < SIDES; i++) {
            float t0 = i * Mth.TWO_PI / SIDES, t1 = (i + 1) * Mth.TWO_PI / SIDES;
            float r0 = 0.22F;
            // a quad from the lens ring to the far ring; both windings, so it shows from inside too
            float[] p0 = {Mth.cos(t0) * r0, Mth.sin(t0) * r0, 0}, p1 = {Mth.cos(t1) * r0, Mth.sin(t1) * r0, 0};
            float[] q0 = {Mth.cos(t0) * RADIUS, Mth.sin(t0) * RADIUS, LENGTH}, q1 = {Mth.cos(t1) * RADIUS, Mth.sin(t1) * RADIUS, LENGTH};
            quad(vc, m, p0, p1, q1, q0, a0, a1);
            quad(vc, m, q0, q1, p1, p0, a1, a0);
        }
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int alphaAB, int alphaCD) {
        vc.vertex(m, a[0], a[1], a[2]).color(255, 226, 170, alphaAB).endVertex();
        vc.vertex(m, b[0], b[1], b[2]).color(255, 226, 170, alphaAB).endVertex();
        vc.vertex(m, c[0], c[1], c[2]).color(255, 226, 170, alphaCD).endVertex();
        vc.vertex(m, d[0], d[1], d[2]).color(255, 226, 170, alphaCD).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(StageLampBlockEntity lamp) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
