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
 * El Gigante (boss/SpiritGiantEntity): a hologram, so it is all light: a sugar skull built of voxels of blue (petals of
 * pink and marigold round its sockets, a flower on its brow, teeth), a hinged jaw, eyes that burn brighter as it charges,
 * and two great hands of bone-light. Additive, scanlines running up it, a flicker. Only its outside faces are drawn; the
 * sockets and the nose are holes you see into.
 */
public class SpiritGiantRenderer extends EntityRenderer<SpiritGiantEntity> {
    /** One voxel (blocks). */
    private static final float V = 0.95F;
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

    @Override
    public void render(SpiritGiantEntity g, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        Vec3 origin = g.getPosition(partial);
        Vec3 f = g.forward();
        Vec3 r = new Vec3(-f.z, 0, f.x);
        Vec3 up = new Vec3(0, 1, 0);
        float t = g.tickCount + partial;
        float act = g.actTime(partial);
        int a = g.act();
        float present = g.risen(partial);
        float flicker = (0.85F + 0.15F * Mth.sin(t * 1.9F)) * (0.4F + 0.6F * present);
        if ((g.tickCount * 7919 % 31) == 0) flicker *= 0.6F;
        // the skull: bobbing, breathing; the jaw opening for the laser
        Vec3 head = g.head(partial).subtract(origin).add(0, Mth.sin(t * 0.05F) * 0.3, 0);
        float jaw = 4F + 2F * Mth.sin(t * 0.07F);
        if (a == SpiritGiantEntity.CHARGE) jaw = 4F + 22F * Math.min(1, act / 30F);
        if (a == SpiritGiantEntity.LASER) jaw = 34F + 3F * Mth.sin(t * 2.3F);
        float shake = a == SpiritGiantEntity.LASER ? 0.15F : 0;
        head = head.add((Math.sin(t * 7) * shake), Math.cos(t * 6.1) * shake, 0);
        drawFaces(vc, m, SKULL, head, r, up, f, 0, 0, t, flicker);
        drawFaces(vc, m, JAW, head, r, up, f, jaw, -6.5F, t, flicker);
        // its eyes: two burning lights in the sockets, brighter as it charges and fires
        float eye = a == SpiritGiantEntity.CHARGE ? 0.5F + 0.5F * Math.min(1, act / 30F) : a == SpiritGiantEntity.LASER ? 1F : 0.4F;
        for (int s : new int[]{-1, 1}) {
            Vec3 c = head.add(r.scale(s * 3.2 * V)).add(up.scale(-0.2 * V)).add(f.scale(4.0 * V));
            glow(vc, m, c, 1.6F + 1.6F * eye, 120, 240, 255, (int) (200 * eye * flicker));
            glow(vc, m, c, 0.6F + 0.8F * eye, 255, 255, 255, (int) (255 * eye * flicker));
        }
        // its hands
        for (int i = 0; i < 2; i++) {
            Vec3 at = hand(g, i, partial).subtract(origin);
            boolean slam = (a == SpiritGiantEntity.SLAM_L && i == 0) || (a == SpiritGiantEntity.SLAM_R && i == 1);
            float splay = slam && act > SpiritGiantEntity.SLAM_HIT - 2 && act < 32 ? 1 : 0.35F + 0.15F * Mth.sin(t * 0.1F + i);
            drawHand(vc, m, at, r, up, f, i == 0 ? 1 : -1, splay, t, flicker);
        }
    }

    /** Where hand {@code i} is: on the wall at rest; in a slam up, over in an arc and down onto its mark, and back. */
    private static Vec3 hand(SpiritGiantEntity g, int i, float partial) {
        Vec3 rest = g.handRest(i == 0 ? 1 : -1).add(0, (1 - g.risen(partial)) * -30, 0);
        int a = g.act();
        boolean slam = (a == SpiritGiantEntity.SLAM_L && i == 0) || (a == SpiritGiantEntity.SLAM_R && i == 1);
        if (!slam) return rest;
        float t = g.actTime(partial);
        Vec3 mark = g.handMark(i).add(0, 0.6, 0);
        Vec3 high = rest.add(0, 9, 0);
        if (t < 9) return rest.lerp(high, smooth(t / 9F));
        if (t < SpiritGiantEntity.SLAM_HIT) {
            float k = (t - 9) / (SpiritGiantEntity.SLAM_HIT - 9F);
            Vec3 p = high.lerp(mark, k * k);
            return p.add(0, Math.sin(k * Math.PI) * 4 * (1 - k), 0);
        }
        if (t < 30) return mark;
        return mark.lerp(rest, smooth((t - 30) / 10F));
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
                int al = (int) (64 * scan * flicker);
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
        // the wrist going down out of sight under the floor
        bone(vc, m, at.add(f.scale(-2 * s)), at.add(f.scale(-3 * s)).add(up.scale(-14)), 1.3F * s, r, up, CYAN, t, flicker * 0.6F);
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
            vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], (int) (80 * scan * flicker)).endVertex();
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
