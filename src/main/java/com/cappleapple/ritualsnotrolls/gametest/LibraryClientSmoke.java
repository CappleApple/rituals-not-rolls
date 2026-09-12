package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.client.RitualScreen;
import com.cappleapple.ritualsnotrolls.network.Networking;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class LibraryClientSmoke {
  private static int ticks, stage, wait;
  private static double filingMaxX;
  private static final BlockPos TABLE = new BlockPos(0, 64, 0);

  private static void next(int n) {
    stage = n;
    wait = 0;
  }

  private static void check(boolean yes, String message) {
    if (!yes) throw new IllegalStateException(message);
  }

  private static ChiseledBookShelfBlockEntity shelf() {
    return (ChiseledBookShelfBlockEntity)
        Minecraft.getInstance().level.getBlockEntity(TABLE.offset(2, 0, 0));
  }

  private static List<ItemEntity> items() {
    return Minecraft.getInstance()
        .level
        .getEntitiesOfClass(ItemEntity.class, new AABB(TABLE).inflate(8));
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory, "library-1.1.1-" + name + ".png", mc.getMainRenderTarget(), c -> {});
  }

  private static long magic() throws Exception {
    var f = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
    f.setAccessible(true);
    var map = (Map<?, ?>) f.get(Minecraft.getInstance().particleEngine);
    return map.values().stream()
        .flatMap(v -> ((Collection<?>) v).stream())
        .filter(
            p ->
                p.getClass().getSimpleName().equals("EnchantmentTableParticle")
                    || p.getClass().getSimpleName().equals("EndRodParticle"))
        .count();
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.librarySmoke")) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      if (mc.level != null && stage == 3 || mc.level != null && stage == 4) {
        for (var entity : items())
          if (entity.getItem().is(RitualsNotRolls.PAGE) && entity.isNoGravity()) {
            filingMaxX = Math.max(filingMaxX, entity.getX());
            if (entity.getX() > 2.5) capture("page-at-left-slot");
          }
      }
      check(ticks < 2400, "Library QA timed out at " + stage);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Library QA", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 30) {
        mc.player.connection.sendCommand("ritualtest library");
        next(2);
      } else if (stage == 2 && wait > 30) {
        mc.getToasts().clear();
        mc.player.connection.sendCommand("ritualtest filepage");
        next(3);
      } else if (stage == 3 && wait > 14) {
        check(
            items().stream()
                .anyMatch(
                    e -> e.getItem().is(RitualsNotRolls.PAGE) && e.isNoGravity() && e.getX() > .65),
            "Page not flying toward shelf");
        check(magic() > 0, "No magical trail particles");
        capture("page-flight");
        next(4);
      } else if (stage == 4 && wait > 45) {
        check(filingMaxX > 2.5, "Page flew to the mirrored side instead of the shelf's left slot");
        check(
            shelf()
                .getBlockState()
                .getValue(
                    net.minecraft.world.level.block.ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES
                        .get(0)),
            "Occupied book slot not preserved after filing");
        check(
            items().stream().noneMatch(e -> e.getItem().is(RitualsNotRolls.PAGE)),
            "Filed page entity not removed");
        mc.player.connection.sendCommand("ritualtest filebook");
        next(5);
      } else if (stage == 5 && wait > 14) {
        check(
            items().stream().anyMatch(e -> e.getItem().is(RitualsNotRolls.BOOK) && e.isNoGravity()),
            "Book not flying toward slot");
        check(magic() > 0, "Book trail missing");
        capture("book-flight");
        next(6);
      } else if (stage == 6 && wait > 45) {
        int count = 0;
        for (int i = 0; i < 6; i++)
          if (shelf()
              .getBlockState()
              .getValue(
                  net.minecraft.world.level.block.ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES
                      .get(i))) count++;
        check(count == 2, "Book not filed into random open slot");
        mc.player.connection.sendCommand("tp @s 0.5 64 -3.5 0 -5");
        next(7);
      } else if (stage == 7 && wait > 20) {
        mc.gameMode.useItemOn(
            mc.player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.NORTH, TABLE, false));
        next(8);
      } else if (stage == 8 && mc.screen instanceof RitualScreen screen && wait > 20) {
        RitualsNotRolls.LOGGER.info(
            "LIBRARY_MENU_CHECK: held={} state={}",
            mc.player.getMainHandItem(),
            screen.getMenu().clientState);
        var sharp =
            screen.getMenu().clientState.getList("knowledge", 10).stream()
                .map(t -> (net.minecraft.nbt.CompoundTag) t)
                .filter(t -> t.getString("id").equals("minecraft:sharpness"))
                .findFirst()
                .orElseThrow();
        check(
            sharp.getList("entries", 8).stream()
                .anyMatch(t -> t.getAsString().equals("netherite_scrap")),
            "Server library did not gain filed discovery");
        PacketDistributor.sendToServer(
            new Networking.Action(
                screen.getMenu().containerId, 1, "retrieve", "minecraft:sharpness"));
        mc.player.closeContainer();
        next(9);
      } else if (stage == 9 && wait > 14) {
        check(
            items().stream().anyMatch(e -> e.getItem().is(RitualsNotRolls.BOOK) && e.isNoGravity()),
            "Menu retrieval did not start a real book flight");
        check(magic() > 0, "Retrieval particles missing");
        capture("retrieval-flight");
        next(10);
      } else if (stage == 10 && wait > 45) {
        check(
            items().stream()
                .anyMatch(
                    e ->
                        e.getItem().is(RitualsNotRolls.BOOK)
                            && !e.isNoGravity()
                            && Math.abs(e.getX() - .5) < .3),
            "Retrieved book not released above table");
        capture("retrieval-drop");
        RitualsNotRolls.LOGGER.info(
            "LIBRARY_CLIENT_COMPLETE: native page and book flights, correct left-slot approach,"
                + " magic trails, shelf updates, menu retrieval and release observed");
        mc.stop();
        next(99);
      }
    } catch (Throwable failure) {
      RitualsNotRolls.LOGGER.error("LIBRARY_CLIENT_FAILED", failure);
      mc.stop();
      next(99);
    }
  }
}
