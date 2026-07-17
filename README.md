![GitHub all releases](https://img.shields.io/github/downloads/MrHakan/Agalar-Hack/total)

# Agalar Hack 26.2

An anarchy utility mod for Minecraft **1.21.1** (Fabric).

> Looking for the original 1.12.2 Forge client? It lives on the [`og` branch](../../tree/og).

## Requirements

- Minecraft 1.21.1
- [Fabric Loader](https://fabricmc.net/use/) 0.16+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21

## Commands

The command prefix is `.` (typed in chat):

| Command | Aliases | Description |
| --- | --- | --- |
| `.help` | `.h`, `.?` | Shows the list of commands |
| `.modules` | `.list`, `.mods` | Lists all modules and their state |
| `.toggle <module>` | `.t` | Toggles a module on or off |
| `.bind <module> <key\|none>` | `.b` | Binds a module to a key (e.g. `r`, `g`, `f4`, `left.shift`) |
| `.set <module> <setting> <value>` | `.setting` | Changes a module setting |

## Modules

- **Combat**: Aura (settings: `range`, `delay`, `players`, `mobs`)
- **Movement**: Speed (`multiplier`), Flight (`speed`), Jesus, Sprint, Step (`height`), NoFall
- **Render**: Fullbright

Enabled modules, keybinds, and settings are saved to `config/agalarhack.json`
in your Minecraft folder and restored on the next launch.

## Building from source

```
./gradlew build
```

Requires Java 21. The mod jar ends up in `build/libs/`.
