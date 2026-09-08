package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Failure previews never become users of the successful resource allocation. */
public record RitualAttempt(
    RitualMath.Plan successful,
    RitualMath.Plan visual,
    Map<ResourceLocation, Double> nativeCosts,
    Map<ResourceLocation, Double> minima,
    Set<ResourceLocation> failing) {
  public static RitualAttempt create(
      ServerPlayer player, ItemStack target, RitualNetwork.Snapshot network) {
    var success = RitualMath.automatic(player, target, network);
    var registry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    Map<ResourceLocation, Double> nativeCosts = new HashMap<>(), minima = new HashMap<>();
    Set<ResourceLocation> failing = new HashSet<>();
    var chains = new ArrayList<>(success.chains());
    var lines = new ArrayList<>(success.evaluation().lines());
    Map<BlockPos, Set<ResourceLocation>> users = new HashMap<>();
    success.users().forEach((pos, ids) -> users.put(pos, new HashSet<>(ids)));
    for (var id : network.knowledge().keySet()) {
      var def = Definitions.SERVER.get(id);
      var holder = registry.getHolder(id).orElse(null);
      if (def == null || holder == null) continue;
      int current = RitualMath.enchantments(target).getLevel(holder);
      if (current == 0
          && !target.is(Items.BOOK)
          && !target.is(Items.ENCHANTED_BOOK)
          && !target.supportsEnchantment(holder)) continue;
      var trial = new TreeMap<ResourceLocation, Integer>(success.selected());
      trial.put(id, 1);
      double scale =
          RitualMath.enchantabilityMultiplier(target)
              * RitualMath.conflicts(target, trial, Definitions.SERVER).getOrDefault(id, 1.0);
      var costs = new TreeMap<Integer, Double>();
      def.thresholds().forEach((l, p) -> costs.put(l, p * scale));
      var threshold = new RitualPower.Target(current, costs);
      nativeCosts.put(id, threshold.cost(holder.value().getMaxLevel()));
      if (success.selected().containsKey(id)) continue;
      var next = costs.higherEntry(current);
      if (next == null) continue;
      var routes = RitualChains.resolve(network, Set.of(id));
      var materials = RitualMath.materials(network, routes, success.experience().multiplier());
      double available = 0;
      for (var material : materials) {
        double power = material.powers().getOrDefault(id, 0.0);
        available += power / (success.users().getOrDefault(material.pos(), Set.of()).size() + 1);
      }
      // Ineffective subtraction remains inert. A positive but insufficient offering can falter.
      if (available <= 0 || available + 1e-8 >= next.getValue() || routes.isEmpty()) continue;
      failing.add(id);
      minima.put(id, next.getValue());
      chains.addAll(routes);
      for (var material : materials)
        if (material.powers().containsKey(id))
          users.computeIfAbsent(material.pos(), p -> new HashSet<>()).add(id);
      lines.add(
          new RitualMath.Line(
              id, next.getKey(), available, next.getValue(), scale, true, true, current));
    }
    var evaluation =
        new RitualMath.Evaluation(
            List.copyOf(lines),
            success.evaluation().used(),
            success.evaluation().ready(),
            success.evaluation().reason(),
            success.evaluation().xpMultiplier());
    var visual =
        new RitualMath.Plan(
            success.selected(),
            success.consumed(),
            success.experienceCatalysts(),
            evaluation,
            success.withdrawals(),
            List.copyOf(chains),
            users,
            success.remainingFractions(),
            success.experience());
    return new RitualAttempt(
        success, visual, Map.copyOf(nativeCosts), Map.copyOf(minima), Set.copyOf(failing));
  }
}
