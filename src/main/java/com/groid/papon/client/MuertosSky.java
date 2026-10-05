package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.ColiseumCrowdEntity;
import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.boss.VoiceLines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Phase four's sky and light (Dia de los Muertos):
 *
 *   the moon     over his coliseum's band stand (PaponBossEntity.moonPos): the potato moon when the clouds part
 *                (moon 1), shaking and going blue under his hammer's lightning, then the dead's: a blue calavera with
 *                glowing eyes (moon 2), a cold halo round it. Drawn over the night's black sky (NightLight.coverSky)
 *   the souls    P4_SOULS: every fan's soul out of him and in a glowing arc to the boss as he turns to bone (the same
 *                order as ColiseumCrowdRenderer.threshold), then the band's, one by one (PaponBossEntity.soulAt)
 *   the arrow    P4_BOW: his arrow of the souls' light up into the moon
 *   the petals   P4_PETALOS: the marigold spirals (the same sums as the server: PaponBossEntity.petal)
 *   the flash    P4_HALFBAKED: the screen floods green-white as he turns, and clears on the dead's coliseum
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MuertosSky {
    private static final ResourceLocation POTATO_MOON = PaponMod.id("textures/environment/potato_moon_phases.png");
    private static final ResourceLocation DEAD_MOON = PaponMod.id("textures/environment/muertos_moon.png");
    private static final double RANGE = 180;

    private MuertosSky() {}

    @Nullable
    static PaponBossEntity boss() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        List<PaponBossEntity> l = mc.level.getEntitiesOfClass(PaponBossEntity.class, mc.player.getBoundingBox().inflate(RANGE), e -> true);
        return l.isEmpty() ? null : l.get(0);
    }

    // ------------------------------------------------------------------ the moon (called after the night's sky cover)

    /** How far the moon has turned (0 the potato moon .. 1 the calavera), and how hard it is shaking. */
    private static float turn(PaponBossEntity b, float partial) {
        boolean brethren = b.getBossState() == BossState.P4_BRETHREN;
        float t = b.ticksInState() + partial;
        float grant = VoiceLines.beat("p4.grant", 114), at = VoiceLines.beat("p4.turn", 194);
        if (b.moon() >= 2) return brethren ? Mth.clamp((t - at) / 10F + 0.6F, 0.6F, 1) : 1;
        // "...grant me your STRENGTH!": it shakes, going cold
        if (brethren) return Mth.clamp((t - grant) / (at - grant), 0, 1) * 0.6F;
        return 0;
    }

    /** Whether the moon is shaking (the line before it turns). */
    private static boolean shaking(PaponBossEntity b, float partial) {
        if (b.getBossState() != BossState.P4_BRETHREN) return false;
        float t = b.ticksInState() + partial;
        return t > VoiceLines.beat("p4.grant", 114) && t < VoiceLines.beat("p4.turn", 194) + 12;
    }

    /** Whether the moon shows (and how much: it fades in as the clouds part). */
    private static float shown(PaponBossEntity b, float partial) {
        if (b.moon() <= 0) return 0;
        if (b.getBossState() == BossState.P4_PROPHECY) {
            float t = b.ticksInState() + partial, at = VoiceLines.beat("p4.moon", 186);
            return Mth.clamp((t - at) / 30F, 0, 1);
        }
        return 1;
    }

    static void drawMoon(PoseStack pose, float partial) {
        PaponBossEntity b = boss();
        if (b == null) return;
        float k = shown(b, partial);
        if (k <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 d = b.moonPos().subtract(cam).normalize();
        float turned = turn(b, partial);
        float t = b.tickCount + partial;
        // shaking as the lightning goes into it
        if (shaking(b, partial)) {
            d = d.add((Math.sin(t * 3.1) * 0.004), Math.cos(t * 2.7) * 0.004, 0).normalize();
        }
        Vec3 right = d.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(d).normalize();
        Vec3 c = d.scale(90);
        float r = 15F;
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // the halo: cold blue round the calavera, a pale glow round the potato moon
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float hr = turned > 0.5F ? 0.25F : 0.9F, hg = turned > 0.5F ? 0.85F : 0.92F, hb = 1F;
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.08F);
        for (int ring = 0; ring < 2; ring++) {
            float rr = r * (ring == 0 ? 1.9F : 3.2F);
            float a = k * (ring == 0 ? 0.55F : 0.22F) * pulse * (0.6F + 0.4F * turned);
            for (int i = 0; i < 32; i++) {
                double a0 = Math.PI * 2 * i / 32, a1 = Math.PI * 2 * (i + 1) / 32;
                vtx(buf, m, c, right, up, 0, 0, hr, hg, hb, a);
                vtx(buf, m, c, right, up, (float) Math.cos(a0) * rr, (float) Math.sin(a0) * rr, hr, hg, hb, 0);
                vtx(buf, m, c, right, up, (float) Math.cos(a1) * rr, (float) Math.sin(a1) * rr, hr, hg, hb, 0);
            }
        }
        Tesselator.getInstance().end();
        // the disc: the potato moon, crossfading into the calavera (a white flash between)
        // the potato moon is drawn as the sky draws it, added on (its black is see-through); the calavera has alpha
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        drawDisc(m, c, right, up, r, POTATO_MOON, 0, 0, 0.25F, 0.5F, 1F, 1F - 0.5F * turned, 1F - 0.2F * turned, k * (1 - Math.max(0, turned - 0.6F) / 0.4F));
        RenderSystem.defaultBlendFunc();
        if (turned > 0.6F) drawDisc(m, c, right, up, r * 1.05F, DEAD_MOON, 0, 0, 1, 1, 1, 1, 1, k * (turned - 0.6F) / 0.4F);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawDisc(Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float r, ResourceLocation tex, float u0, float v0, float u1, float v1,
                                 float cr, float cg, float cb, float a) {
        if (a <= 0) return;
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[][] q = {{-r, -r, u0, v1}, {r, -r, u1, v1}, {r, r, u1, v0}, {-r, r, u0, v0}};
        for (float[] p : q) {
            Vec3 at = c.add(right.scale(p[0])).add(up.scale(p[1]));
            buf.vertex(m, (float) at.x, (float) at.y, (float) at.z).uv(p[2], p[3]).color(cr, cg, cb, a).endVertex();
        }
        Tesselator.getInstance().end();
    }

    private static void vtx(BufferBuilder buf, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float x, float y, float r, float g, float b, float a) {
        Vec3 p = c.add(right.scale(x)).add(up.scale(y));
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    // ------------------------------------------------------------------ in the world: the bolt, the rays, the petals

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        PaponBossEntity b = boss();
        if (b == null) return;
        BossState s = b.getBossState();
        if (s != BossState.P4_SOULS && s != BossState.P4_BOW && s != BossState.P4_PETALOS) return;
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
        if (s == BossState.P4_SOULS) souls(vc, m, b, t, partial, cam);
        if (s == BossState.P4_BOW) arrow(vc, m, b, t, partial, cam);
        if (s == BossState.P4_PETALOS) petals(vc, m, b, t, partial, cam);
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    /** How long a soul takes to fly to him, as a share of the stands' sweep (and in ticks for the band's). */
    private static final float SOUL_SPAN = 0.2F;
    private static final float BAND_FLIGHT = 22F;

    /**
     * The souls: out of each fan in turn (ColiseumCrowdRenderer.threshold: all over the bowl at once, not a sweep) and up in
     * an arc into his chest, a green-white wisp with a tail; then the band's, one by one, out of the stage.
     */
    private static void souls(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, float partial, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 into = b.getPosition(partial).add(0, b.getBbHeight() * 0.5, 0);
        AABB box = b.getBoundingBox().inflate(160);
        ColiseumCrowdEntity crowd = mc.level.getEntitiesOfClass(ColiseumCrowdEntity.class, box, e -> true).stream().findFirst().orElse(null);
        if (crowd != null) {
            float sweep = (t - PaponBossEntity.SOULS_STANDS) / (PaponBossEntity.SOULS_STANDS_END - PaponBossEntity.SOULS_STANDS);
            if (sweep > 0 && sweep < 1 + SOUL_SPAN) {
                Vec3 at = crowd.getPosition(partial);
                List<com.groid.papon.boss.Coliseum.Seat> seats = crowd.seatList();
                for (int i = 0; i < seats.size(); i++) {
                    com.groid.papon.boss.Coliseum.Seat seat = seats.get(i);
                    if (ColiseumCrowdRenderer.empty(seat.seed())) continue;
                    float k = (sweep - ColiseumCrowdRenderer.threshold(seat.seed())) / SOUL_SPAN;
                    if (k <= 0 || k >= 1) continue;
                    Vec3 from = at.add(seat.x(), seat.y() + 1.3, seat.z());
                    wisp(vc, m, from, into, k, cam, 0.55F, seat.seed());
                }
            }
        }
        List<MariachiEntity> band = new java.util.ArrayList<>(mc.level.getEntitiesOfClass(MariachiEntity.class, box, e -> true));
        band.sort(java.util.Comparator.comparingInt(e -> e.getRole().ordinal()));
        for (int i = 0; i < band.size(); i++) {
            float k = (t - PaponBossEntity.soulAt(i)) / BAND_FLIGHT;
            if (k <= 0 || k >= 1) continue;
            wisp(vc, m, band.get(i).getPosition(partial).add(0, 1.3, 0), into, k, cam, 1.1F, i * 31 + 7);
        }
    }

    /** One soul {@code k} of the way along its arc from {@code from} to {@code to}: a bright head, a fading tail. */
    private static void wisp(VertexConsumer vc, Matrix4f m, Vec3 from, Vec3 to, float k, Vec3 cam, float size, int seed) {
        double lift = 8 + (seed & 7);
        double sway = ((seed >> 3 & 15) - 7.5) * 0.6;
        Vec3 side = to.subtract(from).cross(new Vec3(0, 1, 0));
        final Vec3 off = side.lengthSqr() < 1e-6 ? Vec3.ZERO : side.normalize().scale(sway);
        java.util.function.DoubleFunction<Vec3> at = u -> {
            double e = u * u * (3 - 2 * u);
            return from.lerp(to, e).add(off.scale(Math.sin(u * Math.PI))).add(0, Math.sin(u * Math.PI) * lift, 0);
        };
        Vec3 prev = at.apply(Math.max(0, k - 0.12));
        for (int j = 1; j <= 4; j++) {
            double u = Math.max(0, k - 0.12 + 0.03 * j);
            Vec3 p = at.apply(u);
            int a = (int) (60 + 40 * j);
            ribbon(vc, m, prev, p, cam, size * (0.3F + 0.18F * j), 82, 255, 132, a);
            prev = p;
        }
        Vec3 head = at.apply(k);
        ribbon(vc, m, head.subtract(0, size * 0.5, 0), head.add(0, size * 0.5, 0), cam, size * 1.3F, 214, 255, 220, 230);
    }

    /** His arrow of the souls' light, from the bow up into the moon (P4_BOW: loosed, a streak, the moon struck). */
    private static void arrow(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, float partial, Vec3 cam) {
        float loose = PaponBossEntity.BOW_LOOSE, hit = PaponBossEntity.BOW_HIT;
        if (t < loose || t > hit + 8) return;
        Vec3 from = b.getPosition(partial).add(0, b.getBbHeight() * 0.75, 0);
        Vec3 to = b.moonPos();
        float k = Mth.clamp((t - loose) / (hit - loose), 0, 1);
        float fade = t > hit ? 1 - (t - hit) / 8F : 1;
        Vec3 headAt = from.lerp(to, k * k);
        Vec3 tailAt = from.lerp(to, Math.max(0, k * k - 0.35));
        ribbon(vc, m, tailAt, headAt, cam, 2.2F, 82, 255, 132, (int) (110 * fade));
        ribbon(vc, m, tailAt, headAt, cam, 0.7F, 230, 255, 236, (int) (240 * fade));
        // the trail it leaves, lingering
        ribbon(vc, m, from, tailAt, cam, 0.5F, 82, 255, 132, (int) (60 * fade));
    }

    /** The marigold spirals: a petal per arm per send, glowing orange, gold and pink as they turn. */
    private static void petals(VertexConsumer vc, Matrix4f m, PaponBossEntity b, float t, float partial, Vec3 cam) {
        Vec3 c = b.getPosition(partial);
        Vec3 base = new Vec3(c.x, c.y - 1.2 + 1.3, c.z);
        float a0 = b.moveAngle();
        int[][] cols = {{255, 150, 24}, {255, 208, 64}, {236, 46, 140}};
        for (int sent = PaponBossEntity.PETAL_FROM; sent <= Math.min(t, PaponBossEntity.PETAL_TO); sent += PaponBossEntity.PETAL_EVERY) {
            if (t - sent > PaponBossEntity.PETAL_LIFE || t < sent) continue;
            float fade = Math.min(1F, (PaponBossEntity.PETAL_LIFE - (t - sent)) / 10F);
            for (int arm = 0; arm < PaponBossEntity.PETAL_ARMS; arm++) {
                Vec3 p = base.add(PaponBossEntity.petal(a0, arm, sent, t));
                int[] col = cols[(arm + sent / 6) % cols.length];
                float spin = (t - sent) * 0.4F + arm;
                petal(vc, m, p, cam, 0.75F, spin, col[0], col[1], col[2], (int) (230 * fade));
            }
        }
    }

    private static void petal(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam, float s, float spin, int r, int g, int b, int a) {
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 right = toCam.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(toCam).normalize();
        float cs = Mth.cos(spin), sn = Mth.sin(spin);
        Vec3 ax = right.scale(cs).add(up.scale(sn)), ay = right.scale(-sn).add(up.scale(cs));
        // a teardrop: long along one axis, narrow across
        Vec3 tip = p.add(ay.scale(s)), tail = p.add(ay.scale(-s * 0.7)), l = p.add(ax.scale(-s * 0.4)), rr = p.add(ax.scale(s * 0.4));
        // both windings: the render type culls back faces, and which side faces the camera depends on the spin
        for (Vec3[] q : new Vec3[][]{{tip, l, tail, rr}, {tip, rr, tail, l}}) {
            for (Vec3 v : q) vc.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(r, g, b, v == tail ? a / 3 : a).endVertex();
        }
    }

    /** A flat strip from a to b turned to face the camera, {@code w} wide. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int r, int g, int bl, int al) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a.add(d.scale(0.5)))).normalize().scale(w / 2);
        if (Double.isNaN(side.x)) return;
        Vec3 p0 = a.subtract(side), p1 = a.add(side), p2 = b.add(side), p3 = b.subtract(side);
        vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, bl, al).endVertex();
        vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, bl, al).endVertex();
        vc.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, bl, al).endVertex();
        vc.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, bl, al).endVertex();
    }

    // ------------------------------------------------------------------ the blue-out

    /** How far the screen is flooded green-white as he turns (P4_HALFBAKED's flash), 0..1: it covers the arena turning. */
    static float blueout(float partial) {
        PaponBossEntity b = boss();
        if (b == null || b.getBossState() != BossState.P4_HALFBAKED) return 0;
        float t = b.ticksInState() + partial;
        float flash = VoiceLines.beat("p4.flash", 136), clear = VoiceLines.beat("p4.clear", 162);
        if (t < flash - 8) return 0;
        if (t < flash) return (t - flash + 8) / 8F;
        if (t < flash + 12) return 1;
        return Math.max(0, 1 - (t - flash - 12) / Math.max(1F, clear - flash - 12));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float a = blueout(event.getPartialTick());
        if (a <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, ((int) (a * 255) & 0xFF) << 24 | (a > 0.85F ? 0xE6FFEC : 0x52FF84));
    }
}
