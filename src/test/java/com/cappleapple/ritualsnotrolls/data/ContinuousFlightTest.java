package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContinuousFlightTest {
  @Test
  void completeRoutePassesEveryPedestalWithContinuousVelocity() {
    for (var waypoints :
        List.of(
            List.of(
                new Vec3(8, 65, 0),
                new Vec3(5, 65, 0),
                new Vec3(1, 65, 0),
                new Vec3(5, 65, 0),
                new Vec3(4, 65, 3)),
            List.of(
                new Vec3(1, 64, 1), new Vec3(1, 69, 1), new Vec3(1, 64, 1), new Vec3(1.001, 64, 1)),
            List.of(
                new Vec3(4000000, -60, -3000000),
                new Vec3(4000003, -60, -3000000),
                new Vec3(4000003, -59, -3000000)))) {
      var c = RitualSpline.through(waypoints, false);
      for (int n = 0; n < waypoints.size(); n++)
        assertEquals(0, waypoints.get(n).distanceTo(at(c, c.arrival(n))), 1e-7);
      for (int i = 1; i < c.ticks().size() - 1; i++) {
        double t = c.ticks().get(i), e = .01;
        var left = at(c, t).subtract(at(c, t - e)).scale(1 / e);
        var right = at(c, t + e).subtract(at(c, t)).scale(1 / e);
        assertTrue(left.distanceTo(right) < .01, "Shared velocity at knot " + i);
      }
      for (double t = 0; t < c.duration(); t += .25)
        assertTrue(Double.isFinite(at(c, t).lengthSqr()));
    }
  }

  private Vec3 at(RitualSpline.Curve c, double t) {
    return RitualSpline.position(c, c.start(), c.end(), null, t);
  }

  @Test
  void fullFlightCodecKeepsRouteAndFailureClock() {
    var c =
        RitualSpline.through(
            List.of(new Vec3(2, 65, 0), new Vec3(4, 65, 2), new Vec3(0, 66, 0)), false);
    var f =
        new RitualParticleOptions.Flight(
            c.end(),
            Vec3.ZERO,
            c.duration(),
            200,
            true,
            .2f,
            -1,
            4,
            false,
            1.1f,
            Optional.of(c),
            new RitualInstability(.8f, 12, 40, 55, 95));
    var json = RitualParticleOptions.Flight.CODEC.encodeStart(JsonOps.INSTANCE, f).getOrThrow();
    assertEquals(f, RitualParticleOptions.Flight.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
  }

  @Test
  void weakChannelsStartCalmAndNearMissesHoldTogetherLonger() {
    var id = ResourceLocation.withDefaultNamespace("sharpness");
    var low =
        RitualAnimation.plan(
                List.of(new RitualAnimation.Input(id, 1, 0, 80, .01, .1, true)), 120, true)
            .stages()
            .getFirst();
    var close =
        RitualAnimation.plan(
                List.of(new RitualAnimation.Input(id, 9, 0, 80, .09, .9, true)), 120, true)
            .stages()
            .getFirst();
    assertEquals(0, low.instability(0).amplitude(50));
    assertTrue(low.instability(0).amplitude(low.failAt() - 1) > .39);
    assertTrue(close.failAt() > low.failAt() + 40);
    assertEquals(0, new RitualInstability(0, 0, 40, 48, -1).amplitude(100));
    assertTrue(
        new RitualInstability(.9f, 0, 40, 48, -1).amplitude(100)
            > new RitualInstability(.1f, 0, 40, 48, -1).amplitude(100));
  }

  @Test
  void collapseAddsScatterInEveryDirectionToExistingMomentumThenFalls() {
    var c =
        RitualSpline.through(
            List.of(new Vec3(6, 65, 2), new Vec3(3, 65, 4), new Vec3(0, 66, 0)), false);
    for (boolean orbit : List.of(false, true)) {
      for (int failure : List.of(20, c.duration() + 25)) {
        var f =
            new RitualParticleOptions.Flight(
                c.end(),
                Vec3.ZERO,
                c.duration(),
                failure + 40,
                orbit,
                .4f,
                -1,
                4,
                false,
                .8f,
                Optional.of(c),
                new RitualInstability(.8f, 0, 0, failure, failure));
        Set<Integer> octants = new HashSet<>();
        for (int i = 0; i < 128; i++) {
          double lead = i / 64.0, epsilon = .0001;
          var at = RitualFlight.position(c.start(), f, failure, lead);
          var previous = RitualFlight.position(c.start(), f, failure - epsilon, lead);
          var next = RitualFlight.position(c.start(), f, failure + epsilon, lead);
          assertTrue(
              at.distanceTo(previous) < .001 && at.distanceTo(next) < .001,
              "Failure starts at each particle's current location");
          var before = at.subtract(previous).scale(1 / epsilon);
          var after = next.subtract(at).scale(1 / epsilon);
          var impulse = after.subtract(before);
          assertTrue(
              impulse.length() >= .175 && impulse.length() <= .365,
              "An outward impulse is added to inherited trail momentum");
          octants.add((impulse.x > 0 ? 1 : 0) | (impulse.y > 0 ? 2 : 0) | (impulse.z > 0 ? 4 : 0));
          assertTrue(
              RitualFlight.position(c.start(), f, failure + 35, lead).y < at.y - 8,
              "Gravity eventually pulls even upward-scattered particles down");
        }
        assertEquals(
            8, octants.size(), "Scatter includes upward, downward and every horizontal direction");
      }
    }
  }

  @Test
  void individualNoiseStaysInOneNarrowTrailAndMovesSmoothly() {
    var c =
        RitualSpline.through(
            List.of(new Vec3(6, 65, 2), new Vec3(3, 65, 4), new Vec3(0, 66, 0)), false);
    var quiet =
        new RitualParticleOptions.Flight(
            c.end(),
            Vec3.ZERO,
            c.duration(),
            500,
            true,
            .4f,
            -1,
            4,
            false,
            .8f,
            Optional.of(c),
            RitualInstability.STABLE);
    var shaky =
        new RitualParticleOptions.Flight(
            c.end(),
            Vec3.ZERO,
            c.duration(),
            500,
            true,
            .4f,
            -1,
            4,
            false,
            .8f,
            Optional.of(c),
            new RitualInstability(1, 0, 10, 20, -1));
    for (double lead : new double[] {0, .375, 1.75}) {
      assertEquals(
          0,
          RitualFlight.position(c.start(), quiet, 5, lead)
              .distanceTo(RitualFlight.position(c.start(), shaky, 5, lead)),
          1e-9);
      for (double tick = 10; tick < 250; tick += .25) {
        var delta =
            RitualFlight.position(c.start(), shaky, tick, lead)
                .subtract(RitualFlight.position(c.start(), quiet, tick, lead));
        assertTrue(
            Math.abs(delta.x) <= .120001
                && Math.abs(delta.z) <= .120001
                && Math.abs(delta.y) <= .111001,
            "Noise stays within the shared trail corridor");
      }
      // Quintic noise crosses every noise-cell boundary without a velocity discontinuity.
      for (int cell = 12; cell < 25; cell++) {
        double tick = cell / .32 - lead, epsilon = .0001;
        var at = RitualFlight.position(c.start(), shaky, tick, lead);
        var before =
            at.subtract(RitualFlight.position(c.start(), shaky, tick - epsilon, lead))
                .scale(1 / epsilon);
        var after =
            RitualFlight.position(c.start(), shaky, tick + epsilon, lead)
                .subtract(at)
                .scale(1 / epsilon);
        assertTrue(before.distanceTo(after) < .002, "No sharp jitter at a noise-cell boundary");
      }
    }
    var a =
        RitualFlight.position(c.start(), shaky, 80, 0)
            .subtract(RitualFlight.position(c.start(), quiet, 80, 0));
    var b =
        RitualFlight.position(c.start(), shaky, 79, 1)
            .subtract(RitualFlight.position(c.start(), quiet, 79, 1));
    assertTrue(
        a.distanceTo(b) > .01, "Particles at the same route position have individual offsets");
  }

  @Test
  void ringEntranceMatchesWholeSplineTangent() {
    var c =
        RitualSpline.through(
            List.of(new Vec3(6, 65, 0), new Vec3(3, 65, 3), new Vec3(0, 66, 0)), false);
    for (int ring = 0; ring < 9; ring++) {
      var f =
          new RitualParticleOptions.Flight(
              c.end(),
              Vec3.ZERO,
              c.duration(),
              400,
              true,
              .7f,
              -1,
              ring,
              false,
              1.1f,
              Optional.of(c),
              RitualInstability.STABLE);
      double t = c.duration(), e = .001;
      var at = RitualFlight.position(c.start(), f, t);
      var a = at.subtract(RitualFlight.position(c.start(), f, t - e)).scale(1 / e);
      var b = RitualFlight.position(c.start(), f, t + e).subtract(at).scale(1 / e);
      assertTrue(a.distanceTo(b) < .001, "Ring tangent and speed continuous");
    }
  }
}
