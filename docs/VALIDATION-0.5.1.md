# Validation — 0.5.1

Completed **2026-09-08** on Windows 11, Java 21.0.12, Minecraft 1.21.1. The base build and GameTests use **NeoForge 21.1.244**. The real-mod integration server uses **21.1.248**, meeting installed Supplementaries 3.9.7's requirement. No live modpack or user world was modified.

| Gate | Result |
| --- | --- |
| Build / unit suite | Passed; **37 unit tests**, zero failures/errors/skips |
| Standalone GameTests | **103 required tests passed** |
| Standalone default loading | 42 active vanilla definitions; 2 explicit exclusions; no unresolved optional items |
| Real-mod dedicated server | Started, checked all requested player enchantments, saved and stopped |
| Optional definition checks | **26 passed**; exact native levels, registered material IDs, matching affinities, full-set power, insufficient incomplete sets, and supported particle types |
| Complete integration registry | **68 active definitions, 2 explicit exclusions, zero missing or unresolved** |
| Particle identity | All 68 active defaults have distinct effect/color pairs |
| Packaged resources | 70 definition/exclusion JSON files, metadata, mixins, supplied page texture, bookshelf tag and production-only classes checked |

The four new GameTests cover exact-registry optional activation, missing/removed optional enchantments, reload snapshot replacement, explicit exclusions in `/ritual missing`, preventing ancient-book migration into internal helper pages, malformed boolean flags, enabled overrides, and standalone validation without optional mods. All prior migration and ritual regressions remain in the suite.

The integration server loaded the actual installed mods and dependencies in `run-compat`. The checked versions include Aggro Fix 3.0.1, Botany Pots 21.1.44, Combat Roll 2.0.6, Create 6.0.10, Critical Strike 1.0.4, When Dungeons Arise 2.1.68, Farmer's Delight 1.3.4, Gouge 1.5.1, Soul Fire'd 6.1.0, Not Enough Trials 6.4, Passable Foliage 9.2.0, Supplementaries 3.9.7, and Vein Mining 5.0.0-beta.2. Kiwi and Cloth Config were included as runtime dependencies. The test mod list and per-enchantment checks are retained in the log.

This verifies definitions against actual registries and native maximum levels, not the visual appearance of each new particle color or manual gameplay feel. No new client visual session was run. See [0.5.0 migration evidence](VALIDATION-0.5.0.md) and [0.4.0 client evidence](VALIDATION-0.4.0.md) for earlier checks.

Evidence: [GameTests](validation/gametest-results.log), [unit tests](validation/unit-tests.json), [real-mod registry checks](validation/optional-mod-definitions.log), and [full material catalog](MOD_DEFAULTS.md).

```powershell
.\gradlew.bat test build runGameTestServer
# Isolated run-compat requires the corresponding optional mods and their dependencies.
.\gradlew.bat '-Pneo_version=21.1.248' runDefinitionCompatServer
# Restore the base build before packaging.
.\gradlew.bat test build
python tools/package_release.py
```
