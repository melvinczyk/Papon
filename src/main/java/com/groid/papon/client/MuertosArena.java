package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.Fiesta;
import com.groid.papon.boss.PaponBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The Final Guardian's grand moves on the floor of his arena (the same sums as the server: boss/Fiesta), all light:
 *
 *   La Pista de los Muertos   every tile of the floor lit in the fiesta's colours, pulsing on the beat; a measure's tiles
 *                             flashing in its colour with a white rim, then columns of spirit fire up out of them
 *   El Camino de Cempasúchil  the ectoplasm flooding the floor, rippling; the path of marigolds blooming across it, its
 *                             start wilting brown
 *   El Castillo               the fireworks tower (a glowing bamboo lattice with its wheels and crown) and the sparks its
 *                             wheels spin out across the floor, each a little star with a tail
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MuertosArena {
    private MuertosArena() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        PaponBossEntity b = MuertosSky.boss();
        if (b == null) return;
        BossState s = b.getBossState();
        if (s != BossState.P4_PISTA && s != BossState.P4_CEMPASUCHIL && s != BossState.P4_CASTILLO) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float t = b.ticksInState() + partial;
        Vec3 c = b.fightCenter();
        double floor = b.fieldFloor();
        double a = b.fieldA(), bb = b.fieldB();
        switch (s) {
            case P4_PISTA -> pista(vc, m, b, t, c, floor, a, bb);
            case P4_CEMPASUCHIL -> camino(vc, m, b, t, c, floor, a, bb);
            case P4_CASTILLO -> castillo(vc, m, b, t, c, floor, a, bb, cam);
            default -> {}
        }
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    // ------------------------------------------------------------------ La Pista de los Muertos

    private static void pista(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, Vec3 c, double floor, double a, double bb) {
        int seed = b.p4Seed();
        float on = Mth.clamp((t - Fiesta.PISTA_LEAD + 12) / 12F, 0, 1);
        int last = Fiesta.eruptBeat(Fiesta.PISTA_MEASURES - 1);
        float end = Fiesta.pistaBeat(last) + 22;
        on *= Mth.clamp((end - t) / 10F, 0, 1);
        if (on <= 0) return;
        double beatK = (t - Fiesta.PISTA_LEAD) / Fiesta.BEAT;
        int beat = (int) Math.floor(beatK);
        float pulse = beatK < 0 ? 0 : (float) Math.exp(-(beatK - beat) * 4.0);
        // which measure is warning now, which erupting
        int warnM = -1, eruptM = -1;
        float eruptAge = 0;
        for (int mm = 0; mm < Fiesta.PISTA_MEASURES; mm++) {
            int w = Fiesta.pistaBeat(Fiesta.warnBeat(mm)), e = Fiesta.pistaBeat(Fiesta.eruptBeat(mm));
            if (t >= w && t < e) warnM = mm;
            if (t >= e && t < e + Fiesta.ERUPT_TICKS + 8) {
                eruptM = mm;
                eruptAge = t - e;
            }
        }
        double h = Fiesta.TILE * 0.47;
        int n = (int) Math.ceil(Math.max(a, bb) / Fiesta.TILE) + 1;
        double y = floor + 0.07;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                if (!Fiesta.tile(i, j, a, bb)) continue;
                double x = c.x + i * Fiesta.TILE, z = c.z + j * Fiesta.TILE;
                int base = Fiesta.colour(i * 3 + j * 5 + beat);
                int al = (int) ((34 + 46 * pulse) * on);
                boolean warn = warnM >= 0 && Fiesta.pistaLit(seed, warnM, i, j);
                boolean fire = eruptM >= 0 && Fiesta.pistaLit(seed, eruptM, i, j);
                int col = base;
                if (warn) {
                    col = Fiesta.pistaColour(seed, warnM);
                    float flick = 0.6F + 0.4F * Mth.sin(t * 1.6F);
                    al = (int) ((130 + 100 * flick) * on);
                    // a white rim round it
                    rim(vc, m, x, y + 0.01, z, h, 0xFFFFFF, (int) (200 * on));
                }
                if (fire) {
                    col = Fiesta.pistaColour(seed, eruptM);
                    al = (int) (255 * on);
                    float k = eruptAge < Fiesta.ERUPT_TICKS ? 1 : 1 - (eruptAge - Fiesta.ERUPT_TICKS) / 8F;
                    float rise = Math.min(1, eruptAge / 3F);
                    pillar(vc, m, x, floor, z, h * 0.9, 5.5 * rise, col, (int) (210 * k * on), t + i * 7 + j * 3);
                }
                floorQuad(vc, m, x, y, z, h, col, al);
            }
        }
    }

    // ------------------------------------------------------------------ El Camino de Cempasúchil

    private static void camino(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, Vec3 c, double floor, double a, double bb) {
        double[][] path = Fiesta.camino(b.p4Seed(), a, bb);
        float flood = Fiesta.flood(t);
        // the flood: the ectoplasm welling up and rippling across the floor
        if (flood > 0) {
            double cell = 3;
            double y = floor + 0.08 + 0.12 * flood;
            int nx = (int) Math.ceil(a / cell), nz = (int) Math.ceil(bb / cell);
            for (int i = -nx; i < nx; i++) {
                for (int j = -nz; j < nz; j++) {
                    double x0 = i * cell, z0 = j * cell;
                    if (!Fiesta.onSand(x0 + cell / 2, z0 + cell / 2, a, bb, 0.5)) continue;
                    float rip = 0.55F + 0.45F * Mth.sin((float) (x0 * 0.35 + z0 * 0.27) + t * 0.18F) * Mth.sin((float) (z0 * 0.31 - x0 * 0.12) - t * 0.13F);
                    int al = (int) (165 * flood * rip);
                    quadXZ(vc, m, c.x + x0, y, c.z + z0, c.x + x0 + cell, c.z + z0 + cell, 50, 255, 190, al);
                }
            }
        }
        // the path: petals bloomed so far; the start wilting
        double bloomed = Fiesta.bloomed(t, path.length), wilted = Fiesta.wilted(t);
        double w = Fiesta.CAMINO_WIDTH;
        double y = floor + 0.26;
        for (int i = 1; i < path.length && i <= bloomed; i++) {
            double[] p = path[i - 1], q = path[i];
            double dx = q[0] - p[0], dz = q[1] - p[1];
            double len = Math.max(1e-6, Math.hypot(dx, dz));
            double nx = -dz / len * w, nz = dx / len * w;
            boolean gold = ((i * 7919) & 3) == 0;
            int r = 255, g = gold ? 210 : 150, bl = gold ? 60 : 24;
            int al = 255;
            if (i < wilted) {
                float k = (float) Math.max(0, 1 - (wilted - i) / 24.0);
                r = 120;
                g = 70;
                bl = 30;
                al = (int) (150 * k);
            }
            float sway = 0.85F + 0.15F * Mth.sin(t * 0.3F + i * 0.7F);
            al = (int) (al * sway);
            if (al <= 0) continue;
            quad(vc, m, c.x + p[0] - nx, y, c.z + p[1] - nz, c.x + p[0] + nx, y, c.z + p[1] + nz,
                    c.x + q[0] + nx, y, c.z + q[1] + nz, c.x + q[0] - nx, y, c.z + q[1] - nz, r, g, bl, al);
            // a brighter heart down the middle, and petals scattered over it
            quad(vc, m, c.x + p[0] - nx * 0.3, y + 0.01, c.z + p[1] - nz * 0.3, c.x + p[0] + nx * 0.3, y + 0.01, c.z + p[1] + nz * 0.3,
                    c.x + q[0] + nx * 0.3, y + 0.01, c.z + q[1] + nz * 0.3, c.x + q[0] - nx * 0.3, y + 0.01, c.z + q[1] - nz * 0.3,
                    255, 230, 120, al / 2);
            if (i % 2 == 0 && i >= wilted) {
                double off = (((i * 2654435761L) >>> 8) % 1000) / 1000.0 * 2 - 1;
                double px = c.x + p[0] + nx * off * 0.8, pz = c.z + p[1] + nz * off * 0.8;
                float bob = 0.25F + 0.2F * Mth.sin(t * 0.25F + i);
                floorQuad(vc, m, px, y + 0.04 + bob * 0.2, pz, 0.35, 0xFFD63C, al);
            }
        }
    }

    // ------------------------------------------------------------------ El Castillo

    private static void castillo(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, Vec3 c, double floor, double a, double bb, Vec3 cam) {
        int seed = b.p4Seed();
        float burstK = t < Fiesta.BURST ? 1 : Math.max(0, 1 - (t - Fiesta.BURST) / 6F);
        if (burstK > 0) {
            double top = Fiesta.tierHeight(Fiesta.CASTILLO_TIERS - 1) + 4.5;
            double rise = Math.min(1, t / 26.0);
            double h = top * rise;
            double half = 1.3;
            int ba = (int) (130 * burstK);
            // the lattice: four poles, cross-braced
            for (int k = 0; k < 4; k++) {
                double x = c.x + ((k & 1) == 0 ? -half : half), z = c.z + ((k & 2) == 0 ? -half : half);
                bar(vc, m, new Vec3(x, floor, z), new Vec3(x, floor + h, z), 0.28, 230, 170, 90, ba, cam);
            }
            for (double y = 1.6; y < h; y += 1.8) {
                for (int k = 0; k < 4; k++) {
                    double[][] e = {{-half, -half, half, -half}, {half, -half, half, half}, {half, half, -half, half}, {-half, half, -half, -half}};
                    bar(vc, m, new Vec3(c.x + e[k][0], floor + y, c.z + e[k][1]), new Vec3(c.x + e[k][2], floor + y + 0.9, c.z + e[k][3]), 0.16,
                            230, 170, 90, ba / 2, cam);
                }
            }
            // the wheels: dim until lit, then spinning and sparkling
            for (int tier = 0; tier < Fiesta.CASTILLO_TIERS; tier++) {
                double y = floor + Fiesta.tierHeight(tier) * rise;
                if (y > floor + h) continue;
                int s0 = Fiesta.tierStart(tier);
                boolean lit = t >= s0 && t < s0 + Fiesta.TIER_RUN + 10;
                int col = Fiesta.colour(tier * 2 + 1);
                int arms = Fiesta.arms(tier);
                double r = 2.4 + tier * 0.3;
                int spin = (tier & 1) == 0 ? 1 : -1;
                double ang0 = lit ? spin * (t - s0) * 0.25 : 0;
                for (int k = 0; k < arms; k++) {
                    double an = ang0 + k * Math.PI * 2 / arms;
                    Vec3 hub = new Vec3(c.x, y, c.z);
                    Vec3 tip = hub.add(Math.cos(an) * r, 0, Math.sin(an) * r);
                    bar(vc, m, hub, tip, 0.18, lit ? 255 : 160, lit ? (col >> 8 & 255) : 120, lit ? (col & 255) : 80, lit ? (int) (220 * burstK) : ba / 2, cam);
                    if (lit) star(vc, m, tip, cam, 0.7F, col, (int) (240 * burstK));
                }
                if (lit) star(vc, m, new Vec3(c.x, y, c.z), cam, 1.4F + 0.3F * Mth.sin(t), 0xFFF4C0, (int) (200 * burstK));
            }
            // the crown
            if (t >= Fiesta.CROWN - 20) {
                double y = floor + top;
                float k = Mth.clamp((t - Fiesta.CROWN + 20) / 20F, 0, 1);
                for (int i = 0; i < 8; i++) {
                    double an = t * 0.3 + i * Math.PI / 4;
                    Vec3 tip = new Vec3(c.x + Math.cos(an) * 2.2, y + 0.8 + (i % 2) * 0.8, c.z + Math.sin(an) * 2.2);
                    star(vc, m, tip, cam, 0.8F * k, Fiesta.colour(i), (int) (230 * burstK * k));
                }
            }
        }
        // the sparks
        if (t >= Fiesta.TIER_START) {
            List<double[]> sp = Fiesta.sparks(seed, t, a, bb);
            double y = floor + Fiesta.SPARK_Y;
            for (double[] s : sp) {
                int tier = (int) s[2];
                int col = Fiesta.colour(tier * 2 + 1);
                Vec3 at = new Vec3(c.x + s[0], y, c.z + s[1]);
                Vec3 from = at.subtract(c.x, y, c.z).multiply(1, 0, 1);
                Vec3 tail = from.lengthSqr() < 1e-6 ? at : at.subtract(from.normalize().scale(1.4));
                ribbon(vc, m, tail, at, cam, 0.35F, col, 140);
                star(vc, m, at, cam, 0.55F, col, 255);
                star(vc, m, at, cam, 0.22F, 0xFFFFFF, 255);
            }
        }
    }

    // ------------------------------------------------------------------ drawing helpers (additive)

    private static void floorQuad(VertexConsumer vc, Matrix4f m, double x, double y, double z, double h, int col, int al) {
        quadXZ(vc, m, x - h, y, z - h, x + h, z + h, col >> 16 & 255, col >> 8 & 255, col & 255, al);
    }

    private static void quadXZ(VertexConsumer vc, Matrix4f m, double x0, double y, double z0, double x1, double z1, int r, int g, int b, int al) {
        if (al <= 0) return;
        quad(vc, m, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, r, g, b, al);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2,
                             double z2, double x3, double y3, double z3, int r, int g, int b, int al) {
        // both windings: the render type culls back faces
        vc.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x3, (float) y3, (float) z3).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x3, (float) y3, (float) z3).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, al).endVertex();
        vc.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, b, al).endVertex();
    }

    private static void rim(VertexConsumer vc, Matrix4f m, double x, double y, double z, double h, int col, int al) {
        double w = 0.22;
        int r = col >> 16 & 255, g = col >> 8 & 255, b = col & 255;
        quadXZ(vc, m, x - h, y, z - h, x + h, z - h + w, r, g, b, al);
        quadXZ(vc, m, x - h, y, z + h - w, x + h, z + h, r, g, b, al);
        quadXZ(vc, m, x - h, y, z - h, x - h + w, z + h, r, g, b, al);
        quadXZ(vc, m, x + h - w, y, z - h, x + h, z + h, r, g, b, al);
    }

    /** A column of spirit fire over a tile: crossed sheets, bright at the foot, fading up, flickering. */
    private static void pillar(VertexConsumer vc, Matrix4f m, double x, double floor, double z, double h, double height, int col, int al, float t) {
        int r = col >> 16 & 255, g = col >> 8 & 255, b = col & 255;
        for (int k = 0; k < 3; k++) {
            double an = k * Math.PI / 3 + t * 0.05;
            double dx = Math.cos(an) * h, dz = Math.sin(an) * h;
            double top = floor + height * (0.85 + 0.15 * Math.sin(t * 0.9 + k));
            for (int side = 0; side < 2; side++) {
                double ax = side == 0 ? x - dx : x + dx, az = side == 0 ? z - dz : z + dz;
                double bx = side == 0 ? x + dx : x - dx, bz = side == 0 ? z + dz : z - dz;
                vc.vertex(m, (float) ax, (float) floor, (float) az).color(r, g, b, al).endVertex();
                vc.vertex(m, (float) bx, (float) floor, (float) bz).color(r, g, b, al).endVertex();
                vc.vertex(m, (float) (x + (bx - x) * 0.3), (float) top, (float) (z + (bz - z) * 0.3)).color(255, 255, 230, 0).endVertex();
                vc.vertex(m, (float) (x + (ax - x) * 0.3), (float) top, (float) (z + (az - z) * 0.3)).color(255, 255, 230, 0).endVertex();
            }
        }
    }

    /** A soft star facing the camera: two crossed strips. */
    private static void star(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam, float s, int col, int al) {
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 right = toCam.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(toCam).normalize();
        ribbon(vc, m, p.subtract(up.scale(s)), p.add(up.scale(s)), cam, s * 0.9F, col, al);
        ribbon(vc, m, p.subtract(right.scale(s)), p.add(right.scale(s)), cam, s * 0.9F, col, al);
    }

    /** A thick bar from a to b (a strip turned to the camera). */
    private static void bar(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double w, int r, int g, int bl, int al, Vec3 cam) {
        ribbon(vc, m, a, b, cam, (float) w, (r << 16) | (g << 8) | bl, al);
    }

    /** A flat strip from a to b turned to face the camera, {@code w} wide, both windings. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int col, int al) {
        if (al <= 0) return;
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a.add(d.scale(0.5))));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(w / 2);
        Vec3 p0 = a.subtract(side), p1 = a.add(side), p2 = b.add(side), p3 = b.subtract(side);
        quad(vc, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, col >> 16 & 255, col >> 8 & 255, col & 255, al);
    }
}
