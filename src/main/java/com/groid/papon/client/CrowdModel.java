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
        this(name, name, animationFile, turnsHead);
    }

    /** Geo and texture by different names: a mesh-bodied rig borrows its props' texture. */
    public CrowdModel(String geoName, String textureName, String animationFile, boolean turnsHead) {
        this.geo = PaponMod.id("geo/entity/" + geoName + ".geo.json");
        this.texture = PaponMod.id("textures/entity/" + textureName + ".png");
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
        face(animatable, state.getPartialTick());
        if (!turnsHead) return;
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -60, 60) * Mth.DEG_TO_RAD);
    }

    /**
     * Mesh faces (rigs with "jaw" and "lid_r"/"lid_l" bones): the jaw flaps while a line is spoken,
     * on top of whatever the animation does with it, and the eyes blink every few seconds, at a
     * rhythm of their own per entity so a crowd never blinks in unison.
     */
    private void face(T animatable, float partialTick) {
        CoreGeoBone jaw = getAnimationProcessor().getBone("jaw");
        if (jaw == null) return;
        float t = animatable.tickCount + partialTick;
        if (animatable.isBabbling()) {
            // two incommensurate waves: syllables that never repeat exactly
            float open = Math.max(0F, Mth.sin(t * 1.9F) * 0.6F + Mth.sin(t * 0.73F + 1.3F) * 0.4F);
            jaw.setRotX(jaw.getRotX() + open * 14F * Mth.DEG_TO_RAD);
        }
        int seed = animatable.getId() * 7919;
        int period = 70 + Math.floorMod(seed, 50);
        float phase = (t + Math.floorMod(seed, period)) % period;
        float blink = phase < 2F ? phase / 2F : phase < 4F ? (4F - phase) / 2F : 0F;
        if (blink <= 0F) return;
        for (String name : new String[]{"lid_r", "lid_l"}) {
            CoreGeoBone lid = getAnimationProcessor().getBone(name);
            if (lid != null) lid.setRotX(lid.getRotX() + blink * 32F * Mth.DEG_TO_RAD);
        }
    }
}
