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
5. Launch the Fabric profile — you should see **Agalar Hack 26.2.2** in the top-left of the HUD.

## ClickGUI

Type **`.gui`** (or `.clickgui`) to open the searchable ClickGUI. The interface uses Minecraft's native widgets, supports small resolutions, provides paged module browsing, direct ON/OFF toggles and a generic settings editor generated from each module's typed setting metadata.

## Commands

The command prefix is `.` (typed in chat).

| Command | Aliases | Description |
| --- | --- | --- |
| `.help` | `.h`, `.?` | Shows the list of commands |
| `.modules [query]` | `.list`, `.mods` | Lists modules or searches names, categories, descriptions and settings |
| `.settings <module>` | `.cfg`, `.config` | Shows editable settings, current values, descriptions and allowed ranges |
| `.toggle <module>` | `.t` | Toggles a module on or off |
| `.bind <module> <key\|none>` | `.b` | Binds a module to a key (e.g. `r`, `g`, `f4`, `left.shift`) |
| `.set <module> <setting> <value>` | `.setting` | Changes a setting through type/range validation |
| `.friend add\|remove\|list\|clear [name]` | `.friends` | Manages the persistent local friend list used by combat filters |
| `.gui` | `.clickgui` | Opens the searchable module/settings interface |
| `.panic` | `.disableall`, `.off` | Immediately disables every active module |

## Modules

| Category | Module | Important settings | What it does |
| --- | --- | --- | --- |
| Combat | **Aura** | `range`, `wallsRange`, `fov`, `players`, `mobs`, `ignoreFriends`, `ignoreInvisible`, `pauseOnUse`, `onlyOnClick`, `vanillaCooldown`, `delay`, `priority` | Selects the best valid target using range/FOV/friend filters and attacks with vanilla or custom timing |
| Combat | **TriggerBot** | `players`, `mobs`, `ignoreFriends`, `ignoreInvisible`, `pauseOnUse`, `onlyOnClick`, `vanillaCooldown`, `delay` | Attacks only the valid entity under the vanilla crosshair; no target searching or forced rotation |
| Movement | **Speed** | `multiplier`, `maxSpeed`, `inFluids`, `whileSneaking` | Boosts ground movement while enforcing a configurable horizontal speed cap |
| Movement | **Flight** | `speed` | Grants creative-style flight and restores the exact previous flight abilities/speed when disabled |
| Movement | **Jesus** | `water`, `lava`, `verticalSpeed` | Adds configurable buoyancy in selected fluids; sneaking allows normal diving |
| Movement | **Sprint** | `whileUsing`, `whileSneaking` | Automatically sprints while respecting vanilla sprint eligibility |
| Movement | **Step** | `height` | Raises step height and restores the exact previous attribute value when disabled |
| Movement | **NoFall** | `threshold` | Sends the grounded status once per qualifying fall instead of spamming it every tick |
| Render | **Fullbright** | — | Client-side night vision while preserving a pre-existing Night Vision effect |
| Render | **Coordinates** | `precision`, `facing` | Shows live XYZ and optional cardinal facing in the HUD |
| Render | **Durability** | `showName`, `showPercent`, `warningPercent` | Shows remaining main-hand item durability and warns when it becomes low |

Use `.settings <module>` for the exact allowed values and ranges. Numeric settings are bounded and persisted values are sanitized on load, so malformed/manual configs cannot inject `NaN`, infinity or extreme out-of-range values into movement/combat logic.

## Configuration

Enabled modules, keybinds and per-module settings are saved to `config/agalarhack.json`. The friend list is stored separately in `config/agalarhack-friends.json`.

Config writes use a replace-safe temporary file. If malformed module JSON is detected, the broken file is preserved as `agalarhack.json.broken-*` before defaults are rebuilt. Multi-module operations such as `.panic` batch persistence into one write.

## 26.2.2 improvements

- Added a searchable, paged **ClickGUI** and generic typed settings editor.
- Reworked settings into typed metadata with number bounds, choice validation, descriptions and config sanitization.
- Added persistent **friends** and friend-aware combat targeting.
- Overhauled **Aura** with FOV, visible/wall range separation, target priority, invisible/friend filters, item-use/click gating and vanilla attack cooldown support.
- Added **TriggerBot** as a predictable manual-aim combat alternative.
- Added **Durability** and generalized HUD info rendering so future HUD modules do not require hard-coded renderer branches.
- Improved **Coordinates** with configurable precision and facing direction.
- Fixed **Flight** and **Step** state restoration so they no longer overwrite abilities/attributes supplied by vanilla game modes or other mods.
- Bounded **Speed** and added a horizontal speed cap plus fluid/sneak controls.
- Made **Jesus** water/lava behavior and vertical velocity configurable.
- Reduced **NoFall** from repeated per-tick packets to one status packet per qualifying fall.
- Made **Sprint** use vanilla sprint eligibility and configurable item-use/sneak behavior.
- Added module-name indexing, searchable module discovery and per-module tick failure isolation.
- Hardened `.panic` and startup restoration so one broken module cannot leave the rest half-enabled.
- Modernized GitHub Actions runtimes and cancel stale CI runs superseded by newer commits.

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

Two GitHub Actions workflows handle builds and releases:

- **[`CI`](.github/workflows/build.yml)** runs on every push to `main` and every pull request. It builds the mod, uploads the jar as an artifact, cancels superseded runs for the same PR/branch and — for `main` pushes — publishes a rolling prerelease tagged with the workflow run ID.
- **[`Release`](.github/workflows/release.yml)** runs when you push a `v*` tag. It builds the mod and publishes a GitHub Release with generated changelog notes and the jar attached.

To cut an official release:

1. Bump `mod_version` in `gradle.properties` and `AgalarHackClient.VERSION`.
2. Commit and push.
3. Tag the commit and push the tag, for example:

   ```sh
   git tag v26.2.2
   git push origin v26.2.2
   ```

> Both workflows need repo → **Settings → Actions → Workflow permissions** set to **Read and write**. The workflow already requests `contents: write`, but the repository-level setting must also allow it.

## Branches

- **`main`** – current Minecraft 26.2 Fabric client.
- **`og`** – the original 1.12.2 Forge client, kept for historical reasons.

## Credits

- **MrHakan** — author
- Built on [Fabric](https://fabricmc.net/) using Mojang's official mappings.

_Use responsibly. This mod is intended for anarchy servers and singleplayer testing — using client-side mods on servers that forbid them can get you banned._
