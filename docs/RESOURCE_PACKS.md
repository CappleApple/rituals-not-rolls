# Resource pack customization

Use Minecraft 1.21.1 resource pack format **34**. Mod assets live under `assets/ritualsnotrolls/`.

## Knowledge pages and books

Knowledge books use an actual `minecraft:enchanted_book` stack carrying the corresponding `minecraft:stored_enchantments` component, rendered through the item model with the current world and player. Its own model chooses the appearance. There is no custom book cover or inset. Compatible CIT/model integrations can distinguish enchantments; vanilla itself uses one enchanted-book icon. A pack requiring a CIT loader still requires its normal compatible loader.

Knowledge pages use the supplied default page base at `textures/item/page_base.png` and their sepia enchanted-book inset. An optional full-page texture for `othermod:enchanted_edge` is:

```text
assets/ritualsnotrolls/textures/item/knowledge_pages/othermod/enchanted_edge.png
```

For `minecraft:sharpness`, use `.../minecraft/sharpness.png`. Transparent square textures, normally 16×16 or 32×32, work best. Nested enchantment paths retain their subdirectories. The definition's optional `visual` field replaces only the page inset. The client integration method `KnowledgeRenderer.registerIconProvider` can supply an actual resolved visual stack.

Pages use centered geometry with a 1/16-block model thickness. Front and back faces are printed, and side faces follow opaque pixels, including holes and irregular borders in full-page overrides. This keeps dropped and pedestal pages spinning on their own axis. The mesh is cached per texture.

Resource reload clears the mesh and override caches and refreshes models, atlas sprites, and rendered enchanted-book models through Minecraft's resource manager.

## Written-book interface

The knowledge screen uses `minecraft:textures/gui/book.png` and Minecraft's page-turn arrow sprites. A pack can supply `assets/ritualsnotrolls/textures/gui/knowledge_book.png` to override only this mod's page background. Use the same 256×256 atlas layout as the written-book texture; the screen draws its upper-left 192×192 region.

The content contains four material entries per page, with icons, power, and tear-out controls. Auto-Add, Gather Pages, and Done sit below the page. There is no inventory grid.

## Table and control sprites

Paths below are relative to `assets/ritualsnotrolls/textures/gui/sprites/`:

| Sprite | Use |
| --- | --- |
| `common/button.png` | Normal buttons |
| `common/button_highlighted.png` | Hovered or keyboard-focused buttons |
| `common/button_disabled.png` | Unavailable buttons, including page boundaries |
| `common/search.png` | Native search input frame |
| `ritual/background.png` | Reference screen outer frame |
| `ritual/knowledge_browser.png` | Material detail panel |
| `ritual/enchantment_row.png` | Known enchantment row |
| `ritual/selected_enchantment.png` | Currently displayed enchantment row |
| `ritual/material_row.png` | Material affinity row |

A mouse click does not leave an unhovered navigation button highlighted. Keyboard focus remains visible for keyboard navigation. Bundled 24×24 panel sprites have adjacent `.png.mcmeta` files declaring nine-slice scaling with a 3-pixel border. Override both files if changing the dimensions:

```json
{ "gui": { "scaling": { "type": "nine_slice", "width": 24, "height": 24, "border": 3 } } }
```

Legacy sprites remain available to packs but are not controls in the table library UI. `examples/resourcepack` is an installable selected-enchantment-frame override with matching metadata, plus a complete editable sounds.json.

## Particles

Each enchantment definition can specify `particle` and optional `particle_color` in its **datapack** JSON. The server assigns that effect separately to the enchantment's bookshelf-to-material and material-to-table routes. A mixed ritual can display different shapes and colors simultaneously. See [datapack fields](DATAPACKS.md).

The client uses the requested vanilla or mod particle provider for the sprite, shape, shading, and age animation. A controlled wrapper replaces its position and lifetime with a curved flight and, for target arrivals, layered rings that pulse inward in the final 28 ticks. Optional RGB is retained throughout the flight; omitting it preserves native color animation. Resource packs can replace the provider’s ordinary sprite resources.

Every bundled enchantment has a unique effect/color pair. Each full chain uses one time-parameterized Hermite spline with shared tangents through every pedestal and curved detours around reversals; all particles share its route and ring entrance. Arrivals join the orbital direction tangentially. Particles emitted together are spaced along that same curve, with a synchronized finishing pulse. The next sequential channel waits for one revolution to fill the preceding ring. Player experience routes use green end-rod sparks into each displayed XP catalyst and onward to the target. The server emits at most sixteen new flights every two ticks. Once all rings have formed (and any failing chains have collapsed), incoming enchantment sources stop together. XP sources stop earlier, once their own ring has formed, while the XP ring persists through completion. Existing particles complete their full routes and join the rings before a 24-tick gathering hold and 28-tick finishing pulse. Orbiting particles live until the shared completion instead of expiring after six seconds. The configured duration is a minimum; long routes extend the ritual to preserve this drain phase. Player movement changes the XP route's geometry without extending its captured arrival deadline. The XP ring radius is `clamp(0.42 + 0.0045 × sqrt(paid_xp), 0.42, 2.4)` blocks, with one tenth of the normal radius growth coefficient; emission weight scales with this radius. Each enchantment has a stable ring plane, cycling through three heights and three tilts including near-vertical planes. Radius scales with its evaluated enchanting power; larger rings receive a greater emission share. The shared center and held item rise to clear the lowest point of every tilted ring, including noise and a sprite margin above the table.

The server config `sequentialEnchantmentAnimations` defaults to `true`. Each enchantment starts with bookshelf-to-pedestal flights, through ordered outward pedestal hops and qualifying return visits, followed by the final target flight; the next enchantment starts when the prior ring forms. Earlier channels remain active. Setting it to `false` starts every enchantment channel together.

After successful enchanting, 96 particles spread into a sphere and fade over 32–44 ticks, retaining the enchantment effects/colors and XP green when applicable. Burst atlas particles use translucent rendering and smoothly decreasing vertex opacity. Failed rituals and removal-only rituals do not trigger a completion burst. Mixed additions/upgrades and removals still burst. After successful disenchanting, the target releases immediately and existing reverse particles finish the full spline to the book; existing flights keep their normal travel time. These trailing visuals do not reserve the table or spend resources.

## World and item resources

- `textures/block/pedestal.png` and `textures/block/pedestal_top.png`
- `models/block/pedestal.json` and `blockstates/pedestal.json` (six `facing` variants)
- Consumption and Subtraction Catalyst icons spread side by side on all four faces of the top rim, following the pedestal’s rotation.
- Displayed material and Experience Catalyst models remain upright for every pedestal direction.
- `textures/item/consumption_catalyst.png`, `textures/item/subtraction_catalyst.png`, and `textures/item/experience_catalyst.png`
- `models/item/knowledge_page.json` uses the `builtin/entity` renderer with page display transforms.
- `models/item/knowledge_book.json` uses `builtin/entity` with no extra transforms; the resolved enchanted-book model supplies them once.

## Sounds

`assets/ritualsnotrolls/sounds.json` defines eight replaceable events. Every sound entry exposes vanilla `volume` and `pitch` multipliers; the server uses volume 1 and pitch 1. A resource pack can replace event references with OGG files and edit subtitles. An enchantment definition may also specify its own completion event.

| Event | Vanilla default | Volume | Pitch |
| --- | --- | --- | --- |
| `capture` | `block.enchantment_table.use` | 0.8 | 1 |
| `knowledge` | `item.book.page_turn` | 0.8 | 1 |
| `material` | `block.amethyst_block.resonate` | 0.8 | 1 |
| `consumption` | `entity.allay.item_taken` | 0.8 | 0.5 |
| `experience` | `entity.experience_orb.pickup` | 0.8 | 1 |
| `complete` | `block.beacon.power_select` | 0.4 | 1 |
| `disenchant` | `block.beacon.deactivate` | 0.4 | 0.6 |
| `failure` | `entity.firework_rocket.twinkle_far` | 0.6 | random 0.2, 0.3, 0.4, 0.5, or 0.6 |

Consumption plays at each pedestal when its offering is taken. Failure plays once per failed enchantment chain. Disenchant plays when a level is removed; mixed addition/removal plays both completion and disenchant events. The other stages play once per ritual. Referenced vanilla sounds retain their own asset multipliers: Allay item-taken uses asset pitch 1.25 and volume 0.1, multiplied by the values above.

To override an event, use `replace: true` so your entries replace the bundled choices:

```json
{
  "consumption": {
    "replace": true,
    "subtitle": "subtitles.ritualsnotrolls.consumption",
    "sounds": [{"name": "minecraft:entity.allay.item_taken", "type": "event", "volume": 0.8, "pitch": 0.5}]
  }
}
```

The failure event contains five equally weighted sound entries. Adjust their pitches, volumes, or vanilla `weight` fields to change the random mix. Ritual events allow final pitches from 0.01 to 4 rather than Minecraft’s normal 0.5–2 clamp; unrelated sound events retain vanilla behavior. Reload resource packs with F3+T after editing.

Insufficient positive-power chains begin calmly and destabilize near ring formation. The failure ramp lasts `24 + round(72 × fraction²)` ticks, where `fraction` is available power divided by the next required level threshold, clamped to 0–1. Destabilization combines a small shared sway with smooth per-particle noise along the same route; it does not create alternate paths. At failure, source emission stops and each existing particle receives an independent spherical scatter impulse added to its instantaneous velocity, then drifts with drag, falls under gravity, and fades over 40 ticks. Successful channels build smaller noise and wobble according to their shortfall from the registry enchantment’s native maximum cost; ritual-defined bonus levels do not move that reference. Failed-only attempts have no success pulse/burst or resource debit. Ineffective subtraction remains inert.

`chainPedestalAnimations` controls sequential pedestal hops independently from enchantment-channel staggering. Subtraction reverses those hops. Partial successful subtraction reduces surviving orbit emissions proportionally; ineffective subtraction produces no ritual particles.

Page power text uses `ritualsnotrolls.power` (`%s Enchanting Power`) and the five `ritualsnotrolls.power_tier.*` translations (`very_low`, `low`, `medium`, `high`, `very_high`). The default labels are Weakest, Weak, Average, Strong, and Strongest; existing translation keys remain stable. Tooltip width is calculated from the translated tier phrase.

Knowledge pages and books use the common `c:hidden_from_recipe_viewers` item tag. The optional JEI, EMI and REI adapters hide these variants from item lists while providing recipes through the Uses key.

The enchanting-table reference uses the same tier translation keys without the Enchanting Power suffix for potential, base, and effective strength. All compare against the strongest affinity in that enchantment’s complete loaded definition, including undiscovered affinities. Effective strength includes current contributions and modifiers; negative values retain a minus sign. Zero falls in Weakest, and amounts above the reference maximum stay Strongest. Numeric power multipliers are hidden; XP costs and counts remain visible.

## Enchantment descriptions and guide text

Descriptions use the same language keys as [Enchantment Descriptions for Minecraft 1.21.1](https://github.com/Darkhax-Minecraft/Enchantment-Descriptions/blob/1.21.1/common/src/main/java/net/darkhax/enchdesc/common/impl/EnchdescMod.java): `enchantment.<namespace>.<path>.desc`. No dependency on that mod is required.

For example, put this in `assets/ritualsnotrolls/lang/en_us.json` inside a resource pack:

```json
{
  "enchantment.minecraft.sharpness.desc": "Adds melee damage to each hit."
}
```

For another enchantment, substitute its registered namespace and path. A slash in the path stays a slash. Keys belong to Minecraft's shared language map; the resource pack's asset namespace need not match the enchantment namespace. Mods can add the same keys to their own language files. Select the resource pack and use F3+T to reload. The [example resource pack](../examples/resourcepack) includes this editable entry.

Lookup follows Enchantment Descriptions' order: `.desc`, `.description`, then `.info`. Each suffix checks its general entry before its level-specific entry, such as `enchantment.minecraft.sharpness.desc.1`. If none exist for the registry ID, the same suffixes are tried after the enchantment's custom name translation key. Table rows describe enchantments generally and use level I when only level-specific text exists.

All 42 vanilla enchantments have short descriptions under these standard keys in `assets/ritualsnotrolls/lang/en_us.json`. Resource packs can replace them and supply modded descriptions. Missing descriptions display an unavailable message. Keep descriptions to one short sentence when possible; supplied text wraps in the tooltip without being truncated.

Guide titles and bodies use `ritualsnotrolls.guide.chapter.0.title` / `.body` through chapter `7` in the same language file. Preserve the `%s` placeholders when translating bodies; they receive live server settings. Text wraps within one continuously scrolling chapter.

Descriptions are white. The player chooses enchantment and curse name colors in `config/ritualsnotrolls-client.toml`; see [CONFIGURATION.md](CONFIGURATION.md).

## Guide screenshot examples

Each guide chapter starts with a real in-game screenshot and caption. Click the image to enlarge it; Back or Escape returns to the chapter. Scroll down with the mouse wheel or drag the scrollbar to reach the mechanics text. Arrow keys, Page Up/Down and Home/End also scroll the chapter.

Replace `assets/ritualsnotrolls/textures/gui/guide/example_0.png` through `example_7.png` for the eight chapters in order. The bundled images are 960 x 540 PNG screenshots captured from the development scenes without a HUD. Keep the 16:9 aspect ratio. Captions use `ritualsnotrolls.guide.chapter.<0-7>.caption` in the language file and should fit three lines at 206 GUI pixels. Screenshots are examples; the adjoining text uses the connected server's current settings.
