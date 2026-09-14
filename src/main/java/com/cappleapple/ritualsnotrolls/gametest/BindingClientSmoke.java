package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.client.RitualScreen;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real-client binding observation; excluded from release artifacts. */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class BindingClientSmoke {
  private static final BlockPos TABLE = new BlockPos(0, 64, 0);
  private static int ticks, stage, wait, bindingStarted, flightStarted;

  private static void next(int n) {
    stage = n;
    wait = 0;
  }

  private static void check(boolean yes, String message) {
    if (!yes) throw new IllegalStateException(message);
  }

  private static List<ItemEntity> items() {
    return Minecraft.getInstance()
        .level
        .getEntitiesOfClass(ItemEntity.class, new AABB(TABLE).inflate(8))
        .stream()
        .filter(ItemEntity::isAlive)
        .toList();
  }

  private static int loose(Item item) {
    return items().stream()
        .filter(e -> e.getItem().is(item))
        .mapToInt(e -> e.getItem().getCount())
        .sum();
  }

  private static boolean floating(Item item) {
    return items().stream().anyMatch(e -> e.getItem().is(item) && e.isNoGravity());
  }

  private static int occupiedSlots() {
    var shelf =
        (ChiseledBookShelfBlockEntity)
            Minecraft.getInstance().level.getBlockEntity(TABLE.offset(2, 0, 0));
    check(shelf != null, "Fixture shelf is missing");
    int occupied = 0;
    for (var property : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES)
      if (shelf.getBlockState().getValue(property)) occupied++;
    return occupied;
  }

  private static long magic() throws Exception {
    var field = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
    field.setAccessible(true);
    var particles = (Map<?, ?>) field.get(Minecraft.getInstance().particleEngine);
    return particles.values().stream()
        .flatMap(value -> ((Collection<?>) value).stream())
        .filter(
            particle ->
                particle.getClass().getSimpleName().equals("EnchantmentTableParticle")
                    || particle.getClass().getSimpleName().equals("EndRodParticle"))
        .count();
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory, "binding-1.3-" + name + ".png", mc.getMainRenderTarget(), c -> {});
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.bindingSmoke") || stage == 99) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      check(ticks < 2400, "Binding QA timed out at stage " + stage);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Binding QA", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 30) {
        mc.options.hideGui = true;
        mc.getToasts().clear();
        mc.player.connection.sendCommand("ritualtest bindbook");
        next(2);
      } else if (stage == 2 && floating(RitualsNotRolls.PAGE.get())) {
        bindingStarted = ticks;
        next(3);
      } else if (stage == 3 && wait >= 12) {
        check(
            floating(RitualsNotRolls.PAGE.get()),
            "Page did not remain above the table during binding");
        check(
            items().stream()
                    .filter(e -> e.getItem().is(Items.LEATHER) && e.isNoGravity())
                    .mapToInt(e -> e.getItem().getCount())
                    .sum()
                == 3,
            "Binding did not animate exactly three leather items");
        check(loose(RitualsNotRolls.PAGE.get()) == 3, "Binding consumed pages before finishing");
        check(loose(Items.LEATHER) == 10, "Binding consumed leather before finishing");
        check(occupiedSlots() == 0, "A book appeared in the shelf before binding finished");
        check(magic() > 0, "Binding particles are missing");
        capture("ingredients");
        RitualsNotRolls.LOGGER.info(
            "BINDING_CLIENT_INGREDIENTS: three leather floating, three pages and ten leather"
                + " retained");
        next(4);
      } else if (stage == 4 && floating(RitualsNotRolls.BOOK.get())) {
        flightStarted = ticks;
        check(ticks - bindingStarted >= 25, "Binding animation ended too early");
        next(5);
      } else if (stage == 5 && wait >= 6) {
        check(floating(RitualsNotRolls.BOOK.get()), "Bound book did not fly to the shelf");
        check(loose(RitualsNotRolls.PAGE.get()) == 2, "Binding did not consume exactly one page");
        check(loose(Items.LEATHER) == 7, "Binding did not consume exactly three leather");
        check(occupiedSlots() == 0, "Shelf filled before the book arrived");
        check(magic() > 0, "Book flight particles are missing");
        capture("book-flight");
        RitualsNotRolls.LOGGER.info(
            "BINDING_CLIENT_FLIGHT: real book flying after {} observed binding ticks; surplus"
                + " pages=2 leather=7",
            flightStarted - bindingStarted);
        next(6);
      } else if (stage == 6 && ticks - bindingStarted >= 100) {
        check(occupiedSlots() == 1, "Exactly one book did not reach the shelf");
        check(loose(RitualsNotRolls.BOOK.get()) == 0, "Filed book still exists as a loose entity");
        check(loose(RitualsNotRolls.PAGE.get()) == 2, "Duplicate surplus pages were consumed");
        check(loose(Items.LEATHER) == 7, "Surplus leather was consumed");
        check(
            items().stream().noneMatch(ItemEntity::isNoGravity),
            "Surplus ingredients remain captured");
        capture("filed");
        mc.options.hideGui = false;
        mc.player.connection.sendCommand("tp @s 0.5 64 -3.5 0 12");
        next(7);
      } else if (stage == 7 && wait > 20) {
        mc.gameMode.useItemOn(
            mc.player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.NORTH, TABLE, false));
        next(8);
      } else if (stage == 8 && mc.screen instanceof RitualScreen screen && wait > 20) {
        var sharp =
            screen.getMenu().clientState.getList("knowledge", 10).stream()
                .map(tag -> (net.minecraft.nbt.CompoundTag) tag)
                .filter(tag -> tag.getString("id").equals("minecraft:sharpness"))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalStateException("The library did not gain Sharpness knowledge"));
        check(
            sharp.getList("entries", 8).stream()
                .anyMatch(tag -> tag.getAsString().equals("diamond")),
            "The bound book did not retain its page discovery");
        RitualsNotRolls.LOGGER.info(
            "BINDING_CLIENT_COMPLETE: real page and three leather animation, book flight, one filed"
                + " Sharpness book, two duplicate pages and seven leather retained");
        mc.stop();
        next(99);
      }
    } catch (Throwable failure) {
      RitualsNotRolls.LOGGER.error("BINDING_CLIENT_FAILED", failure);
      mc.stop();
      next(99);
    }
  }
}
