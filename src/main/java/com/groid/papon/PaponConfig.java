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
        SPEC = b.build();
    }

    private PaponConfig() {}
}
