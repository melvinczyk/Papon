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
    /** -Pautoshot=phase4: into the coliseum, then a quarter health: phase four's ritual beat by beat, the dead's coliseum, and every move of his truest form. */
    private static final boolean PHASE4 = MODE.equals("phase4");
    /** -Pautoshot=climax: into the coliseum, then phase four's stages: El Gigante up close from every side, the storm's rage and
     *  the fight with the giant and the serpents on their own, El Juicio Final (papon_z*.png). */
    private static final boolean CLIMAX = MODE.equals("climax");
    /** -Pautoshot=phases: into the coliseum's fight, then real hits (not /papon bossstate) take him to half health and to a
     *  quarter: phase three's whole cutscene and phase four's must follow on their own (papon_p*.png, every state logged). */
    private static final boolean PHASES = MODE.equals("phases") || MODE.equals("phasesdown");
    /** -Pautoshot=phasesdown: the same, but first you leave so his coliseum comes down (as after the user's reload): phase three
     *  must wait for it to go up again and play in full, not its ground version. */
    private static final boolean PHASES_DOWN = MODE.equals("phasesdown");
    /** -Pautoshot=nights: into the coliseum, then the flags, the crowd up close, the potato sun, nightfall (the lamplighter), the potato moon. */
    private static final boolean NIGHTS = MODE.equals("nights");
    /** -Pautoshot=ending: a truck, the reveal, his fall, Gerbin's pillow, back to the truck: the bald chef's wave and menu; the pillow hugged, licked, in bed. */
    private static final boolean ENDING = MODE.equals("ending");
    /**
     * -Pautoshot=pillow: a bed against a wall; the pillow hugged, licked and humped (third and first person), slouched on the bed,
     * humped there (planked, from behind, from the side of the bed on her back and on her knees, until Post Nut Clarity);
     * sat on the edge of the bed, her feet licked from the floor, then lifted to the face (side, front, first person).
     */
    private static final boolean PILLOW = MODE.equals("pillow");
    /** -Pautoshot=state:STATE[:FORM]:t1,t2,...  a boss here (form 3 Ascended or 4 the Final Guardian), straight into STATE; the
     *  cutscene camera's view (or yours) shot at those ticks in it (papon_s_STATE_t.png). For checking one beat quickly. */
    private static final boolean STATE = MODE.startsWith("state:");
    /** -Pautoshot=serpents: Quetzalcoatl and Kukulcan out of their smoke over a flat world, from the ground and from the air. */
    private static final boolean SERPENTS = MODE.equals("serpents");
    /** -Pautoshot=skyline: the Skyline parked in daylight, all the way round and up close (papon_k*.png), then from its driver's seat. */
    private static final boolean SKYLINE = MODE.equals("skyline");
    /** -Pautoshot=skyshake: how much the Skyline and your view move frame to frame (2026-10-07, the user: "maybe shaking too
     *  much"): every rendered frame's camera angles, position, the body's lean and the screen shake logged ("[shake]"), parked
     *  and idling, idling from the seat, flat out, coasting; papon_q*.png at consecutive ticks. */
    private static final boolean SKYSHAKE = MODE.equals("skyshake");
    private static String shakePhase = "";
    /** -Pautoshot=mirrors: the Skyline's live mirrors (2026-10-07): on a lot with a red pillar behind its left, a blue one behind its
     *  right, a yellow one straight behind and an armor stand; from the driver's seat at each mirror (papon_m*.png), from behind,
     *  then pulling away. Logs each pass's time. */
    private static final boolean MIRRORS = MODE.equals("mirrors");

    /** The phase of the skyshake tour, or null (ClientEvents logs the camera each frame while it runs). */
    public static String shakePhase() { return SKYSHAKE && !shakePhase.isEmpty() ? shakePhase : null; }
    /** -Pautoshot=giant: the coliseum up, straight to phase four's storm: El Gigante hunting you, its lunge, La Mano catching you
     *  (lifted, the blast, the slam), one struggled free, then left to its own scheduler (papon_g*.png; every act logged). */
    private static final boolean GIANT = MODE.equals("giant") || MODE.equals("tells");
    /** -Pautoshot=tells: El Gigante's attack tells (2026-10-06): its laser, both slams, a swipe, La Mano and the lunge, each shot
     *  through its wind-up from where you stand (papon_t*.png; the banner on your screen, the pillars and eyes in the world). */
    private static final boolean TELLS = MODE.equals("tells");
    /** -Pautoshot=wildgiant: El Gigante out of its spawn egg on open ground: the dig, its hunt and every act on its own, then
     *  its rift closing and the drag under (papon_w*.png; every act logged). */
    private static final boolean WILD_GIANT = MODE.equals("wildgiant");
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
        if (SERPENTS) {
            serpents(mc);
            return;
        }
        if (SKYLINE) {
            skyline(mc);
            return;
        }
        if (SKYSHAKE) {
            skyshake(mc);
            return;
        }
        if (MIRRORS) {
            mirrors(mc);
            return;
        }
        if (STATE) {
            statePose(mc);
            return;
        }
        if (GIANT) {
            giantTest(mc);
            return;
        }
        if (WILD_GIANT) {
            wildGiant(mc);
            return;
        }
        if (COLISEUM || VOICE || ARENA || NIGHTS || PHASE3 || PHASE4 || CLIMAX || PHASES) {
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
            case P2_ARRIVE -> t == 12 ? "c01_crane_high" : t == 30 ? "c02_crane_down" : t == 52 ? "c03_welcome"
                    : t == com.groid.papon.boss.VoiceLines.beat("arrive.roar1", 89) + 8 ? "c03b_roar_back" : t == com.groid.papon.boss.VoiceLines.beat("arrive.listos", 105) + 10 ? "c03c_listos"
                    : t == com.groid.papon.boss.VoiceLines.beat("arrive.roar2", 135) + 10 ? "c03d_roar_louder"
                    : t == com.groid.papon.boss.VoiceLines.beat("arrive.musica", 161) + 10 ? "c04_musica" : null;
            case P2_NOTICE -> t == 12 ? "c05_band_asleep" : t == 40 ? "c06_maestro" : null;
            case P2_MARCH -> t == 20 ? "c07_march" : t == 45 ? "c07b_march" : null;
            case P2_WAKE -> t == 8 ? "c08_windup" : t == 15 ? "c08b_whack" : t == 20 ? "c09_singer_up" : t == 27 ? "c09b_singer_top" : t == 42 ? "c10_band_wakes" : t == 62 ? "c10b_satisfied" : null;
            case P2_WAIT -> t == 30 ? "c11_wait" : t == 110 ? "c12_band_dressing" : t == 190 ? "c13_band_dressing2" : t == 250 ? "c13b_wait" : null;
            case P2_KICK -> t == 13 ? "c14_kick" : null;
            case P2_COUNT -> t == 38 ? "c15_count_uno" : t == 75 ? "c16_count_tres" : t == 94 ? "c16b_count_cuatro" : t == 110 ? "c17_downbeat" : null;
            case P2_SHOWTIME -> t == 8 ? "c18_guitar_close" : t == 40 ? "c19_guitar_tilt" : t == 66 ? "c20_singer_aura" : null;
            case P2_APOLOGY -> t == 30 ? "c21_walk_over" : t == 80 ? "c22_apology" : t == 135 ? "c23_aura_farm" : t == 172 ? "c24_aura_farm2" : null;
            case P2_RESUME -> t == 10 ? "c25_where_were_we" : null;
            case P2_HYPE -> t == 14 ? "c26_hype_ear" : t == 30 ? "c26b_hype_roar" : t == 60 ? "c27_hype_turn" : t == 72 ? "c27b_mas_fuerte" : t == 92 ? "c28_raise_roof"
                    : t == 112 ? "c28b_raise_roof" : t == 132 ? "c29_tu" : t == 151 ? "c29b_erupt" : null;
            case P2_SOLO -> t == 10 ? "s1_solo_hands" : t == 36 ? "s2_solo_face" : t == 52 ? "s3_solo_reverse" : null;
            case P2_CUTAWAY -> t == 9 ? "s4_" + b.cutaway().name().toLowerCase(java.util.Locale.ROOT) + "_a"
                    : t == 28 ? "s4_" + b.cutaway().name().toLowerCase(java.util.Locale.ROOT) + "_b" : null;
            default -> null;
        };
        if (n != null) once(mc, n);
        // the same apology frame with particles at their least: what is on his face is a particle or it is not
        if (s == BossState.P2_APOLOGY && t == 83) mc.options.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
        if (s == BossState.P2_APOLOGY && t == 88) once(mc, "c22p_apology_no_particles");
        if (s == BossState.P2_APOLOGY && t == 92) mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
        if (s == BossState.IDLE && b.isPhaseTwo() && colMark < 0 && taken.contains("c25_where_were_we")) colMark = tick;
        // the first sung word: the opening pyrotechnics (PyroShow), as you see them, him in the middle of the frame
        if (colMark > 0 && COLISEUM) {
            int k0 = tick - colMark;
            if (k0 == 2 || k0 == 8 || k0 == 16 || k0 == 30 || k0 == 50 || k0 == 80) once(mc, String.format("c30_first_word_%02d", k0));
        }
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
        if (colMark > 0 && PHASE4) {
            phaseFourTour(mc, b, tick - colMark);
            return;
        }
        if (colMark > 0 && PHASES) {
            phasesTour(mc, b, tick - colMark);
            return;
        }
        if (colMark > 0 && CLIMAX) {
            climaxTour(mc, b, tick - colMark);
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
                Vec3 f = Vec3.directionFromRotation(0, singer.stageYaw());
                Vec3 side = new Vec3(-f.z, 0, f.x);
                Vec3 mouth = singer.position().add(0, singer.getBbHeight() * 0.6, 0);
                tpLook(mouth.add(f.scale(2.4)).add(side.scale(0.8)).add(0, -1.5, 0), mouth);
            }
            if (k >= 20 && k <= 80 && k % 6 == 2) shot(mc, "c3" + String.format("%02d", k) + "_singing");
            if (k == 90) run("tp @s ~ ~ ~ 0 0");
            if (singer != null && k == 92) {
                Vec3 f = Vec3.directionFromRotation(0, singer.stageYaw());
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
            // the cuts to the band, one of each (a spectator meanwhile, so no QTE grabs you between them)
            if (k == 670) run("gamemode spectator");
            for (int i = 0; i < PaponBossEntity.Cutaway.values().length; i++) {
                if (k == 680 + 50 * i) run("papon bossstate IDLE", "papon cutaway " + PaponBossEntity.Cutaway.values()[i].name());
            }
            if (k == 990) run("gamemode survival");
            if (k == 1000) shot(mc, "c43_fight");
            if (k == 1020) run("kill @e[type=papon:papon_boss]");
            if (k == 1080) once(mc, "c48_his_fall");
            if (k == 1160) once(mc, "c49_back_down");
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
            // the hump from a camera of its own (she is bent over in front of him, her back to him): the side, behind, the front
            case 222 -> playerCamera(mc, new Vec3(2.8, -1.15, 0.0));
            case 230 -> shot(mc, "pl04_hump_side");
            case 232 -> playerCamera(mc, new Vec3(-0.4, -0.5, -2.6));
            case 240 -> shot(mc, "pl05_hump_behind");
            case 242 -> playerCamera(mc, new Vec3(1.8, -0.9, 2.2));
            case 249 -> shot(mc, "pl06_hump_front");
            case 251 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
            }
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
            case 371 -> {
                // round to the foot of the bed (from beside it, she would be had across it)
                var p = bedAt;
                if (p != null) tpLook(Vec3.atBottomCenterOf(p).add(0, 0, 1.4), Vec3.atBottomCenterOf(p).add(0, 0.6, -0.5));
            }
            case 375 -> {
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) mc.gameMode.interact(mc.player, pillow, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            // planked over her on her back: the side, from the foot, first person
            case 395 -> bedSideCam(mc, 2.6, 1.0, 0.6, 0.4, 0.8);
            case 403 -> shot(mc, "pl13_bed_plank_side");
            case 405 -> bedSideCam(mc, 0.9, 1.5, 3.0, 0.4, 0.6);
            case 413 -> shot(mc, "pl14_bed_plank_foot");
            case 415 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
                opts.setCameraType(CameraType.FIRST_PERSON);
            }
            case 425 -> shot(mc, "pl15_bed_plank_fp");
            // rolled over onto her knees, from behind
            case 427 -> com.groid.papon.network.PaponNetwork.CHANNEL.sendToServer(new com.groid.papon.network.PaponNetwork.PillowSwap());
            case 433 -> bedSideCam(mc, 2.6, 1.0, 0.5, 0.4, 0.6);
            case 434 -> shot(mc, "pl15a_bed_rolling_side");
            case 450 -> shot(mc, "pl15b_bed_behind_side");
            case 451 -> bedSideCam(mc, 1.9, 0.55, 0.45, 0.3, 0.45);
            case 452 -> shot(mc, "pl15bb_bed_behind_side_low");
            case 453 -> bedSideCam(mc, -1.6, 1.3, 2.6, 0.4, 0.5);
            case 460 -> shot(mc, "pl15c_bed_behind_foot");
            case 462 -> bedSideCam(mc, 1.8, 0.9, -0.6, 0.3, 0.7);
            case 470 -> shot(mc, "pl15d_bed_behind_front");
            case 472 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
                opts.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setXRot(40);
                mc.player.xRotO = 40;
            }
            case 482 -> shot(mc, "pl15e_bed_behind_fp");
            case 484 -> run("ride @s dismount");
            case 500 -> bedSideCam(mc, 2.6, 1.0, 0.5, 0.4, 0.6);
            case 506 -> shot(mc, "pl15f_bed_sat_back_up");
            // from the side of the bed (beside its foot half, west): on her back across it, then on her knees there, until
            // done (the clarity's wait cut short)
            case 508 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
                opts.setCameraType(CameraType.FIRST_PERSON);
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) {
                    var w = pillow.facing().getCounterClockWise();
                    Vec3 at = Vec3.atBottomCenterOf(pillow.bed().relative(pillow.facing().getOpposite())).add(w.getStepX() * 1.4, 0, w.getStepZ() * 1.4);
                    tpLook(at, pillow.middle().add(0, 0.6, 0));
                }
            }
            case 514 -> {
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) mc.gameMode.interact(mc.player, pillow, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            case 532 -> sideCam(mc, 0.6, 1.4, 2.2);
            case 540 -> shot(mc, "pl15g_side_back");
            case 542 -> sideCam(mc, 2.4, 1.0, -0.4);
            case 550 -> shot(mc, "pl15h_side_back_along");
            case 552 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
            }
            case 562 -> shot(mc, "pl15i_side_back_fp");
            case 564 -> com.groid.papon.network.PaponNetwork.CHANNEL.sendToServer(new com.groid.papon.network.PaponNetwork.PillowSwap());
            case 582 -> sideCam(mc, 0.6, 1.4, 2.2);
            case 590 -> shot(mc, "pl15j_side_knees");
            case 592 -> sideCam(mc, 2.4, 1.0, -0.4);
            case 600 -> shot(mc, "pl15k_side_knees_along");
            case 602 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
            }
            case 610 -> shot(mc, "pl15l_side_knees_fp");
            case 612 -> System.setProperty("papon.clarity", "1");
            case 680 -> shot(mc, "pl15m_clarity");
            case 690 -> {
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) mc.gameMode.interact(mc.player, pillow, net.minecraft.world.InteractionHand.MAIN_HAND);
                PaponMod.LOG.info("[autoshot] clarity {} riding {}", mc.player.hasEffect(com.groid.papon.registry.ModEffects.POST_NUT_CLARITY.get()),
                        mc.player.getVehicle());
            }
            case 694 -> shot(mc, "pl15n_not_now");
            case 696 -> {
                // the clarity's other hindrances: no sleep, weaker attacks, slower
                IntegratedServer srv = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                var bed = pillow == null ? null : pillow.bed();
                if (srv != null && bed != null) srv.execute(() -> {
                    ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                    if (sp == null) return;
                    var slept = sp.startSleepInBed(bed);
                    PaponMod.LOG.info("[autoshot] clarity sleep {} speed {} damage {} effect left {}", slept.left().map(Object::toString).orElse("slept"),
                            sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                            sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                            sp.getEffect(com.groid.papon.registry.ModEffects.POST_NUT_CLARITY.get()).getDuration());
                });
            }
            case 698 -> shot(mc, "pl15o_sleepless");
            case 700 -> {
                System.clearProperty("papon.clarity");
                run("effect clear @s");
            }
            // sat on the edge of the bed (sneak + right-click), her feet licked from the floor, then lifted to the face
            case 730 -> net.minecraft.client.KeyMapping.set(opts.keyShift.getKey(), true);
            case 736 -> {
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) mc.gameMode.interact(mc.player, pillow, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            case 740 -> net.minecraft.client.KeyMapping.set(opts.keyShift.getKey(), false);
            case 748 -> edgeCam(mc, 2.8, 0.9, 0.0, 0.3);
            case 756 -> shot(mc, "pl16_edge_front");
            case 758 -> edgeCam(mc, 1.6, 0.35, 2.4, 0.25);
            case 766 -> shot(mc, "pl17_edge_side_low");
            case 768 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
                var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
                if (pillow != null) mc.gameMode.interact(mc.player, pillow, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            case 790 -> edgeCam(mc, 1.4, 0.9, 2.6, 0.2);
            case 798 -> shot(mc, "pl18_floor_side");
            case 800 -> edgeCam(mc, 3.4, 1.2, -1.3, 0.25);
            case 808 -> shot(mc, "pl19_floor_behind");
            case 810 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
                opts.setCameraType(CameraType.FIRST_PERSON);
            }
            case 822 -> shot(mc, "pl20_floor_fp");
            case 826 -> com.groid.papon.network.PaponNetwork.CHANNEL.sendToServer(new com.groid.papon.network.PaponNetwork.PillowSwap());
            case 838 -> shot(mc, "pl21_lifting_fp");
            case 852 -> shot(mc, "pl22_lift_fp");
            case 854 -> edgeCam(mc, 1.6, 1.1, 2.4, 0.7);
            case 862 -> shot(mc, "pl23_lift_side");
            case 864 -> edgeCam(mc, 2.2, 1.3, -1.8, 0.8);
            case 872 -> shot(mc, "pl24_lift_behind");
            case 874 -> {
                mc.setCameraEntity(mc.player);
                opts.hideGui = false;
            }
            case 880 -> mc.stop();
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
            Vec3 f = Vec3.directionFromRotation(0, m.stageYaw());
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

    /**
     * The client-only camera for the side of the bed someone is at: {@code out} from the middle of the block she lies across
     * toward them, {@code up}, {@code along} the bed toward its foot; looking at her hips at the bed's edge.
     */
    private static void sideCam(Minecraft mc, double out, double up, double along) {
        var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
        if (pillow == null || pillow.humpSide() == null) return;
        var sd = pillow.humpSide();
        var f = pillow.facing();
        Vec3 spot = pillow.sideSpot();
        Vec3 c = spot.add(sd.getStepX() * 0.4, 0.75, sd.getStepZ() * 0.4);
        Vec3 at = spot.add(sd.getStepX() * out - f.getStepX() * along, up, sd.getStepZ() * out - f.getStepZ() * along);
        if (bedCam == null) {
            bedCam = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
            bedCam.setInvisible(true);
        }
        Vec3 d = c.subtract(at.add(0, bedCam.getEyeHeight(), 0));
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
        mc.options.hideGui = true;
        PaponMod.LOG.info("[autoshot] pillow act {} side {} rider {}", pillow.act(), sd, mc.player.position());
    }

    /**
     * The client-only camera by the bed: {@code across} it (its clockwise side, as seen from the foot), {@code up}, {@code along}
     * from the head block's centre toward the foot; looking at the mattress {@code lookAlong} toward the foot, {@code lookUp} high.
     */
    private static void bedSideCam(Minecraft mc, double across, double up, double along, double lookUp, double lookAlong) {
        var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
        if (pillow == null) return;
        var e = pillow.facing().getClockWise();
        Vec3 at = pillow.alongBed(along, up).add(e.getStepX() * across, 0, e.getStepZ() * across);
        Vec3 c = pillow.alongBed(lookAlong, lookUp);
        if (bedCam == null) {
            bedCam = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
            bedCam.setInvisible(true);
        }
        Vec3 d = c.subtract(at.add(0, bedCam.getEyeHeight(), 0));
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
        mc.options.hideGui = true;
        PaponMod.LOG.info("[autoshot] pillow act {} rider {}", pillow.act(),
                mc.player.getVehicle() == pillow ? mc.player.position() + " eye " + mc.player.getEyeHeight() : "none");
    }

    /**
     * The client-only camera beside the pillow sat on the edge of its bed: {@code out} from the bed's middle out over her side,
     * {@code up}, {@code along} the bed toward its foot; looking at the floor at her feet ({@code lookUp} high, 0.9 out).
     */
    private static void edgeCam(Minecraft mc, double out, double up, double along, double lookUp) {
        var pillow = nearest(mc, com.groid.papon.entity.BodyPillowEntity.class);
        if (pillow == null) return;
        if (bedCam == null) {
            bedCam = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
            bedCam.setInvisible(true);
        }
        var e = pillow.edge();
        var f = pillow.facing();
        Vec3 mid = pillow.middle();
        Vec3 c = mid.add(e.getStepX() * 0.9, lookUp, e.getStepZ() * 0.9);
        Vec3 at = mid.add(e.getStepX() * out - f.getStepX() * along, up, e.getStepZ() * out - f.getStepZ() * along);
        Vec3 d = c.subtract(at.add(0, bedCam.getEyeHeight(), 0));
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
        mc.options.hideGui = true;
        PaponMod.LOG.info("[autoshot] pillow pose {} edge {} act {} rider {}", pillow.pose(), e, pillow.act(),
                mc.player.getVehicle() == pillow ? mc.player.position() + " eye " + mc.player.getEyeHeight() : "none");
    }

    /** The client-only camera at {@code off} from the player (yaw 0: facing south), looking at their hips. */
    private static void playerCamera(Minecraft mc, Vec3 off) {
        if (bedCam == null) {
            bedCam = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
            bedCam.setInvisible(true);
        }
        Vec3 c = mc.player.position().add(0, 0.8, 0);
        Vec3 at = mc.player.position().add(off);
        Vec3 d = c.subtract(at.add(0, bedCam.getEyeHeight(), 0));
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
        mc.options.hideGui = true;            // no hand drawn from a camera that is not you
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
            case P3_KNOCKBACK -> t == 8 ? "t01_flung" : t == 15 ? "t02_impact" : t == 30 ? "t03_down" : t == vb("knock.fall", 54) - 2 ? "t03b_arm_gives"
                    : t == vb("knock.knee", 76) + 4 ? "t03c_knee" : t == vb("knock.hurt", 84) + 20 ? "t03d_hurt" : t == vb("knock.up", 130) + 12 ? "t04_rising" : null;
            case P3_GLARE -> t == 12 ? "t05_pov_blur" : t == vb("glare.noise", 34) + 16 ? "t06_pov_noise" : t == vb("glare.focus", 84) + 18 ? "t07_pov_zoom_band"
                    : t == vb("glare.scare", 114) + 14 ? "t07b_band_scared" : t == vb("glare.face", 148) + 6 ? "t08_angry_face"
                    : t == vb("glare.notstyle", 158) + 40 ? "t08b_who_am_i" : t == vb("glare.realplay", 230) + 30 ? "t09_really_play" : null;
            case P3_POWER -> t == 8 ? "t18f_power_stagger" : t == vb("power.line", 22) + 12 ? "t18g_so_much_power" : t == vb("power.grin", 60) + 8 ? "t18h_grin" : null;
            case P3_STALK -> t == 20 ? "t10_stalk" : t == 60 ? "t10b_stalk" : null;
            case P3_SNATCH -> t == 10 ? "t11_looming" : t == com.groid.papon.boss.VoiceLines.beat("snatch.grab", 22) + 3 ? "t12_rip"
                    : t == com.groid.papon.boss.VoiceLines.beat("snatch.grab", 22) + 50 ? "t13_band_cowers" : null;
            case P3_RETURN -> t == 30 ? "t14_lights_out" : t == 90 ? "t15_lights_out2" : null;
            case P3_BLACKOUT -> t == 22 ? "t16_black" : t == 34 ? "t17_spotlight" : t == 48 ? "t18_spot_on_him" : null;
            case P3_SERENADE -> t == 40 ? "t19_orbit" : t == 100 ? "t20_stands" : t == 130 ? "t20b_band_watching" : t == 200 ? "t21_lighters"
                    : t == 236 ? "t22_sings" : t == 252 ? "t22a_sings" : t == 270 ? "t22b_sings_close" : t == 290 ? "t22c_sings_close"
                    : t == 310 ? "t22d_sings_close" : t == 326 ? "t22e_sings_close" : t == 366 ? "t23d_band_awe" : t == 376 ? "t24_last_chord" : null;
            case P3_VERDICT -> t == 30 ? "t25_more_my_style" : t == 70 ? "t26_learn" : null;
            case P3_TOSS -> t == 14 ? "t27_toss" : t == 30 ? "t28_flight" : t == 46 ? "t29_catch" : t == vb("toss.talk1", 66) + 6 ? "t29b_band_whisper"
                    : t == vb("toss.talk2", 88) + 8 ? "t29c_band_whisper2" : null;
            case P3_FELT -> t == 8 ? "t30_ascended" : t == 50 ? "t30b_felt" : t == 110 ? "t30c_evil_smile" : null;
            case P3_TO_CAR -> t == 20 ? "t31_to_car" : null;
            case P3_CRUSH -> t == vb("crush.upgrade", 6) + 24 ? "t31b_upgrade" : t == vb("crush.out", 40) + 6 ? "t32_out_with_the_old"
                    : t == vb("crush.squeeze", 18) + 12 ? "t32b_crush" : t == vb("crush.ball", 42) + 4 ? "t32c_ball" : t == vb("crush.reform", 52) + 2 ? "t32d_reform"
                    : t == vb("crush.down", 66) + 4 ? "t32e_slam" : null;
            case P3_SHOWCAR -> t == 6 ? "t32g_dark" : t == 20 ? "t32h_lamps" : t == 26 ? "t32i_lamps_on" : t == 38 ? "t32j_glow_on" : t == 46 ? "t32k_glow"
                    : t == 64 ? "t32l_pop" : t == 78 ? "t32m_whip" : t == 92 ? "t32n_roof" : t == 104 ? "t32o_flank" : t == 118 ? "t32p_wing"
                    : t == 138 ? "t32q_in_with_the_new" : t == 168 ? "t32r_hero" : null;
            case P3_ASCEND -> t == 30 ? "t18b_groan" : t == vb("ascend.burst", 100) + 3 ? "t18c_wings_tear" : t == vb("ascend.roar", 108) + 12 ? "t18d_roar"
                    : t == vb("ascend.cut", 30) + 8 ? "t18b2_band_clutching" : t == vb("ascend.god", 162) + 20 ? "t18f_no_more_games"
                    : t == vb("ascend.crowd", 200) + 12 ? "t18d2_stands_up" : t == vb("ascend.band", 230) + 12 ? "t18e_band_backs_off" : null;
            case P3_GO -> t == 14 ? "t33_go" : null;
            case P3_DANCE -> t == 20 ? "t34_pickup" : t == vb("dance.drop", 42) + 12 ? "t35_fire_back" : t == vb("dance.line", 76) + 14 ? "t36_lets_dance" : null;
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
        if (k > 60 * 20 * 12) mc.stop();
    }

    private static int p3Mark = -1;
    private static int p4Mark = -1;

    /** Phase four: the ritual from a quarter health (shots on every beat), the dead's coliseum from outside and in, the band and the stands turned, then each of his moves on you. */
    private static void phaseFourTour(Minecraft mc, PaponBossEntity b, int k) {
        BossState s = b.getBossState();
        int t = b.ticksInState();
        if (k == 2) {
            run("gamemode survival", "effect give @s regeneration 100000 4 true");
            run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ~ ~ ~7");
        }
        if (k == 40) run("papon bossstate P4_FALL");
        if (k > 40 && !com.groid.papon.client.Cinematic.isActive() && !s.phaseFour()) lookAt(mc, b);
        String n = switch (s) {
            case P4_FALL -> t == 6 ? "w01_thrown" : t == 30 ? "w02a_from_above" : t == vb("p4.land", 14) + 40 ? "w02_on_his_back"
                    : t == vb("p4.elbow", 66) + 10 ? "w02b_silent_arena" : t == vb("p4.look", 86) + 14 ? "w03_the_scar"
                    : t == vb("p4.scar", 112) + 16 ? "w04_it_reopened" : t == vb("p4.fall_end", 184) - 26 ? "w04b_band_jaws" : t == vb("p4.fall_end", 184) - 2 ? "w04c_band_jaws_close" : null;
            case P4_RISEUP -> t == 20 ? "w05_struggles_up" : t == vb("p4.cough", 52) + 3 ? "w06_coughs_blood" : t == vb("p4.cough", 52) + 30 ? "w06b_over_shoulder"
                    : t == vb("p4.howgot", 92) + 20 ? "w07_how_i_got_it" : null;
            case P4_IGNIS -> t == 12 ? "w08_reaches_back" : t == vb("p4.head_out", 26) + 16 ? "w09_ignis_head" : t == vb("p4.ignis", 50) + 60 ? "w10a_two_faces"
                    : t == vb("p4.ignis", 50) + 100 ? "w10_nothing_notable" : t == vb("p4.thud", 184) + 2 ? "w11_thud"
                    : t == vb("p4.met", 210) + 12 ? "w12_over_your_shoulder" : t == vb("p4.met", 210) + 40 ? "w13_met_before" : null;
            case P4_PROPHECY -> t == 40 ? "w14_ten_thousand_years" : t == vb("p4.throne_look", 119) - 20 ? "w15_fog"
                    : t == vb("p4.throne", 133) + 30 ? "w15a_throne_over_shoulder" : t == vb("p4.throne", 133) + 80 ? "w15b_the_throne"
                    : t == vb("p4.carved", 262) - 20 ? "w15c_looking_up" : t == vb("p4.carved", 262) + 40 ? "w15d_carved"
                    : t == vb("p4.carved", 262) + 62 ? "w15d2_band_listens" : t == vb("p4.carved", 262) + 82 ? "w15d3_band_listens_close"
                    : t == vb("p4.warrior", 368) + 50 ? "w15e_warrior" : t == vb("p4.prophecy_end", 563) - 20 ? "w15f_carry_the_world" : null;
            case P4_THRONE -> t == 40 ? "w16a_moctezuma_wide" : t == vb("p4.moctezuma", 18) + 100 ? "w16b_dolly" : t == vb("p4.moctezuma", 18) + 180 ? "w16c_saw_a_god"
                    : t == vb("p4.gates", 249) + 40 ? "w16d_gates" : t == vb("p4.gates", 249) + 100 ? "w16e_took_everything"
                    : t == vb("p4.mistake", 395) + 30 ? "w16f_not_his_mistake" : t == vb("p4.mistake", 395) + 110 ? "w16g_take_it"
                    : t == vb("p4.moon", 573) + 30 ? "w16h_moon_dark" : t == vb("p4.moon", 573) + 70 ? "w16h2_mist_rolls_in"
                    : t == vb("p4.moonlit", 693) - 16 ? "w16h3_darkening" : t == vb("p4.moonlit", 693) + 20 ? "w16h4_moon_lit"
                    : t == vb("p4.moonlit", 693) + 44 ? "w16h5_night" : t == vb("p4.prophecy", 617) + 30 ? "w16i_fulfilled"
                    : t == vb("p4.lunaknows", 712) + 24 ? "w17_luna_knows" : t == vb("p4.throne_end", 791) - 8 ? "w17b_wide" : null;
            case P4_BRETHREN -> t == 40 ? "w18_my_brethren" : t == vb("p4.brethren", 20) + 70 ? "w19_silent_stands" : t == vb("p4.grant", 139) + 30 ? "w20_grant_strength"
                    : t == vb("p4.turn", 189) + 4 ? "w21_moon_turning" : t == vb("p4.turn", 189) + 24 ? "w22_moon_turned" : t == vb("p4.turn", 189) + 50 ? "w22b_under_the_moon" : null;
            case P4_SOULS -> t == 40 ? "w23_souls_pour" : t == 80 ? "w24_stands_turning" : t == 150 ? "w25_band_souls" : t == 206 ? "w26_aglow"
                    : t == vb("p4.feel", 214) + 30 ? "w26b_the_scream" : null;
            case P4_HALFBAKED -> t == 40 ? "w27_half_baked" : t == vb("p4.burst", 112) + 12 ? "w28_burst" : t == vb("p4.clear", 178) + 4 ? "w29_final_guardian"
                    : t == vb("p4.guardian", 222) + 20 ? "w30_i_am_papon" : t == vb("p4.halfbaked_end", 322) - 10 ? "w31_dead_arena" : null;
            case P4_BOW -> t == 22 ? "w32_bow_forms" : t == 56 ? "w33_draw" : t == com.groid.papon.boss.PaponBossEntity.BOW_HIT - 2 ? "w34_arrow"
                    : t == com.groid.papon.boss.PaponBossEntity.BOW_SMOKE + 14 ? "w35_sky_torn" : t == vb("p4.bowline", 112) + 30 ? "w35b_come_down" : null;
            case P4_SERPENTS -> t == 40 ? "w36_out_of_smoke" : t == 100 ? "w37_how_big" : t == 160 ? "w38_boros" : t == 176 ? "w38b_band_hype_a" : t == 200 ? "w38c_band_hype_b" : t == 214 ? "w38d_band_ready" : t == 230 ? "w38e_band_fiesta" : t == 244 ? "w39_circling" : null;
            case P4_TALKING -> t == 14 ? "w40_now_were_talking" : null;
            case P4_TOBAND -> t == 20 ? "w41_to_the_band" : t == 50 ? "w41b_band_hyped" : t == 90 ? "w41c_band_hyped" : null;
            case P4_HONOR -> t == 50 ? "w42_one_last_song" : t == vb("p4.nod", 112) + 8 ? "w43_singer_nods" : t == vb("p4.song", 128) + 20 ? "w44_trumpets" : null;
            case P4_MUCHACHAS -> t == 30 ? "w45_muchachas" : t == vb("p4.rise", 60) + 30 ? "w46_they_rise" : t == vb("p4.twirl", 112) + 30 ? "w47_twirl"
                    : t == vb("p4.dip", 170) + 10 ? "w48_dip" : null;
            case P4_GOODTIME -> t == 20 ? "w49_striding" : t == vb("p4.dance", 24) + 60 ? "w49b_its_a_dance" : t == vb("p4.goodtime", 125) + 20 ? "w50_good_time"
                    : t == vb("p4.erupt", 162) + 30 ? "w51_erupts" : null;
            default -> null;
        };
        if (n != null) once(mc, n);
        if (b.muertosFight() && s == BossState.IDLE && p4Mark < 0) p4Mark = k;
        if (p4Mark < 0) {
            if (k > 60 * 20 * 6) mc.stop();
            return;
        }
        int j = k - p4Mark;
        Vec3 c = b.arena() != null ? b.arena().centerVec() : b.position();
        if (j == 2) PaponBossEntity.devFrozen = true;
        if (j == 4) run("gamemode spectator");
        if (j == 6) tpLook(c.add(0, 70, 120), c);
        if (j == 24) shot(mc, "x01_dead_coliseum_outside");
        if (j == 26) tpLook(c.add(0, 30, 50), c);
        if (j == 44) shot(mc, "x02_dead_bowl");
        if (j == 46) PaponMod.LOG.info("[autoshot] dead coliseum fps {}", mc.getFps());
        var singer = mc.level.getEntitiesOfClass(com.groid.papon.boss.MariachiEntity.class, b.getBoundingBox().inflate(160),
                m -> m.getRole().singer()).stream().findFirst().orElse(null);
        if (singer != null && j == 48) {
            Vec3 f = c.subtract(singer.position()).multiply(1, 0, 1).normalize();
            tpLook(singer.position().add(f.scale(9)).add(0, 1.5, 0), singer.position().add(0, 1.2, 0));
        }
        if (j == 64) shot(mc, "x03_band_of_the_dead");
        if (singer != null && j == 66) {
            Vec3 f = c.subtract(singer.position()).multiply(1, 0, 1).normalize();
            Vec3 mouth = singer.position().add(0, singer.getBbHeight() * 0.6, 0);
            tpLook(mouth.add(f.scale(2.6)).add(0, -1.5, 0), mouth);
        }
        if (j == 80) shot(mc, "x04_singer_skull");
        if (j == 82) tpLook(c.add(0, 2, 0), c.add(40, 14, 0));
        if (j == 98) shot(mc, "x05_stands_dead");
        if (j == 100) tpLook(c.add(6, 3, 10), b.position().add(0, 2, 0));
        if (j == 116) shot(mc, "x06_rey_de_los_muertos");
        if (j == 118) {
            PaponBossEntity.devFrozen = false;
            run("gamemode survival");
        }
        if (j >= 120) muertosShowcase(mc, b, j - 120);
    }

    /**
     * Phase four's stages, quickly (from the coliseum's fight: his truest form forced, no dead coliseum): El Gigante up out of the
     * floor and looked at from every side (spectating), the storm's rage and a minute of the fight with the giant and the serpents
     * fighting on their own, then the end of the world: the rage and El Juicio Final, shot all through.
     */
    private static void climaxTour(Minecraft mc, PaponBossEntity b, int k) {
        BossState s = b.getBossState();
        if (s.ordinal() != lastState) {
            lastState = s.ordinal();
            PaponMod.LOG.info("[autoshot] climax: boss -> {} stage {} (hp {}, fps {})", s, b.p4StageShown(), (int) b.getHealth(), mc.getFps());
        }
        Vec3 c = b.arena() != null ? b.arena().centerVec() : b.position();
        if (k == 2) run("effect give @s regeneration 100000 4 true", "effect give @s resistance 100000 4 true");
        if (k == 10) run("papon bossstate P4_GIGANTE");
        var giant = nearest(mc, com.groid.papon.boss.SpiritGiantEntity.class);
        // the giant from the floor round it (never spectating: a coliseum with nobody in it for 10 s comes down): ahead of it,
        // ahead and to one side, from the side, right under its ribs, from across the arena, ahead and to the other side
        if (giant != null && k >= 40 && k < 330) {
            Vec3 base = giant.base(), f = giant.forward(), r = giant.right();
            Vec3 head = giant.head(0);
            double fy = c.y + 0.2;
            Vec3[][] views = {
                    {flat(base.add(f.scale(16)), fy), head.add(0, -5, 0)},
                    {flat(base.add(f.scale(13)).add(r.scale(9)), fy), head.add(0, -7, 0)},
                    {flat(base.add(f.scale(4)).add(r.scale(-14)), fy), head.add(0, -9, 0)},
                    {flat(base.add(f.scale(44)), fy), base.add(0, 13, 0)},
                    {flat(base.add(f.scale(7)), fy), head.add(0, -12, 0)},
                    {flat(base.add(f.scale(12)).add(r.scale(-8)), fy), head.add(0, -4, 0)},
            };
            int v = Math.min(views.length - 1, (k - 40) / 48);
            // (2026-10-05) from across the arena when its reach comes (P4_GIGANTE's t 222): La Mano, shot through
            if ((k - 40) % 48 == 0 && giant.act() != com.groid.papon.boss.SpiritGiantEntity.GRAB) tpLook(views[v][0], views[v][1]);
            if (giant.act() == com.groid.papon.boss.SpiritGiantEntity.GRAB) {
                int ga = (int) giant.actTime(0);
                lookAtPoint(mc, giant.hand(giant.grabHand(), 0));
                if (ga == 20 || ga == 45 || ga == 60 || ga == 70 || ga == 95 || ga == 118) shot(mc, String.format("z%02d_la_mano_%03d", v, ga));
            }
            if ((k - 40) % 48 > 0 && (k - 40) % 48 < 40) lookAtPoint(mc, views[v][1]);
            if ((k - 40) % 48 == 20) shot(mc, String.format("z%02d_giant_view%d_%s", v, v, actName(giant.act())));
            if ((k - 40) % 48 == 36) shot(mc, String.format("z%02d_giant_view%db_%s", v, v, actName(giant.act())));
        }
        if (k == 330) run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", c.x + 6, c.y + 0.2, c.z + 10));
        // the storm
        if (k == 400) run("papon bossstate P4_RAGE");
        if (k > 400 && k < 1500) {
            if (giant != null && (k / 60) % 2 == 0) lookAtPoint(mc, giant.head(0).add(0, -8, 0));
            else lookAt(mc, b);
            if (s == BossState.P4_RAGE && b.ticksInState() == 30) shot(mc, "z10_rage_storm");
            if ((k - 400) % 90 == 0 && k > 420) shot(mc, String.format("z%02d_storm_%s", 11 + (k - 400) / 90, s.name().toLowerCase(java.util.Locale.ROOT)));
        }
        // the end of the world
        if (k == 1500) run("papon bossstate P4_JUICIO");
        if (k > 1500) {
            if (s == BossState.P4_JUICIO) {
                int t = b.ticksInState();
                if (t % 40 == 20) lookAtPoint(mc, c.add(0, 4, 0));
                else if (t % 40 == 0) lookAt(mc, b);
                if (t % 30 == 10) once(mc, String.format("z%02d_juicio_%03d", 30 + t / 30, t));
            } else if (s == BossState.P4_RAGE) {
                lookAt(mc, b);
                if (b.ticksInState() == 30) once(mc, "z29_rage_end_of_world");
            } else if (k > 1700) {
                if ((k - 1700) % 100 == 0) once(mc, String.format("z%02d_end_of_world_%s", 50 + (k - 1700) / 100, s.name().toLowerCase(java.util.Locale.ROOT)));
                lookAt(mc, b);
            }
        }
        if (k > 1500 + 20 * 60 * 2) mc.stop();
    }

    /** Real hits from you: down to just over half, one more hit; then (once phase three's fight is on) to just over a quarter. */
    private static void phasesTour(Minecraft mc, PaponBossEntity b, int k) {
        BossState s = b.getBossState();
        if (k == 2) run("effect give @s regeneration 100000 4 true", "effect give @s resistance 100000 4 true");
        if (k % 20 == 0) PaponMod.LOG.info("[autoshot] phases: {} t{} hp {} p3 {} p4 {} form {}", s, b.ticksInState(), (int) b.getHealth(),
                b.isPhaseThree(), b.isPhaseFour(), b.form());
        if (!com.groid.papon.client.Cinematic.isActive()) lookAt(mc, b);
        float max = b.getMaxHealth();
        if (PHASES_DOWN) {
            // away from it (below it, on the ground) until it comes down, then back to him on the ground
            if (k == 20) run("effect give @s slow_falling 60 0 true", "tp @s ~ ~-190 ~");
            if (k < 20 * 20) return;
            if (k == 20 * 20) run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ~ ~1 ~6");
            k -= 20 * 20;
        }
        if (k == 60) run(String.format(java.util.Locale.ROOT, "data merge entity @e[type=papon:papon_boss,limit=1] {Health:%.1ff}", max * 0.52F));
        if (k >= 80 && k % 20 == 0 && !b.isPhaseThree() && !s.phaseThree() && b.getHealth() > max * 0.5F)
            run("damage @e[type=papon:papon_boss,limit=1] 30 minecraft:player_attack by @s");
        if (s.phaseThree() && b.ticksInState() % 60 == 10) once(mc, String.format("p_%s_%d", s.name().toLowerCase(java.util.Locale.ROOT), b.ticksInState()));
        if (b.isPhaseThree() && !b.isPhaseFour() && !s.phaseThree() && s != BossState.P3_DANCE && p4At < 0) p4At = k + 200;
        if (k == p4At) run(String.format(java.util.Locale.ROOT, "data merge entity @e[type=papon:papon_boss,limit=1] {Health:%.1ff}", max * 0.27F));
        if (p4At > 0 && k > p4At + 20 && k % 20 == 0 && !b.isPhaseFour() && !s.phaseFour() && b.getHealth() > max * 0.25F)
            run("damage @e[type=papon:papon_boss,limit=1] 30 minecraft:player_attack by @s");
        if (s.phaseFour() && b.ticksInState() == 30) once(mc, "p_" + s.name().toLowerCase(java.util.Locale.ROOT));
        if (s == BossState.P4_HONOR && b.ticksInState() % 20 == 5) once(mc, String.format("p_p4_honor_%03d", b.ticksInState()));
        if (b.muertosFight() && s == BossState.IDLE && p4At > 0) {
            once(mc, "p_muertos_fight");
            mc.stop();
        }
        if (k > 20 * 60 * 14) mc.stop();
    }

    private static int p4At = -1;

    private static Vec3 flat(Vec3 p, double y) { return new Vec3(p.x, y, p.z); }

    private static String actName(int act) {
        String[] n = {"rise", "idle", "charge", "laser", "slam_l", "slam_r", "sink", "swipe_l", "swipe_r", "slam_both", "roar", "grab", "lunge", "heed", "stomp"};
        return act >= 0 && act < n.length ? n[act] : "act" + act;
    }

    /** El Rey de los Muertos's moves, one after another, on you (Resistance V and regeneration keep you up). */
    private static final String[] SHOW_P4 = {"P4_GIGANTE", "P4_TZOMPANTLI", "P4_BESO", "P4_CASTILLO", "P4_PETALOS", "P4_OUROBOROS", "P4_PISTA",
            "P4_CEMPASUCHIL", "P4_SALSA", "P4_TANGO", "P4_SERPIENTES", "P4_LUNA", "P4_GRIETA"};
    private static final int[] SHOW_P4_LEN = {520, 260, 320, 320, 240, 380, 230, 290, 200, 580, 260, 160, 230};

    private static void muertosShowcase(Minecraft mc, PaponBossEntity b, int j) {
        int at = 0, i = 0;
        for (; i < SHOW_P4.length; i++) {
            if (j < at + SHOW_P4_LEN[i]) break;
            at += SHOW_P4_LEN[i];
        }
        if (i >= SHOW_P4.length) {
            int u = j - at;
            if (u == 4) run("kill @e[type=papon:papon_boss]");
            if (u == 80) once(mc, "y99_his_fall");
            if (u == 140) once(mc, "y99_back_down");
            if (u == 160) mc.stop();
            return;
        }
        int u = j - at;
        String st = SHOW_P4[i];
        Vec3 c = b.arena() != null ? b.arena().centerVec() : b.position();
        if (u == 0) {
            PaponMod.LOG.info("[autoshot] showcase {}", st);
            run("effect give @s instant_health 1 4 true", "effect give @s resistance 100000 4 true");
            run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", c.x + 6, c.y + 0.2, c.z + 12));
            run("papon bossstate " + st);
        }
        // the view on the action: the giant at its end, the floor for the rift, the petals and the candles, else him
        Entity giant = st.equals("P4_GIGANTE") ? nearest(mc, com.groid.papon.boss.SpiritGiantEntity.class) : null;
        if (st.equals("P4_TANGO")) com.groid.papon.client.QteClient.devPress();
        // (2026-10-05) El Gigante digs itself out, and then you are out of its reach, at the far end: La Mano
        if (giant instanceof com.groid.papon.boss.SpiritGiantEntity g && u == 150) {
            Vec3 far = g.base().add(g.forward().scale(52));
            run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", far.x, c.y + 0.2, far.z));
        }
        if (giant instanceof com.groid.papon.boss.SpiritGiantEntity g && st.equals("P4_GIGANTE")) {
            lookAtPoint(mc, u < 130 ? g.base().add(0, 8, 0) : g.head(0).lerp(g.hand(g.grabHand(), 0), 0.5));
            int o = com.groid.papon.boss.SpiritGiantEntity.RISE_T - 52 + 150;
            int[] dig = {20, 40, 52, 70, 95, 124, o + 15, o + 45, o + 62, o + 72, o + 95, o + 118};
            for (int k = 0; k < dig.length; k++) if (u == dig[k]) shot(mc, String.format("y00_gigante_%02d", k));
            return;
        }
        if (giant != null && u % 80 < 50) lookAtPoint(mc, giant.position().add(0, com.groid.papon.boss.SpiritGiantEntity.HEAD_Y * 0.6, 0));
        else if (st.equals("P4_GRIETA") || st.equals("P4_VELAS") || st.equals("P4_PETALOS") || st.equals("P4_PISTA") || st.equals("P4_CEMPASUCHIL"))
            lookAtPoint(mc, mc.player.position().lerp(c, 0.6).add(0, -2, 0));
        else if (st.equals("P4_CASTILLO")) lookAtPoint(mc, c.add(0, 13, 0));
        else if (st.equals("P4_TZOMPANTLI") || st.equals("P4_BESO")) lookAtPoint(mc, c.add(0, 1, 0));
        else if (st.equals("P4_OUROBOROS")) {
            Entity sp = nearest(mc, com.groid.papon.boss.SkySerpentEntity.class);
            if (sp instanceof com.groid.papon.boss.SkySerpentEntity se && u > 60) lookAtPoint(mc, se.headAt(0));
            else lookAt(mc, b);
        } else lookAt(mc, b);
        int len = SHOW_P4_LEN[i];
        int[] shots = {len / 6, 2 * len / 6, 3 * len / 6, 4 * len / 6, 5 * len / 6};
        for (int k = 0; k < shots.length; k++) {
            if (u == shots[k]) shot(mc, String.format("y%02d_%s_%d", i, st.toLowerCase(java.util.Locale.ROOT), k));
        }
    }

    private static int vb(String key, int def) {
        return com.groid.papon.boss.VoiceLines.beat(key, def);
    }

    /** Ascended Papon's moves, one after another (an invulnerable armor stand is the target, the camera a spectator), then the QTEs on you. */
    private static final String[] SHOW = {"P3_COMBO", "P3_FLASH", "P3_COMET", "P3_LASER", "P3_SOLAR", "P3_MARIACHI", "P3_PINATA_SWING",
            "P3_SOMBRERO", "P3_VOLCAN", "P3_CALAVERAS", "ARENA_OLA", "P3_BRAZIERS", "P3_NITRO", "P3_NOVA", "QTE_GRAB", "P3_DUEL_FETCH"};
    private static final int[] SHOW_LEN = {70, 110, 170, 120, 180, 170, 230, 240, 190, 200, 200, 120, 320, 250, 150, 520};

    private static void ascendedShowcase(Minecraft mc, PaponBossEntity b, int j) {
        if (j == 0) run("gamemode survival", "effect give @s resistance 100000 4 true");
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
        Vec3 c = b.arena() != null ? b.arena().centerVec() : b.position();
        if (u == 0) {
            PaponMod.LOG.info("[autoshot] showcase {}", st);
            if (qte) run("execute as @e[type=papon:papon_boss,limit=1] at @s run tp @p ^ ^ ^5 facing entity @s");
            else run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", c.x, c.y + 0.2, c.z + 9));
            run("papon bossstate " + st);
        }
        // the player is the target (Resistance V keeps them up); the view is theirs, always on him
        lookAt(mc, b);
        if (qte && (st.equals("P3_DUEL_FETCH") || u % 2 == 0)) com.groid.papon.client.QteClient.devPress();
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

    private static void statePose(Minecraft mc) {
        String[] parts = MODE.split(":");
        String st = parts[1];
        String form = parts.length > 3 ? parts[2] : "3";
        String[] ticks = parts[parts.length - 1].split(",");
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 18000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "tp @s 4000 200 4000", "papon escort true");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 90 -> run("kill @e[type=papon:papon_boss]", "execute at @s positioned ^ ^ ^8 rotated 180 0 run papon boss", "kill @e[type=papon:caravan]");
            case 140 -> run("papon bossstate " + (form.equals("4") ? "P4_MARTILLO" : "P3_COMBO"));
            case 150 -> run("papon bossstate IDLE");
            case 170 -> run("papon bossstate " + st);
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null || tick < 172) return;
        if (b.getBossState().name().equals(st)) {
            int t = b.ticksInState();
            // (a move, not a cutscene: the camera is yours) keep him in frame, flying or not
            if (!b.getBossState().cutscene()) lookAtPoint(mc, b.position().add(0, b.getBbHeight() * 0.6, 0));
            for (String k : ticks) if (Integer.parseInt(k) == t) once(mc, "s_" + st.toLowerCase(java.util.Locale.ROOT) + "_" + k);
            if (t > Integer.parseInt(ticks[ticks.length - 1]) + 4) mc.stop();
        } else if (tick > 172 + 20 * 60) mc.stop();
    }

    private static void serpents(Minecraft mc) {
        Vec3 c = new Vec3(4000.5, 200, 4000.5);
        switch (tick) {
            case 40 -> run("gamemode spectator", "time set 18000", "gamerule doDaylightCycle false", "weather clear 100000",
                    "gamerule doMobSpawning false", "kill @e[type=papon:sky_serpent]", "tp @s 4000.5 200 4000.5 0 -20");
            case 80 -> run("execute positioned 4000.5 200 4000.5 run papon serpents");
            case 90 -> tpLook(c, c.add(0, 90, -200));
            default -> {}
        }
        int t = tick - 80;
        if (t == 40) shot(mc, "s01_portal");
        if (t == 100) shot(mc, "s02_coming_out");
        var s = nearest(mc, com.groid.papon.boss.SkySerpentEntity.class);
        if (s == null) return;
        // beside its head, a few seconds ahead of it, looking back at it as it comes
        int[] at = {150, 260, 380, 500};
        for (int i = 0; i < at.length; i++) {
            if (t == at[i]) {
                Vec3 h = s.path().at(s.head(0) + 30 * com.groid.papon.boss.SkySerpentEntity.SPEED);
                Vec3 h2 = s.path().at(s.head(0) + 31 * com.groid.papon.boss.SkySerpentEntity.SPEED);
                Vec3 d = h2.subtract(h).normalize();
                Vec3 side = new Vec3(-d.z, 0, d.x);
                double dist = i == 3 ? 90 : 34;
                Vec3 eye = h.add(side.scale(dist)).add(d.scale(dist * 0.7)).add(0, i == 2 ? -26 : 6, 0);
                tpLook(eye.subtract(0, 1.62, 0), h);
            }
            if (t == at[i] + 30) shot(mc, "s0" + (3 + i) + "_head_" + i);
        }
        if (t == 560) tpLook(c.add(0, -120, 0), c.add(1, -100, 0));
        if (t == 600) shot(mc, "s07_from_ground");
        if (t == 640) mc.stop();
    }

    private static int giantMark = -1, giantAct = -1;
    private static Vec3 giantWatch, giantWatchF, giantWatchR;
    private static double giantFloor;
    private static Vec3 giantWatchAt;

    /** The tells, one attack after another: you stand 28 blocks off it, looking at its head, and each is shot through its wind-up. */
    private static void tellsPlan(Minecraft mc, int k, com.groid.papon.boss.SpiritGiantEntity g, double fy) {
        String[] moves = {"laser", "slam", "slam2", "swipe", "grab", "lunge"};
        int[][] shots = {{6, 12, 18, 23, 30, 40}, {5, 10, 16, 20}, {8, 16, 24, 29}, {5, 10, 15, 20}, {20, 36, 52, 64}, {6, 12, 18, 21}};
        int i = (k - 280) / 150;
        if (k < 280 || i >= moves.length) return;
        int at = (k - 280) % 150;
        if (at == 0) tpLook(flat(g.base().add(g.forward().scale(28)), fy), g.head(0));
        if (at == 12) run("papon giant " + moves[i]);
        if (at > 12 && at < 140 && g.act() != com.groid.papon.boss.SpiritGiantEntity.IDLE && !mc.player.isPassenger()) {
            lookAtPoint(mc, g.head(0).add(0, -9, 0));
            int ga = (int) g.actTime(0);
            for (int s : shots[i]) { if (ga == s) once(mc, String.format("t%d_%s_%02d_%s", i, moves[i], s, actName(g.act()))); }
        }
    }

    private static int wildArrived = -1;

    private static void wildGiant(Minecraft mc) {
        if (tick == 40) run("ride @s dismount", "gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                "effect give @s regeneration 100000 4 true", "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000",
                "gamerule doMobSpawning false", "kill @e[type=papon:spirit_giant]", "tp @s 6000 200 6000");
        // the server lags the client at start: go by where you are, not by the clock
        boolean there = Math.abs(mc.player.getX() - 6000) < 40 && Math.abs(mc.player.getZ() - 6000) < 40;
        if (wildArrived < 0) {
            if (there && tick % 20 == 0) run("execute positioned 6000 0 6000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 30",
                    "item replace entity @s hotbar.0 with papon:spirit_giant_spawn_egg");
            if (there && mc.player.onGround() && mc.player.getY() < 190) wildArrived = tick;
            return;
        }
        int a = tick - wildArrived;
        if (a == 10) {
            // the forest there hid its legs: cleared, trunks and leaves, for a lot round where it will walk
            for (int x = 5952; x < 6080; x += 32) {
                for (int z = 5952; z < 6112; z += 32) {
                    for (int y = 60; y < 156; y += 32) {
                        for (String what : new String[]{"#minecraft:leaves", "#minecraft:logs", "minecraft:snow", "minecraft:fern",
                                "minecraft:grass"}) {
                            run(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air replace %s", x, y, z, x + 31, y + 31, z + 31, what));
                        }
                    }
                }
            }
        }
        if (a == 40) {
            mc.player.getInventory().selected = 0;
            shot(mc, "w00_egg");
        }
        if (a >= 50 && a < 56) {
            // the egg, used on the ground ahead like a player would
            mc.player.setXRot(60);
            mc.player.xRotO = 60;
        }
        if (a == 56) {
            if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh && mc.gameMode != null) {
                var r = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, bh);
                PaponMod.LOG.info("[autoshot] wild giant: egg used on {}: {}", bh.getBlockPos(), r);
            } else PaponMod.LOG.info("[autoshot] wild giant: egg not aimed at a block ({})", mc.hitResult);
        }
        var g = nearest(mc, com.groid.papon.boss.SpiritGiantEntity.class);
        if (tick >= 110 && tick % 100 == 0) run("effect give @s instant_health 1 3 true");
        if (g == null) {
            if (wildArrived > 0 && tick - wildArrived == 120) PaponMod.LOG.info("[autoshot] wild giant: none spawned");
            if (tick > 3000) mc.stop();
            return;
        }
        if (giantMark < 0) giantMark = tick;
        int k = tick - giantMark;
        if (g.act() != giantAct) {
            giantAct = g.act();
            PaponMod.LOG.info("[autoshot] wild giant: act {} at k {} (you {} from it, rift {})", actName(giantAct), k,
                    String.format("%.1f", Math.sqrt(mc.player.distanceToSqr(g.base().x, mc.player.getY(), g.base().z))), g.portalTicksLeft());
        }
        Vec3 base = g.base(), f = g.forward(), r = g.right();
        // the dig and the stand, from the side, back far enough to take it all in
        if (k == 2) tpLook(base.add(f.scale(48)).add(r.scale(32)), base.add(0, 14, 0));
        if (k > 2 && k < 176) lookAtPoint(mc, base.add(0, 10 + 20 * Math.min(1, k / 170.0), 0));
        if (k == 40 || k == 64 || k == 90 || k == 120 || k == 150 || k == 172) shot(mc, "w0" + (k / 30 + 1) + "_dig_" + k);
        // its walk (2026-10-06, on its legs): it goes after a husk far off while you watch it from the side
        if (k == 180) {
            run("gamemode creative", "kill @e[type=husk]");
            Vec3 bait = base.add(f.scale(36));
            run(String.format(java.util.Locale.ROOT, "execute positioned %.1f 0 %.1f positioned over motion_blocking_no_leaves run summon husk ~ ~ ~ "
                    + "{NoAI:1b,PersistenceRequired:1b,Invulnerable:1b}", bait.x, bait.z));
            tpGround(base.add(f.scale(18)).add(r.scale(50)), base.add(f.scale(14)).add(0, 22, 0));
        }
        if (k > 186 && k < 440) lookAtPoint(mc, g.pelvis(0).add(0, k % 80 < 40 ? 4 : -6, 0));
        mc.options.hideGui = k > 186 && k < 440;
        if (k >= 220 && k <= 420 && k % 25 == 0) shot(mc, String.format("w1%d_walk_%d", (k - 220) / 25, k));
        if (k == 430) shot(mc, "w19_walk_close");
        // the hunt: you in front of it, every act shot as it lands
        if (k == 440) {
            run("kill @e[type=husk]", "gamemode survival");
            tpGround(base.add(f.scale(36)), g.head(0));
        }
        if (k > 440 && k < 1500) lookAtPoint(mc, g.head(0).add(0, -18, 0));
        if (k == 470) run("papon giant stomp");
        if (k == 470 + 14 || k == 470 + 27 || k == 470 + 34) shot(mc, "w2" + (k - 470) / 14 + "_stomp_" + (k - 470));
        if (k == 540) tpGround(base.add(f.scale(24)), g.head(0));
        if (k == 550) run("papon giant swipe");
        if (k == 550 + 14 || k == 550 + 22) shot(mc, "w3" + (k - 550) / 14 + "_swipe_" + (k - 550));
        if (k == 620) run("papon giant slam2");
        if (k == 620 + 16 || k == 620 + 30) shot(mc, "w4" + (k - 620) / 16 + "_slam2_" + (k - 620));
        if (k == 700) run("papon giant laser");
        if (k == 720 || k == 740) shot(mc, "w5" + (k - 700) / 20 + "_laser");
        if (k == 800) tpGround(base.add(f.scale(36)), g.head(0));
        if (k == 810) run("papon giant lunge");
        if (k == 810 + 18 || k == 810 + 30) shot(mc, "w6" + (k - 810) / 18 + "_lunge_" + (k - 810));
        if (k == 900) tpGround(base.add(f.scale(50)), g.head(0));
        if (k == 910) run("papon giant grab");
        if (k == 910 + 60 || k == 910 + 100 || k == 910 + 130) shot(mc, "w7" + (k - 910) / 40 + "_grab_" + (k - 910));
        if (k == 1200 || k == 1300 || k == 1400) shot(mc, "w8" + (k - 1200) / 100 + "_free_" + k);
        // its rift closes: dragged under
        if (k == 1500) {
            tpGround(base.add(f.scale(44)).add(r.scale(18)), g.head(0));
            run("papon giant portal");
        }
        if (k > 1500) lookAtPoint(mc, base.add(0, 16, 0));
        if (k == 1680 || k == 1720 || k == 1760 || k == 1800) shot(mc, "w9" + (k - 1680) / 40 + "_drag_" + k);
        if (k >= 1900) mc.stop();
    }

    /** Stands you on the ground at x, z of {@code at}, looking at {@code look}. */
    private static void tpGround(Vec3 at, Vec3 look) {
        Minecraft mc = Minecraft.getInstance();
        double gy = mc.level == null ? at.y : mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
        double dx = look.x - at.x, dy = look.y - (gy + 1.62), dz = look.z - at.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        run(String.format(java.util.Locale.ROOT, "execute positioned %.2f 0 %.2f positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ %.1f %.1f", at.x, at.z, yaw, pitch));
    }

    private static void giantTest(Minecraft mc) {
        switch (tick) {
            case 40 -> run("gamemode survival", "effect give @s resistance 100000 4 true", "effect give @s saturation 100000 1 true",
                    "time set 5000", "gamerule doDaylightCycle false", "weather clear 100000", "gamerule doMobSpawning false",
                    "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "kill @e[type=papon:mariachi]",
                    "kill @e[type=papon:coliseum_crowd]", "tp @s 4000 200 4000");
            case 70 -> run("execute positioned 4000 0 4000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
            case 100 -> run("execute at @s rotated 0 0 run papon boss");
            case 220 -> run("papon bossstate IDLE", MID, "papon bossstate P2_KNEEL");
            default -> {}
        }
        PaponBossEntity b = nearest(mc, PaponBossEntity.class);
        if (b == null) return;
        if (giantMark < 0) {
            if (b.getBossState() == BossState.P2_ARRIVE && b.ticksInState() > 5 && nearest(mc, com.groid.papon.boss.ColiseumCrowdEntity.class) != null) {
                giantMark = tick;
                run("papon bossstate P4_RAGE", "effect give @s regeneration 100000 4 true");
            }
            return;
        }
        int k = tick - giantMark;
        var crowd = nearest(mc, com.groid.papon.boss.ColiseumCrowdEntity.class);
        if (crowd == null) return;
        Vec3 c = crowd.position();
        var g = nearest(mc, com.groid.papon.boss.SpiritGiantEntity.class);
        if (g != null && g.act() != giantAct) {
            giantAct = g.act();
            PaponMod.LOG.info("[autoshot] giant: act {} at k {} (you {} from it)", actName(giantAct), k,
                    String.format("%.1f", Math.sqrt(mc.player.distanceToSqr(g.base().x, mc.player.getY(), g.base().z))));
        }
        BossState s = b.getBossState();
        if (s.ordinal() != lastState) {
            lastState = s.ordinal();
            PaponMod.LOG.info("[autoshot] giant test: boss -> {} at k {} stage {}", s, k, b.p4StageShown());
        }
        if (k >= (TELLS ? 1500 : Boolean.getBoolean("papon.autoshot.long") ? 3100 : 1640)) mc.stop();
        // after the rage: the giant summoned, you at the far end from it, its stalk watched from the side
        if (k == 200) run("papon giant summon");
        if (g == null) return;
        Vec3 base = g.base(), f = g.forward(), r = g.right();
        double fy = c.y + 0.2;
        if (TELLS) {
            tellsPlan(mc, k, g, fy);
            return;
        }
        if (k == 340) tpLook(flat(base.add(f.scale(30)), fy), g.head(0));
        if (k > 340 && k < 420) lookAtPoint(mc, g.head(0).add(0, -6, 0));
        if (k == 360) shot(mc, "g00_stalk_a");
        if (k == 400) shot(mc, "g01_stalk_b");
        // the lunge, from in front and to one side of it (it comes for you)
        if (k == 430) tpLook(flat(base.add(f.scale(24)).add(r.scale(6)), fy), g.head(0));
        if (k == 440) run("papon giant lunge");
        if (k > 440 && k < 520) lookAtPoint(mc, g.head(0).add(0, -6, 0));
        if (k == 452 || k == 462 || k == 470 || k == 480 || k == 500) shot(mc, "g1" + (k - 440) / 10 + "_lunge_" + (k - 440));
        // La Mano: stand still under it; caught, lifted, blasted, slammed (the struggle not pressed)
        if (k == 560) tpLook(flat(base.add(f.scale(26)), fy), g.head(0));
        if (k == 570) run("papon giant grab");
        if (k >= 570 && k < 800 && g.act() == com.groid.papon.boss.SpiritGiantEntity.GRAB) {
            int ga = (int) g.actTime(0);
            if (ga < com.groid.papon.boss.SpiritGiantEntity.GRAB_CLOSE) lookAtPoint(mc, g.hand(g.grabHand(), 0));
            for (int at : new int[]{14, 40, 60, 70, 80, 92, 100, 115, 130, 143, 148, 158, 163, 175, 200}) {
                if (ga == at) once(mc, String.format("g2%03d_grab", at));
            }
            if (ga == 100 && !mc.player.isPassenger()) PaponMod.LOG.info("[autoshot] giant test: NOT caught by La Mano");
            if (ga % 10 == 0 && ga >= 60) {
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                PaponMod.LOG.info("[autoshot] grab t {} cam {} me {} head {} hand {} base {} fwd {} eyeL {}", ga, v3(cam), v3(mc.player.position()),
                        v3(g.head(0)), v3(g.hand(g.grabHand(), 0)), v3(g.base()), v3(g.forward()), v3(g.eye(-1, 0)));
            }
        }
        // again, and this time struggle free
        if (k == 900) tpLook(flat(g.base().add(g.forward().scale(26)), fy), g.head(0));
        if (k == 910) run("papon giant grab");
        if (k > 910 && k < 1150 && mc.player.getVehicle() instanceof com.groid.papon.boss.SpiritGiantEntity gg && k % 3 == 0)
            com.groid.papon.network.PaponNetwork.CHANNEL.sendToServer(new com.groid.papon.network.PaponNetwork.Struggle());
        if (k > 910 && k < 1150 && g.act() == com.groid.papon.boss.SpiritGiantEntity.GRAB) {
            int ga = (int) g.actTime(0);
            if (ga == 85 || ga == 100 || ga == 120) once(mc, String.format("g3%03d_struggle", ga));
        }
        // (2026-10-06) its body is solid: stood in its ribcage you are thrown out
        if (k == 1160) tpLook(flat(g.base().add(g.forward().scale(1.2)), fy), g.head(0));
        if (k == 1175) {
            PaponMod.LOG.info("[autoshot] giant body: you are {} from its middle (thrown out if > 6.5)",
                    String.format("%.1f", Math.sqrt(mc.player.distanceToSqr(g.base().x, mc.player.getY(), g.base().z))));
            shot(mc, "g50_body");
        }
        // its rift's clock run short: the ring burning down, the dead reaching up for it; then dragged under
        if (k == 1186) run("gamemode creative", "effect clear @s");         // out of its way: watched, not hunted (still in the arena)
        if (k == 1190) {
            run("papon giant portal");
            giantWatch = g.base();
            // the floor where we will stand: the first solid block down from well over it
            // from the middle of the arena (the crowd entity's place), looking at it
            Vec3 at = new Vec3(c.x, g.base().y, c.z);
            giantWatchAt = at;
            giantFloor = g.base().y;
            for (int y = (int) g.base().y + 12; y > g.base().y - 60; y--) {
                if (!mc.level.getBlockState(net.minecraft.core.BlockPos.containing(at.x, y, at.z)).isAir()) {
                    giantFloor = y + 1;
                    break;
                }
            }
            PaponMod.LOG.info("[autoshot] giant watch: floor {} (its base {})", giantFloor, String.format("%.1f", g.base().y));
            giantWatchF = g.forward();
            giantWatchR = g.right();
        }
        if (giantWatch != null && k >= 1190 && k < 1700 && k % 20 == 10)
        {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            tpLook(flat(giantWatchAt.lerp(giantWatch, 0.4), giantFloor + 24), flat(giantWatch, giantFloor + 4));
        }
        if (giantWatch != null && k > 1190 && k < 1700) lookAtPoint(mc, flat(giantWatch, giantFloor + 4));
        if (k == 1230 || k == 1280 || k == 1320) shot(mc, "g5" + (k - 1190) / 40 + "_clock_" + (k - 1190));
        if (k > 1190 && k < 1700 && g.act() == com.groid.papon.boss.SpiritGiantEntity.SINK) {
            int ga = (int) g.actTime(0);
            for (int at : new int[]{6, 20, 34, 48, 58, 64, 76, 92, 104, 116, 128, 140}) if (ga == at) once(mc, String.format("g6%03d_drag", at));
        }
        // then on its own: you walk about the arena; every act logged, shots every 4 s
        if (k > 1700 && k < 3100) {
            if (k % 160 == 0) {
                double a = k * 0.05;
                tpLook(new Vec3(c.x + Math.cos(a) * 22, fy, c.z + Math.sin(a) * 12), g.head(0));
            }
            if (k % 160 > 0) lookAtPoint(mc, g.head(0).add(0, -8, 0));
            if (k % 80 == 40) shot(mc, String.format("g4%04d_free_%s", k, actName(g.act())));
        }
    }

    private static String v3(Vec3 v) { return String.format(java.util.Locale.ROOT, "(%.1f %.1f %.1f)", v.x, v.y, v.z); }

    private static Vec3 skyAt;

    /** The Skyline from eight sides at its own height, three close-ups, from above, then driven: first person and behind. */
    private static void skyline(Minecraft mc) {
        if (tick == 40) run("gamemode spectator", "time set 6000", "gamerule doDaylightCycle false", "weather clear 100000",
                "gamerule doMobSpawning false", "kill @e[type=papon:caravan]", "kill @e[type=papon:papon_boss]", "tp @s 6000 200 6000");
        // (well away from 4000 4000, where the other tours leave their coliseum standing)
        if (tick == 70) run("execute positioned 6000 0 6000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
        // a clear, flat lot to shoot it on
        if (tick == 80) run("execute at @s run fill ~-16 ~ ~-16 ~16 ~14 ~16 air", "execute at @s run fill ~-16 ~-1 ~-16 ~16 ~-1 ~16 minecraft:smooth_stone",
                // and a road on ahead of it for the test drive
                "execute at @s run fill ~-10 ~ ~16 ~10 ~6 ~150 air", "execute at @s run fill ~-10 ~-1 ~16 ~10 ~-1 ~150 minecraft:smooth_stone");
        if (tick == 90) run("execute at @s run summon papon:caravan ~ ~ ~ {Skyline:1b,Rotation:[0f,0f]}");
        var car = nearest(mc, com.groid.papon.vehicle.CaravanEntity.class);
        if (tick % 20 == 0) PaponMod.LOG.info("[autoshot] skyline tick {} car {} fps {} me {}", tick, car == null ? "none" : v3(car.position()), mc.getFps(), v3(mc.player.position()));
        // (once it has been seen the tour goes on without it: the fob's part puts it away and summons another)
        if (tick < 110 || (car == null && skyAt == null)) return;
        if (skyAt == null) skyAt = car.position();
        Vec3 c = skyAt;
        int k = tick - 110;
        if (k < 8 * 30) {
            int i = k / 30;
            double a = Math.toRadians(i * 45);
            Vec3 eye = c.add(-Math.sin(a) * 7.5, 0.1, Math.cos(a) * 7.5);
            if (k % 30 == 0) tpLook(eye, c.add(0, 0.7, 0));
            if (k % 30 == 24) shot(mc, "k0" + i + "_orbit_" + (i * 45));
            return;
        }
        k -= 8 * 30;
        Vec3[][] close = {{c.add(-2.6, 0.9, 4.6), c.add(0, 1.0, 1.4)}, {c.add(3.8, 0.4, -4.4), c.add(0, 0.9, -1.2)},
                {c.add(4.2, 0.3, 0.2), c.add(0, 0.8, 0)}, {c.add(0.01, 9, 0), c}, {c.add(-0.5, -0.23, -0.2), c.add(-0.5, 1.2, 4)},
                {c.add(0.45, -0.2, -0.3), c.add(-0.6, 1.0, 0.4)}};
        String[] names = {"k10_rear_glass", "k11_front", "k12_side", "k13_top", "k14_cabin_driver_eye", "k15_cabin_across"};
        if (k < close.length * 30) {
            int i = k / 30;
            if (k % 30 == 0) tpLook(close[i][0], close[i][1]);
            if (k % 30 == 24) shot(mc, names[i]);
            return;
        }
        k -= close.length * 30;
        // the driver's door opening (someone getting in: an armor stand), from both sides and from the front
        if (k < 60) {
            if (k == 0) tpLook(c.add(-4.2, 0.6, -3.6), c.add(-0.6, 0.9, 0));
            if (k == 2) run("summon armor_stand " + c.x + " " + (c.y + 3) + " " + c.z + " {Tags:[\"rider\"],Invisible:1b}",
                    "ride @e[tag=rider,limit=1] mount @e[type=papon:caravan,limit=1,sort=nearest]");
            if (k == 14) shot(mc, "k16_door_open_a");
            if (k == 15) tpLook(c.add(4.2, 0.6, -3.6), c.add(0.6, 0.9, 0));
            if (k == 19) shot(mc, "k17_door_open_b");
            if (k == 20) tpLook(c.add(0.3, 1.6, -5.5), c.add(0, 0.8, 0));
            if (k == 24) shot(mc, "k18_door_open_front");
            if (k == 30) run("kill @e[tag=rider]");
            return;
        }
        k -= 60;
        if (k == 0) run("gamemode creative", "tp @s " + c.x + " " + c.y + " " + (c.z + 2), "ride @s mount @e[type=papon:caravan,limit=1,sort=nearest]");
        if (k == 10) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (k > 10 && k < 120 && car != null) {
            mc.player.setYRot(car.getYRot());
            mc.player.setXRot(k < 60 ? 8 : 25);
        }
        if (k == 40) shot(mc, "k20_driver_seat");
        if (k == 80) shot(mc, "k21_driver_dash");
        if (k == 100) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (k == 130) shot(mc, "k22_behind");
        if (k == 150) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (k == 180) shot(mc, "k23_front_seat");
        // a drive: foot down for five seconds, then off; the engine's simulation logged (rpm, gear, speed)
        if (k == 190) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (k == 200) mc.options.keyUp.setDown(true);
        if (k == 300) mc.options.keyUp.setDown(false);
        if (k >= 200 && k <= 380 && k % 5 == 0 && car != null) PaponMod.LOG.info("[autoshot] drive k {} rpm {} speed {} braking {}", k,
                (int) com.groid.papon.client.CaravanSounds.rpm(car), String.format("%.2f", car.getDeltaMovement().horizontalDistance()), car.braking);
        if (k == 260) shot(mc, "k24_driving");
        // the fob: out of the car, the car put away, pressed in the air: it reforms beside you
        if (k == 400) run("ride @s dismount", "kill @e[type=papon:caravan]", "clear @s", "item replace entity @s weapon.mainhand with papon:skyline_fob");
        if (k == 410) { run("tp @s " + c.x + " " + c.y + " " + (c.z + 30) + " 90 25"); mc.options.setCameraType(CameraType.FIRST_PERSON); }
        if (k == 420) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (k == 423) shot(mc, "k25_summon_a");
        if (k == 428) shot(mc, "k26_summon_b");
        if (k == 434) shot(mc, "k27_summon_c");
        if (k == 470) shot(mc, "k28_summoned");
        if (k == 500) mc.stop();
    }

    private static void skyshake(Minecraft mc) {
        if (tick == 40) run("gamemode creative", "time set 6000", "gamerule doDaylightCycle false", "weather clear 100000",
                "gamerule doMobSpawning false", "kill @e[type=papon:caravan]", "tp @s 6000 200 6000");
        if (tick == 70) run("execute positioned 6000 0 6000 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
        if (tick == 80) run("execute at @s run fill ~-16 ~ ~-16 ~16 ~14 ~16 air", "execute at @s run fill ~-16 ~-1 ~-16 ~16 ~-1 ~16 minecraft:smooth_stone",
                "execute at @s run fill ~-10 ~ ~16 ~10 ~6 ~150 air", "execute at @s run fill ~-10 ~-1 ~16 ~10 ~-1 ~150 minecraft:smooth_stone",
                "item replace entity @s weapon.mainhand with papon:skyline_fob");
        // the fob: it reforms ahead of you, side on, the engine running
        if (tick == 95) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        var car = nearest(mc, com.groid.papon.vehicle.CaravanEntity.class);
        if (tick == 140 && car != null) tpLook(car.position().add(4.5, 0.2, 3.0), car.position().add(0, 0.8, 0));
        if (tick == 150) shakePhase = "parked_idle";
        for (int i = 0; i < 4; i++) if (tick == 170 + i) shot(mc, "q0" + i + "_parked_idle");
        if (tick == 210) {
            shakePhase = "";
            // a fresh one facing down the road for the drive (the summoned one stands side on)
            run("kill @e[type=papon:caravan]", "execute positioned 6000.5 ~ 6004.5 run summon papon:caravan ~ ~ ~ {Skyline:1b,Rotation:[0f,0f]}");
        }
        if (tick == 212) run("ride @s mount @e[type=papon:caravan,limit=1,sort=nearest]");
        if (tick == 215) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (tick > 215 && tick < 240 && car != null) { mc.player.setYRot(car.getYRot()); mc.player.setXRot(8); }
        if (tick == 245) shakePhase = "seat_idle";
        for (int i = 0; i < 4; i++) if (tick == 265 + i) shot(mc, "q1" + i + "_seat_idle");
        // up at the rear-view mirror: its pad on the glass, the stem, the housing
        if (tick == 275 && car != null) { mc.player.setYRot(car.getYRot() - 28); mc.player.setXRot(-32); }
        if (tick == 280) shot(mc, "q15_mirror");
        if (tick == 285 && car != null) { mc.player.setYRot(car.getYRot()); mc.player.setXRot(8); }
        if (tick == 300) { shakePhase = "accelerating"; mc.options.keyUp.setDown(true); }
        for (int i = 0; i < 4; i++) if (tick == 330 + i) shot(mc, "q2" + i + "_accel");
        if (tick == 380) shakePhase = "flat_out";
        for (int i = 0; i < 4; i++) if (tick == 395 + i) shot(mc, "q3" + i + "_flat_out");
        if (tick == 420) { shakePhase = "coasting"; mc.options.keyUp.setDown(false); }
        for (int i = 0; i < 4; i++) if (tick == 440 + i) shot(mc, "q4" + i + "_coast");
        if (tick == 480) shakePhase = "";
        if (tick % 10 == 0 && car != null) PaponMod.LOG.info("[autoshot] skyshake {} car {} roll {} pitch {} rpm {}", tick, v3(car.position()),
                car.bodyRoll, car.bodyPitch, (int) com.groid.papon.client.CaravanSounds.rpm(car));
        if (tick == 500) mc.stop();
    }

    private static float mirrorYaw = Float.NaN, mirrorPitch;

    private static void mirrors(Minecraft mc) {
        if (tick == 40) run("gamemode creative", "time set 6000", "gamerule doDaylightCycle false", "weather clear 100000",
                "gamerule doMobSpawning false", "kill @e[type=papon:caravan]", "tp @s 6300 200 6300");
        if (tick == 70) run("execute positioned 6300 0 6300 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 0 0");
        if (tick == 80) run("execute at @s run fill ~-16 ~ ~-30 ~16 ~14 ~5 air", "execute at @s run fill ~-16 ~ ~6 ~16 ~14 ~40 air",
                "execute at @s run fill ~-16 ~-1 ~-30 ~16 ~-1 ~40 minecraft:smooth_stone",
                "execute at @s run fill ~4 ~ ~-12 ~5 ~5 ~-11 minecraft:red_concrete", "execute at @s run fill ~-5 ~ ~-12 ~-4 ~5 ~-11 minecraft:blue_concrete",
                "execute at @s run fill ~-1 ~ ~-17 ~1 ~3 ~-16 minecraft:yellow_concrete", "kill @e[type=armor_stand]",
                "execute at @s run summon armor_stand ~1.5 ~ ~-6 {Rotation:[0f,0f],ShowArms:1b}");
        if (tick == 95) run("execute at @s run summon papon:caravan ~ ~ ~ {Skyline:1b,Rotation:[0f,0f]}");
        if (tick == 100) run("ride @s mount @e[type=papon:caravan,limit=1,sort=nearest]");
        if (tick == 102) mc.options.setCameraType(CameraType.FIRST_PERSON);
        var car = nearest(mc, com.groid.papon.vehicle.CaravanEntity.class);
        // where to look (from the car's heading) and the shot's name, every 12 ticks
        String[][] looks = {{"0", "8", "m01_ahead"}, {"-28", "-30", "m02_rearview"}, {"-20", "-22", "m03_rearview_b"}, {"-63", "7", "m04_left"},
                {"-55", "4", "m05_left_b"}, {"38", "14", "m06_right"}, {"52", "18", "m07_right_b"}};
        for (int i = 0; i < looks.length; i++) {
            int at = 130 + i * 12;
            if (tick == at) { mirrorYaw = Float.parseFloat(looks[i][0]); mirrorPitch = Float.parseFloat(looks[i][1]); }
            if (tick == at + 10) shot(mc, looks[i][2]);
        }
        if (car != null && !Float.isNaN(mirrorYaw) && mc.player.getVehicle() == car) { mc.player.setYRot(car.getYRot() + mirrorYaw); mc.player.setXRot(mirrorPitch); }
        if (tick == 220) { mirrorYaw = Float.NaN; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (tick == 222 && car != null) { mc.player.setYRot(car.getYRot()); mc.player.setXRot(12); }
        if (tick == 235) shot(mc, "m08_behind");
        if (tick == 240) { mc.options.setCameraType(CameraType.FIRST_PERSON); mirrorYaw = -28; mirrorPitch = -30; }
        if (tick == 250) mc.options.keyUp.setDown(true);
        if (tick == 268) shot(mc, "m09_pulling_away");
        if (tick == 280) mc.options.keyUp.setDown(false);
        if (tick == 282) shot(mc, "m10_further");
        // the frame rate with 0, 1, 2 and 3 passes, back in the seat looking at the rear-view mirror (the setting put back after)
        if (tick == 340) { run("ride @s mount @e[type=papon:caravan,limit=1,sort=nearest]"); mirrorYaw = -20; mirrorPitch = -22; }
        for (int i = 0; i < 4; i++) if (tick == 350 + i * 40) com.groid.papon.PaponConfig.MIRROR_PASSES.set(i);
        if (tick == 509) com.groid.papon.PaponConfig.MIRROR_PASSES.set(2);
        if (tick > 350 && tick % 40 == 39) PaponMod.LOG.info("[autoshot] mirrors fps {} with {} passes", mc.getFps(), com.groid.papon.PaponConfig.MIRROR_PASSES.get());
        if (tick % 10 == 0) PaponMod.LOG.info("[autoshot] mirrors {} passes {} {} ms a pass, {} fps, setting {}", tick, com.groid.papon.client.SkylineMirrors.passCount,
                com.groid.papon.client.SkylineMirrors.passMillis, mc.getFps(), com.groid.papon.PaponConfig.MIRROR_PASSES.get());
        // out of it: by its left mirror, behind and beside, looking into the glass (you should see yourself)
        if (tick == 295) run("ride @s dismount");
        if (tick == 300 && car != null) {
            Vec3 f = Vec3.directionFromRotation(0, car.getYRot()), l = new Vec3(f.z, 0, -f.x);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            tpLook(car.position().add(l.scale(1.9)).add(f.scale(-0.4)).add(0, -0.2, 0), car.position().add(l.scale(1.2)).add(f.scale(0.65)).add(0, 1.2, 0));
        }
        if (tick == 315) shot(mc, "m11_outside_left");
        if (tick == 318 && car != null) {
            Vec3 f = Vec3.directionFromRotation(0, car.getYRot()), l = new Vec3(f.z, 0, -f.x);
            tpLook(car.position().add(l.scale(2.6)).add(f.scale(-3.5)).add(0, -0.4, 0), car.position().add(l.scale(1.2)).add(f.scale(0.65)).add(0, 1.2, 0));
        }
        if (tick == 333) shot(mc, "m12_outside_left_far");
        if (tick == 510) mc.stop();
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

    private static void lookAtPoint(Minecraft mc, Vec3 p) {
        Vec3 d = p.subtract(mc.player.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
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
        return mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(200)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
    }

    static {
        if (ENABLED) PaponMod.LOG.info("[autoshot] on: screenshots go to run/screenshots/papon_*.png");
    }
}
