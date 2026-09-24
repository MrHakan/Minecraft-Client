# AutoGrind

AutoGrind separates the inventory-only `CraftingPlan` from Minecraft actions. `.grind <item>
[count]` and `.grind plan` remain read-only plans. `.grind run <item> [count]` builds a
`GrindExecutionPlan`, then runs its ordered work through `TaskRunner` against live inventory and
world state. Starting a new run takes a fresh inventory snapshot, so collected items and completed
crafts reduce the remaining work after a stop, death, disconnect, or world change.

## Executable GrindBook goals

Every current generic goal can be run:

| Goal | Execution |
| --- | --- |
| `log` | Find a loaded nearby log, turn with `RotationService`, break through vanilla block interaction, and wait until the drop is in inventory. |
| `planks`, `stick`, `crafting_table` | Use the 2×2 inventory recipe grid. |
| `cobblestone`, `coal`, `raw_iron` | Scan loaded chunks with the shared bounded scanner, choose a compatible carried pickaxe, mine with vanilla interaction, and confirm the matching drop. |
| `wooden_pickaxe`, `stone_pickaxe`, `iron_pickaxe` | Craft the required inputs in the 3×3 table grid; prerequisites add the needed pickaxe tier and table. |
| `furnace` | Craft the 3×3 recipe from eight cobblestone. |
| `iron_ingot` | Use a nearby or newly placed furnace, load raw iron and coal, wait for vanilla smelting, and collect the ingots. `GrindBook` represents furnace work in eight-ingot loads, so a smaller request may leave surplus ingots. |

The executor expands prerequisites without changing `CraftingPlan`. It places a carried crafting
table or furnace when the requested chain needs one; if no table is carried, it crafts one first.
Resource gathering adds a wooden pickaxe before cobblestone or coal and a stone pickaxe before raw
iron. Existing suitable tools and nearby stations are reused.

## Interactions and resumability

Resource searches cover a 6-block horizontal / 4-block vertical area in already-loaded chunks and
consume `ScannerService`'s shared per-tick block budget. AutoGrind does not load chunks or scan the
whole world. A target outside vanilla's 4.5-block interaction reach, behind an obstruction, or with
a drop outside pickup reach pauses in `NEEDS_MOVEMENT`. Status reports the coordinates and the
missing movement capability; move manually and use `.grind resume`. AutoGrind has no pathfinder.

2×2 crafting, crafting-table transfers, and furnace transfers all use
`InventoryService` / `ContainerTransferController`. They keep container ownership, one click per
tick, cursor recovery, and bounded click plans. 3×3 recipes require an available table block, and
smelting requires an available furnace block. AutoGrind only inserts into an empty furnace when it
starts a new smelt task; unrelated contents are left untouched. A task can continue after a station
screen closes: it rechecks the crafting grid, furnace slots, and inventory before queuing remaining
clicks. If cursor recovery has no empty inventory slot, it pauses and asks for space before resume.

The task runner advances only when the expected inventory total or world state is observed. It
rechecks each task before running it, releases temporary aim/tool ownership at task boundaries,
cancels on player/world replacement or death, and reports missing resources, incompatible tools,
unavailable blocks, occupied stations, or exhausted task budgets explicitly. Manual movement is the
only movement path; no guessed Baritone API or custom pathfinder is used.

## Evidence

- `GrindExecutionPlanTest` verifies the prerequisite order for wooden, stone, iron, furnace and
  ingot work, and verifies that carried tools/stations avoid unnecessary work.
- `GrindExecutionPlanTest` checks every crafting recipe's input count, grid bounds, and unique
  cells. `InventoryTransfersTest` checks player-slot mappings
  for inventory, crafting-table, and furnace menus.
- `ContainerTransferControllerTest` checks one-click pacing, captured station-menu ownership,
  interrupted-menu recovery, bounded recipe plans, and safe cursor return.
- `ModuleBehaviourGameTest` exercises real block breaking and pickup for logs, stone/cobblestone,
  coal ore, and iron ore. It crafts 2×2 and 3×3 recipes, places stations, smelts a full furnace
  batch, crafts an iron pickaxe, and verifies the existing stop/restart and manual-movement paths.

## Current limits

- Gathering only considers loaded blocks within the bounded local scan radius. The player must move
  between resource locations and resume paused tasks.
- Silk Touch is not used when it would produce a block instead of the requested raw resource.
- A non-empty furnace at the start of a smelt task is never overwritten; resolve its contents
  manually before retrying.
- `iron_ingot` uses the recipe book's eight-item furnace batch, so it can produce more than the
  requested minimum. Other crafts follow vanilla recipe yields and retain their surplus.
