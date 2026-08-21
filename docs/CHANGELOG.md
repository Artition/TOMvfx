# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/). The versions below are guide/feature-set versions of the mod (as they progressed historically, see `docs/GUIDE.md`), plus git release tags where applicable (`v1.0.x`, `gradle.properties` → `mod_version`). Add new entries at the top, in the same PR as the behavior change.

## [Unreleased]
### Added
- **`[players]` argument in `/vfx playentity`.** The command now accepts an optional player list at the end (`/vfx playentity <effect> [{params}] <targets> [players]`) — who sees the effect. Previously it was always sent only to the executing player.
- **Stop marker: `/vfx key <effect> stop <time>`.** An explicit end-of-animation keyframe: when a running instance reaches `time`, it stops itself — fading out over its `fade_ticks`, or instantly without them. Also ends persistent and looping instances, and survives reconnects like regular keyframes (the server records and re-sends it).
### Changed
- **BREAKING**: `PROTOCOL_VERSION` 4 → 5: the play packet carries a resume offset (`elapsedTicks`) used when re-applying effects after a reconnect.
### Fixed
- **Effects no longer restart from the first keyframe after a reconnect.** The server now records keyframes added via `/vfx key` and, on rejoin, re-sends the play with the elapsed offset plus the recorded keys, so the animation continues where it left off. Keyframes past the nominal duration also extend the effect's lifetime correctly (previously such effects were dropped early or replayed from scratch).
- **Non-looping effects whose runtime edits animate to zero remove themselves.** A persistent instance keyed down to zero (`/vfx key ... <time> 0`, or `/vfx set param 0`) used to linger invisibly until the active-effect cap evicted it; it is now removed as soon as every edited parameter rests at zero. Definition-driven and looping effects are unaffected.

## v1.0.3
### Fixed
- Expression parser now accepts `_` in identifiers — documented variables (`player_x/y/z`, `light_level`, `time_of_day`) were declared in the `expr` switch but could never be parsed.

## v1.0.2 / Guide v17
### Added
- **Flashback compatibility** — client-local VFX effects (started via `VFXAPI.playEffect` or other mods on the client) are written into Flashback replays as custom actions and re-triggered during playback; effects already running when a recording starts are snapshotted into the replay. Flashback is a soft dependency (`suggests`, reflection-based, no compile-time coupling). Server-triggered effects already travel as `vfxweaver:vfx_trigger` packets which Flashback replays on its own.
- **Server-side effect memory (`VFXServerEffects`)** — effects sent via `VFXAPI.sendEffect` are remembered per player and re-applied on reconnect/join with their remaining duration (persistent `-1` effects always; finite ones while not expired). Pruned when expired; disabled during Flashback replay playback so replays are not doubled.

### Changed
- **Minecraft support widened to 26.1 – 26.1.2** (built against 26.1.2, `fabric.mod.json` `"minecraft": "~26.1"` covers the whole line; verified the API compiles on 26.1.2 without changes).
- **`speed_lines` reworked** — lines now emanate from the screen borders as wedges (full width at the edge, clipped by it, tapering to a point towards the centre) with sharp step edges, instead of a radial band around the centre. New `length_rand` param (0..1) controls how much the per-line length varies with the seed.

## v1.0.0 / Guide v16
### Added
- Entity effects `entity_tint` / `entity_outline` (by UUID), `/vfx playentity`.
- `through_blocks` (0/1) on both entity effect types.
### Added
- **Parameter overrides in `/vfx play`, `playat`, `playentity`.** These commands now accept an optional param-map `{[name:value],...}` (like `/vfx set`) that overrides the definition's default params at trigger time — including world coordinates (`pos_x/y/z`). This gives the command/datapack the same capability as the Java API (`sendEffect(...overrides)`).
### Added
- **New bindings and player variables.** Bindings: `distance` (raw distance from the camera to `pos` in blocks), `look_x/look_y/look_z` (components of the camera's look vector), `player_x/player_y/player_z` (the local player's position). Math expressions (`expr`) now expose player variables: `health`, `hunger`, `speed`, `light_level`, `time_of_day`, `player_x/y/z`. Datapack examples: `test_expr_health` (screen_flash, `expr: 1.0 - health`), `test_distance` (vignette via `bind: distance`).
### Added
- **Feedback for broken datapacks.** `/vfx list` now prints the list of datapack files that failed to parse on the last `/reload`, with the error text — previously they were silently skipped (log only). `VFXDefinitionManager` stores `parseErrors` (id → message).
### Added
- **`entity_selector` in the effect definition.** The `entity_selector` field (a selector string, e.g. `"@e[type=minecraft:zombie,distance=..10]"`) lets an entity effect (`entity_tint`/`entity_outline`) find its own targets: the server resolves the selector into UUIDs on every play, so `/vfx play <effect>` works without `playentity`. Datapack example — `test_zombie_outline`.
### Added
- **`gradient_map`: gradient build mode and colour coordinate.** New params `mode` (0 = linear, 1 = constant/stepped) and `pos` (0..1, colour coordinate). In linear mode `pos` shifts the transition centre (0.5 — no shift); in stepped mode it is a hard threshold: brighter than `pos` → colour `to`, darker → `from` (useful for masks/stylized shadows). The built-in `vfxweaver:gradient_map` got defaults `mode: 0`, `pos: 0.5`. True grayscale — linear with `from`=black, `to`=white, `pos`=0.5; a hard black/white mask — constant with `pos`=0.5 (0 — black, 0.5+ — white). Datapack examples: `test_grayscale`, `test_grayscale_constant`, `test_gradient_constant`.
- **`posterize`: clean colour reduction.** Removed the per-pixel dithering that produced large random colour steps at high strength ("pixelation"). The shader now does clean quantization (255 → 2 levels) without grain.
### Added
- **Entity effects respect the entity texture.** `entity_tint`/`entity_outline` now bind the entity texture (`Sampler0`) and use it as an alpha mask (like vanilla `rendertype_outline`): transparent pixels are discarded, so the effect follows the texture silhouette rather than a flat box around the model. `entity_tint` gained a `texture` param (0/1): `1` — recolour the texture (texture × colour, pattern visible), `0` — flat colour with the texture only as a mask. Render types memoized per entity texture.
### Added
- **Entity tint/outline (`entity_tint`, `entity_outline`)** — new effect types targeting entities by UUID. New subcommand `/vfx playentity <effect> <targets>` collects target UUIDs (up to 16) and sends them in `vfxweaver:vfx_trigger`; the client stores the UUID on the render state of living entities (mixin `LivingEntityRenderState`) and, in a second pass, redraws the entity model with a custom render type: `entity_tint` — a solid translucent fill of the effect colour, `entity_outline` — an inverted hull (inflated silhouette with front faces discarded), thickness via `width`. Both support `through_blocks` (0 — hidden behind walls, 1 — visible through them). Pipelines registered on the client, shaders — `assets/vfxweaver/shaders/core/entity_fx.{vsh,fsh}`.
### Fixed
- **Camera shake works again**: the per-instance seed (`instanceSeed`) shifted the noise domain by `seed * 0.0001` — for a random 64-bit seed that's ~10¹⁴, `SimplexNoise.fastFloor` overflows the int cast, and all noise samples became exactly 0 → the shake silently didn't play. The seed is now masked to 32 bits (phase range ~4.3e5, safe for the int lattice).
### Added
- **Math expressions in params** — a new way to set a param via `"expr": "sin(t * 0.1) + noise(x, y, z) * 0.2"`. Variables: `t` (ticks since start), `x`/`y`/`z` (camera coordinates), constants `pi`/`e`; functions `sin`, `cos`, `abs`, `min`, `max`, `pow`, `sqrt`, `random()` (0..1), `noise(x,y,z)` (simplex 3D, -1..1). The string compiles to an AST once (Recursive Descent Parser) when the instance is created and is evaluated every frame via `eval(t,x,y,z)` — no per-frame parsing. `random()`/`noise()` are deterministic per-instance seed, so each instance gets its own noise.
- **Unique camera shake** — `VFXActiveEffect` carries a random `instanceSeed`; `CameraShakeManager` shifts the noise domain by this seed, so every `/vfx play vfxweaver:camera_shake` gives a non-repeating shake.
- `SimplexNoise` moved to the shared (common) source (`com.tom.vfx.noise`) — now available both to math expressions and to camera shake.
- **Sound params `volume`/`pitch`** — the effect sound (the `sound` field) can now set volume and pitch via the reserved `volume`/`pitch` params, supporting all modes (constant, animation, world/camera bind, expression). Values are read once at start time (one-shot sound). Example: `"volume": { "bind": "proximity", "pos": [8,80,8], "range": 32 }` — louder near the point.
- **Positional sound `sound_pos`** — the `sound_pos: [x,y,z]` field plays the sound in the world at coordinates via the vanilla mechanism (like `/playsound ... x y z`, with distance falloff); without it the sound plays directly to the player. The position is overridable via the API (`sound_pos_x/y/z` in `sendEffect` overrides).
### Security
- Server packet sizes bounded: the param map in `vfxweaver:vfx_trigger` — max 32 entries; the definition/curve maps in `vfxweaver:vfx_sync` — 1024/256 entries (strings already capped by `ByteBufCodecs.STRING_UTF8`). Protects the client from OOM on a hostile/broken server.
- `VFXSyncPayload` got `protocolVersion` (checked on the client before applying).
- `VFXEffectManager.play` caps server-supplied duration (`MAX_DURATION_TICKS` = 1 hour); persistent/loop semantics from the definition are preserved, but an arbitrary negative/huge `durationTicks` from the server no longer creates an infinite effect.
- `VFXEffectManager.stop(effectId, instanceId)` checks that the instance belongs to the given effect — a server-supplied instance id cannot stop another instance.
- The mutating `/vfx` subcommands (`play`, `playat`, `stop`, `set`, `key`) require operator rights (gamemaster level 2); `/vfx list` stays open.
- The client log no longer prints the packet position (less noise on spam).
### Fixed
- **Datapack VFX effects and curves now sync with dedicated-server clients.** Previously `VFXDefinitionManager`/`VFXCurveManager` loaded definitions only from `PackType.SERVER_DATA`, so a dedicated-server client held only the built-in `vfxweaver:*` and ignored custom datapack effects (`Ignoring unknown VFX effect`). Added a server→client `vfxweaver:vfx_sync` packet (raw definition/curve JSON) sent to each player on join (`ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS`) and to everyone after `/reload` (`END_DATA_PACK_RELOAD`); the client merges them over the built-ins.
### Added
- Direct world-position passing in `VFXAPI.sendEffect(player, effectId, Vec3 worldPos, ...)` — the client re-anchors spatial bindings (`screen_x/y`, `proximity`) to the point without the `pos_x/y/z` hack.
- `position` and `instanceId` fields in the `vfxweaver:vfx_trigger` packet — stopping a specific effect instance via `sendStop(player, effectId, instanceId)`.
- Custom easing curves: named files `data/<ns>/vfx_curves/<name>.json` (an array of control points `points`) and inline objects `"easing": { "curve": [[t,v],...] }` — anywhere an easing is expected (effect default, keyframe, collection child).
- Multiplicative param modifier: `"strength": { "keyframes": [...], "multiply": { "bind": "proximity", ... } }` — final value = base × multiplier (e.g. an animated dent fading with distance to a point).
- Client `VFXAPI.playEffectId(...)` returns the id of the created instance; `VFXAPI.stopEffect(long instanceId)` stops one specific instance.
### Changed
- **BREAKING**: `PROTOCOL_VERSION` 2 → 4: the packet carries an optional position and instance id; the easing field is now a string name (built-in or custom curve id).
- `/vfx playat` moved to the new packet position field (backward compat with the old `pos_x/y/z` trick kept).
- `VFXTimeline` supports param multipliers alongside bindings (position rebinding reconfigures both).
- `VFXDefinitionManager`/`VFXCurveManager` store raw JSON sources for network sync; parsing moved into `reload()`.
### Fixed
- `VFXDefinitionManager.prepare()` no longer aborts loading all VFX definitions because of one malformed datapack file — `catch` widened to `IllegalArgumentException` (previously an unknown `type` or broken `positions` would hit it).
### Changed
- `getModelQuads()` (`VFXWorldOverlayRenderer`) now logs an error when collecting block geometry instead of silently swallowing it.
### Docs
- The user guide moved to `docs/GUIDE.md`; added `README.md`, `AGENTS.md`, `CONTRIBUTING.md`, `docs/API.md`, `docs/ARCHITECTURE.md`, `docs/CHANGELOG.md`.

## v11
- `block_outline` has two modes by the boolean `shell` (default `0`): `0` — extruded walls, `1` — a classic scaled shell with back faces, clipped by the block via the depth buffer.

## v10
- The outline was reworked from a scaled shell to "walls" (each face is extruded outwards along its normal by `width/2`) — the contour physically cannot cover the block in either mode.
- `block_outline` `through_blocks` default is now `0` (occluded by other blocks), `block_tint` — `1` (see-through).

## v9
- `through_blocks` (0/1) on `block_tint`/`block_outline` — visibility through blocks or with occlusion.
- The outline no longer covers the block itself (the shell is drawn only with back faces and masked by the block's own depth).
- `/vfx playat` correctly sets the position again (command positions take priority over the definition's `positions`).
- `sound` on `collection` now plays (locally, only to targeted players).

## v8
- Added `vignette`, `screen_flash`, `motion_blur`, `fov_modifier`.
- `block_tint` restored.
- `block_outline` rewritten as a scaled model shell with multi-block support (`positions`) and depth test disabled.
- The network protocol gained an `action` field (`PLAY`/`STOP`) and a version (`protocolVersion`).
- Datapacks support `sound` and `positions`.
- Adaptive blur; `distortion` supports negative `amount` values.
- The `/vfx playat` command for quick block-effect testing by coordinates.

## v7
- `block_tint` removed; the outline rewritten onto the custom shader `vfxweaver:core/block_outline`.

## v6
- Tint fixed under Iris (custom pipeline instead of `debug_filled_box`).
- Overlay fault tolerance (try/catch per effect and on flush).
- The `look` binding (yaw/pitch/range) to bind effects to the camera rotation.

## v5
- Model geometry for tint/outline; outline with depth test (outside only); `loop`; guide created.

## v4
- `block_tint`, `block_outline`, `persistent`/`fade_ticks`, `collection`. *(historically: cube tint)*

## v3
- World bindings (`bind`), `dent`.

## v2
- Keyframes, two-pass blur, posterize, command hints.

## v1
- Base shader effects, `camera_shake`, datapacks, commands, network.
