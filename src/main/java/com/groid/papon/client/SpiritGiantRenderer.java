package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.SpiritGiantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * El Gigante (boss/SpiritGiantEntity), rebuilt 2026-10-05 (the user: "it feels somewhat flat... make it feel like a massive,
 * three-dimensional physical presence in the arena... proportions, dimensionality, depth, movement... a more convincing skeletal
 * structure and a greater sense of scale... an imposing creature rather than a flat visual effect"). It was a hologram: every
 * part an additive glow, so nothing had a lit side or a shadowed one. Now it is bone, lit like everything else in the world
 * (normals, the sun's two lights, ambient occlusion baked into the skull) and drawn full bright so the night cannot hide it,
 * with the dead's blue fire only round it: a thin glowing shell over its bones, its eyes burning in black sockets, the floor
 * cracked and glowing where it comes up through it.
 * <p>
 * The skull and jaw are smooth meshes (GiantMesh: a sugar skull, petals round the sockets, a flower on the brow), turned the
 * way the entity says it looks ({@link SpiritGiantEntity#skull}: the same sums the server aims its lasers with). Under it a neck
 * and a spine of separate vertebrae, each with its spines and processes, curving down into the floor; ten pairs of ribs that
 * sweep back from the spine round to a sternum (the lowest two floating), breathing; collarbones out to the shoulders, shoulder
 * blades behind the ribs; each arm a humerus down to its elbow, then a radius and an ulna side by side down to the wrist (two
 * bones, so the forearm has depth from every side); a hand of carpals, four fingers of three bones and a thumb, knuckles at
 * every joint, curling and splaying, lying flat on the floor at rest. Its hands are where the server hits with them
 * ({@link SpiritGiantEntity#hand}); the body twists into its swipes and leans into its slams. Two passes: the bone solid (one
 * buffer, never interleaved), then all the light additive.
 */
public class SpiritGiantRenderer extends EntityRenderer<SpiritGiantEntity> {
    private static final ResourceLocation WHITE = PaponMod.id("textures/misc/white.png");
    private static final float V = SpiritGiantEntity.V;
    private static final int[] BONE = {236, 229, 210}, BONE_DARK = {170, 160, 150}, CART = {196, 206, 214};
    private static final int[] CYAN = {60, 214, 255}, PINK = {255, 60, 170}, GOLD = {255, 160, 30};
    private static final int FULL = LightTexture.FULL_BRIGHT;

    public SpiritGiantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
        // the meshes are built when GiantMesh loads: here, with the resources, not the first frame it rises
        if (GiantMesh.SKULL.quads.length == 0 || GiantMesh.JAW.quads.length == 0) PaponMod.LOG.warn("[papon] El Gigante's skull mesh is empty");
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritGiantEntity e) { return WHITE; }

    @Override
    public boolean shouldRender(SpiritGiantEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    /** What one frame of it needs: the frame of reference, the skull's axes, where its parts are. */
    private static final class Frame {
        Vec3 origin, base, f, r, up = new Vec3(0, 1, 0), head;
        Vec3 sx, sy, sz;          // the skull's axes, scaled to blocks
        Vec3 nx, ny, nz;          // the same, unit (for normals)
        float t, act, risen, breath, glow, jaw, sunk;
        int a, fury;
        Matrix4f m;
        Matrix3f n;
    }

    @Override
    public void render(SpiritGiantEntity g, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Frame fr = new Frame();
        fr.origin = g.getPosition(partial);
        fr.base = g.base();
        fr.f = g.forward();
        fr.r = g.right();
        fr.t = g.tickCount + partial;
        fr.act = g.actTime(partial);
        fr.a = g.act();
        fr.fury = g.fury();
        fr.risen = g.risen(partial);
        fr.sunk = (float) g.sunk(partial);
        fr.breath = Mth.sin(fr.t * (fr.fury >= 3 ? 0.11F : fr.fury == 2 ? 0.08F : 0.05F));
        fr.glow = (0.55F + 0.45F * fr.risen) * (fr.fury >= 3 ? 1.25F + 0.2F * Mth.sin(fr.t * 0.9F) : 1F);
        fr.head = g.head(partial);
        fr.m = ps.last().pose();
        fr.n = ps.last().normal();
        // the skull's axes (SpiritGiantEntity.skull, as a matrix)
        float[] lk = g.look(partial);
        double cy = Math.cos(lk[0]), sy = Math.sin(lk[0]), cp = Math.cos(lk[1]), sp = Math.sin(lk[1]);
        fr.nx = fr.r.scale(cy).subtract(fr.f.scale(sy));
        fr.ny = fr.r.scale(-sp * sy).add(fr.up.scale(cp)).subtract(fr.f.scale(sp * cy));
        fr.nz = fr.r.scale(cp * sy).add(fr.up.scale(sp)).add(fr.f.scale(cp * cy));
        fr.sx = fr.nx.scale(V);
        fr.sy = fr.ny.scale(V);
        fr.sz = fr.nz.scale(V);
        // the jaw: breathing, working, wide as it roars and fires
        float jaw = 6F + 4F * Mth.sin(fr.t * 0.07F);
        if (fr.a == SpiritGiantEntity.CHARGE) jaw = 6F + 18F * Math.min(1, fr.act / 24F);
        if (fr.a == SpiritGiantEntity.LASER) jaw = 28F + 4F * Mth.sin(fr.t * 2.3F);
        if (fr.a == SpiritGiantEntity.ROAR) jaw = fr.act < 12 ? 6 + 30 * fr.act / 12F : fr.act < 40 ? 36F + 3F * Mth.sin(fr.t * 3.1F) : 36 - 30 * Math.min(1, (fr.act - 40) / 10F);
        if (fr.a == SpiritGiantEntity.SWIPE_L || fr.a == SpiritGiantEntity.SWIPE_R || fr.a == SpiritGiantEntity.RISE || fr.a == SpiritGiantEntity.SLAM_BOTH) jaw = 30F;
        fr.jaw = jaw;

        Skeleton sk = new Skeleton(g, fr, partial);

        // ---- the bone: one solid buffer
        VertexConsumer bone = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        skull(bone, fr, GiantMesh.SKULL, 0, false, 0);
        skull(bone, fr, GiantMesh.JAW, fr.jaw, false, 0);
        sk.draw(new Solid(bone, fr));

        // ---- the light: one additive buffer
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        int shell = (int) Math.min(255, 26 * fr.glow);
        skull(glow, fr, GiantMesh.SKULL, 0, true, shell);
        skull(glow, fr, GiantMesh.JAW, fr.jaw, true, shell);
        sk.draw(new Glow(glow, fr, shell));
        // its eyes: two burning lights deep in the sockets, blazing as it charges and fires
        float eye = fr.a == SpiritGiantEntity.CHARGE ? 0.5F + 0.6F * Math.min(1, fr.act / 24F) : fr.a == SpiritGiantEntity.LASER ? 1.35F
                : fr.a == SpiritGiantEntity.ROAR ? 1.0F : 0.6F + 0.1F * fr.fury;
        for (int s : new int[]{-1, 1}) {
            Vec3 c = g.eye(s, partial).subtract(fr.origin);
            glow(glow, fr.m, c, 1.6F + 2.0F * eye, 120, 240, 255, (int) Math.min(255, 230 * eye * fr.glow));
            glow(glow, fr.m, c, 0.7F + 0.9F * eye, 255, 255, 255, (int) Math.min(255, 255 * eye));
        }
        // its heart: a marigold glow behind the sternum
        glow(glow, fr.m, sk.heart.subtract(fr.origin), 2.2F + 0.3F * fr.breath, 255, 160, 30, (int) (120 * fr.glow));
        // where it comes up through the floor: a ring of cracks glowing with the dead
        floorRing(glow, fr, g);
        // the swipe's band, marked on the floor before it comes
        for (int i = 0; i < 2; i++) {
            boolean swipe = (fr.a == SpiritGiantEntity.SWIPE_L && i == 0) || (fr.a == SpiritGiantEntity.SWIPE_R && i == 1);
            if (swipe && fr.act < SpiritGiantEntity.SWIPE_FROM + 2) swipeBand(glow, fr, g, i);
        }
    }

    // ------------------------------------------------------------------ the skull

    /** A skull mesh, opened {@code jawDeg} about the hinge; as bone, or ({@code shell}) as its glow pushed out along the normals. */
    private static void skull(VertexConsumer vc, Frame fr, GiantMesh.Mesh mesh, float jawDeg, boolean shell, int alpha) {
        float cj = Mth.cos(jawDeg * Mth.DEG_TO_RAD), sj = Mth.sin(jawDeg * Mth.DEG_TO_RAD);
        Vec3 h = fr.head.subtract(fr.origin);
        float hx = (float) h.x, hy = (float) h.y, hz = (float) h.z;
        float ax = (float) fr.sx.x, ay = (float) fr.sx.y, az = (float) fr.sx.z;
        float bx = (float) fr.sy.x, by = (float) fr.sy.y, bz = (float) fr.sy.z;
        float cx = (float) fr.sz.x, cy = (float) fr.sz.y, cz = (float) fr.sz.z;
        float push = shell ? 0.32F : 0;
        int[] q = mesh.quads;
        for (int i = 0; i < q.length; i++) {
            int v = q[i];
            float x = mesh.pos[v * 3], y = mesh.pos[v * 3 + 1], z = mesh.pos[v * 3 + 2];
            float nx = mesh.nrm[v * 3], ny = mesh.nrm[v * 3 + 1], nz = mesh.nrm[v * 3 + 2];
            if (jawDeg != 0) {
                float dy = y - GiantMesh.HINGE_Y, dz = z - GiantMesh.HINGE_Z;
                y = GiantMesh.HINGE_Y + dy * cj - dz * sj;
                z = GiantMesh.HINGE_Z + dy * sj + dz * cj;
                float ny2 = ny * cj - nz * sj, nz2 = ny * sj + nz * cj;
                ny = ny2;
                nz = nz2;
            }
            x += nx * push;
            y += ny * push;
            z += nz * push;
            float wx = hx + ax * x + bx * y + cx * z, wy = hy + ay * x + by * y + cy * z, wz = hz + az * x + bz * y + cz * z;
            float wnx = ax * nx + bx * ny + cx * nz, wny = ay * nx + by * ny + cy * nz, wnz = az * nx + bz * ny + cz * nz;
            if (shell) {
                vc.vertex(fr.m, wx, wy, wz).color(CYAN[0], CYAN[1], CYAN[2], alpha).endVertex();
            } else {
                int c = mesh.col[v];
                vc.vertex(fr.m, wx, wy, wz).color(c >> 16 & 255, c >> 8 & 255, c & 255, 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(FULL).normal(fr.n, wnx / V, wny / V, wnz / V).endVertex();
            }
        }
    }

    // ------------------------------------------------------------------ the body

    /** Where a piece of bone goes: solid bone or its glow. */
    private interface Out {
        /** A tube from a (radius ra) to b (radius rb), {@code sides} round, coloured. */
        void tube(Vec3 a, Vec3 b, float ra, float rb, int[] col, int sides);

        /** A ball (a knuckle, a joint's head). */
        void ball(Vec3 c, float r, int[] col);

        /** A flat plate: a triangle a, b, c of {@code thick} (a shoulder blade). */
        void plate(Vec3 a, Vec3 b, Vec3 c, float thick, int[] col);
    }

    /** Solid, lit bone. */
    private static final class Solid implements Out {
        final VertexConsumer vc;
        final Frame fr;

        Solid(VertexConsumer vc, Frame fr) {
            this.vc = vc;
            this.fr = fr;
        }

        void v(Vec3 p, Vec3 n, int[] c, float shade) {
            vc.vertex(fr.m, (float) (p.x - fr.origin.x), (float) (p.y - fr.origin.y), (float) (p.z - fr.origin.z))
                    .color((int) (c[0] * shade), (int) (c[1] * shade), (int) (c[2] * shade), 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(FULL).normal(fr.n, (float) n.x, (float) n.y, (float) n.z).endVertex();
        }

        @Override
        public void tube(Vec3 a, Vec3 b, float ra, float rb, int[] col, int sides) {
            Vec3 d = b.subtract(a);
            double len = d.length();
            if (len < 1e-4) return;
            Vec3 dir = d.scale(1 / len);
            Vec3 u = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 s1 = dir.cross(u).normalize(), s2 = dir.cross(s1).normalize();
            for (int i = 0; i < sides; i++) {
                double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides;
                Vec3 n0 = s1.scale(Math.cos(a0)).add(s2.scale(Math.sin(a0))), n1 = s1.scale(Math.cos(a1)).add(s2.scale(Math.sin(a1)));
                // the underside a little darker: the bone's own shadow
                float k0 = 0.82F + 0.18F * (float) (0.5 + 0.5 * n0.y), k1 = 0.82F + 0.18F * (float) (0.5 + 0.5 * n1.y);
                v(a.add(n0.scale(ra)), n0, col, k0);
                v(b.add(n0.scale(rb)), n0, col, k0);
                v(b.add(n1.scale(rb)), n1, col, k1);
                v(a.add(n1.scale(ra)), n1, col, k1);
            }
        }

        @Override
        public void ball(Vec3 c, float r, int[] col) {
            int lat = 5, lon = 8;
            for (int i = 0; i < lat; i++) {
                double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
                for (int j = 0; j < lon; j++) {
                    double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                    Vec3[] ns = {sph(t0, p0), sph(t0, p1), sph(t1, p1), sph(t1, p0)};
                    for (Vec3 n : ns) v(c.add(n.scale(r)), n, col, 0.82F + 0.18F * (float) (0.5 + 0.5 * n.y));
                }
            }
        }

        @Override
        public void plate(Vec3 a, Vec3 b, Vec3 c, float thick, int[] col) {
            Vec3 n = b.subtract(a).cross(c.subtract(a)).normalize();
            Vec3 o = n.scale(thick / 2);
            v(a.add(o), n, col, 1);
            v(b.add(o), n, col, 1);
            v(c.add(o), n, col, 1);
            v(c.add(o), n, col, 1);
            Vec3 m = n.scale(-1);
            v(a.subtract(o), m, col, 0.85F);
            v(c.subtract(o), m, col, 0.85F);
            v(b.subtract(o), m, col, 0.85F);
            v(b.subtract(o), m, col, 0.85F);
            Vec3[] p = {a, b, c};
            for (int i = 0; i < 3; i++) {
                Vec3 p0 = p[i], p1 = p[(i + 1) % 3];
                Vec3 en = p1.subtract(p0).cross(n).normalize();
                v(p0.add(o), en, col, 0.9F);
                v(p0.subtract(o), en, col, 0.9F);
                v(p1.subtract(o), en, col, 0.9F);
                v(p1.add(o), en, col, 0.9F);
            }
        }
    }

    private static Vec3 sph(double t, double p) { return new Vec3(Math.cos(t) * Math.cos(p), Math.sin(t), Math.cos(t) * Math.sin(p)); }

    /** The same shapes as a faint shell of the dead's blue fire round the bone. */
    private static final class Glow implements Out {
        final VertexConsumer vc;
        final Frame fr;
        final int alpha;

        Glow(VertexConsumer vc, Frame fr, int alpha) {
            this.vc = vc;
            this.fr = fr;
            this.alpha = alpha;
        }

        void v(Vec3 p, int al) {
            vc.vertex(fr.m, (float) (p.x - fr.origin.x), (float) (p.y - fr.origin.y), (float) (p.z - fr.origin.z)).color(CYAN[0], CYAN[1], CYAN[2], al).endVertex();
        }

        @Override
        public void tube(Vec3 a, Vec3 b, float ra, float rb, int[] col, int sides) {
            Vec3 d = b.subtract(a);
            double len = d.length();
            if (len < 1e-4) return;
            Vec3 dir = d.scale(1 / len);
            Vec3 u = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 s1 = dir.cross(u).normalize(), s2 = dir.cross(s1).normalize();
            float pa = ra + 0.28F, pb = rb + 0.28F;
            int n = Math.max(4, sides - 2);
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 n0 = s1.scale(Math.cos(a0)).add(s2.scale(Math.sin(a0))), n1 = s1.scale(Math.cos(a1)).add(s2.scale(Math.sin(a1)));
                // facing out (the lightning render type culls back faces)
                v(a.add(n0.scale(pa)), alpha);
                v(a.add(n1.scale(pa)), alpha);
                v(b.add(n1.scale(pb)), alpha);
                v(b.add(n0.scale(pb)), alpha);
            }
        }

        @Override
        public void ball(Vec3 c, float r, int[] col) {}

        @Override
        public void plate(Vec3 a, Vec3 b, Vec3 c, float thick, int[] col) {}
    }

    /**
     * Its body this frame: the spine's curve, the ribs, the girdle, the arms (two-bone IK to the hands the entity gives), all in
     * world space; the twist into a swipe and the breath applied.
     */
    private static final class Skeleton {
        final Frame fr;
        final SpiritGiantEntity g;
        final float partial;
        final Vec3[] spine = new Vec3[18];
        Vec3 neck, heart, manubrium;
        final Vec3[] shoulder = new Vec3[2], hand = new Vec3[2];
        final double twist;

        Skeleton(SpiritGiantEntity g, Frame fr, float partial) {
            this.g = g;
            this.fr = fr;
            this.partial = partial;
            // twisting into a swipe: the shoulders turn with the sweeping hand
            double tw = 0;
            if (fr.a == SpiritGiantEntity.SWIPE_L || fr.a == SpiritGiantEntity.SWIPE_R) {
                int i = fr.a == SpiritGiantEntity.SWIPE_L ? 0 : 1;
                double k = fr.act < SpiritGiantEntity.SWIPE_T - 8 ? 1 : Math.max(0, (SpiritGiantEntity.SWIPE_T - fr.act) / 8.0);
                tw = SpiritGiantEntity.swipeAngle(i, fr.act) * 0.32 * k;
            }
            twist = tw;
            // the spine: from below the floor up to under the skull, bowed back through the chest
            Vec3 up = fr.up;
            Vec3 skullBase = fr.head.add(fr.ny.scale(-6.4 * V)).add(fr.nz.scale(-2.2 * V));
            Vec3 p0 = fr.base.add(fr.f.scale(-1.0)).add(0, -4.5 - fr.sunk, 0);
            Vec3 p2 = skullBase;
            double lean = g.lean(partial);
            Vec3 p1 = fr.base.add(fr.f.scale(-3.2 + lean * 0.3)).add(0, 12 - fr.sunk, 0);
            for (int i = 0; i < spine.length; i++) {
                double t = i / (double) (spine.length - 1);
                spine[i] = p0.scale((1 - t) * (1 - t)).add(p1.scale(2 * (1 - t) * t)).add(p2.scale(t * t));
            }
            neck = spine[spine.length - 4];
            Vec3 chestTop = spine[spine.length - 5];
            manubrium = turn(chestTop.add(fr.f.scale(3.6)).add(0, -0.8, 0));
            heart = turn(spine[9].add(fr.f.scale(3.0)));
            for (int i = 0; i < 2; i++) {
                shoulder[i] = turn(g.shoulder(i, partial).add(0, 0.25 * fr.breath, 0));
                hand[i] = g.hand(i, partial);
            }
        }

        /** Turned with the twist about the spine's line through the base. */
        Vec3 turn(Vec3 p) {
            if (twist == 0) return p;
            Vec3 rel = p.subtract(fr.base);
            double c = Math.cos(twist), s = Math.sin(twist);
            // about the vertical: positive toward its right
            Vec3 f = fr.f, r = fr.r;
            double fc = rel.dot(f), rc = rel.dot(r);
            double f2 = fc * c - rc * s, r2 = fc * s + rc * c;
            return fr.base.add(f.scale(f2)).add(r.scale(r2)).add(0, rel.y, 0);
        }

        void draw(Out out) {
            Vec3 f = fr.f, r = fr.r, up = fr.up;
            // the vertebrae: a body, its spine pointing back and down, its processes out to the sides
            for (int i = 0; i + 1 < spine.length; i++) {
                Vec3 a = turnPart(i, spine[i]), b = turnPart(i + 1, spine[i + 1]);
                Vec3 d = b.subtract(a);
                Vec3 mid = a.add(d.scale(0.5));
                float rad = (float) Mth.lerp(i / (double) spine.length, 1.15, 0.6);
                out.tube(a.add(d.scale(0.12)), a.add(d.scale(0.82)), rad, rad * 0.95F, BONE, 8);
                Vec3 back = f.scale(-1).add(0, -0.45, 0).normalize();
                out.tube(mid, mid.add(back.scale(rad * 2.1)), rad * 0.42F, rad * 0.22F, BONE, 5);
                for (int s = -1; s <= 1; s += 2) out.tube(mid, mid.add(r.scale(s * rad * 1.7)).add(f.scale(-0.25)), rad * 0.3F, rad * 0.2F, BONE, 5);
            }
            // the ribs: ten pairs round from the spine to the sternum; the lowest two float free
            float br = 1 + 0.035F * fr.breath;
            Vec3 sternTop = null, sternEnd = null;
            for (int k = 0; k < 10; k++) {
                int vi = spine.length - 6 - k;
                if (vi < 2) break;
                Vec3 at = turnPart(vi, spine[vi]);
                double w = (3.4 + 3.9 * Math.sin(Math.PI * (k + 1.4) / 11.5)) * br;
                double depth = (2.6 + 2.4 * Math.sin(Math.PI * (k + 1.0) / 11)) * br;
                double drop = 1.6 + 0.12 * k;
                boolean floating = k >= 8;
                double end = floating ? 0.62 : 0.86;
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 prev = null;
                    int n = 9;
                    for (int j = 0; j <= n; j++) {
                        double th = Math.PI * end * j / n;
                        double x = s * w * Math.sin(th);
                        double z = -0.7 + (depth + 0.7) * (1 - Math.cos(th)) / 2;
                        double y = -drop * (1 - Math.cos(th)) / 2 - 0.55 * Math.sin(th);
                        Vec3 p = turn(at.add(r.scale(x)).add(f.scale(z)).add(0, y, 0));
                        if (prev != null) out.tube(prev, p, 0.36F, 0.34F, BONE, 6);
                        prev = p;
                    }
                    if (!floating) {
                        // its cartilage on to the sternum
                        Vec3 st = turn(at.add(f.scale(depth + 0.2)).add(0, -drop - 0.3, 0));
                        out.tube(prev, st, 0.28F, 0.26F, CART, 5);
                        if (k == 0) sternTop = st;
                        if (k == 7) sternEnd = st;
                    }
                }
            }
            if (sternTop != null && sternEnd != null) {
                out.tube(manubrium, sternTop, 0.75F, 0.6F, BONE, 6);
                out.tube(sternTop, sternEnd, 0.6F, 0.45F, BONE, 6);
                out.tube(sternEnd, sternEnd.add(0, -1.4, 0).add(f.scale(-0.3)), 0.35F, 0.15F, BONE, 5);
            }
            // the iliac crests, breaking up through the floor round its waist
            for (int s = -1; s <= 1; s += 2) {
                Vec3 prev = null;
                for (int j = 0; j <= 6; j++) {
                    double th = Math.PI * 0.85 * j / 6;
                    Vec3 p = fr.base.add(r.scale(s * (1.5 + 5.0 * Math.sin(th)))).add(f.scale(-1.8 + 4.2 * (1 - Math.cos(th)) / 2)).add(0, 0.3 - 0.4 * Math.sin(th) - fr.sunk, 0);
                    if (prev != null) out.tube(prev, p, 0.55F, 0.5F, BONE, 6);
                    prev = p;
                }
            }
            // the girdle: collarbones, shoulder blades, the heads of the arms
            Vec3 upperBack = turnPart(spine.length - 6, spine[spine.length - 6]);
            Vec3 lowBack = turnPart(spine.length - 11, spine[spine.length - 11]);
            for (int i = 0; i < 2; i++) {
                int s = i == 0 ? 1 : -1;
                Vec3 sh = shoulder[i];
                Vec3 mid = manubrium.lerp(sh, 0.5).add(0, 0.9, 0).add(f.scale(0.4));
                out.tube(manubrium, mid, 0.55F, 0.6F, BONE, 6);
                out.tube(mid, sh.add(f.scale(-0.4)).add(0, 0.6, 0), 0.6F, 0.75F, BONE, 6);
                Vec3 blade0 = sh.add(f.scale(-1.2)).add(0, 0.4, 0);
                Vec3 blade1 = upperBack.add(r.scale(s * 2.0)).add(f.scale(-1.6)).add(0, 0.4, 0);
                Vec3 blade2 = lowBack.add(r.scale(s * 2.6)).add(f.scale(-1.9));
                out.plate(turn(blade0), turn(blade1), turn(blade2), 0.45F, BONE_DARK);
                out.ball(sh, 1.35F, BONE);
                // the arm: humerus, then radius and ulna side by side
                Vec3 h = hand[i];
                Vec3 el = elbow(sh, h, r.scale(s), f);
                out.tube(sh, el, 0.95F, 0.75F, BONE, 8);
                out.ball(el, 1.05F, BONE);
                Vec3 along = h.subtract(el);
                Vec3 side = along.cross(up);
                side = side.lengthSqr() < 1e-4 ? r : side.normalize();
                out.tube(el.add(side.scale(0.42)), h.add(side.scale(0.38)), 0.48F, 0.42F, BONE, 7);
                out.tube(el.add(side.scale(-0.4)).add(along.normalize().scale(-0.5)), h.add(side.scale(-0.3)), 0.55F, 0.38F, BONE, 7);
                hand(out, i, el, h);
            }
        }

        /** The part of the spine above the chest turns with the twist; the lumbar under the floor does not. */
        Vec3 turnPart(int i, Vec3 p) {
            if (twist == 0 || i < 6) return p;
            double k = Math.min(1, (i - 6) / 4.0);
            Vec3 q = turn(p);
            return p.lerp(q, k);
        }

        /** A hand: the wrist's carpals, four fingers fanned and curled, a thumb; flat on the floor at rest, open in a slam. */
        void hand(Out out, int i, Vec3 elbow, Vec3 wrist) {
            int s = i == 0 ? 1 : -1;
            Vec3 up = fr.up;
            double floor = fr.base.y - fr.sunk + 1.2;
            boolean low = wrist.y < floor + 2.5;
            Vec3 along = wrist.subtract(elbow).normalize();
            // flat on the floor the fingers lie along it, pointing out ahead of it
            Vec3 fwd = low ? new Vec3(along.x, 0, along.z).normalize().lerp(fr.f, 0.35).normalize() : along;
            Vec3 side = fwd.cross(up);
            side = side.lengthSqr() < 1e-4 ? fr.r : side.normalize();
            Vec3 palm = side.cross(fwd).normalize();        // the back of the hand
            boolean swipe = (fr.a == SpiritGiantEntity.SWIPE_L && i == 0) || (fr.a == SpiritGiantEntity.SWIPE_R && i == 1);
            boolean slam = (fr.a == SpiritGiantEntity.SLAM_L && i == 0) || (fr.a == SpiritGiantEntity.SLAM_R && i == 1) || fr.a == SpiritGiantEntity.SLAM_BOTH;
            float splay = slam ? 1F : swipe ? 0.95F : 0.45F + 0.15F * Mth.sin(fr.t * 0.1F + i);
            float curl = low && !slam ? 0.55F + 0.1F * Mth.sin(fr.t * 0.07F + i) : (1 - splay) * 0.9F;
            float sc = 1.25F;
            for (int c = 0; c < 3; c++) out.ball(wrist.add(side.scale((c - 1) * 0.7 * sc)).add(fwd.scale(0.5 * sc)), 0.55F * sc, BONE);
            float[] lens = {3.0F, 2.3F, 1.7F, 1.3F};
            for (int k = 0; k < 4; k++) {
                double x = (-1.5 + k) * 0.85 * sc;
                double spread = (k - 1.5) * 0.14 * splay;
                Vec3 dir = fwd.scale(Math.cos(spread)).add(side.scale(Math.sin(spread)));
                Vec3 p = wrist.add(side.scale(x * 0.6)).add(fwd.scale(0.8 * sc));
                Vec3 knuckle = p.add(dir.scale(lens[0] * sc));
                out.tube(p, knuckle, 0.36F * sc, 0.3F * sc, BONE, 6);
                out.ball(knuckle, 0.38F * sc, BONE);
                Vec3 q = knuckle;
                for (int seg = 1; seg < 4; seg++) {
                    double bend = curl * seg * 0.55;
                    Vec3 d = dir.scale(Math.cos(bend)).add(palm.scale(-Math.sin(bend)));
                    Vec3 nq = q.add(d.scale(lens[seg] * sc));
                    out.tube(q, nq, (0.3F - seg * 0.05F) * sc, (0.26F - seg * 0.05F) * sc, BONE, 5);
                    if (seg < 3) out.ball(nq, (0.28F - seg * 0.04F) * sc, BONE);
                    q = nq;
                }
            }
            // the thumb, off the side toward its middle
            Vec3 tb = wrist.add(side.scale(-s * 1.4 * sc)).add(fwd.scale(0.4 * sc));
            Vec3 tdir = fwd.scale(0.7).add(side.scale(-s * 0.7)).normalize();
            Vec3 t1 = tb.add(tdir.scale(1.9 * sc));
            out.tube(tb, t1, 0.4F * sc, 0.32F * sc, BONE, 6);
            out.ball(t1, 0.34F * sc, BONE);
            out.tube(t1, t1.add(tdir.scale(1.5 * sc)).add(palm.scale(-curl * 0.6)), 0.3F * sc, 0.2F * sc, BONE, 5);
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

    // ------------------------------------------------------------------ the light round it

    /** Where it comes up through the floor: cracks out from its waist and a glow, the dead's blue. */
    private static void floorRing(VertexConsumer vc, Frame fr, SpiritGiantEntity g) {
        if (fr.risen <= 0.01F) return;
        Vec3 c = fr.base.subtract(fr.origin).add(0, 0.08, 0);
        Vec3 f = fr.f, r = fr.r;
        int al = (int) (110 * fr.risen * fr.glow);
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = c.add(r.scale(Math.cos(a0) * 7.5)).add(f.scale(Math.sin(a0) * 5.5));
            Vec3 p1 = c.add(r.scale(Math.cos(a1) * 7.5)).add(f.scale(Math.sin(a1) * 5.5));
            Vec3 q0 = c.add(r.scale(Math.cos(a0) * 10.5)).add(f.scale(Math.sin(a0) * 8));
            Vec3 q1 = c.add(r.scale(Math.cos(a1) * 10.5)).add(f.scale(Math.sin(a1) * 8));
            quad2(vc, fr.m, p0, p1, q1, q0, al, 0);
        }
        // the cracks: jagged lines out over the floor
        long seed = g.getId() * 31L;
        for (int k = 0; k < 12; k++) {
            double ang = Math.PI * 2 * k / 12 + ((seed >> k) & 7) * 0.05;
            Vec3 dir = r.scale(Math.cos(ang)).add(f.scale(Math.sin(ang)));
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            Vec3 p = c.add(dir.scale(6.5));
            for (int s = 0; s < 4; s++) {
                Vec3 q = p.add(dir.scale(2.2 + ((k * 7 + s * 3) % 5) * 0.4)).add(side.scale((((k + s) % 3) - 1) * 0.9));
                Vec3 w = side.scale(0.18 - s * 0.03);
                quad2(vc, fr.m, p.subtract(w), q.subtract(w.scale(0.6)), q.add(w.scale(0.6)), p.add(w), (int) (al * 1.6 * (1 - s * 0.2)), (int) (al * 1.6 * (1 - (s + 1) * 0.2)));
                p = q;
            }
        }
    }

    private static void quad2(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int a0, int a1) {
        int[][] order = {{0, 1, 2, 3}, {3, 2, 1, 0}};
        Vec3[] p = {p0, p1, p2, p3};
        int[] al = {Math.min(255, a0), Math.min(255, a1), Math.min(255, a1), Math.min(255, a0)};
        for (int[] o : order) {
            for (int i : o) vc.vertex(m, (float) p[i].x, (float) p[i].y, (float) p[i].z).color(CYAN[0], CYAN[1], CYAN[2], al[i]).endVertex();
        }
    }

    /** The band a swipe will sweep: an arc on the floor round it, pulsing as it comes. */
    private static void swipeBand(VertexConsumer vc, Frame fr, SpiritGiantEntity g, int i) {
        double rad = g.swipeRadius();
        double a0 = SpiritGiantEntity.swipeAngle(i, 0), a1 = SpiritGiantEntity.swipeAngle(i, 99);
        float k = Mth.clamp(fr.act / SpiritGiantEntity.SWIPE_FROM, 0, 1);
        int al = (int) ((90 + 110 * (0.5F + 0.5F * Mth.sin(fr.t * 1.4F))) * (0.4F + 0.6F * k));
        Vec3 c = fr.base.subtract(fr.origin).add(0, 0.12, 0);
        Vec3 f = fr.f, r = fr.r;
        int n = 28;
        double[] rr = {rad - 5.2, rad + 5.2};
        for (int s = 0; s < n; s++) {
            double u0 = a0 + (a1 - a0) * s / n, u1 = a0 + (a1 - a0) * (s + 1) / n;
            Vec3 q0 = c.add(f.scale(Math.cos(u0) * rr[0])).add(r.scale(-Math.sin(u0) * rr[0]));
            Vec3 q1 = c.add(f.scale(Math.cos(u0) * rr[1])).add(r.scale(-Math.sin(u0) * rr[1]));
            Vec3 q2 = c.add(f.scale(Math.cos(u1) * rr[1])).add(r.scale(-Math.sin(u1) * rr[1]));
            Vec3 q3 = c.add(f.scale(Math.cos(u1) * rr[0])).add(r.scale(-Math.sin(u1) * rr[0]));
            int[] col = (s & 1) == 0 ? PINK : CYAN;
            for (Vec3[] w : new Vec3[][]{{q0, q1, q2, q3}, {q3, q2, q1, q0}}) {
                for (Vec3 p : w) vc.vertex(fr.m, (float) p.x, (float) p.y, (float) p.z).color(col[0], col[1], col[2], al / 2).endVertex();
            }
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
