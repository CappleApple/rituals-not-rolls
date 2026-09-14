# Testing notes - 1.1.3

Checked on 2026-09-13 with Java 21.0.12, Minecraft 1.21.1 and NeoForge 21.1.244. Earlier evidence remains in [VALIDATION-1.1.2.md](VALIDATION-1.1.2.md).

## Data and automated checks

The 68 active presets now use 40 native particle types and 31 distinct sprite sets. Reuse is limited to four presets per particle ID. The [theme catalog](PARTICLES.md) explains each choice, including optional mod enchantments.

All 70 bundled definition/exclusion files were compared with 1.1.2. Only particle IDs and tints changed. Materials, source items, powers, thresholds, conflicts and exclusions were preserved. Both generators and the example datapack match the shipped definitions. No preset uses `minecraft:enchant`.

The build passed 66 unit tests and all 134 required GameTests. The mixed-enchantment test checks Sharpness blade slashes and Unbreaking coating sparks independently. Book placement/retrieval code is unchanged; the renderer preserves the prior rune path, including fallback runes.

Evidence: [build and GameTests](validation/themed-presets-1.1.3-tests.log), [unit totals](validation/release-1.1.3-unit-results.json), [preset audit](validation/themed-presets-1.1.3-audit.json).

## Native client checks

`runParticlePaletteClient`, connected to a dedicated development server, reads all 68 bundled presets, including optional definitions whose owning mods are absent. Each configured effect must resolve to its native provider and produce finite, nondegenerate quads at orbit ages through 550 ticks and completion-burst ages through 28 ticks. Visibility checks allow the vault particle's native first-quarter fade-in and require visible interior samples. The check covers the long cherry-leaf countdown that otherwise produces nonfinite motion.

The [native client log](validation/themed-presets-1.1.3-palette.log) records all 68 passing probes and six labeled screenshots. These verify the optional presets' native visuals; the optional mods' gameplay mechanics were not rerun for this cosmetic patch.

Screenshots: [1](validation/themed-presets-1.1.3-palette-1.png), [2](validation/themed-presets-1.1.3-palette-2.png), [3](validation/themed-presets-1.1.3-palette-3.png), [4](validation/themed-presets-1.1.3-palette-4.png), [5](validation/themed-presets-1.1.3-palette-5.png), [6](validation/themed-presets-1.1.3-palette-6.png).

The guide's active-ritual screenshots were recaptured with the updated effects; see the [guide capture log](validation/themed-presets-1.1.3-guide.log).
