# Release hardening plan — Agalar Hack 26.2

This is the execution plan for the late-stage branch. It is deliberately evidence-driven: the
client already has 56 catalogue modules and mature shared services, so new work must close a
player-visible risk or add a behavior that can be proven end-to-end.

## Current baseline

- Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Java 25.
- 56 built-in modules across six categories, with 2 `UNTESTED` badges; two fixture modules exist only during game tests.
- The code-bearing batch is verified at commit `ff226f8` (CI run `35861603084`): Java 25 build, 826
  unit tests with no failures/errors/skips, inventory regression controls, client game tests,
  runtime mixin verification, rolled-log scan, artifacts and all three production addon markers passed.
  This documentation-only reconciliation receives its own full main-branch CI run.
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
| P2 | AutoGrind execution | **Full current GrindBook executor implemented; this branch's full CI is pending** | `.grind run` now covers the current raw resources, 2×2/3×3 recipes, table/furnace placement and iron smelting through TaskRunner and the shared inventory transfer channel. Local client game tests exercise real block drops, crafting, station use, resumability and manual movement. No pathfinding claim is made. |
| P2 | Addon installation | **Three-stage production test passed** | CI installed the 26.2 production `jar` with a separately packaged addon, saved addon and built-in settings, restarted with the addon present, removed it, then verified the base client config still loaded. All three stage markers passed in run 35861603084. The monitor checks at Fabric's client-started lifecycle event before the first title-screen render; a watchdog and 15-minute step limit retain failure diagnostics. Missing-dependency loader messaging remains manual. |
| P2 | Dedicated multiplayer | **Opt-in/manual** | Use the existing harness only with owner-approved `-PacceptServerEula=true`; then test latency, reconnect, packet scoping and automation. Do not enable it in CI silently. |
| P3 | Documentation/release | **Current checkpoint reconciled** | This update aligns module counts, CI evidence, AutoGrind and production-addon acceptance; generated `docs/MODULES.md` remains source-driven. The documentation-only full CI run validates this reconciliation. |

## Conditional module candidates

Do not add modules just to increase the catalogue. AutoGrind now executes every goal in the current
GrindBook through the existing shared services. Broader presets and automated movement are later
work; any extension must preserve TaskRunner resumability, use RotationService, InventoryService and
action ownership, and carry real 26.2 game-test evidence.

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
4. Run the full Java 25 pipeline, including game tests, production addon install/removal launches and generated documentation.
5. Report separately what was unit tested, integrated-server tested, dedicated-server tested,
   externally packaged, visually inspected and still manual-only.
6. Update `handover.md` while preserving historical records, and verify the live PR state. PR #9 has merged; do not describe it as open or draft or rewrite its historical review record.

## Deliberate non-goals

No packet flooding, crash/dupe exploits, malformed packet tricks, anti-cheat bypass presets, hidden
server-authoritative claims, or silent rotations intended to defeat server checks. Turkish setting
description localization is cancelled and must not be restarted. AutoGrind supports the current
GrindBook vocabulary but still requires manual movement between out-of-reach targets. Add no custom
pathfinder; only bridge a verified 26.2-compatible Baritone API. The production-like addon test
covers installation, restart persistence and removal; missing-dependency messaging remains manual.
Baritone reflection must never be written from guessed signatures.
