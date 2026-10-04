package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.item.BodyPillowItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The body pillow on a player, seen from outside (its own third-person item transform is empty), as the soft mesh
 * (PillowMesh) in the shape of what they are doing with it (PillowClient.shape): hugged to the chest and squeezed,
 * held up to the face and licked (the tongue out with each lick), folded over at the hips and humped, or just carried,
 * tucked under the arm. The arm and head poses are PillowClient's.
 */
public class PillowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TONGUE = PaponMod.id("textures/entity/tongue.png");

    public PillowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partial, float age, float headYaw, float headPitch) {
        ItemStack stack = player.getMainHandItem();
        boolean main = true;
        if (!(stack.getItem() instanceof BodyPillowItem)) {
            stack = player.getOffhandItem();
            main = false;
            if (!(stack.getItem() instanceof BodyPillowItem)) return;
        }
        if (player.isSleeping()) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        PillowClient.Mode mode = PillowClient.mode(player, stack);
        PillowMesh.Shape shape = PillowClient.shape(player, partial, false);
        ps.pushPose();
        switch (mode) {
            case LICK -> {
                // up at the face, pressed in where the face is
                model.body.translateAndRotate(ps);
                ps.translate(0, -0.05, -0.4);
                ps.mulPose(Axis.XP.rotationDegrees(-10));
                drawPillow(ps, buffers, light, shape);
                ps.popPose();
                tongue(ps, buffers, light, model, PillowClient.lick(age));
                return;
            }
            case HUG -> {
                // held tight against the chest, Makima to you, squeezed where the arms are
                model.body.translateAndRotate(ps);
                ps.translate(0, 0.3, -0.33);
                ps.mulPose(Axis.ZP.rotationDegrees(4 * (float) Math.sin(age * 0.08F)));
                drawPillow(ps, buffers, light, shape);
            }
            case HUMP -> {
                // folded over double at the hips, sticking out in front, squashed on every thrust
                model.body.translateAndRotate(ps);
                ps.translate(0, 0.72, -0.24);
                ps.mulPose(Axis.ZP.rotationDegrees(180));
                ps.mulPose(Axis.XP.rotationDegrees(90));
                PillowMesh.render(ps, buffers, light, shape);
            }
            default -> {
                // carried: tucked under the arm, along the side
                HumanoidArm arm = main == (player.getMainArm() == HumanoidArm.RIGHT) ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
                (arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm).translateAndRotate(ps);
                int s = arm == HumanoidArm.RIGHT ? -1 : 1;
                ps.translate(s * 0.05, 0.42, 0.02);
                ps.mulPose(Axis.YP.rotationDegrees(90));
                ps.mulPose(Axis.XP.rotationDegrees(-12));
                drawPillow(ps, buffers, light, shape);
            }
        }
        ps.popPose();
    }

    /** The pillow upright (model space is y-down here), its front toward the wearer. */
    private static void drawPillow(PoseStack ps, MultiBufferSource buffers, int light, PillowMesh.Shape shape) {
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        PillowMesh.render(ps, buffers, light, shape);
    }

    /** The tongue: a pink slab out of the mouth, longer at the top of each lick. */
    private static void tongue(PoseStack ps, MultiBufferSource buffers, int light, PlayerModel<AbstractClientPlayer> model, float lick) {
        ps.pushPose();
        model.head.translateAndRotate(ps);
        float len = (0.6F + 3.4F * lick) / 16F;
        float x0 = -1.1F / 16, x1 = 1.1F / 16, y0 = -2.7F / 16, y1 = -2.0F / 16, z0 = -4.05F / 16 - len, z1 = -4.05F / 16 + 0.2F / 16;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TONGUE));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float[][] q = {
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0},
                {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0},
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1},
                {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0},
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0},
        };
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (float[] f : q) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(255, 255, 255, 255).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, f[12], f[13], f[14]).endVertex();
            }
        }
        ps.popPose();
    }
}
