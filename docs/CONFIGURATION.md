# Configuration

Server settings live in `config/ritualsnotrolls-server.toml`. A file of the same name in a world's `serverconfig` folder overrides the instance file. Personal tooltip colors live in `config/ritualsnotrolls-client.toml`.

Enchantments, material affinities and level thresholds remain datapack data; see [DATAPACKS.md](DATAPACKS.md). `data/ritualsnotrolls/ritual_enchanting/rules.json` is retired and ignored with a warning. Move any overrides from that file into the server settings below.

## Consumption and duplicate materials

```toml
[rules]
consumptionMultiplier = 1.5
duplicateConsumptionEquation = "1 / sqrt(n)"
xpLevelsPerCatalyst = 10
xpBonusPerCatalyst = 0.1
durationTicks = 120
conflictMultipliers = ["protection=2", "damage=2", "boots=2", "bow=2", "crossbow=2", "trident=2", "mining=2", "mace=2"]
```

For each enchantment, consumed copies of the same actual item ID are ranked by their contribution, strongest first; equal contributions use pedestal position. The nth copy receives:

```text
base material power × consumptionMultiplier × duplicateConsumptionEquation(n)
```

Route visits, XP and sharing still apply. Counts span all bookshelf branches for that enchantment. Applying and subtracting have separate counts. Components and overlapping affinity entries do not create different item identities. Unmarked reusable copies do not advance the consumed-copy count; only one reusable copy of an item can contribute to an enchantment.

The default `1 / sqrt(n)` keeps the first consumed copy at full strength and reduces each additional copy:

| Consumed copy | Fraction retained | Power relative to the original material, with 1.5× consumption |
| --- | --- | --- |
| 1 | 100% | 1.5× |
| 2 | 70.71% | 1.061× |
| 3 | 57.74% | 0.866× |
| 4 | 50% | 0.75× |

Use `"1"` for equal contributions from all consumed copies, `"1 / n"` for stronger diminishing returns, or `"1 / n ^ 0.25"` for a gentler curve. The result is clamped to 0–1. A zero-power copy is excluded and not consumed. Every participating marked pedestal still spends one item, not a fraction of an item. Return visits do not charge it again. Catalysts remain intact.

Offering removal sends twelve native item-break particles from that pedestal's displayed-item position. This happens when the leading ritual trail reaches it. Interrupted reservations are refunded; failed chains do not reserve offerings.

## Item enchantability

These settings are at the top level of the server file, before `[rules]`:

```toml
useItemEnchantability = true
baseEnchantability = 10.0
enchantabilityExponent = 0.5
enchantabilityEquation = "base * (1 + log(rating / base))"
```

`rating` is the item's stack-aware enchantability; `base` is `baseEnchantability`. Ratings above the base pass through `enchantabilityEquation`. Ratings at or below it keep their original value, with a minimum of 1. The effective rating is clamped between the base and the original rating.

The final cost multiplier remains:

```text
(baseEnchantability / effective rating) ^ enchantabilityExponent
```

With the defaults, ratings 10, 20 and 30 cost about 100%, 77% and 69% of the neutral requirement. Higher ratings help progressively less. Books remain neutral. Set the equation to `"rating"` to restore the previous rating behavior; set `useItemEnchantability = false` or `enchantabilityExponent = 0` to remove the cost adjustment.

Both equation settings support numbers, parentheses, `+`, `-`, `*`, `/`, `^`, `sqrt(x)`, natural `log(x)`, `min(a,b)`, `max(a,b)` and `pow(a,b)`. Exponentiation associates to the right and precedes unary minus. Equations are limited to 256 characters. Invalid syntax or invalid sampled results are rejected by config validation. If a different input later produces a nonfinite result, that evaluation uses the default curve. There is no scripting or access to game state from expressions.

## Experience, conflicts and timing

`xpLevelsPerCatalyst` is the capacity of each displayed Experience Catalyst, in equivalent levels earned from zero. `xpBonusPerCatalyst` is its full power bonus. With the defaults, one fully funded catalyst costs 160 XP for +10%; two cost 550 XP for +20%. Bonuses add instead of compounding. Partial XP funds a proportional bonus based on equivalent levels, including fractional progress. Zero XP does not block material-only enchanting. XP is paid once on success, and XP gained after capture is retained.

`conflictMultipliers` lists `group=base` entries. The nth enchantment in a group costs `base^(n-1)` times its ordinary threshold. Existing enchantments occupy positions first. Groups referenced by definitions but absent from this list use base 2. Add custom group names here; later duplicate entries override earlier ones. Values must be 1–100.

`durationTicks` is a minimum of 20–12000 ticks. Long routes extend the ritual so trails and rings can finish. `consumptionMultiplier` accepts 1–100, `xpLevelsPerCatalyst` accepts 1–1000, and `xpBonusPerCatalyst` accepts 0–100.

The existing top-level spatial and presentation options remain:

| Setting | Default | Behavior |
| --- | --- | --- |
| `ritualRadius` | 16 | Spherical range for loaded shelves and pedestals |
| `inventoryRadius` | 16 | Nearby inventory range used by inventory integrations |
| `networkRefreshTicks` | 100 | Fallback topology refresh interval |
| `dropCaptureWindowTicks` | 200 | Time a newly thrown item can begin a ritual |
| `sequentialEnchantmentAnimations` | true | Start each enchantment after the preceding ring forms |
| `chainPedestalAnimations` | true | Route through spatial pedestal branches |
| `chainReturnBonus` | 0.5 | Extra contribution from a return visit |
| `chainReturnCorridor` | 1.25 | Maximum sideways distance from the return route |

Config reloads refresh the server rule snapshot and synchronize it to connected clients. An active ritual is invalidated by the new revision, and reserved offerings are refunded. The guide displays current server settings. If config file watching is disabled, restart the server after editing. Datapack `/reload` also picks up the currently loaded config values; it does not itself reread TOML files.

## Client tooltip colors

```toml
enchantmentTooltipColor = "#FFAA00"
curseTooltipColor = "#FF5555"
```

These colors apply only to enchantment and curse names in the table's hover tooltip. Descriptions always use standard white. Colors use six-digit `#RRGGBB` values. Defaults are Minecraft gold and red. Curses are identified by the registry's curse tag, including tagged modded enchantments. Color choices are local to each client.

All 42 vanilla enchantments have concise descriptions under the standard `enchantment.<namespace>.<path>.desc` language keys used by Enchantment Descriptions. The same `.description` / `.info`, level-specific and custom-name fallbacks are supported. Resource packs can replace descriptions and supply missing modded entries; see [RESOURCE_PACKS.md](RESOURCE_PACKS.md).

## Library transfers

Shelf retrieval and filing use `ritualRadius` and loaded chunks only. Shift-click retrieval prefers a Knowledge Book, then takes one loose page. A thrown page chooses the nearest matching book that lacks its entry. A thrown Knowledge Book chooses randomly among empty, unreserved chiseled bookshelf slots. Filing a stack of pages consumes one page and leaves its remaining copies loose. Filing ignores a book's inventory Auto-Add setting because throwing onto the table is an explicit action.

Transfers take roughly one to two and a half seconds, depending on distance. The real dropped item follows an arc with enchantment and end-rod particles. Shelf destinations are reserved during flight and checked again at arrival. A removed table, changed destination, or interrupted transfer releases the item without spending it. Saved in-flight entities recover their gravity when loaded. Retrieved pages stay loose on pickup instead of immediately auto-adding to a carried book.
