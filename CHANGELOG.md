# Changelog

Releases are numbered **2.x.yy**: `x` is the major version and `yy` a two-digit minor update (see
[Versioning](README.md#versioning)). Every version needs a section here before it can be merged; CI
publishes the section as the GitHub release notes.

## [2.0.13] - 2026-10-10

### Internal
- The dedicated-server game tests now run in CI on every pull request and `main` push; the
  repository owner accepted Minecraft's server EULA for this automation. They connect to a real
  dedicated server, watch the packet counters fire, equip armour across the socket, disconnect and
  reconnect. New: the same equip with every client packet held back 150 ms, so clicks are still in
  flight when the next is due. It finishes with one helmet equipped and nothing duplicated or lost
  (400 ms against 249 ms without the lag, 19 packets held back, locally).
- The first Netherite Upgrade template stays a documented limit of AutoGrind: looting a bastion
  cannot be proved by any deterministic test world, so it is left to the player on purpose.

## [2.0.12] - 2026-10-10

### Added
- A visual acceptance list, `docs/VISUAL_ACCEPTANCE.md`, and a game test that runs its automatic
  half on every pull request. The ClickGUI is shot under Default Dark, AMOLED, Light and high
  contrast at GUI scales 1 to 4 (in a window enlarged for it), and checked for widgets outside the window, the
  theme's accent actually on screen and the light theme being the brightest. ESP's box must sit
  where the target's bounding box projects through the camera (it does, within 1-3 px), ESP labels
  and Nametags above it and centred, and the TargetHUD face patch must hold a skin. CI uploads every
  frame as the `visual-acceptance` artifact for the checks only a person can make, which the list
  marks as human approval. Findings for review so far: the Light theme leaves the search box
  black, and at GUI scale 4 the ClickGUI's quick-action labels are cut and module rows overlap
  their on/off buttons.

## [2.0.11] - 2026-10-10

### Fixed
- On a crowded server ESP, Nametags and the other entity overlays show the entities nearest to you
  again. The shared entity walk inspects at most 4,096 entities a tick, and past that it used to
  read them in arrival order, so the nearest could be skipped entirely: with 5,000 entities and the
  nearest 500 arriving last, none of ESP's 256 targets was among the true nearest 256. Past the cap
  the walk now reads the nearest first (256 of 256 in the same test). Below the cap nothing changes.
  Ordering costs about 0.3 ms a tick with 5,000 entities.

## [2.0.10] - 2026-10-10

### Fixed
- Hunting no longer leaves meat lying on the ground. After a kill it walked to the block where the
  animal died and waited there, but death drops scatter and often land two or three blocks away,
  out of pickup range; the hunt then gave up on the drop and went looking for another animal. It
  now walks to the dropped item of the kind it is hunting, within six blocks of the kill. This made
  the real-Baritone hunting test fail about half the time.

### Internal
- Crowded-server budgets are measured. A new game test times a fresh BlockESP scan while ESP and
  Nametags walk a crowd every tick: 27 ticks alone, with 1,500 entities and with 5,000, because
  entities and blocks are separate allowances in the shared scan budget. The scanner's tick cost
  grows from 0.25 ms to 0.63 ms and 1.28 ms, and past the 4,096-entity observation cap a tick
  spends exactly the cap. The test also records the cap's known cost: the walk reads the render
  list in arrival order, so when the nearest 500 of 5,000 entities arrive last, none of ESP's 256
  targets is among the true nearest. That is the next item in the plan.
- Trajectories was measured and left as it is. In the same scene its path costs 20-50 us a frame
  for ordinary throws and 70-80 us at worst (straight up, 300 steps), about a quarter of ESP with
  256 targets in view, so the planned single-query rewrite would not pay for itself.
- The real-Baritone hunting scenario starts from a known player state (full health and food, an
  empty inventory) rather than whatever the earlier scenarios left. A failure now reports health,
  food, free slots and the pause reason, and the Baritone step prints the failing assertion instead
  of only Gradle's stack trace.

## [2.0.09] - 2026-10-10

### Changed
- ESP no longer builds labels or boxes for targets outside the view. With 256 targets in front of
  the player ESP costs 18% less a frame (354 to 290 us measured locally over five 120-frame windows,
  labels 201 to 161 us, boxes 152 to 129 us); with them behind the player, 86% less (361 to 52 us).
  Tracers are still drawn to every target, on screen or not. Labels read exactly as before: the
  distance and health are formatted without `String.format`, with a fallback wherever rounding
  could differ, and a label is only skipped when the space it could occupy is out of view.

### Fixed
- A Windows checkout no longer fails the build in `ModuleDocsTest`: `docs/MODULES.md` is checked
  out with LF line endings regardless of `core.autocrlf`.

### Internal
- The Module Timings store keeps each overlay's labels and lines apart as well
  (`OverlayTimings.slowestParts`), which is how the ESP cost above was split. The widget still shows
  the totals. The overlay frame cost scenario now faces the targets before measuring and logs both
  passes, and a new check confirms ESP draws no labels or boxes for targets behind the player while
  still drawing all 256 tracers.

## [2.0.08] - 2026-10-09

### Added
- Hunting looks for animals instead of pausing at once. With no suitable animal within 64 blocks
  and Baritone available, a food goal walks a fixed round of lookouts around where it started
  (eight on a ring 48 blocks out, eight at 96, clockwise from east) and hunts the first animal that
  comes into sight; a lookout Baritone cannot reach is skipped, and after the full round, about 160
  blocks out, it pauses with a clear message. Without Baritone nothing changes. The real-Baritone
  test fetches beef from a single cow 100 blocks away that the client had not even loaded.

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
