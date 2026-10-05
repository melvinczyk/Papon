package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.SalsaDancerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The muchachas (tools/gen_salsa.py): skeleton Papons in salsa dresses, a dress of her own each (salsa_dancer_&lt;look&gt;.png),
 * half again a Papon's size so the Final Guardian can dance with one; their eyes glow (the glowmask).
 */
public class SalsaDancerRenderer extends GeoEntityRenderer<SalsaDancerEntity> {
    private static final ResourceLocation[] LOOKS = {PaponMod.id("textures/entity/salsa_dancer_0.png"), PaponMod.id("textures/entity/salsa_dancer_1.png"),
            PaponMod.id("textures/entity/salsa_dancer_2.png"), PaponMod.id("textures/entity/salsa_dancer_3.png")};

    public SalsaDancerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<SalsaDancerEntity>(PaponMod.id("salsa_dancer")) {
            @Override
            public ResourceLocation getTextureResource(SalsaDancerEntity e) { return LOOKS[e.look()]; }
        });
        withScale(1.6F);
        shadowRadius = 0.7F;
    }

    @Override
    public ResourceLocation getTextureLocation(SalsaDancerEntity e) { return LOOKS[e.look()]; }

    @Override
    public void render(SalsaDancerEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // the night of phase four: the stage lamps' spill keeps them in view
        float night = NightLight.night(partialTick);
        if (night > 0.3F) light = net.minecraft.client.renderer.LightTexture.pack(Math.max(10, net.minecraft.client.renderer.LightTexture.block(light)),
                net.minecraft.client.renderer.LightTexture.sky(light));
        super.render(e, yaw, partialTick, pose, buffers, light);
    }
}
