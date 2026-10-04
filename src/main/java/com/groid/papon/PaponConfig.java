package com.groid.papon;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PaponConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue JOURNEY_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue JOURNEY_MAX_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue REUSE_EXISTING_TRUCKS;
    public static final ForgeConfigSpec.IntValue ESCORT_RADIUS;
    public static final ForgeConfigSpec.IntValue RESUME_RADIUS;
    public static final ForgeConfigSpec.IntValue LONELY_DELAY_TICKS;
    public static final ForgeConfigSpec.BooleanValue SHOW_JOURNEY_BAR;
    public static final ForgeConfigSpec.IntValue EXTRA_PAPONS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PLAYER_IN_LINE;
    public static final ForgeConfigSpec.DoubleValue LINE_SLACK;
    public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH;
    public static final ForgeConfigSpec.DoubleValue BOSS_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue VAN_RAM_DAMAGE;
    public static final ForgeConfigSpec.IntValue VAN_COOLDOWN;
    public static final ForgeConfigSpec.IntValue VAN_STUN_TICKS;
    public static final ForgeConfigSpec.DoubleValue HIT_CAP;
    public static final ForgeConfigSpec.DoubleValue TRUE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BURST_CAP;
    public static final ForgeConfigSpec.DoubleValue ARMOR_PIERCE;
    public static final ForgeConfigSpec.BooleanValue CINEMATICS;
    public static final ForgeConfigSpec.BooleanValue BOSS_MUSIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("journey");
        JOURNEY_MIN_DISTANCE = b.comment("Closest a new Taco Truck site may be to where the coupon was handed over, in blocks.")
                .defineInRange("minDistance", 800, 64, 100_000);
        // Both distances can also be changed in game with /papon distance <min> [max].
        JOURNEY_MAX_DISTANCE = b.comment("Farthest a new Taco Truck site may be, in blocks.")
                .defineInRange("maxDistance", 1200, 64, 100_000);
        REUSE_EXISTING_TRUCKS = b.comment("Send a new Wandering Papon to the nearest Taco Truck that already stands (within maxDistance) instead of a fresh site.")
                .define("reuseExistingTrucks", true);
        b.pop();
        b.push("escort");
        ESCORT_RADIUS = b.comment("A player within this many blocks keeps Papon walking.")
                .defineInRange("escortRadius", 16, 4, 128);
        RESUME_RADIUS = b.comment("While grilling, a player must come this close before he packs up. Smaller than escortRadius so he does not flicker between the two.")
                .defineInRange("resumeRadius", 10, 2, 128);
        LONELY_DELAY_TICKS = b.comment("Ticks with nobody in escortRadius before he sits down to grill.")
                .defineInRange("lonelyDelayTicks", 40, 0, 20 * 60);
        SHOW_JOURNEY_BAR = b.comment("Show nearby players a boss bar with the distance left to the Taco Truck.")
                .define("showJourneyBar", true);
        b.pop();
        b.push("truck");
        EXTRA_PAPONS = b.comment("Plain Papons in line at the Taco Truck alongside Don Pollo, EDP445, Badlands Chugs and Reviewbrah.",
                        "The line is the countdown to the boss fight: each customer adds roughly 10 seconds.")
                .defineInRange("extraPapons", 3, 0, 8);
        REQUIRE_PLAYER_IN_LINE = b.comment("The line only moves while a player stands in it, behind the Wandering Papon.",
                        "If false, any player within 32 blocks of the window keeps it moving.")
                .define("requirePlayerInLine", true);
        LINE_SLACK = b.comment("How far from their spot in line, in blocks, a player may stand and still count as in line.")
                .defineInRange("lineSlack", 2.5, 1.0, 8.0);
        b.pop();
        b.push("boss");
        BOSS_HEALTH = b.comment("Chef Papon's health. Half of it gone starts his second phase.")
                .defineInRange("health", 800.0, 50.0, 100_000.0);
        BOSS_DAMAGE = b.comment("Multiplier on all of Chef Papon's attacks (spatula, belly slam, fire breath, molotovs).")
                .defineInRange("damageMultiplier", 1.0, 0.1, 20.0);
        VAN_RAM_DAMAGE = b.comment("Damage of a full-speed hit from the van (less when it is slower). Shields do not block it.")
                .defineInRange("vanRamDamage", 22.0, 1.0, 1000.0);
        VAN_COOLDOWN = b.comment("Ticks between van attacks in phase one (two thirds of this in phase two).")
                .defineInRange("vanCooldownTicks", 360, 40, 20 * 60 * 10);
        // renamed from vanStunTicks (old default 160) so configs written before 0.6.2 fall back to 3 s
        VAN_STUN_TICKS = b.comment("Ticks any of his stuns (van hits, the suplex, crashes) leave the victim flat on the ground, unable to move or jump. 60 = 3 s.")
                .defineInRange("stunTicks", 60, 0, 20 * 60);
        HIT_CAP = b.comment("The most of his maximum health one hit can take off Chef Papon (0.05 = 5%): nobody one-shots",
                        "him or skips a phase, whatever their damage. 1.0 turns it off.")
                .defineInRange("hitCapFraction", 0.05, 0.001, 1.0);
        TRUE_DAMAGE = b.comment("Multiplier on his mechanics' damage: the hits that take a share of your maximum health through",
                        "armor (drunken attacks, Habanero, the burrito, the gravity spud). Independent of damageMultiplier.")
                .defineInRange("trueDamageMultiplier", 1.0, 0.0, 10.0);
        BURST_CAP = b.comment("His composure: the most of his maximum health he can lose in a burst (it drains 2.5% a tick, so",
                        "0.08 lets about 4% a second through). Past it, hits glance off: big damage does not skip his mechanics.",
                        "In his open windows (winded after a big move, staggered, stumbling, spent) he can lose three times as much.",
                        "1.0 turns it off. (Renamed from burstCapFraction in 0.10.0: the old, much tighter 0.04 is dropped.)")
                .defineInRange("composureFraction", 0.08, 0.001, 1.0);
        ARMOR_PIERCE = b.comment("His heavy hits also take this share of your maximum health, through armor (more in phase two):",
                        "no amount of armor lets you stand and trade with him. 0 turns it off.")
                .defineInRange("armorPierceFraction", 0.06, 0.0, 1.0);
        CINEMATICS = b.comment("Play the unmasking and the boss intro as cutscenes (camera, letterbox) for players nearby.")
                .define("cinematics", true);
        BOSS_MUSIC = b.comment("Play the mariachi boss music during the fight.")
                .define("bossMusic", true);
        b.pop();
        SPEC = b.build();
    }

    private PaponConfig() {}
}
