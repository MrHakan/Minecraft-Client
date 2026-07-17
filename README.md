![GitHub all releases](https://img.shields.io/github/downloads/MrHakan/Agalar-Hack/total)

# Agalar Hack 26.2

An anarchy utility mod for Minecraft 1.12.2 (Forge).

## Commands

The command prefix is `.` (typed in chat):

| Command | Aliases | Description |
| --- | --- | --- |
| `.help` | `.h`, `.?` | Shows the list of commands |
| `.modules` | `.list`, `.mods` | Lists all modules and their state |
| `.toggle <module>` | `.t` | Toggles a module on or off |
| `.bind <module> <key\|none>` | `.b` | Binds a module to a key (or unbinds it) |
| `.set <module> <setting> <value>` | `.setting` | Changes a module setting |

## Modules

- **Combat**: Aura (settings: `range`, `delay`, `players`, `mobs`)
- **Movement**: Speed (`speed`), Flight (`speed`), Jesus, Sprint, Step (`height`), NoFall
- **Render**: Fullbright

Enabled modules, keybinds, and settings are saved to `AgalarHack/agalarhack-config.json`
and restored on the next launch.

-------------------------------------------
Source installation information for modders
-------------------------------------------
> gradlew setupDecompWorkspace
