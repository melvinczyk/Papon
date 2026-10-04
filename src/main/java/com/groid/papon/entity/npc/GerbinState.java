package com.groid.papon.entity.npc;

/**
 * Gerbin, the dishwasher. Synced as its ordinal; durations of timed states are whole loops of
 * their animations in gerbin.animation.json (chop 6 s, wash 5 s, look 3 s, shocked 2 s).
 *
 * <pre>
 * TO_CHOP --> CHOP (3 loops) --> [LOOK] --> TO_SINK --> WASH (3 loops) --> [LOOK] --> TO_CHOP ...
 * any --> SHOCKED --> COWER       (the fraud, and while the boss is loose; back to work after)
 * any --> DELIVER --> GIVE [--> DELIVER ...] --> back to work
 *                                 (the boss beaten: he brings each winner a body pillow)
 * </pre>
 * Append new states at the end: the ordinal is synced and the name is saved.
 */
public enum GerbinState {
    IDLE(0),
    TO_CHOP(0),
    CHOP(360),
    TO_SINK(0),
    WASH(300),
    LOOK(60),
    SHOCKED(40),
    COWER(0),
    DELIVER(0),
    GIVE(60);

    public final int duration;

    GerbinState(int duration) {
        this.duration = duration;
    }

    public static GerbinState byId(int id) {
        GerbinState[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }

    public boolean scared() {
        return this == SHOCKED || this == COWER;
    }
}
