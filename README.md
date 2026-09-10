<div align="center">

# Agalar Hack

**An anarchy utility mod for Minecraft 26.2 (Fabric).**

[![Downloads](https://img.shields.io/github/downloads/MrHakan/Minecraft-Client/total?style=for-the-badge&logo=github&color=e94b4b)](https://github.com/MrHakan/Minecraft-Client/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/MrHakan/Minecraft-Client/build.yml?style=for-the-badge&logo=gradle&label=build)](https://github.com/MrHakan/Minecraft-Client/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b132?style=for-the-badge&logo=minecraft&logoColor=white)](https://fabricmc.net/use/)

</div>

> [!NOTE]
> The original 1.12.2 Forge client is retained on the [`og` branch](../../tree/og).

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | **26.2** |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.157.0+26.2+ |
| Java | 25 |

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Put Fabric API and `agalarhack-*.jar` in `.minecraft/mods/`.
3. Launch the Fabric profile. The HUD should report **Agalar Hack 26.2.5**.

## Control Center

Press **Right Shift** or use `.gui` / `.clickgui`.

26.2.5 introduces a rebuilt dark Control Center with a persistent category sidebar, searchable module/settings metadata, module cards, typed settings editing and matching themed screens for Profiles and Global Target Policy. HUD, Profiles and Targets remain directly accessible from the header.

## HUD editor

Open **Control Center → HUD**. Branding, Module List, Info and Target HUD are draggable independently.

The editor now provides:

- a visible 10 px placement grid;
- grid snapping plus sibling edge/center alignment snapping;
- direct drag-and-drop with one config write on release;
- nearest-corner anchoring after drop;
- live overlap detection with red conflict outlines;
- visibility, manual anchor and reset controls.

HUD layout is persisted in `config/agalarhack-hud.json`.

## Profiles and per-server configs

Profiles store complete module state/settings/keybinds, Global Target Policy and HUD layout. They support save/load/delete, per-server binding, duplicate/rename and clipboard JSON import/export. Rename migrates server bindings; imported maps are sanitized before storage.

## Commands

| Command | Description |
| --- | --- |
| `.help` | Lists commands |
| `.modules [query]` | Lists/searches modules, descriptions and settings |
| `.settings <module>` | Shows typed setting values and constraints |
| `.toggle <module>` | Toggles a module |
| `.bind <module> <key|none>` | Changes a keybind |
| `.set <module> <setting> <value>` | Changes a typed setting |
| `.friend add|remove|list|clear [name]` | Manages friends |
| `.profile save|load|delete|list|bind|unbind [name]` | Core profile lifecycle |
| `.profile duplicate|rename <source> <target>` | Copies/renames profiles |
| `.profile export|import <name>` | Clipboard profile JSON |
| `.gui` | Opens the Control Center |
| `.panic` | Disables all active modules |

## Modules

| Category | Module | Highlights |
| --- | --- | --- |
| Combat | **Aura** | Shared target policy, FOV/range/wall range, priority and vanilla/custom cooldown |
| Combat | **TriggerBot** | Crosshair-only attacks with friend/entity filters |
| Misc | **AutoEat** | Hunger-aware food choice with safe slot/use ownership |
| Misc | **AutoReconnect** | Bounded reconnect attempts from the vanilla disconnect screen |
| Movement | **Speed** | Ground speed multiplier with cap and movement conditions |
| Movement | **Flight** | Creative-style flight with exact ability restoration |
| Movement | **Jesus** | Configurable water/lava buoyancy |
| Movement | **Sprint** | Vanilla-eligibility-aware automatic sprint |
| Movement | **Step** | Configurable step height with exact restoration |
| Movement | **NoFall** | One grounded status packet per qualifying fall |
| Render | **Fullbright** | Client night vision with prior-effect preservation |
| Render | **Coordinates** | XYZ and facing HUD info |
| Render | **Durability** | Item durability HUD info |
| Render | **ESP** | Boxes/tracers/labels, friend color, scoreboard team color and distance fade |
| Render | **StorageESP** | Bounded loaded-chunk highlighting for chests, barrels, shulkers and utility storage |
| Render | **BlockESP** | Incremental budgeted scanning for ores, spawners, portals and beacons |
| Render | **Trajectories** | Vanilla-aware bow charge, charged crossbow, trident threshold, source motion and impact collision |
| Render | **TargetHUD** | Health bar, equipment and known effect icons |
| Render | **Freecam** | Detached camera with acceleration, deceleration, smoothing and body marker |
| World | **AutoTool** | Correct-tool selection with durability protection |

### ESP behavior

Friend coloring takes priority over scoreboard-team coloring. Distance fading begins at the configured `fadeStart` and approaches `minimumAlpha` near the maximum ESP range. StorageESP scans only loaded chunks and caps results. BlockESP spreads its search budget across client ticks instead of scanning the entire volume during world rendering.

### Trajectory behavior

`physics=Vanilla` uses Minecraft-aware family behavior: the vanilla bow draw-power curve, a charged-projectile requirement for crossbows, the trident minimum use threshold, family launch speeds/gravity/drag and the same source-motion addition used by `shootFromRotation`. `physics=Custom` preserves manual gravity/drag tuning from earlier versions. Collision and landing markers remain available.

### TargetHUD effect note

Minecraft 26.2 multiplayer does not always synchronize complete active-effect state for remote living entities. TargetHUD displays only effects actually known by the client and never invents missing effect data.

## Configuration

| Path | Purpose |
| --- | --- |
| `config/agalarhack.json` | Module state, keybinds and settings |
| `config/agalarhack-friends.json` | Friend list |
| `config/agalarhack-hud.json` | HUD layout |
| `config/agalarhack-target-policy.json` | Shared target filter |
| `config/agalarhack-profiles/*.json` | Named complete snapshots |
| `config/agalarhack-server-profiles.json` | Server → profile bindings |

## 26.2.5 polish

- Added friend/team-aware ESP coloring and distance fading.
- Added bounded **StorageESP** and incremental **BlockESP**.
- Added HUD grid snapping, sibling alignment snapping and overlap diagnostics.
- Reworked held-projectile prediction around vanilla bow/crossbow/trident launch behavior and source motion.
- Added Freecam acceleration/deceleration before final positional smoothing.
- Rebuilt the Control Center and settings surfaces around a shared dark UI theme.

### Earlier 26.2.x work

26.2.4 added drag-and-drop HUD positioning, ESP tracers/name labels, collision-aware Trajectories, richer TargetHUD, Freecam smoothing/body marker and full profile import/export/duplicate/rename. 26.2.3 introduced profiles/per-server configs, the HUD layout manager, shared target policy/tracker and utility/render modules. 26.2.2 established typed settings, friends, Aura/TriggerBot improvements, safer module lifecycle and modern CI.

## Foundation development branch

The shared service foundation and its current limitations are documented in
[Foundation services](docs/FOUNDATION_SERVICES.md). This branch introduces an internal event
bridge, inventory/target/rotation services, notifications, lifecycle cleanup and explicit
module-config migration while preserving the existing 26.2 rendering pipeline.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/agalarhack-<version>.jar`.

To cut an official release:

```sh
git tag v26.2.5
git push origin v26.2.5
```

`main` targets Minecraft 26.2 Fabric; `og` keeps the historical 1.12.2 Forge client.

_Use responsibly. Client-side mods can violate server rules and may result in bans where prohibited._
