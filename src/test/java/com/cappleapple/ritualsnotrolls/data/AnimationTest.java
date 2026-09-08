package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import com.cappleapple.ritualsnotrolls.client.PageMesh;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AnimationTest {
  private List<RitualAnimation.Input> inputs() {
    return List.of(
        new RitualAnimation.Input(ResourceLocation.withDefaultNamespace("sharpness"), 256, 24, 22),
        new RitualAnimation.Input(
            ResourceLocation.withDefaultNamespace("fire_aspect"), 64, 30, 20));
  }

  @Test
  void nextStartsAtPriorRingFormationAndFinishWaitsForAll() {
    var plan = RitualAnimation.plan(inputs(), 120, true);
    var a = plan.stages().getFirst();
    var b = plan.stages().getLast();
    assertEquals(0, a.start());
    assertEquals(a.formed(), b.start());
    assertEquals(30, a.materialStart());
    assertEquals(1, plan.active(b.start() - 1).size());
    assertEquals(2, plan.active(b.start()).size());
    assertTrue(plan.duration() >= b.formed() + 52);
  }

  @Test
  void simultaneousStartsTogetherAndSingleEnchantHasNoExtraDelay() {
    var plan = RitualAnimation.plan(inputs(), 200, false);
    assertTrue(plan.stages().stream().allMatch(s -> s.start() == 0));
    assertEquals(200, plan.duration());
    assertEquals(
        RitualAnimation.plan(inputs().subList(0, 1), 120, true),
        RitualAnimation.plan(inputs().subList(0, 1), 120, false));
  }

  @Test
  void longRoutesStillFormAllRingsWithShortConfiguredDuration() {
    var plan = RitualAnimation.plan(inputs(), 20, true);
    assertTrue(plan.duration() > 120);
    assertTrue(plan.duration() - 28 > plan.stages().getLast().formed());
  }

  @Test
  void sharedCutoffLeavesEveryFlightTimeToArriveBeforeThePulse() {
    var longInputs =
        List.of(
            new RitualAnimation.Input(
                ResourceLocation.withDefaultNamespace("sharpness"), 256, 0, 220),
            new RitualAnimation.Input(
                ResourceLocation.withDefaultNamespace("unbreaking"), 80, 0, 40));
    for (boolean sequential : List.of(false, true)) {
      var plan = RitualAnimation.plan(longInputs, 20, sequential, 300, 315);
      assertTrue(plan.stages().stream().allMatch(s -> s.formed() <= plan.sourceStop()));
      assertTrue(plan.sourceStop() >= 22 + 300 + RitualFlight.ringFormationTicks(8));
      assertTrue(
          plan.duration() - 28
              >= Math.max(plan.sourceStop() + 220, plan.experienceStop() + 300) + 24);
      assertTrue(
          RitualAnimation.plan(longInputs, 20, sequential, 300, 550).experienceRadius()
              > plan.experienceRadius());
    }
  }

  @Test
  void experienceRingGrowthIsOneTenthAndItsSourceStopsAtItsOwnFormation() {
    assertEquals(.42 + .0045 * Math.sqrt(550), RitualAnimation.experienceRadius(550), 1e-6);
    assertEquals(
        .1,
        (RitualAnimation.experienceRadius(550) - .42) / (RitualAnimation.radius(550) - .42),
        1e-6);
    var plan = RitualAnimation.plan(inputs(), 20, true, 20, 550);
    assertEquals(22 + 20 + RitualFlight.ringFormationTicks(8), plan.experienceStop());
    assertTrue(plan.experienceStop() < plan.sourceStop());
  }

  @Test
  void tiltedRingsStayAboveTableThroughoutWobbleAndFinishingPulse() {
    for (int index = 0; index < 9; index++)
      for (float radius : new float[] {.42f, 1.2f, 2.4f})
        for (boolean failing : List.of(false, true)) {
          double height = RitualFlight.minimumCenterHeight(index, radius, failing ? .455 : .14);
          var center = new Vec3(0, height, 0);
          var f =
              new RitualParticleOptions.Flight(
                  center,
                  Vec3.ZERO,
                  20,
                  300,
                  true,
                  0,
                  -1,
                  index,
                  false,
                  radius,
                  Optional.empty(),
                  new RitualInstability(1, 0, 0, 1, failing ? 1000 : -1));
          for (double tick = 20; tick < 300; tick += .25)
            assertTrue(
                RitualFlight.position(Vec3.ZERO, f, tick).y >= 1.15 - 1e-6,
                "Ring clears table, including near-vertical orientation, wobble and pulse");
        }
  }

  @Test
  void radiusAndEmissionDensityGrowWithPowerButStayBounded() {
    float small = RitualAnimation.radius(16), large = RitualAnimation.radius(1024);
    assertTrue(large > small);
    assertEquals(2.4, RitualAnimation.radius(Double.MAX_VALUE), 1e-6);
    int low = RitualAnimation.weight(small), high = RitualAnimation.weight(large);
    int[] totals = new int[2];
    for (int age = 0; age < 400; age += 2) {
      var allocation = RitualAnimation.allocation(List.of(low, high), age);
      assertTrue(allocation.size() <= 16);
      allocation.forEach(i -> totals[i]++);
    }
    assertTrue(totals[1] > totals[0] * 2);
    assertTrue(
        RitualAnimation.allocation(List.of(high), 0).size()
            > RitualAnimation.allocation(List.of(low), 0).size());
  }

  @Test
  void manyEnchantsRetainFairEmissionShares() {
    var weights = Collections.nCopies(42, 20);
    var seen = new HashSet<Integer>();
    for (int age = 0; age < 120; age += 2) {
      var allocation = RitualAnimation.allocation(weights, age);
      assertEquals(16, allocation.size());
      seen.addAll(allocation);
    }
    assertEquals(42, seen.size());
  }

  @Test
  void actualOrbitUsesSynchronizedPowerRadius() {
    var a =
        new RitualParticleOptions.Flight(Vec3.ZERO, Vec3.ZERO, 20, 200, true, 0, -1, 0, false, .6f);
    var b =
        new RitualParticleOptions.Flight(
            Vec3.ZERO, Vec3.ZERO, 20, 200, true, 0, -1, 0, false, 1.8f);
    Vec3 offset = new Vec3(0, RitualFlight.ring(0).height(), 0);
    double small = RitualFlight.position(Vec3.ZERO, a, 40).subtract(offset).length();
    double large = RitualFlight.position(Vec3.ZERO, b, 40).subtract(offset).length();
    assertEquals(3, large / small, 1e-6);
    var codec = RitualParticleOptions.Flight.CODEC;
    assertEquals(
        b,
        codec
            .parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, b).getOrThrow())
            .getOrThrow());
  }

  @Test
  void pageMeshHasCenteredThicknessAndAnEdgeAroundEveryCutout() {
    var faces = PageMesh.generate(3, 3, (x, y) -> x != 1 || y != 1);
    for (var face : faces.subList(0, 2))
      for (var v : face.vertices()) {
        assertEquals(
            v.x(), v.u(), 1e-6); // same silhouette on both faces, including asymmetric cutouts
        assertEquals(1 - v.y(), v.v(), 1e-6);
      }
    assertEquals(18, faces.size()); // two printed faces, twelve outside edges, four hole edges
    var vertices = faces.stream().flatMap(f -> f.vertices().stream()).toList();
    assertEquals(
        1,
        vertices.stream().mapToDouble(PageMesh.Vertex::z).min().orElseThrow()
            + vertices.stream().mapToDouble(PageMesh.Vertex::z).max().orElseThrow(),
        1e-6);
    assertEquals(1.0 / 16, PageMesh.FRONT - PageMesh.BACK, 1e-6);
    assertTrue(faces.stream().anyMatch(f -> f.nx() == -1));
    assertTrue(faces.stream().anyMatch(f -> f.nx() == 1));
    assertTrue(faces.stream().anyMatch(f -> f.ny() == -1));
    assertTrue(faces.stream().anyMatch(f -> f.ny() == 1));
  }
}
