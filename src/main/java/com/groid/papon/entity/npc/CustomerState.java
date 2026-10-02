package com.groid.papon.entity.npc;

/**
 * A customer at the Taco Truck. Synced as its ordinal, which picks the animation.
 *
 * <pre>
 * QUEUED --> ORDERING --> WAITING_FOOD --> RECEIVING --> LEAVING --> TALKING --> EXITING (gone)
 *    \______________________________ FLEEING (the fraud scatters the line) ______/
 * </pre>
 * Append new states at the end: the ordinal is synced and the name is saved.
 */
public enum CustomerState {
    /** Standing in line, shuffling up as it moves. */
    QUEUED,
    /** At the window, giving the order. */
    ORDERING,
    /** At the window while the Papon cooks. */
    WAITING_FOOD,
    /** Taking the taco (Customer.SERVE ticks). */
    RECEIVING,
    /** Walking back down the walkway to the player in line. */
    LEAVING,
    /** Stopped beside the player, reacting to the taco (Customer.reactTicks). */
    TALKING,
    /** Walking off, then gone. */
    EXITING,
    /** Running away from the fraud scene, then gone. */
    FLEEING;

    public static CustomerState byId(int id) {
        CustomerState[] all = values();
        return id >= 0 && id < all.length ? all[id] : QUEUED;
    }
}
