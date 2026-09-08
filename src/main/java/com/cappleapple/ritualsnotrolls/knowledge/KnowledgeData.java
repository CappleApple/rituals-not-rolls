package com.cappleapple.ritualsnotrolls.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

public record KnowledgeData(ResourceLocation enchantment, List<String> entries, boolean autoAdd) {
  public static final Codec<KnowledgeData> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC
                          .fieldOf("enchantment")
                          .forGetter(KnowledgeData::enchantment),
                      Codec.STRING.listOf().fieldOf("entries").forGetter(KnowledgeData::entries),
                      Codec.BOOL
                          .optionalFieldOf("auto_add", true)
                          .forGetter(KnowledgeData::autoAdd))
                  .apply(i, KnowledgeData::new));

  public KnowledgeData {
    entries = List.copyOf(new LinkedHashSet<>(entries));
  }

  public KnowledgeData add(String entry) {
    if (entries.contains(entry)) return this;
    var out = new ArrayList<>(entries);
    out.add(entry);
    return new KnowledgeData(enchantment, out, autoAdd);
  }

  public KnowledgeData remove(String entry) {
    var out = new ArrayList<>(entries);
    out.remove(entry);
    return new KnowledgeData(enchantment, out, autoAdd);
  }

  public KnowledgeData toggle() {
    return new KnowledgeData(enchantment, entries, !autoAdd);
  }
}
