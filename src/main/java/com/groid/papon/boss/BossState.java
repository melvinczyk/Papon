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
 * IDLE --> P2_SOLO | P2_CUTAWAY --> IDLE                                   (the singer's guitar solo: the camera on him a moment,
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
 * IDLE --> P3_KNOCKBACK --> P3_GLARE --> P3_STALK --> P3_SNATCH --> P3_RETURN --> P3_BLACKOUT --> P3_ASCEND
 *       --> P3_SERENADE --> P3_VERDICT --> P3_TOSS --> P3_FELT --> P3_TO_CAR --> P3_CRUSH --> P3_SHOWCAR --> P3_GO --> P3_DANCE --> IDLE
 *                                                             (half health, in the coliseum: knocked out of the air, he
 *                                                              glares at the band ("This is NOT my style"), takes the
 *                                                              singer's requinto, the lights go out round the bowl, and
 *                                                              in a spotlight he plays and sings the opening of "Para
 *                                                              Siempre" himself; "Learn, muchachos", the guitar back,
 *                                                              "GO!", the band takes it from there and the fire comes back
 *                                                              crimson: "Now... let's DANCE." Night from here on, the
 *                                                              spotlight on him. With no coliseum: P3_KNOCKBACK --> P3_GLARE
 *                                                              --> P3_DANCE)
 * IDLE --> P3_COMBO | P3_FLASH | P3_LASER | P3_SOLAR | P3_MARIACHI | P3_PINATA | P3_BRAZIERS --> IDLE
 *                                                             (Ascended Papon, phase three's fight: golden fists, lightning
 *                                                              dashes, eye lasers, the Aztec sun, the band's fire, the piñata,
 *                                                              the braziers; none of the chef's moves)
 * IDLE --> P3_COMET --> P3_COMET_LAND [--> P3_COMET --> P3_COMET_LAND] --> IDLE   (two comets)
 * IDLE --> P3_DUEL_FETCH --> P3_DUEL --> P3_DUEL_SMASH | P3_DUEL_SHAME --> IDLE    (the guitar duel, a guitar-hero QTE)
 * IDLE --> P3_NOVA --> P3_SPENT --> IDLE                     (the supernova: only his shadow is safe; then he is spent)
 * IDLE --> P3_NITRO --> DRIVING --> IDLE                     (the Skyline, nitro on; out through the roof)
 * IDLE --> QTE_GRAB (kind 5) --> QTE_HOLD --> QTE_FAIL | QTE_BROKEN --> IDLE      (the squeeze: spam keys to break out)
 * IDLE --> P4_FALL --> P4_RISEUP --> P4_IGNIS --> P4_PROPHECY --> P4_BRETHREN --> P4_SOULS --> P4_HALFBAKED --> P4_BOW
 *      --> P4_SERPENTS --> P4_TALKING --> P4_TOBAND --> P4_HONOR --> P4_MUCHACHAS --> P4_GOODTIME --> IDLE
 *                                                             (a quarter health, Dia de los Muertos: he rises laughing, a
 *                                                              spirit hammer forms in his hand, its lightning turns the moon
 *                                                              into a blue calavera, the moon's rays turn the band and the
 *                                                              stands to skeletons, the moon floods the bowl blue and it
 *                                                              becomes El Coliseo de los Muertos, twice the size; he looks
 *                                                              round it ("centuries"), and burns down to his truest form:
 *                                                              a sugar-skull skeleton with bone wings. No coliseum: no swap)
 * IDLE --> P4_MARTILLO | P4_GIGANTE | P4_GRIETA | P4_PETALOS | P4_PAPEL | P4_VELAS | P4_DANZA | P4_LUNA | P4_SERPIENTES | P4_FLECHAS --> IDLE
 *                                                             (El Rey de los Muertos: the hammer, the hologram giant and its
 *                                                              laser, the rift of souls, the petal spirals, the banners, the
 *                                                              erupting cracks, the dancers, the moon's beams)
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
    P2_COUNT(112),
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
    /** He plays the song's opening and sings its first lines himself, ascended, in the spotlight; the stands light lighters. */
    P3_SERENADE(390),
    /** "THIS... is more my style. Learn, muchachos." */
    P3_VERDICT(80),
    /** The guitar thrown back over his shoulder without a look; the singer juggles it; "What are you WAITING for?! GO!!!" */
    P3_TOSS(80),
    /** The singer's pickup, the band crashing in, the fire back in crimson round the bowl; quietly: "Now... let's DANCE." */
    P3_DANCE(130),
    /** Ascended: he turns to you, the power going through him: "Hmmm... I haven't felt this way... in a LOOOOONG time." An evil smile. */
    P3_FELT(120),
    /** Over to his van. */
    P3_TO_CAR(0),
    /** The van over his head, crushed in his hands, reformed into a black Skyline and slammed down; it revs. */
    P3_CRUSH(200),
    /** He whirls round on the band: "What are you WAITING for?! GO!!!" */
    P3_GO(70),
    /** In the spotlight, before the song: bent double, groaning, the wings tear out of his back (the flash, his new
     *  form), and the roar. Appended (the ordinal is synced). */
    P3_ASCEND(150),
    // ---- Ascended Papon's own moves (phase three's fight, after "let's dance"): a demigod's, none of the chef's
    /** Puños de Oro: in like a thunderbolt, three golden jabs, an uppercut, and down on you from above. */
    P3_COMBO(56),
    /** Relámpago Dorado: five lightning dashes through you, each on a golden line (AI-held). */
    P3_FLASH(0),
    /** Cometa de Papa: up out of sight, a red mark that follows you and locks, and down he comes burning (AI-held). */
    P3_COMET(0),
    /** ...the crater, and three rings rolling out across the sand (jump them). */
    P3_COMET_LAND(30),
    /** Ojos de Papa: lasers out of his eyes (and his golden statues' eyes), chasing you over the sand. The Skyline stops them. */
    P3_LASER(110),
    /** El Sol Azteca: a sun stone behind him and its light over the whole sand; only the Skyline's shade is safe. */
    P3_SOLAR(170),
    /** ¡Fuego, Mariachi!: he conducts the band at you: trumpet blasts along the ground (jump), violin cuts at head height (duck). */
    P3_MARIACHI(160),
    /** Duelo de Guitarras: to the band stand for the guitarist's guitar, and back down in front of you (AI-held). */
    P3_DUEL_FETCH(0),
    /** The duel: a guitar-hero QTE on your screen while he shreds (AI-held until it is over). */
    P3_DUEL(0),
    /** He won the duel: the guitar over your head. */
    P3_DUEL_SMASH(50),
    /** You won: the crowd cheers you and he stands there, humiliated (taking double). */
    P3_DUEL_SHAME(80),
    /** La Piñata: a giant potato piñata over the sand, a flying kick, and candy bombs raining down. */
    P3_PINATA(140),
    /** Llamas del Coliseo: the braziers round the podium hurl burning potatoes at you, one after another. */
    P3_BRAZIERS(110),
    /** La Supernova: a star over the middle of the sand; only his shadow is safe when it goes. */
    P3_NOVA(150),
    /** ...and he drops out of the sky, spent (taking double). */
    P3_SPENT(80),
    /** Nitro: onto the Skyline's roof and in at the wheel (then DRIVING, the nitro on). */
    P3_NITRO(0),
    /**
     * A moment on the band in the middle of the fight (1.8 s, like the guitar solo): the camera cuts to the trumpets
     * coming in, the violins' run, Gordo's guitarron, the coro, or the stands; he stops and grooves along.
     * Which: PaponBossEntity.cutaway() (a Cutaway).
     */
    P2_CUTAWAY(36),
    /**
     * The Skyline's reveal, after the crush: quick action shots all round the car as he starts it (the lamps come on),
     * the purple underglow flickers on front to back, the rev and the backfire, the blow-off, the eagle on the hood,
     * from over the roof; then him by it: "...and in with the NEW." (beats showcar.*)
     */
    P3_SHOWCAR(170),
    // ---- 2026-10-04 (appended: the ordinal is synced)
    /** Winded after a big move he channelled (untouchable while he cast it): down on the sand, panting, and open (x1.5). */
    WINDED(50),
    /** El Sombrero Asesino: giant sombreros spun off his head like saw blades, ricocheting round the sand. */
    P3_SOMBRERO(170),
    /** El Volcán: down into the middle of the sand; it cracks, salsa erupts along the cracks in waves, rocks rain. */
    P3_VOLCAN(180),
    /** Las Calaveras: sugar-skull potatoes rise round you, circle, and dive at you one after another. */
    P3_CALAVERAS(180),
    /** Phase three's cutscene: just ascended, the power staggers him (a beat before his serenade). */
    P3_POWER(80),
    /** La Piñata (reworked): swung on its rope like a wrecking ball through the sand, then smashed into a spiral of candy. */
    P3_PINATA_SWING(220),
    // ---- phase four, Dia de los Muertos (a quarter of his health; appended: the ordinal is synced)
    // (2026-10-04, reworked: the user's script) the Final Guardian's awakening. Durations are fallbacks: the beats come from
    // his recorded lines (gen_boss_voice.beats, p4.*), and P4_GOODTIME ends on the song's first sung word.
    /** Hit down to a quarter: thrown flat on his back, the stands gasp, the band stops dead; panting, he finds the old scar on
     *  his chest torn open and bleeding: "Huh... it reopened, didn't it." */
    P4_FALL(150),
    /** He struggles up, coughing blood: "Do you want to know... how I got this scar?" */
    P4_RISEUP(110),
    /** He reaches behind his back and pulls out Ignis's head, holds it up, and throws it down with a thud: "...a little damage,
     *  but nothing notable." Then at you: "I assume you two have met before, no?" */
    P4_IGNIS(200),
    /** The villain's monologue, walking: "Ten thousand years...", "...the prophecy...", the fog rolling in; up at the moon,
     *  smiling: "Ha... la luna knows it too." */
    P4_PROPHECY(270),
    /** Arms wide to the stands: "My brethren... this is it...", "...grant me your STRENGTH!"; the moon turns: the dead's. */
    P4_BRETHREN(220),
    /** The souls of the stands stream into him, the fans left as skeletons; then the band's, one by one. He glows green. */
    P4_SOULS(220),
    /** Blazing green: "You know... it's not right to go into a fight half baked." His transformation (the arena turns in the
     *  flash): Papon, the Final Guardian. */
    P4_HALFBAKED(240),
    /** An Aztec bow out of the light; he draws and shoots the moon; black smoke tears open in the sky. */
    P4_BOW(120),
    /** Quetzalcoatl and Kukulcan come out of the smoke, colossal, and down onto their orbit round the arena. */
    P4_SERPENTS(250),
    /** "Now we're TALKING!" */
    P4_TALKING(60),
    /** Over to the band (walking: ends when he is there). */
    P4_TOBAND(200),
    /** Softly, to the band: "Señores... it has been an honor. But I need you to play one last song." The band strikes up. */
    P4_HONOR(500),
    /** "Hey! Where are the muchachas?": skeleton salsa dancers rise by the stage; he takes one by the hand and twirls her. */
    P4_MUCHACHAS(200),
    /** Round to you, smirking: "COME ON!!! SHOW ME A GOOD TIME!!!" The fight starts on the first sung word. */
    P4_GOODTIME(200),
    /** El Martillo: blinks in beside you, two swings of the spirit hammer, up and down: rings and blue lightning. */
    P4_MARTILLO(64),
    /** El Gigante: he plays; a giant hologram skeleton rises out from under the arena: its mouth's laser, its hands. */
    P4_GIGANTE(480),
    /** La Grieta de las Almas: he slams the floor and tears it open with his hands; the souls in it pull you in. */
    P4_GRIETA(200),
    /** Lluvia de Cempasuchil: spinning over the middle, spirals of marigold petals out across the floor. */
    P4_PETALOS(215),
    /** Papel Picado: giant banners of cut paper sweep the floor, low (jump) and high (duck), from both ends. */
    P4_PAPEL(170),
    /** Las Velas de Animas: the ectoplasm in the cracks erupts in blue fire, out from the skull and back. */
    P4_VELAS(160),
    /** La Danza de los Muertos: skeleton dancers ring you, closing, and spin through you one after another. */
    P4_DANZA(170),
    /** La Luna: the moon answers him: rings of blue on the floor, then its beams down into them, volley after volley. */
    P4_LUNA(130),
    /** Las Serpientes: Quetzalcoatl and Kukulcan dive out of the sky in turn, straight across the floor (a long warning line each). */
    P4_SERPIENTES(160),
    /** Flechas del Sol: his bow; arrows of the souls' light shot up, raining back down on rings round you, then a volley. */
    P4_FLECHAS(130),
    // ---- (2026-10-04, the final guardian's story and his grand moves; appended: the ordinal is synced)
    /** Phase four's cutscene, after the prophecy: Moctezuma, who saw a god in Cortés and gave his throne away; "I will not make
     *  his mistake"; the moon comes out: "...the prophecy might finally be fulfilled." (beats p4.throne_*) */
    P4_THRONE(400),
    /** El Ouroboros: Quetzalcoatl and Kukulcan come down and close a ring round the whole arena, chasing each other's tails; their heads
     *  breathe jade fire across the floor as they go round (jump it). */
    P4_OUROBOROS(300),
    /** La Pista de los Muertos: the floor lights up as a dance floor of coloured tiles; on the beat, tiles flash and then
     *  erupt in spirit fire, pattern after pattern, while he dances salsa in the middle. */
    P4_PISTA(240),
    /** El Camino de Cempasúchil: a path of marigolds winds across the floor; the ectoplasm floods everything else, and the path
     *  wilts behind you; he lobs sugar skulls onto it. */
    P4_CEMPASUCHIL(250),
    /** El Castillo: a fiesta fireworks tower rises in the middle; its wheels light tier by tier and spin spirals of coloured
     *  sparks across the floor; its crown rains rockets; it bursts. */
    P4_CASTILLO(280),
    /** Salsa Caliente: he dances at you on the beat, every step leaving a footprint of fire, cross-body leads, spin kicks. */
    P4_SALSA(170),
    /** El Último Tango: the dance-off (QTE kind 7): call and response on the band's beat, the embrace, the dip. */
    P4_TANGO(520),
    /** He won the dance: he dips you, and drops you. */
    P4_TANGO_WIN(60),
    /** You won the dance: he stands there flustered, dizzy, wide open. */
    P4_TANGO_LOSE(110),
    /** Aliento de Jade: Quetzalcoatl and Kukulcan breathe green fire across the floor in sweeps, crossing, then both at once. */
    P4_ALIENTO(150),
    /** La Gran Serpiente: the two serpents coil round each other high over the floor, fuse into one blazing knot and pour
     *  down one huge beam of green fire that hunts you, shockwaves rolling out of where it strikes. */
    P4_FUSION(300),
    // ---- (2026-10-05, phase four in stages: the user: "start dangerous but become increasingly chaotic... the boss losing
    // control... the final portion combining the boss, the serpents and the giant into a visually spectacular climax")
    /** A stage of phase four breaking: he clutches his skull, screams, and everything in the arena answers him (El Gigante rises
     *  to stay, the serpents start hunting on their own). */
    P4_RAGE(80),
    /** El Juicio Final: the climax. Everything at once: the serpents wall the arena in and breathe, El Gigante's lasers and
     *  slams, his arrows and the moon, the floor erupting, and the last blast only the marigold circles survive. */
    P4_JUICIO(500),
    // ---- (2026-10-05, the revision pass; appended: the ordinal is synced)
    /** El Tzompantli (replaces La Danza's ring of echoes, the user: "does not look good"): in the middle of the floor he slams
     *  his fists down and the skull racks of the old gods burst up out of it: lines of sugar-skull posts racing out at you
     *  across the whole arena, then rings of them spreading to the walls (find the gap). */
    P4_TZOMPANTLI(236),
    /** El Beso de la Muerte: the muchachas spin away and rise round the arena; they kiss their roses and throw them at you
     *  (thorn thickets burst up where they fall); then all together a last kiss: thorn vines race to the middle, a great rose
     *  blooms there and bursts in a ring of petals out to the walls (jump it). He dances salsa in the middle. */
    P4_BESO(296);

    /** The hype's animation length (p2_hype 8.0 s): P2_RESUME hands over this long before the singing. */
    public static final int P2_HYPE_LENGTH = 160;

    public final int duration;

    BossState(int duration) { this.duration = duration; }

    public static BossState byId(int id) {
        BossState[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }

    /** The intro: he cannot be hurt and the boss bar is hidden. */
    public boolean intro() { return this == LEAP_OUT || this == CHUG; }

    /** The phase-two cutscene before the dome: he cannot be hurt, and the music stops dead. */
    public boolean silent() { return this == P2_KNEEL || this == P2_TALK || this == P2_SEASON || this == P2_SCREAM; }

    /** Any part of the phase-two cutscene (he cannot be hurt), the guitar solo's moment, and the phase-three cutscene. */
    public boolean cutscene() {
        return silent() || this == P2_ARRIVE || coliseum() || this == P2_SOLO || this == P2_CUTAWAY || phaseThree() || phaseFour();
    }

    /** Phase four's cutscene: the reopened scar to the first sung word. */
    public boolean phaseFour() { return ordinal() >= P4_FALL.ordinal() && ordinal() <= P4_GOODTIME.ordinal() || this == P4_THRONE; }

    /** Phase four's moves. */
    public boolean muertosMove() {
        return ordinal() >= P4_MARTILLO.ordinal() && ordinal() <= P4_FLECHAS.ordinal() || ordinal() >= P4_OUROBOROS.ordinal();
    }

    /** Phase four's cutscene before the band plays his last song (their music off). */
    public boolean p4Silent() { return ordinal() >= P4_FALL.ordinal() && ordinal() <= P4_TOBAND.ordinal() || this == P4_THRONE; }

    /** Phase four's cutscene before the souls: the stands dead silent (the user: "the crowd needs to be silent"). */
    public boolean p4Hushed() { return ordinal() >= P4_FALL.ordinal() && ordinal() <= P4_BRETHREN.ordinal() || this == P4_THRONE; }

    /** The phase-three cutscene, the knockback to "let's dance". */
    public boolean phaseThree() {
        return ordinal() >= P3_KNOCKBACK.ordinal() && ordinal() <= P3_ASCEND.ordinal() || this == P3_SHOWCAR || this == P3_POWER;
    }

    /** Through phase three's cutscene, before the band takes the song back: the stands' own song is off. */
    public boolean bandStopped() { return phaseThree() && this != P3_DANCE; }

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
    public boolean qte() { return this == QTE_HOLD || this == QTE_FAIL; }

    /** Music and boss bar are on. */
    public boolean fighting() { return !intro() && this != DEATH && !silent(); }
}
