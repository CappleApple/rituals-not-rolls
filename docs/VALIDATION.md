# Testing notes - 1.3.1

Checked on 2026-09-14 with Java 21, Minecraft 1.21.1 and NeoForge 21.1.244. The library, storage and optional-mod GameTest evidence for 1.3 is preserved in [VALIDATION-1.3.md](VALIDATION-1.3.md).

## Build and assets

`gradlew.bat test build --offline --console=plain` passes all 69 unit tests. The release-content gate checks that the binder uses `minecraft:item/generated` and packages the authored flat texture. Development fixtures remain excluded from the JAR.

Evidence: [build](validation/binder-1.3.1-build.log), [test totals and JAR hash](validation/binder-1.3.1-artifact.json).

## Client checks

The binder client fixture ran against a dedicated development server. It checked vanilla book-arrow widgets, three standard Minecraft buttons, a flat item model, the absence of the usage hint and tooltip status visibility matching the current Shift-key state. This run used the normal, unshifted tooltip.

Navigation between both spreads, both settings, one-page extraction, inventory persistence, GUI resizing and empty-binder display passed. Screenshots show the arrows inside the paper, separate page numbers 1/2 and 3/4, and standard footer buttons. The server saved and stopped cleanly.

Evidence: [client log](validation/binder-1.3.1-client.log), [server log](validation/binder-1.3.1-server.log), [first spread at fitted scale](validation/binder-1.3.1-fitted.png), [second spread](validation/binder-1.3.1-second-spread.png), [empty binder](validation/binder-1.3.1-empty.png).
