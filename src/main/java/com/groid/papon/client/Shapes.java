package com.groid.papon.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Solid shapes for procedural props (the roses and thorns, the skull posts' poles): tubes, balls, cones and two-sided quads in
 * the entity vertex format, coloured per vertex, shaded a little darker underneath (the shape's own shadow) and drawn at the
 * light given. Coordinates are relative to the pose (the prop's position). Use with a QUADS entity render type over a white
 * texture (RenderType.entityCutoutNoCull(white)).
 */
final class Shapes {
    private Shapes() {}

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, Vec3 nr, int[] c, float shade, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((int) (c[0] * shade), (int) (c[1] * shade), (int) (c[2] * shade), 255)
                .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, (float) nr.x, (float) nr.y, (float) nr.z).endVertex();
    }

    private static float shade(Vec3 nr) { return 0.66F + 0.34F * (float) (0.5 + 0.5 * nr.y); }

    /** A tube from a (radius ra) to b (radius rb), {@code sides} round. */
    static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float ra, float rb, int[] col, int sides, int light) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 u = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(u).normalize(), s2 = dir.cross(s1).normalize();
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides;
            Vec3 n0 = s1.scale(Math.cos(a0)).add(s2.scale(Math.sin(a0))), n1 = s1.scale(Math.cos(a1)).add(s2.scale(Math.sin(a1)));
            v(vc, m, n, a.add(n0.scale(ra)), n0, col, shade(n0), light);
            v(vc, m, n, b.add(n0.scale(rb)), n0, col, shade(n0), light);
            v(vc, m, n, b.add(n1.scale(rb)), n1, col, shade(n1), light);
            v(vc, m, n, a.add(n1.scale(ra)), n1, col, shade(n1), light);
        }
    }

    /** A cone from a base of radius r at a to its point at b (a thorn, a petal's tip). */
    static void cone(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float r, int[] col, int light) {
        tube(vc, m, n, a, b, r, 0.0F, col, 4, light);
    }

    /** A ball. */
    static void ball(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, float r, int[] col, int light) {
        int lat = 4, lon = 7;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3[] ns = {sph(t0, p0), sph(t0, p1), sph(t1, p1), sph(t1, p0)};
                for (Vec3 nr : ns) v(vc, m, n, c.add(nr.scale(r)), nr, col, shade(nr), light);
            }
        }
    }

    /** A flat quad seen from both sides (a petal, a leaf). */
    static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int[] col, int light) {
        Vec3 nr = p1.subtract(p0).cross(p3.subtract(p0));
        nr = nr.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : nr.normalize();
        Vec3 back = nr.scale(-1);
        v(vc, m, n, p0, nr, col, 1, light);
        v(vc, m, n, p1, nr, col, 1, light);
        v(vc, m, n, p2, nr, col, 1, light);
        v(vc, m, n, p3, nr, col, 1, light);
        v(vc, m, n, p3, back, col, 0.8F, light);
        v(vc, m, n, p2, back, col, 0.8F, light);
        v(vc, m, n, p1, back, col, 0.8F, light);
        v(vc, m, n, p0, back, col, 0.8F, light);
    }

    /**
     * A rose: a cup of petals round a tight bud, opening by {@code open} (0 bud .. 1 in full bloom), facing {@code up}, {@code r}
     * across; {@code spin} turns it.
     */
    static void rose(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, Vec3 up, float r, float open, float spin, int[] petal, int[] deep, int light) {
        Vec3 side = Math.abs(up.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 ax = up.cross(side).normalize(), ay = up.cross(ax).normalize();
        ball(vc, m, n, c.add(up.scale(r * 0.15)), r * 0.32F, deep, light);
        for (int ring = 0; ring < 3; ring++) {
            int count = 4 + ring;
            float rr = r * (0.38F + 0.3F * ring);
            float tilt = (0.25F + 0.45F * ring) * (0.35F + 0.65F * open);
            for (int i = 0; i < count; i++) {
                double a = spin + Math.PI * 2 * (i + ring * 0.5) / count;
                Vec3 out = ax.scale(Math.cos(a)).add(ay.scale(Math.sin(a)));
                Vec3 tan = ax.scale(-Math.sin(a)).add(ay.scale(Math.cos(a)));
                Vec3 base = c.add(out.scale(rr * 0.35));
                Vec3 tip = c.add(out.scale(rr * (0.5 + 0.9 * tilt))).add(up.scale(r * (0.75 - 0.5 * tilt)));
                float w = rr * 0.55F;
                int[] col = ring == 0 ? deep : petal;
                quad(vc, m, n, base.add(tan.scale(-w * 0.5)), base.add(tan.scale(w * 0.5)), tip.add(tan.scale(w * 0.7)), tip.add(tan.scale(-w * 0.7)), col, light);
            }
        }
    }

    private static Vec3 sph(double t, double p) { return new Vec3(Math.cos(t) * Math.cos(p), Math.sin(t), Math.cos(t) * Math.sin(p)); }
}
