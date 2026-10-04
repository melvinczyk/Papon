package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.BodyPillowEntity;
import com.groid.papon.item.BodyPillowItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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
 *             it, a slow sway from side to side and now and then a nuzzle; the pillow squeezed in the
 *             middle where the arms are, bulging either side, bent round you
 *   licking   (hold use while sneaking) up to the face, the head bobbing into it on every lick, the
 *             tongue out (PillowLayer); the pillow pressed in where the face is
 *   humping   (hold use looking down) folded over double at the hips, hands gripping it, the hips
 *             thrusting; the pillow squashed at the fold on every thrust
 *   in bed    laid on a bed (entity/BodyPillowEntity) it slouches against the headboard; right-click it
 *             and you are on the bed with it (humping, the pillow squashed on every thrust, the bed
 *             creaking); with someone asleep in the bed it lies beside them instead, and they sleep
 *             rolled onto their side, facing her
 * The pillow's shape for each is {@link #shape} (PillowMesh); the item draws through PillowItemRenderer.
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PillowClient {
    private PillowClient() {}

    public enum Mode { CARRY, HUG, LICK, HUMP }

    /** Hugging, licking or humping: the arms (and the head, the body, the legs) from {@link #pose}. */
    public static final HumanoidModel.ArmPose HUG = HumanoidModel.ArmPose.create("PAPON_PILLOW_HUG", true,
            (model, entity, arm) -> pose(model, entity, Mode.HUG));
    public static final HumanoidModel.ArmPose LICK = HumanoidModel.ArmPose.create("PAPON_PILLOW_LICK", true,
            (model, entity, arm) -> pose(model, entity, Mode.LICK));
    public static final HumanoidModel.ArmPose HUMP = HumanoidModel.ArmPose.create("PAPON_PILLOW_HUMP", true,
            (model, entity, arm) -> pose(model, entity, Mode.HUMP));

    private static BlockEntityWithoutLevelRenderer itemRenderer;

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            if (!using(entity, stack)) return null;
            return switch (mode(entity, stack)) {
                case LICK -> LICK;
                case HUMP -> HUMP;
                default -> HUG;
            };
        }

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (itemRenderer == null) itemRenderer = new PillowItemRenderer();
            return itemRenderer;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                               float equip, float swing) {
            if (!using(player, stack)) return false;
            float t = player.tickCount + partial;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            switch (mode(player, stack)) {
                case LICK -> {
                    // up at your face, pushed into it on every lick
                    float lick = lick(t);
                    ps.translate(side * 0.06F, -0.42F + 0.03F * lick, -0.62F + 0.2F * lick);
                    ps.mulPose(Axis.YP.rotationDegrees(side * -8));
                    ps.mulPose(Axis.XP.rotationDegrees(-8 + 6 * lick));
                }
                case HUMP -> {
                    // low in front of you, folded over, rammed forward on every thrust
                    float th = thrust(t);
                    ps.translate(side * -0.2F, -0.62F + 0.02F * th, -0.62F - 0.12F * th);
                    ps.mulPose(Axis.YP.rotationDegrees(side * -10));
                    ps.mulPose(Axis.XP.rotationDegrees(-30 + 6 * th));
                }
                default -> {
                    // held to your chest, rocking slowly from side to side
                    float sway = Mth.sin(t * 0.08F);
                    ps.translate(side * -0.18F + 0.05F * sway, -0.62F + 0.015F * Mth.sin(t * 0.16F), -0.5F);
                    ps.mulPose(Axis.YP.rotationDegrees(side * -14));
                    ps.mulPose(Axis.ZP.rotationDegrees(6 * sway));
                    ps.mulPose(Axis.XP.rotationDegrees(4));
                }
            }
            return true;
        }
    };

    static boolean using(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack && stack.getItem() instanceof BodyPillowItem;
    }

    /** What they are doing with it: sneaking, a lick; looking down, a hump; otherwise a hug (or just carrying it). */
    public static Mode mode(LivingEntity entity, ItemStack stack) {
        if (!using(entity, stack)) return Mode.CARRY;
        if (entity.isCrouching()) return Mode.LICK;
        if (BodyPillowItem.humping(entity)) return Mode.HUMP;
        return Mode.HUG;
    }

    /** A lick's stroke, 0..1..0, about two a second. */
    public static float lick(float t) {
        float ph = (t % 11F) / 11F;
        float k = Mth.sin(ph * Mth.PI);
        return k * k;
    }

    /** A thrust, -1..1, about three a second; sharp on the way in. */
    public static float thrust(float t) {
        float s = Mth.sin(t * 0.9F);
        return s > 0 ? (float) Math.pow(s, 0.6) : s;
    }

    /** How hard the pillow is squashed by the thrust right now, 0..1 (at the end of each stroke in). */
    public static float squash(float t) {
        return Math.max(0, thrust(t));
    }

    /** The pillow's shape in their hands ({@code firstPerson}: as you see it in your own hand). */
    public static PillowMesh.Shape shape(LivingEntity entity, float partial, boolean firstPerson) {
        float t = entity.tickCount + partial;
        ItemStack stack = entity.getUseItem();
        return switch (mode(entity, stack)) {
            case HUG -> new PillowMesh.Shape().squeeze(0.85F + 0.15F * Mth.sin(t * 0.16F), 0.56F).bend(0.55F).press(0.18F, 0.84F);
            case LICK -> new PillowMesh.Shape().press(0.35F + 0.45F * lick(t), 0.66F).bend(0.3F).squeeze(0.4F, 0.4F);
            case HUMP -> firstPerson
                    // in your own view: held low in front of you, bent over toward you, squashed on every thrust
                    ? new PillowMesh.Shape().fold(1.1F, 0.55F).press(0.25F + 0.65F * squash(t), 0.5F).squeeze(0.4F, 0.66F)
                    : new PillowMesh.Shape().fold(2.55F, 0.5F).anchor(0.5F).press(0.25F + 0.65F * squash(t), 0.45F).squeeze(0.5F, 0.62F);
            default -> new PillowMesh.Shape().squeeze(0.5F, 0.72F).bend(-0.15F);
        };
    }

    private static void pose(HumanoidModel<?> model, LivingEntity entity, Mode mode) {
        float t = entity.tickCount + Minecraft.getInstance().getFrameTime();
        if (mode == Mode.LICK) {
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
        if (mode == Mode.HUMP) {
            // gripping it low at the hips, the hips driving into it, leaning back, head thrown back now and then
            float th = thrust(t);
            model.rightArm.xRot = -0.75F - 0.12F * th;
            model.rightArm.yRot = -0.32F;
            model.rightArm.zRot = 0.1F;
            model.leftArm.xRot = -0.75F - 0.12F * th;
            model.leftArm.yRot = 0.32F;
            model.leftArm.zRot = -0.1F;
            model.body.xRot = -0.12F - 0.1F * th;
            model.head.xRot = -0.25F - 0.12F * th;
            model.rightLeg.xRot = 0.15F + 0.12F * th;
            model.leftLeg.xRot = 0.15F + 0.12F * th;
            model.rightLeg.z = 0.1F - 1.0F * th;
            model.leftLeg.z = 0.1F - 1.0F * th;
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

    // ------------------------------------------------------------------ the whole body: thrusting, asleep beside her

    private static final Set<Player> PUSHED = new HashSet<>();

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        PoseStack ps = event.getPoseStack();
        float t = p.tickCount + event.getPartialTick();
        if (p.getVehicle() instanceof BodyPillowEntity pillow) {
            // on the bed with her: down on the knees in the mattress, the hips driving at her
            Direction f = pillow.facing();
            float th = thrust(t);
            ps.pushPose();
            ps.translate(f.getStepX() * 0.09 * th, -0.42, f.getStepZ() * 0.09 * th);
            ps.mulPose(new Quaternionf().rotateAxis(0.1F * th, -f.getStepZ(), 0, f.getStepX()));
            PUSHED.add(p);
            return;
        }
        if (using(p, p.getUseItem()) && mode(p, p.getUseItem()) == Mode.HUMP) {
            // humping it in hand: the whole body rocks with the hips
            float th = thrust(t);
            double yaw = Math.toRadians(Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot));
            ps.pushPose();
            ps.translate(-Math.sin(yaw) * 0.06 * th, 0, Math.cos(yaw) * 0.06 * th);
            PUSHED.add(p);
            return;
        }
        if (!p.isSleeping()) return;
        BlockPos bed = p.getSleepingPos().orElse(null);
        if (bed == null) return;
        BodyPillowEntity pillow = BodyPillowEntity.onBed(p.level(), bed);
        if (pillow == null) return;
        Direction f = pillow.facing(), side = pillow.side();
        ps.pushPose();
        // over to the other half of the bed, up off the mattress, and rolled onto the side to face her
        ps.translate(-side.getStepX() * 0.16, 0.3, -side.getStepZ() * 0.16);
        ps.mulPose(new Quaternionf().rotateAxis(-Mth.HALF_PI, f.getStepX(), 0, f.getStepZ()));
        PUSHED.add(p);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (PUSHED.remove(event.getEntity())) event.getPoseStack().popPose();
    }
}
