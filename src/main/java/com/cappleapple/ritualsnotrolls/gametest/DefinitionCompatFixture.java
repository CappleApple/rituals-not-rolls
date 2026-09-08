package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Optional real-mod registry/definition gate, excluded from the production artifact. */
@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class DefinitionCompatFixture {
  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    if (!Boolean.getBoolean("ritualsnotrolls.definitionCompatFixture")) return;
    var server = event.getServer();
    try {
      var manifest =
          JsonParser.parseString(
                  Files.readString(Path.of("../tools/compat-defaults-manifest.json")))
              .getAsJsonArray();
      var registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
      Set<String> visuals = new HashSet<>();
      for (var definition : Definitions.SERVER.enchantments().values()) {
        if (!visuals.add(
            definition.particle().orElseThrow() + "/" + definition.particleColor().orElseThrow()))
          throw new IllegalStateException(
              "Duplicate particle identity: " + definition.enchantment());
      }
      for (var row : manifest) {
        var meta = row.getAsJsonObject();
        var id = ResourceLocation.parse(meta.get("enchantment").getAsString());
        var definition = Definitions.SERVER.get(id);
        if (definition == null || registry.get(id) == null)
          throw new IllegalStateException("Missing optional support: " + id);
        int maximum = registry.get(id).getMaxLevel();
        if (maximum != meta.get("native_maximum").getAsInt())
          throw new IllegalStateException("Native level drift: " + id);
        Set<ResourceLocation> unique = new HashSet<>();
        double total = 0;
        for (var affinity : definition.materials()) {
          var item = affinity.item().orElseThrow();
          if (!unique.add(item) || !BuiltInRegistries.ITEM.containsKey(item))
            throw new IllegalStateException("Bad material: " + id + "/" + item);
          if (!affinity.matches(new ItemStack(BuiltInRegistries.ITEM.get(item))))
            throw new IllegalStateException("Material cannot contribute: " + item);
          total += affinity.power();
        }
        if (Math.abs(total - definition.required(maximum)) > 1e-8)
          throw new IllegalStateException("Maximum not balanced: " + id);
        for (var affinity : definition.materials())
          if (total - affinity.power() >= definition.required(maximum))
            throw new IllegalStateException("Incomplete set reaches max: " + id);
        if (!(BuiltInRegistries.PARTICLE_TYPE.get(definition.particle().orElseThrow())
            instanceof net.minecraft.core.particles.SimpleParticleType))
          throw new IllegalStateException("Particle not supported: " + id);
        RitualsNotRolls.LOGGER.info(
            "OPTIONAL DEFINITION PASS {} max={} power={} materials={}", id, maximum, total, unique);
      }
      if (DebugCommands.validate(server) != 0)
        throw new IllegalStateException("Validation warnings");
      if (!DebugCommands.missing(server).isEmpty())
        throw new IllegalStateException(
            "Remaining unsupported registry entries: " + DebugCommands.missing(server));
      RitualsNotRolls.LOGGER.info(
          "OPTIONAL DEFINITIONS GATE PASS: {} requested, {} active total, {} excluded; zero missing"
              + " or unresolved",
          manifest.size(),
          Definitions.SERVER.enchantments().size(),
          Definitions.SERVER.disabled().size());
    } catch (Exception exception) {
      RitualsNotRolls.LOGGER.error("OPTIONAL DEFINITIONS GATE FAILED", exception);
    } finally {
      server.halt(false);
    }
  }
}
