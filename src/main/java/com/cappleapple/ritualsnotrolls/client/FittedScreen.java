package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.network.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fits the Minecraft workstation to small GUI resolutions without changing the user's GUI scale
 * setting.
 */
public abstract class FittedScreen<M extends AbstractContainerMenu>
    extends AbstractContainerScreen<M> {
  private double fit = 1;
  private int sequence;

  protected FittedScreen(M menu, Inventory inventory, Component title, int w, int h) {
    super(menu, inventory, title);
    imageWidth = w;
    imageHeight = h;
  }

  @Override
  protected void init() {
    fit =
        Math.min(
            1, Math.min(width / (double) (imageWidth + 12), height / (double) (imageHeight + 12)));
    width = (int) Math.ceil(width / fit);
    height = (int) Math.ceil(height / fit);
    super.init();
  }

  protected void action(String action, String value) {
    PacketDistributor.sendToServer(
        new Networking.Action(menu.containerId, ++sequence, action, value));
  }

  protected RitualButton button(int x, int y, int width, String label, Runnable action) {
    return addRenderableWidget(
        new RitualButton(
            leftPos + x, topPos + y, width, Component.literal(label), b -> action.run()));
  }

  protected void sprite(GuiGraphics g, String name, int x, int y, int w, int h) {
    g.blitSprite(RitualsNotRolls.id(name), leftPos + x, topPos + y, w, h);
  }

  protected void text(GuiGraphics g, String text, int x, int y, int color) {
    g.drawString(font, text, leftPos + x, topPos + y, color, false);
  }

  protected String trim(String s, int width) {
    return font.plainSubstrByWidth(s, width);
  }

  protected long time() {
    return minecraft.level == null ? 0 : minecraft.level.getGameTime();
  }

  protected boolean hit(double x, double y, int bx, int by, int w, int h) {
    return x >= leftPos + bx && x < leftPos + bx + w && y >= topPos + by && y < topPos + by + h;
  }

  protected boolean contentClick(double x, double y, int button) {
    return false;
  }

  protected boolean contentScroll(double x, double y, double amount) {
    return false;
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    g.pose().pushPose();
    g.pose().scale((float) fit, (float) fit, 1);
    super.render(g, (int) (x / fit), (int) (y / fit), partial);
    renderTooltip(g, (int) (x / fit), (int) (y / fit));
    g.pose().popPose();
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    x /= fit;
    y /= fit;
    return contentClick(x, y, button) || super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    return super.mouseReleased(x / fit, y / fit, button);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return super.mouseDragged(x / fit, y / fit, button, dx / fit, dy / fit);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    return contentScroll(x / fit, y / fit, dy) || super.mouseScrolled(x / fit, y / fit, dx, dy);
  }

  @Override
  public void mouseMoved(double x, double y) {
    super.mouseMoved(x / fit, y / fit);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {}
}
