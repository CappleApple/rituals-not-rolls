package com.cappleapple.ritualsnotrolls.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/** IDs are stable logical discoveries; power is always resolved from the current datapack. */
public record Affinity(
    String id,
    Optional<ResourceLocation> item,
    Optional<ResourceLocation> tag,
    double power,
    double value) {
  public static final Codec<Affinity> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      Codec.STRING.fieldOf("id").forGetter(Affinity::id),
                      ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Affinity::item),
                      ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Affinity::tag),
                      Codec.DOUBLE.fieldOf("power").forGetter(Affinity::power),
                      Codec.DOUBLE
                          .optionalFieldOf("resource_value", 1.0)
                          .forGetter(Affinity::value))
                  .apply(i, Affinity::new));

  public Affinity {
    if (id.isBlank()
        || id.length() > 128
        || item.isPresent() == tag.isPresent()
        || !Double.isFinite(power)
        || power <= 0
        || !Double.isFinite(value)
        || value < 0)
      throw new IllegalArgumentException(
          "Affinity needs stable id, exactly one item/tag, positive finite power, and nonnegative"
              + " value");
  }

  public boolean matches(ItemStack stack) {
    return !stack.isEmpty()
        && (item.map(x -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(x)).orElse(false)
            || tag.map(x -> stack.is(TagKey.create(Registries.ITEM, x))).orElse(false));
  }

  private static final java.util.Map<ResourceLocation, List<ItemStack>> TAG_ICONS =
      java.util.Collections.synchronizedMap(new java.util.HashMap<>());

  public static void clearCache() {
    TAG_ICONS.clear();
  }

  private List<ItemStack> icons() {
    if (item.isPresent())
      return BuiltInRegistries.ITEM
          .getOptional(item.get())
          .map(x -> List.of(new ItemStack(x)))
          .orElse(List.of());
    return TAG_ICONS.computeIfAbsent(
        tag.orElseThrow(),
        id ->
            BuiltInRegistries.ITEM
                .getTag(TagKey.create(Registries.ITEM, id))
                .map(x -> x.stream().map(h -> new ItemStack(h.value())).toList())
                .orElse(List.of()));
  }

  public List<ItemStack> representatives() {
    return icons().stream().map(ItemStack::copy).toList();
  }

  public ItemStack icon(long time) {
    List<ItemStack> items = icons();
    return items.isEmpty()
        ? ItemStack.EMPTY
        : items.get((int) Math.floorMod(time / 30, items.size())).copy();
  }

  public Component name(long time) {
    ItemStack icon = icon(time);
    if (tag.isPresent())
      return Component.translatable(
          "ritualsnotrolls.tag_material",
          icon.isEmpty() ? tag.get().toString() : icon.getHoverName());
    return icon.isEmpty()
        ? Component.translatable("ritualsnotrolls.unresolved")
        : icon.getHoverName();
  }
}
