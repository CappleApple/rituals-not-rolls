# Current interaction revision — 2026-09-07


## Latest accepted changes — 2026-09-08

- Remove the table material-row explanation tooltip. Knowledge pages and book entries show relative five-tier Enchanting Power, comparing their affinity with the strongest affinity for that enchantment in the loaded definition. Boundaries start at 0%, 20%, 40%, 60%, and 80%; the calculation changes presentation only.

- Ship optional definitions for all 26 player enchantments in the provided missing-enchantment list. Use owning-mod materials where logical. Explicitly exclude the internal Storm Front Marker and retained Arcane Assembly entry. Defaults retain full-distinct-set native-maximum balancing at enchantability 10.

- Loose knowledge pages count as physical knowledge sources in nearby chiseled bookshelves, just like books; each page grants only its one affinity. This supersedes book-only requirements below.
- When Immersive Enchanting is absent, convert saved ancient books to one matching affinity page per book during item loading. Preserve unsupported books and retry after matching ritual data is added. Modern stored enchantments and the legacy NeoForge no_network component are supported.

The user's follow-up supersedes the original planner/arming workflow below:

- Relative material power is shared only when every participating enchantment can change a full level after the split.
- A pedestal holds one material. Unconsumed duplicate item IDs do not extend the same enchantment chain; consumed duplicates contribute independently and all contributing copies are consumed once.
- A return visit to the SAME pedestal contributes 150% of that pedestal's base power, rather than another full contribution.
- Chains run from book through nearest-to-farthest pedestals, then qualifying return-path pedestals, then the table. Branches can separate by bookshelf or non-overlapping starter materials near a shared bookshelf.
- Subtraction and consumption modifiers can coexist; ineffective subtraction has no animation or resource cost.
- Enchantability uses configurable gentle scaling around rating 10. Only shipped defaults follow the full-distinct-material-set equals vanilla-maximum balance convention.
- Chest books convert only configured enchantments into pages. `/ritual missing` lists registered enchantments without loaded definitions.
- Enchant automatically from a thrown compatible target, bookshelf knowledge, and pedestal contents.
- The enchanting-table UI only references currently known nearby enchantments and their material powers.
- A sacrifice catalyst doubles and consumes only its own pedestal’s material; its icon appears on all four sides of that oriented pedestal’s top rim.
- Pedestals support all six placement directions while displaying their held items upright.
- Experience Catalysts occupy the main displayed item slot; curved particles flow from the player into each catalyst and then to the target.
- Each placed XP catalyst adds 10% power and ten levels to a combined cost basis. The user explicitly chose 550 XP total for two catalysts (the XP needed to reach level 20 from zero).
- Enchantment animations start sequentially by default, with a config switch for simultaneous starts. Each new channel waits for the preceding ring to form; prior rings persist until the shared finish. Per-enchantment imbuement power controls ring radius and particle amount.
- Knowledge pages rotate about their center and have an extruded edge like vanilla item sprites.
- Particle routes curve through space and arrivals orbit the target in several layers and tilted planes, including near-vertical rings. They pulse inward before successful completion, then burst in every direction and fade out. Each enchantment has a distinct default effect/color pair and configurable effect/optional hex color.
- Material powers vary per enchantment (iron favors Knockback over Sharpness), and Mending costs 256 power.
- Navigation controls do not stay highlighted after a mouse click, and the captured item floats steadily instead of falling between updates.
- Knowledge book items use the actual enchanted-book appearance; knowledge page appearance is unchanged.
- The book screen should resemble Minecraft's written-book GUI and may use its texture.

The initial specification is retained below for the unchanged framework requirements.

---

Build a complete Minecraft **NeoForge 1.21.1** mod that replaces the normal enchanting-table experience with a physical, knowledge-driven ritual enchanting system.

The mod must be designed as a highly configurable, datapack/resource-pack-friendly framework suitable for large modpacks. Avoid hardcoding vanilla-only assumptions. Compatibility with modded enchantments, items, inventories, bookshelves, pedestals, resource packs, and enchantment-book texture packs is a major goal.

Use clean architecture, appropriate client/server separation, efficient event-driven logic, robust networking, and comments/documentation where appropriate.

Do not create placeholder systems when the actual functionality can reasonably be implemented.

# Core Design Philosophy

Enchanting has three separate components:

1. **Knowledge**

   * Determines which materials the player knows can imbue a particular enchantment.

2. **Imbuement Power**

   * Physical items placed around the enchanting table provide power toward enchantments.

3. **Ritual Infrastructure**

   * Bookshelves, books, pedestals, catalysts, automation, XP amplification, and the enchanting table itself determine how the ritual can be performed.

Do NOT treat knowledge pages as enchantment-level unlocks.

There is no concept such as:

* Knowledge of Sharpness I
* Knowledge of Sharpness II
* Knowledge of Sharpness III

Instead, every Sharpness page is simply:

**Knowledge of Sharpness**

Each unique page teaches one material affinity for Sharpness.

Example:

* Knowledge of Sharpness

  * Diamond
  * 30 power

* Knowledge of Sharpness

  * Flint
  * 7 power

* Knowledge of Sharpness

  * Netherite Scrap
  * 55 power

If Sharpness III requires 75 power, a player who has only discovered three Sharpness pages can still cast Sharpness III if those three known materials provide enough power.

Knowledge quantity does NOT directly gate enchantment levels.

# Datapack-Driven Enchantment Definitions

Every enchantment must be configurable through datapacks.

Create a sensible resource location structure such as:

`data/<namespace>/ritual_enchanting/enchantments/<enchantment>.json`

Exact naming may be adjusted if a cleaner architecture is appropriate, but document it.

Each enchantment definition must support:

* enchantment ID
* arbitrary imbuement materials
* item IDs
* item tags
* independent power values
* arbitrary enchantment levels
* required power for each level
* optional conflict group definitions or references
* optional visual/resource metadata where useful
* optional particle/sound ritual configuration where practical

Example conceptual definition:

```json
{
  "enchantment": "minecraft:sharpness",

  "materials": [
    {
      "item": "minecraft:iron_ingot",
      "power": 8
    },
    {
      "item": "minecraft:diamond",
      "power": 30
    },
    {
      "tag": "c:gems/amethyst",
      "power": 16
    },
    {
      "item": "minecraft:netherite_scrap",
      "power": 55
    }
  ],

  "levels": {
    "1": 15,
    "2": 35,
    "3": 75,
    "4": 130,
    "5": 210,
    "6": 320
  }
}
```

The maximum enchantment level supported by this ritual system is:

**the highest level for which a power requirement exists in the datapack.**

Do NOT use the enchantment's normal vanilla maximum level as the ritual limit.

However, detect/document compatibility concerns where an enchantment's own effect implementation internally clamps its level.

# Material Affinities Are Per Enchantment

An item's power is NOT globally defined.

The same item may have different values for different enchantments.

Example:

Blaze Rod:

* Fire Aspect: 40
* Sharpness: 4
* Looting: 2
* Unbreaking: 9

The same item may appear in any number of enchantment definitions.

An item being known for one enchantment does not make it known for another.

# Knowledge Pages

Create a Knowledge Page item system.

Every page represents exactly one discovered material-affinity entry belonging to an enchantment definition.

Example:

**Knowledge of Sharpness**

Material:
Diamond

Imbuement Power:
30

Internally, the page needs sufficient data to uniquely identify:

* enchantment
* material-definition entry
* exact power/value as resolved through the datapack or a stable definition identifier
* any required metadata

Avoid fragile implementations where datapack reloads unnecessarily invalidate legitimate pages.

Pages should preferably reference stable logical definition IDs rather than duplicating excessive configuration data.

Duplicate logical knowledge entries must be detectable.

# Knowledge Page Item Appearance

Knowledge pages need strong visual identity.

Support a custom texture for knowledge pages on a per-enchantment basis at a documented resource location.

For example, something conceptually similar to:

`assets/<namespace>/textures/gui/knowledge_pages/<enchantment>.png`

or another clean resource structure.

If a custom page texture exists for that enchantment, use it.

If it does NOT exist, dynamically compose/default-render the icon using:

1. a worn/yellowed Minecraft-style paper/page base
2. the associated enchantment's current enchanted-book icon or visual representation centered on the page
3. shrink the book icon so it fits comfortably within the page
4. desaturate/tint it so it visually matches the yellowed paper
5. retain enough silhouette/detail that different enchantments are visually distinguishable

Do NOT hardcode vanilla enchanted-book artwork if another resource pack has replaced the enchantment's book appearance.

The intent is that resource packs such as **Beautiful Enchanted Books** or equivalent per-enchantment book texture systems can naturally influence the knowledge-page icon.

Research the actual Minecraft 1.21.1 rendering/resource model system and implement this in a resource-pack-compatible way rather than assuming a specific third-party mod API unless needed.

If direct dynamic texture composition is inappropriate, implement an equivalent runtime rendering/model-layer solution.

Page appearance must still work when no compatibility mod/resource pack is installed.

# Knowledge Page Tooltip

Hovering a page in an inventory must show:

* Knowledge of <Enchantment>
* associated material
* actual item icon rendered inline where practical
* material name
* imbuement power

Example conceptually:

Knowledge of Sharpness

[Diamond icon] Diamond
30 Imbuement Power

Do not merely dump raw registry IDs.

For tag-based material definitions, provide a useful representation.

If a tag contains multiple valid items, the page represents knowledge of that material definition/tag affinity rather than silently becoming a different unrelated entry.

Design a sensible cycling/representative icon behavior if required.

# Knowledge Books

Each enchantment has its own knowledge book.

A Sharpness knowledge page can initialize a Sharpness knowledge book.

Initial crafting recipe:

* 3 Leather
* 1 Knowledge Page

The resulting book becomes the:

**Book of Knowledge: Sharpness**

and already contains the page used to create it.

The book then stores unique discovered knowledge entries for that enchantment.

Books may never contain duplicate copies of the same logical knowledge entry.

Attempting to add a duplicate must leave the existing entry unchanged and must not destroy the incoming page unless specifically appropriate.

# Knowledge Book Appearance

Books also support per-enchantment custom textures through a documented resource location.

If no custom texture is provided, create a default visual based on:

* a Minecraft-like old/worn book
* the associated enchantment's current enchanted-book visual/icon incorporated into its cover
* visual treatment/desaturation consistent with the knowledge pages

Again, use resource-pack-resolved visuals where possible so texture packs such as Beautiful Enchanted Books naturally give each knowledge book a distinctive identity.

Do not require pack makers to overwrite vanilla textures to customize this mod.

# Resource-Packable UI Framework

ALL custom UIs in this mod must look like they belong in Minecraft.

This is important.

Do NOT create generic flat colored rectangles/panels that look like a web application.

Use:

* vanilla inventory textures
* slot textures
* buttons
* tabs
* scrollbars
* tooltip styling
* book-page styling
* beveled borders
* sprites
* nine-slice GUI elements
* Minecraft typography
* vanilla interaction states

You may modify, combine, expand, recolor, or create new Minecraft-style GUI elements where necessary.

The enchanting UI and knowledge-book UI can be substantially more advanced than vanilla while still visually belonging to Minecraft.

# Custom UI Texture Overrides

Every custom GUI component created by this mod should first look for a mod-specific resource-pack override.

Create and document a GUI sprite resource hierarchy, e.g.:

`assets/<namespace>/textures/gui/...`

Prefer the modern GUI sprite/resource system where appropriate for 1.21.1.

Possible categories:

* enchanting background
* knowledge browser
* book background
* selected enchant frame
* power bar
* pedestal indicator
* catalyst indicator
* automation button
* consume toggle
* XP toggle
* page slot
* page navigation
* tabs
* scrollbar
* ritual preview elements

For every custom GUI element:

1. try to resolve the mod's custom sprite/resource
2. if pack makers provide it, use it
3. otherwise fall back to the mod's bundled Minecraft-style default
4. where appropriate, fall back further to an actual vanilla GUI sprite

Do not require resource-pack authors to overwrite Minecraft's own GUI textures merely to reskin this mod.

Document all override locations.

# Book Page Auto-Absorption

Knowledge books can automatically absorb matching knowledge pages entering the player's inventory.

This needs careful event behavior.

DO NOT poll the player's inventory every tick.

DO NOT use a naive vanilla-only inventory polling system.

Use the appropriate **NeoForge inventory/item-handler APIs and events/capabilities available in NeoForge 1.21.1**.

Research the correct NeoForge APIs rather than assuming old Forge APIs still exist unchanged.

Page absorption should happen in response to meaningful inventory insertion/acquisition events.

A matching knowledge page can automatically enter the appropriate book when it enters the player's possession through actions such as:

* picking the page up from the world
* shift-clicking it from another container into the player's inventory
* another NeoForge-compatible inventory transfer that actually inserts it into the player's inventory
* holding the page and using/right-clicking it where appropriate

However:

**Do not auto-absorb pages that the player manually places/rearranges inside their own inventory.**

The player must be able to intentionally keep a loose page.

Track insertion context appropriately instead of scanning and consuming everything matching after every inventory change.

# Per-Book Auto-Add Toggle

Each knowledge book has a persistent:

**Auto-Add Pages: ON/OFF**

setting.

Default can be ON unless there is a compelling technical reason otherwise.

The state belongs to the individual book stack.

The book UI must provide a vanilla-styled toggle/button for this.

Tooltip should expose its state.

When OFF, incoming pages remain loose unless explicitly added by the player.

# Direct Page Addition

Support intentionally adding pages to a book.

This can include intuitive actions such as:

* using/right-clicking the page while a compatible book is available
* interacting through the book UI
* other sensible vanilla-like interactions

Do not add duplicate knowledge.

# Double-Click Book Collection

When a knowledge book is double-clicked inside an inventory interface:

* search the relevant accessible inventory/container context for loose Knowledge Pages associated with that exact book/enchantment
* pull matching non-duplicate pages into the book
* leave unrelated pages alone
* leave duplicate pages alone
* use correct client/server synchronization
* do not accidentally interfere with vanilla double-click stack-gather behavior beyond what is necessary

Prefer a narrowly scoped interception only for knowledge books.

This should feel like quickly gathering matching pages into a binder.

# Tearing Pages Out

Holding a knowledge book and right-clicking it must provide access to its knowledge contents.

Through the book UI, the player can tear individual pages back out.

Tearing out a page:

* removes that knowledge entry from the book
* creates the corresponding Knowledge Page item
* places it into the player's inventory when space exists
* otherwise safely drops it in the world
* never duplicates or deletes the knowledge because of networking/desync

This allows pages to be moved between books or traded.

# Book UI

Right-clicking/using a knowledge book opens a Minecraft-styled knowledge-book interface.

It must display:

* enchantment name
* enchantment visual/icon
* every stored knowledge entry
* associated material item icon
* associated material name
* imbuement power
* page count
* total possible page count
* auto-add state
* controls for removing/tearing pages out
* scroll/navigation behavior where required

Show prominently:

**Pages: X / Y**

Where:

X = unique knowledge entries currently contained

Y = total currently-defined knowledge entries for that enchantment from loaded datapacks

The same information must appear in the book's inventory tooltip.

Example:

Book of Knowledge: Sharpness
Pages: 8 / 17
Auto-Add: On

Datapack reloads should cause Y to reflect the new available definition count safely.

# Chiseled Bookshelf Requirement

Knowledge only counts for enchanting if the physical knowledge book is placed in a valid nearby bookshelf.

The enchanting table searches within a configurable radius:

Default:
**16 blocks**

At minimum, support vanilla chiseled bookshelves.

Also build a compatibility abstraction for modded bookshelf/storage blocks where practical.

A knowledge book in the player's inventory does NOT grant ritual access.

A book sitting somewhere unrelated does NOT grant ritual access.

The enchanting table must discover nearby books containing the knowledge needed for the ritual.

# Pedestals

Create the mod's own pedestal block.

Also create a generic compatibility system for recognizing pedestals from other mods.

Possible mechanisms include:

* NeoForge block/item capabilities where applicable
* datapack/block tags
* explicit compatibility adapters
* clean public API

Provide something similar to:

`#<namespace>:enchanting_pedestals`

if appropriate.

Do not make integrations hard dependencies.

The enchanting table searches pedestals within the same configurable default 16-block radius.

Each pedestal must support at least:

* displayed imbuement item
* optional catalyst relationship/state
* physical ritual rendering

# Duplicate Material Rule

For a single enchantment during a ritual:

**The same actual item ID may only contribute imbuement power once.**

Therefore:

Diamond + Diamond + Diamond

does NOT provide triple power.

But:

Diamond + Emerald + Amethyst

can all contribute.

For tags, duplicate identity is based on the actual supplied item, not merely the tag definition.

If Diamond and Emerald both satisfy the same tag-based affinity and knowledge permits that affinity, they are still distinct actual items and may each contribute according to the intended rules.

Ensure this rule remains understandable and deterministic.

# Materials May Power Multiple Enchantments Simultaneously

A pedestal item is NOT consumed/reserved exclusively by a single enchantment calculation.

The same physical Blaze Rod may simultaneously provide:

* 40 Fire Aspect power
* 4 Sharpness power
* 2 Looting power

if:

* those affinities exist in datapacks
* the relevant knowledge pages have been discovered
* relevant knowledge books are in nearby bookshelves

The ritual evaluates the complete pedestal/material network against every selected enchantment.

# Enchantment Power Levels

Each enchantment level has a configured power requirement.

Example:

Sharpness:

* I = 15
* II = 35
* III = 75
* IV = 130
* V = 210
* VI = 320

No vanilla enchantment-level cap should stop selection if the datapack defines higher levels.

# Consumption Catalyst

Create a dedicated custom catalyst item that has no unrelated vanilla recipe ambiguity.

When the consumption catalyst is enabled for an imbuement item:

* the relevant material is consumed during a successful ritual
* its effective imbuement power is doubled by default

Example:

Diamond:
30 base

Normal:
30

Consumed:
60

Make the multiplier configurable/datapackable.

Consumption selection must operate per material where feasible.

The enchanting UI must visually distinguish:

* normal/non-consumed material
* consumed material
* resulting effective power

The ritual must validate resources immediately before completion so duplication or race conditions cannot occur.

# XP Amplification Catalyst

Create another dedicated catalyst that allows player experience to amplify imbuement power.

When available and enabled for the ritual:

* the player can allocate/spend experience
* experience amplifies the effectiveness of the ritual's imbuement materials

Do NOT hardcode only one possible progression curve.

Provide configurable/datapack-configurable XP amplification.

A reasonable default may be nonlinear/diminishing-return scaling.

The UI must clearly show:

* XP/levels being spent
* resulting multiplier or bonus
* resulting effective ritual power

Never consume XP until the ritual has successfully validated and committed.

# Conflicting Enchantments

Remove vanilla mutual-exclusion restrictions for enchantments such as:

* Protection
* Fire Protection
* Blast Protection
* Projectile Protection

and similar configurable conflict groups.

Also permit combinations like:

* Sharpness
* Smite
* Bane of Arthropods

where technically applicable.

However, combining enchantments from the same conflict group increases their required ritual power.

Default rule:

First conflicting enchantment:
×1 cost

Second:
×2

Third:
×4

Fourth:
×8

etc.

So each subsequent member doubles again.

Make conflict groups and multiplier behavior data-driven.

Provide sensible vanilla defaults but allow datapacks to redefine them.

Do not blindly rely on vanilla `isCompatibleWith` checks if that would prevent the intended system.

Preserve technical safety for enchantments that fundamentally cannot operate on the target item.

# Enchanting Table UI Replacement

Replace the normal enchanting table interface entirely.

This should be a Minecraft-styled enchantment browser and ritual planner.

Primary view should include:

* searchable available-enchantment browser
* known/available enchantments
* maximum currently attainable level from knowledge/materials
* level selector
* current selected ritual enchantments
* power requirements
* current available power
* conflict multipliers
* required/available materials
* knowledge availability
* pedestals
* catalysts
* XP amplification
* automation capabilities
* ritual readiness

Clicking an enchantment should open detailed information including:

* enchantment description/name
* selectable level
* required power
* nearby knowledge entries
* known valid materials
* available materials
* each material's base imbuement power
* current effective power
* whether it is set to be consumed
* whether the player actually possesses/accesses it

Use item icons heavily.

Do not make this a spreadsheet-looking colored-panel UI.

It must feel like an advanced vanilla Minecraft workstation.

# Ritual Preview

Before starting an automated/prepared ritual, expose a clear preview showing things such as:

Target:
Netherite Sword

Enchantments:
Sharpness VI
Looting IV
Fire Aspect III
Unbreaking VII

Pedestals:
14 used

Consumed Materials:
2

Experience:
18 levels

Conflict Modifiers:
Sharpness ×1
Smite ×2

Power:
Current / Required

Then allow the player to prepare/start the ritual.

# Enchanting Table Automation Enchantment

The enchanting table itself must support being enchanted/upgraded with a special enchantment that grants automation.

Create an appropriate custom enchantment, tentatively something like:

**Arcane Assembly**

Name may be improved if another Minecraft-like name fits better.

Higher levels can progressively unlock capabilities such as:

Level 1:

* detect nearby compatible inventories
* show accessible ingredients in the enchanting UI

Level 2:

* allow the player to click individual remote ingredients in the GUI
* retrieve them automatically

Level 3:

* automatically choose ingredients sufficient for a requested enchantment level

Level 4:

* intelligently determine which materials should be consumed to reduce waste/resource cost

Level 5:

* plan larger multi-enchantment rituals automatically

Exact progression should be configurable and architected cleanly.

The important part is that enchanting-table automation is itself progression.

# Nearby Inventories

Automated tables may interact with nearby inventories.

Use NeoForge inventory/item handler systems rather than hardcoding vanilla ChestBlockEntity behavior.

Support arbitrary compatible modded inventories when they expose the expected NeoForge interfaces.

Search radius should be configurable.

Avoid scanning all block entities every tick.

Cache/discover networks intelligently and invalidate/rebuild them when necessary.

# Automated Material Placement

With sufficient automation level, the player can:

1. choose a specific material in the enchanting UI
2. have the table find it in a nearby inventory
3. move it onto an available pedestal

They can also select whether its consumption multiplier should be used.

If a consumption catalyst is needed and available:

* retrieve/place/activate it appropriately

Alternatively the player can simply choose:

**Sharpness V**

and ask the table to prepare the ritual automatically.

The table should then choose available materials that satisfy the required power.

# Ritual Planning / Optimization

Create a clean optimizer abstraction.

It should be capable of planning combinations while respecting:

* unique actual-item rule
* available knowledge
* pedestal count
* material availability
* multiple enchantments
* cross-enchantment material contribution
* optional consumption
* consumption multipliers
* XP amplification
* conflict multipliers
* player-selected locked ingredients
* table automation level

Reasonable optimization priorities can include:

1. satisfy requirements
2. avoid unnecessary consumption
3. minimize resource value loss
4. minimize excess/wasted power
5. minimize pedestals used

Do not implement an obviously exponential brute-force algorithm that freezes large modpacks.

Use bounded/heuristic/dynamic programming strategies where appropriate and profile the algorithm.

Keep the optimizer separate enough that future strategies could be added.

# Physical Ritual

The actual enchantment occurs physically in the world.

The player may manually place imbuement items onto nearby pedestals.

When ready, the player throws/drops the item they wish to enchant toward the enchanting table.

The enchanting table detects a valid nearby thrown ItemEntity and, when appropriate:

1. pulls/grabs the target item toward itself
2. suspends it above the table
3. slowly rotates/bobs it
4. begins the ritual
5. activates relevant nearby knowledge books/bookshelves
6. sends particles/visual energy from knowledge sources toward the relevant imbuement materials
7. sends energy from materials/pedestals toward the enchanting table or target
8. activates XP effects when XP amplification is used
9. visibly consumes sacrificed ingredients
10. applies all selected enchantments together
11. completes with sound/particles
12. returns/releases the enchanted item

A single ritual may apply a very large number of enchantments.

Do not impose an arbitrary low limit such as 3 enchantments.

Practical limits should come from:

* knowledge
* room size
* available pedestals
* resources
* power requirements
* XP
* conflict multipliers

# Ritual Rendering

Ritual rendering must be performant.

Do not spawn ridiculous numbers of persistent entities.

Prefer:

* client-side particles
* transient visual paths/beams
* block/entity renderers
* event-driven animation state
* interpolated movement

Do not perform expensive world searches every render frame.

The server remains authoritative for the ritual state and result.

Clients render synchronized ritual phases.

# Sounds

Use Minecraft-style sounds and support resource-pack replacement.

Different ritual phases should have distinct audio cues:

* item capture
* knowledge activation
* material activation
* catalyst consumption
* XP amplification
* enchant completion

Avoid excessive repeating audio spam when dozens of pedestals are active.

# Bookshelf and Pedestal Network Discovery

Do not rescan a 33×33×33 area every tick.

Implement efficient network discovery/caching.

Possible triggers for invalidation:

* enchanting table placed/removed
* nearby relevant block changed
* pedestal content changed
* bookshelf content changed
* datapack reload
* automation inventory network changed
* chunk load/unload

A ritual should do a final authoritative validation before committing.

# Inventory Safety

All page movement, material movement, catalyst consumption, XP consumption, book changes, and enchant application must be server authoritative.

Prevent:

* duplication
* client desync
* page loss
* item loss
* ritual double execution
* race conditions from two players
* changing a pedestal halfway through commit to duplicate power
* pulling the same inventory item twice

Use transactional-style validation/commit where practical.

# Multiplayer

The system must work correctly on dedicated servers.

Knowledge belongs to the physical books/pages, NOT inherently to player persistent data.

That means:

* books can be traded
* pages can be traded
* enchanting libraries can be communal
* a player may use knowledge contained in nearby books regardless of who discovered it

Do not accidentally store required knowledge progression only in client state/player advancement state.

# Datapack Reloading

Support `/reload`.

When enchantment definitions change:

* rebuild indexes safely
* invalidate ritual/network planning caches
* refresh total-page counts
* ensure existing page/book data remains as stable as reasonably possible
* handle removed definitions gracefully
* log useful warnings for unresolved legacy knowledge entries instead of crashing

# Resource Reloading

Resource-pack reloads should update:

* GUI sprites
* page appearances
* knowledge-book appearances
* enchantment visual integrations
* resource-pack-provided override textures

without requiring a world restart where Minecraft normally permits reloads.

# Public Compatibility API

Expose a small clean API where useful for other mods to:

* register/detect pedestal behavior
* register bookshelf/knowledge-container behavior
* query ritual state
* add or inspect imbuement definitions
* integrate alternate inventories
* optionally provide enchantment visual icons
* listen for ritual start/complete events

Do not overengineer a giant API before needed, but avoid locking everything behind private implementation details.

# Commands / Debugging

Add useful operator/debug commands for development, for example:

* list resolved enchantment definitions
* give a specific knowledge page
* inspect a knowledge book
* inspect a nearby enchanting network
* inspect detected pedestals
* inspect available ritual power
* reload/debug material affinities
* trigger validation output

Keep these permission-gated.

# Logging

Do not spam logs during normal gameplay.

Use useful warnings for:

* malformed datapack definitions
* nonexistent items/tags/enchantments
* duplicate definition IDs
* impossible power levels
* compatibility failures
* stale knowledge entries

Debug-level logging can expose more detail when explicitly enabled.

# Required UX Details

Knowledge Page tooltip:

* enchantment
* associated material with icon
* power

Knowledge Book tooltip:

* enchantment
* Pages X / Y
* Auto-Add On/Off

Knowledge Book UI:

* vanilla/Minecraft styling
* X / Y pages
* material icons
* power
* tear-out controls
* auto-add toggle

Enchanting Table:

* advanced Minecraft-style UI
* no generic colored dashboard aesthetic
* searchable enchantment browser
* item icons
* level requirements
* available/selected materials
* consumption state
* XP amplification
* automation controls
* full ritual preview

# Important Interaction Rules

Implement these specifically:

1. Knowledge pages are material discoveries, NOT level fragments.
2. No arbitrary page count gates enchantment level.
3. Power requirements determine attainable levels.
4. No duplicate knowledge entries inside one knowledge book.
5. No duplicate actual item ID contributes twice to one enchantment during one ritual.
6. One physical item MAY contribute to multiple different enchantments simultaneously.
7. Manually rearranging a page inside the player's own inventory must NOT cause auto-absorption.
8. Picking up/shift-transferring a new page into the player's inventory CAN trigger auto-add.
9. Auto-add is configurable independently per book.
10. Double-clicking a book gathers matching loose pages.
11. Pages can be torn back out.
12. Knowledge only participates in enchanting when the relevant book is physically available in a nearby valid bookshelf.
13. Normal imbuement materials are not consumed.
14. Consumption catalyst allows selected materials to be consumed for doubled power.
15. XP catalyst allows experience to amplify power.
16. Vanilla incompatible enchantments may coexist, with exponentially increasing same-conflict-group requirements.
17. Ritual maximum enchantment levels are datapack-defined, not vanilla-max-defined.
18. A single ritual may apply many enchantments at once.
19. Automation retrieves actual items and physically prepares the ritual rather than merely pretending the pedestals exist.
20. Final result must always be server-authoritatively validated.

# Testing

Create automated tests/game tests wherever NeoForge/Minecraft infrastructure reasonably permits.

At minimum test:

* knowledge page creation
* page identity
* knowledge-book crafting
* adding pages
* rejecting duplicates
* tearing pages out
* auto-add enabled
* auto-add disabled
* manual inventory movement does not auto-add
* pickup does auto-add
* shift-click into player inventory does auto-add
* double-click book gathering
* page count X/Y
* datapack reload
* item affinity resolution
* tag affinity resolution
* same item with different enchantment powers
* duplicate physical material prevention
* one material contributing to multiple enchantments
* power thresholds
* beyond-vanilla-max enchanting
* conflicting enchantment multiplier
* nonconsuming ritual
* consumption catalyst
* XP catalyst
* multi-enchantment ritual
* nearby chiseled bookshelf detection
* missing knowledge preventing use
* pedestal detection
* modded inventory through NeoForge item handlers
* automation material movement
* ritual validation
* failed ritual does not consume resources
* multiplayer synchronization
* resource reload without errors

# Development Process

Before writing large amounts of code:

1. Inspect the NeoForge 1.21.1 APIs actually available in the development environment.
2. Determine the modern mechanisms for:

   * item data/components
   * inventory/item handlers
   * inventory interaction events
   * menus/screens
   * custom GUI sprites
   * resource reload listeners
   * datapack reload listeners/codecs
   * enchantment application
   * rendering dynamic item overlays/models
   * ItemEntity interception/movement
   * block capabilities
   * networking
3. Build the architecture around current APIs rather than old Forge examples.
4. Compile frequently.
5. Fix warnings/errors rather than leaving dead placeholder code.
6. Run applicable automated tests/GameTests.
7. Ensure dedicated-server classes never load client-only rendering code.

Organize the project into clear subsystems such as:

* knowledge
* definitions/datapacks
* books
* inventory integration
* ritual network
* pedestals
* catalysts
* enchanting logic
* automation/planner
* menus
* client screens
* rendering
* compatibility
* networking
* API
* tests

Use names that fit the final mod package/name once established.

# Deliverables

Produce:

* complete compiling NeoForge 1.21.1 mod
* source code
* assets
* default Minecraft-style GUI assets
* default knowledge page/book rendering
* recipes
* catalysts
* pedestal block
* automation enchantment
* datapack codecs/loaders
* vanilla enchantment default data
* default vanilla conflict groups
* networking
* client rendering
* book UI
* enchanting-table UI
* ritual system
* tests
* example datapack overrides
* example resource-pack UI overrides
* README documenting:

  * knowledge system
  * datapack schema
  * resource locations
  * custom page textures
  * custom knowledge-book textures
  * custom GUI sprites
  * pedestal compatibility
  * bookshelf compatibility
  * API hooks
  * automation
  * catalysts
  * ritual power math
  * conflict math
  * limitations of enchantments whose implementation internally clamps levels

Prioritize making the underlying framework correct and extensible rather than hacking special cases for vanilla content.

The desired end result should feel like enchanting has become a physical magical research and ritual system:

**discover knowledge → build books → build a library → collect meaningful materials → arrange a ritual → channel power → enchant the item.**

It should still visually and interactively feel unmistakably like Minecraft.
