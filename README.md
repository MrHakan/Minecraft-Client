<div align="center">

# Agalar Hack

**An anarchy utility mod for Minecraft 1.21.1 (Fabric).**

[![Downloads](https://img.shields.io/github/downloads/MrHakan/Agalar-Hack/total?style=for-the-badge&logo=github&color=e94b4b)](https://github.com/MrHakan/Minecraft-Client/releases)
[![Latest release](https://img.shields.io/github/v/release/MrHakan/Minecraft-Client?style=for-the-badge&color=4bb1e9)](https://github.com/MrHakan/Minecraft-Client/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/MrHakan/Minecraft-Client/build.yml?style=for-the-badge&logo=gradle&label=build)](https://github.com/MrHakan/Minecraft-Client/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62b132?style=for-the-badge&logo=minecraft&logoColor=white)](https://fabricmc.net/use/)

</div>

> [!NOTE]
> Looking for the original 1.12.2 Forge client? It lives on the [`og` branch](../../tree/og).

---

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | **1.21.1** |
| [Fabric Loader](https://fabricmc.net/use/) | 0.16 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | latest for 1.21.1 |
| Java | 21 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for **Minecraft 1.21.1**.
2. Download the latest `agalarhack-*.jar` from the [Releases page](https://github.com/MrHakan/Minecraft-Client/releases).
3. Also download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1.
4. Drop both jars into your `.minecraft/mods/` folder.
5. Launch the Fabric profile — you should see **Agalar Hack 26.2** in the top-left of the HUD.

## Commands

The command prefix is `.` (typed in chat).

| Command | Aliases | Description |
| --- | --- | --- |
| `.help` | `.h`, `.?` | Shows the list of commands |
| `.modules` | `.list`, `.mods` | Lists all modules and their state |
| `.toggle <module>` | `.t` | Toggles a module on or off |
| `.bind <module> <key\|none>` | `.b` | Binds a module to a key (e.g. `r`, `g`, `f4`, `left.shift`) |
| `.set <module> <setting> <value>` | `.setting` | Changes a module setting |

## Modules

| Category | Module | Settings | What it does |
| --- | --- | --- | --- |
| Combat | **Aura** | `range`, `delay`, `players`, `mobs` | Attacks the closest valid entity in range |
| Movement | **Speed** | `multiplier` | Multiplies ground-move velocity each tick |
| Movement | **Flight** | `speed` | Grants creative-style flight at a chosen speed |
| Movement | **Jesus** | — | Lets you walk on water and lava |
| Movement | **Sprint** | — | Sprints automatically while moving forward |
| Movement | **Step** | `height` | Walk up full blocks without jumping |
| Movement | **NoFall** | — | Prevents fall damage via `OnGroundOnly` packet |
| Render | **Fullbright** | — | Client-side night vision (no gamma hack needed) |

Enabled modules, keybinds, and per-module settings are saved to
`config/agalarhack.json` in your Minecraft folder and restored on the next launch.

## Building from source

Requires Java 21.

```sh
./gradlew build
```

The mod jar ends up in `build/libs/agalarhack-<version>.jar`.

To run a dev client:

```sh
./gradlew runClient
```

## Releases

Every push to `main` builds the mod on GitHub Actions and uploads the jar as a
build artifact. To cut an official release:

1. Bump `mod_version` in `gradle.properties` and `AgalarHackClient.VERSION`.
2. Commit and push.
3. Tag the commit with a `v`-prefixed version and push the tag:

   ```sh
   git tag v26.2
   git push origin v26.2
   ```

The [`release.yml`](.github/workflows/release.yml) workflow will build the mod
and publish a GitHub Release with the jar attached automatically.

## Branches

- **`main`** – current Fabric 1.21.1 client (this is what you probably want).
- **`og`** – the original 1.12.2 Forge client, kept for historical reasons.

## Credits

- **MrHakan** — author
- Built on [Fabric](https://fabricmc.net/) and [Yarn](https://github.com/FabricMC/yarn).

_Use responsibly. This mod is intended for anarchy servers and singleplayer
testing — using client-side mods on servers that forbid them will get you banned._
