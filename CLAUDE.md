# Papon — the Groid Pack final boss

Forge 1.20.1 (47.4.10) + GeckoLib 4.8.4 mod, modid `papon`, package `com.groid.papon`.
Repo: https://github.com/melvinczyk/Papon (branch `main`). Read this whole file before working.

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
7. Later (not built yet): the **boss fight** at the truck, with **cutscenes**, Cataclysm-level
   mechanics. Joke boss, polished fight.

## What exists (status as of 2026-10-02)

Done and compiling; tested headless on a dev server (`/papon escort true`).

| Piece | Where |
|---|---|
| Mod entry, config (`papon-common.toml`) | `PaponMod.java`, `PaponConfig.java` |
| Taco Coupon item, spawn egg | `registry/ModItems.java`, `item/TacoCouponItem.java` |
| Coupon → Wandering Papon handoff (looks up `wkcr:papon` by name, no compile dep) | `event/CommonEvents.java` |
| Wandering Papon entity: state machine, escort, invulnerability, journey bar, sound cues | `entity/WanderingPaponEntity.java`, `entity/PaponState.java` |
| Surface-only pathing (blocks underground nodes), max fall 2 (no ravines) | `entity/ai/SurfaceNodeEvaluator.java`, `JourneyNavigation.java` |
| Long-distance walking: waypoint fan ~20 blocks ahead, blacklist, hop, rescue | `entity/ai/JourneyGoal.java` |
| Destination picking from biome map (no chunk loading), reuse nearest existing truck | `journey/JourneyPlanner.java`, `journey/Terrain.java` |
| Journey/truck SavedData (`papon_journeys`) | `journey/JourneyData.java` |
| Taco Truck template placement on arrival, ground levelling | `journey/TacoTruck.java`, `data/papon/structures/taco_truck.nbt` |
| Admin commands `/papon locate|skip|arrive|remove|escort|placetruck` | `command/PaponCommand.java` |
| Renderer | `client/WanderingPaponRenderer.java`, `client/ClientSetup.java` |
| Carry On blacklist tag | `data/carryon/tags/entity_types/entity_blacklist.json` |

**Boss hook:** `WanderingPaponEntity.onJourneyComplete(ServerLevel)` runs right after the truck
is placed. Right-clicking him in `WAITING` shows a placeholder message. The boss, its cutscene
and its trigger go there.

### State machine
`RECEIVING(84t) → WALKING ⇄ SITTING_DOWN(60t) → GRILLING → PACKING_UP(48t) → WALKING …`,
then `ARRIVED(64t, truck appears at tick 22) → WAITING` forever. Durations equal the one-shot
animation lengths so server and client hand over together. State is synced as an int and picks
the GeckoLib animation in `animate()`.

### Never dies / never despawns
`hurt` false, `isInvulnerableTo` true, `die`/`kill` no-op, `setHealth` clamps to max,
`remove(KILLED)` ignored (only `discard()` removes him — `/papon remove`), not attackable,
`canBeSeenAsEnemy` false + `LivingChangeTargetEvent` cancel, no projectiles/explosions/effects/
fall/fire/freeze/drowning, not pushable, no boats/leads/dimension change/Carry On, MISC category,
`checkDespawn` no-op, persistence required. `keepSafe()` teleports him to the surface if he is
ever underground >3s, in a wall, in lava or below the world.

## Assets: generated, then checked in Blockbench

Model, texture and animations are **generated by scripts** — edit the scripts, not the JSON:

- `tools/gen_model.py` → `assets/papon/geo/entity/wandering_papon.geo.json` + 128×128 texture.
  Body/arms/legs/brows are the wkcr Papon bone-for-bone at the same pixel UVs (source texture
  `tools/src/papon_base.png`). Added: straw hat, backpack + bedroll, and props `grill`
  (+`taco_a`, `taco_b`), `grill_hand`, `spatula`, `shaker`, `coupon`.
- `tools/gen_anims.py` → `assets/papon/animations/entity/wandering_papon.animation.json`:
  `walk, idle, sit_down, grill, pack_up, receive_coupon, celebrate`. Catmull-Rom by default,
  GeckoLib easings (easeOutBack, easeOutBounce…) on accents, squash/stretch, secondary motion on
  hat/backpack. Props hide via scale 0; every animation not using a prop pins it to 0. Sound keys
  are **cue names** mapped to vanilla sounds in `WanderingPaponEntity.onSoundCue`.
- `tools/gen_items.py` → coupon item texture. `tools/gen_truck.py` → truck `.nbt` (placeholder
  build; window faces +Z, `TacoTruck.WINDOW = (4,0,8)`; can be replaced by a structure-block save).
- `tools/blockbench/wandering_papon.bbmodel` — Blockbench project with model + all animations.

Rig conventions (verified by render): rot X < 0 swings an arm/leg forward; Y yaws in the parent
frame (+Y = inward for `arm_l`, −Y inward for `arm_r`); `arm_r` Z > 0 / `arm_l` Z < 0 raise
outward; position Z < 0 is forward (face is −Z).

### Blockbench MCP
Blockbench (5.2.1, MCP plugin 1.10.0) serves MCP at `http://localhost:3000/bb-mcp`
(streamable HTTP). It was **not registered as an MCP server in Claude Code**, so it was driven
with a small Python JSON-RPC client (initialize → `tools/call`). Useful tools: `create_project`
(format `geckolib_model`), `from_geo_json`, `create_texture`, `risky_eval` (full Blockbench JS:
`Animator.loadFile`, `Timeline.setTime`, `Codecs.project.compile`), `create_offscreen_view` +
`set_camera_angle` for renders, `geckolib_*` tools. Note the tool list grows once a project is open.

## Research notes for the boss fight (from decompiling the pack's jars)

**Cataclysm (L_Enders_Cataclysm-3.31, lib lionfishapi)** — the mechanics bar to hit:
- Synced `ATTACK_STATE` int + server `attackTicks`; each attack is a goal
  (`InternalAttackGoal` picks it by range/cooldown, `InternalStateGoal` runs it for N ticks and
  chains to a follow-up state). Hits, projectiles, shockwaves fire on exact `attackTicks` so they
  land on the animation's impact frame.
- Client: one vanilla `AnimationState` per attack, started on the state change; animations are
  Blockbench exports as Java keyframe definitions with **Catmull-Rom** interpolation.
- Juice: `ScreenShake_Entity.ScreenShake(level, pos, radius, magnitude, duration, fadeOut)` on
  slams, custom boss bars (`CMBossInfoServer`, e.g. a separate rage meter), telegraph particles,
  per-boss music, multi-phase behaviour, combos with branches (grab → success/fail).

**Block Factory's Bosses (block_factorys_bosses-2.1.2)** — the cutscene technique:
- The boss's GeckoLib model has a **camera bone**; the intro is an ordinary GeckoLib animation
  that keyframes that bone (e.g. `kraken_cinematic.animation.json`).
- **Instruction keyframes** drive it: `cinematic_start;camera;<bone>`, `cinematic_start;bars`,
  `cinematic_start;shake`, `cinematic_start;fov`, `cinematic_start;hide_gui`, `cinematic_end`.
  A custom `AnimationController` parses them on the client.
- Client handlers: camera position from the bone's world position via a `Camera` mixin
  (`setCinematicPosition`), yaw/pitch in `ViewportEvent.ComputeCameraAngles` (interpolated with
  partial ticks), letterbox bars (`textures/screens/cinematic_bars.png`), FOV, shake, fade to
  black, hide HUD; restores the previous camera type on end. Intro music per boss.

## Building and testing

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew build            # jar in build/libs/papon-<version>.jar
./gradlew runServer        # run/ has eula + server.properties with RCON (password papon, port 25575)
```
Headless test: `/forceload add …`, `/papon escort true` (acts as a virtual escort and keeps his
chunks loaded), `/summon papon:wandering_papon …`, watch `[debug]` lines in the log.
Walking Crops is not in the dev run, so the coupon handoff can only be tested in the real pack.

## Rules for working here
- Commit often and push (the user asked for this).
- Regenerate assets with the `tools/*.py` scripts; re-save the `.bbmodel` after model changes.
- Bump `mod_version` on every jar handed to the pack; never ship two jars with the same version.
- Don't push anything to the Groid server; the instance's `CLAUDE.md` governs that.
