package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.client.mesh.SkinnedMesh;
import com.groid.papon.entity.MakimaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makima (entity/MakimaEntity): her rig-only GeckoLib model (geo/entity/makima_mesh) runs the animations, and the mesh
 * (meshes/makima.json, tools/gen_makima_mesh.py: four bones a vertex) is skinned on the CPU with each bone's pose as it is
 * drawn, the way mesh/MeshCrowdRenderer does it. With someone at her (2026-10-07):
 *   her body takes every stroke   jolted along her body ({@link #applyRotations}), riding up and down on top; the back
 *                                 arching, the head thrown back, the hips pushed, the knees rocking ({@link Model#moving})
 *   what hangs moves after it     her braid (three links), bust and tie on damped springs ({@link Jiggle}) driven by the
 *                                 stroke, a beat behind it; at the climax she shudders and arches
 *   her mouth waters              strands of drool off her lips that stretch, snap and fall, a little puddle where they
 *                                 land ({@link Drool}); hearts now and then, never in the rider's face
 * Standing, her head and neck follow you; walking (she follows you) the stride runs at her speed.
 * (2026-10-07) Her face ({@link MakimaFace}: moods, blinks, her eyes on you, her look and the tilt of her head) is put on the
 * rest positions before skinning, her irises on bones placed off the head's pose here ({@link #aim} finds where you are in
 * her head's space), her blush drawn over her cheeks after the body; skinning is dual quaternion ({@link #skin}) so her
 * elbows, knees and knuckles bend without pinching; her fingers squeeze with each stroke and grip in the climax.
 */
public class MakimaRenderer extends GeoEntityRenderer<MakimaEntity> {
    /**
     * Her lips in the mesh's own space (blocks, as SkinnedMesh loads it: the geo's "mouth" locator / 16, x mirrored), put through
     * the head's pose exactly as the mesh is. (GeckoLib's tracked world matrix put it behind her head: the old drool fell from
     * the back of her hair.)
     */
    private static final Vector4f MOUTH = new Vector4f(0F, 27.45F / 16, -1.229F / 16, 1F);
    private static final ResourceLocation WHITE = PaponMod.id("textures/misc/white.png");
    /** How far her lower lip drops with her mouth wide open (blocks: tools/makima_face.py OPEN_H, scaled to her size). */
    private static final float LIP_DROP = 0.0062F * 1.2F;

    private final Map<String, Matrix4f> poses = new HashMap<>();
    /** The pose stack's top as render() was handed it (her position relative to the camera, in view space). */
    private Matrix4f root;
    private float[] px = new float[0];
    private float[] nx = new float[0];
    /** The rest positions with her face put on (MakimaFace.apply), and each bone's dual quaternion (real, dual: 8 floats). */
    private float[] rest = new float[0];
    private float[] dq = new float[0];
    private final Quaternionf quat = new Quaternionf();
    private final Vector3f q = new Vector3f();
    private static final Map<MakimaEntity, Jiggle> JIGGLE = new WeakHashMap<>();
    private static final Map<MakimaEntity, Drool> DROOL = new WeakHashMap<>();

    public MakimaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.4F;
    }

    /** The stroke's motion now: -1..1 (PillowClient.thrust on her clock), 1 held deep at the end. */
    static float thrust(MakimaEntity e, float partial) { return MakimaRider.thrust(e, partial); }

    static class Model extends GeoModel<MakimaEntity> {
        private static final ResourceLocation GEO = PaponMod.id("geo/entity/makima_mesh.geo.json");
        private static final ResourceLocation TEX = PaponMod.id("textures/entity/makima_mesh.png");
        private static final ResourceLocation ANIM = PaponMod.id("animations/entity/makima.animation.json");

        @Override
        public ResourceLocation getModelResource(MakimaEntity e) { return GEO; }

        @Override
        public ResourceLocation getTextureResource(MakimaEntity e) { return TEX; }

        @Override
        public ResourceLocation getAnimationResource(MakimaEntity e) { return ANIM; }

        @Override
        public void setCustomAnimations(MakimaEntity e, long instanceId, AnimationState<MakimaEntity> state) {
            float partial = state.getPartialTick();
            Jiggle j = JIGGLE.computeIfAbsent(e, k -> new Jiggle());
            MakimaFace.State face = MakimaFace.state(e);
            face.update(e, partial);
            // her tilt and the dip of her chin (MakimaFace: her look), the neck taking part
            add("neck", face.chin * 0.35F, 0, face.tilt * 0.3F);
            add("head", face.chin * 0.65F, 0, face.tilt * 0.7F);
            if (e.getFirstPassenger() instanceof Player && e.act() != null) {
                moving(e, partial, j);
                return;
            }
            j.settle(e.tickCount + partial);
            sway(j);
            // standing (or walking after you), her head and neck follow you
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (data == null) return;
            float yaw = Mth.clamp(data.netHeadYaw(), -70, 70), pitch = Mth.clamp(data.headPitch(), -40, 50);
            add("neck", pitch * 0.35F, yaw * 0.35F, 0);
            add("head", pitch * 0.55F, yaw * 0.55F, 0);
        }

        /**
         * Her fingers closed further than the pose holds them (degrees of curl at the middle joint): squeezing with each
         * stroke, gripping in the climax. About each finger's own curl axis (meshes/makima.json "hands"), a small addition.
         */
        private void clench(float deg) {
            if (deg < 0.2F) return;
            SkinnedMesh mesh = SkinnedMesh.get("makima");
            MakimaFace.Rig rig = mesh == null ? null : MakimaFace.rig(mesh);
            if (rig == null) return;
            for (var c : rig.curl.entrySet()) {
                String b = c.getKey();
                float k = b.contains("1_") ? 0.75F : b.contains("3_") ? 0.7F : 1F;
                if (b.startsWith("f_thumb")) k *= 0.6F;
                float[] a = c.getValue();
                add(b, a[0] * deg * k, a[1] * deg * k, a[2] * deg * k);
            }
        }

        /**
         * Her body taking every stroke (on top of the position's loop): the back arching, the head thrown back, the hips
         * pushed, the knees rocking, quick breaths; the climax a shudder and a long arch.
         */
        private void moving(MakimaEntity e, float partial, Jiggle j) {
            float t = e.tickCount + partial, s = e.stroke(partial);
            float th = thrust(e, partial), sq = Math.max(0, th), lag = PillowClient.thrust(s - 2.5F);
            float breath = Mth.sin(t * (0.5F + 0.12F * e.tempo()));
            float hold = e.holding() ? 1 : 0, shake = MakimaRider.tremor(e, t);
            j.drive(e, t, th);
            switch (e.posture()) {
                case MISSIONARY -> {
                    add("spine", -4 * sq, 0, 0);
                    add("chest", -6 * sq + 1.5F * breath - 14 * hold, 0, 0);
                    add("neck", -6 * sq - 10 * hold, 0, 0);
                    add("head", -8 * sq - 12 * hold + 4 * shake, 0, 0);
                    add("leg_l", 6 * lag, 0, -4 * lag);
                    add("leg_r", 6 * lag, 0, 4 * lag);
                    add("forearm_l", -8 * sq, 0, 0);
                    add("forearm_r", -8 * sq, 0, 0);
                }
                case DOGGY, BENT -> {
                    add("torso", 4 * sq, 0, 0);
                    add("spine", -3 * sq, 0, 0);
                    add("chest", -6 * sq + 1.5F * breath - 12 * hold, 0, 0);
                    add("neck", -5 * sq - 10 * hold, 0, 0);
                    add("head", -10 * sq - 14 * hold + 5 * shake, 0, 0);
                    add("arm_l", 4 * sq, 0, 0);
                    add("arm_r", 4 * sq, 0, 0);
                }
                case COWGIRL, REVERSE -> {
                    float roll = Mth.sin(s * 0.45F);
                    add("torso", 6 * th, 0, 4 * roll);
                    add("spine", -3 * th, 0, -2 * roll);
                    add("chest", -6 * Math.abs(th) + 1.5F * breath - 16 * hold, 0, 0);
                    add("neck", -4 * sq - 10 * hold, 0, 0);
                    add("head", -10 * sq - 14 * hold + 5 * shake, 0, 0);
                    add("forearm_l", -6 * sq, 0, 0);
                    add("forearm_r", -6 * sq, 0, 0);
                    add("leg_l", 3 * th, 0, 0);
                    add("leg_r", 3 * th, 0, 0);
                }
                case CARRY -> {
                    add("torso", -5 * th, 0, 0);
                    add("chest", -4 * sq + 1.5F * breath - 14 * hold, 0, 0);
                    add("neck", -4 * sq - 8 * hold, 0, 0);
                    add("head", -8 * sq - 14 * hold + 5 * shake, 0, 0);
                    add("shin_l", 8 * lag, 0, 0);
                    add("shin_r", 8 * lag, 0, 0);
                }
                default -> {}
            }
            // her hands squeeze with each stroke (pressed flat on the ground, less), grip in the climax
            float squeeze = e.posture() == MakimaEntity.Position.DOGGY ? 0.4F : 1F;
            clench(e.holding() ? 26 : e.climax() > 0 ? 16 + 8 * sq : 7 * sq * squeeze);
            sway(j);
        }

        /** The springs into the bones: the braid's links, the bust, the tie. */
        private void sway(Jiggle j) {
            add("braid", 10 * j.braid[0], 0, 7 * j.side[0]);
            add("braid2", 14 * j.braid[1], 0, 10 * j.side[1]);
            add("braid3", 18 * j.braid[2], 0, 14 * j.side[2]);
            for (String b : new String[]{"breast_l", "breast_r"}) {
                add(b, 9 * j.bust, 0, 0);
                CoreGeoBone bone = getAnimationProcessor().getBone(b);
                if (bone != null) bone.setPosY(bone.getPosY() + 0.55F * j.bust);
            }
            add("tie", 14 * j.tie, 0, 6 * j.side[0]);
        }

        /** Adds degrees in the animation files' convention (GeckoLib flips X and Y from them). */
        private void add(String bone, float x, float y, float z) {
            CoreGeoBone b = getAnimationProcessor().getBone(bone);
            if (b == null) return;
            b.setRotX(b.getRotX() - x * Mth.DEG_TO_RAD);
            b.setRotY(b.getRotY() - y * Mth.DEG_TO_RAD);
            b.setRotZ(b.getRotZ() + z * Mth.DEG_TO_RAD);
        }
    }

    /**
     * What hangs off her, on damped springs (time in ticks, integrated per frame): each follows a target a beat behind it,
     * overshoots, settles. The bust follows the stroke; the braid's three links follow each other (so a wave runs down it)
     * and swing to the side too; the tie follows the bust.
     */
    static final class Jiggle {
        final float[] braid = new float[3], braidV = new float[3], side = new float[3], sideV = new float[3];
        float bust, bustV, tie, tieV;
        private float last = Float.NaN;

        /** Advances to time t (ticks) toward targets. */
        private float step(float t) {
            float dt = Float.isNaN(last) ? 0 : Mth.clamp(t - last, 0, 2);
            last = t;
            return dt;
        }

        void drive(MakimaEntity e, float t, float th) {
            float dt = step(t);
            float hard = 0.5F + 0.25F * e.tempo();
            integrate(dt, -0.9F * th * hard, -0.3F * Mth.sin(t * 0.21F) * hard);
        }

        void settle(float t) {
            integrate(step(t), 0, 0);
        }

        private void integrate(float dt, float bustTarget, float sideTarget) {
            // sub-steps for stability (stiff springs, frame times up to two ticks)
            int n = Math.max(1, (int) Math.ceil(dt / 0.25F));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                float a = 1.1F * (bustTarget - bust) - 0.22F * bustV;
                bustV += a * h;
                bust += bustV * h;
                float prev = bust * 0.8F, prevS = sideTarget;
                for (int k = 0; k < 3; k++) {
                    float ab = 0.55F * (prev - braid[k]) - 0.12F * braidV[k];
                    braidV[k] += ab * h;
                    braid[k] += braidV[k] * h;
                    float as = 0.35F * (prevS - side[k]) - 0.1F * sideV[k];
                    sideV[k] += as * h;
                    side[k] += sideV[k] * h;
                    prev = braid[k];
                    prevS = side[k];
                }
                float at = 0.7F * (bust * 0.7F - tie) - 0.14F * tieV;
                tieV += at * h;
                tie += tieV * h;
            }
            bust = Mth.clamp(bust, -1.6F, 1.6F);
            tie = Mth.clamp(tie, -1.5F, 1.5F);
            for (int k = 0; k < 3; k++) {
                braid[k] = Mth.clamp(braid[k], -1.8F, 1.8F);
                side[k] = Mth.clamp(side[k], -1.5F, 1.5F);
            }
        }
    }

    @Override
    protected void applyRotations(MakimaEntity e, PoseStack pose, float age, float yaw, float partial) {
        // the stroke, in the world's axes: along her body toward her head, or up and down astride / held up
        if (e.getFirstPassenger() instanceof Player && e.act() != null) {
            float th = thrust(e, partial), sq = Math.max(0, th), t = e.tickCount + partial;
            float shake = MakimaRider.tremor(e, t) * 0.012F;
            Vec3 f = e.facing();
            switch (e.posture()) {
                case COWGIRL, REVERSE -> pose.translate(shake, 0.055F * (1 - th), shake);
                case CARRY -> pose.translate(-f.x * 0.03 * sq + shake, 0.045F * (1 - th), -f.z * 0.03 * sq);
                default -> pose.translate(-f.x * 0.07 * sq + shake, 0.01F * sq, -f.z * 0.07 * sq + shake);
            }
        }
        super.applyRotations(e, pose, age, yaw, partial);
    }

    @Override
    public void render(MakimaEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = e.posture() == MakimaEntity.Position.STAND ? 0.4F : 0.85F;
        root = new Matrix4f(pose.last().pose());
        super.render(e, yaw, partial, pose, buffers, light);
        Drool d = DROOL.computeIfAbsent(e, k -> new Drool());
        d.render(e, partial, pose, buffers, light);
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                  float r, float g, float b, float a) {
        poses.put(bone.getName(), new Matrix4f(pose.last().pose()));
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
    }

    @Override
    public void renderFinal(PoseStack pose, MakimaEntity e, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                            float partial, int light, int overlay, float r, float g, float b, float a) {
        super.renderFinal(pose, e, model, buffers, buffer, partial, light, overlay, r, g, b, a);
        Matrix4f headPose = poses.get("head");
        SkinnedMesh mesh = SkinnedMesh.get("makima");
        MakimaFace.Rig rig = mesh == null ? null : MakimaFace.rig(mesh);
        MakimaFace.State face = MakimaFace.state(e);
        if (headPose != null && root != null) {
            // the bone poses are in view space (the camera's turn is on the stack): back to the world through the stack's
            // root as render() was handed it (her position, the camera's turn); her lower lip as far down as her mouth is open
            Drool d = DROOL.computeIfAbsent(e, k -> new Drool());
            Vector4f lip = new Vector4f(MOUTH);
            if (rig != null) {
                float k = face.openNow() * LIP_DROP;
                lip.add(-rig.ey[0] * k, -rig.ey[1] * k, -rig.ey[2] * k, 0);
            }
            Vector4f at = new Matrix4f(root).invert().mul(headPose).transform(lip);
            Vec3 o = e.getPosition(partial);
            d.mouth = new Vector4f((float) (at.x + o.x), (float) (at.y + o.y), (float) (at.z + o.z), 1F);
            hearts(e, d.mouth);
        }
        if (mesh == null || e.isInvisible()) return;
        if (rig != null && headPose != null) {
            // her irises: on the head's pose, slid where she looks; and where that is, for next frame
            Matrix4f eyes = new Matrix4f(headPose).translate(face.offX(rig) * rig.ex[0] + face.offY(rig) * rig.ey[0],
                    face.offX(rig) * rig.ex[1] + face.offY(rig) * rig.ey[1], face.offX(rig) * rig.ex[2] + face.offY(rig) * rig.ey[2]);
            poses.put("eye_l", eyes);
            poses.put("eye_r", eyes);
            aim(e, partial, rig, face, headPose);
        }
        Matrix4f[] m = new Matrix4f[mesh.bones.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = poses.get(mesh.bones[i]);
            if (m[i] == null) return;
        }
        int count = mesh.vertexCount(), k = mesh.influences;
        if (px.length < count * 3) {
            px = new float[count * 3];
            nx = new float[count * 3];
            rest = new float[count * 3];
        }
        float[] src = mesh.positions;
        if (rig != null) {
            MakimaFace.apply(rig, face, mesh.positions, rest);
            src = rest;
        }
        skin(mesh, m, src, count, k);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture));
        int[] idx = mesh.indices;
        int[] blush = mesh.groups.get("blush");
        int end = blush == null ? idx.length : blush[0];
        boolean[] lit = irises(mesh);
        int eyes = eyeLight(light);
        for (int t = 0; t < end; t += 3) {
            int lt = lit[t / 3] ? eyes : light;
            put(out, mesh, idx[t], lt, overlay, r, g, b, a);
            put(out, mesh, idx[t + 1], lt, overlay, r, g, b, a);
            put(out, mesh, idx[t + 2], lt, overlay, r, g, b, a);
            put(out, mesh, idx[t + 2], lt, overlay, r, g, b, a);
        }
        if (blush != null && face.blush > 0.02F) {
            // her blush over her cheeks, as strong as she is flushed
            VertexConsumer over = buffers.getBuffer(RenderType.entityTranslucent(mesh.texture));
            float alpha = Math.min(1, face.blush) * 0.9F * a;
            for (int t = blush[0], stop = blush[0] + blush[1]; t < stop; t += 3) {
                put(over, mesh, idx[t], light, overlay, r, g, b, alpha);
                put(over, mesh, idx[t + 1], light, overlay, r, g, b, alpha);
                put(over, mesh, idx[t + 2], light, overlay, r, g, b, alpha);
                put(over, mesh, idx[t + 2], light, overlay, r, g, b, alpha);
            }
        }
    }

    /**
     * Dual quaternion skinning (2026-10-07, the user: her arm bends "less plastic"): each vertex's bones' rigid motions blended
     * as dual quaternions, so a bent elbow, knee or knuckle keeps its volume where the old linear blend pinched it thin (the
     * rubber-hose look). The bone poses are rigid (view space: the camera's turn, her place, the bones' turns) with at most a
     * uniform scale, taken out and put back.
     */
    private void skin(SkinnedMesh mesh, Matrix4f[] m, float[] src, int count, int k) {
        if (dq.length < m.length * 8) dq = new float[m.length * 8];
        float scale = 1;
        for (int i = 0; i < m.length; i++) {
            m[i].getNormalizedRotation(quat);
            if (i == 0) scale = m[i].getScale(q).x;
            float qw = quat.w, qx = quat.x, qy = quat.y, qz = quat.z;
            float tx = m[i].m30(), ty = m[i].m31(), tz = m[i].m32();
            int o = i * 8;
            dq[o] = qw; dq[o + 1] = qx; dq[o + 2] = qy; dq[o + 3] = qz;
            dq[o + 4] = -0.5F * (tx * qx + ty * qy + tz * qz);
            dq[o + 5] = 0.5F * (qw * tx + ty * qz - tz * qy);
            dq[o + 6] = 0.5F * (qw * ty + tz * qx - tx * qz);
            dq[o + 7] = 0.5F * (qw * tz + tx * qy - ty * qx);
        }
        for (int v = 0; v < count; v++) {
            int j0 = mesh.joints[v * k] * 8;
            float rw = 0, rx = 0, ry = 0, rz = 0, dw = 0, dx = 0, dy = 0, dz = 0;
            for (int i = 0; i < k; i++) {
                float w = mesh.weights[v * k + i];
                if (w <= 0) continue;
                int o = mesh.joints[v * k + i] * 8;
                // the shorter way round: every quaternion in the first one's hemisphere
                if (dq[o] * dq[j0] + dq[o + 1] * dq[j0 + 1] + dq[o + 2] * dq[j0 + 2] + dq[o + 3] * dq[j0 + 3] < 0) w = -w;
                rw += w * dq[o]; rx += w * dq[o + 1]; ry += w * dq[o + 2]; rz += w * dq[o + 3];
                dw += w * dq[o + 4]; dx += w * dq[o + 5]; dy += w * dq[o + 6]; dz += w * dq[o + 7];
            }
            float len = (float) Math.sqrt(rw * rw + rx * rx + ry * ry + rz * rz);
            if (len < 1.0E-6F) len = 1;
            rw /= len; rx /= len; ry /= len; rz /= len;
            dw /= len; dx /= len; dy /= len; dz /= len;
            int p3 = v * 3;
            // rotation: p + 2 r x (r x p + w p); translation: 2 (w d - dw r + r x d)
            float sx = src[p3], sy = src[p3 + 1], sz = src[p3 + 2];
            float cx = ry * sz - rz * sy + rw * sx, cy = rz * sx - rx * sz + rw * sy, cz = rx * sy - ry * sx + rw * sz;
            float ox = sx + 2 * (ry * cz - rz * cy), oy = sy + 2 * (rz * cx - rx * cz), oz = sz + 2 * (rx * cy - ry * cx);
            float tx = 2 * (rw * dx - dw * rx + ry * dz - rz * dy), ty = 2 * (rw * dy - dw * ry + rz * dx - rx * dz),
                    tz = 2 * (rw * dz - dw * rz + rx * dy - ry * dx);
            px[p3] = ox * scale + tx; px[p3 + 1] = oy * scale + ty; px[p3 + 2] = oz * scale + tz;
            float nx0 = mesh.normals[p3], ny0 = mesh.normals[p3 + 1], nz0 = mesh.normals[p3 + 2];
            cx = ry * nz0 - rz * ny0 + rw * nx0;
            cy = rz * nx0 - rx * nz0 + rw * ny0;
            cz = rx * ny0 - ry * nx0 + rw * nz0;
            nx[p3] = nx0 + 2 * (ry * cz - rz * cy); nx[p3 + 1] = ny0 + 2 * (rz * cx - rx * cz); nx[p3 + 2] = nz0 + 2 * (rx * cy - ry * cx);
        }
    }

    /**
     * Where she looks (for MakimaFace's next frame): at your eyes (the camera, so she meets them in any view) when you are
     * near or with her, else at the nearest other player; turned into her head's own space by this frame's head pose, as
     * how far aside and up from her eyes' straight ahead.
     */
    private void aim(MakimaEntity e, float partial, MakimaFace.Rig rig, MakimaFace.State face, Matrix4f headPose) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 target = null;
        if (mc.player != null && (mc.player.getVehicle() == e || mc.player.distanceToSqr(e) < 12 * 12)) {
            target = mc.gameRenderer.getMainCamera().getPosition();
        } else {
            Player near = e.level().getNearestPlayer(e, 12);
            if (near != null) target = near.getEyePosition(partial);
        }
        face.aiming = false;
        if (target == null || root == null) return;
        Vec3 o = e.getPosition(partial);
        Vector4f view = new Matrix4f(root).transform(new Vector4f((float) (target.x - o.x), (float) (target.y - o.y), (float) (target.z - o.z), 1F));
        Vector4f local = new Matrix4f(headPose).invert().transform(view);
        float dx = local.x - (rig.eyeL[0] + rig.eyeR[0]) / 2, dy = local.y - (rig.eyeL[1] + rig.eyeR[1]) / 2,
                dz = local.z - (rig.eyeL[2] + rig.eyeR[2]) / 2;
        float f = dx * rig.fwd[0] + dy * rig.fwd[1] + dz * rig.fwd[2];
        float h = dx * rig.ex[0] + dy * rig.ex[1] + dz * rig.ex[2];
        float u = dx * rig.ey[0] + dy * rig.ey[1] + dz * rig.ey[2];
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-3F || f < 0.25F * d) return;
        face.aiming = true;
        face.aimX = Mth.clamp((float) Math.atan2(h, f) / (28 * Mth.DEG_TO_RAD), -1, 1);
        face.aimY = Mth.clamp((float) Math.atan2(u, f) / (20 * Mth.DEG_TO_RAD), -1, 1);
    }

    /**
     * Her irises (2026-10-07, the overhaul): the triangles textured from the atlas's eye square (gen_makima_mesh QUADS 'eyes':
     * u 1792..2048, v 1024..1280 of 2048), found once per mesh. They keep some light of their own (eyeLight): her ringed gold
     * eyes still read in shade and at night, faintly lit, as the anime draws them in the dark.
     */
    private static boolean[] irises(SkinnedMesh mesh) {
        if (irisOf == mesh) return iris;
        int[] idx = mesh.indices;
        boolean[] out = new boolean[idx.length / 3];
        for (int t = 0; t + 2 < idx.length; t += 3) {
            float u = (mesh.uvs[idx[t] * 2] + mesh.uvs[idx[t + 1] * 2] + mesh.uvs[idx[t + 2] * 2]) / 3;
            float v = (mesh.uvs[idx[t] * 2 + 1] + mesh.uvs[idx[t + 1] * 2 + 1] + mesh.uvs[idx[t + 2] * 2 + 1]) / 3;
            out[t / 3] = u > 0.875F && v > 0.5F && v < 0.625F;
        }
        irisOf = mesh;
        return iris = out;
    }

    private static SkinnedMesh irisOf;
    private static boolean[] iris;

    /** Block light at least 11 (of 15) on her irises. */
    private static int eyeLight(int light) {
        return Math.max(light & 0xFFFF, 0xB0) | (light & 0xFFFF0000);
    }

    private void put(VertexConsumer out, SkinnedMesh mesh, int v, int light, int overlay, float r, float g, float b, float a) {
        out.vertex(px[v * 3], px[v * 3 + 1], px[v * 3 + 2], r, g, b, a, mesh.uvs[v * 2], mesh.uvs[v * 2 + 1], overlay, light,
                nx[v * 3], nx[v * 3 + 1], nx[v * 3 + 2]);
    }

    /**
     * Hearts over the two of you now and then (more the faster you go), from her head up: never within reach of the camera,
     * so they never fill the rider's view.
     */
    private static void hearts(MakimaEntity e, Vector4f mouth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || e.act() == null) return;
        int every = 30 - 5 * e.tempo();
        if (e.tickCount - e.hearted < every) return;
        e.hearted = e.tickCount;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (int i = 0; i < 4; i++) {
            double x = mouth.x + (e.getRandom().nextDouble() - 0.5) * 1.2, y = mouth.y + 0.4 + e.getRandom().nextDouble() * 0.5,
                    z = mouth.z + (e.getRandom().nextDouble() - 0.5) * 1.2;
            if (cam.distanceToSqr(x, y, z) < 1.6 * 1.6) continue;
            e.level().addParticle(ParticleTypes.HEART, x, y, z, 0, 0.02, 0);
            return;
        }
    }

    /**
     * Her drool: a strand off her lips that stretches as it fills (swinging with her), snaps, and falls as a drop; where it
     * lands, a small wet patch that dries. While someone is with her it never stops; standing, when you are close.
     */
    static final class Drool {
        Vector4f mouth;
        private float strand, strandMax = 0.04F, swing, swingV;
        private Vec3 lastMouth;
        private float last = Float.NaN;
        private final List<float[]> drops = new ArrayList<>();      // x, y, z, vy, age
        private final List<float[]> puddles = new ArrayList<>();    // x, y, z, size, age

        void render(MakimaEntity e, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            if (mouth == null) return;
            Minecraft mc = Minecraft.getInstance();
            float t = e.tickCount + partial;
            float dt = Float.isNaN(last) ? 0 : Mth.clamp(t - last, 0, 3);
            last = t;
            if (mc.isPaused()) dt = 0;
            Vec3 m = new Vec3(mouth.x, mouth.y, mouth.z);
            boolean with = e.act() != null, near = mc.player != null && e.distanceToSqr(mc.player) < 9;
            float grow = with ? 0.0018F + 0.0007F * e.tempo() : near ? 0.0005F : 0;
            // the strand fills; its tip swings as her head moves (a pendulum on the mouth's motion)
            if (lastMouth != null && dt > 0) {
                Vec3 v = m.subtract(lastMouth).scale(1 / dt);
                swingV += (float) (-(v.x + v.z) * 0.6F) - 0.25F * swing * dt - 0.1F * swingV * dt;
                swing += swingV * dt;
                swing = Mth.clamp(swing, -0.6F, 0.6F);
            }
            lastMouth = m;
            if (grow > 0 || strand > 0) strand += Math.max(grow, 0.0008F) * dt;
            float ground = (float) e.getY() + 0.015F;
            if (strand >= strandMax) {
                // it snaps: a drop falls from the tip, a short thread stays on her lip
                Vec3 tip = tip(m, strand);
                drops.add(new float[]{(float) tip.x, (float) tip.y, (float) tip.z, -0.02F, 0});
                strand = 0.008F;
                strandMax = with ? 0.05F + e.getRandom().nextFloat() * 0.1F : 0.025F + e.getRandom().nextFloat() * 0.04F;
            } else if (grow == 0 && strand > 0) {
                strand = Math.max(0, strand - 0.004F * dt);
            }
            for (int i = drops.size() - 1; i >= 0; i--) {
                float[] d = drops.get(i);
                d[3] -= 0.04F * dt;
                d[1] += d[3] * dt;
                d[4] += dt;
                if (d[1] <= ground || d[4] > 60) {
                    if (d[1] <= ground + 0.05F) puddles.add(new float[]{d[0], ground, d[2], 0.025F + e.getRandom().nextFloat() * 0.02F, 0});
                    drops.remove(i);
                }
            }
            for (int i = puddles.size() - 1; i >= 0; i--) {
                float[] p = puddles.get(i);
                p[4] += dt;
                if (p[4] > 140 || puddles.size() > 12) puddles.remove(i);
            }
            draw(e, partial, m, pose, buffers, light);
        }

        private Vec3 tip(Vec3 m, float len) {
            return m.add(swing * len * 0.6F, -len, 0);
        }

        private void draw(MakimaEntity e, float partial, Vec3 m, PoseStack pose, MultiBufferSource buffers, int light) {
            Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 c = cam.getPosition(), o = e.getPosition(partial);
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
            Matrix4f mat = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            int lit = Math.max(light, 0xB000B0);
            if (strand > 0.004F) {
                // the strand: a ribbon facing the camera, thinning to a thread, a bead at its tip
                Vec3 a = m, b = tip(m, strand);
                Vec3 mid = a.lerp(b, 0.5).add(swing * strand * 0.15F, 0, 0);
                ribbon(out, mat, nm, c, o, a, mid, 0.0034F, 0.0021F, lit);
                ribbon(out, mat, nm, c, o, mid, b, 0.0021F, 0.003F, lit);
                bead(out, mat, nm, c, o, b, 0.0045F + strand * 0.025F, 1.4F, lit);
            }
            for (float[] d : drops) bead(out, mat, nm, c, o, new Vec3(d[0], d[1], d[2]), 0.004F, 2.2F, lit);
            for (float[] p : puddles) {
                float k = 1 - p[4] / 140F, s = p[3] * (0.6F + 0.4F * Math.min(1, p[4] / 6F));
                flat(out, mat, nm, o, new Vec3(p[0], p[1], p[2]), s, 0.45F * k, lit);
            }
        }

        private static final float R = 0.86F, G = 0.92F, B = 1.0F, A = 0.5F;

        private static void ribbon(VertexConsumer out, Matrix4f mat, Matrix3f nm, Vec3 cam, Vec3 o, Vec3 a, Vec3 b, float wa, float wb, int light) {
            Vec3 d = b.subtract(a), side = d.cross(a.subtract(cam));
            if (side.lengthSqr() < 1.0E-10) return;
            side = side.normalize();
            quad(out, mat, nm, o, a.add(side.scale(wa)), a.subtract(side.scale(wa)), b.subtract(side.scale(wb)), b.add(side.scale(wb)), A, light);
        }

        /** A drop: a billboard, stretched down by `stretch`. */
        private static void bead(VertexConsumer out, Matrix4f mat, Matrix3f nm, Vec3 cam, Vec3 o, Vec3 at, float r, float stretch, int light) {
            Vec3 view = at.subtract(cam);
            Vec3 side = view.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-10) side = new Vec3(1, 0, 0);
            side = side.normalize().scale(r);
            Vec3 up = new Vec3(0, r * stretch, 0);
            quad(out, mat, nm, o, at.add(side).add(up.scale(0.4)), at.subtract(side).add(up.scale(0.4)),
                    at.subtract(side.scale(0.5)).subtract(up), at.add(side.scale(0.5)).subtract(up), A + 0.1F, light);
        }

        private static void flat(VertexConsumer out, Matrix4f mat, Matrix3f nm, Vec3 o, Vec3 at, float s, float alpha, int light) {
            quad(out, mat, nm, o, at.add(-s, 0, -s), at.add(-s, 0, s), at.add(s, 0, s), at.add(s, 0, -s), alpha, light);
        }

        private static void quad(VertexConsumer out, Matrix4f mat, Matrix3f nm, Vec3 o, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha, int light) {
            for (Vec3 v : new Vec3[]{a, b, c, d}) {
                out.vertex(mat, (float) (v.x - o.x), (float) (v.y - o.y), (float) (v.z - o.z)).color(R, G, B, alpha).uv(0.5F, 0.5F)
                        .overlayCoords(0, 10).uv2(light).normal(nm, 0, 1, 0).endVertex();
            }
        }
    }

    /** Where her lips were last drawn (world), or null. */
    @org.jetbrains.annotations.Nullable
    public static Vec3 mouth(MakimaEntity e) {
        Drool d = DROOL.get(e);
        return d == null || d.mouth == null ? null : new Vec3(d.mouth.x, d.mouth.y, d.mouth.z);
    }

    /** For the dev tour (client/dev/AutoShot): where her mouth is drawn and how long the strand is. */
    public static String debugDrool(MakimaEntity e) {
        Drool d = DROOL.get(e);
        return d == null || d.mouth == null ? "none" : String.format("mouth %.3f %.3f %.3f (her %.2f %.2f %.2f) strand %.3f drops %d",
                d.mouth.x, d.mouth.y, d.mouth.z, e.getX(), e.getY(), e.getZ(), d.strand, d.drops.size());
    }

    @Override
    public void doPostRenderCleanup() {
        super.doPostRenderCleanup();
        poses.clear();
    }
}
