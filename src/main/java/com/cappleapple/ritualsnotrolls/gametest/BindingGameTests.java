package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class BindingGameTests {
  private record Room(
      GameTestHelper h, ServerPlayer player, BlockPos table, ChiseledBookShelfBlockEntity shelf) {
    ItemEntity drop(ItemStack stack) {
      return dropAt(stack, Vec3.atBottomCenterOf(table).add(0, 1.1, 0), player);
    }

    ItemEntity dropAt(ItemStack stack, Vec3 at, ServerPlayer owner) {
      var entity = new ItemEntity(h.getLevel(), at.x, at.y, at.z, stack);
      if (owner != null) entity.setThrower(owner);
      entity.setPickUpDelay(200);
      h.getLevel().addFreshEntity(entity);
      return entity;
    }

    List<ItemEntity> items() {
      return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(table).inflate(10));
    }

    int loose(Item item) {
      return items().stream()
          .filter(e -> e.isAlive() && e.getItem().is(item))
          .mapToInt(e -> e.getItem().getCount())
          .sum();
    }

    List<ItemStack> books() {
      var books = new ArrayList<ItemStack>();
      for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
        if (shelf.getItem(slot).is(RitualsNotRolls.BOOK)) books.add(shelf.getItem(slot));
      }
      return books;
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

  private static ItemStack pages(String entry, int count) {
    var stack = RitualGameTests.page(entry);
    stack.setCount(count);
    return stack;
  }

  private static void assertReleased(GameTestHelper h, Room r) {
    h.assertTrue(
        r.items().stream().noneMatch(e -> KnowledgeTransfers.active(e) || e.isNoGravity()),
        "Every remaining ingredient or book has normal physics and no capture marker");
  }

  private static void assertSavedRecovery(GameTestHelper h, ItemEntity source) {
    var expected = source.getItem().copy();
    var saved = source.saveWithoutId(new CompoundTag());
    var recovered = new ItemEntity(h.getLevel(), 0, 0, 0, expected.copy());
    recovered.load(saved);
    RitualEngine.restoreCapturedItem(recovered);
    RitualEngine.restoreCapturedItem(recovered);
    h.assertTrue(
        !KnowledgeTransfers.active(recovered)
            && !recovered.isNoGravity()
            && ItemStack.matches(expected, recovered.getItem()),
        "Serialized recovery releases the exact saved stack idempotently");
  }

  @GameTest(template = "network", timeoutTicks = 120)
  public static void bindingConsumesOnePageAndExactlyThreeLeather(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 3));
    r.drop(new ItemStack(Items.LEATHER, 10));
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, page), "Binding starts");
    h.assertTrue(
        r.items().stream()
                .filter(e -> e.getItem().is(Items.LEATHER) && KnowledgeTransfers.active(e))
                .mapToInt(e -> e.getItem().getCount())
                .sum()
            == 3,
        "Only three real leather items participate in the animation");
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              page.getItem().is(RitualsNotRolls.PAGE)
                  && KnowledgeTransfers.active(page)
                  && r.loose(RitualsNotRolls.PAGE.get()) == 3
                  && r.loose(Items.LEATHER) == 10
                  && r.books().isEmpty(),
              "The animation retains all raw ingredients until binding completes");
        });
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              page.getItem().is(RitualsNotRolls.BOOK)
                  && KnowledgeTransfers.active(page)
                  && r.loose(RitualsNotRolls.PAGE.get()) == 2
                  && r.loose(Items.LEATHER) == 7
                  && r.books().isEmpty(),
              "One real book flies to the shelf after consuming one page and three leather");
          h.assertTrue(
              Knowledge.data(page.getItem()).entries().equals(List.of("diamond")),
              "The new book contains the source page entry");
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              r.books().size() == 1
                  && !page.isAlive()
                  && r.loose(RitualsNotRolls.PAGE.get()) == 2
                  && r.loose(Items.LEATHER) == 7,
              "One book reaches the library and all surplus ingredients remain");
          assertReleased(h, r);
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 80)
  public static void changedSurplusPageCancelsBindingWithoutConsumption(GameTestHelper h) {
    var r = room(h);
    var original = r.drop(pages("diamond", 3));
    r.drop(new ItemStack(Items.LEATHER, 3));
    var altered = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Consumer<EntityJoinLevelEvent> listener =
        event -> {
          if (event.getLevel() == h.getLevel()
              && event.getEntity() instanceof ItemEntity item
              && item != original
              && item.getOwner() == r.player
              && item.getItem().is(RitualsNotRolls.PAGE)) {
            item.setItem(item.getItem().copyWithCount(40));
            altered.incrementAndGet();
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
    try {
      h.assertTrue(
          KnowledgeTransfers.tryStart(h.getLevel(), r.table, original),
          "Binding starts before the surplus entity is created");
    } catch (RuntimeException | Error failure) {
      NeoForge.EVENT_BUS.unregister(listener);
      throw failure;
    }
    h.runAfterDelay(
        45,
        () -> {
          try {
            h.assertTrue(
                altered.get() == 1, "Fixture changed the surplus page count during entity join");
            h.assertTrue(
                original.isAlive() && ItemStack.matches(original.getItem(), pages("diamond", 3)),
                "Rejected surplus entity leaves the original page stack unchanged");
            h.assertTrue(
                r.loose(RitualsNotRolls.PAGE.get()) == 3 && r.loose(Items.LEATHER) == 3,
                "Failed binding retains every original page and leather item");
            h.assertTrue(
                r.books().isEmpty() && r.loose(RitualsNotRolls.BOOK.get()) == 0,
                "Changed surplus cannot create a book or duplicate pages");
            assertReleased(h, r);
          } finally {
            NeoForge.EVENT_BUS.unregister(listener);
          }
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 120)
  public static void bindingCombinesSeparateLeatherDrops(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 1));
    r.drop(new ItemStack(Items.LEATHER, 2));
    r.drop(new ItemStack(Items.LEATHER, 1));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Split leather binds");
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              r.books().size() == 1
                  && r.loose(Items.LEATHER) == 0
                  && r.loose(RitualsNotRolls.PAGE.get()) == 0,
              "Two leather plus one leather creates one book without loss or duplication");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void existingBookFilesPagesWithoutUsingLeather(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(
        0, Knowledge.book(new KnowledgeData(RitualGameTests.SHARP, List.of("diamond"), false)));
    var page = r.drop(pages("netherite_scrap", 2));
    var leather = r.drop(new ItemStack(Items.LEATHER, 8));
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, page), "Existing type accepts page");
    h.assertTrue(!KnowledgeTransfers.active(leather), "Existing books do not reserve leather");
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              r.books().size() == 1
                  && Knowledge.data(r.books().getFirst()).entries().contains("netherite_scrap")
                  && r.loose(RitualsNotRolls.PAGE.get()) == 1
                  && r.loose(Items.LEATHER) == 8,
              "The existing book gains one new entry without binding another book");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void duplicateInAnyLibraryBookLeavesAllItemsAlone(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.book("diamond"));
    r.shelf.setItem(1, RitualGameTests.book("netherite_scrap"));
    var page = r.drop(pages("netherite_scrap", 3));
    r.drop(new ItemStack(Items.LEATHER, 10));
    h.assertTrue(
        !RitualEngine.tryStart(h.getLevel(), r.table, page),
        "An entry already in another library book cannot be added to the first book");
    h.assertTrue(
        r.loose(RitualsNotRolls.PAGE.get()) == 3 && r.loose(Items.LEATHER) == 10,
        "Duplicate pages and all leather remain untouched");
    assertReleased(h, r);
    h.succeed();
  }

  @GameTest(template = "network")
  public static void insufficientLeatherOrFullLibraryCannotReserveIngredients(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 2));
    r.drop(new ItemStack(Items.LEATHER, 2));
    h.assertTrue(
        !KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Two leather is insufficient");
    assertReleased(h, r);
    r.drop(new ItemStack(Items.LEATHER, 1));
    for (int slot = 0; slot < r.shelf.getContainerSize(); slot++)
      r.shelf.setItem(slot, new ItemStack(Items.BOOK));
    h.assertTrue(
        !KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "A full library cannot bind");
    h.assertTrue(
        r.loose(RitualsNotRolls.PAGE.get()) == 2 && r.loose(Items.LEATHER) == 3,
        "Rejected binding keeps every input item");
    assertReleased(h, r);
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 160)
  public static void sharedTablesReserveOnlyOneNewBookForTheSameType(GameTestHelper h) {
    var r = room(h);
    var otherTable = r.table.offset(0, 0, 2);
    h.getLevel().setBlock(otherTable, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
    RitualNetwork.invalidate(h.getLevel());
    var page = r.drop(pages("diamond", 1));
    var second =
        r.dropAt(
            pages("netherite_scrap", 1),
            Vec3.atBottomCenterOf(otherTable).add(0, 1.1, 0),
            r.player);
    r.dropAt(
        new ItemStack(Items.LEATHER, 6), Vec3.atBottomCenterOf(r.table).add(0, 1.1, 1), r.player);
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "First binding starts");
    h.assertTrue(
        !KnowledgeTransfers.tryStart(h.getLevel(), otherTable, second),
        "A second table sharing the library cannot reserve another book of the same type");
    h.runAfterDelay(
        90,
        () -> {
          h.assertTrue(r.books().size() == 1, "Exactly one new book is shelved");
          if (second.isAlive() && !KnowledgeTransfers.active(second)) {
            second.setPos(Vec3.atBottomCenterOf(otherTable).add(0, 1.1, 0));
            h.assertTrue(
                KnowledgeTransfers.tryStart(h.getLevel(), otherTable, second),
                "The second page can file normally once the book exists");
          }
        });
    h.runAfterDelay(
        145,
        () -> {
          h.assertTrue(
              r.books().size() == 1
                  && Knowledge.data(r.books().getFirst())
                      .entries()
                      .containsAll(List.of("diamond", "netherite_scrap"))
                  && r.loose(Items.LEATHER) == 3
                  && r.loose(RitualsNotRolls.PAGE.get()) == 0,
              "Both different entries share one book and cost only three leather");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void appearingMatchingBookCancelsBindingBeforeConsumption(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 2));
    r.drop(new ItemStack(Items.LEATHER, 5));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Binding starts");
    r.shelf.setItem(0, RitualGameTests.book("diamond"));
    RitualNetwork.invalidate(h.getLevel());
    h.runAfterDelay(
        60,
        () -> {
          h.assertTrue(
              r.books().size() == 1
                  && r.loose(RitualsNotRolls.PAGE.get()) == 2
                  && r.loose(Items.LEATHER) == 5
                  && r.loose(RitualsNotRolls.BOOK.get()) == 0,
              "A newly added matching book releases all unconsumed ingredients");
          assertReleased(h, r);
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 120)
  public static void blockedShelfAfterBindingReleasesTheCompletedBook(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 1));
    r.drop(new ItemStack(Items.LEATHER, 3));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Binding starts");
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(page.getItem().is(RitualsNotRolls.BOOK), "Binding has completed");
          for (int slot = 0; slot < r.shelf.getContainerSize(); slot++)
            r.shelf.setItem(slot, new ItemStack(Items.BOOK));
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              r.books().isEmpty()
                  && r.loose(RitualsNotRolls.BOOK.get()) == 1
                  && r.loose(RitualsNotRolls.PAGE.get()) == 0
                  && r.loose(Items.LEATHER) == 0
                  && Knowledge.data(page.getItem()).entries().equals(List.of("diamond")),
              "A changed destination preserves the finished book without refunding spent inputs");
          assertReleased(h, r);
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 100)
  public static void removedTableReturnsEveryUnconsumedIngredient(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 2));
    r.drop(new ItemStack(Items.LEATHER, 5));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Binding starts");
    h.getLevel().setBlock(r.table, Blocks.AIR.defaultBlockState(), 3);
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              r.loose(RitualsNotRolls.PAGE.get()) == 2
                  && r.loose(Items.LEATHER) == 5
                  && r.loose(RitualsNotRolls.BOOK.get()) == 0
                  && r.books().isEmpty(),
              "Removing the table before binding consumes nothing");
          assertReleased(h, r);
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 120)
  public static void serializedBindingRecoversRawIngredientsAndCompletedBook(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 2));
    r.drop(new ItemStack(Items.LEATHER, 5));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Binding starts");
    var captured = r.items().stream().filter(KnowledgeTransfers::active).toList();
    h.assertTrue(
        captured.stream().anyMatch(e -> e.getItem().is(Items.LEATHER)),
        "Reserved leather is serialized as real captured item entities");
    captured.forEach(e -> assertSavedRecovery(h, e));
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              page.getItem().is(RitualsNotRolls.BOOK) && KnowledgeTransfers.active(page),
              "The finished book is a real captured entity during shelf flight");
          assertSavedRecovery(h, page);
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(r.books().size() == 1, "Reading saved copies does not alter live binding");
          h.succeed();
        });
  }

  @GameTest(template = "network", batch = "binding_unload", timeoutTicks = 100)
  public static void worldUnloadReleasesRawIngredientsThenCompletedBooks(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 2));
    r.drop(new ItemStack(Items.LEATHER, 5));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, page), "Binding starts");
    RitualEngine.unload(h.getLevel());
    h.assertTrue(
        r.loose(RitualsNotRolls.PAGE.get()) == 2 && r.loose(Items.LEATHER) == 5,
        "World unload before crafting preserves every raw ingredient");
    assertReleased(h, r);
    r.items().forEach(ItemEntity::discard);
    var nextPage = r.drop(pages("diamond", 1));
    r.drop(new ItemStack(Items.LEATHER, 3));
    h.assertTrue(
        KnowledgeTransfers.tryStart(h.getLevel(), r.table, nextPage), "Binding can restart");
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(nextPage.getItem().is(RitualsNotRolls.BOOK), "Book has finished binding");
          RitualEngine.unload(h.getLevel());
          h.assertTrue(
              r.loose(RitualsNotRolls.BOOK.get()) == 1
                  && r.loose(RitualsNotRolls.PAGE.get()) == 0
                  && r.loose(Items.LEATHER) == 0,
              "World unload after crafting releases the finished book exactly once");
          assertReleased(h, r);
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(
              r.books().isEmpty() && r.loose(RitualsNotRolls.BOOK.get()) == 1,
              "An unloaded flight does not later insert or duplicate its book");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void bindingOnlyUsesNearbyLeatherFromThePageOwner(GameTestHelper h) {
    var r = room(h);
    var page = r.drop(pages("diamond", 1));
    r.drop(new ItemStack(Items.LEATHER, 2));
    var other = RitualGameTests.player(h);
    other.setPos(Vec3.atCenterOf(r.table));
    var origin = Vec3.atBottomCenterOf(r.table).add(0, 1.1, 0);
    var strangers = r.dropAt(new ItemStack(Items.LEATHER, 3), origin, other);
    var unowned = r.dropAt(new ItemStack(Items.LEATHER, 3), origin, null);
    var distant = r.dropAt(new ItemStack(Items.LEATHER, 3), origin.add(5, 0, 0), r.player);
    h.assertTrue(
        !KnowledgeTransfers.tryStart(h.getLevel(), r.table, page),
        "Foreign, unowned, and distant leather cannot make up the missing ingredient");
    r.drop(new ItemStack(Items.LEATHER, 1));
    h.assertTrue(
        KnowledgeTransfers.tryStart(h.getLevel(), r.table, page),
        "The owner's third leather binds");
    h.assertTrue(
        !KnowledgeTransfers.active(strangers)
            && !KnowledgeTransfers.active(unowned)
            && !KnowledgeTransfers.active(distant),
        "Unrelated leather is never reserved");
    h.succeed();
  }
}
