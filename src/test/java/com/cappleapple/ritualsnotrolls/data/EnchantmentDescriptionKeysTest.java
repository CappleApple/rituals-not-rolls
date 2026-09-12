package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EnchantmentDescriptionKeysTest {
  private static final ResourceLocation ID =
      ResourceLocation.fromNamespaceAndPath("test", "magic/ward");

  @Test
  void resourceIdsKeepTheirNamespaceAndSlashesAndWinOverCustomNames() {
    var keys = Set.of("enchantment.test.magic/ward.desc", "custom.name.desc");
    assertEquals(
        "enchantment.test.magic/ward.desc",
        EnchantmentDescriptionKeys.resolve(ID, "custom.name", 1, keys::contains));
  }

  @Test
  void suffixAndLevelPrecedenceMatchesEnchantmentDescriptions() {
    var keys =
        Set.of(
            "enchantment.test.magic/ward.desc.3",
            "enchantment.test.magic/ward.description",
            "enchantment.test.magic/ward.info");
    assertEquals(
        "enchantment.test.magic/ward.desc.3",
        EnchantmentDescriptionKeys.resolve(ID, null, 3, keys::contains));
    assertEquals(
        "enchantment.test.magic/ward.description",
        EnchantmentDescriptionKeys.resolve(ID, null, 1, keys::contains));
    assertEquals(
        "enchantment.test.magic/ward.desc",
        EnchantmentDescriptionKeys.resolve(
            ID,
            null,
            3,
            key -> keys.contains(key) || key.equals("enchantment.test.magic/ward.desc")));
  }

  @Test
  void customNameInfoAndMissingDescriptionsAreHandled() {
    var keys = Set.of("custom.name.info.1");
    assertEquals(
        "custom.name.info.1",
        EnchantmentDescriptionKeys.resolve(ID, "custom.name", 1, keys::contains));
    assertNull(EnchantmentDescriptionKeys.resolve(ID, null, 1, keys::contains));
    assertNull(EnchantmentDescriptionKeys.resolve(ID, "custom.name", 2, keys::contains));
  }
}
