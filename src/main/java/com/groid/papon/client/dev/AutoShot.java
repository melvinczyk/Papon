package com.groid.papon.client.dev;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.PaponBossEntity;
import com.groid.papon.entity.npc.GerbinEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
import com.groid.papon.entity.npc.VendorState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Dev tool, off unless the client runs with -Dpapon.autoshot=true (./gradlew runClient -Pautoshot,
 * which also quick-plays the "New World" save): sets up a Taco Truck in singleplayer, plays the
 * fraud, the unmasking and the fight, and saves screenshots of the key moments to
 * run/screenshots/papon_*.png, then quits. For checking visuals without a person at the keyboard.
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ENABLED = Boolean.getBoolean("papon.autoshot");
    private static int tick;
    private static final Set<String> taken = new HashSet<>();
    private static int doneAt = -1;
    private static int drivingSince = -1;

    private AutoShot() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        tick++;
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "papon escort true", "tp @s 4000 200 4000", "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]");
            case 100 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves rotated 0 0 run papon tacoline");
            case 160 -> frontOfWindow(mc, 5.5, 0);
            case 200 -> shot(mc, "01_truck_window");
            case 690 -> {
                frontOfWindow(mc, 4.5, 0);
                run("papon fraud");
            }
            default -> {}
        }
        if (tick > 200 && tick < 690) watchGerbin(mc);
        if (tick > 690) followTheShow(mc);
    }

    /** Beside Gerbin inside the truck, at each of his stations. */
    private static void watchGerbin(Minecraft mc) {
        GerbinEntity g = nearest(mc, GerbinEntity.class);
        if (g == null) return;
        String name = switch (g.getGerbinState()) {
            case CHOP -> "02_gerbin_chop";
            case WASH -> "03_gerbin_wash";
            default -> null;
        };
        if (name == null || taken.contains(name)) return;
        if (g.ticksInState() == 20) {
            Vec3 f = Vec3.directionFromRotation(0, g.getYRot());
            Vec3 side = new Vec3(-f.z, 0, f.x);
            Vec3 at = g.position().add(side.scale(1.9)).add(f.scale(-0.6));
            Vec3 look = g.position().add(f.scale(0.6)).add(0, 1.0, 0);
            double dx = look.x - at.x, dy = look.y - (at.y + 1.62), dz = look.z - at.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", at.x, at.y, at.z, yaw, pitch));
        }
        if (g.ticksInState() == 70) once(mc, name);
    }

    private static void followTheShow(Minecraft mc) {
        TacoVendorEntity v = nearest(mc, TacoVendorEntity.class);
        if (v != null) {
            VendorState s = v.getVendorState();
            int t = v.ticksInState();
            if (s == VendorState.INSPECTING && t == 120) once(mc, "04_inspect");
            if (s == VendorState.FRAUD && t == 20) once(mc, "05_fraud");
            if (s == VendorState.EXPOSED && t == 30) once(mc, "06_exposed");
            if (s == VendorState.UNMASK && t == 22) once(mc, "07_rip");
            if (s == VendorState.UNMASK && t == 76) once(mc, "08_toque_off");
            if (s == VendorState.UNMASK && t == 96) once(mc, "09_norwood");
            if (s == VendorState.REALIZE && t == 50) once(mc, "10_realize");
            if (s == VendorState.RAGE && t == 40) once(mc, "11_rage");
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) return;
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (s == BossState.LEAP_OUT && t == 14) once(mc, "12_leap");
        if (s == BossState.CHUG && t == 60) once(mc, "13_chug");
        if (s == BossState.ROAR && t == 8) once(mc, "14_roar");
        if (s.fighting() && !s.equals(BossState.ROAR) && !taken.contains("15_bossbar")) {
            lookAt(mc, b);
            if (t == 20) once(mc, "15_bossbar");
        }
        if (taken.contains("15_bossbar")) lookAt(mc, b);
        if (s == BossState.SWIPE && t == 13) once(mc, "16_swipe");
        if (s == BossState.BREATH && t == 30) once(mc, "17_breath");
        if (s == BossState.TO_VAN && t == 20) once(mc, "18_to_van");
        if (s == BossState.DRINK_HANDLE && t == 30) once(mc, "19_handle");
        if (s == BossState.DRIVING) {
            if (drivingSince < 0) drivingSince = tick;
            if (tick - drivingSince == 30) once(mc, "20_ram");
            if (tick - drivingSince == 50) once(mc, "21_ram_close");
        }
        if (s == BossState.EXIT_STUMBLE && t == 30) {
            once(mc, "22_stumble");
            if (doneAt < 0) doneAt = tick;
        }
        if (doneAt > 0) {
            if (tick == doneAt + 10) run("kill @e[type=papon:papon_boss]", "ride @s mount @e[type=papon:caravan,limit=1,sort=nearest]");
            if (tick == doneAt + 30) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (tick == doneAt + 60) shot(mc, "23_player_driving");
            if (tick == doneAt + 70) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                shot(mc, "24_driver_seat");
            }
            if (tick == doneAt + 90) mc.stop();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void once(Minecraft mc, String name) {
        if (taken.add(name)) shot(mc, name);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "papon_" + name + ".png", mc.getMainRenderTarget(),
                msg -> PaponMod.LOG.info("[autoshot] {}", name));
    }

    private static void run(String... commands) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        var id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            for (String c : commands) {
                srv.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
            }
        });
    }

    /** Stand in front of the serving window, {@code back} blocks out, {@code side} to the right. */
    private static void frontOfWindow(Minecraft mc, double back, double side) {
        TacoVendorEntity v = nearest(mc, TacoVendorEntity.class);
        if (v == null) return;
        Vec3 out = Vec3.directionFromRotation(0, v.getYRot());
        Vec3 right = new Vec3(-out.z, 0, out.x);
        Vec3 at = v.position().add(out.scale(back)).add(right.scale(side)).add(0, -1, 0);
        Vec3 look = v.position().add(0, 1.4, 0);
        double dx = look.x - at.x, dy = look.y - (at.y + 1.62), dz = look.z - at.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", at.x, at.y, at.z, yaw, pitch));
    }

    private static void lookAt(Minecraft mc, Entity e) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    @Nullable
    private static <T extends Entity> T nearest(Minecraft mc, Class<T> type) {
        return mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(80)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
    }

    static {
        if (ENABLED) PaponMod.LOG.info("[autoshot] on: screenshots go to run/screenshots/papon_*.png");
    }
}
