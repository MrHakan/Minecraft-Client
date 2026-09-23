# AutoGrind

AutoGrind keeps planning and Minecraft actions separate. `.grind <item> [count]` and `.grind plan`
show a deterministic `CraftingPlan`; `.grind run` opts into the executor.

## Implemented execution slice

`GrindExecutor` currently executes raw log gathering only. It asks `ScannerService` to inspect a
bounded 6-block horizontal / 4-block vertical area of already-loaded chunks, using the shared scan
budget. It chooses the nearest log within vanilla's 4.5-block interaction range, requests a normal
client rotation through `RotationService`, verifies the ray hit after turning, then uses
`MultiPlayerGameMode` block breaking. The task advances only after `InventoryService` observes the
drop in the player's inventory.

If a matching log is found outside direct reach or behind a block, TaskRunner pauses in
`NEEDS_MOVEMENT` and `.grind status` reports the target coordinates and that movement automation is
unavailable. After breaking a log, the task waits for its drop; if it is still not in the inventory
after 60 ticks, it pauses with the drop coordinates. Move close enough to collect the drop and run
`.grind resume`. Pauses release temporary rotation ownership. There is no pathfinder connected to
AutoGrind; the existing Baritone bridge remains an optional `.goto` integration, not a claim that
grind can path or mine remotely.

The task's goal is an absolute inventory total calculated from the plan's initial inventory
snapshot. Stopping and starting `.grind run log N` again plans against the live inventory, so drops
already collected reduce the remaining work. Disconnect, world replacement, player replacement,
death, or explicit stop cancels the active block interaction and releases rotation ownership.

## Current boundaries

- `.grind run log [count]` is executable; other raw resources and every craft/smelt chain remain
  plan-only and are refused before any world action.
- Resource scans never load chunks and share scanner budgets with module scanners.
- The executor does not click containers, craft, smelt, or implement pathfinding.
- `TaskRunner` has per-task time budgets, skips already-satisfied tasks, pauses for movement, and
  reports task failures.

## Evidence

- `TaskRunnerTest` verifies task order, satisfied-task skipping, bounded failure, cancellation, and
  movement pause/resume. `ScanSchedulerTest` verifies an AutoGrind query consumes only the remaining
  shared per-tick scan budget.
- `ModuleBehaviourGameTest.grindExecutesNearbyLogs` starts with one oak log in the offhand, breaks
  two real nearby oak logs through vanilla interaction, confirms a drop pause while the item remains
  out of pickup reach, moves onto the real dropped item, and resumes. It then interrupts and restarts
  from the updated inventory count before collecting the next drop. It also checks turning from an
  intentionally incorrect view, temporary aim restoration, and the `NEEDS_MOVEMENT`/manual-resume
  path for a farther log.
- `ModuleBehaviourGameTest.grindPlan` continues to prove the planner counts carried wood and that an
  unsupported gather step is refused honestly.

Crafting through 2x2/3x3 menus and furnace smelting should be added only through the existing
`InventoryService` / `ContainerTransferController` ownership rules and with game-test evidence.
