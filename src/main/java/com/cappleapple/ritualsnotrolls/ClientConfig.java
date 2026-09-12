package com.cappleapple.ritualsnotrolls;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.ConfigValue<String> ENCHANTMENT_TOOLTIP_COLOR,
      CURSE_TOOLTIP_COLOR;

  static {
    var builder = new ModConfigSpec.Builder();
    ENCHANTMENT_TOOLTIP_COLOR =
        builder
            .comment("Enchantment name color, as #RRGGBB. Descriptions remain white.")
            .define("enchantmentTooltipColor", "#FFAA00", ClientConfig::validColor);
    CURSE_TOOLTIP_COLOR =
        builder
            .comment(
                "Curse name color, as #RRGGBB. Descriptions remain white. Uses the enchantment"
                    + " curse tag.")
            .define("curseTooltipColor", "#FF5555", ClientConfig::validColor);
    SPEC = builder.build();
  }

  private static boolean validColor(Object value) {
    return value instanceof String text && text.matches("#[0-9a-fA-F]{6}");
  }

  public static int tooltipColor(boolean curse) {
    return Integer.parseInt(
        (curse ? CURSE_TOOLTIP_COLOR : ENCHANTMENT_TOOLTIP_COLOR).get().substring(1), 16);
  }
}
