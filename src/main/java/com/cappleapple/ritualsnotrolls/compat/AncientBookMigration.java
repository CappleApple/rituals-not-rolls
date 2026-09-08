package com.cappleapple.ritualsnotrolls.compat;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.KnowledgeData;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Reads obsolete item data before registry decoding can discard a removed mod's item. */
public final class AncientBookMigration {
  public static final String OLD_ITEM = "immersiveenchanting:ancient_book";
  public static final String ARCHIVE = "ritualsnotrolls:ancient_book_origin";
  private static final String PENDING = "ritualsnotrolls:ancient_book_pending";
  private static final String CUSTOM = "minecraft:custom_data";

  private AncientBookMigration() {}

  public static Codec<ItemStack> wrap(Codec<ItemStack> original) {
    return new Codec<>() {
      @Override
      public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
        var map = ops.getMap(input).result();
        if (map.isEmpty()) return original.decode(ops, input);
        T rawId = map.get().get("id");
        String id = rawId == null ? "" : ops.getStringValue(rawId).result().orElse("");
        if ((!id.equals(OLD_ITEM) && !id.equals("minecraft:book"))
            || ModList.get() == null
            || ModList.get().isLoaded("immersiveenchanting")) return original.decode(ops, input);
        if (id.equals("minecraft:book") && map.get().get("components") == null)
          return original.decode(ops, input);
        Tag nbt = ops.convertTo(NbtOps.INSTANCE, input);
        if (!(nbt instanceof CompoundTag tag)) return original.decode(ops, input);
        CompoundTag migrated = rewrite(tag, Definitions.SERVER);
        if (migrated == tag) return original.decode(ops, input);
        var result = original.decode(ops, NbtOps.INSTANCE.convertTo(ops, migrated));
        if (result.error().isEmpty()) return result;
        // Cosmetic metadata supplied by another mod may no longer decode. The full original
        // remains archived, while the safe core still loads rather than losing the item.
        migrated.getCompound("components").remove("minecraft:custom_name");
        migrated.getCompound("components").remove("minecraft:lore");
        return original.decode(ops, NbtOps.INSTANCE.convertTo(ops, migrated));
      }

      @Override
      public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
        return original.encode(stack, ops, prefix);
      }
    };
  }

  public static CompoundTag rewrite(CompoundTag input, Definitions.Snapshot definitions) {
    boolean ancient = input.getString("id").equals(OLD_ITEM);
    CompoundTag previous = input.getCompound("components").getCompound(CUSTOM);
    boolean pending =
        input.getString("id").equals("minecraft:book")
            && previous.getBoolean(PENDING)
            && previous.getCompound(ARCHIVE).getString("id").equals(OLD_ITEM);
    if (!ancient && !pending) return input;
    CompoundTag source = ancient ? input : previous.getCompound(ARCHIVE);
    CompoundTag oldComponents = source.getCompound("components");
    CompoundTag stored = oldComponents.getCompound("minecraft:stored_enchantments");
    // ItemEnchantments accepts both the full {levels: {...}} and shorthand map codecs.
    CompoundTag levels =
        stored.contains("levels", Tag.TAG_COMPOUND) ? stored.getCompound("levels") : stored;
    var enchantments = new TreeSet<ResourceLocation>();
    for (String key : levels.getAllKeys()) {
      ResourceLocation enchantment = ResourceLocation.tryParse(key);
      if (enchantment != null
          && levels.contains(key, Tag.TAG_ANY_NUMERIC)
          && levels.getInt(key) > 0) enchantments.add(enchantment);
    }
    // Immersive Enchanting 2.x used this component before vanilla stored enchantments.
    // Respect its original precedence: only consult it when the modern component is absent.
    if (!oldComponents.contains("minecraft:stored_enchantments")) {
      String legacy =
          oldComponents.getCompound("immersiveenchanting:no_network").getString("value1");
      ResourceLocation enchantment = ResourceLocation.tryParse(legacy);
      if (enchantment != null) enchantments.add(enchantment);
    }
    // Ancient books normally hold exactly one enchantment. Preserve ambiguous/malformed books
    // whole rather than silently throwing away extra enchantments or guessing their identity.
    var definition = enchantments.size() == 1 ? definitions.get(enchantments.first()) : null;
    boolean supported = definition != null && !definition.materials().isEmpty();
    if (pending && !supported) return input;

    CompoundTag result = input.copy();
    result.putString("id", supported ? "ritualsnotrolls:knowledge_page" : "minecraft:book");
    CompoundTag components = new CompoundTag();
    CompoundTag custom = oldComponents.getCompound(CUSTOM).copy();
    custom.put(ARCHIVE, source.copy());
    if (!supported) custom.putBoolean(PENDING, true);
    else custom.remove(PENDING);
    components.put(CUSTOM, custom);
    if (supported) {
      // Stable selection makes reloading the same unsaved legacy stack non-rerollable.
      var entries = definition.materials().stream().map(a -> a.id()).sorted().toList();
      String entry = entries.get(Math.floorMod(source.hashCode(), entries.size()));
      var knowledge = new KnowledgeData(definition.enchantment(), List.of(entry), true);
      components.put(
          "ritualsnotrolls:knowledge",
          KnowledgeData.CODEC.encodeStart(NbtOps.INSTANCE, knowledge).getOrThrow());
      for (String key : List.of("minecraft:custom_name", "minecraft:lore"))
        if (oldComponents.contains(key)) components.put(key, oldComponents.get(key).copy());
    } else {
      components.putString(
          "minecraft:custom_name", "{\"translate\":\"ritualsnotrolls.migration.pending\"}");
      ListTag lore = new ListTag();
      lore.add(StringTag.valueOf("{\"translate\":\"ritualsnotrolls.migration.pending_hint\"}"));
      for (var enchantment : enchantments)
        lore.add(StringTag.valueOf("{\"text\":\"" + enchantment + "\"}"));
      components.put("minecraft:lore", lore);
    }
    result.put("components", components);
    return result;
  }
}
