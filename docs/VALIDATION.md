# Validation — 1.0

Release **1.0** rebuilt and validated **2026-09-08**, Windows, Java 21, Minecraft 1.21.1 / NeoForge 21.1.244. All 59 unit tests and 119 required GameTests passed again under the new version.

Every packaged gameplay class and asset is byte-identical to the completed build originally labeled 1.6.4; only `META-INF/neoforge.mods.toml` changes to version `1.0`. The actual-client observations below were collected on that same implementation before its stable-version promotion, and the raw evidence retains its original filenames. The optional-mod fixture used NeoForge 21.1.248 to meet its installed dependencies. No additional visual client run was needed for the version-only rebuild.

- Build and all **59 unit tests** passed, with zero failures/errors/skips. All **119 required server GameTests** passed.
- Timing checks cover sequential and simultaneous starts, a shared enchantment-source cutoff, earlier XP-source cutoff, long-chain draining before the final pulse, and moving-player XP routes retaining their captured arrival deadline.
- Geometry checks cover all nine orbital planes at small, medium, and maximum radii, throughout the orbit and inward pulse, with successful and failing instability. Raised centers keep rings above the table with clearance for particle sprites and noise.
- XP checks cover 315 available XP with two catalysts yielding exactly 15 equivalent levels and +15% power, the 550-XP full capacity, configurable rates, fractional levels, vanilla XP curve boundaries, and large budgets. Server tests verify actual payment, matching previews, a fixed captured budget, cancellation if reserved XP disappears, and zero-XP base-power rituals without an XP stream.
- Noise checks verify a stable start, bounded individual offsets along one trail, smooth velocity across noise boundaries, and weaker shared wobble. Failure checks verify position continuity, an independent scatter impulse added to current momentum in all eight octants, and eventual downward motion under gravity, both before and after orbit entry.
- Actual client: four enchantment chains and both XP catalysts formed five rings. The XP source stopped early while its smaller ring persisted; remaining enchantment sources stopped together, their last particles joined the rings, and the target remained captured until the shared pulse and completion.
- Actual client: ring positions were sampled above the table throughout the ritual. Early particles survived beyond the former 120-tick orbit cap. Pulse, spherical burst, fading opacity, release, four applied enchantments, and the exact 1395 → 845 XP debit passed. Pure disenchantment retained its reverse tail after release and produced no burst. The full fixture reached `CLIENT_ACTION_SMOKE_COMPLETE`.
- Actual optional-mod client: Supplementaries and Iron's Spellbooks pedestal materials disappeared in particle arrival order. A failed chain remained stable initially, developed noisy motion, then scattered upward, downward, and in both directions on each horizontal axis; actual rendered vertex alpha decreased while gravity pulled particles down. Successful chains completed alongside it, failed offerings remained intact, and a failed-only ritual left its tool unchanged without a success burst. Pure disenchantment also drained after release without a burst. The fixture reached `REFINEMENT_CLIENT_SMOKE_COMPLETE`.
- Gathered-ring, retained-XP-ring, noisy-trail, and failure-scatter screenshots were visually inspected. No trails remain in the gathered frame; the item and ring assembly sit above the table.
- Packaging checks confirm production-only classes, exact retained icon/page textures and default definitions, valid resources and mixins, version metadata, matching compiled class bytes, and SHA-256 checksums. Development fixtures are excluded from the JAR.

The temporary clients and validation servers were stopped after validation.

- [Version 1.0 build, unit tests and GameTests](validation/release-1.0-tests.log)
- [Version 1.0 unit totals](validation/release-1.0-unit-results.json)
- [Pre-promotion build, unit tests and GameTests](validation/trail-drain-1.6.4-tests.log)
- [Final baseline build](validation/final-package-1.6.4-build.log)
- [Unit totals](validation/trail-drain-1.6.4-unit-results.json)
- [Main actual-client observations](validation/trail-drain-1.6.4-client.log)
- [Failure and native-pedestal actual-client observations](validation/particle-refinement-1.6.4-client.log)
- [Sources stopped; last particles traveling](validation/trail-drain-1.6.4-draining.png)
- [All trails gathered into rings](validation/trail-drain-1.6.4-gathered.png)
- [XP ring retained after its trail drains](validation/trail-drain-1.6.4-xp-retained.png)
- [Noisy trail before failure](validation/particle-refinement-1.6.4-noisy.png)
- [Failure scatter](validation/particle-refinement-1.6.4-scatter.png)

Prior releases: [0.6.3](VALIDATION-0.6.3.md), [0.6.0 native compatibility](VALIDATION-0.6.0.md).
