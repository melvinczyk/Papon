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
 * IDLE --> P2_KNEEL --> P2_TALK --> P2_SEASON --> P2_SCREAM --> P2_ARRIVE --> ...
 *                                                             (three quarters health: the cutscene: blood, "enough
 *                                                              playing around", a giant salt shaker salts him and he
 *                                                              bakes golden, the scream, and up into El Coliseo Papa,
 *                                                              built in the sky while he knelt; he flies from here on)
 * P2_ARRIVE --> P2_NOTICE --> P2_MARCH --> P2_WAKE --> P2_WAIT [<--> P2_KICK] --> P2_COUNT --> P2_SHOWTIME
 *           --> P2_APOLOGY --> P2_RESUME --> P2_HYPE --> IDLE
 *                                                             (in the coliseum: "¡Música, maestro!", but the band is
 *                                                              asleep; he strides straight over and brings his spatula
 *                                                              down on the singer's head (up he goes), the bang wakes
 *                                                              the rest; he paces while they dress, kicks the
 *                                                              guitarron's chair; the singer counts them in; the guitar
 *                                                              intro, his apology and his aura; "where were we?"; he
 *                                                              works the stands into a roar, and the fight is on the
 *                                                              moment the singing starts. With no coliseum: P2_ARRIVE --> IDLE)
 * IDLE --> P2_SOLO --> IDLE                                   (the singer's guitar solo: the camera on him a moment,
 *                                                              the boss stops to air-guitar along)
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
 * IDLE --> P3_KNOCKBACK --> P3_GLARE --> P3_STALK --> P3_SNATCH --> P3_RETURN --> P3_BLACKOUT --> P3_SERENADE
 *       (he ascends as he sings) --> P3_VERDICT --> P3_TOSS --> P3_FELT --> P3_TO_CAR --> P3_CRUSH --> P3_GO --> P3_DANCE --> IDLE
 *                                                             (half health, in the coliseum: knocked out of the air, he
 *                                                              glares at the band ("This is NOT my style"), takes the
 *                                                              singer's requinto, the lights go out round the bowl, and
 *                                                              in a spotlight he plays and sings the opening of "Para
 *                                                              Siempre" himself; "Learn, muchachos", the guitar back,
 *                                                              "GO!", the band takes it from there and the fire comes back
 *                                                              crimson: "Now... let's DANCE." Night from here on, the
 *                                                              spotlight on him. With no coliseum: P3_KNOCKBACK --> P3_GLARE
 *                                                              --> P3_DANCE)
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
    CHARGE_WINDUP(9),
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
    P2_KNEEL(120),
    P2_TALK(70),
    P2_SCREAM(70),
    P2_ARRIVE(100),
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
    P2_GRAVITY(180),
    // phase two in his coliseum: the band is asleep (see the diagram)
    P2_NOTICE(60),
    P2_MARCH(0),
    P2_WAKE(70),
    P2_WAIT(0),
    P2_KICK(30),
    P2_COUNT(80),
    P2_SHOWTIME(70),
    P2_APOLOGY(178),
    P2_RESUME(0),
    // ---- the hardening: punishes and new attacks (append only: the ordinal is synced)
    /** Face-tanked: he balls up and bursts in a blast of habanero salsa (0.5 s), throwing everyone off him. */
    SALSA_BURST(26),
    /** La Freidora: a leap and a double stomp (0.7 s / 1.2 s): the sand in a pattern gives way to frying oil. */
    ARENA_FRYER(40),
    /** ¡Abucheo!: he turns to the stands, thumbs down: they pelt you with produce for the next 7 s. */
    ARENA_HECKLE(32),
    /** El Machacador (phase two): a masher the size of the sand comes down out of the sky (4.0 s): only its holes are safe. */
    P2_MASHER(130),
    /** Working the stands up, timed to end on the first sung word (P2_HYPE_LENGTH long when on time). */
    P2_HYPE(0),
    P2_SOLO(60),
    /** ¡La Ola! (phase two, coliseum): he gets the stands doing the wave and three waves of salsa sweep the sand: jump them. */
    ARENA_OLA(36),
    /** ¡Estampida!: a whistle to the stands and giant potatoes roll across the sand in lanes, three volleys: find the gaps. */
    ARENA_STAMPEDE(36),
    // ---- phase three, "Para Siempre" (half health; see the diagram). Durations of the voiced beats are fallbacks:
    // VoiceLines.beat times them to his lines.
    /** Knocked out of the air: flung back, down on his back on the sand, a skid, and up again (2.5 s). */
    P3_KNOCKBACK(50),
    /** Up, the blood wiped off his lip, and a long look at the band: "This... is NOT... my style." */
    P3_GLARE(110),
    /** He walks over to the band stand, every step a thud. */
    P3_STALK(0),
    /** "Give me THAT.": the singer's requinto ripped out of his hands; the band cowers. */
    P3_SNATCH(70),
    /** Back to the middle of the sand with it, the braziers going out round the bowl one by one behind him. */
    P3_RETURN(0),
    /** Dark: a beat of nothing, then the spotlight slams on him (1.5 s), head bowed, the guitar coming round. */
    P3_BLACKOUT(50),
    /** He plays the song's opening and sings its first lines himself, in the spotlight; the stands light lighters. */
    P3_SERENADE(390),
    /** "THIS... is more my style. Learn, muchachos." */
    P3_VERDICT(80),
    /** The guitar thrown back over his shoulder without a look; the singer juggles it; "What are you WAITING for?! GO!!!" */
    P3_TOSS(80),
    /** The singer's pickup, the band crashing in, the fire back in crimson round the bowl. */
    P3_DANCE(130),
    /** Ascended: he turns to you, the power going through him: "Hmmm... I haven't felt this way... in a LOOOOONG time." An evil smile. */
    P3_FELT(120),
    /** Over to his van. */
    P3_TO_CAR(0),
    /** The van over his head, crushed in his hands, reformed into a black Skyline and slammed down; "Now... let's DANCE." */
    P3_CRUSH(200),
    /** He whirls round on the band: "What are you WAITING for?! GO!!!" */
    P3_GO(70);

    /** The hype's animation length (p2_hype 8.0 s): P2_RESUME hands over this long before the singing. */
    public static final int P2_HYPE_LENGTH = 160;

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

    /** Any part of the phase-two cutscene (he cannot be hurt), the guitar solo's moment, and the phase-three cutscene. */
    public boolean cutscene() {
        return silent() || this == P2_ARRIVE || coliseum() || this == P2_SOLO || phaseThree();
    }

    /** The phase-three cutscene, the knockback to "let's dance". */
    public boolean phaseThree() {
        return ordinal() >= P3_KNOCKBACK.ordinal() && ordinal() <= P3_GO.ordinal();
    }

    /** Through phase three's cutscene, before the band takes the song back: the stands' own song is off. */
    public boolean bandStopped() {
        return phaseThree() && this != P3_DANCE;
    }

    /** The coliseum's own beats: the band, the count-in, the apology. */
    public boolean coliseum() {
        return this == P2_NOTICE || this == P2_MARCH || this == P2_WAKE || this == P2_WAIT || this == P2_KICK || this == P2_COUNT
                || this == P2_SHOWTIME || this == P2_APOLOGY || this == P2_RESUME || this == P2_HYPE;
    }

    /** He cannot be stopped: no knockback, no stagger, half damage (drawn with a red edge). */
    public boolean unstoppable() {
        return this == DRUNK_SWIG || this == DRUNK_FLURRY || this == HAYMAKER || this == CHARGE || this == P2_GRAVITY
                || this == TO_VAN || this == DRINK_HANDLE || this == ENTER_VAN || this == SALSA_BURST;
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
