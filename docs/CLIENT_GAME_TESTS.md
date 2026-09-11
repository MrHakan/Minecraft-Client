# Client game tests

`./gradlew runClientGameTest` starts a real client, drives it, and exits with a verdict.
`tools/smoke-client.sh` wraps that for CI and adds the mixin check; it runs on every pull request.

The tests live in `src/gametest/java` and are registered through the `fabric-client-gametest`
entry point in `src/gametest/resources/fabric.mod.json`. They never ship: the source set is separate
and nothing in it ends up in the mod jar.

## Why this exists

`./gradlew build` passes whether or not the client can start. Three whole classes of bug are
invisible to it:

* a mixin target named in an annotation string — a missing one compiles fine and fails at launch;
* a null dereference during client initialisation — a crash no unit test reaches, and one this branch
  actually shipped for several commits;
* a module that throws on its first tick in a real world, because `onEnable`, `onUpdate` and every
  render path need a client, a level and a player that unit tests do not have.

## What runs

| Test | What it does |
| --- | --- |
| `ClientScreensGameTest` | Opens every screen the mod can show — the ClickGUI, the HUD editor, profiles, themes, the target policy, the colour picker, and each module's settings, actions and keybind screens — against the title screen, where there is no player and no level. |
| `ModuleLifecycleGameTest` | Creates a flat, fixed-seed world, builds a scene around the player, then enables each module in turn, ticks it, and disables it. |
| `SwallowedFailureGameTest` | Reads the log the run just wrote and fails if the mod caught and logged a failure anywhere in it. |

`TestScene` puts a chest, a trapped chest, an ender chest, a barrel, a shulker box, a diamond ore, a
two-deep obsidian hole, a zombie, a dropped item and a full inventory next to the player. An empty
world is a weak test: a scanner that throws the moment it finds a chest passes happily in a world
with no chests, and "it ran without throwing" collapses to "its inner loop never executed".

## How failures are detected

Mostly they are not caught here, because the mod already catches them — and that is the point.
`ModuleManager.tick` force-disables a module whose tick throws, and `Module.setToggled` reverts one
whose `onEnable` throws. So a module that is still enabled after ticking is a module that ticked
without throwing, and `ModuleLifecycleGameTest` reads exactly that.

A module whose `onDisable` throws is invisible to that check: `setToggled` swallows it. That is why
`SwallowedFailureGameTest` exists. It greps the run's log for the `LOGGER.error` messages that sit
inside the mod's catch blocks, by literal prefix rather than by level, because the game itself logs
errors this run has no opinion about and failing on those would make the check worthless within a
week.

Both arms are known to work: injecting a throw into `Fullbright.onUpdate` fails the lifecycle test by
name, and moving the same throw to `Fullbright.onDisable` slips past it and is caught by the log
scan. A check nobody has watched fail is a check nobody should trust.

## Machine speed must not change the answer

The SafeWalk and Parkour scenarios dig a pit and walk into it, and the first version dug straight
through a superflat world's four blocks of terrain into the void. It passed locally four times in a
row, because the player was still falling when the sampling window closed, and failed on a slower CI
runner where they fell forty blocks and died. The scenario then reported that SafeWalk had let the
player fall - a true statement about a scene that no longer meant anything.

The pit has a floor now, so the fall is exactly three blocks on any machine, and the control walk is
checked for falling *too far* as well as far enough: a drop deeper than the pit means the scene is
broken, not the module, and the failure says so. Each walk also resets the player's health, momentum
and fall distance first, because three falls in a row otherwise accumulate into a death.

The general rule this is an instance of: **a scenario whose result depends on how fast the machine is
has no result.** Bound whatever the player is doing - give the pit a floor, cap the fall, reset the
state - rather than widening a tolerance until CI goes quiet.

## What this does *not* prove

It proves a module **runs**. It does not prove a module **works**. Nothing here asserts that AutoTotem
actually moved a totem, that Flight actually left the ground, or that an ESP drew anything where it
should have. Those need per-module assertions, and a module's `markExperimental()` flag should only be
cleared in the commit that adds the assertion justifying it — not on the strength of this file.

Two more limits worth knowing. The world is superflat, so there are no natural ores, structures or
caves beyond what `TestScene` places. And the run is single-player against an integrated server, so
nothing here exercises the network paths a busy multiplayer server produces.

## Running it locally

```bash
export JAVA_HOME=/path/to/jdk-25
./tools/smoke-client.sh            # game tests + mixin verification, as CI runs it
./gradlew runClientGameTest        # just the game tests
```

Headless machines need `xvfb` and Mesa's software rasteriser; `tools/smoke-client.sh` sets the
OpenGL overrides itself. Minecraft 26.2 needs an OpenGL 3.3 core context, so asking llvmpipe for 3.2
makes window creation fail with a misleading error.

Locally the run takes about two minutes. On a GitHub runner the whole step is **2m41s**, including
installing xvfb and downloading Minecraft's assets, against **7m11s** for the timeout-driven check it
replaced. The saving is not incidental: the client shuts itself down as soon as the tests finish, so
a slow exit is a real failure rather than the normal way the run ends.
