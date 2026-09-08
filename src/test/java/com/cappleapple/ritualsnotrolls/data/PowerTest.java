package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import com.cappleapple.ritualsnotrolls.ritual.RitualPower;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PowerTest {
  private static ResourceLocation id(String name) {
    return ResourceLocation.withDefaultNamespace(name);
  }

  private static final ResourceLocation SHARP = id("sharpness"), LOOT = id("looting");

  private static RitualPower.Target target(int current, double... costs) {
    NavigableMap<Integer, Double> map = new TreeMap<>();
    for (int i = 0; i < costs.length; i++) map.put(i + 1, costs[i]);
    return new RitualPower.Target(current, map);
  }

  private static RitualPower.Material material(
      int index, String item, Map<ResourceLocation, Double> powers) {
    return new RitualPower.Material(
        new BlockPos(index, 0, 0), id(item), powers.values().stream().allMatch(p -> p < 0), powers);
  }

  @Test
  void halvesRelativeAffinitiesWhenBothReachNextLevel() {
    var result =
        RitualPower.allocate(
            Map.of(SHARP, target(1, 2, 5), LOOT, target(1, 3, 10)),
            List.of(material(0, "diamond", Map.of(SHARP, 10.0, LOOT, 20.0))));
    assertEquals(Map.of(SHARP, 5.0, LOOT, 10.0), result.powers());
    assertEquals(Map.of(SHARP, 2, LOOT, 2), result.levels());
  }

  @Test
  void cannotUpgradeOneSideSoItDoesNotShare() {
    var result =
        RitualPower.allocate(
            Map.of(SHARP, target(1, 2, 11), LOOT, target(1, 3, 15)),
            List.of(material(0, "diamond", Map.of(SHARP, 10.0, LOOT, 20.0))));
    assertEquals(20.0, result.powers().get(LOOT));
    assertEquals(0.0, result.powers().get(SHARP));
    assertEquals(Set.of(LOOT), result.users().get(BlockPos.ZERO));
  }

  @Test
  void allFiveMayShareWithTheirOwnRelativePowers() {
    Map<ResourceLocation, RitualPower.Target> targets = new LinkedHashMap<>();
    Map<ResourceLocation, Double> powers = new LinkedHashMap<>();
    for (int i = 1; i <= 5; i++) {
      targets.put(id("test" + i), target(0, i));
      powers.put(id("test" + i), i * 5.0);
    }
    var result = RitualPower.allocate(targets, List.of(material(0, "diamond", powers)));
    for (int i = 1; i <= 5; i++) assertEquals((double) i, result.powers().get(id("test" + i)));
    assertTrue(result.levels().values().stream().allMatch(level -> level == 1));
  }

  @Test
  void uselessSharedMaterialIsReleasedWhenOtherMaterialsAlreadySupplySameLevel() {
    var targets = new LinkedHashMap<ResourceLocation, RitualPower.Target>();
    targets.put(SHARP, target(0, 10, 100));
    targets.put(LOOT, target(0, 10, 20));
    var result =
        RitualPower.allocate(
            targets,
            List.of(
                material(0, "iron_ingot", Map.of(SHARP, 10.0)),
                material(1, "diamond", Map.of(SHARP, 10.0, LOOT, 20.0))));
    assertEquals(10.0, result.powers().get(SHARP));
    assertEquals(20.0, result.powers().get(LOOT));
  }

  @Test
  void duplicateConsumedMaterialsAreConsideredTogether() {
    var result =
        RitualPower.allocate(
            Map.of(SHARP, target(2, 10, 30)),
            List.of(
                material(0, "diamond", Map.of(SHARP, -15.0)),
                material(1, "diamond", Map.of(SHARP, -15.0))));
    assertEquals(0, result.levels().get(SHARP));
    assertEquals(2, result.users().values().stream().filter(s -> !s.isEmpty()).count());
  }

  @Test
  void subtractionNeedsAnEntireLevelAndDoesNotCreateProgress() {
    var t = target(3, 10, 30, 60);
    assertEquals(3, t.level(-29));
    assertEquals(2, t.level(-30));
    assertEquals(1, t.level(-50));
    assertEquals(0, t.level(-60));
    assertEquals(0, target(0, 10).level(-100));
  }

  @Test
  void weakSubtractionHasNoUsers() {
    var result =
        RitualPower.allocate(
            Map.of(SHARP, target(2, 10, 30)),
            List.of(material(0, "diamond", Map.of(SHARP, -19.0))));
    assertTrue(result.users().get(BlockPos.ZERO).isEmpty());
    assertEquals(2, result.levels().get(SHARP));
    assertEquals(0.0, result.powers().get(SHARP));
  }

  @Test
  void costsForExistingLevelsMissingFromPackAreInterpolated() {
    var t = new RitualPower.Target(3, new TreeMap<>(Map.of(1, 10.0, 3, 50.0)));
    assertEquals(30.0, t.cost(2));
    assertEquals(2, t.level(-20));
    assertEquals(50.0 * 16 / 9, t.cost(4), 1e-8);
  }

  @Test
  void subtractionOffsetsIncomingPowerBeforeDecidingTheUpgrade() {
    var result =
        RitualPower.allocate(
            Map.of(SHARP, target(0, 10, 30)),
            List.of(
                material(0, "iron_ingot", Map.of(SHARP, 35.0)),
                material(1, "diamond", Map.of(SHARP, -10.0))));
    assertEquals(1, result.levels().get(SHARP));
    assertEquals(25.0, result.powers().get(SHARP));
  }
}
