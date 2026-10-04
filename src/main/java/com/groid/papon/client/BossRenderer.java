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
    private static final ResourceLocation ASCENDED = PaponMod.id("textures/entity/papon_ascended.png");
    /** Ascended Papon is drawn bigger than the risen one. */
    private static final float ASCENDED_SCALE = 2.0F;
    private static final ResourceLocation AURA = PaponMod.id("textures/entity/papon_aura.png");

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BossModel());
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
                    boolean crimson = boss.isPhaseThree() || boss.getBossState() == BossState.P3_DANCE;
                    getRenderer().reRender(model, pose, buffers, boss, swirl, buffers.getBuffer(swirl), partialTick,
                            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            Math.min(1F, f), Math.min(1F, f * (crimson ? 0.14F : 0.78F)), Math.min(1F, f * (crimson ? 0.16F : 0.32F)), 1F);
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
        // phase three: crimson, flaring as the band crashes in and the fire comes back, then smouldering on him all fight
        if (s == BossState.P3_DANCE) {
            float drop = com.groid.papon.boss.VoiceLines.beat("dance.drop", 42);
            if (t < drop) return 0;
            return (t < drop + 20 ? 1.0F - (t - drop) / 20F * 0.55F : 0.45F) * pulse;
        }
        if (boss.isPhaseThree() && !s.phaseThree()) return 0.32F * pulse;
        return 0;
    }

    @Override
    public ResourceLocation getTextureLocation(PaponBossEntity boss) {
        return boss.isAscended() ? ASCENDED : boss.isBaked() ? BAKED : super.getTextureLocation(boss);
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
        return (!Cinematic.isPovOf(boss) || handInView(boss)) && super.shouldRender(boss, frustum, x, y, z);
    }

    /** Through his own eyes (P2_KNEEL) the only part of him drawn is his left arm, held up in front of them, bleeding. */
    private static boolean handInView(PaponBossEntity boss) {
        int t = boss.ticksInState();
        return Cinematic.isPovOf(boss) && boss.getBossState() == BossState.P2_KNEEL && t >= 56 && t <= 106;
    }

    /** Where his bleeding hand was drawn last (world), per boss: the point-of-view camera looks at it (Cinematic). */
    private static final java.util.Map<Integer, net.minecraft.world.phys.Vec3> HANDS = new java.util.HashMap<>();

    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.phys.Vec3 handPos(PaponBossEntity boss) {
        return HANDS.get(boss.getId());
    }

    @Override
    public void renderFinal(PoseStack pose, PaponBossEntity entity, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                            float partialTick, int light, int overlay, float r, float g, float b, float a) {
        super.renderFinal(pose, entity, model, buffers, buffer, partialTick, light, overlay, r, g, b, a);
        if (!handInView(entity)) {
            HANDS.remove(entity.getId());
            return;
        }
        model.getBone("hand_blood").ifPresent(bone -> {
            var w = bone.getWorldPosition();
            HANDS.put(entity.getId(), new net.minecraft.world.phys.Vec3(w.x, w.y, w.z));
        });
    }

    private static final java.util.Set<String> POV_ARM = java.util.Set.of("arm_l", "arm_l_mesh", "hand_l", "hand_blood", "hand_blood_drop");
    private static boolean povHidden;

    private static void hideForPov(BakedGeoModel model, boolean pov) {
        if (!pov && !povHidden) return;
        povHidden = pov;
        for (software.bernie.geckolib.cache.object.GeoBone bone : model.topLevelBones()) hideForPov(bone, pov);
    }

    private static void hideForPov(software.bernie.geckolib.cache.object.GeoBone bone, boolean pov) {
        bone.setHidden(pov && !POV_ARM.contains(bone.getName()));
        bone.setChildrenHidden(false);                  // setHidden hides the children too: the arm hangs off hidden bones
        for (software.bernie.geckolib.cache.object.GeoBone child : bone.getChildBones()) hideForPov(child, pov);
    }

    @Override
    public RenderType getRenderType(PaponBossEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack pose, PaponBossEntity entity, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        float s = entity.isPassenger() ? DRIVING_SCALE : entity.isAscended() ? ASCENDED_SCALE : entity.isRisen() ? RISEN_SCALE : SCALE;
        this.scaleWidth = s;
        this.scaleHeight = s;
        if (!reRender) hideForPov(model, handInView(entity));
        model.getBone("hand_blood").ifPresent(bone -> bone.setTrackingMatrices(true));
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
        if (s == BossState.P3_ASCEND) {
            // the ascension: light cracking out of him as he groans, bursting with the wings, fading after the roar
            float burst = com.groid.papon.boss.VoiceLines.beat("ascend.burst", 100);
            if (t < burst) return 0.1F + 0.7F * t / burst;
            return Math.max(0.2F, 1F - (t - burst) / 40F);
        }
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

    // Ascended Papon's eyes: their bones' final matrices, caught as they are drawn, for the lightning round them
    private final org.joml.Matrix4f eyeR = new org.joml.Matrix4f(), eyeL = new org.joml.Matrix4f();
    private boolean eyesSeen;

    @Override
    public void renderCubesOfBone(PoseStack pose, software.bernie.geckolib.cache.object.GeoBone bone, VertexConsumer buffer, int light,
                                  int overlay, float r, float g, float b, float a) {
        if (bone.getName().equals("eye_r")) {
            eyeR.set(pose.last().pose());
            eyesSeen = true;
        } else if (bone.getName().equals("eye_l")) {
            eyeL.set(pose.last().pose());
        }
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
    }

    /**
     * Lightning crackling off his white eyes (Ascended Papon): a few jagged arcs from each, re-struck every couple
     * of ticks, white at the core in a violet glow. Drawn in the eye bones' own space, so they follow his face.
     */
    private void eyeLightning(PaponBossEntity boss, float partial, MultiBufferSource buffers) {
        if (!eyesSeen) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        int seed = boss.tickCount / 2;
        for (int e = 0; e < 2; e++) {
            org.joml.Matrix4f m = e == 0 ? eyeR : eyeL;
            float ex = (e == 0 ? 4.5F : -4.5F) / 16F, ey = 19.5F / 16F, ez = -8.3F / 16F;
            net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed * 31L + e * 7L + boss.getId());
            int arcs = 2 + r.nextInt(2);
            for (int a = 0; a < arcs; a++) {
                float x = ex, y = ey, z = ez;
                float dx = (e == 0 ? 1 : -1) * (0.04F + r.nextFloat() * 0.05F), dy = (r.nextFloat() - 0.3F) * 0.06F;
                for (int k = 0; k < 5; k++) {
                    float nx = x + dx + (r.nextFloat() - 0.5F) * 0.03F, ny = y + dy + (r.nextFloat() - 0.5F) * 0.05F, nz = z - 0.01F;
                    ribbon(vc, m, x, y, z, nx, ny, nz, 0.012F * (1 - k / 6F), 200, 170, 255, 210);
                    ribbon(vc, m, x, y, z, nx, ny, nz, 0.005F, 255, 255, 255, 255);
                    x = nx; y = ny; z = nz;
                }
            }
        }
        eyesSeen = false;
    }

    private static void ribbon(VertexConsumer vc, org.joml.Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float w,
                               int cr, int cg, int cb, int ca) {
        org.joml.Vector4f a = m.transform(new org.joml.Vector4f(x0, y0 - w, z0, 1));
        org.joml.Vector4f b = m.transform(new org.joml.Vector4f(x0, y0 + w, z0, 1));
        org.joml.Vector4f c = m.transform(new org.joml.Vector4f(x1, y1 + w, z1, 1));
        org.joml.Vector4f d = m.transform(new org.joml.Vector4f(x1, y1 - w, z1, 1));
        vc.vertex(a.x, a.y, a.z).color(cr, cg, cb, ca).endVertex();
        vc.vertex(b.x, b.y, b.z).color(cr, cg, cb, ca).endVertex();
        vc.vertex(c.x, c.y, c.z).color(cr, cg, cb, ca).endVertex();
        vc.vertex(d.x, d.y, d.z).color(cr, cg, cb, ca).endVertex();
    }

    @Override
    public void render(PaponBossEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float spot = spot(entity, partialTick);
        super.render(entity, yaw, partialTick, pose, buffers, spot > 0.5F ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : keyLight(entity, light));
        if (spot > 0) drawSpotlight(entity, pose, buffers, spot, partialTick);
        if (entity.isAscended()) eyeLightning(entity, partialTick, buffers);
        else eyesSeen = false;
        float k = rays(entity, partialTick);
        if (k > 0) drawRays(pose, buffers, k, entity.tickCount + partialTick, entity.getBbHeight() * 0.6F);
        SpeechBubble.render(entity, entity.getSpeech(), 0.4F, pose, buffers, light, entityRenderDispatcher);
    }

    /**
     * How bright his spotlight is (phase three, in his coliseum): it slams on in the blackout (a stutter as the
     * lamp catches), and stays on him for the rest of the fight.
     */
    static float spot(PaponBossEntity b, float partial) {
        if (!b.inColiseum() || !b.spotlit()) return 0;
        if (b.getBossState() == BossState.P3_BLACKOUT) {
            int t = b.ticksInState();
            if (t < 30) return 0;
            return t == 31 || t == 33 ? 0.25F : 1F;           // the lamp catching
        }
        return 1F;
    }

    /**
     * The spotlight: a beam straight down on him out of the dark from high over the sand (two cones, a soft wide
     * one round a brighter core, brightest where it meets the ground), the pool of light on the sand under him,
     * and dust turning in it. Additive, so it only shows against the dark.
     */
    private static void drawSpotlight(PaponBossEntity b, PoseStack pose, MultiBufferSource buffers, float k, float partial) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 at = b.getPosition(partial);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(at.add(0, 0.5, 0), at.add(0, -30, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        float ground = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 0F : (float) (hit.getLocation().y - at.y) + 0.03F;
        float top = ground + 30F;
        float scale = b.isRisen() ? 1.15F : 1F;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        org.joml.Matrix4f m = pose.last().pose();
        float flick = 0.94F + 0.06F * net.minecraft.util.Mth.sin((b.tickCount + partial) * 0.9F);
        // seen from inside the beam (or close to it) it would wash everything behind it out: fade it
        net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double axis = Math.hypot(cam.x - at.x, cam.z - at.z);
        float inside = (float) net.minecraft.util.Mth.clamp((axis - 2.5) / 6.0, 0.2, 1.0);
        k *= inside;
        cone(vc, m, top, ground, 0.5F, 3.4F * scale, 255, 240, 214, (int) (26 * k * flick));
        cone(vc, m, top, ground, 0.25F, 1.9F * scale, 255, 248, 230, (int) (38 * k * flick));
        // the pool on the sand
        int n = 32;
        float r = 3.6F * scale;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (Math.PI * 2 * i / n), a1 = (float) (Math.PI * 2 * (i + 1) / n);
            int c = (int) (150 * k * flick);
            vc.vertex(m, 0, ground, 0).color(255, 246, 225, c).endVertex();
            vc.vertex(m, net.minecraft.util.Mth.cos(a0) * r * 0.55F, ground, net.minecraft.util.Mth.sin(a0) * r * 0.55F).color(255, 240, 214, c * 2 / 3).endVertex();
            vc.vertex(m, net.minecraft.util.Mth.cos(a1) * r * 0.55F, ground, net.minecraft.util.Mth.sin(a1) * r * 0.55F).color(255, 240, 214, c * 2 / 3).endVertex();
            vc.vertex(m, 0, ground, 0).color(255, 246, 225, c).endVertex();
            vc.vertex(m, net.minecraft.util.Mth.cos(a0) * r * 0.55F, ground, net.minecraft.util.Mth.sin(a0) * r * 0.55F).color(255, 240, 214, c * 2 / 3).endVertex();
            vc.vertex(m, net.minecraft.util.Mth.cos(a0) * r, ground, net.minecraft.util.Mth.sin(a0) * r).color(255, 230, 200, 0).endVertex();
            vc.vertex(m, net.minecraft.util.Mth.cos(a1) * r, ground, net.minecraft.util.Mth.sin(a1) * r).color(255, 230, 200, 0).endVertex();
            vc.vertex(m, net.minecraft.util.Mth.cos(a1) * r * 0.55F, ground, net.minecraft.util.Mth.sin(a1) * r * 0.55F).color(255, 240, 214, c * 2 / 3).endVertex();
        }
    }

    /** One cone of the beam: from a small ring at the lamp down to {@code r1} at the ground, fading out toward the lamp. Both sides. */
    private static void cone(VertexConsumer vc, org.joml.Matrix4f m, float top, float bottom, float r0, float r1, int cr, int cg, int cb, int alpha) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (Math.PI * 2 * i / n), a1 = (float) (Math.PI * 2 * (i + 1) / n);
            float c0 = net.minecraft.util.Mth.cos(a0), s0 = net.minecraft.util.Mth.sin(a0), c1 = net.minecraft.util.Mth.cos(a1), s1 = net.minecraft.util.Mth.sin(a1);
            float mid = bottom + (top - bottom) * 0.35F;
            float rm = r1 + (r0 - r1) * 0.35F;
            for (int side = 0; side < 2; side++) {
                float[][] ring = side == 0 ? new float[][]{{c0, s0}, {c1, s1}} : new float[][]{{c1, s1}, {c0, s0}};
                // the lower part, bright at the ground
                vc.vertex(m, ring[0][0] * r1, bottom, ring[0][1] * r1).color(cr, cg, cb, alpha).endVertex();
                vc.vertex(m, ring[1][0] * r1, bottom, ring[1][1] * r1).color(cr, cg, cb, alpha).endVertex();
                vc.vertex(m, ring[1][0] * rm, mid, ring[1][1] * rm).color(cr, cg, cb, alpha * 3 / 5).endVertex();
                vc.vertex(m, ring[0][0] * rm, mid, ring[0][1] * rm).color(cr, cg, cb, alpha * 3 / 5).endVertex();
                // the upper part, fading into the dark at the lamp
                vc.vertex(m, ring[0][0] * rm, mid, ring[0][1] * rm).color(cr, cg, cb, alpha * 3 / 5).endVertex();
                vc.vertex(m, ring[1][0] * rm, mid, ring[1][1] * rm).color(cr, cg, cb, alpha * 3 / 5).endVertex();
                vc.vertex(m, ring[1][0] * r0, top, ring[1][1] * r0).color(cr, cg, cb, alpha / 6).endVertex();
                vc.vertex(m, ring[0][0] * r0, top, ring[0][1] * r0).color(cr, cg, cb, alpha / 6).endVertex();
            }
        }
    }

    /**
     * A key light on him through the phase-two cutscene on the ground: his coliseum is going up right over
     * the fight, and its shadow would leave his face (and his lines) in the dark.
     */
    private static int keyLight(PaponBossEntity b, int light) {
        if (!b.isPhaseTwo() || b.inColiseum() || !b.getBossState().cutscene()) return light;
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(13, net.minecraft.client.renderer.LightTexture.block(light)),
                net.minecraft.client.renderer.LightTexture.sky(light));
    }
}
