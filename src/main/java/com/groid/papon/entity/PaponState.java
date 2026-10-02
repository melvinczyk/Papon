package com.groid.papon.entity;

/**
 * The Wandering Papon's journey. Synced to clients as its ordinal, which picks the animation.
 *
 * <pre>
 * RECEIVING --> WALKING <--> SITTING_DOWN --> GRILLING --> PACKING_UP --> WALKING ...
 *                  |
 *                  +--> ARRIVED --> WAITING   (at the Taco Truck, forever)
 * </pre>
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
    WAITING(0);

    /** Ticks this state lasts before it advances on its own; 0 = until something else moves it on. */
    public final int duration;

    PaponState(int duration) {
        this.duration = duration;
    }

    /** States in which he stays put and keeps facing one way. */
    public boolean isSeated() {
        return this == SITTING_DOWN || this == GRILLING || this == PACKING_UP;
    }

    public static PaponState byId(int id) {
        PaponState[] all = values();
        return id >= 0 && id < all.length ? all[id] : WALKING;
    }
}
