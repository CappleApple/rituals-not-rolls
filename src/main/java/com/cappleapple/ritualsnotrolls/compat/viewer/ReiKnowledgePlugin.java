package com.cappleapple.ritualsnotrolls.compat.viewer;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import java.util.*;
import me.shedaniel.math.*;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.*;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.*;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.*;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.util.*;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

@REIPluginClient
public final class ReiKnowledgePlugin implements REIClientPlugin {
  public static final CategoryIdentifier<Display> CATEGORY =
      CategoryIdentifier.of("ritualsnotrolls", "knowledge");

  @Override
  public void registerCategories(CategoryRegistry registry) {
    registry.add(new Category());
    registry.addWorkstations(CATEGORY, EntryStacks.of(Items.CRAFTING_TABLE));
  }

  @Override
  public void registerEntries(EntryRegistry registry) {
    registry.removeEntryIf(
        entry -> entry.getValue() instanceof ItemStack stack && KnowledgeRecipeViews.hidden(stack));
  }

  @Override
  public void registerItemComparators(ItemComparatorRegistry registry) {
    registry.register(
        (context, stack) -> KnowledgeRecipeViews.subtype(stack).hashCode(),
        RitualsNotRolls.PAGE.get(),
        RitualsNotRolls.BOOK.get());
  }

  @Override
  public void registerDisplays(DisplayRegistry registry) {
    registry.registerDisplayGenerator(
        CATEGORY,
        new DynamicDisplayGenerator<Display>() {
          public Optional<List<Display>> getUsageFor(EntryStack<?> entry) {
            return entry.getValue() instanceof ItemStack stack
                ? Optional.of(KnowledgeRecipeViews.uses(stack).stream().map(Display::new).toList())
                : Optional.empty();
          }

          public Optional<List<Display>> getRecipeFor(EntryStack<?> entry) {
            return entry.getValue() instanceof ItemStack stack
                ? Optional.of(
                    KnowledgeRecipeViews.recipes(stack).stream().map(Display::new).toList())
                : Optional.empty();
          }
        });
  }

  public static final class Display extends BasicDisplay {
    final KnowledgeRecipeViews.View view;

    public Display(KnowledgeRecipeViews.View view) {
      super(
          view.inputs().stream().map(s -> EntryIngredients.ofItemStacks(List.of(s))).toList(),
          List.of(EntryIngredients.ofItemStacks(view.outputs())),
          Optional.of(view.id()));
      this.view = view;
    }

    public CategoryIdentifier<?> getCategoryIdentifier() {
      return CATEGORY;
    }
  }

  private static final class Category implements DisplayCategory<Display> {
    public CategoryIdentifier<Display> getCategoryIdentifier() {
      return CATEGORY;
    }

    public Component getTitle() {
      return KnowledgeRecipeViews.title();
    }

    public Renderer getIcon() {
      return EntryStacks.of(RitualsNotRolls.BOOK);
    }

    public int getDisplayWidth(Display display) {
      return 170;
    }

    public int getDisplayHeight() {
      return 80;
    }

    public List<Widget> setupDisplay(Display display, Rectangle bounds) {
      var view = display.view;
      List<Widget> widgets = new ArrayList<>();
      widgets.add(Widgets.createRecipeBase(bounds));
      int x = bounds.x + 5, y = bounds.y + 5;
      widgets.add(
          Widgets.createLabel(new Point(x + 4, y + 2), view.label())
              .leftAligned()
              .noShadow()
              .color(0xFF444444, 0xFFDDDDDD));
      for (int i = 0; i < view.inputs().size(); i++)
        widgets.add(
            Widgets.createSlot(new Point(x + 8 + (i % 2) * 18, y + 19 + (i / 2) * 18))
                .entries(display.getInputEntries().get(i))
                .markInput());
      widgets.add(Widgets.createArrow(new Point(x + 68, y + 28)));
      widgets.add(
          Widgets.createSlot(new Point(x + 120, y + 28))
              .entries(display.getOutputEntries().getFirst())
              .markOutput());
      if (view.random())
        widgets.add(
            Widgets.createLabel(
                    new Point(x + 4, y + 59),
                    Component.translatable("ritualsnotrolls.recipe.remainder"))
                .leftAligned()
                .noShadow()
                .color(0xFF444444, 0xFFDDDDDD));
      return widgets;
    }
  }
}
