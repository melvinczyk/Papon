package com.groid.papon.entity;

/**
 * The Wandering Papon's journey. Synced to clients as its ordinal, which picks the animation.
 *
 * <pre>
 * RECEIVING --> WALKING <--> SITTING_DOWN --> GRILLING --> PACKING_UP --> WALKING ...
 *                  |
 *                  +--> ARRIVED --> QUEUED --> PRESENTING --> BUSTED   (in line at the Taco Truck,
 *                          |                                            his coupon, the fraud)
 *                          +--> WAITING   (a truck with nobody at the window: forever)
 * </pre>
 *
 * Append new states at the end: the ordinal is synced and the name is saved.
 *
 * Durations are the lengths of the matching one-shot animations in ticks, so the server hands
 * over exactly as the client's animation finishes.
 */
public enum PaponState {
    RECEIVING(84),
    WALKING(0),
    SITTING_DOWN(60),
    GRILLING(0),
    PACKING_UP(48),
    ARRIVED(64),
    WAITING(0),
    QUEUED(0),
    PRESENTING(48),
    BUSTED(0);

    /** Ticks this state lasts before it advances on its own; 0 = until something else moves it on. */
    public final int duration;

    PaponState(int duration) {
        this.duration = duration;
    }

    /** States in which he stays put and keeps facing one way. */
    public boolean isSeated() {
        return this == SITTING_DOWN || this == GRILLING || this == PACKING_UP;
    }

    /** States at the Taco Truck, where he is done travelling (and may stand under its awning). */
    public boolean atTruck() {
        return this == ARRIVED || this == WAITING || this == QUEUED || this == PRESENTING || this == BUSTED;
    }

    public static PaponState byId(int id) {
        PaponState[] all = values();
        return id >= 0 && id < all.length ? all[id] : WALKING;
    }
}
