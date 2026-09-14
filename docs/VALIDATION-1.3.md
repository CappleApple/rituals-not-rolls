# Testing notes - 1.3

Checked on 2026-09-14 with Java 21, Minecraft 1.21.1 and NeoForge 21.1.244. Previous inventory integration evidence is preserved in [VALIDATION-1.2.1.md](VALIDATION-1.2.1.md).

## Automated checks

The build passes 69 unit tests and all 168 required GameTests without optional inventory or vessel mods installed.

```powershell
.\gradlew.bat test build runGameTestServer --offline --console=plain
```

The new cases cover:

- First-book binding, exactly three leather from one or several stacks, surplus pages, duplicate discoveries across books, full shelves and missing ingredients.
- Twenty simultaneous first-book bindings from 64 leather, leaving four leather, and concurrent page arrivals that preserve every discovery in one book.
- Binder capacity, duplicate filtering, automatic collection controls, data persistence and network encoding.
- Batches of 16 page checks per 20 ticks and a configured limit of two, including filing into existing books and binding missing books.
- Interruption with pages already in flight, changed destinations, owner or level changes, and item-join callbacks that alter generated pages or split ingredients.
- Menu paging, stale request rejection, bound-item validity, extraction and collection pauses.

Evidence: [build and GameTests](validation/binder-1.3-baseline.log), [test totals and JAR hash](validation/binder-1.3-artifact.json).

## Optional integrations

All eight inventory integration GameTests pass with Bundled Not Siloed 1.4.5, Stacks Not Slots 1.0 and Panels Not Screens 1.1.7. These exercise the real NeoForge handlers and storage backend: existing book gathering, visible and stowed binder collection, shrinking logical inventories, bound-stack identity and backend consistency.

```powershell
.\gradlew.bat runBundledGameTestServer -PbundledTestMods=output/bundled-test-mods --offline --console=plain
```

All five vessel integration GameTests pass with Sable 2.0.5, Create 6.0.10 and Create Aeronautics 1.3.2 installed together. They include book binding while the table and shelves move and rotate in a Sable sub-level.

```powershell
.\gradlew.bat runSableGameTestServer -PsableTestMods=output/sable-test-mods --offline --console=plain
```

Place the listed mod JARs in the corresponding test directory before running these commands. Fixture classes and templates are excluded from the production JAR, and these optional mods are not bundled.

Evidence: [inventory runtime](validation/binder-1.3-bundled.log), [Sable/Create Aeronautics runtime](validation/binder-1.3-sable.log).

## Client checks

Three opt-in client fixtures ran against a dedicated development server. The server started, accepted connections, saved and shut down cleanly.

- `runBinderClient` opened the item through normal use, checked twelve cards and a second spread, changed both controls, extracted one page, verified inventory persistence, resized the GUI and opened an empty binder.
- `runBindingClient` observed the page and three leather during the 40-tick animation, the completed book flying to a shelf, the shelf occupancy and the resulting library knowledge. Two surplus pages and seven of the original ten leather remained.
- `runGuideClient` opened all eight chapters, checked expanded server values, scrolled each chapter to its end and verified scrollbar, resize and navigation behavior. The expanded library chapter includes binder and binding instructions.

Launch `runSmokeServer` in the development world before a client fixture. These opt-in fixtures replace their test inventory and nearby blocks; use an expendable development world. The configured test server uses localhost port 25585.

Evidence: [binder client](validation/binder-1.3-client.log), [binding client](validation/binding-1.3-client.log), [guide client](validation/binder-1.3-guide-client.log), [dedicated server](validation/binder-1.3-server.log).

Screenshots: [binder at fitted scale](validation/binder-1.3-fitted.png), [second spread](validation/binder-1.3-second-spread.png), [empty binder](validation/binder-1.3-empty.png), [binding ingredients](validation/binding-1.3-ingredients.png), [book flight](validation/binding-1.3-book-flight.png), [filed book](validation/binding-1.3-filed.png), [library guide](validation/binder-1.3-guide-library.png).

The client checks used the base mod. Optional inventory and vessel behavior was checked on dedicated GameTest servers, without a client visual check for those integrations.
