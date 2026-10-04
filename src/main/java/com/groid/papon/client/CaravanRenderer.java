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

    /** Crushed into a ball and reformed (Ascended Papon): drawn at its squash, a little flatter than wide as it crumples. */
    @Override
    public void preRender(PoseStack pose, CaravanEntity van, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        float k = van.squash();
        this.scaleWidth = k;
        this.scaleHeight = k < 1 ? k * (0.8F + 0.2F * k) : 1F;
        super.preRender(pose, van, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    public void render(CaravanEntity van, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(van, yaw, partialTick, pose, buffers, light);
        if (van.isSkyline() && van.squash() > 0.95F) underglow(van, partialTick, pose, buffers);
    }

    /** The Skyline's purple underglow: a pool of light on the ground under it, brightest under the middle, breathing. */
    private static void underglow(CaravanEntity van, float partial, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-Mth.rotLerp(partial, van.yBodyRotO, van.yBodyRot)));
        org.joml.Matrix4f m = pose.last().pose();
        float pulse = 0.85F + 0.15F * Mth.sin((van.tickCount + partial) * 0.2F);
        float hw = (float) CaravanEntity.HALF_WIDTH + 0.6F, hl = (float) CaravanEntity.HALF_LENGTH + 0.4F;
        int n = 10;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -hw + 2 * hw * i / n, x1 = -hw + 2 * hw * (i + 1) / n;
                float z0 = -hl + 2 * hl * j / n, z1 = -hl + 2 * hl * (j + 1) / n;
                quadGlow(vc, m, x0, z0, x1, z1, hw, hl, pulse);
            }
        }
        pose.popPose();
    }

    private static void quadGlow(VertexConsumer vc, org.joml.Matrix4f m, float x0, float z0, float x1, float z1, float hw, float hl, float pulse) {
        float[][] c = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
        for (float[] p : c) {
            float d = (float) Math.min(1, Math.hypot(p[0] / hw, p[1] / hl));
            int a = (int) (150 * pulse * (1 - d * d));
            vc.vertex(m, p[0], 0.06F, p[1]).color(170, 70, 255, Math.max(0, a)).endVertex();
        }
    }

    @Override
    protected float getDeathMaxRotation(CaravanEntity entity) {
        return 0;
    }

    static final class Model extends GeoModel<CaravanEntity> {
        private static final ResourceLocation GEO = PaponMod.id("geo/entity/caravan.geo.json");
        private static final ResourceLocation TEX = PaponMod.id("textures/entity/caravan.png");
        private static final ResourceLocation ANIM = PaponMod.id("animations/entity/caravan.animation.json");
        // Ascended Papon's Skyline (tools/gen_skyline.py): the same rig, so everything below drives it too
        private static final ResourceLocation SKY_GEO = PaponMod.id("geo/entity/skyline.geo.json");
        private static final ResourceLocation SKY_TEX = PaponMod.id("textures/entity/skyline.png");
        private static final String[] WHEELS = {"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr"};

        @Override public ResourceLocation getModelResource(CaravanEntity van) { return van.isSkyline() ? SKY_GEO : GEO; }
        @Override public ResourceLocation getTextureResource(CaravanEntity van) { return van.isSkyline() ? SKY_TEX : TEX; }
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

    /** GeckoLib labels every non-mob living entity; a van shows its name only if someone named it. */
    @Override
    public boolean shouldShowName(CaravanEntity van) {
        return van.hasCustomName() && super.shouldShowName(van);
    }
}
