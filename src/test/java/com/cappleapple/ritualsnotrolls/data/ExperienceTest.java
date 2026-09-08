package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperienceTest {
  @Test
  void partialPaymentUsesEquivalentLevelsRatherThanPercentageOfRawPoints() {
    var partial = RitualRules.DEFAULT.experience(2, 315);
    assertEquals(315, partial.points());
    assertEquals(15, partial.levels());
    assertEquals(1.15, partial.multiplier(), 1e-12);
    assertEquals(1.0, RitualRules.DEFAULT.experience(2, 0).multiplier());
    assertEquals(0, RitualRules.DEFAULT.experience(0, 1395).points());
    assertEquals(0, RitualRules.DEFAULT.experience(2, -1).points());
  }

  @Test
  void paymentStopsAtCapacityAndHonorsConfiguredLevelAndBonusRates() {
    var full = RitualRules.DEFAULT.experience(2, 1395);
    assertEquals(550, full.points());
    assertEquals(20, full.levels());
    assertEquals(1.2, full.multiplier(), 1e-12);
    var custom = new RitualRules(2, 5, .2, Map.of(), 120).experience(4, 315);
    assertEquals(315, custom.points());
    assertEquals(1.6, custom.multiplier(), 1e-12);
  }

  @Test
  void partialLevelsInterpolateAcrossEveryVanillaXpCurveBoundary() {
    for (int level : new int[] {0, 1, 10, 15, 16, 17, 30, 31, 32, 40, 1000}) {
      int base = (int) RitualRules.experienceAtLevel(level);
      int span = (int) RitualRules.experienceAtLevel(level + 1) - base;
      assertEquals(level, RitualRules.levelsAtExperience(base), 1e-12);
      for (int offset : new int[] {1, span / 2, span - 1})
        assertEquals(
            level + offset / (double) span, RitualRules.levelsAtExperience(base + offset), 1e-9);
    }
    double huge = RitualRules.levelsAtExperience(Integer.MAX_VALUE);
    assertTrue(Double.isFinite(huge) && huge > 20000);
  }
}
