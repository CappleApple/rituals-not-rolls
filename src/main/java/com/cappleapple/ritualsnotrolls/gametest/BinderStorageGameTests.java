package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.Config;
import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.BinderData;
import com.cappleapple.ritualsnotrolls.knowledge.BinderStorage;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class BinderStorageGameTests {
  private static ItemStack binder() {
    return new ItemStack(RitualsNotRolls.BINDER_ITEM.get());
  }

  @GameTest(template = "empty")
  public static void doubleClickGathersWithAutoCollectOff(GameTestHelper h) {
    var player = RitualGameTests.player(h);
    var binder = binder();
    binder.set(RitualsNotRolls.BINDER_DATA, BinderData.EMPTY.withAutoCollect(false));
    player.getInventory().setItem(9, RitualGameTests.page("diamond").copyWithCount(4));
    player
        .getInventory()
        .setItem(10, Knowledge.page(RitualGameTests.UNBREAKING, "diamond").copyWithCount(3));
    var chest = new net.minecraft.world.SimpleContainer(27);
    chest.setItem(0, RitualGameTests.page("diamond").copyWithCount(6));
    chest.setItem(1, RitualGameTests.page("flint").copyWithCount(5));
    var menu = net.minecraft.world.inventory.ChestMenu.threeRows(81, player.getInventory(), chest);
    player.containerMenu = menu;
    menu.setCarried(binder);
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL, player);
    h.assertTrue(
        menu.getCarried() == binder && dataTotal(binder) == 3,
        "Cursor binder collects one of each discovery across enchantments");
    h.assertTrue(
        chest.getItem(0).getCount() == 5
            && chest.getItem(1).getCount() == 4
            && player.getInventory().getItem(9).getCount() == 4
            && player.getInventory().getItem(10).getCount() == 2,
        "Duplicate copies stay in their inventories");
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL, player);
    h.assertTrue(
        dataTotal(binder) == 3 && !BinderStorage.data(binder).autoCollect(),
        "Repeated gather respects filter and preserves auto setting");
    binder.set(RitualsNotRolls.BINDER_DATA, BinderStorage.data(binder).withFilterDuplicates(false));
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL, player);
    h.assertTrue(
        dataTotal(binder) == 18
            && chest.isEmpty()
            && player.getInventory().getItem(9).isEmpty()
            && player.getInventory().getItem(10).isEmpty(),
        "Filter off gathers every remaining page");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void doubleClickRespectsRemainingCapacity(GameTestHelper h) {
    var player = RitualGameTests.player(h);
    var binder = binder();
    binder.set(
        RitualsNotRolls.BINDER_DATA,
        new BinderData(
            List.of(
                new BinderData.Entry(RitualGameTests.page("flint"), BinderStorage.capacity() - 2)),
            false,
            false));
    var pages = RitualGameTests.page("diamond").copyWithCount(9);
    player.getInventory().setItem(9, pages);
    var menu = player.inventoryMenu;
    menu.setCarried(binder);
    menu.clicked(9, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL, player);
    h.assertTrue(
        dataTotal(binder) == BinderStorage.capacity()
            && player.getInventory().getItem(9).getCount() == 7,
        "Gather fills only available capacity");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void doubleClickHonorsDeniedSlotsAndTakeCallbacks(GameTestHelper h) {
    var player = RitualGameTests.player(h);
    var binder = binder();
    player.getInventory().setItem(9, RitualGameTests.page("diamond").copyWithCount(4));
    var chest = new net.minecraft.world.SimpleContainer(27);
    chest.setItem(0, RitualGameTests.page("flint").copyWithCount(3));
    var menu = net.minecraft.world.inventory.ChestMenu.threeRows(82, player.getInventory(), chest);
    var takes = new int[1];
    menu.slots.set(
        0,
        new net.minecraft.world.inventory.Slot(chest, 0, 0, 0) {
          @Override
          public void onTake(net.minecraft.world.entity.player.Player owner, ItemStack stack) {
            takes[0]++;
            super.onTake(owner, stack);
          }
        });
    menu.slots.set(
        27,
        new net.minecraft.world.inventory.Slot(player.getInventory(), 9, 0, 0) {
          @Override
          public boolean mayPickup(net.minecraft.world.entity.player.Player owner) {
            return false;
          }
        });
    menu.setCarried(binder);
    menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP_ALL, player);
    h.assertTrue(
        dataTotal(binder) == 1 && takes[0] == 1 && chest.getItem(0).getCount() == 2,
        "Allowed slot uses onTake exactly once");
    h.assertTrue(
        player.getInventory().getItem(9).getCount() == 4,
        "Capability fallback cannot bypass denied player slot");
    h.succeed();
  }

  private static int dataTotal(ItemStack binder) {
    return BinderStorage.data(binder).total();
  }

  @GameTest(template = "empty")
  public static void duplicateFilterUsesKnowledgeIdentity(GameTestHelper h) {
    var binder = binder();
    h.assertTrue(
        BinderStorage.data(binder).autoCollect() && BinderStorage.data(binder).filterDuplicates(),
        "Both binder options default on");
    var pages = RitualGameTests.page("diamond").copyWithCount(8);
    h.assertTrue(
        BinderStorage.insert(binder, pages, true, 64) == 1 && pages.getCount() == 7,
        "Automatic collection takes one new page");
    pages.set(DataComponents.CUSTOM_NAME, Component.literal("A different page label"));
    h.assertTrue(
        BinderStorage.insert(binder, pages, true, 64) == 0 && pages.getCount() == 7,
        "Different item components do not bypass duplicate knowledge filtering");
    h.assertTrue(
        BinderStorage.insert(binder, pages, false, 64) == 7 && pages.isEmpty(),
        "Manual insertion preserves duplicate pages");
    h.assertTrue(
        BinderStorage.data(binder).total() == 8 && BinderStorage.data(binder).entries().size() == 2,
        "Distinct item components remain distinct stored entries");
    var otherEnchantment = Knowledge.page(RitualGameTests.UNBREAKING, "diamond").copyWithCount(4);
    h.assertTrue(
        BinderStorage.insert(binder, otherEnchantment, true, 64) == 1,
        "Same material under another enchantment is a different page");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void optionTogglesAndManualInsertion(GameTestHelper h) {
    var binder = binder();
    binder.set(RitualsNotRolls.BINDER_DATA, BinderStorage.data(binder).withAutoCollect(false));
    var pages = RitualGameTests.page("flint").copyWithCount(6);
    h.assertTrue(
        BinderStorage.insert(binder, pages, true, 6) == 0 && pages.getCount() == 6,
        "Disabled auto collection leaves pages unchanged");
    h.assertTrue(
        BinderStorage.insert(binder, pages, false, 2) == 2 && pages.getCount() == 4,
        "Manual insertion ignores auto setting and respects the requested limit");
    binder.set(
        RitualsNotRolls.BINDER_DATA,
        BinderStorage.data(binder).withAutoCollect(true).withFilterDuplicates(false));
    h.assertTrue(
        BinderStorage.insert(binder, pages, true, 64) == 4 && pages.isEmpty(),
        "Disabling filtering collects remaining duplicates");
    h.assertTrue(BinderStorage.data(binder).total() == 6, "All six pages are retained");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void capacityLimitsDoNotDeleteStoredPages(GameTestHelper h) {
    int previous = Config.BINDER_PAGE_CAPACITY.get();
    try {
      Config.BINDER_PAGE_CAPACITY.set(4);
      var binder = binder();
      var pages = RitualGameTests.page("diamond").copyWithCount(8);
      h.assertTrue(
          BinderStorage.insert(binder, pages, false, 64) == 4 && pages.getCount() == 4,
          "Total capacity bounds accepted quantities");
      Config.BINDER_PAGE_CAPACITY.set(2);
      h.assertTrue(
          BinderStorage.data(binder).total() == 4, "Lowering capacity preserves existing pages");
      h.assertTrue(
          BinderStorage.insert(binder, pages, false, 64) == 0 && pages.getCount() == 4,
          "Overfull binder rejects insertion without consuming pages");
      h.assertTrue(
          BinderStorage.extract(binder, 0, 3).getCount() == 3,
          "Overfull contents remain extractable");
      h.assertTrue(
          BinderStorage.insert(binder, pages, false, 64) == 1,
          "Insertion resumes below the reduced capacity");
    } finally {
      Config.BINDER_PAGE_CAPACITY.set(previous);
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void largeQuantitiesAndComponentsSurviveSerialization(GameTestHelper h) {
    var binder = binder();
    var prototype = RitualGameTests.page("diamond");
    prototype.set(DataComponents.CUSTOM_NAME, Component.literal("Field notes"));
    binder.set(
        RitualsNotRolls.BINDER_DATA,
        new BinderData(List.of(new BinderData.Entry(prototype, 130)), false, false));
    var restored =
        ItemStack.parse(h.getLevel().registryAccess(), binder.save(h.getLevel().registryAccess()))
            .orElseThrow();
    h.assertTrue(
        ItemStack.matches(binder, restored),
        "Binder stack and all page components survive persistent serialization");
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      BinderData.STREAM.encode(buffer, BinderStorage.data(restored));
      var decoded = BinderData.STREAM.decode(buffer);
      h.assertTrue(
          decoded.equals(BinderStorage.data(restored))
              && decoded.hashCode() == BinderStorage.data(restored).hashCode(),
          "Component network roundtrip preserves stable equality and quantities above stack"
              + " limits");
    } finally {
      buffer.release();
    }
    var taken = BinderStorage.extract(restored, 0, 130);
    h.assertTrue(
        taken.getCount() == 64 && ItemStack.isSameItemSameComponents(taken, prototype),
        "Extraction returns a legal stack with original components");
    h.assertTrue(BinderStorage.data(restored).total() == 66, "Unextracted stored quantity remains");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void storedPageSnapshotsAreImmutable(GameTestHelper h) {
    var prototype = RitualGameTests.page("diamond");
    var entry = new BinderData.Entry(prototype, 3);
    prototype.set(DataComponents.CUSTOM_NAME, Component.literal("Changed source"));
    var snapshot = entry.page();
    snapshot.set(DataComponents.CUSTOM_NAME, Component.literal("Changed snapshot"));
    snapshot.setCount(40);
    h.assertTrue(
        entry.page().getCount() == 1 && !entry.page().has(DataComponents.CUSTOM_NAME),
        "Neither source nor borrowed snapshot can mutate stored components");
    h.assertTrue(entry.count() == 3, "Stored quantity remains independent of stack mutations");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void autoCollectionPreservesBinderIdentityAndExtraPages(GameTestHelper h) {
    var player = RitualGameTests.player(h);
    var binder = binder();
    player.getInventory().setItem(0, binder);
    player.getInventory().setItem(1, RitualGameTests.page("diamond").copyWithCount(12));
    player.getInventory().setItem(2, RitualGameTests.page("diamond").copyWithCount(5));
    player.getInventory().setItem(3, RitualGameTests.page("flint").copyWithCount(3));
    player
        .getInventory()
        .setItem(40, Knowledge.page(RitualGameTests.UNBREAKING, "diamond").copyWithCount(2));
    h.assertTrue(
        BinderStorage.collect(player) == 3, "Collects new pages from main inventory and offhand");
    h.assertTrue(
        player.getInventory().getItem(0) == binder,
        "Visible binder keeps the exact object bound to its menu");
    h.assertTrue(
        player.getInventory().getItem(1).getCount() == 11
            && player.getInventory().getItem(2).getCount() == 5,
        "Duplicate pages remain in their original slots");
    h.assertTrue(
        player.getInventory().getItem(3).getCount() == 2
            && player.getInventory().getItem(40).getCount() == 1,
        "Each new knowledge identity consumes exactly one page");
    h.assertTrue(
        BinderStorage.collect(player) == 0 && BinderStorage.data(binder).total() == 3,
        "Repeated scanning does not collect duplicate knowledge");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void invalidItemsAndInvalidIndicesLeaveContentsUnchanged(GameTestHelper h) {
    var binder = binder();
    var leather = new ItemStack(Items.LEATHER, 4);
    var malformed = new ItemStack(RitualsNotRolls.PAGE.get());
    h.assertTrue(
        BinderStorage.insert(binder, leather, false, 4) == 0 && leather.getCount() == 4,
        "Binder rejects non-pages");
    h.assertTrue(
        BinderStorage.insert(binder, malformed, false, 4) == 0 && malformed.getCount() == 1,
        "Binder rejects pages missing knowledge");
    h.assertTrue(
        BinderStorage.extract(binder, -1, 1).isEmpty()
            && BinderStorage.extract(binder, 0, 1).isEmpty(),
        "Invalid extraction indices do nothing");
    h.assertTrue(
        BinderStorage.data(binder).total() == 0, "Invalid operations do not change contents");
    h.succeed();
  }
}
