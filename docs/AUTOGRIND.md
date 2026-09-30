# AutoGrind

`.grind survival` starts the full survival campaign. Choose a flat, empty plot in the Overworld before starting;
its southwest floor corner is two blocks east of the starting position. `.grind status` reports the
current milestone or actionable pause, `.grind stop` cancels it, and `.grind resume` continues the
same task after a pause. A new campaign in the same loaded world reuses its base location and checks
live inventory again. Disconnecting or loading an unrelated world clears the base.

| Command | Result |
| --- | --- |
| `.grind survival iron` | Bare hands → wood/stone tools → food → iron tools and full armor, shield, bucket, torches → 5×5 starter house and surplus chest. |
| `.grind survival diamond` | Iron campaign, then diamond tools/full armor, a larger food reserve, and a 7×7 storage annex with three categorized chests and a connecting walkway. |
| `.grind survival max` | Diamond campaign, then a Nether portal, ancient debris, upgrade-template duplication, full Netherite tools/armor, an enchanting area with 15 bookshelves, and level-30 vanilla enchanting offers. |
| `.grind survival` | Same as `max`. |
| `.grind run <item> [count]` | Execute one supported item goal, including its tools, stations, crafting, smelting or smithing prerequisites. |
| `.grind <item> [count]` / `.grind plan <item> [count]` | Read-only inventory plan. |
| `.grind baritone on` / `.grind baritone off` | Enable/disable optional mining and travel for individual goals. Campaigns enable it automatically. |

## Progression and world interactions

`SurvivalProgression` defines milestones rather than one stale inventory estimate. Each item
milestone compiles a fresh `GrindExecutionPlan` from live inventory. Carried higher-tier equipment
satisfies earlier-tier campaign milestones, and equipment tasks wear stronger armor and equip the
shield through `InventoryService`. Worn diamond armor is safely moved into inventory before smithing.
A resource goal can rebuild its prerequisites if its pickaxe breaks, with bounded retries.

Food tasks hunt loaded adult cows/pigs/sheep/chickens, excluding named animals. They use vanilla
attack cooldown, line of sight and `RotationService`, and wait for real drops. Carried wheat can be
made into bread; meat is cooked in a furnace. Campaign upkeep eats ordinary food through the shared
inventory lease, excluding golden apples and poisonous foods. Low health, fire or exhausted food
pauses work for recovery. Smelting uses eight-item batches per coal and may retain surplus output.

Buildings use ordered, exact world placements: a raised floor, walls, roof, door, lighting and
fixtures. The annex contains three separated single chests for building materials, minerals and
other surplus. Sorting keeps equipment, templates, food and useful supplies carried and preserves
custom-named items. Transfers use captured menu ownership, one click per tick and cursor recovery;
full chests or blocked plots pause without erasing terrain. Local gathering excludes recorded campaign placements; Baritone cobblestone requests target natural stone/deepslate, and portal/enchanting obsidian is collected together before building the portal. A build requests small batches of
materials rather than filling inventory with an entire house at once.

The max campaign gathers spare diamonds for copying templates before entering the Nether. **The
first Netherite Upgrade template must be looted from a bastion by the player.** AutoGrind pauses
there and duplicates the remaining templates using the vanilla seven-diamond/netherrack recipe.
Smithing uses the real template/base/ingot menu slots. The Nether portal is built and lit with
vanilla interactions, and campaign-controlled dimension transitions preserve the runner. Other
world changes, death and disconnect cancel work.

Enchanting builds a table and 15 shelves with a clear one-block gap. Each unenchanted Netherite
piece uses the third vanilla offer after its advertised level/lapis requirements arrive. If XP is
insufficient, the campaign mines coal in small batches and stores surplus before trying again.
Enchantment rolls are vanilla RNG: this provides level-30 enchanted equipment, not a guarantee of
specific maximum-level enchantments, Mending, or a complete villager/anvil optimization pipeline.

## Optional Baritone

Install a Baritone build compatible with your Minecraft version separately. It is an optional
runtime API, not a bundled dependency. Raw gathering delegates to its mining process when enabled;
live AutoGrind inventory totals determine completion, and only the owned mining process is stopped.
Quantity zero lets AutoGrind own the stopping condition across mixed log/stone variants. Task tick
budgets still bound every attempt. Wood-specific door recipes count exact oak inputs without
counting the same stacks again through their generic aliases.

Travel uses the documented custom-goal API and clear standing positions near stations/build cells.
AutoGrind relinquishes only its custom goal; it never sends Baritone commands or coordinates to
server chat, and it refuses to take over an already-active mining/pathing/custom-goal process.
Lost portable crafting/furnace stations can be recreated near the player after a mining journey.

Without Baritone, resource scans remain bounded to loaded chunks within 6 horizontal / 4 vertical
blocks and use `ScannerService`'s shared budget. Direct interactions stay within vanilla reach.
Outside reach, occluded interactions, uncollected drops and absent local resources pause for manual
movement and `.grind resume`. A compatible Baritone installation is required for continuous travel
and remote mining. Hunting only targets loaded animals; unseen animals/loot are not inferred.

## Validation and limits

Unit tests cover all campaign item prerequisite chains, gear tiers, crafting layouts, smithing
ordering, building/portal geometry, storage reserves, station slot arithmetic and owned Baritone
process cancellation. Real-client scenarios cover ordinary resource breaking and pickup, crafting,
station placement, cooking, diamond/iron equipment recipes, Netherite smithing, survival startup and
stop, exact building placements, chest transfers and automatic equipment.

The test runtime has no Baritone installed. Its bridge is tested against API-shaped stubs, while
absence is exercised in the real client. A complete randomly generated survival world from empty
inventory through the Nether and all enchants is not a deterministic integration-test fixture.
Baritone availability, terrain access, animal/resource availability and server interaction rules
can require manual intervention. Build footprints need clear supports; existing blocks are not
removed. Full chests, unrelated station contents, and server-rejected placements remain resumable
pauses or specific failures. Eight-item smelting batches can exceed the requested minimum.
