package com.groid.papon.client;

import com.groid.papon.entity.npc.TacoVendorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Papon the Taquero. Once he is beaten and back at his window (TacoVendorEntity#isFriendly) he has
 * nothing left to hide: no toque, no fake mustache, the Norwood 7 out for all to see. His animations
 * pin those away, so the hair is shown here after they have run (and the hiding is set every frame
 * either way: the baked model is shared with every other vendor).
 */
public class VendorRenderer extends CrowdRenderer<TacoVendorEntity> {
    private static final String[] DISGUISE = {"toque", "mustache", "stache_mark"};
    private static final String[] HAIR = {"hair", "wisps"};

    public VendorRenderer(EntityRendererProvider.Context ctx) { super(ctx, new CrowdModel<>("taco_vendor", "papon_npc", false), 1.5F, 0.9F, 0.35F); }

    @Override
    public void preRender(PoseStack pose, TacoVendorEntity v, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        boolean friendly = v.isFriendly();
        for (String name : DISGUISE) model.getBone(name).ifPresent(bone -> bone.setHidden(friendly));
        super.preRender(pose, v, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    public void renderRecursively(PoseStack pose, TacoVendorEntity v, GeoBone bone, RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        if (v.isFriendly()) {
            for (String name : HAIR) {
                if (bone.getName().equals(name)) {
                    bone.setScaleX(1);
                    bone.setScaleY(1);
                    bone.setScaleZ(1);
                }
            }
        }
        super.renderRecursively(pose, v, bone, type, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }
}
