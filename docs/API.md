# Architecture and compatibility

All Java packages are beneath `com.cappleapple.ritualsnotrolls`.

| Package | Responsibility |
| --- | --- |
| `data` | Validated codecs, immutable datapack snapshots, configurable power/XP/conflict rules |
| `knowledge` | Stable logical entries, immutable stack data component, book crafting and safe page movement |
| `mixin` | Narrow acquisition-context hooks and enchanting-table item upgrade placement |
| `pedestal` | Block, three-slot item capability, persistence, block updates |
| `ritual` | Loaded network discovery, power calculation, physical session, validation and commit |
| `menu` / `network` | Server menus, sequenced context-checked actions, definition and library reference synchronization |
| `client` | Client-only screens, item composition, pedestal renderer, material tooltips |
| `api` | Adapters, candidate discovery, query surface, start/complete events |
| `gametest` | Development-only tests and opt-in real-client fixture; excluded from release JAR |

## Block adapters

`api.RitualApi` exposes:

```java
RitualApi.registerPedestal((level, pos) -> /* IItemHandler or null */);
RitualApi.registerBookshelf((level, pos) -> /* IItemHandler or null */);
RitualApi.registerInventory((level, pos) -> /* IItemHandler or null */);
RitualApi.registerCandidateProvider((level, table, radius) -> /* candidate BlockPos collection */);
```

Register during common setup before worlds are in use. The first adapter returning a non-null handler wins; null delegates to the next adapter and then the built-in fallback. The default fallback uses:

- `#ritualsnotrolls:enchanting_pedestals`: displayed material or Experience Catalyst in slot 0; Consumption Catalyst in slot 1 and Subtraction Catalyst in slot 2. Built-in slots each accept one item. External adapters may use any auxiliary slot for either modifier; the scanner checks slots 1 onward.
- Vanilla chiseled bookshelves and `#ritualsnotrolls:knowledge_bookshelves`: books may be in any slot.
- `Capabilities.ItemHandler.BLOCK`: optional storage queries for integrations/debug, excluding detected bookshelves and pedestals; the ritual never retrieves items automatically.

For a modded pedestal with another slot layout, expose an adapter handler mapping logical material slot 0 and auxiliary modifier slots onto that layout. Implement simulate, insertion, and extraction honestly. A bookshelf adapter may be read-only for ritual knowledge. Shift-click retrieval also requires extraction; filing pages into its books requires extraction and reinsertion. Those operations respect handler simulation and revalidate stack components at arrival. Automatic filing of entire Knowledge Books is limited to native chiseled bookshelf slots.

Topology candidates normally come from loaded chunk block-entity indexes. A block capability without a block entity, or an external inventory system, should provide candidate positions. Providers must avoid force-loading chunks or performing huge world searches. The table checks range and loaded-chunk status again. Inventory handlers are re-resolved when a snapshot is taken, so invalidated capabilities are not retained indefinitely.

`RitualApi.definitions()` returns the current immutable definition snapshot. `RitualApi.ritualState(level,pos)` returns `idle` or `channeling`. Add affinities through datapacks; this preserves one source of truth for client sync and reload.

## Events

Subscribe on `NeoForge.EVENT_BUS` to `RitualEvent.Start` and `RitualEvent.Complete`. Start is cancellable and occurs before capture. Complete occurs after successful resource debit and enchantment replacement. Events include server level, table position, owning player, an item-stack copy, and immutable selected enchantments. Mutating the event's stack copy cannot mutate the captured target.

Client integrations may call `client.KnowledgeRenderer.registerIconProvider(Function<ResourceLocation, ItemStack>)`. Return an actual visual stack or null/empty to defer. Register this only on the client. Avoid returning this mod's knowledge page/book items recursively. Per-enchantment full page textures and page inset textures remain available. Knowledge books render the returned enchanted-book model directly.

## Inventory acquisition

NeoForge 1.21.1 has block/entity/item capabilities and `ItemEntityPickupEvent`, but no universal event describing the origin of every inventory slot write. The implementation inspects the actual 1.21.1 sources and uses these narrow hooks:

- `Inventory.add(int, ItemStack)` for genuine external acquisition, including vanilla world pickup.
- `InvWrapper.insertItem` for NeoForge player capability insertion, preserving input immutability and simulation purity. `PlayerInvWrapper`/`PlayerMainInvWrapper` delegate through this path.
- `AbstractContainerMenu.clicked` scopes manual interaction and recognizes external `QUICK_MOVE`; pickup permission and `Slot.safeTake`/output callbacks are honored.
- `PICKUP_ALL` is intercepted only when the carried stack is a knowledge book.
- `ItemEntity.playerTouch` suppresses auto-add for explicitly torn-out overflow pages and pages retrieved from shelves.

There is no player inventory polling task or every-tick inventory scan. `Knowledge.absorb` is also a public integration entry point for alternate acquisition systems. Direct `setItem` writes deliberately do not auto-add: that method alone cannot distinguish intentional rearrangement, load, synchronization, and acquisition. Mods bypassing both vanilla add/menu paths and NeoForge item handlers should call the explicit integration method when they know the insertion context.

## Transactions and multiplayer

Menus are server-authoritative and validate container ID, sequence number, reachability, and bound book identity. Replaying a packet cannot tear the same entry twice. Both screens have no inventory slots. The table accepts a sequenced `retrieve` action for an enchantment in its current filtered library; it never accepts client-supplied inventory positions or stacks. Book actions bind to the same physical stack while its UI is open.

Each table has one active owner, determined by the dropped item's throwing player. Item capture also claims a specific live entity so overlapping tables cannot both use it. Sessions use a bounded lifetime and keep the target in the world rather than persisting an invisible copy. Reload revisions, disconnection, dimension changes, unloaded chunks, and missing tables invalidate sessions. Capture disables gravity and holds a server anchor in the table's coordinate space while the vanilla renderer supplies smooth bob and rotation. Completion and interruption restore the original gravity state. Persisted recovery markers also restore orphaned items when they rejoin the world after a crash.

Ritual completion rereads books, pedestals, catalysts, target components, item support, and player XP. Plans carry physical-position allocations, ordered outward/return routes, exact withdrawal counts, and surviving ring fractions. Consumed items are extracted as the leading wave reaches each contributing catalyst-marked pedestal. `RitualConsumption` holds receipts and writes reserved offerings to the captured item’s persistent NBT. Revalidation overlays reserved materials into the network snapshot without putting them back into live inventories. Completion clears the escrow; interruption refunds current native handlers or drops a remainder. Loading an orphaned captured item refunds its serialized offerings once. Commit checks that routes, per-position users, powers, exact material components, and modifier states still match the captured plan. The commit rechecks each contributing pedestal’s local catalyst state after extraction callbacks; a catalyst elsewhere cannot mask its removal. Rollback reinserts actually extracted items or drops any remainder, then reports failure. XP debit and target replacement happen consecutively on the server thread after successful extraction. The standard NeoForge handler contract is the compatibility boundary; arbitrary malicious/reentrant third-party handlers cannot be made globally transactional by an external mod.

Definition sync is broken into ordered chunks and replaces a client-side snapshot distinct from the server-side one, including in integrated servers. The known-library reference and bound book are synchronized separately; clients never supply target stacks, power values, XP multipliers, selected enchantments, or inventory addresses for commit. Automatic selection uses the server's physical library and pedestal snapshot. Each particle route carries its effect ID, optional RGB, curve, remaining duration, and orbit parameters through a registered particle type. Player-input routes also include the source entity ID so their origin follows the throwing player. A client-only invoker constructs the native particle visual without separately queueing it; a controlled wrapper supplies curved movement, layered orbital planes, the inward pulse, and fading outward motion. Ring index, power-derived radius, burst state, the complete timed spline, and per-channel instability/failure clocks are synchronized with each flight (network protocol 8). Session timing and radii are captured from the authoritative evaluated plan, with the sequential/simultaneous config captured at the same time. The server emits the spherical completion burst only after a successful ritual commit.

## API sources inspected

The implementation was compiled against local NeoForge 21.1.244 patched sources for `Inventory`, `AbstractContainerMenu`, `InvWrapper`, `PlayerMainInvWrapper`, `ItemStack`, `ItemRenderer`, `BlockEntityWithoutLevelRenderer`, `GuiGraphics`, enchantment components, and block events. Relevant official documentation:

- [NeoForge 1.21.1 data components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)
- [NeoForge 1.21.1 capabilities](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/)
- [NeoForge 1.21.1 networking](https://docs.neoforged.net/docs/1.21.1/networking/)

## Pedestal orientation

The built-in block uses six-way `facing`, pointing outward from the clicked placement face. Collision shapes and top-rim decals share the block-model rotation. `PedestalGeometry.displayPosition` resolves the main item anchor outside the oriented top; item rendering stays in world-up coordinates. External pedestal adapters without this property retain an upward display anchor. XP catalyst migration preserves old attached catalysts by moving them to an empty displayed slot or dropping them if occupied.

## Power and loot helpers

`RitualChains.resolve` constructs spatial routes from a snapshot containing source positions and the actual table position. `RitualPower.allocate` resolves signed relative shares against scaled level costs. `RitualMath.automatic` performs deterministic admission and returns a physical `Plan`; use the plan-taking `RitualEngine.commit` overload to revalidate a captured plan. The older selected-map overload performs fresh fixed-level evaluation for compatibility.

`DiscoveryLoot` is a NeoForge global loot modifier. Its conversion applies to every loot-table context and reads `STORED_ENCHANTMENTS`, emits configured discovery pages, and preserves unsupported entries and other components on a copied residual book. `/ritual missing` compares the current registry against `Definitions.SERVER`, including modded/data-defined enchantments.

Bookshelf adapters also expose loose `ritualsnotrolls:knowledge_page` items as sources when they contain exactly one affinity entry. Allow their insertion in the external inventory if desired. Empty/malformed pages and unrelated items carrying knowledge components do not contribute.

`Definitions.Snapshot.enchantments()` contains active definitions only. `disabled()` lists explicit exclusions; `configured(id)` distinguishes those from missing definitions. Client sync sends active definitions, so excluded entries do not enter knowledge/discovery UI. `optional` is evaluated against the frozen enchantment registry at each server-resource reload.

## Built-in optional pedestal bridges

`ForeignPedestals` recognizes `supplementaries:pedestal` and `irons_spellbooks:pedestal` without hard mod dependencies. It uses the native item capability/container or public held-item accessors for slot 0; slots 1/2 are persistent consumption/subtraction attachments. Custom registered adapters take precedence. Native held-item models/renderers are retained. Modifier updates use a dimension/position-checked client payload and chunk-send synchronization. Serialization hooks preserve attachments even if a native pedestal omits the superclass save/load hooks. Break removal drops attached items once; chunk unload does not drop them.

`RitualAttempt` separates successful resource allocation from insufficient-positive-power preview channels. Only the successful plan can reserve resources or commit. `RitualEvent.Start` can therefore contain an empty selected map for a failed-only preview; such an attempt does not emit a Complete event.

## Sable and Create Aeronautics

Sable is optional. The release JAR includes Sable Companion 1.6.0, whose fallback leaves ordinary-world behavior unchanged when Sable is absent. The bridge uses the [Companion API](https://github.com/ryanhcode/sable-companion); Create Aeronautics vessels use the same Sable sub-level support.

A table reads bookshelves, pedestals and storage in its own sub-level. Radius limits and route calculations use those blocks' local positions, so rotating the vessel preserves the setup. Separate sub-levels and terrain do not share a ritual network. Adapters and candidate providers continue to receive the parent `ServerLevel` and real block addresses in Sable's plot coordinate space.

Dropped-item detection checks nearby loaded world and sub-level chunk indexes, including the item or owner's tracked sub-level. It converts the item's world position into each candidate table's local space before applying the capture bounds. Menus measure distance to the table's current world position. `/ritual network` scans the player's tracked sub-level when aboard one.

Captured items and library transfers retain local anchors and resolve the current logical pose each tick. Particle packets carry the table anchor, sub-level identity and local flight geometry while using world positions for delivery; the client resolves the render pose for movement and rotation. Sounds and consumption effects originate at the current world position. Removing or unloading the table, or replacing its sub-level identity, interrupts the session and restores the captured item. Reserved offerings return to surviving pedestals or drop at the target when their source is unavailable.

See [testing notes](VALIDATION.md) for the verified runtime and the limits of client validation.

## Optional recipe viewers

`compat.viewer` contains separate JEI, EMI and REI client plugins. Their APIs are compile-only dependencies. No viewer classes are bundled and no viewer is required in mod metadata. JEI uses a typed recipe-manager plugin and REI uses a dynamic display generator. EMI uses its native recipe/category API plus a client-only optional lookup mixin because its public API does not provide dynamic lookups. These lookups preserve other recipes and vanilla enchanted-book comparison rules, including levels and multiple enchantments.

All adapters resolve recipes from the current client definition snapshot and match knowledge by enchantment and entry list, ignoring per-book identity and Auto-Add settings. The underlying server recipes remain authoritative. Synthetic recipe views show possible random outputs; they do not add variants to the item index. With EMI and JEI together, the native EMI adapter supplies these views.
