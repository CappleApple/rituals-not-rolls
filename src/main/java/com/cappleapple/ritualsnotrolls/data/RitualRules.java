package com.cappleapple.ritualsnotrolls.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

public record RitualRules(
    double consumptionMultiplier,
    int xpLevelsPerCatalyst,
    double xpBonusPerCatalyst,
    Map<String, Double> conflicts,
    int duration,
    String duplicateConsumptionEquation) {
  public RitualRules(
      double consumptionMultiplier,
      int xpLevelsPerCatalyst,
      double xpBonusPerCatalyst,
      Map<String, Double> conflicts,
      int duration) {
    this(
        consumptionMultiplier,
        xpLevelsPerCatalyst,
        xpBonusPerCatalyst,
        conflicts,
        duration,
        PowerEquation.DUPLICATES);
  }

  public double duplicateFactor(int copy) {
    return PowerEquation.duplicateFactor(duplicateConsumptionEquation, copy);
  }

  public static final RitualRules DEFAULT =
      new RitualRules(
          1.5,
          10,
          .1,
          Map.of(
              "protection",
              2.0,
              "damage",
              2.0,
              "boots",
              2.0,
              "bow",
              2.0,
              "crossbow",
              2.0,
              "trident",
              2.0,
              "mining",
              2.0,
              "mace",
              2.0),
          120);
  public static final Codec<RitualRules> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      Codec.DOUBLE
                          .optionalFieldOf("consumption_multiplier", 1.5)
                          .forGetter(RitualRules::consumptionMultiplier),
                      Codec.INT
                          .optionalFieldOf("xp_levels_per_catalyst", 10)
                          .forGetter(RitualRules::xpLevelsPerCatalyst),
                      Codec.DOUBLE
                          .optionalFieldOf("xp_bonus_per_catalyst", .1)
                          .forGetter(RitualRules::xpBonusPerCatalyst),
                      Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                          .optionalFieldOf("conflict_multipliers", DEFAULT.conflicts)
                          .forGetter(RitualRules::conflicts),
                      Codec.INT
                          .optionalFieldOf("duration_ticks", 120)
                          .forGetter(RitualRules::duration),
                      Codec.STRING
                          .optionalFieldOf(
                              "duplicate_consumption_equation", PowerEquation.DUPLICATES)
                          .forGetter(RitualRules::duplicateConsumptionEquation))
                  .apply(i, RitualRules::new));

  public RitualRules {
    conflicts = Map.copyOf(conflicts);
    if (!PowerEquation.valid(duplicateConsumptionEquation, "n"))
      throw new IllegalArgumentException("Invalid duplicate consumption equation");
    if (!Double.isFinite(consumptionMultiplier)
        || consumptionMultiplier < 1
        || xpLevelsPerCatalyst < 1
        || xpLevelsPerCatalyst > 1000
        || !Double.isFinite(xpBonusPerCatalyst)
        || xpBonusPerCatalyst < 0
        || xpBonusPerCatalyst > 100
        || duration < 20
        || duration > 12000
        || conflicts.values().stream().anyMatch(v -> !Double.isFinite(v) || v < 1))
      throw new IllegalArgumentException("Invalid ritual rules");
  }

  public record Experience(int points, double levels, double multiplier) {}

  /** Spend up to the catalyst capacity, with the bonus measured in equivalent levels from zero. */
  public Experience experience(int catalysts, int available) {
    int points = (int) Math.min(Math.max(0, available), xpCost(catalysts));
    double levels = levelsAtExperience(points);
    return new Experience(points, levels, 1 + levels / xpLevelsPerCatalyst * xpBonusPerCatalyst);
  }

  public static double levelsAtExperience(int points) {
    long value = Math.max(0, points), low = 0, high = value + 1;
    while (high - low > 1) {
      long middle = (low + high) / 2;
      if (experienceAtLevel(middle) <= value) low = middle;
      else high = middle;
    }
    long base = experienceAtLevel(low);
    return low + (value - base) / (double) (experienceAtLevel(low + 1) - base);
  }

  public double xpMultiplier(int catalysts) {
    return 1 + Math.max(0, catalysts) * xpBonusPerCatalyst;
  }

  public long xpLevels(int catalysts) {
    return (long) Math.max(0, catalysts) * xpLevelsPerCatalyst;
  }

  public long xpCost(int catalysts) {
    return experienceAtLevel(xpLevels(catalysts));
  }

  /** Cost of reaching this total level from zero, not N separate ten-level purchases. */
  public static long experienceAtLevel(long level) {
    double l = Math.max(0, level);
    double xp =
        l <= 16
            ? l * l + 6 * l
            : l <= 31 ? 2.5 * l * l - 40.5 * l + 360 : 4.5 * l * l - 162.5 * l + 2220;
    return xp >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) xp;
  }
}
