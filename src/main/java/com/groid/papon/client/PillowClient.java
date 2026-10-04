package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.BodyPillowEntity;
import com.groid.papon.item.BodyPillowItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Set;

/**
 * The body pillow, client side (item/BodyPillowItem):
 *
 *   hugging   (hold use) both arms wrapped round it, pulled in tight to the chest, the cheek laid on
 *             it, a slow sway from side to side and now and then a nuzzle; in first person it fills
 *             the bottom of the screen and rocks with you
 *   licking   (hold use while sneaking) up to the face, the head bobbing into it on every lick, the
 *             tongue out (PillowLayer)
 *   in bed    with one laid on the bed (entity/BodyPillowEntity) you sleep rolled onto your side,
 *             facing her, shifted over to make room
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PillowClient {
    private PillowClient() {}

    /** Hugging or licking: the arms (and the head) from {@link #pose}. */
    public static final HumanoidModel.ArmPose HUG = HumanoidModel.ArmPose.create("PAPON_PILLOW_HUG", true,
            (model, entity, arm) -> pose(model, entity, false));
    public static final HumanoidModel.ArmPose LICK = HumanoidModel.ArmPose.create("PAPON_PILLOW_LICK", true,
            (model, entity, arm) -> pose(model, entity, true));

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            if (!using(entity, stack)) return null;
            return entity.isCrouching() ? LICK : HUG;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                               float equip, float swing) {
            if (!using(player, stack)) return false;
            float t = player.tickCount + partial;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            if (player.isCrouching()) {
                // up at your face, pushed into it on every lick
                float lick = lick(t);
                ps.translate(side * 0.06F, -0.42F + 0.03F * lick, -0.62F + 0.2F * lick);
                ps.mulPose(Axis.YP.rotationDegrees(side * -8));
                ps.mulPose(Axis.XP.rotationDegrees(-8 + 6 * lick));
            } else {
                // held to your chest, rocking slowly from side to side
                float sway = Mth.sin(t * 0.08F);
                ps.translate(side * -0.18F + 0.05F * sway, -0.62F + 0.015F * Mth.sin(t * 0.16F), -0.5F);
                ps.mulPose(Axis.YP.rotationDegrees(side * -14));
                ps.mulPose(Axis.ZP.rotationDegrees(6 * sway));
                ps.mulPose(Axis.XP.rotationDegrees(4));
            }
            return true;
        }
    };

    static boolean using(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack && stack.getItem() instanceof BodyPillowItem;
    }

    /** A lick's stroke, 0..1..0, about two a second. */
    public static float lick(float t) {
        float ph = (t % 11F) / 11F;
        float k = Mth.sin(ph * Mth.PI);
        return k * k;
    }

    private static void pose(HumanoidModel<?> model, LivingEntity entity, boolean licking) {
        float t = entity.tickCount + Minecraft.getInstance().getFrameTime();
        if (licking) {
            float lick = lick(t);
            model.rightArm.xRot = -1.6F;
            model.rightArm.yRot = -0.45F;
            model.rightArm.zRot = 0;
            model.leftArm.xRot = -1.6F;
            model.leftArm.yRot = 0.45F;
            model.leftArm.zRot = 0;
            model.head.xRot = -0.08F + 0.3F * lick;
            model.head.yRot = 0.06F * Mth.sin(t * 0.13F);
            model.head.zRot = 0.14F * Mth.sin(t * 0.21F);
            return;
        }
        float sway = Mth.sin(t * 0.08F);
        // a nuzzle every few seconds: the cheek rubbed along it
        float cycle = (t % 90F) / 90F;
        float nuzzle = cycle < 0.25F ? Mth.sin(cycle / 0.25F * Mth.PI) : 0;
        model.rightArm.xRot = -1.0F + 0.06F * sway;
        model.rightArm.yRot = -0.7F + 0.05F * Mth.sin(t * 0.11F);
        model.rightArm.zRot = 0.08F;
        model.leftArm.xRot = -0.88F - 0.06F * sway;
        model.leftArm.yRot = 0.78F - 0.05F * Mth.sin(t * 0.11F + 1);
        model.leftArm.zRot = -0.08F;
        model.head.xRot = 0.3F;
        model.head.yRot = 0.3F + 0.06F * sway + 0.16F * nuzzle * Mth.sin(t * 1.1F);
        model.head.zRot = 0.24F + 0.07F * sway;
    }

    // ------------------------------------------------------------------ asleep beside her

    private static final Set<Player> ROLLED = new HashSet<>();

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!p.isSleeping()) return;
        BlockPos bed = p.getSleepingPos().orElse(null);
        if (bed == null) return;
        BodyPillowEntity pillow = BodyPillowEntity.onBed(p.level(), bed);
        if (pillow == null) return;
        Direction f = pillow.facing(), side = pillow.side();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        // over to the other half of the bed, up off the mattress, and rolled onto the side to face her
        ps.translate(-side.getStepX() * 0.16, 0.3, -side.getStepZ() * 0.16);
        ps.mulPose(new Quaternionf().rotateAxis(-Mth.HALF_PI, f.getStepX(), 0, f.getStepZ()));
        ROLLED.add(p);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (ROLLED.remove(event.getEntity())) event.getPoseStack().popPose();
    }
}
