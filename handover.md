# Agalar Hack — AI agent handover

Last updated: 2026-09-11 (inventory scoring / container transfer batch). Read this before implementing more features.

## Goal and current checkpoint

Repository: `MrHakan/Minecraft-Client`. Build a polished, maintainable Minecraft **26.2**
Fabric utility/anarchy client with deep useful modules, predictable restoration, bounded
scanners, strong configuration and an eventual addon API. Module count is not a success metric.

Work continues on **`codex/foundation-services-26.2`**, draft **[PR #9](https://github.com/MrHakan/Minecraft-Client/pull/9)**.
Keep using that branch and that PR; do not open a second PR for the same line of work.
`claude/main-goal-mvph38` is a mirror of the same commits, kept only because the session was told to
push there, and it carries nothing #9 does not.
Last inspected `main`: **`19f83ab888a55d9b459f84fbae27dffe39036f71`** (26.2.5, merged PR #8).
At the 2026-09-11 re-inspection, #9 was still the only open PR, at head `f3eb4ff`. Always inspect current main and all
open PRs again: this document is a checkpoint, not a substitute for live repository state.
**Nothing in this work has been automatically merged into main.**

Approximate progress against the complete requested roadmap: **70–75% implemented;
25–30% remains**. This is a qualitative scope estimate, not measured work hours, a count
of commits, or a release-readiness percentage. Earlier estimate was about 20%; this batch
mainly deepens existing foundations. Do not extrapolate remaining duration from these numbers.
No entire phase is accepted as complete; Minecraft in-game smoke testing remains outstanding.

| Phase | Approximate implementation | Main remaining work |
| --- | --- | --- |
| A: foundation | 90–95% | Rotation adoption beyond Aura, shared selector adoption by visuals, integration tests |
| B: UI/HUD | 62–67% | Full widget/animation/accessibility coverage, Module List transitions, player faces, remaining legacy bounds |
| C: rendering | 75–80% | More ESP render modes, per-block BlockESP colours |
| D: player utility | 55–60% | AutoFish, AutoWalk, AutoAccept, FastPlace, inventory HUD depth |
| E: movement/world | 70–75% | NewChunks |
| F: information/social | 75–80% | BetterChat rendering (timestamps/highlighting) |
| G: ecosystem | 15–20% | Stable external addon API/template, optional Baritone; localization coverage beyond the ClickGUI |
| H: hardening | 60–65% | In-game lifecycle testing, complete profiling/config migration audit |

The pre-existing client was substantial. Many baseline features below existed before PR #9;
do not delete/reimplement them just because their requested expansion is incomplete.

## Non-negotiable development rules

- Minecraft 26.2, Java 25. Current pins: Loader 0.19.3, Fabric API 0.157.0+26.2, Loom 1.17.19.
  `mod_version` remains 26.2.5; no new release/version was invented for this unfinished PR.
- Inspect each existing system before changing it. Reuse working typed settings, profiles,
  target policy, utility arbitration, HUD registry and Minecraft submit-node rendering.
- Fabric callbacks feed the internal event bus. Modules should use services/internal events,
  not register independent permanent Fabric callbacks.
- No placeholders, duplicate mode inflation, named anti-cheat bypass presets, packet flooding,
  malformed packets, crash/dupe exploits or intentionally destructive server features.
- Never claim server-hidden facts. Any TPS/threat/projectile/network inference must be labelled.
- Bound scanning and caches; no full-world discovery in render callbacks. Respect loaded chunks.
- Restore owned state on disable, disconnect, world unload, death, player replacement and dimension change.
  Never restore an old player's state into the replacement player.
- Preserve saved settings and keys or explicitly migrate. Reject future/unreadable files without overwriting.
- Make topic-level commits. Normally accumulate **5–6 meaningful commits**, update the branch once,
  run `./gradlew build --stacktrace` in GitHub Actions, inspect actual logs if it fails, then publish a small fix batch.
  A prior one-commit exception was explicitly requested because the user's quota was nearly exhausted.
- Update PR feature/architecture/compatibility/performance/CI/manual-test notes. Leave it for review; do not merge.
- Do not mark phases complete without reviewing duplication, stale settings, null/world lifetimes,
  allocations/caches, render-thread discovery, ownership conflicts, duplicate binds and config recovery.
- Turkish progress messages suit this user; source/docs/commit descriptions are currently English.
  Keep progress factual and avoid repeatedly asking permission for already authorized work.

## What exists and how it works

### Baseline preserved from main

Typed bounded settings and metadata; categories and keybinds; friends/global target policy;
profiles and server bindings, rename/duplicate/clipboard; HUD positions/editor snapping and
overlap checks; TargetTracker/TargetHUD; Aura, TriggerBot, AutoEat, AutoTool, AutoReconnect;
Speed, Flight, Sprint, Step, Jesus, NoFall, Freecam; EntityESP, StorageESP, BlockESP,
Trajectories, Coordinates, Durability, Fullbright; config recovery, utility arbitration,
module isolation and Java 25 CI/JUnit. Inspect concrete implementations before expanding.

### PR foundation and reliability

- `events/EventBus`: synchronous typed events, ordered listeners, closeable subscriptions,
  copy-on-change dispatch lists, failing-listener isolation and central logging.
- `events/FabricEventBridge`: one bridge for tick/world tick, connect/disconnect, entity and chunk
  load/unload, HUD extraction, submit-node collection and per-screen keyboard/mouse input.
- `services/ServerContextService`: identity-based world/player/alive transitions and observed screen changes.
  `InputStateService` polls bounded physical keys/buttons each tick; gameplay input is **not lossless**.
- `ServiceRegistry`/`ClientServices`: typed lookup; existing manager instances are registered too.
  Modules have `service(Class<T>)`. Public composition-root manager fields remain for compatibility.
- `InventoryService`, `InventoryLeaseController`, `UtilityActionManager`: bounded queries, atomic
  slot/use claims, persistent ownership, priority preemption, physical input preservation and manual-slot backoff.
  AutoEat/AutoTool use these. Main inventory indices are **not container-menu slot IDs**.
- `TargetService`/`TargetSelection`: Aura/TriggerBot share policy, filtering and geometry/priority logic.
  At most 4096 observed entities and 32 retained combat candidates per selection. NPC-like filtering is a heuristic.
- `RotationService`/`RotationMath`: None/Client/Smooth request arbitration, bounded yaw/pitch movement
  and conditional restoration. Aura has optional aiming; default remains None. **No silent mode adapter**.
- `NotificationService`: bounded monotonic-clock queue, types, duplicate suppression, duration/maximum,
  module/profile/friend/reconnect/recovery/error producers. Notifications module and draggable HUD component.
- Flight/Sprint/Freecam/Fullbright restoration and profile settings application were audited/refined;
  default world-scoped cleanup and module/render/scanner error isolation retain client operation.
  These are code changes plus pure tests, **not proof of in-game restoration**.
- `ConfigCodec`: legacy module-map v0 -> `{schemaVersion:1, modules:{...}}`; future/unreadable/oversized
  config protection and backup-before-recovery. Profiles still use their existing module snapshots.
- `BoundedJsonFile`: bounded UTF-8 reads and temporary-file replacement for HUD files; invalid reads
  disable writes until a successful reload. HUD layout remains a bounded legacy map; editor schema is 1.

### PR UI/HUD work

- `KeyChord` and `KeybindCaptureScreen`: keyboard/mouse/modifier capture, readable names, duplicate
  warnings, ESC clears. Legacy numeric bindings survive; mouse codes -100..-107; separate modifier mask.
  A captured key is checked against the **configured** ClickGUI binding, not a hardcoded Right Shift.
- Browser: favorites, recency, enabled/bound filters, metadata/settings fuzzy search, sorting and context actions.
- Reusable toggle, bounded slider/exact number entry, choice modal and RGB/HSV/alpha/hex color editor.
  Numeric RGBA storage stays compatible. Full widget catalogue/rainbow picker is not finished.
- `ThemeService`/`ThemeScreen`: six palettes (Dark, AMOLED, Light, Ocean, Crimson, Purple), separate
  persistence/import/export, opacity/radius/shadows and animation controls. Latest continuation adds guarded theme files,
  paginated editing, high contrast and reduced motion. No font-scale/blur implementation yet.
- `UiSession`/`ClientScreen`: parent ancestry preserves nested edit sessions and reverts abandoned theme previews.
  Choice callbacks run **before** parent rebuilding; theme color opening is deferred to the next tick.
- `ModuleSettingsClipboard`: bounded payload, canonical names, protected-key checks regardless of casing,
  duplicate-alias rejection and validate-before-apply. Active modules release old state before reset/paste.
- `HudRegistry`: dynamic stable IDs. Existing four components plus notifications, FPS/memory/server/speed/
  direction/inventory and the new scanner diagnostics. Editor has multi-selection, group drag, locks,
  z-order, grid/magnet/margins, snapping, guides and overlap warnings. Some legacy bounds are approximate.

### Shared discovery and previous continuation batch

- `ScanScheduler`: weighted NEAR/FOCUSED/BACKGROUND turns, rotating equal-priority order, per-tick
  expiring requests, atomic budget costs and bounded task execution. One failure disables its scanner owner.
- `ScannerService`: per tick caps **12000 block probes / 64 explicit chunk lookups / 4096 entity checks /
  16384 task steps**. Missing chunks are never loaded. Chunk reference cache lives only during scheduler execution.
- BlockESP: tested chunk-local nearest-chunk cursor, mutable probe reuse, skip absent chunks,
  bounded result snapshots and chunk-unload marker cleanup. Still periodically rescans static blocks.
- StorageESP: incremental block-entity iteration across ticks, completed-pass snapshots, per-chunk visit/retry
  cap 4096, unload cleanup. `scanInterval` now delays between completed passes. Undyed shulkers are included.
- **Previous batch, commit topic 1:** `SlotSnapshots` now tracks 36 inventory slots and six equipment slots
  (head/chest/legs/feet/mainhand/offhand). `EquipmentUpdated` is separate from `InventoryUpdated`;
  copied payloads and player-replacement reset are tested. Added defensive read snapshots and full-inventory food lookup.
- **Previous batch, topic 2:** notification render/editor bounds share `NotificationLayout`, fitting row counts
  and widths to the viewport. Animation direction follows the actual HUD anchor. Disabling clears/suppresses
  queued notices; startup recovery messages are preserved when notifications start enabled.
- **Previous batch, topic 3:** EntityESP discovery moved out of render collection into scheduler work at NEAR
  priority. `NearestCandidates` retains nearest observed entities (default 256, cap 512) from at most 4096
  observations; global budget sharing may yield fewer. `labelRange` is separate. Despawn/world/player checks
  prevent stale snapshots and deferred geometry from referencing a replacement world.
- **Previous batch, topic 4:** hidden-by-default `scanner_debug` HUD displays budget counters, cached marker
  counts and measured discovery elapsed time. Enable in HUD editor via Select -> scanner_debug -> Visible.
  It initiates no world scan; it is a developer diagnostic, not a gameplay module or full profiler.
- **Previous batch, topic 5:** this handover plus updates to foundation docs. No empty modules were added.

### Latest continuation: theme safety, accessibility and module-list HUD

This batch adds four implementation topics plus this updated handover. Main and every open PR were
re-inspected first; main remained `19f83ab`, with only draft PR #9 open at head `e9bcd1b`.

1. `ThemeCodec` validates object/field types, integral colors/schema, finite values and a **16 KiB UTF-8**
   limit before import. Existing schema 1 and missing-field defaults remain supported; new accessibility
   booleans default false. Unknown fields are rejected to avoid silently discarding unsupported data.
   `ThemeService` now reuses `BoundedJsonFile`; failed reads block writes, and a successful reload restores
   write access. Tests cover preservation, repair/reload, defaults, type coercions and multibyte limits.
2. Themes expose `highContrast` and `reducedMotion`, with paginated keyboard-accessible controls that
   adapt the number of rows to GUI height. High contrast overrides the shared palette with opaque black,
   white text and yellow/blue accents without destroying chosen colors. `motionEnabled()` combines the
   existing animation switch with reduced motion; notification sliding and HUD rainbow stop when disabled.
   Palette selection keeps accessibility choices; import intentionally replaces the full theme. Not every
   legacy hardcoded HUD/editor color has been migrated, and no UI scale/large text/blur control exists yet.
3. `ModuleList` is a real Render settings module controlling the existing `modules` HUD ID (default enabled,
   hidden from its own list). `ModuleListHud` replaces the old inline renderer. Controls: left/right/anchor
   alignment, width/name/category sorting, normal/upper/lower case, module display/name/category text,
   rainbow/accent/category color, panel background, edge strip, text shadow and maximum rows (1–64,
   default 32; further capped by viewport). Existing modules gain `showInHud=true`; hiding leaves automation
   behavior intact. Display mode uses existing module-provided suffixes, not invented server information.
   `ModuleListModel` tests stable ties, formatting, Turkish-system-locale behavior, bounds and ordering.
   Row animations and new per-module suffix producers remain pending. UI settings participate in existing
   module config/profile snapshots; existing HUD positions and visibility are preserved.
4. `HudRegistry` validates registrations and caps at 256. Renderer/measurement failures suspend only that
   component, log and notify once, without modifying saved visibility. HUD editor shows `[ERROR]` and has
   a **Retry** action. `HudMeasurement` bounds provider sizes to viewport/4096 and rejects negative sizes;
   failures reach the registry boundary. The editor no longer expands small components to 70x30, which
   previously shifted right/bottom anchors. Tiny labels are clipped to their actual rectangle. Dynamic
   Module List/notification bounds use their latest rendered measurement; legacy Info/Target dimensions
   are still approximate. Pure dimension tests do not substitute for runtime failure-injection testing.

No scanners, inventory ownership or world render pipeline were rewritten in this UI/HUD batch.
Overall scope remains roughly **20–25% implemented / 75–80% remaining**. The modest Phase B estimate
increase reflects deeper existing controls, not completion of the whole phase.

## Roadmap coverage by original requirement number

“Implemented” below means the stated code exists, not that every in-game acceptance check passed.
“Partial” explicitly means more of the original requirement remains. Baseline functionality counts as existing.

| # | Requirement | Current status / next work |
| --- | --- | --- |
| 1 | Internal events | Substantially implemented: tick/render/connection/entities/chunks/block entities/equipment/screens/input plus verified block-update and packet-count producers |
| 2 | Service registry | Partial: real shared managers/services; waypoint/addon contracts pending |
| 3 | Target selector | Partial: combat filters/priorities implemented; visuals share EntityDiscovery but not the combat selector |
| 4 | Inventory service | Partial: bounded lookup, copies, equipment events, hotbar/use ownership, armour/weapon scoring and preemptible container transfers; multi-container transfers still out of scope |
| 5 | Rotations | Partial: None/Client/Smooth with per-tick yaw/pitch step limits, which is the *validated* half. The **silent** half is deliberately not implemented: hiding rotation from the player's own view while sending it to the server exists to defeat server-side checks, and the brief rules that out. Wider integration beyond Aura is still open |
| 6 | Notifications | Substantially implemented: queue, producers, HUD, settings and optional sound; more producers may still be added |
| 7 | UI components | Partial: toggle/slider/choice/text/bind/color plus a `ConfirmScreen` modal now used for destructive actions. Panel/card/range/multiselect still pending |
| 8 | Animations | Partial: global controls, notification motion, the target HUD's eased bar and module list row slide/fade. ClickGUI screen/category/scroll transitions are still missing |
| 9 | Module browser | Substantially implemented: filters/favorites/recency/search/sort/actions; visual/manual QA remains |
| 10 | Bind capture | Implemented keyboard/mouse/modifiers/ESC and conflict warnings; in-game duplicate/reserved-key tests remain |
| 11 | Color picker | Implemented: RGB/HSV/alpha/hex/recent/copy-paste plus `RainbowColors`, a per-module cycling flag on all seven overlays with colour sliders. Saturation and brightness come from the module's own colour, and phase offsets spread a trail or a screen of tracers along the cycle |
| 12 | Themes | Partial: six presets, guarded import/export, high contrast/reduced motion; font scale/blur and full accessibility integration missing |
| 13 | Dynamic HUD | Substantially implemented: registry plus branding, module list, info, target, FPS, memory, server, speed, movement stats, waypoint, TPS estimate, ping graph, direction, inventory, packet rate, overworld clock, biome and light level. Remaining catalogue entries are cosmetic rather than missing capability |
| 14 | HUD editor | Implemented: groups/locks/z-order/grid/guides/snapping, plus undo/redo over bounded whole-layout snapshots (`LayoutHistory`) and Ctrl+D to match placement across a selection. Legacy Info/Target bounds still pending |
| 15 | Module List HUD | Implemented: alignment/sorting/style/case/display/row cap/per-module hiding plus slide-and-fade row animations (`RowAnimations`) that never change the measured rectangle. Additional suffix producers are still open |
| 16 | EntityESP | Partial: boxes/tracers/labels/colors/fade; now bounded tick discovery, caps and label distance; expanded modes pending |
| 17 | Nametags | Implemented: single-line opt-in fields, friend marker, bounded discovery; no per-field styling yet |
| 18 | StorageESP | Implemented: per-type toggles and per-type colours were already there; tracers in each container's own colour are new and exempt from culling. Filled boxes remain the only cosmetic gap |
| 19 | BlockESP search | Implemented: category filters, user block ids, eight mergeable presets and per-block colours in three tiers (explicit `id=RRGGBB` override, built-in colour, module sliders) |
| 20 | Waypoints | Implemented: persistence, .waypoint commands, beams, labels, HUD arrow and an opt-in death waypoint with a capped, oldest-first prune that never touches hand-made entries |
| 21 | Breadcrumbs | Implemented: distance-based sampling, bounded store, optional age limit, fade, cleared on dimension change |
| 22 | Generalized tracers | Implemented as its own module with per-group filters, colours and origin; ESP keeps its own tracer toggle |
| 23 | ItemESP | Implemented: bounded discovery, whitelist/blacklist, rarity colouring, count/distance labels |
| 24 | ProjectileESP | Implemented: bounded discovery, boxes and heading lines; TNT optional |
| 25 | Projectile warning | Implemented: closest-approach estimate through the shared simulator, labelled as an estimate, informational only |
| 26 | Freecam expansion | Partial existing camera/body/motion settings plus restoration changes; full requested controls/QA pending |
| 27 | Fullbright modes | Implemented: `nightVision` (brighter, but adds an effect the server never granted) and `gamma` (only moves the brightness slider, inside vanilla's own range, restored on disable and disconnect). The gamma mode is new and has not been used in game |
| 28 | Camera tweaks | Implemented: no hurt shake (third mixin), bobbing, FOV override, steady FOV, all with guarded restoration |
| 29 | Trajectory simulator | Implemented: ProjectilePhysics/ProjectileSimulator extracted and consumed by the renderer and the warning module |
| 30 | Trajectory accuracy | Audited against 26.2 and every constant was already correct. Gravity and drag are now re-checked on every build by reading the game's own bytecode, so a version bump that changes one fails the build. Flight-time markers still pending |
| 31 | TargetHUD | Implemented: three layouts, absorption, ping, friend marker, hurt tint, eased bar and the target player's face (both skin layers). A mob card is byte-identical to before, which a test pins |
| 32 | Combat history | Implemented: bounded recent-target log with engagement counts; deliberately reports no damage figure |
| 33 | AutoArmor | Implemented via scoring plus the container channel; in-game swap/cursor testing outstanding |
| 34 | AutoTotem | Implemented with hysteresis-guarded offhand restore and totem counting; explosion/falling triggers and in-game testing outstanding |
| 35 | AutoRefill | Implemented: threshold refills that prefer the smallest source stack |
| 36 | InventoryCleaner | Implemented whitelist-driven with enchanted/named/hotbar protection |
| 37 | AutoFish | Implemented over `BobberBite`, which infers a bite from the bobber's **visible downward motion** - the nibble counter is server-side and private. Use is pulsed for one tick through the hotbar lease, and a pending action is cancelled when the world stops agreeing with it |
| 38 | AutoWalk | Implemented over a shared input-override helper: adds a key press without ever taking one away, pauses on a screen and stands itself down after sustained collision |
| 39 | AutoRespawn | Implemented with a configurable delay. The death waypoint deliberately lives on the Waypoints module instead, since it is useful whether or not you respawn automatically |
| 40 | AutoAccept | Implemented over `RequestMatcher`, built to be hard to abuse: **every** configured phrase must appear (one phrase is a trap), the requester must be a listed name matched whole-word, the reply must be a command the player typed, a crafted name cannot turn one reply into two, and everything is empty by default |
| 41 | Use tweaks | **Deliberately skipped.** The additive half - hold use for a chosen item category - is what holding the key already does and duplicates AutoEat, which is the meaningless-variant case the brief rules out. The valuable half - release at full bow charge, stop eating at full hunger - requires taking a key away from the player, which this client has an explicit rule against (see `PlayerInputOverrides`); AutoEat's own `fillToFull` already covers the eating case from inside its lease. Revisit only with a tweak that is additive and not already covered |
| 42 | Inventory HUD | Implemented: worn equipment row plus the 27 storage slots, with vanilla's own `itemDecorations` so counts, durability bars and cooldowns match the real screen. The hotbar stays absent because vanilla already draws it |
| 43 | SafeWalk | Implemented by reusing vanilla's sneak-edge check through a client-only mixin |
| 44 | Parkour | Implemented conservatively; stands down while SafeWalk is on |
| 45 | AutoJump | **Deliberately skipped**: Minecraft already has `Options.autoJump()`, so a module would only forward to a vanilla switch — the brief rules that out |
| 46 | Elytra utility | Implemented as ElytraInfo: durability and firework warnings plus glide speed, informational only; auto-equip/replace still pending |
| 47 | Movement stats | Implemented: `MovementStats` measures from position deltas rather than the motion vector (which a collision zeroes), giving horizontal and vertical speed, windowed average and peak, peak fall speed, acceleration and a history buffer. Teleports clear the window instead of entering it |
| 48 | Aura improvements | Implemented: `TargetRotation` holds a chosen target through a configurable switch delay so nearly equal targets stop flipping and discarding attack charge, switches immediately when the target is genuinely lost, and spreads attacks round-robin across N targets advancing on landed hits |
| 49 | TriggerBot improvements | Implemented: weapon requirement by item tag, an optional critical-hit wait using 26.2's own rule (`CriticalHits`), and a deterministic reaction delay (`DwellGate`) so sweeping the crosshair past something does not attack it |
| 50 | AutoWeapon | Implemented on the hotbar lease above AutoTool, using scoring and the damage-family tags |
| 51 | Critical information | Implemented as `CritInfo`: attack charge and crit readiness from the client's own player state, reusing `CriticalHits`. Informational only - it changes nothing, sends nothing and touches no input |
| 52 | Totem tracker | Implemented from observed EntityEvent.PROTECTED_FROM_DEATH; resets on death/timeout, labelled "(seen)" |
| 53 | BaseFinder | Implemented over StorageESP's existing results with single-link clustering; opens no scan of its own and says "likely" |
| 54 | NewChunks | **Deliberately not implemented.** The usual detection infers server-side chunk generation from packet artefacts, which is exactly the server-hidden inference this client refuses elsewhere (TPS, totems, BaseFinder). No honest client-side signal was found that is also version-safe, and none could be verified without running the game. Revisit only with a signal that can be stated truthfully |
| 55 | Light/spawn visualization | Implemented as SpawnESP over light levels only; deliberately does not model biome/mob/cap rules and says so |
| 56 | HoleESP | Implemented: safe vs unsafe by real explosion resistance, bounded shared-cursor scan, block-update invalidation |
| 57 | Portal/gateway finder | Implemented: BlockESP still highlights portal blocks, and `.portal` answers the part highlighting cannot - where to build on the other side so a pair links, with the linking search radius and an optional waypoint placed in the target dimension |
| 58 | BetterChat | Implemented as far as is safe: local timestamps and a mention marker through the `ChatComponent.addMessage` mixin, and repeat hiding through the existing veto (no mixin needed). **True inline word styling is deliberately not done**: recolouring a substring means rebuilding the component tree, and getting it wrong strips the server's own colours and breaks click events on links. A prefix cannot damage what it is prepended to |
| 59 | Chat mentions | Implemented: whole-word own-name/friend/keyword matching with notification and optional sound |
| 60 | Translator architecture | Optional, not started; core operation must not depend on cloud API |
| 61 | Macros | Implemented: key to chat/command/toggle, persistent, revalidated on load; timed sequences deliberately excluded |
| 62 | Command aliases | Implemented: persistent, single-pass expansion, cannot shadow real commands or self-reference |
| 63 | Server info HUD | Implemented: address/dimension, ping history, a labelled tick estimate, and **counted** packets per second in and out via a fourth mixin on `Connection`. The packet figure is the only network number here that is not an estimate |
| 64 | Ping graph | Implemented as a hidden-by-default HUD component over a bounded rolling window |
| 65 | TPS monitor | Implemented as an estimate from world-time update spacing, labelled "(est)" everywhere and capped at 20 |
| 66 | Lag detector | Implemented: tick drop, ping spike against a median baseline, and server silence, all on the shared `LatchingThreshold`. Silence is reported as silence, since a stalled server, a dropped connection and a suspended laptop are indistinguishable from the client |
| 67 | Profile manager 2 | Implemented: optional descriptions and tags (`ProfileMetadata`), `.profile search` across name/description/tags, and per-dimension bindings that take precedence over server bindings. Metadata is an optional field, so no migration was needed |
| 68 | Partial profiles | Implemented: `.profile load <name> [selection]` narrows to named modules, categories, `hud` or `targets`; an unknown token or a selection that would change nothing is refused rather than applied |
| 69 | Profile diff | Implemented: `.profile diff <a> [b]` against another profile or the live config, with numeric-tolerant comparison so a Gson round trip is not reported as a change |
| 70 | Config schemas | Implemented: `SchemaVersions` states the policy once (missing means current, future is refused rather than overwritten, older is returned so a migration can read it) and the alias, macro and waypoint codecs share it. Profiles gained the version marker they lacked |
| 71 | Addon API | Internal groundwork only; no external stable API or JAR loading |
| 72 | Addon metadata | Not started |
| 73 | Addon template repo | Not created; create MrHakan/AgalarHack-Addon-Template only after API stability |
| 74 | Baritone | **Deliberately deferred.** Baritone has no 26.2 build, so any bridge would be unverifiable, and the obvious shortcut - sending `#goto` through `sendChat` - leaks the command to public chat whenever Baritone is absent or its prefix is off. Revisit when a 26.2 Baritone exists and its API can actually be called |
| 75 | Localization | Partial: `Translations` with English fallback, `en_us`/`tr_tr`, ClickGUI labels and **all 52 module descriptions** translated, guarded by three key tests. Module **names** stay untranslated deliberately - they are the identifiers commands and configs use. Setting descriptions and command output remain English |
| 76 | Accessibility | Partial: keyboard controls, paginated themes, high contrast/reduced motion, two colourblind presets, and a WCAG contrast guard that raises text a picked or imported theme made unreadable. UI scale, text size and blur controls still pending |
| 77 | Unified scheduler | Implemented: all nine scanning modules go through it, and the shared per-tick ceiling is now tunable through `ScanBudgets` and the Performance module. Balanced reproduces the previous constants exactly |
| 78 | Chunk result cache | Implemented for BlockESP: bounded LRU clean-chunk cache with block-update, unload, anchor and filter invalidation. Other scanners still sweep |
| 79 | Render culling | Implemented: box overlays test the frustum the game already built, in world space. Tracers and breadcrumbs are deliberately exempt and a test enforces that. Safe because it is view-volume, not occlusion, culling - the overlays draw through walls on purpose |
| 80 | Performance HUD | Implemented: `ModuleTimings` ranks per-module tick cost over a rolling window, in a hidden-by-default widget. Measurement is self-expiring rather than a setting, and a module that stops ticking is dropped rather than frozen on screen |
| 81 | Unit tests | 631 tests; adds block-update batching, chunk cache eviction, cursor completion, id-list parsing, notification sinks, waypoint normalisation/persistence and compass bearings; trajectory and fade coverage pending |
| 82 | Integration smoke tests | Partial: the client boots headless under xvfb/llvmpipe and all **nine** mixin injections across five target classes are verified applied in the transformed bytecode. **Check the run succeeded, not just the export** - transformation happens at class load, so a client that crashes later still writes a complete `.mixin.out`, which hid a startup crash here for several commits. No gameplay was exercised |
| 83 | Lifecycle audit | Partial code audit/restoration; all listed state-changing modules need in-game transition checks |
| 84 | Error reporting | Reviewed. Module tick, render, scanner, command, macro and HUD boundaries were already guarded. The one real gap was the shared chat callback: the bus detaches a listener that throws, so one chat module's bug disabled all three for the session. `ModuleGuard` now contains each module separately and reports once per failure episode, and ChatFilter fails open so a broken filter cannot hide chat |
| 85 | Structured logging | Implemented SLF4J replacement for raw stderr. One `System.out.println` survived in the profile auto-load path and has now been replaced; the rest of the audit remains |
| 86 | Module documentation | Implemented: `docs/MODULES.md` is generated from the live settings registry and a test fails when it and the code disagree, rewriting the file as it fails |
| 87 | Experimental flags | Implemented: `markExperimental()` plus an UNTESTED badge in the ClickGUI, applied to all 31 modules added here and surfaced in the generated module reference; a source-level test stops a new module shipping unmarked |

## Recommended next development batch

1. Verify a **real 26.2 block-update producer** against current sources. Do not invent an event without a producer
   or paste an old-version mixin. Preserve Fabric hooks; add a narrowly scoped mixin only if necessary and verified.
2. Use block/chunk lifecycle signals for a bounded chunk-result cache, with world/config invalidation and budgeted rebuilds.
3. Continue Phase D on the now-available inventory foundation: AutoRefill, InventoryCleaner (conservative
   whitelist defaults), AutoRespawn and AutoFish. They should consume `ContainerTransferController` and the
   hotbar lease rather than adding new slot handling.
4. Complete notification sound and menu rendering/lifecycle behavior, then close remaining Phase A integration gaps.
5. Continue Phase B quality work (exact HUD measurements, Module List transitions/TargetHUD, accessibility) before aggressively
   adding the Phase C–F module catalogue. Addon template and Baritone belong after stable internal contracts.

Choose 5–6 meaningful topic commits, not arbitrary tiny edits just to reach a count. If the user changes scope,
follow their latest instruction. Keep this file updated whenever a system's status materially changes.

## Latest continuation: inventory scoring, container transfers and Phase D automation

Six topic commits. `ItemScoring` plus `InventoryService` adapters; `InventoryTransfers` and
`ContainerTransferController`; `AutoArmor` with a new `PLAYER` category and a bounded ClickGUI sidebar;
`AutoTotem` with hysteresis-guarded offhand restore; `AutoWeapon` with `AutoEat` regrouped; and a review
fix making container priority actually preempt instead of depending on registration order.

**26.2 renamed the inventory click call**: it is
`MultiPlayerGameMode.handleContainerInput(containerId, slotId, button, ContainerInput, player)`.
`handleInventoryMouseClick`/`ClickType` no longer exist.

## Latest continuation: block updates, chunk caching and more Phase D

Six further topic commits.

1. **Block-update producer.** Fabric API 26.2 has no client block-update event, so
   `ClientPacketListenerMixin` injects at TAIL on `handleBlockUpdate` and `handleChunkBlocksUpdate`.
   This is the mod's **first mixin**; `agalarhack.mixins.json` is client-only and its
   `compatibilityLevel` must stay **JAVA_25**, because the mod compiles to class version 69 and a lower
   level is rejected at load time — something a passing build does not catch. Section packets report
   individually up to `BlockUpdateBatch` cap 512, then post one `ChunkBlocksInvalidated` instead.
2. **Chunk-result cache.** `ChunkScanCache` is a bounded LRU of fully scanned, untouched chunks;
   BlockESP skips them entirely and applies single block changes to its markers directly. Cleanliness is
   recorded only on genuine chunk exhaustion, and never while the result set is at its cap.
3. **AutoRefill, AutoRespawn, InventoryCleaner.** AutoRespawn must set `runsWithoutWorld()` because the
   module manager stops ticking once the player is not alive. InventoryCleaner is whitelist-driven and
   protects enchanted/named items and the hotbar by default; `ItemIdList` bounds and validates the
   user-entered list.
4. **Notification sound.** `NotificationService` gained a sink so it stays free of Minecraft types;
   `NotificationSounds` holds the only Minecraft call. `SoundEvents.NOTE_BLOCK_PLING` is a `Holder` in
   26.2 and must be unwrapped with `.value()` for the volume overload.
5. **StorageESP live tracking** through Fabric's `ClientBlockEntityEvents`; a completed pass stays
   authoritative so missed events self-heal.
6. This handover and the foundation document.

Not done here: no rotation adoption beyond Aura, visuals still do not consume the shared combat target
selector, AutoFish/AutoWalk/AutoAccept/FastPlace remain, and **nothing has been run inside Minecraft**.
The mixin in particular is verified only by signature and by a passing build — its injection has never
been observed to apply at runtime.

## Latest continuation: waypoints and visual modules

Five topic commits.

1. **Waypoints.** `Waypoint` normalises in its constructor so a stored waypoint is always renderable;
   identity is name-within-dimension, case-insensitively. `WaypointCodec` is strict about the envelope
   and forgiving about entries (one bad waypoint is skipped, a future schema version is refused so the
   file is preserved). `WaypointService` uses `BoundedJsonFile` with the usual preserve-on-failure rule
   and **its own SLF4J logger**: `AgalarHackClient.LOGGER` drags in a static initialiser that needs a
   Fabric runtime, which made the failure paths untestable. `.waypoint add|remove|list|clear|beam|
   show|hide|color`; rendering reuses the existing line geometry and label path.
   **26.2: a dimension id is `ResourceKey.identifier()`, not `location()`.**
2. **ItemESP** with a whitelist/blacklist over `ItemIdList` and vanilla rarity colours.
   **26.2 does not expose `ChatFormatting.getColor()`** — use `TextColor.fromLegacyFormat(...).getValue()`.
3. **Waypoint HUD arrow** backed by `WaypointCompass`. Yaw convention is pinned by tests: yaw 0 faces
   +Z, yaw increases turning left, a target to the player's right has a positive bearing. Registers hidden.
4. **Nametags**, plus `EntityDiscovery` extracted first so the tick-scheduled nearest-first pattern is
   not copied a third time; EntityESP and ItemESP were refactored onto it.
5. This handover and the foundation document.

## Latest continuation: breadcrumbs, tracers and user block sets

Three more topic commits on top of the waypoint batch.

1. **Breadcrumbs.** `BreadcrumbTrail` samples by distance, not by tick, so standing still records
   nothing. Bounded store with optional age expiry; cleared on disable, disconnect and dimension
   change, and whenever the level instance changes underneath it.
2. **Tracers** as its own module rather than widening ESP's filter, since tracers and boxes are
   usually wanted for different sets. Uses `EntityDiscovery`, so no new sweep.
3. **BlockESP user block ids** through `ItemIdList`, plus eight presets that merge into the editable
   list and then reset the selector. The custom list is part of the scan signature, so editing it
   invalidates the chunk cache.

## Latest continuation: projectile simulator, projectile modules and SafeWalk

Three more topic commits.

1. **`ProjectilePhysics`/`ProjectileSimulator`** extracted from the trajectory renderer. Collision stays
   in the renderer because it needs the world; keeping it out is what makes the physics testable.
   **The drag/gravity order differs by family** — arrow-like moves then decays, throwable-like decays
   then moves — and a test asserts the two orders diverge. Constants are carried over unchanged, so
   this was a refactor, not a retune.
2. **ProjectileESP** (heading lines deliberately ignore gravity: a direction, not a predicted path) and
   **ProjectileWarning**, which extrapolates an observed velocity with an *estimated* motion model and
   says so in the text. It reuses ProjectileESP's discovery rather than opening a second sweep.
3. **SafeWalk** through a second mixin, `PlayerEdgeMixin`. It forces vanilla's own
   `isStayingOnGroundSurface` gate rather than reimplementing edge detection. `Player` is common code,
   so the injection is restricted to the client's own player.

There are now **two mixin classes** listed in `agalarhack.mixins.json` under `client`.
There are now **three mixin classes**. `ClientPacketListenerMixin` carries four TAIL injections
(block update, section blocks update, entity event, set time) through `PacketHooks`;
`PlayerEdgeMixin` backs SafeWalk; `GameRendererMixin` cancels the private `bobHurt` for CameraTweaks.
Each injected method is a single predicate or hook call, with the decision left in module code. Entity events feed **TotemTracker** (`EntityEvent.PROTECTED_FROM_DEATH`), which counts
only activations the client observed and says "(seen)" rather than implying a server-side tally.

## Latest continuation: server information and command aliases

Two more topic commits.

1. **ServerInfo**, `ServerTickEstimate` and `RollingSamples`. The tick figure is an **estimate** from
   the spacing of world-time updates and is labelled as one in the module suffix, the HUD line and the
   warning. Implausible intervals (non-positive, multi-minute, or implying above 20 tps) are discarded
   rather than reported as lag, and samples reset on world change. Ping graph and TPS HUD both register
   hidden. This added the fourth packet injection, on `handleSetTime`.
2. **Command aliases**, persistent and single-pass. An alias cannot loop, cannot name itself, and
   cannot shadow a real command; loading re-validates hand-edited entries through the same rules.

## Latest continuation: TargetHUD depth

`TargetHudModel` holds the layout rules and the bar easing, free of Minecraft types, so the renderer
is placement only. Minimal/Compact/Detailed; absorption counts toward the same bar rather than
overflowing it and is called out in the text; ping is omitted when unknown instead of shown as -1;
the eased bar resets on target change so it never slides between two players, and snaps once the gap
is invisible. **Reduced motion and the theme animation switch both override the module's own
animation toggle** — accessibility wins over a per-module preference.

## Latest continuation: chat observation

`ChatMatcher` holds whole-word, case-insensitive, regex-free matching, bounded in pattern count and
length. **ChatMentions** notifies on own-name, friend or keyword; own messages are not mentions.
**ChatFilter** hides lines locally only, with an empty default list so enabling it hides nothing.

**Fabric 26.2 has `ALLOW_CHAT`/`CHAT` but no `MODIFY_CHAT`**, so chat timestamps, inline highlighting
and duplicate compaction would need a mixin into chat rendering. That was left undone rather than
done fragilely; the internal `ChatReceived` event is vetoable but not rewritable, matching what the
API actually supports.

Also fixed: local Gradle runs write to `logs/`, which was missing from `.gitignore`, so `git add -A`
had swept eight log files into the branch. They are untracked now and `logs/` is ignored; the pushed
history was left alone rather than rewritten over 40 KB of noise. **Prefer explicit paths over
`git add -A` in this repo.**

## Latest continuation: holes and camera

**HoleESP** classifies by the block's own `getExplosionResistance()` rather than a hardcoded list, so
modded blast-resistant blocks work without maintenance. `HoleDetector` holds the rules free of
Minecraft types. Each candidate costs five probes and reserves five, so the scheduler is not
under-charged; a nearby block update invalidates the anchor.

**CameraTweaks** borrows `bobView`, `fov` and `fovEffectScale` and restores each **only when the
current value is still the one it applied** — a manual change by the player wins. It ticks without a
world because those options are client-wide. No hurt shake needed the third mixin, since vanilla has
no option and the shake lives in a private renderer method.

## Latest continuation: movement, elytra and combat history

**Parkour** stands down entirely while SafeWalk is on — one module holding you at an edge and
another jumping off it would make both useless. **ElytraInfo** warns and counts but never touches
movement; warnings fire once per episode and re-arm only after a real recovery.
**CombatHistory** records what this client observed and **deliberately reports no damage figure**:
the client is not told damage dealt, and deriving it from health differences would be quietly wrong
whenever a server heals, absorbs or cancels a hit.

**AutoJump was skipped on purpose** — see requirement 45 above. Do not add it later without checking
that reasoning.

## Mixin targets are now verified in CI

`MixinTargetsTest` checks all six injection targets against the real 26.2 classes, loading them with
initialisation disabled so no Minecraft bootstrap is needed. This matters because `required: true`
with `defaultRequire: 1` turns a missing target into a **failure to start**, and a normal build
cannot catch it — the target is named in an annotation string, so the mixin compiles either way.
The guard was verified by deliberately breaking a target name and confirming the test went red.

**When you add or change a mixin, add its target to that list.** A test asserts one target owner per
mixin class so a new mixin cannot slip past unnoticed.

It proves the targets exist. It does **not** prove the injection points inside those methods resolve,
or that anything works in game — that is still the first manual check.

## Mixins verified applied at runtime

`MixinTargetsTest` only proves the target methods exist in the jar; it cannot prove Mixin applies the
injections. That second check was done by running the client headless with `-Dmixin.debug.export=true`
and reading the transformed bytecode back with `javap`. All three target classes were transformed and
all six injections were present **and invoked from the correct target method** on real 26.2, with zero
mixin failures and `Compatibility level set to JAVA_25` accepted.

`ClientPacketListener` is loaded during startup rather than on connect, so its four handlers were
verified as applied without a server — but they were never observed firing. That still needs a
connection.

This changes nothing about the `UNTESTED` badge: it tracks in-game testing, not bytecode presence.
Nobody walked off a ledge or took damage during this run.

Exact procedure, environment variables and the result table: [docs/RUNTIME_MIXIN_VERIFICATION.md](docs/RUNTIME_MIXIN_VERIFICATION.md).

## Lifecycle audit result

Requirement 83 was carried out by hand and found nothing wrong. Recorded so it is not redone blindly:
Flight and Step capture and restore with player-replacement handling; Freecam mutates only the camera
entity it creates, never the real player; Speed, Jesus and Parkour touch per-tick velocity and input,
which physics recomputes, so they need no restoration; every subscriber closes and every inventory
owner releases. `ModuleLifecycleTest` now enforces those three properties plus a non-vacuity check.
Source-level and coarse: it cannot prove release on every path. **In-game transition testing is still
outstanding.**

## Untested badge

Every module added in this work is marked with `markExperimental()` and shows **UNTESTED** in the
ClickGUI. The criterion: inventory manipulation, a mixin dependency, or simply never having been run
in Minecraft. **Compiling and passing unit tests does not clear it.** Clear a flag only after
actually using that module in game, and remove the module's name from the exempt list in
`ExperimentalFlagTest` only if it genuinely predates this work.

**Localisation uses a fallback at every call site.** `Translations.text(key, english)` renders the
English text when a key is missing, so partial coverage never shows raw keys. **Do not translate the
ClickGUI filter/sort lists**: the chosen string is also the stored value the switches compare
against, so translating it silently breaks filtering. That needs a display/value split in
`ChoiceScreen` first — there is a comment at the call site saying so.

**Two modules deliberately consume another module's results instead of opening a second sweep**:
ProjectileWarning reads ProjectileESP, BaseFinder reads StorageESP. Both state the dependency in
their description and show it in the module list when the source module is off, so neither looks
broken. Keep that pattern rather than adding parallel scanners.

**Macros hold one action each, on purpose.** Timed multi-action sequences were left out: a macro
firing several actions over time is indistinguishable from unattended automation, and the brief only
asks for local sequences, which aliases already cover. Dispatch reuses the module keybind edge
detection rather than adding a second input path.

**SpawnESP is light-only by design.** Block light zero, with sky light deciding night-only versus
always. Biome rules, mob-specific placement, spawn caps and difficulty are **not** modelled, and the
module says "light permits" rather than "mobs will spawn" in its description, its documentation and
its colour split. If someone later widens it, the claim in the UI has to widen with it — overclaiming
here is the same failure as reporting a server's TPS as fact.

Still missing in Phase B: full widget/animation coverage, Module List row transitions, player faces
on the target card, UI scale and blur controls, and the remaining legacy Info bounds.
Still missing in Phase C: camera tweaks, per-block BlockESP colours and richer ESP render modes.
Still missing in Phase F: BetterChat, chat mentions, macros and combat history.

## Latest continuation: colours, deaths, walking, profiles and generated docs

Six topic commits after the runtime mixin verification.

**Per-block BlockESP colours.** `BlockColorRules` resolves in three tiers: an explicit `id=RRGGBB`
override, a built-in colour for blocks worth telling apart, then the module's existing sliders.
Turning `perBlockColors` off restores the previous single-colour behaviour exactly. Three-digit hex
shorthand is rejected on purpose, since `f00` is far more likely to be a truncated paste. The parsed
map is cached on the module, so this costs nothing per block per frame.

**Death waypoints.** On the Waypoints module rather than AutoRespawn, because it is useful whether or
not you respawn automatically. Names carry the coordinates, so dying twice in one spot replaces an
entry rather than accumulating one, and dying somewhere new never overwrites the marker you are
walking towards. Recognition is name-based rather than a stored flag, which avoided a config
migration and means renaming a death waypoint protects it from pruning. Pruning is oldest-first
across all dimensions and never touches hand-made waypoints. `WaypointService.apply` writes the file
once per death instead of once per add/remove.

**AutoWalk plus `PlayerInputOverrides`.** `Input` is an all-or-nothing record, so holding one key
means rebuilding it and copying six booleans back. Parkour was the only caller; a second one made
that a helper. It is also what makes the record safe to share: each edit preserves the other fields,
so tick order between AutoWalk and Parkour does not matter, and a test asserts both orders agree.
Nothing here ever releases a key the player is holding.

**Profile diff and partial loads.** `ProfileDiff` compares numerically across types, because
snapshots go through Gson (everything becomes `Double`) while live settings hold whatever the module
assigned. `ProfileSelection` does not know module or category names, so it matches tokens against
both and reports the unrecognised ones - a partial load that silently does nothing looks exactly like
one that worked. `applyPartialSettings` deliberately does not route through `applyValues`, which
resets every module to defaults first; a partial load makes the opposite promise. A partial load also
does not claim the profile as active, since afterwards the live config matches no stored profile.

**Generated module reference.** `docs/MODULES.md` comes from the live settings registry, guarded by a
test that rewrites the file as it fails. The generator lives in the test source set because nothing
in the client reads it.

Useful finding: **constructing a Module in a test works.** `ExperimentalFlagTest` assumes otherwise
and reads source text; `Minecraft.getInstance()` returns null and no module touches it during
construction, so future guards can use the real settings registry instead of grepping sources.

## Latest continuation: performance, lag signals and culling

Five topic commits.

**Module timings.** Per-module tick cost over a forty-tick window, ranked. Measurement is
self-expiring - a consumer asks each time it wants figures and recording stops three seconds later -
so the `nanoTime` pairs stay off the normal path without a setting anyone can leave on. A module that
did not tick last tick is dropped entirely, because leaving its last average on screen would name an
innocent module as the expensive one.

**Movement stats.** Measured from position deltas, not `getDeltaMovement`: the motion vector is what
the client intends, and a collision zeroes it the moment you scrape a wall, so the old readout
flickered exactly when someone was watching it. Teleports (over twenty blocks in a tick) clear the
window rather than entering it.

**Lag signals on a shared latch.** `LatchingThreshold` is the hysteresis ServerInfo had hand-rolled,
written once and reused by three detectors. The release margin is the point: without it a value
drifting either side of the line by a rounding error produces a wall of warnings. Ping spikes compare
against the *median* of previous samples, and compare before the sample joins them, so a spike cannot
raise its own threshold. Silence is reported as silence, never as a frozen server.

**Fullbright gamma mode.** Raises the brightness slider inside vanilla's own range instead of granting
an effect the server never gave. Dimmer, but it adds nothing to the player. Restores on disable and on
disconnect, and only undoes a change still recognisable as ours. Untested in game - it is on the
manual checklist rather than badged, because the module itself is not new.

**Frustum culling.** Box overlays use the frustum the game already prepared, tested in world space.
Safe because it is view-volume rather than occlusion culling: the overlays draw through walls on
purpose, and anything outside the view volume was never visible. Tracers and breadcrumbs are exempt -
their far ends are meant to be off screen - and a source-level test enforces that, verified by adding
the call and watching it go red.

## Latest continuation: combat depth, scan budgets and rainbows

Four topic commits.

**Aura target holding.** `TargetRotation`. Priority ordering alone flips between two nearly equal
targets as they circle, and every flip discards the attack charge that was building. A switch delay
makes a better target prove itself first; losing a target is deliberately exempt, since there is
nothing left to hold. Multi-target spreads attacks round-robin, advancing on landed hits rather than
on ticks so the rotation cannot run ahead of the attack timing.

**TriggerBot gates.** `CriticalHits` mirrors `Player.canCriticalAttack` in **26.2**, read from the
deobfuscated jar — worth knowing that 26.2 has **no blindness term** and `fallDistance` is a
`double`, so an older version's crit check would wait for a crit that never comes. Everything it
reads is public, so no mixin. `DwellGate` requires the crosshair to rest on a target first; the count
is deterministic rather than randomised, because randomising it would only serve to imitate human
jitter, which is a different goal.

**Configurable scan budgets.** `ScanBudgets` with low/balanced/high, owned by a new `Performance`
module in the shape Notifications already set. New budgets apply from the next tick, never mid-tick.
The scanner diagnostics HUD now prints the live ceiling instead of the old constants. Correcting a
stale note: **all nine scanning modules already went through the scheduler**, so only the budget half
of requirement 77 was outstanding.

**Rainbow colours.** `RainbowColors` on the seven overlays with colour sliders. A flag rather than
something packed into the colour integer, so turning it off restores exactly the previous colour.
Saturation and brightness come from the module's own colour; grey and black are the exception since
they have no hue to preserve. Phase offsets spread a breadcrumb trail and a screen of tracers along
the cycle, which is the difference between a gradient and a flashing line.

## Latest continuation: fishing, faces, inventory and profile metadata

Five topic commits.

**AutoFish.** `BobberBite` infers a bite from the bobber's **visible downward motion**, because
`FishingHook`'s nibble counter and state machine are server-side and private and the client's copy of
the entity carries neither. A bobber still falling through the air is excluded: gravity is not a fish.
One plunge lasts several ticks, so a minimum gap stops a single bite being recognised repeatedly.
Use is pulsed for one tick through the existing hotbar lease; at most one action is pending, and the
world disagreeing cancels it — a hook appearing cancels a pending cast, a hook vanishing cancels a
pending reel. The first draft of that state machine had two real bugs (a reel scheduled and never
pulsed, and two casts for one bite on a slow server); both are why the cancellation exists.

**TargetHUD faces.** Both skin layers, since skipping the hat leaves a bald head for most skins. Mob
cards keep their exact previous layout — the indent is zero, pinned by a test. The `blit` parameter
order is taken from vanilla's own call and has **not been seen on screen**; it is on the checklist.

**Inventory widget.** Worn equipment row plus storage, using vanilla's `itemDecorations` so
durability bars and counts match the real screen rather than being approximated. It also now checks
for a player before dereferencing one.

**Profile metadata, search and dimension bindings.** Metadata is an optional field, so no migration:
old profiles load without it, new ones load in a client that does not know about it. Dimension
bindings are checked after server bindings so the more specific one wins. Two fixes found on the way:
a `System.out.println` in the auto-load path, and an auto-load failure that could propagate out of
`tick()` and break joining a world.

## Latest continuation: crits, portals, requests and contrast

Five topic commits.

**CritInfo** (51). Attack charge and crit readiness from the client's own player state, reusing
`CriticalHits` rather than restating the rule — one place that has to stay correct against the game,
not two. Informational only: no input, no packets.

**`.portal`** (57). BlockESP already highlighted portal blocks; this answers the question
highlighting cannot. Division rounds toward zero because that is what the game does, and being one
block off near an axis is worse than saying nothing. The end returns nothing rather than a 1:1 ratio,
because end portals do not pair by coordinate and a ratio would imply they do.

**AutoAccept** (40). Built to be hard to abuse rather than convenient, because an automatic reply is
a decision taken from text an arbitrary player can influence. Every phrase must match, not any;
friends only by default; replies must be commands; a newline in a crafted name cannot turn one
command into two; everything empty by default.

**Contrast guard** (76). `ContrastRules` with the WCAG formula. Body text to AA, accent and status
colours to the large-text ratio so themes keep their identity. Default on, including for older theme
files, because the only themes it changes are ones that were already too faint.

**Use tweaks (41) deliberately skipped**, with the reasoning recorded in the roadmap table: the
additive half duplicates AutoEat and the valuable half requires taking a key away from the player,
which `PlayerInputOverrides` establishes as something this client does not do.

## Latest continuation: HUD history, error boundaries and Turkish descriptions

Three topic commits.

**HUD undo/redo** (14). Whole-layout snapshots rather than per-widget deltas — a drag moves one
widget, a snap moves it and changes its anchor, and expressing that as reversible operations is a lot
of machinery for a map of small records. Deep copies, because widget states are mutable and live
references would make every snapshot equal the current state. One drag is one step, recorded *before*
the first pixel moves. Ctrl+D copies placement across a selection rather than cloning a widget: HUD
widgets are a fixed registry, so a duplicated id would be a widget nothing knows how to draw.

**Error boundary review** (84). Module tick, render, scanner, command, macro and HUD boundaries were
already guarded. The one real gap: the event bus detaches a listener that throws, which is right for
a single-purpose listener and wrong for the chat callback, where three modules share one listener —
a bug in any of them silently disabled chat handling for all three for the session. `ModuleGuard`
applies the tick loop's policy (contain, report once, disable that module) to shared callbacks.
ChatFilter additionally fails **open**: a filter that throws must not hide chat. `RenderService`
keeps its own guard deliberately, because it defers the disable to the client thread.

**Turkish module descriptions** (75). All 52, keyed by module name. Module **names** stay English on
purpose — they are identifiers used by `.toggle`, the keybind file and every profile, so translating
the displayed name would either break those or make the thing on screen differ from the thing you
type. Same trap as the ClickGUI filter list, same answer. The generated docs read a new
`rawDescription()`, or regenerating on a Turkish client would produce a half-translated page that
fails its own guard.

## Latest continuation: schema policy, confirmations and the trajectory audit

Three topic commits.

**`SchemaVersions`** (70). Three codecs had grown their own version check and had already drifted:
two used `getAsInt`, which truncates `1.9` to version 1 and accepts values outside the int range,
while the third used `intValueExact` and rejected both. Now one reading, with the policy written
where it can be argued with. **Profiles** were the only store with no version marker at all — a
profile from a later client would have been loaded as current and re-saved in today's shape.

**Confirmations** (7). `ConfirmScreen`, used for deleting a profile; `.waypoint clear all` requires
the word `confirm`. Cancel is the default in all three senses (focused, Escape, left-hand button) and
the destructive button names what it destroys, because "Are you sure?" trains people to click yes.
Clearing one dimension's waypoints stays unconfirmed deliberately — asking every time trains the same
reflex.

**Trajectory audit** (30). Every constant checked against 26.2 and every one was already right.
Gravity and drag are now re-checked on every build: `ProjectilePhysicsAuditTest` reads them out of the
game's bytecode with ASM (already on the test classpath), verified by changing one and watching it go
red. Launch speeds are checked by reading rather than by test, because they sit among other constants
in long methods and automatic extraction would be guesswork dressed as verification.

**Worth knowing for the next version bump:** 26.2 moved the projectile classes into subpackages —
`projectile.arrow` and `projectile.throwableitemprojectile` — so looking them up by their old paths
finds nothing and invites the assumption they were removed.

## Latest continuation: counted packet rates and a fourth mixin

**`PacketRates`** (63). Fabric 26.2 reports nothing about packet volume, so this needs a fourth mixin,
on `Connection`: HEAD injections on `channelRead0` and the **three-argument** `send`, which is where
both other `send` overloads end up in 26.2 — counting on the one-argument version would miss
everything routed through the others, and counting on two would double it.

Both injections run on the **netty thread**, which shapes the whole service: `LongAdder` counts,
sampling as a separate client-thread step, and no reach back into the service registry. Anything that
touched client state from there would be a race. `channelRead0` runs for every packet received, so the
cost while nobody is watching is two volatile reads — counting is self-expiring like module timing.

Rates divide by real elapsed time rather than assuming a second has passed, so a stalled client tick
reports what arrived instead of a figure several times too high.

**Verified applied at runtime**, by the procedure in `docs/RUNTIME_MIXIN_VERIFICATION.md`: four target
classes transformed, eight injections invoked from the correct methods, zero mixin failures. Like
`ClientPacketListener`, `Connection` is loaded during startup rather than on connect, so its handlers
are verified applied but have not been observed *firing*.

`MixinTargetsTest`'s coverage guard now reads the mixin config and each mixin's own `@Mixin`
annotation rather than a hand-kept count of three — which is exactly the number that would have gone
stale here.

## Latest continuation: clock, biome and light widgets

`WorldClock` plus three HUD components (13). Light level is the one you want while spawn-proofing and
vanilla offers no way to see it outside F3. The clock is the **overworld's** even in another
dimension, because the local sky in the nether says nothing while "is it night back home" is what
decides when to walk through the portal.

**26.2 has no `getDayTime()`** — it is `getOverworldClockTime()` and `getDefaultClockTime()`. That is
easy to misread as the method having been removed rather than renamed.

The conversion is its own tested class because **tick 0 is 06:00, not midnight**: every naive
conversion is six hours out, which is exactly the gap between "safe" and "everything is about to
spawn". Light is shown as block and sky separately, since block light is the half a torch changes and
showing it alone would let an open-air reading pass for a lit room.

Requirement 5's remaining half is recorded as **deliberately not implemented**: a *silent* rotation
adapter hides rotation from the player's own view while sending it to the server, which exists to
defeat server-side checks. The *validated* half — per-tick yaw and pitch step limits — is already
there.

## Latest continuation: storage tracers and module list motion

**Storage tracers** (18). Per-type toggles and colours were already implemented — only tracers were
missing, which is the half that matters when you are *looking for* a stash rather than looking at
one. Each line takes its container's colour, and they are exempt from frustum culling for the same
reason the other tracers are; the guard test now names them.

**Module list row animations** (15, part of 8). The constraint that shaped this: `ModuleListHud`
promises the HUD editor the same rectangle it draws in, so the **measured rectangle does not
animate** — a box changing size mid-animation would make the editor anchor jitter, which is worse
than the jump being fixed. The box stays the settled size and only rows move inside it.

A first appearance starts at its own position rather than sliding down from where the list used to
end, because that reads as the wrong row moving. Reordering moves rows rather than fading them out
and back, so changing the sort does not look like everything was re-enabled. Reduced motion and the
theme switch both make appearance and removal instant.

## Latest continuation: chat rewriting, and a startup crash I caused

**Chat timestamps and repeat hiding** (58). The deferral reason was that Fabric 26.2 can veto or
observe a chat line but not rewrite one, and doing it fragilely was worse than not doing it. What
changed is that this branch now verifies mixins at runtime, so "fragile" no longer applies.
Timestamps use a `ModifyVariable` on `ChatComponent.addMessage`'s `Component` parameter — the private
choke point all three public `add*` methods delegate to, and a change that depends on the signature
rather than on anything inside the method. Repeat hiding needed **no mixin at all**: vetoing the
duplicate through the existing `ALLOW_CHAT` path stops the spam without rewriting chat history.

**A startup crash, mine.** `Hud.register` copied whatever `HUD_LAYOUT.get(id)` returned, and that was
null for an id with no declared default — during client initialisation, before any screen exists to
report it. **The client had not started since the movement stats widget landed.** `get()` is total
now; `defaultFor` already existed and already handled unknown ids, it simply was not being used.

The reason it went unnoticed for several commits is worth more than the fix: **a complete
`.mixin.out` does not mean the client started.** Transformation happens at class load, long before
most startup work, so the export is written in full even by a run that crashes later. I checked the
exports and the mixin failure count and never checked that the run itself succeeded. The procedure
doc now says to grep for `Game crashed` and for texture stitching, and notes that `ChatComponent`
only loads once the GUI is built — so its presence in the export doubles as a health signal.

## Validation and source references

Canonical command: **`./gradlew build --stacktrace` with JDK 25**.
**Local builds work in this environment**, contrary to the earlier note below: Gradle resolves through the
agent proxy, and a JDK 25 tarball from Adoptium passed via `-Dorg.gradle.java.home` satisfies
`options.release = 25` (the system JDK is 21 and cannot). `./gradlew genSources` also succeeds, and
`javap -constants -cp ~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-deobf-26.2.jar`
is the fastest way to confirm a 26.2 signature before coding against it — that is how the
`handleContainerInput` rename was caught. Local success is still not a substitute for the branch-head CI run.
Workflow: `.github/workflows/build.yml` (`CI`), PR events on main. Latest branch-head CI and manual checklist
are maintained in [PR #9](https://github.com/MrHakan/Minecraft-Client/pull/9); inspect the exact head SHA/run,
not a previous green run. Historical baseline before this UI/HUD continuation: head `e9bcd1b` passed
[34551618912](https://github.com/MrHakan/Minecraft-Client/actions/runs/34551618912), job `103115542075`,
`BUILD SUCCESSFUL in 23s`; compileJava/JUnit/build logs were inspected.
For the current continuation, consult the head-matched run and result recorded in PR #9; never reuse the
baseline green check as evidence for new code. The PR CI section is updated after publishing this batch.

Pure tests cover dispatch, registry, leases/arbitration, selection/geometry, rotations, notifications,
config migrations/file preservation, key chords, UI sessions/search/colors/layout, scan budgets/cursors/order,
copied slot snapshots, notification layout, nearest-candidate retention, theme preservation/accessibility,
module-list ordering/formatting and HUD measurement bounds.
**No Minecraft in-game run, screenshot validation or measured gameplay performance has been performed here.**
The local `./gradlew build` result covers compilation and JUnit only.
The scanner HUD exposes timing for future real measurements; its presence is not a performance benchmark.

Primary API sources inspected: [Fabric API 26.2](https://github.com/FabricMC/fabric-api/tree/26.2), especially
client lifecycle, screen, keymapping and render APIs. Known version differences:
`GuiGraphicsExtractor`/`extractRenderState`, `Identifier`, current key/mouse event records,
`KeyMappingHelper`, `LevelRenderEvents.COLLECT_SUBMITS`, `submitNodeCollector`, `ChunkPos.x()/z()` and `pack()`.
One earlier CI failure was direct access to private ChunkPos fields; corrected using accessors verified in
Fabric's 26.2 `ClientChunkCacheMixin`. Do not reintroduce 1.20/1.21 examples blindly.

### Required manual acceptance checks

- Flight/Step/Sprint/Fullbright/Freecam: enable/disable, death/respawn, dimension switch, disconnect/rejoin,
  player replacement and profile reload. Verify only owned state is restored; pre-existing effects survive.
- AutoEat/AutoTool: simultaneous activation, priority preemption, manual slot selection, screen opening,
  keyboard/mouse use-button holds, profile switching and old-player cleanup.
- Equipment events: damage an equipped item, change armor/offhand/mainhand, respawn; no stale previous-player values.
- UI: small/large GUI scales, keyboard navigation, modifier/mouse/Right Shift capture, duplicate conflicts,
  exact numeric entry, mixed-case/protected clipboard keys and invalid final values (no partial application).
- Themes: edit nested presets/colors, apply/cancel, GUI shortcut close, external screen replacement and disconnect.
  Test page navigation at GUI scales, high contrast on/off with custom colors, reduced-motion precedence,
  preset changes preserving accessibility and theme export/import. Preserve a deliberately unreadable theme file.
- Module List: old layout/config upgrade, disabling the controller, per-module hiding, each sort/alignment/case/color,
  background/strip changes, tiny viewport clipping and profile switches. Check editor bounds at each anchor.
- Inject a failing HUD renderer or width supplier: peers continue, one error is logged/notified, visibility preference
  survives and Retry reactivates a repaired component. Tiny component labels must stay inside selection bounds.
- HUD: notification bounds and animation after anchor changes, crowded/small screens, groups/locks/z-order,
  hidden components and overlap warnings. Enable scanner_debug only when diagnosing.
- All three scanners together at maximum settings: verify budgets, dense areas, chunks unloading/reloading,
  block/entity removal, changing filters/ranges and world replacement. Visual latency/partial observations are expected
  under limits, not a promise that every entity in a dense world is considered.
- Render isolation: failed overlay must not suppress peers; queued geometry from an old world must be skipped.
- **Mixin**: confirm the client actually starts with the mixin applied and that both injections fire.
  `required: true` with `defaultRequire: 1` means a failed injection is a hard crash at load, so this is
  the first thing to check in-game. Place and break blocks, trigger a piston or explosion for the
  section path, and watch BlockESP markers update without a rescan.
- TargetHUD: switch between all three layouts while a target is live; check absorption on a target
  with golden apples; confirm the bar does not slide when the target changes; confirm reduced motion
  disables the easing; confirm ping disappears for mobs and for players whose latency is unknown.
- After any module is verified in game, clear its `markExperimental()` call in the same commit that
  records what was tested. The badge is only useful while it is accurate.
- Localization: run the client in Turkish and in a language with no file at all; confirm the ClickGUI
  reads correctly in Turkish and falls back to English elsewhere, with no raw `agalarhack.*` keys
  anywhere. Confirm filtering and sorting still work in Turkish.
- BaseFinder: confirm it reports nothing with StorageESP off and says so in the module list; check a
  village and a real base both cluster sensibly; confirm a stable base does not re-notify each
  interval; confirm optional waypoint creation lands at the cluster centre.
- Macros: bind each of the three kinds, confirm a held key fires once, confirm nothing fires while a
  screen is open, confirm a macro targeting a missing module reports instead of failing silently, and
  restart to confirm persistence.
- SpawnESP: compare marks against a torch-lit area and an enclosed cave; confirm placing a torch
  clears the neighbourhood rather than leaving stale marks, and that "always" versus "night" matches
  whether the spot sees sky.
- Parkour with SafeWalk both on: confirm Parkour stands down rather than fighting the edge hold.
  Test at speed, while sprinting, in water and on stairs/slabs.
- ElytraInfo: confirm warnings fire once rather than repeatedly at the threshold, and that firework
  warnings stay quiet on the ground.
- ServerInfo: compare the estimate against a server whose real tick rate you know, confirm it never
  reads above 20, that it resets on dimension change, and that the lag warning fires once rather than
  repeatedly while the rate hovers at the threshold.
- Aliases: define, use with and without extra arguments, restart the client to confirm persistence,
  try to shadow a real command and to define a self-referencing alias, and hand-edit the file with
  nonsense to confirm only valid entries load.
- SafeWalk: walk off ledges with each mode, in singleplayer as well as multiplayer, and confirm the
  integrated server's own players are unaffected. Check it does not fight with Sprint or Speed.
- Projectile modules: have someone shoot at you and confirm the warning fires once, reads as an
  estimate, and does not fire for your own shots. Compare the trajectory overlay against where arrows
  actually land, since the simulator refactor must not have changed the path.
- Waypoints: add/remove/list from both dimensions, restart the client and confirm they persist, corrupt
  the file deliberately and confirm it is preserved with saving disabled, check the HUD arrow points the
  right way while turning, and confirm beams and labels respect render distance.
- ItemESP/Nametags: verify the caps hold in a dense area, that filters behave as whitelist and blacklist,
  and that labels stay readable rather than becoming a wall of text.
- BlockESP chunk cache: mine a tracked ore inside a scanned chunk and confirm the marker disappears
  immediately; reload the chunk, move the anchor and change filters and confirm rescans still happen.
- StorageESP: place a chest just after a sweep completes and confirm it appears before the next sweep;
  break it and confirm the marker goes.
- AutoRefill/InventoryCleaner/AutoRespawn: refill with a full inventory and with partial stacks;
  confirm InventoryCleaner drops nothing with an empty or garbage junk list, never drops enchanted or
  renamed items, and leaves the hotbar alone by default; confirm AutoRespawn does not fire on other screens.
- AutoArmor/AutoTotem/AutoWeapon/AutoRefill: swap with a full inventory, with a stack already on the cursor, while opening
  and closing the inventory mid-swap, during death/respawn and dimension changes, and while AutoEat/AutoTool are
  also active. Confirm no item is ever left on the cursor, that AutoTotem preempts AutoArmor, that a popped totem
  cancels the pending restore, and that a renamed armour piece is never auto-equipped by default.

### Manual acceptance for chat

- BetterChat: confirm timestamps appear on player chat, server messages and the client's own
  notifications, that seconds can be turned on, and that turning the module off removes them cleanly.
- ChatFilter repeats: have someone send the same line twice and confirm the second is hidden; have two
  people alternate the same two lines and confirm the window catches it; change one character and
  confirm it is shown. Reconnect and confirm the first line of the new session is never hidden.
- **Startup**: this is the batch that fixed a crash-on-launch, so simply confirm the client reaches
  the main menu and that the HUD editor shows the Movement Stats widget hidden rather than stacked on
  the branding line.

### Manual acceptance for tracers and row motion

- Storage tracers: turn them on near several container types and confirm each line takes its own
  colour, that lines still reach containers behind you, and that the frame cost is acceptable in a
  base with many chests.
- Module list motion: toggle modules on and off quickly and confirm rows slide rather than jump, that
  the HUD editor's box for the module list does not resize or jitter during the animation, and that
  changing the sort moves rows rather than flashing them all. Then turn reduced motion on in the theme
  and confirm both appearance and removal become instant.

### Manual acceptance for packet rates

- Turn on ServerInfo's `packetRates` and place the Packet Rate widget. Confirm the figure is plausible
  (tens per second while walking around, far more while loading chunks), that it reads "Packets off"
  with the setting disabled, and that it resets on reconnect rather than carrying the old server's
  rate across.
- Watch the frame time with the widget hidden versus shown; the counting path should be invisible
  either way, and this is the one change on a genuinely hot code path.

### Manual acceptance for the schema, confirmation and audit batch

- Confirmations: click Delete in the profile screen and confirm the dialog lists what is lost, that
  Escape and Enter both cancel, and that confirming actually deletes. Run `.waypoint clear all`
  without `confirm` and check nothing is removed.
- Schema versions: hand-edit a waypoint or alias file to `"schemaVersion": 99` and confirm the file is
  preserved with saving disabled rather than overwritten. Do the same to a profile.
- Trajectories: compare the overlay against where arrows, potions and experience bottles actually
  land, especially the potion families with their -20 degree offset. The constants are verified; the
  rendering of the arc is not.

### Manual acceptance for the HUD history and localization batch

- HUD editor: drag a widget, press Ctrl+Z and confirm it returns exactly where it was; Ctrl+Shift+Z
  to go forward again; make a new change after an undo and confirm redo is gone. Select several
  widgets and press Ctrl+D. Confirm the shortcut hints show the current depth.
- Error boundary: hard to trigger deliberately — if a chat module ever errors, confirm only that
  module is disabled and that the notification appears once rather than every message.
- Turkish: run the client in Turkish and confirm module descriptions read correctly in the ClickGUI
  and in `.modulesettings`, that module **names** are still English, and that search still finds
  modules by their Turkish description.

### Manual acceptance for the crit, portal, request and contrast batch

- CritInfo: jump and confirm it reads CRIT only while falling and not sprinting; confirm the charge
  percentage tracks the vanilla crosshair indicator.
- `.portal`: stand in the overworld and check the nether figure against actually building there;
  repeat from the nether. Confirm the waypoint lands in the other dimension and is visible on arrival.
- AutoAccept: configure phrases and a reply, have a friend send a request and confirm one command is
  sent; have a non-friend send the same line and confirm nothing happens; say the phrase yourself and
  confirm nothing happens; repeat quickly and confirm the cooldown holds.
- Contrast guard: pick a deliberately bad theme (dark grey text on a dark panel) and confirm the text
  becomes readable without losing the theme's colour; turn the guard off and confirm the original
  returns. Check both colourblind presets for on/off states being distinguishable.

### Manual acceptance for the fishing, face and profile batch

- **TargetHUD face** (unverified rendering): confirm the face draws at the right size and is not
  stretched, garbled or offset — the blit argument order is inferred from vanilla's own call, not
  observed. Confirm a skin with a hood or hair shows it, and that a mob card looks exactly as before.
- AutoFish: confirm one bite produces one reel and one recast, not two; confirm it does nothing while
  a screen is open or with no rod in the hotbar; confirm a missed bite does not leave it stuck. Try a
  laggy server, which is where the pending-action cancellation matters.
- Inventory widget: confirm durability bars and stack counts match the real inventory screen, that
  armour and the offhand appear above the rule, and that empty slots still outline.
- Profiles: describe one with a description and `#tags`, search for each, confirm an empty search
  lists everything, and confirm an old profile without metadata still loads. Bind a profile to the
  nether, confirm it applies on arrival and that the server binding does not reapply over it.

### Manual acceptance for the combat and colour batch

- Aura switch delay: fight two mobs at almost the same distance and confirm it stays on one rather
  than alternating; kill the held one and confirm it moves on immediately rather than after the delay.
  Set maxTargets above 1 and confirm each target gets a real hit rather than a spray.
- TriggerBot: with requireCritical on, confirm it holds until you are falling and not sprinting, and
  that it never waits forever on the ground. With reactionTicks set, sweep the crosshair across a
  passive mob and confirm nothing happens, then rest on it and confirm it fires. Confirm the weapon
  requirement blocks an empty hand.
- Performance: switch scanBudget to low in a dense area and confirm markers fill in more slowly but
  frames improve; confirm the scanner diagnostics HUD shows the new ceiling, not the old one.
- Rainbow: turn it on for Breadcrumbs and confirm the trail reads as a moving gradient rather than
  flashing in unison; turn it off and confirm the exact previous colour returns. Try it with a muted
  colour and confirm it stays muted, and with grey and confirm it is visible rather than staying grey.

### Manual acceptance for render culling

- Frustum culling: with BlockESP and StorageESP running in a dense area, turn on the spot and confirm
  markers appear and disappear at the screen edge without popping inside the view, and that nothing
  vanishes while still visible. Check a waypoint beam whose base is below the horizon still draws.
  Confirm tracers and breadcrumbs still reach targets behind you - they are exempt on purpose.
- Compare frame time in that dense area before and after; this is the change that should show up.

### Manual acceptance for the performance and lag batch

- Module timings: open the widget and confirm it fills in within a couple of seconds, that switching a
  module off removes it from the list rather than freezing its last figure, and that closing the widget
  stops measurement (the figures should restart from "sampling..." when reopened).
- Movement stats: compare the speed reading against walking, sprinting, sprint-jumping and elytra; walk
  into a wall and confirm the reading does not flicker to zero the way the old one did; take a portal
  and confirm the window clears instead of showing a teleport as speed.
- Ping spike and silence warnings: confirm each fires once rather than repeatedly, that the spike
  warning stays quiet for the first few samples after joining, and that turning a warning off and on
  again does not immediately re-fire for a condition that was already true.
- **Fullbright gamma mode** (new, untested): switch to `gamma`, confirm the brightness rises without a
  night vision effect appearing in the inventory, move the vanilla brightness slider yourself and
  confirm the module puts it back, then disable the module and confirm your original brightness
  returns. Disconnect while enabled and confirm the slider is restored there too. Note that a crash
  while enabled can leave the raised value in options.txt.

### Manual acceptance checks added by this batch

- BlockESP colours: turn on several categories at once and confirm ores, spawners and containers are
  distinguishable at a glance; set a custom `blockColors` entry and confirm it beats the built-in
  colour; turn `perBlockColors` off and confirm the sliders take over again.
- Death waypoints: die with the setting on and confirm the marker lands where you died with a beam;
  die again in the same spot and confirm one entry, not two; die three more times and confirm the
  oldest death waypoint goes and hand-made ones do not; rename a death waypoint and confirm it then
  survives pruning; die in the nether and confirm the cap still counts it.
- AutoWalk: confirm steering still works while it runs, that pressing the opposite key stops you
  without the module fighting back, that opening chat pauses it, and that walking into a wall turns it
  off with a notification after the configured time. Run it with Parkour on and confirm both act.
- Profiles: `.profile diff` two profiles you know differ, then diff one against the live config after
  changing a single setting. `.profile load <name> hud`, then a category, then a module name, and
  confirm nothing outside the selection changed - including which modules are enabled. Confirm a
  typo is refused by name rather than applied.

## Environment and publishing notes for the next agent

Previous local work directory: `/workspace/scratch/ca15ca290945/Minecraft-Client` (transient; may disappear).
No AGENTS.md was present at inspection; check again in a fresh environment. Use the remote branch to recover.
This is an externally Git-backed project; do not duplicate the repository into a separate artifact store.

An earlier environment had no Gradle download and no `javac`; that is no longer true here. Verify in your own
environment before assuming either way, and never claim a build result you did not observe.
Direct git push lacked HTTPS credentials; the connected GitHub tools published Git trees/commits and advanced
one branch ref per batch. Every created tree was verified against the corresponding local commit tree.
Consequently **local and remote commit SHAs differ even when source trees match**. Prefer checking out the
remote branch fresh. If reusing the old worktree, compare trees and history before synchronizing; never force-push
or reset away another agent's/user's commits. Use non-force ref updates and recheck the live head before publishing.

Further architectural details: [docs/FOUNDATION_SERVICES.md](docs/FOUNDATION_SERVICES.md).
