package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.ritual.RitualMath;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * One random affinity; other enchantments stay on the returned book. No shared recipe RNG state.
 */
public final class EnchantedBookPageRecipe extends CustomRecipe {
  public EnchantedBookPageRecipe(CraftingBookCategory category) {
    super(category);
  }

  public static ResourceLocation firstConfigured(ItemStack stack, Definitions.Snapshot data) {
    if (!stack.is(Items.ENCHANTED_BOOK)) return null;
    return RitualMath.enchantments(stack).keySet().stream()
        .map(h -> h.unwrapKey().orElseThrow().location())
        .sorted()
        .filter(id -> data.get(id) != null && !data.get(id).materials().isEmpty())
        .findFirst()
        .orElse(null);
  }

  private static ItemStack input(CraftingInput input) {
    ItemStack book = ItemStack.EMPTY;
    for (var stack : input.items()) {
      if (stack.isEmpty()) continue;
      if (!book.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) return ItemStack.EMPTY;
      book = stack;
    }
    return book;
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    return firstConfigured(input(input), Definitions.forSide(level.isClientSide)) != null;
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
    var data = Definitions.forSide(EffectiveSide.get().isClient());
    var id = firstConfigured(input(input), data);
    if (id == null) return ItemStack.EMPTY;
    var materials = data.get(id).materials();
    return Knowledge.page(id, materials.get(RandomSource.create().nextInt(materials.size())).id());
  }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
    var remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
    var book = input(input);
    var id = firstConfigured(book, Definitions.forSide(EffectiveSide.get().isClient()));
    if (id == null) return remaining;
    var enchantments = new ItemEnchantments.Mutable(RitualMath.enchantments(book));
    for (var enchantment : RitualMath.enchantments(book).keySet())
      if (enchantment.unwrapKey().orElseThrow().location().equals(id))
        enchantments.set(enchantment, 0);
    if (!enchantments.toImmutable().isEmpty()) {
      var rest = book.copyWithCount(1);
      rest.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
      for (int i = 0; i < input.size(); i++)
        if (!input.getItem(i).isEmpty()) remaining.set(i, rest);
    }
    return remaining;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 1;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return RitualsNotRolls.PAGE_RECIPE.get();
  }
}
