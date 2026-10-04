package com.groid.papon.client;

import com.groid.papon.entity.BodyPillowEntity;
import com.groid.papon.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * A body pillow laid on a bed (entity/BodyPillowEntity): the item's own model, lying on its side along the
 * bed, its length head to foot, Makima turned in toward whoever sleeps there.
 */
public class BodyPillowRenderer extends EntityRenderer<BodyPillowEntity> {
    private ItemStack stack;

    public BodyPillowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(BodyPillowEntity pillow, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        if (stack == null) stack = new ItemStack(ModItems.BODY_PILLOW.get());
        Direction f = pillow.facing(), side = pillow.side();
        ps.pushPose();
        ps.translate(0, 0.3, 0);
        // item axes: its length (y) head-to-foot along the bed, its front (-z) in toward the sleeper, its width (x) up
        float fx = f.getStepX(), fz = f.getStepZ(), sx = side.getStepX(), sz = side.getStepZ();
        Matrix3f m = new Matrix3f(0, 1, 0, fx, 0, fz, sx, 0, sz);
        ps.mulPose(new Quaternionf().setFromNormalized(m));
        ps.scale(1.6F, 1.6F, 1.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buffers,
                pillow.level(), pillow.getId());
        ps.popPose();
        super.render(pillow, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BodyPillowEntity pillow) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
