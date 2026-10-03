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
    private static final String MODE = System.getProperty("papon.autoshot", "");
    private static final boolean ENABLED = !MODE.isEmpty() && !MODE.equals("false");
    /** -Pautoshot=phase2: a boss right here, the new phase-one moves, then phase two start to finish. */
    private static final boolean PHASE2 = MODE.equals("phase2");
    /** -Pautoshot=drive: a boss right here sent to his van; logs the van's heading against where it really goes. */
    private static final boolean DRIVE = MODE.equals("drive");
    /** -Pautoshot=rework: every new attack of the rework, the QTEs (one lost, one won), then phase two's potato attacks. */
    private static final boolean REWORK = MODE.equals("rework");
    /** -Pautoshot=pose: the boss held still in his parry and his summon, front and side, then his echoes throwing. */
    private static final boolean POSE = MODE.equals("pose");
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
        if (PHASE2) {
            phaseTwo(mc);
            return;
        }
        if (DRIVE) {
            drive(mc);
            return;
        }
        if (POSE) {
            pose(mc);
            return;
        }
        if (REWORK) {
            rework(mc);
            return;
        }
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

    // ------------------------------------------------------------------ the van, watched

    private static Vec3 vanLast;

    private static void drive(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "time set 5000", "gamerule doDaylightCycle false",
                    "weather clear 100000", "gamerule doMobSpawning false", "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]",
                    "tp @s 4000 200 4000", "papon escort true");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 100 -> run("execute at @s rotated 0 0 run papon boss");
            case 260 -> run("papon bossstate TO_VAN");
            case 900 -> mc.stop();
            default -> {}
        }
        var van = nearest(mc, com.groid.papon.vehicle.CaravanEntity.class);
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (van == null || b == null) return;
        if (tick > 260) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            lookAt(mc, van);
        }
        Vec3 now = van.position();
        if (vanLast != null && b.getVehicle() == van) {
            Vec3 d = now.subtract(vanLast);
            if (d.horizontalDistance() > 0.05 && tick % 4 == 0) {
                double moving = Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90.0;
                PaponMod.LOG.info("[autoshot] van client yaw {} body {} moving {} off {} state {}", (int) van.getYRot(), (int) van.yBodyRot,
                        (int) moving, (int) Mth.wrapDegrees(moving - van.yBodyRot), b.getBossState());
            }
            if (tick % 20 == 0) shot(mc, "drive_" + tick);
        }
        vanLast = now;
    }

    // ------------------------------------------------------------------ phase two

    private static int p2Mark = -1;
    private static int lastState = -1;

    private static void phaseTwo(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "tp @s 4000 200 4000");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 90 -> run("kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "kill @e[type=papon:foil_potato]");
            case 100 -> run("execute at @s rotated 0 0 run papon boss");
            case 300 -> run("papon bossstate IDLE", "execute as @e[type=papon:papon_boss,limit=1,sort=nearest] at @s run tp @p ^ ^ ^3",
                    "papon bossstate GRAB");
            case 450 -> run("papon bossstate DASH_COMBO");
            case 520 -> run("papon bossstate PUNT_RUN");
            case 650 -> run("papon bossstate P2_KNEEL");
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) return;
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (s.ordinal() != lastState) {
            lastState = s.ordinal();
            PaponMod.LOG.info("[autoshot] boss -> {}", s);
        }
        if (!com.groid.papon.client.Cinematic.isActive()) lookAt(mc, b);
        if (s == BossState.DASH_COMBO && t == 9) once(mc, "p1_dash");
        if (s == BossState.DASH_COMBO && t == 38) once(mc, "p1_dash_spin");
        if (s == BossState.VAN_KICK && t == 9) once(mc, "p1_kick");
        if (s == BossState.VAN_KICK && t == 20) once(mc, "p1_kick_slide");
        if (s == BossState.GRAB_COMBO && t == 2) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (s == BossState.GRAB_COMBO && t == 28) once(mc, "p1_grab_torch");
        if (s == BossState.GRAB_COMBO && t == 60) once(mc, "p1_grab_salt");
        if (s == BossState.GRAB_COMBO && t == 82) {
            once(mc, "p1_grab_thrown");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (s == BossState.P2_KNEEL && t == 18) once(mc, "p2_01_kneel");
        if (s == BossState.P2_KNEEL && t == 44) once(mc, "p2_02_pov_blood");
        if (s == BossState.P2_KNEEL && t == 76) once(mc, "p2_03_pov_up");
        if (s == BossState.P2_TALK && t == 20) once(mc, "p2_04_enough");
        if (s == BossState.P2_SEASON && t == 30) once(mc, "p2_05_shaker");
        if (s == BossState.P2_SEASON && t == 50) once(mc, "p2_06_salt_aura");
        if (s == BossState.P2_SEASON && t == 84) once(mc, "p2_07_golden");
        if (s == BossState.P2_SCREAM && t == 10) once(mc, "p2_08_scream_in");
        if (s == BossState.P2_SCREAM && t == 30) once(mc, "p2_09_scream");
        if (s == BossState.P2_SCREAM && t == 50) once(mc, "p2_10_wings");
        if (s == BossState.P2_ARRIVE && t == 18) once(mc, "p2_11_arrive");
        if (s == BossState.P2_ARRIVE && t == 45) once(mc, "p2_12_dome");
        if (s == BossState.P2_ARRIVE && t == 49 && p2Mark < 0) p2Mark = tick;
        if (p2Mark > 0) {
            int k = tick - p2Mark;
            if (k == 40) once(mc, "p2_13_flying_foils");
            if (k == 60) run("papon bossstate P2_METEOR");
            if (s == BossState.P2_METEOR && t == 40) once(mc, "p2_14_meteor");
            if (k == 150) run("papon bossstate P2_DIVE");
            if (s == BossState.P2_DIVE && t == 10) once(mc, "p2_15_dive_mark");
            if (k == 240) run("papon bossstate VAN_GRAB");
            if (s == BossState.VAN_THROW && t == 26) once(mc, "p2_16_van_lift");
            if (s == BossState.VAN_THROW && t == 44) once(mc, "p2_17_van_thrown");
            if (k == 360) run("execute as @e[type=papon:papon_boss,limit=1,sort=nearest] at @s run tp @p ^ ^ ^3");
            if (k == 364) run("papon bossstate P2_BARRAGE");
            if (s == BossState.P2_BARRAGE && t == 20) once(mc, "p2_18_barrage");
            if (k == 440) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                shot(mc, "p2_19_inside_dome");
            }
            if (k == 460) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                run("kill @e[type=papon:papon_boss]");
            }
            if (k == 520) once(mc, "p2_20_back_down");
            if (k == 540) mc.stop();
        }
    }

    private static int reworkMark = -1;
    private static boolean qteWin;

    private static final String CLOSE = "execute as @e[type=papon:papon_boss,limit=1,sort=nearest] at @s run tp @p ^ ^ ^5 facing entity @s";
    private static final String MID = "execute as @e[type=papon:papon_boss,limit=1,sort=nearest] at @s run tp @p ^ ^ ^9 facing entity @s";

    private static void rework(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "tp @s 4000 200 4000");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 90 -> run("kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "kill @e[type=papon:foil_potato]");
            case 100 -> run("execute at @s rotated 0 0 run papon boss");
            case 260 -> run("papon bossstate IDLE", CLOSE, "papon bossstate GUARD");
            case 330 -> run(CLOSE, "papon bossstate CLEAVER_TOSS");
            case 420 -> run("papon bossstate IDLE", MID, "papon bossstate BURRITO_TOSS");
            case 530 -> run("papon bossstate IDLE", CLOSE, "papon bossstate COMAL_SLAM");
            case 610 -> run("papon bossstate IDLE", CLOSE, "papon bossstate DRUNK_SWIG");
            case 720 -> run("papon bossstate IDLE", MID, "papon bossstate HAYMAKER");
            case 800 -> run("papon bossstate IDLE", MID, "papon bossstate BRICK");
            case 1000 -> run("papon bossstate IDLE", "kill @e[type=papon:caravan]", CLOSE, "papon bossstate QTE_GRAB");
            case 1200 -> {
                qteWin = true;
                run("papon bossstate IDLE", "kill @e[type=papon:caravan]", CLOSE, "papon bossstate QTE_GRAB");
            }
            case 1420 -> run("papon bossstate IDLE", MID, "papon bossstate P2_KNEEL");
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) return;
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (s.ordinal() != lastState) {
            lastState = s.ordinal();
            PaponMod.LOG.info("[autoshot] boss -> {}", s);
        }
        if (!com.groid.papon.client.Cinematic.isActive()) lookAt(mc, b);
        if (s == BossState.QTE_HOLD && qteWin && t % 9 == 6) com.groid.papon.client.QteClient.devPress();
        String n = switch (s) {
            case GUARD -> t == 10 ? "r01_guard" : null;
            case RIPOSTE -> t == 11 ? "r02_riposte" : null;
            case CLEAVER_TOSS -> t == 12 ? "r03_cleavers" : t == 28 ? "r03b_echo_throws" : null;
            case BURRITO_TOSS -> t == 12 ? "r04_tortilla" : t == 22 ? "r05_burrito_mark" : null;
            case CHARGE_WINDUP -> t == 8 ? "r06_burrito_bowling" : null;
            case COMAL_SLAM -> t == 24 ? "r07_griddle" : null;
            case DRUNK_SWIG -> t == 8 ? "r08_swig" : null;
            case DRUNK_FLURRY -> t == 17 ? "r09_flurry" : t == 50 ? "r10_hammer" : null;
            case HAYMAKER -> t == 31 ? "r11_glint" : t == 37 ? "r12_haymaker" : null;
            case BRICK -> t == 16 ? "r13_brick" : null;
            case QTE_HOLD -> t == 12 ? (qteWin ? "r16_qte_win_hold" : "r14_qte_hold") : t == 30 ? (qteWin ? "r17_qte_win_keys" : "r14b_qte_keys") : null;
            case QTE_FAIL -> t == 25 ? "r15_qte_fail" : null;
            case QTE_BROKEN -> t == 20 ? "r18_qte_broken" : null;
            case P2_FRY_VOLLEY -> t == 22 ? "r20_fries" : t == 36 ? "r21_fries_fly" : null;
            case P2_MASH -> t == 26 ? "r22_mash" : t == 44 ? "r23_mash_wide" : null;
            case P2_TOT -> t == 34 ? "r24_tot" : null;
            case P2_SNARE -> t == 22 ? "r25_snare" : t == 32 ? "r26_sprouts" : null;
            case P2_GRAVITY -> t == 50 ? "r27_gravity" : t == 86 ? "r28_gravity_slam" : null;
            case P2_METEOR -> t == 40 ? "r29_meteors" : null;
            case GATLING -> t == 30 ? "r30_gatling" : null;
            default -> null;
        };
        if (n != null) once(mc, n);
        if (brickShot(mc, b)) once(mc, "r13b_van_bricked");
        if (s == BossState.BURRITO_TOSS && t == 24) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (s == BossState.CHARGE_WINDUP && t == 14) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (s == BossState.P2_ARRIVE && t == 49 && reworkMark < 0) reworkMark = tick;
        if (reworkMark > 0) {
            int k = tick - reworkMark;
            if (k == 30) run(MID, "papon bossstate P2_FRY_VOLLEY");
            if (k == 120) run(MID, "papon bossstate P2_MASH");
            if (k == 200) run(MID, "papon bossstate P2_TOT");
            if (k == 270) run(MID, "papon bossstate P2_SNARE");
            if (k == 340) run(MID, "papon bossstate P2_GRAVITY");
            if (k == 460) run(MID, "papon bossstate P2_METEOR");
            if (k == 560) run(MID, "papon bossstate GATLING");
            if (k == 660) mc.stop();
        }
    }

    private static final String SIDE = "execute as @e[type=papon:papon_boss,limit=1,sort=nearest] at @s run tp @p ^4.5 ^0.5 ^1.5 facing entity @s";

    private static void pose(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "tp @s 4000 200 4000");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 90 -> run("kill @e[type=papon:papon_boss]", "execute at @s rotated 0 0 run papon boss", "kill @e[type=papon:caravan]");
            case 400 -> run("papon bossstate IDLE", CLOSE, "papon bossstate GUARD");
            case 402 -> PaponBossEntity.devFrozen = true;
            case 414 -> shot(mc, "p01_guard_front");
            case 416 -> run(SIDE);
            case 424 -> shot(mc, "p02_guard_side");
            case 430 -> PaponBossEntity.devFrozen = false;
            case 432 -> run("papon bossstate IDLE", CLOSE, "papon bossstate CLEAVER_TOSS");
            case 434 -> PaponBossEntity.devFrozen = true;
            case 442 -> shot(mc, "p03_summon_front");
            case 444 -> run(SIDE);
            case 450 -> shot(mc, "p04_summon_side");
            case 460 -> {
                PaponBossEntity.devFrozen = false;
                run("papon bossstate IDLE", MID);
            }
            case 462 -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                run("papon bossstate CLEAVER_TOSS");
            }
            case 476 -> shot(mc, "p05_echoes");
            case 484 -> shot(mc, "p06_echo_throw");
            case 492 -> shot(mc, "p07_echo_throw2");
            case 520 -> mc.stop();
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b != null && tick < 462 && tick > 100 && !com.groid.papon.client.Cinematic.isActive()) lookAt(mc, b);
    }

    private static boolean brickShot(Minecraft mc, PaponBossEntity b) {
        var van = nearest(mc, com.groid.papon.vehicle.CaravanEntity.class);
        return van != null && van.isEngineOn() && !van.isVehicle() && b.getBossState() == BossState.IDLE && tick > 820 && tick < 990
                && tick % 30 == 0 && taken.size() < 200 && !taken.contains("r13b_van_bricked") && tick > 900;
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
