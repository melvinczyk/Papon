package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.MakimaEntity;
import com.groid.papon.network.PaponNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.lang.reflect.Method;

/**
 * Whoever is with Makima (entity/MakimaEntity), client side (2026-10-07, the user: "the player camera needs to render her
 * better and more closely"):
 *
 *   the body   placed by her {@link MakimaEntity.Fit} (the numbers her poses were fitted to, tools/gen_makima_anims.py):
 *              the hips where hers meet them, the body tipped over her, kneeling, standing or lying under her, every
 *              stroke driven from the hips ({@link #body}); arms, legs and head per position ({@link #pose})
 *   the eyes   first person, the camera is where that body's eyes are ({@link #eye}), not at the eye height of an
 *              upright player: close over her face, behind her, under her looking up, face to face; the scroll wheel
 *              zooms the view in. Third person, the camera circles the two of you close up (scroll: nearer or further),
 *              kept out of walls
 *   controls   W / S the pace, jump the next position, 1-6 pick one ({@link #keys}); a heat bar where her hearts would
 *              be, the position and pace over it, the keys for the first seconds ({@link #hud}); the climax flashes the
 *              screen
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MakimaRider {
    private MakimaRider() {}

    /** Steve: hips to eyes, hips to shoulders, arm and leg lengths (blocks). */
    private static final float EYES = 0.87F, SHOULDERS = 0.625F, LIMB = 0.75F;

    /** Third person: how far the camera circles out; first person: the field of view's zoom (scroll wheel for both). */
    private static float orbit = 2.3F, zoom = 1.0F;
    private static int mountedAt = -1000;
    private static boolean upWas, downWas;
    @Nullable private static Method setPosition;
    private static boolean reflectionFailed;

    @Nullable
    static MakimaEntity of(@Nullable LivingEntity e) {
        return e != null && e.getVehicle() instanceof MakimaEntity m && m.posture() != MakimaEntity.Position.STAND ? m : null;
    }

    /** The stroke right now, -1..1 (PillowClient.thrust on her clock; held deep in the climax's end). */
    static float thrust(MakimaEntity m, float partial) {
        return m.holding() ? 1F : PillowClient.thrust(m.stroke(partial));
    }

    /** The climax's tremor, 0 outside it. */
    static float tremor(MakimaEntity m, float t) {
        return m.holding() ? 0.5F * Mth.sin(t * 5.3F) : 0;
    }

    /** The rider's facing (unit, horizontal): back along hers. */
    private static Vec3 riderFacing(MakimaEntity m) {
        return m.facing().scale(-1);
    }

    /**
     * Where the hips are from the rider's feet (their entity position, on the ground under them), and how far the body is
     * tipped (radians), this frame: the fit plus the stroke (into her along the body's front; lying under her, up).
     */
    private static float[] place(MakimaEntity m, float partial, float t) {
        MakimaEntity.Fit fit = m.posture().fit();
        float th = thrust(m, partial) + tremor(m, t) * 0.2F;
        float tilt = fit.tilt() * Mth.DEG_TO_RAD;
        float fwd = 0, up = (float) fit.up(), lean = 0;
        switch (fit.legs()) {
            case LIE -> up += 0.035F * Math.max(0, th);
            default -> {
                // along the front of the hips: forward, and down as much as the body is tipped over
                float d = 0.075F * th;
                fwd += d * Mth.cos(tilt);
                up -= d * Mth.sin(tilt);
                lean = -0.07F * th;
            }
        }
        return new float[]{fwd, up, tilt + lean};
    }

    /** RenderPlayerEvent.Pre (PillowClient pushes and pops): the whole body placed against her. */
    static void body(Player p, MakimaEntity m, PoseStack ps, float partial) {
        float[] pl = place(m, partial, p.tickCount + partial);
        Vec3 r = riderFacing(m);
        float fx = (float) r.x, fz = (float) r.z;
        ps.translate(fx * pl[0], pl[1], fz * pl[0]);
        ps.mulPose(new Quaternionf().rotateAxis(pl[2], fz, 0, -fx));
        ps.translate(0, -LIMB, 0);
    }

    /** The rider's eyes in the world this frame (the camera's place in first person). */
    static Vec3 eye(Player p, MakimaEntity m, float partial) {
        float[] pl = place(m, partial, p.tickCount + partial);
        Vec3 r = riderFacing(m);
        Vec3 feet = p.getPosition(partial);
        // up the tipped body from the hips, a little forward to the front of the face
        float tilt = pl[2];
        double f = pl[0] + EYES * Mth.sin(tilt) + 0.12 * Mth.cos(tilt), u = pl[1] + EYES * Mth.cos(tilt) - 0.12 * Mth.sin(tilt);
        return feet.add(r.x * f, u, r.z * f);
    }

    /** RIDE's arm pose (PillowClient.ridePose): arms, legs and head for the position. */
    static void pose(HumanoidModel<?> model, LivingEntity entity) {
        MakimaEntity m = of(entity);
        if (m == null) return;
        float partial = Minecraft.getInstance().getFrameTime(), t = entity.tickCount + partial;
        MakimaEntity.Fit fit = m.posture().fit();
        float th = thrust(m, partial), tilt = place(m, partial, t)[2];
        // the legs: straight down in the world standing, angled back to the knees kneeling, along the body lying
        float legs = switch (fit.legs()) {
            case KNEEL -> (float) Math.acos(Mth.clamp(fit.up() / LIMB, -1, 1)) - tilt;
            case STAND -> -tilt + 0.04F * th;
            case LIE -> 0.03F * th;
        };
        model.rightLeg.xRot = legs;
        model.leftLeg.xRot = legs;
        model.rightLeg.yRot = 0;
        model.leftLeg.yRot = 0;
        float spread = fit.legs() == MakimaEntity.Legs.STAND ? 0.08F : 0.16F;
        model.rightLeg.zRot = spread;
        model.leftLeg.zRot = -spread;
        model.body.xRot = 0;
        // the head looks where you look, from a body that is not upright
        float pitch = entity.getViewXRot(partial) * Mth.DEG_TO_RAD - tilt;
        model.head.xRot = Mth.clamp(pitch, -1.1F, 1.1F);
        // the arms
        float ax, ay, az;
        switch (m.posture()) {
            case MISSIONARY -> {
                // braced on the ground beside her shoulders: down from the shoulders and forward to reach it
                float shoulders = (float) fit.up() + SHOULDERS * Mth.cos(tilt);
                float reach = (float) Math.acos(Mth.clamp(shoulders / LIMB, -1, 1));
                ax = -(tilt + reach) + 0.05F * th;
                ay = 0;
                az = 0.28F;
            }
            case DOGGY, BENT -> {
                // gripping her hips, pulling her in on every stroke
                ax = -0.95F - 0.12F * th;
                ay = 0.38F;
                az = 0.05F;
            }
            case CARRY -> {
                // under her, holding her up
                ax = -1.15F - 0.06F * th;
                ay = 0.5F;
                az = 0;
            }
            default -> {
                // under her: up on her hips
                ax = -1.05F - 0.1F * th;
                ay = 0.28F;
                az = 0.1F;
            }
        }
        model.rightArm.xRot = ax;
        model.leftArm.xRot = ax;
        model.rightArm.yRot = -ay;
        model.leftArm.yRot = ay;
        model.rightArm.zRot = az;
        model.leftArm.zRot = -az;
    }

    // ------------------------------------------------------------------ the camera

    /** ComputeCameraAngles (ClientEvents): the camera moved to the body's eyes, or circled close round the two of you. */
    static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player) return;
        MakimaEntity m = of(mc.player);
        if (m == null) return;
        float partial = (float) event.getPartialTick();
        Camera cam = event.getCamera();
        if (mc.options.getCameraType().isFirstPerson()) {
            // a nod into her on every stroke; a shudder at the end
            float th = thrust(m, partial), t = mc.player.tickCount + partial;
            event.setPitch(event.getPitch() + 1.2F * th + 2.5F * tremor(m, t));
            event.setRoll(event.getRoll() + 1.5F * tremor(m, t * 1.3F));
            move(cam, eye(mc.player, m, partial));
            return;
        }
        // third person: round the middle of the two of you, orbit away along the view, never through a wall, and always
        // from above (looking up from under her, the orbit went into the ground and through your own body)
        event.setPitch(Math.max(event.getPitch(), 14F));
        float yaw = event.getYaw() * Mth.DEG_TO_RAD, pitch = event.getPitch() * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 her = m.getPosition(partial), you = eye(mc.player, m, partial);
        Vec3 mid = her.add(0, 0.55, 0).lerp(you, 0.35);
        Vec3 want = mid.subtract(look.scale(orbit));
        var hit = mc.level.clip(new ClipContext(mid, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) want = hit.getLocation().add(look.scale(0.15));
        move(cam, want);
    }

    private static void move(Camera cam, Vec3 at) {
        if (reflectionFailed) return;
        try {
            if (setPosition == null) setPosition = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90581_", Vec3.class);
            setPosition.invoke(cam, at);
        } catch (Exception e) {
            reflectionFailed = true;
            PaponMod.LOG.warn("Makima: cannot move the camera ({}); it stays at your eye height", e.toString());
        }
    }

    /** ComputeFov (ClientEvents): first person, the scroll wheel's zoom. */
    static void fov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (of(mc.player) != null && mc.options.getCameraType().isFirstPerson() && zoom < 0.999F) event.setFOV(event.getFOV() * zoom);
    }

    /** With her the scroll wheel is the camera's: nearer or further round you (third person), the view zoomed (first). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || of(mc.player) == null) return;
        double d = event.getScrollDelta();
        if (mc.options.getCameraType().isFirstPerson()) zoom = Mth.clamp(zoom - (float) d * 0.06F, 0.45F, 1.0F);
        else orbit = Mth.clamp(orbit - (float) d * 0.25F, 0.9F, 5.0F);
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ controls

    /** Client tick, before the game reads its keys (ClientEvents START): the pace and the position picked. */
    static void keys() {
        Minecraft mc = Minecraft.getInstance();
        MakimaEntity m = of(mc.player);
        if (m == null) {
            upWas = downWas = false;
            mountedAt = -1000;
            return;
        }
        if (mountedAt < 0) mountedAt = mc.player.tickCount;
        if (mc.screen != null) return;
        boolean up = mc.options.keyUp.isDown(), down = mc.options.keyDown.isDown();
        if (up && !upWas) send(MakimaEntity.FASTER);
        if (down && !downWas) send(MakimaEntity.SLOWER);
        upWas = up;
        downWas = down;
        int n = Math.min(MakimaEntity.Position.values().length - 1, mc.options.keyHotbarSlots.length);
        for (int i = 0; i < n; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) send(i + 1);
        }
    }

    private static void send(int action) {
        PaponNetwork.CHANNEL.sendToServer(new PaponNetwork.MakimaControl(action));
    }

    // ------------------------------------------------------------------ the HUD

    private static final String[] TEMPO = {"slow", "steady", "fast", "frantic"};

    /** Her hearts (a mount's health) give way to the heat bar; the climax flashes the screen. */
    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        MakimaEntity m = of(mc.player);
        if (m == null || !event.getOverlay().id().equals(VanillaGuiOverlay.MOUNT_HEALTH.id())) return;
        event.setCanceled(true);
        if (mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick(), t = mc.player.tickCount + partial;
        // the bar: pink, filling, pulsing with the stroke (and faster the hotter she is)
        int bw = 81, x = w / 2 + 10, y = h - 39 - 10;
        float heat = m.climax() > 0 ? 1F : m.heat();
        float pulse = 0.5F + 0.5F * Mth.sin(t * (0.25F + 0.5F * heat));
        g.fill(x - 1, y - 1, x + bw + 1, y + 6, 0xC0200810);
        int fill = Math.round(bw * heat);
        int c1 = blend(0xFFE0457B, 0xFFFF9EC4, pulse * heat);
        g.fill(x, y, x + fill, y + 5, c1);
        g.fill(x, y, x + fill, y + 1, 0x60FFFFFF);
        // her position and the pace beside the hotbar
        String pos = m.posture().name().toLowerCase();
        Component label = Component.translatable("hud.papon.makima.label", Component.translatable("hud.papon.makima.pos." + pos),
                Component.translatable("hud.papon.makima.tempo." + TEMPO[m.tempo()]));
        g.drawString(mc.font, label, w / 2 + 98, h - 15, m.climax() > 0 ? 0xFFFFB0D0 : 0xFFF2C6D6, true);
        // the keys, for the first ten seconds with her (above the game's own line over the hotbar)
        if (mc.player.tickCount - mountedAt < 200) {
            int a = (int) (255 * Mth.clamp((200 - (mc.player.tickCount - mountedAt)) / 30F, 0, 1));
            if (a > 8) g.drawCenteredString(mc.font, Component.translatable("hud.papon.makima.keys"), w / 2, h - 84, (a << 24) | 0xD8D8D8);
        }
        flash(g, m, w, h, t);
    }

    /** The climax: the screen's edges pulsing pink while frantic, then a white flash fading as she is held. */
    private static void flash(GuiGraphics g, MakimaEntity m, int w, int h, float t) {
        int c = m.climax();
        if (c <= 0) return;
        if (c > MakimaEntity.CLIMAX_HOLD) {
            float k = 1 - (c - MakimaEntity.CLIMAX_HOLD) / (float) (MakimaEntity.CLIMAX - MakimaEntity.CLIMAX_HOLD);
            int a = (int) (90 * k * (0.6F + 0.4F * Mth.sin(t * 1.4F)));
            int edge = Math.max(8, w / 10);
            g.fillGradient(0, 0, edge, h, (a << 24) | 0xFF4F8A, 0x00FF4F8A);
            g.fillGradient(w - edge, 0, w, h, 0x00FF4F8A, (a << 24) | 0xFF4F8A);
            return;
        }
        float k = c / (float) MakimaEntity.CLIMAX_HOLD;
        int a = (int) (235 * k * k);
        g.fill(0, 0, w, h, (a << 24) | 0xFFF0F4);
    }

    private static int blend(int a, int b, float k) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) Mth.lerp(k, ar, br) << 16 | (int) Mth.lerp(k, ag, bg) << 8 | (int) Mth.lerp(k, ab, bb);
    }
}
