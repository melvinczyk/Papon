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
 *   on her back (someone on the bed with it, planked over her) laid down flat along the bed, Makima face up, pressed flat
 *              at the hips on every thrust
 *   on her knees (jump, from behind) rolled over: face down, her shins along the mattress, her thighs up to her hips
 *              raised at the kneeling rider's, her top half sloping down to the head of the bed; squashed and rocking
 *              with every thrust
 *   across it  (someone at the side of the bed) on her back across the bed, hips at its edge, legs up on their shoulders;
 *              or on her knees on the floor there, bent over the bed, her top half flat on it
 * Every one of those is a place, a turn and a shape (her slouch too), eased one into the next: she lies down as you climb on,
 * rolls over, sits back up when you get off.
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
        // mesh x across the bed, its length (y) toward the head, its front up
        Direction across = f.getClockWise();
        Quaternionf bedFrame = new Quaternionf().setFromNormalized(new Matrix3f(across.getStepX(), 0, across.getStepZ(), fx, 0, fz, 0, 1, 0));
        float d = ease(pillow.down(partial)), r = ease(pillow.lift(partial));
        float squash = 0, rock = 0;
        if (pillow.getFirstPassenger() instanceof Player p) {
            float t = p.tickCount + partial;
            squash = PillowClient.squash(t);
            rock = PillowClient.thrust(t);
        }
        // slouched: sat at the headboard (the bottom of the fold there), folded up and leaning back until its top rests on
        // the wall, sagging to one side: a slouch, not a stand
        Vec3 slouchAt = pillow.slouchPlace().add(0, PillowMesh.THICK * 0.9, 0);
        Quaternionf slouchTurn = new Quaternionf(bedFrame).rotateY(0.2F);
        PillowMesh.Shape slouch = new PillowMesh.Shape().anchor(0.3F).knee(1.2F, 0.3F).fold(0, 0.5F).bend(-0.25F).bow(0.09F).press(0.15F, 0.42F);
        // laid down for her rider (anchored at her hips), pressed flat there on every thrust
        Vec3 at;
        Quaternionf turn;
        PillowMesh.Shape laid;
        Direction side = pillow.humpSide();
        if (side == null) {
            // on the bed: on her back along it; rolled over (about her length) onto her knees, the shins flat, the thighs up
            // to her hips raised at the kneeling rider's, the top half down from them to the head of the bed
            Vec3 lying = pillow.alongBed(BodyPillowEntity.LIE_HIPS, PillowMesh.THICK);
            Vec3 kneeling = pillow.alongBed(BodyPillowEntity.KNEEL_HIPS, PillowMesh.THICK + BodyPillowEntity.HIPS_UP + 0.02 * rock * r);
            at = lying.lerp(kneeling, r);
            turn = new Quaternionf(bedFrame).rotateY(Mth.PI * r + 0.04F * rock);
            laid = new PillowMesh.Shape().anchor(0.5F).knee(-(1.4F + 0.06F * rock) * r, 0.3F).fold((1.82F + 0.08F * rock) * r, 0.5F);
        } else {
            // across the bed from its side (her length, y, away from you, into it): on her back, hips at the edge, the legs
            // tipped up from them onto your shoulders (bobbing with you) and her top half flat; rolled over, on her knees on
            // the floor there, the shins along it, the thighs up to her hips at the edge, her top half flat on the bed
            float sx = side.getStepX(), sz = side.getStepZ();
            Quaternionf sideFrame = new Quaternionf().setFromNormalized(new Matrix3f(sz, 0, -sx, -sx, 0, -sz, 0, 1, 0));
            Vec3 spot = pillow.sideSpot();
            at = spot.add(sx * BodyPillowEntity.SIDE_HIPS, BodyPillowEntity.MATTRESS + PillowMesh.THICK, sz * BodyPillowEntity.SIDE_HIPS);
            float legs = (0.85F + 0.08F * rock) * (1 - r);
            turn = sideFrame.rotateY(Mth.PI * r + 0.04F * rock).rotateX(-legs);
            laid = new PillowMesh.Shape().anchor(0.5F).knee(-1.2F * r, 0.13F).fold(legs + (1.25F + 0.06F * rock) * r, 0.5F);
        }
        laid.press(0.15F + 0.75F * squash, 0.47F).squeeze(0.2F * squash, 0.6F);
        Vec3 here = slouchAt.lerp(at, d).subtract(base);
        ps.translate(here.x, here.y, here.z);
        ps.mulPose(slouchTurn.slerp(turn, d));
        PillowMesh.Shape shape = blend(slouch, laid, d);
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

    private static float ease(float k) { return k * k * (3 - 2 * k); }

    /** One shape eased into another. */
    private static PillowMesh.Shape blend(PillowMesh.Shape a, PillowMesh.Shape b, float k) {
        return new PillowMesh.Shape().anchor(Mth.lerp(k, a.anchor, b.anchor))
                .knee(Mth.lerp(k, a.knee, b.knee), Mth.lerp(k, a.kneeAt, b.kneeAt)).fold(Mth.lerp(k, a.fold, b.fold), Mth.lerp(k, a.foldAt, b.foldAt))
                .bend(Mth.lerp(k, a.bend, b.bend)).bow(Mth.lerp(k, a.bow, b.bow))
                .press(Mth.lerp(k, a.press, b.press), Mth.lerp(k, a.pressAt, b.pressAt))
                .squeeze(Mth.lerp(k, a.squeeze, b.squeeze), Mth.lerp(k, a.squeezeAt, b.squeezeAt));
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
