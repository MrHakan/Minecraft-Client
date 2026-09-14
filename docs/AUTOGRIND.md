# AutoGrind and the Baritone bridge

AutoGrind turns a deterministic resource plan into a bounded client action. The command keeps its
short form plan-only for safety and compatibility; execution is an explicit opt-in.

## What is implemented

| Piece | What it is | Evidence |
| --- | --- | --- |
| TaskRunner | Ordered, resumable tasks with satisfied checks, per-task budgets and cancellation. | TaskRunnerTest |
| CraftingPlan | Deterministic gather/craft dependency expansion with held-stock subtraction, cycle checks, leftovers and furnace-load fuel accounting. | CraftingPlanTest |
| GrindBook | Generic early-game names plus real Baritone block-name mappings for logs, cobblestone, coal and raw iron. | GrindBookTest |
| BaritoneBridge | Optional reflective calls to the inspected Baritone 26.2 API: path goals and IMineProcess.mineByName(int, String...). No chat commands are sent. | BaritoneBridgeTest; installed runtime is manual |
| GrindExecutor | Runs raw gather steps through Baritone, watches the player's actual inventory, and cancels on completion, failure or lifecycle change. | ModuleBehaviourGameTest absence/refusal path; installed runtime is manual |
| .grind run item count | Starts a raw-resource run when Baritone is present. | Client command path |
| .grind stop / .grind status | Explicit cancellation and readable state/failure reporting. | Client command path |
| .grind item count / .grind plan | Preserved plan-only view for crafted goals and compatibility. | ModuleBehaviourGameTest.grindPlan |

The bridge was not written from a guessed signature. The official Baritone 26.2 API source was
inspected at
https://github.com/cabaletta/baritone/blob/26.2/src/api/java/baritone/api/IBaritone.java
and
https://github.com/cabaletta/baritone/blob/26.2/src/api/java/baritone/api/process/IMineProcess.java.
The production client still has no compile-time Baritone dependency, so an installed Baritone JAR
must be tested separately.

## Execution boundary

Only plans containing raw GATHER steps whose generic names have a Baritone block mapping execute:
log, cobblestone, coal and raw_iron. The executor passes the missing quantity (not an unbounded
request), waits for the corresponding generic inventory count, and gives up after a bounded idle grace
or task budget when Baritone no longer reports mining/pathing.

Crafted and smelted chains (planks, sticks, tables, furnaces, pickaxes and iron ingots) remain
plan-only. Baritone is a movement/mining process, not a crafting API; pretending that a mine request
will craft a pickaxe would be unsafe. Extending this boundary requires a vanilla crafting executor
with menu/input ownership, peer-inventory contention tests and real 26.2 game-test evidence.

Lifecycle is explicit:

- a run is bound to the exact LocalPlayer and ClientLevel that started it;
- disconnect, world change, player replacement or death cancels the owned mining process;
- stopping AutoGrind cancels only the mining process, not an unrelated custom Baritone goal;
- .grind run never sends a public chat command or leaks coordinates.

## Remaining acceptance

Unit tests prove the reflection names and argument shape against a source-compatible stub; they do not
prove a remapped production JAR. Manual acceptance with a real Baritone 26.2 installation must confirm:

1. .goto starts and .goto stop cancels a real path;
2. .grind run log 1 causes a real nearby resource run to finish and stop;
3. a missing target, an unavailable Baritone process and a failed path leave no stuck input or process;
4. settings/keybind persistence and addon installation remain intact.

No anti-cheat bypass, packet flooding, malformed packet or hidden server information was added.
