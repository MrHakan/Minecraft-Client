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

### Item scoring

`ItemScoring` ranks armour and weapons over plain records so the rules are unit tested without a
client. `InventoryService` adapts live items into those records: attribute values come from
`ItemStack.forEachModifier` for the slot the item would occupy, and only `ADD_VALUE` modifiers are
summed because the multiplied operations depend on the wearer's other gear. Damage families come
from the `SENSITIVE_TO_SMITE` / `SENSITIVE_TO_BANE_OF_ARTHROPODS` entity type tags, not the
mob-type enum older versions used. Nearly broken gear is devalued progressively rather than
rejected. Scores are comparison keys only and are never shown as damage predictions.

### Container transfers

Inventory indices (0..35) and `InventoryMenu` slot ids (0 result, 1..4 crafting, 5..8 armour,
9..35 storage, 36..44 hotbar, 45 offhand) are different coordinate systems for the same items.
Every conversion goes through `InventoryTransfers`, whose constants are asserted against the
26.2 layout by tests. Minecraft 26.2 uses `handleContainerInput` with `ContainerInput`; the older
`handleInventoryMouseClick`/`ClickType` pair no longer exists.

`ContainerTransferController` runs at most one click per tick, only from a normal play state with
an empty cursor. Priority preemption is allowed only while the cursor is empty, because a click
boundary with nothing carried always leaves the inventory consistent; a plan holding a carried
item cannot be preempted, an owner cannot preempt itself, and an owner still returning a stack is
left alone. Losing the click channel mid-plan drops the remaining clicks instead of guessing, and
a full inventory holds the channel rather than dropping the player's item on the floor. Hotbar
sources use a single atomic SWAP click that never involves the cursor.

AutoArmor (50), AutoWeapon (45 on the hotbar lease) and AutoTotem (90) consume these. Container
transfers only operate on the player's own inventory menu; other containers are out of scope.

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
Return does not overwrite a manually changed view. Hidden/silent rotation is deliberately omitted; visible aiming is shared by Aura and `.look`. No packet exploit chains are added.

## Notifications and configuration

Notifications render status/error toasts, with duration, queue bound, corner and animation
controls under the Notifications module. Module-toggle notices can be disabled separately.
Profile load, friend add, reconnect attempts and config recovery are connected. Queue size
is capped at 10, text at 180 characters; repeated immediate duplicates are coalesced. Notifications are registered as a draggable HUD component. Optional severity-filtered sound is implemented in Notifications/NotificationSounds.

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

## Block updates

Fabric API 26.2 provides chunk, block-entity and player-break events but no general server-sent block
update, so `ClientPacketListenerMixin` is the producer: TAIL injections on `handleBlockUpdate` and
`handleChunkBlocksUpdate`, which never alter or cancel vanilla handling and run on the client thread.
The client now has six mixin classes; this is the block-update producer. `agalarhack.mixins.json` is client-only and its `compatibilityLevel` must
remain **JAVA_25**: the mod compiles to class version 69, and a lower level is rejected at load time
even though the build succeeds.

Section packets can carry up to 4096 changes. `BlockUpdateBatch` reports the first 512 individually and
then posts one `ChunkBlocksInvalidated` for the chunk instead. `ClientBlockEntityEvents` supplies block
entity load/unload directly, with no mixin.

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

BlockESP now uses the persistent bounded ChunkScanCache described below, with block/chunk/config
invalidation. StorageESP maintains its bounded event-updated pass; it does not use that cache.
Rendering consumes bounded snapshots and preserves the current submit-node pipeline.

## Waypoints

`Waypoint` is a record that normalises in its canonical constructor - trimmed bounded name,
coordinates inside world limits, opaque colour - so anything holding one can render it without
re-validating. Identity is the name within a dimension, case-insensitively.

`WaypointCodec` rejects a damaged or future-versioned envelope so `BoundedJsonFile` preserves the file
rather than overwriting it, but skips individual malformed entries so one bad waypoint does not cost
the player the rest. `WaypointService` follows the same preserve-on-failure rule as the other stores
and uses its own SLF4J logger rather than the client's shared field, which cannot be touched outside a
Fabric runtime.

`WaypointCompass` holds the bearing maths for the HUD arrow. Minecraft's yaw convention is pinned by
tests: yaw 0 faces +Z, yaw increases turning left, and a target to the player's right has a positive
relative bearing.

A dimension id in 26.2 is `ResourceKey.identifier()`, not `location()`.

## Shared entity discovery

`EntityDiscovery` owns the tick-scheduled, nearest-first entity sweep used by EntityESP, ItemESP and
Nametags: at most 4096 observations per tick, budget-aware, and it publishes an empty snapshot rather
than a stale one if the world or player is replaced mid-pass. Visual modules must not walk the entity
list during rendering.

## Chunk result caching

`ChunkScanCache` is a bounded LRU of chunks a scanner has walked in full and that nothing has
invalidated since. BlockESP skips those chunks without probing a block, and applies single block
updates to its markers directly so one placed block no longer forces a rescan. Cleanliness is recorded
only when the cursor genuinely exhausts a chunk — never when it skips an unloaded one — and never while
the result set is at its cap, since markers are being dropped there and caching would make that
permanent. Eviction is the safe direction: a forgotten chunk is rescanned, never assumed clean.

StorageESP does not use this cache; it keeps its published set live from block-entity events, with a
completed sweep remaining authoritative so missed events self-heal.

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
the latest head validation. A compiled JAR does not establish in-game visual correctness.

Local builds are possible after all: Gradle resolves dependencies through the environment proxy,
and a JDK 25 can be fetched from Adoptium and passed with `-Dorg.gradle.java.home`. The system JDK
is 21, which cannot satisfy `options.release = 25`. `./gradlew genSources` also works, and
`javap -constants -cp` against
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/...` is a
fast way to confirm a 26.2 signature before writing against it. Local success still does not
replace the branch-head CI run recorded in the PR.

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

## Current scope and acceptance

Use the authoritative CURRENT STATE at the top of handover.md. Inventory scoring/transfers, block
updates, sound, HUD history/scale, addons, optional Baritone and most Phase C–F modules already exist.
Remaining work is predominantly adversarial lifecycle and release acceptance, standalone addon
packaging, dedicated-server verification, visual correctness and selected partial UI/localization depth.
Historical build runs below/above are provenance; PR #9 records the current head-matched full CI.

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


## Theme and HUD continuation

ThemeService now uses the shared BoundedJsonFile with a typed ThemeCodec (16 KiB UTF-8).
Existing schema-1 themes and defaults remain supported; invalid/future/unknown-field input is rejected,
original bytes survive failed reads, and a successful reload unlocks saving. High contrast overrides the
shared palette without changing stored colors. Reduced motion takes precedence over uiAnimations and
stops notification movement and HUD rainbow. Theme controls paginate; font scaling/blur and migration
of every hardcoded legacy HUD color remain pending.

ModuleList controls the existing `modules` HUD ID and preserves saved positions. It defaults enabled and
hides itself; modules have a showInHud setting. The renderer supports stable width/name/category sorting,
left/right/anchor alignment, case/display modes, rainbow/accent/category colors, background/edge/shadow
and bounded rows. The pure ModuleListModel formats with Locale.ROOT and caps custom labels at 128 chars.
RowAnimations now supplies bounded slide/fade transitions; suffix text still comes from real module state.

HUD registration is capped at 256 with non-null callbacks and validated IDs/titles. Render/measurement
failures suspend the component and emit one error without persisting hidden state. The editor's Retry
reopens that boundary. HudMeasurement keeps small dimensions exact and clamps oversize providers;
legacy Info/Target bounds still need extraction from their render models. Latest head-specific CI evidence
and manual acceptance checklist are in PR #9 and handover.md.
