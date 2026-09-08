# Changelog

## 1.0 — 2026-09-08

### Changed

- Promoted the completed development build to stable version 1.0; earlier versions are catalogued as 0.x.x.

- Incoming enchantment trails stop emitting together after all chains have formed; remaining particles reach the rings before the finishing pulse and burst.
- Early rings retain their particles through the shared completion, and long routes extend the drain phase.
- XP catalysts use the available experience up to their combined capacity, with bonuses based on the equivalent funded levels.
- XP ring size and particle density scale with the experience spent, and reference previews show the payable amount.
- XP gained during a ritual remains with the player; the captured payment and bonus stay fixed.
- The XP trail stops once its ring forms, while the ring remains through completion; XP radius growth is reduced to one tenth.
- The held item rises according to the largest tilted ring, keeping rings clear of the table.
- Destabilizing particles follow individual smooth noise along the same trail with gentler shared wobble.
- Failed-chain particles scatter in all directions from their current positions, retaining momentum while gravity pulls them down and they fade.

## 0.6.3 — 2026-09-08

### Added

- Ritual artwork as the in-game Mods screen icon.
- Right-click the enchanting-table search bar to clear its query and start a new search.

## 0.6.2 — 2026-09-08

### Added

- Enchanting-table searches match known material names and item IDs, including material-tag members, and narrow the detail panel to matching materials.

### Changed

- Clicking outside the search bar clears its typing focus without clearing the query.
- The inventory key closes the table when the search bar is unfocused and remains available for typing while it is focused.
- Searches with no matches show a search-specific empty state.

## 0.6.1 — 2026-09-08

### Changed

- The enchanting table displays short relative tiers for potential, base, and effective power instead of raw power values or numeric power multipliers.
- Pressing the inventory key keeps the enchanting-table reference open while preserving search input and Escape closing.

## 0.6.0 — 2026-09-08

### Added

- Supplementaries and Iron’s Spells ’n Spellbooks pedestal support, including attached modifiers, persistence, and rim icons.
- Per-enchantment insufficient-power animations that destabilize, fall with their momentum, and fade without spending offerings.
- Progressive instability based on power relative to the enchantment’s native maximum, with longer-lived near misses.
- Separate disenchant and failure sound events; explicit volume and pitch settings for every ritual event in sounds.json.

### Changed

- Successful offerings are taken in particle-arrival order and returned if the ritual is interrupted.
- Particle streams follow a continuous spline through the entire chain, with smooth turns and ring entrances.
- Consumption uses the Allay item-taken event at 0.5 pitch; disenchanting uses beacon deactivate at 0.6 pitch.
- Failed chains play distant firework twinkles with randomized 0.2–0.6 pitch.

## 0.5.4 — 2026-09-08

### Changed

- Ritual completion uses the beacon power-select sound at half volume.
- Particle streams follow one curved spline per hop and enter their ring at one shared point.
- Sequential channels wait for the preceding ring to fill from its single entrance.
- Enchanting Power tiers are named Weakest, Weak, Average, Strong, and Strongest.
- Knowledge pages are hidden from JEI and EMI item lists.
- When splitting cannot improve both enchantments, the one with higher available power takes priority.
- Disenchantment particles finish their reverse route after the item releases.
- Removal-only rituals skip the completion burst; mixed enchanting and disenchanting retain it.

## 0.5.3 — 2026-09-08

### Changed

- Ritual completion plays a soft amethyst chime at half volume instead of the challenge-completion fanfare.

## 0.5.2 — 2026-09-08

### Changed

- Knowledge pages display five relative Enchanting Power tiers, based on the strongest affinity in their enchantment's loaded definition.
- Knowledge-book entries use the same relative tiers.
- Visible Imbuement Power wording is now Enchanting Power.
- Removed the enchanting-table material-row explanation tooltip.

## 0.5.1 — 2026-09-08

### Added

- 26 built-in optional enchantment definitions, with themed materials from their owning mods where applicable.
- Distinct particle/color combinations and full-material-set native-maximum balancing for every added enchantment.
- `optional` and `enabled` definition controls for absent enchantments and explicit exclusions.

### Changed

- `/ritual missing` recognizes explicit disabled definitions for Storm Front Marker and legacy Arcane Assembly.

## 0.5.0 — 2026-09-08

### Added

- Loose knowledge pages can be inserted into chiseled bookshelves and supply their affinity to nearby rituals.
- Automatic conversion of saved Immersive Enchanting ancient books into matching knowledge pages when Immersive Enchanting is absent.
- Support for modern stored enchantments and the older NeoForge `no_network` component.
- Preservation of unsupported ancient books, with conversion retried on a later load after ritual data is added.

## 0.4.0 — 2026-09-07

### Added

- Conditional relative power sharing between enchantments that can each change a full level.
- Ordered curved pedestal chains, bookshelf/starter-based branch isolation, and configurable 50% power bonuses for return visits to the same pedestal.
- Subtraction Catalyst for reducing enchantments and curses, with reverse particle flow and proportionate surviving rings.
- Multiple pedestal modifiers, with icons spread across each oriented rim face.
- Configurable gentle enchantability scaling around a rating of 10.
- `/ritual missing` to list registered enchantments without loaded ritual data.

### Changed

- Consumption-marked duplicates each contribute power and consume their own single material; unconsumed duplicates do not extend chains.
- Chest enchanted books become knowledge pages only for configured enchantments; unsupported entries remain books.
- Default material palettes overlap less, and complete distinct sets reach the vanilla maximum at rating 10 without bonuses.
- The table reference shows signed potential power, return bonuses, and all contributing marked copies.
- The supplied page texture is the default and survives resource regeneration.

### Fixed

- Commit rejects changed routes, material components, modifiers, or power allocations before spending resources.
- Ineffective subtraction does not animate, consume materials, or charge XP.

## 0.3.0 — 2026-09-07

### Added

- Sequential enchantment animations, enabled by default, with a config option for simultaneous starts.
- Ring radius and particle density based on each enchantment’s imbuement power.

### Changed

- Earlier rings remain active as later enchantments start, and completion waits until all rings have formed.

### Fixed

- Knowledge pages spin around their center instead of orbiting an offset pivot.
- Knowledge pages have printed front/back faces and 3D edges following their texture silhouette.

## 0.2.1 — 2026-09-07

### Changed

- Orbiting particles pulse inward before a successful enchantment, then burst outward in every direction and fade away.
- Particle rings use different radii, heights, and tilts, including near-vertical planes.
- Completion bursts retain the selected enchantment effects/colors and experience-catalyst sparks.

## 0.2.0 — 2026-09-06

### Added

- Six-direction pedestal placement with matching collision shapes and upright displayed items.
- Consumption Catalyst icons on all four sides of the oriented pedestal rim.
- Curved particle flights, player-to-XP-catalyst streams, and an orbit that collapses into the enchanted target.
- Individual default particle effects/colors for all 42 vanilla enchantments.

### Changed

- Consumption Catalysts double and consume only their own pedestal’s material.
- Experience Catalysts occupy the displayed item slot and channel XP through their own pedestals.
- Mending requires 256 imbuement power.
- Material weights are assigned per enchantment, including 24 Knockback power versus 8 Sharpness power for iron.

### Fixed

- Duplicate materials prioritize the catalyst-marked copy without consuming an unmarked duplicate.
- Local catalyst changes during extraction trigger rollback even when other pedestals still have catalysts.
- Old attached Experience Catalysts move to an empty displayed slot or return to the world without losing either item.

## 0.1.0 — 2026-09-06

### Added

- Per-enchantment particle effects and optional RGB hex colors for both ritual routes.

### Changed

- Dropped targets now enchant automatically from compatible nearby knowledge and placed materials.
- The enchanting-table screen now only displays nearby known enchantments, material affinities, and power.
- Sacrifice catalyst presence doubles and consumes all contributing materials automatically.
- Each placed XP catalyst adds 10% power; combined ten-level increments determine the total XP cost.
- Knowledge books use the resolved enchanted-book model, and their interface uses Minecraft written-book pages.
- Knowledge and material particle paths now continue throughout channeling.

### Fixed

- Mouse-clicked navigation buttons stop highlighting when the pointer leaves; page boundaries disable their controls.
- Captured items float steadily with gravity disabled and restore their original physics on release or interruption.

- Ritual completion rechecks physical catalyst counts and the combined XP charge before spending resources.
- Active sessions remain safe when integration callbacks start another ritual.

## 0.0.0 — 2026-09-06

### Added

- Physical enchanting rituals with tradeable material knowledge pages and books, communal libraries, and pedestals.
- A replacement enchanting-table browser with level selection, material controls, XP amplification, and multi-enchantment previews.
- Consumption and Experience Catalysts, configurable conflict penalties, and enchantment levels defined by datapacks.
- Arcane Assembly table upgrades, nearby inventory access, real material transfers, and bounded automated planning.
- Definitions for every vanilla enchantment, stable knowledge IDs, reload support, and public integration adapters and events.
- Minecraft-style GUI sprites, dynamic knowledge item rendering, ritual particles and sounds, recipes, and chest discovery loot.
- Automated tests, example datapacks and resource packs, and customization documentation.
