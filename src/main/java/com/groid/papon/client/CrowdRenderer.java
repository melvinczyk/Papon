package com.groid.papon.client;

import com.groid.papon.entity.npc.TacoCustomerEntity;
import com.groid.papon.entity.npc.TacoNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the Taco Truck crowd, with their speech bubbles. Papon customers wear one of the hats
 * in their model (or none): the others are hidden per entity, every frame, because the baked
 * model is shared by all of them.
 */
public class CrowdRenderer<T extends TacoNpc> extends GeoEntityRenderer<T> {
    private static final String[] HATS = {"sombrero", "cap"};
    private final float bubbleLift;

    public CrowdRenderer(EntityRendererProvider.Context ctx, CrowdModel<T> model, float scale, float shadow, float bubbleLift) {
        super(ctx, model);
        withScale(scale);
        this.shadowRadius = shadow;
        this.bubbleLift = bubbleLift;
    }

    @Override
    public void preRender(PoseStack pose, T entity, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        if (entity instanceof TacoCustomerEntity c && model.getBone(HATS[0]).isPresent()) {
            int hat = c.getHat();
            for (int i = 0; i < HATS.length; i++) {
                int index = i;
                model.getBone(HATS[i]).ifPresent(bone -> bone.setHidden(hat != index + 1));
            }
        }
        super.preRender(pose, entity, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        SpeechBubble.render(entity, entity.getSpeech(), bubbleLift, pose, buffers, light, entityRenderDispatcher);
    }
}
