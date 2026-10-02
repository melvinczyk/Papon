package com.groid.papon.boss;

import com.groid.papon.PaponMod;
import com.groid.papon.entity.WanderingPaponEntity;
import com.groid.papon.entity.npc.TacoVendorEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the boss fight starts. The line has run out, the Wandering Papon has handed his Taco
 * Coupon through the window, and the Papon working the truck has found it to be a fake: he is in
 * his FURIOUS loop, the Wandering Papon is BUSTED, the other customers have fled.
 *
 * Not built yet. The fight (Cataclysm-style attack states with frame-timed hits) and its intro
 * cutscene (Block Factory's Bosses-style camera bone + cinematic instruction keyframes) take over
 * from here; see CLAUDE.md "Reference mods". The vendor is the natural boss: he is already 1.5x,
 * already furious, and the truck is his arena.
 */
public final class TacoBoss {
    private TacoBoss() {}

    public static void onFraud(ServerLevel level, TacoVendorEntity vendor, WanderingPaponEntity wanderer) {
        PaponMod.LOG.info("[boss] fraud at the Taco Truck {} - the fight would start here", vendor.blockPosition());
    }
}
