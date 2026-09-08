package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.MaterialTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public record MaterialTooltipRenderer(MaterialTooltip tooltip) implements ClientTooltipComponent {
  private Affinity affinity() {
    var d = Definitions.CLIENT.get(tooltip.enchantment());
    return d == null ? null : d.affinity(tooltip.entry());
  }

  private long time() {
    return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
  }

  @Override
  public int getHeight() {
    return affinity() == null ? 0 : 20;
  }

  @Override
  public int getWidth(Font font) {
    var a = affinity();
    return a == null
        ? 0
        : 24
            + Math.max(
                font.width(a.name(time())),
                font.width(
                    Definitions.CLIENT.get(tooltip.enchantment()).powerTier(a).description()));
  }

  @Override
  public void renderImage(Font font, int x, int y, GuiGraphics g) {
    var a = affinity();
    if (a == null) return;
    g.renderItem(a.icon(time()), x + 1, y + 1);
    g.drawString(font, a.name(time()), x + 23, y + 1, 0xffaaaaaa, false);
    g.drawString(
        font,
        Definitions.CLIENT.get(tooltip.enchantment()).powerTier(a).description(),
        x + 23,
        y + 10,
        0xffffaa00,
        false);
  }
}
