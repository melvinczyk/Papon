package com.groid.papon.entity.npc;

/**
 * The Taco Truck vendor. Synced as its ordinal; durations of one-shot states equal their
 * animations' lengths in papon_npc.animation.json (0 = until something moves it on).
 *
 * <pre>
 * IDLE --> TAKING_ORDER --> COOKING --> SERVING --> IDLE ...      (each customer)
 *  |
 *  +--> INSPECTING --> FRAUD --> EXPOSED --> UNMASK --> REALIZE --> RAGE --> the boss
 *                                                                (the Wandering Papon's coupon)
 *
 * Beaten, he is back at the window, friendly (TacoVendorEntity#reform): IDLE --> COOKING --> SERVING
 * for every taco ordered off his menu (free), and GREETING whoever he welcomes back.
 * </pre>
 * FRAUD: he slams the counter. EXPOSED: the game is up (sweat, darting eyes). UNMASK: rips off his
 * fake mustache and lifts off his toque: Norwood 7. REALIZE: it dawns on him that everyone saw.
 * RAGE: he loses it; at its end TacoBoss.startFight swaps him for the boss, who leaps out of the
 * window. FURIOUS is the old dead end (saves from before the fight existed move on to EXPOSED).
 * Append new states at the end: the ordinal is synced and the name is saved.
 */
public enum VendorState {
    IDLE(0),
    TAKING_ORDER(0),
    COOKING(0),
    SERVING(30),
    INSPECTING(150),
    FRAUD(60),
    FURIOUS(0),
    EXPOSED(60),
    UNMASK(100),
    REALIZE(60),
    RAGE(70),
    /** Reformed: a big wave and a welcome for the one who beat him. */
    GREETING(50);

    public final int duration;

    VendorState(int duration) { this.duration = duration; }

    /** From the fraud until he turns into the boss. */
    public boolean unmasking() { return this == FRAUD || this == FURIOUS || this == EXPOSED || this == UNMASK || this == REALIZE || this == RAGE; }

    public static VendorState byId(int id) {
        VendorState[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }
}
