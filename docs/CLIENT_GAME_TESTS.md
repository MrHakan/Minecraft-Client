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
| `ModuleBehaviourGameTest` | One scenario per module, asserting the effect a player would actually notice — a totem moved into the offhand, a player who walked, a death screen that closed, pixels that changed where an overlay should be. Every scenario first asserts that effect is *absent* with the module off. Commands and the addon entrypoint are covered the same way: `.look` turns the real view, `.goto` refuses without Baritone and puts nothing in chat, `.grind` plans a stone pickaxe around wood the player is already carrying (and says it only plans), and the game test mod declares its own `agalarhack` entrypoint so a **real addon, loaded by the real Fabric loader**, is asserted to have registered a real module and command. |
| `InventoryRecoveryGameTest` | Interrupts a real AutoArmor pickup with its settings screen and module disable; checks deferred recovery, both cursors and server-side item conservation. |
| `WorldTransitionGameTest` | Travels to the Nether through the server, observes held-look cleanup before/after real world replacement, checks Freecam restoration and closes the actual connection. |
| `InventoryContentionGameTest` | Changes the inventory through vanilla commands **between two clicks of a plan** and asserts item conservation, empty cursors and a released channel. Runs the identical change with nothing in flight first, as a control: the accounting has to be shown trustworthy before its verdict means anything. |
| `ProfileBindingGameTest` | Binds profiles to dimensions and drives a real Nether round trip, asserting each arrival loads its own bound profile. The first arrival is unbound and must change nothing, which is what makes the later ones evidence that the binding caused the load rather than the transition. |
| `ExternalAddonGameTest` | Installs two **separately packaged addon jars** and checks them from the outside: one registers a module and a command and receives settings, the other collides with a built-in module and a built-in command on purpose and has to be contained. Asserts the good one was loaded from a real `.jar` file, and first asserts the client itself is *not*, so that check separates installed jars from classpath mods rather than passing for everything. |
| `DedicatedServerGameTest` | **Opt-in; see below.** Starts a real dedicated server and connects to it: observes the packet counters actually counting, equips armour where every click crosses a socket and the server has to agree, then checks the counters reset on disconnect and that a reconnect works. |
| `SwallowedFailureGameTest` | Reads the log the run just wrote and fails if the mod caught and logged a failure anywhere in it. One exemption, by mod id: the deliberately broken addon fixture, whose failure is *asserted to happen* rather than merely ignored. |

## The dedicated-server scenarios are opt-in

A dedicated server will not start until Minecraft's server EULA is accepted, and the harness
recreates its run directory on every run, so an accepted `eula.txt` cannot be checked in — it has to
be written by automation while the test runs. Agreeing to [Mojang's
EULA](https://aka.ms/MinecraftEULA) is the repository owner's decision rather than a test's, so it
is off by default:

```sh
AGALARHACK_ACCEPT_SERVER_EULA=true ./tools/smoke-client.sh   # or: -PacceptServerEula=true
```

Without it `DedicatedServerGameTest` logs a warning naming exactly what it skipped and asserts
nothing. **A skipped run is not evidence**, so do not count those scenarios when describing coverage
unless the flag was actually set. The environment variable is read by the shell script and passed to
Gradle as a property, because a Gradle daemon started before the variable was exported would not see
it.

What the dedicated-server run adds over the rest of the suite: the `Connection` mixins are the only
ones whose handlers were verified *applied* to the bytecode without ever being seen to *fire*, and
they cannot fire meaningfully without a connection. It is still a single player on loopback — the
code paths a remote connection uses, not behaviour on a busy public server.

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

## Render modules, without reference images

An overlay leaves nothing behind to assert on, so the evidence has to be the picture — and there are
no stored reference images here, because a screenshot from a software rasteriser is not reproducible
across Mesa builds, driver versions or window sizes, and a test that needs regenerating whenever the
runner image changes stops being read.

Each render scenario measures the scene against itself instead:

1. a screenshot with the module off;
2. **the same length of time later**, a second one, still off — the difference between these two is
   the noise floor, how much the scene changes on its own;
3. the module on, the same length of time again, and a third.

The verdict is the ratio: the on-frame must differ from the off-frame by several times the noise the
scene produces by itself. An absolute floor only rules out a handful of stray pixels; it is the ratio
that carries the claim. If the scene will not hold still, noise and signal come out alike and the
scenario reports that it has proven **nothing** rather than passing or failing the module.

Three ways to get this wrong, all of them found the hard way here:

* **Unequal windows.** Measuring noise over twenty ticks and signal over a hundred and twenty reads
  two minutes of cloud drift as a module's work. Both windows are the same sixty ticks, always.
* **An absolute threshold.** A floor calibrated on HoleESP's filled box rejects a genuinely drawn
  one-pixel tracer line; a ceiling on noise rejects ItemESP at 1822 changed pixels against 210.
* **Measuring the scene instead of the module.** A scanning module fills its results on a shared
  budget, and an empty result set produces a frame identical to the control. `drawsSomething` takes
  an optional guard on whether the module had anything to draw when the frame was taken, so that
  reports itself instead of being blamed on the module. The guard is *read*, never waited on —
  waiting would make the signal window longer than the noise window, which is the first mistake
  again.

## The world has to hold still

Superflat and empty is not the same as still. Two things move in it on their own, and both were found
by a render scenario reporting a signal of zero or a noise floor it could not see past:

* **Random block ticks.** Grass dies when it is roofed over, and every scanning module restarts its
  sweep when a block near it changes — correctly, since a new light source changes a whole
  neighbourhood. A sealed box built to make somewhere dark for SpawnESP therefore kept emptying its
  own result set. The run now sets `randomTickSpeed` to zero, and the box has a stone floor.
* **Status effects.** A popped totem leaves regeneration and absorption running for forty-five
  seconds, and their particles drift through the camera for every scenario that follows. The totem
  scenario clears them when it is done.

Entities are the obvious third, and each render scenario already discards everything that is not the
player before it measures.

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

Lifecycle checks prove callbacks run without detected failures. The separate behaviour scenarios
also assert player-visible effects (including actual AutoTotem/AutoArmor transfers and render activity).
Neither proves general correctness: scene assertions cover their explicit controls and conditions only.
Pixel differences do not establish face UVs, box placement or trajectory accuracy. The 0 UNTESTED count
includes 22 baseline exemptions; do not interpret it as 53 independently comprehensive scenarios.

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


## Regression controls and reproducible counts

CI first runs `tools/inventory-regression-control.sh`: it temporarily restores the old release predicate
and removes the atomic click-budget guards. It requires four named unit assertions to fail (open-screen
recovery, repeated/preempting atomics, deposit/release and recovery/release), then restores exact source
bytes. The previous report is deleted first; a missing new report fails the control, so compilation
failure can never reuse a prior regression result.
The normal build follows, and `tools/summarize-tests.py` reports observed totals from JUnit XML (archived
with the client log). This control does not bypass the full game suite or whitelist swallowed failures.
There are 42 grouped scenario checks: 38 direct behaviour scenario calls (excluding `quietFrames`
setup), inventory interruption, dimension replacement, disconnect and external addon packaging.
SafeWalk/Parkour and Tracers/Nametags are grouped; this is not an assertion or log-line count.
Three dedicated-server checks are excluded while the EULA gate is off. Eight entrypoints include
screen, lifecycle and swallowed-failure gates; module names and flag mappings remain unchanged. Worlds are integrated-server worlds, including the
real dimension/disconnect checks. Restarted external addons and dedicated-server latency remain unverified.
