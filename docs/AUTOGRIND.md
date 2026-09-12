# The automated grind: foundation

The goal this works towards is a client that can start a fresh world and play the opening of a run by
itself — wood, a stone pickaxe, iron, diamonds, the nether, blazes, piglin trading, and finally eyes of
ender pointing at a stronghold. That is a long chain, and most of the ways it can go wrong are quiet:
a plan that asks for wood the player is already carrying, a step that repeats because nothing recorded
that it finished, a task that hangs and takes the whole run with it.

This branch builds the part of that which can be **proven without a world**, plus one command that
shows the result to a player. It deliberately stops short of the part that walks and swings.

## What is here

| Piece | What it is | Tested by |
| --- | --- | --- |
| `TaskRunner` | The engine a long run is made of: an ordered plan of tasks, each with a satisfied-check, a tick and a budget. | `TaskRunnerTest` (15) |
| `CraftingPlan` | The arithmetic: expands a wanted item into the gather/craft steps it needs, subtracting what is already held. | `CraftingPlanTest` (17) |
| `GrindBook` | The early-game recipes, plus the mapping from real item ids onto the generic names those recipes use. | `GrindBookTest` (9) |
| `.grind <item> [count]` | Shows the plan for a real inventory. **Plans only.** | `ModuleBehaviourGameTest.grindPlan` |

None of the first three import a Minecraft type, which is the reason they can be unit tested at all.

### Two decisions worth stating

**Satisfied tasks are never started.** A run that is interrupted and resumed has to skip what it
already did rather than redo it, so `satisfied()` is checked before `tick()` and a task that is already
done costs nothing. This is what makes a plan resumable instead of merely repeatable.

**Plans are deterministic.** `CraftingPlan` resolves a recipe's ingredients in **name order**, not map
order. This is not cosmetic: `Map.of` randomises its iteration per JVM run, so an earlier version
produced a different step order on different runs and two tests passed by luck for a while. Sorting in
the production code — rather than relaxing the tests — means the same goal gives the same plan every
time, which is also what makes a plan comparable across a resume.

## What is not here, and why

**`.grind` does not gather or craft anything**, and it says so in its own output. The planner is
finished; the executor is not. A command that looked like it had started a grind and had in fact done
nothing would be worse than one that refuses, so it refuses out loud.

**There is no Baritone call.** The pathing this eventually needs (walk to a tree, mine that block) is
Baritone's job, and the client already has a reflective bridge for it. Extending that bridge to
`mine`-style calls was in scope for this batch and was **not done**, for one reason: a Baritone build
for 26.2 cannot be obtained in this environment — Maven Central returns no artifact, and the
third-party host is unreachable from here. Writing a reflective call against an API whose real shape
could not be checked would produce code that compiles, passes every test that does not have Baritone,
and fails the first time a player actually installs it. The `baritoneAbsent` scenario proves the
existing bridge refuses cleanly when Baritone is missing; it cannot prove anything about a call that
was never verified against the real jar.

So the next batch needs one of two things: a real Baritone 26.2 jar to verify against, or an in-reach
miner of our own — targeting blocks within the player's own reach needs no pathing library and can be
proven in a game test the same way every other module here is.
