package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** A searchable reference for the physical library, with no ritual controls or inventory slots. */
public final class RitualScreen extends FittedScreen<RitualMenu> {
  private EditBox search;
  private RitualButton browserPrevious, browserNext, detailPrevious, detailNext;
  private ResourceLocation focused;
  private int browserPage, detailPage;
  private CompoundTag cachedState;
  private Definitions.Snapshot cachedDefinitions;
  private String cachedQuery;
  private List<CompoundTag> cachedRows = List.of();
  private final Map<String, Set<String>> matchingMaterials = new HashMap<>();

  public RitualScreen(RitualMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 380, 252);
  }

  @Override
  protected void init() {
    super.init();
    search =
        new EditBox(
            font,
            leftPos + 15,
            topPos + 32,
            111,
            16,
            Component.literal("Search known enchantments or material items"));
    search.setHint(Component.literal("Enchant / item..."));
    search.setBordered(false);
    search.setResponder(
        s -> {
          browserPage = 0;
          detailPage = 0;
        });
    addRenderableWidget(search);
    browserPrevious = button(12, 224, 24, "<", () -> browserPage = Math.max(0, browserPage - 1));
    browserNext = button(104, 224, 24, ">", () -> browserPage++);
    detailPrevious = button(313, 198, 22, "<", () -> detailPage = Math.max(0, detailPage - 1));
    detailNext = button(338, 198, 22, ">", () -> detailPage++);
  }

  private String name(ResourceLocation id) {
    return Knowledge.name(id, minecraft.level.registryAccess()).getString();
  }

  private List<CompoundTag> browser() {
    String query = search.getValue().strip().toLowerCase(Locale.ROOT);
    if (cachedState == menu.clientState
        && cachedDefinitions == Definitions.CLIENT
        && query.equals(cachedQuery)) return cachedRows;
    cachedState = menu.clientState;
    cachedDefinitions = Definitions.CLIENT;
    cachedQuery = query;
    matchingMaterials.clear();
    // Material groups can contain many items. Resolve matches on query/library updates, not each
    // frame.
    cachedRows =
        menu.clientState.getList("knowledge", Tag.TAG_COMPOUND).stream()
            .map(t -> (CompoundTag) t)
            .filter(row -> matches(row, query))
            .sorted(Comparator.comparing(t -> name(ResourceLocation.parse(t.getString("id")))))
            .toList();
    return cachedRows;
  }

  private boolean matches(CompoundTag row, String query) {
    var id = ResourceLocation.parse(row.getString("id"));
    if (query.isEmpty() || contains(name(id), query) || contains(id.toString(), query)) return true;
    var definition = Definitions.CLIENT.get(id);
    if (definition == null) return false;
    Set<String> materials = new HashSet<>();
    for (var entry : row.getList("entries", Tag.TAG_STRING)) {
      var affinity = definition.affinity(entry.getAsString());
      if (affinity != null && matchesMaterial(affinity, query)) materials.add(affinity.id());
    }
    if (materials.isEmpty()) return false;
    matchingMaterials.put(id.toString(), Set.copyOf(materials));
    return true;
  }

  private boolean matchesMaterial(Affinity affinity, String query) {
    if (affinity.item().map(id -> contains(id.toString(), query)).orElse(false)
        || affinity.tag().map(id -> contains(id.toString(), query)).orElse(false)) return true;
    return affinity.representatives().stream()
        .anyMatch(
            stack ->
                contains(stack.getHoverName().getString(), query)
                    || contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), query));
  }

  private boolean contains(String text, String query) {
    return text.toLowerCase(Locale.ROOT).contains(query);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    sprite(g, "ritual/background", 0, 0, 380, 252);
    text(g, "Nearby knowledge", 12, 13, 0x403629);
    text(
        g,
        menu.clientState.getInt("shelves")
            + " bookshelves · "
            + menu.clientState.getInt("pedestals")
            + " pedestals",
        145,
        13,
        0x51412f);
    sprite(g, "common/search", 12, 28, 117, 20);
    var browser = browser();
    browserPage = Math.clamp(browserPage, 0, Math.max(0, (browser.size() - 1) / 8));
    browserPrevious.active = browserPage > 0;
    browserNext.active = (browserPage + 1) * 8 < browser.size();
    detailPrevious.active = detailNext.active = false;
    if (browser.stream()
        .noneMatch(r -> ResourceLocation.parse(r.getString("id")).equals(focused))) {
      focused =
          browser.isEmpty() ? null : ResourceLocation.parse(browser.getFirst().getString("id"));
      detailPage = 0;
    }
    for (int row = 0; row < 8; row++) {
      int index = browserPage * 8 + row;
      if (index >= browser.size()) break;
      var data = browser.get(index);
      var id = ResourceLocation.parse(data.getString("id"));
      int y = 52 + row * 21;
      sprite(
          g,
          id.equals(focused) ? "ritual/selected_enchantment" : "ritual/enchantment_row",
          12,
          y,
          117,
          20);
      g.renderItem(KnowledgeRenderer.enchantedBook(id), leftPos + 14, topPos + y + 2);
      text(g, trim(name(id), 91), 33, y + 2, 0x453c31);
      text(
          g,
          data.getList("entries", Tag.TAG_STRING).size() + " known affinities",
          33,
          y + 11,
          0x72644f);
    }
    text(g, (browserPage + 1) + " / " + Math.max(1, (browser.size() + 7) / 8), 53, 229, 0x594b3b);
    sprite(g, "ritual/knowledge_browser", 138, 28, 230, 194);
    if (browser.isEmpty()) {
      boolean emptyLibrary = menu.clientState.getList("knowledge", Tag.TAG_COMPOUND).isEmpty();
      text(g, emptyLibrary ? "No nearby knowledge" : "No matching knowledge", 149, 46, 0x6a563a);
      g.drawWordWrap(
          font,
          Component.literal(
              emptyLibrary
                  ? "Place knowledge pages or books in nearby bookshelves to read their materials"
                      + " and powers here."
                  : "Try another enchantment or material item."),
          leftPos + 149,
          topPos + 66,
          204,
          0x6a563a);
    } else {
      var row =
          browser.stream()
              .filter(r -> r.getString("id").equals(focused.toString()))
              .findFirst()
              .orElseThrow();
      details(g, row);
    }
    var state = menu.clientState;
    text(g, state.getInt("xp_catalysts") + " XP catalysts", 145, 226, 0x385938);
    String xp =
        BookScreen.format(state.getDouble("xp_levels"))
            + " levels = "
            + state.getLong("xp_cost")
            + " XP";
    text(
        g,
        xp,
        145,
        238,
        state.getLong("xp_cost") > state.getInt("xp_available") ? 0x993f32 : 0x594b3b);
    if (hit(mx, my, 141, 224, 225, 25))
      g.renderTooltip(
          font,
          Component.literal(
              "Available: "
                  + state.getInt("xp_available")
                  + " XP. Paid only on a successful ritual."),
          mx,
          my);
  }

  private void details(GuiGraphics g, CompoundTag row) {
    var def = Definitions.CLIENT.get(focused);
    if (def == null) return;
    double highest = def.materials().stream().mapToDouble(Affinity::power).max().orElseThrow();
    text(g, trim(name(focused), 204), 149, 38, 0x433521);
    text(
        g,
        Component.translatable(
                "ritualsnotrolls.potential_power", powerLabel(row.getDouble("power"), highest))
            .getString(),
        149,
        51,
        0x684d29);
    text(
        g,
        menu.clientState.getBoolean("sacrifice")
            ? "Marked items are consumed"
            : "Materials remain intact",
        149,
        64,
        0x766344);
    var matching = matchingMaterials.get(row.getString("id"));
    var affinities =
        row.getList("affinities", Tag.TAG_COMPOUND).stream()
            .map(t -> (CompoundTag) t)
            .filter(a -> matching == null || matching.contains(a.getString("id")))
            .toList();
    detailPage = Math.clamp(detailPage, 0, Math.max(0, (affinities.size() - 1) / 4));
    detailPrevious.active = detailPage > 0;
    detailNext.active = (detailPage + 1) * 4 < affinities.size();
    for (int r = 0; r < 4; r++) {
      int index = detailPage * 4 + r;
      if (index >= affinities.size()) break;
      var status = affinities.get(index);
      var a = def.affinity(status.getString("id"));
      if (a == null) continue;
      int y = 81 + r * 28;
      sprite(g, "ritual/material_row", 147, y, 214, 26);
      g.renderItem(a.icon(time()), leftPos + 150, topPos + y + 5);
      text(g, trim(a.name(time()).getString(), 182), 170, y + 4, 0x493c2a);
      String strength =
          powerLabel(a.power(), highest)
              + " base · "
              + powerLabel(status.getDouble("effective"), highest)
              + " effective";
      float scale = Math.min(1, 182f / Math.max(1, font.width(strength)));
      g.pose().pushPose();
      g.pose().translate(leftPos + 170, topPos + y + 14, 0);
      g.pose().scale(scale, scale, 1);
      g.drawString(font, strength, 0, 0, 0x766344, false);
      g.pose().popPose();
    }
    text(
        g, (detailPage + 1) + " / " + Math.max(1, (affinities.size() + 3) / 4), 149, 204, 0x725021);
  }

  private String powerLabel(double power, double highest) {
    return (power < 0 ? "−" : "")
        + PowerTier.relative(Math.abs(power), highest).label().getString();
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (search != null
        && search.isFocused()
        && minecraft.options.keyInventory.isActiveAndMatches(
            InputConstants.getKey(keyCode, scanCode))) {
      // Keep editing shortcuts and character input available without invoking container close.
      search.keyPressed(keyCode, scanCode, modifiers);
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  @Override
  protected boolean contentClick(double x, double y, int button) {
    if (!hit(x, y, 12, 28, 117, 20)) {
      search.setFocused(false);
      if (getFocused() == search) setFocused(null);
    } else if (button == 1) {
      search.setValue("");
      setFocused(search);
      search.setFocused(true);
      return true;
    } else if (!search.isMouseOver(x, y)) {
      // The decorative padding belongs to the search bar too.
      setFocused(search);
      search.setFocused(true);
      return true;
    }
    var list = browser();
    for (int row = 0; row < 8; row++)
      if (hit(x, y, 12, 52 + row * 21, 117, 20) && browserPage * 8 + row < list.size()) {
        focused = ResourceLocation.parse(list.get(browserPage * 8 + row).getString("id"));
        detailPage = 0;
        return true;
      }
    return false;
  }

  @Override
  protected boolean contentScroll(double x, double y, double amount) {
    if (hit(x, y, 8, 48, 124, 174)) browserPage = Math.max(0, browserPage + (amount < 0 ? 1 : -1));
    else detailPage = Math.max(0, detailPage + (amount < 0 ? 1 : -1));
    return true;
  }
}
