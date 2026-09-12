package com.cappleapple.ritualsnotrolls.compat.viewer;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

@JeiPlugin
public final class JeiKnowledgePlugin implements IModPlugin {
  public static final RecipeType<KnowledgeRecipeViews.View> TYPE =
      RecipeType.create("ritualsnotrolls", "knowledge", KnowledgeRecipeViews.View.class);

  public ResourceLocation getPluginUid() {
    return RitualsNotRolls.id("knowledge");
  }

  private static boolean nativeJei() {
    return !ModList.get().isLoaded("emi");
  }

  @Override
  public void registerItemSubtypes(ISubtypeRegistration registration) {
    var interpreter =
        new ISubtypeInterpreter<ItemStack>() {
          public Object getSubtypeData(ItemStack stack, UidContext context) {
            return KnowledgeRecipeViews.subtype(stack);
          }

          public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return KnowledgeRecipeViews.subtype(stack);
          }
        };
    registration.registerSubtypeInterpreter(RitualsNotRolls.PAGE.get(), interpreter);
    registration.registerSubtypeInterpreter(RitualsNotRolls.BOOK.get(), interpreter);
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registration) {
    if (nativeJei())
      registration.addRecipeCategories(
          new Category(
              registration
                  .getJeiHelpers()
                  .getGuiHelper()
                  .createDrawableItemLike(RitualsNotRolls.BOOK)));
  }

  @Override
  public void registerAdvanced(IAdvancedRegistration registration) {
    if (nativeJei()) registration.addTypedRecipeManagerPlugin(TYPE, new Lookup());
  }

  public static final class Lookup
      implements ISimpleRecipeManagerPlugin<KnowledgeRecipeViews.View> {
    public boolean isHandledInput(ITypedIngredient<?> input) {
      return !getRecipesForInput(input).isEmpty();
    }

    public boolean isHandledOutput(ITypedIngredient<?> output) {
      return !getRecipesForOutput(output).isEmpty();
    }

    public List<KnowledgeRecipeViews.View> getRecipesForInput(ITypedIngredient<?> input) {
      return input.getItemStack().map(KnowledgeRecipeViews::uses).orElse(List.of());
    }

    public List<KnowledgeRecipeViews.View> getRecipesForOutput(ITypedIngredient<?> output) {
      return output.getItemStack().map(KnowledgeRecipeViews::recipes).orElse(List.of());
    }

    public List<KnowledgeRecipeViews.View> getAllRecipes() {
      return KnowledgeRecipeViews.all();
    }
  }

  private record Category(IDrawable icon) implements IRecipeCategory<KnowledgeRecipeViews.View> {
    public RecipeType<KnowledgeRecipeViews.View> getRecipeType() {
      return TYPE;
    }

    public Component getTitle() {
      return KnowledgeRecipeViews.title();
    }

    public IDrawable getIcon() {
      return icon;
    }

    public int getWidth() {
      return 160;
    }

    public int getHeight() {
      return 70;
    }

    public void setRecipe(
        IRecipeLayoutBuilder builder, KnowledgeRecipeViews.View view, IFocusGroup focuses) {
      for (int i = 0; i < view.inputs().size(); i++)
        builder
            .addInputSlot(8 + (i % 2) * 18, 19 + (i / 2) * 18)
            .addItemStack(view.inputs().get(i))
            .setStandardSlotBackground();
      builder.addOutputSlot(120, 28).addItemStacks(view.outputs()).setStandardSlotBackground();
      builder.setShapeless();
    }

    public void draw(
        KnowledgeRecipeViews.View view,
        IRecipeSlotsView slots,
        GuiGraphics graphics,
        double x,
        double y) {
      var font = Minecraft.getInstance().font;
      graphics.drawString(font, view.label(), 4, 2, 0xFF444444, false);
      graphics.drawString(font, "->", 76, 32, 0xFF777777, false);
      if (view.random())
        graphics.drawString(
            font,
            Component.translatable("ritualsnotrolls.recipe.remainder"),
            4,
            59,
            0xFF555555,
            false);
    }
  }
}
