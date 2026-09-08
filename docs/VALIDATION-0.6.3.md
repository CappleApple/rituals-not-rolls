# Validation — 0.6.3

Completed **2026-09-08**, Windows, Java 21, Minecraft 1.21.1 / NeoForge 21.1.244.

- Gradle build and all **51 existing unit tests** passed.
- Bundled logo.png matches the user-supplied **256×256** image byte for byte. Mod metadata references it and disables texture smoothing.
- Actual client: opened NeoForge's Mods screen, selected Rituals Not Rolls, and visually inspected the rendered icon screenshot.
- Actual client at GUI scales **2 and 4**: right-clicking inside the search field or on its decorative padding clears the query, focuses the field, and allows immediate typing. Repeated right-clicks on an empty query remain safe.
- Actual client: left-clicking the bar preserves its text. Right-clicking outside preserves the query and removes typing focus.
- Existing item-search checks passed for names, IDs, known-only affinities, tag members, and snapshot refresh. Default and rebound inventory keys still close the table only when search is unfocused. Escape still closes it while focused.
- Existing relative power labels and tooltip checks passed.
- Compared with 0.6.2, the production JAR changes only RitualScreen.class, mod metadata, and the added logo.png. The icon and metadata are also verified by release packaging.

Broader GameTests and optional-mod gates were not repeated for this UI/artwork update. Earlier gameplay coverage is recorded in [0.6.0 validation](VALIDATION-0.6.0.md); the previous search update is recorded in [0.6.2 validation](VALIDATION-0.6.2.md).

- [Build and unit tests](validation/icon-search-1.6.3-build.log)
- [Actual client checks](validation/icon-search-1.6.3-client.log)
- [Rendered Mods screen](validation/mod-icon-1.6.3.png)

The client and local fixture server were stopped after validation. Development fixtures remain excluded from the production JAR.
