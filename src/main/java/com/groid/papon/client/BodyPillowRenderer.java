package com.groid.papon.client;

import com.groid.papon.entity.BodyPillowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * A body pillow on a bed (entity/BodyPillowEntity), the soft mesh (PillowMesh):
 *   slouched   sat on the mattress at the head of the bed, its bottom third lying toward the foot, folded up at the
 *              headboard and leaning back on the wall, sagging a little to one side; Makima facing the room
 *   humped     (someone on the bed with it) squashed where they thrust, rocking with it
 *   beside you (someone asleep in the bed) lying on its side along the bed, Makima turned in to them
 */
public class BodyPillowRenderer extends EntityRenderer<BodyPillowEntity> {
    public BodyPillowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(BodyPillowEntity pillow, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Direction f = pillow.facing();
        float fx = f.getStepX(), fz = f.getStepZ();
        Vec3 base = pillow.getPosition(partial);
        ps.pushPose();
        if (sleeperIn(pillow)) {
            // lying along the side of the bed, on its edge, the front in toward the sleeper
            Direction side = pillow.side();
            Vec3 at = pillow.restingPlace().subtract(base);
            ps.translate(at.x, at.y + PillowMesh.WIDTH / 2, at.z);
            ps.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(0, -1, 0, fx, 0, fz, -side.getStepX(), 0, -side.getStepZ())));
            PillowMesh.render(ps, buffers, light, new PillowMesh.Shape().bend(0.1F));
            ps.popPose();
            return;
        }
        // slouched against the headboard: mesh x across the bed, its length (y) toward the head, its front up
        Direction across = f.getClockWise();
        Vec3 at = pillow.slouchPlace().subtract(base);
        ps.translate(at.x, at.y + PillowMesh.THICK * 0.9, at.z);
        ps.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(across.getStepX(), 0, across.getStepZ(), fx, 0, fz, 0, 1, 0)));
        float squash = 0, rock = 0;
        if (pillow.getFirstPassenger() instanceof Player p) {
            float t = p.tickCount + partial;
            squash = PillowClient.squash(t);
            rock = PillowClient.thrust(t);
        }
        // a slump to one side, and a little give with each thrust
        ps.mulPose(new Quaternionf().rotateY(0.2F + 0.04F * rock));
        // folded up at the headboard and leaning back until its top rests on the wall, sagging: a slouch, not a stand
        PillowMesh.Shape shape = new PillowMesh.Shape().anchor(0.3F).fold(1.2F + 0.12F * squash, 0.3F).bend(-0.25F).bow(0.09F)
                .press(0.15F + 0.75F * squash, 0.42F).squeeze(0.2F * squash, 0.6F);
        PillowMesh.render(ps, buffers, light, shape);
        ps.popPose();
        super.render(pillow, yaw, partial, ps, buffers, light);
    }

    /** Someone asleep in this pillow's bed. */
    private static boolean sleeperIn(BodyPillowEntity pillow) {
        for (Player p : pillow.level().players()) {
            if (p.isSleeping() && p.getSleepingPos().map(pos -> pos.equals(pillow.bed())).orElse(false)) return true;
        }
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(BodyPillowEntity pillow) { return TextureAtlas.LOCATION_BLOCKS; }
}
