package com.groid.papon.client;

import com.groid.papon.boss.SpiritGiantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * El Gigante (boss/SpiritGiantEntity): a hologram risen out of the arena floor, so it is all light: a sugar skull built of voxels of blue (petals of
 * pink and marigold round its sockets, a flower on its brow, teeth), a hinged jaw, eyes that burn brighter as it charges,
 * and two great hands of bone-light. Additive, scanlines running up it, a flicker. Only its outside faces are drawn; the
 * sockets and the nose are holes you see into.
 */
public class SpiritGiantRenderer extends EntityRenderer<SpiritGiantEntity> {
    /** One voxel (blocks). */
    private static final float V = SpiritGiantEntity.V;
    private record Face(float[] p, int r, int g, int b) {}

    private static final List<Face> SKULL = new ArrayList<>(), JAW = new ArrayList<>();
    private static final int[] CYAN = {60, 214, 255}, PINK = {255, 60, 170}, GOLD = {255, 160, 30}, WHITE = {200, 255, 255};

    static {
        build();
    }

    public SpiritGiantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritGiantEntity e) { return new ResourceLocation("minecraft", "textures/misc/white.png"); }

    @Override
    public boolean shouldRender(SpiritGiantEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    // ------------------------------------------------------------------ the voxels

    private static boolean cranium(int x, int y, int z) {
        if (y < -3) return false;
        double e = Math.pow(x / 8.2, 2) + Math.pow((y - 1) / 7.6, 2) + Math.pow(z / 7.2, 2);
        return e <= 1;
    }

    private static boolean socket(int x, int y, int z) {
        for (int s : new int[]{-1, 1}) {
            double d = Math.sqrt(Math.pow(x - s * 3.2, 2) + Math.pow(y + 0.2, 2) * 1.1 + Math.pow(Math.max(0, 6 - z), 2) * 0.35);
            if (d < 2.4 && z >= 2) return true;
        }
        return false;
    }

    private static boolean skull(int x, int y, int z) {
        boolean solid = cranium(x, y, z);
        if (y >= -6 && y <= -3) {
            double half = 6.2 - (-3 - y) * 0.45;
            solid |= Math.abs(x) <= half && z >= -3 && z <= 6;
        }
        if (!solid) return false;
        if (socket(x, y, z)) return false;
        if (z >= 5 && Math.abs(x) <= 1 && (y == -3 || y == -2) && !(Math.abs(x) == 1 && y == -3)) return false;    // the nose
        if (y == -6 && z >= 5 && (x & 1) == 0) return false;                                                        // between the teeth
        return true;
    }

    private static boolean jaw(int x, int y, int z) {
        if (y < -9 || y > -7 || z < -2 || z > 6) return false;
        double half = 5.8 - Math.max(0, z - 3) * 0.6 - (y == -9 ? 0.8 : 0);
        if (Math.abs(x) > half) return false;
        return !(y == -7 && z >= 5 && (x & 1) == 1);
    }

    private static int[] colour(int x, int y, int z) {
        if (z >= 4) {
            for (int s : new int[]{-1, 1}) {
                double d = Math.hypot(x - s * 3.2, y + 0.2);
                if (d >= 2.4 && d < 3.7) return (Math.floorMod((int) Math.round(Math.atan2(y + 0.2, x - s * 3.2) * 4), 2) == 0) ? PINK : GOLD;
            }
            double f = Math.hypot(x, y - 5);
            if (f < 1.1) return PINK;
            if (f < 2.3) return GOLD;
            if (y <= -5) return WHITE;
        }
        return CYAN;
    }

    private interface Shape {
        boolean at(int x, int y, int z);
    }

    private static void build() {
        voxels(SKULL, SpiritGiantRenderer::skull, 0);
        voxels(JAW, SpiritGiantRenderer::jaw, 0);
    }

    private static void voxels(List<Face> out, Shape s, int dummy) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = -9; x <= 9; x++) {
            for (int y = -10; y <= 9; y++) {
                for (int z = -8; z <= 8; z++) {
                    if (!s.at(x, y, z)) continue;
                    int[] c = colour(x, y, z);
                    for (int[] d : dirs) {
                        if (s.at(x + d[0], y + d[1], z + d[2])) continue;
                        out.add(new Face(quad(x, y, z, d), c[0], c[1], c[2]));
                    }
                }
            }
        }
    }

    /** The face of voxel (x, y, z) toward {@code d}: four corners, voxel units, centred on the voxel's middle. */
    private static float[] quad(int x, int y, int z, int[] d) {
        float[] q = new float[12];
        float h = 0.5F;
        float[][] corners;
        if (d[0] != 0) corners = new float[][]{{d[0] * h, -h, -h}, {d[0] * h, h, -h}, {d[0] * h, h, h}, {d[0] * h, -h, h}};
        else if (d[1] != 0) corners = new float[][]{{-h, d[1] * h, -h}, {h, d[1] * h, -h}, {h, d[1] * h, h}, {-h, d[1] * h, h}};
        else corners = new float[][]{{-h, -h, d[2] * h}, {h, -h, d[2] * h}, {h, h, d[2] * h}, {-h, h, d[2] * h}};
        for (int i = 0; i < 4; i++) {
            q[i * 3] = x + corners[i][0];
            q[i * 3 + 1] = y + corners[i][1];
            q[i * 3 + 2] = z + corners[i][2];
        }
        return q;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * (2026-10-04) In the arena now, waist deep in its floor: the skull over a ribcage and spine of bone-light, collarbones out to
     * shoulders as wide as a house, arms bent at the elbow down to its hands (SpiritGiantEntity.hand: the same positions the
     * server hits with), a band on the floor where a swipe is coming, its eyes blazing while the lasers (BeamEntity) burn.
     */
    @Override
    public void render(SpiritGiantEntity g, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        Vec3 origin = g.getPosition(partial);
        Vec3 f = g.forward();
        Vec3 r = g.right();
        Vec3 up = new Vec3(0, 1, 0);
        float t = g.tickCount + partial;
        float act = g.actTime(partial);
        int a = g.act();
        float present = g.risen(partial);
        float flicker = (0.88F + 0.12F * Mth.sin(t * 1.9F)) * (0.45F + 0.55F * present);
        if ((g.tickCount * 7919 % 31) == 0) flicker *= 0.7F;
        // the skull: breathing; the jaw working, wide as it roars and fires
        Vec3 head = g.head(partial).subtract(origin);
        float jaw = 5F + 3F * Mth.sin(t * 0.07F);
        if (a == SpiritGiantEntity.CHARGE) jaw = 5F + 18F * Math.min(1, act / 26F);
        if (a == SpiritGiantEntity.LASER) jaw = 26F + 4F * Mth.sin(t * 2.3F);
        if (a == SpiritGiantEntity.SWIPE_L || a == SpiritGiantEntity.SWIPE_R || a == SpiritGiantEntity.RISE) jaw = 30F;
        float shake = a == SpiritGiantEntity.LASER ? 0.15F : 0;
        head = head.add((Math.sin(t * 7) * shake), Math.cos(t * 6.1) * shake, 0);
        drawFaces(vc, m, SKULL, head, r, up, f, 0, 0, t, flicker);
        drawFaces(vc, m, JAW, head, r, up, f, jaw, -6.5F, t, flicker);
        // its eyes: two burning lights in the sockets, blazing as it charges and fires
        float eye = a == SpiritGiantEntity.CHARGE ? 0.5F + 0.5F * Math.min(1, act / 26F) : a == SpiritGiantEntity.LASER ? 1.3F : 0.55F;
        for (int s : new int[]{-1, 1}) {
            Vec3 c = g.eye(s, partial).subtract(origin).subtract(f.scale(0.6));
            glow(vc, m, c, 2.0F + 2.2F * eye, 120, 240, 255, (int) Math.min(255, 220 * eye * flicker));
            glow(vc, m, c, 0.8F + 1.0F * eye, 255, 255, 255, (int) Math.min(255, 255 * eye * flicker));
        }
        // the spine, from under the floor up to the skull, and the ribcage round it
        double sunk = g.sunk(partial);
        Vec3 base = new Vec3(0, -4 - sunk, 0);
        Vec3 neck = head.subtract(up.scale(7.5)).subtract(f.scale(1.5));
        int verts = 9;
        for (int k = 0; k < verts; k++) {
            Vec3 p = base.lerp(neck, k / (double) (verts - 1));
            Vec3 q = base.lerp(neck, (k + 0.6) / (verts - 1));
            bone(vc, m, p, q, 1.1F, r, up, CYAN, t, flicker);
        }
        for (int k = 0; k < 6; k++) {
            double y = neck.y - 2.2 - k * 1.7;
            Vec3 sp = new Vec3(neck.x, y, neck.z).add(f.scale(-0.2));
            double w = 5.6 - Math.abs(k - 2.2) * 0.6;
            for (int s : new int[]{-1, 1}) {
                Vec3 p0 = sp;
                Vec3 p1 = sp.add(r.scale(s * w * 0.8)).add(f.scale(-0.8)).add(0, -0.4, 0);
                Vec3 p2 = sp.add(r.scale(s * w)).add(f.scale(2.0)).add(0, -0.9, 0);
                Vec3 p3 = sp.add(r.scale(s * w * 0.35)).add(f.scale(4.0)).add(0, -1.3, 0);
                int[] col = k == 0 ? PINK : CYAN;
                bone(vc, m, p0, p1, 0.45F, r, up, col, t, flicker);
                bone(vc, m, p1, p2, 0.45F, r, up, col, t, flicker);
                bone(vc, m, p2, p3, 0.4F, r, up, col, t, flicker);
            }
        }
        // the sternum, with a marigold heart
        Vec3 st = neck.add(f.scale(4.0)).add(0, -2.6, 0);
        bone(vc, m, st, st.add(0, -6.5, 0).add(f.scale(-0.4)), 0.6F, r, up, WHITE, t, flicker);
        glow(vc, m, st.add(0, -3.0, 0).add(f.scale(0.4)), 2.2F, 255, 160, 30, (int) (160 * flicker));
        // collarbones, arms, hands
        for (int i = 0; i < 2; i++) {
            Vec3 sh = g.shoulder(i, partial).subtract(origin);
            bone(vc, m, neck.add(f.scale(1.2)), sh, 0.7F, r, up, CYAN, t, flicker);
            glow(vc, m, sh, 1.6F, 120, 230, 255, (int) (120 * flicker));
            Vec3 hand = g.hand(i, partial).subtract(origin);
            Vec3 elbow = elbow(sh, hand, r.scale(i == 0 ? 1 : -1), f);
            bone(vc, m, sh, elbow, 1.15F, r, up, CYAN, t, flicker);
            bone(vc, m, elbow, hand, 0.95F, r, up, CYAN, t, flicker);
            glow(vc, m, elbow, 1.4F, 120, 230, 255, (int) (110 * flicker));
            boolean swipe = (a == SpiritGiantEntity.SWIPE_L && i == 0) || (a == SpiritGiantEntity.SWIPE_R && i == 1);
            boolean slam = (a == SpiritGiantEntity.SLAM_L && i == 0) || (a == SpiritGiantEntity.SLAM_R && i == 1);
            float splay = slam && act > SpiritGiantEntity.SLAM_HIT - 2 && act < 32 ? 1 : swipe ? 0.9F : 0.35F + 0.15F * Mth.sin(t * 0.1F + i);
            Vec3 along = hand.subtract(elbow).normalize();
            Vec3 hr = along.cross(up);
            hr = hr.lengthSqr() < 1e-4 ? r : hr.normalize();
            Vec3 hup = hr.cross(along).normalize();
            drawHand(vc, m, hand, hr, hup, along, i == 0 ? 1 : -1, splay, t, flicker * (swipe ? 1.25F : 1F));
            // the swipe's band, marked on the floor before it comes
            if (swipe && act < SpiritGiantEntity.SWIPE_FROM + 2) swipeBand(vc, m, g, i, act, f, r, t);
        }
    }

    /** The elbow of an arm from {@code sh} to {@code hand} (two bones, UPPER and FORE), bent out and back. */
    private static Vec3 elbow(Vec3 sh, Vec3 hand, Vec3 out, Vec3 f) {
        Vec3 d = hand.subtract(sh);
        double len = Math.max(1e-3, d.length());
        double l1 = SpiritGiantEntity.UPPER, l2 = SpiritGiantEntity.FORE;
        Vec3 dir = d.normalize();
        len = Math.min(len, l1 + l2 - 0.01);
        double x = (l1 * l1 - l2 * l2 + len * len) / (2 * len);
        double h = Math.sqrt(Math.max(0, l1 * l1 - x * x));
        Vec3 pole = out.add(f.scale(-0.6)).add(0, -0.4, 0);
        Vec3 perp = pole.subtract(dir.scale(pole.dot(dir)));
        perp = perp.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : perp.normalize();
        return sh.add(dir.scale(x)).add(perp.scale(h));
    }

    /** The band a swipe will sweep: an arc on the floor round it, pulsing as it comes. */
    private static void swipeBand(VertexConsumer vc, Matrix4f m, SpiritGiantEntity g, int i, float act, Vec3 f, Vec3 r, float t) {
        double rad = g.swipeRadius();
        double a0 = SpiritGiantEntity.swipeAngle(i, 0), a1 = SpiritGiantEntity.swipeAngle(i, 99);
        float k = Mth.clamp(act / SpiritGiantEntity.SWIPE_FROM, 0, 1);
        int al = (int) ((90 + 110 * (0.5F + 0.5F * Mth.sin(t * 1.4F))) * (0.4F + 0.6F * k));
        double y = 0.12;
        int n = 28;
        double[] rr = {rad - 5.2, rad + 5.2};
        for (int s = 0; s < n; s++) {
            double u0 = a0 + (a1 - a0) * s / n, u1 = a0 + (a1 - a0) * (s + 1) / n;
            Vec3 q0 = f.scale(Math.cos(u0) * rr[0]).add(r.scale(-Math.sin(u0) * rr[0])).add(0, y, 0);
            Vec3 q1 = f.scale(Math.cos(u0) * rr[1]).add(r.scale(-Math.sin(u0) * rr[1])).add(0, y, 0);
            Vec3 q2 = f.scale(Math.cos(u1) * rr[1]).add(r.scale(-Math.sin(u1) * rr[1])).add(0, y, 0);
            Vec3 q3 = f.scale(Math.cos(u1) * rr[0]).add(r.scale(-Math.sin(u1) * rr[0])).add(0, y, 0);
            int[] c = (s & 1) == 0 ? PINK : CYAN;
            for (Vec3[] w : new Vec3[][]{{q0, q1, q2, q3}, {q3, q2, q1, q0}}) {
                for (Vec3 p : w) vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], al / 2).endVertex();
            }
        }
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static void drawFaces(VertexConsumer vc, Matrix4f m, List<Face> faces, Vec3 o, Vec3 r, Vec3 up, Vec3 f, float jawDeg, float hingeY,
                                  float t, float flicker) {
        float cj = Mth.cos(jawDeg * Mth.DEG_TO_RAD), sj = Mth.sin(jawDeg * Mth.DEG_TO_RAD);
        float hingeZ = -2;
        for (Face face : faces) {
            float[] p = face.p();
            for (int k = 0; k < 4; k++) {
                float x = p[k * 3], y = p[k * 3 + 1], z = p[k * 3 + 2];
                if (jawDeg != 0) {
                    // the jaw drops round its hinge at the back of the mouth
                    float dy = y - hingeY, dz = z - hingeZ;
                    y = hingeY + dy * cj - dz * sj;
                    z = hingeZ + dy * sj + dz * cj;
                }
                double wx = o.x + (r.x * x + up.x * y + f.x * z) * V;
                double wy = o.y + (r.y * x + up.y * y + f.y * z) * V;
                double wz = o.z + (r.z * x + up.z * y + f.z * z) * V;
                float scan = 0.7F + 0.3F * Mth.sin((float) wy * 2.2F - t * 0.5F);
                int al = (int) Math.min(255, 100 * scan * flicker);
                vc.vertex(m, (float) wx, (float) wy, (float) wz).color(face.r(), face.g(), face.b(), al).endVertex();
            }
        }
    }

    /** A hand of light, palm down, fingers out toward the arena, splayed open (1) or curled (0). */
    private static void drawHand(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 r, Vec3 up, Vec3 f, int side, float splay, float t, float flicker) {
        float s = 1.1F;
        box(vc, m, at, r, up, f, -3.2F * s, -0.8F * s, -2F * s, 3.2F * s, 0.8F * s, 3.5F * s, CYAN, t, flicker);
        for (int k = 0; k < 4; k++) {
            float x = (-2.4F + k * 1.6F) * s;
            float spread = (k - 1.5F) * 0.12F * splay;
            Vec3 dir = f.scale(Math.cos(spread)).add(r.scale(Math.sin(spread)));
            float curl = (1 - splay) * 0.9F;
            Vec3 base = at.add(r.scale(x)).add(f.scale(3.5 * s));
            Vec3 p = base;
            for (int seg = 0; seg < 3; seg++) {
                float len = (seg == 0 ? 2.6F : 2.0F) * s;
                Vec3 d = dir.scale(Math.cos(curl * seg)).add(up.scale(-Math.sin(curl * seg)));
                Vec3 q = p.add(d.scale(len));
                bone(vc, m, p, q, 0.55F * s, r, up, k == 0 && seg == 2 ? PINK : CYAN, t, flicker);
                p = q;
            }
        }
        // the thumb, off the side toward the middle
        Vec3 tb = at.add(r.scale(-side * 3.4 * s)).add(f.scale(0.5 * s));
        bone(vc, m, tb, tb.add(r.scale(-side * 2.2 * s)).add(f.scale(1.8 * s)), 0.6F * s, r, up, CYAN, t, flicker);
    }

    private static void bone(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float w, Vec3 r, Vec3 up, int[] c, float t, float flicker) {
        Vec3 d = b.subtract(a).normalize();
        Vec3 s1 = d.cross(up);
        if (s1.lengthSqr() < 1e-4) s1 = r;
        s1 = s1.normalize().scale(w);
        Vec3 s2 = d.cross(s1).normalize().scale(w);
        Vec3[] ring = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = a.add(ring[i]), p1 = a.add(ring[(i + 1) % 4]), p2 = b.add(ring[(i + 1) % 4]), p3 = b.add(ring[i]);
            quadAt(vc, m, p0, p1, p2, p3, c, t, flicker);
        }
    }

    private static void box(VertexConsumer vc, Matrix4f m, Vec3 o, Vec3 r, Vec3 up, Vec3 f, float x0, float y0, float z0, float x1, float y1,
                            float z1, int[] c, float t, float flicker) {
        Vec3[] p = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
            p[i] = o.add(r.scale(x)).add(up.scale(y)).add(f.scale(z));
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] q : faces) quadAt(vc, m, p[q[0]], p[q[1]], p[q[2]], p[q[3]], c, t, flicker);
    }

    private static void quadAt(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int[] c, float t, float flicker) {
        for (Vec3 p : new Vec3[]{p0, p1, p2, p3}) {
            float scan = 0.7F + 0.3F * Mth.sin((float) p.y * 2.2F - t * 0.5F);
            vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], (int) Math.min(255, 120 * scan * flicker)).endVertex();
        }
    }

    /** A soft round light facing every way (two crossed discs and a flat one). */
    private static void glow(VertexConsumer vc, Matrix4f m, Vec3 c, float r, int cr, int cg, int cb, int a) {
        for (int plane = 0; plane < 3; plane++) {
            for (int i = 0; i < 12; i++) {
                double a0 = Math.PI * 2 * i / 12, a1 = Math.PI * 2 * (i + 1) / 12;
                Vec3 p0 = disc(c, plane, a0, r), p1 = disc(c, plane, a1, r);
                vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(cr, cg, cb, 0).endVertex();
                vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(cr, cg, cb, 0).endVertex();
                vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(cr, cg, cb, a).endVertex();
            }
        }
    }

    private static Vec3 disc(Vec3 c, int plane, double a, float r) {
        double u = Math.cos(a) * r, v = Math.sin(a) * r;
        return switch (plane) {
            case 0 -> c.add(u, v, 0);
            case 1 -> c.add(0, v, u);
            default -> c.add(u, 0, v);
        };
    }
}
