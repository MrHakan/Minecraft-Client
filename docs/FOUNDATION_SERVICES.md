# Foundation services — Minecraft 26.2

This change extends main at `19f83ab` (26.2.5). No open PRs existed when work began.
It is an incremental foundation, not completion of the full client roadmap.

## Architecture

`AgalarHackClient` remains the composition root and keeps its public manager fields for
compatibility. Modules can use `service(Contract.class)` instead of static manager lookup.
Existing Settings, profiles, HUD layouts and submit-node renderer remain in place.

Registered services include existing managers, EventBus, InventoryService, TargetService,
RotationService, NotificationService, ServerContextService, InputStateService, RenderService, ScannerService, ThemeService and HudRegistry.
Registration rejects duplicates. Optional integrations must use `registry().find(...)`.
There is no external addon compatibility promise yet.

## Events and lifetime

Events dispatch synchronously on the caller's thread. Subscribe/unsubscribe and normal posts
belong on the client thread. Render payloads are borrowed and must never be retained or passed
to asynchronous tasks. Priority sorts descending; equal priorities retain registration order.
Subscriptions created during a post begin on the next post. Closing a subscription immediately
prevents subsequent invocation. Failed listeners are removed and logged, without suppressing peers.

Fabric feeds client/world ticks, joins, disconnects, entity and chunk loads/unloads, render submission,
HUD extraction and screen-specific input. ServerContextService publishes identity-based
world/player/death transitions and screen open/close changes at tick boundaries; resizes do
not create duplicate opens. InputStateService publishes bounded tick-sampled keyboard/mouse
edges, not a lossless raw GLFW event stream. Inventory changes cover the 36 main inventory
slots and selected slot; separate EquipmentUpdated events cover four armor slots and both hands.
SlotSnapshots isolates cached state from source stacks and event payloads; player replacement resets both trackers.

Tick order: context (300), sampled input (200), action reset (100), inventory (90), profiles
(80), module binds (60), modules (40), scanner execution (30), rotation resolution (20), GUI key (0).

World changes clear service leases before enabled modules restore and rebuild world-scoped
state. Menu-only modules retain their separate lifecycle. RenderService isolates deferred
geometry failures and schedules module disable/persistence on the client thread.

## Inventory

Inventory queries inspect at most 36 slots (hotbar queries: 9). Food and tool scoring are
shared by AutoEat/AutoTool. Generic predicate/item/block/potion lookups, full-inventory food selection and defensive inventory/equipment snapshots are available.
Leases persist across ticks; priority preemption restores the previous owner before a new
owner captures the baseline. Dual hotbar/use acquisition is atomic. Cleanup targets the
captured player, never a replacement player's inventory. Manual slot changes cancel the lease
and cause a 10-tick backoff. Use-key release samples the physical configured input.

Main-inventory swaps and specialized best-weapon/armor selection are not implemented yet.
Automation only selects the local hotbar; inventory GUI slots are not interchangeable with
main inventory indices.

## Targets and rotations

Aura and TriggerBot share filtering, range/FOV geometry and global-policy enforcement.
Settings retain the existing players/mobs/ignoreFriends/ignoreInvisible names. New filters
cover hostile/passive/creature-category mobs, teammates, creative players, sleeping entities,
armor stands and named mobs. NPC-like filtering is an opt-in player-list heuristic; it must
not be described as authoritative bot detection. Global safety policy remains authoritative.

Priorities: closest, lowest/highest health, lowest armor, horizontal angle, crosshair angle,
hurt time and recently observed attacker (200 ticks). Ties use distance then entity ID.
Selection retains at most 32 candidates and considers at most 4096 loaded entities per call.
In unusually dense worlds, candidates beyond the iteration cap are not considered; the
selector does not promise globally nearest results outside its bounded observed set.

Aura rotation defaults to None. Client/Smooth requests share one service, with yaw wrapping,
pitch clamps, speed/step limits, priority arbitration and optional conditional return.
Return does not overwrite a manually changed view. Silent rotation is not exposed until a
verified appropriate movement/interaction adapter exists. No packet exploit chains are added.

## Notifications and configuration

Notifications render status/error toasts, with duration, queue bound, corner and animation
controls under the Notifications module. Module-toggle notices can be disabled separately.
Profile load, friend add, reconnect attempts and config recovery are connected. Queue size
is capped at 10, text at 180 characters; repeated immediate duplicates are coalesced. Notifications are registered as a draggable HUD component. Sound remains pending.

The module config migrates from a legacy bare module map (v0) to:

```json
{"schemaVersion": 1, "modules": {}}
```

Module setting names and values survive migration. Profile JSON remains backward compatible
and contains its existing module snapshot map. A future schema is rejected without rewriting
its file. An unreadable or oversized file disables writes. Malformed files are backed up
before defaults may be written; failed backups also block writes. Atomic replacement remains.
HUD layout and editor files now use bounded reads and temporary-file replacement. Invalid,
unreadable, oversized or future editor schemas preserve the source and disable writes until a
successful reload. Layouts retain their legacy map format, bounded to 256 valid component IDs.
Editor options accept older unversioned objects and now write schemaVersion 1. Other stores
still require a migration audit.

## UI session and clipboard boundaries

Client screens expose their parent hierarchy. Opening a child keeps the parent edit session;
leaving the entire branch (including external screen replacement) abandons it. Unsaved theme
previews then restore the previous palette. Captured binding events cannot also trigger the
GUI shortcut. The GUI shortcut does not replace unrelated vanilla screens.

Module clipboard parsing canonicalizes metadata names, rejects case-insensitive protected keys
and duplicate aliases, validates every scalar before mutation, and bounds payloads to 64 KiB.
Applying/resetting settings to an active module restores its old state before re-enabling with
the new settings. This boundary is covered by pure parsing tests.

## Shared scanner execution

BlockESP, StorageESP and EntityESP offer work to one cooperative scheduler after module updates.
Each tick allows at most 12,000 block probes, 64 explicit chunk lookups, 4,096 block-entity
iterations and 16,384 task steps. Requests expire each tick. Steps reserve costs before world
access; missing chunks are never requested for loading. Failed tasks disable only their owner.
Near/focused/background priorities have weighted turns; equal-priority starting order rotates.
BlockESP and StorageESP request background priority and traverse nearby chunks first. EntityESP
requests NEAR priority, observes at most 4096 entities and retains at most 512 nearest observed targets
(default 256). Labels have a separate distance setting. It no longer discovers entities in render callbacks.
Budgets cover scanner discovery, not every client subsystem or existing render validation.

BlockESP uses a tested chunk-local cursor, one reusable mutable probe, skips missing chunks,
and only rebuilds immutable render snapshots when results change. StorageESP incrementally
walks chunk block-entity maps and publishes a completed bounded pass. A map changed between
ticks restarts that chunk's iterator; 4,096 visits per chunk bound retries and unusually dense
chunks. `scanInterval` now means ticks between completed passes. Undyed shulker boxes are
included. Chunk unload removes markers and releases active iterators. World/player changes,
disable and config changes reset the applicable state.

This is not a completed persistent chunk-result cache: lookup references live only for one
scheduler tick; discovery still refreshes periodically. Block-update invalidation and shared
budgets for other entity consumers remain pending. Storage scans may take multiple ticks and
omit block entities beyond their bounded per-chunk/result caps. The submit-node pipeline is
unchanged; rendering uses bounded snapshots and skips unloaded storage/block positions.

## Verification

JUnit covers dispatch ordering, unsubscribe during dispatch, deferred subscription, failure
isolation, service registration, bounded inventory scoring, atomic utility claims, inventory
preemption/manual input/player replacement, target geometry/priorities, rotation math,
notification expiry/eviction, settings sanitation and v0-to-v1 config migration.

The first six commits passed `./gradlew build --stacktrace` with JDK 25 in Actions run
[34493696597](https://github.com/MrHakan/Minecraft-Client/actions/runs/34493696597).
The second and third six-commit batches passed Actions runs
[34494795564](https://github.com/MrHakan/Minecraft-Client/actions/runs/34494795564) and
[34496559943](https://github.com/MrHakan/Minecraft-Client/actions/runs/34496559943).
The fourth UI/persistence/scanner batch initially failed run 34533509097 on private ChunkPos
fields. The follow-up uses the x()/z() accessors verified in Fabric 26.2 ClientChunkCacheMixin;
the follow-up passed `./gradlew build --stacktrace` (including JUnit) in Actions run
[34533637579](https://github.com/MrHakan/Minecraft-Client/actions/runs/34533637579).
The final choice-screen refresh correction must also pass its own CI run; see the PR for
the latest head validation. Local Gradle bootstrap is unavailable in the
restricted execution environment. A compiled JAR does not establish in-game visual correctness.

### Required in-game smoke tests

- Enable Flight/Step/Sprint/Fullbright, disable them, then repeat across death, respawn,
  dimension changes and reconnect. Check restored abilities, attributes and potion state.
- Enable Freecam in-world and from a saved profile; verify camera initialization on join,
  restoration on disable/death/disconnect and that an old-world camera is never reattached.
- Run AutoEat and AutoTool together; change slots manually; open inventory; hold/release
  the physical use binding; change its keyboard/mouse assignment; verify swap-back.
- Load profiles while automation is active, including a profile keeping the same module
  enabled with changed settings. Check old state is released before applying new values.
- Exercise friend/team/creative/sleeping/named-mob filters and wall/FOV boundaries.
- Try smooth Aura aim with return enabled, custom/vanilla cooldowns and manually moving view.
- Check all existing overlays with labels and notifications at small GUI sizes.
- Confirm notification preferences, reconnect attempts and config-recovery messages.
- Open theme color/preset children, cancel them, close the whole GUI with its shortcut and
  replace it with a vanilla screen/disconnect. Verify saved themes persist and abandoned previews revert.
- Capture Right Shift and modifier combinations; confirm capture does not also close/reopen the GUI.
- Paste mixed-case setting names, protected aliases and an invalid last field; verify no partial mutation.
- Enable both scanners at maximum settings in a dense loaded area. Check scheduler counters,
  unload/reload chunks, replace storage blocks during a pass, change filters/ranges, reconnect and
  change dimension. Verify no stale world references or unloaded markers survive.
- Check undyed/colored shulkers and storage distance culling. In-game timing and visuals remain unverified.

## Remaining roadmap

Phase A remains in progress: lossless gameplay input/block-update producers, specialized weapon/armor
scoring and inventory transfers, rotation integration hardening, notification sound, further scanner/cache
integration and additional lifecycle integration tests. Equipment has a distinct event contract from main inventory slots.

Phase B now includes reusable sliders with exact entry, choices/toggles, keyboard/mouse/modifier
bind capture, RGB/HSV/alpha colors, six independently persisted themes, dynamic HUD registration,
inventory/information widgets and multi-selection/locking/z-order/alignment tools. Full animation,
typography/blur/accessibility controls, richer Module List and TargetHUD remain pending. Phases C–G (deeper rendering, player utilities, movement,
information/social, ecosystem/localization) remain pending. Phase H applies continuously;
no phase is marked complete without its review and required validation.

## API sources inspected

- [Fabric client lifecycle events, 26.2](https://github.com/FabricMC/fabric-api/tree/26.2/fabric-lifecycle-events-v1/src/client/java/net/fabricmc/fabric/api/client/event/lifecycle/v1)
- [Fabric screen API, 26.2](https://github.com/FabricMC/fabric-api/tree/26.2/fabric-screen-api-v1/src/client/java/net/fabricmc/fabric/api/client/screen/v1)
- [Fabric render API, 26.2](https://github.com/FabricMC/fabric-api/tree/26.2/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1)
- [KeyMappingHelper, 26.2](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-key-mapping-api-v1/src/client/java/net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.java)

## Current handover

See [../handover.md](../handover.md) for the numbered roadmap, current estimates and exact continuation rules.
The optional scanner_debug HUD displays last-tick discovery counters and elapsed time without initiating scans.
NotificationLayout shares measured bounds between rendering and editor placement; motion follows the actual
HUD anchor. Disabling notifications clears and suppresses their queue. Deferred world geometry skips changed
world/player identities. In-game acceptance remains pending; consult the latest PR-head CI result.
