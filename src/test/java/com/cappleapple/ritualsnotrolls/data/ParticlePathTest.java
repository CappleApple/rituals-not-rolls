package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParticlePathTest {
  private static final ResourceLocation ENCHANT =
      ResourceLocation.withDefaultNamespace("sharpness");
  private static final ResourceLocation XP =
      ResourceLocation.fromNamespaceAndPath("ritualsnotrolls", "experience");
  private static final RitualParticleOptions EFFECT =
      new RitualParticleOptions(ResourceLocation.withDefaultNamespace("enchant"), 0xffffff);

  private RitualParticleOptions.Flight flight(
      Vec3 from, Vec3 to, boolean orbit, int ring, int remaining) {
    return new RitualParticleOptions.Flight(
        to,
        RitualFlight.bend(from, to),
        24,
        remaining,
        orbit,
        RitualFlight.entryPhase(from, to, ring),
        -1,
        ring,
        false,
        1.2f);
  }

  @Test
  void allEmissionsShareOneCurvePerHopAcrossBatchesAndRingArrivals() {
    var book = new Vec3(8.5, 65.5, 2.5);
    var pedestal = new Vec3(4.5, 65.1, 4.5);
    var target = new Vec3(.5, 65.7, .5);
    var player = new Vec3(2.5, 66, -3.5);
    var catalyst = new Vec3(2.5, 65.1, -1.5);
    var paths =
        List.of(
            new RitualEffects.Path(book, pedestal, true, ENCHANT, EFFECT, false, false),
            new RitualEffects.Path(pedestal, target, false, ENCHANT, EFFECT, false, true),
            new RitualEffects.Path(target, pedestal, false, ENCHANT, EFFECT, false, false),
            new RitualEffects.Path(Vec3.ZERO, catalyst, false, XP, EFFECT, true, false),
            new RitualEffects.Path(catalyst, target, false, XP, EFFECT, false, true));
    var schedule =
        RitualAnimation.plan(
            List.of(new RitualAnimation.Input(ENCHANT, 4096, 0, 24)), 300, false, 48, 550);
    record Hop(Vec3 from, Vec3 to, boolean orbit) {}
    Map<Hop, List<Vec3>> samples = new HashMap<>();
    Map<Hop, Integer> counts = new HashMap<>();
    for (int age = 0; age < 80; age += 2) {
      for (var emission : RitualEffects.emissions(paths, schedule, player, 7, age)) {
        var f = emission.particle().flight().orElseThrow();
        var hop = new Hop(emission.from(), f.destination(), f.orbit());
        var points = new ArrayList<Vec3>();
        for (double t : new double[] {0, .2, .5, .8, 1})
          points.add(RitualFlight.position(emission.from(), f, f.travel() * t));
        var first = samples.putIfAbsent(hop, points);
        if (first != null)
          for (int i = 0; i < points.size(); i++)
            assertEquals(
                0,
                points.get(i).distanceTo(first.get(i)),
                1e-9,
                "No alternate lanes or ring-entry fan");
        counts.merge(hop, 1, Integer::sum);
      }
    }
    assertEquals(5, samples.size());
    assertTrue(counts.values().stream().allMatch(n -> n > 3));
  }

  @Test
  void splineIsCurvedReversibleAndFiniteForVerticalAndShortHops() {
    Vec3 from = new Vec3(1, 64, 1);
    for (var to : List.of(new Vec3(8, 66, 3), new Vec3(1, 68, 1), new Vec3(1.01, 64, 1), from)) {
      var forward = flight(from, to, false, 0, 300);
      var reverse = flight(to, from, false, 0, 300);
      assertEquals(0, RitualFlight.position(from, forward, 0).distanceTo(from), 1e-9);
      assertEquals(0, RitualFlight.position(from, forward, 24).distanceTo(to), 1e-9);
      assertTrue(RitualFlight.position(from, forward, 12).distanceTo(from.lerp(to, .5)) > .5);
      for (int tick = 0; tick <= 24; tick++) {
        var point = RitualFlight.position(from, forward, tick);
        assertTrue(
            Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z));
        assertEquals(0, point.distanceTo(RitualFlight.position(to, reverse, 24 - tick)), 1e-9);
      }
    }
  }

  @Test
  void everyRingHasAContinuousTangentEntranceAndFinishesFillingBeforeNextStage() {
    Vec3 from = new Vec3(6, 65, 3), center = new Vec3(.5, 65.7, .5);
    for (int ring = 0; ring < 9; ring++) {
      var f = flight(from, center, true, ring, 300);
      var at = RitualFlight.position(from, f, 24);
      var before = at.subtract(RitualFlight.position(from, f, 24 - .0001));
      var after = RitualFlight.position(from, f, 24 + .0001).subtract(at);
      assertTrue(before.length() < .001 && after.length() < .001);
      assertTrue(
          before.dot(after) / (before.length() * after.length()) > .999,
          "Smooth tangent for ring " + ring);
    }
    var inputs = new ArrayList<RitualAnimation.Input>();
    for (int i = 0; i < 9; i++)
      inputs.add(
          new RitualAnimation.Input(
              ResourceLocation.withDefaultNamespace("test_" + i), 100, 20, 24));
    var schedule = RitualAnimation.plan(inputs, 120, true);
    for (int i = 0; i < schedule.stages().size(); i++) {
      var stage = schedule.stages().get(i);
      int filling = stage.formed() - stage.materialStart() - 24;
      assertTrue(filling >= 39 && filling <= 45);
      if (i > 0) assertEquals(schedule.stages().get(i - 1).formed(), stage.start());
    }
  }

  @Test
  void spacingAlongSplineKeepsOneLaneAndDoesNotDesynchronizeFinishingPulse() {
    Vec3 from = new Vec3(6, 65, 3), center = new Vec3(.5, 65.7, .5);
    var f = flight(from, center, true, 7, 300);
    for (double lead : new double[] {0, .375, 1.75}) {
      assertEquals(
          0,
          RitualFlight.position(from, f, 11 - lead, lead)
              .distanceTo(RitualFlight.position(from, f, 11)),
          1e-9);
      assertEquals(
          0,
          RitualFlight.position(from, f, 40 - lead, lead)
              .distanceTo(RitualFlight.position(from, f, 40)),
          1e-9);
    }
    var finishing = flight(from, center, true, 7, 50);
    var ringCenter = center.add(0, RitualFlight.ring(7).height() * RitualFlight.pulseScale(10), 0);
    double radius = RitualFlight.position(from, finishing, 40, 0).distanceTo(ringCenter);
    assertEquals(
        radius, RitualFlight.position(from, finishing, 40, 1.75).distanceTo(ringCenter), 1e-9);
    assertTrue(radius < .3);
  }

  @Test
  void reverseStreamDrainsDownstreamAfterReleaseWithoutRushingExistingFlights() {
    Vec3 item = new Vec3(0, 65, 0),
        near = new Vec3(2, 65, 0),
        far = new Vec3(4, 65, 1),
        book = new Vec3(6, 65, 2);
    var paths =
        List.of(
            new RitualEffects.Path(item, near, false, ENCHANT, EFFECT, false, false, 0, 1, true),
            new RitualEffects.Path(near, far, false, ENCHANT, EFFECT, false, false, 20, 1, true),
            new RitualEffects.Path(far, book, true, ENCHANT, EFFECT, false, false, 40, 1, true),
            new RitualEffects.Path(book, item, true, ENCHANT, EFFECT, false, true, 0, .5f));
    var schedule =
        new RitualAnimation.Schedule(
            List.of(new RitualAnimation.Stage(ENCHANT, 0, 0, 0, 60, 1, 60)), 100);
    var late = RitualEffects.emissions(paths, schedule, Vec3.ZERO, -1, 98);
    var outbound = late.stream().filter(e -> !e.particle().flight().orElseThrow().orbit()).toList();
    assertFalse(outbound.isEmpty());
    for (var e : outbound) {
      var flight = e.particle().flight().orElseThrow();
      assertTrue(
          flight.travel() >= 16 && flight.remaining() >= flight.travel(),
          "Last particles keep their full travel time");
    }
    assertTrue(RitualEffects.emissions(paths, schedule, Vec3.ZERO, -1, 100).isEmpty());
    var tail = RitualEffects.tailEmissions(paths, schedule, 100);
    assertFalse(tail.isEmpty());
    assertTrue(
        tail.stream()
            .allMatch(e -> !e.from().equals(item) && !e.particle().flight().orElseThrow().orbit()));
    assertEquals(
        Set.of(near, far), new HashSet<>(tail.stream().map(RitualEffects.Emission::from).toList()));
    var lastHop = RitualEffects.tailEmissions(paths, schedule, 120);
    assertFalse(lastHop.isEmpty());
    assertTrue(lastHop.stream().allMatch(e -> e.from().equals(far)));
    assertEquals(40, RitualEffects.tailDuration(paths));
    assertTrue(RitualEffects.tailEmissions(paths, schedule, 140).isEmpty());
  }

  @Test
  void allIncomingSourcesStopTogetherAndLastWavesReachRingsBeforePulse() {
    var second = net.minecraft.resources.ResourceLocation.withDefaultNamespace("unbreaking");
    var experience =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            "ritualsnotrolls", "experience");
    var ids = List.of(ENCHANT, second, experience);
    var paths = new java.util.ArrayList<RitualEffects.Path>();
    var inputs = new java.util.ArrayList<RitualAnimation.Input>();
    int xpTravel = 0;
    for (int i = 0; i < 3; i++) {
      var points =
          List.of(
              new Vec3(12 + i, 65, 0),
              new Vec3(9, 65, 10 + i * 3),
              new Vec3(-8, 65, 9),
              new Vec3(0, 66, 0));
      var curve = RitualSpline.through(points, i == 2);
      paths.add(
          new RitualEffects.Path(
              points.get(2),
              curve.end(),
              false,
              ids.get(i),
              new RitualParticleOptions(EFFECT.effect(), 100 + i),
              false,
              true,
              curve.arrival(2),
              1,
              false,
              java.util.Optional.of(curve)));
      if (i < 2) inputs.add(new RitualAnimation.Input(ids.get(i), 200, 0, curve.duration()));
      else xpTravel = curve.duration();
    }
    for (boolean sequential : List.of(false, true)) {
      var schedule = RitualAnimation.plan(inputs, 20, sequential, xpTravel, 315);
      var seen = new java.util.HashSet<Integer>();
      int latestArrival = 0;
      for (int born = 0; born < schedule.duration(); born += 2) {
        // Moving the player changes the curve geometry but must not extend its arrival clock.
        var emissions = RitualEffects.emissions(paths, schedule, new Vec3(55, 70, -30), 7, born);
        if (born >= schedule.sourceStop())
          assertTrue(emissions.isEmpty(), "All sources share the cutoff");
        for (var emission : emissions) {
          var f = emission.particle().flight().orElseThrow();
          seen.add(emission.particle().rgb());
          latestArrival = Math.max(latestArrival, born + f.travel());
          assertEquals(schedule.duration(), born + f.remaining());
          assertTrue(
              f.travel() + 28 < f.remaining(), "No last-wave particle is truncated by the pulse");
          assertTrue(
              RitualFlight.position(emission.from(), f, f.travel()).distanceTo(f.destination())
                  < 3);
          if (emission.particle().rgb() == 102) {
            assertEquals(xpTravel, f.travel());
            assertEquals(RitualAnimation.experienceRadius(315), f.radius());
            assertTrue(
                born < schedule.experienceStop(), "XP source stops as soon as its own ring forms");
          }
        }
      }
      assertEquals(java.util.Set.of(100, 101, 102), seen, "Both enchantments and XP emitted");
      assertTrue(
          latestArrival <= schedule.duration() - 52, "Last particles gather before the pulse");
      assertTrue(RitualEffects.tailEmissions(paths, schedule, schedule.duration()).isEmpty());
    }
  }
}
