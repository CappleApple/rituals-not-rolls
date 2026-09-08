package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.RitualApi;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.menu.*;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {
  private record Room(
      ServerPlayer player,
      BlockPos table,
      PedestalEntity pedestal,
      ChiseledBookShelfBlockEntity shelf) {}

  private static Room room(GameTestHelper h) {
    h.setBlock(20, 1, 20, Blocks.ENCHANTING_TABLE);
    h.setBlock(22, 1, 20, Blocks.CHISELED_BOOKSHELF);
    h.setBlock(20, 1, 22, RitualsNotRolls.PEDESTAL.get());
    BlockPos table = h.absolutePos(new BlockPos(20, 1, 20));
    var player = RitualGameTests.player(h);
    player.setPos(RitualGameTests.Vec3At(table));
    var shelf =
        (ChiseledBookShelfBlockEntity)
            h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(22, 1, 20)));
    shelf.setItem(0, RitualGameTests.book("diamond", "netherite_scrap"));
    var pedestal =
        (PedestalEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(20, 1, 22)));
    pedestal.items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
    RitualNetwork.invalidate(h.getLevel());
    return new Room(player, table, pedestal, shelf);
  }

  @GameTest(template = "network")
  public static void partialExtractionFailureRollsBack(GameTestHelper h) {
    var room = room(h);
    room.pedestal.items.setStackInSlot(
        1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    BlockPos external = room.table.offset(-2, 0, 0);
    ItemStackHandler backend =
        new ItemStackHandler(2) {
          @Override
          public ItemStack extractItem(int slot, int count, boolean simulate) {
            return simulate ? super.extractItem(slot, count, true) : ItemStack.EMPTY;
          }
        };
    backend.setStackInSlot(0, new ItemStack(Items.NETHERITE_SCRAP));
    backend.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    RitualApi.registerCandidateProvider(
        (level, table, radius) ->
            level == h.getLevel() && table.equals(room.table) ? List.of(external) : List.of());
    RitualApi.registerPedestal(
        (level, pos) -> level == h.getLevel() && pos.equals(external) ? backend : null);
    var target =
        new ItemEntity(
            h.getLevel(),
            room.table.getX() + .5,
            room.table.getY() + 1,
            room.table.getZ() + .5,
            new ItemStack(Items.DIAMOND_SWORD));
    h.getLevel().addFreshEntity(target);
    String reason =
        RitualEngine.commit(
            h.getLevel(),
            room.table,
            room.player,
            target,
            target.getItem().copy(),
            Map.of(RitualGameTests.SHARP, 3),
            Set.of(
                ResourceLocation.parse("minecraft:diamond"),
                ResourceLocation.parse("minecraft:netherite_scrap")),
            0);
    h.assertTrue(!reason.isEmpty(), "Extraction failure rejected");
    h.assertTrue(
        room.pedestal.items.getStackInSlot(0).getCount() == 1, "Earlier extraction rolled back");
    h.assertTrue(RitualMath.enchantments(target.getItem()).isEmpty(), "Target unchanged");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void changedDatapackPowerKeepsKnowledge(GameTestHelper h) {
    var room = room(h);
    var old = Definitions.SERVER;
    var d = old.get(RitualGameTests.SHARP);
    var materials = new ArrayList<>(d.materials());
    materials.replaceAll(
        a -> a.id().equals("diamond") ? new Affinity(a.id(), a.item(), a.tag(), 99, a.value()) : a);
    var updated =
        new RitualDefinition(
            d.enchantment(),
            materials,
            d.levels(),
            d.conflictGroups(),
            d.visual(),
            d.particle(),
            d.sound());
    var definitions = new TreeMap<>(old.enchantments());
    definitions.put(d.enchantment(), updated);
    try {
      Definitions.SERVER = new Definitions.Snapshot(definitions, old.rules(), old.revision() + 1);
      var e =
          RitualMath.evaluate(
              room.player,
              new ItemStack(Items.DIAMOND_SWORD),
              Map.of(d.enchantment(), 3),
              Set.of(),
              0,
              RitualNetwork.scan(h.getLevel(), room.table, false, false));
      h.assertTrue(
          e.ready() && e.lines().getFirst().power() == 99, "Stable entry resolves reloaded power");
    } finally {
      Definitions.SERVER = old;
      RitualNetwork.invalidate(h.getLevel());
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void differentActualItemsInSameTagContribute(GameTestHelper h) {
    var room = room(h);
    var old = Definitions.SERVER;
    var d = old.get(RitualGameTests.SHARP);
    var a =
        new Affinity(
            "ingots", Optional.empty(), Optional.of(ResourceLocation.parse("c:ingots")), 10, 1);
    var replacement =
        new RitualDefinition(
            d.enchantment(),
            List.of(a),
            Map.of("1", 15.0),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    var definitions = new TreeMap<>(old.enchantments());
    definitions.put(d.enchantment(), replacement);
    room.shelf.setItem(0, RitualGameTests.book("ingots"));
    room.pedestal.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
    h.setBlock(21, 1, 22, RitualsNotRolls.PEDESTAL.get());
    ((PedestalEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(21, 1, 22))))
        .items.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
    try {
      Definitions.SERVER = new Definitions.Snapshot(definitions, old.rules(), old.revision() + 1);
      var e =
          RitualMath.evaluate(
              room.player,
              new ItemStack(Items.DIAMOND_SWORD),
              Map.of(d.enchantment(), 1),
              Set.of(),
              0,
              RitualNetwork.scan(h.getLevel(), room.table, false, true));
      h.assertTrue(
          e.ready() && e.lines().getFirst().power() == 20,
          "Two actual ingots share one known affinity");
    } finally {
      Definitions.SERVER = old;
      RitualNetwork.invalidate(h.getLevel());
    }
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 180)
  public static void physicalDropChannelsAndCompletes(GameTestHelper h) {
    var room = room(h);
    var stack = new ItemStack(Items.DIAMOND_SWORD);
    var target =
        new ItemEntity(
            h.getLevel(),
            room.table.getX() + .5,
            room.table.getY() + 1,
            room.table.getZ() + .5,
            stack);
    target.setThrower(room.player);
    target.setPickUpDelay(20);
    h.getLevel().addFreshEntity(target);
    h.succeedWhen(
        () -> {
          h.assertTrue(
              RitualMath.enchantments(target.getItem()).size() == 1, "Physical ritual completes");
          h.assertTrue(
              room.pedestal.items.getStackInSlot(0).getCount() == 1,
              "Physical default ritual preserves materials");
        });
  }

  @GameTest(template = "network", timeoutTicks = 60)
  public static void removedTableAbortsCapture(GameTestHelper h) {
    var room = room(h);
    var stack = new ItemStack(Items.DIAMOND_SWORD);
    var target =
        new ItemEntity(
            h.getLevel(),
            room.table.getX() + .5,
            room.table.getY() + 1,
            room.table.getZ() + .5,
            stack);
    target.setThrower(room.player);
    h.getLevel().addFreshEntity(target);
    h.runAfterDelay(12, () -> h.getLevel().setBlock(room.table, Blocks.AIR.defaultBlockState(), 3));
    h.runAfterDelay(
        25,
        () -> {
          h.assertTrue(
              RitualEngine.state(h.getLevel(), room.table).equals("idle"),
              "Missing table releases session");
          h.assertTrue(
              target.isAlive() && RitualMath.enchantments(target.getItem()).isEmpty(),
              "Target preserved unenchanted");
          h.assertTrue(
              room.pedestal.items.getStackInSlot(0).getCount() == 1, "No spending on abort");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void tableUpgradePersistsAndDrops(GameTestHelper h) {
    var room = room(h);
    var be = h.getLevel().getBlockEntity(room.table);
    be.setData(RitualsNotRolls.ASSEMBLY, 5);
    var nbt = be.saveWithFullMetadata(h.getLevel().registryAccess());
    var loaded =
        net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            room.table, be.getBlockState(), nbt, h.getLevel().registryAccess());
    h.assertTrue(
        loaded != null && loaded.getData(RitualsNotRolls.ASSEMBLY) == 5,
        "Attachment saves and loads");
    var drop =
        new ItemEntity(
            h.getLevel(),
            room.table.getX(),
            room.table.getY(),
            room.table.getZ(),
            new ItemStack(Items.ENCHANTING_TABLE));
    var event =
        new net.neoforged.neoforge.event.level.BlockDropsEvent(
            h.getLevel(),
            room.table,
            be.getBlockState(),
            be,
            new ArrayList<>(List.of(drop)),
            room.player,
            new ItemStack(Items.DIAMOND_PICKAXE));
    CommonEvents.drops(event);
    var enchant =
        h.getLevel()
            .registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT)
            .getHolder(RitualsNotRolls.id("arcane_assembly"))
            .orElseThrow();
    h.assertTrue(
        RitualMath.enchantments(drop.getItem()).getLevel(enchant) == 5,
        "Dropped table keeps upgrade");
    be.setData(RitualsNotRolls.ASSEMBLY, 0);
    Blocks.ENCHANTING_TABLE.setPlacedBy(
        h.getLevel(), room.table, be.getBlockState(), room.player, drop.getItem());
    h.assertTrue(
        be.getData(RitualsNotRolls.ASSEMBLY) == 5, "Placed enchanted table restores upgrade");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void alternateMenuProviderUsesRitual(GameTestHelper h) {
    var room = room(h);
    var provider = h.getLevel().getBlockState(room.table).getMenuProvider(h.getLevel(), room.table);
    h.assertTrue(
        provider.createMenu(14, room.player.getInventory(), room.player) instanceof RitualMenu,
        "Table provider never exposes vanilla enchanting menu");
    h.succeed();
  }
}
