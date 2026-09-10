# Foundation services — Minecraft 26.2

This change extends main at `19f83ab` (26.2.5). No open PRs existed when work began.
It is an incremental foundation, not completion of the full client roadmap.

## Architecture

`AgalarHackClient` remains the composition root and keeps its public manager fields for
compatibility. Modules can use `service(Contract.class)` instead of static manager lookup.
Existing Settings, profiles, HUD layouts and submit-node renderer remain in place.

Registered services include existing managers, EventBus, InventoryService, TargetService,
RotationService, NotificationService, ServerContextService, InputStateService and RenderService.
Registration rejects duplicates. Optional integrations must use `registry().find(...)`.
There is no external addon compatibility promise yet.

## Events and lifetime

Events dispatch synchronously on the caller's thread. Subscribe/unsubscribe and normal posts
belong on the client thread. Render payloads are borrowed and must never be retained or passed
to asynchronous tasks. Priority sorts descending; equal priorities retain registration order.
Subscriptions created during a post begin on the next post. Closing a subscription immediately
prevents subsequent invocation. Failed listeners are removed and logged, without suppressing peers.

Fabric feeds client/world ticks, joins, disconnects, entity loads/unloads, render submission,
HUD extraction and screen-specific input. ServerContextService publishes identity-based
world/player/death transitions and screen open/close changes at tick boundaries; resizes do
not create duplicate opens. InputStateService publishes bounded tick-sampled keyboard/mouse
edges, not a lossless raw GLFW event stream. Inventory changes cover the 36 main inventory
slots and selected slot; payloads contain copies rather than mutable cached stacks.

Tick order: context (300), sampled input (200), action reset (100), inventory (90), profiles
(80), module binds (60), modules (40), rotation resolution (20), GUI key (0).

World changes clear service leases before enabled modules restore and rebuild world-scoped
state. Menu-only modules retain their separate lifecycle. RenderService isolates deferred
geometry failures and schedules module disable/persistence on the client thread.

## Inventory

Inventory queries inspect at most 36 slots (hotbar queries: 9). Food and tool scoring are
shared by AutoEat/AutoTool. Generic predicate/item/block/potion lookups are available.
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
is capped at 10, text at 180 characters; repeated immediate duplicates are coalesced. Sound
and a dynamically draggable HUD component are still pending.

The module config migrates from a legacy bare module map (v0) to:

```json
{"schemaVersion": 1, "modules": {}}
```

Module setting names and values survive migration. Profile JSON remains backward compatible
and contains its existing module snapshot map. A future schema is rejected without rewriting
its file. An unreadable or oversized file disables writes. Malformed files are backed up
before defaults may be written; failed backups also block writes. Atomic replacement remains.
Other configuration stores retain their current formats; their migration audit is pending.

## Verification

JUnit covers dispatch ordering, unsubscribe during dispatch, deferred subscription, failure
isolation, service registration, bounded inventory scoring, atomic utility claims, inventory
preemption/manual input/player replacement, target geometry/priorities, rotation math,
notification expiry/eviction, settings sanitation and v0-to-v1 config migration.

The first six commits passed `./gradlew build --stacktrace` with JDK 25 in Actions run
[34493696597](https://github.com/MrHakan/Minecraft-Client/actions/runs/34493696597).
Later commits require their own Actions result. Local Gradle bootstrap is unavailable in the
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

## Remaining roadmap

Phase A remains in progress: lossless gameplay input/block-update producers, equipment and
full-inventory queries, rotation integration hardening, notification sound, service-level
scanner budgets and additional lifecycle integration tests. The 36-slot inventory event is
explicitly not an armor/offhand inventory event.

Phase B follows with reusable setting controls, bind capture, colors/themes, dynamic HUD
registration and alignment tools. Phases C–G (deeper rendering, player utilities, movement,
information/social, ecosystem/localization) remain pending. Phase H applies continuously;
no phase is marked complete without its review and required validation.

## API sources inspected

- [Fabric client lifecycle events, 26.2](https://github.com/FabricMC/fabric-api/tree/26.2/fabric-lifecycle-events-v1/src/client/java/net/fabricmc/fabric/api/client/event/lifecycle/v1)
- [Fabric screen API, 26.2](https://github.com/FabricMC/fabric-api/tree/26.2/fabric-screen-api-v1/src/client/java/net/fabricmc/fabric/api/client/screen/v1)
- [Fabric render API, 26.2](https://github.com/FabricMC/fabric-api/tree/26.2/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1)
- [KeyMappingHelper, 26.2](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-key-mapping-api-v1/src/client/java/net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.java)
