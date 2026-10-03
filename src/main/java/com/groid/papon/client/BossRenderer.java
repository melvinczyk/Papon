package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Chef Papon as the boss: 1.5x like the taquero he was, cut down to size behind the wheel of his
 * van so he fits under its roof. Translucent, so the tequila in his bottles shows through the
 * glass. No vanilla death flip: his death is an animation.
 */
public class BossRenderer extends GeoEntityRenderer<PaponBossEntity> {
    private static final float SCALE = 1.5F;
    private static final float DRIVING_SCALE = 0.95F;

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("papon_boss")));
        withScale(SCALE);
        this.shadowRadius = 0.9F;
    }

    @Override
    public RenderType getRenderType(PaponBossEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack pose, PaponBossEntity entity, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        float s = entity.isPassenger() ? DRIVING_SCALE : SCALE;
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.preRender(pose, entity, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    protected float getDeathMaxRotation(PaponBossEntity entity) {
        return 0;
    }

    @Override
    public void render(PaponBossEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        SpeechBubble.render(entity, entity.getSpeech(), 0.4F, pose, buffers, light, entityRenderDispatcher);
    }
}
