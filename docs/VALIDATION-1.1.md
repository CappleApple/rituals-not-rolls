# Testing notes — 1.1

Checked on 2026-09-12 with Java 21.0.12, Minecraft 1.21.1 and NeoForge 21.1.244. These are local build and development-client results. Previous release evidence remains in [VALIDATION-1.0.md](VALIDATION-1.0.md).

## Build and server tests

- 66 unit tests passed, including expression evaluation and Enchantment Descriptions key precedence.
- 133 required GameTests passed. Coverage includes duplicate consumption across branches, enchantability, non-enchantable held-item fallback, loot conversion, crafting remainders, shelf transfers, destination changes, retrieval and existing ritual transactions.
- The dedicated development server reached `Done` without a recipe viewer installed. No-viewer native clients connected and completed rituals and library transfers.
- Artifact checks verify all 70 definition files, 33 multi-level vanilla enchantments reaching X, eight guide screenshots, optional viewer adapters and exclusion of development fixtures and viewer API classes. Existing material sources, powers and previously defined thresholds were compared with the pulled Git revision and preserved.

Evidence: [build and GameTests](validation/release-1.1-tests.log), [unit totals](validation/release-1.1-unit-results.json), [final description build/client](validation/standard-descriptions-1.1-client.log), [dedicated server](validation/release-1.1-server.log).

## Native client checks

The automated fixtures operated a real Minecraft client connected to the dedicated server. Screenshots were inspected for layout and legibility.

| Check | Observed result |
| --- | --- |
| Table filtering | Real right-click with a sword opened compatible knowledge, including curses; server tests cover the full-list fallback for non-enchantable items |
| Tooltips | Gold enchantment names, red curse names, white descriptions; custom name colors retained white descriptions; text resolved through the standard `.desc` key |
| Guide | All eight screenshot chapters loaded, text pagination worked, images enlarged and returned with Escape, and small GUI/search state survived resizing |
| Consumption | Twelve native item fragments appeared after offering removal; the catalyst remained and the ritual completed |
| Library | Real page/book entities followed magical arcs, shelf occupancy and server menu contents updated, retrieval arrived above the table and released the book |
| JEI 19.21.0.247 | Actual Uses key on a level VII, multi-enchantment book and on a page opened the corresponding recipes; hidden variants stayed outside the item index |
| EMI 1.1.24+1.21.1 | Same Uses-key and index checks passed with EMI alone |
| REI 16.0.799 | Same Uses-key and index checks passed with REI alone |

The library fixture sent the actual table retrieval packet; physical Shift-key input was not exercised by that fixture. Enchantment Descriptions' 1.21.1 source was checked for its translation contract; the resolver's suffix, level, registry-ID and custom-name precedence is covered by unit tests. Enchantment Descriptions itself was not installed in the client runs.

EMI plus JEI also passed the Uses-key checks through the native EMI adapter. That combination logged duplicate `jei:/...` tag recipes from the viewers. It is not a warning-free combination. EMI's expected modified-manager notice comes from the optional dynamic lookup mixin. Without EMI, its optional target can produce a class-not-found warning without preventing startup.

Logs: [guide and descriptions](validation/standard-descriptions-1.1-client.log), [library](validation/library-1.1-client.log), [JEI](validation/viewer-1.1-jei.log), [EMI](validation/viewer-1.1-emi.log), [REI](validation/viewer-1.1-rei.log), [EMI with JEI](validation/viewer-1.1-emi-jei.log).

Screenshots:

- [Gold name and white description](validation/guide-1.1-gold-tooltip.png)
- [Red curse name and white description](validation/guide-1.1-curse-tooltip.png)
- [Guide screenshot chapter](validation/guide-1.1-chapter-0.png)
- [Enlarged screenshot](validation/guide-1.1-screenshot-enlarged.png)
- [Page filing in flight](validation/library-1.1-page-flight.png)
- [Retrieved book in flight](validation/library-1.1-retrieval-flight.png)
- [JEI book uses](validation/viewer-1.1-jei-book-uses.png)
- [EMI page uses](validation/viewer-1.1-emi-page-uses.png)
- [REI page uses](validation/viewer-1.1-rei-page-uses.png)

The eight images bundled into the guide are unedited 960 × 540 screenshots captured in Minecraft with the HUD hidden. Their source capture log is [guide examples](validation/guide-examples-1.1-client.log).

## Repeating checks

```powershell
.\gradlew.bat test build runGameTestServer
python tools/package_release.py
```

The client fixtures expect a disposable `run` server configured for localhost port 25585. Start it with `runSmokeServer`. Run `runGuideClient`, `runLibraryClient`, or `runViewerClient -PrecipeViewer=jei` (also `emi`, `rei` or `emi-jei`) separately; these clients exit when their fixture completes. `runGuideExamplesClient` captures the guide's development scenes. All use disposable development worlds and change their contents.

Normal survival progression, concurrent multiplayer library access and arbitrary modpack combinations still need gameplay QA. Automated/native fixture results do not establish those cases.
