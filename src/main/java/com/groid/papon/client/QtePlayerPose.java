package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * The player in his fist (the QTEs) acts it out (the user: "animate the player model too"): the vanilla player model can
 * only be posed between its setupAnim and its draw, so for the moment a player is held this draws them itself, the way
 * LivingEntityRenderer does (their skin, their armour and held items through the renderer's own layers), with the limbs
 * posed here:
 *   the squeeze     arms pinned to the sides, legs kicking, the body crushed thinner the worse they mash (his meter,
 *                   synced from their client) and pushing the arms out as they win
 *   the tango       dipped and turned in his arms, one hand on his shoulder, the other held out, stepping on the beat
 *   the others      a struggle: arms shoving at his fist, legs flailing
 *   the fail        limp
 *   the duel        (you, on your screen) an air guitar: the neck held out, the right hand strumming on every note
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QtePlayerPose {
    private QtePlayerPose() {}

    enum Kind { STRUGGLE, SQUEEZE, TANGO, LIMP, SHRED }

    private static Field layers;
    private static boolean broken;
    /** The duel: when the local player last hit a fret (the strum). */
    private static long strumAt;

    static void strum() {
        strumAt = System.currentTimeMillis();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (broken || event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer p)) return;
        PaponBossEntity boss = holder(p);
        if (boss == null) return;
        Kind kind = kind(boss, p);
        if (kind == null) return;
        try {
            draw(event.getRenderer(), p, boss, kind, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight());
            event.setCanceled(true);
        } catch (Throwable e) {
            broken = true;
            PaponMod.LOG.warn("[papon] QTE player poses off: {}", e.toString());
        }
    }

    @Nullable
    private static PaponBossEntity holder(AbstractClientPlayer p) {
        for (PaponBossEntity b : p.level().getEntitiesOfClass(PaponBossEntity.class, p.getBoundingBox().inflate(24))) {
            BossState s = b.getBossState();
            if (b.heldId() == p.getId() && (s == BossState.QTE_HOLD || s == BossState.QTE_FAIL)) return b;
            if (s == BossState.P3_DUEL && p == net.minecraft.client.Minecraft.getInstance().player && QteClient.duel(b)) return b;
        }
        return null;
    }

    @Nullable
    private static Kind kind(PaponBossEntity boss, AbstractClientPlayer p) {
        BossState s = boss.getBossState();
        if (s == BossState.P3_DUEL) return Kind.SHRED;
        if (s == BossState.QTE_FAIL) return boss.ticksInState() > 22 ? Kind.LIMP : Kind.STRUGGLE;
        return switch (boss.qteKind()) {
            case 5 -> Kind.SQUEEZE;
            case 4 -> Kind.TANGO;
            default -> Kind.STRUGGLE;
        };
    }

    @SuppressWarnings("unchecked")
    private static void draw(PlayerRenderer renderer, AbstractClientPlayer p, PaponBossEntity boss, Kind kind, float partial, PoseStack pose,
                             MultiBufferSource buffers, int light) throws IllegalAccessException {
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        float age = p.tickCount + partial;
        float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot);
        float pitch = Mth.lerp(partial, p.xRotO, p.getXRot());
        float meter = boss.qteMeterValue();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        // the whole body: crushed in the squeeze (thinner and shaking, the worse it goes), dipped in the tango
        if (kind == Kind.SQUEEZE) {
            float crush = 0.25F + 0.45F * (1F - meter);
            pose.translate(Mth.sin(age * 2.7F) * 0.03F * crush, 0, 0);
            pose.scale(1F - 0.28F * crush, 1F + 0.08F * crush, 1F - 0.18F * crush);
        } else if (kind == Kind.TANGO) {
            pose.translate(0, 0.9, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-25F - 10F * Mth.sin(age * 0.25F)));
            pose.mulPose(Axis.ZP.rotationDegrees(8F * Mth.sin(age * 0.5F)));
            pose.translate(0, -0.9, 0);
        } else if (kind == Kind.LIMP) { pose.mulPose(Axis.XP.rotationDegrees(-12F)); }
        pose.scale(-1F, -1F, 1F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0, -1.501F, 0);
        model.setAllVisible(true);
        model.hat.visible = p.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = p.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = p.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = p.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = p.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = p.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.crouching = false;
        model.riding = false;
        model.young = false;
        model.attackTime = 0;
        model.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.prepareMobModel(p, 0, 0, partial);
        model.setupAnim(p, 0, 0, age, Mth.wrapDegrees(headYaw - bodyYaw), pitch);
        posed(model, kind, age, meter);
        var vc = buffers.getBuffer(model.renderType(renderer.getTextureLocation(p)));
        model.renderToBuffer(pose, vc, light, LivingEntityRenderer.getOverlayCoords(p, 0F), 1F, 1F, 1F, 1F);
        if (layers == null) layers = ObfuscationReflectionHelper.findField(LivingEntityRenderer.class, "f_115291_");
        for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer
                : (List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>) layers.get(renderer)) {
            layer.render(pose, buffers, light, p, 0, 0, partial, age, Mth.wrapDegrees(headYaw - bodyYaw), pitch);
        }
        pose.popPose();
    }

    private static void posed(PlayerModel<AbstractClientPlayer> m, Kind kind, float age, float meter) {
        float d = Mth.DEG_TO_RAD;
        switch (kind) {
            case SQUEEZE -> {
                // arms pinned to the sides, pushed out as the meter fills; legs kicking; head thrown back gasping
                float out = 0.1F + 0.9F * meter;
                float strain = Mth.sin(age * 1.9F);
                set(m.rightArm, -10 * d + strain * 0.08F, 0, (6 + 50 * out) * d + strain * 0.1F * out);
                set(m.leftArm, -10 * d - strain * 0.08F, 0, -(6 + 50 * out) * d - strain * 0.1F * out);
                set(m.rightLeg, Mth.sin(age * 1.3F) * 0.7F, 0, 0.12F);
                set(m.leftLeg, -Mth.sin(age * 1.3F) * 0.7F, 0, -0.12F);
                m.head.xRot = -30 * d + Mth.sin(age * 0.6F) * 0.1F;
            }
            case TANGO -> {
                // a hand on his shoulder, the other held out in his; the head turned, chin up; a step on every beat
                set(m.rightArm, -150 * d, 0, 20 * d);
                set(m.leftArm, -90 * d, 30 * d, -10 * d);
                float step = Mth.sin(age * 0.5F);
                set(m.rightLeg, step * 0.5F, 0, 0.1F);
                set(m.leftLeg, -0.6F - step * 0.2F, 0, -0.15F);
                m.head.yRot = 40 * d;
                m.head.xRot = -15 * d;
            }
            case LIMP -> {
                set(m.rightArm, 20 * d, 0, 30 * d);
                set(m.leftArm, 20 * d, 0, -30 * d);
                set(m.rightLeg, 10 * d, 0, 0.1F);
                set(m.leftLeg, 5 * d, 0, -0.1F);
                m.head.xRot = 40 * d;
                m.head.yRot = 0;
            }
            case SHRED -> {
                // an air guitar: the neck held out to the left, the right hand strumming on every note you hit
                float since = (System.currentTimeMillis() - strumAt) / 1000F;
                float hit = Mth.clamp(1F - since / 0.18F, 0F, 1F);
                set(m.leftArm, -70 * d, 40 * d, -10 * d);
                set(m.rightArm, (-40 - 30 * hit) * d, -30 * d, 10 * d);
                set(m.rightLeg, 0, 0, 8 * d);
                set(m.leftLeg, -10 * d, 0, -8 * d);
                m.head.xRot = 15 * d + Mth.sin(age * 0.9F) * 0.15F;     // banging his head
            }
            default -> {
                // the struggle: shoving at the fist round him, legs flailing
                float a = Mth.sin(age * 1.6F), b = Mth.cos(age * 1.3F);
                set(m.rightArm, -110 * d + a * 0.5F, 0, 20 * d + b * 0.2F);
                set(m.leftArm, -110 * d - a * 0.5F, 0, -20 * d - b * 0.2F);
                set(m.rightLeg, Mth.sin(age * 1.1F) * 0.8F, 0, 0.1F);
                set(m.leftLeg, -Mth.sin(age * 1.1F) * 0.8F, 0, -0.1F);
            }
        }
        // the outer layer follows (setupAnim copied it before these changes)
        m.hat.copyFrom(m.head);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.jacket.copyFrom(m.body);
    }

    private static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }
}
