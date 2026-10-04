package com.groid.papon.client;

import com.groid.papon.entity.npc.GerbinEntity;
import com.groid.papon.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Gerbin, with the winner's body pillow in his arms while he brings it over (the item drawn on his
 * `pillow` bone, standing up against his chest, Makima facing out).
 */
public class GerbinRenderer extends CrowdRenderer<GerbinEntity> {
    public GerbinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrowdModel<>("gerbin", "gerbin", true), 1.25F, 0.4F, 0.6F);
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            private ItemStack pillow;

            @Override
            protected ItemStack getStackForBone(GeoBone bone, GerbinEntity g) {
                if (!bone.getName().equals("pillow") || !g.carryingPillow()) return null;
                if (pillow == null) pillow = new ItemStack(ModItems.BODY_PILLOW.get());
                return pillow;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, GerbinEntity g) {
                return ItemDisplayContext.FIXED;
            }

            @Override
            protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack, GerbinEntity g, MultiBufferSource buffers,
                                              float partialTick, int light, int overlay) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.XP.rotationDegrees(-8));
                pose.scale(0.75F, 0.75F, 0.75F);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                        pose, buffers, g.level(), g.getId());
                pose.popPose();
            }
        });
    }
}
