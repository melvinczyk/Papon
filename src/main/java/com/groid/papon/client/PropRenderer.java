package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossProp;
import com.groid.papon.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws {@link BossProp}s: each kind is one of the item models from tools/gen_props.py, turned the way
 * that kind moves (cleavers spin flat, fries point where they fly and shiver while they aim, tots and
 * potatoes tumble, the burrito stands round whoever is in it, the sprouts push up out of the ground). The
 * mash wave is a ring of wall drawn here, at the radius the server gives it.
 */
public class PropRenderer extends EntityRenderer<BossProp> {
    private static final ResourceLocation MASH = PaponMod.id("textures/entity/mash_wave.png");
    private static final ResourceLocation SALSA = PaponMod.id("textures/entity/salsa_wave.png");

    public PropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(BossProp p, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = p.tickCount + partialTick;
        BossProp.Kind k = p.kind();
        if (k == BossProp.Kind.MASH) {
            drawMash(p, pose, buffers, light);
            return;
        }
        if (k == BossProp.Kind.MASHER) {
            drawMasher(p, pose, buffers, light, age);
            return;
        }
        if (k == BossProp.Kind.SALSA_RING) {
            drawRing(p, pose, buffers, light, SALSA, 1.8F);
            return;
        }
        if (k == BossProp.Kind.BAND_WAVE) {
            drawBandWave(p, pose, buffers, age);
            return;
        }
        if (k == BossProp.Kind.SALSA_WAVE) {
            drawSalsaWave(p, pose, buffers, light, age);
            return;
        }
        if (k == BossProp.Kind.PAPEL) {
            drawPapel(p, pose, buffers, age);
            return;
        }
        if (k == BossProp.Kind.SOUL) {
            drawSoul(p, pose, buffers, age, partialTick);
            return;
        }
        switch (k) {
            case DEBRIS -> {
                drawDebris(p, pose, buffers, age);
                return;
            }
            case SKULL_LINE -> {
                drawSkullLine(p, pose, buffers, age, partialTick);
                return;
            }
            case SKULL_RING -> {
                drawSkullRing(p, pose, buffers, age, partialTick);
                return;
            }
            case ROSE -> {
                drawRose(p, pose, buffers, age);
                return;
            }
            case THORNS -> {
                drawThorns(p, pose, buffers, age);
                return;
            }
            case VINE -> {
                drawVine(p, pose, buffers, age, partialTick);
                return;
            }
            case PETAL_RING -> {
                drawPetalRing(p, pose, buffers, age, partialTick);
                return;
            }
            case CASTILLO -> {
                drawCastillo(p, pose, buffers, age);
                return;
            }
            default -> {}
        }
        pose.pushPose();
        int lit = light;
        ItemStack stack;
        switch (k) {
            case CLEAVER -> {
                stack = new ItemStack(ModItems.FX_CLEAVER.get());
                pose.translate(0, 0.2, 0);
                pose.mulPose(Axis.YP.rotationDegrees(age * 42F));
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.scale(1.8F, 1.8F, 1.8F);
            }
            case CLEAVER_DART -> {
                // along its flight, turning end over end (still while it hangs aimed)
                stack = new ItemStack(ModItems.FX_CLEAVER.get());
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot() + 90F));
                pose.mulPose(Axis.ZP.rotationDegrees(-p.getXRot() + (p.delay() > 0 ? 45F : -age * 50F)));
                pose.scale(1.9F, 1.9F, 1.9F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case FRY -> {
                stack = new ItemStack(ModItems.FX_FRY.get());
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(p.getXRot() + 90F));
                if (p.delay() > 0) pose.translate(Mth.sin(age * 2.7F) * 0.04, 0, Mth.cos(age * 3.1F) * 0.04);
                pose.scale(2.4F, 2.4F, 2.4F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case TOT, TOT_SMALL -> {
                stack = new ItemStack(ModItems.FX_TOT.get());
                float s = k == BossProp.Kind.TOT ? 1.6F : 0.8F;
                pose.translate(0, 0.25 * s, 0);
                pose.mulPose(Axis.XP.rotationDegrees(age * 23F));
                pose.mulPose(Axis.ZP.rotationDegrees(age * 11F));
                pose.scale(s, s, s);
            }
            case METEOR -> {
                stack = new ItemStack(ModItems.FX_METEOR.get());
                pose.translate(0, 0.6, 0);
                pose.mulPose(Axis.XP.rotationDegrees(age * 9F));
                pose.mulPose(Axis.YP.rotationDegrees(age * 5F));
                pose.scale(2.6F, 2.6F, 2.6F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case BURRITO -> {
                stack = new ItemStack(ModItems.FX_BURRITO.get());
                float in = Math.min(1F, age / 4F);
                float squish = 1F + 0.08F * Mth.sin(age * 0.9F) * (1F - Math.min(1F, age / 20F));
                pose.translate(0, 1.0 * in, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                pose.scale(2.2F * squish, 2.0F * in, 2.2F * squish);
            }
            case SPROUTS -> {
                stack = new ItemStack(ModItems.FX_SPROUTS.get());
                float up = Mth.clamp(age / 5F, 0F, 1F);
                pose.translate(0, 0.9 * up, 0);
                pose.scale(1.8F, 1.8F * up, 1.8F);
            }
            case GUITAR -> {
                // the requinto, end over end along its arc, catching the spotlight
                stack = new ItemStack(ModItems.FX_GUITAR.get());
                float yawT = (float) (Mth.atan2(p.getDeltaMovement().x, p.getDeltaMovement().z) * Mth.RAD_TO_DEG);
                pose.translate(0, 0.3, 0);
                pose.mulPose(Axis.YP.rotationDegrees(yawT));
                pose.mulPose(Axis.XP.rotationDegrees(age * 38F));
                pose.scale(1.7F, 1.7F, 1.7F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case PINATA -> {
                // the piñata, swinging gently on its rope (the rope up out of sight is drawn as a line); swung like a wrecking
                // ball (La Piñata, reworked) it hangs along its rope, tilted by the swing (pitch = the angle, yaw = the plane)
                stack = new ItemStack(ModItems.FX_PINATA.get());
                float swing = p.getViewXRot(partialTick);
                if (Math.abs(swing) > 0.5F) {
                    double py = Math.toRadians(p.getViewYRot(partialTick));
                    // the swing's plane is the yaw's facing f; the tilt is about f x up, so the rope leans back toward its anchor
                    float kx = (float) -Math.cos(py), kz = (float) -Math.sin(py);
                    pose.translate(0, 5.7, 0);
                    pose.mulPose(new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(swing), kx, 0, kz));
                    pose.translate(0, -5.7, 0);
                }
                pose.translate(0, 1.6, 0);
                pose.mulPose(Axis.YP.rotationDegrees(age * 1.5F));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.12F) * 8F));
                pose.translate(0, -1.6, 0);
                pose.scale(6.0F, 6.0F, 6.0F);
                pose.translate(0, 0.45, 0);
                drawRope(pose, buffers, 30F / 6F, light);
            }
            case CANDY -> {
                stack = new ItemStack(ModItems.FX_CANDY.get());
                pose.translate(0, 0.3, 0);
                pose.mulPose(Axis.YP.rotationDegrees(age * 31F));
                pose.mulPose(Axis.XP.rotationDegrees(age * 23F));
                pose.scale(1.6F, 1.6F, 1.6F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case PRODUCE -> {
                // a tomato (a beetroot will do), a potato, a carrot, a baked potato, tumbling
                net.minecraft.world.item.Item[] what = {net.minecraft.world.item.Items.BEETROOT, net.minecraft.world.item.Items.POTATO,
                        net.minecraft.world.item.Items.CARROT, net.minecraft.world.item.Items.BAKED_POTATO};
                stack = new ItemStack(what[Math.floorMod(p.getId(), what.length)]);
                pose.mulPose(Axis.XP.rotationDegrees(age * 23F));
                pose.mulPose(Axis.YP.rotationDegrees(age * 17F));
                pose.scale(1.1F, 1.1F, 1.1F);
            }
            case ROLLER -> {
                // ¡Estampida!: a potato as tall as two of you, rolling along its lane (rocking while it waits)
                stack = new ItemStack(ModItems.FX_GIANT_SPUD.get());
                pose.translate(0, 1.6, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                if (p.delay() > 0) pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 1.3F) * 6F));
                else pose.mulPose(Axis.XP.rotationDegrees(age * 21F));
                pose.scale(3.4F, 3.4F, 3.4F);
            }
            case GIANT_SPUD -> {
                // El Gran Meteoro: swells in over 2 s to nearly the width of his coliseum, blazing, rays pouring off it
                stack = new ItemStack(ModItems.FX_GIANT_SPUD.get());
                float in = Math.min(1F, age / 40F);
                in = in * in * (3 - 2 * in);
                float pulse = 1F + 0.03F * Mth.sin(age * 0.4F);
                lit = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                pose.pushPose();
                pose.scale(5F, 5F, 5F);
                BossRenderer.drawRays(pose, buffers, Math.min(1F, age / 30F), age * 0.6F, 0F);
                pose.popPose();
                pose.mulPose(Axis.YP.rotationDegrees(age * 1.2F));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.04F) * 10F));
                float s = 26F * in * pulse;
                pose.scale(s, s, s);
            }
            case MONOLITH -> {
                // a giant potato stood on end, pushing up out of the sand (and sinking back)
                stack = new ItemStack(ModItems.FX_GIANT_SPUD.get());
                float up = Mth.clamp(age / 20F, 0F, 1F);
                up = up * up * (3 - 2 * up);
                pose.translate(Mth.sin(age * 3.1F) * 0.03F * (1 - up), -5.2F * (1 - up) + 2.6F, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(90F));
                pose.mulPose(Axis.XP.rotationDegrees(p.getId() * 37F % 360F));
                pose.scale(5.2F, 3.0F, 3.0F);
            }
            case SOMBRERO -> {
                // spun flat like a saw blade, wobbling as it goes, the brim catching the light
                stack = new ItemStack(ModItems.FX_SOMBRERO.get());
                pose.translate(0, 0.1, 0);
                pose.mulPose(Axis.YP.rotationDegrees(age * 47F));
                pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.31F) * 7F));
                pose.scale(4.2F, 4.2F, 4.2F);
                lit = LightTexture.FULL_BRIGHT;
            }
            case CALAVERA -> {
                // a sugar skull, its face where it is going (or bobbing round you while it circles), eyes aglow
                stack = new ItemStack(ModItems.FX_CALAVERA.get());
                net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
                float yawT = v.horizontalDistanceSqr() > 1e-4 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : -p.getYRot();
                pose.translate(0, 0.4 + Mth.sin(age * 0.25F + p.getId()) * 0.12F, 0);
                pose.mulPose(Axis.YP.rotationDegrees(yawT + 180F));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.17F + p.getId()) * 10F));
                pose.scale(2.3F, 2.3F, 2.3F);
                lit = LightTexture.FULL_BRIGHT;
                drawGlow(pose, buffers, 0F, 0.62F, 240, 74, 160, 0.45F + 0.2F * Mth.sin(age * 0.5F));       // a pink halo round it
            }
            case IGNIS_HEAD -> {
                // Ignis's head: thrown down upright (no tumbling: the user wanted it to drop, not spin), then where it fell,
                // tipped on its side, facing the way it was thrown. Landed by height, not by speed (the client's motion can lag).
                stack = new ItemStack(ModItems.FX_IGNIS_HEAD.get());
                float drop = Mth.clamp((float) (p.getY() - p.radius()) / 1.5F, 0F, 1F);
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                pose.mulPose(Axis.ZP.rotationDegrees(14F * (1F - drop)));
                pose.scale(1.5F, 1.5F, 1.5F);
                pose.translate(-0.5, -0.7 * drop, -0.5);
            }
            default -> {
                pose.popPose();
                return;
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, lit, OverlayTexture.NO_OVERLAY, pose,
                buffers, p.level(), p.getId());
        pose.popPose();
        super.render(p, yaw, partialTick, pose, buffers, light);
    }

    private static final ResourceLocation PAPEL = PaponMod.id("textures/entity/papel_picado.png");
    private static final ResourceLocation SOUL = PaponMod.id("textures/entity/soul.png");

    /**
     * Papel Picado: a string of giant cut-paper banners across the floor (its half length the radius), travelling along its
     * yaw (a maths angle): four colours in turn, skulls and flowers cut out of them, fluttering, full bright; low ones from
     * the floor to a jump's height, high ones from a crouch's height up. A glowing string along their top.
     */
    private static void drawPapel(BossProp p, PoseStack pose, MultiBufferSource buffers, float age) {
        float half = p.radius();
        if (half <= 0.5F) return;
        boolean high = p.delay() == 1;
        float y0 = high ? 1.55F : 0.02F, y1 = high ? 3.2F : 1.05F;
        double yaw = Math.toRadians(p.getYRot());
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw);
        float ax = -dz, az = dx;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PAPEL));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int lit = LightTexture.FULL_BRIGHT;
        float w = 1.5F;
        int count = (int) Math.ceil(half * 2 / w);
        for (int i = 0; i < count; i++) {
            float s0 = -half + i * w, s1 = Math.min(half, s0 + w - 0.12F);
            int col = Math.floorMod(i + p.getId(), 4);
            float u0 = col * 0.25F, u1 = u0 + 0.25F;
            // the sheet sways back along its path as it flies, more at its bottom edge
            float sway = 0.25F * Mth.sin(age * 0.6F + i * 0.9F);
            float[] a = {ax * s0, y1, az * s0}, b = {ax * s1, y1, az * s1};
            float[] c = {ax * s1 - dx * sway, y0, az * s1 - dz * sway}, d = {ax * s0 - dx * sway, y0, az * s0 - dz * sway};
            vtx(vc, m, n, a, u0, 0, lit);
            vtx(vc, m, n, b, u1, 0, lit);
            vtx(vc, m, n, c, u1, 1, lit);
            vtx(vc, m, n, d, u0, 1, lit);
        }
        // the string, glowing gold
        VertexConsumer glow = buffers.getBuffer(PaponRenderTypes.glow());
        for (float side : new float[]{-1, 1}) {
            glow.vertex(m, -ax * half, y1 + 0.05F, -az * half).color(255, 210, 90, 200).endVertex();
            glow.vertex(m, ax * half, y1 + 0.05F, az * half).color(255, 210, 90, 200).endVertex();
            glow.vertex(m, ax * half, y1 + 0.05F + 0.08F * side, az * half).color(255, 210, 90, 0).endVertex();
            glow.vertex(m, -ax * half, y1 + 0.05F + 0.08F * side, -az * half).color(255, 210, 90, 0).endVertex();
        }
    }

    /**
     * A tortured soul out of the rift (La Grieta): a wailing face of blue light, its arms out toward whoever it is after,
     * a tail of smoke; it faces the camera and leans along its flight.
     */
    private static void drawSoul(BossProp p, PoseStack pose, MultiBufferSource buffers, float age, float partial) {
        net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        float grow = Mth.clamp(age / 10F, 0, 1);
        float wob = Mth.sin(age * 0.5F + p.getId()) * 0.08F;
        pose.pushPose();
        pose.translate(0, 0.9 + Mth.sin(age * 0.2F) * 0.15F, 0);
        pose.mulPose(cam.rotation());
        pose.mulPose(Axis.ZP.rotation(wob));
        float w = 0.95F * grow, h = 1.25F * grow;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SOUL));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int a = (int) (215 * (0.8F + 0.2F * Mth.sin(age * 0.9F)));
        float[][] q = {{-w, -h, 0, 1}, {w, -h, 1, 1}, {w, h, 1, 0}, {-w, h, 0, 0}};
        for (float[] c : q) {
            vc.vertex(m, c[0], c[1], 0).color(255, 255, 255, a).uv(c[2], c[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        }
        pose.popPose();
        drawGlow(pose, buffers, 0.9F, 1.1F * grow, 80, 220, 255, 0.4F);
    }

    // ------------------------------------------------------------------ 2026-10-05: El Gigante's debris, El Tzompantli, El Beso

    private static final int FULL = LightTexture.FULL_BRIGHT;
    private static final int[] STEM = {46, 92, 36}, STEM_DARK = {30, 60, 26}, THORN = {112, 46, 34}, ROSE_RED = {214, 22, 52},
            ROSE_DEEP = {140, 8, 34}, ROSE_PINK = {255, 96, 150}, LEAF = {58, 120, 44}, POLE = {92, 60, 36};

    /** A slab of the floor, tumbling. */
    private static void drawDebris(BossProp p, PoseStack pose, MultiBufferSource buffers, float age) {
        float s = p.radius();
        pose.pushPose();
        pose.translate(0, s * 0.5, 0);
        pose.mulPose(Axis.XP.rotationDegrees(age * (9 + p.getId() % 7)));
        pose.mulPose(Axis.ZP.rotationDegrees(age * (5 + p.getId() % 5)));
        pose.scale(s, s * 0.6F, s);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(p.block(), pose, buffers, FULL, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static final net.minecraft.world.level.block.state.BlockState SKULL =
            com.groid.papon.registry.ModBlocks.MUERTOS_SKULL.get().defaultBlockState();

    /** A tzompantli post at (x, z): a pole with three sugar skulls skewered on it, risen {@code h} (0 under the floor .. 1), the
     *  skulls' faces turned to {@code faceDeg} (a maths angle: the way the line runs, out of the ring), not all north. */
    private static void post(PoseStack pose, MultiBufferSource buffers, double x, double z, float h, float yaw, double faceDeg) {
        if (h <= 0.02F) return;
        net.minecraft.world.level.block.state.BlockState skull = SKULL;
        if (skull.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            double a = Math.toRadians(faceDeg);
            skull = skull.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                    net.minecraft.core.Direction.getNearest((float) Math.cos(a), 0, (float) Math.sin(a)));
        }
        float tall = 3.6F;
        pose.pushPose();
        pose.translate(x, (h - 1) * (tall + 0.4F), z);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Shapes.tube(vc, pose.last().pose(), pose.last().normal(), new net.minecraft.world.phys.Vec3(0, 0, 0), new net.minecraft.world.phys.Vec3(0, tall + 0.5, 0),
                0.12F, 0.1F, POLE, 5, FULL);
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(-0.45, 0.25 + i * 1.08, -0.45);
            pose.scale(0.9F, 0.9F, 0.9F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(skull, pose, buffers, FULL, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** How far up a post is {@code age} ticks after the front passed it. */
    private static float postHeight(double age) {
        if (age < 0 || age > BossProp.SKULL_GONE) return 0;
        if (age < BossProp.SKULL_UP) {
            float k = (float) (age / BossProp.SKULL_UP);
            return Math.min(1.08F, k * (2.16F - k * 1.08F) * 1.0F + 0.0F);
        }
        if (age < BossProp.SKULL_STAND) return 1;
        return 1 - (float) ((age - BossProp.SKULL_STAND) / (BossProp.SKULL_GONE - BossProp.SKULL_STAND));
    }

    /** A line of skull posts racing out along its heading; a glowing crack runs ahead of its front. */
    private static void drawSkullLine(BossProp p, PoseStack pose, MultiBufferSource buffers, float age, float partial) {
        double a = Math.toRadians(p.getYRot());
        float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
        double front = p.radius() + BossProp.SKULL_SPEED * partial;
        int reach = p.delay();
        float yaw = -p.getYRot() + 90F;
        for (int k = 0; ; k++) {
            double d = 2.0 + k * BossProp.SKULL_GAP;
            if (d > reach || d > front) break;
            // a little off the line each side, so it reads as a hedge of them, not a ruler
            double off = ((k * 37 + p.getId()) % 5 - 2) * 0.18;
            post(pose, buffers, dx * d - dz * off, dz * d + dx * off, postHeight((front - d) / BossProp.SKULL_SPEED), yaw, p.getYRot());
        }
        if (front < reach) {
            VertexConsumer glow = buffers.getBuffer(PaponRenderTypes.glow());
            Matrix4f m = pose.last().pose();
            float f0 = (float) Math.max(0, front - 1), f1 = (float) Math.min(reach, front + 4.5);
            float w = 0.55F;
            for (int side = 0; side < 2; side++) {
                float[] xs = {dx * f0 - dz * w, dx * f0 + dz * w, dx * f1 + dz * w * 0.2F, dx * f1 - dz * w * 0.2F};
                float[] zs = {dz * f0 + dx * w, dz * f0 - dx * w, dz * f1 - dx * w * 0.2F, dz * f1 + dx * w * 0.2F};
                int[] al = {230, 230, 0, 0};
                int[] ord = side == 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
                for (int i : ord) glow.vertex(m, xs[i], 0.06F, zs[i]).color(255, 120, 30, al[i]).endVertex();
            }
        }
    }

    /** A ring of skull posts spreading out (its gaps open), the two rings behind it sinking. */
    private static void drawSkullRing(BossProp p, PoseStack pose, MultiBufferSource buffers, float age, float partial) {
        double r0 = p.radius() + BossProp.SKULL_SPEED * partial;
        int packed = p.delay();
        double fa = packed & 0xFF, fb = (packed >> 8) & 0xFF;
        boolean two = (packed >> 16 & 1) != 0;
        double gap = Math.toRadians(p.getYRot());
        float[] hs = {1F, 0.55F, 0.2F};
        for (int ring = 0; ring < 3; ring++) {
            double r = r0 - ring * BossProp.SKULL_GAP;
            if (r < 2) continue;
            int n = Math.max(10, (int) (Math.PI * 2 * r / 2.4));
            for (int i = 0; i < n; i++) {
                double ang = Math.PI * 2 * i / n;
                if (inGap(ang, gap, r) || two && inGap(ang, gap + Math.PI, r)) continue;
                double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
                if (fa > 0 && fb > 0 && (x / fa) * (x / fa) + (z / fb) * (z / fb) > 0.93) continue;
                post(pose, buffers, x, z, ring == 0 ? Math.min(1, (float) ((age % 3) / 3) * 0.3F + 0.85F) : hs[ring], 0F, Math.toDegrees(ang));
            }
        }
    }

    static boolean inGap(double ang, double gap, double r) {
        double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - gap)));
        return Math.toRadians(d) * r < BossProp.RING_GAP;
    }

    /** A muchacha's rose in flight, turning over. */
    private static void drawRose(BossProp p, PoseStack pose, MultiBufferSource buffers, float age) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        pose.pushPose();
        pose.translate(0, 0.3, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 31));
        pose.mulPose(Axis.XP.rotationDegrees(age * 23));
        pose.scale(1.6F, 1.6F, 1.6F);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        net.minecraft.world.phys.Vec3 o = net.minecraft.world.phys.Vec3.ZERO, up = new net.minecraft.world.phys.Vec3(0, 1, 0);
        Shapes.tube(vc, m, n, o.add(0, -0.7, 0), o, 0.035F, 0.04F, STEM, 4, FULL);
        Shapes.quad(vc, m, n, o.add(0, -0.4, 0), o.add(0.18, -0.32, 0.02), o.add(0.26, -0.2, 0), o.add(0.08, -0.3, -0.02), LEAF, FULL);
        Shapes.rose(vc, m, n, o, up, 0.32F, 0.8F, 0, ROSE_RED, ROSE_DEEP, FULL);
        pose.popPose();
        drawGlow(pose, buffers, 0.3F, 0.9F, 255, 40, 90, 0.35F);
    }

    /** A thicket of thorned rose vines bursting up out of the floor and curling out, roses at their ends; withering at the end. */
    private static void drawThorns(BossProp p, PoseStack pose, MultiBufferSource buffers, float age) {
        boolean bloom = p.delay() == 1;
        float s = p.radius();
        int life = bloom ? BossProp.BLOOM_LIFE : BossProp.THORNS_LIFE;
        float grow = Mth.clamp(age / (bloom ? 10F : 5F), 0, 1);
        grow = 1 - (1 - grow) * (1 - grow);
        float wither = Mth.clamp((life - age) / 12F, 0, 1);
        float k = grow * wither;
        if (k <= 0.01F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int vines = bloom ? 14 : 9;
        for (int j = 0; j < vines; j++) {
            double a = Math.PI * 2 * j / vines + (p.getId() % 7) * 0.3 + (j % 2) * 0.2;
            net.minecraft.world.phys.Vec3 out = new net.minecraft.world.phys.Vec3(Math.cos(a), 0, Math.sin(a));
            double tall = s * (bloom ? 0.55 : 0.85) * (0.8 + 0.3 * ((j * 7) % 3) / 2.0);
            net.minecraft.world.phys.Vec3 p0 = out.scale(s * 0.25), p1 = out.scale(s * 0.55).add(0, tall * 1.3 * k, 0),
                    p2 = out.scale(s * (1.0 + 0.1 * (j % 3))).add(0, tall * 0.55 * k, 0);
            net.minecraft.world.phys.Vec3 prev = p0;
            int segs = 8;
            for (int i = 1; i <= segs; i++) {
                double t = i / (double) segs * k;
                net.minecraft.world.phys.Vec3 q = p0.scale((1 - t) * (1 - t)).add(p1.scale(2 * (1 - t) * t)).add(p2.scale(t * t));
                float rad = (float) (0.16 * s / 2.6 * (1 - 0.6 * i / segs) + 0.04);
                Shapes.tube(vc, m, n, prev, q, rad, rad * 0.9F, i % 2 == 0 ? STEM : STEM_DARK, 5, FULL);
                // thorns: out from the vine, every segment, alternating sides
                net.minecraft.world.phys.Vec3 d = q.subtract(prev).normalize();
                net.minecraft.world.phys.Vec3 sideV = d.cross(new net.minecraft.world.phys.Vec3(0, 1, 0));
                sideV = sideV.lengthSqr() < 1e-4 ? out : sideV.normalize();
                net.minecraft.world.phys.Vec3 th = sideV.scale(i % 2 == 0 ? 1 : -1).add(0, 0.4, 0).normalize();
                Shapes.cone(vc, m, n, q.subtract(d.scale(0.1)), q.add(th.scale(rad * 3.2 + 0.12)), rad * 0.8F, THORN, FULL);
                if (i == segs / 2) Shapes.quad(vc, m, n, q, q.add(sideV.scale(0.35)).add(0, 0.12, 0), q.add(sideV.scale(0.5)).add(0, 0.3, 0), q.add(0, 0.22, 0), LEAF, FULL);
                prev = q;
            }
            // a rose at its end, opening as it grows
            Shapes.rose(vc, m, n, prev, prev.subtract(p1.scale(0.6)).normalize().add(0, 0.6, 0).normalize(), (float) (0.32 * s / 2.6 + 0.12),
                    grow, j, j % 3 == 0 ? ROSE_PINK : ROSE_RED, ROSE_DEEP, FULL);
        }
        if (bloom) {
            // the great rose in the middle: opening out, then bursting
            float open = Mth.clamp(age / 18F, 0, 1);
            Shapes.rose(vc, m, n, new net.minecraft.world.phys.Vec3(0, s * 0.35 * k, 0), new net.minecraft.world.phys.Vec3(0, 1, 0), s * 0.75F * k, open,
                    age * 0.02F, ROSE_RED, ROSE_DEEP, FULL);
            drawGlow(pose, buffers, s * 0.4F, s * 1.1F * k, 255, 40, 90, 0.45F);
        } else {
            drawGlow(pose, buffers, 0.4F, s * 1.2F * k, 255, 40, 90, 0.25F * wither);
        }
    }

    /** A vine of thorns and roses racing along the floor, writhing; its front a burst of leaves. */
    private static void drawVine(BossProp p, PoseStack pose, MultiBufferSource buffers, float age, float partial) {
        double a = Math.toRadians(p.getYRot());
        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(Math.cos(a), 0, Math.sin(a)), side = new net.minecraft.world.phys.Vec3(-dir.z, 0, dir.x);
        double len = Math.min(p.delay(), p.radius() + 1.6 * partial);
        float wither = Mth.clamp((p.delay() / 1.6F + 40 - age) / 12F, 0, 1);
        if (len < 0.5 || wither <= 0) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        net.minecraft.world.phys.Vec3 prev = null;
        for (double d = 0; d <= len; d += 0.6) {
            double wob = Math.sin(d * 0.9 + age * 0.25 + p.getId()) * 0.35;
            net.minecraft.world.phys.Vec3 q = dir.scale(d).add(side.scale(wob)).add(0, (0.35 + 0.15 * Math.sin(d * 1.7)) * wither, 0);
            if (prev != null) {
                Shapes.tube(vc, m, n, prev, q, 0.24F * wither, 0.24F * wither, ((int) (d / 0.6)) % 2 == 0 ? STEM : STEM_DARK, 5, FULL);
                int i = (int) (d / 0.6);
                net.minecraft.world.phys.Vec3 th = side.scale(i % 2 == 0 ? 1 : -1).add(0, 0.6, 0).normalize();
                Shapes.cone(vc, m, n, q, q.add(th.scale(0.75 * wither)), 0.13F * wither, THORN, FULL);
                if (i % 4 == 2) Shapes.rose(vc, m, n, q.add(0, 0.25, 0), new net.minecraft.world.phys.Vec3(0, 1, 0).add(side.scale(0.4)).normalize(),
                        0.42F * wither, 0.9F, i, i % 8 == 2 ? ROSE_PINK : ROSE_RED, ROSE_DEEP, FULL);
            }
            prev = q;
        }
        if (len < p.delay() && prev != null) drawGlowAt(pose, buffers, prev, 1.6F, 255, 60, 110, 0.5F);
    }

    /** The great rose's burst: a ring of petals rolling out over the floor, fluttering. */
    private static void drawPetalRing(BossProp p, PoseStack pose, MultiBufferSource buffers, float age, float partial) {
        double r = p.radius() + 1.0 * partial;
        if (r < 0.5) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int count = Math.max(16, (int) (Math.PI * 2 * r / 0.55));
        for (int i = 0; i < count; i++) {
            double ang = Math.PI * 2 * i / count;
            double rr = r + Math.sin(i * 1.7 + age * 0.4) * 0.4;
            float y = (float) (0.15 + 0.9 * Math.abs(Math.sin(i * 0.9 + age * 0.3)));
            net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(Math.cos(ang) * rr, y, Math.sin(ang) * rr);
            net.minecraft.world.phys.Vec3 out = new net.minecraft.world.phys.Vec3(Math.cos(ang), 0, Math.sin(ang));
            net.minecraft.world.phys.Vec3 tan = new net.minecraft.world.phys.Vec3(-Math.sin(ang), 0, Math.cos(ang));
            double tw = i * 0.7 + age * 0.5;
            net.minecraft.world.phys.Vec3 u = tan.scale(Math.cos(tw)).add(0, Math.sin(tw), 0).scale(0.32), w = out.scale(0.22);
            Shapes.quad(vc, m, n, c.subtract(u).subtract(w), c.subtract(u).add(w), c.add(u).add(w), c.add(u).subtract(w), i % 3 == 0 ? ROSE_PINK : ROSE_RED, FULL);
        }
    }

    // ---- El Castillo

    private static final int[] BAMBOO = {214, 176, 106}, BAMBOO_NODE = {150, 112, 62};

    /**
     * El Castillo (2026-10-05, the user: "much more visually obvious... do not let it blend into the environment"): a real fiesta
     * castillo, solid and lit (it was a faint lattice of light): a bamboo tower as wide as a house and 26 blocks tall, rising out
     * of the floor; four poles cross-braced, a platform at each tier with its wheel round it (spokes, a rim, paper fringes in the
     * flag's colours at every tip), a crown of spokes on top round a giant sugar skull. Lit, a wheel spins and throws fountains
     * of fire; the fuse burns down it from his hand; a column of light up it and a glow on the floor round it while it burns.
     * Its age is the move's clock (spawned at its tick 1).
     */
    private static void drawCastillo(BossProp p, PoseStack pose, MultiBufferSource buffers, float age) {
        if (p.radius() > 0) return;                         // burst
        float t = age + 1;
        float rise = Mth.clamp(t / com.groid.papon.boss.Fiesta.RISE, 0, 1);
        rise = 1 - (1 - rise) * (1 - rise);
        double top = com.groid.papon.boss.Fiesta.TOWER_TOP;
        double sink = (1 - rise) * (top + 2);
        double half = com.groid.papon.boss.Fiesta.TOWER_HALF;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        net.minecraft.world.phys.Vec3 o = new net.minecraft.world.phys.Vec3(0, -sink, 0);
        // the poles, with their nodes
        for (int k = 0; k < 4; k++) {
            double x = (k & 1) == 0 ? -half : half, z = (k & 2) == 0 ? -half : half;
            Shapes.tube(vc, m, n, o.add(x, 0, z), o.add(x, top, z), 0.34F, 0.28F, BAMBOO, 6, FULL);
            for (double y = 2; y < top; y += 2.6) Shapes.ball(vc, m, n, o.add(x, y, z), 0.36F, BAMBOO_NODE, FULL);
        }
        // the bracing: an X on every face between the platforms
        double[][] faces = {{-half, -half, half, -half}, {half, -half, half, half}, {half, half, -half, half}, {-half, half, -half, -half}};
        for (int tier = 0; tier <= com.groid.papon.boss.Fiesta.CASTILLO_TIERS; tier++) {
            double y0 = tier == 0 ? 0.4 : com.groid.papon.boss.Fiesta.tierHeight(tier - 1), y1 = tier < com.groid.papon.boss.Fiesta.CASTILLO_TIERS
                    ? com.groid.papon.boss.Fiesta.tierHeight(tier) : top - 1;
            for (double[] f : faces) {
                Shapes.tube(vc, m, n, o.add(f[0], y0, f[1]), o.add(f[2], y1, f[3]), 0.16F, 0.16F, BAMBOO, 5, FULL);
                Shapes.tube(vc, m, n, o.add(f[2], y0, f[3]), o.add(f[0], y1, f[1]), 0.16F, 0.16F, BAMBOO, 5, FULL);
                Shapes.tube(vc, m, n, o.add(f[0], y1, f[1]), o.add(f[2], y1, f[3]), 0.22F, 0.22F, BAMBOO_NODE, 5, FULL);
            }
        }
        // the wheels: spokes out from the platform to a rim, a paper fringe at every tip, spinning once lit
        for (int tier = 0; tier < com.groid.papon.boss.Fiesta.CASTILLO_TIERS; tier++) {
            double y = com.groid.papon.boss.Fiesta.tierHeight(tier);
            int s0 = com.groid.papon.boss.Fiesta.tierStart(tier);
            boolean lit = t >= s0 && t < s0 + com.groid.papon.boss.Fiesta.TIER_RUN + 10;
            int arms = com.groid.papon.boss.Fiesta.arms(tier) + 3;
            double r = com.groid.papon.boss.Fiesta.wheelR(tier);
            int spin = (tier & 1) == 0 ? 1 : -1;
            double ang0 = t >= s0 ? spin * (Math.min(t, s0 + com.groid.papon.boss.Fiesta.TIER_RUN + 10) - s0) * 0.25 : tier * 0.4;
            int col = com.groid.papon.boss.Fiesta.colour(tier * 2 + 1);
            int[] paper = {col >> 16 & 255, col >> 8 & 255, col & 255};
            net.minecraft.world.phys.Vec3 prevRim = null;
            for (int k = 0; k <= arms; k++) {
                double an = ang0 + k * Math.PI * 2 / arms;
                net.minecraft.world.phys.Vec3 tip = o.add(Math.cos(an) * r, y, Math.sin(an) * r);
                if (k < arms) {
                    Shapes.tube(vc, m, n, o.add(Math.cos(an) * half * 1.1, y, Math.sin(an) * half * 1.1), tip, 0.14F, 0.12F, BAMBOO, 4, FULL);
                    // the fringe: a little banner of paper hanging off the tip, fluttering
                    double fl = Math.sin(t * 0.5 + k) * 0.2;
                    net.minecraft.world.phys.Vec3 tan = new net.minecraft.world.phys.Vec3(-Math.sin(an), 0, Math.cos(an)).scale(0.55);
                    Shapes.quad(vc, m, n, tip.subtract(tan), tip.add(tan), tip.add(tan).add(fl, -1.3, fl), tip.subtract(tan).add(fl, -1.3, fl),
                            (k & 1) == 0 ? paper : new int[]{255, 244, 220}, FULL);
                }
                if (prevRim != null) Shapes.tube(vc, m, n, prevRim, tip, 0.1F, 0.1F, BAMBOO_NODE, 4, FULL);
                prevRim = tip;
            }
        }
        // the crown: spokes round a giant sugar skull, grinning down over the arena
        double cy = top - 0.5;
        for (int k = 0; k < 10; k++) {
            double an = t * 0.05 + k * Math.PI / 5;
            Shapes.tube(vc, m, n, o.add(0, cy, 0), o.add(Math.cos(an) * 3.6, cy + 1.6 + (k & 1), Math.sin(an) * 3.6), 0.14F, 0.1F, BAMBOO, 4, FULL);
        }
        pose.pushPose();
        pose.translate(-1.6, cy - sink - 0.2, -1.6);
        pose.scale(3.2F, 3.2F, 3.2F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(SKULL, pose, buffers, FULL, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // ---- its light
        VertexConsumer glow = buffers.getBuffer(PaponRenderTypes.glow());
        Matrix4f gm = pose.last().pose();
        net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 camRel = cam.getPosition().subtract(p.getPosition(Minecraft.getInstance().getFrameTime()));
        int burning = 0;
        for (int tier = 0; tier < com.groid.papon.boss.Fiesta.CASTILLO_TIERS; tier++) {
            int s0 = com.groid.papon.boss.Fiesta.tierStart(tier);
            if (t < s0 || t >= s0 + com.groid.papon.boss.Fiesta.TIER_RUN + 10) continue;
            burning++;
            double y = com.groid.papon.boss.Fiesta.tierHeight(tier) - sink;
            int col = com.groid.papon.boss.Fiesta.colour(tier * 2 + 1);
            int arms = com.groid.papon.boss.Fiesta.arms(tier) + 3;
            double r = com.groid.papon.boss.Fiesta.wheelR(tier);
            int spin = (tier & 1) == 0 ? 1 : -1;
            double ang0 = spin * (t - s0) * 0.25;
            for (int k = 0; k < arms; k++) {
                double an = ang0 + k * Math.PI * 2 / arms;
                net.minecraft.world.phys.Vec3 tip = new net.minecraft.world.phys.Vec3(Math.cos(an) * r, y, Math.sin(an) * r);
                // a fountain of fire off each tip, sprayed back against the spin
                net.minecraft.world.phys.Vec3 back = new net.minecraft.world.phys.Vec3(Math.sin(an) * spin, 0.15, -Math.cos(an) * spin).scale(2.2);
                glowStreak(glow, gm, tip, tip.add(back), camRel, 0.6F, col, 230);
                glowStar(glow, gm, tip, camRel, 0.9F, 0xFFF4C0, 255);
            }
            glowStar(glow, gm, new net.minecraft.world.phys.Vec3(0, y, 0), camRel, 2.4F + 0.4F * Mth.sin(t), col, 200);
        }
        // the fuse, burning down from his hand to the first wheel
        if (t > com.groid.papon.boss.Fiesta.FUSE && t < com.groid.papon.boss.Fiesta.TIER_START) {
            double k = (t - com.groid.papon.boss.Fiesta.FUSE) / (double) (com.groid.papon.boss.Fiesta.TIER_START - com.groid.papon.boss.Fiesta.FUSE);
            double y = top + 1 - (top + 1 - com.groid.papon.boss.Fiesta.tierHeight(0)) * k - sink;
            net.minecraft.world.phys.Vec3 f = new net.minecraft.world.phys.Vec3(half, y, half);
            glowStar(glow, gm, f, camRel, 1.4F + 0.4F * Mth.sin(t * 2.3F), 0xFFE070, 255);
            glowStreak(glow, gm, f, f.add(0, 2.5, 0), camRel, 0.5F, 0xFF8020, 200);
        }
        // while it burns: a column of light up it, and its glow on the floor
        float lightK = Mth.clamp(burning / 2F, 0, 1);
        if (rise > 0.2F) {
            float base = 0.35F + 0.65F * lightK;
            glowStreak(glow, gm, new net.minecraft.world.phys.Vec3(0, -sink, 0), new net.minecraft.world.phys.Vec3(0, top - sink + 4, 0), camRel, 7F, 0xFFB040,
                    (int) (70 * base));
            int ring = 32;
            for (int i = 0; i < ring; i++) {
                double a0 = Math.PI * 2 * i / ring, a1 = Math.PI * 2 * (i + 1) / ring;
                float rr = 9F;
                int al = (int) (120 * base);
                glow.vertex(gm, 0, 0.06F, 0).color(255, 170, 60, al).endVertex();
                glow.vertex(gm, (float) Math.cos(a0) * rr, 0.06F, (float) Math.sin(a0) * rr).color(255, 120, 40, 0).endVertex();
                glow.vertex(gm, (float) Math.cos(a1) * rr, 0.06F, (float) Math.sin(a1) * rr).color(255, 120, 40, 0).endVertex();
                glow.vertex(gm, 0, 0.06F, 0).color(255, 170, 60, al).endVertex();
            }
        }
    }

    /** An additive streak from a to b, turned to the camera. */
    private static void glowStreak(VertexConsumer vc, Matrix4f m, net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b,
                                   net.minecraft.world.phys.Vec3 cam, float w, int col, int al) {
        net.minecraft.world.phys.Vec3 d = b.subtract(a);
        net.minecraft.world.phys.Vec3 side = d.cross(cam.subtract(a.add(d.scale(0.5))));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(w / 2);
        net.minecraft.world.phys.Vec3[] q = {a.subtract(side), a.add(side), b.add(side.scale(0.3)), b.subtract(side.scale(0.3))};
        int[] alpha = {al, al, 0, 0};
        int r = col >> 16 & 255, g = col >> 8 & 255, bl = col & 255;
        for (int[] o : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
            for (int i : o) vc.vertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).color(r, g, bl, alpha[i]).endVertex();
        }
    }

    /** An additive star at p, facing the camera. */
    private static void glowStar(VertexConsumer vc, Matrix4f m, net.minecraft.world.phys.Vec3 p, net.minecraft.world.phys.Vec3 cam, float s, int col, int al) {
        net.minecraft.world.phys.Vec3 toCam = cam.subtract(p).normalize();
        net.minecraft.world.phys.Vec3 right = toCam.cross(new net.minecraft.world.phys.Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new net.minecraft.world.phys.Vec3(1, 0, 0);
        right = right.normalize();
        net.minecraft.world.phys.Vec3 up = right.cross(toCam).normalize();
        int r = col >> 16 & 255, g = col >> 8 & 255, b = col & 255;
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            net.minecraft.world.phys.Vec3 p0 = p.add(right.scale(Math.cos(a0) * s)).add(up.scale(Math.sin(a0) * s));
            net.minecraft.world.phys.Vec3 p1 = p.add(right.scale(Math.cos(a1) * s)).add(up.scale(Math.sin(a1) * s));
            vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, al).endVertex();
            vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, 0).endVertex();
            vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, 0).endVertex();
            vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, al).endVertex();
        }
    }

    /** A soft light at {@code at} (relative to the prop). */
    private static void drawGlowAt(PoseStack pose, MultiBufferSource buffers, net.minecraft.world.phys.Vec3 at, float rad, int r, int g, int b, float k) {
        pose.pushPose();
        pose.translate(at.x, 0, at.z);
        drawGlow(pose, buffers, (float) at.y, rad, r, g, b, k);
        pose.popPose();
    }

    private static final ResourceLocation METAL = new ResourceLocation("minecraft", "textures/block/iron_block.png");
    private static final ResourceLocation GRIP = new ResourceLocation("minecraft", "textures/block/stripped_oak_log.png");
    private static final float PLATE = 1.4F;

    /**
     * El Machacador: the plate (boss/Masher's shape: a grid of square holes) one block cell at a time, its
     * rim, the handle going up out of sight with a wooden grip, and on the sand under it the holes lit green.
     */
    private static void drawMasher(BossProp p, PoseStack pose, MultiBufferSource buffers, int light, float age) {
        float yaw = p.getYRot();
        int v = p.delay();                                  // the layout of the holes (boss/Masher)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        int lit = LightTexture.FULL_BRIGHT;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int hx = (int) com.groid.papon.boss.Masher.HALF_X, hz = (int) com.groid.papon.boss.Masher.HALF_Z;
        for (int x = -hx; x < hx; x++) {
            for (int z = -hz; z < hz; z++) {
                if (!com.groid.papon.boss.Masher.metal(x + 0.5, z + 0.5, v)) continue;
                // top and bottom of this cell
                quad(vc, m, n, x, PLATE, z, x, PLATE, z + 1, x + 1, PLATE, z + 1, x + 1, PLATE, z, lit, 0, 1, 0);
                quad(vc, m, n, x, 0, z, x + 1, 0, z, x + 1, 0, z + 1, x, 0, z + 1, lit, 0, -1, 0);
                // sides where it meets a hole or the edge
                if (!com.groid.papon.boss.Masher.metal(x - 0.5, z + 0.5, v)) quad(vc, m, n, x, 0, z, x, 0, z + 1, x, PLATE, z + 1, x, PLATE, z, lit, -1, 0, 0);
                if (!com.groid.papon.boss.Masher.metal(x + 1.5, z + 0.5, v)) quad(vc, m, n, x + 1, 0, z + 1, x + 1, 0, z, x + 1, PLATE, z, x + 1, PLATE, z + 1, lit, 1, 0, 0);
                if (!com.groid.papon.boss.Masher.metal(x + 0.5, z - 0.5, v)) quad(vc, m, n, x + 1, 0, z, x, 0, z, x, PLATE, z, x + 1, PLATE, z, lit, 0, 0, -1);
                if (!com.groid.papon.boss.Masher.metal(x + 0.5, z + 1.5, v)) quad(vc, m, n, x, 0, z + 1, x + 1, 0, z + 1, x + 1, PLATE, z + 1, x, PLATE, z + 1, lit, 0, 0, 1);
            }
        }
        // the shaft up to the handle, and the grip across it
        box(vc, m, n, -1.2F, PLATE, -1.2F, 1.2F, PLATE + 34, 1.2F, lit);
        VertexConsumer wood = buffers.getBuffer(RenderType.entityCutoutNoCull(GRIP));
        box(wood, m, n, -9F, PLATE + 34, -1.6F, 9F, PLATE + 37, 1.6F, lit);
        pose.popPose();
        // the holes lit green on the sand below it
        float h = p.radius();
        if (h > 0.6F) {
            VertexConsumer glow = buffers.getBuffer(PaponRenderTypes.glow());
            float pulse = 0.35F + 0.2F * Mth.sin(age * 0.4F);
            Matrix4f g = pose.last().pose();
            float hw = (float) com.groid.papon.boss.Masher.HOLE;
            for (net.minecraft.world.phys.Vec3 c : com.groid.papon.boss.Masher.holes(yaw, v)) {
                net.minecraft.world.phys.Vec3 a = c.add(com.groid.papon.boss.Masher.toWorld(-hw, -hw, yaw));
                net.minecraft.world.phys.Vec3 b = c.add(com.groid.papon.boss.Masher.toWorld(hw, -hw, yaw));
                net.minecraft.world.phys.Vec3 d = c.add(com.groid.papon.boss.Masher.toWorld(hw, hw, yaw));
                net.minecraft.world.phys.Vec3 e = c.add(com.groid.papon.boss.Masher.toWorld(-hw, hw, yaw));
                float y = -h + 0.06F;
                glow.vertex(g, (float) a.x, y, (float) a.z).color(0.4F, 1F, 0.4F, pulse).endVertex();
                glow.vertex(g, (float) e.x, y, (float) e.z).color(0.4F, 1F, 0.4F, pulse).endVertex();
                glow.vertex(g, (float) d.x, y, (float) d.z).color(0.4F, 1F, 0.4F, pulse).endVertex();
                glow.vertex(g, (float) b.x, y, (float) b.z).color(0.4F, 1F, 0.4F, pulse).endVertex();
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int light, float nx, float ny, float nz) {
        vc.vertex(m, x0, y0, z0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        quad(vc, m, n, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, light, 0, 1, 0);
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, light, 0, -1, 0);
        quad(vc, m, n, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, light, -1, 0, 0);
        quad(vc, m, n, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, light, 1, 0, 0);
        quad(vc, m, n, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, light, 0, 0, -1);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, light, 0, 0, 1);
    }

    /** The mash wave: a lumpy wall 1.4 high in a ring of the synced radius, gravy along the top. */
    private static void drawMash(BossProp p, PoseStack pose, MultiBufferSource buffers, int light) {
        float r = p.radius();
        if (r <= 0.1F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(MASH));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int segs = Math.max(16, (int) (r * 5));
        float h = 1.4F;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float bump0 = h + 0.25F * Mth.sin(i * 1.7F), bump1 = h + 0.25F * Mth.sin((i + 1) * 1.7F);
            float u0 = (i % 4) / 4F, u1 = u0 + 0.25F;
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            vert(vc, m, n, x0, 0, z0, u0, 1, light, nx, nz);
            vert(vc, m, n, x1, 0, z1, u1, 1, light, nx, nz);
            vert(vc, m, n, x1, bump1, z1, u1, 0, light, nx, nz);
            vert(vc, m, n, x0, bump0, z0, u0, 0, light, nx, nz);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light, float nx,
                             float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, 0, nz).endVertex();
    }

    /** A soft glow round a prop (additive, a flat disc turned to the camera), in the prop's pose space. */
    private static void drawGlow(PoseStack pose, MultiBufferSource buffers, float y, float rad, int r, int g, int b, float k) {
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        Matrix4f m = pose.last().pose();
        int a = (int) (200 * Mth.clamp(k, 0, 1));
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Math.PI * 2 * i / seg), a1 = (float) (Math.PI * 2 * (i + 1) / seg);
            vc.vertex(m, 0, 0, 0).color(r, g, b, a).endVertex();
            vc.vertex(m, Mth.cos(a0) * rad, Mth.sin(a0) * rad, 0).color(r, g, b, 0).endVertex();
            vc.vertex(m, Mth.cos(a1) * rad, Mth.sin(a1) * rad, 0).color(r, g, b, 0).endVertex();
            vc.vertex(m, 0, 0, 0).color(r, g, b, a).endVertex();
        }
        pose.popPose();
    }

    /**
     * ¡Fuego, Mariachi!: the band's blast rolling out across the sand, an arc of a ring, glowing. A trumpet's is a wall of
     * gold along the ground, staves of light running through it and a white-hot leading edge (jump it); a violin's a crimson
     * ribbon slashing through at head height (duck it). Additive: it blazes in his night.
     */
    private static void drawBandWave(BossProp p, PoseStack pose, MultiBufferSource buffers, float age) {
        float r = p.radius();
        if (r <= 0.2F) return;
        boolean horn = p.delay() == 0;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        double heading = Math.toRadians(p.getYRot()), span = Math.toRadians(70);
        int segs = Math.max(12, (int) (r * span * 2 / 0.5));
        float fade = Mth.clamp((62F - r) / 10F, 0F, 1F);
        int cr = horn ? 255 : 255, cg = horn ? 206 : 70, cb = horn ? 74 : 112;
        float y0 = horn ? 0.02F : 1.5F, y1 = horn ? 0.95F : 2.4F, ym = (y0 + y1) / 2;
        float pulse = 0.85F + 0.15F * Mth.sin(age * 1.7F);
        for (int i = 0; i < segs; i++) {
            double a0 = heading - span + span * 2 * i / segs, a1 = heading - span + span * 2 * (i + 1) / segs;
            // the ends of the arc fade out
            float e0 = edge(i, segs), e1 = edge(i + 1, segs);
            for (float dr : new float[]{0F, -0.35F, -0.7F}) {
                float rr = r + dr;
                float k = (dr == 0F ? 1F : dr > -0.5F ? 0.55F : 0.25F) * fade * pulse;
                float x0 = (float) Math.cos(a0) * rr, z0 = (float) Math.sin(a0) * rr, x1 = (float) Math.cos(a1) * rr, z1 = (float) Math.sin(a1) * rr;
                int ab = (int) (220 * k * e0), ab1 = (int) (220 * k * e1);
                if (horn) {
                    // bright at the ground, gone at the top
                    quad2(vc, m, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, cr, cg, cb, ab, ab1, 0, 0);
                } else {
                    // a ribbon: bright in the middle, gone at both edges
                    quad2(vc, m, x0, y0, z0, x1, y0, z1, x1, ym, z1, x0, ym, z0, cr, cg, cb, 0, 0, ab1, ab);
                    quad2(vc, m, x0, ym, z0, x1, ym, z1, x1, y1, z1, x0, y1, z0, cr, cg, cb, ab, ab1, 0, 0);
                }
            }
            // the white-hot leading edge, and (the trumpet's) three staves of light
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float[] lines = horn ? new float[]{0.22F, 0.45F, 0.68F} : new float[]{ym};
            for (float ly : lines) {
                int a = (int) (240 * fade * e0);
                quad2(vc, m, x0, ly - 0.04F, z0, x1, ly - 0.04F, z1, x1, ly + 0.04F, z1, x0, ly + 0.04F, z0, 255, 250, 230, a, a, a, a);
            }
        }
    }

    private static float edge(int i, int n) {
        float k = Math.min(i, n - i) / (float) Math.max(1, n / 6);
        return Mth.clamp(k, 0F, 1F);
    }

    /** One quad, both windings, a colour per corner's alpha (the lightning render type culls back faces). */
    private static void quad2(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                              float z2, float x3, float y3, float z3, int r, int g, int b, int a0, int a1, int a2, int a3) {
        vc.vertex(m, x0, y0, z0).color(r, g, b, a0).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a2).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a3).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a3).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a2).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
        vc.vertex(m, x0, y0, z0).color(r, g, b, a0).endVertex();
    }

    /** The salsa wave's cross-section (along its travel, height): up its back to the crest, the lip curling over, the hollow under it. */
    private static final float[][] CURL = {{-2.6F, 0F}, {-1.8F, 1.1F}, {-1.0F, 2.3F}, {-0.2F, 3.2F}, {0.6F, 3.5F}, {1.3F, 3.2F},
            {1.7F, 2.6F}, {1.5F, 2.0F}, {1.0F, 1.9F}, {0.7F, 1.2F}, {0.9F, 0F}};

    /**
     * ¡La Ola!: a wall of salsa three and a half blocks high rolling across the sand, its lip curling over and slopping,
     * chunks of tomato and onion in it (tools/gen_props.salsa_wave), the wall trembling as it goes. Its one gap (the way
     * through) is a low slop you can step through.
     */
    private static void drawSalsaWave(BossProp p, PoseStack pose, MultiBufferSource buffers, int light, float age) {
        float half = p.radius();
        if (half <= 0.5F) return;
        double yaw = Math.toRadians(p.getYRot());
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw);
        float ax = -dz, az = dx;
        double gap = p.gap();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SALSA));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int lit = Math.max(light, LightTexture.pack(10, 12));
        float step = 0.75F;
        int cols = (int) Math.ceil(half * 2 / step);
        for (int c = 0; c < cols; c++) {
            float s0 = -half + c * step, s1 = Math.min(half, s0 + step);
            float h0 = height(s0, gap, age), h1 = height(s1, gap, age);
            for (int i = 0; i < CURL.length - 1; i++) {
                float[] pA = CURL[i], pB = CURL[i + 1];
                float v0 = i / (float) (CURL.length - 1), v1 = (i + 1) / (float) (CURL.length - 1);
                float u0 = s0 / 2F, u1 = s1 / 2F;
                // the wall's corners: along (u), across (s), up (h)
                float[] a = at(pA, s0, h0, dx, dz, ax, az), b = at(pA, s1, h1, dx, dz, ax, az);
                float[] cc = at(pB, s1, h1, dx, dz, ax, az), d = at(pB, s0, h0, dx, dz, ax, az);
                vtx(vc, m, n, a, u0, v0, lit);
                vtx(vc, m, n, b, u1, v0, lit);
                vtx(vc, m, n, cc, u1, v1, lit);
                vtx(vc, m, n, d, u0, v1, lit);
            }
        }
    }

    /** How tall the salsa is {@code s} across the wave: full but for the gap (a slop), the crest rippling as it rolls. */
    private static float height(float s, double gap, float age) {
        float k = 1F + 0.07F * Mth.sin(s * 0.9F + age * 0.45F) + 0.04F * Mth.sin(s * 2.3F - age * 0.7F);
        double g = Math.abs(s - gap);
        if (g < 2.2) k *= (float) Mth.lerp(Mth.clamp((g - 1.6) / 0.6, 0, 1), 0.12, 1.0);
        return k;
    }

    private static float[] at(float[] prof, float s, float hk, float dx, float dz, float ax, float az) {
        float u = prof[0] * (0.6F + 0.4F * hk), y = prof[1] * hk;
        return new float[]{dx * u + ax * s, y, dz * u + az * s};
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float u, float v, int light) {
        vc.vertex(m, p[0], p[1], p[2]).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0, 1, 0).endVertex();
    }

    /** A ring wall of the synced radius ({@link #drawMash}'s shape), in {@code tex}, {@code height} high. */
    private static void drawRing(BossProp p, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, float height) {
        float r = p.radius();
        if (r <= 0.1F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int segs = Math.max(16, (int) (r * 5));
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float bump0 = height + 0.3F * Mth.sin(i * 1.7F), bump1 = height + 0.3F * Mth.sin((i + 1) * 1.7F);
            float u0 = (i % 4) / 4F, u1 = u0 + 0.25F;
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            vert(vc, m, n, x0, 0, z0, u0, 1, light, nx, nz);
            vert(vc, m, n, x1, 0, z1, u1, 1, light, nx, nz);
            vert(vc, m, n, x1, bump1, z1, u1, 0, light, nx, nz);
            vert(vc, m, n, x0, bump0, z0, u0, 0, light, nx, nz);
        }
    }

    /**
     * Drawn whenever it is near enough to see. Most props are drawn far bigger than their 0.6-block box (the mash wave's ring,
     * the giant potato, the piñata 6x on its 30-block rope, the monoliths, the sombreros, a rolling spud as tall as two of
     * you), so the box's own frustum test (and Embeddium's, which goes by the chunk sections the box is in) dropped them
     * whenever their middle went out of view or into a section it thought hidden: they flickered at the screen's edges.
     * There are only ever a few dozen of them.
     */
    @Override
    public boolean shouldRender(BossProp p, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        double far = p.kind() == BossProp.Kind.GIANT_SPUD || p.kind() == BossProp.Kind.MASHER || p.kind() == BossProp.Kind.CASTILLO ? 192 : 160;
        return p.distanceToSqr(x, y, z) < far * far;
    }

    @Override
    public ResourceLocation getTextureLocation(BossProp p) { return MASH; }

    /** The piñata's rope, straight up out of sight from the top of it (model space: the item's 16 units are a block): a thin
     *  solid post, lit (it was a debug-quads strip, which a shader pack or the translucency sort could drop or smear). */
    private static void drawRope(PoseStack pose, MultiBufferSource buffers, float up, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        float w = 0.03F;
        float[][] sides = {{w, 0, 1, 0}, {-w, 0, -1, 0}, {0, w, 0, 1}, {0, -w, 0, -1}};
        for (float[] sd : sides) {
            // each face of the post: the two corners on this side, facing out
            float ax = sd[0] != 0 ? sd[0] : -w, az = sd[1] != 0 ? sd[1] : -w;
            float bx = sd[0] != 0 ? sd[0] : w, bz = sd[1] != 0 ? sd[1] : w;
            float nx = sd[2], nz = sd[3];
            rope(vc, m, n, ax, 0.45F, az, 150, 118, 72, light, nx, nz);
            rope(vc, m, n, bx, 0.45F, bz, 150, 118, 72, light, nx, nz);
            rope(vc, m, n, bx, up, bz, 120, 94, 58, light, nx, nz);
            rope(vc, m, n, ax, up, az, 120, 94, 58, light, nx, nz);
        }
    }

    private static final ResourceLocation WHITE = com.groid.papon.PaponMod.id("textures/misc/white.png");

    private static void rope(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int r, int g, int b, int light, float nx, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, 0, nz).endVertex();
    }
}
