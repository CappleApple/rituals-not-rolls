package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.data.PowerEquation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Player-facing explanations with values from the connected server's settings. */
public final class RitualGuide {
  public static Component chapter(int chapter, CompoundTag state) {
    if (!state.contains("guide")) return Component.translatable("ritualsnotrolls.guide.waiting");
    var settings = state.getCompound("guide");
    var rules = Definitions.CLIENT.rules();
    double base = settings.getDouble("base");
    double effective =
        PowerEquation.effectiveEnchantability(settings.getString("equation"), base * 2, base);
    double cost = Math.pow(base / effective, settings.getDouble("exponent"));
    Object[] values =
        switch (chapter) {
          case 0 -> new Object[] {settings.getInt("radius")};
          case 1 ->
              new Object[] {settings.getInt("binder_capacity"), settings.getInt("binder_rate")};
          case 3 ->
              new Object[] {
                BookScreen.format(rules.consumptionMultiplier()),
                percent(rules.duplicateFactor(1)),
                percent(rules.duplicateFactor(2)),
                percent(rules.duplicateFactor(3)),
                percent(rules.duplicateFactor(4))
              };
          case 4 ->
              new Object[] {
                BookScreen.format(base),
                BookScreen.format(base * 2),
                percent(cost),
                Component.translatable(
                    settings.getBoolean("enchantability")
                        ? "ritualsnotrolls.guide.enabled"
                        : "ritualsnotrolls.guide.disabled")
              };
          case 5 ->
              new Object[] {
                Component.translatable(
                    settings.getBoolean("chains")
                        ? "ritualsnotrolls.guide.enabled"
                        : "ritualsnotrolls.guide.disabled"),
                percent(settings.getDouble("return_bonus"))
              };
          case 6 ->
              new Object[] {
                rules.xpLevelsPerCatalyst(),
                percent(rules.xpBonusPerCatalyst()),
                rules.xpCost(1),
                rules.xpCost(2)
              };
          default -> new Object[0];
        };
    return Component.translatable("ritualsnotrolls.guide.chapter." + chapter + ".body", values);
  }

  private static String percent(double value) {
    return BookScreen.format(value * 100) + "%";
  }

  public static net.minecraft.resources.ResourceLocation example(int chapter) {
    return com.cappleapple.ritualsnotrolls.RitualsNotRolls.id(
        "textures/gui/guide/example_" + chapter + ".png");
  }

  public static Component caption(int chapter) {
    return Component.translatable("ritualsnotrolls.guide.chapter." + chapter + ".caption");
  }
}
