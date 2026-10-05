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
                drawRope(pose, buffers, 30F / 6F);
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
                // Ignis's head, tumbling as it flies, then where it fell, tipped on its side, facing the way it was thrown
                stack = new ItemStack(ModItems.FX_IGNIS_HEAD.get());
                boolean flying = p.getDeltaMovement().lengthSqr() > 1e-4;
                pose.mulPose(Axis.YP.rotationDegrees(-p.getYRot()));
                if (flying) pose.mulPose(Axis.XP.rotationDegrees(age * 22F));
                else pose.mulPose(Axis.ZP.rotationDegrees(14F));
                pose.scale(1.5F, 1.5F, 1.5F);
                pose.translate(-0.5, flying ? -0.7 : 0, -0.5);
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
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
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
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
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
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
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
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
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

    /** The mash wave is a ring far wider than its entity: draw it whenever it is near enough to see. */
    @Override
    public boolean shouldRender(BossProp p, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (p.kind() == BossProp.Kind.GIANT_SPUD || p.kind() == BossProp.Kind.MASHER) return p.distanceToSqr(x, y, z) < 192 * 192;
        if (p.kind() == BossProp.Kind.MASH || p.kind() == BossProp.Kind.SALSA_RING || p.kind() == BossProp.Kind.BAND_WAVE
                || p.kind() == BossProp.Kind.SALSA_WAVE || p.kind() == BossProp.Kind.SOMBRERO) return p.distanceToSqr(x, y, z) < 128 * 128;
        return super.shouldRender(p, frustum, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(BossProp p) { return MASH; }

    /** The piñata's rope, straight up out of sight from the top of it (model space: the item's 16 units are a block). */
    private static void drawRope(PoseStack pose, MultiBufferSource buffers, float up) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = pose.last().pose();
        float w = 0.03F;
        for (int side = 0; side < 2; side++) {
            float dx = side == 0 ? w : 0, dz = side == 0 ? 0 : w;
            for (int face = 0; face < 2; face++) {
                float s = face == 0 ? 1 : -1;
                vc.vertex(m, -dx * s, 0.45F, -dz * s).color(150, 118, 72, 255).endVertex();
                vc.vertex(m, dx * s, 0.45F, dz * s).color(150, 118, 72, 255).endVertex();
                vc.vertex(m, dx * s, up, dz * s).color(120, 94, 58, 255).endVertex();
                vc.vertex(m, -dx * s, up, -dz * s).color(120, 94, 58, 255).endVertex();
            }
        }
    }
}
