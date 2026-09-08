# Validation — 0.6.1

Completed **2026-09-08**, Windows, Java 21, Minecraft 1.21.1 / NeoForge 21.1.244.

- Gradle build and all **51 existing unit tests** passed.
- Actual client: pressing the default inventory key and a rebound inventory key kept the enchanting-table screen open.
- Actual client: the inventory-key letter remained typeable in the search field, and Escape closed the screen.
- Actual client screenshot: potential, base, and effective power use short relative labels without numeric power values or the Enchanting Power suffix. Material hover still produces no explanation tooltip.
- Base and effective strength share the strongest affinity in the full loaded definition as their baseline. Signed subtraction keeps a minus sign; zero is Weakest and above-maximum strength remains Strongest. Longer row labels fit the available width.
- Page and knowledge-book tier labels retain their existing Enchanting Power wording; all five page tooltip tiers rendered in the client.
- XP costs, catalyst counts, library counts, and pagination remain numeric. The server's power calculations and synchronized numeric state are unchanged.

Only the table screen's production code and its heading translation changed. Broader game-mechanics and optional-pedestal coverage remains documented in [0.6.0 validation](VALIDATION-0.6.0.md); those GameTests and native-mod gates were not repeated for this UI-only update.

- [Build and actual client checks](validation/table-reference-1.6.1.log)
- [Rendered table](validation/table-reference-1.6.1.png)

The client and local fixture server were stopped after validation. Development fixtures remain excluded from the production JAR.
