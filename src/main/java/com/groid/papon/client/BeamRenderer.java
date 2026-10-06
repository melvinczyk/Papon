package com.groid.papon.client;

import com.groid.papon.boss.BeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A {@link BeamEntity}: a shaft of light between its two ends, additive (it glows in the dark of phase three). A
 * white-hot core inside a coloured sheath and a soft halo, each a ribbon turned to face the camera; it shimmers
 * along its length; where it lands, a burning splash. Aiming, it is a thin flickering line of the same colour.
 */
public class BeamRenderer extends EntityRenderer<BeamEntity> {
    public BeamRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public boolean shouldRender(BeamEntity e, Frustum frustum, double x, double y, double z) {
        return true;              // its box is at one end; the beam can cross the whole view
    }

    @Override
    public void render(BeamEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 base = e.getPosition(partial);
        Vec3 from = e.prevFrom.lerp(e.drawFrom, partial).subtract(base);
        boolean hisEye = false;
        if (e.anchor() >= 0 && e.level().getEntity(e.anchor()) instanceof com.groid.papon.boss.PaponBossEntity boss) {
            // out of his eye as it is drawn this frame (the server only knows roughly where his eyes are)
            Vec3 eye = BossRenderer.eyePos(boss, e.side());
            if (eye != null) from = eye.subtract(base);
            hisEye = true;
        } else if (e.anchor() >= 0 && e.level().getEntity(e.anchor()) instanceof com.groid.papon.boss.SpiritGiantEntity g) {
            // out of El Gigante's eye socket, its skull turned as it is drawn this frame
            from = g.eye(e.side(), partial).subtract(base);
        }
        Vec3 to = e.prevTo.lerp(e.drawTo, partial).subtract(base);
        if (from.distanceToSqr(to) < 1e-4) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
        int c = e.color();
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        float time = e.tickCount + partial;
        VertexConsumer vc = buffers.getBuffer(PaponRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        float w = e.width();
        if (e.mode() == BeamEntity.AIM) {
            float flick = 0.75F + 0.25F * Mth.sin(time * 1.2F);         // a pulse, not a flicker
            ribbon(vc, m, from, to, cam, 0.05F + w * 0.06F, r, g, b, (int) (170 * flick), 0);
            if (hisEye) disc(vc, m, from, cam, w * (0.5F + 0.25F * flick), 255, 255, 255, 210, r, g, b);    // his eye, charging
            return;
        }
        if (hisEye) eyeBeam(vc, m, from, to, cam, w, r, g, b, time);
        float pulse = 1F + 0.12F * Mth.sin(time * 1.3F) + 0.06F * Mth.sin(time * 3.7F);
        ribbon(vc, m, from, to, cam, w * 1.9F * pulse, r, g, b, 40, 30);
        ribbon(vc, m, from, to, cam, w * 1.0F * pulse, r, g, b, 120, 90);
        ribbon(vc, m, from, to, cam, w * 0.42F, 255, 255, 255, 230, 200);
        // the splash where it lands: a flat glow on the ground, flaring
        Vec3 n = to.subtract(from).normalize();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = n.cross(u).normalize(), bb = n.cross(a).normalize();
        float rad = w * (2.4F + 0.4F * Mth.sin(time * 2.1F));
        int seg = 18;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 p0 = to.add(a.scale(Math.cos(a0) * rad)).add(bb.scale(Math.sin(a0) * rad));
            Vec3 p1 = to.add(a.scale(Math.cos(a1) * rad)).add(bb.scale(Math.sin(a1) * rad));
            quad(vc, m, to, p0, p1, to, new int[]{255, 255, 255, 200}, new int[]{r, g, b, 0}, new int[]{r, g, b, 0}, new int[]{255, 255, 255, 200});
        }
    }

    /**
     * What makes his eye beams weigh something (2026-10-06, the user: "make it look much more substantial"): a blazing flare
     * at his eye, rings of force racing from it down the beam to where it lands, a looser outer sheath crackling round the
     * core, and a column of fire standing up where it hits.
     */
    private static void eyeBeam(VertexConsumer vc, Matrix4f m, Vec3 from, Vec3 to, Vec3 cam, float w, int r, int g, int b, float time) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 n = d.scale(1 / len);
        float pulse = 1F + 0.18F * Mth.sin(time * 2.3F);
        disc(vc, m, from, cam, w * 2.2F * pulse, 255, 255, 255, 255, r, g, b);
        disc(vc, m, from, cam, w * 4.2F * pulse, r, g, b, 120, r, g, b);
        // the outer sheath, wavering
        ribbon(vc, m, from, to, cam, w * (3.0F + 0.5F * Mth.sin(time * 5.1F)), r, g, b, 26, 18);
        // rings racing down it
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = n.cross(u).normalize(), bb = n.cross(a).normalize();
        int rings = Math.max(3, (int) (len / 3.5));
        for (int i = 0; i < rings; i++) {
            float k = ((time * 0.09F + i / (float) rings) % 1F);
            Vec3 c = from.add(d.scale(k));
            float rad = w * (0.9F + 0.7F * k);
            int al = (int) (150 * Math.sin(k * Math.PI));
            ring(vc, m, c, a, bb, rad, rad * 0.55F, r, g, b, al);
        }
        // the column where it lands
        Vec3 up = new Vec3(0, w * (5F + 1.5F * Mth.sin(time * 3.3F)), 0);
        ribbon(vc, m, to, to.add(up), cam, w * 2.4F, 255, 230, 200, 170, 0);
        ribbon(vc, m, to, to.add(up.scale(1.6)), cam, w * 4.5F, r, g, b, 90, 0);
    }

    /** A camera-facing glow disc: {@code inner} colour at the middle fading to nothing at {@code rad}. */
    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, float rad, int r0, int g0, int b0, int a0, int r1, int g1, int b1) {
        Vec3 view = cam.subtract(at).normalize();
        Vec3 u = Math.abs(view.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = view.cross(u).normalize(), b = view.cross(a).normalize();
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 p0 = at.add(a.scale(Math.cos(t0) * rad)).add(b.scale(Math.sin(t0) * rad));
            Vec3 p1 = at.add(a.scale(Math.cos(t1) * rad)).add(b.scale(Math.sin(t1) * rad));
            quad(vc, m, at, p0, p1, at, new int[]{r0, g0, b0, a0}, new int[]{r1, g1, b1, 0}, new int[]{r1, g1, b1, 0}, new int[]{r0, g0, b0, a0});
        }
    }

    /** A flat ring round {@code c} square to the beam: outer radius {@code ro}, inner {@code ri}, brightest at its middle. */
    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 a, Vec3 b, float ro, float ri, int r, int g, int bl, int al) {
        if (al <= 2) return;
        float rm = (ro + ri) / 2;
        int seg = 18;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 d0 = a.scale(Math.cos(t0)).add(b.scale(Math.sin(t0))), d1 = a.scale(Math.cos(t1)).add(b.scale(Math.sin(t1)));
            int[] mid = {255, 255, 255, al}, edge = {r, g, bl, 0};
            quad(vc, m, c.add(d0.scale(rm)), c.add(d1.scale(rm)), c.add(d1.scale(ro)), c.add(d0.scale(ro)), mid, mid, edge, edge);
            quad(vc, m, c.add(d0.scale(rm)), c.add(d1.scale(rm)), c.add(d1.scale(ri)), c.add(d0.scale(ri)), mid, mid, edge, edge);
        }
    }

    /** A flat band from {@code a} to {@code b}, {@code w} wide, turned toward the camera; alpha {@code aa} at a, {@code ab} at b. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int r, int g, int bl, int aa, int ab) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1e-8) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-8) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(w / 2);
        // two halves, each fading from the bright middle line to nothing at the edge: a soft-edged beam
        for (int half = -1; half <= 1; half += 2) {
            Vec3 s = side.scale(half);
            quad(vc, m, a, b, b.add(s), a.add(s), new int[]{r, g, bl, aa}, new int[]{r, g, bl, ab}, new int[]{r, g, bl, 0}, new int[]{r, g, bl, 0});
        }
    }

    /** One quad, both windings (the lightning render type culls back faces). */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int[] c0, int[] c1, int[] c2, int[] c3) {
        vertex(vc, m, p0, c0[0], c0[1], c0[2], c0[3]);
        vertex(vc, m, p1, c1[0], c1[1], c1[2], c1[3]);
        vertex(vc, m, p2, c2[0], c2[1], c2[2], c2[3]);
        vertex(vc, m, p3, c3[0], c3[1], c3[2], c3[3]);
        vertex(vc, m, p3, c3[0], c3[1], c3[2], c3[3]);
        vertex(vc, m, p2, c2[0], c2[1], c2[2], c2[3]);
        vertex(vc, m, p1, c1[0], c1[1], c1[2], c1[3]);
        vertex(vc, m, p0, c0[0], c0[1], c0[2], c0[3]);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(BeamEntity e) { return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; }
}
