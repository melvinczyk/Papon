package com.groid.papon.boss;

/**
 * Chef Papon, the boss. Synced as its ordinal; each timed state lasts exactly as long as its
 * one-shot animation in papon_boss.animation.json (gen_boss_anims.py), so server and client hand
 * over together, Cataclysm style. 0 = until the AI moves it on.
 *
 * <pre>
 * LEAP_OUT --> CHUG --> ROAR --> IDLE                       (the intro: out of the window, a whole bottle, the roar)
 * IDLE --> SWIPE | SLAM | BREATH | THROW --> IDLE           (on foot)
 * IDLE --> TO_VAN --> DRINK_HANDLE --> ENTER_VAN --> DRIVING --> EXIT_STUMBLE --> IDLE   (the van)
 * IDLE --> PHASE --> IDLE                                   (half health)
 * any --> DEATH
 * </pre>
 * Append new states at the end: the ordinal is synced and the name is saved.
 */
public enum BossState {
    LEAP_OUT(30),
    CHUG(100),
    ROAR(40),
    IDLE(0),
    SWIPE(40),
    SLAM(50),
    BREATH(60),
    THROW(40),
    TO_VAN(0),
    DRINK_HANDLE(50),
    ENTER_VAN(16),
    DRIVING(0),
    EXIT_STUMBLE(80),
    PHASE(60),
    DEATH(100);

    public final int duration;

    BossState(int duration) {
        this.duration = duration;
    }

    public static BossState byId(int id) {
        BossState[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }

    /** The intro: he cannot be hurt and the boss bar is hidden. */
    public boolean intro() {
        return this == LEAP_OUT || this == CHUG;
    }

    /** Music and boss bar are on. */
    public boolean fighting() {
        return !intro() && this != DEATH;
    }
}
