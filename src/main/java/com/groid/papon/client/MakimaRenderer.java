package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.client.mesh.SkinnedMesh;
import com.groid.papon.entity.MakimaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Makima (entity/MakimaEntity): her rig-only GeckoLib model (geo/entity/makima_mesh) runs the animations, and the mesh
 * (meshes/makima.json, tools/gen_makima_mesh.py) is skinned on the CPU with each bone's pose as it is drawn, the way
 * mesh/MeshCrowdRenderer does it. With someone at her she is jolted along her body on every thrust (bounced on top in
 * {@link MakimaEntity.Position#COWGIRL}), in step with PillowClient.thrust; her mouth waters ({@link #drool}).
 */
public class MakimaRenderer extends GeoEntityRenderer<MakimaEntity> {
    /** Her lips from the head bone's pivot (blocks, front -Z; tracked bone matrices are taken at the pivot): the geo's "mouth" locator less the head's pivot. */
    private static final Vector4f MOUTH = new Vector4f(0F, 0.054F, -0.082F, 1F);

    private final Map<String, Matrix4f> poses = new HashMap<>();
    private final Map<String, Matrix3f> normalPoses = new HashMap<>();
    private float[] px = new float[0];
    private float[] nx = new float[0];
    private final Vector4f p = new Vector4f();
    private final Vector3f q = new Vector3f();

    public MakimaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.4F;
    }

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
            if (e.getFirstPassenger() instanceof Player rider && e.act() != null) {
                moving(e.posture(), rider.tickCount + state.getPartialTick());
                return;
            }
            // standing, her head follows you
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head == null || data == null) return;
            head.setRotX(head.getRotX() + data.headPitch() * 0.8F * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -60, 60) * Mth.DEG_TO_RAD);
        }

        /**
         * Her body taking every thrust (in step with PillowClient.thrust, on top of the position's loop): the back arching,
         * the head thrown back, the hips pushed, the knees rocking, the braid swinging a beat behind, quick breaths.
         */
        private void moving(MakimaEntity.Position at, float t) {
            float th = PillowClient.thrust(t), sq = PillowClient.squash(t), lag = PillowClient.thrust(t - 2.5F);
            float breath = Mth.sin(t * 0.6F);
            switch (at) {
                case MISSIONARY -> {
                    add("chest", -6 * sq + 1.5F * breath, 0, 0);
                    add("head", -10 * sq, 0, 0);
                    add("leg_l", 7 * lag, 0, -4 * lag);
                    add("leg_r", 7 * lag, 0, 4 * lag);
                    add("forearm_l", -8 * sq, 0, 0);
                    add("forearm_r", -8 * sq, 0, 0);
                }
                case DOGGY, BENT -> {
                    add("torso", 5 * sq, 0, 0);
                    add("chest", -7 * sq + 1.5F * breath, 0, 0);
                    add("head", -12 * sq, 0, 0);
                    add("braid", 12 * lag, 0, 16 * lag);
                    add("arm_l", 5 * sq, 0, 0);
                    add("arm_r", 5 * sq, 0, 0);
                }
                case COWGIRL -> {
                    add("torso", 0, 0, 4 * Mth.sin(t * 0.45F));
                    add("chest", -8 * Math.abs(th) + 1.5F * breath, 0, 0);
                    add("head", -12 * sq, 0, 0);
                    add("braid", -16 * lag, 0, 6 * lag);
                    add("forearm_l", -6 * sq, 0, 0);
                    add("forearm_r", -6 * sq, 0, 0);
                }
                default -> {}
            }
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

    @Override
    protected void applyRotations(MakimaEntity e, PoseStack pose, float age, float yaw, float partial) {
        // the thrust, in the world's axes: along her body toward her head, or up and down astride
        if (e.getFirstPassenger() instanceof Player rider && e.act() != null) {
            float t = rider.tickCount + partial, sq = PillowClient.squash(t);
            if (e.posture() == MakimaEntity.Position.COWGIRL) {
                pose.translate(0, 0.14F * Math.abs(PillowClient.thrust(t)), 0);
            } else {
                Vec3 f = e.facing();
                pose.translate(-f.x * 0.07 * sq, 0, -f.z * 0.07 * sq);
            }
        }
        super.applyRotations(e, pose, age, yaw, partial);
    }

    @Override
    public void preRender(PoseStack pose, MakimaEntity e, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partial, int light, int overlay, float r, float g, float b, float a) {
        model.getBone("head").ifPresent(bone -> bone.setTrackingMatrices(true));
        super.preRender(pose, e, model, buffers, buffer, reRender, partial, light, overlay, r, g, b, a);
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                  float r, float g, float b, float a) {
        poses.put(bone.getName(), new Matrix4f(pose.last().pose()));
        normalPoses.put(bone.getName(), new Matrix3f(pose.last().normal()));
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
    }

    @Override
    public void renderFinal(PoseStack pose, MakimaEntity e, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                            float partial, int light, int overlay, float r, float g, float b, float a) {
        super.renderFinal(pose, e, model, buffers, buffer, partial, light, overlay, r, g, b, a);
        model.getBone("head").ifPresent(head -> drool(e, head));
        SkinnedMesh mesh = SkinnedMesh.get("makima");
        if (mesh == null || e.isInvisible()) return;
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
            float sx = 0, sy = 0, sz = 0, tx = 0, ty = 0, tz = 0;
            for (int k = 0; k < 2; k++) {
                float w = mesh.weights[v * 2 + k];
                if (w <= 0) continue;
                int j = mesh.joints[v * 2 + k];
                m[j].transform(p.set(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2], 1F));
                n[j].transform(q.set(mesh.normals[v * 3], mesh.normals[v * 3 + 1], mesh.normals[v * 3 + 2]));
                sx += p.x * w; sy += p.y * w; sz += p.z * w;
                tx += q.x * w; ty += q.y * w; tz += q.z * w;
            }
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len > 1.0E-6F) { tx /= len; ty /= len; tz /= len; }
            px[v * 3] = sx; px[v * 3 + 1] = sy; px[v * 3 + 2] = sz;
            nx[v * 3] = tx; nx[v * 3 + 1] = ty; nx[v * 3 + 2] = tz;
        }
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture));
        int[] idx = mesh.indices;
        for (int t = 0; t < idx.length; t += 3) {
            put(out, mesh, idx[t], light, overlay, r, g, b, a);
            put(out, mesh, idx[t + 1], light, overlay, r, g, b, a);
            put(out, mesh, idx[t + 2], light, overlay, r, g, b, a);
            put(out, mesh, idx[t + 2], light, overlay, r, g, b, a);
        }
    }

    private void put(VertexConsumer out, SkinnedMesh mesh, int v, int light, int overlay, float r, float g, float b, float a) {
        out.vertex(px[v * 3], px[v * 3 + 1], px[v * 3 + 2]).color(r, g, b, a).uv(mesh.uvs[v * 2], mesh.uvs[v * 2 + 1])
                .overlayCoords(overlay).uv2(light).normal(nx[v * 3], nx[v * 3 + 1], nx[v * 3 + 2]).endVertex();
    }

    /**
     * Her mouth waters: a drop of drool off her lips (it hangs, falls and splashes) every few ticks while someone is with her,
     * now and then while you stand close to her.
     */
    private static void drool(MakimaEntity e, GeoBone head) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null) return;
        int every = e.act() != null ? 5 : e.distanceToSqr(mc.player) < 16 ? 24 : 0;
        if (every == 0 || e.tickCount - e.drooled < every) return;
        e.drooled = e.tickCount;
        Vector4f at = head.getWorldSpaceMatrix().transform(new Vector4f(MOUTH));
        double jx = (e.getRandom().nextDouble() - 0.5) * 0.03, jz = (e.getRandom().nextDouble() - 0.5) * 0.03;
        e.level().addParticle(ParticleTypes.DRIPPING_WATER, at.x + jx, at.y - 0.02, at.z + jz, 0, 0, 0);
        if (e.act() != null && e.getRandom().nextInt(3) == 0) {
            e.level().addParticle(ParticleTypes.FALLING_WATER, at.x - jx, at.y - 0.03, at.z - jz, 0, 0, 0);
        }
    }

    @Override
    public void doPostRenderCleanup() {
        super.doPostRenderCleanup();
        poses.clear();
        normalPoses.clear();
    }
}
