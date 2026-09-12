# Writing an addon

An addon is an ordinary Fabric mod that declares one extra entrypoint. There is no addon folder, no
jar scanner and no separate class loader: discovery is the Fabric loader's job, so dependency
versions, load order and mod metadata all work the way they already do for every other mod.

This document describes API version `1`. It is **provisional** — see
[What is published, and what is not](#what-is-published-and-what-is-not).

## The shortest addon that does something

`fabric.mod.json`:

```json
{
  "schemaVersion": 1,
  "id": "my-addon",
  "version": "1.0.0",
  "name": "My Addon",
  "environment": "client",
  "entrypoints": {
    "agalarhack": ["com.example.MyAddon"]
  },
  "depends": {
    "agalarhack": "*"
  }
}
```

The `depends` entry is what makes a missing client a clean refusal instead of a crash: the loader
declines to start and says which mod is absent, rather than letting your classes load and fail on
the first `AddonContext` they touch. It is not needed for ordering — this client asks the loader for
its addon entrypoints itself, so your entrypoint is called when it is ready, whatever order the mods
initialised in.

`MyAddon.java`:

```java
public class MyAddon implements AgalarHackAddon {
    @Override
    public void onAgalarHackReady(AddonContext context) {
        if (AgalarHackApi.version() != 1) {
            context.logger().warn("built against API 1, found {}; not registering",
                    AgalarHackApi.version());
            return;
        }
        context.addModule(new MyModule());
        context.addCommand(new MyCommand());
    }
}
```

`onAgalarHackReady` is called exactly once, during client startup, on the client thread. There is no
world and no player yet, so do your registering here and leave anything that needs a world to the
module's own `onEnable` and tick callbacks.

## Building against it

This client is not on a Maven repository, so depend on the jar you have:

```gradle
dependencies {
    modImplementation files("libs/agalarhack-26.2.5.jar")
}
```

Use the same Minecraft version, the same mappings and the same Java release as this project —
Minecraft 26.2, Mojang mappings, Java 25. A module compiled against different mappings will not
resolve the Minecraft types it inherits.

## What you get

`AddonContext` is the whole surface:

| Method | What it gives you |
| --- | --- |
| `id()`, `name()`, `version()` | your own mod metadata, as the loader read it |
| `logger()` | an SLF4J logger named `agalarhack-addon/<your id>`, so your output is attributable |
| `addModule(Module)` | a module that appears in the ClickGUI, the module list and the config like any other |
| `addCommand(Command)` | a command under this client's prefix |

A module you register is a first-class one: it gets a keybind setting, a HUD toggle, saved settings
and a ClickGUI entry, because it goes through the same `ModuleManager.register` every built-in module
does. Register settings in `selfSettings()` as usual.

## Rules the loader enforces

- **One turn each, in a guard.** If your addon throws, the exception is logged against your mod id,
  your registrations stop there, and the client and every other addon carry on. You will not take
  anybody down, and you will not be told nicely — check the log.
- **Names are unique.** A module name or a command name or alias that is already taken is refused
  with an `IllegalStateException`. Prefix yours if you are worried; `.addons` and the log will tell
  you which one collided.
- **Ordering is fixed.** Addons load after commands are registered and before saved settings are
  applied, so an addon module receives its saved settings on the very first launch after it is
  installed. This is load-bearing and documented at the call site in `AgalarHackClient`.

## Checking what loaded

`.addons` lists every addon the loader called, its version, how many modules and commands it
registered, and the failure text for any that threw.

## What is published, and what is not

Published: `AgalarHackApi`, `AgalarHackAddon`, `AddonContext`, and — stated plainly because it is a
real commitment — the existing `Module` and `Command` classes, which the two `add` methods take.

Not published, deliberately: the event bus, the service registry, the scan scheduler, the rotation
service, the HUD layout manager, the config codecs. Every one of those would be frozen the day an
addon touched it, and they are still moving. If you need one, say which and why — that is how
version 2 gets decided, rather than by guessing.

`AgalarHackApi.version()` is raised whenever a change could break an addon written against the
previous number; additive changes keep it. Check it and refuse to register against a number you do
not know, as in the example above — a module that half-loads is worse than one that says why it did
not.

It is a method rather than a constant for a reason worth knowing, because the obvious design is
broken: a `public static final int` is a compile-time constant, so javac would bake the value into
your addon and your own version check would compare the literal against itself and always pass. Read
it through `version()` and nothing gets inlined.

## How this is tested

The game test mod declares its own `agalarhack` entrypoint (`TestAddon`) and the
`ModuleBehaviourGameTest` `addonLoaded` scenario asserts that the real Fabric loader called it, that
its module and command are actually registered and findable, and that the loader's record names the
addon's own mod id rather than this client's. That is the same path a third-party addon takes; a unit
test with a hand-made context would only prove the hand-made context works.

**Not yet verified by hand:** no genuinely separate third-party addon jar has been built and dropped
into a mods folder. The entrypoint path is exercised, the packaging is not.
