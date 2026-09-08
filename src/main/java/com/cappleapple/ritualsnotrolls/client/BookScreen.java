package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.menu.BookMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class BookScreen extends FittedScreen<BookMenu> {
  private int page;
  private Button auto;
  private PageButton back, next;
  private ResourceLocation background;

  public BookScreen(BookMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 192, 240);
  }

  @Override
  protected void init() {
    super.init();
    var override = RitualsNotRolls.id("textures/gui/knowledge_book.png");
    background =
        minecraft.getResourceManager().getResource(override).isPresent()
            ? override
            : ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    auto = button(0, 195, 94, "Auto-Add", () -> action("auto", ""));
    button(98, 195, 94, "Gather pages", () -> action("gather", ""));
    button(48, 218, 96, "Done", () -> onClose());
    back =
        addRenderableWidget(
            new PageButton(
                leftPos + 38, topPos + 156, false, b -> page = Math.max(0, page - 1), true));
    next =
        addRenderableWidget(new PageButton(leftPos + 132, topPos + 156, true, b -> page++, true));
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    var override = RitualsNotRolls.id("textures/gui/knowledge_book.png");
    background =
        minecraft.getResourceManager().getResource(override).isPresent()
            ? override
            : ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    g.blit(background, leftPos, topPos, 0, 0, 192, 192);
    var data = Knowledge.data(menu.book());
    if (data == null) return;
    var definition = Definitions.CLIENT.get(data.enchantment());
    g.renderItem(KnowledgeRenderer.enchantedBook(data.enchantment()), leftPos + 32, topPos + 16);
    text(
        g,
        trim(Knowledge.name(data.enchantment(), minecraft.level.registryAccess()).getString(), 108),
        51,
        18,
        0x403629);
    text(
        g,
        "Pages: " + data.entries().size() + " / " + Knowledge.total(data, true),
        35,
        34,
        0x725021);
    auto.setMessage(Component.literal("Auto-Add: " + (data.autoAdd() ? "On" : "Off")));
    page = Math.clamp(page, 0, Math.max(0, (data.entries().size() - 1) / 4));
    back.visible = page > 0;
    next.visible = (page + 1) * 4 < data.entries().size();
    for (int row = 0; row < 4; row++) {
      int index = page * 4 + row;
      if (index >= data.entries().size()) break;
      var affinity = definition == null ? null : definition.affinity(data.entries().get(index));
      int y = 50 + row * 24;
      if (affinity != null) {
        g.renderItem(affinity.icon(time()), leftPos + 33, topPos + y + 3);
        text(g, trim(affinity.name(time()).getString(), 88), 52, y + 3, 0x473822);
        text(g, definition.powerTier(affinity).label().getString(), 52, y + 13, 0x8b673b);
        if (hit(mx, my, 32, y, 108, 23))
          g.renderTooltip(
              font,
              affinity
                  .name(time())
                  .copy()
                  .append(" · ")
                  .append(definition.powerTier(affinity).description()),
              mx,
              my);
      } else text(g, "Legacy affinity", 35, y + 8, 0x795646);
      text(g, "×", 147, y + 7, hit(mx, my, 142, y, 17, 23) ? 0xa33b2b : 0x725021);
      if (hit(mx, my, 142, y, 17, 23))
        g.renderTooltip(font, Component.literal("Tear out this page"), mx, my);
    }
    text(g, (page + 1) + " / " + Math.max(1, (data.entries().size() + 3) / 4), 83, 159, 0x725021);
  }

  @Override
  protected boolean contentClick(double x, double y, int button) {
    var data = Knowledge.data(menu.book());
    if (data == null) return false;
    for (int row = 0; row < 4; row++)
      if (hit(x, y, 142, 50 + row * 24, 17, 23) && page * 4 + row < data.entries().size()) {
        action("tear", data.entries().get(page * 4 + row));
        return true;
      }
    return false;
  }

  @Override
  protected boolean contentScroll(double x, double y, double amount) {
    page = Math.max(0, page + (amount < 0 ? 1 : -1));
    return true;
  }

  static String format(double value) {
    return value == Math.rint(value)
        ? Long.toString((long) value)
        : java.math.BigDecimal.valueOf(value)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
  }
}
