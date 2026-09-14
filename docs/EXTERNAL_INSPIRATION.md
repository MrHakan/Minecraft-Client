# External inspiration and scope

This batch is a clean-room, safety-first mash-up of useful ideas observed in public
Minecraft client addons. It does not copy source code, mixin names, packet formats or
licenses into Agalar Hack.

## Implemented

- **PlayerAlerts** takes the bounded "player alarm" idea reviewed in
  [Trouser-Streak](https://github.com/etianl/Trouser-Streak) and routes it through Agalar
  Hack's typed EventBus, FriendManager and NotificationService. It reports only
  client-loaded player entities, deduplicates UUIDs, applies a cooldown and stays bounded.
- **GamemodeAlerts** takes the informational "game mode notifier" idea reviewed in
  [meteor-rejects](https://github.com/AntiCope/meteor-rejects) and polls the client-visible
  player list. The first snapshot is a baseline, changes are bounded and notices explicitly
  say "client-visible". No packet mixin or hidden server state is inferred.
- The surrounding client already contains the deeper utility foundations those addons often
  duplicate: shared target selection, scanner budgets, inventory arbitration, waypoints,
  AutoGrind and the optional Baritone bridge.

## Deliberately excluded

The reviewed repositories also contain duplicate ESP variants, packet flooding, crash and
dupe attempts, anti-cheat bypass presets, automation that would conflict with the existing
inventory leases, and modules that require unverified version-specific mixins. Those are not
part of the product target and were not imported merely to increase the module count.

The [MeteorPlus 26.2 branch](https://github.com/MeteorClientPlus/MeteorPlus/tree/26.2)
was inspected as a compatibility reference, but its bypass/blatant focus is deliberately
outside this client. This repository therefore gets two useful, testable information modules,
not a bulk copy of unrelated or unsafe features.

## Evidence boundary

GamemodeAlerts is now verified by the integrated-server survival-to-creative scenario in
ModuleBehaviourGameTest, so its experimental flag is cleared in the same evidence batch. PlayerAlerts
and its pure bounded tracker have unit coverage; ModuleLifecycleGameTest proves the module enables,
ticks and disables without taking down the client, but PlayerAlerts still needs a real second-player
client-visibility acceptance before its flag can be cleared.
