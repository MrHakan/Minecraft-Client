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
Pickaxes with at most one durability point remaining do not satisfy gear goals or planning stock;
installed-Baritone mining also checks harvesting capability and Silk Touch before continuing.

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
Distant unloaded build/storage chunks are approached by column first, then a standing cell is resolved
from loaded collision data. Replaced goals are left with their new owner. A minute without movement
pauses travel with a resumable route diagnostic instead of waiting for the entire milestone budget.

Campaign inventory pressure interrupts work when fewer than two slots remain. After base storage is
built, its chest locations are reused when restarting in the same loaded world. Overworld trips
sort surplus into those chests and resume the same milestone from live stock.
Reserves include the current recipe and unfinished campaign recipes, keeping earlier debris and
copying diamonds for later milestones. Compatible chest stacks are filled before new slots are used;
obsolete unenchanted gear can be stored once a usable stronger piece exists. Named and enchanted
gear is retained. Before storage exists, in the Nether, or when protected supplies/chest capacity leave
insufficient space, free two slots manually and resume.

Stored supplies come back when a goal needs them. Before a campaign goal gathers anything it cannot
make from what is carried, it visits base storage chests that hold part of its recipe chain, nearest
first: within 64 blocks with Baritone, within reach without it. At each item of the chain carried
stock is spent first, then stored stock, and only the rest is crafted or gathered, so a stored ingot
is taken rather than smelting carried ore. What to take is recomputed from the chest's live contents
once it is open. Renamed, enchanted and nearly broken stacks are never taken, and a stack only goes
into an empty slot while two stay free. AutoGrind knows a chest's contents from the last time it had
it open, which covers everything it deposited; a registered chest it has not opened this session is
visited once, and items added by hand are noticed the next time it opens that chest. Withdrawal is a
shortcut, never a new pause: a chest that cannot be reached or opened is skipped and the goal
gathers as before. Mining coal for enchanting experience never takes coal from storage.

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
stop, exact building placements, chest transfers, storage withdrawal and automatic equipment.

The regular client suite verifies Baritone absence; bridge unit tests also use API-shaped stubs.
`tools/test-baritone.sh` runs a separate installed-Baritone 1.19.0 / Minecraft 26.2 fixture with a
checksum-pinned upstream Fabric jar. It covers a 384-block journey, actual remote iron/diamond mining, mid-goal pickaxe rebuilding, return
to an unloaded base chunk, door/chest access, deposit accounting and player replacement-goal ownership.
It runs in CI before publication and never adds Baritone to the distributable jar. Baritone 1.19.0
leaves non-daemon cache workers alive at client shutdown under 26.2; the fixture closes its pinned
executor after the world is saved/closed so Minecraft's shutdown watchdog can exit cleanly. This
test-only cleanup does not fix or establish clean shutdown for a normal installed-Baritone client. The regular client
suite also exercises pressure storage, full-chest stack merging and near-broken tool replacement. A complete randomly generated survival world from empty
inventory through the Nether and all enchants is not a deterministic integration-test fixture.
Baritone availability, terrain access, animal/resource availability and server interaction rules
can require manual intervention. Build footprints need clear supports; existing blocks are not
removed. Full chests, unrelated station contents, and server-rejected placements remain resumable
pauses or specific failures. Eight-item smelting batches can exceed the requested minimum.
