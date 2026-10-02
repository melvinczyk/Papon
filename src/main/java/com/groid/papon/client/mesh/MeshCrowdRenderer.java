package com.groid.papon.client.mesh;

import com.groid.papon.client.CrowdModel;
import com.groid.papon.client.CrowdRenderer;
import com.groid.papon.entity.npc.TacoNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;

/**
 * A crowd member whose body is a skinned triangle mesh instead of GeckoLib cubes. The GeckoLib
 * model still runs: it carries the shared rig and animations, and draws the props (as cubes).
 * While it renders, each bone's final pose matrix is captured; afterwards the mesh is skinned
 * on the CPU with those matrices (linear blend, two bones per vertex) and drawn in one pass.
 *
 * Rest pose: a GeckoLib bone with no animation applied leaves model-space geometry where it is,
 * so the mesh's own coordinates are its bind pose and the captured matrices skin it directly.
 */
public class MeshCrowdRenderer<T extends TacoNpc> extends CrowdRenderer<T> {
    private final String meshName;
    private final Map<String, Matrix4f> poses = new HashMap<>();
    private final Map<String, Matrix3f> normalPoses = new HashMap<>();

    public MeshCrowdRenderer(EntityRendererProvider.Context ctx, CrowdModel<T> model, float scale, float shadow,
                             float bubbleLift, String meshName) {
        super(ctx, model, scale, shadow, bubbleLift);
        this.meshName = meshName;
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                  float r, float g, float b, float a) {
        poses.put(bone.getName(), new Matrix4f(pose.last().pose()));
        normalPoses.put(bone.getName(), new Matrix3f(pose.last().normal()));
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, r, g, b, a);
    }

    @Override
    public void renderFinal(PoseStack pose, T animatable, BakedGeoModel model, MultiBufferSource buffers,
                            VertexConsumer buffer, float partialTick, int light, int overlay,
                            float r, float g, float b, float a) {
        super.renderFinal(pose, animatable, model, buffers, buffer, partialTick, light, overlay, r, g, b, a);
        SkinnedMesh mesh = SkinnedMesh.get(meshName);
        if (mesh == null || animatable.isInvisible()) return;
        Matrix4f[] m = new Matrix4f[mesh.bones.length];
        Matrix3f[] n = new Matrix3f[mesh.bones.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = poses.get(mesh.bones[i]);
            n[i] = normalPoses.get(mesh.bones[i]);
            if (m[i] == null) return;   // the rig is missing a bone: draw nothing rather than garbage
        }
        int count = mesh.vertexCount();
        float[] px = new float[count * 3];
        float[] nx = new float[count * 3];
        Vector4f p = new Vector4f();
        Vector3f q = new Vector3f();
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
        // Entity render types draw quads: each triangle goes in as a quad with its last corner doubled.
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture));
        int[] idx = mesh.indices;
        for (int t = 0; t < idx.length; t += 3) {
            put(out, mesh, px, nx, idx[t], light, overlay, r, g, b, a);
            put(out, mesh, px, nx, idx[t + 1], light, overlay, r, g, b, a);
            put(out, mesh, px, nx, idx[t + 2], light, overlay, r, g, b, a);
            put(out, mesh, px, nx, idx[t + 2], light, overlay, r, g, b, a);
        }
    }

    private static void put(VertexConsumer out, SkinnedMesh mesh, float[] px, float[] nx, int v, int light, int overlay,
                            float r, float g, float b, float a) {
        out.vertex(px[v * 3], px[v * 3 + 1], px[v * 3 + 2])
                .color(r, g, b, a)
                .uv(mesh.uvs[v * 2], mesh.uvs[v * 2 + 1])
                .overlayCoords(overlay)
                .uv2(light)
                .normal(nx[v * 3], nx[v * 3 + 1], nx[v * 3 + 2])
                .endVertex();
    }

    @Override
    public void doPostRenderCleanup() {
        super.doPostRenderCleanup();
        poses.clear();
        normalPoses.clear();
    }
}
