package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.vehicle.CaravanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The van. Translucent (its windows are glass you can see Papon through), headlights and tail
 * lamps glow while the engine runs (caravan_glowmask.png), and the wheels, steering and body
 * lean are driven here from the van's motion rather than by animations.
 */
public class CaravanRenderer extends GeoEntityRenderer<CaravanEntity> {
    public CaravanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 1.6F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack pose, CaravanEntity van, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                               VertexConsumer buffer, float partialTick, int light, int overlay) {
                if (van.isEngineOn()) super.render(pose, van, model, renderType, buffers, buffer, partialTick, light, overlay);
            }
        });
    }

    @Override
    public RenderType getRenderType(CaravanEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    /** Ghost mode (half alive, half dead): see-through and a little green, like a spirit. */
    @Override
    public software.bernie.geckolib.core.object.Color getRenderColor(CaravanEntity entity, float partialTick, int light) {
        boolean ghost = entity.isGhost();
        return ghost ? software.bernie.geckolib.core.object.Color.ofRGBA(0.72F, 1.0F, 0.86F, 0.5F)
                : super.getRenderColor(entity, partialTick, light);
    }

    @Override
    protected float getDeathMaxRotation(CaravanEntity entity) {
        return 0;
    }

    static final class Model extends GeoModel<CaravanEntity> {
        private static final ResourceLocation GEO = PaponMod.id("geo/entity/caravan.geo.json");
        private static final ResourceLocation TEX = PaponMod.id("textures/entity/caravan.png");
        private static final ResourceLocation ANIM = PaponMod.id("animations/entity/caravan.animation.json");
        private static final String[] WHEELS = {"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr"};

        @Override public ResourceLocation getModelResource(CaravanEntity van) { return GEO; }
        @Override public ResourceLocation getTextureResource(CaravanEntity van) { return TEX; }
        @Override public ResourceLocation getAnimationResource(CaravanEntity van) { return ANIM; }

        @Override
        public void setCustomAnimations(CaravanEntity van, long instanceId, AnimationState<CaravanEntity> state) {
            float partial = state.getPartialTick();
            float spin = Mth.lerp(partial, van.wheelSpinO, van.wheelSpin);
            for (String w : WHEELS) {
                CoreGeoBone bone = getAnimationProcessor().getBone(w);
                if (bone != null) bone.setRotX(-spin);
            }
            float steer = van.steerAngle * Mth.DEG_TO_RAD;
            for (String s : new String[]{"steer_fl", "steer_fr"}) {
                CoreGeoBone bone = getAnimationProcessor().getBone(s);
                if (bone != null) bone.setRotY(steer);
            }
            CoreGeoBone wheel = getAnimationProcessor().getBone("steering_wheel");
            if (wheel != null) wheel.setRotZ(-steer * 3F);
            CoreGeoBone body = getAnimationProcessor().getBone("body");
            if (body != null) {
                body.setRotZ(van.bodyRoll * Mth.DEG_TO_RAD);
                body.setRotX(van.bodyPitch * Mth.DEG_TO_RAD);
            }
            CoreGeoBone dice = getAnimationProcessor().getBone("dice");
            if (dice != null) {
                dice.setRotX(van.bodyPitch * 4F * Mth.DEG_TO_RAD);
                dice.setRotZ(van.bodyRoll * 4F * Mth.DEG_TO_RAD);
            }
        }
    }
}
