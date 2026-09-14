package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalGeometry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Each route emits controlled curved flights; arrivals at the target orbit until the final inward
 * pulse. Successful completion emits a separate outward burst.
 */
public final class RitualEffects {
  public record Path(
      Vec3 from,
      Vec3 to,
      boolean knowledge,
      ResourceLocation enchantment,
      RitualParticleOptions particle,
      boolean fromPlayer,
      boolean orbit,
      int delay,
      float fraction,
      boolean draining,
      Optional<RitualSpline.Curve> curve) {
    public Path(
        Vec3 from,
        Vec3 to,
        boolean knowledge,
        ResourceLocation enchantment,
        RitualParticleOptions particle,
        boolean fromPlayer,
        boolean orbit,
        int delay,
        float fraction,
        boolean draining) {
      this(
          from,
          to,
          knowledge,
          enchantment,
          particle,
          fromPlayer,
          orbit,
          delay,
          fraction,
          draining,
          Optional.empty());
    }

    public boolean terminal() {
      return curve.isEmpty()
          || delay == curve.get().arrival((curve.get().points().size() - 1) / 2 - 1);
    }

    public int span() {
      if (curve.isEmpty()) return travel(from, to);
      var c = curve.get();
      for (int i = 0; i < c.ticks().size() - 1; i += 2)
        if (c.ticks().get(i) == delay) return c.ticks().get(i + 2) - delay;
      return travel(from, to);
    }

    public Path(
        Vec3 from,
        Vec3 to,
        boolean knowledge,
        ResourceLocation enchantment,
        RitualParticleOptions particle,
        boolean fromPlayer,
        boolean orbit,
        int delay,
        float fraction) {
      this(from, to, knowledge, enchantment, particle, fromPlayer, orbit, delay, fraction, false);
    }

    public Path(
        Vec3 from,
        Vec3 to,
        boolean knowledge,
        ResourceLocation enchantment,
        RitualParticleOptions particle,
        boolean fromPlayer,
        boolean orbit) {
      this(from, to, knowledge, enchantment, particle, fromPlayer, orbit, 0, 1);
    }
  }

  /** One common orbit center keeps the item centered in every tilted ring above the table. */
  public static Vec3 targetCenter(BlockPos table, RitualMath.Plan plan) {
    double height = 1.7;
    int index = 0;
    for (var line :
        plan.evaluation().lines().stream()
            .sorted(Comparator.comparing(l -> l.enchantment().toString()))
            .toList()) {
      int ring = (index++ * 4) % 9;
      // Conservative clearance covers both individual noise and the smaller shared sway.
      double shake = line.level() > line.attainable() ? .455 : .14;
      height =
          Math.max(
              height,
              RitualFlight.minimumCenterHeight(
                  ring, RitualAnimation.radius(Math.abs(line.power())), shake));
    }
    if (!plan.selected().isEmpty() && plan.experience().points() > 0)
      height =
          Math.max(
              height,
              RitualFlight.minimumCenterHeight(
                  8, RitualAnimation.experienceRadius(plan.experience().points()), 0));
    return Vec3.atLowerCornerOf(table).add(.5, height, .5);
  }

  public static List<Path> paths(
      ServerLevel level, RitualNetwork.Snapshot network, RitualMath.Plan plan, BlockPos table) {
    return paths(level, network, plan, table, null);
  }

  public static List<Path> paths(
      ServerLevel level,
      RitualNetwork.Snapshot network,
      RitualMath.Plan plan,
      BlockPos table,
      Vec3 playerOrigin) {
    List<Path> result = new ArrayList<>();
    Vec3 target = targetCenter(table, plan);
    for (var chain : plan.chains()) {
      var id = chain.enchantment();
      var def = Definitions.SERVER.get(id);
      if (def == null) continue;
      var effect =
          new RitualParticleOptions(
              def.particle().orElse(ResourceLocation.withDefaultNamespace("enchant")),
              def.particleRgb());
      float fraction = plan.remainingFractions().getOrDefault(id, 1.0).floatValue();
      var positive =
          chain.outward().stream()
              .filter(
                  p ->
                      !p.subtraction() && plan.users().getOrDefault(p.pos(), Set.of()).contains(id))
              .toList();
      var negative =
          chain.outward().stream()
              .filter(
                  p -> p.subtraction() && plan.users().getOrDefault(p.pos(), Set.of()).contains(id))
              .toList();
      Vec3 source = Vec3.atCenterOf(chain.source());
      if (chain.chained()) {
        List<Vec3> forward = new ArrayList<>();
        forward.add(source);
        for (var p : positive)
          forward.add(PedestalGeometry.displayPosition(p.pos(), level.getBlockState(p.pos())));
        for (var p : chain.returning())
          if (positive.contains(p))
            forward.add(PedestalGeometry.displayPosition(p.pos(), level.getBlockState(p.pos())));
        if (!positive.isEmpty()) {
          forward.add(target);
          appendChain(result, forward, id, effect, true, fraction);
        }
        List<Vec3> reverse = new ArrayList<>();
        reverse.add(source);
        for (var p : negative)
          reverse.add(PedestalGeometry.displayPosition(p.pos(), level.getBlockState(p.pos())));
        for (var p : chain.returning())
          if (negative.contains(p))
            reverse.add(PedestalGeometry.displayPosition(p.pos(), level.getBlockState(p.pos())));
        if (!negative.isEmpty()) {
          reverse.add(target);
          Collections.reverse(reverse);
          appendChain(result, reverse, id, effect, false, fraction);
        }
      } else {
        for (var p : positive) {
          var material = PedestalGeometry.displayPosition(p.pos(), level.getBlockState(p.pos()));
          appendChain(result, List.of(source, material, target), id, effect, true, fraction);
        }
        for (var p : negative) {
          var material = PedestalGeometry.displayPosition(p.pos(), level.getBlockState(p.pos()));
          appendChain(result, List.of(target, material, source), id, effect, false, fraction);
        }
      }
      if (positive.isEmpty() && !negative.isEmpty() && fraction > 0)
        result.add(new Path(source, target, true, id, effect, false, true, 0, fraction));
    }
    // XP catalysts remain a player-to-catalyst-to-table route alongside the enchantment chains.
    if (!plan.selected().isEmpty() && plan.experience().points() > 0)
      result.addAll(paths(level, network, List.of(), Set.of(), table, playerOrigin, target));
    return List.copyOf(result);
  }

  private static void appendChain(
      List<Path> result,
      List<Vec3> points,
      ResourceLocation id,
      RitualParticleOptions effect,
      boolean forward,
      float fraction) {
    appendChain(result, points, id, effect, forward, fraction, false);
  }

  private static void appendChain(
      List<Path> result,
      List<Vec3> points,
      ResourceLocation id,
      RitualParticleOptions effect,
      boolean forward,
      float fraction,
      boolean playerSource) {
    var curve = RitualSpline.through(points, playerSource);
    int delay = 0;
    for (int i = 1; i < points.size(); i++) {
      var from = points.get(i - 1);
      var to = points.get(i);
      result.add(
          new Path(
              from,
              to,
              forward ? i == 1 : i == points.size() - 1,
              id,
              effect,
              playerSource && i == 1,
              forward && i == points.size() - 1 && fraction > 0,
              delay,
              forward && i == points.size() - 1 ? fraction : 1,
              !forward,
              Optional.of(curve)));
      delay = curve.arrival(i);
    }
  }

  public static List<Path> paths(
      ServerLevel level,
      RitualNetwork.Snapshot network,
      List<RitualNetwork.Pedestal> used,
      Set<ResourceLocation> enchantments,
      BlockPos table) {
    return paths(
        level,
        network,
        used,
        enchantments,
        table,
        null,
        Vec3.atLowerCornerOf(table).add(.5, 1.7, .5));
  }

  private static List<Path> paths(
      ServerLevel level,
      RitualNetwork.Snapshot network,
      List<RitualNetwork.Pedestal> used,
      Set<ResourceLocation> enchantments,
      BlockPos table,
      Vec3 playerOrigin,
      Vec3 target) {
    Set<Path> paths = new LinkedHashSet<>();
    for (var id : new TreeSet<>(enchantments)) {
      var def = Definitions.SERVER.get(id);
      if (def == null) continue;
      var effect =
          new RitualParticleOptions(
              def.particle().orElse(ResourceLocation.withDefaultNamespace("enchant")),
              def.particleRgb());
      for (var pedestal : used) {
        if (RitualMath.base(def, network.knowledge().getOrDefault(id, Set.of()), pedestal.stack())
            <= 0) continue;
        var material =
            PedestalGeometry.displayPosition(pedestal.pos(), level.getBlockState(pedestal.pos()));
        paths.add(new Path(material, target, false, id, effect, false, true));
        for (var source : network.sources())
          if (id.equals(source.enchantment())
              && RitualMath.base(def, source.entries(), pedestal.stack()) > 0)
            paths.add(
                new Path(Vec3.atCenterOf(source.pos()), material, true, id, effect, false, false));
      }
    }
    var xpEffect =
        new RitualParticleOptions(ResourceLocation.withDefaultNamespace("end_rod"), 0x98FF50);
    for (var pedestal : network.pedestals())
      if (pedestal.stack().is(RitualsNotRolls.XP_CATALYST)) {
        var catalyst =
            PedestalGeometry.displayPosition(pedestal.pos(), level.getBlockState(pedestal.pos()));
        List<Path> xpPaths = new ArrayList<>();
        appendChain(
            xpPaths,
            List.of(playerOrigin == null ? catalyst : playerOrigin, catalyst, target),
            RitualsNotRolls.id("experience"),
            xpEffect,
            true,
            1,
            true);
        paths.addAll(xpPaths);
      }
    return List.copyOf(paths);
  }

  public static RitualAnimation.Schedule schedule(
      List<Path> paths, RitualMath.Evaluation evaluation, int duration, boolean sequential) {
    return schedule(paths, evaluation, duration, sequential, Map.of(), Map.of(), Set.of());
  }

  public static RitualAnimation.Schedule schedule(
      List<Path> paths,
      RitualMath.Evaluation evaluation,
      int duration,
      boolean sequential,
      Map<ResourceLocation, Double> nativeCosts,
      Map<ResourceLocation, Double> minima,
      Set<ResourceLocation> failing) {
    return schedule(paths, evaluation, duration, sequential, nativeCosts, minima, failing, 0);
  }

  public static RitualAnimation.Schedule schedule(
      List<Path> paths,
      RitualMath.Evaluation evaluation,
      int duration,
      boolean sequential,
      Map<ResourceLocation, Double> nativeCosts,
      Map<ResourceLocation, Double> minima,
      Set<ResourceLocation> failing,
      int experiencePoints) {
    List<RitualAnimation.Input> inputs = new ArrayList<>();
    for (var line : evaluation.lines()) {
      var own = paths.stream().filter(p -> p.enchantment().equals(line.enchantment())).toList();
      int knowledge = 0;
      int material = own.stream().mapToInt(p -> p.delay() + p.span()).max().orElse(0);
      inputs.add(
          new RitualAnimation.Input(
              line.enchantment(),
              Math.abs(line.power()),
              knowledge,
              material,
              Math.abs(line.power())
                  / nativeCosts.getOrDefault(
                      line.enchantment(), Math.max(1, Math.abs(line.power()))),
              Math.abs(line.power())
                  / minima.getOrDefault(line.enchantment(), Math.max(1, Math.abs(line.power()))),
              failing.contains(line.enchantment())));
    }
    int experienceTravel =
        paths.stream()
            .filter(p -> p.enchantment().equals(RitualsNotRolls.id("experience")))
            .mapToInt(p -> p.delay() + p.span())
            .max()
            .orElse(0);
    return RitualAnimation.plan(inputs, duration, sequential, experienceTravel, experiencePoints);
  }

  private static int travel(Vec3 from, Vec3 to) {
    return Math.clamp((int) (to.distanceTo(from) * 2) + 12, 16, 34);
  }

  public record Emission(Vec3 from, RitualParticleOptions particle) {}

  private record Channel(
      List<Path> paths, int ring, float radius, int weight, RitualInstability instability) {}

  /** Pure emission selection also lets tests inspect exactly what the server sends. */
  public static List<Emission> emissions(
      List<Path> paths,
      RitualAnimation.Schedule schedule,
      Vec3 playerOrigin,
      int playerId,
      int age) {
    return emissions(paths, schedule, playerOrigin, playerId, age, false);
  }

  /** Stop the source on completion, then let the last wave reach each downstream hop. */
  public static List<Emission> tailEmissions(
      List<Path> paths, RitualAnimation.Schedule schedule, int age) {
    return emissions(paths, schedule, Vec3.ZERO, -1, age, true);
  }

  public static int tailDuration(List<Path> paths) {
    return paths.stream()
        .filter(p -> p.draining() && p.curve().isEmpty())
        .mapToInt(Path::delay)
        .max()
        .orElse(0);
  }

  private static List<Emission> emissions(
      List<Path> paths,
      RitualAnimation.Schedule schedule,
      Vec3 playerOrigin,
      int playerId,
      int age,
      boolean finishing) {
    if (paths.isEmpty() || (!finishing && age >= schedule.duration())) return List.of();
    List<Channel> channels = new ArrayList<>();
    for (var stage : schedule.active(age)) {
      var own =
          paths.stream()
              .filter(
                  p ->
                      p.enchantment().equals(stage.enchantment())
                          && p.terminal()
                          && (stage.failAt() < 0 || age < stage.failAt())
                          && (p.draining()
                              || age
                                  < schedule.sourceStop() + (p.curve().isPresent() ? 0 : p.delay()))
                          && age >= stage.start() + (p.curve().isPresent() ? 0 : p.delay())
                          && (!finishing
                              || (p.draining() && age < schedule.duration() + p.delay())))
              .toList();
      if (!own.isEmpty())
        channels.add(
            new Channel(own, stage.ring(), stage.radius(), stage.weight(), stage.instability(age)));
    }
    var xp =
        paths.stream()
            .filter(
                p ->
                    p.enchantment().equals(RitualsNotRolls.id("experience"))
                        && p.terminal()
                        && !finishing
                        && age < schedule.experienceStop() + (p.curve().isPresent() ? 0 : p.delay())
                        && (p.fromPlayer() || age >= 22))
            .toList();
    if (!xp.isEmpty())
      channels.add(
          new Channel(
              xp,
              8,
              schedule.experienceRadius(),
              RitualAnimation.weight(schedule.experienceRadius()),
              RitualInstability.STABLE));
    var allocation =
        RitualAnimation.allocation(channels.stream().map(Channel::weight).toList(), age);
    List<Emission> result = new ArrayList<>();
    int i = 0;
    for (int choice : allocation) {
      var channel = channels.get(choice);
      var path = channel.paths().get(Math.floorMod(age / 2 + i * 7, channel.paths().size()));
      if (path.orbit()
          && path.fraction() < 1
          && ((age * .61803398875 + i * .38196601125) % 1) >= path.fraction()) {
        i++;
        continue;
      }
      boolean fromPlayer =
          path.curve().map(RitualSpline.Curve::playerSource).orElse(path.fromPlayer());
      Vec3 from =
          fromPlayer
              ? playerOrigin
              : path.curve().map(RitualSpline.Curve::start).orElse(path.from());
      Optional<RitualSpline.Curve> route = path.curve();
      if (fromPlayer && route.isPresent()) {
        var waypoints = new ArrayList<Vec3>();
        for (int knot = 0; knot < route.get().points().size(); knot += 2)
          waypoints.add(route.get().points().get(knot));
        waypoints.set(0, playerOrigin);
        var moving = RitualSpline.through(waypoints, true);
        // A moving source changes geometry, not the captured arrival deadline.
        route = Optional.of(new RitualSpline.Curve(moving.points(), route.get().ticks(), true));
      }
      Vec3 approach = route.map(c -> c.points().get(c.points().size() - 3)).orElse(from);
      float phase = RitualFlight.entryPhase(approach, path.to(), channel.ring());
      Vec3 bend = RitualFlight.bend(from, path.to());
      int remaining = schedule.duration() - age;
      int travel = route.map(RitualSpline.Curve::duration).orElseGet(() -> travel(from, path.to()));
      if (channel.instability().fails()) remaining = channel.instability().failAt() - age + 40;
      if (path.draining()) remaining = Math.max(remaining, travel);
      var flight =
          new RitualParticleOptions.Flight(
              path.to(),
              bend,
              route.isPresent() ? travel : Math.min(remaining, travel),
              remaining,
              path.orbit(),
              phase,
              fromPlayer ? playerId : -1,
              channel.ring(),
              false,
              channel.radius(),
              route,
              channel.instability());
      result.add(new Emission(from, path.particle().flying(flight)));
      i++;
    }
    return List.copyOf(result);
  }

  public static void emit(
      ServerLevel level,
      List<Path> paths,
      RitualAnimation.Schedule schedule,
      ServerPlayer owner,
      int age) {
    emit(level, null, paths, schedule, owner, age);
  }

  public static void emit(
      ServerLevel level,
      BlockPos table,
      List<Path> paths,
      RitualAnimation.Schedule schedule,
      ServerPlayer owner,
      int age) {
    Vec3 playerOrigin = owner.position().add(0, owner.getBbHeight() * .6, 0);
    if (table != null) playerOrigin = RitualSpace.toLocal(level, table, playerOrigin);
    for (var emission : emissions(paths, schedule, playerOrigin, owner.getId(), age))
      send(level, table, emission);
  }

  public static void emitTail(
      ServerLevel level, List<Path> paths, RitualAnimation.Schedule schedule, int age) {
    emitTail(level, null, paths, schedule, age);
  }

  public static void emitTail(
      ServerLevel level,
      BlockPos table,
      List<Path> paths,
      RitualAnimation.Schedule schedule,
      int age) {
    for (var emission : tailEmissions(paths, schedule, age)) send(level, table, emission);
  }

  private static void send(ServerLevel level, BlockPos table, Emission emission) {
    Vec3 from = emission.from();
    var particle = emission.particle();
    var spaceId = table == null ? null : RitualSpace.frameId(level, table);
    if (spaceId != null) {
      from = RitualSpace.toWorld(level, table, from);
      particle = particle.inSpace(table, emission.from(), spaceId);
    }
    // Packet coordinates must be world positions for particle recipients and Sable's native hooks.
    level.sendParticles(particle, from.x, from.y, from.z, 1, 0, 0, 0, 0);
  }

  /** A success burst celebrates additions/upgrades, never a removal-only ritual. */
  public static List<RitualParticleOptions> completionParticles(
      Vec3 center, ItemStack before, Map<ResourceLocation, Integer> selected, int xp) {
    Map<ResourceLocation, Integer> existing = new HashMap<>();
    for (var entry : RitualMath.enchantments(before).entrySet())
      existing.put(entry.getKey().unwrapKey().orElseThrow().location(), entry.getIntValue());
    if (selected.entrySet().stream()
        .noneMatch(e -> e.getValue() > existing.getOrDefault(e.getKey(), 0))) return List.of();
    return completionParticles(center, selected.keySet(), xp);
  }

  /**
   * A fixed spherical distribution gives every direction coverage without particle-count scaling.
   */
  public static List<RitualParticleOptions> completionParticles(
      Vec3 center, Set<ResourceLocation> enchantments, int experienceCatalysts) {
    List<RitualParticleOptions> effects = new ArrayList<>();
    for (var id : new TreeSet<>(enchantments)) {
      var def = Definitions.SERVER.get(id);
      if (def == null) continue;
      effects.add(
          new RitualParticleOptions(
              def.particle().orElse(ResourceLocation.withDefaultNamespace("enchant")),
              def.particleRgb()));
    }
    if (experienceCatalysts > 0)
      effects.add(
          new RitualParticleOptions(ResourceLocation.withDefaultNamespace("end_rod"), 0x98FF50));
    if (effects.isEmpty()) return List.of();
    List<RitualParticleOptions> particles = new ArrayList<>();
    for (int i = 0; i < 96; i++) {
      var flight =
          new RitualParticleOptions.Flight(
              center,
              RitualFlight.burstDirection(i, 96),
              1,
              32 + i % 13,
              false,
              (float) ((i * .61803398875 % 1) * Math.PI * 2),
              -1,
              0,
              true);
      particles.add(effects.get(i % effects.size()).flying(flight));
    }
    return List.copyOf(particles);
  }

  public static void complete(
      ServerLevel level,
      BlockPos table,
      ItemStack before,
      Map<ResourceLocation, Integer> selected,
      int experienceCatalysts) {
    complete(
        level,
        table,
        Vec3.atLowerCornerOf(table).add(.5, 1.7, .5),
        before,
        selected,
        experienceCatalysts);
  }

  public static void complete(
      ServerLevel level,
      Vec3 center,
      ItemStack before,
      Map<ResourceLocation, Integer> selected,
      int experienceCatalysts) {
    complete(level, null, center, before, selected, experienceCatalysts);
  }

  public static void complete(
      ServerLevel level,
      BlockPos table,
      Vec3 center,
      ItemStack before,
      Map<ResourceLocation, Integer> selected,
      int experienceCatalysts) {
    for (var particle : completionParticles(center, before, selected, experienceCatalysts))
      send(level, table, new Emission(center, particle));
  }
}
