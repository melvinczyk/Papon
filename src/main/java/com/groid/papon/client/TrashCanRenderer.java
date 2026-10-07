package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.block.TrashCanBlock;
import com.groid.papon.block.TrashCanBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The trash can's lid (block/TrashCanBlock): the baked model papon:block/trash_can_lid (tools/gen_trash_can.py, loaded by
 * ClientSetup), swung up on its hinge at the back edge by the angle TrashCanBlockEntity keeps (it flaps as it eats), and
 * the happy face (block/trash_can_happy) over the front while it eats.
 */
public class TrashCanRenderer implements BlockEntityRenderer<TrashCanBlockEntity> {
    public static final ResourceLocation LID = PaponMod.id("block/trash_can_lid"), HAPPY = PaponMod.id("block/trash_can_happy");
    /** The hinge, in model units (the lid's knuckle). */
    private static final float HINGE_Y = 14.1F / 16, HINGE_Z = 14.75F / 16;

    public TrashCanRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TrashCanBlockEntity can, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState s = can.getBlockState();
        if (!(s.getBlock() instanceof TrashCanBlock)) return;
        BakedModel lid = Minecraft.getInstance().getModelManager().getModel(LID);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-yRot(s)));
        ps.translate(-0.5, 0, -0.5);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        // the face beaming while it eats
        if (can.happy()) {
            renderer.renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), s, Minecraft.getInstance().getModelManager().getModel(HAPPY),
                    1F, 1F, 1F, light, overlay, ModelData.EMPTY, Sheets.cutoutBlockSheet());
        }
        ps.translate(0, HINGE_Y, HINGE_Z);
        ps.mulPose(Axis.XP.rotation(can.lid(partial)));
        ps.translate(0, -HINGE_Y, -HINGE_Z);
        renderer.renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), s, lid, 1F, 1F, 1F, light, overlay, ModelData.EMPTY, Sheets.cutoutBlockSheet());
        ps.popPose();
    }

    /** The blockstate's y rotation (the model's front faces north). */
    private static float yRot(BlockState s) {
        return switch (s.getValue(TrashCanBlock.FACING)) {
            case EAST -> 90F;
            case SOUTH -> 180F;
            case WEST -> 270F;
            default -> 0F;
        };
    }
}
