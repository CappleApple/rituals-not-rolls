package com.cappleapple.ritualsnotrolls.ritual;

import net.minecraft.world.phys.Vec3;

/** Shared geometry for layered orbital planes, the inward pulse, and a fading spherical burst. */
public final class RitualFlight {
  public record Ring(double radius, double height, Vec3 u, Vec3 v) {}

  public static Ring ring(int index) {
    int i = Math.floorMod(index, 9);
    double yaw = i * 2.399963229728653, tilt = new double[] {.12, .78, 1.45}[i / 3];
    double radius = new double[] {.48, .78, 1.08}[i % 3],
        height = new double[] {-.16, 0, .16}[i % 3];
    var u = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    var v =
        new Vec3(-Math.sin(yaw) * Math.cos(tilt), Math.sin(tilt), Math.cos(yaw) * Math.cos(tilt));
    return new Ring(radius, height, u, v);
  }

  /** Lowest point of any phase of the tilted ring, including wobble and a sprite margin. */
  public static double minimumCenterHeight(int index, float radius, double verticalShake) {
    var ring = ring(index);
    double belowCenter = radius * Math.hypot(ring.u().y, ring.v().y) - ring.height();
    return 1.15 + verticalShake + Math.max(0, belowCenter);
  }

  /** Compress during the last 28 ticks, then rebound slightly into the completion flash. */
  public static double pulseScale(double remaining) {
    if (remaining >= 28) return 1;
    if (remaining <= 8) {
      double t = Math.clamp(1 - remaining / 8, 0, 1);
      return .12 + .08 * t * t;
    }
    double t = (28 - remaining) / 20;
    return 1 - .88 * t * t * (3 - 2 * t);
  }

  public static Vec3 burstDirection(int index, int count) {
    double y = 1 - 2 * (index + .5) / count,
        r = Math.sqrt(Math.max(0, 1 - y * y)),
        a = index * 2.399963229728653;
    return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
  }

  public static float burstAlpha(double tick, int lifetime) {
    double t = Math.clamp((tick - 3) / Math.max(1, lifetime - 3), 0, 1);
    return (float) ((1 - t) * (1 - t));
  }

  /** A reversible, stable bow shared by all particles on the same hop. */
  public static Vec3 bend(Vec3 from, Vec3 to) {
    Vec3 delta = to.subtract(from);
    Vec3 side = delta.cross(new Vec3(0, 1, 0)).normalize();
    if (delta.x < 0 || (delta.x == 0 && delta.z < 0)) side = side.scale(-1);
    if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
    return side.scale(Math.clamp(delta.length() * .23, .6, 2.3)).add(0, .7, 0);
  }

  /** Enter the near side of the orbital plane at one fixed point per route. */
  public static float entryPhase(Vec3 from, Vec3 center, int index) {
    var ring = ring(index);
    var approach = from.subtract(center.add(0, ring.height(), 0));
    return (float) Math.atan2(approach.dot(ring.v()), approach.dot(ring.u()));
  }

  private static double angularSpeed(int index) {
    return index % 2 == 0 ? .16 : -.14;
  }

  public static int ringFormationTicks(int index) {
    return (int) Math.ceil(Math.PI * 2 / Math.abs(angularSpeed(index)));
  }

  private static Vec3 orbit(
      Vec3 center, double tick, double remaining, RitualParticleOptions.Flight f) {
    var ring = ring(f.ring());
    double angle = f.phase() + (tick - f.travel()) * angularSpeed(f.ring()),
        scale = f.instability().fails() ? 1 : pulseScale(remaining),
        radius = f.radius() > 0 ? f.radius() : ring.radius();
    return center
        .add(ring.u().scale(Math.cos(angle) * radius * scale))
        .add(ring.v().scale(Math.sin(angle) * radius * scale))
        .add(0, ring.height() * scale, 0);
  }

  public static Vec3 position(Vec3 from, RitualParticleOptions.Flight f, double tick) {
    return position(from, f, tick, 0);
  }

  /**
   * Fractional spacing moves particles along the same spline; the finish clock stays synchronized.
   */
  public static Vec3 position(Vec3 from, RitualParticleOptions.Flight f, double tick, double lead) {
    var instability = f.instability();
    if (instability.fails() && instability.age() + tick >= instability.failAt()) {
      double failureTick = instability.failAt() - instability.age();
      Vec3 at = movingPosition(from, f, failureTick, lead);
      Vec3 momentum = at.subtract(movingPosition(from, f, failureTick - .001, lead)).scale(1000);
      long seed = particleSeed(f, lead);
      double y = random(seed + 71) * 2 - 1, angle = random(seed + 97) * Math.PI * 2;
      double horizontal = Math.sqrt(1 - y * y), speed = .18 + .18 * random(seed + 113);
      Vec3 scatter =
          new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal).scale(speed);
      Vec3 velocity = momentum.add(scatter);
      double falling = tick - failureTick, drag = Math.pow(.96, falling);
      return at.add(velocity.scale((1 - drag) / -Math.log(.96)))
          .add(0, -.025 * falling * falling, 0);
    }
    return movingPosition(from, f, tick, lead);
  }

  private static Vec3 movingPosition(
      Vec3 from, RitualParticleOptions.Flight f, double tick, double lead) {
    Vec3 p = stablePosition(from, f, tick, lead);
    double amplitude = f.instability().amplitude(tick);
    double time = (f.instability().age() + tick) * .4;
    long seed = particleSeed(f, lead);
    double noiseTime = (tick + lead) * .32;
    // A small shared sway keeps the route readable. Smooth, individual noise frays its edges.
    return p.add(
        (Math.sin(p.z * 1.3 + time) * .25 + noise(seed, noiseTime) * .75) * amplitude,
        (Math.sin(p.x * 1.1 + time * 1.2) * .175 + noise(seed + 23, noiseTime) * .75) * amplitude,
        (Math.cos(p.y * .9 + time * .85) * .25 + noise(seed + 47, noiseTime) * .75) * amplitude);
  }

  private static long particleSeed(RitualParticleOptions.Flight f, double lead) {
    return Double.doubleToLongBits(lead)
        ^ ((long) f.instability().age() << 32)
        ^ Float.floatToIntBits(f.phase())
        ^ f.ring() * 0x9E3779B97F4A7C15L;
  }

  private static double random(long seed) {
    long x = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
    x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
    return ((x ^ (x >>> 31)) >>> 11) * 0x1.0p-53;
  }

  private static double noise(long seed, double time) {
    long cell = (long) Math.floor(time);
    double t = time - cell;
    double blend = t * t * t * (t * (t * 6 - 15) + 10);
    double a = random(seed + cell * 0x9E3779B97F4A7C15L);
    double b = random(seed + (cell + 1) * 0x9E3779B97F4A7C15L);
    return (a + (b - a) * blend) * 2 - 1;
  }

  private static Vec3 stablePosition(
      Vec3 from, RitualParticleOptions.Flight f, double tick, double lead) {
    if (f.burst()) {
      double radius = .08 + (2.2 + f.phase() * .12) * (1 - Math.exp(-Math.max(0, tick) / 8));
      return f.destination().add(f.bend().normalize().scale(radius));
    }
    double motionTick = tick + lead;
    if (f.orbit() && motionTick >= f.travel())
      return orbit(f.destination(), motionTick, f.remaining() - tick, f);
    double t = Math.clamp(motionTick / f.travel(), 0, 1);
    Vec3 end =
        f.orbit()
            ? orbit(f.destination(), f.travel(), f.remaining() - f.travel() + lead, f)
            : f.destination();
    if (f.route().isPresent()) {
      Vec3 tangent = null;
      if (f.orbit()) {
        var ring = ring(f.ring());
        double radius = f.radius() > 0 ? f.radius() : ring.radius();
        tangent =
            ring.u()
                .scale(-Math.sin(f.phase()))
                .add(ring.v().scale(Math.cos(f.phase())))
                .scale(angularSpeed(f.ring()) * radius);
      }
      return RitualSpline.position(f.route().get(), from, end, tangent, motionTick);
    }
    Vec3 control1 = from.lerp(end, 1.0 / 3).add(f.bend().scale(4.0 / 3));
    Vec3 control2 = from.lerp(end, 2.0 / 3).add(f.bend().scale(4.0 / 3));
    if (f.orbit()) {
      var ring = ring(f.ring());
      Vec3 tangent =
          ring.u()
              .scale(-Math.sin(f.phase()))
              .add(ring.v().scale(Math.cos(f.phase())))
              .scale(Math.signum(angularSpeed(f.ring())));
      control2 = end.subtract(tangent.scale(Math.clamp(from.distanceTo(end) / 3, .15, 1.5)));
    }
    double u = 1 - t;
    return from.scale(u * u * u)
        .add(control1.scale(3 * u * u * t))
        .add(control2.scale(3 * u * t * t))
        .add(end.scale(t * t * t));
  }
}
