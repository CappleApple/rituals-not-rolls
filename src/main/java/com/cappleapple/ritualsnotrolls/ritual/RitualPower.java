package com.cappleapple.ritualsnotrolls.ritual;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Conditional sharing is per physical material, using each enchantment's own relative power. */
public final class RitualPower {
  public record Target(int current, NavigableMap<Integer, Double> costs) {
    public double cost(int level) {
      if (level <= 0) return 0;
      var exact = costs.get(level);
      if (exact != null) return exact;
      var low = costs.floorEntry(level);
      var high = costs.ceilingEntry(level);
      if (high == null)
        return costs.lastEntry().getValue() * Math.pow(level / (double) costs.lastKey(), 2);
      int below = low == null ? 0 : low.getKey();
      double base = low == null ? 0 : low.getValue();
      return base + (high.getValue() - base) * (level - below) / (high.getKey() - below);
    }

    public int level(double power) {
      if (power >= 0) {
        int result = current;
        for (var e : costs.entrySet())
          if (power + 1e-8 >= e.getValue()) result = Math.max(result, e.getKey());
        return result;
      }
      double remaining = Math.max(0, cost(current) + power);
      int result = current;
      while (result > 0 && remaining <= cost(result - 1) + 1e-8) result--;
      return result;
    }
  }

  public record Material(
      BlockPos pos,
      ResourceLocation item,
      boolean subtraction,
      Map<ResourceLocation, Double> powers) {}

  public record Result(
      Map<ResourceLocation, Double> powers,
      Map<ResourceLocation, Integer> levels,
      Map<BlockPos, Set<ResourceLocation>> users) {}

  public static Result allocate(Map<ResourceLocation, Target> targets, List<Material> materials) {
    Map<BlockPos, Set<ResourceLocation>> users = new LinkedHashMap<>();
    for (var m : materials) {
      var eligible = new HashSet<>(m.powers().keySet());
      eligible.retainAll(targets.keySet());
      users.put(m.pos(), eligible);
    }
    // An affinity unable to change a level even without sharing cannot reserve a share.
    targets.forEach(
        (id, target) -> {
          double full = materials.stream().mapToDouble(m -> m.powers().getOrDefault(id, 0.0)).sum();
          if (target.level(full) == target.current()) users.values().forEach(set -> set.remove(id));
        });
    boolean changed;
    do {
      changed = false;
      for (var id : targets.keySet()) {
        Set<String> groups = new HashSet<>();
        for (var m : materials) {
          if (!users.get(m.pos()).contains(id) || !groups.add(m.item() + ":" + m.subtraction()))
            continue;
          var group =
              materials.stream()
                  .filter(
                      p ->
                          p.item().equals(m.item())
                              && p.subtraction() == m.subtraction()
                              && users.get(p.pos()).contains(id))
                  .toList();
          boolean shared = group.stream().anyMatch(p -> users.get(p.pos()).size() > 1);
          if (!shared && !m.subtraction()) continue;
          double before = power(id, materials, users);
          for (var p : group) users.get(p.pos()).remove(id);
          double after = power(id, materials, users);
          if (targets.get(id).level(before) == targets.get(id).level(after)) changed = true;
          else for (var p : group) users.get(p.pos()).add(id);
        }
      }
    } while (changed);
    Map<ResourceLocation, Double> powers = new LinkedHashMap<>();
    Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
    targets.forEach(
        (id, target) -> {
          double p = power(id, materials, users);
          powers.put(id, p);
          levels.put(id, target.level(p));
        });
    Map<BlockPos, Set<ResourceLocation>> immutable = new LinkedHashMap<>();
    users.forEach((p, set) -> immutable.put(p, Set.copyOf(set)));
    return new Result(Map.copyOf(powers), Map.copyOf(levels), Map.copyOf(immutable));
  }

  private static double power(
      ResourceLocation id, List<Material> materials, Map<BlockPos, Set<ResourceLocation>> users) {
    double result = 0;
    for (var m : materials) {
      var assigned = users.get(m.pos());
      if (assigned.contains(id)) result += m.powers().getOrDefault(id, 0.0) / assigned.size();
    }
    return result;
  }
}
