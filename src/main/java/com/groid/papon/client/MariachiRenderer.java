package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BandRole;
import com.groid.papon.boss.BandState;
import com.groid.papon.boss.MariachiEntity;
import com.groid.papon.boss.Song;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The band: each member at his own build (BandRole: wider, taller), holding only his own
 * instrument (the rig has all of them; the rest are hidden per entity, every frame, because the
 * baked model is shared). The singer alone has the microphone and the aviators, and while he
 * sings a golden aura runs over him (the boss's energy swirl), with light bursting out of him at
 * the climax. Speech bubbles for the count and the grumbling.
 */
public class MariachiRenderer extends GeoEntityRenderer<MariachiEntity> {
    private static final float SCALE = 1.12F;
    private static final ResourceLocation AURA = PaponMod.id("textures/entity/papon_aura.png");
    private static final String[] INSTRUMENTS = {"guitar", "vihuela", "guitarron", "trumpet", "violin"};

    public MariachiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MariachiModel());
        this.shadowRadius = 0.6F;
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MariachiEntity m, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                               VertexConsumer buffer, float partialTick, int light, int overlay) {
                float k = aura(m, partialTick);
                if (k <= 0) return;
                float time = m.tickCount + partialTick;
                RenderType swirl = RenderType.energySwirl(AURA, (time * 0.011F) % 1F, (time * -0.017F) % 1F);
                for (float grow : new float[]{1.06F, 1.13F}) {
                    pose.pushPose();
                    pose.translate(0, 0.8, 0);
                    pose.scale(grow, grow, grow);
                    pose.translate(0, -0.8, 0);
                    float f = k * (grow > 1.1F ? 0.55F : 0.9F);
                    getRenderer().reRender(model, pose, buffers, m, swirl, buffers.getBuffer(swirl), partialTick,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, Math.min(1F, f), Math.min(1F, f * 0.8F), Math.min(1F, f * 0.35F), 1F);
                    pose.popPose();
                }
            }
        });
    }

    /** The singer's aura: on while the band plays, brighter the louder he sings. */
    static float aura(MariachiEntity m, float partialTick) {
        if (!m.getRole().singer()) return 0;
        BandState s = m.getBandState();
        if (s == BandState.COUNT) return Math.min(0.5F, m.ticksInState() / 60F);
        if (s != BandState.PLAY) return 0;
        Song song = Song.get();
        double t = SongPlayer.songTime(partialTick);
        float v = song == null || Double.isNaN(t) ? 0 : song.voiceAt(t);
        float pulse = 0.88F + 0.12F * net.minecraft.util.Mth.sin((m.tickCount + partialTick) * 0.4F);
        return (0.45F + 0.55F * v) * pulse;
    }

    @Override
    public void preRender(PoseStack pose, MariachiEntity m, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        BandRole role = m.getRole();
        this.scaleWidth = SCALE * role.width;
        this.scaleHeight = SCALE * role.height;
        for (String inst : INSTRUMENTS) {
            boolean mine = inst.equals(role.instrument);
            model.getBone(inst).ifPresent(bone -> bone.setHidden(!mine));
            model.getBone(inst + "_floor").ifPresent(bone -> bone.setHidden(!mine));
        }
        boolean violin = role.instrument.equals("violin");
        model.getBone("bow").ifPresent(bone -> bone.setHidden(!violin));
        model.getBone("mic_stand").ifPresent(bone -> bone.setHidden(!role.singer()));
        model.getBone("shades").ifPresent(bone -> bone.setHidden(!role.singer()));
        super.preRender(pose, m, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    public void render(MariachiEntity m, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(m, yaw, partialTick, pose, buffers, light);
        if (m.getRole().singer() && m.getBandState() == BandState.PLAY) {
            Song.Segment seg = SongPlayer.segment();
            if (seg != null && seg.id().startsWith("climax")) {
                float k = aura(m, partialTick) * 0.7F;
                BossRenderer.drawRays(pose, buffers, k, m.tickCount + partialTick, m.getBbHeight() * 0.6F);
            }
        }
        SpeechBubble.render(m, m.getSpeech(), 0.6F, pose, buffers, light, entityRenderDispatcher);
    }
}
