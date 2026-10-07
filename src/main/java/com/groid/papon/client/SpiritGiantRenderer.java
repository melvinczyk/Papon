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
    private static final int[] BONE = {228, 218, 194}, BONE_DARK = {178, 166, 148}, CART = {200, 208, 214};
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
        fr.base = g.base(partial);
        fr.f = g.forward(partial);
        fr.r = g.right(partial);
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
        if (fr.a == SpiritGiantEntity.GRAB) jaw = fr.act < SpiritGiantEntity.GRAB_CLOSE ? 22F + 10F * Mth.sin(fr.t * 0.6F) : 34F + 4F * Mth.sin(fr.t * 2.1F);
        if (fr.a == SpiritGiantEntity.LUNGE) jaw = fr.act < SpiritGiantEntity.LUNGE_GO ? 10F + 26F * fr.act / SpiritGiantEntity.LUNGE_GO : 36F - 20F * Math.min(1, (fr.act - SpiritGiantEntity.LUNGE_STOP) / 20F);
        // heeding him: a low growl through barely parted teeth; dragged under: roaring, gnashing, screaming as it goes
        if (fr.a == SpiritGiantEntity.HEED) jaw = 9F + 5F * Mth.sin(fr.t * 0.9F);
        if (fr.a == SpiritGiantEntity.SINK) jaw = fr.act < SpiritGiantEntity.DRAG_CLING ? 36F : fr.act < SpiritGiantEntity.DRAG_TEAR_L
                ? 18F + 10F * Math.abs(Mth.sin(fr.t * 0.7F)) : 38F + 3F * Mth.sin(fr.t * 2.9F);
        fr.jaw = jaw;

        Skeleton sk = new Skeleton(g, fr, partial);

        // ---- the bone: one solid buffer
        VertexConsumer bone = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        skull(bone, fr, GiantMesh.SKULL, 0, false, 0);
        skull(bone, fr, GiantMesh.JAW, fr.jaw, false, 0);
        Solid solid = new Solid(bone, fr);
        sk.draw(solid);
        sk.paint(solid);
        hat(solid, fr);
        spirits(null, solid, fr, g, sk, partial);

        // ---- the light: one additive buffer
        VertexConsumer glow = buffers.getBuffer(PaponRenderTypes.glow());
        int shell = (int) Math.min(255, 26 * fr.glow);
        skull(glow, fr, GiantMesh.SKULL, 0, true, shell);
        skull(glow, fr, GiantMesh.JAW, fr.jaw, true, shell);
        sk.draw(new Glow(glow, fr, shell));
        hatGlow(glow, fr, shell);
        // its eyes: two burning lights deep in the sockets, blazing as it charges and fires
        float eye = fr.a == SpiritGiantEntity.CHARGE ? 0.5F + 0.6F * Math.min(1, fr.act / 24F) : fr.a == SpiritGiantEntity.LASER ? 1.35F
                : fr.a == SpiritGiantEntity.ROAR ? 1.0F : 0.6F + 0.1F * fr.fury;
        // La Mano's point-blank blast: its eyes swelling into two suns in front of you, then firing
        float charge = g.charge(partial);
        if (charge >= 0) eye = Math.max(eye, 0.6F + 1.3F * charge + (charge < 1 ? 0.15F * Mth.sin(fr.t * (1.5F + 2F * charge)) : 0));
        for (int s : new int[]{-1, 1}) {
            Vec3 c = g.eye(s, partial).subtract(fr.origin);
            glow(glow, fr.m, c, 1.6F + 2.0F * eye, 120, 240, 255, (int) Math.min(255, 230 * eye * fr.glow));
            glow(glow, fr.m, c, 0.7F + 0.9F * eye, 255, 255, 255, (int) Math.min(255, 255 * eye));
        }
        // its heart: a marigold glow behind the sternum
        glow(glow, fr.m, sk.heart.subtract(fr.origin), 2.2F + 0.3F * fr.breath, 255, 160, 30, (int) (120 * fr.glow));
        // where it comes up through the floor: a ring of cracks glowing with the dead, burning down like a clock
        floorRing(glow, fr, g, partial);
        // the dead reaching up out of the rift for it: a few as its time runs out, a host dragging it under
        spirits(glow, null, fr, g, sk, partial);
        // the swipe's band, marked on the floor before it comes
        for (int i = 0; i < 2; i++) {
            boolean swipe = (fr.a == SpiritGiantEntity.SWIPE_L && i == 0) || (fr.a == SpiritGiantEntity.SWIPE_R && i == 1);
            if (swipe && fr.act < SpiritGiantEntity.SWIPE_FROM + 2) swipeBand(glow, fr, g, i);
        }
        tells(glow, fr, g, partial);
    }

    // ------------------------------------------------------------------ the tells

    /**
     * What it is about to do, shown in the world (2026-10-06, the user: "like big tells the player can see"; the screen's side is
     * client/GiantTells): the eyes swelling into suns with rings pulling in and a line to where each beam will go, a pillar of light
     * over each spot a hand will come down on, over where La Mano will close and along the path of La Embestida.
     */
    private static void tells(VertexConsumer vc, Frame fr, SpiritGiantEntity g, float partial) {
        Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(fr.origin);
        switch (fr.a) {
            case SpiritGiantEntity.CHARGE -> laserTell(vc, fr, g, partial, cam);
            case SpiritGiantEntity.SLAM_L, SpiritGiantEntity.SLAM_R -> {
                float k = Mth.clamp(fr.act / SpiritGiantEntity.SLAM_HIT, 0, 1);
                if (fr.act < SpiritGiantEntity.SLAM_HIT + 3) pillar(vc, fr, g.handMark(fr.a == SpiritGiantEntity.SLAM_L ? 0 : 1), 5.5F, k, VIOLET, 44F);
            }
            case SpiritGiantEntity.SLAM_BOTH -> {
                float k = Mth.clamp(fr.act / SpiritGiantEntity.SLAM2_HIT, 0, 1);
                if (fr.act < SpiritGiantEntity.SLAM2_HIT + 3) {
                    for (int i = 0; i < 2; i++) pillar(vc, fr, g.handMark(i), 6F, k, VIOLET, 52F);
                    Vec3 mid = g.handMark(0).lerp(g.handMark(1), 0.5);
                    floorRing(vc, fr, mid, 9.5F * (1.5F - 0.5F * k), 0.7F, 0.2F + 0.8F * k, VIOLET, fr.t);
                }
            }
            case SpiritGiantEntity.GRAB -> {
                if (!g.caught() && fr.act < SpiritGiantEntity.GRAB_CLOSE + 3) {
                    float k = Mth.clamp(fr.act / SpiritGiantEntity.GRAB_CLOSE, 0, 1);
                    // the whole of its reach: a column of red over where the hand will close
                    pillar(vc, fr, g.handMark(g.grabHand()), 4.4F, 0.35F + 0.65F * k, BLOOD, 90F);
                    Vec3 to = g.goingTo();
                    Vec3 mark = g.handMark(g.grabHand());
                    if (to.lengthSqr() > 1 && fr.act < SpiritGiantEntity.GRAB_WADE && mark.distanceToSqr(to) > 1) {
                        // and the line it wades along to get there
                        floorLine(vc, fr, g.base(partial), to.add(mark.subtract(to).normalize().scale(10)), 1.4F, 0.3F + 0.5F * k, BLOOD, fr.t);
                    }
                }
            }
            case SpiritGiantEntity.STOMP -> {
                float k = Mth.clamp(fr.act / SpiritGiantEntity.STOMP_HIT, 0, 1);
                if (fr.act < SpiritGiantEntity.STOMP_HIT + 3) pillar(vc, fr, g.goingTo(), 5F, k, VIOLET, 40F);
            }
            case SpiritGiantEntity.LUNGE -> {
                Vec3 to = g.goingTo();
                if (fr.act < SpiritGiantEntity.LUNGE_STOP && to.lengthSqr() > 1) {
                    float k = Mth.clamp(fr.act / SpiritGiantEntity.LUNGE_GO, 0, 1);
                    Vec3 b = g.base(partial);
                    Vec3 dir = to.subtract(b).multiply(1, 0, 1).normalize();
                    // the lane it will tear through: a strip of fire-orange down the floor, chevrons running along it
                    floorLine(vc, fr, b.add(dir.scale(3)), to.add(dir.scale(16)), 6.5F, 0.35F + 0.65F * k, EMBER, fr.t);
                    pillar(vc, fr, to.add(dir.scale(10)), 3F, k, EMBER, 40F);
                }
            }
            default -> {}
        }
    }

    private static final int[] VIOLET = {200, 100, 255}, BLOOD = {255, 40, 80}, SKY = {150, 235, 255};

    /** The eyes charging: two suns, rings falling into each, and a line from each to where its beam will land, thickening as it comes. */
    private static void laserTell(VertexConsumer vc, Frame fr, SpiritGiantEntity g, float partial, Vec3 cam) {
        float k = Mth.clamp(fr.act / GiantTells.LASER_CHARGE, 0, 1);
        float flick = 0.75F + 0.25F * Mth.sin(fr.t * (1.2F + 3F * k));
        Vec3 aim = g.aim();
        Vec3 aimRel = aim.subtract(fr.origin);
        for (int s : new int[]{-1, 1}) {
            Vec3 eye = g.eye(s, partial).subtract(fr.origin);
            // a sun in each socket
            glow(vc, fr.m, eye, (2.5F + 6.5F * k * k) * flick, 150, 235, 255, (int) (120 + 135 * k));
            glow(vc, fr.m, eye, (1.2F + 3.0F * k) * flick, 255, 255, 255, (int) (160 + 95 * k));
            // the line its beam will take
            Vec3 d = aimRel.subtract(eye);
            if (d.lengthSqr() < 1) continue;
            float pulse = 0.55F + 0.45F * Mth.sin(fr.t * (0.8F + 2.2F * k) + s);
            ribbon(vc, fr.m, eye, aimRel, cam, 0.9F + 3.2F * k, 90, 220, 255, (int) ((70 + 150 * k) * pulse));
            ribbon(vc, fr.m, eye, aimRel, cam, 0.2F + 0.6F * k, 255, 255, 255, (int) (140 + 115 * k));
            // rings falling into the eye
            Vec3 dn = d.normalize();
            Vec3 u = dn.cross(new Vec3(0, 1, 0));
            u = u.lengthSqr() < 1e-4 ? fr.r : u.normalize();
            Vec3 v = dn.cross(u).normalize();
            for (int r = 0; r < 3; r++) {
                float ph = (fr.t * (0.09F + 0.1F * k) + r / 3F) % 1F;
                float rad = 11F * (1F - ph) + 0.6F;
                int al = (int) (230 * ph * (0.3F + 0.7F * k));
                Vec3 centre = eye.add(dn.scale(1.5F + 3F * ph));
                Vec3 prev = centre.add(u.scale(rad));
                for (int i = 1; i <= 20; i++) {
                    double a = Math.PI * 2 * i / 20;
                    Vec3 p = centre.add(u.scale(Math.cos(a) * rad)).add(v.scale(Math.sin(a) * rad));
                    ribbon(vc, fr.m, prev, p, cam, 0.5F + 0.4F * ph, 190, 245, 255, al);
                    prev = p;
                }
            }
        }
        // where it will land: a column of light and a mark on the floor the size of the burn, flashing white as it fires
        pillar(vc, fr, aim, 3.4F, k, SKY, 56F);
    }

    /**
     * A column of light over a spot on the floor (world position {@code at}), {@code h} tall, brightening and thickening as {@code k}
     * runs 0..1, with a ring on the floor of radius {@code r} closing in on it; whitening as it lands.
     */
    private static void pillar(VertexConsumer vc, Frame fr, Vec3 at, float r, float k, int[] col, float h) {
        Vec3 c = at.subtract(fr.origin).add(0, 0.12, 0);
        float flash = 0.5F + 0.5F * Mth.sin(fr.t * (0.7F + 2.6F * k));
        int[] hot = {(int) Mth.lerp(k * k, col[0], 255), (int) Mth.lerp(k * k, col[1], 255), (int) Mth.lerp(k * k, col[2], 255)};
        float w = r * (0.35F + 0.4F * k);
        int a0 = (int) Math.min(255, (70 + 170 * k) * (0.7F + 0.3F * flash)), a1 = (int) (a0 * 0.12F);
        for (int plane = 0; plane < 2; plane++) {
            Vec3 side = plane == 0 ? new Vec3(w, 0, 0) : new Vec3(0, 0, w);
            Vec3 top = c.add(0, h, 0);
            quad2c(vc, fr.m, c.subtract(side), c.add(side), top.add(side.scale(0.25)), top.subtract(side.scale(0.25)), hot, a0, a1);
        }
        floorRing(vc, fr, at, r * (1.7F - 0.7F * k), 0.5F, 0.25F + 0.75F * k, hot, fr.t);
    }

    /** A ring flat on the floor round {@code at}: radius r, band {@code width} wide, alpha by k (0..1), flickering. */
    private static void floorRing(VertexConsumer vc, Frame fr, Vec3 at, float r, float width, float k, int[] col, float t) {
        Vec3 c = at.subtract(fr.origin).add(0, 0.14, 0);
        int n = 36;
        int al = (int) (Math.min(1F, k) * (150 + 100 * (0.5F + 0.5F * Mth.sin(t * 1.3F))));
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 i0 = c.add(Math.cos(a0) * (r - width), 0, Math.sin(a0) * (r - width)), o0 = c.add(Math.cos(a0) * (r + width), 0, Math.sin(a0) * (r + width));
            Vec3 i1 = c.add(Math.cos(a1) * (r - width), 0, Math.sin(a1) * (r - width)), o1 = c.add(Math.cos(a1) * (r + width), 0, Math.sin(a1) * (r + width));
            quad2c(vc, fr.m, i0, o0, o1, i1, col, al, al);
        }
        // and the filled disc inside it, faint
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            quad2c(vc, fr.m, c, c, p1, p0, col, al / 3, al / 5);
        }
    }

    /** A strip down the floor from {@code a} to {@code b}, {@code half} either side of the line, chevrons running along it. */
    private static void floorLine(VertexConsumer vc, Frame fr, Vec3 a, Vec3 b, float half, float k, int[] col, float t) {
        Vec3 d = b.subtract(a).multiply(1, 0, 1);
        double len = d.length();
        if (len < 1) return;
        Vec3 dir = d.scale(1 / len), side = new Vec3(-dir.z, 0, dir.x).scale(half);
        Vec3 s = a.subtract(fr.origin).add(0, 0.13, 0);
        int segs = Math.max(2, (int) (len / 3));
        int al = (int) (Math.min(1F, k) * 120);
        for (int i = 0; i < segs; i++) {
            Vec3 p0 = s.add(dir.scale(len * i / segs)), p1 = s.add(dir.scale(len * (i + 1) / segs));
            // marching chevrons: every other segment lit, the lit ones sliding along
            boolean lit = ((i + (int) (t * 0.5F)) & 1) == 0;
            int a2 = lit ? al : al / 3;
            quad2c(vc, fr.m, p0.subtract(side), p0.add(side), p1.add(side), p1.subtract(side), col, a2, a2);
        }
        // its edges, hot
        int e = (int) (Math.min(1F, k) * 230);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            Vec3 o = side.scale(sgn);
            quad2c(vc, fr.m, s.add(o), s.add(o.scale(1.06)), s.add(dir.scale(len)).add(o.scale(1.06)), s.add(dir.scale(len)).add(o), col, e, e);
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

    // ------------------------------------------------------------------ its hat

    /**
     * El Catrín's top hat (2026-10-05, the user: "an El Catrín / Día de los Muertos-inspired hat... large and visually recognizable
     * enough that the player can actually see it during the fight"): a tall black crown, a little flared, worn at a rakish tilt
     * on the cranium, a wide brim curled up at the sides, a marigold band with a purple stripe, a cluster of marigolds and a
     * rose on the band, and two long feathers sweeping up and back. Skull units (it turns with the skull), drawn as bone is.
     */
    private static final int[] HAT = {44, 38, 52}, HAT_TOP = {70, 62, 82}, BAND = {255, 150, 24}, STRIPE = {122, 44, 186}, BLOOM = {255, 170, 30},
            BLOOM_DEEP = {226, 96, 10}, ROSE = {214, 22, 52}, PLUME = {170, 60, 210}, PLUME_TIP = {255, 90, 170};
    /** The crown: seated at HAT_Y on the cranium, HAT_H tall, radius HAT_R at the base (skull units). */
    private static final float HAT_Y = 6.0F, HAT_H = 11.5F, HAT_R = 6.3F, BRIM = 10.8F;
    private static final double HAT_TILT = Math.toRadians(-11), HAT_BACK = Math.toRadians(-7);

    /** A point on the hat (its own axes: x right, y up the crown, z forward) to the world. */
    private static Vec3 hp(Frame fr, double x, double y, double z) {
        // tilted over to its left, and back a little
        double ct = Math.cos(HAT_TILT), st = Math.sin(HAT_TILT), cb = Math.cos(HAT_BACK), sb = Math.sin(HAT_BACK);
        double x1 = x * ct - y * st, y1 = x * st + y * ct;
        double y2 = y1 * cb - z * sb, z2 = y1 * sb + z * cb;
        double sx = x1 + 0.4, sy = y2 + HAT_Y, sz = z2 - 0.8;
        return fr.head.add(fr.sx.scale(sx)).add(fr.sy.scale(sy)).add(fr.sz.scale(sz));
    }

    private static void hat(Solid out, Frame fr) {
        int n = 20;
        // the crown: rings up from the band, flaring a little to the top
        float[] ys = {0, 1.6F, 2.9F, 7.0F, HAT_H};
        float[] rs = {HAT_R, HAT_R * 1.0F, HAT_R * 1.01F, HAT_R * 1.04F, HAT_R * 1.1F};
        int[][] cols = {BAND, STRIPE, HAT, HAT};
        for (int ring = 0; ring + 1 < ys.length; ring++) {
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 p00 = hp(fr, Math.cos(a0) * rs[ring], ys[ring], Math.sin(a0) * rs[ring]), p01 = hp(fr, Math.cos(a1) * rs[ring], ys[ring], Math.sin(a1) * rs[ring]);
                Vec3 p10 = hp(fr, Math.cos(a0) * rs[ring + 1], ys[ring + 1], Math.sin(a0) * rs[ring + 1]);
                Vec3 p11 = hp(fr, Math.cos(a1) * rs[ring + 1], ys[ring + 1], Math.sin(a1) * rs[ring + 1]);
                Vec3 nr = p01.subtract(p00).cross(p10.subtract(p00)).normalize().scale(-1);
                // a satin sheen down one side of the crown
                float sheen = ring >= 2 ? 0.85F + 0.35F * (float) Math.max(0, Math.cos(a0 - 0.9)) : 1F;
                out.v(p00, nr, cols[ring], sheen);
                out.v(p10, nr, cols[ring], sheen);
                out.v(p11, nr, cols[ring], sheen);
                out.v(p01, nr, cols[ring], sheen);
            }
        }
        // the top
        Vec3 top = hp(fr, 0, HAT_H, 0);
        Vec3 upN = hp(fr, 0, HAT_H + 1, 0).subtract(top).normalize();
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float r = rs[rs.length - 1];
            Vec3 p0 = hp(fr, Math.cos(a0) * r, HAT_H, Math.sin(a0) * r), p1 = hp(fr, Math.cos(a1) * r, HAT_H, Math.sin(a1) * r);
            out.v(top, upN, HAT_TOP, 1);
            out.v(top, upN, HAT_TOP, 1);
            out.v(p1, upN, HAT_TOP, 1);
            out.v(p0, upN, HAT_TOP, 1);
        }
        // the brim: wide, an oval a little longer front to back, its sides curled up
        int m = 28;
        for (int i = 0; i < m; i++) {
            double a0 = Math.PI * 2 * i / m, a1 = Math.PI * 2 * (i + 1) / m;
            Vec3 i0 = brim(fr, a0, HAT_R * 0.95F, 0), i1 = brim(fr, a1, HAT_R * 0.95F, 0);
            Vec3 o0 = brim(fr, a0, BRIM, 1), o1 = brim(fr, a1, BRIM, 1);
            Vec3 t0 = brim(fr, a0, BRIM, 1).add(fr.ny.scale(-0.5 * SpiritGiantEntity.V)), t1 = brim(fr, a1, BRIM, 1).add(fr.ny.scale(-0.5 * SpiritGiantEntity.V));
            Vec3 nr = o0.subtract(i0).cross(i1.subtract(i0)).normalize();
            if (nr.dot(upN) < 0) nr = nr.scale(-1);
            out.v(i0, nr, HAT, 1);
            out.v(o0, nr, HAT, 1);
            out.v(o1, nr, HAT, 1);
            out.v(i1, nr, HAT, 1);
            out.v(i1, nr.scale(-1), HAT, 0.7F);
            out.v(o1, nr.scale(-1), HAT, 0.7F);
            out.v(o0, nr.scale(-1), HAT, 0.7F);
            out.v(i0, nr.scale(-1), HAT, 0.7F);
            // the brim's edge, bound in the band's colour
            Vec3 en = o0.subtract(hp(fr, 0, o0.subtract(fr.head).dot(fr.ny) / SpiritGiantEntity.V - HAT_Y, 0)).normalize();
            out.v(o0, en, STRIPE, 1);
            out.v(t0, en, STRIPE, 1);
            out.v(t1, en, STRIPE, 1);
            out.v(o1, en, STRIPE, 1);
        }
        // on the band, its left side: marigolds round a red rose, and two feathers sweeping up and back
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(150 + k * 13);
            double y = 0.8 + (k % 2) * 1.0;
            Vec3 c = hp(fr, Math.cos(a) * (HAT_R + 0.7), y, Math.sin(a) * (HAT_R + 0.7));
            out.ball(c, (float) (0.95 * SpiritGiantEntity.V), k % 2 == 0 ? BLOOM : BLOOM_DEEP);
        }
        out.ball(hp(fr, Math.cos(Math.toRadians(170)) * (HAT_R + 1.1), 1.6, Math.sin(Math.toRadians(170)) * (HAT_R + 1.1)), (float) (1.2 * SpiritGiantEntity.V), ROSE);
        for (int k = 0; k < 2; k++) {
            double a = Math.toRadians(176 + k * 16);
            Vec3 prev = hp(fr, Math.cos(a) * (HAT_R + 0.5), 1.8, Math.sin(a) * (HAT_R + 0.5));
            for (int s = 1; s <= 6; s++) {
                double u = s / 6.0;
                double rr = HAT_R + 0.5 + 2.5 * u * u;
                Vec3 q = hp(fr, Math.cos(a) * rr - 2.2 * u, 1.8 + 13.0 * u - 3.0 * u * u, Math.sin(a) * rr - 3.5 * u * u);
                float w = (float) ((0.75 - 0.5 * u + 0.25 * Math.sin(u * Math.PI)) * SpiritGiantEntity.V);
                out.tube(prev, q, w, w * 0.85F, s >= 5 ? PLUME_TIP : PLUME, 5);
                prev = q;
            }
        }
    }

    /** A point on the brim: {@code a} round it, out to {@code r}, {@code edge} 1 at its rim (where it curls up at the sides). */
    private static Vec3 brim(Frame fr, double a, float r, int edge) {
        double x = Math.cos(a) * r, z = Math.sin(a) * r * 1.08;
        double curl = edge * 1.6 * Math.pow(Math.abs(Math.cos(a)), 2.5) - edge * 0.4 * Math.max(0, Math.sin(a));
        return hp(fr, x, 0.1 + curl, z);
    }

    /** The hat's outline in the dead's light, so it reads against the night. */
    private static void hatGlow(VertexConsumer vc, Frame fr, int alpha) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float r = HAT_R * 1.1F + 0.35F;
            Vec3 p0 = hp(fr, Math.cos(a0) * r, HAT_H + 0.2, Math.sin(a0) * r).subtract(fr.origin), p1 = hp(fr, Math.cos(a1) * r, HAT_H + 0.2, Math.sin(a1) * r).subtract(fr.origin);
            Vec3 q0 = hp(fr, Math.cos(a0) * r, 3.0, Math.sin(a0) * r).subtract(fr.origin), q1 = hp(fr, Math.cos(a1) * r, 3.0, Math.sin(a1) * r).subtract(fr.origin);
            quad2(vc, fr.m, p0, p1, q1, q0, alpha * 2, 0);
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
                // the underside a little darker: the bone's own shadow; and old bone's grain, a little different down each facet
                float g0 = GRAIN[(i * 5 + sides) % GRAIN.length], g1 = GRAIN[((i + 1) % sides * 5 + sides) % GRAIN.length];
                float k0 = (0.62F + 0.38F * (float) (0.5 + 0.5 * n0.y)) * g0, k1 = (0.62F + 0.38F * (float) (0.5 + 0.5 * n1.y)) * g1;
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
                    for (Vec3 n : ns) v(c.add(n.scale(r)), n, col, 0.62F + 0.38F * (float) (0.5 + 0.5 * n.y));
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

    /** Weathered bone: each facet of a bone a shade apart (stable: by the facet's place round the bone). */
    private static final float[] GRAIN = {1.0F, 0.95F, 1.04F, 0.97F, 0.91F, 1.02F, 0.98F, 1.05F, 0.94F, 1.0F, 0.96F};

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
        Vec3 neck, heart, manubrium, manubriumRaw;
        final Vec3[] shoulder = new Vec3[2], hand = new Vec3[2];
        final double twist;
        /** On its legs (the spawn egg's giant): its trunk's forward and up (bent at the hips), the middle of its waist, its legs. */
        final boolean walker;
        final Vec3 F, U, centre;
        Vec3 pelvis;
        final Vec3[] hip = new Vec3[2], knee = new Vec3[2], ankle = new Vec3[2];

        Skeleton(SpiritGiantEntity g, Frame fr, float partial) {
            this.g = g;
            this.fr = fr;
            this.partial = partial;
            walker = g.walker();
            F = g.torsoForward(partial);
            U = g.torsoUp(partial);
            pelvis = g.pelvis(partial);
            centre = walker ? pelvis : fr.base;
            // twisting into a swipe: the shoulders turn with the sweeping hand
            double tw = 0;
            if (fr.a == SpiritGiantEntity.SWIPE_L || fr.a == SpiritGiantEntity.SWIPE_R) {
                int i = fr.a == SpiritGiantEntity.SWIPE_L ? 0 : 1;
                double k = fr.act < SpiritGiantEntity.SWIPE_T - 8 ? 1 : Math.max(0, (SpiritGiantEntity.SWIPE_T - fr.act) / 8.0);
                tw = SpiritGiantEntity.swipeAngle(i, fr.act) * 0.32 * k;
            }
            if (walker) {
                // striding, its shoulders swing against its hips
                for (int i = 0; i < 2; i++) {
                    hip[i] = g.hipJoint(i, partial);
                    knee[i] = g.knee(i, partial);
                    ankle[i] = g.ankle(i, partial);
                }
                double lead = ankle[0].subtract(ankle[1]).dot(fr.f);
                if (fr.a == SpiritGiantEntity.IDLE) tw += Mth.clamp(lead * 0.018, -0.16, 0.16);
            }
            twist = tw;
            // the spine: from below the floor up to under the skull, bowed back through the chest (on its legs: up out of its pelvis)
            Vec3 skullBase = fr.head.add(fr.ny.scale(-6.4 * V)).add(fr.nz.scale(-2.2 * V));
            Vec3 p0, p1, p2 = skullBase;
            if (walker) {
                p0 = pelvis.add(U.scale(-0.8)).add(F.scale(-1.8));
                p1 = pelvis.add(F.scale(-3.4)).add(U.scale(12));
            } else {
                double lean = g.lean(partial);
                p0 = fr.base.add(fr.f.scale(-1.0)).add(0, -4.5 - fr.sunk, 0);
                p1 = fr.base.add(fr.f.scale(-3.2 + lean * 0.3)).add(0, 12 - fr.sunk, 0);
            }
            for (int i = 0; i < spine.length; i++) {
                double t = i / (double) (spine.length - 1);
                spine[i] = p0.scale((1 - t) * (1 - t)).add(p1.scale(2 * (1 - t) * t)).add(p2.scale(t * t));
            }
            neck = spine[spine.length - 4];
            Vec3 chestTop = spine[spine.length - 5];
            manubriumRaw = chestTop.add(F.scale(3.6)).add(U.scale(-0.8));
            manubrium = turn(manubriumRaw);
            heart = turn(spine[9].add(F.scale(3.0)));
            for (int i = 0; i < 2; i++) {
                shoulder[i] = turn(g.shoulder(i, partial).add(U.scale(0.25 * fr.breath)));
                hand[i] = g.hand(i, partial);
            }
        }

        /** Turned with the twist about the spine's line through the base (on its legs: about its trunk, through its waist). */
        Vec3 turn(Vec3 p) {
            if (twist == 0) return p;
            Vec3 rel = p.subtract(centre);
            double c = Math.cos(twist), s = Math.sin(twist);
            // about its trunk's up: positive toward its right
            Vec3 f = F, r = fr.r;
            double fc = rel.dot(f), rc = rel.dot(r);
            double f2 = fc * c - rc * s, r2 = fc * s + rc * c;
            return centre.add(f.scale(f2)).add(r.scale(r2)).add(rel.subtract(f.scale(fc)).subtract(r.scale(rc)));
        }

        void draw(Out out) {
            Vec3 f = F, r = fr.r, up = U;
            // the vertebrae: a body, its spine pointing back and down, its processes out to the sides
            for (int i = 0; i + 1 < spine.length; i++) {
                Vec3 a = turnPart(i, spine[i]), b = turnPart(i + 1, spine[i + 1]);
                Vec3 d = b.subtract(a);
                Vec3 mid = a.add(d.scale(0.5));
                float rad = (float) Mth.lerp(i / (double) spine.length, 1.15, 0.6);
                out.tube(a.add(d.scale(0.12)), a.add(d.scale(0.82)), rad, rad * 0.95F, BONE, 8);
                Vec3 back = f.scale(-1).add(U.scale(-0.45)).normalize();
                out.tube(mid, mid.add(back.scale(rad * 2.1)), rad * 0.42F, rad * 0.22F, BONE, 5);
                for (int s = -1; s <= 1; s += 2) out.tube(mid, mid.add(r.scale(s * rad * 1.7)).add(f.scale(-0.25)), rad * 0.3F, rad * 0.2F, BONE, 5);
            }
            // the ribs: ten pairs, each sweeping back and out from its vertebra, round the side and down to the front (the front end
            // well below the back, as a real ribcage hangs), the true ribs joined to the sternum by cartilage that turns back up to
            // it (the costal arch), the lowest two floating free; small at the top, widest in the middle
            float br = 1 + 0.035F * fr.breath;
            Vec3 sternTop = manubriumRaw.add(U.scale(-0.6)), xiphoid = null;
            for (int k = 0; k < 10; k++) {
                int vi = spine.length - 6 - k;
                if (vi < 2) break;
                Vec3 at = turnPart(vi, spine[vi]);
                double w = (2.6 + 4.6 * Math.sin(Math.PI * (k + 1.6) / 12.5)) * br;
                double depth = (2.4 + 2.8 * Math.sin(Math.PI * (k + 1.0) / 11)) * br;
                double drop = 2.6 + 0.25 * k;
                boolean floating = k >= 8;
                double end = floating ? 0.55 : 0.78;
                Vec3 sternum = sternTop.add(U.scale(-1.15 * Math.min(k, 6))).add(f.scale(-0.06 * Math.min(k, 6)));
                if (k == 6) xiphoid = sternum.add(U.scale(-1.6)).add(f.scale(-0.3));
                float rr = 0.42F - 0.012F * Math.abs(k - 4);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 prev = null;
                    int n = 10;
                    for (int j = 0; j <= n; j++) {
                        double th = Math.PI * end * j / n;
                        // out and back from the spine first (the angle of the rib), then round
                        double x = s * w * Math.sin(th);
                        double z = -0.9 * Math.sin(Math.min(th * 2.2, Math.PI)) * 0.6 + (depth + 0.6) * (1 - Math.cos(th)) / 2 - 0.4;
                        double y = -drop * (1 - Math.cos(th)) / 2;
                        Vec3 p = turn(at.add(r.scale(x)).add(f.scale(z)).add(U.scale(y)));
                        if (prev != null) out.tube(prev, p, rr, rr * 0.94F, BONE, 6);
                        prev = p;
                    }
                    if (!floating) {
                        // the cartilage: forward and back up to the sternum (the lower ones to its tip)
                        Vec3 to = k <= 6 ? turn(sternum.add(r.scale(s * 0.55))) : turn(xiphoid != null ? xiphoid.add(r.scale(s * 0.9)) : sternum);
                        Vec3 mid = prev.lerp(to, 0.5).add(f.scale(0.5)).add(U.scale(-0.35));
                        out.tube(prev, mid, rr * 0.8F, rr * 0.72F, CART, 5);
                        out.tube(mid, to, rr * 0.72F, rr * 0.62F, CART, 5);
                    }
                }
            }
            if (xiphoid != null) {
                out.tube(manubrium, turn(sternTop), 0.85F, 0.7F, BONE, 6);
                out.tube(turn(sternTop), turn(xiphoid.add(U.scale(1.0))), 0.62F, 0.5F, BONE, 6);
                out.tube(turn(xiphoid.add(U.scale(1.0))), turn(xiphoid), 0.42F, 0.16F, BONE, 5);
            }
            // the iliac crests, breaking up through the floor round its waist (on its legs: the whole pelvis, and the legs under it)
            if (walker) legs(out);
            else for (int s = -1; s <= 1; s += 2) {
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
                Vec3 mid = manubrium.lerp(sh, 0.5).add(U.scale(0.9)).add(f.scale(0.4));
                out.tube(manubrium, mid, 0.55F, 0.6F, BONE, 6);
                out.tube(mid, sh.add(f.scale(-0.4)).add(U.scale(0.6)), 0.6F, 0.75F, BONE, 6);
                // the shoulder blade: flat on the back of the ribs, from behind the shoulder in toward the spine
                Vec3 blade0 = sh.add(f.scale(-1.6)).add(r.scale(-s * 1.4)).add(U.scale(0.3));
                Vec3 blade1 = upperBack.add(r.scale(s * 2.2)).add(f.scale(-2.3)).add(U.scale(0.4));
                Vec3 blade2 = lowBack.add(r.scale(s * 3.2)).add(f.scale(-2.5));
                out.plate(turn(blade0), turn(blade1), turn(blade2), 0.4F, BONE_DARK);
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

        /**
         * Its pelvis and legs (the walker, 2026-10-06): a sacrum and tailbone down from the spine between two iliac wings (their
         * crests a thick rim, the blade filled in) curling round to the front; each hip socket ringed by the pubis forward to the
         * symphysis and the ischium down and back. Each leg a femur from its head in the socket, out over the trochanter and down to
         * the knee's two condyles and the kneecap; the tibia and the thinner fibula beside it down to the ankle's knobs; a foot of
         * heel, five metatarsals fanned from the ankle and toes of two bones curling to claws, the big toe inside.
         */
        void legs(Out out) {
            Vec3 r = fr.r;
            Vec3 sac = pelvis.add(U.scale(-3.4)).add(F.scale(-2.4));
            out.tube(spine[0], sac, 1.35F, 0.6F, BONE, 7);
            out.tube(sac, sac.add(U.scale(-1.4)).add(F.scale(0.7)), 0.5F, 0.18F, BONE, 5);
            Vec3 sym = pelvis.add(F.scale(2.6)).add(U.scale(-4.6));
            for (int i = 0; i < 2; i++) {
                int s = i == 0 ? 1 : -1;
                Vec3 socket = hip[i];
                // the wing: its crest round from the sacrum to the front, the blade between it and the socket
                Vec3 prev = null;
                for (int j = 0; j <= 6; j++) {
                    double th = Math.PI * 0.85 * j / 6;
                    Vec3 c = pelvis.add(r.scale(s * (1.3 + 4.4 * Math.sin(th)))).add(F.scale(-2.3 + 4.6 * (1 - Math.cos(th)) / 2))
                            .add(U.scale(0.7 - 0.6 * Math.sin(th)));
                    if (prev != null) {
                        out.tube(prev, c, 0.55F, 0.5F, BONE, 6);
                        out.plate(prev, c, socket.add(U.scale(0.6)), 0.35F, BONE_DARK);
                    }
                    prev = c;
                }
                out.ball(socket, 1.25F, BONE_DARK);
                // the pubis forward to the symphysis; the ischium down and back from the socket, and round to it
                out.tube(socket.add(F.scale(0.6)), sym.add(r.scale(s * 0.55)), 0.62F, 0.5F, BONE, 6);
                Vec3 isch = socket.add(U.scale(-2.6)).add(F.scale(-0.9)).add(r.scale(-s * 0.8));
                out.tube(socket, isch, 0.75F, 0.62F, BONE, 6);
                out.ball(isch, 0.7F, BONE);
                out.tube(isch, sym.add(r.scale(s * 0.5)).add(U.scale(-0.7)), 0.5F, 0.42F, BONE, 6);
                leg(out, i, s);
            }
            out.ball(sym.add(U.scale(-0.3)), 0.72F, BONE);
        }

        /** The way leg {@code i}'s kneecap faces: out of the bend. */
        Vec3 kneeFront(int i) {
            Vec3 thigh = knee[i].subtract(hip[i]).normalize(), shin = ankle[i].subtract(knee[i]).normalize();
            Vec3 kf = thigh.subtract(shin);
            return kf.lengthSqr() < 1e-4 ? fr.f : kf.normalize();
        }

        void leg(Out out, int i, int s) {
            Vec3 r = fr.r;
            Vec3 h = hip[i], k = knee[i], a = ankle[i];
            Vec3 thigh = k.subtract(h).normalize(), shin = a.subtract(k).normalize();
            Vec3 kf = kneeFront(i);
            Vec3 kside = thigh.cross(kf);
            kside = kside.lengthSqr() < 1e-4 ? r : kside.normalize();
            if (kside.dot(r) * s < 0) kside = kside.scale(-1);               // toward its outside
            // the femur: its head in the socket, the neck out to the trochanter, the shaft down to the knee
            Vec3 troch = h.add(r.scale(s * 1.7)).add(U.scale(-0.7));
            out.ball(h, 1.1F, BONE);
            out.tube(h, troch, 0.72F, 0.85F, BONE, 7);
            out.ball(troch, 0.95F, BONE);
            out.tube(troch, k.subtract(thigh.scale(1.0)), 0.9F, 0.78F, BONE, 8);
            // the knee: two condyles, the kneecap over them
            for (int c = -1; c <= 1; c += 2) out.ball(k.add(kside.scale(c * 0.62)).subtract(thigh.scale(0.4)), 0.95F, BONE);
            out.ball(k.add(kf.scale(1.05)), 0.72F, BONE_DARK);
            // the tibia, thick, and the fibula on the outside
            out.tube(k.add(shin.scale(0.5)), a.subtract(shin.scale(0.5)), 0.82F, 0.56F, BONE, 8);
            out.tube(k.add(shin.scale(0.9)).add(kside.scale(0.95)), a.subtract(shin.scale(0.2)).add(kside.scale(0.75)), 0.36F, 0.3F, BONE, 6);
            out.ball(a.add(kside.scale(0.75)), 0.5F, BONE);
            out.ball(a.subtract(kside.scale(0.6)), 0.5F, BONE);
            out.ball(a, 0.8F, BONE);
            foot(out, i, s, a);
        }

        /** The foot: rolled off its toes as it lifts, its toes up as it comes down, flat planted; the big toe inside. */
        void foot(Out out, int i, int s, Vec3 a) {
            float k = g.step(i, partial);
            double pitch = k < 0 ? 0 : k < 0.5 ? -0.55 * Math.sin(Math.PI * k * 2) : 0.4 * Math.sin(Math.PI * (k - 0.5) * 2);
            Vec3 ff = Vec3.directionFromRotation(0, g.footYaw(i, partial));
            Vec3 fs = new Vec3(-ff.z, 0, ff.x);
            Vec3 fwd = ff.scale(Math.cos(pitch)).add(0, Math.sin(pitch), 0), dn = ff.scale(Math.sin(pitch)).add(0, -Math.cos(pitch), 0);
            Vec3 heel = a.add(fwd.scale(-2.3)).add(dn.scale(0.75));
            out.tube(a, heel, 0.85F, 0.72F, BONE, 7);
            out.ball(heel, 0.78F, BONE);
            float sc = 1.15F;
            for (int m = 0; m < 5; m++) {
                // m 0 the big toe, inside (toward its middle), out to the little toe
                double across = (m - 2) * s;
                Vec3 m0 = a.add(fwd.scale(0.9)).add(dn.scale(0.45)).add(fs.scale(across * 0.38));
                Vec3 m1 = a.add(fwd.scale(4.6 - 0.3 * Math.abs(m - 1))).add(dn.scale(1.0)).add(fs.scale(across * 0.82));
                float w = (m == 0 ? 0.44F : 0.34F) * sc;
                out.tube(m0, m1, w, w * 0.85F, BONE, 6);
                out.ball(m1, w * 0.95F, BONE);
                double len = (m == 0 ? 1.5 : 1.25 - 0.1 * m) * sc;
                Vec3 t1 = m1.add(fwd.scale(len)).add(dn.scale(0.15));
                Vec3 t2 = t1.add(fwd.scale(len * 0.75)).add(dn.scale(0.5));
                out.tube(m1, t1, w * 0.8F, w * 0.7F, BONE, 5);
                out.ball(t1, w * 0.72F, BONE);
                out.tube(t1, t2, w * 0.65F, w * 0.25F, BONE, 5);
            }
        }

        /**
         * (2026-10-06, the user: "improve his textures where you can") painted like a sugar skull all over, not only on its
         * face: a marigold medallion on its sternum with a pink heart, beads of colour on the ends of its ribs, marigold dots
         * down the spines of its vertebrae, painted bands round its arms above the elbow and at the wrist, a ring of dots on
         * each shoulder.
         */
        void paint(Solid out) {
            Vec3 f = F, r = fr.r;
            int[][] beads = {PINK, CYAN, GOLD};
            for (int i = 4; i + 1 < spine.length - 2; i += 2) {
                Vec3 a = turnPart(i, spine[i]), b = turnPart(i + 1, spine[i + 1]);
                Vec3 mid = a.lerp(b, 0.5);
                float rad = (float) Mth.lerp(i / (double) spine.length, 1.15, 0.6);
                Vec3 back = f.scale(-1).add(U.scale(-0.45)).normalize();
                out.ball(mid.add(back.scale(rad * 1.6)), rad * 0.3F, GOLD);
            }
            // the medallion: a marigold disc on the sternum, a pink heart in it
            Vec3 chest = manubrium.lerp(heart, 0.35).add(f.scale(0.9));
            out.ball(chest, 1.15F, GOLD);
            out.ball(chest.add(f.scale(0.75)).add(r.scale(0.32)).add(U.scale(0.2)), 0.42F, PINK);
            out.ball(chest.add(f.scale(0.75)).add(r.scale(-0.32)).add(U.scale(0.2)), 0.42F, PINK);
            out.ball(chest.add(f.scale(0.78)).add(U.scale(-0.25)), 0.38F, PINK);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                out.ball(chest.add(r.scale(Math.cos(a) * 1.45)).add(U.scale(Math.sin(a) * 1.45)).add(f.scale(0.35)), 0.26F, k % 2 == 0 ? PINK : CYAN);
            }
            // beads on the ribs' front ends (where the paint would go on a calavera)
            float br = 1 + 0.035F * fr.breath;
            for (int k = 0; k < 8; k++) {
                int vi = spine.length - 6 - k;
                if (vi < 2) break;
                Vec3 at = turnPart(vi, spine[vi]);
                double w = (2.6 + 4.6 * Math.sin(Math.PI * (k + 1.6) / 12.5)) * br;
                double depth = (2.4 + 2.8 * Math.sin(Math.PI * (k + 1.0) / 11)) * br;
                double drop = 2.6 + 0.25 * k;
                double th = Math.PI * 0.5;
                for (int s = -1; s <= 1; s += 2) {
                    double x = s * w * Math.sin(th);
                    double z = -0.9 * Math.sin(Math.min(th * 2.2, Math.PI)) * 0.6 + (depth + 0.6) * (1 - Math.cos(th)) / 2 - 0.4;
                    double y = -drop * (1 - Math.cos(th)) / 2;
                    Vec3 p = turn(at.add(r.scale(x)).add(f.scale(z)).add(U.scale(y)));
                    out.ball(p.add(r.scale(s * 0.38)), 0.3F, beads[(k + (s > 0 ? 1 : 0)) % 3]);
                }
            }
            // the arms: painted bands above the elbow and at the wrist, dots round the shoulder
            for (int i = 0; i < 2; i++) {
                int s = i == 0 ? 1 : -1;
                Vec3 sh = shoulder[i], h = hand[i];
                Vec3 el = elbow(sh, h, r.scale(s), f);
                Vec3 up1 = el.subtract(sh).normalize();
                Vec3 b0 = el.subtract(up1.scale(2.4));
                out.tube(b0, b0.add(up1.scale(0.45)), 0.88F, 0.86F, PINK, 8);
                out.tube(b0.add(up1.scale(0.7)), b0.add(up1.scale(1.0)), 0.86F, 0.85F, CYAN, 8);
                Vec3 fa = h.subtract(el).normalize();
                Vec3 w0 = h.subtract(fa.scale(1.6));
                out.tube(w0, w0.add(fa.scale(0.5)), 0.62F, 0.6F, GOLD, 7);
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2 * k / 6;
                    Vec3 o = f.scale(Math.cos(a) * 1.25).add(U.scale(Math.sin(a) * 1.25));
                    out.ball(sh.add(o).add(r.scale(s * 0.55)), 0.24F, k % 2 == 0 ? GOLD : PINK);
                }
            }
            if (!walker) return;
            // its legs: bands round the thigh over the knee, a gold one at the ankle, a pink dot on each kneecap
            for (int i = 0; i < 2; i++) {
                Vec3 th = knee[i].subtract(hip[i]).normalize(), sh = ankle[i].subtract(knee[i]).normalize();
                Vec3 b0 = knee[i].subtract(th.scale(3.2));
                out.tube(b0, b0.add(th.scale(0.5)), 0.86F, 0.85F, PINK, 8);
                out.tube(b0.add(th.scale(0.8)), b0.add(th.scale(1.15)), 0.84F, 0.83F, CYAN, 8);
                Vec3 w0 = ankle[i].subtract(sh.scale(2.0));
                out.tube(w0, w0.add(sh.scale(0.5)), 0.66F, 0.64F, GOLD, 7);
                out.ball(knee[i].add(kneeFront(i).scale(1.62)), 0.34F, PINK);
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
            double floor = fr.base.y - (walker ? 0 : fr.sunk) + 1.2;
            boolean low = wrist.y < floor + 2.5;
            Vec3 along = wrist.subtract(elbow).normalize();
            // flat on the floor the fingers lie along it, pointing out ahead of it
            Vec3 fwd = low ? new Vec3(along.x, 0, along.z).normalize().lerp(fr.f, 0.35).normalize() : along;
            Vec3 side = fwd.cross(up);
            side = side.lengthSqr() < 1e-4 ? fr.r : side.normalize();
            Vec3 palm = side.cross(fwd).normalize();        // the back of the hand
            boolean swipe = (fr.a == SpiritGiantEntity.SWIPE_L && i == 0) || (fr.a == SpiritGiantEntity.SWIPE_R && i == 1);
            boolean slam = (fr.a == SpiritGiantEntity.SLAM_L && i == 0) || (fr.a == SpiritGiantEntity.SLAM_R && i == 1) || fr.a == SpiritGiantEntity.SLAM_BOTH;
            boolean reach = fr.a == SpiritGiantEntity.GRAB && i == g.grabHand();
            boolean fist = reach && g.caught() && fr.act >= SpiritGiantEntity.GRAB_CLOSE - 1 && fr.act < SpiritGiantEntity.GRAB_SLAM + 2;
            boolean dig = fr.a == SpiritGiantEntity.RISE && fr.act < SpiritGiantEntity.DIG_HEAVE;
            float splay = slam || reach && !fist || dig ? 1F : swipe ? 0.95F : 0.45F + 0.15F * Mth.sin(fr.t * 0.1F + i);
            float curl = fist ? 1.25F : reach || dig ? 0.15F : low && !slam ? 0.55F + 0.1F * Mth.sin(fr.t * 0.07F + i) : (1 - splay) * 0.9F;
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
    private static void floorRing(VertexConsumer vc, Frame fr, SpiritGiantEntity g, float partial) {
        float dug = g.dug(0);
        boolean drag = fr.a == SpiritGiantEntity.SINK;
        float shown = drag ? 1F : Math.max(fr.risen, dug);
        if (drag && fr.act > SpiritGiantEntity.DRAG_SHUT + 4) return;
        if (shown <= 0.01F) return;
        Vec3 c = fr.base.subtract(fr.origin).add(0, 0.08, 0);
        Vec3 f = fr.f, r = fr.r;
        // digging: the cracks run out from where it will come up before anything of it shows, flickering with the dead's light
        float flick = fr.a == SpiritGiantEntity.RISE && fr.act < SpiritGiantEntity.DIG_HEAVE ? 0.75F + 0.25F * Mth.sin(fr.t * 1.3F) : 1F;
        int al = (int) (110 * shown * fr.glow * flick);
        // (2026-10-06) the rift's clock: the ring burns down round from its front as its time runs out, the dead's blue going violet,
        // then red and pulsing in its last fifth; the part burnt out only embers. Dragged under, it blazes violet and closes.
        float left = g.portalLeft(partial);
        float shrink = drag ? 1F - Mth.clamp((fr.act - SpiritGiantEntity.DRAG_UNDER) / (float) (SpiritGiantEntity.DRAG_SHUT - SpiritGiantEntity.DRAG_UNDER), 0F, 1F) : 1F;
        int[] lit = ringColour(drag ? 0.3F : left, fr.t);
        if (drag) al = (int) (200 * shrink);
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            // round from straight ahead of it, the way a clock's hand goes
            double u = (i + 0.5) / n;
            boolean on = drag || left < 0 || u <= left;
            double s0 = Math.PI / 2 - a0, s1 = Math.PI / 2 - a1;
            Vec3 p0 = c.add(r.scale(Math.cos(s0) * 7.5 * shrink)).add(f.scale(Math.sin(s0) * 5.5 * shrink));
            Vec3 p1 = c.add(r.scale(Math.cos(s1) * 7.5 * shrink)).add(f.scale(Math.sin(s1) * 5.5 * shrink));
            Vec3 q0 = c.add(r.scale(Math.cos(s0) * 10.5 * shrink)).add(f.scale(Math.sin(s0) * 8 * shrink));
            Vec3 q1 = c.add(r.scale(Math.cos(s1) * 10.5 * shrink)).add(f.scale(Math.sin(s1) * 8 * shrink));
            if (on) quad2c(vc, fr.m, p0, p1, q1, q0, lit, al, 0);
            else quad2c(vc, fr.m, p0, p1, q1, q0, EMBER, (int) (al * 0.22F), 0);
            // the burning edge: a bright flare where the clock has burnt down to
            if (left >= 0 && !drag && Math.abs(u - left) < 0.5 / n + 1e-4) {
                Vec3 e = p0.lerp(q0, 0.5);
                glow(vc, fr.m, e, 2.2F + 0.4F * Mth.sin(fr.t * 0.8F), lit[0], lit[1], lit[2], 230);
            }
        }
        // the cracks: jagged lines out over the floor
        long seed = g.getId() * 31L;
        int reach = Math.max(1, Math.round(4 + 3 * dug));
        for (int k = 0; k < 12; k++) {
            double ang = Math.PI * 2 * k / 12 + ((seed >> k) & 7) * 0.05;
            Vec3 dir = r.scale(Math.cos(ang)).add(f.scale(Math.sin(ang)));
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            Vec3 p = c.add(dir.scale(6.5 * Math.max(0.3, dug)));
            for (int s = 0; s < reach; s++) {
                Vec3 q = p.add(dir.scale(2.2 + ((k * 7 + s * 3) % 5) * 0.4)).add(side.scale((((k + s) % 3) - 1) * 0.9));
                Vec3 w = side.scale(Math.max(0.05, 0.2 - s * 0.022));
                quad2(vc, fr.m, p.subtract(w), q.subtract(w.scale(0.6)), q.add(w.scale(0.6)), p.add(w), (int) (al * 1.6 * Math.max(0, 1 - s * 0.14)),
                        (int) (al * 1.6 * Math.max(0, 1 - (s + 1) * 0.14)));
                p = q;
            }
        }
    }

    private static final int[] EMBER = {255, 110, 60};

    /** The rift's colour by how much of its time is left: the dead's blue, violet, then a red pulsing faster. */
    private static int[] ringColour(float left, float t) {
        if (left < 0 || left > 0.5F) return CYAN;
        if (left > 0.2F) {
            float k = (0.5F - left) / 0.3F;
            return new int[]{(int) Mth.lerp(k, CYAN[0], 170), (int) Mth.lerp(k, CYAN[1], 80), (int) Mth.lerp(k, CYAN[2], 255)};
        }
        float pulse = 0.5F + 0.5F * Mth.sin(t * (0.5F + (0.2F - left) * 6F));
        return new int[]{(int) Mth.lerp(pulse, 170, 255), (int) Mth.lerp(pulse, 80, 40), (int) Mth.lerp(pulse, 255, 70)};
    }

    /** {@link #quad2} in a colour of its own. */
    private static void quad2c(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int[] col, int a0, int a1) {
        int[][] order = {{0, 1, 2, 3}, {3, 2, 1, 0}};
        Vec3[] p = {p0, p1, p2, p3};
        int[] al = {Math.min(255, a0), Math.min(255, a1), Math.min(255, a1), Math.min(255, a0)};
        for (int[] o : order) {
            for (int i : o) vc.vertex(m, (float) p[i].x, (float) p[i].y, (float) p[i].z).color(col[0], col[1], col[2], al[i]).endVertex();
        }
    }

    /**
     * The dead of the underworld, reaching up out of the rift for it (2026-10-06, the user: "pulled back under... the underworld
     * spirits are stronger than even him... keep that theme the whole time he is summoned"): spectral arms of violet light up
     * from the ring round its waist, wavering, each ending in a hand of four clutching fingers round its spine, its shoulders,
     * its wrists. None while it is fresh; from half its time a few, tugging, more as the rift closes; dragging it under, a host.
     */
    private static final int[] GHOST = {64, 30, 104}, GHOST_HAND = {84, 44, 132};

    /** As bone ({@code solid}: a dark violet body, so they read in daylight too) or as light ({@code vc}: the glow round it). */
    private static void spirits(@org.jetbrains.annotations.Nullable VertexConsumer vc, @org.jetbrains.annotations.Nullable Solid solid, Frame fr,
                                SpiritGiantEntity g, Skeleton sk, float partial) {
        float pull = g.pull(partial);
        if (pull <= 0.01F) return;
        boolean drag = fr.a == SpiritGiantEntity.SINK;
        int n = Math.round(14 * pull);
        Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (int k = 0; k < n; k++) {
            // where it rises from on the ring, and what it takes hold of
            double a = Math.PI * 2 * ((k * 0.381966) % 1.0) + fr.t * 0.004 * (k % 2 == 0 ? 1 : -1);
            Vec3 root = fr.base.add(fr.r.scale(Math.cos(a) * 8.6)).add(fr.f.scale(Math.sin(a) * 6.4)).add(0, 0.1, 0);
            Vec3 grip = sk.walker ? switch (k % 5) {
                case 0 -> sk.ankle[0];
                case 1 -> sk.ankle[1];
                case 2 -> sk.knee[k / 5 % 2];
                case 3 -> sk.pelvis;
                default -> sk.hand[k / 5 % 2];
            } : switch (k % 5) {
                case 0 -> sk.hand[0];
                case 1 -> sk.hand[1];
                case 2 -> sk.shoulder[k / 5 % 2];
                case 3 -> sk.spine[Math.min(sk.spine.length - 4, 5 + (k * 3) % 6)];
                default -> sk.heart;
            };
            // reaching up: it grows out of the rift over a few ticks (dragging: at the seizure, each at its own moment)
            float reach = drag ? Mth.clamp((fr.act - (k % 4) * 2) / 7F, 0F, 1F) : 1F;
            if (reach <= 0) continue;
            Vec3 d = grip.subtract(root);
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-4 ? fr.r : side.normalize();
            int segs = 9;
            Vec3 prev = root;
            float wob = drag ? 0.9F : 0.5F;
            for (int s = 1; s <= segs; s++) {
                double u = s / (double) segs * reach;
                // bowed out and up, wavering like smoke
                Vec3 p = root.add(d.scale(u)).add(0, Math.sin(Math.PI * u) * 2.2, 0)
                        .add(side.scale(Math.sin(fr.t * 0.21 + k * 1.7 + u * 5) * wob * Math.sin(Math.PI * u)));
                float w = (float) (0.75 - 0.35 * u) * (drag ? 1.15F : 1F);
                int al = (int) ((drag ? 230 : 150) * Math.min(1, pull * 1.6) * (0.55 + 0.45 * u));
                if (solid != null) solid.tube(prev, p, w * 0.42F, w * 0.38F, GHOST, 6);
                if (vc != null) {
                    ribbon(vc, fr.m, prev.subtract(fr.origin), p.subtract(fr.origin), cam.subtract(fr.origin), w * 2.0F, 150, 70, 255, al);
                    ribbon(vc, fr.m, prev.subtract(fr.origin), p.subtract(fr.origin), cam.subtract(fr.origin), w * 0.6F, 220, 200, 255, al);
                }
                prev = p;
            }
            if (reach < 1) continue;
            // the hand: four fingers clutching round what it holds
            Vec3 fwd = d.normalize();
            Vec3 up2 = fwd.cross(side).normalize();
            for (int fgr = 0; fgr < 4; fgr++) {
                double fa = (fgr - 1.5) * 0.5;
                Vec3 base = grip.add(side.scale(Math.sin(fa) * 0.9)).subtract(fwd.scale(0.4));
                Vec3 tip = base.add(fwd.scale(1.3)).add(up2.scale(-0.9 - 0.2 * Mth.sin(fr.t * 0.3F + fgr))).add(side.scale(Math.sin(fa) * 0.6));
                if (solid != null) solid.tube(base, tip, 0.2F, 0.13F, GHOST_HAND, 5);
                if (vc != null) ribbon(vc, fr.m, base.subtract(fr.origin), tip.subtract(fr.origin), cam.subtract(fr.origin), 0.6F, 190, 140, 255, drag ? 230 : 150);
            }
        }
    }

    /** A soft band from a to b, {@code w} wide, turned to the camera: bright down its middle, fading to its edges. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int r, int g, int bl, int al) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(cam.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(w / 2);
        for (int half = -1; half <= 1; half += 2) {
            Vec3 s = side.scale(half);
            Vec3[] p = {a, b, b.add(s), a.add(s)};
            int[] alpha = {al, al, 0, 0};
            for (int[] o : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
                for (int i : o) vc.vertex(m, (float) p[i].x, (float) p[i].y, (float) p[i].z).color(r, g, bl, Math.min(255, alpha[i])).endVertex();
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
