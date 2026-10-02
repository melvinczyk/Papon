package com.groid.papon.entity.npc;

/**
 * The Taco Truck vendor. Synced as its ordinal; durations of one-shot states equal their
 * animations' lengths in papon_npc.animation.json (0 = until something moves it on).
 *
 * <pre>
 * IDLE --> TAKING_ORDER --> COOKING --> SERVING --> IDLE ...      (each customer)
 *  |
 *  +--> INSPECTING --> FRAUD --> FURIOUS                         (the Wandering Papon's coupon)
 * </pre>
 * Append new states at the end: the ordinal is synced and the name is saved.
 */
public enum VendorState {
    IDLE(0),
    TAKING_ORDER(0),
    COOKING(0),
    SERVING(30),
    INSPECTING(150),
    FRAUD(60),
    FURIOUS(0);

    public final int duration;

    VendorState(int duration) {
        this.duration = duration;
    }

    public static VendorState byId(int id) {
        VendorState[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }
}
