package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The body pillow as a soft thing, not a box: a dakimakura 1.5 blocks long and half a block wide, plump in the middle,
 * flat at its seam, the ends rounded; Makima on the front (+z), from behind on the back (textures/item/body_pillow.png,
 * tools/gen_extras.py). It is drawn from a spine running up its length (y), and how it is being handled bends that spine
 * and squashes the cushion round it ({@link Shape}): arms round it pinch it and it bulges either side, a face or hips
 * pressed into it flatten it, it folds over (twice, sat on the edge of a bed), it slumps against a wall. Used by the hand (PillowItemRenderer), on players
 * (PillowLayer) and on a bed (BodyPillowRenderer).
 */
public final class PillowMesh {
    public static final ResourceLocation TEXTURE = PaponMod.id("textures/item/body_pillow.png");
    public static final float WIDTH = 0.5F, LENGTH = 1.5F, THICK = 0.085F;
    private static final int COLS = 9, ROWS = 30;
    // texture regions (256 x 256): the front 96 wide at 0, the back at 96, 256 tall
    private static final float FRONT_U0 = 0, BACK_U0 = 96 / 256F, PANEL_U = 96 / 256F;

    private PillowMesh() {}

    /** How it is being handled. All zero: lying flat, front up (+z), its length along y, centred on the origin. */
    public static final class Shape {
        /** Arms round it (0..1) at {@link #squeezeAt} (0 the foot end .. 1 the head end): pinched there, bulging either side. */
        public float squeeze, squeezeAt = 0.55F;
        /** Folded over at {@link #foldAt}, radians (+ folds the head end over the front, - over the back). */
        public float fold, foldAt = 0.5F;
        /** A second fold at {@link #kneeAt}, radians (sat on the edge of a bed: the lap on the mattress, the legs over the side). */
        public float knee, kneeAt = 0.3F;
        /** A curve along its whole length, radians from end to end (+ toward the front). */
        public float bend;
        /** Pressed flat (0..1) at {@link #pressAt}: a face, a thrust. */
        public float press, pressAt = 0.5F;
        /** Bowed sideways (blocks at the middle), slumping. */
        public float bow;
        /** Where along it (0..1) sits at the origin. */
        public float anchor = 0.5F;

        public Shape squeeze(float k, float at) {
            squeeze = k;
            squeezeAt = at;
            return this;
        }

        public Shape fold(float rad, float at) {
            fold = rad;
            foldAt = at;
            return this;
        }

        public Shape knee(float rad, float at) {
            knee = rad;
            kneeAt = at;
            return this;
        }

        public Shape bend(float rad) {
            bend = rad;
            return this;
        }

        public Shape press(float k, float at) {
            press = k;
            pressAt = at;
            return this;
        }

        public Shape bow(float b) {
            bow = b;
            return this;
        }

        public Shape anchor(float v) {
            anchor = v;
            return this;
        }
    }

    private static float gauss(float x, float w) {
        return (float) Math.exp(-(x / w) * (x / w));
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Draws it in the current pose (blocks), lit by {@code light}. */
    public static void render(PoseStack ps, MultiBufferSource buffers, int light, Shape s) {
        // the spine: its angle along the length, integrated into a curve in the y-z plane
        float[] sy = new float[ROWS + 1], sz = new float[ROWS + 1], ang = new float[ROWS + 1];
        float[] half = new float[ROWS + 1], thick = new float[ROWS + 1];
        float dv = 1F / ROWS;
        for (int j = 0; j <= ROWS; j++) {
            float v = j * dv;
            ang[j] = s.bend * (v - 0.5F) + s.fold * smooth((v - (s.foldAt - 0.07F)) / 0.14F) + s.knee * smooth((v - (s.kneeAt - 0.07F)) / 0.14F);
            float end = Math.max(0, Math.abs(2 * v - 1) - 0.86F) / 0.14F;      // the rounded ends
            float round = (float) Math.sqrt(Math.max(0.02, 1 - end * end));
            float sq = s.squeeze * gauss(v - s.squeezeAt, 0.07F);
            float bulge = s.squeeze * 0.35F * (gauss(v - s.squeezeAt + 0.17F, 0.08F) + gauss(v - s.squeezeAt - 0.17F, 0.08F));
            float pr = s.press * gauss(v - s.pressAt, 0.14F);
            half[j] = WIDTH / 2 * round * (1 - 0.28F * sq + 0.08F * bulge + 0.14F * pr);
            thick[j] = THICK * (float) Math.sqrt(Math.max(0, 1 - end * end)) * Math.max(0.15F, 1 - 0.7F * sq + bulge - 0.55F * pr);
            // the fold squashes it where it bends
            thick[j] *= 1 - 0.35F * Math.min(1, Math.abs(s.fold)) * gauss(v - s.foldAt, 0.06F) - 0.3F * Math.min(1, Math.abs(s.knee)) * gauss(v - s.kneeAt, 0.06F);
        }
        for (int j = 1; j <= ROWS; j++) {
            float a = (ang[j] + ang[j - 1]) / 2;
            sy[j] = sy[j - 1] + Mth.cos(a) * LENGTH * dv;
            sz[j] = sz[j - 1] + Mth.sin(a) * LENGTH * dv;
        }
        float av = Mth.clamp(s.anchor, 0, 1) * ROWS;
        int aj = Math.min(ROWS - 1, (int) av);
        float af = av - aj;
        float oy = Mth.lerp(af, sy[aj], sy[aj + 1]), oz = Mth.lerp(af, sz[aj], sz[aj + 1]);
        // the surface: front (+1) and back (-1), each a COLS x ROWS grid
        float[][][] p = new float[2][(COLS + 1) * (ROWS + 1)][3];
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1 : -1;
            for (int j = 0; j <= ROWS; j++) {
                float v = j * dv;
                float ny = -Mth.sin(ang[j]), nz = Mth.cos(ang[j]);
                float bow = s.bow * Mth.sin(v * Mth.PI);
                for (int i = 0; i <= COLS; i++) {
                    float x = 2F * i / COLS - 1;
                    float puff = (float) Math.sqrt(Math.max(0, 1 - x * x)) * thick[j] * sgn;
                    float[] q = p[side][j * (COLS + 1) + i];
                    q[0] = x * half[j] + bow;
                    q[1] = sy[j] - oy + ny * puff;
                    q[2] = sz[j] - oz + nz * puff;
                }
            }
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1 : -1;
            float u0 = side == 0 ? FRONT_U0 : BACK_U0;
            for (int j = 0; j < ROWS; j++) {
                for (int i = 0; i < COLS; i++) {
                    int[][] corners = side == 0 ? new int[][]{{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}}
                            : new int[][]{{i + 1, j}, {i, j}, {i, j + 1}, {i + 1, j + 1}};
                    for (int[] c : corners) {
                        float[] q = p[side][c[1] * (COLS + 1) + c[0]];
                        float[] nn = normal(p[side], c[0], c[1], sgn);
                        // the front reads left to right along +x; the back, seen from behind, along -x
                        float u = u0 + PANEL_U * (side == 0 ? c[0] / (float) COLS : 1 - c[0] / (float) COLS);
                        float vv = 1 - c[1] / (float) ROWS;
                        vc.vertex(m, q[0], q[1], q[2]).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                                .uv2(light).normal(n, nn[0], nn[1], nn[2]).endVertex();
                    }
                }
            }
        }
    }

    /** The surface normal at a grid point (central differences), facing out of the pillow. */
    private static float[] normal(float[][] g, int i, int j, float sgn) {
        float[] a = g[j * (COLS + 1) + Math.min(COLS, i + 1)], b = g[j * (COLS + 1) + Math.max(0, i - 1)];
        float[] c = g[Math.min(ROWS, j + 1) * (COLS + 1) + i], d = g[Math.max(0, j - 1) * (COLS + 1) + i];
        float ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
        float vx = c[0] - d[0], vy = c[1] - d[1], vz = c[2] - d[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6) return new float[]{0, 0, sgn};
        return new float[]{nx / len * sgn, ny / len * sgn, nz / len * sgn};
    }
}
