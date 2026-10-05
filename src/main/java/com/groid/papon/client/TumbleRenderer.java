package com.groid.papon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** A thrown thing drawn as its 3D item model, tumbling end over end (the potato rounds, the molotov). */
public class TumbleRenderer<T extends Entity> extends EntityRenderer<T> {
    private final Supplier<ItemStack> stack;
    private final float scale;
    private final float spin;

    public TumbleRenderer(EntityRendererProvider.Context ctx, Supplier<ItemStack> stack, float scale, float spin) {
        super(ctx);
        this.stack = stack;
        this.scale = scale;
        this.spin = spin;
        this.shadowRadius = 0.1F;
    }

    @Override
    public void render(T e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(e) < 12.25) return;
        float age = e.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.1, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(age * spin));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack.get(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, e.level(), e.getId());
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(T e) { return TextureAtlas.LOCATION_BLOCKS; }
}
