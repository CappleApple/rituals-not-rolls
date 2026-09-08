# Rituals Not Rolls

Physical, knowledge-driven enchanting for **Minecraft 1.21.1 / NeoForge 21.1.244+**. Version **1.0**, Java 21, namespace `com.cappleapple.ritualsnotrolls`, mod ID `ritualsnotrolls`.

## Install

Download the JAR from the [1.0 release](https://github.com/CappleApple/rituals-not-rolls/releases/tag/v1.0) and put `ritualsnotrolls-1.0.jar` in `mods` on both client and server. No additional mods are required. Remove the previous version's JAR. The release also includes a source ZIP, optional example packs, and SHA-256 checksums. Local packaging writes these files to `dist`.

Version 1.0 is the first stable release. Earlier development versions use 0.x.x numbering; see the [version history](docs/VERSION_HISTORY.md).

## Migrating from Immersive Enchanting

When Immersive Enchanting is absent, saved ancient books automatically become one matching affinity page per book as inventories and chunks load. Both modern stored-enchantment data and the older NeoForge `no_network` format are supported. Unsupported books retain their original data in a labeled fallback book and retry on a later load when ritual data is available. Install this version before opening the world without Immersive Enchanting; already-erased items cannot be recovered. See [migration details](docs/MIGRATION.md).

## Enchanting

1. Discover **Knowledge Pages** in chest loot, replacing enchanted books for configured enchantments. Each page teaches one material affinity for one enchantment; it does not unlock an enchantment level.
2. Craft **three leather and one page** into a knowledge book. Use the book to read its materials, toggle Auto-Add, gather pages, or tear entries back out.
3. Put loose pages or knowledge books in **chiseled bookshelves within 16 blocks** of an enchanting table. Knowledge in a player's inventory does not participate. Libraries can be communal.
4. Place materials and optional catalysts on nearby **Ritual Pedestals**. Pedestals attach in any of six directions; displayed items always stay upright.
5. **Drop one item toward the enchanting table.** It automatically captures the item and applies every compatible known enchantment it can improve, at the highest level the current materials can power.

There is no arming, target slot, enchantment selection, XP allocation slider, or preparation step. Opening the table shows a read-only reference containing only currently known nearby enchantments, their known materials, base and effective power, and physical catalyst totals. Opening the UI is never required to enchant.

The ritual keeps the real dropped item suspended above the table, raising it according to the largest tilted ring so the rings stay clear of the table. Particles stream from relevant bookshelves to contributing pedestals and from pedestals to the table **throughout** channeling. By default, each enchantment starts after the previous ring forms; earlier rings keep running. Each complete chain follows one continuous spline through its pedestals into a fixed ring entrance, with shared tangents through turns and return visits. Arriving particles spread into tilted rings, including near-vertical planes. Each enchantment’s actual enchanting power determines its ring radius and share of particles. The rings pulse inward, then successful enchanting releases a spherical burst that spreads outward and fades. Each enchantment has its own default effect/color combination, configurable in its datapack definition. Catalysts and resources are rechecked before completion. Insufficient positive power still captures the tool and animates that enchantment chain: it starts stable, becomes shaky as the ring forms, then scatters in every direction with its existing momentum, gravity, and a gradual fade. Destabilization adds smooth individual noise along the same trail, with a smaller shared wobble. Near misses hold together longer. Other successful chains finish normally; failed chains spend no offerings, and a failed-only attempt spends no XP. Successful chains also become shakier when their power is farther below the cost of the enchantment’s native maximum level, before ritual-defined extra levels.

Once every participating chain has formed its ring or finished failing, all incoming enchantment sources stop together. Existing trail particles continue along their full routes and join the rings; the final inward pulse and completion burst wait until the longest trail has arrived. Orbiting particles remain until completion, including those from early sequential chains.

## Catalysts

Right-click with a **Consumption Catalyst** or **Subtraction Catalyst** to attach it to a pedestal. Both can be attached together; their icons spread across all four sides of the oriented top rim. Each pedestal holds exactly one material. **Experience Catalysts occupy the displayed item slot**, just like an ordinary pedestal item. Sneak-use removes an attachment first, then the displayed item. All catalyst items are reusable.

**Consumption Catalyst:** doubles only its own pedestal’s material power and takes that material as its successful chain reaches the pedestal. Reserved offerings are returned if the ritual is interrupted; a completed ritual consumes each once. Unmarked pedestals remain intact. One unconsumed copy of each actual item ID contributes per enchantment. Extra unconsumed copies are excluded from the chain. Every contributing consumption-marked copy adds its own doubled power and is consumed once, even when several pedestals hold the same item. An unrelated or empty marked pedestal does not boost other materials.

**Subtraction Catalyst:** reverses its material’s power to reduce or remove an existing enchantment, including curses. Combine it with consumption to double the subtraction. A change must reach a full level; weak subtraction starts no animation and spends no materials or XP. Successful subtraction reverses the particle route, with a proportionate surviving ring for partial removal. The item releases on completion while the last reverse-flow particles finish their route afterward. Removal-only rituals have no completion burst; mixed enchanting/disenchanting rituals still burst.

**Experience Catalyst:** each displayed catalyst permits up to **10%** extra power and **10 levels** in the combined cost basis. The XP charge is the amount needed to reach that combined level **from zero**, capped by the throwing player's available XP.

| XP catalysts | Power multiplier | Cost basis | XP charged |
| --- | --- | --- | --- |
| 0 | ×1.0 | 0 levels | 0 |
| 1 | ×1.1 | 10 levels | 160 |
| 2 | ×1.2 | 20 levels | 550 |
| 3 | ×1.3 | 30 levels | 1,395 |
| 4 | ×1.4 | 40 levels | 2,920 |

Two catalysts cost 550 XP total, not two separate 160-XP payments and not removing 20 levels from the player's current level. The player who dropped the target pays once after successful validation. If the player has less XP, the ritual uses the available amount and scales its bonus by the equivalent levels from zero, including partial level progress. For example, two catalysts with 15 levels available spend 315 XP for +15% power. With zero XP, the materials can still enchant at their unboosted power. The payment and bonus are captured when the ritual starts; XP gained during the animation is retained, while losing the reserved amount cancels the ritual without a further debit. Catalysts carried in inventories have no effect. During channeling, green experience particles curve from the throwing player into each XP catalyst, then from each catalyst into the orbit around the target. The XP ring's radius and particle density grow gently with the actual XP being spent, using one tenth of the enchantment ring's radius growth factor. Its source stops once the XP ring forms; the last traveling particles join that ring, which remains through completion. Zero-XP rituals have no XP stream.

When loading older pedestal data, an XP catalyst in the former attachment slot moves into an empty display slot. If occupied, the material stays and the XP catalyst is returned as a dropped item.

## Power and automatic selection

For each enchantment, a material uses its **strongest matching known affinity**. Different items in a tag remain distinct. Power is divided by the number of enchantments a physical pedestal meaningfully serves, using each enchantment's own affinity value. A 20-power Looting / 10-power Sharpness material becomes 10 / 5 when sharing lets both change a level. An enchantment that cannot gain a level does not reserve a share. If sharing cannot raise both, the enchantment with the higher available unsplit power takes priority, including modifiers and return bonuses; equal power uses registry-ID order. Three, four, or more beneficial shares work the same way.

By default, each chain travels **bookshelf → nearest relevant pedestal → next nearest → farthest → previously visited pedestals on the way back → table**. Distances for the outward order are measured from the bookshelf. Returning through the **same physical pedestal** gives that material 150% of its base power, including when it has no Consumption Catalyst; it is not another full contribution and does not consume a second item. Only pedestals within the configurable return corridor qualify.

Bookshelves and the nearest matching starter material define branches. A downstream pedestal follows the cheapest source-to-starter-to-pedestal route. Equal-distance branches may share it. Separate shelves, or different non-overlapping starter materials near a shared shelf, can give different enchantments their own material copies at full power.

```text
contribution = affinity power × (consumption ? 2 : 1)
             × (revisited on return ? 1.5 : 1)
             × (1 + 0.01 × equivalent XP levels actually funded)
             ÷ number of enchantments meaningfully using this pedestal
             × (subtraction ? -1 : 1)

required = configured level threshold × conflict penalty
         × (10 / max(1, item enchantability)) ^ 0.5
```

Enchantability 10 is neutral. Lower ratings increase costs; higher ratings decrease them. The square-root scaling is gentle and configurable; books use the neutral baseline. Subtraction uses the same scaled thresholds and removes only complete levels. No partial progress is stored.

Existing enchantments keep their levels unless the ritual can improve them or effective subtraction lowers them. New compatible enchantments are considered by highest available unsplit power first, with stable `namespace:path` tie-breaking. Conflict positions still use stable registry-ID order. Existing enchantments occupy conflict positions first; each successfully added conflicting member increases the next member's required power (default ×1, ×2, ×4, ×8). An enchantment that cannot be applied does not reserve a conflict position. Multiple groups use the largest applicable penalty. Vanilla mutual exclusion does not block these combinations; actual target support is checked through NeoForge's `ItemStack.supportsEnchantment` hook.

All material powers are assigned per enchantment. For example, an iron ingot gives **8 Sharpness power** and **24 Knockback power**. **Mending requires 256 base power**. Each shipped enchantment is balanced so its complete distinct material set, at rating 10 without bonuses or sharing, exactly reaches the vanilla maximum. Any missing material falls short. This is a default-data balance choice; custom datapacks can use any valid thresholds. See the [default affinity and effect catalog](docs/DEFAULTS.md).

The maximum is the highest configured threshold, not the vanilla maximum. Minecraft's component codec supports levels up to 255. Some enchantment effects have internal clamps or boolean behavior that higher stored levels cannot change.

## Knowledge items and screens

Knowledge pages show **Weakest, Weak, Average, Strong, or Strongest Enchanting Power** instead of a number. Their affinity is compared with the strongest affinity in that enchantment's full loaded datapack definition: below 20%, 20–under 40%, 40–under 60%, 60–under 80%, and 80–100%, respectively. Knowledge pages are hidden from JEI and EMI item lists. Unknown affinities remain unresolved. Book entries show the same tier, with the full label on hover. The enchanting-table reference uses the same short tier names for potential, base, and effective power, without the Enchanting Power suffix or raw power numbers. Negative effective power keeps a minus sign. XP costs and library counts remain numeric. Search accepts enchantment names/IDs and known material item names/IDs, including every item in a material tag. Item searches show matching enchantments and narrow their material details. Right-clicking the search bar clears the query and keeps it ready for typing. Clicking anywhere outside the search bar clears typing focus while keeping the query. The inventory key closes the table unless the search bar is focused; inventory-key letters remain typeable while searching. Escape always closes normally.

Knowledge books render as their **actual resolved enchanted-book model**, including enchantment metadata for compatible model/CIT integrations. They have no custom leather cover or page-like item frame. Knowledge pages retain their separate worn-paper appearance, with centered rotation, printed front/back faces, and a one-pixel-thick edge following the texture silhouette.

The book interface uses Minecraft's **written-book page texture** and page-turn arrows. It displays material icons, power, page counts, tear-out controls, Auto-Add, and Gather Pages.

Auto-Add is enabled per book by default. External acquisition, world pickups, and external shift transfers can absorb matching new pages; manual rearrangement inside the player's inventory keeps pages loose. Duplicate entries are preserved as loose pages. Tearing entries out is safe when the inventory is full. Double-clicking a carried book gathers compatible accessible pages.

## Configuration and integration

- [Datapack definitions and catalyst rules](docs/DATAPACKS.md)
- [Book/page models, GUI textures, and sounds](docs/RESOURCE_PACKS.md)
- [Public adapters, events, and inventory contracts](docs/API.md)
- [Validation evidence and real-client screenshots](docs/VALIDATION.md)

In `config/ritualsnotrolls-server.toml`, use:

```toml
sequentialEnchantmentAnimations = true
chainPedestalAnimations = true
chainReturnBonus = 0.5
chainReturnCorridor = 1.25
useItemEnchantability = true
baseEnchantability = 10.0
enchantabilityExponent = 0.5
```

Set `sequentialEnchantmentAnimations` to `false` for simultaneous enchantment starts. Set `chainPedestalAnimations` to `false` for concurrent bookshelf-to-material routes followed by their table routes, with no return bonus. If a world has its own serverconfig override, edit that file. New rituals pick up the setting; existing rituals retain their captured timing.

Server configuration controls ritual radius (16), topology cache refresh (100 ticks), and the capture window for newly thrown items (200 ticks). Only loaded chunks participate. Item-join events track possible targets; idle tables perform no network polling. Active particle paths are resolved once, with a fixed emission budget. The old inventory scan radius remains available to integration/debug queries and does not cause automatic retrieval.

Definitions cover all **42 vanilla enchantments**, plus **26 optional enchantments** from Aggro Fix, Botany Pots, Combat Roll, Create, Critical Strike, When Dungeons Arise, Farmer's Delight, Gouge, Soul Fire'd, Not Enough Trials, Passable Foliage, Supplementaries, and Vein Mining. See [the modded material and power catalog](docs/MOD_DEFAULTS.md). Optional data activates only when its exact enchantment exists. Internal Storm Front Marker and legacy Arcane Assembly entries are explicitly disabled and omitted from `/ritual missing`. Supplementaries and Iron’s Spells ’n Spellbooks pedestals have built-in native-inventory support, including displayed XP catalysts, both attached modifiers, persistence, and modifier icons on their upright top rims. Their normal item rendering and placement behavior are retained. Other bookshelves and pedestals can be supplied through block tags and adapters. CIT packs require their own compatibility testing. Existing Arcane Assembly registry/attachment data is preserved for old worlds, but no upgrade is required and the old preparation controls are gone.

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

`/ritual missing` lists registered enchantments without loaded ritual definitions and suggests JSON file paths. Unsupported enchanted books remain in chest loot; a mixed book retains its unsupported enchantments and produces pages for its configured ones.

## Build

```powershell
.\gradlew.bat test build runGameTestServer
python tools/package_release.py
```

Development-only GameTests and client fixture classes are excluded from the distribution JAR. See the validation document before using the opt-in fixture, which changes its disposable test world and player's inventory.

MIT licensed. Authored page, pedestal, catalyst, and GUI sprites are included; the written-book screen and enchanted-book model resolve Minecraft resources at runtime.
