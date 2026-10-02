package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.npc.TacoNpc;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Model for one member of the Taco Truck crowd: geo and texture by name, animations from the
 * file that name shares with its rig (customer.animation.json for the humans, papon_npc for the
 * Papons). Humans also turn their "head" bone toward what they look at, added on top of the
 * animation so a reaction that tilts the head still works.
 */
public class CrowdModel<T extends TacoNpc> extends GeoModel<T> {
    private final ResourceLocation geo;
    private final ResourceLocation texture;
    private final ResourceLocation animation;
    private final boolean turnsHead;

    public CrowdModel(String name, String animationFile, boolean turnsHead) {
        this.geo = PaponMod.id("geo/entity/" + name + ".geo.json");
        this.texture = PaponMod.id("textures/entity/" + name + ".png");
        this.animation = PaponMod.id("animations/entity/" + animationFile + ".animation.json");
        this.turnsHead = turnsHead;
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animation;
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
        if (!turnsHead) return;
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -60, 60) * Mth.DEG_TO_RAD);
    }
}
