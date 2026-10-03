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

    /** Diving through the truck's window his position is briefly inside its wall: keep him lit. */
    @Override
    protected int getSkyLightLevel(PaponBossEntity entity, net.minecraft.core.BlockPos pos) {
        int sky = super.getSkyLightLevel(entity, pos);
        return entity.getBossState() == com.groid.papon.boss.BossState.LEAP_OUT
                ? Math.max(sky, entity.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, pos.above(4))) : sky;
    }

    @Override
    protected int getBlockLightLevel(PaponBossEntity entity, net.minecraft.core.BlockPos pos) {
        int block = super.getBlockLightLevel(entity, pos);
        return entity.getBossState() == com.groid.papon.boss.BossState.LEAP_OUT ? Math.max(block, 8) : block;
    }

    /** Ghost mode (half alive, half dead): see-through and a little green, like a spirit. */
    @Override
    public software.bernie.geckolib.core.object.Color getRenderColor(PaponBossEntity entity, float partialTick, int light) {
        boolean ghost = entity.isGhost();
        if (!ghost && entity.getVehicle() instanceof com.groid.papon.vehicle.CaravanEntity v) ghost = v.isGhost();
        return ghost ? software.bernie.geckolib.core.object.Color.ofRGBA(0.72F, 1.0F, 0.86F, 0.5F)
                : super.getRenderColor(entity, partialTick, light);
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
