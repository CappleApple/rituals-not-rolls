package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class DefinitionLoadingGameTests {
  private static JsonObject optional() {
    var json =
        JsonParser.parseString(Definitions.encode(Definitions.SERVER.get(RitualGameTests.SHARP)))
            .getAsJsonObject();
    json.addProperty("enchantment", "absentmod:optional_enchantment");
    json.addProperty("optional", true);
    return json;
  }

  @GameTest(template = "empty")
  public static void optionalDefinitionFollowsExactRegistryAvailability(GameTestHelper h) {
    var id = ResourceLocation.parse("absentmod:optional_enchantment");
    var input =
        Map.<ResourceLocation, JsonElement>of(
            ResourceLocation.parse("absentmod:enchantments/optional_enchantment"), optional());
    var absent = Definitions.load(input, key -> false, 1);
    h.assertTrue(
        absent.get(id) == null && !absent.configured(id),
        "Missing optional enchantment contributes no data or false exclusion");
    var present = Definitions.load(input, id::equals, 2);
    h.assertTrue(
        present.get(id) != null,
        "The same optional file activates when its exact enchantment exists");
    var removed = Definitions.load(input, key -> false, 3);
    h.assertTrue(removed.get(id) == null, "Reload cannot retain a stale optional definition");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void disabledDefinitionIsConfiguredWithoutGrantingKnowledge(GameTestHelper h) {
    var id = RitualsNotRolls.id("arcane_assembly");
    var json =
        JsonParser.parseString(
            "{\"enchantment\":\"ritualsnotrolls:arcane_assembly\",\"enabled\":false}");
    var snapshot =
        Definitions.load(
            Map.of(RitualsNotRolls.id("enchantments/arcane_assembly"), json), key -> true, 1);
    h.assertTrue(
        snapshot.configured(id) && snapshot.get(id) == null && snapshot.enchantments().isEmpty(),
        "Disabled helper is acknowledged but supplies no discovery or ritual definition");
    var previous = Definitions.SERVER;
    try {
      Definitions.SERVER = snapshot;
      h.assertTrue(
          !DebugCommands.missing(h.getLevel().getServer()).contains(id),
          "Missing command honors explicit exclusions");
      var raw = MigrationGameTests.ancient(id.toString(), 1);
      var migrated =
          net.minecraft.world.item.ItemStack.parse(h.getLevel().registryAccess(), raw)
              .orElseThrow();
      h.assertTrue(
          Knowledge.data(migrated) == null,
          "Disabled helper does not migrate into a knowledge page");
    } finally {
      Definitions.SERVER = previous;
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void malformedDisabledFlagDoesNotHideMissingDefinition(GameTestHelper h) {
    var json = optional();
    json.addProperty("enabled", "not a boolean");
    var id = ResourceLocation.parse("absentmod:optional_enchantment");
    var snapshot =
        Definitions.load(Map.of(RitualsNotRolls.id("enchantments/bad"), json), key -> true, 1);
    h.assertTrue(
        !snapshot.configured(id), "Invalid enable flag cannot silently mark a file configured");
    json.addProperty("enabled", true);
    var repaired =
        Definitions.load(Map.of(RitualsNotRolls.id("enchantments/bad"), json), key -> true, 2);
    h.assertTrue(repaired.get(id) != null, "Complete enabled override activates normally");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void standaloneDefaultsExcludeOnlyInternalEntries(GameTestHelper h) {
    h.assertTrue(
        Definitions.SERVER.enchantments().size() == 42,
        "Optional mods are not required by the standalone mod");
    h.assertTrue(
        Definitions.SERVER
            .disabled()
            .equals(
                Set.of(
                    RitualsNotRolls.id("arcane_assembly"),
                    ResourceLocation.parse("notenoughtrials:storm_front_marker"))),
        "Both known internal entries are explicitly excluded");
    h.assertTrue(
        DebugCommands.validate(h.getLevel().getServer()) == 0,
        "No unresolved optional items or enchantments without other mods");
    h.succeed();
  }
}
