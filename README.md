<div align="center">

# Agalar Hack

**An anarchy utility mod for Minecraft 26.2 (Fabric).**

[![Downloads](https://img.shields.io/github/downloads/MrHakan/Minecraft-Client/total?style=for-the-badge&logo=github&color=e94b4b)](https://github.com/MrHakan/Minecraft-Client/releases)
[![Latest release](https://img.shields.io/github/v/release/MrHakan/Minecraft-Client?style=for-the-badge&color=4bb1e9)](https://github.com/MrHakan/Minecraft-Client/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/MrHakan/Minecraft-Client/build.yml?style=for-the-badge&logo=gradle&label=build)](https://github.com/MrHakan/Minecraft-Client/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b132?style=for-the-badge&logo=minecraft&logoColor=white)](https://fabricmc.net/use/)

</div>

> [!NOTE]
> Looking for the original 1.12.2 Forge client? It lives on the [`og` branch](../../tree/og).

---

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | **26.2** |
| [Fabric Loader](https://fabricmc.net/use/) | 0.19.3 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.157.0+26.2 or newer |
| Java | 25 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for **Minecraft 26.2**.
2. Download the latest `agalarhack-*.jar` from the [Releases page](https://github.com/MrHakan/Minecraft-Client/releases).
3. Also download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2.
4. Drop both jars into your `.minecraft/mods/` folder.
5. Launch the Fabric profile — you should see **Agalar Hack 26.2.4** in the HUD.

## Control Center / ClickGUI

Press **Right Shift** or type **`.gui`** / `.clickgui` to open the control center. The shortcut is a real Fabric key mapping and can be changed under **Options → Controls → Key Binds → Agalar Hack**.

The control center provides:

- live module search across names, categories, descriptions and setting metadata;
- category filtering and paged module browsing;
- direct module ON/OFF controls and generic typed settings editing;
- **HUD** drag-and-drop editor access;
- **Profiles** and per-server binding management;
- shared **Global Target Policy** editing.

The UI is built from Minecraft's native `Screen`, `Button` and `EditBox` widgets instead of a second custom input framework, which keeps the interface easier to maintain across game updates.

## Drag-and-drop HUD editor

Open **ClickGUI → HUD**. Four first-party widget groups can be positioned independently:

- **Branding** — client name/version;
- **Module List** — enabled module array list;
- **Info** — Coordinates, Durability and future `HudInfoProvider` modules;
- **Target HUD** — the active/recent combat target card.

The editor shows selectable preview boxes directly on screen. Hold **left mouse button** on a widget and drag it to the desired position. When released, the widget automatically anchors to the nearest corner (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`). This keeps layouts stable when resolution or GUI scale changes.

Visibility, manual anchor selection and reset controls are still available. Drag movement is kept in memory while the mouse is held and persisted once on release rather than rewriting the HUD config on every mouse event. Layout is stored in `config/agalarhack-hud.json`.

## Profiles and per-server configs

Profiles are complete named snapshots, not just lists of enabled modules. A profile stores:

- every module's enabled state, keybind and typed settings;
- the shared Global Target Policy;
- the HUD layout.

Use **ClickGUI → Profiles** or `.profile`. A saved profile can be bound to the multiplayer server you are currently connected to. On the next connection to that address, the profile is automatically loaded once for that session.

The profile lifecycle also supports:

- **Duplicate** — clone an existing profile under a new name;
- **Rename** — rename the profile while preserving server bindings that referenced it;
- **Export** — copy canonical profile JSON to the system clipboard;
- **Import** — create/replace a named profile from JSON currently in the system clipboard. Imported module/HUD maps are sanitized before storage so null entries cannot be carried into later profile loads.

Profiles are stored in `config/agalarhack-profiles/`, with address bindings in `config/agalarhack-server-profiles.json`. Missing settings in profiles created by older client versions fall back to current-version defaults instead of leaking values from the previously active profile.

## Global Target Policy

**ClickGUI → Targets** controls a shared first-pass target filter used by Aura, TriggerBot and target-aware ESP behavior. The policy can globally allow/block players and mobs, ignore friends/invisible/sleeping entities and enforce a health range. Individual modules then apply their own stricter range/FOV/timing rules on top.

This avoids target rules drifting apart between combat and render modules.

## Commands

The command prefix is `.` (typed in chat).

| Command | Aliases | Description |
| --- | --- | --- |
| `.help` | `.h`, `.?` | Shows the list of commands |
| `.modules [query]` | `.list`, `.mods` | Lists modules or searches names, categories, descriptions and settings |
| `.settings <module>` | `.cfg`, `.config` | Shows editable settings, current values, descriptions and allowed ranges |
| `.toggle <module>` | `.t` | Toggles a module on or off |
| `.bind <module> <key\|none>` | `.b` | Binds a module to a key |
| `.set <module> <setting> <value>` | `.setting` | Changes a setting through type/range validation |
| `.friend add\|remove\|list\|clear [name]` | `.friends` | Manages the persistent friend list used by target filters |
| `.profile save\|load\|delete\|list\|bind\|unbind [name]` | `.profiles` | Saves, loads, deletes, lists or binds complete profiles |
| `.profile duplicate\|rename <source> <target>` | `.profiles` | Clones or renames a profile; rename preserves server bindings |
| `.profile export\|import <name>` | `.profiles` | Copies profile JSON to / imports profile JSON from the system clipboard |
| `.gui` | `.clickgui` | Opens the control center |
| `.panic` | `.disableall`, `.off` | Immediately disables every active module |

## Modules

| Category | Module | Important settings | What it does |
| --- | --- | --- | --- |
| Combat | **Aura** | `range`, `wallsRange`, `fov`, `players`, `mobs`, `ignoreFriends`, `ignoreInvisible`, `pauseOnUse`, `onlyOnClick`, `vanillaCooldown`, `delay`, `priority` | Selects the best target allowed by global + local filters and attacks with vanilla or custom timing |
| Combat | **TriggerBot** | `players`, `mobs`, `ignoreFriends`, `ignoreInvisible`, `pauseOnUse`, `onlyOnClick`, `vanillaCooldown`, `delay` | Attacks only a valid entity under the vanilla crosshair and feeds TargetHUD |
| Misc | **AutoEat** | `hunger`, `fillToFull`, `swapBack`, `allowGoldenApples` | Selects the best allowed hotbar food, holds use, then safely restores owned input/slot state |
| Misc | **AutoReconnect** | `delaySeconds`, `maxAttempts` | Reconnects from the vanilla disconnect screen with a bounded retry schedule; leaving that screen cancels the schedule |
| Movement | **Speed** | `multiplier`, `maxSpeed`, `inFluids`, `whileSneaking` | Boosts ground movement while enforcing a horizontal speed cap |
| Movement | **Flight** | `speed` | Grants creative-style flight and restores the exact previous flight ability/speed state |
| Movement | **Jesus** | `water`, `lava`, `verticalSpeed` | Adds configurable buoyancy in selected fluids; sneaking allows normal diving |
| Movement | **Sprint** | `whileUsing`, `whileSneaking` | Automatically sprints while respecting vanilla sprint eligibility |
| Movement | **Step** | `height` | Raises step height and restores the exact previous attribute value |
| Movement | **NoFall** | `threshold` | Sends one grounded status packet per qualifying fall instead of per-tick spam |
| Render | **Fullbright** | — | Client-side night vision while preserving a pre-existing Night Vision effect |
| Render | **Coordinates** | `precision`, `facing` | Shows live XYZ and optional cardinal facing through the shared Info HUD widget |
| Render | **Durability** | `showName`, `showPercent`, `warningPercent` | Shows remaining main-hand item durability with a low-durability warning |
| Render | **ESP** | `range`, `respectTargetPolicy`, `boxes`, `tracers`, `labels`, `showDistance`, `showHealth`, RGBA | Draws target boxes/tracers and vanilla-style world labels with optional distance/health |
| Render | **Trajectories** | `steps`, `powerScale`, `gravity`, `drag`, `collision`, `landingMarker`, `markerSize`, RGB | Predicts common projectile paths, stops at block/living-entity impacts and marks the predicted hit |
| Render | **TargetHUD** | `showHealth`, `healthBar`, `showDistance`, `showArmor`, `showEquipment`, `showEffects`, `maxEffects`, `timeout` | Combat target card with health bar, gear and known status-effect icons |
| Render | **Freecam** | `speed`, `sprintMultiplier`, `smoothing`, `freezePlayer`, `bodyMarker` | Smooth detached camera movement plus a world marker showing the real player's anchored body |
| World | **AutoTool** | `swapBack`, `miningOnly`, `minDurability` | Selects the fastest correct hotbar tool, avoids near-broken tools and restores its owned slot afterwards |

AutoEat and AutoTool use a shared per-tick utility action arbiter. AutoEat has higher hotbar/use priority, so enabled automation modules do not fight over the player's selected slot in the same tick.

Use `.settings <module>` for exact allowed values and ranges. Numeric settings are bounded and persisted values are sanitized on load, so malformed/manual configs cannot inject `NaN`, infinity or extreme out-of-range values into module logic.

### TargetHUD status-effect note

TargetHUD renders potion/status-effect icons from the effects currently known by the client. Minecraft 26.2 multiplayer does not necessarily synchronize every remote living entity's complete active-effect list to tracking clients, so a remote target can legitimately show fewer/no effect icons even when the server knows it has effects. The client does not guess missing effect state.

## Configuration files

| Path | Purpose |
| --- | --- |
| `config/agalarhack.json` | Enabled states, keybinds and module settings |
| `config/agalarhack-friends.json` | Local friend list |
| `config/agalarhack-hud.json` | HUD anchors, visibility and offsets |
| `config/agalarhack-target-policy.json` | Shared target filter |
| `config/agalarhack-profiles/*.json` | Named complete snapshots |
| `config/agalarhack-server-profiles.json` | Multiplayer server → profile bindings |

Main config writes use a replace-safe temporary file. Malformed module JSON is preserved as `agalarhack.json.broken-*` before defaults are rebuilt. Bulk operations such as `.panic` batch persistence into one write.

## 26.2.4 polish

- Rebuilt the HUD editor around **direct drag-and-drop**, with nearest-corner auto anchoring and one-write-on-release persistence.
- Expanded **ESP** with optional tracers, world-space name labels, distance labels and health labels while retaining boxes and shared target-policy filtering.
- Added block and living-entity collision checks to **Trajectories**, with configurable impact/landing markers.
- Upgraded **TargetHUD** with a proportional health bar, armor/mainhand/offhand item icons and known status-effect icons.
- Added **Freecam smoothing** and a world-space body marker for the real anchored player position.
- Expanded profile lifecycle with **clipboard import/export, duplicate and rename**; rename also migrates per-server bindings and imports sanitize malformed null entries.

### 26.2.3 architecture

- Expanded ClickGUI into a searchable/category-filtered **Control Center** with HUD, Profiles and Targets pages.
- Added persistent HUD layout, complete profiles and automatic per-server profile bindings.
- Added a shared Global Target Policy and recent-target tracker.
- Added AutoEat, AutoTool and AutoReconnect with shared utility-action arbitration.
- Added ESP, Trajectories and Freecam on Minecraft 26.2's `LevelRenderEvents.COLLECT_SUBMITS` submit-node rendering path.
- Added unit coverage for utility action priority/reset behavior in addition to typed setting regression tests.

### Previous 26.2.2 foundation

- Added the initial searchable ClickGUI and rebindable Right Shift key mapping.
- Added typed setting metadata, validation and config sanitization.
- Added persistent friends and friend-aware combat filters.
- Overhauled Aura and added TriggerBot.
- Added Coordinates/Durability HUD providers.
- Corrected Flight/Step state restoration and hardened movement module limits.
- Added module indexing, failure isolation, safe `.panic`, atomic config writes and modernized CI.

## Building from source

Requires Java 25.

```sh
./gradlew build
```

The mod jar ends up in `build/libs/agalarhack-<version>.jar`.

To run a dev client:

```sh
./gradlew runClient
```

## Releases

- **[`CI`](.github/workflows/build.yml)** runs on every push to `main` and every pull request. It builds/tests the mod, uploads the jar artifact, cancels superseded runs for the same PR/branch and — on `main` pushes — publishes a rolling prerelease.
- **[`Release`](.github/workflows/release.yml)** runs for `v*` tags and publishes a GitHub Release with generated notes and the jar attached.

To cut an official release:

```sh
git tag v26.2.4
git push origin v26.2.4
```

> Both workflows need repo → **Settings → Actions → Workflow permissions** set to **Read and write**. The workflow requests `contents: write`, but the repository-level setting must also permit it.

## Branches

- **`main`** – current Minecraft 26.2 Fabric client.
- **`og`** – original 1.12.2 Forge client, retained for history.

## Credits

- **MrHakan** — author
- Built on [Fabric](https://fabricmc.net/) using Mojang's official mappings.

_Use responsibly. This mod is intended for anarchy servers and singleplayer testing — using client-side mods on servers that forbid them can get you banned._
