package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class LibraryGameTests {
  private record Room(
      GameTestHelper h, ServerPlayer player, BlockPos table, ChiseledBookShelfBlockEntity shelf) {
    ItemEntity drop(ItemStack stack) {
      var p = Vec3.atBottomCenterOf(table).add(0, 1.1, 0);
      var entity = new ItemEntity(h.getLevel(), p.x, p.y, p.z, stack);
      entity.setThrower(player);
      entity.setPickUpDelay(100);
      h.getLevel().addFreshEntity(entity);
      return entity;
    }

    List<ItemEntity> items() {
      return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(table).inflate(10));
    }
  }

  private static Room room(GameTestHelper h) {
    h.setBlock(20, 1, 20, Blocks.ENCHANTING_TABLE);
    h.setBlock(23, 1, 20, Blocks.CHISELED_BOOKSHELF);
    var table = h.absolutePos(new BlockPos(20, 1, 20));
    var player = RitualGameTests.player(h);
    player.setPos(Vec3.atCenterOf(table));
    var shelf = (ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(table.offset(3, 0, 0));
    RitualNetwork.invalidate(h.getLevel());
    return new Room(h, player, table, shelf);
  }

  @GameTest(template = "network")
  public static void allShelfFlightOriginsMatchVanillaSlots(GameTestHelper h) throws Exception {
    var r = room(h);
    var block = (net.minecraft.world.level.block.ChiseledBookShelfBlock) Blocks.CHISELED_BOOKSHELF;
    var hitSlot =
        block
            .getClass()
            .getDeclaredMethod(
                "getHitSlot",
                BlockHitResult.class,
                net.minecraft.world.level.block.state.BlockState.class);
    hitSlot.setAccessible(true);
    for (Direction facing : Direction.Plane.HORIZONTAL) {
      var state =
          Blocks.CHISELED_BOOKSHELF
              .defaultBlockState()
              .setValue(
                  net.minecraft.world.level.block.state.properties.BlockStateProperties
                      .HORIZONTAL_FACING,
                  facing);
      h.getLevel().setBlock(r.shelf.getBlockPos(), state, 3);
      for (int slot = 0; slot < 6; slot++) {
        r.shelf.setItem(slot, RitualGameTests.book("diamond"));
        RitualNetwork.invalidate(h.getLevel());
        h.assertTrue(
            KnowledgeTransfers.retrieve(r.player, r.table, RitualGameTests.SHARP),
            "Retrieval starts");
        var entity = r.items().getFirst();
        var result =
            (OptionalInt)
                hitSlot.invoke(
                    block,
                    new BlockHitResult(entity.position(), facing, r.shelf.getBlockPos(), false),
                    state);
        h.assertTrue(
            result.isPresent() && result.getAsInt() == slot,
            "Animation must hit vanilla slot " + slot + " facing " + facing + ", got " + result);
        entity.discard();
      }
    }
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void retrievalPrefersBookAndFliesBeforeDropping(GameTestHelper h) {
    var r = room(h);
    var book = RitualGameTests.book("diamond");
    r.shelf.setItem(0, RitualGameTests.page("diamond"));
    r.shelf.setItem(1, book);
    h.assertTrue(
        KnowledgeTransfers.retrieve(r.player, r.table, RitualGameTests.SHARP), "Retrieved");
    h.assertTrue(
        r.shelf.getItem(1).isEmpty() && r.shelf.getItem(0).is(RitualsNotRolls.PAGE),
        "Book preferred over loose page");
    var entity = r.items().getFirst();
    var start = entity.position();
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(
              entity.isNoGravity() && entity.position().distanceToSqr(start) > .1,
              "Real book travels during animation");
        });
    h.runAfterDelay(
        28,
        () -> {
          h.assertTrue(
              entity.isAlive()
                  && !entity.isNoGravity()
                  && ItemStack.matches(entity.getItem(), book),
              "Book released intact");
          h.assertTrue(
              Math.abs(entity.getX() - (r.table.getX() + .5)) < .2
                  && Math.abs(entity.getZ() - (r.table.getZ() + .5)) < .2,
              "Book delivered above table");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void retrievalTakesOnePageAndMenuRejectsUnknown(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.page("diamond"));
    r.shelf.setItem(1, RitualGameTests.page("diamond"));
    var menu = new RitualMenu(2, r.player.getInventory(), r.table);
    menu.action("retrieve", "minecraft:protection");
    h.assertTrue(r.items().isEmpty(), "Cannot retrieve an enchantment not in this library");
    menu.action("retrieve", RitualGameTests.SHARP.toString());
    h.assertTrue(
        r.items().size() == 1
            && r.items().getFirst().getItem().getCount() == 1
            && r.shelf.getItem(1).is(RitualsNotRolls.PAGE),
        "One page per click");
    menu.action("retrieve", RitualGameTests.SHARP.toString());
    h.assertTrue(r.items().size() == 1, "Duplicate packets cannot drain shelf in same tick");
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void thrownPageMergesOneAtArrivalAndLeavesRemainder(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.book("diamond"));
    var page = RitualGameTests.page("netherite_scrap");
    page.setCount(3);
    var entity = r.drop(page);
    h.assertTrue(
        RitualEngine.tryStart(h.getLevel(), r.table, entity), "Stack of pages starts filing");
    h.assertTrue(
        Knowledge.data(r.shelf.getItem(0)).entries().size() == 1
            && entity.getItem().getCount() == 3,
        "No early mutation");
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              Knowledge.data(r.shelf.getItem(0)).entries().contains("netherite_scrap"),
              "Page filed");
          h.assertTrue(
              entity.isAlive() && entity.getItem().getCount() == 2 && !entity.isNoGravity(),
              "Only one consumed; excess released");
          h.assertTrue(
              !KnowledgeTransfers.tryStart(h.getLevel(), r.table, entity),
              "Duplicate cannot start another transfer");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void duplicatePageAndFullShelfDoNothing(GameTestHelper h) {
    var r = room(h);
    for (int slot = 0; slot < 6; slot++) r.shelf.setItem(slot, RitualGameTests.book("diamond"));
    var page = r.drop(RitualGameTests.page("diamond"));
    var book = r.drop(RitualGameTests.book("netherite_scrap"));
    h.assertTrue(
        !KnowledgeTransfers.tryStart(h.getLevel(), r.table, page)
            && !KnowledgeTransfers.tryStart(h.getLevel(), r.table, book),
        "Duplicate page and no slot leave items alone");
    h.assertTrue(!page.isNoGravity() && !book.isNoGravity(), "Physics untouched");
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void thrownBooksReserveEmptySlotsAndInsertAtArrival(GameTestHelper h) {
    var r = room(h);
    for (int slot = 0; slot < 4; slot++) r.shelf.setItem(slot, new ItemStack(Items.BOOK));
    var first = r.drop(RitualGameTests.book("diamond"));
    var second = r.drop(RitualGameTests.book("netherite_scrap"));
    var third = r.drop(RitualGameTests.book("iron_ingot"));
    h.assertTrue(
        KnowledgeTransfers.tryStart(h.getLevel(), r.table, first)
            && KnowledgeTransfers.tryStart(h.getLevel(), r.table, second),
        "Two open slots reserved");
    h.assertTrue(
        !KnowledgeTransfers.tryStart(h.getLevel(), r.table, third),
        "Cannot double-book reserved slot");
    h.assertTrue(
        r.shelf.getItem(4).isEmpty() && r.shelf.getItem(5).isEmpty(),
        "Shelf stays empty during flight");
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              !first.isAlive() && !second.isAlive() && third.isAlive(),
              "Only successfully filed entities removed");
          h.assertTrue(
              r.shelf.getItem(4).is(RitualsNotRolls.BOOK)
                  && r.shelf.getItem(5).is(RitualsNotRolls.BOOK),
              "Both books in distinct slots");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void changedDestinationDoesNotDestroyPage(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.book("diamond"));
    var page = r.drop(RitualGameTests.page("netherite_scrap"));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Page captured");
    var replacement = RitualGameTests.book("iron_ingot");
    r.shelf.setItem(0, replacement);
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              page.isAlive() && page.getItem().getCount() == 1 && !page.isNoGravity(),
              "Changed book releases page");
          h.assertTrue(
              ItemStack.matches(r.shelf.getItem(0), replacement), "Replacement book unchanged");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void savedFlightRestoresGravityWithoutChangingKnowledge(GameTestHelper h) {
    var r = room(h);
    var book = r.drop(RitualGameTests.book("diamond"));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, book), "Book captured");
    var saved = book.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    var recovered = new ItemEntity(h.getLevel(), 0, 0, 0, book.getItem().copy());
    recovered.load(saved);
    RitualEngine.restoreCapturedItem(recovered);
    RitualEngine.restoreCapturedItem(recovered);
    h.assertTrue(
        !recovered.isNoGravity()
            && !KnowledgeTransfers.active(recovered)
            && ItemStack.matches(recovered.getItem(), book.getItem()),
        "Save recovery idempotently releases intact item");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void enchantedBookCraftingKeepsOtherEnchantments(GameTestHelper h) {
    var player = RitualGameTests.player(h);
    var book =
        RitualMath.apply(
            player,
            new ItemStack(Items.BOOK),
            Map.of(RitualGameTests.SHARP, 7, RitualGameTests.UNBREAKING, 3));
    book.set(DataComponents.CUSTOM_NAME, Component.literal("Keep my name"));
    var input =
        CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, book, ItemStack.EMPTY, ItemStack.EMPTY));
    var recipe = new EnchantedBookPageRecipe(CraftingBookCategory.MISC);
    h.assertTrue(
        recipe.matches(input, h.getLevel()), "Enchanted book alone fits inventory crafting");
    var output = recipe.assemble(input, h.getLevel().registryAccess());
    h.assertTrue(
        output.is(RitualsNotRolls.PAGE)
            && Knowledge.data(output).enchantment().equals(RitualGameTests.SHARP)
            && Definitions.SERVER
                    .get(RitualGameTests.SHARP)
                    .affinity(Knowledge.data(output).entries().getFirst())
                != null,
        "Random page belongs to the first configured enchantment");
    var remaining = recipe.getRemainingItems(input).getFirst();
    h.assertTrue(
        remaining.is(Items.ENCHANTED_BOOK)
            && RitualMath.enchantments(remaining).size() == 1
            && RitualMath.enchantments(remaining)
                    .getLevel(
                        h.getLevel()
                            .registryAccess()
                            .registryOrThrow(Registries.ENCHANTMENT)
                            .getHolder(RitualGameTests.UNBREAKING)
                            .orElseThrow())
                == 3,
        "Other enchantment and level retained");
    h.assertTrue(
        remaining.getHoverName().getString().equals("Keep my name"),
        "Other book components retained");
    h.assertTrue(
        !recipe.matches(
            CraftingInput.of(2, 1, List.of(book, new ItemStack(Items.PAPER))), h.getLevel()),
        "Extra ingredient rejected");
    h.assertTrue(
        h.getLevel()
            .getRecipeManager()
            .byKey(RitualsNotRolls.id("enchanted_book_page"))
            .isPresent(),
        "Recipe loaded from data");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void directDropsConvertButPlayerRitualTargetsStayBooks(GameTestHelper h) {
    var r = room(h);
    var book =
        RitualMath.apply(
            r.player,
            new ItemStack(Items.BOOK),
            Map.of(RitualGameTests.SHARP, 3, RitualGameTests.UNBREAKING, 2));
    var playerDrop = r.drop(book.copy());
    h.assertTrue(playerDrop.getItem().is(Items.ENCHANTED_BOOK), "Player-thrown target preserved");
    var drop =
        new ItemEntity(
            h.getLevel(), r.table.getX(), r.table.getY() + 1, r.table.getZ(), book.copy());
    drop.setPickUpDelay(40);
    h.getLevel().addFreshEntity(drop);
    h.assertTrue(drop.getItem().is(RitualsNotRolls.PAGE), "Direct non-player drop converted");
    var pages = r.items().stream().filter(e -> e.getItem().is(RitualsNotRolls.PAGE)).toList();
    h.assertTrue(
        pages.size() == 2
            && pages.stream().map(e -> Knowledge.data(e.getItem()).enchantment()).distinct().count()
                == 2,
        "One associated page for each configured enchantment");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void nonEnchantableHeldItemsShowWholeLibrary(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.book("diamond"));
    r.shelf.setItem(
        1,
        Knowledge.page(
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("protection"),
            "diamond"));
    var empty = new RitualMenu(1, r.player.getInventory(), r.table).snapshot();
    var stone =
        new RitualMenu(2, r.player.getInventory(), r.table, new ItemStack(Items.STONE)).snapshot();
    h.assertTrue(
        !stone.getBoolean("filtered")
            && stone.getList("knowledge", 10).equals(empty.getList("knowledge", 10)),
        "Stone shows the same full library as an empty hand");
    var sword =
        new RitualMenu(3, r.player.getInventory(), r.table, new ItemStack(Items.DIAMOND_SWORD))
            .snapshot();
    h.assertTrue(
        sword.getBoolean("filtered") && sword.getList("knowledge", 10).size() == 1,
        "Enchantable sword still filters");
    h.succeed();
  }
}
