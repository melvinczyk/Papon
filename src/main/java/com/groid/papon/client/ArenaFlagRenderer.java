package com.groid.papon.client;

import com.groid.papon.block.ArenaFlagBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The coliseum's flags (block/ArenaFlagBlockEntity): La Bandera Papa hanging from a gold rod held off
 * the wall by two brackets, the cloth rippling ({@link FlagCloth}). Each kind has its size and where
 * it hangs from its block; the wall curves, so the cloth stands a little off it.
 */
public class ArenaFlagRenderer implements BlockEntityRenderer<ArenaFlagBlockEntity> {
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "textures/block/gold_block.png");
    /** width, height, top (above the block's bottom), standoff from the block's centre, wave depth. */
    private static final float[][] KINDS = {
            {4.2F, 2.4F, 1.0F, 0.62F, 0.07F},
            {2.8F, 1.6F, 0.0F, 0.62F, 0.08F},
            {7.0F, 4.0F, 1.6F, 0.54F, 0.03F},
    };

    public ArenaFlagRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ArenaFlagBlockEntity flag, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (flag.getLevel() == null) return;
        float[] k = KINDS[Mth.clamp(flag.kind(), 0, KINDS.length - 1)];
        float w = k[0], h = k[1], top = k[2], off = k[3], amp = k[4];
        float phi = flag.normal();
        BlockPos pos = flag.getBlockPos();
        // the light of the air in front of the cloth (the block itself is solid)
        Vec3 front = Vec3.atCenterOf(pos).add(Mth.cos(phi) * 1.2, top - h * 0.5 - 0.5, Mth.sin(phi) * 1.2);
        int lt = LevelRenderer.getLightColor(flag.getLevel(), BlockPos.containing(front));
        float time = flag.getLevel().getGameTime() + partial;
        float phase = (pos.getX() * 0.37F + pos.getZ() * 0.61F) % 6.28F;

        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(Mth.HALF_PI - phi));
        // the rod and its brackets
        VertexConsumer gold = buffers.getBuffer(RenderType.entitySolid(GOLD));
        box(ps, gold, -w / 2 - 0.12F, top - 0.05F, off - 0.04F, w / 2 + 0.12F, top + 0.05F, off + 0.06F, lt);
        for (float s : new float[]{-1, 1}) {
            float x = s * (w / 2 - 0.3F);
            box(ps, gold, x - 0.04F, top - 0.03F, 0.2F, x + 0.04F, top + 0.03F, off, lt);
        }
        box(ps, gold, -w / 2 - 0.2F, top - 0.08F, off - 0.07F, -w / 2 - 0.06F, top + 0.08F, off + 0.09F, lt);
        box(ps, gold, w / 2 + 0.06F, top - 0.08F, off - 0.07F, w / 2 + 0.2F, top + 0.08F, off + 0.09F, lt);
        // the cloth, hung just in front of the rod
        ps.translate(-w / 2, top - 0.03F, off + 0.02F);
        int nx = Math.max(8, (int) (w * 4)), ny = Math.max(4, (int) (h * 3));
        FlagCloth.draw(ps, buffers.getBuffer(FlagCloth.renderType()), w, h, lt, time, phase, amp, false, nx, ny);
        ps.popPose();
    }

    private static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1},
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
        };
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(255, 255, 255, 255).uv(uv[i][0] * 0.25F, uv[i][1] * 0.25F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, f[12], f[13], f[14]).endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ArenaFlagBlockEntity flag) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
