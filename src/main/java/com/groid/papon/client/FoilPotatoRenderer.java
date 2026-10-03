package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.FoilPotatoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The foil potatoes circling Chef Papon in phase two: 1.6x, turning as they go. */
public class FoilPotatoRenderer extends GeoEntityRenderer<FoilPotatoEntity> {
    public FoilPotatoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(PaponMod.id("foil_potato")));
        withScale(1.6F);
        this.shadowRadius = 0.4F;
    }

    @Override
    protected void applyRotations(FoilPotatoEntity entity, com.mojang.blaze3d.vertex.PoseStack pose, float ageInTicks, float yaw, float partialTick) {
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-(ageInTicks + partialTick) * 6F));
    }
}
