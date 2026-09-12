package com.cappleapple.ritualsnotrolls.data;

import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Translation-key order shared with Enchantment Descriptions for Minecraft 1.21.1. */
public final class EnchantmentDescriptionKeys {
  private static final String[] SUFFIXES = {"desc", "description", "info"};

  @Nullable
  public static String resolve(
      ResourceLocation id, @Nullable String nameKey, int level, Predicate<String> exists) {
    String registryBase = "enchantment." + id.getNamespace() + "." + id.getPath();
    String result = resolveBase(registryBase, level, exists);
    if (result == null && nameKey != null && !nameKey.equals(registryBase)) {
      result = resolveBase(nameKey, level, exists);
    }
    return result;
  }

  @Nullable
  private static String resolveBase(String base, int level, Predicate<String> exists) {
    for (String suffix : SUFFIXES) {
      String key = base + "." + suffix;
      if (exists.test(key)) return key;
      if (exists.test(key + "." + level)) return key + "." + level;
    }
    return null;
  }
}
