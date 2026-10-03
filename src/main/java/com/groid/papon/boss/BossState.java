package com.groid.papon.boss;

/**
 * Chef Papon, the boss. Synced as its ordinal; each timed state lasts exactly as long as its
 * one-shot animation in papon_boss.animation.json (gen_boss_anims.py), so server and client hand
 * over together, Cataclysm style. 0 = until the AI moves it on.
 *
 * <pre>
 * LEAP_OUT --> CHUG --> ROAR --> IDLE                       (the intro: out of the window, a whole bottle, the roar)
 * IDLE --> SWIPE [--> SWIPE_OVERHEAD] --> IDLE                 (the spatula flurry; the overhead on a hit)
 * IDLE --> LEAP --> LEAP_LAND --> IDLE                        (up, hover and track, dive, shockwave rings)
 * IDLE --> CHARGE_WINDUP --> CHARGE [--> CHARGE_WINDUP] --> IDLE | STAGGER (into a wall) --> IDLE
 * IDLE --> UPPERCUT [--> AIR_SPIKE --> LEAP_LAND] --> IDLE    (launch you, leap after you, spike you down)
 * IDLE --> BREATH | THROW --> IDLE                            (sweeping fire; a molotov fan)
 * IDLE --> BLINK --> LEAP (from the top) --> LEAP_LAND --> IDLE   (teleport fist combo, a dive to finish)
 * IDLE --> GATLING --> IDLE                                   (the potato gatling)
 * IDLE --> DASH_COMBO --> IDLE                                (three dashing swings and a spin)
 * IDLE --> PUNT_RUN --> VAN_KICK --> IDLE                     (runs behind the parked van and boots it at you)
 * DRIVING --> BAILOUT --> SWIPE                               (jumps out of the moving van at you; it rolls on)
 * IDLE --> P2_KNEEL --> P2_TALK --> P2_SEASON --> P2_SCREAM --> P2_ARRIVE --> IDLE
 *                                                             (three quarters health: the cutscene: blood, "enough
 *                                                              playing around", a giant salt shaker salts him and he
 *                                                              bakes golden, the scream, the potato dome; he flies
 *                                                              from here on)
 * IDLE --> GRAB [--> GRAB_COMBO [--> CHARGE_WINDUP]] --> IDLE  (grabs you: blowtorch, salt, the throw)
 * IDLE --> P2_BARRAGE | P2_METEOR | P2_DIVE --> IDLE          (phase two: hands, meteors, dive bombs)
 * IDLE --> VAN_GRAB --> VAN_THROW --> IDLE                    (phase two: flies to the van, throws it)
 * IDLE --> GUARD [--> RIPOSTE] --> IDLE                      (the comal up: blocks the front, reflects arrows, then bashes back)
 * IDLE --> EVADE --> (an attack)                             (a drunk's sidestep out of your swing, straight into a counter)
 * IDLE --> CLEAVER_TOSS --> DASH_COMBO                       (two cleavers out on a curve and back; he follows them in)
 * IDLE --> BURRITO_TOSS [--> CHARGE_WINDUP] --> IDLE         (a giant tortilla lands where you stand: in it, you are a
 *                                                              burrito, and he comes bowling)
 * IDLE --> DRUNK_SWIG --> DRUNK_FLURRY --> IDLE              (a swig, then five haymakers and a hammer: unstoppable)
 * IDLE --> HAYMAKER --> IDLE                                 (the feint: wobbling, then an eight-block haymaker)
 * IDLE --> COMAL_SLAM --> IDLE                               (the ground round him a hot griddle: Habanero)
 * IDLE --> TO_VAN --> BRICK --> IDLE                         (a brick on the gas: the van hunts you while he fights)
 * IDLE --> VAN_HOP --> LEAP                                  (onto the van's roof and off it, higher)
 * IDLE --> QTE_GRAB --> QTE_HOLD --> QTE_FAIL | QTE_BROKEN --> IDLE   (the QTEs: keys on screen, a second each)
 * IDLE --> P2_FRY_VOLLEY | P2_MASH | P2_TOT | P2_SNARE [--> P2_DIVE] | P2_GRAVITY --> IDLE   (phase two, potatoes)
 * (SLAM is no longer chosen; LEAP replaced it. PHASE is the old half-health roar, no longer used)
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
    SWIPE(28),
    SLAM(50),
    BREATH(60),
    THROW(40),
    TO_VAN(0),
    DRINK_HANDLE(50),
    ENTER_VAN(16),
    DRIVING(0),
    EXIT_STUMBLE(80),
    PHASE(60),
    DEATH(100),
    // phase one, reworked (Cataclysm-style chains)
    SWIPE_OVERHEAD(26),
    LEAP(0),
    LEAP_LAND(30),
    CHARGE_WINDUP(16),
    CHARGE(0),
    STAGGER(40),
    UPPERCUT(22),
    AIR_SPIKE(0),
    BLINK(0),
    GATLING(70),
    // phase one: more combos, and the van as a weapon on foot
    DASH_COMBO(50),
    PUNT_RUN(0),
    VAN_KICK(24),
    BAILOUT(30),
    // phase two (from three quarters health): the cutscene, the pocket dimension, flight
    P2_KNEEL(80),
    P2_TALK(70),
    P2_SCREAM(70),
    P2_ARRIVE(50),
    P2_BARRAGE(40),
    P2_METEOR(70),
    VAN_GRAB(0),
    VAN_THROW(52),
    P2_DIVE(0),
    P2_SEASON(100),
    // phase one: the grab (Ignis-style, no getting out): blowtorched, salted (Brittle), thrown
    GRAB(0),
    GRAB_COMBO(90),
    // the rework: a guard and a dodge, chef's attacks, drunken attacks, the van as a tool, QTEs, potato attacks
    GUARD(0),
    RIPOSTE(18),
    EVADE(10),
    CLEAVER_TOSS(30),
    BURRITO_TOSS(36),
    DRUNK_SWIG(14),
    DRUNK_FLURRY(60),
    HAYMAKER(50),
    COMAL_SLAM(40),
    BRICK(36),
    VAN_HOP(0),
    QTE_GRAB(0),
    QTE_HOLD(0),
    QTE_FAIL(40),
    QTE_BROKEN(80),
    P2_FRY_VOLLEY(60),
    P2_MASH(50),
    P2_TOT(40),
    P2_SNARE(36),
    P2_GRAVITY(100);

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

    /** The phase-two cutscene before the dome: he cannot be hurt, and the music stops dead. */
    public boolean silent() {
        return this == P2_KNEEL || this == P2_TALK || this == P2_SEASON || this == P2_SCREAM;
    }

    /** Any part of the phase-two cutscene (he cannot be hurt). */
    public boolean cutscene() {
        return silent() || this == P2_ARRIVE;
    }

    /** He cannot be stopped: no knockback, no stagger, half damage (drawn with a red edge). */
    public boolean unstoppable() {
        return this == DRUNK_SWIG || this == DRUNK_FLURRY || this == HAYMAKER || this == CHARGE || this == P2_GRAVITY;
    }

    /** A QTE in progress (he cannot be hurt until it resolves). */
    public boolean qte() {
        return this == QTE_HOLD || this == QTE_FAIL;
    }

    /** Music and boss bar are on. */
    public boolean fighting() {
        return !intro() && this != DEATH && !silent();
    }
}
