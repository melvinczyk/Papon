package com.groid.papon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The body pillow as an item (models/item/body_pillow.json is builtin/entity): the soft mesh (PillowMesh) upright in
 * the item's unit box, front toward -z like the old box model was, so the display transforms still frame it. In your
 * own hand it takes the shape of what you are doing with it (PillowClient.shape): squeezed in a hug, pressed to your
 * face, folded over.
 */
public class PillowItemRenderer extends BlockEntityWithoutLevelRenderer {
    public PillowItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        PillowMesh.Shape shape = new PillowMesh.Shape().bend(0.12F);
        boolean firstPerson = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (firstPerson && mc.player != null && PillowClient.using(mc.player, stack)) {
            shape = PillowClient.shape(mc.player, mc.getFrameTime(), true);
            if (PillowClient.mode(mc.player, stack) == PillowClient.Mode.HUMP && PillowClient.humpPose != null) {
                // bent over in front of you, in the frame the hand transform set up (not the item's display transform)
                ps.pushPose();
                ps.last().pose().set(PillowClient.humpPose);
                ps.last().normal().set(PillowClient.humpNormal);
                PillowMesh.render(ps, buffers, light, shape);
                ps.popPose();
                return;
            }
        }
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        float s = 1F / PillowMesh.LENGTH;
        ps.scale(s, s, s);
        PillowMesh.render(ps, buffers, light, shape);
        ps.popPose();
    }
}
