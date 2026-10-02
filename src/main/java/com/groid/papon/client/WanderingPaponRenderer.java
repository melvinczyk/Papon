package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WanderingPaponRenderer extends GeoEntityRenderer<WanderingPaponEntity> {
    public WanderingPaponRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("wandering_papon")));
        this.shadowRadius = 0.6F;
    }
}
