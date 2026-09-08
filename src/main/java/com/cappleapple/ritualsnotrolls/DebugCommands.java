package com.cappleapple.ritualsnotrolls;

import static net.minecraft.commands.Commands.*;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class DebugCommands {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        literal("ritual")
            .requires(s -> s.hasPermission(2))
            .then(literal("missing").executes(c -> printMissing(c.getSource())))
            .then(
                literal("definitions")
                    .executes(
                        c -> {
                          for (var d : Definitions.SERVER.enchantments().values())
                            c.getSource()
                                .sendSuccess(
                                    () ->
                                        Component.literal(
                                            d.enchantment()
                                                + ": "
                                                + d.materials().size()
                                                + " affinities, levels "
                                                + d.thresholds().keySet()),
                                    false);
                          return Definitions.SERVER.enchantments().size();
                        }))
            .then(
                literal("givepage")
                    .then(
                        argument("enchantment", ResourceLocationArgument.id())
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggestResource(
                                        Definitions.SERVER.enchantments().keySet(), b))
                            .then(
                                argument("entry", StringArgumentType.word())
                                    .suggests(
                                        (c, b) -> {
                                          var d =
                                              Definitions.SERVER.get(
                                                  ResourceLocationArgument.getId(c, "enchantment"));
                                          return SharedSuggestionProvider.suggest(
                                              d == null
                                                  ? java.util.List.of()
                                                  : d.materials().stream()
                                                      .map(a -> a.id())
                                                      .toList(),
                                              b);
                                        })
                                    .executes(
                                        c -> {
                                          var id = ResourceLocationArgument.getId(c, "enchantment");
                                          String entry = StringArgumentType.getString(c, "entry");
                                          var d = Definitions.SERVER.get(id);
                                          if (d == null || d.affinity(entry) == null) {
                                            c.getSource()
                                                .sendFailure(Component.literal("Unknown affinity"));
                                            return 0;
                                          }
                                          Knowledge.returnLoose(
                                              c.getSource().getPlayerOrException(),
                                              Knowledge.page(id, entry));
                                          return 1;
                                        }))))
            .then(
                literal("inspectbook")
                    .executes(
                        c -> {
                          var d =
                              Knowledge.data(
                                  c.getSource().getPlayerOrException().getMainHandItem());
                          c.getSource()
                              .sendSuccess(
                                  () ->
                                      Component.literal(
                                          d == null
                                              ? "Not a knowledge item"
                                              : d.toString()
                                                  + "; total "
                                                  + Knowledge.total(d, false)),
                                  false);
                          return 1;
                        }))
            .then(
                literal("network")
                    .executes(
                        c -> {
                          var p = c.getSource().getPlayerOrException();
                          var n =
                              RitualNetwork.scan(p.serverLevel(), p.blockPosition(), true, true);
                          c.getSource()
                              .sendSuccess(
                                  () ->
                                      Component.literal(
                                          "Around player: "
                                              + n.shelves().size()
                                              + " shelves; "
                                              + n.pedestals().size()
                                              + " pedestals; "
                                              + n.storage().size()
                                              + " extractable storage slots; knowledge "
                                              + n.knowledge()),
                                  false);
                          for (var pedestal : n.pedestals())
                            c.getSource()
                                .sendSuccess(
                                    () ->
                                        Component.literal(
                                            pedestal.pos()
                                                + " "
                                                + pedestal.stack()
                                                + " catalyst="
                                                + pedestal.catalyst()),
                                    false);
                          return 1;
                        }))
            .then(
                literal("validate")
                    .executes(
                        c -> {
                          int warnings = validate(c.getSource().getServer());
                          c.getSource()
                              .sendSuccess(
                                  () ->
                                      Component.literal(
                                          "Validated "
                                              + Definitions.SERVER.enchantments().size()
                                              + " definitions; "
                                              + warnings
                                              + " warnings (see log). Use /reload to reload"
                                              + " datapacks."),
                                  false);
                          return warnings == 0 ? 1 : 0;
                        })));
  }

  public static java.util.List<net.minecraft.resources.ResourceLocation> missing(
      MinecraftServer server) {
    return server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet().stream()
        .filter(id -> !Definitions.SERVER.configured(id))
        .sorted(java.util.Comparator.comparing(Object::toString))
        .toList();
  }

  private static int printMissing(CommandSourceStack source) {
    var ids = missing(source.getServer());
    source.sendSuccess(
        () ->
            Component.literal(
                ids.size()
                    + " registered enchantments have no ritual definition or explicit exclusion:"),
        false);
    for (var id : ids)
      source.sendSuccess(
          () ->
              Component.literal(
                  id
                      + " -> data/"
                      + id.getNamespace()
                      + "/ritual_enchanting/enchantments/"
                      + id.getPath()
                      + ".json"),
          false);
    return ids.size();
  }

  public static int validate(MinecraftServer server) {
    int warnings = 0;
    for (var d : Definitions.SERVER.enchantments().values()) {
      server
          .registryAccess()
          .registryOrThrow(Registries.ENCHANTMENT)
          .getHolder(d.enchantment())
          .ifPresent(h -> Knowledge.NAMES.put(d.enchantment(), h.value().description()));
      if (!server
          .registryAccess()
          .registryOrThrow(Registries.ENCHANTMENT)
          .containsKey(d.enchantment())) {
        RitualsNotRolls.LOGGER.warn("Unresolved ritual enchantment {}", d.enchantment());
        warnings++;
      }
      for (var a : d.materials())
        if (a.representatives().isEmpty()) {
          RitualsNotRolls.LOGGER.warn(
              "Ritual {} affinity {} has no resolved items", d.enchantment(), a.id());
          warnings++;
        }
    }
    return warnings;
  }
}
