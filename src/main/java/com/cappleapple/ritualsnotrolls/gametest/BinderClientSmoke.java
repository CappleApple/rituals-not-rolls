package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.client.BinderScreen;
import com.cappleapple.ritualsnotrolls.knowledge.BinderStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real client/server binder menu checks, enabled only by the development launch property. */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class BinderClientSmoke {
  private static int ticks, stage, wait, originalTotal;

  private static void next(int value) {
    stage = value;
    wait = 0;
    var mc = Minecraft.getInstance();
    RitualsNotRolls.LOGGER.info(
        "BINDER_CLIENT_STAGE: stage={} screen={} selected={} held={}",
        stage,
        mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
        mc.player == null ? -1 : mc.player.getInventory().selected,
        mc.player == null ? "none" : mc.player.getMainHandItem());
  }

  private static void check(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory, "binder-1.3-" + name + ".png", mc.getMainRenderTarget(), ignored -> {});
  }

  private static BinderScreen screen() {
    check(Minecraft.getInstance().screen instanceof BinderScreen, "Binder screen is not open");
    return (BinderScreen) Minecraft.getInstance().screen;
  }

  private static void click(int x, int y) {
    var screen = screen();
    // GUI scale two gives an unscaled 366 x 304 binder in the configured 1280 x 720 window.
    check(
        screen.mouseClicked((screen.width - 366) / 2.0 + x, (screen.height - 304) / 2.0 + y, 0),
        "Binder click was not handled");
    screen.mouseReleased((screen.width - 366) / 2.0 + x, (screen.height - 304) / 2.0 + y, 0);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.binderSmoke") || stage == 99) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      check(ticks < 2400 && wait < 600, "Binder client QA timed out at stage " + stage);
      if (wait % 100 == 0)
        RitualsNotRolls.LOGGER.info(
            "BINDER_CLIENT_WAIT: stage={} ticks={} screen={} selected={} held={}",
            stage,
            wait,
            mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
            mc.player == null ? -1 : mc.player.getInventory().selected,
            mc.player == null ? "none" : mc.player.getMainHandItem());
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        mc.options.guiScale().set(2);
        mc.options.pauseOnLostFocus = false;
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Binder QA", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 30) {
        mc.player.connection.sendCommand("ritualtest library");
        next(2);
      } else if (stage == 2 && wait > 30) {
        mc.player.connection.sendCommand("ritualbinder");
        next(3);
      } else if (stage == 3
          && wait > 20
          && mc.player.getMainHandItem().is(RitualsNotRolls.BINDER_ITEM)) {
        var result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        RitualsNotRolls.LOGGER.info(
            "BINDER_CLIENT_USE: stage={} result={} held={}",
            stage,
            result,
            mc.player.getMainHandItem());
        next(4);
      } else if (stage == 4 && mc.screen instanceof BinderScreen && wait > 20) {
        var menu = screen().getMenu();
        check(
            menu.cards().size() == 12 && menu.spreads() == 2,
            "First spread does not contain twelve cards with another spread available");
        check(menu.cards().getFirst().count() == 65, "Stored quantity is not displayed");
        check(
            menu.capacity() == BinderStorage.capacity(),
            "Displayed capacity is not the synced server capacity");
        check(
            menu.autoCollect() && menu.filterDuplicates(), "Default toggles are not both enabled");
        originalTotal = menu.total();
        mc.getToasts().clear();
        capture("cards");
        click(328, 253);
        next(5);
      } else if (stage == 5 && wait > 10) {
        check(
            screen().getMenu().viewPage() == 1 && screen().getMenu().cards().size() == 8,
            "Next spread did not synchronize");
        capture("second-spread");
        click(38, 253);
        next(6);
      } else if (stage == 6 && wait > 10) {
        check(screen().getMenu().viewPage() == 0, "Previous spread did not synchronize");
        click(76, 286);
        next(7);
      } else if (stage == 7 && wait > 10) {
        check(!screen().getMenu().autoCollect(), "Auto-collect toggle did not round-trip");
        click(224, 286);
        next(8);
      } else if (stage == 8 && wait > 10) {
        check(!screen().getMenu().filterDuplicates(), "Duplicate filter toggle did not round-trip");
        click(42, 74);
        next(9);
      } else if (stage == 9 && wait > 10) {
        check(
            screen().getMenu().total() == originalTotal - 1
                && screen().getMenu().cards().getFirst().count() == 64,
            "Click did not return exactly one page");
        capture("take-and-toggles");
        mc.options.guiScale().set(3);
        mc.resizeDisplay();
        next(10);
      } else if (stage == 10 && wait > 10) {
        check(screen().getMenu().cards().size() == 12, "Resizing lost binder contents");
        capture("fitted");
        screen().onClose();
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        next(11);
      } else if (stage == 11 && wait > 10) {
        check(
            mc.player.getInventory().items.stream()
                    .filter(stack -> stack.is(RitualsNotRolls.PAGE))
                    .mapToInt(net.minecraft.world.item.ItemStack::getCount)
                    .sum()
                == 1,
            "Taken page did not remain loose in inventory");
        check(
            BinderStorage.data(mc.player.getMainHandItem()).total() == originalTotal - 1,
            "Inventory binder did not retain the updated contents");
        mc.player.connection.sendCommand("ritualbinder empty");
        next(12);
      } else if (stage == 12 && wait > 20) {
        var result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        RitualsNotRolls.LOGGER.info(
            "BINDER_CLIENT_USE: stage={} result={} held={}",
            stage,
            result,
            mc.player.getMainHandItem());
        next(13);
      } else if (stage == 13 && mc.screen instanceof BinderScreen && wait > 20) {
        check(
            screen().getMenu().cards().isEmpty() && screen().getMenu().total() == 0,
            "Empty binder state did not synchronize");
        capture("empty");
        RitualsNotRolls.LOGGER.info(
            "BINDER_CLIENT_COMPLETE: twelve-card grid, two-spread navigation, both toggles,"
                + " one-page extraction, inventory persistence, fitted GUI, empty binder");
        mc.stop();
        next(99);
      }
    } catch (Throwable failure) {
      RitualsNotRolls.LOGGER.error("BINDER_CLIENT_FAILED at stage " + stage, failure);
      capture("failed-stage-" + stage);
      mc.stop();
      next(99);
    }
  }
}
