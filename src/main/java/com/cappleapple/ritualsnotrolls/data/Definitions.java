package com.cappleapple.ritualsnotrolls.data;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Atomic, immutable server snapshots. Client snapshots are kept separately, including on integrated
 * servers.
 */
public final class Definitions extends SimpleJsonResourceReloadListener {
  public record Snapshot(
      Map<ResourceLocation, RitualDefinition> enchantments,
      RitualRules rules,
      int revision,
      Set<ResourceLocation> disabled) {
    public Snapshot(
        Map<ResourceLocation, RitualDefinition> enchantments, RitualRules rules, int revision) {
      this(enchantments, rules, revision, Set.of());
    }

    public Snapshot {
      enchantments = Collections.unmodifiableMap(new TreeMap<>(enchantments));
      disabled = Set.copyOf(disabled);
    }

    public boolean configured(ResourceLocation id) {
      return enchantments.containsKey(id) || disabled.contains(id);
    }

    public RitualDefinition get(ResourceLocation id) {
      return enchantments.get(id);
    }
  }

  public static volatile Snapshot SERVER = new Snapshot(Map.of(), RitualRules.DEFAULT, 0);
  public static volatile Snapshot CLIENT = new Snapshot(Map.of(), RitualRules.DEFAULT, 0);

  private final Predicate<ResourceLocation> enchantmentPresent;

  public Definitions(Predicate<ResourceLocation> enchantmentPresent) {
    super(new Gson(), "ritual_enchanting");
    this.enchantmentPresent = enchantmentPresent;
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
    SERVER = load(input, enchantmentPresent, SERVER.revision() + 1);
    RitualsNotRolls.LOGGER.info(
        "Loaded {} ritual enchantment definitions, {} explicitly disabled, revision {}",
        SERVER.enchantments().size(),
        SERVER.disabled().size(),
        SERVER.revision());
  }

  public static Snapshot load(
      Map<ResourceLocation, JsonElement> input,
      Predicate<ResourceLocation> enchantmentPresent,
      int revision) {
    Map<ResourceLocation, RitualDefinition> definitions = new TreeMap<>();
    Set<ResourceLocation> disabled = new TreeSet<>();
    Set<ResourceLocation> seen = new HashSet<>();
    RitualRules rules = com.cappleapple.ritualsnotrolls.Config.rules();
    for (var e : new TreeMap<>(input).entrySet()) {
      try {
        if (e.getKey().equals(RitualsNotRolls.id("rules"))) {
          RitualsNotRolls.LOGGER.warn(
              "Ignoring legacy ritual rules.json; move its settings to ritualsnotrolls-server.toml"
                  + " [rules]");
        } else if (e.getKey().getPath().startsWith("enchantments/")) {
          JsonObject json = e.getValue().getAsJsonObject();
          ResourceLocation id =
              ResourceLocation.CODEC.parse(JsonOps.INSTANCE, json.get("enchantment")).getOrThrow();
          if (json.has("optional") && readBoolean(json, "optional") && !enchantmentPresent.test(id))
            continue;
          if (seen.contains(id))
            throw new IllegalArgumentException(
                "Duplicate enchantment definition "
                    + id
                    + "; override the original file path instead");
          if (json.has("enabled") && !readBoolean(json, "enabled")) {
            disabled.add(id);
            seen.add(id);
            continue;
          }
          RitualDefinition d =
              RitualDefinition.CODEC.parse(JsonOps.INSTANCE, e.getValue()).getOrThrow();
          definitions.put(d.enchantment(), d);
          seen.add(d.enchantment());
        }
      } catch (RuntimeException ex) {
        RitualsNotRolls.LOGGER.warn("Ignoring ritual data {}: {}", e.getKey(), ex.getMessage());
      }
    }
    return new Snapshot(definitions, rules, revision, disabled);
  }

  public static void refreshRules() {
    var current = SERVER;
    var rules = com.cappleapple.ritualsnotrolls.Config.rules();
    SERVER =
        new Snapshot(current.enchantments(), rules, current.revision() + 1, current.disabled());
  }

  private static boolean readBoolean(JsonObject json, String field) {
    return com.mojang.serialization.Codec.BOOL
        .parse(JsonOps.INSTANCE, json.get(field))
        .getOrThrow();
  }

  public static Snapshot forSide(boolean client) {
    return client ? CLIENT : SERVER;
  }

  public static String encode(RitualDefinition d) {
    return RitualDefinition.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow().toString();
  }
}
