package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PowerTierTest {
  @Test
  void tierBoundariesIncludeTheirLowerLimitWithoutRounding() {
    double[] powers = {0, 15, 19, 19.9999, 20, 39.9999, 40, 59.9999, 60, 79.9999, 80, 100};
    PowerTier[] expected = {
      PowerTier.VERY_LOW,
      PowerTier.VERY_LOW,
      PowerTier.VERY_LOW,
      PowerTier.VERY_LOW,
      PowerTier.LOW,
      PowerTier.LOW,
      PowerTier.MEDIUM,
      PowerTier.MEDIUM,
      PowerTier.HIGH,
      PowerTier.HIGH,
      PowerTier.VERY_HIGH,
      PowerTier.VERY_HIGH
    };
    for (int i = 0; i < powers.length; i++)
      assertEquals(expected[i], PowerTier.relative(powers[i], 100), "Power " + powers[i]);
  }

  private static Affinity affinity(String id, double power, boolean tag) {
    var location = ResourceLocation.withDefaultNamespace(id);
    return new Affinity(
        id,
        tag ? Optional.empty() : Optional.of(location),
        tag ? Optional.of(location) : Optional.empty(),
        power,
        1);
  }

  private static RitualDefinition definition(Affinity... affinities) {
    return new RitualDefinition(
        ResourceLocation.withDefaultNamespace("sharpness"),
        List.of(affinities),
        Map.of("1", 50.0),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  @Test
  void strongestUndiscoveredAndTagAffinitiesBothSetTheScale() {
    var string = affinity("string", 15, false);
    assertEquals(
        PowerTier.VERY_LOW, definition(string, affinity("cobweb", 100, false)).powerTier(string));
    assertEquals(
        PowerTier.VERY_LOW, definition(string, affinity("wool", 100, true)).powerTier(string));
    assertEquals(PowerTier.VERY_HIGH, definition(string).powerTier(string));
  }

  @Test
  void updatedDefinitionAndDifferentEnchantmentsCanGiveDifferentTiers() {
    var string = affinity("string", 15, false);
    assertEquals(
        PowerTier.VERY_LOW, definition(string, affinity("cobweb", 100, false)).powerTier(string));
    assertEquals(
        PowerTier.HIGH, definition(string, affinity("cobweb", 20, false)).powerTier(string));
    assertEquals(
        PowerTier.VERY_HIGH, definition(string, affinity("cobweb", 15, false)).powerTier(string));
  }

  @Test
  void extremeFinitePowersKeepTheirRelativeTier() {
    assertEquals(PowerTier.VERY_HIGH, PowerTier.relative(Double.MAX_VALUE, Double.MAX_VALUE));
    assertEquals(PowerTier.MEDIUM, PowerTier.relative(Double.MAX_VALUE / 2, Double.MAX_VALUE));
    assertEquals(PowerTier.VERY_HIGH, PowerTier.relative(Double.MIN_VALUE, Double.MIN_VALUE));
    assertEquals(PowerTier.VERY_LOW, PowerTier.relative(Double.MIN_VALUE, Double.MAX_VALUE));
  }
}
