package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.client.*;
import com.cappleapple.ritualsnotrolls.ritual.RitualMath;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in native client checks; excluded from release jars. */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class GuideClientSmoke {
  private static boolean zoomChecked;
  private static int ticks, stage, wait, chapter, breakingAt, tooltipRenders;
  private static final BlockPos TABLE = new BlockPos(0, 64, 0);

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
        mc.gameDirectory, "guide-1.1.1-" + name + ".png", mc.getMainRenderTarget(), c -> {});
  }

  private static double[] point(int x, int y) {
    var w = Minecraft.getInstance().getWindow();
    double fit =
        Math.min(1, Math.min(w.getGuiScaledWidth() / 392.0, w.getGuiScaledHeight() / 264.0));
    return new double[] {
      ((Math.ceil(w.getGuiScaledWidth() / fit) - 380) / 2 + x) * fit,
      ((Math.ceil(w.getGuiScaledHeight() / fit) - 252) / 2 + y) * fit
    };
  }

  private static void click(int x, int y) {
    var screen = Minecraft.getInstance().screen;
    var p = point(x, y);
    screen.mouseClicked(p[0], p[1], 0);
    screen.mouseReleased(p[0], p[1], 0);
  }

  private static void hover(int x, int y) throws ReflectiveOperationException {
    var w = Minecraft.getInstance().getWindow();
    var p = point(x, y);
    GLFW.glfwSetCursorPos(
        w.getWindow(),
        p[0] * w.getScreenWidth() / w.getGuiScaledWidth(),
        p[1] * w.getScreenHeight() / w.getGuiScaledHeight());
    var move =
        net.minecraft.client.MouseHandler.class.getDeclaredMethod(
            "onMove", long.class, double.class, double.class);
    move.setAccessible(true);
    move.invoke(
        Minecraft.getInstance().mouseHandler,
        w.getWindow(),
        p[0] * w.getScreenWidth() / w.getGuiScaledWidth(),
        p[1] * w.getScreenHeight() / w.getGuiScaledHeight());
  }

  private static Object field(RitualScreen screen, String name)
      throws ReflectiveOperationException {
    var field = RitualScreen.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(screen);
  }

  @SuppressWarnings("unchecked")
  private static int fragments() throws ReflectiveOperationException {
    var field = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
    field.setAccessible(true);
    var particles =
        (Map<?, Queue<net.minecraft.client.particle.Particle>>)
            field.get(Minecraft.getInstance().particleEngine);
    return (int)
        particles.values().stream()
            .flatMap(Collection::stream)
            .filter(p -> p instanceof net.minecraft.client.particle.BreakingItemParticle)
            .count();
  }

  @SubscribeEvent
  public static void tooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.Pre event) {
    if (Boolean.getBoolean("ritualsnotrolls.guideSmoke")
        && (stage == 31 || stage == 4 || stage == 5)) tooltipRenders++;
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.guideSmoke")) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      check(ticks < 2400, "Guide client timed out at stage " + stage);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Guide QA", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 30) {
        mc.player.connection.sendCommand("ritualtest guide");
        next(2);
      } else if (stage == 2 && wait > 30) {
        mc.gameMode.useItemOn(
            mc.player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.NORTH, TABLE, false));
        next(3);
      } else if (stage == 3 && mc.screen instanceof RitualScreen screen && wait > 30) {
        var state = screen.getMenu().clientState;
        check(state.getBoolean("filtered"), "Real right-click did not capture held item");
        var ids =
            state.getList("knowledge", 10).stream()
                .map(t -> ((net.minecraft.nbt.CompoundTag) t).getString("id"))
                .toList();
        check(
            ids.contains("minecraft:sharpness")
                && ids.contains("minecraft:vanishing_curse")
                && !ids.contains("minecraft:protection"),
            "Filtered network state is wrong");
        check(
            !RitualScreen.materialStrength(30, 0, 55).contains("effective"),
            "Zero effective power is visible");
        check(
            RitualScreen.materialStrength(30, -15, 55).contains("effective"),
            "Negative effective power was hidden");
        var normal = EnchantmentTooltip.lines(ResourceLocation.withDefaultNamespace("sharpness"));
        var curse =
            EnchantmentTooltip.lines(ResourceLocation.withDefaultNamespace("vanishing_curse"));
        check(
            normal.getFirst().getStyle().getColor().getValue() == 0xFFAA00
                && normal.getLast().getStyle().getColor().getValue() == 0xFFFFFF,
            "Default gold name / white description incorrect");
        check(
            curse.getFirst().getStyle().getColor().getValue() == 0xFF5555
                && curse.getLast().getStyle().getColor().getValue() == 0xFFFFFF,
            "Default red curse name / white description incorrect");
        check(
            normal.get(1).getString().contains("melee")
                && curse.get(1).getString().contains("disappear"),
            "Descriptions missing");
        check(
            normal.get(1).getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("enchantment.minecraft.sharpness.desc"),
            "Description did not use the standard Enchantment Descriptions key");
        String normalColor = ClientConfig.ENCHANTMENT_TOOLTIP_COLOR.get(),
            curseColor = ClientConfig.CURSE_TOOLTIP_COLOR.get();
        try {
          ClientConfig.ENCHANTMENT_TOOLTIP_COLOR.set("#123456");
          ClientConfig.CURSE_TOOLTIP_COLOR.set("#ABCDEF");
          check(
              EnchantmentTooltip.lines(ResourceLocation.withDefaultNamespace("sharpness"))
                      .getFirst()
                      .getStyle()
                      .getColor()
                      .getValue()
                  == 0x123456,
              "Normal color config ignored");
          check(
              EnchantmentTooltip.lines(ResourceLocation.withDefaultNamespace("vanishing_curse"))
                      .getFirst()
                      .getStyle()
                      .getColor()
                      .getValue()
                  == 0xABCDEF,
              "Curse color config ignored");
          for (String id : java.util.List.of("sharpness", "vanishing_curse")) {
            check(
                EnchantmentTooltip.lines(ResourceLocation.withDefaultNamespace(id))
                        .getLast()
                        .getStyle()
                        .getColor()
                        .getValue()
                    == 0xFFFFFF,
                "Name color config changed description color");
          }
        } finally {
          ClientConfig.ENCHANTMENT_TOOLTIP_COLOR.set(normalColor);
          ClientConfig.CURSE_TOOLTIP_COLOR.set(curseColor);
        }
        mc.getToasts().clear();
        tooltipRenders = 0;
        hover(210, 16);
        next(31);
      } else if (stage == 31 && wait > 12) {
        check(tooltipRenders == 0, "Item header still shows a filtering tooltip");
        capture("item-header");
        hover(60, 62);
        next(4);
      } else if (stage == 4 && wait > 12) {
        check(tooltipRenders > 0, "Gold name and white description were not rendered on hover");
        capture("gold-tooltip");
        tooltipRenders = 0;
        var screen = (RitualScreen) mc.screen;
        var search = (net.minecraft.client.gui.components.EditBox) field(screen, "search");
        search.setValue("vanishing");
        hover(60, 62);
        next(5);
      } else if (stage == 5 && wait > 12) {
        check(tooltipRenders > 0, "Curse description was not rendered on hover");
        capture("curse-tooltip");
        ((net.minecraft.client.gui.components.EditBox) field((RitualScreen) mc.screen, "search"))
            .setValue("diamond");
        click(340, 16);
        hover(135, 245);
        chapter = 0;
        next(6);
      } else if (stage == 6 && wait > 12) {
        var screen = (RitualScreen) mc.screen;
        check((boolean) field(screen, "guideOpen"), "Guide did not open");
        capture("chapter-" + chapter);
        check(
            !RitualGuide.chapter(chapter, screen.getMenu().clientState).getString().contains("%s"),
            "Guide has unexpanded values");
        check(
            mc.getResourceManager().getResource(RitualGuide.example(chapter)).isPresent(),
            "Guide screenshot is missing");
        check(
            mc.font.split(RitualGuide.caption(chapter), 206).size() <= 3,
            "Guide screenshot caption overflows");
        if (!zoomChecked) {
          zoomChecked = true;
          click(230, 110);
          next(61);
          return;
        }
        var at = point(240, 160);
        screen.mouseScrolled(at[0], at[1], 0, -.5);
        check(
            (double) field(screen, "guideScroll") == 12,
            "Scroll did not retain fractional wheel input");
        next(60);
      } else if (stage == 61 && wait > 12) {
        var screen = (RitualScreen) mc.screen;
        check((boolean) field(screen, "guideZoom"), "Screenshot did not enlarge");
        capture("screenshot-enlarged");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(
            !(boolean) field(screen, "guideZoom") && (boolean) field(screen, "guideOpen"),
            "Escape did not return from screenshot to guide");
        next(6);
      } else if (stage == 60 && wait > 5) {
        capture("chapter-" + chapter + "-partial");
        var at = point(240, 160);
        mc.screen.mouseScrolled(at[0], at[1], 0, -6);
        next(62);
      } else if (stage == 62 && wait > 5) {
        capture("chapter-" + chapter + "-middle");
        mc.screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        next(63);
      } else if (stage == 63 && wait > 5) {
        var screen = (RitualScreen) mc.screen;
        capture("chapter-" + chapter + "-bottom");
        double maximum = (int) field(screen, "guideContentHeight") - 162;
        check(
            (double) field(screen, "guideScroll") == maximum, "End did not reach all chapter text");
        var at = point(240, 160);
        screen.mouseScrolled(at[0], at[1], 0, -1000);
        check((double) field(screen, "guideScroll") == maximum, "Scroll exceeded bottom bound");
        click(230, 45);
        check(!(boolean) field(screen, "guideZoom"), "Clipped screenshot captured title click");
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        check((double) field(screen, "guideScroll") == 0, "Home did not return to screenshot");
        var start = point(360, 58);
        var end = point(360, 218);
        screen.mouseClicked(start[0], start[1], 0);
        screen.mouseDragged(end[0], end[1], 0, end[0] - start[0], end[1] - start[1]);
        screen.mouseReleased(end[0], end[1], 0);
        check(
            (double) field(screen, "guideScroll") == maximum,
            "Scrollbar drag did not reach bottom");
        check(!(boolean) field(screen, "guideDragging"), "Scrollbar drag did not release");
        chapter++;
        if (chapter < 8) {
          click(65, 53 + chapter * 22);
          check((double) field(screen, "guideScroll") == 0, "New chapter did not reset scroll");
          next(6);
        } else {
          mc.options.guiScale().set(4);
          mc.resizeDisplay();
          click(65, 53);
          next(7);
        }
      } else if (stage == 7 && wait > 15) {
        capture("small-gui");
        var screen = (RitualScreen) mc.screen;
        check((boolean) field(screen, "guideOpen"), "Resize lost guide");
        var at = point(240, 160);
        screen.mouseScrolled(at[0], at[1], 0, -8);
        check((double) field(screen, "guideScroll") == 192, "Small GUI scroll coordinates failed");
        mc.resizeDisplay();
        check((double) field(screen, "guideScroll") == 192, "Resize lost scroll position");
        next(70);
      } else if (stage == 70 && wait > 8) {
        var screen = (RitualScreen) mc.screen;
        capture("small-gui-scrolled");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(!(boolean) field(screen, "guideOpen"), "Escape did not return to library");
        check(
            ((net.minecraft.client.gui.components.EditBox) field(screen, "search"))
                .getValue()
                .equals("diamond"),
            "Guide lost search");
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.player.closeContainer();
        mc.player.connection.sendCommand("ritualtest consume");
        next(8);
      } else if (stage == 8) {
        int fragments = fragments();
        if (fragments > 0 && breakingAt == 0) breakingAt = ticks;
        if (breakingAt > 0 && ticks >= breakingAt + 2) {
          check(fragments > 0, "Break particles expired before observation");
          var pedestal =
              (com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity)
                  mc.level.getBlockEntity(TABLE.offset(-2, 0, 0));
          check(
              pedestal.items.getStackInSlot(0).isEmpty()
                  && !pedestal.items.getStackInSlot(1).isEmpty(),
              "Fragments did not coincide with offering removal");
          capture("consumption-fragments");
          RitualsNotRolls.LOGGER.info(
              "GUIDE PARTICLE CHECK: {} native item fragments present after offering removal;"
                  + " catalyst retained",
              fragments);
          next(9);
        }
      } else if (stage == 9 && wait > 150) {
        var targets =
            mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(TABLE).inflate(12));
        check(
            targets.stream()
                .anyMatch(
                    e ->
                        e.getItem().is(Items.DIAMOND_SWORD)
                            && !RitualMath.enchantments(e.getItem()).isEmpty()),
            "Ritual did not complete after consuming offering");
        RitualsNotRolls.LOGGER.info(
            "GUIDE_CLIENT_COMPLETE: real held-item right-click, gold/red names, white standard-key"
                + " descriptions, custom colors, quiet item header, zero power, all guide chapters,"
                + " continuous scrolling, scrollbar dragging, small GUI, preserved search,"
                + " consumption fragments and completion");
        mc.stop();
        next(10);
      }
    } catch (Exception failure) {
      RitualsNotRolls.LOGGER.error("GUIDE_CLIENT_FAILED", failure);
      mc.stop();
      next(10);
    }
  }
}
