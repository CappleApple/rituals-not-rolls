package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Captures real game frames for the guide's bundled screenshot examples. */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class GuideExampleCapture {
  private static int ticks, stage, wait, chapter, fragmentAt, originalFov;

  private static void next(int n) {
    stage = n;
    wait = 0;
  }

  private static int fragments() throws Exception {
    var field = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
    field.setAccessible(true);
    var particles = (Map<?, ?>) field.get(Minecraft.getInstance().particleEngine);
    return (int)
        particles.values().stream()
            .flatMap(v -> ((Collection<?>) v).stream())
            .filter(p -> p instanceof net.minecraft.client.particle.BreakingItemParticle)
            .count();
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.captureGuideExamples")) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      if (ticks > 4000)
        throw new IllegalStateException(
            "Screenshot capture timed out at " + stage + " chapter " + chapter);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        originalFov = mc.options.fov().get();
        mc.options.fov().set(55);
        mc.options.guiScale().set(2);
        mc.options.hideGui = true;
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Guide examples", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 40) {
        fragmentAt = 0;
        mc.player.connection.sendCommand("ritualtest guideexample " + chapter);
        next(2);
      } else if (stage == 2) {
        int delay = chapter == 1 ? 16 : chapter >= 5 ? 75 : 22;
        if (chapter == 3 && fragments() > 0 && fragmentAt == 0) fragmentAt = wait;
        if (wait >= delay && (chapter != 3 || fragmentAt > 0 && wait >= fragmentAt + 2)) {
          mc.getToasts().clear();
          Screenshot.grab(
              mc.gameDirectory,
              "guide-example-" + chapter + ".png",
              mc.getMainRenderTarget(),
              c -> {});
          RitualsNotRolls.LOGGER.info("GUIDE_EXAMPLE_CAPTURED: {}", chapter);
          next(3);
        }
      } else if (stage == 3 && wait > 180) {
        if (++chapter < 8) next(1);
        else {
          mc.options.hideGui = false;
          mc.options.fov().set(originalFov);
          RitualsNotRolls.LOGGER.info("GUIDE_EXAMPLES_COMPLETE");
          mc.stop();
          next(99);
        }
      }
    } catch (Throwable failure) {
      mc.options.hideGui = false;
      mc.options.fov().set(originalFov);
      RitualsNotRolls.LOGGER.error("GUIDE_EXAMPLES_FAILED", failure);
      mc.stop();
      next(99);
    }
  }
}
