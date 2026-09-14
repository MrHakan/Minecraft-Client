# Release hardening plan — Agalar Hack 26.2

This is the execution plan for the late-stage branch. It is deliberately evidence-driven: the
client already has 53 meaningful modules and mature shared services, so new work must close a
player-visible risk or add a behavior that can be proven end-to-end.

## Current baseline

- Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Java 25.
- 53 built-in modules, 0 `UNTESTED` badges; two fixture modules exist only during game tests.
- The latest complete CI run (#245) executes 803 JUnit tests with zero failures/errors/skips, all
  client game-test entrypoints, runtime mixin verification, addon fixture containment, generated docs
  and artifact creation. The code-bearing commit is 60bd87b; the current branch tip is a documentation
  checkpoint on the same tree.
- 46 grouped client checks across 11 entrypoints and 171 no-world screens are covered. Dedicated
  server checks exist but are opt-in behind the EULA flag and are not part of that count.
- Useful feature scope is approximately 80–85% at meaningful depth. Release confidence is a separate
  measure and remains below feature scope until visual, installation and multiplayer acceptance is
  performed.

## Priorities and work state

| Priority | Workstream | State | Next evidence or action |
| --- | --- | --- | --- |
| P0 | Configuration safety | **Substantial** | Profile, HUD and module stores are guarded; server/dimension binding maps now use bounded atomic codecs. Audit any new store with the same limits and future-schema policy. |
| P0 | Lifecycle and ownership | **Substantial** | Real world replacement, disconnect, player replacement and contention scenarios exist. Add only a concrete failing regression for remaining profile precedence, inventory, rotation or input cases. |
| P0 | CI and failure containment | **Implemented** | Keep the four inventory controls, Java 25 build, game tests, mixin verification and rolled-log scan green. Never count a skipped scenario as coverage. |
| P1 | Scanner/render performance | **Substantial** | Shared budgets, chunk-aware caches, culling and timings exist. Profile before changing; never scan the world from a render callback or trade boundedness for a continuous rescan. |
| P1 | UX/HUD | **Substantial** | Dynamic registry, editor, themes, accessibility and typed controls exist. Remaining work is primarily visual inspection at multiple GUI scales, not another widget rewrite. |
| P1 | Visual acceptance | **Manual-only** | Inspect TargetHUD skin layers, ESP/nametag geometry, waypoint beams, trajectories, rainbow phases and AMOLED/light/high-contrast themes with a real client. Pixel-difference tests only establish drawing activity. |
| P2 | AutoGrind execution | **Substantial** | Raw-resource execution is implemented through the inspected Baritone 26.2 mining API with bounded quantities, explicit stop/status and lifecycle cancellation. Recipe crafting/smelting remains plan-only until a vanilla menu executor has real contention and transition evidence. |
| P2 | Addon installation | **Partly proven** | Fixture JARs prove entrypoints, registration and failure isolation. Still manually validate a remapped production JAR in `mods/`, restart persistence, safe removal and missing-dependency behavior. |
| P2 | Dedicated multiplayer | **Opt-in/manual** | Use the existing harness only with owner-approved `-PacceptServerEula=true`; then test latency, reconnect, packet scoping and automation. Do not enable it in CI silently. |
| P3 | Documentation/release | **In progress** | Keep handover and PR facts synchronized; generate module docs from source; record evidence class for every acceptance item. |

## Conditional module candidates

Do not add modules just to increase the catalogue. AutoGrind now has a bounded raw-resource
executor; its remaining recipe crafting/smelting work is a service concern, not a reason to add a
duplicate module. Any extension must use the existing RotationService, InventoryService and action
ownership, restore all input/state on disable or world replacement, and carry real 26.2 game-test
evidence.

Other original roadmap bullets are already represented by existing modules or services (for example
Movement Stats, Ping Graph, ItemESP, ProjectileWarning, SafeWalk, AutoArmor and AutoTotem). Before
proposing another module, search the registry and generated `docs/MODULES.md`; prefer extending the
shared consumer over creating a duplicate.

## Optimization backlog

1. **Measure first.** Use the Performance/debug HUD and CI timings to identify a real hot path.
2. **Preserve bounded work.** Scanner budgets, chunk caches, frustum/distance culling and bounded
   result sets are correctness constraints, not optional tuning.
3. **Keep render callbacks cheap.** Rendering consumes immutable snapshots; discovery belongs in the
   scheduler/tick path. Avoid per-frame parsing and allocations where a measured cache can be safely
   invalidated.
4. **Keep persistence safe.** New maps/lists need byte, entry and element limits, atomic replacement,
   corruption preservation and explicit schema policy. The profile-binding batch applies this rule to
   both legacy binding files.
5. **Verify lifecycle before micro-optimizing.** A cached result must be invalidated on block updates,
   chunk unload, dimension/world change, player replacement and setting changes as appropriate.

## Evidence gates for every future batch

1. Inspect live source, tests, open PRs and CI before editing.
2. Reproduce a concrete defect or define a player-visible behavior; write the narrowest discriminating
   test first.
3. Implement one logical topic without rewriting working services.
4. Run the full Java 25 pipeline, including game tests and generated documentation.
5. Report separately what was unit tested, integrated-server tested, dedicated-server tested,
   externally packaged, visually inspected and still manual-only.
6. Update `handover.md` and PR #9 while preserving historical records. Keep the PR open/draft.

## Deliberate non-goals

No packet flooding, crash/dupe exploits, malformed packet tricks, anti-cheat bypass presets, hidden
server-authoritative claims, or silent rotations intended to defeat server checks. Turkish setting
description localization is cancelled and must not be restarted. AutoGrind executes only its proven raw-resource boundary; crafting/smelting and installed-jar acceptance remain explicitly gated by evidence; Baritone reflection must never be written from guessed signatures.
