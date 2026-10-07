# Papon — the Groid Pack final boss

Forge 1.20.1 (47.4.10) + GeckoLib 4.8.4 mod, modid `papon`, package `com.groid.papon`. Repo: https://github.com/melvinczyk/Papon.
Current version: `mod_version` in `gradle.properties`. Work on a `claude/*` branch, never `main`.

## Who and what
The Groid Pack: boss-progression Forge 1.20.1 modpack (~190 mods) running a live server. Instance on this Mac:
`~/Documents/curseforge/minecraft/Instances/Groid Pack OG` (its own `CLAUDE.md`: **the server is production**, pull → edit →
push → verify, never push unattended). This mod never touches the server or instance; ship the jar only when asked.

Final boss = a **joke boss played completely straight**: Papon, the potato mob from Walking Crops (`wkcr:papon`). The user wants
it **extremely polished**: smooth dramatic animations, Cataclysm-quality mechanics, Block Factory's Bosses-style cutscenes.

## Spec (the user's requirements)
**Quest**
1. **Taco Coupon** crafted from boss trophies (KubeJS blocks `kubejs:<ns>_<boss>_trophy`, instance
   `kubejs/startup_scripts/boss_trophies.js`): wither, warden, ender dragon + paper for now, more later
   (`data/papon/recipes/taco_coupon.json`).
2. Give the coupon to a natural Papon → **Wandering Papon**, who walks at normal pace to the **nearest Taco Truck**.
3. Taco Truck is a structure that spawns the boss; **NEVER generates in the world**, only appears when he arrives. Always on
   land, **never water or shore/beach**.
4. Only walks with **players near**; otherwise sits, **takes out a little grill and grills tacos** until someone returns, packs up.
5. **Never dies, never harmed by enemies, never despawns**, avoids caves/ravines, fixes his own path if stuck. Efficient paths.
6. Deep water: rides his own **llama raft** (bones in his model, not an entity). Night: **sets down backpack, unrolls a burlap
   potato sack** (sleeping bag rejected) and sleeps till morning.
7. **Jarritos**: `alexscaves:radon_bottle` + beetroot (red) / carrot (orange) / potato (yellow) / cabbage (green; FD has no
   lettuce). Player: 3 hunger, 2 saturation, nausea + speed; 3D model. Papon: 1.5x speed 2 min then **tired**; **yellow makes
   him angry: smashes it**.
8. **Chancla** (2 sponges): whacking a sleeping Papon, 2%/whack wakes him **angry at you**, then he continues.
9. `/papon distance`: how far the truck is.

**Truck line (the boss timer + comic relief)**: customers in line mixing **Don Pollo, EDP445, Badlands Chugs, Reviewbrah** with
ordinary Papons; player must **wait in line**, the Wandering Papon **in front of the player**. Each served customer walks past
and does their bit (Don Pollo: salsa is spicy, in Spanish; EDP445: "Yo these tacos is heat bro, straight mother fuckin heat
bro"). Caricatures of the real people, editable models. Fight trigger: he hands the truck Papon a **fraud taco voucher**.

**Gerbin**: a chort (`realmrpg_demons:chort`) named "Gerbin", sack stripped, dishwasher's apron; **cuts lettuce** and **washes
tomatoes in a sink**. After the boss falls he gives every winner a **Makima body pillow**.

**Unmasking / phase one**: chef gets angry, found out, **rips off his mustache, takes off his hat: Norwood 7** baldness; rage;
**jumps out the front window**, **chugs a whole bottle of tequila (visible amount)**. **2001 Dodge Caravan** entity, drivable by
the player, own sounds; he **rams you unblockably** after **drinking a whole handle (visible, 2-3 s warning)**, **exits stumbling
drunk**; only uses it when close to it; can **back into you**. Own **potato/taco boss bar**; mariachi music. Phase one: fast
combos, car gimmicks, car driven accurately.

**Phase two (75%)**: kneels, his **POV with blood from his mouth**, **music stops**, "**okay, enough playing around**"; a **huge
salt shaker above his head salts him** in a flowing aura, baked golden; screams, camera shakes; pulls everyone into his pocket
dimension = **El Coliseo Papa**, a potato/Papon-themed Roman coliseum, stands **full of Papon fans cheering him**; he flies.
**Rejected**: pink sprout arms, spectral hands, tortilla wings, red glowing eyes, a Spiritcaller copy, white splits on his sides,
too dark a bake, the potato dome, the gold aura on the singer. Phase-two parts never show in phase-one animations.
Arrival: excited, but the **mariachi band (13 Papons, varied sizes) is asleep**; he wakes them (whacks the singer); they slowly
dress, grab instruments; singer (microphone, **lip-synced**) counts off. Song **"Canción del Mariachi" (Antonio Banderas & Los
Lobos)**. Intro zooms on the guitar; he **apologises for poor hospitality, aura farms, "where were we?"**; fight starts when the
singer sings; song loops, highs/lows follow the battle. (2026-10-05) The singer **counts off "¡Un, dos, tres, cuatro!" in a real
voice**, expressive and energetic, cloned from the user's reference (`tools/src/voice/singer_reference.mp3`, gitignored: a Castilian
male voiceover demo). Only Gordo (guitarron) has a chair (dozes off, chair kicked). One potato
smiley Mexican flag design everywhere, fans with small flags, lively crowd, **potato sun/moon**, a band member lights stage lamps
at nightfall. Showtime: crowd **heard roaring** at his entrance; **fireworks in flag colours** on the music (user said "red, blue
and white like the Mexican flag"; built green/white/red: say so if asked); reactions to what the player does (e.g. stealing his
car); 1-2 s cinematic cuts to instruments mid-fight. Tone: showman, "everyone is here for ME".

**Phase three (50%) "Para Siempre"**: knocked back, beaten, weak, **blurry POV** zooming on the band; "**This is NOT my style...
who do you think I AM?!**", "Let me show you how to really play"; **snatches the guitar from the singer**, band scared; everything
dark, **spotlight**, user's song **"Para Siempre"** (his record's own voice, no voice conversion); stumbles, then plays the intro
and sings; "**This is more my style, learn muchachos**", throws the guitar back, band whispers scared in their own shot, "**What
are you waiting for, GO**"; "**Now, let's dance!**" on his face. Golden form, **crimson** aura/fire. (2026-10-05) **Daylight until
phase four's moon**: the dark is only the serenade's stage blackout (spotlight), lifted to dusk for the Skyline's reveal and to day
on "let's dance". Van → **Skyline** ("It's about time I gave this thing an UPGRADE"; quick action shots, new engine roar, purple underglow
on). Tone: "**I am the ascended potato god, it's time to get serious**". Moveset: demigod, buff, flies fast, **no chef attacks**,
over-the-top potato/Mexican, arena involvement, lasers (**from his eyes**), band attacks (flashy, not particles), **La Ola a real
salsa wave**, the car **not** a way out, a **Guitar Hero duel** (hard), spam QTE. Over-the-top attacks and QTEs take **% of
health**. QTEs: **1 s per key**, squeeze mashes **K and L**, keys swallowed around QTEs, player's body acts them out.

**Phase four (25%) Final Guardian**, Day of the Dead + Aztec: scar on his chest reopened; crowd shocked, mariachi stops; "**Huh,
it reopened, didn't it**", "**Do you want to know how I got this scar?**"; pulls **Ignis's head** (pack trophy) and throws it
down: "**I remember he did a little damage, but nothing notable**", "**I assume you two have met before, no?**"; "**10,000
years...**"; story: final guardian of the world, the **potato prophecy** of a warrior who takes **the throne he kept warm for
centuries** for a champion to care for the world; **Moctezuma/Cortés-as-Quetzalcoatl parallel**; fog, "**la luna knows it
too**"; "**My brethren... grant me your strength**": **Día de los Muertos moon** (the user's sugar skull,
`tools/src/sugar_skull.webp`), crowd souls flow into him (they turn skeletons), then the band's; "**it's not right to go into a
fight half baked**", "**Papon, The Final Guardian**"; Aztec bow, shoots the moon, **Quetzalcóatl and Kukulcán** (first
"Ouros and Boros"; the user asked for names true to Mexican lore for stone feathered serpents: Teotihuacan's and Chichén Itzá's;
Dungeon Now Loading's Fairkeeper serpents, repainted from the user's Quetzalcoatl picture) out of black smoke, huge; they **breathe
green fire** and **fuse** into one for a super attack; "**Now we are talking!**"; "**Señores, it has been an honor, but I need you to play one last
song**": **"Persona Ideal (Me Tengo Que Ir)"**, brass animated; "**Where are the muchachas?**" skeletal salsa Papons, he twirls
one; "**COME ON!!! SHOW ME A GOOD TIME!!!**". Crowd silent, him serious/wounded/panting in the transition; scenes slow enough to
land; screams screamed; grand attacks are most of what he does (serpents circling the arena, salsa, more colour); **dance-off
QTE, interactive, a tango with you as opponent**; his movement dance-like on the beat; El Gigante really in the arena (thick eye
lasers, swipes into walls); his texture must be visible (not too dark).
**Phase four, escalated (2026-10-05)**: a **major escalation and the true climax**: more frequent and intense attacks that **overlap**
(several threats at once); serpents **last longer, cover more of the arena, get progressively more threatening, part of his
patterns** (not standalone); **El Gigante a massive 3D physical presence** (proportions, depth, movement, convincing skeleton,
scale; not a flat effect) whose attacks work **alongside** the serpents and him; built **in stages**, dangerous at first and
**increasingly chaotic, the boss losing control**; the **final portion combines him, the serpents and the giant** into a
challenging, spectacular climax. **Fix rendering**: no attack may go invisible, flicker, vanish or distort (the giant potato,
lasers, piñata, serpents, skeleton...). Keep the existing style and mechanics; no needless performance cost.
**Revision pass (2026-10-05)**: "I will not make his mistake" **cold, controlled, matter-of-fact** (mood `resolute`) and every line
**clearly articulated** (diction audit: `tools/voice_clarity.py`); mix **Papon > music > band voices > SFX**, music louder; Skyline
reveal lit; El Gigante **digs out of the floor**, wears an **El Catrín top hat**, and **La Mano** (wades through the floor, bends over
the arena, grabs you from afar); the **muchachas' own attack** (El Beso de la Muerte) and roses all phase; **petals reach the walls**;
La Danza's ghosts replaced (**El Tzompantli**); **Castillo obvious** (solid tower, he lights its fuse, sparks from its wheels);
**daytime until the moon**, the **fog with the moon** (moon up dark, mist rolls in, darkening, moon lights, night); phase four
**much more aggressive**; phase two's attacks (the **fryer**) reach the whole sand; the count-in **never cut off**.

**Ending**: after Gerbin's pillow, back at the truck: **Chef Papon a friendly vendor again, menu gives free tacos**.
**Pillow**: hug "sensually", lick, put on a bed and sleep beside it on your side, hump (hand and bed); natural plump look,
slouches against the wall on a bed, squeezed/folded by use; the user's picture `tools/src/makima_pillow.jpg`. (2026-10-06) **Feet**:
sneak + right-click her on a bed sits her on its edge, feet just off the floor; right-click: you lie on the floor licking them,
jump: kneel and lift them to your face; punch takes her back. Bed hump: she lies on her back and you **plank** over her;
jump rolls her onto her knees, face down, you kneeling **behind** her; off the bed she sits back up.

**User's song/voice files (gitignored, never download, e.g. from YouTube)**: `tools/src/song/` (Canción del Mariachi,
`para_siempre.mp3` from `~/Downloads/Para Siempre.mp3`, `persona_ideal.mp3`), `tools/src/voice/reference.mp3`,
`tools/src/crowd/` (stadium recordings).

## Component map
| Piece | Files |
|---|---|
| Entry, config `papon-common.toml` | `PaponMod`, `PaponConfig` |
| Items: coupon, Jarritos, chancla, tequila, Caravan Keys, tacos, pillow, spawn eggs | `registry/ModItems`, `item/*` |
| Coupon handoff (`wkcr:papon` by name, no compile dep), totem/attack hooks | `event/CommonEvents` |
| Wandering Papon: state machine, escort, invulnerability, raft, sleep, Jarritos | `entity/WanderingPaponEntity`, `PaponState`, `RaftDriver` |
| Pathing: surface-only nodes, max fall 2, legs ≤28, coarse A* route planner, rescue | `entity/ai/{SurfaceNodeEvaluator,JourneyNavigation,JourneyGoal,RoutePlanner}` |
| Destination (biome map, inland only), SavedData `papon_journeys`, truck placement | `journey/{JourneyPlanner,Terrain,JourneyData,TacoTruck,TacoTruckScene,TacoLine}`, `structures/taco_truck.nbt` |
| Vendor, customers, Gerbin, NPC base, speech, sound cues | `entity/npc/*`, `entity/Speech`, `client/SpeechBubble` |
| Boss: states, phases, moves | `boss/PaponBossEntity`, `BossState`, `TacoBoss`, `Mechanics`, `Fiesta`, `BossProp`, `BeamEntity`, `FoilPotatoEntity`, `MolotovEntity`, … |
| Coliseum, crowd, band, songs | `boss/{Coliseum,ColiseumCrowdEntity,CrowdMood,MariachiEntity,BandRole,BandState,Song}`, `block/*` |
| Phase four extras | `boss/{SkySerpentEntity,SerpentPath,SalsaDancerEntity,SpiritGiantEntity}`, `client/{SpiritGiantRenderer,GiantMesh,Shapes}` |
| Singer's count-in voice | `tools/gen_singer_voice.py` → `sounds/voice/band/count.ogg`, `voice/singer.json` (`gen_band_anims.count`): at half time, a word every other beat, COUNT/P2_COUNT 5.6 s, "cuatro" over before the downbeat |
| Mix (2026-10-05) | `client/VoiceDuck` (tiers: his voice; `music()` 0.8 under him (2026-10-06, was 0.5); band voices `SoundMix.BAND` under the music, `band()`; `get()` effects/crowd 0.22 under him, 0.6 under the band); `SoundMix.FIGHT` 0.5; voices on the Voice slider (`Speech.say`), band sounds `attenuation_distance` 48. Gain clamps at 1: louder music = louder files (`tools/master_music.py`: every song folder to -10 LUFS, one gain per song, lookahead limiter; idempotent). No vocal cues on the band or him but recorded lines (`MariachiEntity.MUTE`, `PaponBossEntity.MUTE_CUES`: the villager "hmm") |
| Phase-four score (2026-10-06) | `tools/sfx.py` (synth kit: filters, hall reverb, formant choir, death whistle, huehuetl, bell, braam, conch) -> `tools/gen_p4_sounds.py`: `cine_souls/soul(a,b,c)/absorb/swell/awaken/emerge` (no vanilla fire-charge/warden/thunder/beacon in P4_SOULS/HALFBAKED), his awakening roar `p4_burst` synthesized by `tools/gen_p4_roar.py` (`gen_boss_voice.SYNTH`: build leaves it alone) |
| El Gigante's sounds + tells (2026-10-06) | `tools/gen_giant_sounds.py`: `boss.laser_giant_{charge,fire,burn,end,sizzle}` (attenuation 128, `SoundMix` leaves `boss.laser_`/`boss.giant_` alone; burn cut by `ClientboundStopSoundPacket` in `stopGiantBurn`), `boss.giant_tell_{grab,swipe,slam,lunge}` played by `giantTell` on each new act; `client/GiantTells` (screen banner + bar + edge tint per attack, lang `hud.papon.giant.*`), `SpiritGiantRenderer.tells` (eye suns + falling rings + aim lines, light pillars over slam/grab marks, lunge lane); autoshot `-Pautoshot=tells` |
| Laser sounds | `tools/gen_laser_sounds.py` → `sounds/boss/laser_{charge,fire,burn,end}.ogg` (synthesized, loud; `SoundMix` leaves `boss.laser_*` alone) |
| Diction audit | `tools/voice_clarity.py` (Whisper word confidence + WER per built line, `voice_modal.clarity`); `gen_boss_voice.CLEAR` lines retaken with `CLARITY`, picked words-then-confidence |
| Band's voiced lines | `tools/gen_band_voice.py` (every `speech.papon.band.*` + the `gasp` cue, cloned from the singer's reference) → `sounds/voice/band/*.ogg`, `voice/band.json` (mouth env, bubble lengths); pitch per member `MariachiEntity.voicePitch`; new lines: `--takes a,b` then `--build a,b` (only those rewritten) |
| Van / Skyline | `vehicle/CaravanEntity`, `client/CaravanRenderer`, `client/CaravanSounds` |
| Skyline (2026-10-06) | mesh `tools/gen_skyline.py` → `meshes/skyline.json` (+ `groups`: opaque, glass, lamps, brake, reverse, gauges, gloss, underglow), `textures/entity/skyline.png` + `skyline_lamps.png`, rig-only `geo/entity/skyline.geo.json` (bone `door_r`: right-hand drive); `CaravanRenderer.drawSkyline` skins it (bone matrices caught in `renderCubesOfBone`), clear coat, needles, flares; preview `tools/mesh_preview.py skyline out.png`; seats `CaravanEntity.SKY_SEATS` |
| Vehicle audio (2026-10-06) | `CaravanSounds.Sim` (rpm/gear/throttle/boost; `rpm(van)` drives the tach) + layered loops (`tools/gen_skyline_sounds.py`: `eng_idle/low/mid/high/decel` + `_in` cockpit set at -20 dBFS, `turbo`, `road`, `wind`, `shift`, `blip`, `pop`, `door_*`; loop starts picked for a clean decoded seam); van uses the same Sim with its two loops |
| Boss bars | `client/BossBarRenderer` (phase one `boss_bar.png`; two to four `bar_p2/3/4.png` from `tools/gen_boss_bars.py`: trail, hit flash, stage ticks) |
| La Mano HUD/camera | `client/GiantGrabHud` (struggle: jump → `PaponNetwork.Struggle`), `Cinematic.giantGrabShot` (outside camera while held, `pullIn` keeps it off walls) |
| Cutscenes, shake, music, bar, night, fog, sky | `client/{Cinematic,ScreenShake,BossMusic,BossBarRenderer,NightLight,PotatoSky,MuertosSky,MuertosArena,BlurFx,ClientEvents}` |
| Boss rendering/face | `client/{BossRenderer,BossModel,BossFace,BeamRenderer,PropRenderer}`; item-model props drawn opaque (`PropRenderer.drawSolid`, also `TumbleRenderer` and Ignis's head: `renderStatic`'s translucent sheet showed them see-through); `BossRenderer.eyePos` scaled (his eye beams left from his chest) |
| Elbows (2026-10-06) | ascended/muertos arms: `fore_r/l` bones (forearm, cuff, fist) hinged at `gen_ascended.FORE_PIVOT`, children of `arm_*` (not the mesh: no shear); `BossModel.forearm` slides/stretches them with `arm_*_mesh` every frame (animations unchanged), `bend` blends two-segment poses (`gen_boss_anims.guitar_bent` → `STRUM_*`, `BENT_*`): the serenade, the duel, P4_GIGANTE, and any animation resting a hand on the guitar (`restingOnGuitar`, by closeness to the straight pose). `phase-one papon_boss` has no forearm (falls back to `set`) |
| QTEs | `client/{QteClient,QteScreen,GuitarDuel,DanceOff,QtePlayerPose}`, `network/PaponNetwork` |
| Band/crowd client | `client/{MariachiModel,MariachiRenderer,BandPoses,ColiseumCrowdRenderer,CrowdSounds,SongPlayer,FlagFireworks,VoiceDuck,SoundMix}` |
| Pillow | `item/BodyPillowItem`, `entity/BodyPillowEntity` (synced `DATA_POSE` SLOUCH/EDGE, `Act` HUMP (plank)/FLOOR/LIFT/BEHIND, eased `down`/`lift`, rider eye heights via `EntityEvent.Size` in `CommonEvents`, jump = `PaponNetwork.PillowSwap`), `client/{PillowMesh (fold + knee),PillowItemRenderer,PillowLayer,PillowClient (rider pose = `RIDE` arm pose set in RenderPlayerEvent.Pre),BodyPillowRenderer}`; `-Pautoshot=pillow` |
| Ending | `TacoVendorEntity.reform/welcomeBack/order`, `client/{TacoMenuScreen,ScreenFade,VendorRenderer}` |
| Mesh celebrities (EDP445, Chugs) | `client/mesh/*`, `tools/{mh_human,gen_edp_mesh,gen_chugs_mesh,gen_celeb_anims,pose_solver}.py` |
| Damage types (`papon:caravan` bypasses shields; ours not `is_fire`), effects (Stunned, Brittle, Splattered) | `registry/{ModDamage,ModEffects}`, `data/papon/damage_type/*` |
| Entity Culling compat | `client/CullingCompat` |
| Commands | `command/PaponCommand` |
| Dev screenshot director | `client/dev/AutoShot` |
| Carry On blacklist | `data/carryon/tags/entity_types/entity_blacklist.json` |

## Wandering Papon / truck essentials
- States synced as **ordinal: append new states at the end** (`PaponState`, `BossState`, `BandState`, …; names are saved).
  Durations = one-shot animation lengths. Core: `RECEIVING → WALKING ⇄ SITTING_DOWN → GRILLING → PACKING_UP`, `ARRIVED (truck +
  line at t22) → QUEUED → PRESENTING → BUSTED`; raft, drink/smash/tired, sleep states start from WALKING only.
- WALKING priority: arrive (refined inland site) → deep water → night → Jarrito wore off → escort rule.
- Never dies: `hurt` false, no-op `die/kill`, `remove(KILLED)` ignored (only `discard()`, `/papon remove`), not targetable,
  `checkDespawn` no-op, `keepSafe()` teleports to surface if underground/in wall/lava.
- Line: `TacoLine` slots 1.5 apart; moves only while a player is in line (`lineSlack`); `/papon escort true` counts as one. Config
  `[truck]`. Vendor `IDLE → TAKING_ORDER → COOKING → SERVING`; with him at the window `INSPECTING → FRAUD → EXPOSED → UNMASK →
  REALIZE → RAGE → TacoBoss.startFight`. Dialogue = lang keys `speech.papon.<who>.<line>` (+ `.gloss`).

## The boss
- Cataclysm pattern: synced `BossState` + server tick counter, frame-timed hits, one-shot anims with matching lengths.
  `choose()`/`pickWeighted` per phase (`chooseAscended`, `choosePhaseFour`), weighted, last two excluded, staleness forced.
  `strike()`, `predict()` (sampled positions), `Mechanics.trueHit` = % max health through armor. Config `[boss]` (health 800, …).
- Damage windows: `armored(state)` big moves clang off (`DATA_ARMOR`); ending one (`winds`) → `WINDED` (x1.5). Composure
  (`composureFraction`), foils, salsa burst, evade, guard. Invulnerable in all cutscenes.
- Phase one: intro LEAP_OUT → CHUG → ROAR; swipes, leap, charge (ghost, through walls), uppercut/air spike, breath, molotovs,
  blink, gatling, dash, Los Tres Chefs (echoes), grab (Brittle), QTEs, van routine (TO_VAN → DRINK_HANDLE → ENTER_VAN → DRIVING
  manoeuvres → EXIT_STUMBLE), punt, bail-out.
- Van: `LivingEntity` with bicycle-model physics in `travel`; player client drives, server autopilots for the boss; run-over =
  Stunned; `lerpTo` takes each update whole. Only `/kill` or the keys remove it.
- Phase two (≤75%, `PHASE_TWO_AT`): P2_KNEEL → P2_TALK → P2_SEASON → P2_SCREAM (coliseum built at the white-out, `Coliseum`) →
  P2_ARRIVE → P2_NOTICE → P2_MARCH → P2_WAKE → P2_WAIT ⇄ P2_KICK → P2_COUNT → P2_SHOWTIME → P2_APOLOGY → P2_RESUME → P2_HYPE →
  IDLE at the first sung word. Mid-fight P2_SOLO / P2_CUTAWAY (client asks via `PaponNetwork`). Arena attacks: fryer, heckle,
  masher, La Ola, stampede, El Gran Meteoro; van is a spice (≤1 per 25 s).
- Phase three (≤50%): P3_KNOCKBACK → P3_GLARE → P3_STALK → P3_SNATCH → P3_RETURN → P3_BLACKOUT → (ascension) → P3_POWER →
  P3_SERENADE → P3_VERDICT → P3_TOSS → P3_DANCE → IDLE; Skyline via P3_CRUSH → P3_SHOWCAR (`stageLightsUp`: dusk, the van
  key-lit in `CaravanRenderer`). Blackout (`DATA_NIGHT`, `NightLight`) only through the serenade; `NIGHT_P3` = 0 (day), spotlight,
  crimson braziers. Ascended moves `P3_*` (combo, flash, comet, laser, solar + monoliths, mariachi, duel, piñata swing,
  braziers, nova/spent, nitro, sombrero, volcán, calaveras); QTEs kind 5 squeeze (K/L), kind 6 guitar duel (D F J K).
  (2026-10-06) The fight opens on Relámpago Dorado (`p3Opener`, pyro chase); cooldowns at `P3_PACE` 0.6. Ojos de Papa: leans in as
  it charges, the shot throws him back (`a_laser`), beams 1.05 wide with an eye flare and rings (`BeamRenderer.eyeBeam`).
- Phase four (≤25%, `PHASE_FOUR_AT`): P4_FALL → P4_RISEUP → P4_IGNIS → P4_PROPHECY → **P4_THRONE** (appended) → P4_BRETHREN →
  P4_SOULS → P4_HALFBAKED (form 4, El Coliseo de los Muertos swapped in the flash) → P4_BOW → P4_SERPENTS → P4_TALKING →
  P4_TOBAND → P4_HONOR → P4_MUCHACHAS → P4_GOODTIME → IDLE on the first sung word. Stands silent fall→souls (`p4Hushed`).
  Band (2026-10-05, never cowering in phase four): `shock` at the fall (BandState SHOCKED: `jaw_drop` → `shocked_a/b`, jaws dropped
  in the scar's band shot), (2026-10-06) `listen` from the prophecy (LISTEN appended: `listen_a/b/c`, still, leaning in, mouth shut, `MariachiEntity.watch` keeps them turned to him: the story is never "sung"; cutaway in P4_PROPHECY), `hype` only after he has risen (P4_HALFBAKED clear: HYPED `hype_<kind>`: fist pumps, pointing, arms up, nothing played) with voiced call-and-answer lines through BOW/SERPENTS (`p4_hype_*`), `rapt` (AWED, still) through the souls (the
  hype loops looked like playing), shouts at the souls/serpents/TOBAND, `settle` (READY) at P4_HONOR. (2026-10-06) Their farewell
  before the singer's "Sí, jefe": `p4_honor_a/b/c` (violin to singer, singer back, trumpet to all; beats `p4.farewell_a..c`), the rest
  turning to whoever speaks (`MariachiEntity.turnTo`; the stage's facing is the synced `stageYaw()`, never the singer's yaw), one band
  shot in `Cinematic`. Phase four's fireworks and pyro are blue and purple (`FlagFireworks.dead`, `PyroShow` SPIRIT) with soul-fire
  embers, souls and sparkles per burst; `p4Fireworks` volleys on his grand moves, the stage breaks and hits over 2.5%.
  Moonrise (P4_THRONE, beats `p4.moon` → `p4.moonlit`): daylight through the monologue; the moon up dark (`MuertosSky.lit`),
  `mistIn` banks rolling in off the stands and `DATA_FOG`/`DATA_NIGHT` ramping, then it lights and night falls (moon drawn in the
  day sky too: `NightLight` always calls `drawMoon`).
  Moves: P4_MARTILLO, P4_GIGANTE (`SpiritGiantEntity`: eye lasers, swipes fling into walls), P4_GRIETA, P4_PETALOS (petals live
  to the walls: `PETAL_LIFE`), P4_PAPEL, P4_VELAS, P4_DANZA (unused since 2026-10-05), P4_LUNA, P4_SERPIENTES, P4_FLECHAS,
  **P4_TZOMPANTLI** (appended: `SkullWave` lines/rings of skull posts, props `SKULL_LINE/RING`), **P4_BESO** (appended: the
  muchachas' attack; `SalsaDancerEntity` AWAY/KISS appended, props `ROSE/THORNS/VINE/PETAL_RING`), and the grand ones (weights `muertosWeight`, sums in `boss/Fiesta`,
  drawn by `client/MuertosArena` from `DATA_P4SEED/FIELD/CENTER`): P4_OUROBOROS (serpents on a ring, `SerpentPath.Ring`),
  P4_PISTA (beat-tile dance floor), P4_CEMPASUCHIL (marigold path, ectoplasm flood), P4_CASTILLO (fireworks tower: a solid prop
  `BossProp.CASTILLO` drawn by `PropRenderer.drawCastillo`; `Fiesta.FUSE` he lights it, sparks drop off the wheels `DROP`), P4_SALSA,
  P4_TANGO → P4_TANGO_WIN/LOSE (El Último Tango, QTE kind 7, `client/DanceOff`: call-and-response WASD + SPACE, mouse-led abrazo,
  applause meter). Serpents (2026-10-05): green fire sweeps (`breathe`/`tickBreaths`, now and then between moves:
  `tickPassiveBreath`), P4_ALIENTO (Aliento de Jade), P4_FUSION (La Gran Serpiente: both on one tight ring 30 up, a hunting beam,
  jumpable shockwaves). `p4Watchdog` puts him back on the sand from any stuck phase-four state.
  **Stages (2026-10-05, `p4Stage()`/`p4StageReached`, saved `P4Stage`, synced `DATA_P4STAGE`)**: 1 the guardian (25%→17%), 2 the
  storm (→8.5%), 3 the end of the world. Each break is **P4_RAGE** (appended; clutches his skull, screams, `p4_stage2/3`); the third
  goes straight into **P4_JUICIO** (appended, El Juicio Final, 25 s: serpents ring the floor and breathe via `closeRing` +
  `tickRingBreaths`, the giant's sweep/track lasers, double slams and a swipe, his arrows and the moon, a spiral of fiesta strikes,
  then the floor red and only the marigold circles survive the blast; WINDED +110 t after) and comes back every 75 s. Layers that
  run under his moves: `tickGiantLayer` (stage ≥2: the giant stays, own scheduler: swipe, laser track/sweep, slam, double slam;
  relocates to your end), `tickGiantHazards` (all giant damage, whoever directs it; P4_GIGANTE only sequences acts),
  `tickSerpentLayer` (dives on their own from stage 2, `serpentQueue` owed by his moves = the serpents inside his patterns),
  `tickTrails` (dives leave the floor burning), `tickChaos` (stage 3 eruptions), passive breaths by stage; cooldowns 1.25x/1.5x/2x.
  Aggression (2026-10-05): `p4Chain` (out of a move or a winded breath straight into El Martillo, likelier by stage), WINDED
  shorter by stage, `tickStillLayer` (stand still 3.5/2.75/2 s: the moon's beam on you), `tickMuchachaLayer` (a rose thrown every
  few seconds), serpents dive from stage 1, `hover` tighter round you.
  Client: `MuertosSky.bolts` (sky lightning by stage, red moon at 3), `BossRenderer` aura flare/stutter.
  **El Gigante's rift (2026-10-06)**: every summon has a clock (`SpiritGiantEntity.portal`, synced `DATA_PORTAL_END/LEN`; windows
  600 + P4_GIGANTE's `extendPortal`, `GIANT_WINDOW_2/3` 760/900, El Juicio and the rage extend it): the floor ring burns down
  (`SpiritGiantRenderer.floorRing`), the dead's arms pull at it (`spirits`, `pull()`), a bar under the boss bar (`BossBarRenderer.drawRift`).
  Out of time it is dragged under: SINK reworked (`SINK_T` 156, `DRAG_*`: seized, clings to the floor, one last grab at you
  `PaponBossEntity.giantGrasp` 18%, hands torn loose, the rift shuts); the boss sees SINK and respawns it after `giantRespawn`. No more
  sinking to relocate (a lunge instead). Its body is solid (`GIANT_BODY` 6.5 round its middle: thrown out, 6%). Orders: he points
  (`DATA_ORDER`, `BossModel.pointing`) and it heeds (`HEED` act: turns to him, nods) before La Mano always and some slams/lunges
  (`order`). Leaving P4_GIGANTE no longer sinks it when its hunt is due. `/papon giant portal|sink`.
  **El Gigante as a threat (2026-10-06)**: after P4_GIGANTE before the storm it stays and hunts `GIANT_HUNT` (600 t) then sinks;
  `tickGiantLayer` stalks you (`SpiritGiantEntity.stalk`), answers in ~1.5 s when you stand within its reach (`giantNearTicks`), guards
  him (crowd him: slam/swipe there, `giantGuardCd`), picks by range (under it: slam/swipe; arm's length: La Embestida `LUNGE`, sweep,
  eyes; far: La Mano, eyes). La Mano caught: hoisted 40 up, brought to its face, eyes charge (struggle free: mash jump), blast 26%,
  slam 14% + stun, then `mercyUntil` 2.5 s. Its dismount keeps the passenger where they are (the entity sits at the arena middle).
  `/papon giant <summon|lunge|grab|swipe|slam|slam2|laser>`.
  **El Gigante (rebuilt)**: entity at the arena's middle, `base()` where it stands (tracking range); `V` 0.74; acts add
  `SLAM_BOTH`, `ROAR`, `GRAB` (La Mano: synced wade target/yaw/hand, `base(partial)`/`forward(partial)` interpolate it; the caught
  player rides its hand via `positionRider`, `GrabHold` blocks the dismount; `tickGiantGrab`); RISE is the dig (124 t:
  `DIG_HANDS`, `DIG_HEAVE`, `dig()` debris = `BossProp.DEBRIS` slabs of the floor block); the Catrín hat in skull space
  (`SpiritGiantRenderer.hat`); head turns to `aim` (`look`/`skull`, same sums both sides; beams anchored with `BeamEntity.eye(giant)`);
  `client/GiantMesh` = SDF sugar skull + jaw by surface nets (AO + petals baked in vertex colours); renderer draws lit solid bone
  (`entityCutoutNoCull` + white texture, full bright) then one additive pass (shell, eyes, floor cracks). `BossModel.groove` on the beat. Serpents/dancers never saved. Death line `p4_death`.
- Forms: 1 baked texture, 2 risen (wings/crown/butter), 3 ascended, 4 Final Guardian (`gen_muertos_papon.py`).
- Ending: DEATH → Gerbin's pillow (`GerbinEntity.reward`) → `TacoVendorEntity.reform` / `welcomeBack` → free taco menu.
- Coliseum: built in the sky (floor build-limit − 40) into air only, unbreakable `papon:` blocks; plan is a pure function of
  the centre (teardown removes only planned positions holding our blocks); `arena_barrier` player-only; `Coliseum.Gate` keeps
  foreign mobs out; comes down 10 s with nobody in it or after his death; `ArenaPortalEntity` in the truck window.

## Gotchas (cause bugs)
- **Append enum states at the end**; ordinals are synced.
- **GeckoLib leaves unkeyed bones as they were**: every animation keys every bone the model touches (`DEFAULTS` in
  `gen_band_anims M.compile`, `B.pins` in boss anims), or one entity's pose leaks onto the next drawn.
- Boss phase-two/form bones (`blood`, `butter`, `fry_crown`, wings, `giant_shaker`, blush, …) are pinned to scale 0 in every main
  animation (`B.pins`); only the second controller "form" (`animateForm`, class `F`) keys them; it runs after "main" so it wins.
  `blush` shows only in `chug`, `drink_handle`, `drunk_swig`.
- **Blockbench multiplies scales** of animations played together: to preview, merge main + form (form's channels replacing).
- `BossFace.add` for shared bones (it takes last frame's addition back off; plain addition piles up). Two adders on one unkeyed
  channel break each other's take-back: `BossModel.setCustomAnimations` resets `body`'s rotation every frame (groove + face).
- No leash in a coliseum (the dead one is wider than `ARENA_RADIUS`; a ground path never starts while flying: he hung in the air).
- The band never babbles (the villagers' "hmm" was the complaint): every band line is recorded; a new `speech.papon.band.*` key needs
  `gen_band_voice.py --takes <line>` + build, then `gen_boss_voice.py --beats` (beats leave room for band lines: `BL`) and
  `gen_boss_anims.py`.
- Voice: phase-four lines all cloned from the `animated` reference (`P4_GEN`); `pick` drops takes more than `SIM_GATE` under the
  line's best speaker match. Check drift of built lines with `voice_modal.similarity`.
- **Glowmask removes those pixels from the normal pass**: nothing visible in phase one may be in a glowmask.
- Props never warp: arms split `arm_*` / `arm_*_mesh` / `hand_*` (`gen_vendor.rig_hands`).
- Geo cube origins are absolute: build instruments around 0 and move to pivot (`placed`).
- Tracked bone positions (`BossRenderer.bonePos/facePos/eyePos`) must be scaled by render scale (GeckoLib leaves it out).
- **Entity Culling** (tr7zw) hid/stopped entities the cutscene camera cut from: `client/CullingCompat` whitelists all `papon:`
  entities by reflection. Cutscene clock synced via `DATA_TICKS`; `Cinematic.moveCamera` auto-cuts on jumps; `BOSS_RANGE` 80.
- Rig conventions: rot X < 0 swings limb forward; `arm_r` Z > 0 / `arm_l` Z < 0 raise outward; position Z < 0 forward (face −Z).
  GeckoLib flips bone X and Z vs Blockbench.
- **Sodium Extras ("Embeddium++") distance culling** (pack config: 64 blocks round, 32 up/down) runs in
  `EntityRenderDispatcher.shouldRender`, so a renderer's own `shouldRender` cannot bypass it: `CullingCompat.distanceWhitelist` sets its
  mixin's `embPlus$checked/whitelisted` on every `papon:` entity and block entity type. Big visuals on small entities: draw by distance
  (`PropRenderer.shouldRender`), keep the entity near the arena's middle (beams at their midpoint, the giant at the centre) so the
  server's tracking range (capped by view distance) reaches every player.
- **Clicking during a cutscene kicked the player** (the camera is not the player, so the click's ray can hit their own body:
  vanilla kicks "Attempting to attack an invalid entity"): `ClientEvents.onInteract` cancels attack/use/pick while a cutscene runs.
  The user hit it mid phase two, rejoined to a half-built coliseum that came down as empty, and phase three played its ground
  version. Now: a coliseum loaded with him below it is entered at once (`resumeArena`), phases three and four wait for the
  coliseum unless there is no room for one (`arenaFailed`), and tickIdle rebuilds it at once when a phase is due.
  `-Pautoshot=phases` / `phasesdown` check the real-damage path.
- **Vanilla particle sends stop at 32 blocks** (and the client drops particles past 32 unless forced): every `Fx` effect goes
  through `Fx.far` (long-distance to every player); big client particles use `addParticle(options, true, ...)`. Without it the far
  half of an arena-wide attack was invisible though it still hit.
- **Glows never write depth**: vanilla `RenderType.lightning()` does, so a glow drawn before the crowd punched a hole in it (the
  stands missing behind phase three's spotlight). Use `PaponRenderTypes.glow()` for every additive effect; phase three's spotlight
  is queued in `BossRenderer.render` and drawn at AFTER_PARTICLES (`drawSpotlights`) so its light lies over the finished stands.
- Cutscene shots choosing a side with a dot product against the arena's middle can flip every frame when the middle is on the
  line (P4_HONOR did: the camera swapped sides constantly); pick sides from something fixed (the stage's facing).
- Voice: `gen_boss_voice.py` with no args retakes every line whose cached signature changed (all of phase four on Windows): to add
  lines, take and build only them (`g.generate(new)`, `g.FRESH = set(new)`, `g.build(meta)`).
- **Never `compileJava` while a dev client is starting** (it reads half-written classes: "modClass is null", mod loading fails);
  and when waiting on an autoshot run, wait for its new screenshots, not for "Stopping!" (the last run's log still has it).
- iCloud makes `* 2` duplicates under `build/` (namespace `papon 2` build failure):
  `find build -name "* [0-9]" -prune -exec rm -rf {} +`.

## Assets: generated — edit the scripts, never the JSON
- Generated asset JSON (geo, animations, models, song, voice data) is written **compact, one line per file: never
  pretty-print**. `sounds.json` one event per line (`geo_util.read_sounds/write_sounds`); `lang/en_us.json` one key per line.
  Java one-statement blocks on one line (`python3.11 tools/compact_java.py <files>`; no bytecode change, check `javap -c`).
- Scripts: `gen_model.py`/`gen_anims.py` (Wandering Papon), `gen_items.py`, `gen_truck.py`, `gen_customers.py`, `gen_vendor.py`
  (vendor, customer_papon, papon_boss + forms, props), `gen_npc_anims.py`, `gen_gerbin.py`, `gen_boss_anims.py` (gerbin + boss),
  `gen_caravan.py`, `gen_band.py`/`gen_band_anims.py`/`band_ik.py`, `gen_crowd.py`/`gen_crowd_samples.py`, `gen_flag.py`,
  `gen_sky.py`, `gen_blocks.py`, `gen_props.py`, `gen_extras.py` (pillow, sun stone, splats), `gen_ui.py`, `gen_particles.py`
  (vanilla jar `$PAPON_MC_JAR` + pack jars), `gen_sounds.py`, `gen_skyline_sounds.py`, `gen_p4_sounds.py`, `gen_muertos.py`,
  `gen_muertos_papon.py`, `gen_serpents.py`, `gen_salsa.py`, `gen_ignis_head.py`, `geo_util.py`. Animation sound keys are cue
  names (`SoundCues`, `onSoundCue`). Hand edits in Blockbench must be ported back into the scripts.
- Pack mods' **assets may be reused** (e.g. `tools/src/papon_base.png`, `chort_base.png`); their code is study-only
  (`tools/decompile_refs.sh`). Reference mods: Walking Crops (`wkcr`), L_Ender's Cataclysm, Block Factory's Bosses (cutscenes).
- Songs: `python tools/gen_song.py [--song para_siempre|persona_ideal] [--audio]` (demucs, librosa, Rhubarb in
  `tools/.cache/rhubarb`) → `assets/papon/song/*.json`; `.ogg` segments only with `--audio`; then `python tools/master_music.py`.
- libsndfile's Vorbis encoder overflows the stack on Windows on one big write (Python dies with no output, exit 127 or
  0xC00000FD): write `.ogg` in chunks (`master_music.py`, `gen_laser_sounds.py`).
- Mesh celebs: run the mesh generator then `python3 tools/gen_npc_anims.py`; Blender headless decimation (`$BLENDER`).
  Preview: `python3.11 tools/anim_preview.py edp445 walk_edp445 8 side`.

## Voice pipeline
- He speaks English in a cloned voice (Qwen3-TTS-12Hz-1.7B-Base on Modal, app `papon-voice`, L40S, volume `papon-voice-cache`);
  line `speech.papon.boss2.<line>` / sound `voice.boss2.<line>`; Rocko TTS (`gen_voice.py`) only as fallback.
- `python3.11 tools/gen_boss_voice.py` (`--build` from cached takes, `--redo a,b`, `--more a,b`, `--beats` only the beats).
  Modal profile: `PAPON_MODAL_PROFILE` or the active one (melvinczyk on the Mac: `python3.11 -m modal token new --profile
  melvinczyk`; zardman1 on Windows). Lines are directed text (`LINES`, `SPOKEN` for misread words, moods `DIRECTION`,
  `FRESH` lines retaken with `NATURAL` directions, `PICKED` pins takes). Whisper + speaker-embedding pick. Rhubarb lip sync →
  `voice/boss2.json`.
- **All cutscene timing comes from `gen_boss_voice.beats`** (from line lengths) into `boss2.json`; read by `VoiceLines.beat` and
  `gen_boss_anims.beat`. After new takes: `gen_boss_voice.py` then `gen_boss_anims.py`. BossState durations are fallbacks.
- No reference.mp3 → clone from built lines (`REFS_FROM_LINES`); no ffmpeg → pyloudnorm; run with `PYTHONIOENCODING=utf-8`.

## Windows notes
Python `%LOCALAPPDATA%\Programs\Python\Python312\python.exe`; run tools with `PYTHONPATH=tools`; `PAPON_MODS` must point at a pack
instance's mods folder (e.g. `C:/Users/czadi/curseforge/minecraft/Instances/OG GROID PACK/mods`). Generators re-encode PNG/ogg
byte-differently: compare contents and check out unchanged ones before committing. Autoshot save is `run/saves/papon_shots`.
MCP client temp: `export BB_SID="$TEMP/bb_mcp.sid" BB_SHOTS="$TEMP/bb_shots"`.

## Blockbench MCP
Blockbench 5.2.1 + MCP plugin 1.10.0 serves `http://localhost:3000/bb-mcp` while open. Repo `.mcp.json` registers it
(or `claude mcp add --transport http blockbench http://localhost:3000/bb-mcp`); open Blockbench first, reconnect from `/mcp`.
Fallback: `python3 tools/bb_mcp.py list | schema <tool> | <tool> '<json>'`. Tools: `create_project` (`geckolib_model`),
`from_geo_json`, `create_texture`, `risky_eval`, `create_offscreen_view`, `set_camera_angle`. Helpers `tools/bb_preview.py`
(`sheet`/`anim`/`save`, `BB_KEEP=1`), `bb_frames.py`, `bb_pose.py`. Save `.bbmodel` to `tools/blockbench/`.
- **Never send big files through MCP calls** (a 700 KB animation in `risky_eval` segfaulted it): have Blockbench read/write
  files itself via `require("fs")`.
- **Never run a JS loop waiting on `Project.close()`** in `risky_eval`: it is async and freezes Blockbench.
- Preview the EDP mesh in generic format with X mirrored.

## Branches and merges (other Claude sessions work here too)
```bash
git fetch origin && git switch -c claude/<task> origin/main
git push -u origin claude/<task>          # commit small and often, push early; Co-Authored-By line
git fetch origin && git merge origin/main # prefer merge over rebase; fix conflicts on your branch
```
Land via `gh pr create --base main`, or on request: `git switch main && git pull && git merge --no-ff claude/<task> &&
./gradlew build && git push`. Only merge when `./gradlew build` passes.

**Conflicts**
- Generated files (`assets/papon/geo/**`, `animations/**`, `textures/entity/**`, `taco_coupon.png`, `taco_truck.nbt`,
  `tools/blockbench/*.bbmodel`, previews): never hand-merge. Resolve the `tools/gen_*.py` scripts, regenerate, `git add -A`,
  re-save `.bbmodel`. Binary with no script change on one side: take that side whole (`git checkout --theirs/--ours`).
  ```bash
  python3 tools/gen_model.py && python3 tools/gen_anims.py && python3 tools/gen_items.py && python3 tools/gen_truck.py
  python3 tools/gen_customers.py && python3 tools/gen_vendor.py && python3 tools/gen_npc_anims.py
  ```
- Java by hand keeping both intents, then `./gradlew compileJava`. Hot spots: state enums (append), `WanderingPaponEntity`,
  `PaponBossEntity`, `ModEntities`/`ModItems` (keep every registration), `PaponCommand` (keep every subcommand).
- `en_us.json` / `sounds.json`: union of keys, valid JSON (`python3 -m json.tool …/lang/en_us.json`).
- `mod_version`: take the higher, bump again if shipping. `CLAUDE.md`: keep both sides' additions.
- After any resolution `./gradlew build` must pass before committing.

## Building and testing
Release jars **only in the main checkout** `~/Documents/Coding/Papon`, after merging to main, never a worktree:
```bash
cd ~/Documents/Coding/Papon
git status                 # must be clean; don't disturb another session's work
git pull --ff-only
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean build      # build/libs/papon-<version>.jar
```
Dev in worktrees is fine (`./gradlew compileJava`, `./gradlew runServer` in `run/`, gitignored).
- **Headless server**: `run/eula.txt` (`eula=true`), `run/server.properties` with `online-mode=false`, `enable-rcon=true`,
  `rcon.password=papon`, `rcon.port=25575`, optionally `level-type=minecraft\:flat`. Drive with
  `python3 tools/rcon.py "<command>"`. Watch `[debug]` log lines.
- `/papon` subcommands: `locate skip arrive remove escort placetruck distance target state tacoline serve fraud reveal boss van
  bossstate cutaway serpents`. `/papon escort true` = virtual escort (also loads chunks; boss targets an armor stand).
  `/papon distance <min> [max]` writes the config: reset after testing. Truck scene: `execute positioned <x y z> rotated <yaw> 0
  run papon tacoline`. Fight: `/papon reveal` or `/papon boss`; `/papon bossstate <STATE>` (P3_KNOCKBACK, P4_FALL, any P3_/P4_
  move forces the form).
- **Autoshot client** (`./gradlew runClient -Pautoshot[=X]`, shots in `run/screenshots/papon_*.png`, then quits): default (truck,
  unmasking, phase one), `phase2`, `coliseum`, `phase3` (ends with an Ascended move showcase), `phase4`, `climax` (the giant from every
  side, the storm, El Juicio Final), `phases` / `phasesdown` (real hits through phase three and into phase four; `phasesdown` with the coliseum down first), `serpents`, `voice`,
  `nights`, `ending`, `arena`, `pillow`, `drive`, `fight` (3-min AI fight log), `pose`, `state:<STATE>:<form>:<t1,t2,…>`,
  `skyline` (on a cleared lot at 6000 6000: eight sides, close-ups, the driver's eye, then a logged test drive), `giant` (straight to
  the storm: stalk, lunge, La Mano caught and struggled free, positions logged; `-Dpapon.autoshot.long=true` for its free roam).
  The test world keeps state between runs (a coliseum left standing at 4000 4000): tours that need open ground go elsewhere.
- Walking Crops is not in the dev run: the coupon handoff can only be tested in the real pack. Jarrito handing and chancla need a
  real player.

## Releases (every jar build)
1. Bump `mod_version` (never reuse), merge to `main`, push.
2. Build in the main checkout.
3. `git tag -a v<ver> -m "Papon <ver>" && git push origin v<ver>`
4. `gh release create v<ver> build/libs/papon-<ver>.jar --title "Papon <ver>" --notes-file <changelog.md>` (player-facing
   changelog since the previous tag + notable dev changes). If `gh` is not logged in, `GH_TOKEN` from
   `printf 'protocol=https\nhost=github.com\n\n' | git credential fill`.
5. Give the user the release URL.

## Rules
- `claude/*` branch, commit often, push.
- Release jars only from the main checkout after merging to main.
- Regenerate assets with `tools/*.py`; re-save `.bbmodel` after model changes.
- Bump `mod_version` for every jar; every build is a tagged GitHub release with a changelog.
- Never push to the Groid server (the instance's `CLAUDE.md` governs that).
- Keep this file current and terse: update the component map and phase notes when you land something.
