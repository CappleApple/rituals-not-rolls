# Validation — 0.6.2

Completed **2026-09-08**, Windows, Java 21, Minecraft 1.21.1 / NeoForge 21.1.244.

- Gradle build and all **51 existing unit tests** passed.
- Actual client: material searches match localized item names and registry IDs, ignoring case and surrounding whitespace. Enchantment name searches continue to work; unmatched queries have a distinct empty state.
- Actual client: item searches show only enchantments with a matching known material, and narrow the selected enchantment's material panel to those matches. Undiscovered affinities are excluded.
- Actual client: tag-backed materials match every member's name and ID independently of the cycling icon. Changed knowledge snapshots refresh the search results.
- Actual client at GUI scales **2 and 4**: clicking enchantment rows, material entries, pagination buttons, blank background, or outside the window removes search focus. Subsequent typing does not enter the field, and the query is preserved. Clicking the search bar's padding restores focus.
- Actual client: default and rebound inventory keys remain typeable while the field is focused. Both close the table when the field is unfocused. Escape still closes the table with a focused search field.
- Visually inspected the captured diamond search: matching enchantments appear on the left and only the Diamond material appears in the selected Sharpness details. Base, effective, and potential power retain their short relative labels.
- Existing client checks also confirmed the removed material explanation tooltip stays absent and all five page tooltip tiers still render.

Only the table screen's production code changed. Gameplay calculations, default definitions, and integrations are unchanged. Broader GameTests and optional-mod gates were not repeated for this UI update; earlier coverage is recorded in [0.6.0 validation](VALIDATION-0.6.0.md) and [0.6.1 validation](VALIDATION-0.6.1.md).

- [Build and unit tests](validation/table-search-1.6.2-build.log)
- [Actual client checks](validation/table-search-1.6.2-client.log)
- [Rendered item search](validation/table-search-1.6.2.png)

The client and local fixture server were stopped after validation. Development fixtures remain excluded from the production JAR.
