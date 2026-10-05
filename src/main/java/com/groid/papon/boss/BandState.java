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
 * PLAY --(nightfall, or dawn: the lamplighter, the third of the coro)--> LAMP_GO --> LAMP_SWITCH --> LAMP_BACK --> PLAY
 * PLAY --(phase three: the boss knocked down)--> READY --(his snatch)--> SCARED (the singer: ROBBED --> SCARED)
 *      --(the spotlight)--> AWED --(the guitar thrown back: the singer)--> CATCH --> READY --> PLAY (Para Siempre)
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
    HAT_SLOW(50),
    /** Over to the floodlight at the back corner of the stage (it has got dark, or light). */
    LAMP_GO(0),
    /** A tug on its chain: on (or off). */
    LAMP_SWITCH(40),
    /** ...and back to his place, still singing. */
    LAMP_BACK(0),
    /** Phase three: cowering from the boss, instrument hugged. */
    SCARED(0),
    /** The singer, his requinto ripped out of his hands: spun round, a look at his empty hands, and down he cowers. */
    ROBBED(40),
    /** Watching the boss play in the spotlight, swaying, hands clasped. */
    AWED(0),
    /** The singer: the requinto thrown back at him, juggled twice and caught. */
    CATCH(30),
    /** The guitar is back, the boss has his back to them: they look at each other, nobody dares start. */
    NERVOUS(0);

    public final int duration;

    BandState(int duration) { this.duration = duration; }

    public static BandState byId(int id) {
        BandState[] all = values();
        return id >= 0 && id < all.length ? all[id] : SLEEP;
    }

    public boolean asleep() { return this == SLEEP || this == RESLEEP; }

    public boolean lamp() { return this == LAMP_GO || this == LAMP_SWITCH || this == LAMP_BACK; }

    /** Up, dressed and holding his instrument. */
    public boolean ready() {
        return this == READY || this == COUNT || this == PLAY || this == BOW || this == SCARED || this == AWED || this == CATCH || this == NERVOUS;
    }
}
