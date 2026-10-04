# Papon — the Groid Pack final boss

Forge 1.20.1 (47.4.10) + GeckoLib 4.8.4 mod, modid `papon`, package `com.groid.papon`.
Repo: https://github.com/melvinczyk/Papon. Read this whole file before working, and work on a
`claude/*` branch, never `main` (see "Working with other Claude models").

## Who and what

The Groid Pack is a boss-progression Forge 1.20.1 modpack (~190 mods) that runs a live server.
Instance on this Mac: `~/Documents/curseforge/minecraft/Instances/Groid Pack OG`. That folder
has its own `CLAUDE.md` with strict rules (the **server is production**, pull → edit → push →
verify; never push unattended). This mod does **not** touch the server or the instance — ship
the jar only when the user asks.

The final boss is a **joke boss played completely straight**: Papon, the potato mob from the
Walking Crops mod (`wkcr:papon`, jar `mods/walking crops-1.0.7.jar`). Joke premise, but the
user wants it **extremely polished**: smooth, dramatic animations; Cataclysm-quality mechanics;
Block Factory's Bosses-style cutscenes. "Most importantly is the boss fight and having it high
quality throughout."

## The user's requirements (keep these, they are the spec)

1. **Taco Coupon** item, crafted from boss trophies. Trophies are KubeJS blocks defined in the
   instance's `kubejs/startup_scripts/boss_trophies.js` (`kubejs:<ns>_<boss>_trophy`). Recipe
   must use the **wither, warden and ender dragon trophies, plus others to be decided later**.
   → `src/main/resources/data/papon/recipes/taco_coupon.json` (currently those three + paper).
2. Papons spawn naturally (Walking Crops). Giving one the coupon turns him into the
   **Wandering Papon**.
3. The Wandering Papon walks, at his **normal pace**, to the **nearest Taco Truck**.
4. The **Taco Truck is a structure that spawns the boss**. It must **NEVER generate in the
   world**; it only appears when the Wandering Papon quest is completed (he arrives).
5. **Players must be near him** for him to keep walking. With nobody near he **sits down,
   takes out a little grill and grills tacos**, looping forever until a player comes back, then
   **packs up and continues**.
6. He must **never die**, **enemies never harm him**, he **avoids caves, ravines, everything**,
   **fixes his own path if stuck**, and **never despawns no matter what**.
7. At the truck: a **line of customers** waiting for tacos, mixing internet personalities
   (**Don Pollo, EDP445, Badlands Chugs, Reviewbrah / The Report of the Week**) in between
   ordinary Papons so it looks natural. The player has to **wait in line**; the **Wandering
   Papon gets in line in front of the player**. The line is the **timer for the boss fight**
   and the comic relief: each customer who gets food **walks past the player and does their
   personality bit** (Don Pollo says the salsa is spicy, in Spanish; EDP445: "Yo these tacos
   is heat bro, straight mother fuckin heat bro"; ...). The characters must look like the
   real people (caricatures), as editable Blockbench models.
8. What **triggers the boss fight**: the Wandering Papon reaches the window and gives the Papon
   working the truck a **fraud taco voucher** (his Taco Coupon).
9. The **boss fight** at the truck, with **cutscenes**, Cataclysm-level mechanics. Joke boss,
   polished fight. (Built, see "The boss fight" below; the user's words:)
   - **Gerbin**, a worker in the back of the truck behind the chef: a **chort** (Imps & Demons,
     `realmrpg_demons:chort`) with **"Gerbin" as a name tag** over his head, **stripped of the
     items the chort carries** (its sack), in a **dishwasher's apron**; animations of him working:
     **cutting lettuce at a station** and **washing tomatoes in a sink**.
   - Chef Papon, handed the fraud coupon, gets **angry and looks found out**, **rips off his
     mustache** and **takes off his chef hat**: under it, **Norwood 7** male pattern baldness.
     Realizing he showed you his hair, he **flies into a rage**: the fight starts. He **jumps out
     through the front opening**, **chugs a whole bottle of tequila (you can see the amount)**.
   - A **2001 Dodge Caravan** made in Blockbench, a **real entity** on the battlefield, which the
     player can **drive** too. He **rams the player in it, unblockable**; the warning is him
     **drinking a whole handle of tequila (visible amount), a 2-3 second window**. He **exits
     right after the ram, stumbling drunk**, giving a few seconds before his next attack. He can
     **only use it when he gets close to it** (so taking the van away prevents it). He can **back
     into the player** too. The van has its **own sounds** (idling, driving...).
   - **His own boss bar, potato and taco themed**; **boss music blending slow and fast mariachi**.
   - **Phase one**: more combos, fast ones, and gimmicks with the car; the car driven accurately.
   - **Phase two at 75% health** (built 2026-10-03, see "Phase two" below): a cutscene, he kneels, his
     **POV with blood coming out of his mouth**, the **music stops**, "**okay, enough playing around**";
     a **huge salt shaker he summons above his head salts him and he gets more power, inside a flowing
     aura** (the user's call, replacing a hand-held shaker), he bakes golden; he **screams, the camera
     shakes**, and pulls everyone into his "**papon pocket dimension**": really a **huge dome where
     everything is potato blocks**. He **looks stronger and can fly**. The user rejected: pink sprout arms,
     spectral hands, tortilla wings, red glowing eyes, a direct copy of the Spiritcaller, white splits on
     his sides, too dark a bake. Phase-two parts must never show in phase-one animations.
   - **Phase three at 50% health (built 2026-10-03, see "Phase three" below), the user's spec:** he gets knocked
     back, **glares at the singer and the band**: "**This is NOT my style, let me show you how to really play**";
     walks over and **angrily snatches the guitar from the singer**, the band **all get scared of him**; walks back to
     the centre, **everything turns dark**, a **spotlight** comes on above him and the **user's song "Para Siempre"**
     plays (their copy, `tools/src/song/para_siempre.mp3`, gitignored, from `~/Downloads/Para Siempre.mp3`): he
     **plays the intro on the guitar and sings the beginning** (the record's own voice, the user's call: no voice
     conversion), "**This is more my style, learn muchachos**", **throws the guitar back** at the singer, "**What
     are you waiting for, GO**", the band **continues the song**; then serious: "**Now, let's dance!**". The user
     asked for it **very dramatic and epic**; they chose: keep his golden form with a **crimson** aura/fire, and
     the rest of the fight **stays night with the spotlight on him**.
   - **Phase two's arena, overhauled (2026-10-03, see "El Coliseo Papa" below):** the dome is gone. His
     pocket dimension is **a Roman coliseum themed on potatoes and on him**, his own arena where he fights
     challengers, its stands **full of Papon-like fans cheering him on against the player**. Teleported in,
     he is all excited to go on, but **the mariachi band is asleep**: he goes over and **wakes them**; they
     (a **full mariachi band of Papons, some taller and fatter than others**) **slowly get up, put their
     hats and jackets on and grab their instruments**; the **singer counts them off** and they play.
     The song is **"Canción del Mariachi" (Antonio Banderas & Los Lobos)**, the user's own copy, dropped at
     `tools/src/song/` (gitignored; never download it from YouTube). The singer has a **microphone**, his
     **lips and mouth lip-sync the song** (the Papon's mouth is the brown bar low on his body) (the gold
     aura he first had was taken off on the user's request, 2026-10-03); the intro starts with **a zoom in on his guitar playing**. Chef Papon then
     **apologises for his poor hospitality and taking so long, aura farms, and says "where were we?"**;
     **the fight starts when the singer starts singing**. The song **loops for the whole fight**, its
     **highs and lows following the state of the battle**.
   - **Coliseum nights (2026-10-03, the user's requests, see "Coliseum nights and the ending" below):** the potato
     smiley in the middle of the Mexican flags, redesigned and **the same on every flag in the stadium**; the fans
     carry **small flags with that design**; the crowd **livelier: jumping up, throwing their hands in the air**;
     the **sun and moon are potato variants during the fight** (day or night: the fight is long); **lights for the
     band stage at night, a lamp a member turns on in the background every time it turns to night**; **no chairs
     for the band except Gordo** (the guitarron, the one in the dialogue with the boss: he dozes off again and gets
     his chair kicked); **no gold aura on the singer**.
   - **The pillow:** the player **hugs the Makima pillow "sensually"** (an animation), can **lick it**, and can **put
     it on a bed and sleep next to it on their side**.
   - **After his fall:** once Gerbin gives you the pillow you **go back to the Taco Truck**; **Chef Papon is a vendor
     again, friendly, back working in the truck, and his vendor menu gives you free tacos**.
8. **Water:** he must never just float. In deep water he rides his own **llama raft** — not a
   separate entity, just more Papon animations (the llama lives in his model) — across rivers,
   lakes and oceans.
9. **Paths** should be efficient (no zig-zagging, no stopping between legs).
10. **Jarritos:** a drink made from Alex's Caves' *jar of radiation* (the item is
    `alexscaves:radon_bottle`, "Bottle of Radon") plus beetroot (red), carrot (orange), potato
    (yellow) or Farmer's Delight lettuce (green — FD has no lettuce, so **cabbage / cabbage leaf**).
    Players can drink them: **3 hunger, 2 saturation, nausea and speed**; unique 3D model. Given
    to Papon: **1.5x speed for 2 minutes**, then a **tired** animation and normal speed. The
    **yellow one makes him angry**: he takes it and **smashes it on the ground** (animation).
11. **Sleep:** at night he **sets down his backpack, unrolls a burlap potato sack** (the user
    rejected a sleeping bag — keep the potato theme), climbs in and sleeps until morning.
12. **Chancla** (crafted from 2 sponges): whacking a sleeping Papon has a **2% chance per whack**
    to wake him up **angry at you**, after which he **continues his quest**.
13. **Taco Truck placement:** always on land, **never on water and never on a shore/beach**.
14. A debug command for **how far away the Taco Truck is** (`/papon distance`).

## What exists (status as of 2026-10-03)

Done and compiling; tested headless on a dev server (`/papon escort true`): walking, grilling,
rafting across ~200 blocks of ocean and landing, sleeping at night and waking at dawn, the Jarrito
boost, inland truck placement from a sea target. Jarrito handing and the chancla need a real
player and were not exercised headless.

| Piece | Where |
|---|---|
| Mod entry, config (`papon-common.toml`) | `PaponMod.java`, `PaponConfig.java` |
| Taco Coupon item, spawn egg | `registry/ModItems.java`, `item/TacoCouponItem.java` |
| Coupon → Wandering Papon handoff (looks up `wkcr:papon` by name, no compile dep) | `event/CommonEvents.java` |
| Wandering Papon entity: state machine, escort, invulnerability, journey bar, sound cues | `entity/WanderingPaponEntity.java`, `entity/PaponState.java` |
| Surface-only pathing (blocks underground nodes), max fall 2 (no ravines) | `entity/ai/SurfaceNodeEvaluator.java`, `JourneyNavigation.java` |
| Long-distance walking: legs up to 28 blocks ranked by distance left, next leg picked 6 blocks early, blacklist, hop, rescue | `entity/ai/JourneyGoal.java` |
| Llama raft: steer along the bearing at the water line, land on the first reachable shore, steer round obstacles | `entity/RaftDriver.java` |
| Jarritos (4 flavours, food + Papon boost / yellow smash), chancla | `item/JarritoItem.java`, `item/ChanclaItem.java`, `WanderingPaponEntity.offerJarrito/onChancla`, `CommonEvents.onAttack` |
| Sleep at night / wake at dawn, snooze "Z" particle | `WanderingPaponEntity.shouldSleep`, `registry/ModParticles.java`, `client/SnoozeParticle.java` |
| Destination picking from biome map (no chunk loading): only inland sites (no sea/river/beach within ~48), refined on load to flat dry ground ≥12 blocks from water; reuse nearest existing truck | `journey/JourneyPlanner.java`, `journey/Terrain.java` |
| Journey/truck SavedData (`papon_journeys`) | `journey/JourneyData.java` |
| Taco Truck template placement on arrival, ground levelling | `journey/TacoTruck.java`, `data/papon/structures/taco_truck.nbt` |
| Taco Truck scene on arrival: truck + vendor + customers, best-facing site, lane levelling | `journey/TacoTruckScene.java`, `journey/TacoTruck.java`, `journey/TacoLine.java` |
| Papon the Taquero (`papon:taco_vendor`, 1.5x): runs the line, inspects the coupon, FRAUD | `entity/npc/TacoVendorEntity.java`, `VendorState.java` |
| Customers `papon:don_pollo|edp445|badlands_chugs|reviewbrah|customer_papon` | `entity/npc/TacoCustomerEntity.java`, `Customer.java`, `CustomerState.java` |
| Shared NPC base (unkillable, speech, voice blips), sound cue table | `entity/npc/TacoNpc.java`, `entity/npc/SoundCues.java` |
| Speech: lang-key lines as bubbles + chat (Spanish lines get a `.gloss` translation) | `entity/Speech.java`, `client/SpeechBubble.java`, `lang/en_us.json` `speech.papon.*` |
| Gerbin the dishwasher (`papon:gerbin`, chort at 1.25x, named): chop / wash loop at his stations, peeks and waves, cowers during the fight | `entity/npc/GerbinEntity.java`, `GerbinState.java`, `TacoTruck.gerbinChop/gerbinSink`, `TacoTruckScene.ensureGerbin` |
| The unmasking: FRAUD → EXPOSED → UNMASK (mustache rip, toque off, Norwood 7) → REALIZE → RAGE → boss | `TacoVendorEntity.unmasking`, `VendorState`, `gen_npc_anims.py` (`vendor_exposed/unmask/realize/rage`) |
| Chef Papon, El Calvo (`papon:papon_boss`, 1.5x): intro, five attacks, the van routine, phase two, death | `boss/PaponBossEntity.java`, `BossState.java`, `TacoBoss.java`, `MolotovEntity.java` |
| The 2001 Dodge Caravan (`papon:caravan`): drivable, boss autopilot, run-over damage (bypasses shields), sounds | `vehicle/CaravanEntity.java`, `client/CaravanRenderer.java`, `client/CaravanSounds.java` |
| Cutscenes (letterbox, scripted camera, subtitles), camera shake, boss music, custom boss bar | `client/Cinematic.java`, `ScreenShake.java`, `BossMusic.java`, `BossBarRenderer.java`, `ClientEvents.java` |
| Networking (shake S→C, horn C→S) | `network/PaponNetwork.java` |
| Tequila (drink; the molotov's look), Caravan Keys (park / pocket a van; the boss drops his) | `item/TequilaItem.java`, `item/CaravanKeysItem.java`, `loot_tables/entities/papon_boss.json` |
| Damage types (`papon:caravan` in `minecraft:bypasses_shield`; fire ones in `is_fire`) | `registry/ModDamage.java`, `data/papon/damage_type/*`, `data/minecraft/tags/damage_type/*` |
| Synthesized sounds and music | `registry/ModSounds.java`, `assets/papon/sounds.json`, `tools/gen_sounds.py` |
| Dev screenshot director (`./gradlew runClient -Pautoshot`) | `client/dev/AutoShot.java` |
| Spawn eggs for every new entity | `registry/ModItems.java` |
| EDP445 fist bump: right-click him empty-handed; a second "gesture" animation controller over his current one | `TacoCustomerEntity.mobInteract/tickBump`, `gen_celeb_anims.edp_fistbump` |
| Disguise: the vendor wears an obviously fake costume mustache (glue strip, price tag) over his own thin one (`real_stache`, also on the boss); brows raised 0.5 px and `gen_anims.guard_brows` keeps every animation off the eyes | `tools/gen_vendor.py`, `tools/gen_anims.py` |
| Phase two "El Papa Horneado": cutscene (kneel/POV blood/talk/season/scream/arrive), baked texture swap, fry wings + fry crown + butter on a second "form" animation controller, seasoning aura, flight, barrage/meteors/dive bombs/van throw | `boss/PaponBossEntity.java` (`tickKneel..tickArrive`, `animateForm`), `client/BossRenderer.java`, `client/Cinematic.phaseTwoShot`, `tools/gen_vendor.py` (`phase_two`, `baked`, `aura`), `tools/gen_boss_anims.py` (`b_p2_*`, class `F`) |
| El Coliseo Papa (replaces the potato dome): a potato-travertine Roman coliseum built in the sky over the fight into air only, unbreakable blocks, a player-only railing over the podium, everyone pulled up, torn down and sent home after his fall; strays put back on the ground at login | `boss/Coliseum.java`, `block/*`, `registry/ModBlocks.java`, `tools/gen_blocks.py` |
| The crowd: hundreds of Papon fans in the stands, one entity drawn in one pass (frustum-culled, back faces skipped, procedural poses per mood and on the song's beat); synthesized crowd noise | `boss/ColiseumCrowdEntity.java`, `boss/CrowdMood.java`, `client/ColiseumCrowdRenderer.java`, `client/CrowdSounds.java`, `tools/gen_band.py` (`arena_fan`), `tools/gen_crowd.py` |
| The mariachi band: thirteen Papons (singer with mic, requinto and aviators; 3 trumpets, 3 violins, guitar, vihuela, guitarron, a coro of 3), asleep → woken (the singer whacked) → dressed → playing; lip sync, strums, bowing, trumpet fingering and the coro's harmonies from the song's data, hand poses solved on the rig | `boss/MariachiEntity.java`, `BandRole.java`, `BandState.java`, `client/MariachiModel.java`, `client/BandPoses.java`, `client/MariachiRenderer.java`, `tools/gen_band.py`, `tools/gen_band_anims.py`, `tools/band_ik.py` |
| Potatoes from the stands (Splattered: 10% of the screen in mash for 5 s) | `boss/CrowdPotatoEntity.java`, `effect/SplatteredEffect.java`, `client/PotatoSplat.java`, `ColiseumCrowdEntity.pelt` |
| Gerbin's present: a Makima body pillow for every winner; hugged, licked, laid on a bed to sleep beside | `item/BodyPillowItem.java`, `entity/BodyPillowEntity.java`, `GerbinEntity.reward`, `client/GerbinRenderer.java`, `client/PillowClient.java`, `client/PillowLayer.java`, `client/BodyPillowRenderer.java`, `tools/gen_extras.py` |
| La Bandera Papa (one potato-smiley flag everywhere: podium, arches, band backdrop, the fans' hands), the livelier crowd, the potato sun and moon, the stage floodlights and the lamplighter | `tools/gen_flag.py`, `block/ArenaFlagBlock*`, `client/ArenaFlagRenderer.java`, `client/FlagCloth.java`, `ColiseumCrowdRenderer.lively`, `client/PotatoSky.java`, `tools/gen_sky.py`, `block/StageLampBlock*`, `client/StageLampRenderer.java`, `MariachiEntity.tickLamp` |
| The ending: back at the truck after Gerbin's pillow, Chef Papon reformed (bald, friendly), free tacos off his menu | `TacoVendorEntity.reform/welcomeBack/order`, `client/VendorRenderer.java`, `client/TacoMenuScreen.java`, `client/ScreenFade.java`, `ModItems.TACOS`, `gen_items.taco_icon`, `gen_npc_anims.v_wave` |
| The song: "Canción del Mariachi" cut into bar-aligned segments with energy levels; Rhubarb lip sync, vocal loudness/pitch and guitar strums; the adaptive score | `tools/gen_song.py`, `assets/papon/song/cancion.json`, `boss/Song.java`, `client/SongPlayer.java` |
| Foil-potato shields (phase two): orbit him, he takes a third of every hit while any is left, rewrapped 45 s after the last breaks | `boss/FoilPotatoEntity.java`, `client/FoilPotatoRenderer.java`, `gen_vendor.foil_potato` |
| The grab (Ignis-style, no escape, he is invincible through it): lunge → held in his fist (his passenger; `GrabHold` cancels the dismount) → blowtorched → salted (**Brittle**: armor and armor toughness −30% for 20 s) → hurled, half the time straight into a charge | `PaponBossEntity` (`tickGrab`, `tickGrabCombo`, `positionRider`), `effect/BrittleEffect.java`, `registry/ModEffects.java`, `gen_ui.brittle_icon` |
| Props never warp: arms split into `arm_*` (rotations) + `arm_*_mesh` (the stretched cube) + `hand_*` (slides to the end of the stretch, carries every prop) | `gen_vendor.rig_hands`, `gen_boss_anims.stretch` |
| Phase-one additions: dash combo (Taco Tornado), van punt (he kicks the parked van at you), bail-out (jumps out of the moving van at you), handbrake drift, door check, sharper van autopilot (speed-aware lead, stiffer steering, slows into turns, brakes and comes again instead of circling a target inside its turning circle); the van no longer looks like it crabs sideways (it takes each server update whole: `CaravanEntity.lerpTo`) | `PaponBossEntity` (`tickDash`, `tickPuntRun`, `tickVanKick`, `bailOut`, `doorCheck`), `vehicle/CaravanEntity` (`shove`, `slide`, `slideOver`) |
| Admin commands `/papon locate|skip|arrive|remove|escort|placetruck|distance|target|state|tacoline|serve|fraud|reveal|boss|van|bossstate` | `command/PaponCommand.java` |
| Renderers: Wandering Papon (only the Jarrito flavour he holds; speech bubble); crowd (per-type model, Papon hat variants, head turning, bubbles) | `client/WanderingPaponRenderer.java`, `client/CrowdRenderer.java`, `client/CrowdModel.java`, `client/ClientSetup.java` |
| EDP445 drawn as the EDP SIM mesh skinned onto the customer rig | `client/mesh/MeshCrowdRenderer.java`, `client/mesh/SkinnedMesh.java`, `tools/gen_edp_mesh.py` |
| EDP445 and Badlands Chugs as semi-photorealistic meshes (MakeHuman body + photographed faces), with elbows, jaw, eyelids | `tools/mh_human.py`, `tools/gen_edp_mesh.py`, `tools/gen_chugs_mesh.py`, `client/mesh/*` |
| Their full animation sets, poses solved by IK on the meshes; talking jaw + blinking | `tools/gen_celeb_anims.py`, `tools/pose_solver.py`, `CrowdModel.face`, `Customer.own` |
| Route planning: coarse A* over the loaded heightmap (cliffs/mountains, water, dead ends), speed-aware path following | `entity/ai/RoutePlanner.java`, `JourneyGoal.java`, `JourneyNavigation.java` |
| Carry On blacklist tag | `data/carryon/tags/entity_types/entity_blacklist.json` |
| Mesh celebrities' bodies: knees (`shin_r/_l` bones), relaxed hands, shoes, loose trousers, neck clear of the shoulders, Blender decimation to quads (~10.5k verts, head and hands untouched) | `tools/mh_human.py` (`relax_hands`, `dress`, `optimize`), `tools/blender_decimate.py` |
| Previews without Minecraft: Blender stills and animation filmstrips (GeckoLib keyframes evaluated, mesh skinned) | `tools/blender_preview.py`, `tools/anim_preview.py` + `tools/blender_filmstrip.py` |
| Phase three "Para Siempre" (half health): knockback, glare, the snatch, lights out round the bowl, the spotlight, his serenade (the song's first 18 s), the toss, the band's drop with the fire back crimson, "let's dance"; night for the rest of the fight | `boss/PaponBossEntity` (`startPhaseThree`, `tickP3*`), `BossState.P3_*`, `boss/BandState` (SCARED/ROBBED/AWED/CATCH), `client/NightLight.java`, `client/BossRenderer` (`drawSpotlight`), `client/BossModel` (serenade hands), `client/BossFace` (sings), `client/Cinematic.phaseThreeShot`, `client/SongPlayer` (`phaseThree`), `client/ColiseumCrowdRenderer` (lighters), `block/ArenaBrazierBlock` (LIT/CRIMSON), `tools/gen_song.py --song para_siempre` |
| Phase-two voice: his lines in English in a cloned voice (Qwen3-TTS on Modal), lip-synced, the cutscene timed to them | `tools/gen_boss_voice.py`, `tools/voice_modal.py`, `boss/VoiceLines.java`, `client/BossFace.java`, `client/BossModel.java`, `assets/papon/voice/boss2.json`, `sounds/voice/boss2/` |

### The boss fight (status 2026-10-02: built; tested headless and with the autoshot client)
- **Gerbin** is hired when a truck opens (and lazily, by the vendor, at older trucks). Stations are
  template cells (`GERBIN_CHOP` (2,1,2) facing the new prep counter on the west wall, `GERBIN_SINK`
  (5,1,2) facing the cauldron moved to the east wall); his props (board, lettuce, basket, bowls,
  suds) are bones in his own model, at 1.25x they line up with those blocks. Loop: TO_CHOP → CHOP
  (3 x 6 s) → [LOOK] → TO_SINK → WASH (3 x 5 s) → ...; SHOCKED → COWER while a vendor is unmasking
  or a boss is within 40 blocks, back to work 10 s after.
- **The unmasking** (vendor): FRAUD 60t → EXPOSED 60t → UNMASK 100t (rip at t19, the reveal at t57:
  `boss.reveal` sting, glint, action bar "Norwood 7.") → REALIZE 60t → RAGE 70t (roar t34) →
  `TacoBoss.startFight`: the vendor is discarded and `PaponBossEntity` takes his place with
  `begin(from, to, van)`. Old saves stuck in FURIOUS move on to EXPOSED. The vendor model gained
  `hair` (Norwood 7 horseshoe + ear puffs), `wisps`, `stache_mark`, `sweat`, `blush`, `tears`
  (all pinned 0 except where keyed); his arms stretch (scale Y) to reach his face and hat.
- **The boss** (synced `BossState`, Cataclysm pattern). Intro: LEAP_OUT 30t (scripted arc out of the
  window, noPhysics, lands t20) → CHUG 100t (750 ml, `bottle_liquid` drains 1.25-3.75 s) → ROAR
  40t (boss bar shown, title, music). **Phase one, reworked (2026-10-03, after a study of Cataclysm's
  bosses):** `choose()` picks by range/cooldown, avoids repeating the last attack, and anyone 3+ blocks
  above him or 18+ away gets the leap; four misses in a row (`misses`, reset on any hit) = frenzy:
  no pause, charges twice. Every hit goes through `strike()`: heavy hits break a raised shield
  (`Player.disableShield`) and land at 60%. `predict()` leads targets from their sampled positions
  (never the server-side player velocity). Attacks:
  SWIPE 28t (track to t6, lunge sized to the gap, forehand t10; re-track t12-16, lunge, backhand t20
  heavy) → SWIPE_OVERHEAD 26t if anything landed or you step back in (tracks to t16, hit t20 heavy +
  a 10-block ground wave `tickLineWave`, grounded only);
  LEAP (AI: crouch to t10, launch, hover with no gravity drifting over you while `warnRing` marks the
  spot, dive after 14t (10 in phase two)) → LEAP_LAND 30t (impact r3.6 heavy, then rings r3.5/5.5/7.5
  at t4/8/12 that only hit grounded targets: jump them);
  CHARGE_WINDUP 16t (tracks to t12) → CHARGE (AI: 0.62 b/t, 0.72 in phase two, turns ≤2.5°/t, hits each
  target once, flings sideways; a wall → STAGGER 40t, 1.75x damage taken) [→ another windup from t7];
  UPPERCUT 22t (launch at t12) → AIR_SPIKE (leaps after the launched target, spikes them down t7-13)
  → LEAP_LAND (small); BREATH sweeps across you at 2.8°/t (3.6) and leaves burning puddles (`fires`);
  THROW: a fan of 3 molotovs (5) at your predicted spot. SLAM is still in the enum but never chosen.
  BLINK (AI, 12t cycles, 9 in phase two with the anim at 1.33x): a red mark beside/behind you, he
  teleports there at mid-cycle and punches 3t later; 4 blinks (6), then a mark above you: he appears
  there and dives (LEAP from t11, short hover `blinkHover`). GATLING 70t: the `potato_gun` prop
  (spinning `gun_barrels`), a `PotatoShotEntity` a tick (two in phase two) t15-55, the barrel
  turning ≤6°/t toward your predicted spot; rounds ignore i-frames (1.2 dmg each).
  The CHARGE is a ghost (`DATA_GHOST`, noPhysics): through trees and walls, height from the median
  of 5 heightmap samples (`groundY`, so a lone trunk does not lift him), `unstick()` after; a charge
  that hit nothing ends in STAGGER (the punish window). Ghosts render see-through green
  (`getRenderColor` in BossRenderer/CaravanRenderer).
  Speed attribute 0.35; pause between attacks 4t (2 in phase two). The van: TO_VAN (walks to the driver's door jingling keys,
  only if the van is empty and within 40 blocks) → DRINK_HANDLE 50t (1.75 l `handle_liquid` drains:
  the warning, plus an action-bar line) → ENTER_VAN 16t → DRIVING (REV burnout 20t, then legs:
  ghost van (after the handle: `CaravanEntity.setGhost`, through anything, riding the terrain, soul
  fire; `settle()` puts it on open ground after), boss top speed 1.35 b/t; manoeuvres: RAM at the
  predicted spot (steering locks only inside 4 blocks), DONUT (circles at 8 blocks throwing molotovs
  out of the window, then cuts in), AIR once a drive (`launch` arc onto you: `vanImpact` crushes and
  stuns within 4.5); drive-by molotovs on the straights; 3 manoeuvres, 5 in phase two) → EXIT_STUMBLE 80t (takes 1.5x damage). PHASE at half
  health (faster, angrier, bar renamed). DEATH 100t (kneels, cries, falls; loot: Caravan Keys,
  2-4 Tequila). Invulnerable in the intro, PHASE and DEATH; half damage while in the van. Config
  `[boss]`: health 800, damageMultiplier, vanRamDamage 22, vanCooldownTicks 360, cinematics, bossMusic.
- **The van** is a `LivingEntity` (step-up, gravity, interpolation for free) with its own bicycle-model
  physics in `travel`: the driving player's client runs it (vanilla vehicle sync), the server runs
  it for the boss (`autopilot(target, reverse, lockSteering)`) and for an empty van. The server
  measures real speed from position deltas for run-over hits (`runOver`: anything inside the van's
  rotated footprint, on the bumper end it moves toward), crashes and tyre screech. Square hitbox
  (2.4) shorter than the van (5.6 blocks); the length is handled in code. Seats (van space, z
  forward; the model faces -Z): driver front left (0.5, +0.62), passenger, the middle bench (-0.47),
  the back bench (-1.75). Climbs 1.5 blocks. Every hit applies **Stunned** (`ModEffects.STUNNED`,
  config `stunTicks` 60 (3 s; renamed in 0.6.2 so old 160 values are dropped): no walking, no jumping, crawl pose via `event/StunEvents`, a hard shake
  for that player via `PaponNetwork.shake(ServerPlayer, ..)`); the front bumper runs you over where you
  stand, the rear bumper (reversing) launches you far. Jump = horn (packet). Only `/kill` or the keys remove it.
- **Client**: `Cinematic` puts a client-only marker entity in as the camera entity during
  EXPOSED..RAGE and LEAP_OUT..ROAR(t30) for players within 40 blocks; shots are computed per tick
  from the subject's synced state and `ticksInState()`, so they stay in step with the animations.
  HUD, hand, chat and the player's own body hidden; movement held; dialogue subtitled in the bottom
  letterbox bar (line + `.gloss`). `BossBarRenderer` replaces the vanilla bar for the
  `bossbar.papon.chef*` keys. `BossMusic` plays `music.boss` on the Jukebox/Note Blocks slider while a
  fighting boss is within 72 blocks and holds vanilla music off. `CaravanSounds` loops idle/drive
  pitched by speed; the van shows a km/h readout instead of mount hearts.

### Phase two (status 2026-10-03: built; checked with `./gradlew runClient -Pautoshot=phase2`)
- Trigger: health ≤ 75% (`PHASE_TWO_AT`) sets `phaseDue`; from IDLE: P2_KNEEL 120t (DATA_PHASE2 on, coughs blood
  onto the ground at t24/40/56, the camera becomes his eyes from t28 with a red pulsing vignette, looking down at
  it; t60-72 his left hand comes up in front of his eyes: bleeding too (`hand_blood` bone on `hand_l`, pinned in
  `B.pins`, shown in p2_kneel/p2_talk; drips t76/84/92), the camera pushes in on it; from t96 his head comes up
  to you). Every other living thing within 10 blocks of him is not rendered from the kneel to the scream
  (`Cinematic.hidden`, `ClientEvents.onRenderLiving`; not the van, not you in his POV) → P2_TALK 70t ("Okay... enough playing around.", in English) → P2_SEASON 100t (a giant salt
  shaker `giant_shaker` off the root over his head, salt t24-64, it bursts t66, form 1 = baked texture at
  t80 with a flash; the aura is `BossRenderer`'s energy-swirl layer) → P2_SCREAM 70t (form 2 at t15: wings,
  crown, butter; the dome is planned at t1 and built 700 blocks a tick; white-out t56-70; everyone within
  48 pulled in at the end) → P2_ARRIVE 50t (title, flash clears) → IDLE. Invulnerable throughout; the music
  cuts (`BossState.silent()`) and comes back at pitch 1.12 once he is risen.
- Drama: golden light rays burst out of him at the bake and through the scream (`BossRenderer.drawRays`, quads
  on `RenderType.lightning()`), visual-only lightning (`bolt`), the fog/sky tinted gold then blood-orange
  (`ClientEvents.onFogColor`), an FOV punch and a white flash; the camera dodges walls and the van
  (`Cinematic.unblock`: other side, craned up, high, then pulled in no closer than 2.5).
- Model: phase-two bones `blood`, `butter`, `fry_crown`, `wing_r/l > fry_r1..7`, `giant_shaker`. **Every main
  animation pins them to scale 0** (`B.pins`); only the second GeckoLib controller, "form" (`animateForm`,
  animations `p2_sprout`, `p2_form_idle|fly|attack|lift`, class `F`), keys them, and it runs after "main", so
  its channels win. The cutscene's main animations show the blood themselves. Blockbench multiplies scales
  of animations played together, so to preview the real result merge main + form into one animation
  (form's bone channels replacing main's) before loading it. Wing signs (by render): right wing +Y sweeps
  back, +Z raises; mirrored on the left. Fries carry their fan angle as rest rotation.
- `papon_boss_baked.png` (+ `_glowmask`, crown only) is swapped in by the renderer at form ≥ 1; GeckoLib's
  glowmask removes those pixels from the normal pass, so nothing visible in phase one may be in a glowmask.
- Risen (form 2): 1.75x render, hitbox x1.15, damage x1.2, flies in IDLE (`hover`: circles you 3-4 up inside
  the dome), lands for ground attacks. New: P2_BARRAGE 40t, P2_METEOR 70t (meteors list: ring, streak,
  impact + fire), P2_DIVE (three dive bombs; DATA_FLYING false while diving picks the dive anim), VAN_GRAB →
  VAN_THROW 52t (van `setCarried`, placed over his head, `launch`ed at t36, `vanImpact` on landing),
  LEAP replaced by the dive. Three foil potatoes orbit him.
- The dome is gone: see "El Coliseo Papa" below. (Old saves with a dome tear it down on load.)

### El Coliseo Papa (status 2026-10-03: built; checked with `./gradlew runClient -Pautoshot=coliseum`)
- **The building** (`boss/Coliseum`): planned when he kneels (P2_KNEEL t1) but **not built until the white-out**
  at the end of the scream (`scream.end` − 13; its shadow must not fall on the cutscene): `startBuild`, then 3200
  blocks a tick (~40k blocks, ~13 ticks), `enterArena` finishes any rest. `Coliseum.started()`; a loaded one counts as started. Floor at build limit − 40 (needs ≥ 12 over the
  highest terrain, or phase two stays on the ground with the old quick arrive). Every ring is an **offset
  of the sand's ellipse** (`offset(x,z)`: the d with (x/(A+d))² + (z/(B+d))² = 1; A 29, B 21), so rows keep a
  constant depth. Layout by d: sand (< 0; kerb, a ring, the golden Papon mosaic) · podium 0-2 (6 high,
  drapes, braziers, the gates at the long ends, **`arena_barrier` above it: collides with players only**,
  so they stay on the sand; anyone who gets into the stands anyway is put back) · stands 2-18 (rows 0-4,
  walkway, 6-10 over a 2-block wall, walkway, 12-15; aisles, doorways) · gallery · outer wall 19-21
  (3 storeys of arches with engaged columns, cornices, gold keystones, flags in some arches, an attic with
  windows, braziers on top). His box over the band stand (gold floor, throne, canopy), golden statues of
  him over both gates, the band stand: a half-moon stage against the north wall with a riser.
  The plan is a pure function of the centre: the teardown regenerates it and removes only planned
  positions that still hold one of our blocks (`ModBlocks.isArena`: any `papon:` block).
- **Getting in:** at the end of P2_SCREAM `enterArena`: players to `playerSpot` (south half, facing north),
  the van to `vanSpot`, him 12 up over the middle; the crowd entity and the band (asleep) are spawned
  (`populate`). `DATA_ARENA` (`inColiseum()`) is synced. Comes down at the end of his DEATH animation (so
  the band can play the ending as he falls), on removal, or 10 s with nobody in it. Back for more (he finds
  a target again): it goes up again and the band, already woken, just plays.
- **The beats** (`BossState`, all cutscene: invulnerable, `Cinematic.coliseumShot`): P2_ARRIVE 100t (drops in,
  superhero landing t36, crowd ROAR, title "EL COLISEO PAPA" t40, "¿ESTÁN LISTOS?" t46, points at the stand
  "¡MÚSICA, MAESTRO!" t74, crowd HUSH t86) → P2_NOTICE 60t ("...¿Maestro?", fumes) → P2_MARCH (one steady
  walk steered by the move control every tick, no path stops, to `wakeSpot()` 2.6 blocks in front of the
  sleeping singer) → P2_WAKE (the whack, user's request 2026-10-03, replacing the comal clangs: big spatula up
  t0-11, down on the singer's head t14 = `MariachiEntity.whack()`, the band jolts awake t17; his recorded
  "WAKE UP, you lazy bums!!!" at beat `wake.line`, the spatula jabbed on the stresses; ends at `wake.end`) →
  P2_WAIT (paces the stand front end to end, foot taps at each end, "Un momentito..."; walks over to the
  dozing guitarron) ⇄ P2_KICK 30t (boots his chair t12) → (all READY) P2_COUNT 80t (the singer counts in;
  `songStart` = the downbeat tick, synced to the band; crowd CLAP) → band PLAY, fireworks, crowd ROAR →
  P2_SHOWTIME 70t (the guitar close-up) → P2_APOLOGY 178t (walks over, "My apologies, amigo..." t30, aura farm
  from `apology.aura`) → P2_RESUME ("Now... where... were we?"; hands over when `P2_HYPE_LENGTH` (160t) is
  left) → P2_HYPE (working the stands, in his recorded voice: a hand to his ear, "Listen to them... They LOVE
  me." (charm1), the other side, the other ear; raise-the-roof on the beat with the bowl chanting, "And the
  crowd... goes WILD!!!" (landed1); a finger at you, fists together, "OLÉ!!!" (charm5) and the eruption t150:
  fireworks, shake, roar) → IDLE exactly at `songStart + Song.fightStartTicks()` (the first sung word).
  The hype is part of `gen_boss_voice.beats` (`HYPE`), which makes it **four** passes of the guitar intro
  (`song.passes`); `python tools/gen_boss_voice.py --beats` redoes only the beats from the recorded lengths.
- **The guitar solo** (P2_SOLO 60t): when a client's score enters `solo1`/`solo2` it sends `PaponNetwork.Solo`;
  the boss (`soloHeard`, 45 s cooldown, only from IDLE within 3 s) stops, air-guitars along (`p2_solo`) and
  the camera cuts to the singer: his hands, his face in the aura, then out over the sand from behind him.
  Invulnerable for those 3 s (`cutscene()`).
- **The band** (`MariachiEntity`, a TacoNpc: nothing hurts or moves them; `BandRole` by stage place: build
  width/height, instrument; `BandState`: SLEEP → STARTLE → GETUP → (the guitarron: RESLEEP → SLEEP until
  kicked) → JACKET → HAT → (singer: SHADES) → GRAB → READY → COUNT (singer) → PLAY → BOW at his fall; the
  singer instead: WHACKED 50t (`whacked`: up in the air 1.4 blocks *in the animation*, off the floor) →
  JACKET_SLOW 90t → HAT_SLOW 50t (dressing under protest) → SHADES ...). Only Gordo (the guitarron, the dozer)
  has a chair; everyone else sleeps sitting on the stage floor, the jacket folded behind him (`jacket_floor`;
  the chair animations are also written as `<name>_floor`, `MariachiEntity.chair`). Thirteen members: the
  coro (CORO_1..3, kind `coro`: maraca + hand mic) on the riser beside the trumpets (riser |x| ≤ 7).
  Model `mariachi` (`gen_band.py`): the wkcr rig split like the boss's (rig_hands); **the painted mouth bar is
  removed** and rebuilt as a lip rig (`mouth` width > `lip_up`, `lip_low`, `mouth_in`, `teeth`, `tongue`);
  eyelids; jacket/sleeves/sombrero on, or `jacket_chair`/`sombrero_floor` off; `chair` and the `*_floor`
  instruments are top-level bones (they stay put). Instrument cubes are built around (0,0,0) and moved to
  their pivot (`placed`): geo cube origins are absolute. `MariachiRenderer` hides every instrument but the
  role's, scales the build, hides the chair but Gordo's (no aura on the singer any more). Animations `gen_band_anims.py`; **every animation keys every bone `MariachiModel` touches**
  (`DEFAULTS` in `M.compile`; GeckoLib leaves unkeyed bones as they were, so one member's pose would leak
  onto the next drawn). Sleeves sit on `arm_*` (not the stretching mesh): a long reach shows the potato
  arm out of the cuff, and stretched arms thin out (`girth`, also in `BandPoses.girth`).
- **Instruments, overhauled 2026-10-03:** the singer plays a small **requinto** (`requinto_cubes`, low
  across his belly, neck to his left) under a **low microphone** (`mic_stand` > `mic_boom`, `mic_head`, the
  grille just under his lip): the two never touch and the lips stay in view. Violin rebuilt (bouts, waist,
  fingerboard, bridge, strings, chinrest) and held at belly height on his left (`band_ik.orient` turns
  it to `VIOLIN_SCROLL`/`VIOLIN_TOP`). Trumpets: valve caps are bones (`valve_1..3`), cheeks puff
  (`cheek_r/l`). **Hand poses are solved on the rig** (`tools/band_ik.py`: arm X/Y/Z + stretch so the hand
  or a point on the prop lands on a target, same rotation convention as `pose_solver.rot`): the bow arm at
  bow positions 0..1 with the bow kept square across the strings and the hair facing them, the violin's
  left hand low/high, both trumpet hands (play/lift/down), the requinto (strum, fret low/high), the coro mic
  (up/down). Written to `assets/papon/song/band_poses.json`, loaded by `client/BandPoses`.
- **The music on the band** (`MariachiModel`): trumpets raise their horns 0.45 s before each part of the
  `horn` track (a breath in first), lips pressed and the cheeks puffing per note (`notes`, a push on each
  attack), valves in the real Bb fingering of the melody line (`lead`), bells up on high notes; violins bow
  stroke by stroke from the `bow` track (positions blended through the solved poses), left hand following
  `lead` with vibrato, fiddles down where `fiddle` is 0; the coro lift their mics and lip-sync his words
  (Rhubarb shapes) wherever `harmony` is on; the singer strums, and in the solos/break takes the lead (the
  fretting hand runs with `lead`, a pick per note, leaning back, guitar face).
- **The song** (`tools/gen_song.py`, needs `pip install demucs librosa` and Rhubarb Lip Sync in
  `tools/.cache/rhubarb`): demucs splits vocals/drums/bass/other; Rhubarb (phonetic recogniser: Spanish)
  reads the vocals into mouth shapes; librosa tracks the beats (~127.5 bpm, it drifts: cut on detected
  beats, not a fixed grid). Segments (beat indices): intro 0-16 (guitar), verse1 16-64 (mid), break1 64-80
  (low), verse2 80-128 (mid), solo1 128-144 (mid), solo2 144-176 (high), breakdown 176-192 (low), climax1
  192-224 (high), climax2 224-248 (high), ending 248-end. Each with a short lead-in/tail so joins crossfade.
  `cancion.json` also has the vocal loudness and pitch and the guitar onset curve at 25 Hz, and the band's
  tracks: `harmony` (backing vocals: polyphony in the vocal stem after taking out the lead's harmonics,
  plus stereo width), `horn` (trumpet parts: break1, the verses' chorus halves, late solo2, the climax, the
  ending), `notes` + `lead` (the melody line in the guitar stem, pYIN), `bow` ([time, position] strokes:
  his syllables while he sings, the line around him) and `fiddle`. The `.ogg` segments are only rewritten
  with `--audio`.
- **The score** (`client/SongPlayer`, per client): starts from the synced downbeat, the intro `Song.introPasses()` times, then
  at each segment end the next in the song if it suits the fight, else the nearest that does: **high**
  under 35% health or in his worst (unstoppable, van, meteors, gravity, dive, QTEs), **low** while he is
  staggered/stumbling/broken/guarding, **mid** = the song runs on. His death: the song's ending. `songTime()`
  drives the singer's lips (`MariachiModel.sing`: shapes eased over 70 ms, opened by loudness, brows by
  pitch), the strums (guitar onsets), bowing and foot taps (beats), trumpets up only in loud parts, and the
  crowd's chant and claps. `BossMusic` steps aside in the coliseum.
- **The crowd** (`ColiseumCrowdEntity` at the middle: seed + mood; `Coliseum.seats(seed)` deals ~1300 seats,
  six in ten taken, clear of aisles, his box and the statues): `ColiseumCrowdRenderer` draws the baked
  `arena_fan` geo per fan (hats, props, jersey, skin tint, size from the seat's dice), poses from `CrowdMood`
  blended over 10 ticks, turned toward him (the band stand while HUSH). Moods: the boss sets them (ROAR when
  he lands one on you, BOO when you hurt him, a CHANT/WAVE/CLAP now and then, GASP/MOURN at his fall).
  ~100 fps in the dev client with the whole bowl in view. `CrowdSounds`: the murmur loop, one-shots on
  mood changes, "PA-PÓN!" on every other beat while chanting, a clap per beat while clapping.
- **Potatoes from the stands** (`ColiseumCrowdEntity.pelt`, from `tickArena` outside cutscenes): anyone on the
  sand within 7 blocks of the stands gets a potato every 2.5-5 s from one of the dozen nearest fans (the
  fan's wind-up synced as `DATA_THROW`, `ColiseumCrowdRenderer.throwPose`); `CrowdPotatoEntity`: 0.5
  damage, a small shove, and **Splattered** (`ModEffects.SPLATTERED`, 5 s): `client/PotatoSplat` covers 10%
  of the screen with three splats (`textures/gui/potato_splat.png`, `tools/gen_extras.py`) that slide and fade.
- **Gerbin's present** (user's request): at the end of his DEATH, everyone on his boss bar (or within 64)
  is owed a **Makima body pillow** (`item/BodyPillowItem`, use = hug); the nearest Gerbin within 160 blocks
  (`GerbinEntity.reward`) walks over hugging it (DELIVER, `carry`; slips out next to them if stuck) and hands
  it over (GIVE 60t, `give`); with no Gerbin it lands in their bag with a note. The pillow is drawn on his
  `pillow` bone by `client/GerbinRenderer`. Art: our own fan-art Makima in `tools/gen_extras.py`
  (`textures/item/body_pillow.png`: front 48x128 at 0,0, back at 48,0) — the user asked for an image
  sourced online; we drew one instead (copyright); a picture can be painted over those regions.
- **Voices:** in phase two he speaks with the cloned voice (see "Phase-two voice"); the hype and the solo use
  lines already recorded (charm1, landed1, charm5), so they need no new takes.
  The band speaks in bubbles with babble, like the Taco Truck crowd.

### Phase three, "Para Siempre" (status 2026-10-03: built; check with `./gradlew runClient -Pautoshot=phase3`)
- **Trigger**: a hit that takes him to ≤ 50% (`PHASE_THREE_AT`) in phase two sets `phaseThreeDue` (and where it came
  from); the next tick he is free (not a cutscene, QTE, the van, or the coliseum's own beats before the band plays)
  `startPhaseThree` knocks him out of whatever he was doing. A reload mid-cutscene starts it again from the top
  (`DATA_PHASE3` is only set at the band's drop). `/papon bossstate P3_KNOCKBACK` forces it (health to just under half).
- **Beats** (`BossState`, all cutscene/invulnerable; timed by `gen_boss_voice.beats` `glare.* snatch.* serenade.*
  verdict.* toss.* dance.*`): P3_KNOCKBACK 50t (scripted flight from the hitter, lands t14, skid to t26, up from 34;
  the band stops; `SongPlayer` winds the record down in pitch) → P3_GLARE (heartbeat, the maraca drop, "This... is
  NOT... my style." `notstyle`, "Let me show you how to REALLY play." `realplay`) → P3_STALK (walks to `wakeSpot`,
  thuds, the band cowers) → P3_SNATCH ("Give me THAT." `gimme`; `MariachiEntity.rob()`: `DATA_ROBBED` hides the
  singer's requinto, the boss shows `p3_guitar_carry`) → P3_RETURN (to the middle; `Coliseum.braziers()` put out in
  order round the bowl as he covers the distance, `DATA_NIGHT` up to 0.6) → P3_BLACKOUT 50t (all out, night 1,
  `DATA_SPOT` at t30) → P3_SERENADE (`serenade.end`: the song's `serenade` segment, 0-18.36 s: guitar alone, then
  voice and guitar; the band AWED; the crowd LIGHTERS mood, `ColiseumCrowdEntity.lighters` spreading 0→1) →
  P3_VERDICT ("THIS... is more my style. Learn, muchachos." `mystyle`) → P3_TOSS (a `BossProp` GUITAR on a ballistic
  arc to the singer, `catchGuitar` at toss.catch, "What are you WAITING for?! GO!!!" `go`; at the end the band
  `play(start, Song.PARA_SIEMPRE)` from the **pickup**) → P3_DANCE (the singer's a cappella pickup; at `dance.drop`
  the band comes in: braziers relit **crimson** (`lightsCrimson`), fireworks, `DATA_PHASE3`, bar "Para Siempre",
  night held at `NIGHT_P3`; "Now... let's DANCE." `dance`; title) → IDLE. No coliseum: knockback → P3_DANCE.
- **The song** (`gen_song.py --song para_siempre`: htdemucs_6s, cache `tools/.cache/song_p3`): 6/8 at ~103 tracked
  bpm (3 beats a bar). Segments: `serenade` 0-18.36 (level `cutscene`, his), `pickup` 18.36-20.46 (`intro`, the
  singer alone), verse1 (mid), chorus1 (high), break (low, guitar), verse2 (mid), climax (high), ending (end).
  Trumpets/violins from the 6-stem `other` stem's loudness. `Song.get(id)`; `SongPlayer.song()` is what plays;
  sounds `music.para_siempre.<id>`. Singing in the serenade is the record's own vocal (Rhubarb mouth shapes →
  `BossFace`), his hands are `BossModel` (poses solved by `gen_boss_anims.guitar_poses`, keep `STRUM/FRET_*` in step).
- **Night** (`client/NightLight`): the coliseum has full skylight, so the client darkens the lightmap (reflection on
  `LightTexture`: forces vanilla's rebuild each frame, then scales skylight by `1 - night`, keeps block light, tints
  it crimson once `isPhaseThree`; cell (15,15) stays white for his spotlight), covers the sky with a black box after
  the sky pass, and blacks the fog. **Spotlight**: `BossRenderer.drawSpotlight` (two additive cones from 30 up and a
  pool on the sand), him drawn full-bright, white-ash motes. **Braziers** `ArenaBrazierBlock.LIT/CRIMSON` (models
  `arena_brazier_out/_crimson`, `gen_blocks.py`).

### Phase-two voice (status 2026-10-03: built; check with `./gradlew runClient -Pautoshot=voice`)
- **From the kneel on he talks in English, in a cloned voice** (the user's reference: Spanish-accented English,
  Antonio Banderas charm; `tools/src/voice/reference.mp3`, gitignored). **Phase one and the Taco Truck use it too
  now** (2026-10-03): `PaponBossEntity.speak` says any line with a recording as `speech.papon.boss2.<line>` / sound
  `voice.boss2.<line>`, its bubble held as long as it plays; the vendor's lines are `vendor_*` in `LINES`
  (`TacoVendorEntity.line`; the unmasking lines moved to EXPOSED t36 / UNMASK t18 to fit the English lengths).
  The Rocko TTS (`gen_voice.py`) is only the fallback for a line with no recording.
- **Generating** (`python3.11 tools/gen_boss_voice.py`; `--build` rebuilds from cached takes, `--redo a,b` retakes,
  `--more a,b` adds takes): Modal profile **melvinczyk** (`python3.11 -m modal token new --profile melvinczyk`),
  app `papon-voice` (L40S, volume `papon-voice-cache` for the weights). Qwen3-TTS-12Hz-1.7B-Base clones in ICL
  mode from one of two cuts of the reference (`REFS`: `excited` for shouts, `animated` otherwise) and takes an
  instruction too (`DIRECTION` per mood; the Base model's generate accepts `instruct_ids`: tested, it doubles
  the pitch range). Lines are **directed text** (capitals = stress, ellipses = pauses: the user wanted emotion
  and dramatic pauses). 8 takes a line, Whisper large-v3 transcribes each, Qwen's speaker embedding scores it.
  Pick: never a take the model cut off mid-word, then the words right, then (for a "!" line) one Whisper heard
  exclaimed, then the most expressive. Trimmed with a quarter second after the last sound (no clipped endings).
- **Lip sync**: Rhubarb (macOS build in `tools/.cache/rhubarb`, PocketSphinx with the line as dialog) + loudness,
  pitch and stressed-syllable tracks at 50 Hz in `voice/boss2.json`. The face rig (`gen_vendor.face_rig`): the
  painted mouth bar replaced by the mariachi lip rig, `mouth_corner_r/l` (smile/scowl), `lid_r/l` (blinks, half
  lids, a wink; pinned to 0 in every animation). `client/BossFace` drives it every frame: shapes eased 60 ms,
  opened by loudness (max 2.6 px, or the lip sinks behind the apron), brows on pitch and stresses, nods, a mood
  per line (`LINES` moods) or per state when silent (blissful under the salt, smouldering + wink in the aura farm),
  blinks always. The line start is the client tick `DATA_SPEECH` changed (`setSpeech` forces the update, so the
  same line twice restarts it).
- **The cutscene follows the voice**: `gen_boss_voice.beats` works out every beat from the line lengths (ticks:
  `talk.*`, `season.*`, `scream.end`, `arrive.*`, `notice.*`, `wake.*`, `wait.*`, `kick.*`, `apology.*`,
  `resume.*`, `song.passes`) into `boss2.json`; `VoiceLines.beat` (server ticks, Cinematic shots, BossFace) and
  `gen_boss_anims.beat` (keyframes) read the same numbers, so after new takes run `gen_boss_voice.py` then
  `gen_boss_anims.py`. The `BossState` durations of those states are only fallbacks now. The apology + aura farm
  need more than two passes of the guitar intro, so `Song.introPasses()` comes from `song.passes` (4 with the hype).
- **Eyes** (`eye_r/l`, painted off the face, cubes now): `BossFace.eyes` moves them in their sockets toward the
  camera (verified: they follow it from either side), a mood's side-eye or downcast look, a dart every 2-4 s,
  wide (shout, surprise) or narrowed (sly, angry). **Shared bones** (body, brows) are added to through
  `BossFace.add`, which takes last frame's addition back off: GeckoLib does not reset a bone no animation keys
  if anything set it that frame, so a plain addition piles up (he spun round and round).
- **Mix**: lines mastered at -9.5 LUFS through a limiter; `client/VoiceDuck` brings the band and the crowd down
  to 32% while he talks. Fight lines never start within 1.5 s of the last one's end (`voiceFree`); the
  cutscene lines have a breath after each (the beats). A key light on him through the ground cutscene
  (`BossRenderer.keyLight`: the coliseum going up overhead shades him).
- **Apron**: torn off and flung away in P2_TALK (`talk.rip`), hidden for the rest of phase two (BossFace).
- **Crowd**: the bed and ROAR are the user's stadium recordings (`tools/gen_crowd_samples.py`, sources in
  `tools/src/crowd`, gitignored): three seamless ambience loops, three roars; gen_crowd.py keeps the rest.
- New lines of his: `bleed` (before "enough"), `charm0-5` (phase-two taunts), `hit0-1` (hurt), `landed0-1` (lands
  one in the coliseum) via `quip` (never over another line or in a cutscene).

### Coliseum nights and the ending (status 2026-10-03, v0.7.4; check with `-Pautoshot=nights` and `-Pautoshot=ending`)
- **La Bandera Papa** (`tools/gen_flag.py` → `textures/entity/potato_flag.png`, 120x68, 7:4): green | white with
  Papon's smiling potato face (fry crown, laurel wreath, tricolour knot) | red. One texture for every flag:
  `block/ArenaFlagBlock` (+ `ArenaFlagBlockEntity`: kind DRAPE 4.2x2.4 on the podium every 14 blocks of arc,
  ARCH 2.8x1.6 in the flagged arches facing out, BACKDROP 7x4 behind the band; the wall's normal set by the build)
  drawn as waving cloth by `client/ArenaFlagRenderer` + `client/FlagCloth` (a gold rod on brackets); the old drape
  blocks are only his box's canopy now (same palette, `gen_blocks` takes it from `gen_flag`). `Coliseum.Plan.special`
  places blocks whose block entity is told something after (`flagCandidate` keeps one cell per flag).
- **The fans' flags**: half the fans (`ColiseumCrowdRenderer.prop`) carry a stick (`arena_fan` `flag` bone); the
  cloth is `FlagCloth` in pole mode off the stick's tip, flapping harder as the arm swings.
- **Livelier crowd**: `ColiseumCrowdRenderer.lively` lays each fan's own moments over the mood (more often in
  ROAR/CHEER): two jumps with the arms flung up, both arms swaying overhead, a fist pumped, the flag swung in
  circles (or an overhead clap), a hop turned to the neighbour; ROAR is real jumping (off the ground half the time,
  legs tucked), flag holders wave them in CHEER/ROAR/CHANT; fewer fans sit in CHEER.
- **Potato sky** (`client/PotatoSky`, `tools/gen_sky.py`): while a boss is within 160 blocks (and 5 s after), the
  vanilla `sun.png` / `moon_phases.png` texture slots are re-registered to `textures/environment/potato_sun.png`
  (a grinning potato with fry rays) and `potato_moon_phases.png` (a sleepy silver potato, all 8 phases); put back
  after. Drawn additively by the sky: black is see-through, faces are deeper tones, not black.
- **Stage floodlights**: `block/StageLampBlock` (FACING, LIT: light 15) at the stage's back corners (`Coliseum.lamps()`,
  aimed at the singer through `StageLampBlockEntity.aim`), beam = `client/StageLampRenderer` (additive cone). The
  lamplighter (`BandRole.lamplighter()`, CORO_3), while the band plays, when night falls (day time 12600..23200) or
  breaks: `BandState.LAMP_GO` (walks to the stage-left lamp, stepping off the riser) → `LAMP_SWITCH` 40t (a tug on
  the chain at t16: both lamps switch, the far one 7t later) → `LAMP_BACK` → PLAY (`MariachiEntity.tickLamp`;
  animations `lamp_walk`, `lamp_switch`).
- **The pillow** (`item/BodyPillowItem`): hold use = hug (`client/PillowClient.HUG` arm pose: arms wrapped, cheek on
  it, slow sway, a nuzzle every few seconds; hearts, purrs), sneak + use = lick (`LICK`: up at the face, the head
  bobbing on each lick; slurps and drips; the tongue drawn by `client/PillowLayer`), first person by
  `applyForgeHandTransform`. Third person the pillow is drawn by `PillowLayer` (hugged, licked, carried under the
  arm; the item's own third-person transform is scale 0). Used on a bed (`onItemUseFirst`, before the bed takes the
  click): `entity/BodyPillowEntity` lies on one side of the bed (right-click to take back; drops if the bed goes);
  sleeping in that bed, `PillowClient` rolls the player onto their side facing it (RenderPlayerEvent Pre/Post).
- **The ending**: `TacoBoss.startFight` gives the boss his truck (`setTruck`, saved); at the end of his DEATH
  `TacoVendorEntity.reform` puts a friendly vendor (`DATA_FRIENDLY`) behind the counter at `portalAt`; Gerbin's GIVE
  (or the note) calls `TacoVendorEntity.welcomeBack(player, delay)`: `PaponNetwork.Fade` (`client/ScreenFade`), the
  player is put in front of the window facing it while it is black, he waves (`VendorState.GREETING`, animation
  `vendor_wave`) and says the tacos are free forever. Right-click him: `client/TacoMenuScreen` (four tacos,
  prices chalked out, GRATIS) → `PaponNetwork.OrderTaco` → he cooks (50t) and serves (`ModItems.TACOS`:
  al pastor, carne asada, pollo, papa). `client/VendorRenderer`: friendly = no toque, no fake mustache, hair shown.

### Boss hardening (status 2026-10-03, v0.7.2; check with `./gradlew runClient -Pautoshot=arena`)
- **Containment**: `Coliseum.Gate` refuses any foreign mob joining within 32 blocks of a standing coliseum (`keepAlive`
  each tick by its boss; `belongs`: non-mobs, `papon:` entities, tamed pets); strays are puffed away every 10 t. He and
  his van are put back on the sand if they leave it; players (not creative, not held) within 48 blocks off the sand
  are put back. A spectator does not count as someone in it (the coliseum comes down after 10 s with nobody).
- **Mix**: `client/SoundMix` scales one-shots near him to 60% and much lower while he talks; `VoiceDuck` 18%; crowd 55%.
- **Portal**: `ArenaPortalEntity` in the truck window (`portalAt` from `begin`) while the coliseum stands; sends you to
  the middle of the sand with 3 s of Resistance V. Not saved; closed in `leaveArena`.
- **QTE input**: `client/QteScreen` (blank, non-pausing) is up during a QTE so keys never reach keybinds.
- **Fire immunity**: our damage types are no longer in `minecraft:is_fire`, so fire attacks hurt the fire-immune.
- **Anti-cheese**: composure (`burstCapFraction` 0.04: hits past a rolling burst glance off; 2.5x when staggered),
  heavy hits also deal `armorPierceFraction` of max health through armor, SALSA_BURST when one player lands 4 melee
  hits in 2.5 s, TO_VAN/DRINK_HANDLE are unstoppable with a shove, speed 0.42, context-aware picker (`context`:
  shields, flyers, melee pressure, kiting, low health, overpowered → more QTEs), no breather after a landed hit in p2.
- **Arena attacks** (phase two, in the coliseum; base weight 2.0 in the picker): `ARENA_FRYER` (boss/Freidora: hot
  sand → frying oil in a pattern, 1.5 s + 10 s, **two blocks deep** (the top layer has no collision when oil is
  under it), every pool **at least 2 wide** (cells kept only inside a fully open 2x2), `FryingOilBlock`: 8% on the
  way in + **1.25% of max health every tick** through armor, x1.5 for the fire-immune / Fire Resistance (stripped
  while in it), the boil throws you up every 16 t (the way out); `Coliseum.restoreFloor` clears both layers after a
  reload), `ARENA_HECKLE` (the stands lob PRODUCE props for 7 s), `P2_MASHER` (boss/Masher, 130 t: **three slams**
  at `MASHER_SLAMS` t50/82/112; between them the holes move: `Masher.VARIANTS` grid layouts (half-cell slides,
  synced in the prop's delay slot) + a 12-30° turn; 30% of max health through armor per slam, slowed, no stun),
  `ARENA_OLA` (¡La Ola!: three salsa walls across the sand, 0.8 b/t, the long way, the short way, back; 1.2 s warn
  line; grounded = 18% + 2 Habanero + shove; jump them), `ARENA_STAMPEDE` (¡Estampida!: 3 volleys of 5 `ROLLER`
  props from alternating long sides, lanes shifted each volley, 1.1 s lane warning, 0.6 b/t, 22% + fling),
  `P2_GRAVITY` rebuilt as El Gran Meteoro (180 t, arena-sized spud, pull, distance-scaled impact, jumpable rings).
- **The van in phase two** is a spice: no forced TO_VAN (only phase one goes for it whenever `vanCd` is up); TO_VAN,
  VAN_GRAB, BRICK, PUNT_RUN are picker options (weight 0.5), at most one every `VAN_GAP` (25 s), never "overdue";
  3 manoeuvres a drive; `vanCd` after a drive 2x the config (36 s); VAN_GRAB cooldown 30 s. Risen damage x1.35.
- **QTEs**: kinds 3 ¡Apaga el Fuego! (mash one key 14x, 9 t each; bottle hold) and 4 El Tango del Papa (arrow keys on
  the song's beat, coliseum only; suplex hold); `qteBase()` maps them onto the three hold/fail animations.

### Boss rework (status 2026-10-03, v0.6.0)
- **Voice:** Chef Papon's lines (vendor + boss) are text-to-speech clips (`tools/gen_voice.py`, macOS `say` Rocko
  es_MX pitched down, mono Vorbis via soundfile; sounds `voice.<who>.<line>`), played where he stands; never in chat
  and no English gloss. The bubble over his head shows the Spanish line; the letterbox shows it only in his POV shot.
- **Parry** (GUARD/RIPOSTE): crossed cleavers in front of his face (the comal is only the griddle slam now); ripostes
  on the first blocked hit or after 8 t close, at most 24 t. Pose angles solved on the rig: GeckoLib flips bone X and Z.
- **Picker** (`choose` → `pickWeighted`): weighted draw, the last two attacks excluded, an attack unused for 30 s and
  valid now is forced, otherwise weight = base x (1 + ticks since used / 200); far/high targets get a weighted gap
  closer (leap, blink, charge, dash, gatling, dive) instead of always the leap; a ready close-range move unused for 20 s
  makes him walk in (3 s max, `closeMoveDue`). The QTEs take turns (`nextQte`), a missed QTE lunge retries in 8 s.
- **QTE:** 1.5 s per key (`QTE_WINDOW` 30), the first 0.6 s ignores presses (`QTE_GRACE`, keycap greyed).
  Dev: `-Pautoshot=fight` logs a 3-minute AI fight against a standing player.
- **Charge:** windup 9 t (0.45 s), 0.95 b/t (1.1 phase two), steering 3.5 deg/t.
- **Los Tres Chefs** (CLEAVER_TOSS): three `papon:papon_echo` (see-through copies on his model, `EchoRenderer`) blink in
  left/right/behind you up high; each draws a red line, glints, throws a `CLEAVER_DART` prop (two in phase two). Hit
  one to shatter it. Animation `echo_throw` (44 t, throws at 16/28).
- **Cleaver:** Dungeons Delight's iron cleaver sprite built up in 3D (`gen_props.cleaver_sprite`), thrown and held.
- **Particles:** every FX texture comes from the pack's jars (`tools/gen_particles.py`); billboards fade out right
  in front of the camera. The QTE has a slow-motion vignette + heartbeat.
- Dev: `-Pautoshot=pose` (boss frozen via `PaponBossEntity.devFrozen`; parry and summon from front/side, echoes);
  **Blockbench:** never send big files through MCP calls (the 700 KB boss animation file pasted into `risky_eval`
  segfaulted it): `bb_preview.py` has Blockbench read/write files itself with `require("fs")`; `BB_KEEP=1` skips closing
  projects. `tools/bb_pose.py` reposes the open project front/side/3-4. The parry angles were solved against the
  hand's world matrix read from Blockbench (its bone Euler: X and Y flipped, Z as is, order ZYX).
  iCloud makes `* 2`, `* 3` duplicates under build/: `find build -name "* [0-9]" -prune -exec rm -rf {} +`.

### The Taco Truck line (the boss timer)
- On arrival (`ARRIVED` tick 22) `TacoTruckScene.open` places the truck **windowGap** blocks
  ahead (`ceil(customers * 1.5)`), so the line fits between him and the window, spawns the
  vendor behind the counter and the customers on their slots, and queues the Wandering Papon
  last. `TacoTruck.bestFacing` turns him toward the flattest, driest ground first. If a vendor
  that still takes customers is within 48 blocks, he just joins that line instead.
- `TacoLine(window, out)`: slot i = window + out * 1.5i; served customers walk a walkway 2
  blocks to the `side()`. The vendor owns the queue (UUIDs) and tells everyone their slot every
  tick; anyone missing for 400 ticks drops out.
- The line only moves while a player stands **in line** (from their spot behind the Wandering
  Papon back down the lane, `lineSlack` = 2.5 blocks). Otherwise the action bar says to get in
  line and green particles mark the spot. `/papon escort true` counts as a player for testing.
  Config `[truck]`: `extraPapons` (3), `requirePlayerInLine`, `lineSlack`.
- Per customer: `orderTicks` + `cookCycles * 80` + 30 ticks at the window, ~9-12 s each; with
  4 regulars + 3 Papons the line is ~75 s. Order is random, Papons slotted between the stars.
- Vendor: `IDLE → TAKING_ORDER → COOKING → SERVING(30t) → IDLE …`; with the Wandering Papon at
  the window: `INSPECTING(150t) → FRAUD(60t) → FURIOUS`.
- Customer: `QUEUED → ORDERING → WAITING_FOOD → RECEIVING → LEAVING → TALKING → EXITING`
  (discarded ~22 blocks away) or `FLEEING` on the fraud. `TALKING` lasts `reactTicks` = the
  react animation, and fires `Customer.lines` at their ticks.
- Wandering Papon (new states, appended): `QUEUED → PRESENTING(48t) → BUSTED`. He is exempt
  from the underground rescue at the truck (`PaponState.atTruck()`, the awning reads as a roof).
- Dialogue is all lang keys (`speech.papon.<who>.<line>`, `.gloss` for the English under a
  Spanish line); Reviewbrah's rating is rolled per visit. No recorded voices: each character
  "babbles" a pitched vanilla sound (`Customer.voice`). Animation sound keys are cue names in
  `SoundCues` (`burp`, `glug`, `ring_hi`, `slam`, `roar`, ...).

### State machine
See the diagram in `entity/PaponState.java`. Core: `RECEIVING(84t) → WALKING ⇄ SITTING_DOWN(60t)
→ GRILLING → PACKING_UP(48t) → WALKING`, then `ARRIVED(64t, truck + line at tick 22) → QUEUED →
PRESENTING(48t) → BUSTED` (the taco line, see above), or `WAITING` forever at a truck with no vendor.
Added: `RAFT_BOARDING(32) → RAFTING ⇄ RAFT_IDLE → RAFT_LANDING(28)`; `DRINKING(48)`,
`SMASHING(60, smash particles at tick 28)`, `TIRED(52)`; `SLEEP_SETUP(90) → SLEEPING ⇄
SLEEP_FLINCH(16)`, `WAKING(80)`, `ANGRY_WAKE(52)`; then `QUEUED`, `PRESENTING(48)`, `BUSTED`.
Raft, Jarrito and sleep all start from `WALKING` only, so none can pull him out of the taco line. Durations equal the one-shot animation lengths
so server and client hand over together. State is synced as an int (ordinal — **append new
states at the end**) and picks the GeckoLib animation in `animate()`.

Priorities inside WALKING each tick: arrive (only on a refined, inland site) → deep water (2+ deep
for 6 ticks → raft) → night (sleep) → Jarrito wore off (tired) → escort rule. GRILLING packs up at
night too. While boosted the walk animation is `run`.

### Never dies / never despawns
`hurt` false, `isInvulnerableTo` true, `die`/`kill` no-op, `setHealth` clamps to max,
`remove(KILLED)` ignored (only `discard()` removes him — `/papon remove`), not attackable,
`canBeSeenAsEnemy` false + `LivingChangeTargetEvent` cancel, no projectiles/explosions/effects/
fall/fire/freeze/drowning, not pushable, no boats/leads/dimension change/Carry On, MISC category,
`checkDespawn` no-op, persistence required. `keepSafe()` teleports him to the surface if he is
ever underground >3s, in a wall, in lava or below the world.

## Assets: generated, then checked in Blockbench

Model, texture and animations are **generated by scripts** — edit the scripts, not the JSON:

- `tools/gen_model.py` → `assets/papon/geo/entity/wandering_papon.geo.json` + 256×256 texture.
  Body/arms/legs/brows are the wkcr Papon bone-for-bone at the same pixel UVs (source texture
  `tools/src/papon_base.png`). Added: straw hat, backpack + bedroll (a rolled potato sack), and
  props `grill` (+`taco_a`, `taco_b`), `grill_hand`, `spatula`, `shaker`, `coupon`, `eyelids`,
  `raft` (llama: `llama_neck`, `llama_head`, `llama_leg_*`, `llama_tail`), `paddle_r/l`,
  `jarrito_red/orange/yellow/green`, `backpack_ground`, `sleeping_bag` + `bag_cover` (these two
  are the burlap PAPAS potato sack; the bone names are historical).
- `tools/gen_anims.py` → `assets/papon/animations/entity/wandering_papon.animation.json`:
  `walk, idle (blinks), sit_down, grill, pack_up, receive_coupon, celebrate, raft_board, raft,
  raft_idle, raft_land, run, drink, smash, tired, sleep_setup, sleep, sleep_flinch, wake_up,
  angry_wake`. Catmull-Rom by default,
  GeckoLib easings (easeOutBack, easeOutBounce…) on accents, squash/stretch, secondary motion on
  hat/backpack. Props hide via scale 0; every animation not using a prop pins it to 0. Sound keys
  are **cue names** mapped to vanilla sounds in `WanderingPaponEntity.onSoundCue`.
- `tools/gen_items.py` → coupon icon, 3D Jarrito bottle models + textures (4 flavours), 3D
  chancla model + texture, snooze particle texture + json. `tools/gen_truck.py` → truck `.nbt` (placeholder
  build; window faces +Z, `TacoTruck.WINDOW = (4,0,8)`; can be replaced by a structure-block save).
- `tools/gen_gerbin.py` → Gerbin: the chort's bones and UVs from `tools/src/chort_base.png` (copied from
  the Imps & Demons jar like `papon_base.png` is from Walking Crops; the user asked for a chort), the
  sack left out, apron, gloves, station props. Animations in `tools/gen_boss_anims.py`.
- `tools/gen_vendor.py` also writes `papon_boss` (256² texture): the vendor's body + apron + the bald
  parts, props `bottle`(+`bottle_liquid`), `handle`(+`handle_liquid`), `big_spatula`, `molotov`,
  `lighter`, `keys`. Bottles hang from the fist by the neck so a raised arm is a chug; glass is
  painted translucent and the boss renders `entityTranslucent` so the level shows.
- `tools/gen_boss_anims.py` → `gerbin.animation.json` and `papon_boss.animation.json` (lengths =
  `GerbinState` / `BossState` durations, listed in its docstring).
- `tools/gen_caravan.py` → the van: geo (512² texture, translucent glass), `caravan_glowmask.png`
  (head/tail lamps, drawn while the engine runs) and `caravan.animation.json` (doors, idle shudder).
  Wheels, steering, body roll/pitch and the fuzzy dice are driven in code (`CaravanRenderer.Model`).
  Bedrock cube rotations: +X tilts a cube's +Z end up (hood +15, windshield +35).
- `tools/gen_ui.py` → `textures/gui/boss_bar.png`. `tools/gen_sounds.py` → every `.ogg`
  (`pip install numpy soundfile`): engine loops built circularly over whole engine cycles, one-shots,
  the reveal sting, and the ~108 s mariachi score (fanfarria / ranchera / son / puente / son final,
  original melodies in A harmonic minor; trumpets and violins additive, vihuela/guitar/guitarron
  Karplus-Strong). Write big Vorbis files in chunks (libsndfile overflows its stack otherwise).
  To use a real recording instead, drop it over `sounds/music/boss.ogg`.
- `tools/blockbench/wandering_papon.bbmodel` — Blockbench project with model + all animations;
  `tools/blockbench/preview.png` — key frames.

- `tools/gen_customers.py` → the four internet customers (`don_pollo`, `edp445`,
  `badlands_chugs`, `reviewbrah`): geo + 128×128 texture each, one shared humanoid rig
  (`root > leg_r/leg_l, torso > head, arm_r/arm_l > props`). Faces and jerseys are pixel grids
  in the script (each grid reads as seen from outside that face). Caricatures from public
  photos: Don Pollo = bald, black boxed beard, grin, yellow shirt + gold chain, King of Ohio crown
  and cape, drumstick + phone; EDP445 = midnight-green "445" jersey ("EDP / 445" on the back),
  green beanie, wide eyes; Badlands Chugs = biggest, bald with shine, goatee, white tee with red
  trim, gallon jug; Reviewbrah = pale, tall slicked-back hair, charcoal suit, red tie, glass of
  blue energy drink.
- **EDP445 in game is a skinned mesh, not those cubes** (the user's call: "we don't care about it
  being blocky"). `tools/gen_edp_mesh.py "<EDP SIM>/EDP SIM_Data"` (needs `pip install UnityPy
  numpy pillow`; the game copy lives in `~/Downloads/EDPsimV8`) converts the EDP SIM character:
  Mixamo humanoid → rig axes (right −X, front −Z), scaled to 33 px tall, T-pose arms swung down,
  54 bones folded onto the rig's six (two weights/vertex), and — because the source body is
  unclothed — the jersey/pants/shoes painted onto the atlas (never ship it bare). Writes
  `meshes/edp445.json`, `textures/entity/edp445_mesh.png` (1024²) and `geo/entity/edp445_mesh.geo.json`
  (bones at the mesh's joints + beanie/taco/phone cubes that reuse `edp445.png` via per-face UVs).
  `client/mesh/MeshCrowdRenderer` runs the normal GeckoLib customer model/animations, captures each
  bone's pose matrix in `renderCubesOfBone`, then CPU-skins and draws the mesh in `renderFinal`.
  The blocky `edp445` geo/texture stay (props texture, and fallback if the mesh is removed). When
  previewing the mesh in Blockbench's generic format, mirror X (GeckoLib bakes −X) or text reads
  backwards.
- **Mesh celebrities (current):** `tools/mh_human.py` is the shared builder (MakeHuman CC0 body,
  targets, rig with elbows/jaw/eyelids, teeth/tongue, multi-photo face projection, props painted
  into the atlas, locators for `pose_solver.py`). `gen_edp_mesh.py` and `gen_chugs_mesh.py` are thin
  character configs. The blocky `edp445` / `badlands_chugs` models are gone. Chugs keeps his
  mustache/goatee (the user wants his facial hair). EDP's head proportions are fitted to four
  frontal photos; his face blends two photos (detail from the first, tone from both). Run the
  generators, then `python3 tools/gen_npc_anims.py` (re-solves the IK poses). The notes below on
  the first Chugs version are history.
- **Badlands Chugs in game is also a skinned mesh** (the user wants the celebrities semi-photorealistic;
  Chugs first, the others to follow the same way). `python3 tools/gen_chugs_mesh.py` (numpy + Pillow
  only, no Blender) downloads into `tools/.cache/chugs` (gitignored): the **MakeHuman 1.1** base mesh,
  targets, default skeleton and weights (all CC0, from github.com/makehumancommunity/makehuman) and
  one frontal photo of him (YouTube thumbnail, `PHOTO_URL`). It morphs the body with MakeHuman targets
  (`TARGETS`: African male, max weight, round fat head, broad nose, smile...), fattens it along normals
  per body part (`FAT`, `BELLY`), irons the navel/nipples flat under the shirt, swings the A-pose arms
  down, folds MakeHuman's weights onto the rig, bakes ambient occlusion, and paints the MakeHuman UV
  atlas (2048²) in 3D: the photo is warped onto the face with a thin-plate spline through `LANDMARKS`
  (mesh vertex ↔ photo pixel; silhouette landmarks are `('side', y, sign)`), white-balanced to `SKIN`,
  matte (highlights flattened, hue evened to his skin), and **clean-shaven** — he has no facial hair,
  so `shave()` repaints the photo's mustache/goatee (`SHAVE` ellipses, `KEEP` = the mouth) with the
  skin around them; painted eyeballs; his black BADLANDS / CHUGS tee, black pants
  and shoes. Writes `meshes/badlands_chugs.json`, `textures/entity/badlands_chugs_mesh.png`,
  `geo/entity/badlands_chugs_mesh.geo.json` (jug and taco cubes reuse `badlands_chugs.png`, scaled 0.8).
  His rig has two extra bones, **`forearm_r` / `forearm_l`** (elbows): only his own animations
  (`idle_badlands_chugs`, `react_badlands_chugs`) bend them; the shared ones leave them at rest. His
  arm poses (`JUG_UP`/`JUG_ELBOW`/`JUG_TIP`/`JUG_LOW`, `TACO_IN_R`/`TACO_ELBOW`) were solved by forward
  kinematics on this rig so the jug's cap and the taco reach his mouth, so the blocky `badlands_chugs`
  (now only a fallback) no longer lines up with them. To redo a face: change the photo, re-mark
  `LANDMARKS` (render the mesh front view and the photo with numbered points side by side), regenerate.
  In-game check: `tools/blockbench/chugs_mesh_preview.png`.
- **Bodies, size, sound (2026-10-02):** EDP445 renders ~2.2 blocks and Chugs ~2.4 (he is 6'5"); `Customer.renderScale`
  and the hitboxes in `ModEntities` set it. Their faces are unchanged from the photo projection above (the user
  rejected the MediaPipe/FLAME face-reconstruction experiments; they are in this branch's history, not in the tree).
  `mh_human.optimize` runs Blender headless (`Blender -b`, found at /Applications/Blender.app or `$BLENDER`) to
  decimate; without Blender the full mesh is written. Meshes now export quads (`"corners": 4`), which
  `SkinnedMesh`/`MeshCrowdRenderer` draw directly. Their animations go through `gen_celeb_anims.crisp()` (cubic
  ease-in-out pose to pose; big `easeOutBack` swings become `easeInOutBack`); the walk's playback speed follows the
  entity's real stride (`TacoCustomerEntity.animate`). Sound cues are throttled per entity (`SoundCues.REPEAT_TICKS`).
  Check a change: `python3.11 tools/anim_preview.py edp445 walk_edp445 8 side` (python3.11 has numpy/Pillow).
- `tools/gen_vendor.py` → `taco_vendor` (wkcr body + toque, mustache, red apron; props notepad,
  pencil, spatula, served_taco, fraud_coupon, magnifier) and `customer_papon` (hats `sombrero`,
  `cap`: the renderer hides all but the entity's `Hat`).
- `tools/gen_npc_anims.py` → `customer.animation.json` (humans) and `papon_npc.animation.json`
  (`customer_*` and `vendor_*`). One-shot lengths are Java state durations (listed in its
  docstring). Character props (drumstick, jug, goblet) are never pinned; held drinks are
  counter-rotated against the arm so they stay upright.
- `tools/geo_util.py` (shared box-UV packer, painter, grid stamping).
- `tools/bb_preview.py` (`sheet` / `anim` / `save`) and `tools/bb_frames.py` drive Blockbench
  over MCP: contact sheets of a model from 4 angles, animation frames, and saving
  `tools/blockbench/<model>.bbmodel` (geometry + texture + animations) for every model.

Rig conventions (verified by render): rot X < 0 swings an arm/leg forward; Y yaws in the parent
frame (+Y = inward for `arm_l`, −Y inward for `arm_r`); `arm_r` Z > 0 / `arm_l` Z < 0 raise
outward; position Z < 0 is forward (face is −Z).

### Blockbench MCP — how to connect

Blockbench (desktop 5.2.1) with the **MCP plugin** (1.10.0) serves MCP over streamable HTTP at
`http://localhost:3000/bb-mcp` whenever Blockbench is open. Two ways to use it:

1. **As real MCP tools (preferred).** The repo ships `.mcp.json`:
   ```json
   { "mcpServers": { "blockbench": { "type": "http", "url": "http://localhost:3000/bb-mcp" } } }
   ```
   Claude Code picks it up when started in this repo (approve the project server when asked; check
   with `/mcp`). Equivalent manual setup:
   ```bash
   claude mcp add --transport http blockbench http://localhost:3000/bb-mcp
   ```
   Blockbench must already be running, or the server shows as failed — open Blockbench, then
   reconnect from `/mcp`. If port 3000 is taken, the plugin's port is set in Blockbench under
   the MCP plugin settings; update `.mcp.json` to match.
2. **Fallback client** when the tools are not registered in your session:
   `python3 tools/bb_mcp.py list | schema <tool> | <tool> '<json>'` (screenshots are saved as PNGs
   and the path printed). This is how the first model built everything.

Useful tools: `create_project` (format `geckolib_model`), `geckolib_set_project_settings`,
`from_geo_json`, `create_texture` (accepts a file path), `risky_eval` (full Blockbench JS — e.g.
`Animator.loadFile({path, content})`, `Animation.all`, `Timeline.setTime(t); Animator.preview()`,
`Codecs.project.compile()` to save a .bbmodel), `create_offscreen_view` + `set_camera_angle`
(renders without moving the user's camera), `geckolib_validate_model`, `geckolib_export_*`,
`geckolib_list_easings`. More tools appear once a project is open (the list is dynamic).

**On Windows** (the second dev machine): Python is `%LOCALAPPDATA%\Programs\Python\Python312\python.exe`
(Pillow installed); run the tools with `PYTHONPATH=tools` and point the MCP client's temp files
somewhere real, e.g. `export BB_SID="$TEMP/bb_mcp.sid" BB_SHOTS="$TEMP/bb_shots"`. Never run a JS
loop waiting on `Project.close()` through `risky_eval`: it is async and freezes Blockbench.

Workflow used for the Wandering Papon: generate geo/texture/animations with `tools/*.py` →
`from_geo_json` + `create_texture` into a `geckolib_model` project → `Animator.loadFile` the
animation JSON → scrub with `Timeline.setTime` and render offscreen views from several angles to
check poses → save `tools/blockbench/wandering_papon.bbmodel`. Hand edits made in Blockbench must
be ported back into the generator scripts, or the next regeneration overwrites them.

## Reference mods

All are in the pack instance at `~/Documents/curseforge/minecraft/Instances/Groid Pack OG/mods/`.
`tools/decompile_refs.sh [out]` unzips them and decompiles the relevant packages with Vineflower
(default `/tmp/papon_refs`). Their code is study material (don't copy it); their **assets may be used** (the user's
call: it is their pack, e.g. `tools/src/papon_base.png`, `chort_base.png`, and every boss-fight particle texture: `tools/gen_particles.py` reads them straight from the jars, no downloaded packs).

### Walking Crops — `walking crops-1.0.7.jar` (modid `wkcr`, MCreator + GeckoLib)
- `wkcr:papon` (`net.mcreator.wkcr.entity.PaponEntity`): hostile `Monster`, 0.9×1.5, 30 HP,
  speed 0.25, armor 0.8, attack 6. Spawns in plains/forest/jungle/swamp/river/beach biomes
  (`data/wkcr/forge/biome_modifier/papon_biome_modifier.json`, weight 100, groups of 2–3) and,
  as a Monster, in dark places — so a coupon can be used on a Papon in a cave (handled: he is
  pulled to the surface).
- Model `assets/wkcr/geo/papon.geo.json` (64×64): bones `todo > cyb > cuerpo(+bone, bone2 =
  eyebrows)`, `brazodr`/`brazoiz` arms, `piernadr`/`piernaiz` legs. Animations `quieto` (idle),
  `caminar` (walk), `correr` (run), `ataque`, `muerte`. Our model keeps these bones/UVs, renamed.
- Other crop mobs (king, king_raider, papoteus, barney, …) exist if the boss wants minions.

### L_Ender's Cataclysm — `L_Enders_Cataclysm-3.31.jar` (+ `lionfishapi-3.0.jar`)
The quality bar for **fight mechanics**.
- **Attack state machine:** `Internal_Animation_Monster` keeps a synced `ATTACK_STATE` int and
  a server `attackTicks` counter (reset on every state change and on the client via an entity
  event). Each attack is a goal: `InternalAttackGoal(entity, fromState, toState, endState,
  duration, seeTick, range)` starts an attack when in range and off cooldown;
  `InternalStateGoal(entity, state, state, nextState, duration, seeTick)` holds it for its
  duration then chains on (combos, branches like grab → success/fail).
- **Frame-accurate hits:** damage, projectiles, shockwaves and shakes fire on exact
  `attackTicks` values in `tick()` (e.g. Maledictus: `attackTicks == 25` → ground slam +
  `ScreenShake_Entity.ScreenShake(level, pos, radius 15, magnitude 0.05–0.3, 0, duration 20–40)`).
  Telegraph first (wind-up anim + particles), then hit on the impact frame.
- **Animation:** one vanilla `AnimationState` per attack (`getAnimationState(name)`), started
  when the state changes; definitions are Blockbench exports to Java keyframes with
  **Catmull-Rom** interpolation (`client/animation/*_Animation.java`). Looping idle/walk are
  layered under one-shots.
- **Effect entities** (`entity/effect`): `ScreenShake_Entity`, `Cm_Falling_Block_Entity`
  (flying debris from slams), `Wave_Entity`, `Flame_Strike_Entity`, `Abyss_Mark_Entity`,
  `Lightning_Storm_Entity`, `SkyColor_Entity` (tints the sky during a phase), `Sandstorm_Entity`.
- **Presentation:** custom boss bars (`CMBossInfoServer`, styled; Maledictus adds a second
  "rage meter" bar), per-boss music, phase transitions, death animations with a long death
  timer, arena structures.

### Block Factory's Bosses — `block_factorys_bosses-2.1.2-forge-1.20.1.jar`
The model for **cutscenes**, and for a structure that spawns its boss.
- **Boss base** `entity/boss/AbstractBossEntity` (Monster + GeoEntity): synced `BOSS_PHASE`
  (`INTRO_PHASE = -1`), `ATTACK_PHASE`, `ATTACK_COOLDOWN`, `ATTACK_ANIMTIME`, `HIT_ANIMTIME`,
  `SPAWN_ANIMTIME`, `DIE_ANIMTIME`, `BATTLE_TIME`, `CINEMATIC_BLACK_SCREEN`; music on/off,
  boss-bar visibility (hidden during the intro). `AbstractStateBossEntity` + `state/*`
  (`State`, `LambdaState`, `DataState`) is a builder-style state machine for attacks.
  Bosses are summoned by a **boss spawner block entity** in their structure
  (`onBossSpawnerSpawn`) — the pattern for "the Taco Truck spawns the boss".
- **Cutscene camera** (`client/camera/*`):
  - The boss's geo has a **camera bone**; the intro is a normal GeckoLib animation that also
    keyframes that bone (`anim_bosses_rise/entity/kraken_cinematic.animation.json` + geo).
  - **Instruction keyframes** in the animation drive it: `cinematic_start;camera;<bone>`,
    `cinematic_start;bars`, `cinematic_start;shake`, `cinematic_start;fov`,
    `cinematic_start;black`, `cinematic_start;hide_gui`, `cinematic_start;blur`,
    `cinematic_end[;<handler>]` (handlers: camera, bars, fov, shake, black, hide_gui, blur).
    `CinematicAnimationController` (an `AnimationController` with a custom instruction handler)
    parses them; an end listener stops the cinematic when the animation finishes.
  - `CameraRenderer`: each client tick reads the camera bone's world position; a `Camera`
    mixin (`CameraInject.setCinematicPosition`) overrides the camera position and
    `ViewportEvent.ComputeCameraAngles` sets yaw/pitch, both lerped by partial tick. Saves and
    restores the player's camera type; cancels the fire overlay.
  - `BarsRenderer` (letterbox, `textures/screens/cinematic_bars.png`), `FovRenderer`,
    `ShakeRenderer`, `BlackRenderer` (fade), `HideGUIRenderer`. Intro music per boss
    (`sounds/*/intro.ogg`).

### How these map onto Papon
- Wandering Papon (done) already uses the Cataclysm-style pattern for his routine: synced state
  + server tick counter + one-shot animations whose lengths equal the state durations.
- Boss fight (todo): Cataclysm attack goals + frame-timed hits + screen shake/debris; BFB-style
  intro cutscene from a camera bone in the boss's geo with `cinematic_*` instruction keyframes,
  started from `WanderingPaponEntity.onJourneyComplete()` / the truck.

## Working with other Claude models (branches and merges)

Several Claude sessions may work on this repo at once. **Never commit straight to `main`.**

1. Start from fresh main and make a branch per task, prefixed `claude/`:
   ```bash
   git fetch origin
   git switch -c claude/<short-task-name> origin/main     # e.g. claude/boss-cutscene
   ```
   One task per branch; keep it focused (the boss, the cutscene, the truck, …).
2. Commit small and often; push the branch early so others can see it:
   ```bash
   git push -u origin claude/<short-task-name>
   ```
   End commit messages with the `Co-Authored-By:` line the session gives you.
3. Before finishing (and whenever main moves), bring main into your branch and fix conflicts
   **on your branch**:
   ```bash
   git fetch origin
   git merge origin/main          # prefer merge over rebase on a pushed branch
   ```
4. Land it: open a PR (`gh pr create --base main`) or, if the user asks for a direct merge,
   `git switch main && git pull && git merge --no-ff claude/<task> && ./gradlew build && git push`.
   Only merge to main when `./gradlew build` passes.

### Resolving merge conflicts
- **Generated files** — `assets/papon/geo/**`, `assets/papon/animations/**`,
  `assets/papon/textures/entity/**`, `textures/item/taco_coupon.png`,
  `data/papon/structures/taco_truck.nbt`, `tools/blockbench/*.bbmodel`, `preview.png`, `crowd_preview.png`:
  never hand-merge. Resolve the **scripts** (`tools/gen_*.py`) first, then regenerate:
  ```bash
  python3 tools/gen_model.py && python3 tools/gen_anims.py && python3 tools/gen_items.py && python3 tools/gen_truck.py
  python3 tools/gen_customers.py && python3 tools/gen_vendor.py && python3 tools/gen_npc_anims.py
  git add -A
  ```
  and re-save the `.bbmodel` through Blockbench (see the workflow above). For binary files with
  no script change on one side, take that side whole: `git checkout --theirs <file>` (or `--ours`).
- **Java** — merge by hand, keeping both sides' intent; then `./gradlew compileJava`. Hot spots:
  `PaponState` (append new states at the end — the ordinal is synced and the name is saved),
  `WanderingPaponEntity` (the state switch, `animate()`, `onSoundCue`), `ModEntities`/`ModItems`
  (keep every registration), `PaponCommand` (keep every subcommand).
- **`en_us.json`** — union of both sides' keys; must stay valid JSON
  (`python3 -m json.tool src/main/resources/assets/papon/lang/en_us.json`).
- **`gradle.properties` `mod_version`** — take the higher version, then bump once more if a jar
  will be shipped from the merge.
- **`CLAUDE.md`** — keep both sides' additions; update the status table to reflect the merge.
- After any conflict resolution: `./gradlew build` must pass before you commit the merge.

## Building and testing

**Always build the release jar in the main git checkout, `~/Documents/Coding/Papon`, never in a
worktree** (`.claude/worktrees/*`). The jar the user takes is
`~/Documents/Coding/Papon/build/libs/papon-<version>.jar`. Merge your branch into `main` first,
then in the main checkout:

```bash
cd ~/Documents/Coding/Papon
git status                 # must be clean; do not disturb another session's uncommitted work
git pull --ff-only         # main as pushed
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean build      # jar in build/libs/papon-<version>.jar
```

Compiling and test runs inside your worktree while developing are fine (`./gradlew compileJava`,
`./gradlew runServer` — dev server in `run/`, gitignored); just never hand over a jar built there.
`run/` is not committed. For a headless test create `run/eula.txt` (`eula=true`) and
`run/server.properties` with `online-mode=false`, `enable-rcon=true`, `rcon.password=papon`,
`rcon.port=25575`; then drive it with `python3 tools/rcon.py "<command>" ...`:
`/forceload add …`, `/papon escort true` (a virtual escort that also keeps his chunks loaded),
`/papon distance <min> [max]` (saved to `run/config/papon-common.toml` — reset it after testing),
`/papon target <x> <z>` (sea coordinates are redirected inland), `/papon state DRINKING` etc.,
`/summon papon:wandering_papon …`, `/papon locate|skip|arrive`, and watch `[debug]` log lines.
The truck scene: `execute positioned <x y z> rotated <yaw> 0 run papon tacoline` builds the truck
ahead, the line, and queues a Wandering Papon; with `/papon escort true` the line runs on its own
and logs every vendor/customer state change. `/papon serve` finishes the current order,
`/papon fraud` feeds everyone ahead of the Wandering Papon so he goes straight to the window.
`level-type=minecraft\:flat` in `run/server.properties` gives a predictable test site.
The fight: `/papon reveal` (nearest truck straight to the fraud and the unmasking) or `/papon boss`
(boss + van right here); with `/papon escort true` the boss targets an armor stand when no player is
near (`summon armor_stand ...`) and logs every state and van leg. `/papon bossstate <STATE>`.
**Visual check without a person:** `./gradlew runClient -Pautoshot` quick-plays `run/saves/New World`,
`client/dev/AutoShot` builds a truck at 4000 4000, plays the fraud, the unmasking and the fight, and
saves `run/screenshots/papon_*.png` at the key moments (cutscene frames, boss bar, handle, ram,
stumble, driving), then quits. `-Pautoshot=coliseum` plays phase two from the kneel through the coliseum,
the band and the song into the fight, shoots every beat (`papon_c*.png`), the singer up close, the stands,
the bowl from outside, and logs fps (on Windows the save is `run/saves/papon_shots`, a copy of New World).
`-Pautoshot=phase2` instead spawns a boss by the player and shoots the new
phase-one moves (the grab included) and the whole of phase two (cutscene, dome, attacks, the dome coming
down); `-Pautoshot=drive` sends him to his van and logs its heading against where it really goes, server
and client. The save it quick-plays is `run/saves/papon_shots`. If the build fails on a namespace
`papon 2`, delete the `* 2` duplicates iCloud makes under `build/`.
Walking Crops is not in the dev run, so the coupon handoff can only be tested in the real pack.

## Releases (every build)

**Every jar build gets a GitHub release, even small ones** (the user asked for this). For each build:
1. Bump `mod_version` in `gradle.properties` (never reuse a version), merge to `main`, push.
2. Build in the main checkout (see "Building and testing").
3. Tag `main` as `v<version>` and push the tag:
   ```bash
   git tag -a v0.3.3 -m "Papon 0.3.3" && git push origin v0.3.3
   ```
4. Publish a release for the tag with the jar attached and a **changelog** (what changed since the
   previous tag, in player-facing terms, plus notable dev/tooling changes):
   ```bash
   gh release create v0.3.3 build/libs/papon-0.3.3.jar --title "Papon 0.3.3" --notes-file <changelog.md>
   ```
   `gh` is installed with Homebrew; if it is not logged in, `GH_TOKEN` can come from the git
   credential helper (`printf 'protocol=https\nhost=github.com\n\n' | git credential fill`).
5. Tell the user the release URL.

## Rules for working here
- Work on a `claude/*` branch, commit often and push (the user asked for this).
- Build release jars only in the main checkout `~/Documents/Coding/Papon` after merging to
  `main` — never in a worktree (the user asked for this).
- Regenerate assets with the `tools/*.py` scripts; re-save the `.bbmodel` after model changes.
- Bump `mod_version` on every jar handed to the pack; never ship two jars with the same version.
- Every build is published as a tagged GitHub release with a changelog (see "Releases").
- Don't push anything to the Groid server; the instance's `CLAUDE.md` governs that.
- Keep this file current: update the status table and notes when you land something.
