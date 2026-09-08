package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.Config;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Spatial ownership starts at a book's nearest matching material, allowing distinct branches. */
public final class RitualChains {
  public record Chain(
      ResourceLocation enchantment,
      BlockPos source,
      List<RitualNetwork.Pedestal> outward,
      List<RitualNetwork.Pedestal> returning,
      boolean chained) {
    public double visits(BlockPos pos) {
      return 1
          + (returning.stream().anyMatch(p -> p.pos().equals(pos))
              ? Config.CHAIN_RETURN_BONUS.get()
              : 0);
    }
  }

  private record Seed(RitualNetwork.KnowledgeSource source, RitualNetwork.Pedestal pedestal) {}

  public static List<Chain> resolve(
      RitualNetwork.Snapshot network, Set<ResourceLocation> enchantments) {
    List<Seed> seeds = new ArrayList<>();
    for (var source : network.sources()) {
      var def = Definitions.SERVER.get(source.enchantment());
      if (def == null || !enchantments.contains(source.enchantment())) continue;
      network.pedestals().stream()
          .filter(
              p ->
                  !RitualMath.isCatalyst(p.stack())
                      && RitualMath.base(def, source.entries(), p.stack()) > 0)
          .min(
              Comparator.<RitualNetwork.Pedestal>comparingDouble(p -> p.pos().distSqr(source.pos()))
                  .thenComparingLong(p -> p.pos().asLong()))
          .ifPresent(p -> seeds.add(new Seed(source, p)));
    }
    Map<Seed, List<RitualNetwork.Pedestal>> owned = new LinkedHashMap<>();
    for (var p : network.pedestals()) {
      if (p.stack().isEmpty() || RitualMath.isCatalyst(p.stack())) continue;
      double best = Double.POSITIVE_INFINITY;
      List<Seed> nearest = new ArrayList<>();
      for (var seed : seeds) {
        var source = seed.source();
        var def = Definitions.SERVER.get(source.enchantment());
        if (RitualMath.base(def, source.entries(), p.stack()) <= 0) continue;
        double distance =
            Math.sqrt(source.pos().distSqr(seed.pedestal().pos()))
                + Math.sqrt(seed.pedestal().pos().distSqr(p.pos()));
        if (distance < best - 1e-6) {
          best = distance;
          nearest.clear();
        }
        if (Math.abs(distance - best) < 1e-6) nearest.add(seed);
      }
      // A physical pedestal may serve tied enchantments, but never two books of the same
      // enchantment.
      Set<ResourceLocation> claimed = new HashSet<>();
      nearest.sort(Comparator.comparingLong(a -> a.source().pos().asLong()));
      for (var seed : nearest)
        if (claimed.add(seed.source().enchantment()))
          owned.computeIfAbsent(seed, k -> new ArrayList<>()).add(p);
    }
    List<Chain> result = new ArrayList<>();
    Set<String> reusableItems = new HashSet<>();
    for (var entry : owned.entrySet()) {
      var source = entry.getKey().source();
      var outward = entry.getValue();
      outward.sort(
          Comparator.<RitualNetwork.Pedestal>comparingDouble(p -> p.pos().distSqr(source.pos()))
              .thenComparingLong(p -> p.pos().asLong()));
      outward.removeIf(
          p ->
              !p.catalyst()
                  && !reusableItems.add(source.enchantment() + "/" + RitualMath.itemId(p.stack())));
      if (outward.isEmpty()) continue;
      result.add(
          route(
              source.enchantment(),
              source.pos(),
              outward,
              network.table(),
              Config.CHAIN_ANIMATIONS.get()));
    }
    return List.copyOf(result);
  }

  private static Chain route(
      ResourceLocation enchantment,
      BlockPos source,
      List<RitualNetwork.Pedestal> outward,
      BlockPos table,
      boolean chained) {
    List<RitualNetwork.Pedestal> returning = new ArrayList<>();
    // Applying and subtracting are opposite flows. Each has its own farthest point.
    if (chained)
      for (boolean subtraction : List.of(false, true)) {
        var branch = outward.stream().filter(p -> p.subtraction() == subtraction).toList();
        if (branch.isEmpty()) continue;
        Vec3 from = Vec3.atCenterOf(branch.getLast().pos()),
            to = Vec3.atCenterOf(table),
            delta = to.subtract(from);
        List<RitualNetwork.Pedestal> back = new ArrayList<>();
        for (var p : branch) {
          Vec3 point = Vec3.atCenterOf(p.pos());
          double t = point.subtract(from).dot(delta) / Math.max(.001, delta.lengthSqr());
          if (t > 1e-6
              && t < 1 - 1e-6
              && point.distanceTo(from.lerp(to, t)) <= Config.CHAIN_CORRIDOR.get()) back.add(p);
        }
        back.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p.pos()).distanceTo(from)));
        returning.addAll(back);
      }
    return new Chain(enchantment, source, List.copyOf(outward), List.copyOf(returning), chained);
  }

  public static List<Chain> trim(
      List<Chain> chains, Map<BlockPos, Set<ResourceLocation>> users, BlockPos table) {
    List<Chain> result = new ArrayList<>();
    for (var c : chains) {
      var outward =
          c.outward().stream()
              .filter(p -> users.getOrDefault(p.pos(), Set.of()).contains(c.enchantment()))
              .toList();
      if (!outward.isEmpty())
        result.add(route(c.enchantment(), c.source(), outward, table, c.chained()));
    }
    return List.copyOf(result);
  }
}
