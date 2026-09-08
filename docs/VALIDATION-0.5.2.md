# Validation — 0.5.2

Completed **2026-09-08**, Windows 11, Java 21.0.12, Minecraft 1.21.1, NeoForge 21.1.244. All checks used disposable development worlds.

| Gate | Result |
| --- | --- |
| Build and JUnit | Passed; **41 tests**, zero failures/errors/skips |
| GameTests | **103 required tests passed** |
| Five-tier boundaries | 0, 15, 19, 19.9999, 20, 39.9999, 40, 59.9999, 60, 79.9999, 80, and 100 percent classified correctly |
| Full-definition reference | Undiscovered and tag affinities included; single/tied maximum, changed definitions, and extreme finite powers checked |
| Actual client page tooltips | All five complete translated Enchanting Power labels rendered without numeric power values or clipping |
| Actual book screen | Relative tiers fit the rows; hover shows the full Enchanting Power phrase |
| Actual table hover | Material-row tooltip event count stayed zero; table displayed Enchanting Power and numeric base/effective values |
| Dedicated server | Started, served the client, saved, and stopped normally |
| Artifact | 68 active-capable definitions, 2 exclusions, source data balance, page artwork and production-only classes checked |

The four new JUnit tests cover boundary classification without rounding, the supplied 15/100 example, definition-relative and tag-aware maxima, recomputation after data changes, and overflow/underflow cases. Tier selection is presentation only. Existing ritual math and migration regressions passed unchanged.

The opt-in client driver used the native page tooltip renderer and actual BookScreen/RitualScreen against a dedicated server. A diagnostic screen displayed one real page for each tier from the synchronized default definitions. The three captures were visually inspected. A vanilla unsigned-chat notification is visible in the captures; it does not overlap the tier labels.

- [Five page tiers](validation/power-tiers-pages.png)
- [Table material hover](validation/power-tiers-table-hover.png)
- [Knowledge-book tiers](validation/power-tiers-book.png)
- [Client checks](validation/power-tooltip-client.log)
- [GameTests](validation/gametest-results.log)
- [Unit results](validation/unit-tests.json)

Earlier optional-mod registry validation is retained in [0.5.1 validation](VALIDATION-0.5.1.md).

```powershell
.\gradlew.bat test build runGameTestServer
.\gradlew.bat runSmokeServer
# In another terminal, while the local development server is ready:
.\gradlew.bat runPowerTooltipClient
python tools/rcon.py stop
python tools/package_release.py
```
