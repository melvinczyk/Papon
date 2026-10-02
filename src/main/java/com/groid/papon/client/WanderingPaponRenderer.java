package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WanderingPaponRenderer extends GeoEntityRenderer<WanderingPaponEntity> {
    public WanderingPaponRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("wandering_papon")));
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(WanderingPaponEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        SpeechBubble.render(entity, entity.getSpeech(), 0.45F, pose, buffers, light, entityRenderDispatcher);
    }
}
