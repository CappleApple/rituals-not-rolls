package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PowerEquationTest {
  @Test
  void defaultConsumedCopiesDiminishWithoutChangingTheFirst() {
    assertEquals(1.5, RitualRules.DEFAULT.consumptionMultiplier());
    double previous = 2, total = 0;
    for (int n = 1; n <= 100; n++) {
      double value = RitualRules.DEFAULT.duplicateFactor(n);
      assertTrue(value > 0 && value < previous);
      previous = value;
      total += value;
    }
    assertEquals(1, RitualRules.DEFAULT.duplicateFactor(1));
    assertEquals(.5, RitualRules.DEFAULT.duplicateFactor(4));
    assertTrue(total > 10 && total < 20);
  }

  @Test
  void expressionsHaveConventionalPrecedenceAndSupportCustomCurves() {
    assertEquals(512, PowerEquation.compile("2^3^2", "n").evaluate(1, 1));
    assertEquals(-4, PowerEquation.compile("-2^2", "n").evaluate(1, 1));
    assertEquals(.25, PowerEquation.duplicateFactor("1 / n", 4));
    assertEquals(1, PowerEquation.duplicateFactor("1", 100));
    assertEquals(.5, PowerEquation.duplicateFactor("pow(n, -0.5)", 4));
    assertEquals(0, PowerEquation.duplicateFactor("max(0, 2 - n)", 2));
  }

  @Test
  void invalidOrUnboundedResultsCannotPoisonPower() {
    for (String source :
        new String[] {"", "1 / 0", "sqrt(-1)", "unknown(n)", "rating", "n; exit()", "1 +", "n..2"})
      assertFalse(PowerEquation.valid(source, "n"), source);
    assertEquals(1, PowerEquation.duplicateFactor("20", 2));
    assertEquals(1 / Math.sqrt(7), PowerEquation.duplicateFactor("1 / (n - 7)", 7));
    assertEquals(30, PowerEquation.effectiveEnchantability("rating * 100", 30, 10));
    assertEquals(10, PowerEquation.effectiveEnchantability("0", 30, 10));
  }

  @Test
  void highRatingsDiminishWhileLowRatingsAndDisabledCurveRemainUnchanged() {
    assertEquals(5, PowerEquation.effectiveEnchantability(PowerEquation.ENCHANTABILITY, 5, 10));
    assertEquals(10, PowerEquation.effectiveEnchantability(PowerEquation.ENCHANTABILITY, 10, 10));
    double r20 = PowerEquation.effectiveEnchantability(PowerEquation.ENCHANTABILITY, 20, 10);
    double r30 = PowerEquation.effectiveEnchantability(PowerEquation.ENCHANTABILITY, 30, 10);
    assertEquals(10 * (1 + Math.log(2)), r20);
    assertTrue(r30 > r20 && r30 - r20 < r20 - 10);
    assertEquals(30, PowerEquation.effectiveEnchantability("rating", 30, 10));
    assertTrue(
        Double.isFinite(
            PowerEquation.effectiveEnchantability(
                PowerEquation.ENCHANTABILITY, Integer.MAX_VALUE, 10)));
  }
}
