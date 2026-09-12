# Datapack format

Minecraft 1.21.1 datapacks use `pack_format: 48`. The mod's built-in resources include both client assets and server data; external example datapacks declare the server format explicitly.

Definitions are JSON files under:

```text
data/<pack_namespace>/ritual_enchanting/enchantments/<name>.json
```

To replace a bundled definition, override its exact path, such as:

```text
data/ritualsnotrolls/ritual_enchanting/enchantments/sharpness.json
```

Do not add a second file for the same enchantment: duplicate enchantment IDs are diagnosed. Files are processed in deterministic resource-ID order. Resource-pack/datapack priority resolves overrides of the same path before loading.

```json
{
  "enchantment": "minecraft:sharpness",
  "materials": [
    { "id": "diamond", "item": "minecraft:diamond", "power": 30, "resource_value": 5 },
    { "id": "amethyst_group", "tag": "c:gems/amethyst", "power": 16, "resource_value": 2 }
  ],
  "levels": { "1": 15, "2": 35, "3": 75, "6": 320 },
  "conflict_groups": ["damage"],
  "particle": "minecraft:enchant",
  "particle_color": "#AFCFFF",
  "sound": "minecraft:block.enchantment_table.use"
}
```

| Field | Meaning |
| --- | --- |
| `enchantment` | Actual enchantment registry ID, including modded/data-defined enchantments |
| `optional` | Boolean, default false; skip this file when its exact enchantment is absent from the registry |
| `enabled` | Boolean, default true; false excludes the enchantment from rituals/discovery/migration and acknowledges it in `/ritual missing`. Only `enchantment` is required for a disabled file |
| `disabled_reason` | Optional explanation for an excluded entry |
| `optional` | Boolean, default false; skip this file when the exact enchantment is absent from the registry. Used by all bundled mod integrations |
| `enabled` | Boolean, default true; false explicitly excludes the enchantment from rituals/discovery/migration and acknowledges it in `/ritual missing`. Only `enchantment` is required for a disabled file |
| `disabled_reason` | Optional human-readable explanation for an excluded entry |
| `materials[].id` | Required stable logical affinity ID, unique within this enchantment, up to 128 characters |
| `item` / `tag` | Exactly one selector per affinity |
| `power` | Positive finite power; independent for each enchantment |
| `resource_value` | Optional nonnegative integration metadata, default 1; not used for automatic selection |
| `levels` | Numeric level keys 1–255 with strictly increasing finite positive thresholds; gaps are allowed |
| `conflict_groups` | Optional group references; members are declared here, not in a vanilla exclusion tag |
| `visual` | Optional complete texture ID for the knowledge-page inset icon, e.g. `mypack:textures/rituals/blade.png` |
| `particle` | Optional vanilla/mod simple particle registry ID, or `minecraft:dust`; defaults to `minecraft:enchant` |
| `particle_color` | Optional six-digit RGB hex string, with or without `#`; case-insensitive. Omit to keep native colors |
| `sound` | Optional completion sound event ID |

When several known entries match the same material, the maximum power wins; affinities never stack on that material. One unconsumed pedestal per actual item ID participates per enchantment; further unconsumed copies are excluded from the chain. Consumption-marked duplicates contribute with configurable diminishing returns; each participating copy consumes one item. Components do not change item identity. Each built-in pedestal accepts exactly one material. A tag discovery remains that tag discovery when tag membership changes.

Never rename an existing `id` merely to rebalance power. Pages and books store enchantment + stable affinity IDs, so changing selectors/power while keeping IDs preserves knowledge. Removing IDs preserves inactive entries and warns when a book is read or used by a library. Malformed files are logged and skipped independently rather than crashing the server. `/reload` publishes a new immutable definition snapshot and synchronizes it to connected clients.

## Ritual settings

Consumption strength, duplicate diminishing returns, XP capacity/bonuses, conflict multipliers and minimum duration live in `config/ritualsnotrolls-server.toml`. The former `data/ritualsnotrolls/ritual_enchanting/rules.json` is ignored with a warning. See [CONFIGURATION.md](CONFIGURATION.md) for defaults, equations, ranges and migration.

Consumption-marked duplicate pedestals still each spend one item when participating, but their power follows the configured diminishing-returns equation. Counts span every bookshelf branch for the same enchantment and actual item ID; applying and subtracting count separately. Zero-power copies do not participate. Experience Catalysts count only in displayed item slots. A Subtraction Catalyst reverses a material's contribution and can coexist with consumption. All catalysts remain intact.

The first automatically chosen enchantment supplies an optional completion sound. Each chosen enchantment independently supplies its own path effect and optional color. Shared materials retain a separate route per enchantment. Knowledge-to-material and material-to-target flights follow curved routes throughout the ritual. Arrivals orbit the target on stable tilted planes, including near-vertical rings. Each enchantment’s actual enchanting power, including local sacrifice and XP bonuses, sets its radius: `clamp(0.42 + 0.045 × sqrt(power), 0.42, 2.4)` blocks. Its emission weight is `ceil(radius × 14)`; larger rings receive more particles, up to a shared sixteen-flight budget every two ticks. The item and shared ring center rise to keep every tilted ring above the table, including its motion and a sprite margin. The rings pulse inward during the final 28 ticks. Successful enchanting then releases a spherical burst using the selected effects/colors, fading over 32–44 ticks. Green player-to-XP-catalyst routes lead into catalyst-to-target routes. The XP radius is `clamp(0.42 + 0.0045 × sqrt(paid_xp), 0.42, 2.4)` blocks. XP emission stops once its ring forms, and that ring remains through the shared completion. Custom phase sounds can be replaced independently. Unknown particle IDs and parameterized types other than `minecraft:dust` fall back to enchantment glyphs; block/item particles need extra parameters and are not supported by this ID-only field. Dust uses size 1 and the specified RGB (white when omitted). Invalid hex strings reject that definition with a reload diagnostic.

## Sharing, chains, and subtraction

Allocation uses physical pedestal positions. Each contribution is divided by the number of enchantments that meaningfully use that pedestal after sharing. An affinity that cannot change a full level is removed from that allocation, allowing its other users more power. Consumed duplicates of the same item are considered together so individually redundant copies are still consumed when that material benefits the enchantment. Automatic admission tries the highest available unsplit chain power first, including consumption, XP and return bonuses; subtraction compares power magnitude. Equal powers use registry-ID order. When sharing cannot give every admitted enchantment a level change, the lower-priority candidate is skipped. Sharing still applies when every admitted enchantment benefits.

With `chainPedestalAnimations = true`, books and their nearest relevant starter pedestals define spatial branches. Ownership minimizes bookshelf-to-starter plus starter-to-material distance; ties across different enchantments share the physical material. Within each branch, the outward order follows increasing bookshelf distance, then the flow returns toward the table through earlier pedestals inside `chainReturnCorridor` (default 1.25 blocks). Applying and subtracting use separate directional routes. A return visit adds `chainReturnBonus` (default 0.5) to that same pedestal's power, before consumption/XP/sharing. Unconsumed materials also receive the return bonus and remain intact. Separate unconsumed copies of the same item stay excluded from the enchantment chain. A physical pedestal is extracted at most once. With chaining disabled, all book-to-material flights start together and return bonuses do not apply.

The target cost multiplier defaults to `(10 / effectiveEnchantability) ^ 0.5`, multiplied by the existing conflict cost. Above rating 10, effective enchantability defaults to `10 * (1 + log(itemEnchantability / 10))`. Lower ratings are unchanged, with a minimum of 1. `useItemEnchantability`, `baseEnchantability`, `enchantabilityExponent`, and `enchantabilityEquation` are server-config options. Exponent 0 disables the rating's influence; exponent 1 gives the stronger inverse-ratio scaling. Books are neutral. The item rating comes from NeoForge's stack-aware `Item.getEnchantmentValue(ItemStack)` hook.

A Subtraction Catalyst makes a pedestal contribution negative. Net negative power lowers the current cost budget until whole levels can be removed; level zero removes the enchantment, including curses. Gaps in configured levels use interpolated costs for existing intermediate levels. Existing levels beyond the configured maximum use a quadratic extrapolation of its last cost. Weak subtraction that changes no level starts no ritual and spends nothing. Mixed incoming/subtracted power is resolved before choosing an attainable level. Successful partial subtraction retains a fraction `(originalPower - subtractedPower) / originalPower` of the orbit emissions, clamped to 0–1. Reverse flights travel from target through the reversed pedestal route toward the book.

## Bundled balance

All 42 vanilla definitions explicitly assign their own material weights and unique particle/color pairs. Iron gives 8 power to Sharpness and 24 to Knockback; Mending I costs 256 power. At enchantability 10, the complete distinct set of each default definition's explicit materials totals exactly the vanilla maximum level's threshold, before consumption, XP, return visits, sharing, and conflict penalties. Removing any one of those materials falls short. All vanilla enchantments with a native maximum above I have explicit thresholds through X. Existing thresholds are preserved; VIII–X each cost 50% more than the preceding level. Single-level enchantments remain at I. This is a shipped-data convention only, not a validation rule for custom definitions. Material overlap between enchants on the same equipment has been reduced. The full [defaults catalog](DEFAULTS.md) is generated from the shipped JSON. Removed default affinities remain stored as inactive knowledge until their IDs are restored by a pack.

## Page distribution and crafting

The default global loot modifier is `data/ritualsnotrolls/loot_modifiers/discovery.json`:

```json
{ "type": "ritualsnotrolls:discovery", "conditions": [], "chance": 0.0 }
```

The modifier replaces each configured enchantment on an enchanted book with one random affinity page in every loot-table context. This includes chest, fishing, entity, trial-spawner and vault loot. Unconfigured enchantments stay on the original book, preserving its other components. Non-book loot is unchanged. The optional `chance` adds an extra random page to chest loot only; it defaults to 0 and does not disable conversion. Chest contexts are recognized by a `chests/` path or loot-container block entity at the origin.

New non-player enchanted-book item entities also use this conversion, covering direct world drops that bypass loot tables. Existing saved entities and player-thrown enchanted books are left alone. Overriding the global loot modifier list disables the loot-table hook; it does not disable direct world-drop conversion. Removing or disabling an enchantment definition excludes it from both conversion paths.

Other loot tables can create exact pages using `minecraft:set_components` and this component value:

```json
{
  "ritualsnotrolls:knowledge": {
    "enchantment": "minecraft:sharpness",
    "entries": ["diamond"],
    "auto_add": true
  }
}
```

The page item ID is `ritualsnotrolls:knowledge_page`. Books use `ritualsnotrolls:knowledge_book` with the same component, a unique entry list, and their per-stack auto-add setting. Do not create page stacks containing more than one entry; interaction code rejects those malformed pages.

The dynamic 3-leather/page recipe has type `ritualsnotrolls:knowledge_book`. The one-book crafting fallback has type `ritualsnotrolls:enchanted_book_page`, defined at `data/ritualsnotrolls/recipe/enchanted_book_page.json`. It converts the first configured enchantment in sorted ID order into one random affinity and returns the book with any other enchantments. Both fit the inventory crafting grid. Other recipes are ordinary 1.21.1 `recipe` JSON files. Both knowledge books and loose knowledge pages are accepted. Default chiseled-bookshelf acceptance is added through `data/minecraft/tags/item/bookshelf_books.json`.

## Example pack

`examples/datapack` is an installable override pack. Its Sharpness file keeps all shipped affinity IDs and changes the Sharpness VI threshold while illustrating the optional effect/color fields. Copy the folder into a world's `datapacks`, or use the ZIP delivered under `dist`. Run `/reload`, then `/ritual validate`.

## Finding unsupported enchantments

Run `/ritual missing` as an operator or from the server console. It compares the live enchantment registry against successfully loaded ritual definitions, prints each missing ID, and suggests `data/<namespace>/ritual_enchanting/enchantments/<path>.json`. A malformed file remains missing. Use `/ritual validate` and the reload log to diagnose it. Entries with `enabled: false` count as explicitly configured and are not reported as missing. The bundled legacy `ritualsnotrolls:arcane_assembly` and internal `notenoughtrials:storm_front_marker` entries are excluded this way.

Ancient-book migration uses the loaded ritual definitions to choose one matching affinity page. Adding a definition enables pending books to convert the next time their items load. See [migration](MIGRATION.md) for formats, preservation, and loading scope.

## Optional mod defaults

The mod ships 26 optional definitions in each enchantment's namespace, for example `data/create/ritual_enchanting/enchantments/capacity.json`. Override the exact path. Their powers and native maxima are listed in [MOD_DEFAULTS.md](MOD_DEFAULTS.md). They remain inactive when their enchantment is missing, including Soul Fire'd's enchantments under the `minecraft` namespace and enchantments disabled through a mod's own configuration. Non-optional custom definitions retain unresolved-ID warnings.

To exclude an enchantment, replace its file with:

```json
{
  "enchantment": "yourmod:internal_marker",
  "enabled": false,
  "disabled_reason": "Internal implementation detail, not player knowledge"
}
```

To re-enable an exclusion, supply a complete definition with materials and levels and set `enabled` to true or omit it. Disabled entries never create knowledge pages or convert legacy ancient books.

## Relative page labels

Page tooltips and book entries derive a tier from `affinity.power / max(all materials[].power)` for the same enchantment in the active definition. All entries count toward the reference maximum, including undiscovered affinities and tag affinities. No percentage is rounded before selecting a tier: 19.99% is Weakest, 20% is Weak, 40% is Average, 60% is Strong, and 80% or more is Strongest. A sole affinity or tied strongest affinity is Strongest. `/reload` and client definition synchronization update these labels without changing saved pages. The tiers are presentation only; numerical power, thresholds, modifiers, sharing, costs, and animation calculations are unchanged.

### Partial experience budgets

The combined catalyst capacity still uses `rules.xpLevelsPerCatalyst` and costs the XP needed to reach that total level from zero. The payable amount is capped by the player's available XP. Convert that amount back to equivalent levels, including fractional progress to the next level; the bonus is `equivalent_levels / xpLevelsPerCatalyst * xpBonusPerCatalyst`. Thus the defaults give +15% for 315 XP with two catalysts. Zero available XP contributes no bonus or XP particles and does not block an otherwise viable ritual. Menu previews, allocation, failure previews, payment and XP ring size all use the same budget.
