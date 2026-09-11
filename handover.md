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

Approximate progress against the complete requested roadmap: **38–44% implemented;
56–62% remains**. This is a qualitative scope estimate, not measured work hours, a count
of commits, or a release-readiness percentage. Earlier estimate was about 20%; this batch
mainly deepens existing foundations. Do not extrapolate remaining duration from these numbers.
No entire phase is accepted as complete; Minecraft in-game smoke testing remains outstanding.

| Phase | Approximate implementation | Main remaining work |
| --- | --- | --- |
| A: foundation | 90–95% | Rotation adoption beyond Aura, shared selector adoption by visuals, integration tests |
| B: UI/HUD | 55–60% | Full widget/animation/accessibility coverage, Module List transitions, richer TargetHUD, remaining legacy bounds |
| C: rendering | 35–40% | More ESP modes, projectiles, breadcrumbs, generalized tracers, user block sets, trajectory extraction |
| D: player utility | 55–60% | AutoFish, AutoWalk, AutoAccept, FastPlace, inventory HUD depth |
| E: movement/world | 0–5% | SafeWalk, Parkour, Elytra utility, BaseFinder/HoleESP/light visualization |
| F: information/social | 0–5% | BetterChat, totems, TPS estimates/ping graph, macros and aliases |
| G: ecosystem | 0% | Stable external addon API/template, optional Baritone, localization |
| H: hardening | 50–55% | In-game lifecycle testing, complete profiling/config migration audit |

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
| 1 | Internal events | Substantially implemented: tick/render/connection/entities/chunks/block entities/equipment/screens/input plus a verified block-update producer |
| 2 | Service registry | Partial: real shared managers/services; waypoint/addon contracts pending |
| 3 | Target selector | Partial: combat filters/priorities implemented; visuals share EntityDiscovery but not the combat selector |
| 4 | Inventory service | Partial: bounded lookup, copies, equipment events, hotbar/use ownership, armour/weapon scoring and preemptible container transfers; multi-container transfers still out of scope |
| 5 | Rotations | Partial: None/Client/Smooth; validated silent adapter and wider integration missing |
| 6 | Notifications | Substantially implemented: queue, producers, HUD, settings and optional sound; more producers may still be added |
| 7 | UI components | Partial: toggle/slider/choice/text/bind/color; full panel/card/range/multiselect/modal toolkit pending |
| 8 | Animations | Partial: global controls and notification motion; screen/category/scroll/modal animation coverage missing |
| 9 | Module browser | Substantially implemented: filters/favorites/recency/search/sort/actions; visual/manual QA remains |
| 10 | Bind capture | Implemented keyboard/mouse/modifiers/ESC and conflict warnings; in-game duplicate/reserved-key tests remain |
| 11 | Color picker | Partial: RGB/HSV/alpha/hex/recent/copy-paste; rainbow and richer visual control pending |
| 12 | Themes | Partial: six presets, guarded import/export, high contrast/reduced motion; font scale/blur and full accessibility integration missing |
| 13 | Dynamic HUD | Partial: registry plus useful components; full suggested catalogue missing |
| 14 | HUD editor | Partial: groups/locks/z-order/grid/guides/snapping and guarded exact small bounds; duplication/undo and legacy Info/Target bounds pending |
| 15 | Module List HUD | Partial: alignment/sorting/style/case/display/row cap/per-module hiding implemented; row animations and additional suffix producers pending |
| 16 | EntityESP | Partial: boxes/tracers/labels/colors/fade; now bounded tick discovery, caps and label distance; expanded modes pending |
| 17 | Nametags | Implemented: single-line opt-in fields, friend marker, bounded discovery; no per-field styling yet |
| 18 | StorageESP | Partial: types, colors/labels, bounded incremental scans and live block-entity tracking; per-type controls/fill/tracers pending |
| 19 | BlockESP search | Partial: existing category filters; custom registry IDs/colors and preset management pending |
| 20 | Waypoints | Implemented: persistence, .waypoint commands, beams, labels, HUD arrow; death waypoint still pending |
| 21 | Breadcrumbs | Not started |
| 22 | Generalized tracers | Not started; existing ESP tracer mode remains |
| 23 | ItemESP | Implemented: bounded discovery, whitelist/blacklist, rarity colouring, count/distance labels |
| 24 | ProjectileESP | Not started |
| 25 | Projectile warning | Not started; informational only when implemented |
| 26 | Freecam expansion | Partial existing camera/body/motion settings plus restoration changes; full requested controls/QA pending |
| 27 | Fullbright modes | Partial: existing effect-based mode and restoration; safe gamma-like mode pending |
| 28 | Camera tweaks | Not started |
| 29 | Trajectory simulator | Partial existing simulation in WorldOverlayRenderer; standalone reusable architecture missing |
| 30 | Trajectory accuracy | Partial existing charge/collision/custom physics; full physics-family audit and markers/time details pending |
| 31 | TargetHUD | Partial existing health/equipment/effects card; faces/layouts/animations/detail expansion pending |
| 32 | Combat history | Not started |
| 33 | AutoArmor | Implemented via scoring plus the container channel; in-game swap/cursor testing outstanding |
| 34 | AutoTotem | Implemented with hysteresis-guarded offhand restore and totem counting; explosion/falling triggers and in-game testing outstanding |
| 35 | AutoRefill | Implemented: threshold refills that prefer the smallest source stack |
| 36 | InventoryCleaner | Implemented whitelist-driven with enchanted/named/hotbar protection |
| 37 | AutoFish | Not started; legitimate local bite cues only |
| 38 | AutoWalk | Not started |
| 39 | AutoRespawn | Implemented with a configurable delay; optional death waypoint still depends on WaypointService |
| 40 | AutoAccept | Not started; explicitly opt-in local requests only |
| 41 | Use tweaks | Not started; bounded, no packet spam |
| 42 | Inventory HUD | Partial implemented main-inventory viewer; richer item overlays/layouts pending |
| 43 | SafeWalk | Not started |
| 44 | Parkour | Not started |
| 45 | AutoJump | Not started |
| 46 | Elytra utility | Not started; warnings/counters/equipment assistance first |
| 47 | Movement stats | Partial simple horizontal speed HUD; vertical/acceleration/history pending |
| 48 | Aura improvements | Partial shared targeting/rotation/policy; switch delay/lock/multiple-target behavior pending |
| 49 | TriggerBot improvements | Partial shared filters and baseline cooldown behavior; full weapon/critical/reaction settings pending |
| 50 | AutoWeapon | Implemented on the hotbar lease above AutoTool, using scoring and the damage-family tags |
| 51 | Critical information | Not started; no packet exploit chains |
| 52 | Totem tracker | Not started; client-visible activations only |
| 53 | BaseFinder | Not started; reuse scheduler and local evidence |
| 54 | NewChunks | Not started; conservative observable classification only |
| 55 | Light/spawn visualization | Not started; verify 26.2 spawn rules and bounded scanning |
| 56 | HoleESP | Not started |
| 57 | Portal/gateway finder | Partial existing BlockESP portal filter; reuse this infrastructure |
| 58 | BetterChat | Not started |
| 59 | Chat mentions | Not started |
| 60 | Translator architecture | Optional, not started; core operation must not depend on cloud API |
| 61 | Macros | Not started |
| 62 | Command aliases | Not started |
| 63 | Server info HUD | Partial address/dimension baseline info; protocol/ping/rates/estimates expansion pending |
| 64 | Ping graph | Not started |
| 65 | TPS monitor | Not started; must label estimates |
| 66 | Lag detector | Not started |
| 67 | Profile manager 2 | Partial existing rename/duplicate/import/export/bindings; metadata/search/dimension overrides pending |
| 68 | Partial profiles | Not started |
| 69 | Profile diff | Not started |
| 70 | Config schemas | Partial module v1 and HUD editor v1; remaining store migrations pending |
| 71 | Addon API | Internal groundwork only; no external stable API or JAR loading |
| 72 | Addon metadata | Not started |
| 73 | Addon template repo | Not created; create MrHakan/AgalarHack-Addon-Template only after API stability |
| 74 | Baritone | Not started; optional detection/integration, no mandatory dependency |
| 75 | Localization | Not started; English/Turkish resources required |
| 76 | Accessibility | Partial keyboard controls, paginated themes, high contrast/reduced motion; full legacy color migration, UI scale/text/colorblind/blur controls pending |
| 77 | Unified scheduler | Partial implemented for BlockESP/StorageESP/EntityESP; further consumers/configurable budgets pending |
| 78 | Chunk result cache | Implemented for BlockESP: bounded LRU clean-chunk cache with block-update, unload, anchor and filter invalidation. Other scanners still sweep |
| 79 | Render culling | Partial distance/target/label bounds shared through EntityDiscovery; frustum culling pending |
| 80 | Performance HUD | Partial scanner diagnostics and memory; module tick/render timings and broader counters pending |
| 81 | Unit tests | 172 tests; adds block-update batching, chunk cache eviction, cursor completion, id-list parsing, notification sinks, waypoint normalisation/persistence and compass bearings; trajectory and fade coverage pending |
| 82 | Integration smoke tests | Not performed in-game; automate where feasible and record exact environment/results |
| 83 | Lifecycle audit | Partial code audit/restoration; all listed state-changing modules need in-game transition checks |
| 84 | Error reporting | Partial logger/module/render/scanner isolation; guarded HUD measurements/renderers with notices and retry; remaining boundaries need review |
| 85 | Structured logging | Implemented SLF4J replacement for raw stderr; logging quality audit remains |
| 86 | Module documentation | Partial metadata/foundation docs/handover; generated per-module defaults/limitations docs pending |
| 87 | Experimental flags | Not started |

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

Still missing in Phase C: ProjectileESP, projectile warning, breadcrumbs, generalized tracers, custom
BlockESP block sets, camera tweaks and the trajectory simulator extraction.

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
