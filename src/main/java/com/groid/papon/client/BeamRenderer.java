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
        if (e.anchor() >= 0 && e.level().getEntity(e.anchor()) instanceof com.groid.papon.boss.PaponBossEntity boss) {
            // out of his eye as it is drawn this frame (the server only knows roughly where his eyes are)
            Vec3 eye = BossRenderer.eyePos(boss, e.side());
            if (eye != null) from = eye.subtract(base);
        }
        Vec3 to = e.prevTo.lerp(e.drawTo, partial).subtract(base);
        if (from.distanceToSqr(to) < 1e-4) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
        int c = e.color();
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        float time = e.tickCount + partial;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float w = e.width();
        if (e.mode() == BeamEntity.AIM) {
            float flick = 0.55F + 0.45F * Mth.sin(time * 1.9F);
            ribbon(vc, m, from, to, cam, 0.05F + w * 0.06F, r, g, b, (int) (170 * flick), 0);
            return;
        }
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
