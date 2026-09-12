package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.compat.viewer.*;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class ViewerClientSmoke {
  private static int ticks, stage, wait;
  private static String viewer;

  private static void next(int value) {
    stage = value;
    wait = 0;
  }

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "viewer-1.1-" + viewer + "-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> {});
  }

  private static void hover(int inventorySlot) throws Exception {
    var mc = Minecraft.getInstance();
    var screen = (InventoryScreen) mc.screen;
    var slot =
        screen.getMenu().slots.stream()
            .filter(
                s ->
                    s.container == mc.player.getInventory()
                        && s.getContainerSlot() == inventorySlot)
            .findFirst()
            .orElseThrow();
    var window = mc.getWindow();
    double x = (screen.getGuiLeft() + slot.x + 8) * window.getGuiScale();
    double y = (screen.getGuiTop() + slot.y + 8) * window.getGuiScale();
    GLFW.glfwSetCursorPos(window.getWindow(), x, y);
    var method =
        MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
    method.setAccessible(true);
    method.invoke(mc.mouseHandler, window.getWindow(), x, y);
  }

  private static void key(int key) throws Exception {
    var mc = Minecraft.getInstance();
    var method =
        KeyboardHandler.class.getDeclaredMethod(
            "keyPress", long.class, int.class, int.class, int.class, int.class);
    method.setAccessible(true);
    method.invoke(mc.keyboardHandler, mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0);
    method.invoke(mc.keyboardHandler, mc.getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0);
  }

  private static void lookups() {
    var mc = Minecraft.getInstance();
    var book = mc.player.getInventory().getItem(0);
    var page = mc.player.getInventory().getItem(1);
    check(
        book.is(Items.ENCHANTED_BOOK) && page.is(RitualsNotRolls.PAGE),
        "Viewer fixture items not synced");
    for (var stack : List.of(book, page)) {
      var views = KnowledgeRecipeViews.uses(stack);
      check(views.size() == 1 && !views.getFirst().outputs().isEmpty(), "Dynamic recipe missing");
      if (viewer.equals("emi")) EmiCheck.run(stack);
      else if (viewer.equals("jei")) JeiCheck.run(stack);
      else ReiCheck.run(stack);
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.viewerSmoke")) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      check(ticks < 3600, "Viewer test timed out at " + stage);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        viewer =
            ModList.get().isLoaded("emi") ? "emi" : ModList.get().isLoaded("jei") ? "jei" : "rei";
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Recipe viewer QA", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1
          && mc.player != null
          && mc.screen == null
          && wait > 150
          && !Definitions.CLIENT.enchantments().isEmpty()) {
        mc.player.connection.sendCommand("ritualtest viewers");
        next(2);
      } else if (stage == 2 && wait > 120) {
        lookups();
        mc.getToasts().clear();
        mc.setScreen(new InventoryScreen(mc.player));
        next(3);
      } else if (stage == 3 && wait > 20) {
        hover(0);
        next(4);
      } else if (stage == 4 && wait > 10) {
        capture("book-hover");
        key(GLFW.GLFW_KEY_U);
        next(5);
      } else if (stage == 5 && wait > 30) {
        check(
            !(mc.screen instanceof InventoryScreen) && mc.screen != null,
            "Uses key did not open enchanted book recipe");
        RitualsNotRolls.LOGGER.info("VIEWER_BOOK_U: {} {}", viewer, mc.screen.getClass().getName());
        capture("book-uses");
        mc.setScreen(new InventoryScreen(mc.player));
        next(6);
      } else if (stage == 6 && wait > 20) {
        hover(1);
        next(7);
      } else if (stage == 7 && wait > 10) {
        key(GLFW.GLFW_KEY_U);
        next(8);
      } else if (stage == 8 && wait > 30) {
        check(
            !(mc.screen instanceof InventoryScreen) && mc.screen != null,
            "Uses key did not open page binding recipe");
        capture("page-uses");
        RitualsNotRolls.LOGGER.info(
            "VIEWER_CLIENT_COMPLETE: {} real Uses key on multi-enchantment level VII book and page;"
                + " both live lookups and hidden index checked",
            viewer);
        mc.stop();
        next(99);
      }
    } catch (Throwable failure) {
      RitualsNotRolls.LOGGER.error("VIEWER_CLIENT_FAILED: " + viewer, failure);
      mc.stop();
      next(99);
    }
  }

  private static final class EmiCheck {
    static void run(ItemStack stack) {
      var recipes =
          dev.emi.emi.api.EmiApi.getRecipeManager()
              .getRecipesByInput(dev.emi.emi.api.stack.EmiStack.of(stack));
      check(
          recipes.stream().anyMatch(r -> r instanceof EmiKnowledgePlugin.Recipe),
          "EMI indexed lookup did not include live component recipe");
      check(
          dev.emi.emi.api.EmiApi.getIndexStacks().stream()
              .noneMatch(s -> KnowledgeRecipeViews.hidden(s.getItemStack())),
          "EMI index shows knowledge variants");
    }
  }

  private static final class JeiCheck {
    static void run(ItemStack stack) {
      var runtime = JeiClientProbe.runtime;
      check(runtime != null, "JEI runtime unavailable");
      var focus =
          runtime
              .getJeiHelpers()
              .getFocusFactory()
              .createFocus(
                  mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                  mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                  stack);
      check(
          runtime
              .getRecipeManager()
              .createRecipeLookup(JeiKnowledgePlugin.TYPE)
              .limitFocus(List.of(focus))
              .get()
              .findAny()
              .isPresent(),
          "JEI typed lookup did not resolve recipe");
      check(
          runtime
              .getIngredientFilter()
              .getFilteredIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)
              .stream()
              .noneMatch(KnowledgeRecipeViews::hidden),
          "JEI index shows knowledge variants");
    }
  }

  private static final class ReiCheck {
    static void run(ItemStack stack) {
      var entries = me.shedaniel.rei.api.client.registry.entry.EntryRegistry.getInstance();
      check(!entries.isReloading(), "REI still reloading");
      check(
          entries
              .getEntryStacks()
              .noneMatch(
                  e -> e.getValue() instanceof ItemStack s && KnowledgeRecipeViews.hidden(s)),
          "REI index shows knowledge variants");
      var matches =
          me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder()
              .addUsagesFor(me.shedaniel.rei.api.common.util.EntryStacks.of(stack))
              .buildMapInternal();
      check(
          matches.keySet().stream()
              .anyMatch(c -> c.getCategoryIdentifier().equals(ReiKnowledgePlugin.CATEGORY)),
          "REI dynamic lookup did not resolve recipe");
    }
  }
}
