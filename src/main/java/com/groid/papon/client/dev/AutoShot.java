package com.groid.papon.client.dev;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.BossState;
import com.groid.papon.boss.Coliseum;
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
    /** -Pautoshot=fight: he fights a player who just stands there, on his own AI, for three minutes; every state is logged. */
    private static final boolean FIGHT = MODE.equals("fight");
    /** -Pautoshot=coliseum: phase two from the kneel: the coliseum, the band asleep, woken, the count, the song, the fight, the crowd. */
    private static final boolean COLISEUM = MODE.equals("coliseum");
    /** -Pautoshot=voice: phase two's cutscene for his voice: shots of his face on every line, each line's start and end logged against its state. */
    private static final boolean VOICE = MODE.equals("voice");
    /** -Pautoshot=arena: into the coliseum, then the arena attacks one by one (meteor, fryer, masher, heckle, salsa burst), containment and the portal checked. */
    private static final boolean ARENA = MODE.equals("arena");
    /** -Pautoshot=phase3: phase two into the coliseum and the song, then half health: phase three's cutscene beat by beat, and the night fight. */
    private static final boolean PHASE3 = MODE.equals("phase3");
    /** -Pautoshot=nights: into the coliseum, then the flags, the crowd up close, the potato sun, nightfall (the lamplighter), the potato moon. */
    private static final boolean NIGHTS = MODE.equals("nights");
    /** -Pautoshot=ending: a truck, the reveal, his fall, Gerbin's pillow, back to the truck: the bald chef's wave and menu; the pillow hugged, licked, in bed. */
    private static final boolean ENDING = MODE.equals("ending");
    /** -Pautoshot=pillow: a bed against a wall; the pillow hugged, licked and humped (third and first person), slouched on the bed, humped there. */
    private static final boolean PILLOW = MODE.equals("pillow");
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
        if (FIGHT) {
            fight(mc);
            return;
        }
        if (ENDING) {
            ending(mc);
            return;
        }
        if (PILLOW) {
            pillow(mc);
            return;
        }
        if (COLISEUM || VOICE || ARENA || NIGHTS || PHASE3) {
            coliseum(mc);
            if (VOICE) voice(mc);
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
        if (s == BossState.P2_KNEEL && t == 84) once(mc, "p2_03_pov_hand");
        if (s == BossState.P2_KNEEL && t == 116) once(mc, "p2_03b_pov_up");
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

    private static int colMark = -1;

    private static void coliseum(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "kill @e[type=papon:mariachi]",
                    "kill @e[type=papon:coliseum_crowd]", "tp @s 4000 200 4000");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 100 -> run("execute at @s rotated 0 0 run papon boss", "execute at @s run summon papon:gerbin ~4 ~ ~-3");
            case 220 -> run("papon bossstate IDLE", MID, "papon bossstate P2_KNEEL");
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) {
            if (colMark > 0 && tick - colMark > 760) gerbinShots(mc, tick - colMark);
            return;
        }
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (s.ordinal() != lastState) {
            lastState = s.ordinal();
            PaponMod.LOG.info("[autoshot] boss -> {} (fps {})", s, mc.getFps());
        }
        if (!com.groid.papon.client.Cinematic.isActive() && colMark < 0) lookAt(mc, b);
        String n = switch (s) {
            case P2_SCREAM -> t == 60 ? "c00_scream_white" : null;
            case P2_ARRIVE -> t == 12 ? "c01_crane_high" : t == 30 ? "c02_crane_down" : t == 52 ? "c03_landed_crowd" : t == 88 ? "c04_musica" : null;
            case P2_NOTICE -> t == 12 ? "c05_band_asleep" : t == 40 ? "c06_maestro" : null;
            case P2_MARCH -> t == 20 ? "c07_march" : t == 45 ? "c07b_march" : null;
            case P2_WAKE -> t == 8 ? "c08_windup" : t == 15 ? "c08b_whack" : t == 20 ? "c09_singer_up" : t == 27 ? "c09b_singer_top" : t == 42 ? "c10_band_wakes" : t == 62 ? "c10b_satisfied" : null;
            case P2_WAIT -> t == 30 ? "c11_wait" : t == 110 ? "c12_band_dressing" : t == 190 ? "c13_band_dressing2" : t == 250 ? "c13b_wait" : null;
            case P2_KICK -> t == 13 ? "c14_kick" : null;
            case P2_COUNT -> t == 6 ? "c15_count_uno" : t == 44 ? "c16_count_tres" : t == 76 ? "c17_downbeat" : null;
            case P2_SHOWTIME -> t == 8 ? "c18_guitar_close" : t == 40 ? "c19_guitar_tilt" : t == 66 ? "c20_singer_aura" : null;
            case P2_APOLOGY -> t == 30 ? "c21_walk_over" : t == 80 ? "c22_apology" : t == 135 ? "c23_aura_farm" : t == 172 ? "c24_aura_farm2" : null;
            case P2_RESUME -> t == 10 ? "c25_where_were_we" : null;
            case P2_HYPE -> t == 14 ? "c26_hype_ear" : t == 30 ? "c26b_hype_roar" : t == 60 ? "c27_hype_turn" : t == 72 ? "c27b_mas_fuerte" : t == 92 ? "c28_raise_roof"
                    : t == 112 ? "c28b_raise_roof" : t == 132 ? "c29_tu" : t == 151 ? "c29b_erupt" : null;
            case P2_SOLO -> t == 10 ? "s1_solo_hands" : t == 36 ? "s2_solo_face" : t == 52 ? "s3_solo_reverse" : null;
            default -> null;
        };
        if (n != null) once(mc, n);
        if (s == BossState.IDLE && b.isPhaseTwo() && colMark < 0 && taken.contains("c25_where_were_we")) colMark = tick;
        if (colMark > 0 && ARENA) {
            arenaTour(mc, b, tick - colMark);
            return;
        }
        if (colMark > 0 && NIGHTS) {
            nightsTour(mc, b, tick - colMark);
            return;
        }
        if (colMark > 0 && PHASE3) {
            phaseThreeTour(mc, b, tick - colMark);
            return;
        }
        // the band, member by member, while they play (from the front, a few blocks off)
        if (colMark > 0) bandShots(mc, b, tick - colMark);
        if (colMark > 0) {
            int k = tick - colMark;
            // the band playing, the singer up close, the stands, the fight, from outside
            var singer = mc.level.getEntitiesOfClass(com.groid.papon.boss.MariachiEntity.class, b.getBoundingBox().inflate(80),
                    m -> m.getRole().singer()).stream().findFirst().orElse(null);
            if (k == 5) PaponBossEntity.devFrozen = true;
            if (k == 8) run("gamemode spectator");
            if (singer != null && k == 10) {
                // eye level with his mouth, a little to his left, so the mic is not in the way
                Vec3 f = Vec3.directionFromRotation(0, singer.getYRot());
                Vec3 side = new Vec3(-f.z, 0, f.x);
                Vec3 mouth = singer.position().add(0, singer.getBbHeight() * 0.6, 0);
                tpLook(mouth.add(f.scale(2.4)).add(side.scale(0.8)).add(0, -1.5, 0), mouth);
            }
            if (k >= 20 && k <= 80 && k % 6 == 2) shot(mc, "c3" + String.format("%02d", k) + "_singing");
            if (k == 90) run("tp @s ~ ~ ~ 0 0");
            if (singer != null && k == 92) {
                Vec3 f = Vec3.directionFromRotation(0, singer.getYRot());
                tpLook(singer.position().add(f.scale(10)).add(0, 2, 0), singer.position().add(0, 1.2, 0));
            }
            if (k == 100) shot(mc, "c40_band_playing");
            if (k == 104) PaponMod.LOG.info("[autoshot] band fps {}", mc.getFps());
            if (k == 110) tpLook(b.position().add(0, 1, 6), b.position().add(18, 9, -10));
            if (k == 120) shot(mc, "c41_stands_east");
            if (k == 122) tpLook(b.position().add(0, 1, 6), b.position().add(-18, 9, 16));
            if (k == 132) shot(mc, "c42_stands_south");
            if (k == 134) PaponMod.LOG.info("[autoshot] stands fps {}", mc.getFps());
            if (VOICE && k >= 136 && k <= 166) {
                // eye calibration: from in front of him, off to his left, then his right; his eyes should follow
                Vec3 fwd = Vec3.directionFromRotation(0, b.yBodyRot);
                Vec3 left = new Vec3(fwd.z * -1, 0, fwd.x).scale(-1);       // MC: facing +z, +x is on your left
                Vec3 face = b.position().add(0, b.getBbHeight() * 0.72, 0);
                if (k == 136) tpLook(face.add(fwd.scale(3.2)).add(left.scale(2.6)).add(0, -1.6, 0), face);
                if (k == 148) shot(mc, "v_gaze_his_left");
                if (k == 150) tpLook(face.add(fwd.scale(3.2)).add(left.scale(-2.6)).add(0, -1.6, 0), face);
                if (k == 162) shot(mc, "v_gaze_his_right");
            }
            if (k == 470) run("gamemode spectator");
            if (k == 474) tpLook(b.position().add(0, 50, 75), b.position());
            if (k == 490) shot(mc, "c45_outside_high");
            if (k == 492) tpLook(b.position().add(0, 3, 15), b.position().add(0, 2, -30));
            if (k == 506) shot(mc, "c47_whole_bowl");
            if (k == 508) {
                PaponBossEntity.devFrozen = false;
                run("gamemode survival");
                // on the sand by the stands: the fans let fly
                tpLook(b.position().add(Coliseum.A - 3, 0.2, 0), b.position().add(Coliseum.A + 6, 4, 0));
            }
            if (k == 530) shot(mc, "p1_pelted");
            if (k == 546) shot(mc, "p2_pelted");
            if (k == 562) shot(mc, "p3_pelted");
            if (k == 566) tpLook(b.position().add(0, 1, 8), b.position().add(0, 2, 0));
            if (k == 600) run("papon bossstate P2_SOLO");
            if (k == 700) shot(mc, "c43_fight");
            if (k == 720) run("kill @e[type=papon:papon_boss]");
            if (k == 780) once(mc, "c48_his_fall");
            if (k == 860) once(mc, "c49_back_down");
        }
    }

    /**
     * Band close-ups while they play (devFrozen keeps him out of the way): the coro (they come in
     * with the harmonies about 8.6 s into the first verse), the trumpets (their part from 11.5 s),
     * the violins; each from in front of the member, at his eye level.
     */
    @Nullable private static net.minecraft.core.BlockPos bedAt;

    private static void pillow(Minecraft mc) {
        var opts = mc.options;
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 6000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:papon_boss]", "kill @e[type=papon:body_pillow]", "tp @s 4100 200 4100");
            case 70 -> run("execute positioned 4100 0 4100 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 180 0");
            case 90 -> {
                var p = mc.player.blockPosition();
                bedAt = p.north(3);
                run(String.format("fill %d %d %d %d %d %d minecraft:smooth_stone", p.getX() - 4, p.getY() - 1, p.getZ() - 6, p.getX() + 4, p.getY() - 1, p.getZ() + 4),
                        String.format("fill %d %d %d %d %d %d minecraft:air", p.getX() - 4, p.getY(), p.getZ() - 6, p.getX() + 4, p.getY() + 4, p.getZ() + 4),
                        String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", p.getX() - 2, p.getY(), p.getZ() - 5, p.getX() + 2, p.getY() + 3, p.getZ() - 5),
                        String.format("setblock %d %d %d minecraft:red_bed[facing=north,part=head]", bedAt.getX(), bedAt.getY(), bedAt.getZ() - 1),
                        String.format("setblock %d %d %d minecraft:red_bed[facing=north,part=foot]", bedAt.getX(), bedAt.getY(), bedAt.getZ()),
                        "item replace entity @s weapon.mainhand with papon:body_pillow");
            }
            // third person, from the front: hug, lick, hump
            case 120 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_FRONT);
                run("tp @s ~ ~ ~ 0 0");
            }
            case 125 -> net.minecraft.client.KeyMapping.set(opts.keyUse.getKey(), true);
            case 150 -> shot(mc, "pl01_hug_front");
            case 160 -> opts.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 170 -> shot(mc, "pl02_hug_back");
            case 175 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_FRONT);
                net.minecraft.client.KeyMapping.set(opts.keyShift.getKey(), true);
            }
            case 195 -> shot(mc, "pl03_lick");
            case 200 -> net.minecraft.client.KeyMapping.set(opts.keyShift.getKey(), false);
            case 205 -> run("tp @s ~ ~ ~ 0 70");
            case 225 -> shot(mc, "pl04_hump_front");
            case 228 -> shot(mc, "pl05_hump_front2");
            case 232 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_BACK);
                run("tp @s ~ ~ ~ 0 -10");
            }
            case 236 -> run("tp @s ~ ~ ~ 0 70");
            case 250 -> shot(mc, "pl06_hump_back");
            // first person
            case 255 -> {
                opts.setCameraType(CameraType.FIRST_PERSON);
                run("tp @s ~ ~ ~ 0 70");
            }
            case 270 -> shot(mc, "pl07_hump_fp");
            case 275 -> run("tp @s ~ ~ ~ 0 0");
            case 290 -> shot(mc, "pl08_hug_fp");
            case 295 -> net.minecraft.client.KeyMapping.set(opts.keyShift.getKey(), true);
            case 310 -> shot(mc, "pl09_lick_fp");
            case 315 -> {
                net.minecraft.client.KeyMapping.set(opts.keyShift.getKey(), false);
                net.minecraft.client.KeyMapping.set(opts.keyUse.getKey(), false);
            }
            case 320 -> shot(mc, "pl10_carry_fp");
            // on the bed: slouched against the wall
            case 330 -> {
                var srv = mc.getSingleplayerServer();
                var pos = bedAt;
                if (srv != null && pos != null) srv.execute(() -> com.groid.papon.entity.BodyPillowEntity.place(srv.overworld(), pos));
                opts.setCameraType(CameraType.FIRST_PERSON);
            }
            case 340 -> {
                var p = bedAt;
                if (p != null) tpLook(Vec3.atBottomCenterOf(p).add(1.6, 0.4, 2.6), Vec3.atBottomCenterOf(p).add(0, 0.9, -1.2));
            }
            case 355 -> shot(mc, "pl11_bed_slouch");
            case 357 -> {
                var p = bedAt;
                if (p != null) tpLook(Vec3.atBottomCenterOf(p).add(-2.4, 0.6, 0.4), Vec3.atBottomCenterOf(p).add(0, 0.9, -1.2));
            }
            case 370 -> shot(mc, "pl12_bed_slouch_side");
            case 375 -> {
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) mc.gameMode.interact(mc.player, pillow, net.minecraft.world.InteractionHand.MAIN_HAND);
                opts.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 395 -> shot(mc, "pl13_bed_hump_back");
            case 400 -> opts.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 415 -> shot(mc, "pl14_bed_hump_front");
            case 425 -> mc.stop();
            default -> {}
        }
    }

    /** After his fall (the boss is gone): Gerbin brings the pillow over. */
    private static void gerbinShots(Minecraft mc, int k) {
            // Gerbin comes over with the pillow
            var g = nearest(mc, com.groid.papon.entity.npc.GerbinEntity.class);
            if (g != null) {
                lookAt(mc, g);
                var gs = g.getGerbinState();
                // (the client does not count an NPC's state ticks: count frames of each state here)
                gerbinSeen = gs == gerbinLast ? gerbinSeen + 1 : 0;
                gerbinLast = gs;
                if (gs == com.groid.papon.entity.npc.GerbinState.DELIVER && gerbinSeen == 30) once(mc, "g1_gerbin_carry");
                if (gs == com.groid.papon.entity.npc.GerbinState.GIVE && gerbinSeen == 16) once(mc, "g2_gerbin_give");
                if (gs == com.groid.papon.entity.npc.GerbinState.GIVE && gerbinSeen == 40) once(mc, "g3_gerbin_given");
            }
            if (taken.contains("g3_gerbin_given") && doneAt < 0) doneAt = k;
            if (doneAt > 0 && k == doneAt + 20) {
                PaponMod.LOG.info("[autoshot] pillows held: {}", mc.player.getInventory().countItem(com.groid.papon.registry.ModItems.BODY_PILLOW.get()));
                run("item replace entity @s weapon.mainhand with papon:body_pillow", "effect give @s papon:splattered 5 0");
            }
            if (doneAt > 0 && k == doneAt + 40) once(mc, "g4_pillow_in_hand");
            if (doneAt > 0 && k == doneAt + 50 || k > 2400) mc.stop();
    }

    private static int gerbinSeen;
    @Nullable private static com.groid.papon.entity.npc.GerbinState gerbinLast;

    private static void bandShots(Minecraft mc, PaponBossEntity b, int k) {
        java.util.List<com.groid.papon.boss.MariachiEntity> band = mc.level.getEntitiesOfClass(com.groid.papon.boss.MariachiEntity.class,
                b.getBoundingBox().inflate(80), m -> true);
        java.util.function.Function<com.groid.papon.boss.BandRole, com.groid.papon.boss.MariachiEntity> who =
                r -> band.stream().filter(m -> m.getRole() == r).findFirst().orElse(null);
        String[] names = {"b1_coro", "b2_trumpets", "b3_violins", "b4_coro_wide"};
        com.groid.papon.boss.BandRole[] roles = {com.groid.papon.boss.BandRole.CORO_2, com.groid.papon.boss.BandRole.TRUMPET_2,
                com.groid.papon.boss.BandRole.VIOLIN_2, com.groid.papon.boss.BandRole.CORO_1};
        int[] at = {176, 250, 330, 410};
        double[] back = {4.8, 7.0, 6.2, 8.5};
        for (int i = 0; i < at.length; i++) {
            var m = who.apply(roles[i]);
            if (m == null) continue;
            Vec3 f = Vec3.directionFromRotation(0, m.getYRot());
            Vec3 side = new Vec3(-f.z, 0, f.x);
            Vec3 eye = m.position().add(0, m.getBbHeight() * 0.62, 0);
            if (k == at[i]) tpLook(eye.add(f.scale(back[i])).add(side.scale(i == 2 ? -2.4 : 0.8)).add(0, i == 1 ? 1.6 : -0.9, 0), eye);
            if (k == at[i] + 8 || k == at[i] + 20 || k == at[i] + 32) shot(mc, names[i] + "_" + (k - at[i]));
        }
    }

    /** The arena attacks, each forced in turn and shot from over the sand. */
    private static void arenaTour(Minecraft mc, PaponBossEntity b, int k) {
        BossState s = b.getBossState();
        int t = b.ticksInState();
        Vec3 c = b.position();
        if (k == 2) {
            run("gamemode creative");          // a spectator is nobody to him: the coliseum would come down
            PaponMod.LOG.info("[arena] portals: {}", mc.level.getEntitiesOfClass(com.groid.papon.boss.ArenaPortalEntity.class,
                    mc.player.getBoundingBox().inflate(512)).size());
            run("execute at @e[type=papon:papon_boss,limit=1] run summon minecraft:phantom ~ ~6 ~", "execute at @e[type=papon:papon_boss,limit=1] run summon minecraft:zombie ~4 ~ ~4");
        }
        if (k == 30) PaponMod.LOG.info("[arena] foreign mobs left near the boss: {}", mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                b.getBoundingBox().inflate(40)).size());
        if (k == 32 || k == 312) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            tpLook(c.add(0, 16, 30), c.add(0, 8, 0));
        }
        if (k == 40) run("papon bossstate P2_GRAVITY");
        if (s == BossState.P2_GRAVITY) {
            if (t == 30) shot(mc, "a01_meteor_swell");
            if (t == 100) shot(mc, "a02_meteor_looms");
            if (t == 145) shot(mc, "a03_meteor_falls");
            if (t == 151) shot(mc, "a04_meteor_impact");
            if (t == 162) shot(mc, "a05_meteor_rings");
        }
        if (k == 240) run("papon bossstate ARENA_FRYER");
        if (s == BossState.ARENA_FRYER && t == 20) shot(mc, "a06_fryer_warn");
        if (k == 300) shot(mc, "a07_fryer_oil");
        if (k == 380) shot(mc, "a07c_fryer_still_oil");
        if (k == 304) {
            tpLook(c.add(3, 3, 6), c.add(0, 0, -6));
            shot(mc, "a07b_fryer_low");
        }
        if (k == 420) run("papon bossstate P2_MASHER");
        if (s == BossState.P2_MASHER) {
            if (t == 40) shot(mc, "a08_masher_looms");
            if (t == 50) shot(mc, "a09_masher_slam1");
            if (t == 70) shot(mc, "a09b_masher_moved_holes");
            if (t == 82) shot(mc, "a10_masher_slam2");
            if (t == 112) shot(mc, "a10b_masher_slam3");
        }
        if (k == 556) {
            run("gamemode survival");
            run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ~ ~ ~8");
        }
        if (k == 560) run("papon bossstate ARENA_HECKLE");
        if (s == BossState.ARENA_HECKLE && t == 30) shot(mc, "a11_heckle");
        if (k == 610) shot(mc, "a12_heckle_rain");
        if (k == 640) {
            run("gamemode survival");
            run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ^ ^ ^3");
        }
        if (k == 646) run("papon bossstate SALSA_BURST");
        if (s == BossState.SALSA_BURST && t == 8) {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            shot(mc, "a13_salsa_charge");
        }
        if (s == BossState.SALSA_BURST && t == 12) shot(mc, "a14_salsa_burst");
        if (k == 700) {
            // on the sand (a creative player does not count as anyone in it: the coliseum would come down)
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            run("gamemode survival", "effect give @s minecraft:resistance 600 4 true");
            tpLook(c.add(4, 0, 17), c.add(0, 0, -4));
        }
        if (k == 760 || k == 790 || k == 820 || k == 920 || k == 950) tpLook(c.add(4, 0, 17), c.add(0, 0, -4));
        if (k == 706) run("papon bossstate ARENA_OLA");
        if (s == BossState.ARENA_OLA && t == 30) shot(mc, "a15_ola_cast");
        if (k == 750) shot(mc, "a16_ola_warn");
        if (k == 770) shot(mc, "a17_ola_wave1");
        if (k == 820) shot(mc, "a18_ola_wave2");
        if (k == 900) run("papon bossstate ARENA_STAMPEDE");
        if (s == BossState.ARENA_STAMPEDE && t == 20) shot(mc, "a19_stampede_cast");
        if (k == 930) shot(mc, "a20_stampede_warn");
        if (k == 950) shot(mc, "a21_stampede_roll");
        if (k == 985) shot(mc, "a22_stampede_volley2");
        if (k == 1040) run("kill @e[type=papon:papon_boss]");
        if (k == 1100) PaponMod.LOG.info("[arena] portals after his fall: {}", mc.level.getEntitiesOfClass(com.groid.papon.boss.ArenaPortalEntity.class,
                mc.player.getBoundingBox().inflate(512)).size());
        if (k == 1120) mc.stop();
    }

    private static int lampSeen;
    @Nullable private static com.groid.papon.boss.BandState lampLast;

    /** The coliseum's new looks: the flags, the crowd up close, the potato sun, nightfall and the lamplighter, the moon. */
    private static void nightsTour(Minecraft mc, PaponBossEntity b, int k) {
        Vec3 c = b.position();
        if (k == 2) {
            PaponBossEntity.devFrozen = true;
            run("gamemode creative");          // a spectator is nobody to him: the coliseum would come down
        }
        if (k == 6) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        if (k == 10) tpLook(c.add(4, 2.5, 6), c.add(-6, 3.5, 21));
        if (k == 22) shot(mc, "n01_podium_flags");
        if (k == 24) tpLook(c.add(0, 3.5, -7), c.add(0, 3, -20));
        if (k == 36) shot(mc, "n02_backdrop_band");
        if (k == 40) tpLook(c.add(6, 6.5, 14), c.add(8, 10, 26));
        if (k == 52) shot(mc, "n03_crowd_flags");
        if (k == 60) shot(mc, "n04_crowd_flags");
        if (k == 68) shot(mc, "n05_crowd_flags");
        if (k == 72) tpLook(c.add(0, 7, -6), c.add(-22, 10, 18));
        if (k == 84) shot(mc, "n06_crowd_wide");
        if (k == 90) run("time set 6000");
        if (k == 92) tpLook(c.add(0, 2, 0), c.add(0.5, 60, 2));
        if (k == 104) shot(mc, "n07_potato_sun");
        if (k == 108) {
            run("time set 13600");
            tpLook(c.add(1, 4, -9), c.add(7, 2.5, -19));
        }
        var lamplighter = mc.level.getEntitiesOfClass(com.groid.papon.boss.MariachiEntity.class, b.getBoundingBox().inflate(80),
                m -> m.getRole().lamplighter()).stream().findFirst().orElse(null);
        if (lamplighter != null && k > 108) {
            var st = lamplighter.getBandState();
            lampSeen = st == lampLast ? lampSeen + 1 : 0;
            lampLast = st;
            if (st == com.groid.papon.boss.BandState.LAMP_GO && lampSeen == 8) once(mc, "n08_lamp_walk");
            if (st == com.groid.papon.boss.BandState.LAMP_SWITCH && lampSeen == 14) once(mc, "n09_lamp_reach");
            if (st == com.groid.papon.boss.BandState.LAMP_SWITCH && lampSeen == 24) once(mc, "n10_lamp_on");
            if (st == com.groid.papon.boss.BandState.LAMP_BACK && lampSeen == 10) once(mc, "n11_lamp_back");
        }
        if (k == 300) tpLook(c.add(0, 3, 4), c.add(0, 2.5, -19));
        if (k == 312) shot(mc, "n12_stage_lit");
        if (k == 316) {
            run("time set 18000");
            tpLook(c.add(0, 2, 0), c.add(0.5, 60, 2));
        }
        if (k == 330) shot(mc, "n13_potato_moon");
        if (k == 334) {
            run("time set 14000");
            tpLook(c.add(0, 40, 62), c.add(0, 2, -6));
        }
        if (k == 350) shot(mc, "n14_night_bowl");
        if (k == 352) PaponMod.LOG.info("[autoshot] lamps: {}", lampLast);
        if (k == 360) mc.stop();
    }

    private static int endMark = -1, endSeen;
    @Nullable private static Object endLast;
    private static boolean bossKilled;

    /** A truck and its boss, beaten: Gerbin's present, the welcome back, the bald chef's menu; then the pillow. */
    private static void ending(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "papon escort true", "tp @s 4000 200 4000", "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]",
                    "kill @e[type=papon:taco_vendor]", "kill @e[type=papon:body_pillow]");
            case 100 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves rotated 0 0 run papon tacoline");
            case 160 -> frontOfWindow(mc, 5.5, 0);
            case 200 -> run("papon reveal");
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b != null && !bossKilled && b.getBossState().fighting() && b.ticksInState() > 10) {
            bossKilled = true;
            run("kill @e[type=papon:papon_boss]");
        }
        if (b != null && b.getBossState() == BossState.DEATH) lookAt(mc, b);
        if (!bossKilled || b != null) return;
        if (endMark < 0) endMark = tick;
        int k = tick - endMark;
        GerbinEntity g = nearest(mc, GerbinEntity.class);
        TacoVendorEntity v = mc.level.getEntitiesOfClass(TacoVendorEntity.class, mc.player.getBoundingBox().inflate(200), TacoVendorEntity::isFriendly)
                .stream().findFirst().orElse(null);
        if (k == 2) PaponMod.LOG.info("[autoshot] reformed chef: {}", v != null);
        if (g != null && k < 400 && !taken.contains("e02_given")) {
            lookAt(mc, g);
            var gs = g.getGerbinState();
            endSeen = gs == endLast ? endSeen + 1 : 0;
            endLast = gs;
            if (gs == com.groid.papon.entity.npc.GerbinState.DELIVER && endSeen == 20) once(mc, "e01_gerbin_brings");
            if (gs == com.groid.papon.entity.npc.GerbinState.GIVE && endSeen == 30) once(mc, "e02_given");
        }
        if (v != null && taken.contains("e02_given")) {
            var vs = v.getVendorState();
            if (vs == VendorState.GREETING && !taken.contains("e04_wave")) {
                if (endLast != vs) {
                    endLast = vs;
                    endSeen = 0;
                    endMark2 = k;
                }
                endSeen++;
                if (endSeen == 2) PaponMod.LOG.info("[autoshot] welcomed back at {} (vendor at {})", mc.player.blockPosition(), v.blockPosition());
                if (endSeen == 18) once(mc, "e04_wave");
            }
            // brought back: the moment they land at the window, the screen still coming up out of black
            if (arrived < 0 && mc.player.distanceToSqr(v) < 8 * 8) arrived = k;
            if (arrived > 0 && k == arrived + 20) once(mc, "e03_fade_in");
        }
        if (endMark2 > 0) {
            int j = k - endMark2;
            if (j % 20 == 0 && j <= 360) PaponMod.LOG.info("[autoshot] j {} screen {}", j, mc.screen);
            if (j == 70) frontOfWindow(mc, 3.2, 0);
            if (j == 84) shot(mc, "e05_bald_chef");
            if (j == 90 && v != null) com.groid.papon.client.ClientHooks.openTacoMenu(v.getId());
            if (j == 104) shot(mc, "e06_menu");
            if (j == 108 && v != null) {
                com.groid.papon.network.PaponNetwork.orderTaco(v.getId(), 3);
                mc.setScreen(null);
            }
            if (j == 150) shot(mc, "e07_cooking");
            if (j == 200) PaponMod.LOG.info("[autoshot] tacos de papa held: {}", mc.player.getInventory().countItem(com.groid.papon.registry.ModItems.TACO_PAPA.get()));
            if (j == 204) {
                run("item replace entity @s weapon.mainhand with papon:body_pillow", "tp @s ~ ~ ~ 0 0");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if (j == 214) shot(mc, "e08_pillow_carried");
            if (j == 216) mc.options.keyUse.setDown(true);
            if (j == 250) shot(mc, "e09_hug");
            if (j == 262) shot(mc, "e10_hug");
            if (j == 266) mc.options.keyShift.setDown(true);
            if (j == 296) shot(mc, "e11_lick");
            if (j == 301) shot(mc, "e12_lick");
            if (j == 306) {
                mc.options.keyShift.setDown(false);
                mc.options.keyUse.setDown(false);
            }
            if (j == 310) mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (j == 312) mc.options.keyUse.setDown(true);
            if (j == 340) shot(mc, "e13_hug_first_person");
            if (j == 342) mc.options.keyUse.setDown(false);
            if (j == 344) run("time set 14000");
            if (j == 346) bedAndPillow(mc);
            if (j == 356) bedCamera(mc, 0);
            if (j == 372) shot(mc, "e14_pillow_on_bed");
            if (j == 376) sleepBeside(mc);
            if (j == 396) bedCamera(mc, 0);
            if (j == 410) shot(mc, "e15_asleep_beside");
            if (j == 412) bedCamera(mc, 1);
            if (j == 424) shot(mc, "e16_asleep_beside_feet");
            if (j == 426) bedCamera(mc, 2);
            if (j == 438) shot(mc, "e17_asleep_beside_side");
            if (j == 440) mc.setCameraEntity(mc.player);
            if (j == 450) mc.stop();
        }
        if (k > 2400) mc.stop();
    }

    private static int endMark2 = -1, arrived = -1;

    /** A bed beside the player, a pillow laid on it (as if used on it). */
    private static void bedAndPillow(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            var level = sp.serverLevel();
            net.minecraft.core.BlockPos foot = sp.blockPosition().offset(3, 0, 0);
            net.minecraft.core.BlockPos head = foot.north();
            var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH);
            level.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
            level.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
            var pillow = com.groid.papon.entity.BodyPillowEntity.place(level, foot);
            PaponMod.LOG.info("[autoshot] pillow on the bed: {}", pillow != null);
            sp.teleportTo(level, foot.getX() + 0.5, foot.getY(), foot.getZ() + 2.5, 180, 20);
        });
    }

    @Nullable private static net.minecraft.world.entity.decoration.ArmorStand bedCam;

    /** A client-only camera over the bed: 0 above and to the side, 1 from the foot end, 2 from the side, low. */
    private static void bedCamera(Minecraft mc, int view) {
        var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
        if (pillow == null) return;
        if (bedCam == null) {
            bedCam = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
            bedCam.setInvisible(true);
        }
        Vec3 c = pillow.position();
        Vec3 at = switch (view) {
            case 1 -> c.add(0, 1.4, 3.2);
            case 2 -> c.add(-3.0, 1.0, -0.2);
            default -> c.add(2.2, 2.6, 1.6);
        };
        Vec3 d = c.add(0, 0.2, 0).subtract(at.add(0, bedCam.getEyeHeight(), 0));
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        bedCam.moveTo(at.x, at.y, at.z, yaw, pitch);
        bedCam.xo = at.x;
        bedCam.yo = at.y;
        bedCam.zo = at.z;
        bedCam.yRotO = yaw;
        bedCam.xRotO = pitch;
        bedCam.setYHeadRot(yaw);
        bedCam.yHeadRotO = yaw;
        mc.setCameraEntity(bedCam);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    private static void sleepBeside(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            var head = sp.blockPosition().offset(0, 0, -3);
            sp.startSleeping(head);
            PaponMod.LOG.info("[autoshot] asleep: {} at {}", sp.isSleeping(), head);
        });
    }

    /** Phase three: knocked down at half health, the cutscene shot by shot (through the cinematic camera), then the night fight. */
    private static void phaseThreeTour(Minecraft mc, PaponBossEntity b, int k) {
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (k == 2) {
            run("gamemode survival");
            run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ~ ~ ~7");
        }
        if (k == 60) run("papon bossstate P3_KNOCKBACK");
        if (k > 60 && !com.groid.papon.client.Cinematic.isActive() && !s.phaseThree()) lookAt(mc, b);
        String n = switch (s) {
            case P3_KNOCKBACK -> t == 8 ? "t01_flung" : t == 15 ? "t02_impact" : t == 26 ? "t03_down" : t == 44 ? "t04_rising" : null;
            case P3_GLARE -> t == 10 ? "t05_wipe" : t == 36 ? "t06_eyes" : t == 66 ? "t07_singer_sweats" : t == 102 ? "t08_not_my_style"
                    : t == com.groid.papon.boss.VoiceLines.beat("glare.realplay", 100) + 30 ? "t09_really_play" : null;
            case P3_STALK -> t == 20 ? "t10_stalk" : t == 60 ? "t10b_stalk" : null;
            case P3_SNATCH -> t == 10 ? "t11_looming" : t == com.groid.papon.boss.VoiceLines.beat("snatch.grab", 22) + 3 ? "t12_rip"
                    : t == com.groid.papon.boss.VoiceLines.beat("snatch.grab", 22) + 50 ? "t13_band_cowers" : null;
            case P3_RETURN -> t == 30 ? "t14_lights_out" : t == 90 ? "t15_lights_out2" : null;
            case P3_BLACKOUT -> t == 22 ? "t16_black" : t == 34 ? "t17_spotlight" : t == 48 ? "t18_spot_on_him" : null;
            case P3_SERENADE -> t == 40 ? "t19_orbit" : t == 100 ? "t20_stands" : t == 130 ? "t20b_band_watching" : t == 200 ? "t21_lighters"
                    : t == 260 ? "t22_sings" : t == 306 ? "t22b_power_builds" : t == 318 ? "t23_ascension_flash" : t == 336 ? "t23b_wings_unfurl"
                    : t == 352 ? "t23c_wings_open" : t == 366 ? "t23d_band_awe" : t == 376 ? "t24_last_chord" : null;
            case P3_VERDICT -> t == 30 ? "t25_more_my_style" : t == 70 ? "t26_learn" : null;
            case P3_TOSS -> t == 14 ? "t27_toss" : t == 30 ? "t28_flight" : t == 46 ? "t29_catch" : t == 80 ? "t29b_band_glances" : null;
            case P3_FELT -> t == 8 ? "t30_ascended" : t == 50 ? "t30b_felt" : t == 110 ? "t30c_evil_smile" : null;
            case P3_TO_CAR -> t == 20 ? "t31_to_car" : null;
            case P3_CRUSH -> t == 14 ? "t32_lift" : t == 30 ? "t32b_crush" : t == 46 ? "t32c_ball" : t == 54 ? "t32d_reform" : t == 70 ? "t32e_skyline"
                    : t == 82 ? "t32f_underglow" : null;
            case P3_ASCEND -> t == 30 ? "t18b_groan" : t == com.groid.papon.boss.VoiceLines.beat("ascend.burst", 100) + 3 ? "t18c_wings_tear"
                    : t == com.groid.papon.boss.VoiceLines.beat("ascend.roar", 108) + 12 ? "t18d_roar"
                    : t == com.groid.papon.boss.VoiceLines.beat("ascend.end", 150) - 8 ? "t18e_band_awe" : null;
            case P3_GO -> t == 14 ? "t33_go" : null;
            case P3_DANCE -> t == 20 ? "t34_pickup" : t == com.groid.papon.boss.VoiceLines.beat("dance.drop", 42) + 20 ? "t35_fire_back" : t == 90 ? "t36_stance" : null;
            default -> null;
        };
        if (n != null) once(mc, n);
        if (b.isPhaseThree() && s == BossState.IDLE && p3Mark < 0) p3Mark = k;
        if (p3Mark > 0) {
            int j = k - p3Mark;
            if (j == 2) PaponBossEntity.devFrozen = true;
            if (j == 4) run("gamemode spectator");
            if (j == 6) tpLook(b.position().add(4, 2, 8), b.position().add(0, 2, 0));
            if (j == 20) shot(mc, "t37_night_fight");
            if (j == 22) tpLook(b.position().add(0, 18, 26), b.position());
            if (j == 40) shot(mc, "t38_night_bowl");
            if (j == 42) PaponMod.LOG.info("[autoshot] night fps {}", mc.getFps());
            if (j == 44) {
                PaponBossEntity.devFrozen = false;
                run("gamemode survival");
                run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ~ ~ ~7");
            }
            if (j >= 46) ascendedShowcase(mc, b, j - 46);
        }
        if (k > 60 * 20 * 4) mc.stop();
    }

    private static int p3Mark = -1;

    /** Ascended Papon's moves, one after another (an invulnerable armor stand is the target, the camera a spectator), then the QTEs on you. */
    private static final String[] SHOW = {"P3_COMBO", "P3_FLASH", "P3_COMET", "P3_LASER", "P3_SOLAR", "P3_MARIACHI", "P3_PINATA",
            "P3_BRAZIERS", "P3_NITRO", "P3_NOVA", "QTE_GRAB", "P3_DUEL_FETCH"};
    private static final int[] SHOW_LEN = {70, 110, 170, 120, 180, 170, 150, 120, 320, 250, 150, 520};

    private static void ascendedShowcase(Minecraft mc, PaponBossEntity b, int j) {
        if (j == 0) {
            run("papon escort true", "gamemode spectator",
                    "execute as @e[type=papon:papon_boss,limit=1] at @s run summon armor_stand ~7 ~ ~5 {Invulnerable:1b,NoGravity:0b}");
        }
        int at = 14, i = 0;
        for (; i < SHOW.length; i++) {
            if (j < at + SHOW_LEN[i]) break;
            at += SHOW_LEN[i];
        }
        if (j < 14) return;
        if (i >= SHOW.length) {
            int u = j - at;
            if (u == 4) run("kill @e[type=papon:papon_boss]");
            if (u == 80) once(mc, "u99_his_fall");
            if (u == 160) mc.stop();
            return;
        }
        int u = j - at;
        String st = SHOW[i];
        boolean qte = st.equals("QTE_GRAB") || st.equals("P3_DUEL_FETCH");
        if (u == 0) {
            PaponMod.LOG.info("[autoshot] showcase {}", st);
            if (qte) {
                run("kill @e[type=armor_stand]", "gamemode survival",
                        "execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ^ ^ ^5 facing entity @s");
            }
            run("papon bossstate " + st);
        }
        var stand = mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, b.getBoundingBox().inflate(60), e -> true)
                .stream().findFirst().orElse(null);
        if (!qte && u % 8 == 1) {
            Vec3 c = b.arena() != null ? b.arena().centerVec() : b.position();
            boolean wide = st.equals("P3_SOLAR") || st.equals("P3_NOVA") || st.equals("P3_PINATA") || st.equals("P3_MARIACHI") || st.equals("P3_BRAZIERS");
            if (wide) {
                tpLook(c.add(0, 14, 24), c.add(0, 3, 0));
            } else {
                Vec3 tgt = stand != null ? stand.position() : c;
                Vec3 mid = tgt.lerp(b.position(), 0.5);
                Vec3 d = b.position().subtract(tgt).multiply(1, 0, 1);
                d = d.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : d.normalize();
                Vec3 side = new Vec3(-d.z, 0, d.x);
                tpLook(mid.add(side.scale(13)).add(0, 4, 0), mid.add(0, 2, 0));
            }
        }
        if (qte) {
            lookAt(mc, b);
            if (st.equals("P3_DUEL_FETCH") || u % 2 == 0) com.groid.papon.client.QteClient.devPress();
        }
        int len = SHOW_LEN[i];
        int[] shots = st.equals("P3_DUEL_FETCH") ? new int[]{40, 120, 200, 300, 400, 470}
                : st.equals("QTE_GRAB") ? new int[]{24, 34, 44, 90} : new int[]{len / 5, 2 * len / 5, 3 * len / 5, 4 * len / 5};
        for (int k = 0; k < shots.length; k++) {
            if (u == shots[k]) shot(mc, String.format("u%02d_%s_%d", i, st.toLowerCase(java.util.Locale.ROOT), k));
        }
    }

    @Nullable private static com.groid.papon.boss.VoiceLines.Line lastLine;
    private static int lastLineTick;
    @Nullable private static com.groid.papon.boss.VoiceLines.Line endedLine;

    /** On top of the coliseum run: every line he says, when, in what state, and whether it ends before the state does; his face mid-line. */
    private static void voice(Minecraft mc) {
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) return;
        var line = b.talking();
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (line != null && line != lastLine && line != endedLine) {
            lastLine = line;
            endedLine = null;
            lastLineTick = tick;
            PaponMod.LOG.info("[voice] {} starts at {} t{} ({} t long, mood {})", line.id(), s, t, line.ticks(), line.mood());
        }
        if (lastLine != null && tick - lastLineTick == lastLine.ticks()) {
            PaponMod.LOG.info("[voice] {} ends at {} t{}", lastLine.id(), s, t);
            endedLine = lastLine;
            lastLine = null;
        }
        if (line != null) {
            int into = tick - lastLineTick;
            if (into == 8 || into == line.ticks() / 2) once(mc, "v_" + line.id() + "_" + (into == 8 ? "a" : "b"));
        }
        if (s == BossState.P2_APOLOGY && t == com.groid.papon.boss.VoiceLines.beat("apology.wink", 144) + 6) once(mc, "v_wink");
        if (s == BossState.P2_SEASON && t == com.groid.papon.boss.VoiceLines.beat("season.kiss", 96)) once(mc, "v_chefs_kiss");
    }

    private static void tpLook(Vec3 at, Vec3 look) {
        double dx = look.x - at.x, dy = look.y - (at.y + 1.62), dz = look.z - at.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", at.x, at.y, at.z, yaw, pitch));
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

    private static void fight(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "effect give @s regeneration 100000 4 true", "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000",
                    "gamerule doMobSpawning false", "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "tp @s 4000 200 4000");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 90 -> run("kill @e[type=papon:papon_boss]", "execute at @s rotated 0 0 run papon boss");
            case 90 + 20 * 200 -> mc.stop();
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) return;
        if (b.getBossState().ordinal() != lastState) {
            lastState = b.getBossState().ordinal();
            PaponMod.LOG.info("[autoshot] boss -> {}", b.getBossState());
        }
    }

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
