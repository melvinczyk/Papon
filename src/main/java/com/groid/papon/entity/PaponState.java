package com.groid.papon.entity;

/**
 * The Wandering Papon's journey. Synced to clients as its ordinal, which picks the animation,
 * and saved by name. New states go at the END so existing ordinals and saves stay valid.
 *
 * <pre>
 * RECEIVING --> WALKING <--> SITTING_DOWN --> GRILLING --> PACKING_UP --> WALKING ...
 *                  |  \--> RAFT_BOARDING --> RAFTING <--> RAFT_IDLE ;  RAFTING --> RAFT_LANDING --> WALKING
 *                  |  \--> DRINKING --> WALKING (boosted) ... TIRED --> WALKING
 *                  |  \--> SMASHING --> WALKING                       (the yellow one)
 *                  |  \--> SLEEP_SETUP --> SLEEPING <--> SLEEP_FLINCH  (night; chancla)
 *                  |                         |--> WAKING --> WALKING      (morning)
 *                  |                         \--> ANGRY_WAKE --> WALKING  (2% chancla)
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
    WAITING(0),
    RAFT_BOARDING(32),
    RAFTING(0),
    RAFT_IDLE(0),
    RAFT_LANDING(28),
    DRINKING(48),
    SMASHING(60),
    TIRED(52),
    SLEEP_SETUP(90),
    SLEEPING(0),
    SLEEP_FLINCH(16),
    WAKING(80),
    ANGRY_WAKE(52);

    /** Ticks this state lasts before it advances on its own; 0 = until something else moves it on. */
    public final int duration;

    PaponState(int duration) {
        this.duration = duration;
    }

    /** States in which he stays put on land and keeps facing one way. */
    public boolean isSeated() {
        return switch (this) {
            case SITTING_DOWN, GRILLING, PACKING_UP, DRINKING, SMASHING, TIRED,
                 SLEEP_SETUP, SLEEPING, SLEEP_FLINCH, WAKING, ANGRY_WAKE -> true;
            default -> false;
        };
    }

    /** On the llama raft: the entity floats at the water line and is steered by hand, not pathfinding. */
    public boolean isRafting() {
        return this == RAFT_BOARDING || this == RAFTING || this == RAFT_IDLE || this == RAFT_LANDING;
    }

    public boolean isAsleep() {
        return this == SLEEP_SETUP || this == SLEEPING || this == SLEEP_FLINCH;
    }

    public static PaponState byId(int id) {
        PaponState[] all = values();
        return id >= 0 && id < all.length ? all[id] : WALKING;
    }
}
