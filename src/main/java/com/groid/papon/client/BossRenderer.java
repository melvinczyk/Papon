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
    /** Phase four's: the same flow in the dead's blue (tools/gen_muertos.py fx; the gold one tinted blue came out green). */
    private static final ResourceLocation AURA_DEAD = PaponMod.id("textures/entity/papon_aura_dead.png");
    /** The Final Guardian's: the souls' jade green (gen_muertos.guardian_aura). */
    private static final ResourceLocation AURA_GUARDIAN = PaponMod.id("textures/entity/papon_aura_guardian.png");
    private static final ResourceLocation MUERTOS = PaponMod.id("textures/entity/papon_muertos.png");
    /** El Rey de los Muertos is bigger again. */
    private static final float MUERTOS_SCALE = 2.25F;

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
                boolean dead = boss.isMuertos() || boss.glow() > 0;
                RenderType swirl = RenderType.energySwirl(dead ? AURA_GUARDIAN : AURA, (time * 0.013F) % 1F, (time * -0.021F) % 1F);
                for (float grow : boss.isMuertos() && !boss.getBossState().phaseFour() ? new float[]{1.12F} : new float[]{1.07F, 1.16F}) {
                    pose.pushPose();
                    pose.translate(0, 0.9, 0);
                    pose.scale(grow, grow, grow);
                    pose.translate(0, -0.9, 0);
                    float f = k * (grow > 1.1F ? 0.6F : 1F);
                    boolean crimson = boss.isPhaseThree() || boss.getBossState() == BossState.P3_DANCE;
                    float cr = 1F, cg = dead ? 1F : crimson ? 0.14F : 0.78F, cb = dead ? 1F : crimson ? 0.16F : 0.32F;
                    getRenderer().reRender(model, pose, buffers, boss, swirl, buffers.getBuffer(swirl), partialTick,
                            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            Math.min(1F, f * cr), Math.min(1F, f * cg), Math.min(1F, f * cb), 1F);
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
        // phase four: the souls' green light gathering in him, flaring through the transformation, burning on after
        if (s == BossState.P4_SOULS) return 0.9F * boss.glow() * pulse;
        if (s == BossState.P4_HALFBAKED) {
            float burst = com.groid.papon.boss.VoiceLines.beat("p4.burst", 106), clear = com.groid.papon.boss.VoiceLines.beat("p4.clear", 162);
            if (t < burst) return 0.75F * pulse;
            if (t < clear) return Math.min(1.0F, 0.75F + (t - burst) / 40F) * pulse;
            return Math.max(0.25F, 0.9F - (t - clear) / 30F) * pulse;
        }
        // (2026-10-04, the user: "we can't see his texture... his green aura is masking it") a thin glow at his edges only
        if (boss.isMuertos()) {
            // (2026-10-05) losing control: in his rage and El Juicio Final it flares; at the end of the world it stutters,
            // surging and dropping (still thin enough to see him through)
            if (s == BossState.P4_RAGE || s == BossState.P4_JUICIO) return (0.3F + 0.12F * net.minecraft.util.Mth.sin(t * 0.9F) + 0.08F * net.minecraft.util.Mth.sin(t * 2.3F)) * pulse;
            float base = (s.muertosMove() ? 0.14F : 0.08F) * pulse;
            if (boss.p4StageShown() >= 3) base *= 1.25F + 0.55F * Math.max(0F, net.minecraft.util.Mth.sin(t * 0.37F) * net.minecraft.util.Mth.sin(t * 1.13F));
            else if (boss.p4StageShown() == 2) base *= 1.15F;
            return base;
        }
        if (boss.isPhaseThree() && !s.phaseThree()) return 0.32F * pulse;
        return 0;
    }

    @Override
    public ResourceLocation getTextureLocation(PaponBossEntity boss) {
        return boss.isMuertos() ? MUERTOS : boss.isAscended() ? ASCENDED : boss.isBaked() ? BAKED : super.getTextureLocation(boss);
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

    /** Bones whose world positions are kept as they are drawn, for the cutscene's camera (phase four: his fists, the scar). */
    private static final String[] TRACKED = {"hand_r", "hand_l", "scar"};
    private static final java.util.Map<Integer, java.util.Map<String, net.minecraft.world.phys.Vec3>> BONES = new java.util.HashMap<>();

    /** Where one of the TRACKED bones was drawn last (world), or null. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.phys.Vec3 bonePos(PaponBossEntity boss, String name) {
        java.util.Map<String, net.minecraft.world.phys.Vec3> m = BONES.get(boss.getId());
        net.minecraft.world.phys.Vec3 p = m == null ? null : m.get(name);
        return p == null ? null : scaled(boss, p);
    }

    /** Between his eyes, as drawn last (or a guess from his box). */
    public static net.minecraft.world.phys.Vec3 facePos(PaponBossEntity boss) {
        net.minecraft.world.phys.Vec3[] e = EYES.get(boss.getId());
        if (e != null) return scaled(boss, e[0].add(e[1]).scale(0.5));
        return boss.position().add(0, boss.getBbHeight() * 0.62, 0);
    }

    /** The tracked bone matrices leave out the render scale (it is applied after them): put it back, about his feet. */
    private static net.minecraft.world.phys.Vec3 scaled(PaponBossEntity boss, net.minecraft.world.phys.Vec3 p) {
        float s = boss.isPassenger() ? DRIVING_SCALE : boss.isMuertos() ? MUERTOS_SCALE : boss.isAscended() ? ASCENDED_SCALE
                : boss.isRisen() ? RISEN_SCALE : SCALE;
        net.minecraft.world.phys.Vec3 o = boss.position();
        return o.add(p.subtract(o).scale(s));
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
        var er = model.getBone("eye_r");
        var el = model.getBone("eye_l");
        if (er.isPresent() && el.isPresent()) {
            var wr = er.get().getWorldPosition();
            var wl = el.get().getWorldPosition();
            EYES.put(entity.getId(), new net.minecraft.world.phys.Vec3[]{new net.minecraft.world.phys.Vec3(wr.x, wr.y, wr.z),
                    new net.minecraft.world.phys.Vec3(wl.x, wl.y, wl.z)});
        }
        java.util.Map<String, net.minecraft.world.phys.Vec3> at = BONES.computeIfAbsent(entity.getId(), k -> new java.util.HashMap<>());
        for (String n : TRACKED) {
            model.getBone(n).ifPresent(bone -> {
                var w = bone.getWorldPosition();
                at.put(n, new net.minecraft.world.phys.Vec3(w.x, w.y, w.z));
            });
        }
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
        float s = entity.isPassenger() ? DRIVING_SCALE : entity.isMuertos() ? MUERTOS_SCALE : entity.isAscended() ? ASCENDED_SCALE
                : entity.isRisen() ? RISEN_SCALE : SCALE;
        this.scaleWidth = s;
        this.scaleHeight = s;
        if (!reRender) hideForPov(model, handInView(entity));
        capturing = !reRender;
        model.getBone("hand_blood").ifPresent(bone -> bone.setTrackingMatrices(true));
        model.getBone("eye_r").ifPresent(bone -> bone.setTrackingMatrices(true));
        for (String n : TRACKED) model.getBone(n).ifPresent(bone -> bone.setTrackingMatrices(true));
        model.getBone("eye_l").ifPresent(bone -> bone.setTrackingMatrices(true));
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
        if (entity.isArmored()) {
            // mid-move and untouchable: a golden shimmer running through him
            float k = 0.5F + 0.5F * net.minecraft.util.Mth.sin((entity.tickCount + partialTick) * 0.9F);
            if (entity.isMuertos()) return software.bernie.geckolib.core.object.Color.ofRGBA(1.0F, 0.94F + 0.06F * k, 0.82F + 0.12F * k, 1F);
            return software.bernie.geckolib.core.object.Color.ofRGBA(1.0F, 0.86F + 0.1F * k, 0.45F + 0.2F * k, 1F);
        }
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
    protected float getDeathMaxRotation(PaponBossEntity entity) { return 0; }

    /** How strong the light rays bursting out of him are: when he bakes golden, and through the scream. */
    private static float rays(PaponBossEntity boss, float partialTick) {
        BossState s = boss.getBossState();
        float t = boss.ticksInState() + partialTick;
        if (s == BossState.P2_SEASON && t > 72) return Math.max(0, 1F - Math.abs(t - 82) / 12F);
        if (s == BossState.P2_SCREAM && t > 12) return t < 22 ? (t - 12) / 10F : t < 50 ? 1F : Math.max(0, 1F - (t - 50) / 14F);
        if (s == BossState.P3_NOVA) {
            // the supernova: a star growing in him, white at the blast, gone after
            if (t < 20) return t / 40F;
            if (t < 130) return 0.5F + 0.5F * (t - 20) / 110F;
            return Math.max(0, 1F - (t - 130) / 16F);
        }
        if (s == BossState.P3_SOLAR && t > 86 && t < 140) return 0.45F;
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
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
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
    private boolean eyesSeen, eyeRSeen, eyeLSeen, capturing;

    /** Where his eyes were drawn last (world), per boss, [right, left]: his eye beams start there (client/BeamRenderer). */
    private static final java.util.Map<Integer, net.minecraft.world.phys.Vec3[]> EYES = new java.util.HashMap<>();

    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.phys.Vec3 eyePos(PaponBossEntity boss, int side) {
        net.minecraft.world.phys.Vec3[] e = EYES.get(boss.getId());
        // scaled like every tracked bone (2026-10-06: left raw, his eye beams came out of his chest, not his eyes)
        return e == null ? null : scaled(boss, e[side >= 0 ? 0 : 1]);
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, software.bernie.geckolib.cache.object.GeoBone bone, VertexConsumer buffer, int light,
                                  int overlay, float r, float g, float b, float a) {
        // only the main pass: a layer's re-render (the glowmask) draws the bones again under another pose, and caught there
        // the eyes' matrices put the lightning somewhere else (one eye's arcs happened to land on the other: "only the left")
        if (!capturing) {
            // a re-render pass: leave the main pass's matrices alone
        } else if (bone.getName().equals("eye_r")) {
            eyeR.set(pose.last().pose());
            eyeRSeen = true;
        } else if (bone.getName().equals("eye_l")) {
            eyeL.set(pose.last().pose());
            eyeLSeen = true;
        }
        eyesSeen = eyeRSeen || eyeLSeen;
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
    }

    /**
     * Lightning crackling round both his white eyes (Ascended Papon): an aura of jagged arcs all the way round each eye
     * (the user: it was only the left), re-struck every couple of ticks, white at the core in a violet glow, over a soft
     * glow. Drawn in each eye bone's own space (its cube's middle: GeckoLib mirrors X, so the right eye is at +4.5), so they
     * follow his face.
     */
    private void eyeLightning(PaponBossEntity boss, float partial, MultiBufferSource buffers) {
        if (!eyesSeen) return;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        int seed = boss.tickCount / 2;
        for (int e = 0; e < 2; e++) {
            if (e == 0 ? !eyeRSeen : !eyeLSeen) continue;
            org.joml.Matrix4f m = e == 0 ? eyeR : eyeL;
            float ex = (e == 0 ? 4.5F : -4.5F) / 16F, ey = 19.5F / 16F, ez = -8.35F / 16F;
            // the glow under the arcs
            for (int i = 0; i < 12; i++) {
                double a0 = Math.PI * 2 * i / 12, a1 = Math.PI * 2 * (i + 1) / 12;
                float r = 0.16F;
                org.joml.Vector4f c = m.transform(new org.joml.Vector4f(ex, ey, ez, 1));
                org.joml.Vector4f p0 = m.transform(new org.joml.Vector4f(ex + (float) Math.cos(a0) * r, ey + (float) Math.sin(a0) * r * 0.7F, ez, 1));
                org.joml.Vector4f p1 = m.transform(new org.joml.Vector4f(ex + (float) Math.cos(a1) * r, ey + (float) Math.sin(a1) * r * 0.7F, ez, 1));
                vc.vertex(c.x, c.y, c.z).color(220, 200, 255, 150).endVertex();
                vc.vertex(p0.x, p0.y, p0.z).color(160, 120, 255, 0).endVertex();
                vc.vertex(p1.x, p1.y, p1.z).color(160, 120, 255, 0).endVertex();
                vc.vertex(c.x, c.y, c.z).color(220, 200, 255, 150).endVertex();
            }
            net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed * 31L + e * 7L + boss.getId());
            int arcs = 5 + r.nextInt(3);
            for (int a = 0; a < arcs; a++) {
                // all the way round the eye, each arc off in its own direction
                double ang = Math.PI * 2 * (a + r.nextFloat() * 0.6F) / arcs;
                float x = ex + (float) Math.cos(ang) * 0.06F, y = ey + (float) Math.sin(ang) * 0.04F, z = ez;
                float dx = (float) Math.cos(ang) * (0.03F + r.nextFloat() * 0.03F), dy = (float) Math.sin(ang) * (0.025F + r.nextFloat() * 0.025F);
                for (int k = 0; k < 4; k++) {
                    float nx = x + dx + (r.nextFloat() - 0.5F) * 0.035F, ny = y + dy + (r.nextFloat() - 0.5F) * 0.035F, nz = z - 0.008F;
                    ribbon(vc, m, x, y, z, nx, ny, nz, 0.012F * (1 - k / 5F), 200, 170, 255, 210);
                    ribbon(vc, m, x, y, z, nx, ny, nz, 0.005F, 255, 255, 255, 255);
                    x = nx;
                    y = ny;
                    z = nz;
                }
            }
        }
        eyesSeen = eyeRSeen = eyeLSeen = false;
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
        if (spot > 0 && SPOTS.size() < 4) SPOTS.add(new Spot(entity, spot));        // drawn after the world: drawSpotlights
        if (entity.isAscended() && !entity.isMuertos()) eyeLightning(entity, partialTick, buffers);
        else eyesSeen = false;
        if (entity.spiritProp() == 4) ignisHead(entity, partialTick, pose, buffers);
        float k = rays(entity, partialTick);
        if (k > 0) drawRays(pose, buffers, k, entity.tickCount + partialTick, entity.getBbHeight() * 0.6F);
        if (entity.getBossState() == BossState.P3_SOLAR) drawSunStone(entity, pose, buffers, partialTick);
        if (entity.getBossState() == BossState.P3_NOVA) drawStar(entity, pose, buffers, partialTick);
        SpeechBubble.render(entity, entity.getSpeech(), 0.4F, pose, buffers, light, entityRenderDispatcher);
    }

    /**
     * Ignis's head in his right fist (phase four, spiritProp 4): hanging from his grip by its crown, upright, its face the way
     * he faces (to you), turning a little as he shows it off. Drawn where the fist was drawn (BossRenderer.bonePos).
     */
    private static void ignisHead(PaponBossEntity b, float partial, PoseStack pose, MultiBufferSource buffers) {
        net.minecraft.world.phys.Vec3 hand = bonePos(b, "hand_r");
        if (hand == null) return;
        net.minecraft.world.phys.Vec3 at = hand.subtract(b.getPosition(partial));
        float s = 1.25F;
        pose.pushPose();
        pose.translate(at.x, at.y - 0.85 * s, at.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - b.yBodyRot + 10F * net.minecraft.util.Mth.sin((b.tickCount + partial) * 0.07F)));
        pose.scale(s, s, s);
        pose.translate(-0.5, 0, -0.5);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                new net.minecraft.world.item.ItemStack(com.groid.papon.registry.ModItems.FX_IGNIS_HEAD.get()), net.minecraft.world.item.ItemDisplayContext.NONE,
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, b.level(), b.getId());
        pose.popPose();
    }

    private static final ResourceLocation SUN_STONE = PaponMod.id("textures/entity/sun_stone.png");

    /**
     * El Sol Azteca: the sun stone (tools/gen_extras.sun_stone: a potato Piedra del Sol, his face in the middle) wheeling up
     * behind his back, turning, growing in (1.5-4.5 s), blazing while it fires (4.5-6.8 s), gone after. Where the server
     * puts the beam's origin (PaponBossEntity.sunPos).
     */
    private static void drawSunStone(PaponBossEntity b, PoseStack pose, MultiBufferSource buffers, float partial) {
        float t = b.ticksInState() + partial;
        float grow = net.minecraft.util.Mth.clamp((t - 24) / 50F, 0, 1);
        float fade = net.minecraft.util.Mth.clamp((156 - t) / 16F, 0, 1);
        float k = grow * fade;
        if (k <= 0) return;
        float size = 4.2F * (0.3F + 0.7F * (1 - (1 - grow) * (1 - grow)));
        float yaw = net.minecraft.util.Mth.rotLerp(partial, b.yBodyRotO, b.yBodyRot);
        pose.pushPose();
        pose.translate(0, b.getBbHeight() * 0.8, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, 0, -3.2);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(t * (t > 90 && t < 136 ? 4F : 1.2F)));
        boolean firing = t > 90 && t < 136;
        int a = (int) (255 * k);
        int glow = firing ? 255 : (int) (170 + 85 * Math.sin(t * 0.3));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SUN_STONE));
        org.joml.Matrix4f m = pose.last().pose();
        org.joml.Matrix3f n = pose.last().normal();
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1 : -1;
            vc.vertex(m, -size * s, -size, 0).color(glow, glow, glow, a).uv(side == 0 ? 0 : 1, 1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).normal(n, 0, 0, s).endVertex();
            vc.vertex(m, size * s, -size, 0).color(glow, glow, glow, a).uv(side == 0 ? 1 : 0, 1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).normal(n, 0, 0, s).endVertex();
            vc.vertex(m, size * s, size, 0).color(glow, glow, glow, a).uv(side == 0 ? 1 : 0, 0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).normal(n, 0, 0, s).endVertex();
            vc.vertex(m, -size * s, size, 0).color(glow, glow, glow, a).uv(side == 0 ? 0 : 1, 0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).normal(n, 0, 0, s).endVertex();
        }
        // and the corona round it
        drawRays(pose, buffers, (firing ? 0.9F : 0.4F) * k, t, 0);
        pose.popPose();
    }

    /** La Supernova: a white-gold sphere of light swelling round him (additive, layered shells), shaking as it nears the blast. */
    private static void drawStar(PaponBossEntity b, PoseStack pose, MultiBufferSource buffers, float partial) {
        float t = b.ticksInState() + partial;
        float k = t < 130 ? net.minecraft.util.Mth.clamp((t - 10) / 120F, 0, 1) : Math.max(0, 1F - (t - 130) / 6F) * 1.6F;
        if (k <= 0) return;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        pose.pushPose();
        pose.translate(0, b.getBbHeight() * 0.5, 0);
        if (t > 100 && t < 130) {
            var r = b.getRandom();
            pose.translate(r.nextGaussian() * 0.08, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08);
        }
        org.joml.Matrix4f m = pose.last().pose();
        for (int shell = 0; shell < 3; shell++) {
            float rad = (1.2F + 3.2F * k) * (1F - shell * 0.22F);
            int al = (int) (Math.min(1F, k) * (40 + shell * 35));
            sphere(vc, m, rad, 255, 236 - shell * 20, 180 - shell * 50, al);
        }
        pose.popPose();
    }

    private static void sphere(VertexConsumer vc, org.joml.Matrix4f m, float r, int cr, int cg, int cb, int a) {
        int lat = 8, lon = 14;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                float[][] v = {
                        {(float) (Math.cos(t0) * Math.cos(p0)), (float) Math.sin(t0), (float) (Math.cos(t0) * Math.sin(p0))},
                        {(float) (Math.cos(t0) * Math.cos(p1)), (float) Math.sin(t0), (float) (Math.cos(t0) * Math.sin(p1))},
                        {(float) (Math.cos(t1) * Math.cos(p1)), (float) Math.sin(t1), (float) (Math.cos(t1) * Math.sin(p1))},
                        {(float) (Math.cos(t1) * Math.cos(p0)), (float) Math.sin(t1), (float) (Math.cos(t1) * Math.sin(p0))}};
                for (int q = 0; q < 2; q++) {
                    for (int c = 0; c < 4; c++) {
                        float[] p = v[q == 0 ? c : 3 - c];
                        vc.vertex(m, p[0] * r, p[1] * r, p[2] * r).color(cr, cg, cb, a).endVertex();
                    }
                }
            }
        }
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
    private record Spot(PaponBossEntity b, float k) {}

    /** The spotlights asked for this frame (render), drawn once everything else is (ClientEvents, AFTER_PARTICLES). */
    private static final java.util.List<Spot> SPOTS = new java.util.ArrayList<>();

    /**
     * (2026-10-05) The beam is drawn last, over the finished picture: drawn with the boss it went down before the stands, so the
     * fans behind it were missing (its depth) or drawn over it. Now the stands, the band and the sand are all there behind it,
     * and its light lies over them.
     */
    static void drawSpotlights(PoseStack pose, net.minecraft.world.phys.Vec3 cam, float partial) {
        if (SPOTS.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        for (Spot s : SPOTS) {
            if (!s.b().isAlive()) continue;
            net.minecraft.world.phys.Vec3 at = s.b().getPosition(partial);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            drawSpotlight(s.b(), pose, buffers, s.k(), partial);
            pose.popPose();
        }
        buffers.endBatch(PaponRenderTypes.glow());
        SPOTS.clear();
    }

    private static void drawSpotlight(PaponBossEntity b, PoseStack pose, MultiBufferSource buffers, float k, float partial) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 at = b.getPosition(partial);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(at.add(0, 0.5, 0), at.add(0, -30, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        float ground = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 0F : (float) (hit.getLocation().y - at.y) + 0.03F;
        float top = ground + 30F;
        float scale = b.isRisen() ? 1.15F : 1F;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
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
        // the Final Guardian in his dark arena: lit as he is, his own colours (NightLight leaves full block light white)
        if (b.isMuertos()) return net.minecraft.client.renderer.LightTexture.pack(15, net.minecraft.client.renderer.LightTexture.sky(light));
        if (!b.isPhaseTwo() || b.inColiseum() || !b.getBossState().cutscene()) return light;
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(13, net.minecraft.client.renderer.LightTexture.block(light)),
                net.minecraft.client.renderer.LightTexture.sky(light));
    }
}
