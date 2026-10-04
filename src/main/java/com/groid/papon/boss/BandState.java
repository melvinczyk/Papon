package com.groid.papon.boss;

/**
 * A mariachi in Chef Papon's band. Synced as its ordinal (append new states at the end); each
 * timed state lasts as long as its animation in mariachi.animation.json (gen_band_anims.py).
 *
 * <pre>
 * SLEEP --(the boss's clang)--> STARTLE --> GETUP --> JACKET --> HAT [--> SHADES] --> GRAB --> READY
 *                                             \--> RESLEEP --> SLEEP (the dozer, until he is kicked)
 * SLEEP --(the boss's spatula: the singer)--> WHACKED --> JACKET_SLOW --> HAT_SLOW --> SHADES --> GRAB --> READY
 * READY --(the singer)--> COUNT --> PLAY (everyone, the song) --> BOW (the fight is over)
 * </pre>
 */
public enum BandState {
    SLEEP(0),
    STARTLE(20),
    GETUP(60),
    RESLEEP(40),
    JACKET(50),
    HAT(30),
    SHADES(20),
    GRAB(30),
    READY(0),
    COUNT(80),
    PLAY(0),
    BOW(50),
    /** The singer, hit on the head and up into the air out of his chair. */
    WHACKED(50),
    /** ...and dressing under protest. */
    JACKET_SLOW(90),
    HAT_SLOW(50);

    public final int duration;

    BandState(int duration) {
        this.duration = duration;
    }

    public static BandState byId(int id) {
        BandState[] all = values();
        return id >= 0 && id < all.length ? all[id] : SLEEP;
    }

    public boolean asleep() {
        return this == SLEEP || this == RESLEEP;
    }

    /** Up, dressed and holding his instrument. */
    public boolean ready() {
        return this == READY || this == COUNT || this == PLAY || this == BOW;
    }
}
