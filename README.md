# Rituals Not Rolls

Rituals Not Rolls replaces random-table enchanting with a physical, knowledge-driven system built around bookshelves, materials, and ritual pedestals.

Instead of repeatedly rolling the enchanting table, you discover how enchantments work, build a library around the table, and supply materials with the right affinities to power the enchantments you want.

Built for Minecraft 1.21.1 / NeoForge 21.1.244+.

## How enchanting works

The normal loop is:

1. Find **Knowledge Pages** in loot. A page teaches one material affinity for one enchantment.
2. Combine three leather and a page to create a **Knowledge Book**.
3. Store pages/books in chiseled bookshelves within 16 blocks of an enchanting table.
4. Put matching materials (and optional catalysts) on nearby Ritual Pedestals.
5. Drop the item you want to enchant toward the table.

The table captures the item and works through every compatible known enchantment it has enough material power to improve.

The enchanting-table screen shows the knowledge and material power currently available around the table. Open it while holding an item to show enchantments that fit that item, including enchantments already on it for subtraction. Open with an empty hand or a completely non-enchantable item to browse all nearby knowledge. Hover an enchantment for its name and description; enchantment names are gold and curse names are red by default, with white descriptions. Both name colors are client-configurable. Descriptions use the standard language keys from Enchantment Descriptions, so mods and resource packs can supply their own. Materials with zero current power omit the effective-power label.

The **Guide** button at the top right opens chapter-by-chapter screenshot examples and explains the library, power, catalysts, chains, costs and subtraction, using the server's current settings. Scroll through each chapter with the mouse wheel or scrollbar; click its screenshot to enlarge it. You do not need to open the screen before starting a ritual.

## Knowledge

Knowledge belongs to the physical library around the enchanting table, not to the player's inventory.

Knowledge books can collect compatible pages and show the materials known for each enchantment. Pages describe their affinity as **Weakest, Weak, Average, Strong,** or **Strongest** rather than exposing the raw balance number.

Auto-Add can absorb newly acquired matching pages into a carried book, while manual inventory rearrangement leaves pages loose. Book entries can also be torn back out.

Shift-click an enchantment in the table to summon its Knowledge Book from a shelf. If there is only loose-page knowledge, each click retrieves one page. The item flies above the table and drops. Throw a page onto the table to file it into a matching shelf book that lacks that discovery. Throw a Knowledge Book onto the table to file it into a random open chiseled bookshelf slot. Duplicates and items with no destination stay loose. Each transfer moves the real item along a short arc with magical particles.

Enchanted books from loot tables and non-player world drops become one random affinity page per configured enchantment, including mob drops, fishing, trial spawners and vaults. Unconfigured enchantments remain on the book. Player-thrown enchanted books remain available as ritual targets.

As a crafting fallback, place an enchanted book alone in either crafting grid to obtain one random page. If it has several configured enchantments, one is converted per craft in enchantment-ID order; the others remain on a returned book. Unconfigured enchantments and other book components are preserved.

The default data covers all vanilla enchantments plus a set of optional modded enchantments. Everything is datapack-driven, so packs can replace those defaults or add their own.

## Material power

Every enchantment defines materials and the amount of affinity/power each one contributes. The ritual adds up the useful materials around the table and compares that result with the configured threshold for the next enchantment level.

Item enchantability can optionally affect the requirement, with a configurable diminishing-returns curve for higher ratings. The bundled multi-level vanilla enchantments reach level X with increasing costs; vanilla enchantments that have only one level remain at I. Material sources and powers are unchanged. Datapack thresholds decide the maximum level.

When one physical pedestal is useful to several enchantments at once, its contribution can be shared between them. Route/return bonuses and conflict scaling are configurable as well.

The complete data format and balancing rules are documented in [docs/DATAPACKS.md](docs/DATAPACKS.md). The shipped values are listed in [docs/DEFAULTS.md](docs/DEFAULTS.md) and [docs/MOD_DEFAULTS.md](docs/MOD_DEFAULTS.md).

## Catalysts

Rituals Not Rolls has three reusable catalyst types.

### Consumption Catalyst

Attach one to a pedestal for 1.5× material power before duplicate diminishing returns. Consumed copies of the same item contribute progressively less by default (`1 / sqrt(n)` for the nth copy). Each participating marked pedestal spends one item, with item-break particles when its offering is taken. The catalyst remains intact.

Unmarked pedestals are not consumed.

### Subtraction Catalyst

Reverses a material's contribution so it can lower or remove an existing enchantment, including curses. It can be combined with a Consumption Catalyst when a pack wants stronger subtraction at the cost of the offering.

Subtraction only applies complete levels; partial removal progress is not stored.

### Experience Catalyst

An Experience Catalyst sits in the pedestal's displayed item slot and lets player XP add extra material power.

By default, each catalyst allows up to 10 equivalent XP levels of bonus, or roughly +10% power. Multiple catalysts expand that ceiling. The player who threw the target item pays the XP once when the ritual succeeds.

Exact XP/power behavior is configurable in the [server settings](docs/CONFIGURATION.md).

## Ritual presentation

A ritual keeps the real target item suspended above the table while particles travel from the library through the contributing pedestals and form rings around it.

Successful enchantments finish in a shared completion pulse. Failed chains destabilize and scatter without consuming their offerings, while successful chains in the same ritual can still finish normally. Subtraction reverses the flow for the affected enchantment.

The visual timing, colors/effects, sequential-vs-simultaneous chain starts, and several route settings are configurable. Resource packs can replace the page/book/GUI assets and sounds; see [docs/RESOURCE_PACKS.md](docs/RESOURCE_PACKS.md).

## Automatic selection

There is no enchantment roulette or “pick one of three” screen. The table considers compatible known enchantments and applies the ones that can actually reach a configured level with the current materials.

Existing enchantments keep their level unless the ritual can improve them or subtraction can lower them. Conflicting enchantments use deterministic ordering and configurable conflict penalties instead of random rolls.

## Configuration and pack support

Server settings are stored in:

```text
config/ritualsnotrolls-server.toml
```

The mod is designed to be pack-driven. Useful references:

- [Server settings, power equations and client colors](docs/CONFIGURATION.md)
- [Datapack format](docs/DATAPACKS.md)
- [Default vanilla material/threshold data](docs/DEFAULTS.md)
- [Optional mod defaults](docs/MOD_DEFAULTS.md)
- [Resource-pack assets and sounds](docs/RESOURCE_PACKS.md)
- [Public API and integrations](docs/API.md)
- [Migration from Immersive Enchanting](docs/MIGRATION.md)
- [Current testing notes](docs/VALIDATION.md)

Built-in compatibility includes native handling for Supplementaries and Iron's Spells 'n Spellbooks pedestals. Other shelves/pedestals can be exposed through tags and adapters.

## Migrating from Immersive Enchanting

When Immersive Enchanting is no longer installed, saved ancient books can be converted into matching Rituals Not Rolls affinity pages as inventories/chunks load.

Install Rituals Not Rolls **before opening the world without Immersive Enchanting**. Items that another version/mod has already erased cannot be reconstructed afterward.

See [docs/MIGRATION.md](docs/MIGRATION.md) before migrating an existing world.

## Commands

Operator commands require permission level 2:

```text
/ritual definitions
/ritual missing
/ritual givepage minecraft:sharpness diamond
/ritual inspectbook
/ritual network
/ritual validate
/reload
```

`/ritual missing` is useful for pack authors: it lists registered enchantments that do not currently have a loaded ritual definition.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.244 or newer compatible 21.1 build
- Java 21

Install the mod on both the server and clients. No additional mod is required for the core enchanting system.

JEI, EMI and REI are optional recipe viewers. Their Uses key on an enchanted book shows its page conversion; on a page it shows the page + three leather Knowledge Book recipe. Random results cycle through possible pages. Knowledge pages and books stay out of the item index. Each viewer has a native adapter, and EMI's adapter takes precedence when EMI and JEI are installed together.

## Building

```powershell
.\gradlew.bat test build runGameTestServer
python tools/package_release.py
```

Development fixtures and GameTest-only classes are excluded from release jars.

## License

Rituals Not Rolls is available under the MIT License.
