package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.menu.BinderMenu;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Two binder leaves with six card pockets apiece; contents remain server-owned. */
public final class BinderScreen extends FittedScreen<BinderMenu> {
  private static final int CARD_WIDTH = 72;
  private static final int CARD_HEIGHT = 56;
  private Button auto;
  private Button filter;
  private Button back;
  private Button next;

  public BinderScreen(BinderMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 366, 304);
  }

  private static net.minecraft.network.chat.MutableComponent label(String key, Object... args) {
    return Component.translatable("gui.ritualsnotrolls.binder." + key, args);
  }

  @Override
  protected void init() {
    super.init();
    back =
        addRenderableWidget(
            new PageButton(
                leftPos + 26,
                topPos + 241,
                false,
                b -> action("page", Integer.toString(menu.viewPage() - 1)),
                true));
    next =
        addRenderableWidget(
            new PageButton(
                leftPos + 318,
                topPos + 241,
                true,
                b -> action("page", Integer.toString(menu.viewPage() + 1)),
                true));
    auto = standardButton(12, 141, Component.empty(), () -> action("auto", ""));
    filter = standardButton(158, 141, Component.empty(), () -> action("filter", ""));
    standardButton(305, 49, Component.translatable("gui.done"), this::onClose);
  }

  private Button standardButton(int x, int width, Component label, Runnable action) {
    return addRenderableWidget(
        Button.builder(label, b -> action.run())
            .bounds(leftPos + x, topPos + 278, width, 20)
            .build());
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    // Leather cover, stitched edge, and two pale leaves surrounding the brass rings.
    fill(g, 2, 4, 362, 268, 0xff211b1a);
    fill(g, 5, 7, 356, 262, 0xff624735);
    fill(g, 8, 10, 350, 256, 0xff3e3029);
    for (int x = 13; x < 354; x += 9) {
      fill(g, x, 11, 4, 1, 0xffb18a53);
      fill(g, x, 264, 4, 1, 0xffb18a53);
    }
    fill(g, 14, 16, 160, 244, 0xffbcac89);
    fill(g, 15, 16, 156, 242, 0xffe8d9b4);
    fill(g, 192, 16, 160, 244, 0xffbcac89);
    fill(g, 195, 16, 156, 242, 0xffe8d9b4);
    fill(g, 174, 16, 18, 246, 0xff302c28);
    fill(g, 178, 16, 10, 246, 0xff4c4234);
    for (int y : new int[] {47, 113, 179, 241}) {
      fill(g, 167, y + 1, 32, 7, 0xff675239);
      fill(g, 166, y, 33, 4, 0xffdbc17c);
      fill(g, 168, y, 29, 1, 0xffffedb0);
      fill(g, 173, y + 4, 20, 2, 0xff96713c);
    }
    text(g, trim(title.getString(), 142), 22, 24, 0x403527);
    text(g, label("pages", menu.total(), menu.capacity()).getString(), 199, 24, 0x725436);
    fill(g, 22, 40, 142, 1, 0xffbaa576);
    fill(g, 200, 40, 143, 1, 0xffbaa576);
    auto.setMessage(label("auto", label(menu.autoCollect() ? "on" : "off")));
    filter.setMessage(label("filter", label(menu.filterDuplicates() ? "on" : "off")));
    back.visible = back.active = menu.viewPage() > 0;
    next.visible = next.active = menu.viewPage() + 1 < menu.spreads();

    var cards = menu.cards();
    for (int card = 0; card < BinderMenu.CARDS_PER_SPREAD; card++) {
      int x = cardX(card), y = cardY(card);
      boolean occupied = card < cards.size();
      boolean hovered = occupied && hit(mx, my, x, y, CARD_WIDTH, CARD_HEIGHT);
      fill(g, x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, hovered ? 0xffb3873f : 0xffb8a77e);
      fill(g, x, y, CARD_WIDTH, CARD_HEIGHT, occupied ? 0xfff6e9c9 : 0xffddd0ac);
      fill(g, x + 1, y + CARD_HEIGHT - 3, CARD_WIDTH - 2, 2, 0xffc5b893);
      fill(g, x + 1, y + 1, CARD_WIDTH - 2, 1, 0xfffff7de);
      if (!occupied) {
        fill(g, x + 30, y + 21, 12, 1, 0xffc7b892);
        fill(g, x + 35, y + 16, 1, 11, 0xffc7b892);
        continue;
      }
      var entry = cards.get(card);
      var knowledge = Knowledge.data(entry.page());
      if (knowledge == null || knowledge.entries().isEmpty()) continue;
      String enchantment =
          Knowledge.name(knowledge.enchantment(), minecraft.level.registryAccess()).getString();
      text(g, trim(enchantment, CARD_WIDTH - 8), x + 4, y + 5, 0x493a2c);
      g.renderItem(entry.page(), leftPos + x + 8, topPos + y + 19);
      text(g, "×" + compactCount(entry.count()), x + 29, y + 23, 0x88682e);
      text(
          g, trim(affinityName(entry.page()).getString(), CARD_WIDTH - 8), x + 4, y + 40, 0x6e5534);
    }
    if (cards.isEmpty()) {
      fill(g, 31, 131, 304, 36, 0xf5f6e9c9);
      var lines = font.split(label("empty"), 278);
      for (int i = 0; i < lines.size(); i++)
        g.drawString(font, lines.get(i), leftPos + 44, topPos + 140 + i * 10, 0x715a38, false);
    }
    for (int leaf = 0; leaf < 2; leaf++) {
      String number = label("page", menu.viewPage() * 2 + leaf + 1).getString();
      text(g, number, (leaf == 0 ? 94 : 273) - font.width(number) / 2, 244, 0x725436);
    }
  }

  private static String compactCount(int count) {
    if (count >= 1_000_000)
      return String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0);
    if (count >= 10_000) return String.format(java.util.Locale.ROOT, "%.1fk", count / 1_000.0);
    return Integer.toString(count);
  }

  private Component affinityName(ItemStack page) {
    var knowledge = Knowledge.data(page);
    if (knowledge == null || knowledge.entries().isEmpty()) return label("legacy");
    var definition = Definitions.CLIENT.get(knowledge.enchantment());
    var affinity = definition == null ? null : definition.affinity(knowledge.entries().getFirst());
    return affinity == null
        ? Component.literal(knowledge.entries().getFirst())
        : affinity.name(time());
  }

  private void fill(GuiGraphics g, int x, int y, int width, int height, int color) {
    g.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, color);
  }

  private static int cardX(int card) {
    return (card < 6 ? 22 : 198) + (card % 2) * 77;
  }

  private static int cardY(int card) {
    return 54 + ((card % 6) / 2) * 63;
  }

  @Override
  protected void renderTooltip(GuiGraphics g, int mx, int my) {
    super.renderTooltip(g, mx, my);
    if (auto.isHoveredOrFocused()) {
      g.renderTooltip(font, label("auto_hint"), mx, my);
      return;
    }
    if (filter.isHoveredOrFocused()) {
      g.renderTooltip(font, label("filter_hint"), mx, my);
      return;
    }
    for (int card = 0; card < menu.cards().size(); card++) {
      if (!hit(mx, my, cardX(card), cardY(card), CARD_WIDTH, CARD_HEIGHT)) continue;
      var entry = menu.cards().get(card);
      var knowledge = Knowledge.data(entry.page());
      if (knowledge == null) return;
      var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
      for (Component line :
          new Component[] {
            Knowledge.name(knowledge.enchantment(), minecraft.level.registryAccess())
                .copy()
                .withStyle(ChatFormatting.GOLD),
            affinityName(entry.page()),
            label("count", entry.count()).withStyle(ChatFormatting.GRAY)
          }) lines.addAll(font.split(line, 235));
      g.renderTooltip(font, lines, mx, my);
      return;
    }
  }

  @Override
  protected boolean contentClick(double x, double y, int button) {
    if (button != 0) return false;
    for (int card = 0; card < menu.cards().size(); card++) {
      if (hit(x, y, cardX(card), cardY(card), CARD_WIDTH, CARD_HEIGHT)) {
        action("take", menu.viewRevision() + ":" + card + ":" + (hasShiftDown() ? 64 : 1));
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean contentScroll(double x, double y, double amount) {
    if (amount == 0) return false;
    int page = Math.clamp(menu.viewPage() + (amount < 0 ? 1 : -1), 0, menu.spreads() - 1);
    if (page != menu.viewPage()) action("page", Integer.toString(page));
    return true;
  }
}
