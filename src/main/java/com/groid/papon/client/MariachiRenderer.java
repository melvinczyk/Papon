package com.groid.papon.client;

import com.groid.papon.boss.BandRole;
import com.groid.papon.boss.MariachiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The band: each member at his own build (BandRole: wider, taller), holding only his own
 * instrument (the rig has all of them; the rest are hidden per entity, every frame, because the
 * baked model is shared): the singer's requinto, the coro's maraca and hand mic. The singer alone has
 * the microphone and the aviators; Gordo, the guitarron, alone has a chair (everyone else sleeps on
 * the floor). Speech bubbles for the count and the grumbling.
 */
public class MariachiRenderer extends GeoEntityRenderer<MariachiEntity> {
    private static final float SCALE = 1.12F;
    private static final String[] INSTRUMENTS = {"guitar", "vihuela", "guitarron", "trumpet", "violin", "requinto", "maraca", "hand_mic"};

    public MariachiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MariachiModel());
        this.shadowRadius = 0.6F;
    }

    @Override
    public void preRender(PoseStack pose, MariachiEntity m, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        BandRole role = m.getRole();
        this.scaleWidth = SCALE * role.width;
        this.scaleHeight = SCALE * role.height;
        for (String inst : INSTRUMENTS) {
            boolean mine = (inst.equals(role.instrument) || role.coro() && inst.equals("hand_mic"))
                    && !(m.robbed() && inst.equals(m.getRole().instrument));                    // phase three: it is in the boss's hands
            model.getBone(inst).ifPresent(bone -> bone.setHidden(!mine));
            model.getBone(inst + "_floor").ifPresent(bone -> bone.setHidden(!mine));
        }
        boolean violin = role.instrument.equals("violin");
        model.getBone("bow").ifPresent(bone -> bone.setHidden(!violin));
        model.getBone("mic_stand").ifPresent(bone -> bone.setHidden(!role.singer()));
        model.getBone("shades").ifPresent(bone -> bone.setHidden(!role.singer()));
        // only Gordo (the guitarron, the one who dozes off again) has a chair
        model.getBone("chair").ifPresent(bone -> bone.setHidden(!role.dozer()));
        super.preRender(pose, m, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    public void render(MariachiEntity m, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // phase three's dark: the spill of the boss's spotlight (and the stage lamps) keeps the band in view
        float night = NightLight.night(partialTick);
        if (night > 0.3F) light = net.minecraft.client.renderer.LightTexture.pack(Math.max(11, net.minecraft.client.renderer.LightTexture.block(light)),
                net.minecraft.client.renderer.LightTexture.sky(light));
        super.render(m, yaw, partialTick, pose, buffers, light);
        SpeechBubble.render(m, m.getSpeech(), 0.6F, pose, buffers, light, entityRenderDispatcher);
    }
}
