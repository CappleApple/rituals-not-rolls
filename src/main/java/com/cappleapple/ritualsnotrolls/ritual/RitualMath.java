package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.Config;
import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

public final class RitualMath {
  public record Line(
      ResourceLocation enchantment,
      int level,
      double power,
      double required,
      double conflict,
      boolean known,
      boolean applicable,
      int attainable) {}

  public record Evaluation(
      List<Line> lines,
      List<RitualNetwork.Pedestal> used,
      boolean ready,
      String reason,
      double xpMultiplier) {}

  public static ResourceLocation itemId(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem());
  }

  public static Map<ResourceLocation, RitualNetwork.Pedestal> unique(
      RitualNetwork.Snapshot network, Set<ResourceLocation> consumed) {
    Map<ResourceLocation, RitualNetwork.Pedestal> out = new TreeMap<>();
    for (var p : network.pedestals())
      if (!p.stack().isEmpty() && !isCatalyst(p.stack()))
        out.merge(itemId(p.stack()), p, (a, b) -> !a.catalyst() && b.catalyst() ? b : a);
    return out;
  }

  public static double base(RitualDefinition definition, Set<String> known, ItemStack stack) {
    return definition.materials().stream()
        .filter(a -> known.contains(a.id()) && a.matches(stack))
        .mapToDouble(Affinity::power)
        .max()
        .orElse(0);
  }

  public static ItemEnchantments enchantments(ItemStack stack) {
    return stack.getOrDefault(
        stack.is(Items.ENCHANTED_BOOK)
            ? DataComponents.STORED_ENCHANTMENTS
            : DataComponents.ENCHANTMENTS,
        ItemEnchantments.EMPTY);
  }

  public static boolean applicable(
      ItemStack target, net.minecraft.core.Holder<Enchantment> enchantment) {
    return target.is(Items.BOOK)
        || target.is(Items.ENCHANTED_BOOK)
        || target.supportsEnchantment(enchantment)
        || enchantments(target).getLevel(enchantment) > 0;
  }

  public static Map<ResourceLocation, Double> conflicts(
      ItemStack target, Map<ResourceLocation, Integer> selected, Definitions.Snapshot data) {
    Map<String, Integer> counts = new HashMap<>();
    Map<ResourceLocation, Double> result = new LinkedHashMap<>();
    // Existing enchantments precede automatically selected entries in stable registry-ID order.
    Set<ResourceLocation> order = new LinkedHashSet<>();
    enchantments(target).keySet().stream()
        .map(h -> h.unwrapKey().orElseThrow().location())
        .sorted()
        .forEach(order::add);
    order.addAll(selected.keySet());
    for (var id : order) {
      var def = data.get(id);
      if (def == null) continue;
      double multiplier = 1;
      for (String group : def.conflictGroups()) {
        multiplier =
            Math.max(
                multiplier,
                Math.pow(
                    data.rules().conflicts().getOrDefault(group, 2.0),
                    counts.getOrDefault(group, 0)));
        counts.merge(group, 1, Integer::sum);
      }
      result.put(id, multiplier);
    }
    return result;
  }

  public static Evaluation evaluate(
      ServerPlayer player,
      ItemStack target,
      Map<ResourceLocation, Integer> selected,
      Set<ResourceLocation> consumed,
      int xp,
      RitualNetwork.Snapshot network) {
    var plan = planFor(player, target, selected, network);
    if (xp != catalysts(network).experience() || !plan.consumed().equals(consumed))
      return new Evaluation(
          plan.evaluation().lines(),
          plan.evaluation().used(),
          false,
          "Catalysts changed",
          plan.evaluation().xpMultiplier());
    return plan.evaluation();
  }

  public record Catalysts(boolean sacrifice, int experience) {}

  public record Plan(
      Map<ResourceLocation, Integer> selected,
      Set<ResourceLocation> consumed,
      int experienceCatalysts,
      Evaluation evaluation,
      Map<BlockPos, Integer> withdrawals,
      List<RitualChains.Chain> chains,
      Map<BlockPos, Set<ResourceLocation>> users,
      Map<ResourceLocation, Double> remainingFractions,
      RitualRules.Experience experience) {
    public Plan {
      selected = Collections.unmodifiableMap(new LinkedHashMap<>(selected));
      consumed = Set.copyOf(consumed);
      withdrawals = Map.copyOf(withdrawals);
      chains = List.copyOf(chains);
      users = Map.copyOf(users);
      remainingFractions = Map.copyOf(remainingFractions);
    }
  }

  public static boolean isCatalyst(ItemStack stack) {
    return stack.is(RitualsNotRolls.CONSUMPTION_CATALYST)
        || stack.is(RitualsNotRolls.SUBTRACTION_CATALYST)
        || stack.is(RitualsNotRolls.XP_CATALYST);
  }

  /** Catalysts are physical pedestal contents, never items merely carried by the player. */
  public static Catalysts catalysts(RitualNetwork.Snapshot network) {
    boolean sacrifice = false;
    long experience = 0;
    for (var p : network.pedestals()) {
      sacrifice |= p.catalyst();
      if (p.stack().is(RitualsNotRolls.XP_CATALYST)) experience += p.stack().getCount();
    }
    return new Catalysts(sacrifice, (int) Math.min(Integer.MAX_VALUE, experience));
  }

  public static double enchantabilityMultiplier(ItemStack target) {
    if (!Config.ENCHANTABILITY.get() || target.is(Items.BOOK) || target.is(Items.ENCHANTED_BOOK))
      return 1;
    double rating =
        PowerEquation.effectiveEnchantability(
            Config.ENCHANTABILITY_EQUATION.get(),
            target.getItem().getEnchantmentValue(target),
            Config.ENCHANTABILITY_BASE.get());
    return Math.pow(
        Config.ENCHANTABILITY_BASE.get() / rating, Config.ENCHANTABILITY_EXPONENT.get());
  }

  public static double power(
      RitualDefinition def, Set<String> known, RitualNetwork.Snapshot network) {
    var chains = RitualChains.resolve(network, Set.of(def.enchantment()));
    return materials(network, chains).stream()
        .mapToDouble(m -> m.powers().getOrDefault(def.enchantment(), 0.0))
        .sum();
  }

  public static List<RitualPower.Material> materials(
      RitualNetwork.Snapshot network, List<RitualChains.Chain> chains) {
    return materials(
        network, chains, Definitions.SERVER.rules().xpMultiplier(catalysts(network).experience()));
  }

  public static List<RitualPower.Material> materials(
      RitualNetwork.Snapshot network, List<RitualChains.Chain> chains, double xp) {
    Map<BlockPos, Map<ResourceLocation, Double>> powers = new LinkedHashMap<>();
    Map<String, RitualNetwork.Pedestal> reusable = new HashMap<>();
    Map<String, Double> reusablePower = new HashMap<>();
    for (var chain : chains) {
      var def = Definitions.SERVER.get(chain.enchantment());
      var entries =
          network.sources().stream()
              .filter(
                  k ->
                      k.pos().equals(chain.source()) && k.enchantment().equals(chain.enchantment()))
              .flatMap(k -> k.entries().stream())
              .collect(java.util.stream.Collectors.toSet());
      for (var p : chain.outward()) {
        double amount =
            base(def, entries, p.stack())
                * chain.visits(p.pos())
                * xp
                * (p.catalyst() ? Definitions.SERVER.rules().consumptionMultiplier() : 1);
        if (amount <= 0) continue;
        String key = chain.enchantment() + "/" + itemId(p.stack()) + "/" + p.subtraction();
        if (!p.catalyst()) {
          if (reusablePower.getOrDefault(key, -1.0) >= amount) continue;
          var previous = reusable.put(key, p);
          reusablePower.put(key, amount);
          if (previous != null) powers.get(previous.pos()).remove(chain.enchantment());
        }
        powers
            .computeIfAbsent(p.pos(), k -> new LinkedHashMap<>())
            .put(chain.enchantment(), p.subtraction() ? -amount : amount);
      }
    }
    // Count consumed copies across every bookshelf branch of this enchantment. Rank the
    // strongest contributions first so scan order cannot change the available power.
    record Duplicate(ResourceLocation enchantment, ResourceLocation item, boolean subtraction) {}
    Map<Duplicate, List<BlockPos>> duplicates = new LinkedHashMap<>();
    for (var p : network.pedestals()) {
      if (!p.catalyst() || !powers.containsKey(p.pos())) continue;
      for (var id : powers.get(p.pos()).keySet())
        duplicates
            .computeIfAbsent(
                new Duplicate(id, itemId(p.stack()), p.subtraction()), k -> new ArrayList<>())
            .add(p.pos());
    }
    duplicates.forEach(
        (key, positions) -> {
          positions.sort(
              Comparator.<BlockPos>comparingDouble(
                      pos -> -Math.abs(powers.get(pos).get(key.enchantment())))
                  .thenComparingLong(BlockPos::asLong));
          for (int i = 0; i < positions.size(); i++) {
            var values = powers.get(positions.get(i));
            double amount =
                values.get(key.enchantment()) * Definitions.SERVER.rules().duplicateFactor(i + 1);
            if (amount == 0) values.remove(key.enchantment());
            else values.put(key.enchantment(), amount);
          }
        });
    List<RitualPower.Material> result = new ArrayList<>();
    for (var p : network.pedestals())
      if (powers.containsKey(p.pos()) && !powers.get(p.pos()).isEmpty())
        result.add(
            new RitualPower.Material(
                p.pos(), itemId(p.stack()), p.subtraction(), Map.copyOf(powers.get(p.pos()))));
    return List.copyOf(result);
  }

  private static Plan solve(
      ServerPlayer player,
      ItemStack target,
      Set<ResourceLocation> ids,
      RitualNetwork.Snapshot network,
      Map<ResourceLocation, Integer> requested) {
    return solve(player, target, ids, network, requested, experiencePoints(player));
  }

  private static Plan solve(
      ServerPlayer player,
      ItemStack target,
      Set<ResourceLocation> ids,
      RitualNetwork.Snapshot network,
      Map<ResourceLocation, Integer> requested,
      int experienceBudget) {
    var data = Definitions.SERVER;
    var experience = data.rules().experience(catalysts(network).experience(), experienceBudget);
    var chains = RitualChains.resolve(network, ids);
    var material = materials(network, chains, experience.multiplier());
    Map<ResourceLocation, Integer> order = new LinkedHashMap<>();
    ids.stream()
        .sorted(java.util.Comparator.comparing(Object::toString))
        .forEach(id -> order.put(id, 1));
    var conflict = conflicts(target, order, data);
    double costScale = enchantabilityMultiplier(target);
    Map<ResourceLocation, RitualPower.Target> targets = new LinkedHashMap<>();
    var registry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    String reason = target.isEmpty() || target.getCount() != 1 ? "Drop one target item" : "";
    for (var id : order.keySet()) {
      var holder = registry.getHolder(id).orElse(null);
      var def = data.get(id);
      if (holder == null || def == null) {
        reason = "Missing enchantment definition";
        continue;
      }
      int current = enchantments(target).getLevel(holder);
      if (!applicable(target, holder)) {
        reason = "Unsupported enchantment";
        continue;
      }
      NavigableMap<Integer, Double> costs = new TreeMap<>();
      def.thresholds()
          .forEach(
              (level, value) ->
                  costs.put(level, value * costScale * conflict.getOrDefault(id, 1.0)));
      targets.put(id, new RitualPower.Target(current, costs));
    }
    var working = RitualPower.allocate(targets, material);
    while (true) {
      var trimmed = RitualChains.trim(chains, working.users(), network.table());
      if (trimmed.equals(chains)) break;
      chains = trimmed;
      material = materials(network, chains, experience.multiplier());
      working = RitualPower.allocate(targets, material);
    }
    final var allocation = working;
    Map<ResourceLocation, Integer> selected = new LinkedHashMap<>();
    List<Line> lines = new ArrayList<>();
    Map<ResourceLocation, Double> fractions = new HashMap<>();
    for (var e : targets.entrySet()) {
      var id = e.getKey();
      var t = e.getValue();
      double power = allocation.powers().get(id);
      int level = allocation.levels().get(id);
      int wanted = requested == null ? level : requested.getOrDefault(id, level);
      if (wanted == t.current()
          || wanted < 0
          || (wanted > t.current() ? level < wanted : level > wanted))
        reason = "No full enchantment level can change";
      selected.put(id, wanted);
      double positive = 0, negative = 0;
      for (var m : material) {
        var users = allocation.users().getOrDefault(m.pos(), Set.of());
        if (!users.contains(id)) continue;
        double contribution = m.powers().get(id) / users.size();
        if (contribution > 0) positive += contribution;
        else negative -= contribution;
      }
      double original = positive + (power < 0 ? t.cost(t.current()) : 0);
      fractions.put(
          id,
          negative > 0 && original > 0 ? Math.clamp((original - negative) / original, 0, 1) : 1.0);
      lines.add(
          new Line(
              id,
              wanted,
              power,
              t.cost(wanted),
              conflict.getOrDefault(id, 1.0),
              true,
              true,
              level));
    }
    List<RitualNetwork.Pedestal> used =
        network.pedestals().stream()
            .filter(p -> !allocation.users().getOrDefault(p.pos(), Set.of()).isEmpty())
            .toList();
    Map<BlockPos, Integer> withdrawals = new LinkedHashMap<>();
    Set<ResourceLocation> consumed = new HashSet<>();
    for (var p : used)
      if (p.catalyst()) {
        withdrawals.put(p.pos(), 1);
        consumed.add(itemId(p.stack()));
      }
    int xp = catalysts(network).experience();
    if (selected.isEmpty()) reason = "No full enchantment level can change";
    return new Plan(
        selected,
        consumed,
        xp,
        new Evaluation(List.copyOf(lines), used, reason.isEmpty(), reason, experience.multiplier()),
        withdrawals,
        chains,
        allocation.users(),
        fractions,
        experience);
  }

  public static Plan planFor(
      ServerPlayer player,
      ItemStack target,
      Map<ResourceLocation, Integer> selected,
      RitualNetwork.Snapshot network) {
    return solve(player, target, selected.keySet(), network, selected);
  }

  /** Revalidate the captured payment without spending newly gained XP or increasing its bonus. */
  public static Plan planFor(
      ServerPlayer player,
      ItemStack target,
      Map<ResourceLocation, Integer> selected,
      RitualNetwork.Snapshot network,
      int experienceBudget) {
    return solve(player, target, selected.keySet(), network, selected, experienceBudget);
  }

  public static Plan automatic(
      ServerPlayer player, ItemStack target, RitualNetwork.Snapshot network) {
    Set<ResourceLocation> active = new LinkedHashSet<>();
    Plan best = solve(player, target, active, network, null);
    // Compare each candidate's full, unsplit chain power, including modifiers and return visits.
    Map<ResourceLocation, Plan> standalone = new HashMap<>();
    for (var id : network.knowledge().keySet())
      if (Definitions.SERVER.get(id) != null)
        standalone.put(id, solve(player, target, Set.of(id), network, null));
    var priority =
        Comparator.<ResourceLocation>comparingDouble(
                id ->
                    standalone.get(id).evaluation().lines().stream()
                        .filter(line -> line.enchantment().equals(id))
                        .mapToDouble(line -> Math.abs(line.power()))
                        .findFirst()
                        .orElse(0))
            .reversed()
            .thenComparing(ResourceLocation::toString);
    for (var id : standalone.keySet().stream().sorted(priority).toList()) {
      Set<ResourceLocation> trial = new LinkedHashSet<>(active);
      trial.add(id);
      var plan =
          active.isEmpty() ? standalone.get(id) : solve(player, target, trial, network, null);
      if (plan.evaluation().ready()) {
        active = trial;
        best = plan;
      }
    }
    return best;
  }

  public static int experiencePoints(ServerPlayer p) {
    long l = p.experienceLevel;
    double base =
        l <= 16
            ? l * l + 6 * l
            : l <= 31 ? 2.5 * l * l - 40.5 * l + 360 : 4.5 * l * l - 162.5 * l + 2220;
    return (int)
        Math.min(
            Integer.MAX_VALUE,
            base + Math.round(p.experienceProgress * p.getXpNeededForNextLevel()));
  }

  public static ItemStack apply(
      ServerPlayer player, ItemStack target, Map<ResourceLocation, Integer> selected) {
    ItemStack result =
        target.is(Items.BOOK) ? target.transmuteCopy(Items.ENCHANTED_BOOK, 1) : target.copy();
    var component =
        result.is(Items.ENCHANTED_BOOK)
            ? DataComponents.STORED_ENCHANTMENTS
            : DataComponents.ENCHANTMENTS;
    var mutable =
        new ItemEnchantments.Mutable(result.getOrDefault(component, ItemEnchantments.EMPTY));
    selected.forEach(
        (id, level) ->
            mutable.set(
                player
                    .registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolder(id)
                    .orElseThrow(),
                level));
    result.set(component, mutable.toImmutable());
    return result;
  }
}
