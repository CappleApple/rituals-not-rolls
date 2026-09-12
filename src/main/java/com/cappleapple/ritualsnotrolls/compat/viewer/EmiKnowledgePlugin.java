package com.cappleapple.ritualsnotrolls.compat.viewer;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

@EmiEntrypoint
public final class EmiKnowledgePlugin implements EmiPlugin {
  public static final EmiRecipeCategory CATEGORY =
      new EmiRecipeCategory(RitualsNotRolls.id("knowledge"), EmiStack.of(RitualsNotRolls.BOOK)) {
        @Override
        public Component getName() {
          return KnowledgeRecipeViews.title();
        }
      };

  @Override
  public void register(EmiRegistry registry) {
    registry.addCategory(CATEGORY);
    registry.addWorkstation(CATEGORY, EmiStack.of(Items.CRAFTING_TABLE));
    registry.removeEmiStacks(stack -> KnowledgeRecipeViews.hidden(stack.getItemStack()));
    var comparison =
        Comparison.compareData(stack -> KnowledgeRecipeViews.subtype(stack.getItemStack()));
    registry.setDefaultComparison(RitualsNotRolls.PAGE.get(), comparison);
    registry.setDefaultComparison(RitualsNotRolls.BOOK.get(), comparison);
    registry.addDeferredRecipes(
        consumer -> KnowledgeRecipeViews.all().forEach(view -> consumer.accept(new Recipe(view))));
  }

  /** Appends live lookup results without altering vanilla enchanted-book comparison rules. */
  public static List<EmiRecipe> lookup(List<EmiRecipe> original, EmiStack stack, boolean uses) {
    var views =
        uses
            ? KnowledgeRecipeViews.uses(stack.getItemStack())
            : KnowledgeRecipeViews.recipes(stack.getItemStack());
    if (views.isEmpty()) return original;
    List<EmiRecipe> result = new ArrayList<>(original);
    result.removeIf(r -> r.getCategory() == CATEGORY);
    views.forEach(view -> result.add(new Recipe(view)));
    return result;
  }

  public record Recipe(KnowledgeRecipeViews.View view) implements EmiRecipe {
    public EmiRecipeCategory getCategory() {
      return CATEGORY;
    }

    public ResourceLocation getId() {
      return ResourceLocation.fromNamespaceAndPath(
          view.id().getNamespace(), "/" + view.id().getPath());
    }

    public List<EmiIngredient> getInputs() {
      return view.inputs().stream().<EmiIngredient>map(EmiStack::of).toList();
    }

    public List<EmiStack> getOutputs() {
      return view.outputs().stream().map(EmiStack::of).toList();
    }

    public int getDisplayWidth() {
      return 160;
    }

    public int getDisplayHeight() {
      return 70;
    }

    public boolean supportsRecipeTree() {
      return !view.random();
    }

    public void addWidgets(WidgetHolder widgets) {
      widgets.addText(view.label(), 4, 2, 0xFF444444, false);
      var inputs = getInputs();
      for (int i = 0; i < inputs.size(); i++)
        widgets.addSlot(inputs.get(i), 7 + (i % 2) * 18, 18 + (i / 2) * 18);
      widgets.addFillingArrow(68, 28, 1200);
      widgets.addSlot(EmiIngredient.of(getOutputs()), 115, 23).large(true).recipeContext(this);
      if (view.random())
        widgets.addText(
            Component.translatable("ritualsnotrolls.recipe.remainder"), 4, 59, 0xFF555555, false);
    }
  }
}
