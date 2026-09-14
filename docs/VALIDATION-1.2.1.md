# Testing notes - 1.2.1

Checked on 2026-09-14 with Java 21, Minecraft 1.21.1 and NeoForge 21.1.244. Previous Sable and Create Aeronautics evidence remains in [VALIDATION-1.2.md](VALIDATION-1.2.md).

## Automated checks

The normal build passes 69 unit tests and all 135 required GameTests without Bundled Not Siloed installed. The new regression test verifies that gathering respects a denied player slot, still reaches unrelated pages through the inventory capability, and calls an allowed output slot's `onTake` exactly once.

```powershell
.\gradlew.bat test build runGameTestServer --offline --console=plain
```

Evidence: [build and GameTests](validation/bundled-1.2.1-baseline.log), [unit totals and artifact hashes](validation/bundled-1.2.1-artifact.json).

## Bundled Not Siloed

All five integration GameTests pass with Bundled Not Siloed 1.4.5, Stacks Not Slots 1.0 and Panels Not Screens 1.1.7. They exercise the real storage backend and NeoForge capability providers on a dedicated GameTest server:

- Capability enumeration, simulated extraction and committed extraction from logical slot 80.
- The book's Gather pages action across visible and stowed pages, preserving duplicates and pages for other enchantments.
- A scrolled inventory view without consuming the displayed page twice.
- Singleton pages whose removal shrinks the handler's reported slot count during gathering.
- Cursor double-click gathering from both a chest and stowed player storage.

The tests also check repeated gathering, the bound book's object identity, menu validity, backend revision and consistency, and the book state sent to the client. Both `ENTITY` and `ENTITY_AUTOMATION` resolved Bundled Not Siloed's `DynamicItemHandler` with 82 slots in the capability fixture. Provider masking was not reproduced in this runtime.

Place the three mod JARs listed above in `output/bundled-test-mods`, then run:

```powershell
.\gradlew.bat runBundledGameTestServer -PbundledTestMods=output/bundled-test-mods --offline --console=plain
```

Evidence: [Bundled Not Siloed runtime](validation/bundled-1.2.1-runtime.log).

The fixture runs in `run-bundled-gametest` under the `ritualsnotrolls_bundled` namespace. Fixture classes and templates are excluded from the release JAR. The optional runtime property does not bundle any of these inventory mods. Production gathering uses only NeoForge's inventory API.

## Client validation limits

The integration tests verify server inventory changes and the outgoing book state. This change has not received an in-game client check.
