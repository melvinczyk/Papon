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
import com.groid.papon.boss.BossState;

/**
 * Chef Papon as the boss: 1.5x like the taquero he was, cut down to size behind the wheel of his
 * van so he fits under its roof. Translucent, so the tequila in his bottles shows through the
 * glass. No vanilla death flip: his death is an animation.
 *
 * Phase two: baked golden (papon_boss_baked.png) once seasoned, 1.75x once risen; the fry crown
 * glows (glowmask); during the seasoning a flowing aura runs over a slightly bigger copy of him (the
 * charged creeper's energy swirl, two shells, gold); he shakes with the scream; and in the
 * cutscene's point-of-view shot he is not drawn at all (the camera is his eyes).
 */
public class BossRenderer extends GeoEntityRenderer<PaponBossEntity> {
    private static final float SCALE = 1.5F;
    private static final float RISEN_SCALE = 1.75F;
    private static final float DRIVING_SCALE = 0.95F;
    private static final ResourceLocation BAKED = PaponMod.id("textures/entity/papon_boss_baked.png");
    private static final ResourceLocation AURA = PaponMod.id("textures/entity/papon_aura.png");

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("papon_boss")));
        withScale(SCALE);
        this.shadowRadius = 0.9F;
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, PaponBossEntity boss, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                               VertexConsumer buffer, float partialTick, int light, int overlay) {
                float k = aura(boss, partialTick);
                if (k <= 0) return;
                float time = boss.tickCount + partialTick;
                RenderType swirl = RenderType.energySwirl(AURA, (time * 0.013F) % 1F, (time * -0.021F) % 1F);
                for (float grow : new float[]{1.07F, 1.16F}) {
                    pose.pushPose();
                    pose.translate(0, 0.9, 0);
                    pose.scale(grow, grow, grow);
                    pose.translate(0, -0.9, 0);
                    float f = k * (grow > 1.1F ? 0.6F : 1F);
                    getRenderer().reRender(model, pose, buffers, boss, swirl, buffers.getBuffer(swirl), partialTick,
                            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            Math.min(1F, f), Math.min(1F, f * 0.78F), Math.min(1F, f * 0.32F), 1F);
                    pose.popPose();
                }
            }
        });
    }

    /** How strong the seasoning aura is now: builds with the salt, flares when he bakes, fades through the scream. */
    private static float aura(PaponBossEntity boss, float partialTick) {
        BossState s = boss.getBossState();
        float t = boss.ticksInState() + partialTick;
        float pulse = 0.85F + 0.15F * net.minecraft.util.Mth.sin(t * 0.5F);
        if (s == BossState.P2_SEASON) {
            if (t < 14) return 0;
            float k = Math.min(1F, (t - 14) / 14F) * 0.7F;
            if (t > 64) k = 0.8F;
            if (t > 76 && t < 92) k = 0.8F + 0.8F * (1 - Math.abs(t - 82) / 10F);
            return k * pulse;
        }
        if (s == BossState.P2_SCREAM) return Math.max(0, 1F - t / 40F) * pulse;
        return 0;
    }

    @Override
    public ResourceLocation getTextureLocation(PaponBossEntity boss) {
        return boss.isBaked() ? BAKED : super.getTextureLocation(boss);
    }

    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(PaponBossEntity boss, float partialTick) {
        BossState s = boss.getBossState();
        int t = boss.ticksInState();
        double j = 0;
        if (s == BossState.P2_SCREAM && t >= 15 && t < 56) j = 0.07;
        else if (s == BossState.P2_SEASON && t >= 76 && t < 90) j = 0.05;
        if (j == 0) return super.getRenderOffset(boss, partialTick);
        var r = boss.getRandom();
        return new net.minecraft.world.phys.Vec3(r.nextGaussian() * j, r.nextGaussian() * j * 0.5, r.nextGaussian() * j);
    }

    @Override
    public boolean shouldRender(PaponBossEntity boss, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return !Cinematic.isPovOf(boss) && super.shouldRender(boss, frustum, x, y, z);
    }

    @Override
    public RenderType getRenderType(PaponBossEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack pose, PaponBossEntity entity, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        float s = entity.isPassenger() ? DRIVING_SCALE : entity.isRisen() ? RISEN_SCALE : SCALE;
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
        if (entity.getBossState().unstoppable() && !entity.isGhost()) {
            // unstoppable: a red heat pulsing through him
            float k = 0.5F + 0.5F * net.minecraft.util.Mth.sin((entity.tickCount + partialTick) * 0.6F);
            return software.bernie.geckolib.core.object.Color.ofRGBA(1.0F, 0.55F + 0.2F * k, 0.5F + 0.15F * k, 1F);
        }
        boolean ghost = entity.isGhost();
        if (!ghost && entity.getVehicle() instanceof com.groid.papon.vehicle.CaravanEntity v) ghost = v.isGhost();
        return ghost ? software.bernie.geckolib.core.object.Color.ofRGBA(0.72F, 1.0F, 0.86F, 0.5F)
                : super.getRenderColor(entity, partialTick, light);
    }

    @Override
    protected float getDeathMaxRotation(PaponBossEntity entity) {
        return 0;
    }

    /** How strong the light rays bursting out of him are: when he bakes golden, and through the scream. */
    private static float rays(PaponBossEntity boss, float partialTick) {
        BossState s = boss.getBossState();
        float t = boss.ticksInState() + partialTick;
        if (s == BossState.P2_SEASON && t > 72) return Math.max(0, 1F - Math.abs(t - 82) / 12F);
        if (s == BossState.P2_SCREAM && t > 12) return t < 22 ? (t - 12) / 10F : t < 50 ? 1F : Math.max(0, 1F - (t - 50) / 14F);
        return 0;
    }

    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);

    /** Golden shafts of light out of his middle in every direction, turning (the dragon's death, his way). */
    static void drawRays(PoseStack pose, MultiBufferSource buffers, float k, float time, float height) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(1945L);
        pose.pushPose();
        pose.translate(0, height, 0);
        int n = 10 + (int) (k * 34);
        for (int i = 0; i < n; i++) {
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(r.nextFloat() * 360F));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(r.nextFloat() * 360F));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(r.nextFloat() * 360F + time * 1.5F));
            float len = (r.nextFloat() * 6F + 3F) * (0.5F + 0.5F * k);
            float w = (r.nextFloat() * 0.9F + 0.35F) * k;
            org.joml.Matrix4f m = pose.last().pose();
            int a = (int) (230 * Math.min(1F, k * 1.4F));
            vc.vertex(m, 0, 0, 0).color(255, 246, 200, a).endVertex();
            vc.vertex(m, -HALF_SQRT_3 * w, len, -0.5F * w).color(255, 170, 40, 0).endVertex();
            vc.vertex(m, 0, len * 1.08F, w).color(255, 210, 90, 0).endVertex();
            vc.vertex(m, HALF_SQRT_3 * w, len, -0.5F * w).color(255, 170, 40, 0).endVertex();
        }
        pose.popPose();
    }

    @Override
    public void render(PaponBossEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        float k = rays(entity, partialTick);
        if (k > 0) drawRays(pose, buffers, k, entity.tickCount + partialTick, entity.getBbHeight() * 0.6F);
        SpeechBubble.render(entity, entity.getSpeech(), 0.4F, pose, buffers, light, entityRenderDispatcher);
    }
}
