package com.cappleapple.ritualsnotrolls;

import com.cappleapple.ritualsnotrolls.data.PowerEquation;
import com.cappleapple.ritualsnotrolls.data.RitualRules;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.DoubleValue CONSUMPTION_MULTIPLIER, XP_BONUS;
  public static final ModConfigSpec.IntValue XP_LEVELS, DURATION;
  public static final ModConfigSpec.IntValue BINDER_PAGE_CAPACITY, BINDER_PAGES_PER_SECOND;
  public static final ModConfigSpec.ConfigValue<String> DUPLICATE_EQUATION, ENCHANTABILITY_EQUATION;
  public static final ModConfigSpec.ConfigValue<List<? extends String>> CONFLICT_MULTIPLIERS;
  public static final ModConfigSpec.BooleanValue SEQUENTIAL_ANIMATIONS,
      CHAIN_ANIMATIONS,
      ENCHANTABILITY;
  public static final ModConfigSpec.DoubleValue ENCHANTABILITY_BASE,
      ENCHANTABILITY_EXPONENT,
      CHAIN_RETURN_BONUS,
      CHAIN_CORRIDOR;
  public static final ModConfigSpec.IntValue RADIUS,
      INVENTORY_RADIUS,
      CACHE_TICKS,
      DROP_CAPTURE_TICKS;

  static {
    var b = new ModConfigSpec.Builder();
    RADIUS =
        b.comment("Bookshelf/pedestal spherical radius. Only loaded chunks participate.")
            .defineInRange("ritualRadius", 16, 1, 64);
    INVENTORY_RADIUS = b.defineInRange("inventoryRadius", 16, 1, 64);
    CACHE_TICKS =
        b.comment(
                "Fallback topology refresh for mods which do not send block notifications. No"
                    + " volume scan.")
            .defineInRange("networkRefreshTicks", 100, 20, 1200);
    DROP_CAPTURE_TICKS =
        b.comment("How long newly thrown items can begin an automatic ritual.")
            .defineInRange("dropCaptureWindowTicks", 200, 20, 1200);
    SEQUENTIAL_ANIMATIONS =
        b.comment(
                "Start each enchantment after the previous ring forms. Earlier rings continue until"
                    + " completion.",
                "False starts all enchantment animations together. Captured rituals keep their"
                    + " initial setting.")
            .define("sequentialEnchantmentAnimations", true);
    CHAIN_ANIMATIONS =
        b.comment(
                "Route through nearby pedestals in order; separate nearest bookshelves own separate"
                    + " chains.")
            .define("chainPedestalAnimations", true);
    CHAIN_RETURN_BONUS =
        b.comment("Extra power for an already visited pedestal on the return path to the table.")
            .defineInRange("chainReturnBonus", .5, 0, 10);
    CHAIN_CORRIDOR =
        b.comment("Maximum side distance from the return route for a pedestal to be visited again.")
            .defineInRange("chainReturnCorridor", 1.25, .1, 8);
    ENCHANTABILITY = b.define("useItemEnchantability", true);
    ENCHANTABILITY_BASE = b.defineInRange("baseEnchantability", 10.0, 1, 100);
    ENCHANTABILITY_EXPONENT =
        b.comment(
                "Cost multiplier is (base / effective item rating) raised to this exponent. Books"
                    + " use base.")
            .defineInRange("enchantabilityExponent", .5, 0, 4);
    ENCHANTABILITY_EQUATION =
        b.comment(
                "Effective item rating above baseEnchantability; lower ratings are unchanged.",
                "Variables: rating, base. Operators: + - * / ^; functions: sqrt, log (natural),"
                    + " min, max, pow.",
                "Result is clamped between base and rating. Use rating to restore the original"
                    + " curve.")
            .define(
                "enchantabilityEquation",
                PowerEquation.ENCHANTABILITY,
                value -> PowerEquation.valid(value, "rating"));
    b.push("rules");
    BINDER_PAGE_CAPACITY =
        b.comment(
                "Maximum pages stored in each Knowledge Binder. Lowering this keeps existing pages"
                    + " but blocks insertion until below the new limit.")
            .defineInRange("binderPageCapacity", 1024, 1, 1_048_576);
    BINDER_PAGES_PER_SECOND =
        b.comment(
                "Maximum pages processed from each thrown Knowledge Binder per 20 ticks at an"
                    + " enchanting table.")
            .defineInRange("binderPagesPerSecond", 16, 1, 1024);
    CONSUMPTION_MULTIPLIER =
        b.comment("Power multiplier for a consumed offering before duplicate diminishing returns.")
            .defineInRange("consumptionMultiplier", 1.5, 1, 100);
    DUPLICATE_EQUATION =
        b.comment(
                "Fraction of power supplied by the nth consumed copy of the same item for one"
                    + " enchantment.",
                "n starts at 1. All bookshelf branches count together; applying and subtracting"
                    + " count separately.",
                "Copies rank by power, strongest first, with position breaking ties. Reusable"
                    + " copies do not count.",
                "Operators: + - * / ^; functions: sqrt, log (natural), min, max, pow. Output is"
                    + " clamped to 0..1.",
                "Use 1 for no diminishing returns; 1 / n for a steeper curve. Zero contributes"
                    + " nothing and is not consumed.")
            .define(
                "duplicateConsumptionEquation",
                PowerEquation.DUPLICATES,
                value -> PowerEquation.valid(value, "n"));
    XP_LEVELS = b.defineInRange("xpLevelsPerCatalyst", 10, 1, 1000);
    XP_BONUS = b.defineInRange("xpBonusPerCatalyst", .1, 0, 100);
    DURATION =
        b.comment("Minimum ritual duration; long routes extend it as needed.")
            .defineInRange("durationTicks", 120, 20, 12000);
    CONFLICT_MULTIPLIERS =
        b.comment(
                "Conflict group=base entries. The nth enchantment in a group costs base^(n-1).",
                "Unlisted groups use 2. Add custom groups used by your enchantment definitions"
                    + " here.")
            .defineListAllowEmpty(
                "conflictMultipliers",
                List.of(
                    "protection=2",
                    "damage=2",
                    "boots=2",
                    "bow=2",
                    "crossbow=2",
                    "trident=2",
                    "mining=2",
                    "mace=2"),
                () -> "custom=2",
                Config::validConflict);
    b.pop();
    SPEC = b.build();
  }

  private static boolean validConflict(Object entry) {
    if (!(entry instanceof String text)) return false;
    var parts = text.split("=", -1);
    if (parts.length != 2 || parts[0].isBlank()) return false;
    try {
      double value = Double.parseDouble(parts[1].strip());
      return Double.isFinite(value) && value >= 1 && value <= 100;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  public static RitualRules rules() {
    // Initial resource loading can precede NeoForge's SERVER config loading.
    if (!SPEC.isLoaded()) return RitualRules.DEFAULT;
    Map<String, Double> conflicts = new LinkedHashMap<>();
    for (String entry : CONFLICT_MULTIPLIERS.get()) {
      var parts = entry.split("=", -1);
      conflicts.put(parts[0].strip(), Double.parseDouble(parts[1].strip()));
    }
    return new RitualRules(
        CONSUMPTION_MULTIPLIER.get(),
        XP_LEVELS.get(),
        XP_BONUS.get(),
        conflicts,
        DURATION.get(),
        DUPLICATE_EQUATION.get());
  }
}
