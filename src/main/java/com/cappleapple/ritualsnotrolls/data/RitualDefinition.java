package com.cappleapple.ritualsnotrolls.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

public record RitualDefinition(
    ResourceLocation enchantment,
    List<Affinity> materials,
    Map<String, Double> levels,
    List<String> conflictGroups,
    Optional<ResourceLocation> visual,
    Optional<ResourceLocation> particle,
    Optional<ResourceLocation> sound,
    Optional<String> particleColor) {
  public static final Codec<RitualDefinition> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC
                          .fieldOf("enchantment")
                          .forGetter(RitualDefinition::enchantment),
                      Affinity.CODEC
                          .listOf()
                          .fieldOf("materials")
                          .forGetter(RitualDefinition::materials),
                      Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                          .fieldOf("levels")
                          .forGetter(RitualDefinition::levels),
                      Codec.STRING
                          .listOf()
                          .optionalFieldOf("conflict_groups", List.of())
                          .forGetter(RitualDefinition::conflictGroups),
                      ResourceLocation.CODEC
                          .optionalFieldOf("visual")
                          .forGetter(RitualDefinition::visual),
                      ResourceLocation.CODEC
                          .optionalFieldOf("particle")
                          .forGetter(RitualDefinition::particle),
                      ResourceLocation.CODEC
                          .optionalFieldOf("sound")
                          .forGetter(RitualDefinition::sound),
                      Codec.STRING
                          .optionalFieldOf("particle_color")
                          .forGetter(RitualDefinition::particleColor))
                  .apply(i, RitualDefinition::new));

  public RitualDefinition(
      ResourceLocation enchantment,
      List<Affinity> materials,
      Map<String, Double> levels,
      List<String> groups,
      Optional<ResourceLocation> visual,
      Optional<ResourceLocation> particle,
      Optional<ResourceLocation> sound) {
    this(enchantment, materials, levels, groups, visual, particle, sound, Optional.empty());
  }

  public int particleRgb() {
    return particleColor.map(c -> Integer.parseInt(c.substring(1), 16)).orElse(-1);
  }

  public RitualDefinition {
    particleColor =
        particleColor.map(
            color -> {
              if (!color.matches("#?[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("particle_color must be #RRGGBB");
              return "#" + color.replace("#", "").toUpperCase(Locale.ROOT);
            });
    materials = List.copyOf(materials);
    levels = Map.copyOf(levels);
    conflictGroups = List.copyOf(conflictGroups);
    Set<String> seen = new HashSet<>();
    for (Affinity a : materials)
      if (!seen.add(a.id())) throw new IllegalArgumentException("Duplicate material id " + a.id());
    if (materials.isEmpty() || levels.isEmpty())
      throw new IllegalArgumentException("Empty materials or levels");
    double previous = 0;
    for (var e : new TreeMap<>(numericLevels(levels)).entrySet()) {
      // The engine's ItemEnchantments codec has a hard 255 limit, independent of an enchantment's
      // max_level.
      if (e.getKey() < 1
          || e.getKey() > 255
          || !Double.isFinite(e.getValue())
          || e.getValue() <= previous)
        throw new IllegalArgumentException(
            "Levels must be 1..255 with strictly increasing, finite positive costs");
      previous = e.getValue();
    }
  }

  private static Map<Integer, Double> numericLevels(Map<String, Double> values) {
    Map<Integer, Double> out = new TreeMap<>();
    values.forEach(
        (k, v) -> {
          if (out.put(Integer.parseInt(k), v) != null)
            throw new IllegalArgumentException("Duplicate numeric level");
        });
    return out;
  }

  public SortedMap<Integer, Double> thresholds() {
    return new TreeMap<>(numericLevels(levels));
  }

  public Affinity affinity(String id) {
    return materials.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
  }

  public PowerTier powerTier(Affinity affinity) {
    double highest = materials.stream().mapToDouble(Affinity::power).max().orElseThrow();
    return PowerTier.relative(affinity.power(), highest);
  }

  public double required(int level) {
    return levels.getOrDefault(Integer.toString(level), Double.POSITIVE_INFINITY);
  }
}
