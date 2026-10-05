package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * La Bandera Papa as a sheet of waving cloth (tools/gen_flag.py: one texture for every flag in the mod).
 * The sheet lies in the local XY plane facing +Z, its hoist (the green, the pole side) at local x = 0,
 * running to x = width, from y = 0 down to y = -height; the waves push it along Z.
 *
 *   hung   on a wall, from a rod along its top edge: it ripples sideways, more toward the bottom
 *   pole   on a stick along its hoist: the fly end flaps, the faster the more it is being waved
 */
public final class FlagCloth {
    public static final ResourceLocation TEXTURE = PaponMod.id("textures/entity/potato_flag.png");

    private FlagCloth() {}

    /** El Coliseo de los Muertos' flag: the same potato, a calavera now, on black, purple and marigold (tools/gen_muertos.py). */
    public static final ResourceLocation DEAD_TEXTURE = PaponMod.id("textures/entity/potato_flag_muertos.png");

    public static RenderType renderType() {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    public static RenderType renderType(boolean dead) {
        return RenderType.entityCutoutNoCull(dead ? DEAD_TEXTURE : TEXTURE);
    }

    /**
     * @param time   ticks (any running clock)
     * @param phase  radians: so neighbouring flags do not wave in step
     * @param amp    the waves' depth, blocks (in the sheet's own units)
     * @param pole   true for a flag on a stick (waves from the hoist), false for one hung from its top
     */
    public static void draw(PoseStack ps, VertexConsumer vc, float width, float height, int light, float time, float phase,
                            float amp, boolean pole, int nx, int ny) {
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float[][] z = new float[nx + 1][ny + 1];
        for (int i = 0; i <= nx; i++) { for (int j = 0; j <= ny; j++) z[i][j] = wave(i / (float) nx, j / (float) ny, time, phase, amp, pole); }
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                vertex(vc, m, nm, z, i, j, nx, ny, width, height, light);
                vertex(vc, m, nm, z, i, j + 1, nx, ny, width, height, light);
                vertex(vc, m, nm, z, i + 1, j + 1, nx, ny, width, height, light);
                vertex(vc, m, nm, z, i + 1, j, nx, ny, width, height, light);
            }
        }
    }

    private static float wave(float u, float v, float t, float phase, float amp, boolean pole) {
        if (pole) {
            float k = u * (0.6F + 0.4F * u);
            return amp * k * (Mth.sin(u * 7.5F - t * 0.55F + phase + v * 0.8F) + 0.35F * Mth.sin(u * 13F - t * 0.9F + phase * 1.7F));
        }
        float k = 0.25F + 0.75F * v;
        return amp * k * (Mth.sin(u * 6.8F + v * 1.9F - t * 0.11F + phase) + 0.3F * Mth.sin(u * 15F - t * 0.23F + phase * 2.3F));
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[][] z, int i, int j, int nx, int ny,
                               float w, float h, int light) {
        float u = i / (float) nx, v = j / (float) ny;
        float dzu = (z[Math.min(nx, i + 1)][j] - z[Math.max(0, i - 1)][j]) / ((Math.min(nx, i + 1) - Math.max(0, i - 1)) / (float) nx);
        float dzv = (z[i][Math.min(ny, j + 1)] - z[i][Math.max(0, j - 1)]) / ((Math.min(ny, j + 1) - Math.max(0, j - 1)) / (float) ny);
        // the sheet's normal from its slopes: d/du = (w, 0, dzu), d/dv = (0, -h, dzv)
        float nxv = -h * dzu, nyv = w * dzv, nzv = w * h;
        float len = Mth.sqrt(nxv * nxv + nyv * nyv + nzv * nzv);
        vc.vertex(m, u * w, -v * h, z[i][j])
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(nm, nxv / len, nyv / len, nzv / len)
                .endVertex();
    }
}
