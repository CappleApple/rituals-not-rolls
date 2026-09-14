# Testing notes - 1.1.2

Checked on 2026-09-13 with Java 21.0.12, Minecraft 1.21.1 and NeoForge 21.1.244. Earlier checks remain in [VALIDATION-1.1.1.md](VALIDATION-1.1.1.md).

## Data and automated checks

All 70 bundled definition/exclusion files were compared with 1.1.1. Exactly 13 particle IDs changed from `minecraft:enchant` to `minecraft:end_rod`: ten vanilla presets and the optional `critical_strike:chance`, `gouge:grip` and `notenoughtrials:equity` presets. All colors, materials, powers, thresholds and other fields were preserved.

None of the 68 active presets uses rune particles. Both generators and the example datapack use the replacements. Packaging rejects bundled rune presets. Production Java is unchanged, including the book placement and retrieval particle effects.

The build passed 66 unit tests and all 134 required GameTests. The existing mixed-enchantment effect test now expects Sharpness sparks and its original tint.

Evidence: [build and GameTests](validation/visible-presets-1.1.2-tests.log), [unit totals](validation/release-1.1.2-unit-results.json), [preset audit](validation/visible-presets-1.1.2-audit.json).

## Native client observation

The screenshot fixture ran the chain scene in a real Minecraft client connected to a dedicated development server. An older example datapack in the disposable test world was disabled because it overrode Sharpness with the previous rune preset. [The client log](validation/visible-presets-1.1.2-client.log) records the captures and verifies that the visible Sharpness chain uses the native `EndRodParticle` renderer. [The chain screenshot](validation/visible-presets-1.1.2-chains.png) shows the updated effects in an active ritual.

The screenshot is a visual check of the base-game fixture. Optional-mod presets were checked through their packaged definitions; this patch did not rerun those mods in a client.
