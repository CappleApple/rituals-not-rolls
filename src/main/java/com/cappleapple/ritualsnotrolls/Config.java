package com.cappleapple.ritualsnotrolls;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
  public static final ModConfigSpec SPEC;
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
                "Cost multiplier is (base / max(1, item rating)) raised to this exponent. Books use"
                    + " base.")
            .defineInRange("enchantabilityExponent", .5, 0, 4);
    SPEC = b.build();
  }
}
