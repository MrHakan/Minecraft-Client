# Changelog

Releases are numbered **2.x.yy**: `x` is the major version and `yy` a two-digit minor update (see
[Versioning](README.md#versioning)). Every version needs a section here before it can be merged; CI
publishes the section as the GitHub release notes.

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
