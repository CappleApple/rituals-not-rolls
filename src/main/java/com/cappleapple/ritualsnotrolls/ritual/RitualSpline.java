package com.cappleapple.ritualsnotrolls.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** One time-parameterized Hermite spline through the whole ordered ritual route. */
public final class RitualSpline {
  public record Curve(List<Vec3> points, List<Integer> ticks, boolean playerSource) {
    public static final Codec<Curve> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Vec3.CODEC.listOf().fieldOf("points").forGetter(Curve::points),
                        Codec.INT.listOf().fieldOf("ticks").forGetter(Curve::ticks),
                        Codec.BOOL
                            .optionalFieldOf("player_source", false)
                            .forGetter(Curve::playerSource))
                    .apply(i, Curve::new));

    public Curve {
      points = List.copyOf(points);
      ticks = List.copyOf(ticks);
      if (points.size() < 2
          || points.size() > 2048
          || points.size() != ticks.size()
          || ticks.getFirst() != 0) throw new IllegalArgumentException("Invalid spline knots");
      for (int n = 1; n < ticks.size(); n++)
        if (ticks.get(n) <= ticks.get(n - 1) || ticks.get(n) > 12000)
          throw new IllegalArgumentException("Spline times must increase");
    }

    public int duration() {
      return ticks.getLast();
    }

    public Vec3 start() {
      return points.getFirst();
    }

    public Vec3 end() {
      return points.getLast();
    }

    public int arrival(int waypoint) {
      return ticks.get(Math.min(points.size() - 1, waypoint * 2));
    }
  }

  public static Curve through(List<Vec3> waypoints, boolean player) {
    List<Vec3> points = new ArrayList<>();
    List<Integer> ticks = new ArrayList<>();
    points.add(waypoints.getFirst());
    ticks.add(0);
    for (int i = 1; i < waypoints.size(); i++) {
      Vec3 a = waypoints.get(i - 1), b = waypoints.get(i);
      Vec3 bow = RitualFlight.bend(a, b).scale(.5);
      Vec3 middle = a.lerp(b, .5).add(bow);
      for (Vec3 p : List.of(middle, b)) {
        int span = Math.max(6, (int) Math.ceil(points.getLast().distanceTo(p) * 3.5));
        ticks.add(ticks.getLast() + span);
        points.add(p);
      }
    }
    return new Curve(points, ticks, player);
  }

  private static Vec3 point(Curve c, int i, Vec3 start, Vec3 end) {
    return i == 0 ? start : i == c.points().size() - 1 ? end : c.points().get(i);
  }

  private static Vec3 velocity(Curve c, int i, Vec3 start, Vec3 end, Vec3 terminal) {
    int last = c.points().size() - 1;
    if (i == last && terminal != null) return terminal;
    if (i == 0) return point(c, 1, start, end).subtract(start).scale(1.0 / c.ticks().get(1));
    if (i == last)
      return end.subtract(point(c, i - 1, start, end))
          .scale(1.0 / (c.ticks().get(i) - c.ticks().get(i - 1)));
    Vec3 p = point(c, i, start, end);
    Vec3 in = p.subtract(point(c, i - 1, start, end));
    Vec3 out = point(c, i + 1, start, end).subtract(p);
    double dt0 = c.ticks().get(i) - c.ticks().get(i - 1),
        dt1 = c.ticks().get(i + 1) - c.ticks().get(i);
    Vec3 direction = in.normalize().add(out.normalize());
    // A return visit must turn through a loop rather than stop and reverse at a cusp.
    if (direction.lengthSqr() < .04) {
      direction = in.cross(new Vec3(0, 1, 0));
      if (direction.lengthSqr() < .001) direction = new Vec3(1, 0, 0);
    }
    return direction.normalize().scale(Math.min(in.length() / dt0, out.length() / dt1) * .85);
  }

  public static Vec3 position(Curve c, Vec3 start, Vec3 end, Vec3 terminal, double tick) {
    if (tick <= 0) return start;
    if (tick >= c.duration()) return end;
    int i = 0;
    while (i + 1 < c.ticks().size() - 1 && tick >= c.ticks().get(i + 1)) i++;
    double dt = c.ticks().get(i + 1) - c.ticks().get(i);
    double t = (tick - c.ticks().get(i)) / dt, t2 = t * t, t3 = t2 * t;
    Vec3 a = point(c, i, start, end), b = point(c, i + 1, start, end);
    return a.scale(2 * t3 - 3 * t2 + 1)
        .add(velocity(c, i, start, end, terminal).scale((t3 - 2 * t2 + t) * dt))
        .add(b.scale(-2 * t3 + 3 * t2))
        .add(velocity(c, i + 1, start, end, terminal).scale((t3 - t2) * dt));
  }
}
