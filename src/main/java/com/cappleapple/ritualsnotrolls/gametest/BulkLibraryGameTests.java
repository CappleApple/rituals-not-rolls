package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
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
public final class BulkLibraryGameTests {
  private static final class Room {
    final GameTestHelper h;
    final ServerPlayer player;
    final BlockPos table;
    final List<ChiseledBookShelfBlockEntity> shelves = new ArrayList<>();

    Room(GameTestHelper h) {
      this.h = h;
      h.setBlock(20, 1, 20, Blocks.ENCHANTING_TABLE);
      table = h.absolutePos(new BlockPos(20, 1, 20));
      player = RitualGameTests.player(h);
      player.setPos(Vec3.atCenterOf(table));
      for (int i = 0; i < 4; i++) {
        var pos = table.offset(3 + i, 0, 0);
        h.getLevel().setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
        shelves.add((ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(pos));
      }
      RitualNetwork.invalidate(h.getLevel());
    }

    ItemEntity drop(ItemStack stack) {
      var p = Vec3.atBottomCenterOf(table).add(0, 1.1, 0);
      var entity = new ItemEntity(h.getLevel(), p.x, p.y, p.z, stack);
      entity.setThrower(player);
      entity.setPickUpDelay(200);
      h.getLevel().addFreshEntity(entity);
      return entity;
    }

    List<ItemEntity> items() {
      return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(table).inflate(12));
    }

    int loose(Item item) {
      return items().stream()
          .filter(e -> e.isAlive() && e.getItem().is(item))
          .mapToInt(e -> e.getItem().getCount())
          .sum();
    }

    long books() {
      return shelves.stream()
          .flatMap(s -> java.util.stream.IntStream.range(0, 6).mapToObj(s::getItem))
          .filter(s -> s.is(RitualsNotRolls.BOOK))
          .count();
    }

    List<ItemStack> pages(int count, boolean existingBooks) {
      var definitions =
          Definitions.SERVER.enchantments().values().stream()
              .filter(
                  d ->
                      d.enchantment().getNamespace().equals("minecraft")
                          && !d.materials().isEmpty())
              .limit(count)
              .toList();
      h.assertTrue(
          definitions.size() == count, "Fixture uses real bundled enchantment definitions");
      List<ItemStack> pages = new ArrayList<>();
      for (int i = 0; i < definitions.size(); i++) {
        var d = definitions.get(i);
        pages.add(Knowledge.page(d.enchantment(), d.materials().getFirst().id()));
        if (existingBooks)
          shelves
              .get(i / 6)
              .setItem(i % 6, Knowledge.book(new KnowledgeData(d.enchantment(), List.of(), true)));
      }
      return pages;
    }

    ItemStack binder(List<ItemStack> pages) {
      var binder = new ItemStack(RitualsNotRolls.BINDER_ITEM.get());
      for (var page : pages)
        h.assertTrue(
            BinderStorage.insert(binder, page.copy(), false, 1) == 1, "Fixture inserts one page");
      return binder;
    }
  }

  @GameTest(template = "network", timeoutTicks = 140)
  public static void severalPagesFileIntoOneBookConcurrently(GameTestHelper h) {
    var r = new Room(h);
    var definition = Definitions.SERVER.get(RitualGameTests.SHARP);
    r.shelves.getFirst().setItem(0, RitualGameTests.book());
    var pages =
        definition.materials().stream()
            .map(a -> r.drop(Knowledge.page(RitualGameTests.SHARP, a.id())))
            .toList();
    h.assertTrue(pages.size() > 3, "Fixture has several distinct discoveries");
    for (var page : pages)
      h.assertTrue(
          KnowledgeTransfers.tryStart(h.getLevel(), r.table, page),
          "Each page starts immediately, without waiting for the previous flight");
    h.assertTrue(pages.stream().allMatch(KnowledgeTransfers::active), "All pages fly concurrently");
    h.runAfterDelay(
        60,
        () -> {
          var entries = Knowledge.data(r.shelves.getFirst().getItem(0)).entries();
          h.assertTrue(
              entries.size() == pages.size(), "Concurrent arrivals retain every discovery");
          h.assertTrue(
              pages.stream().noneMatch(ItemEntity::isAlive), "Each submitted page consumed once");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 160)
  public static void twentyDifferentBooksBindConcurrently(GameTestHelper h) {
    var r = new Room(h);
    var pages = r.pages(20, false).stream().map(r::drop).toList();
    r.drop(new ItemStack(Items.LEATHER, 64));
    for (var page : pages)
      h.assertTrue(
          KnowledgeTransfers.tryStart(h.getLevel(), r.table, page),
          "Every different enchantment reserves its binding immediately");
    h.assertTrue(
        r.items().stream()
                .filter(KnowledgeTransfers::active)
                .filter(e -> e.getItem().is(Items.LEATHER))
                .mapToInt(e -> e.getItem().getCount())
                .sum()
            == 60,
        "Twenty concurrent bindings reserve exactly sixty leather");
    h.runAfterDelay(
        110,
        () -> {
          h.assertTrue(
              r.books() == 20 && r.loose(Items.LEATHER) == 4,
              "All books arrive and surplus leather is retained");
          h.assertTrue(r.loose(RitualsNotRolls.PAGE.get()) == 0, "All twenty pages are bound");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 180)
  public static void binderMetersSubmissionsAndFilesEveryPage(GameTestHelper h) {
    var r = new Room(h);
    var binder = r.drop(r.binder(r.pages(20, true)));
    h.assertTrue(BinderStorage.pagesPerSecond() == 16, "Default processing budget is sixteen");
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, binder), "Thrown binder starts");
    KnowledgeTransfers.tick(h.getLevel());
    h.assertTrue(BinderStorage.data(binder.getItem()).total() == 4, "First batch submits sixteen");
    h.runAfterDelay(
        10,
        () ->
            h.assertTrue(
                BinderStorage.data(binder.getItem()).total() == 4,
                "No second batch before twenty ticks"));
    h.runAfterDelay(
        120,
        () -> {
          h.assertTrue(
              BinderStorage.data(binder.getItem()).total() == 0
                  && !KnowledgeTransfers.active(binder),
              "Finished binder is empty and released");
          for (var shelf : r.shelves)
            for (int slot = 0; slot < 6; slot++) {
              var book = shelf.getItem(slot);
              if (book.is(RitualsNotRolls.BOOK))
                h.assertTrue(
                    Knowledge.data(book).entries().size() == 1,
                    "Each existing book receives its discovery");
            }
          h.assertTrue(
              r.loose(RitualsNotRolls.PAGE.get()) == 0, "No submitted page lost or left behind");
          h.succeed();
        });
  }

  @GameTest(batch = "binder_configured_rate", template = "network", timeoutTicks = 140)
  public static void binderUsesConfiguredBatchBudget(GameTestHelper h) {
    var r = new Room(h);
    var binder = r.drop(r.binder(r.pages(5, true)));
    int previous = Config.BINDER_PAGES_PER_SECOND.get();
    Config.BINDER_PAGES_PER_SECOND.set(2);
    try {
      h.assertTrue(
          KnowledgeTransfers.tryStart(h.getLevel(), r.table, binder), "Configured binder starts");
      KnowledgeTransfers.tick(h.getLevel());
      h.assertTrue(
          BinderStorage.data(binder.getItem()).total() == 3,
          "Nondefault first batch submits exactly two pages");
    } catch (RuntimeException | Error failure) {
      Config.BINDER_PAGES_PER_SECOND.set(previous);
      throw failure;
    }
    h.runAfterDelay(
        10,
        () -> {
          try {
            h.assertTrue(
                BinderStorage.data(binder.getItem()).total() == 3,
                "Configured binder waits the full twenty ticks between batches");
          } catch (RuntimeException | Error failure) {
            Config.BINDER_PAGES_PER_SECOND.set(previous);
            throw failure;
          }
        });
    h.runAfterDelay(
        20,
        () -> {
          try {
            KnowledgeTransfers.tick(h.getLevel());
            h.assertTrue(
                BinderStorage.data(binder.getItem()).total() == 1,
                "Second configured batch submits exactly two more pages");
          } finally {
            Config.BINDER_PAGES_PER_SECOND.set(previous);
          }
        });
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(
              BinderStorage.data(binder.getItem()).total() == 0
                  && !KnowledgeTransfers.active(binder),
              "Every page eventually files and the binder is released");
          int learned = 0;
          for (var shelf : r.shelves)
            for (int slot = 0; slot < 6; slot++) {
              var knowledge = Knowledge.data(shelf.getItem(slot));
              if (knowledge != null) learned += knowledge.entries().size();
            }
          h.assertTrue(
              learned == 5 && r.loose(RitualsNotRolls.PAGE.get()) == 0,
              "Configured batches deliver every page exactly once");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 60)
  public static void interruptedBinderConservesStoredAndSubmittedPages(GameTestHelper h) {
    var r = new Room(h);
    var pages = r.pages(20, true);
    var binder = r.drop(r.binder(pages));
    h.assertTrue(
        KnowledgeTransfers.tryStart(h.getLevel(), r.table, binder),
        "Binder starts before interruption");
    KnowledgeTransfers.tick(h.getLevel());
    h.assertTrue(
        BinderStorage.data(binder.getItem()).total() == 4,
        "One default batch is already in flight");
    h.getLevel().setBlock(r.table, Blocks.AIR.defaultBlockState(), 3);
    KnowledgeTransfers.tick(h.getLevel());
    h.assertTrue(!KnowledgeTransfers.active(binder), "Breaking the table releases the binder");
    h.assertTrue(
        r.items().stream().noneMatch(KnowledgeTransfers::active),
        "Breaking the table releases every submitted page");
    h.runAfterDelay(
        5,
        () -> {
          h.assertTrue(
              BinderStorage.data(binder.getItem()).total() == 4
                  && r.loose(RitualsNotRolls.PAGE.get()) == 16,
              "Stored pages and released in-flight pages retain all twenty inputs");
          for (ItemStack original : pages) {
            int stored =
                BinderStorage.data(binder.getItem()).entries().stream()
                    .filter(entry -> ItemStack.isSameItemSameComponents(entry.page(), original))
                    .mapToInt(BinderData.Entry::count)
                    .sum();
            int loose =
                r.items().stream()
                    .filter(
                        entity ->
                            entity.isAlive()
                                && ItemStack.isSameItemSameComponents(entity.getItem(), original))
                    .mapToInt(entity -> entity.getItem().getCount())
                    .sum();
            h.assertTrue(
                stored + loose == 1,
                "Each individual original page survives interruption exactly once");
          }
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void alteredGeneratedBinderPageCannotDebitStorage(GameTestHelper h) {
    var r = new Room(h);
    var binder = r.drop(r.binder(List.of(RitualGameTests.page("diamond"))));
    r.drop(new ItemStack(Items.LEATHER, 3));
    var altered = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Consumer<EntityJoinLevelEvent> listener =
        event -> {
          if (event.getLevel() == h.getLevel()
              && event.getEntity() instanceof ItemEntity item
              && item.getOwner() == r.player
              && item.getItem().is(RitualsNotRolls.PAGE)) {
            item.setItem(item.getItem().copyWithCount(2));
            altered.incrementAndGet();
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
    try {
      h.assertTrue(
          KnowledgeTransfers.tryStart(h.getLevel(), r.table, binder),
          "Binder begins inspection with a scoped join callback");
      KnowledgeTransfers.tick(h.getLevel());
      h.assertTrue(
          altered.get() == 1, "Fixture altered the newly generated page entity during join");
      h.assertTrue(
          BinderStorage.data(binder.getItem()).total() == 1,
          "Changed generated pages never debit the source binder");
      h.assertTrue(
          r.loose(RitualsNotRolls.PAGE.get()) == 0
              && r.books() == 0
              && r.loose(RitualsNotRolls.BOOK.get()) == 0,
          "Rejected generated pages cannot become loose duplicates or books");
      h.assertTrue(
          r.loose(Items.LEATHER) == 3 && r.items().stream().noneMatch(KnowledgeTransfers::active),
          "Rejected submission preserves leather and releases the binder");
    } finally {
      NeoForge.EVENT_BUS.unregister(listener);
    }
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 200)
  public static void binderCreatesMissingBooksWithLooseLeather(GameTestHelper h) {
    var r = new Room(h);
    var binder = r.drop(r.binder(r.pages(20, false)));
    r.drop(new ItemStack(Items.LEATHER, 64));
    h.assertTrue(
        KnowledgeTransfers.tryStart(h.getLevel(), r.table, binder), "Binder starts binding");
    h.runAfterDelay(
        150,
        () -> {
          h.assertTrue(
              r.books() == 20 && r.loose(Items.LEATHER) == 4,
              "Metered pages bind all twenty missing books with sixty leather");
          h.assertTrue(
              BinderStorage.data(binder.getItem()).total() == 0
                  && !KnowledgeTransfers.active(binder),
              "Binder returns after all new books are filed");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 80)
  public static void binderKeepsDuplicateAndUnusablePages(GameTestHelper h) {
    var r = new Room(h);
    r.shelves.getFirst().setItem(0, RitualGameTests.book("diamond"));
    var pages =
        List.of(
            RitualGameTests.page("diamond"), Knowledge.page(RitualGameTests.UNBREAKING, "diamond"));
    var binder = r.drop(r.binder(pages));
    h.assertTrue(KnowledgeTransfers.tryStart(h.getLevel(), r.table, binder), "Binder is inspected");
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              BinderStorage.data(binder.getItem()).total() == 2
                  && !KnowledgeTransfers.active(binder),
              "Duplicate and missing-leather pages stay in returned binder");
          h.assertTrue(
              r.books() == 1 && r.loose(RitualsNotRolls.PAGE.get()) == 0,
              "Unusable pages never become loose duplicates");
          h.succeed();
        });
  }
}
