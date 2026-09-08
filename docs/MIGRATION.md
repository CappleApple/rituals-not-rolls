# Immersive Enchanting world migration

Install Rituals Not Rolls 1.5.0 on the client and server before opening an existing world without Immersive Enchanting. Books convert as players, inventories, entities and chunks load; the mod does not force-load every region. Items already removed and saved by an earlier load cannot be reconstructed from empty slots; their original world backup is needed.

Each saved `immersiveenchanting:ancient_book` with a supported enchantment becomes a `ritualsnotrolls:knowledge_page` containing **one affinity for that same enchantment**. Stack counts are preserved. Selection is deterministic from the old stack data and sorted affinity IDs; saved converted pages keep that affinity on subsequent loads. Identical source stacks can produce the same affinity. Place the pages directly into chiseled bookshelves or add them to knowledge books.

## Supported data

- Modern `minecraft:stored_enchantments`, including both `{levels:{"minecraft:mending":1}}` and shorthand maps.
- Older NeoForge `immersiveenchanting:no_network` components with a `value1` enchantment ID. Modern data takes precedence when both components remain.
- Replicated ancient books, even though their old `immersiveenchanting:replicated` component is no longer registered.

The modern format was checked against the installed 6.0.2 artifact and the author's [stored-enchantment utility](https://github.com/alfiehanks/Immersive-Enchanting/blob/neoforge-1.21.1/src/main/java/me/alfie/immersiveenchanting/util/EnchantmentUtil.java). The legacy format was checked against the author's [historical component codec](https://github.com/alfiehanks/Immersive-Enchanting/blob/0b2371cb8124294fe094a9cc8735142cdb87ef6b/src/main/java/me/alfie/immersiveenchanting/item/legacy/EnchantmentDataComponent.java) and [component registration](https://github.com/alfiehanks/Immersive-Enchanting/blob/0b2371cb8124294fe094a9cc8735142cdb87ef6b/src/main/java/me/alfie/immersiveenchanting/item/legacy/ModDataComponents.java). This is an independent format adapter; no Immersive Enchanting code or dependency is bundled.

## Preservation and unsupported books

An enchantment needs a loaded ritual definition with its material affinities. The definition may come from the default data or a custom datapack. Books with missing/unknown enchantments, malformed data, or ambiguous multiple enchantments become a vanilla book labeled **Ancient Book (awaiting ritual data)**. They grant no ritual knowledge. A single-enchantment pending book retries conversion when it is loaded after matching data becomes available. `/reload` loads definitions; an already loaded item retries on its next item load, such as after saving and reopening the world.

Every migrated or pending stack keeps its full original item NBT in `minecraft:custom_data` under `ritualsnotrolls:ancient_book_origin`. This includes obsolete components and any metadata that cannot be applied to a page. Valid custom names/lore are also applied to converted pages. Pending books carry `ritualsnotrolls:ancient_book_pending: true`. Archives are not recursively converted or duplicated on every save.

The adapter is disabled while Immersive Enchanting itself is loaded. Ordinary enchanted books and existing knowledge pages are unaffected. This conversion does not migrate that mod's blocks, configuration, cost definitions, or unknown container implementations that use their own serialization rather than Minecraft ItemStack codecs. Only books retaining the ancient-book item ID and supported enchantment metadata can be identified.

## Why this uses an item-loading adapter

Minecraft's DataFixerUpper upgrade path is tied to Minecraft data versions. Removing a mod while staying on Minecraft 1.21.1 does not create a new vanilla upgrade step. Waiting until an inventory-loaded event is also too late: decoding the unregistered item or old component can already discard the book.

The narrowly scoped mixin wraps the persistent ItemStack codecs before their derived codecs are created. It rewrites recognized old book data immediately before item/component registry decoding, including direct nested-container codec use. Current-version worlds therefore migrate without spoofing their DataVersion, registering fake Immersive Enchanting items, or scanning world files offline. [Validation](VALIDATION.md) includes an actual three-process save/load/restart check.
