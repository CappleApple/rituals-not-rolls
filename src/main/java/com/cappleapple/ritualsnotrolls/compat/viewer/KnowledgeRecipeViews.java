package com.cappleapple.ritualsnotrolls.compat.viewer;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Viewer-independent descriptions, resolved against the connected server at lookup time. */
public final class KnowledgeRecipeViews {
  public record View(
      ResourceLocation id, List<ItemStack> inputs, List<ItemStack> outputs, boolean random) {
    public Component label() {
      return Component.translatable(
          random ? "ritualsnotrolls.recipe.random" : "ritualsnotrolls.recipe.binding");
    }
  }

  public static Component title() {
    return Component.translatable("ritualsnotrolls.recipe.title");
  }

  public static boolean hidden(ItemStack stack) {
    return stack.is(RitualsNotRolls.PAGE) || stack.is(RitualsNotRolls.BOOK);
  }

  public static String subtype(ItemStack stack) {
    var data = Knowledge.data(stack);
    return data == null
        ? ""
        : data.enchantment() + "/" + String.join(",", new TreeSet<>(data.entries()));
  }

  public static List<View> uses(ItemStack stack) {
    if (stack.is(Items.ENCHANTED_BOOK)) {
      var id = EnchantedBookPageRecipe.firstConfigured(stack, Definitions.CLIENT);
      return id == null ? List.of() : List.of(conversion(stack, Definitions.CLIENT.get(id)));
    }
    var data = Knowledge.data(stack);
    if (stack.is(RitualsNotRolls.PAGE) && data != null && data.entries().size() == 1)
      return List.of(binding(stack));
    if (stack.is(Items.LEATHER)) return all().stream().filter(v -> !v.random).toList();
    return List.of();
  }

  public static List<View> recipes(ItemStack stack) {
    var data = Knowledge.data(stack);
    if (data == null) return List.of();
    if (stack.is(RitualsNotRolls.PAGE)) {
      var def = Definitions.CLIENT.get(data.enchantment());
      if (def == null
          || data.entries().size() != 1
          || def.affinity(data.entries().getFirst()) == null) return List.of();
      var book = enchanted(data.enchantment());
      return book.isEmpty() ? List.of() : List.of(conversion(book, def));
    }
    if (stack.is(RitualsNotRolls.BOOK) && data.entries().size() == 1)
      return List.of(binding(Knowledge.page(data.enchantment(), data.entries().getFirst())));
    return List.of();
  }

  public static List<View> all() {
    List<View> views = new ArrayList<>();
    for (var def : Definitions.CLIENT.enchantments().values()) {
      if (def.materials().isEmpty()) continue;
      var book = enchanted(def.enchantment());
      if (!book.isEmpty()) views.add(conversion(book, def));
      for (var affinity : def.materials())
        views.add(binding(Knowledge.page(def.enchantment(), affinity.id())));
    }
    return views;
  }

  private static ItemStack enchanted(ResourceLocation id) {
    var level = Minecraft.getInstance().level;
    if (level == null) return ItemStack.EMPTY;
    var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id);
    if (holder.isEmpty()) return ItemStack.EMPTY;
    var book = new ItemStack(Items.ENCHANTED_BOOK);
    book.enchant(holder.get(), 1);
    return book;
  }

  private static View conversion(ItemStack book, RitualDefinition def) {
    return new View(
        RitualsNotRolls.id(
            "page/" + def.enchantment().getNamespace() + "/" + def.enchantment().getPath()),
        List.of(book.copyWithCount(1)),
        def.materials().stream().map(a -> Knowledge.page(def.enchantment(), a.id())).toList(),
        true);
  }

  private static View binding(ItemStack page) {
    var data = Knowledge.data(page);
    String key =
        HexFormat.of()
            .formatHex(data.entries().getFirst().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return new View(
        RitualsNotRolls.id(
            "binding/"
                + data.enchantment().getNamespace()
                + "/"
                + data.enchantment().getPath()
                + "/"
                + key),
        List.of(
            page.copyWithCount(1),
            new ItemStack(Items.LEATHER),
            new ItemStack(Items.LEATHER),
            new ItemStack(Items.LEATHER)),
        List.of(Knowledge.book(new KnowledgeData(data.enchantment(), data.entries(), true))),
        false);
  }
}
