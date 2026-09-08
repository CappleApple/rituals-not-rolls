package com.cappleapple.ritualsnotrolls.knowledge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record MaterialTooltip(ResourceLocation enchantment, String entry)
    implements TooltipComponent {}
