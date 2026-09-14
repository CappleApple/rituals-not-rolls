package com.cappleapple.ritualsnotrolls.gametest;

import static com.cappleapple.ritualsnotrolls.gametest.RitualGameTests.*;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.BinderData;
import com.cappleapple.ritualsnotrolls.knowledge.BinderStorage;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.menu.BinderMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class BinderMenuGameTests {
  private static ItemStack binder(BinderData.Entry... entries) {
    ItemStack binder = new ItemStack(RitualsNotRolls.BINDER_ITEM.get());
    binder.set(RitualsNotRolls.BINDER_DATA, new BinderData(List.of(entries), true, true));
    return binder;
  }

  @GameTest(template = "empty")
  public static void takePageStaysLooseInsteadOfEnteringKnowledgeBook(GameTestHelper h) {
    var player = player(h);
    ItemStack binder = binder(new BinderData.Entry(page("flint"), 2));
    ItemStack book = book("diamond");
    player.getInventory().setItem(0, binder);
    player.getInventory().setItem(2, book);
    var menu = new BinderMenu(1, player.getInventory(), 0);
    player.containerMenu = menu;
    menu.action("take", menu.viewRevision() + ":0:1");
    check(h, BinderStorage.data(binder).total() == 1, "One page leaves the binder");
    check(
        h,
        !Knowledge.data(book).entries().contains("flint"),
        "Returning a page bypasses automatic book absorption");
    check(
        h,
        Knowledge.data(player.getInventory().getItem(1)).entries().equals(List.of("flint")),
        "Page is returned to inventory");
    check(h, menu.stillValid(player), "Binder keeps its bound inventory identity");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void bulkTakeIsLimitedToOneNormalStack(GameTestHelper h) {
    var player = player(h);
    ItemStack binder = binder(new BinderData.Entry(page("diamond"), 96));
    player.getInventory().setItem(0, binder);
    var menu = new BinderMenu(1, player.getInventory(), 0);
    player.containerMenu = menu;
    menu.action("take", menu.viewRevision() + ":0:64");
    check(h, BinderStorage.data(binder).total() == 32, "Bulk take removes 64 pages");
    check(
        h,
        player.getInventory().getItem(1).getCount() == 64,
        "Returned stack uses the normal maximum");
    menu.action("take", menu.viewRevision() + ":0:999");
    check(
        h, BinderStorage.data(binder).total() == 32, "Unsupported extraction amounts are rejected");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void staleTakeCannotExtractTheNextCard(GameTestHelper h) {
    var player = player(h);
    ItemStack binder =
        binder(new BinderData.Entry(page("diamond"), 1), new BinderData.Entry(page("flint"), 1));
    player.getInventory().setItem(0, binder);
    var menu = new BinderMenu(1, player.getInventory(), 0);
    player.containerMenu = menu;
    int revision = menu.viewRevision();
    menu.action("take", revision + ":0:1");
    menu.action("take", revision + ":0:1");
    check(
        h,
        BinderStorage.data(binder).total() == 1,
        "Replayed stale click cannot take a different card");
    check(
        h,
        Knowledge.data(BinderStorage.data(binder).entries().getFirst().page())
            .entries()
            .equals(List.of("flint")),
        "The next card remains stored");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void navigationChangesRevisionAndClampsToLastSpread(GameTestHelper h) {
    var player = player(h);
    var entries = new ArrayList<BinderData.Entry>();
    for (int i = 0; i < 20; i++) entries.add(new BinderData.Entry(page("entry_" + i), 1));
    ItemStack binder = binder(entries.toArray(BinderData.Entry[]::new));
    player.getInventory().setItem(0, binder);
    var menu = new BinderMenu(1, player.getInventory(), 0);
    player.containerMenu = menu;
    int revision = menu.viewRevision();
    menu.action("page", "999");
    check(h, menu.viewPage() == 1, "Navigation clamps to the last twelve-card spread");
    menu.action("take", revision + ":0:1");
    check(h, BinderStorage.data(binder).total() == 20, "A click from the previous spread is stale");
    menu.action("take", menu.viewRevision() + ":0:1");
    check(
        h,
        Knowledge.data(player.getInventory().getItem(1)).entries().equals(List.of("entry_12")),
        "The visible card maps to its server-owned entry");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void togglesPersistButReplacementBinderCannotBeChanged(GameTestHelper h) {
    var player = player(h);
    ItemStack binder = binder();
    player.getInventory().setItem(0, binder);
    var menu = new BinderMenu(1, player.getInventory(), 0);
    player.containerMenu = menu;
    menu.action("auto", "");
    menu.action("filter", "");
    check(
        h,
        !BinderStorage.data(binder).autoCollect() && !BinderStorage.data(binder).filterDuplicates(),
        "Both toggles persist in the binder component");
    ItemStack replacement = binder.copy();
    player.getInventory().setItem(0, replacement);
    menu.action("auto", "");
    check(h, !menu.stillValid(player), "Replacing the bound stack invalidates its menu");
    check(
        h,
        !BinderStorage.data(replacement).autoCollect(),
        "Invalid menu cannot modify its replacement");
    h.succeed();
  }
}
