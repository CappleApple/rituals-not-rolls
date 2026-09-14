# Testing notes - 1.2

Checked on 2026-09-13 with Java 21.0.12, Minecraft 1.21.1 and NeoForge 21.1.244. Earlier particle and client evidence remains in [VALIDATION-1.1.3.md](VALIDATION-1.1.3.md).

## Automated checks

The normal build passes 69 unit tests and all 134 required GameTests without Sable installed. The new particle tests round-trip ordinary and sub-level simple flights, spline routes and bursts through both JSON and network codecs, including the exact local origin, immutable table anchor and sub-level UUID.

```powershell
.\gradlew.bat test build runGameTestServer
```

Evidence: [build and GameTests](validation/sable-1.2-baseline.log), [unit totals and JAR audit](validation/sable-1.2-artifact.json).

## Sable and Create Aeronautics

Four integration GameTests pass in both environments:

| Runtime | Result |
| --- | --- |
| Sable 2.0.5 | 4/4 passed |
| Sable 2.0.5, Create 6.0.10, Create Aeronautics bundled 1.3.2 | 4/4 passed |

The tests assemble a real table, filled chiseled bookshelf and pedestal through Sable's assembly API. They move and rotate the resulting native physics body throughout the test. Coverage includes plot inventory preservation, table discovery from world-space dropped items, menu reach including vertical distance, capture and cancellation, a complete enchantment with consumed offerings, book retrieval, page merging, whole-book filing, and exactly-once refunds after the source plot is removed.

The Aeronautics run includes its bundled Simulated and Offroad modules. These tests exercise an assembled Sable setup while Aeronautics is loaded; they do not operate a pilot seat or test vehicle controls.

Evidence: [Sable runtime](validation/sable-1.2-runtime.log), [Create Aeronautics runtime](validation/aeronautics-1.2-runtime.log).

To reproduce with Sable alone, supply a locally obtained Sable JAR using `-PsableTestJar=<absolute-jar-path>` to `runSableGameTestServer`. For the combined run, place the three mod JARs listed above in `output/sable-test-mods`, then run:

```powershell
.\gradlew.bat runSableGameTestServer -PsableTestMods=output/sable-test-mods
```

The optional runtime properties do not bundle these mods into the release. The dedicated fixture runs in `run-sable-gametest`, uses the `ritualsnotrolls_sable` test namespace, and is excluded from the release JAR together with its structure template. The final JAR includes Sable Companion 1.6.0 and its license.

## Client validation limits

Particle serialization and shared flight geometry have automated coverage. This change has not received an in-game visual or audio check on a moving vessel. The older screenshots and native particle checks above document the existing appearance only.
