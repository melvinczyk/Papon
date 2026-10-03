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
9. Later (not built yet): the **boss fight** at the truck, with **cutscenes**, Cataclysm-level
   mechanics. Joke boss, polished fight.
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

## What exists (status as of 2026-10-02)

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
| Boss hook (placeholder) | `boss/TacoBoss.java` |
| Spawn eggs for every new entity | `registry/ModItems.java` |
| Admin commands `/papon locate|skip|arrive|remove|escort|placetruck|distance|target|state|tacoline|serve|fraud` | `command/PaponCommand.java` |
| Renderers: Wandering Papon (only the Jarrito flavour he holds; speech bubble); crowd (per-type model, Papon hat variants, head turning, bubbles) | `client/WanderingPaponRenderer.java`, `client/CrowdRenderer.java`, `client/CrowdModel.java`, `client/ClientSetup.java` |
| EDP445 drawn as the EDP SIM mesh skinned onto the customer rig | `client/mesh/MeshCrowdRenderer.java`, `client/mesh/SkinnedMesh.java`, `tools/gen_edp_mesh.py` |
| EDP445 and Badlands Chugs as semi-photorealistic meshes (MakeHuman body + photographed faces), with elbows, jaw, eyelids | `tools/mh_human.py`, `tools/gen_edp_mesh.py`, `tools/gen_chugs_mesh.py`, `client/mesh/*` |
| Their full animation sets, poses solved by IK on the meshes; talking jaw + blinking | `tools/gen_celeb_anims.py`, `tools/pose_solver.py`, `CrowdModel.face`, `Customer.own` |
| Route planning: coarse A* over the loaded heightmap (cliffs/mountains, water, dead ends), speed-aware path following | `entity/ai/RoutePlanner.java`, `JourneyGoal.java`, `JourneyNavigation.java` |
| Carry On blacklist tag | `data/carryon/tags/entity_types/entity_blacklist.json` |

**Boss hook:** `TacoBoss.onFraud(level, vendor, wanderer)` runs the moment the vendor declares
the coupon a fraud (vendor enters `FRAUD`, Wandering Papon `BUSTED`, customers flee, title
"¡FRAUDE!"). It only logs today; the fight and its cutscene start there. The vendor is the
natural boss (already 1.5x, already furious, in his own arena).

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
(default `/tmp/papon_refs`). Study material only: never copy their code or assets into this repo.

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
