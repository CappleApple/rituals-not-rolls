package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class KnowledgeBookRecipe extends CustomRecipe {
  public KnowledgeBookRecipe(CraftingBookCategory category) {
    super(category);
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    int leather = 0, pages = 0;
    for (int i = 0; i < input.size(); i++) {
      var s = input.getItem(i);
      if (s.is(Items.LEATHER)) leather++;
      else if (s.is(RitualsNotRolls.PAGE)
          && Knowledge.data(s) != null
          && Knowledge.data(s).entries().size() == 1) pages++;
      else if (!s.isEmpty()) return false;
    }
    return leather == 3 && pages == 1;
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
    for (int i = 0; i < input.size(); i++)
      if (input.getItem(i).is(RitualsNotRolls.PAGE))
        return Knowledge.book(Knowledge.data(input.getItem(i)));
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 4;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return RitualsNotRolls.BOOK_RECIPE.get();
  }
}
