package com.cappleapple.ritualsnotrolls.ritual;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Immutable, server-owned animation timing, captured when a ritual starts. */
public final class RitualAnimation {
  public record Input(
      ResourceLocation enchantment,
      double power,
      int knowledgeTravel,
      int materialTravel,
      double nativeFraction,
      double failureFraction,
      boolean failing) {
    public Input(
        ResourceLocation enchantment, double power, int knowledgeTravel, int materialTravel) {
      this(enchantment, power, knowledgeTravel, materialTravel, 1, 1, false);
    }
  }

  public record Stage(
      ResourceLocation enchantment,
      int ring,
      int start,
      int materialStart,
      int formed,
      float radius,
      int weight,
      float weakness,
      int shakeAt,
      int ramp,
      int failAt) {
    public Stage(
        ResourceLocation enchantment,
        int ring,
        int start,
        int materialStart,
        int formed,
        float radius,
        int weight) {
      this(enchantment, ring, start, materialStart, formed, radius, weight, 0, 0, 1, -1);
    }

    public RitualInstability instability(int age) {
      return new RitualInstability(weakness, age, shakeAt, ramp, failAt);
    }
  }

  public record Schedule(
      List<Stage> stages,
      int duration,
      int sourceStop,
      float experienceRadius,
      int experienceStop) {
    public Schedule(List<Stage> stages, int duration) {
      this(stages, duration, duration, .65f, duration);
    }

    public List<Stage> active(int age) {
      return stages.stream().filter(s -> age >= s.start()).toList();
    }
  }

  /**
   * Square-root growth is visible across normal powers without letting data packs fill the world.
   */
  public static float radius(double power) {
    return (float) Math.clamp(.42 + .045 * Math.sqrt(Math.max(0, power)), .42, 2.4);
  }

  /** Preserve a small visible ring, with one tenth of the enchantment radius growth coefficient. */
  public static float experienceRadius(int points) {
    return (float) Math.clamp(.42 + .0045 * Math.sqrt(Math.max(0, points)), .42, 2.4);
  }

  public static int weight(float radius) {
    return (int) Math.ceil(radius * 14);
  }

  public static Schedule plan(List<Input> inputs, int baseDuration, boolean sequential) {
    return plan(inputs, baseDuration, sequential, 0, 0);
  }

  public static Schedule plan(
      List<Input> inputs,
      int baseDuration,
      boolean sequential,
      int experienceTravel,
      int experiencePoints) {
    List<Stage> stages = new ArrayList<>();
    int next = 0, latest = 0, index = 0;
    int longest = 0;
    int experienceStop =
        experienceTravel > 0 ? 22 + experienceTravel + RitualFlight.ringFormationTicks(8) : 0;
    latest = experienceStop;
    for (var input :
        inputs.stream().sorted(Comparator.comparing(i -> i.enchantment().toString())).toList()) {
      int start = sequential ? next : 0;
      int material = start + input.knowledgeTravel();
      // A single entrance needs one revolution to populate the ring before the next channel.
      int ring = (index++ * 4) % 9;
      int formed = material + input.materialTravel() + RitualFlight.ringFormationTicks(ring);
      float radius = radius(input.power());
      int shake = material + input.materialTravel() - 4;
      int ramp = input.failing() ? RitualInstability.failureRamp(input.failureFraction()) : 48;
      int fail = input.failing() ? shake + ramp : -1;
      stages.add(
          new Stage(
              input.enchantment(),
              ring,
              start,
              material,
              formed,
              radius,
              weight(radius),
              (float) (1 - Math.clamp(input.nativeFraction(), 0, 1)),
              shake,
              ramp,
              fail));
      next = input.failing() ? Math.min(formed, fail) : formed;
      latest = Math.max(latest, input.failing() ? fail : formed);
      if (!input.failing())
        longest = Math.max(longest, input.knowledgeTravel() + input.materialTravel());
    }
    // Enchantment sources stop together; XP stops once its own ring forms. Last waves arrive before
    // the 24-tick gathering hold and shared 28-tick pulse, even on the longest chain.
    return new Schedule(
        List.copyOf(stages),
        Math.max(baseDuration, Math.max(latest + longest, experienceStop + experienceTravel) + 52),
        latest,
        experienceRadius(experiencePoints),
        experienceStop);
  }

  /** Evenly stratified weighted sampling avoids starving small rings when many enchantments run. */
  public static List<Integer> allocation(List<Integer> weights, int age) {
    int total = weights.stream().mapToInt(Integer::intValue).sum();
    if (total <= 0) return List.of();
    int count = Math.min(16, Math.max(1, (total + 4) / 5));
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      double point = ((i + .5) / count + age * .38196601125) % 1 * total;
      for (int j = 0; j < weights.size(); j++) {
        point -= weights.get(j);
        if (point < 0) {
          result.add(j);
          break;
        }
      }
    }
    return List.copyOf(result);
  }
}
