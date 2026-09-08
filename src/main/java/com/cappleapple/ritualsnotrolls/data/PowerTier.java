package com.cappleapple.ritualsnotrolls.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Presentation only: relative affinity strength never changes ritual calculations. */
public enum PowerTier {
  VERY_LOW("very_low"),
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high"),
  VERY_HIGH("very_high");

  private final String key;

  PowerTier(String name) {
    key = "ritualsnotrolls.power_tier." + name;
  }

  public MutableComponent label() {
    return Component.translatable(key);
  }

  public MutableComponent description() {
    return Component.translatable("ritualsnotrolls.power", label());
  }

  /** Inputs come from validated, finite, positive datapack affinities. */
  public static PowerTier relative(double power, double highestPower) {
    // Divide first so very large finite powers cannot overflow. Do not round percentages:
    // 19.99% remains Weakest, and each exact 20% boundary starts the next tier.
    double ratio = power / highestPower;
    if (ratio < .2) return VERY_LOW;
    if (ratio < .4) return LOW;
    if (ratio < .6) return MEDIUM;
    if (ratio < .8) return HIGH;
    return VERY_HIGH;
  }
}
