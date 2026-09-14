# Testing notes - 1.3.1

Checked on 2026-09-14 with Java 21, Minecraft 1.21.1 and NeoForge 21.1.244. The library, storage and optional-mod GameTest evidence for 1.3 is preserved in [VALIDATION-1.3.md](VALIDATION-1.3.md).

## Build and assets

`gradlew.bat test build --offline --console=plain` passes all 69 unit tests. The release-content gate checks that the binder uses `minecraft:item/generated` and packages the authored flat texture. Development fixtures remain excluded from the JAR.

Evidence: [build](validation/binder-1.3.1-build.log), [test totals and JAR hash](validation/binder-1.3.1-artifact.json).

## Client checks

The binder client fixture ran against a dedicated development server. It checked vanilla book-arrow widgets, three standard Minecraft buttons, a flat item model, the absence of the usage hint and tooltip status visibility matching the current Shift-key state. This run used the normal, unshifted tooltip.

Navigation between both spreads, both settings, one-page extraction, inventory persistence, GUI resizing and empty-binder display passed. Screenshots show the arrows inside the paper, separate page numbers 1/2 and 3/4, and standard footer buttons. The server saved and stopped cleanly.

Evidence: [client log](validation/binder-1.3.1-client.log), [server log](validation/binder-1.3.1-server.log), [first spread at fitted scale](validation/binder-1.3.1-fitted.png), [second spread](validation/binder-1.3.1-second-spread.png), [empty binder](validation/binder-1.3.1-empty.png).

## Binder double-click gathering

The updated build passes 69 unit tests and all 171 baseline GameTests. New menu-interaction cases gather from a chest and player inventory across enchantments, retain duplicate copies with filtering on, collect them with filtering off, work while Auto-collect is off, enforce remaining capacity, preserve denied slots through the capability fallback and call an allowed slot's `onTake` once.

All nine optional inventory GameTests pass with Bundled Not Siloed 1.4.5, Stacks Not Slots 1.0 and Panels Not Screens 1.1.7. The added case gathers visible, chest and stowed pages through a cursor binder and checks backend consistency.

Evidence: [baseline](validation/binder-gather-1.3.1-baseline.log), [expanded inventory](validation/binder-gather-1.3.1-bundled.log). The new double-click interaction was checked through server menu GameTests; the earlier client screenshots cover the binder's appearance and controls.

## Sharpness material replacement

The final build passes 69 unit tests and 171 baseline GameTests after replacing the overlapping amethyst group with Prismarine Shard. The loaded-definition test checks 16 power, resource value 2, exact item matching and a single remaining Amethyst Shard match. The full-material-set test verifies the recalculated cost curve: the distinct total is 213 power, exactly enough for Sharpness V.

Evidence: [final baseline](validation/sharpness-1.3.1-baseline.log).
