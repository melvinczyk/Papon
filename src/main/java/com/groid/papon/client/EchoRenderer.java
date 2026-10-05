package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponEchoEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** His echoes: his own model (1.5x, as he is), a see-through gold afterimage, full bright. */
public class EchoRenderer extends GeoEntityRenderer<PaponEchoEntity> {
    /** La Danza de los Muertos: the dancers are his truest form, in blue light (tools/gen_muertos_papon.py). */
    private static final ResourceLocation DANCER_GEO = PaponMod.id("geo/entity/papon_muertos.geo.json");
    private static final ResourceLocation DANCER_TEX = PaponMod.id("textures/entity/papon_muertos.png");
    public EchoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("papon_boss")) {
            @Override
            public ResourceLocation getModelResource(PaponEchoEntity e) { return e.isDancer() ? DANCER_GEO : super.getModelResource(e); }

            @Override
            public ResourceLocation getTextureResource(PaponEchoEntity e) { return e.isDancer() ? DANCER_TEX : super.getTextureResource(e); }
        });
        withScale(1.5F);
        this.shadowRadius = 0F;
    }

    @Override
    public RenderType getRenderType(PaponEchoEntity echo, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(PaponEchoEntity echo, float partialTick, int light) {
        if (echo.isDancer()) {
            // the dead dancing: blue light, a pink flush as one spins through
            float t = echo.tickCount + partialTick;
            float k = Math.min(1F, t / 8F) * (0.85F + 0.15F * net.minecraft.util.Mth.sin(t * 1.3F));
            return echo.spinning() ? Color.ofRGBA(1.0F, 0.45F, 0.8F, 0.75F * k) : Color.ofRGBA(0.45F, 0.95F, 1.0F, 0.6F * k);
        }
        float a = echo.presence(partialTick);
        float flicker = 0.85F + 0.15F * net.minecraft.util.Mth.sin((echo.tickCount + partialTick) * 1.7F);
        return Color.ofRGBA(1.0F, 0.88F * flicker, 0.55F * flicker, 0.5F * a);
    }

    @Override
    public void render(PaponEchoEntity echo, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose, MultiBufferSource buffers,
                       int light) {
        super.render(echo, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }
}
