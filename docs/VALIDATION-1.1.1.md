# Testing notes — 1.1.1

Checked on 2026-09-12 with Java 21.0.12, Minecraft 1.21.1 and NeoForge 21.1.244. The broader feature and recipe-viewer checks for 1.1 remain in [VALIDATION-1.1.md](VALIDATION-1.1.md).

## Automated checks

The build passed 66 unit tests and 134 required GameTests. The added shelf regression retrieves items from all six slots in all four horizontal orientations and checks each animation origin against Minecraft's own chiseled-bookshelf hit-slot resolver. Filing and retrieval use the same corrected slot-position calculation.

See [build and GameTests](validation/scroll-slot-1.1.1-tests.log) and [unit totals](validation/release-1.1.1-unit-results.json).

## Native client checks

The real-client guide fixture checks all eight chapters with fractional mouse-wheel input, continuous movement from screenshot through text, bottom bounds, Home/End, scrollbar dragging and release, chapter reset, screenshot enlargement, and preserved scroll/search state after resizing. The small-GUI screenshot checks clipping under the workstation's fit transform. The item header has no hover tooltip; enchantment description and item-break particle checks remain in this fixture.

The library client checks the page's approach to the left slot of a north-facing shelf, then confirms filing, book insertion and animated retrieval. The all-orientation regression above covers the other shelf facings and slots. The dedicated development server and both clients run without recipe viewers installed.

Evidence:

- [Item header without a tooltip](validation/guide-1.1.1-item-header.png)
- [Guide client log](validation/scroll-guide-1.1.1-client.log)
- [Continuously scrolled chapter text](validation/guide-1.1.1-chapter-0-middle.png)
- [Bottom of the long library chapter](validation/guide-1.1.1-chapter-1-bottom.png)
- [Scrolled guide at small GUI size](validation/guide-1.1.1-small-gui-scrolled.png)
- [Library client log](validation/slot-library-1.1.1-client.log)
- [Page approaching the correct left slot](validation/library-1.1.1-page-at-left-slot.png)

These are development-fixture checks. Multiplayer library contention and arbitrary modpack combinations were not tested in this patch.

## Commands

```powershell
.\gradlew.bat test build runGameTestServer
python tools/package_release.py
```

Native client fixtures use the disposable server on localhost port 25585: start `runSmokeServer`, then run `runGuideClient` and `runLibraryClient` separately. They modify their development world and exit after completion.
