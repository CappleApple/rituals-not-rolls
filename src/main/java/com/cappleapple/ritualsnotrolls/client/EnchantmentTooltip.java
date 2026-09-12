package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.ClientConfig;
import com.cappleapple.ritualsnotrolls.data.EnchantmentDescriptionKeys;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;

public final class EnchantmentTooltip {
  public static List<Component> lines(ResourceLocation id) {
    var level = Minecraft.getInstance().level;
    var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id);
    var name = Knowledge.name(id, level.registryAccess());
    boolean curse = holder.map(h -> h.is(EnchantmentTags.CURSE)).orElse(false);
    int color = ClientConfig.tooltipColor(curse);
    String nameKey =
        name.getContents() instanceof TranslatableContents translated ? translated.getKey() : null;
    // Library rows describe an enchantment generally; level-only entries use level I.
    String description =
        EnchantmentDescriptionKeys.resolve(id, nameKey, 1, Language.getInstance()::has);
    if (description == null) description = "ritualsnotrolls.description.unavailable";
    return List.of(
        name.copy().withStyle(style -> style.withColor(color)),
        Component.translatable(description).withStyle(ChatFormatting.WHITE));
  }
}
