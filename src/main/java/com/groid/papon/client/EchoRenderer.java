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
    public EchoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("papon_boss")));
        withScale(1.5F);
        this.shadowRadius = 0F;
    }

    @Override
    public RenderType getRenderType(PaponEchoEntity echo, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(PaponEchoEntity echo, float partialTick, int light) {
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
