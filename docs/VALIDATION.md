# Testing notes — 1.0

This file records the current release checks for Rituals Not Rolls 1.0 and points to the raw test/client artifacts kept in the repository.

The 1.0 release was rebuilt on 2026-09-08 with Java 21 for Minecraft 1.21.1 / NeoForge 21.1.244. The stable-version build uses the same gameplay implementation that was tested during the final 1.6.4 development pass; the release version change was metadata-only.

## Automated coverage

The release test run includes:

- 59 unit tests;
- 119 required GameTests;
- ritual timing and sequential/simultaneous chain behavior;
- material sharing, catalyst payment, XP budgeting, and cancellation;
- enchantment/subtraction calculations;
- particle-route and ring geometry math;
- failure instability/scatter behavior;
- native pedestal persistence/inventory behavior; and
- packaging checks that keep development fixtures out of the release jar.

The current release logs are kept here:

- [1.0 build, unit tests and GameTests](validation/release-1.0-tests.log)
- [1.0 unit-test totals](validation/release-1.0-unit-results.json)

Older validation records are retained under the versioned `VALIDATION-*.md` files for regression history.

## Client checks

The final development client pass covered the pieces that are difficult to establish from server tests alone:

- multiple enchantment chains forming and completing together;
- Experience Catalyst streams and the retained XP ring;
- the shared cutoff where incoming trails stop but already-emitted particles finish their routes;
- successful and failing chains in the same ritual;
- pure disenchanting without a success burst;
- particle instability and failure scatter;
- target-item release timing; and
- the expected XP debit for the test fixture.

The optional-mod client pass also covered the native Supplementaries and Iron's Spellbooks pedestal adapters, including material removal timing and failure behavior.

Useful artifacts:

- [Main client log](validation/trail-drain-1.6.4-client.log)
- [Optional-mod/failure client log](validation/particle-refinement-1.6.4-client.log)
- [Trails draining into the rings](validation/trail-drain-1.6.4-draining.png)
- [All trails gathered](validation/trail-drain-1.6.4-gathered.png)
- [XP ring retained](validation/trail-drain-1.6.4-xp-retained.png)
- [Noisy trail before failure](validation/particle-refinement-1.6.4-noisy.png)
- [Failure scatter](validation/particle-refinement-1.6.4-scatter.png)

Those filenames keep the pre-1.0 development version because the client observations were collected before the stable-version rename.

## What to rerun after changes

For normal logic/data changes:

```powershell
.\gradlew.bat test build runGameTestServer
```

For visual/timing changes, also run the appropriate disposable client fixture and inspect at least one complete successful ritual plus a failed or subtraction path.

Changes to optional pedestal integrations should be checked with the corresponding mod present, not only through the generic pedestal path.

## Manual checks still worth doing

Automated coverage is intentionally not treated as a substitute for normal gameplay QA. Before a release that touches the relevant systems, manually check:

- a real survival flow from finding pages through completing an enchantment;
- multiplayer interaction with a shared library/table;
- resource-pack replacements for pages, GUI assets, sounds, and enchanted-book models;
- modpack-specific enchantments/material definitions; and
- any new third-party pedestal/book integration.

The raw logs/screenshots in `validation/` are debugging records, not a claim that every possible modpack combination has been tested.
