# Changelog

Releases are numbered **2.x.yy**: `x` is the major version and `yy` a two-digit minor update (see
[Versioning](README.md#versioning)). Every version needs a section here before it can be merged; CI
publishes the section as the GitHub release notes.

## [2.0.07] - 2026-10-09

### Added
- The survival campaign picks its build plot. Starting on rough ground no longer means building
  into a hill or a tree and pausing at the first blocked placement: the campaign looks up to 16
  blocks around the start, once, for the nearest clear, level plot that fits every building of the
  chosen tier, and keeps the old spot two blocks east of the start whenever that already fits. When
  nothing fits it pauses straight away and says so; `.grind resume` builds at the old spot anyway.
  The search reads each column once from loaded chunks and took at most 10 ms in the game tests,
  where a test arena with a pillar on every third column is refused until a clean patch is opened.

## [2.0.06] - 2026-10-09

### Added
- The survival campaign takes stored supplies back. A goal that cannot be made from what is carried
  first visits base storage chests that hold part of its recipe chain (within 64 blocks with
  Baritone, within reach without it) and takes what it needs, preferring finished items such as
  ingots over smelting carried ore. Renamed, enchanted and nearly broken stacks are left alone, two
  inventory slots stay free, and a chest that cannot be reached is skipped rather than pausing the
  campaign. A game test has a goal make an iron pickaxe in an area with no ore, from ingots and sticks
  AutoGrind had deposited earlier, beside a renamed stack it must not touch.

### Internal
- AutoGrind's inventory helpers (counting by generic name, hotbar and tool preparation, crafting
  grid clicks) moved out of `GrindExecutor` into `GrindItems`, leaving the executor at about 340
  lines of lifecycle, planning and campaign state. Same compiler-guided move as 2.0.04 and 2.0.05:
  the 225 moved lines match the originals apart from the executor prefix. No behaviour change.

## [2.0.05] - 2026-10-08

### Added
- The Module Timings developer widget now also shows the costliest world overlays per frame
  (labels and lines together, over the last 120 frames), under the existing tick costs. Like the
  tick figures, it measures only while the widget is open; otherwise an overlay frame costs one
  extra call. A game test checks the five entity overlays are each measured with 300 entities in
  view.

### Internal
- AutoGrind's Baritone travel, its crafting table and furnace bookkeeping, and its reach, sight and
  aiming checks moved out of `GrindExecutor` into three collaborators (`GrindTravel`,
  `GrindStations`, `GrindReach`), taking the executor from 814 to about 590 lines. The move was
  compiler-guided like 2.0.04's: the 210 moved lines match the originals apart from the executor
  prefix, and callers changed only in which object they ask. No behaviour change.

## [2.0.04] - 2026-10-08

### Internal
- AutoGrind's six step types (gathering, moving an offhand ingredient, placing and opening a
  station, crafting, smelting) moved out of `GrindExecutor` into their own classes, so the executor
  drops from 1,650 to about 810 lines. The move was compiler-guided: the moved code reaches the
  executor's shared state only through an explicit reference, and apart from that prefix and
  constructor formatting it is line-for-line the code it was. No behaviour change.

## [2.0.03] - 2026-10-08

### Internal
- The world overlay renderer is split by overlay family. Each module's overlay is one small class
  in `ui/overlay` (entity, block and path families), drawing primitives live in one helper, and a
  single ordered list drives labels and lines. Method bodies were moved unchanged, the draw order
  and per-module failure isolation are the same, and a unit test pins the order. No visible change.

## [2.0.02] - 2026-10-08

### Verified
- PlayerAlerts and SessionTimer are no longer marked UNTESTED. Each now has a real-client game test:
  PlayerAlerts sees a second player the client receives over the connection come into view and
  leave it, and SessionTimer is checked against measured time, resets when it is switched back on
  and honours `showSeconds`.

### Performance
- A module's keybind is parsed once per change instead of twice per tick for every module.

## [2.0.01] - 2026-10-08

### Release process
- A version is released once. CI publishes a release only when no `v<version>` tag exists, so a push
  that does not raise `mod_version` (documentation, tests) no longer produces a duplicate release.
  A pull request that would not publish one says so in a CI warning.
- Release tags are now `v2.x.yy` instead of the CI run number, and the release notes come from this
  file. CI fails a pull request whose `mod_version` has no section here.
- One release path: the separate tag-triggered `release.yml` workflow is removed. Raising
  `mod_version` and adding notes here, then merging to `main`, is how a release is cut.

## [2.0.00] - 2026-10-08

First release under the 2.x.yy scheme; the previous release was 26.2.7.

### Fixed
- The HUD branding, Control Center and update checker showed version 26.2.5 on 26.2.7. The version
  now comes from the mod's own metadata.
- The HUD editor outlined, snapped and overlap-checked Info, Target HUD and Movement Stats at fixed
  sizes rather than the size they draw.

### Performance
- Removed per-frame allocation from HUD layout lookups, HUD draw order, service lookups and the
  Module List rainbow.
- Friend lookups are constant-time instead of lower-casing the whole list per call.
- Block and item id strings are cached for the session in BlockESP, StorageESP, ItemESP and
  InventoryCleaner.
- ESP colour and fade settings are read once per pass instead of once per target.
- ESP, Tracers, Nametags, ItemESP and ProjectileESP share one entity walk per tick: an entity costs
  one scan budget unit however many of them look at it, so busy servers no longer make them
  truncate each other.

### Versioning
- Releases are numbered 2.x.yy; the build rejects any other `mod_version`.
