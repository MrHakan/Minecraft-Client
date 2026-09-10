# Agalar Hack — Competitor Audit (Minecraft 26.2)

Date: 2026-09-10

This document records product/architecture observations used for the 26.2.2 improvement pass. It is an implementation roadmap, not a request to copy another client's code. The goal is to learn from mature interaction patterns while keeping Agalar Hack small, understandable and maintainable.

## Projects reviewed

### Meteor Client

Useful patterns observed:

- typed settings organized around modules rather than raw unvalidated values;
- KillAura-style combat has explicit visible/wall ranges, target filters, target priorities and attack timing controls;
- module discovery/search and active-module tracking are handled centrally;
- feature depth is usually preferred over dozens of settings that do the same thing in subtly different ways.

Reference: https://github.com/MeteorDevelopment/meteor-client

### LiquidBounce

Useful patterns observed:

- shared target-selection concepts are reused by several combat/aim modules;
- target filtering is separated from target ordering/priority;
- ClickGUI is the primary configuration surface and supports search;
- global target settings reduce duplicated combat configuration;
- categories are broad enough to separate Combat, Movement, Player, Render, World, Exploit and Misc concerns.

References:

- https://liquidbounce.net/docs/modules/overview
- https://liquidbounce.net/docs/modules/shared-settings/target
- https://liquidbounce.net/docs/global-settings/targets
- https://liquidbounce.net/docs/usage/clickgui

### Aoba Client

Useful patterns observed on its Minecraft 26.2 code line:

- native game screens/widgets are used for several management screens;
- KillAura exposes FOV, entity filters, target priority, timing and friend/NPC/invisibility filters;
- ClickGUI and settings are first-class rather than requiring chat commands for every operation;
- managers separate GUI, modules, commands and other local systems.

Reference: https://github.com/Cocolots/Aoba-Client

### Wurst 7

Useful patterns observed:

- Fabric-first installation and a long-lived modular client structure;
- GUI-first module configuration plus keybinds;
- strong emphasis on recognizable, single-purpose modules instead of hiding everything behind one giant module.

Reference: https://github.com/Wurst-Imperium/Wurst7

### Helikon

Useful patterns observed on its Minecraft 26.2 line:

- searchable ClickGUI, customizable HUD, profiles, friends, waypoints and macros are treated as core local systems;
- modules document client/server authority and avoid pretending client-only changes guarantee server-side behavior;
- the project keeps explicit porting/testing notes for mapping-sensitive render, input and lifecycle hooks;
- local systems are kept independent so features such as HUD, profiles and friends do not need to be hard-coded into individual modules.

Reference: https://github.com/erivgout/helikon

## Gaps found in Agalar Hack before this pass

1. **Raw settings map** — numeric values had no declared bounds and there was no reusable choice/boolean metadata.
2. **No settings discovery UI** — users had to know setting names before using `.set`.
3. **No ClickGUI** — module management was almost entirely command-driven.
4. **Aura was too primitive** — nearest entity plus a fixed tick delay, with no friend, FOV, wall-range or priority concepts.
5. **No friend system** — combat automation could not protect known players.
6. **Movement modules could overwrite external state** — Flight and Step restored hard-coded defaults rather than captured values.
7. **Speed was insufficiently bounded** — repeated horizontal multiplication had no explicit maximum velocity.
8. **NoFall produced unnecessary packet traffic** — it sent the grounded status repeatedly during a fall.
9. **HUD was hard-coded per information module** — every new HUD utility required editing `Hud.java`.
10. **Module lookup was linear and registry errors were not isolated** — one bad module tick could affect the whole update loop.
11. **CI used deprecated/older Node-runtime actions.**

## Changes implemented in 26.2.2

### Core/settings

- added typed setting metadata: Boolean, Number, Choice and String;
- added numeric min/max validation and finite-number checks;
- added persisted-config sanitization/clamping for old or manually edited configs;
- retained backward-compatible on-disk module setting structure;
- added `.settings <module>` to inspect values, ranges and descriptions;
- added searchable `.modules [query]` discovery;
- indexed modules by normalized name and added duplicate-name protection;
- isolated per-module runtime failures and hardened bulk disable/startup recovery.

### GUI

- added `.gui` / `.clickgui`;
- added a searchable, paged native-widget module browser;
- added direct ON/OFF toggles;
- added a generic typed settings editor with Boolean/Choice controls and validated text entry for numeric/text settings.

### Combat

- overhauled Aura with visible range vs wall range, FOV, players/mobs, friend protection, invisible filtering, pause-on-use, click-only mode, vanilla cooldown/custom delay and target priority;
- added persistent local friends plus `.friend add/remove/list/clear`;
- added TriggerBot as a manual-aim alternative that only attacks the entity under the vanilla crosshair;
- deliberately did **not** add rotation spoofing, packet-obfuscation or anti-cheat bypass presets to the base architecture.

### Movement

- Flight snapshots/restores previous flight ability state and speed;
- Step snapshots/restores the actual previous step-height base value;
- Speed has bounded multiplier/max-speed settings and optional fluid/sneak behavior;
- Jesus has separate water/lava toggles and bounded vertical speed;
- Sprint respects vanilla sprint eligibility and has item-use/sneak controls;
- NoFall sends one status packet per qualifying fall instead of one per tick.

### Render/HUD

- introduced a generic `HudInfoProvider` contract;
- Coordinates now exposes precision and facing options;
- added Durability HUD with name/percentage/warning threshold settings;
- Fullbright preserves Night Vision that existed before the module supplied its own effect.

### Build/reliability

- config writes are replace-safe and malformed JSON is backed up;
- stale CI runs are cancelled when superseded;
- Actions runtimes were moved forward to current Node-24-era versions.

## What should come next

Priority is based on user impact divided by complexity/maintenance cost.

### P0 — verify before expanding again

- CI build must pass on Java 25 / Minecraft 26.2 after every mapping-sensitive change;
- run a live dev-client smoke test for ClickGUI, toggles, settings persistence, friends, Aura, Flight restore and HUD stacking;
- add small unit tests around setting parsing/sanitization where Minecraft runtime objects are not required.

### P1 — high-value local systems

1. **Dedicated ClickGUI keybind** (default Right Shift) with a configurable global bind.
2. **Profiles** — named local configurations and optional per-server automatic profile selection.
3. **HUD editor** — position/order/visibility for info providers instead of one fixed bottom-left stack.
4. **Global target policy** — reusable entity/friend/passive/hostile filters shared by Aura, TriggerBot and future visual target modules.
5. **Notifications/toasts** — replace important console-only failures with unobtrusive in-game feedback.

### P2 — modules that add real utility without requiring fragile protocol tricks

- AutoTool;
- AutoEat;
- AutoFish;
- AutoReconnect;
- BetterChat / timestamps;
- Freecam;
- StorageESP / EntityESP;
- Trajectories;
- AutoWalk / AutoParkour;
- waypoint HUD;
- TargetHUD / ReachDisplay.

These should be added only when the underlying render/input/inventory hooks can be tested on 26.2.

### P3 — architecture when module count grows

- split categories further into Player and World instead of putting automation in Misc;
- reusable target-selector service rather than duplicating filters in combat modules;
- reusable inventory selection service for AutoTool/AutoEat/AutoArmor;
- profile version/migration schema;
- formal event bus only when enough modules need events other than client tick/HUD extraction to justify it.

## Things not worth adding just to match a feature count

- duplicate variants of the same movement module with slightly different constants;
- opaque anti-cheat-specific presets that cannot be regression-tested;
- packet spam or malformed-packet tricks;
- server-authoritative claims a client cannot guarantee;
- dozens of modules that exist only as empty toggles or one-line stubs;
- a custom rendering framework when native 26.2 widgets already solve the required GUI interaction reliably.

## Product direction

Agalar Hack should compete on **predictability, clean state restoration, discoverable settings and maintainable 26.2 compatibility**, not raw module count. A smaller set of modules that can be searched, configured, safely disabled and correctly restored is a better base for adding advanced render/player/world utilities later.
