package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.client.*;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;

/** Opt-in visual check of actual tooltip components and screens; excluded from the JAR. */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class PowerTooltipClientSmoke {
  private static int ticks, stage, wait, tableTooltips;

  private static void next(int value) {
    stage = value;
    wait = 0;
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
  }

  private static void hover(int x, int y, int imageWidth, int imageHeight) {
    var mc = Minecraft.getInstance();
    var window = mc.getWindow();
    double fit =
        Math.min(
            1,
            Math.min(
                window.getGuiScaledWidth() / (double) (imageWidth + 12),
                window.getGuiScaledHeight() / (double) (imageHeight + 12)));
    double gx = ((Math.ceil(window.getGuiScaledWidth() / fit) - imageWidth) / 2 + x) * fit;
    double gy = ((Math.ceil(window.getGuiScaledHeight() / fit) - imageHeight) / 2 + y) * fit;
    GLFW.glfwSetCursorPos(
        window.getWindow(),
        gx * window.getScreenWidth() / window.getGuiScaledWidth(),
        gy * window.getScreenHeight() / window.getGuiScaledHeight());
  }

  @SubscribeEvent
  public static void tooltip(RenderTooltipEvent.Pre event) {
    if (Boolean.getBoolean("ritualsnotrolls.powerTooltipSmoke")
        && stage == 5
        && Minecraft.getInstance().screen instanceof RitualScreen) tableTooltips++;
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.powerTooltipSmoke")) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      if (ticks > 1800)
        throw new IllegalStateException("Tooltip client check timed out at " + stage);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        var mods = new net.neoforged.neoforge.client.gui.ModListScreen(mc.screen);
        mc.setScreen(mods);
        var list =
            mods.children().stream()
                .filter(w -> w instanceof net.neoforged.neoforge.client.gui.widget.ModListWidget)
                .map(w -> (net.neoforged.neoforge.client.gui.widget.ModListWidget) w)
                .findFirst()
                .orElseThrow();
        var entry =
            list.children().stream()
                .filter(e -> e.getInfo().getModId().equals(RitualsNotRolls.ID))
                .findFirst()
                .orElseThrow();
        mods.setSelected(entry);
        if (!entry.getInfo().getLogoFile().orElse("").equals("logo.png")
            || entry.getInfo().getLogoBlur())
          throw new IllegalStateException("Incorrect mod logo metadata");
        next(11);
      } else if (stage == 11 && wait > 25) {
        capture("mod-icon");
        RitualsNotRolls.LOGGER.info(
            "MOD ICON CHECK: native Mods screen selected ritual artwork with nearest-neighbor"
                + " filtering");
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Tooltip QA", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 40) {
        mc.player.connection.sendCommand("ritualtest setup");
        mc.player.connection.sendCommand("ritualtest book");
        next(2);
      } else if (stage == 2 && mc.screen instanceof BookScreen && wait > 20) {
        hover(100, 65, 192, 240);
        next(3);
      } else if (stage == 3 && wait > 20) {
        capture("power-tiers-book");
        mc.player.closeContainer();
        mc.player.connection.sendCommand("ritualtest ritual");
        next(4);
      } else if (stage == 4 && mc.screen instanceof RitualScreen && wait > 30) {
        mc.getToasts().clear();
        mc.screen.mouseClicked(
            (mc.getWindow().getGuiScaledWidth() - 380) / 2.0 + 65,
            (mc.getWindow().getGuiScaledHeight() - 252) / 2.0 + 52 + 3 * 21 + 10,
            0);
        hover(205, 95, 380, 252);
        tableTooltips = 0;
        next(5);
      } else if (stage == 5 && wait > 30) {
        if (tableTooltips != 0)
          throw new IllegalStateException("Material hover still renders a tooltip");
        capture("power-tiers-table-hover");
        RitualsNotRolls.LOGGER.info(
            "POWER TOOLTIP CHECK: table material hover produced zero tooltips");
        checkItemSearch((RitualScreen) mc.screen);
        checkFocusAndRebound((RitualScreen) mc.screen);
        mc.player.connection.sendCommand("ritualtest ritual");
        next(8);
      } else if (stage == 8 && mc.screen instanceof RitualScreen screen && wait > 20) {
        click(screen, 50, 38);
        for (char c : "diamond".toCharArray()) screen.charTyped(c, 0);
        if (!search(screen).getValue().equals("diamond"))
          throw new IllegalStateException("Item query could not be typed");
        next(9);
      } else if (stage == 9 && wait > 20) {
        capture("table-item-search");
        var screen = (RitualScreen) mc.screen;
        click(screen, 135, 15);
        var key = mc.options.keyInventory.getKey();
        screen.keyPressed(key.getValue(), 0, 0);
        if (mc.screen != null)
          throw new IllegalStateException("Unfocused inventory key did not close table");
        RitualsNotRolls.LOGGER.info(
            "TABLE KEYBOARD CHECK: unfocused default inventory key closes with item query"
                + " retained");
        mc.player.connection.sendCommand("ritualtest ritual");
        next(10);
      } else if (stage == 10 && mc.screen instanceof RitualScreen screen && wait > 20) {
        click(screen, 50, 38);
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        if (mc.screen != null)
          throw new IllegalStateException("Escape did not close focused search");
        mc.setScreen(new TierScreen());
        next(6);
      } else if (stage == 6 && wait > 30) {
        capture("power-tiers-pages");
        RitualsNotRolls.LOGGER.info(
            "POWER_TOOLTIP_CLIENT_COMPLETE: all five native page tooltip tiers rendered");
        next(7);
      } else if (stage == 7 && wait > 40) mc.stop();
    } catch (Exception failure) {
      RitualsNotRolls.LOGGER.error("POWER_TOOLTIP_CLIENT_FAILED", failure);
      mc.stop();
    }
  }

  private static net.minecraft.client.gui.components.EditBox search(RitualScreen screen) {
    return screen.children().stream()
        .filter(w -> w instanceof net.minecraft.client.gui.components.EditBox)
        .map(w -> (net.minecraft.client.gui.components.EditBox) w)
        .findFirst()
        .orElseThrow();
  }

  private static void click(RitualScreen screen, double x, double y) {
    click(screen, x, y, 0);
  }

  private static void click(RitualScreen screen, double x, double y, int button) {
    var window = Minecraft.getInstance().getWindow();
    double fit =
        Math.min(
            1, Math.min(window.getGuiScaledWidth() / 392.0, window.getGuiScaledHeight() / 264.0));
    double px = ((Math.ceil(window.getGuiScaledWidth() / fit) - 380) / 2 + x) * fit;
    double py = ((Math.ceil(window.getGuiScaledHeight() / fit) - 252) / 2 + y) * fit;
    screen.mouseClicked(px, py, button);
    screen.mouseReleased(px, py, button);
  }

  @SuppressWarnings("unchecked")
  private static List<net.minecraft.nbt.CompoundTag> results(RitualScreen screen, String query)
      throws ReflectiveOperationException {
    search(screen).setValue(query);
    var method = RitualScreen.class.getDeclaredMethod("browser");
    method.setAccessible(true);
    return (List<net.minecraft.nbt.CompoundTag>) method.invoke(screen);
  }

  private static void checkItemSearch(RitualScreen screen) throws ReflectiveOperationException {
    var menu = screen.getMenu();
    var original = menu.clientState;
    var definitions = Definitions.CLIENT;
    var sharp = net.minecraft.resources.ResourceLocation.withDefaultNamespace("sharpness");
    try {
      var matches = results(screen, "  DiAmOnD  ");
      if (matches.stream().noneMatch(r -> r.getString("id").equals(sharp.toString()))
          || matches.stream()
              .anyMatch(r -> r.getString("id").equals("minecraft:bane_of_arthropods")))
        throw new IllegalStateException("Known item name search returned wrong enchantments");
      if (!matches.equals(results(screen, "minecraft:diamond")))
        throw new IllegalStateException("Item ID and localized name disagree");
      if (results(screen, "sharpness").size() != 1
          || results(screen, "no_such_material_qa").size() != 0)
        throw new IllegalStateException("Enchantment/empty search regression");
      var limited = original.copy();
      for (var entry : limited.getList("knowledge", 10)) {
        var row = (net.minecraft.nbt.CompoundTag) entry;
        if (row.getString("id").equals(sharp.toString())) {
          row.getList("entries", 8).removeIf(t -> t.getAsString().equals("diamond"));
          row.getList("affinities", 10)
              .removeIf(t -> ((net.minecraft.nbt.CompoundTag) t).getString("id").equals("diamond"));
        }
      }
      menu.clientState = limited;
      if (results(screen, "diamond").stream()
          .anyMatch(r -> r.getString("id").equals(sharp.toString())))
        throw new IllegalStateException("Unknown material leaked into search");
      var logs =
          new Affinity(
              "logs",
              Optional.empty(),
              Optional.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("logs")),
              10,
              1);
      var tagDef =
          new RitualDefinition(
              sharp,
              List.of(logs),
              Map.of("1", 10.0),
              List.of(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
      Definitions.CLIENT =
          new Definitions.Snapshot(
              Map.of(sharp, tagDef), definitions.rules(), definitions.revision() + 1);
      var synthetic = new net.minecraft.nbt.CompoundTag();
      var row = new net.minecraft.nbt.CompoundTag();
      row.putString("id", sharp.toString());
      var entries = new net.minecraft.nbt.ListTag();
      entries.add(net.minecraft.nbt.StringTag.valueOf("logs"));
      row.put("entries", entries);
      var rows = new net.minecraft.nbt.ListTag();
      rows.add(row);
      synthetic.put("knowledge", rows);
      menu.clientState = synthetic;
      var members = logs.representatives();
      if (members.size() < 2) throw new IllegalStateException("Missing logs tag fixture");
      for (var member : List.of(members.getFirst(), members.getLast())) {
        if (results(screen, member.getHoverName().getString()).size() != 1)
          throw new IllegalStateException("Search misses a non-displayed tag member");
        if (results(
                    screen,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(member.getItem())
                        .toString())
                .size()
            != 1) throw new IllegalStateException("Tag member ID not searchable");
      }
      RitualsNotRolls.LOGGER.info(
          "TABLE ITEM SEARCH CHECK: item names/IDs, case/whitespace, enchantment names, no matches,"
              + " known-only affinities, all tag members, and snapshot refresh passed");
    } finally {
      Definitions.CLIENT = definitions;
      menu.clientState = original;
      results(screen, "");
    }
  }

  private static void checkFocusAndRebound(RitualScreen screen) {
    var mc = Minecraft.getInstance();
    var binding = mc.options.keyInventory;
    var previous = binding.getKey();
    try {
      binding.setKey(
          com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_E));
      click(screen, 50, 38);
      search(screen).setValue("");
      screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
      screen.charTyped('e', 0);
      if (mc.screen != screen || !search(screen).getValue().equals("e"))
        throw new IllegalStateException("Focused inventory key interrupted typing");
      binding.setKey(
          com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_I));
      screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
      screen.charTyped('i', 0);
      if (mc.screen != screen || !search(screen).getValue().equals("ei"))
        throw new IllegalStateException("Focused rebound inventory key interrupted typing");
      search(screen).setValue("");
      for (int scale : List.of(2, 4)) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
        for (double[] point :
            List.of(new double[] {50, 38}, new double[] {13, 29}, new double[] {128, 47})) {
          search(screen).setValue("diamond");
          click(screen, 135, 15);
          click(screen, point[0], point[1], 1);
          if (!search(screen).getValue().isEmpty() || !search(screen).isFocused())
            throw new IllegalStateException(
                "Right-click did not clear/focus search at scale " + scale);
          screen.charTyped('x', 0);
          if (!search(screen).getValue().equals("x"))
            throw new IllegalStateException("Cannot type after right-click clear");
          click(screen, point[0], point[1]);
          if (!search(screen).getValue().equals("x"))
            throw new IllegalStateException("Left-click unexpectedly cleared search");
          click(screen, 135, 15, 1);
          if (!search(screen).getValue().equals("x") || search(screen).isFocused())
            throw new IllegalStateException("Outside right-click changed query or retained focus");
          click(screen, point[0], point[1], 1);
          click(screen, point[0], point[1], 1);
          if (!search(screen).getValue().isEmpty() || !search(screen).isFocused())
            throw new IllegalStateException("Repeated clear of empty search failed");
        }
        for (double[] point :
            List.of(
                new double[] {60, 62},
                new double[] {205, 95},
                new double[] {315, 205},
                new double[] {115, 232},
                new double[] {135, 15},
                new double[] {-8, -8})) {
          click(screen, 50, 38);
          if (!search(screen).isFocused())
            throw new IllegalStateException("Click did not focus search");
          search(screen).setValue("");
          click(screen, point[0], point[1]);
          screen.charTyped('z', 0);
          if (search(screen).isFocused() || !search(screen).getValue().isEmpty())
            throw new IllegalStateException(
                "Outside click retained typing focus at scale " + scale);
        }
      }
      RitualsNotRolls.LOGGER.info(
          "TABLE CLEAR CHECK: right-click clears and focuses search, including padding, at GUI"
              + " scales 2/4; typing resumes; left/outside clicks preserve query; repeated empty"
              + " clears work");
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
      click(screen, 50, 29);
      if (!search(screen).isFocused())
        throw new IllegalStateException("Search bar padding does not focus field");
      search(screen).setValue("diamond");
      click(screen, 135, 15);
      if (!search(screen).getValue().equals("diamond"))
        throw new IllegalStateException("Blur cleared query");
      screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
      if (mc.screen != null)
        throw new IllegalStateException("Unfocused rebound inventory key did not close");
      RitualsNotRolls.LOGGER.info(
          "TABLE FOCUS CHECK: typing protects inventory keys; rows, buttons, material panel,"
              + " background and outside-screen clicks blur at GUI scales 2/4; query persists and"
              + " unfocused rebound key closes");
    } finally {
      binding.setKey(previous);
      net.minecraft.client.KeyMapping.resetMapping();
    }
  }

  private static final class TierScreen extends Screen {
    private final Map<PowerTier, ItemStack> examples = new EnumMap<>(PowerTier.class);

    TierScreen() {
      super(Component.literal("Knowledge page power tiers"));
      for (var definition : Definitions.CLIENT.enchantments().values())
        for (var affinity : definition.materials())
          examples.putIfAbsent(
              definition.powerTier(affinity),
              Knowledge.page(definition.enchantment(), affinity.id()));
      if (examples.size() != 5) throw new IllegalStateException("Missing tier examples");
      for (var tier : PowerTier.values()) {
        String text = tier.description().getString();
        if (!text.endsWith(" Enchanting Power") || text.chars().anyMatch(Character::isDigit))
          throw new IllegalStateException("Incorrect page power label: " + text);
        RitualsNotRolls.LOGGER.info("POWER TOOLTIP LABEL: {}", text);
      }
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partial) {
      graphics.fill(0, 0, width, height, 0xff26232c);
      graphics.drawCenteredString(font, title, width / 2, 14, 0xffeee3c2);
      int index = 0;
      for (var tier : PowerTier.values()) {
        int x = 16 + (index % 2) * (width / 2), y = 68 + (index / 2) * 94;
        graphics.renderItem(examples.get(tier), x, y - 25);
        graphics.renderTooltip(font, examples.get(tier), x, y);
        index++;
      }
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }
  }
}
