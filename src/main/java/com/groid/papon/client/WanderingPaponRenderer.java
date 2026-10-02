package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WanderingPaponRenderer extends GeoEntityRenderer<WanderingPaponEntity> {
    /** One bottle bone per JarritoItem.Flavor, in ordinal order. */
    private static final String[] JARRITO_BONES = {"jarrito_red", "jarrito_orange", "jarrito_yellow", "jarrito_green"};

    public WanderingPaponRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("wandering_papon")));
        this.shadowRadius = 0.6F;
    }

    @Override
    public void preRender(PoseStack poseStack, WanderingPaponEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // The animations show "the bottle"; only the flavour he was actually given is drawn.
        int flavor = animatable.getFlavorId();
        for (int i = 0; i < JARRITO_BONES.length; i++) {
            final boolean hidden = i != flavor;
            model.getBone(JARRITO_BONES[i]).ifPresent(b -> b.setHidden(hidden));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void render(WanderingPaponEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        SpeechBubble.render(entity, entity.getSpeech(), 0.45F, pose, buffers, light, entityRenderDispatcher);
    }
}
