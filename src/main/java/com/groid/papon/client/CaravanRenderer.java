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
import com.groid.papon.client.mesh.SkinnedMesh;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The van. Translucent (its windows are glass you can see Papon through), headlights and tail
 * lamps glow while the engine runs (caravan_glowmask.png), and the wheels, steering and body
 * lean are driven here from the van's motion rather than by animations.
 *
 * The Skyline (2026-10-06, tools/gen_skyline.py) is a triangle mesh skinned onto the same rig's bones (meshes/skyline.json:
 * the rig has no cubes, its bones' matrices are caught as GeckoLib walks them, as client/mesh/MeshCrowdRenderer does), drawn
 * in passes: the body, the interior and the wheels; the glass, translucent; while the engine runs, what is lit
 * (skyline_lamps.png: the lamps, the gauges, the display) and their flares, the brake lamps brighter as it slows and the
 * reverse lamps white as it backs up, the tach and speedo needles live off the engine (CaravanSounds.rpm); and over its
 * paint and glass a clear coat: the sky and a highlight reflected in it, stronger at a glancing angle, so the black paint
 * reads as gloss and not as a hole in the picture.
 */
public class CaravanRenderer extends GeoEntityRenderer<CaravanEntity> {
    private static final ResourceLocation SKY_LAMPS = PaponMod.id("textures/entity/skyline_lamps.png");
    private static final int FULL = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
    private final java.util.Map<String, Matrix4f> poses = new java.util.HashMap<>();
    private final java.util.Map<String, Matrix3f> normalPoses = new java.util.HashMap<>();
    private float[] px = new float[0], nx = new float[0];
    private final Vector4f p4 = new Vector4f();
    private final Vector3f q3 = new Vector3f();

    public CaravanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 1.6F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack pose, CaravanEntity van, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                               VertexConsumer buffer, float partialTick, int light, int overlay) {
                if (van.isEngineOn() && !van.isSkyline()) super.render(pose, van, model, renderType, buffers, buffer, partialTick, light, overlay);
            }
        });
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        if (animatable != null && animatable.isSkyline()) {
            poses.put(bone.getName(), new Matrix4f(pose.last().pose()));
            normalPoses.put(bone.getName(), new Matrix3f(pose.last().normal()));
        }
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
    }

    @Override
    public void renderFinal(PoseStack pose, CaravanEntity van, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                            float partialTick, int light, int overlay, float r, float g, float b, float a) {
        super.renderFinal(pose, van, model, buffers, buffer, partialTick, light, overlay, r, g, b, a);
        if (van.isSkyline() && !van.isInvisible()) drawSkyline(van, buffers, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    public void doPostRenderCleanup() {
        super.doPostRenderCleanup();
        poses.clear();
        normalPoses.clear();
    }

    // ------------------------------------------------------------------ the Skyline

    private void drawSkyline(CaravanEntity van, MultiBufferSource buffers, float partial, int light, int overlay, float r, float g, float b, float a) {
        SkinnedMesh mesh = SkinnedMesh.get("skyline");
        if (mesh == null) return;
        Matrix4f[] m = new Matrix4f[mesh.bones.length];
        Matrix3f[] n = new Matrix3f[mesh.bones.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = poses.get(mesh.bones[i]);
            n[i] = normalPoses.get(mesh.bones[i]);
            if (m[i] == null) return;
        }
        int count = mesh.vertexCount();
        if (px.length < count * 3) {
            px = new float[count * 3];
            nx = new float[count * 3];
        }
        for (int v = 0; v < count; v++) {
            int j = mesh.joints[v * 2];
            m[j].transform(p4.set(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2], 1F));
            n[j].transform(q3.set(mesh.normals[v * 3], mesh.normals[v * 3 + 1], mesh.normals[v * 3 + 2])).normalize();
            px[v * 3] = p4.x; px[v * 3 + 1] = p4.y; px[v * 3 + 2] = p4.z;
            nx[v * 3] = q3.x; nx[v * 3 + 1] = q3.y; nx[v * 3 + 2] = q3.z;
        }
        boolean ghost = van.isGhost();
        ResourceLocation tex = Model.SKY_TEX;
        group(buffers.getBuffer(ghost ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex)), mesh, "opaque", light, overlay, r, g, b, a);
        group(buffers.getBuffer(RenderType.entityTranslucent(tex)), mesh, "glass", light, overlay, r, g, b, a);
        VertexConsumer lit = null;
        boolean engine = van.isEngineOn() && van.squash() > 0.95F;
        if (engine && van.engineTicks > 3) {
            lit = buffers.getBuffer(RenderType.eyes(SKY_LAMPS));
            group(lit, mesh, "lamps", FULL, overlay, 1, 1, 1, 1);
            group(lit, mesh, "gauges", FULL, overlay, 1, 1, 1, 1);
            if (van.braking) group(lit, mesh, "brake", FULL, overlay, 1, 1, 1, 1);
        }
        if (van.underglowAge(partial) >= 0 && van.squash() > 0.95F) {
            if (lit == null) lit = buffers.getBuffer(RenderType.eyes(SKY_LAMPS));
            group(lit, mesh, "underglow", FULL, overlay, 1, 1, 1, 1);
        }
        VertexConsumer glow = buffers.getBuffer(PaponRenderTypes.glow());
        clearCoat(glow, mesh, light, partial, ghost ? 0.4F : 1F);
        Matrix4f body = poses.get("body");
        if (body != null) {
            needles(glow, body, van, engine);
            if (engine) flares(glow, body, van);
            if (SkylineCockpit.driving(van)) hands(body, poses.get("steering_wheel"), buffers, light);
        }
    }

    /** gen_skyline's steering wheel: its centre and the rim's radius (px). */
    private static final float WHEEL_X = -8F, WHEEL_Y = 14.1F, WHEEL_Z = -4.4F, WHEEL_R = 3.05F;

    /**
     * Your hands on the wheel (2026-10-06): from your own eyes behind the Skyline's wheel, your arms (your skin, sleeves and
     * all) reach from your shoulders to the rim at ten to two, turning with it as you steer, instead of a held item hanging
     * over the dash (ClientEvents.onHand hides that). Built in the camera's space: each arm's long axis from the shoulder
     * (below and beside your eyes, which are the origin here) to its grip, its back turned down.
     */
    private void hands(Matrix4f body, Matrix4f wheel, MultiBufferSource buffers, int light) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (wheel == null || mc.player == null) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) return;
        var model = pr.getModel();
        ResourceLocation skin = mc.player.getSkinTextureLocation();
        Vector3f o = at(body, 0, 16, 0);
        Vector3f left = at(body, 1, 16, 0).sub(o).normalize(), up = at(body, 0, 17, 0).sub(o).normalize(), fwd = at(body, 0, 16, -1).sub(o).normalize();
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(skin));
        VertexConsumer sleeve = buffers.getBuffer(RenderType.entityTranslucent(skin));
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        for (int side : new int[]{-1, 1}) {                 // -1: your right hand (the car's right, the model's -x)
            double a = Math.toRadians(16);
            float gx = WHEEL_X + side * WHEEL_R * (float) Math.cos(a), gy = WHEEL_Y + WHEEL_R * (float) Math.sin(a);
            wheel.transform(p4.set(-gx / 16F, gy / 16F, WHEEL_Z / 16F, 1F));
            Vector3f grip = new Vector3f(p4.x, p4.y, p4.z);
            Vector3f shoulder = new Vector3f(up).mul(-0.34F).add(new Vector3f(left).mul(side * 0.3F)).add(new Vector3f(fwd).mul(-0.05F));
            Vector3f y = new Vector3f(grip).sub(shoulder).normalize();
            Vector3f x = new Vector3f(left).sub(new Vector3f(y).mul(left.dot(y))).normalize();
            Vector3f z = new Vector3f(x).cross(y);
            PoseStack ps = new PoseStack();
            ps.translate(grip.x, grip.y, grip.z);
            ps.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(x, y, z)));
            ps.translate(side < 0 ? 1 / 16F : -1 / 16F, -9.6F / 16F, 0);
            net.minecraft.client.model.geom.ModelPart arm = side < 0 ? model.rightArm : model.leftArm;
            net.minecraft.client.model.geom.ModelPart cuff = side < 0 ? model.rightSleeve : model.leftSleeve;
            for (var part : new net.minecraft.client.model.geom.ModelPart[]{arm, cuff}) {
                if (!part.visible) continue;
                var saved = part.storePose();
                part.setPos(0, 0, 0);
                part.setRotation(0, 0, 0);
                part.render(ps, part == arm ? solid : sleeve, light, overlay);
                part.loadPose(saved);
            }
        }
    }

    private void group(VertexConsumer out, SkinnedMesh mesh, String name, int light, int overlay, float r, float g, float b, float a) {
        int[] run = mesh.groups.get(name);
        if (run == null) return;
        int[] idx = mesh.indices;
        for (int i = run[0], end = run[0] + run[1]; i < end; i++) {
            int v = idx[i];
            out.vertex(px[v * 3], px[v * 3 + 1], px[v * 3 + 2]).color(r, g, b, a).uv(mesh.uvs[v * 2], mesh.uvs[v * 2 + 1])
                    .overlayCoords(overlay).uv2(light).normal(nx[v * 3], nx[v * 3 + 1], nx[v * 3 + 2]).endVertex();
        }
    }

    /**
     * The clear coat: for each vertex of the paint and the glass, the view reflected off it (positions here are relative to
     * the camera, already turned by its rotation), how much of the sky that reflection sees (a bright band at the horizon,
     * the sky over it, little from the ground), a sharp highlight where it meets the key light, weighted by Fresnel (a
     * mirror at a glancing angle, little head on), dimmed with the light where the car is. Added over the paint.
     */
    private void clearCoat(VertexConsumer out, SkinnedMesh mesh, int light, float partial, float k) {
        int[] run = mesh.groups.get("gloss");
        if (run == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        Quaternionf view = new Quaternionf(mc.gameRenderer.getMainCamera().rotation()).conjugate();
        Vector3f up = view.transform(new Vector3f(0, 1, 0));
        Vector3f sun = view.transform(new Vector3f(0.35F, 0.85F, 0.4F).normalize());
        float sky = mc.level == null ? 1 : mc.level.getSkyDarken(partial);
        float amb = light == FULL ? 1F : Math.max(net.minecraft.client.renderer.LightTexture.block(light) / 15F * 0.8F,
                net.minecraft.client.renderer.LightTexture.sky(light) / 15F * sky);
        amb *= k;
        if (amb < 0.02F) return;
        int[] idx = mesh.indices;
        for (int i = run[0], end = run[0] + run[1]; i < end; i++) {
            int v = idx[i];
            float x = px[v * 3], y = px[v * 3 + 1], z = px[v * 3 + 2];
            float len = Math.max(1e-4F, Mth.sqrt(x * x + y * y + z * z));
            float vx = -x / len, vy = -y / len, vz = -z / len;           // toward the eye
            float nx0 = nx[v * 3], ny0 = nx[v * 3 + 1], nz0 = nx[v * 3 + 2];
            float ndv = nx0 * vx + ny0 * vy + nz0 * vz;
            if (ndv < 0) {                                                  // seen from its back (the inside of the glass)
                nx0 = -nx0; ny0 = -ny0; nz0 = -nz0;
                ndv = -ndv;
            }
            float rx = 2 * ndv * nx0 - vx, ry = 2 * ndv * ny0 - vy, rz = 2 * ndv * nz0 - vz;
            float e = rx * up.x + ry * up.y + rz * up.z;                    // the reflection's elevation
            float env = e > 0 ? 0.35F + 0.35F * e : 0.06F;
            env += 0.55F * (float) Math.exp(-(e * e) / 0.012F);             // the horizon's bright band
            float s = Math.max(0, rx * sun.x + ry * sun.y + rz * sun.z);
            s = s * s;
            s = s * s;
            s = s * s;
            s = s * s * s;                                                  // ^48: a tight highlight
            float om = 1 - ndv;
            float fres = 0.04F + 0.96F * om * om * om * om * om;
            float in = Math.min(1F, (fres * env * 0.95F + 0.035F * env + s * 1.5F) * amb);
            out.vertex(x, y, z).color(200, 214, 255, (int) (in * 255)).endVertex();
        }
    }

    /** A point of the model (gen_skyline's pixels) through the body bone's matrix. */
    private Vector3f at(Matrix4f body, float x, float y, float z) {
        body.transform(p4.set(-x / 16F, y / 16F, z / 16F, 1F));
        return new Vector3f(p4.x, p4.y, p4.z);
    }

    /**
     * The tach and the speedo's needles over the gauge face (gen_skyline: the face from x -3.8 to -12.2, y 15.9 to 19.3,
     * at z -6.75; the tach's centre at (128, 66) of its 256x128 picture, radius 54, 0 to 10,000 rpm from 225 degrees round
     * 270 clockwise; the speedo at (212, 70), radius 40, 0 to 320 km/h).
     */
    private void needles(VertexConsumer out, Matrix4f body, CaravanEntity van, boolean engine) {
        float rpm = engine ? CaravanSounds.rpm(van) : 0;
        double kmh = CaravanSounds.speedOf(van) * 20 * 3.6 * 2.6;          // the car's own scale: it feels this fast
        needle(out, body, 128, 66, 54, (float) Mth.clamp(rpm / 10000.0, 0, 1.05), engine);
        needle(out, body, 212, 70, 40, (float) Mth.clamp(kmh / 320.0, 0, 1), engine);
    }

    private void needle(VertexConsumer out, Matrix4f body, float cx, float cy, float r, float k, boolean lit) {
        double ang = Math.toRadians(225 - 270 * k);
        float ux = (float) Math.cos(ang), uy = (float) Math.sin(ang);
        Vector3f c = gauge(body, cx / 256F, cy / 128F);
        Vector3f t = gauge(body, (cx + ux * r * 0.9F) / 256F, (cy - uy * r * 0.9F) / 128F);
        // its width across the needle, in the face's plane
        Vector3f across = gauge(body, (cx + uy * 2.2F) / 256F, (cy + ux * 2.2F) / 128F).sub(c);
        int alpha = lit ? 255 : 90;
        out.vertex(c.x - across.x, c.y - across.y, c.z - across.z).color(255, 40, 24, alpha).endVertex();
        out.vertex(c.x + across.x, c.y + across.y, c.z + across.z).color(255, 40, 24, alpha).endVertex();
        out.vertex(t.x, t.y, t.z).color(255, 90, 60, alpha).endVertex();
        out.vertex(t.x, t.y, t.z).color(255, 90, 60, alpha).endVertex();
    }

    private Vector3f gauge(Matrix4f body, float u, float v) { return at(body, -3.8F - u * 8.4F, 19.3F - v * 3.4F, -6.7F); }

    /**
     * Flares on the lamps while the engine runs: soft discs facing the camera, seen from the side the lamp faces; the
     * brake lamps flare up as it slows, the reverse lamps white as it backs up.
     */
    private void flares(VertexConsumer out, Matrix4f body, CaravanEntity van) {
        Vector3f nose = at(body, 0, 11.4F, -46F), tail = at(body, 0, 15F, 47F), mid = at(body, 0, 12, 0);
        Vector3f fwd = new Vector3f(nose).sub(mid).normalize();
        float front = Math.max(0, -nose.dot(fwd) / Math.max(nose.length(), 1e-3F));
        float back = Math.max(0, tail.dot(fwd) / Math.max(tail.length(), 1e-3F));
        if (front > 0.05F) {
            for (float x : new float[]{-12.6F, -9.4F, 9.4F, 12.6F}) flare(out, at(body, x, 11.4F, -45.9F), 0.22F, 255, 248, 225, (int) (150 * front));
        }
        if (back > 0.05F) {
            boolean brake = van.braking;
            for (float x : new float[]{-12.4F, -6.6F, 6.6F, 12.4F}) {
                flare(out, at(body, x, 15F, 47.4F), brake ? 0.3F : 0.17F, 255, 30, 30, (int) ((brake ? 210 : 90) * back));
                if (van.reversing && Math.abs(x) < 7) flare(out, at(body, x, 15F, 47.5F), 0.2F, 255, 255, 255, (int) (200 * back));
            }
            if (brake) flare(out, at(body, 0, 23.6F, 44.6F), 0.3F, 255, 30, 30, (int) (120 * back));
        }
    }

    /** A soft disc of light at {@code c} (camera space), radius {@code r} blocks, facing the camera: bright middle, clear rim. */
    private static void flare(VertexConsumer out, Vector3f c, float r, int cr, int cg, int cb, int a) {
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            out.vertex(c.x, c.y, c.z).color(cr, cg, cb, a).endVertex();
            out.vertex(c.x, c.y, c.z).color(cr, cg, cb, a).endVertex();
            out.vertex(c.x + (float) Math.cos(a1) * r, c.y + (float) Math.sin(a1) * r, c.z).color(cr, cg, cb, 0).endVertex();
            out.vertex(c.x + (float) Math.cos(a0) * r, c.y + (float) Math.sin(a0) * r, c.z).color(cr, cg, cb, 0).endVertex();
        }
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
        // (2026-10-05, the user: "the skyline transformation is far too dark") in his upgrade it is the star of the stage: lit as
        // if under the stage lights, whatever the night
        if (keyLit(van)) light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        super.render(van, yaw, partialTick, pose, buffers, light);
        if (van.isSkyline() && van.squash() > 0.95F) {
            underglow(van, partialTick, pose, buffers);
            if (van.isEngineOn()) headlights(van, partialTick, pose, buffers);
        }
    }

    /** Whether the boss is upgrading this van right now (P3_CRUSH, P3_SHOWCAR). */
    private static boolean keyLit(CaravanEntity van) {
        for (com.groid.papon.boss.PaponBossEntity b : van.level().getEntitiesOfClass(com.groid.papon.boss.PaponBossEntity.class, van.getBoundingBox().inflate(24), e -> true)) {
            com.groid.papon.boss.BossState s = b.getBossState();
            if (s == com.groid.papon.boss.BossState.P3_CRUSH || s == com.groid.papon.boss.BossState.P3_SHOWCAR) return true;
        }
        return false;
    }

    /**
     * The Skyline's purple underglow: a pool of light on the ground under it, brightest under the middle, breathing.
     * Switched on (CaravanEntity.setUnderglow) it does not just appear: the tubes stutter on from the front bumper back
     * to the rear (0.6 s), flash bright as the last one catches, and settle.
     */
    private static void underglow(CaravanEntity van, float partial, PoseStack pose, MultiBufferSource buffers) {
        float age = van.underglowAge(partial);
        if (age < 0) return;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-Mth.rotLerp(partial, van.yBodyRotO, van.yBodyRot)));
        org.joml.Matrix4f m = pose.last().pose();
        float pulse = 0.85F + 0.15F * Mth.sin((van.tickCount + partial) * 0.2F);
        // the switch-on: a stutter sweeping front to back (12 ticks), a flare as it completes, settling by 30
        float flare = age < 12 ? 0 : Math.max(0, 1 - (age - 12) / 18F);
        pulse *= 1 + 0.9F * flare;
        float hw = (float) CaravanEntity.HALF_WIDTH + 0.6F, hl = (float) CaravanEntity.HALF_LENGTH + 0.4F;
        int n = 10;
        for (int j = 0; j < n; j++) {
            // row j from the nose (+Z in this pose) back
            float row = 1;
            if (age < 12) {
                float lit = age / 12F * (n + 2) - j;               // how long this row has had to catch
                if (lit <= 0) continue;
                long seed = (long) j * 31 + (long) (age * 2.5F);
                boolean on = lit > 2.5F || (seed * 2654435761L >>> 7) % 3 != 0;   // flickering as it catches
                if (!on) continue;
                row = Math.min(1F, 0.35F + lit * 0.3F);
            }
            for (int i = 0; i < n; i++) {
                float x0 = -hw + 2 * hw * i / n, x1 = -hw + 2 * hw * (i + 1) / n;
                float z0 = hl - 2 * hl * (j + 1) / n, z1 = hl - 2 * hl * j / n;
                quadGlow(vc, m, x0, z0, x1, z1, hw, hl, pulse * row);
            }
        }
        pose.popPose();
    }

    /**
     * The Skyline's headlamps throw light: two pale cones ahead of it while the engine runs (it lives at night in his
     * coliseum). They stutter on with the engine (two blinks) and settle.
     */
    private static void headlights(CaravanEntity van, float partial, PoseStack pose, MultiBufferSource buffers) {
        int on = van.engineTicks;
        if (on < 6 && (on == 1 || on == 2 || on == 4)) return;
        float k = on < 6 ? 0.6F : 1F;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-Mth.rotLerp(partial, van.yBodyRotO, van.yBodyRot)));
        org.joml.Matrix4f m = pose.last().pose();
        for (float x : new float[]{-0.69F, 0.69F}) {
            // (this pose is turned by -yaw, half a turn from the model's: the nose is +Z here)
            cone(vc, m, x, 0.71F, 2.86F, x * 1.6F, 0.1F, 11.5F, 0.16F, 1.9F, (int) (70 * k));
        }
        pose.popPose();
    }

    /** A cone of light from (x0, y0, z0), radius r0, to (x1, y1, z1), radius r1, fading out along it; both windings. */
    private static void cone(VertexConsumer vc, org.joml.Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float r0, float r1, int alpha) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] q = {{x0 + c0 * r0, y0 + s0 * r0, z0, alpha}, {x0 + c1 * r0, y0 + s1 * r0, z0, alpha},
                    {x1 + c1 * r1, y1 + s1 * r1 * 0.6F, z1, 0}, {x1 + c0 * r1, y1 + s0 * r1 * 0.6F, z1, 0}};
            for (int w = 0; w < 2; w++) {
                for (int j = 0; j < 4; j++) {
                    float[] p = q[w == 0 ? j : 3 - j];
                    vc.vertex(m, p[0], p[1], p[2]).color(235, 238, 255, (int) p[3]).endVertex();
                }
            }
        }
    }

    private static void quadGlow(VertexConsumer vc, org.joml.Matrix4f m, float x0, float z0, float x1, float z1, float hw, float hl, float pulse) {
        float[][] c = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
        for (float[] p : c) {
            float d = (float) Math.min(1, Math.hypot(p[0] / hw, p[1] / hl));
            int a = (int) Math.min(255, 150 * pulse * (1 - d * d));
            vc.vertex(m, p[0], 0.06F, p[1]).color(170, 70, 255, Math.max(0, a)).endVertex();
        }
    }

    @Override
    protected float getDeathMaxRotation(CaravanEntity entity) { return 0; }

    static final class Model extends GeoModel<CaravanEntity> {
        private static final ResourceLocation GEO = PaponMod.id("geo/entity/caravan.geo.json");
        private static final ResourceLocation TEX = PaponMod.id("textures/entity/caravan.png");
        private static final ResourceLocation ANIM = PaponMod.id("animations/entity/caravan.animation.json");
        // Ascended Papon's Skyline (tools/gen_skyline.py): the same rig, so everything below drives it too
        private static final ResourceLocation SKY_GEO = PaponMod.id("geo/entity/skyline.geo.json");
        static final ResourceLocation SKY_TEX = PaponMod.id("textures/entity/skyline.png");
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
    public boolean shouldShowName(CaravanEntity van) { return van.hasCustomName() && super.shouldShowName(van); }
}
