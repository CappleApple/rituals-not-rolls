# Validation — 0.5.0

Completed **2026-09-08**, Windows 11, Java 21.0.12, Minecraft 1.21.1, NeoForge 21.1.244. Tests used disposable development worlds; no installed modpack or user world was changed.

| Gate | Result |
| --- | --- |
| Build and unit suite | Passed; **37 JUnit tests**, zero failures/errors/skips |
| GameTests | **99 required tests passed** |
| Saved-world migration | Three dedicated-server boots: seed old IDs, load/convert, restart without rerolling |
| Immersive Enchanting absent | Confirmed in the test server mod list |
| Near and distant shelves | Both migrated on load and supplied ritual knowledge |
| Production JAR | Metadata, mixins, 42 definitions, bookshelf tag and supplied page texture checked; development fixtures excluded |

The 11 new GameTests cover normal player insertion/removal, shelf occupancy, page/book knowledge union, ritual power, malformed-page rejection, modern and legacy ancient-book components, stack counts, archived source NBT, all persistent ItemStack codec variants, JSON decoding, nested bundle/shulker contents, player inventories, Ender Chests, item entities, saved shelves, unknown/unsupported enchantments, pending retry after data becomes available, ambiguous books, names, invalid cosmetic metadata, and repeated save/load.

The dedicated-server fixture writes the obsolete item IDs directly through actual chunk/block-entity serialization, then stops cleanly. The second process loads the same current-Minecraft-version world with Immersive Enchanting absent, including a shelf 2,048 blocks from spawn. A third process confirms the converted item NBT is identical. This exercises normal world loading without a Minecraft DataVersion upgrade.

This release's new bookshelf interactions were checked through the real server block interaction methods in GameTests. No new visual client check was performed. Earlier animation/client evidence is retained in [0.4.0 validation](VALIDATION-0.4.0.md).

Evidence: [unit tests](validation/unit-tests.json), [GameTests](validation/gametest-results.log), [migration seed](validation/migration-world-seed.log), [legacy load](validation/migration-world-load.log), [restart](validation/migration-world-restart.log).

```powershell
.\gradlew.bat test build runGameTestServer
# run-migration is an isolated fixture directory, with eula=true and loopback server properties.
# First boot seeds legacy books; second migrates; third checks persistence.
.\gradlew.bat runMigrationServer
.\gradlew.bat runMigrationServer
.\gradlew.bat runMigrationServer
python tools/package_release.py
```
