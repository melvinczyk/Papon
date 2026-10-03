package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossProp;
import com.groid.papon.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws {@link BossProp}s: each kind is one of the item models from tools/gen_props.py, turned the way
 * that kind moves (cleavers spin flat, fries point where they fly and shiver while they aim, tots and
 * potatoes tumble, the burrito stands round whoever is in it, the sprouts push up out of the ground). The
 * mash wave is a ring of wall drawn here, at the radius the server gives it.
 */
public class PropRenderer extends EntityRenderer<BossProp> {
    private static final ResourceLocation MASH = PaponMod.id("textures/entity/mash_wave.png");

    public PropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(BossProp p, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = p.tickCount + partialTick;
        BossProp.Kind k = p.kind();
        if (k == BossProp.Kind.MASH) {
            drawMash(p, pose, buffers, light);
            return;
        }
        pose.pushPose();
        int lit = light;
        ItemStack stack;
        switch (k) {
            case CLEAVER -> {
                stack = new ItemStack(ModItems.FX_CLEAVER.get());
                pose.translate(0, 0.2, 0);
                pose.mulPose(Axis.YP.rotationDegrees(age * 42F));
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.scale(1.8F, 1.8F, 1.8F);
            }
            case CLEAVER_DART -> {
                // along its flight, turning end over end (still while it hangs aimed)
                stack = new ItemStack(ModItems.FX_CLEAVER.get());
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot() + 90F));
                pose.mulPose(Axis.ZP.rotationDegrees(-p.getXRot() + (p.delay() > 0 ? 45F : -age * 50F)));
                pose.scale(1.9F, 1.9F, 1.9F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case FRY -> {
                stack = new ItemStack(ModItems.FX_FRY.get());
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(p.getXRot() + 90F));
                if (p.delay() > 0) pose.translate(Mth.sin(age * 2.7F) * 0.04, 0, Mth.cos(age * 3.1F) * 0.04);
                pose.scale(2.4F, 2.4F, 2.4F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case TOT, TOT_SMALL -> {
                stack = new ItemStack(ModItems.FX_TOT.get());
                float s = k == BossProp.Kind.TOT ? 1.6F : 0.8F;
                pose.translate(0, 0.25 * s, 0);
                pose.mulPose(Axis.XP.rotationDegrees(age * 23F));
                pose.mulPose(Axis.ZP.rotationDegrees(age * 11F));
                pose.scale(s, s, s);
            }
            case METEOR -> {
                stack = new ItemStack(ModItems.FX_METEOR.get());
                pose.translate(0, 0.6, 0);
                pose.mulPose(Axis.XP.rotationDegrees(age * 9F));
                pose.mulPose(Axis.YP.rotationDegrees(age * 5F));
                pose.scale(2.6F, 2.6F, 2.6F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case BURRITO -> {
                stack = new ItemStack(ModItems.FX_BURRITO.get());
                float in = Math.min(1F, age / 4F);
                float squish = 1F + 0.08F * Mth.sin(age * 0.9F) * (1F - Math.min(1F, age / 20F));
                pose.translate(0, 1.0 * in, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                pose.scale(2.2F * squish, 2.0F * in, 2.2F * squish);
            }
            case SPROUTS -> {
                stack = new ItemStack(ModItems.FX_SPROUTS.get());
                float up = Mth.clamp(age / 5F, 0F, 1F);
                pose.translate(0, 0.9 * up, 0);
                pose.scale(1.8F, 1.8F * up, 1.8F);
            }
            case GIANT_SPUD -> {
                // El Gran Meteoro: swells in over 2 s to nearly the width of his coliseum, blazing, rays pouring off it
                stack = new ItemStack(ModItems.FX_GIANT_SPUD.get());
                float in = Math.min(1F, age / 40F);
                in = in * in * (3 - 2 * in);
                float pulse = 1F + 0.03F * Mth.sin(age * 0.4F);
                lit = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                pose.pushPose();
                pose.scale(5F, 5F, 5F);
                BossRenderer.drawRays(pose, buffers, Math.min(1F, age / 30F), age * 0.6F, 0F);
                pose.popPose();
                pose.mulPose(Axis.YP.rotationDegrees(age * 1.2F));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.04F) * 10F));
                float s = 26F * in * pulse;
                pose.scale(s, s, s);
            }
            default -> {
                pose.popPose();
                return;
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, lit, OverlayTexture.NO_OVERLAY, pose,
                buffers, p.level(), p.getId());
        pose.popPose();
        super.render(p, yaw, partialTick, pose, buffers, light);
    }

    /** The mash wave: a lumpy wall 1.4 high in a ring of the synced radius, gravy along the top. */
    private static void drawMash(BossProp p, PoseStack pose, MultiBufferSource buffers, int light) {
        float r = p.radius();
        if (r <= 0.1F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(MASH));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int segs = Math.max(16, (int) (r * 5));
        float h = 1.4F;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float bump0 = h + 0.25F * Mth.sin(i * 1.7F), bump1 = h + 0.25F * Mth.sin((i + 1) * 1.7F);
            float u0 = (i % 4) / 4F, u1 = u0 + 0.25F;
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            vert(vc, m, n, x0, 0, z0, u0, 1, light, nx, nz);
            vert(vc, m, n, x1, 0, z1, u1, 1, light, nx, nz);
            vert(vc, m, n, x1, bump1, z1, u1, 0, light, nx, nz);
            vert(vc, m, n, x0, bump0, z0, u0, 0, light, nx, nz);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light, float nx,
                             float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, 0, nz).endVertex();
    }

    /** The mash wave is a ring far wider than its entity: draw it whenever it is near enough to see. */
    @Override
    public boolean shouldRender(BossProp p, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (p.kind() == BossProp.Kind.GIANT_SPUD) return p.distanceToSqr(x, y, z) < 192 * 192;
        if (p.kind() == BossProp.Kind.MASH) return p.distanceToSqr(x, y, z) < 96 * 96;
        return super.shouldRender(p, frustum, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(BossProp p) {
        return MASH;
    }
}
