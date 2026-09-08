package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class RitualButton extends Button {
  public RitualButton(int x, int y, int width, Component label, OnPress press) {
    super(x, y, width, 18, label, press, DEFAULT_NARRATION);
  }

  @Override
  protected void renderWidget(GuiGraphics g, int x, int y, float partial) {
    g.blitSprite(
        RitualsNotRolls.id(
            !active
                ? "common/button_disabled"
                : (isHovered()
                        || isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard())
                    ? "common/button_highlighted"
                    : "common/button"),
        getX(),
        getY(),
        getWidth(),
        getHeight());
    renderString(g, Minecraft.getInstance().font, active ? 0xffeeeeee : 0xff888888);
  }
}
