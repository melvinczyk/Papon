package com.groid.papon.client;

import com.groid.papon.entity.BodyPillowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
 *   on the edge  sat on the side of the bed facing the room: her lap on the mattress, folded down over its edge at the
 *              knees, her feet just off the floor, her top half sat up and leaning back a little; pressed in at the feet
 *              on every lick, and lifted (the legs straightened out from the lap) to the face of whoever kneels there
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
        if (pillow.pose() == BodyPillowEntity.EDGE) {
            onTheEdge(pillow, partial, base, ps, buffers, light);
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

    /** Sat on the edge: where her lap ends (her knees), and her shins hanging down from it. */
    public static final float KNEE_AT = 0.38F, HIPS_AT = 0.54F;

    private static void onTheEdge(BodyPillowEntity pillow, float partial, Vec3 base, PoseStack ps, MultiBufferSource buffers, int light) {
        Direction e = pillow.edge();
        float ex = e.getStepX(), ez = e.getStepZ();
        float k = pillow.lift(partial);
        k = k * k * (3 - 2 * k);
        float lick = 0, t = pillow.tickCount + partial;
        if (pillow.getFirstPassenger() instanceof Player p) {
            t = p.tickCount + partial;
            lick = PillowClient.lick(t);
        }
        Vec3 at = pillow.kneePlace().subtract(base);
        ps.translate(at.x, at.y, at.z);
        // mesh x along the bed, its length (y) up, its front (z) out into the room
        ps.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(ez, 0, -ex, 0, 1, 0, ex, 0, ez)));
        // the shins: hanging nearly straight down, the feet a touch out from the knees; lifted, out just past level to the
        // face (you look down her legs and up her body) and nudged up into it on each lick
        float shin = Mth.lerp(k, -0.14F, -1.75F - 0.07F * lick);
        ps.mulPose(new Quaternionf().rotateX(shin));
        // the lap lies flat on the mattress (the knee takes the shins back to level), her top half sat up from the hips,
        // leaning back and breathing; squirming a little while licked
        float squirm = lick * (1 - k) * 0.04F + 0.025F * Mth.sin(t * 0.06F);
        PillowMesh.Shape shape = new PillowMesh.Shape().anchor(KNEE_AT).knee(-Mth.HALF_PI - shin, KNEE_AT).fold(1.22F, HIPS_AT)
                .bow(squirm).press(0.15F + 0.55F * lick, 0.05F);
        PillowMesh.render(ps, buffers, light, shape);
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
