package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.DieGlockeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Die Glocke (entity/DieGlockeEntity, model tools/gen_glocke.py). The drums are turned here from the entity's eased spin
 * (lower one way, upper the other), the bell trembles as it comes up to speed, and the Xerum slits and lamps
 * (die_glocke_glowmask.png) brighten with the spin from a dull violet (the glowmask takes those texels out of the normal
 * pass, so the glow layer always draws them, only dimmer while it stands still).
 */
public class DieGlockeRenderer extends GeoEntityRenderer<DieGlockeEntity> {
    public DieGlockeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new Glow(this));
        this.shadowRadius = 0.95F;
    }

    private static final class Model extends DefaultedEntityGeoModel<DieGlockeEntity> {
        Model() { super(PaponMod.id("die_glocke")); }

        @Override
        public void setCustomAnimations(DieGlockeEntity glocke, long instanceId, AnimationState<DieGlockeEntity> state) {
            float pt = state.getPartialTick();
            float a = (float) Math.toRadians(Mth.lerp(pt, glocke.prevAngle, glocke.angle));
            float spin = Mth.lerp(pt, glocke.prevSpin, glocke.spin);
            CoreGeoBone low = getAnimationProcessor().getBone("drum_low"), high = getAnimationProcessor().getBone("drum_high");
            CoreGeoBone bell = getAnimationProcessor().getBone("glocke");
            if (low != null) low.setRotY(a);
            if (high != null) high.setRotY(-a * 1.15F);
            if (bell != null) {
                // a buzz that grows with the speed, strongest on the way up
                float t = glocke.tickCount + pt, k = spin * spin * (1.4F - spin * 0.6F);
                bell.setPosX(k * 0.22F * Mth.sin(t * 2.9F));
                bell.setPosZ(k * 0.22F * Mth.sin(t * 3.7F + 1.3F));
                bell.setPosY(k * 0.12F * Math.abs(Mth.sin(t * 5.1F)));
                bell.setRotZ(k * 0.004F * Mth.sin(t * 2.3F));
            }
        }
    }

    private static final class Glow extends AutoGlowingGeoLayer<DieGlockeEntity> {
        Glow(DieGlockeRenderer renderer) { super(renderer); }

        @Override
        public void render(PoseStack pose, DieGlockeEntity glocke, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                           VertexConsumer buffer, float partial, int light, int overlay) {
            float spin = Mth.lerp(partial, glocke.prevSpin, glocke.spin), t = glocke.tickCount + partial;
            float k = 0.14F + 0.86F * spin * (0.9F + 0.1F * Mth.sin(t * 1.7F));
            RenderType glow = getRenderType(glocke);
            getRenderer().reRender(model, pose, buffers, glocke, glow, buffers.getBuffer(glow), partial, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, k, k, k, 1F);
        }
    }

    /** Started from ClientHooks.glockeTick while the drums turn: the beacon's hum, pitched and swelled with the spin. */
    static void tick(DieGlockeEntity glocke) {
        if (glocke.hum instanceof Hum h && !h.isStopped()) return;
        Hum h = new Hum(glocke);
        glocke.hum = h;
        Minecraft.getInstance().getSoundManager().play(h);
    }

    private static final class Hum extends AbstractTickableSoundInstance {
        private final DieGlockeEntity glocke;

        Hum(DieGlockeEntity glocke) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, RandomSource.create());
            this.glocke = glocke;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            place();
        }

        private void place() {
            x = glocke.getX();
            y = glocke.getY() + 1.2;
            z = glocke.getZ();
            volume = Math.max(0.01F, glocke.spin * 1.4F);
            pitch = 0.45F + 0.8F * glocke.spin;
        }

        @Override
        public void tick() {
            if (glocke.isRemoved() || (glocke.spin < 0.01F && !glocke.active())) { stop(); return; }
            place();
        }
    }
}
