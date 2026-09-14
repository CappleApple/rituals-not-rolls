package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.Config;
import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.menu.BinderMenu;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class BinderStorage {
  private static final Map<Player, Long> LAST_COLLECTION = new WeakHashMap<>();
  private static final Map<Player, Long> PAUSED_UNTIL = new WeakHashMap<>();

  private BinderStorage() {}

  public static int capacity() {
    return Config.SPEC.isLoaded() ? Config.BINDER_PAGE_CAPACITY.get() : 1024;
  }

  public static int pagesPerSecond() {
    return Config.SPEC.isLoaded() ? Config.BINDER_PAGES_PER_SECOND.get() : 16;
  }

  public static BinderData data(ItemStack binder) {
    return binder.getOrDefault(RitualsNotRolls.BINDER_DATA, BinderData.EMPTY);
  }

  public static boolean isPage(ItemStack page) {
    var knowledge = Knowledge.data(page);
    return !page.isEmpty()
        && page.is(RitualsNotRolls.PAGE)
        && knowledge != null
        && knowledge.entries().size() == 1;
  }

  private static boolean sameKnowledge(ItemStack left, ItemStack right) {
    var a = Knowledge.data(left);
    var b = Knowledge.data(right);
    return a != null
        && b != null
        && a.enchantment().equals(b.enchantment())
        && a.entries().equals(b.entries());
  }

  private static int insertionLimit(
      ItemStack binder, ItemStack page, boolean automatic, int maximum) {
    if (!binder.is(RitualsNotRolls.BINDER_ITEM) || !isPage(page) || maximum < 1) return 0;
    var data = data(binder);
    if (automatic && !data.autoCollect()) return 0;
    int amount =
        Math.min(Math.min(page.getCount(), maximum), Math.max(0, capacity() - data.total()));
    if (automatic && data.filterDuplicates()) {
      if (data.entries().stream().anyMatch(entry -> sameKnowledge(entry.page(), page))) return 0;
      amount = Math.min(amount, 1);
    }
    return amount;
  }

  /** Returns the stored quantity and shrinks the source only after updating the binder. */
  public static int insert(ItemStack binder, ItemStack page, boolean automatic, int maximum) {
    int amount = insertionLimit(binder, page, automatic, maximum);
    if (amount == 0) return 0;
    var data = data(binder);
    var entries = new ArrayList<>(data.entries());
    boolean merged = false;
    for (int index = 0; index < entries.size(); index++) {
      var entry = entries.get(index);
      if (ItemStack.isSameItemSameComponents(entry.page(), page)) {
        entries.set(index, new BinderData.Entry(page, entry.count() + amount));
        merged = true;
        break;
      }
    }
    if (!merged) entries.add(new BinderData.Entry(page, amount));
    binder.set(
        RitualsNotRolls.BINDER_DATA,
        new BinderData(entries, data.autoCollect(), data.filterDuplicates()));
    page.shrink(amount);
    return amount;
  }

  /** Returns at most one normal stack; stored counts can exceed that stack's size limit. */
  public static ItemStack extract(ItemStack binder, int index, int amount) {
    if (!binder.is(RitualsNotRolls.BINDER_ITEM) || amount < 1) return ItemStack.EMPTY;
    var data = data(binder);
    if (index < 0 || index >= data.entries().size()) return ItemStack.EMPTY;
    var entries = new ArrayList<>(data.entries());
    var entry = entries.get(index);
    ItemStack page = entry.page();
    int taken = Math.min(Math.min(amount, entry.count()), page.getMaxStackSize());
    if (taken < 1) return ItemStack.EMPTY;
    if (taken == entry.count()) entries.remove(index);
    else entries.set(index, new BinderData.Entry(page, entry.count() - taken));
    binder.set(
        RitualsNotRolls.BINDER_DATA,
        new BinderData(entries, data.autoCollect(), data.filterDuplicates()));
    return page.copyWithCount(taken);
  }

  public static void pauseCollection(Player player, int ticks) {
    PAUSED_UNTIL.merge(player, player.level().getGameTime() + Math.max(0, ticks), Math::max);
  }

  public static void onPlayerTick(PlayerTickEvent.Post event) {
    tick(event.getEntity());
  }

  /**
   * A shared gate prevents multiple held binders and the player event from rescanning each tick.
   */
  public static void tick(Player player) {
    if (player.level().isClientSide || player.containerMenu instanceof BinderMenu) return;
    long now = player.level().getGameTime();
    if (now % 20 != 0
        || now < PAUSED_UNTIL.getOrDefault(player, Long.MIN_VALUE)
        || LAST_COLLECTION.getOrDefault(player, Long.MIN_VALUE) == now) return;
    LAST_COLLECTION.put(player, now);
    collect(player);
  }

  /** Collects into real visible stacks first, preserving the stack bound to an open item menu. */
  public static int collect(Player player) {
    if (player.level().isClientSide) return 0;
    int collected = 0;
    var visible = new ArrayList<ItemStack>();
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack binder = player.getInventory().getItem(slot);
      if (!binder.is(RitualsNotRolls.BINDER_ITEM)) continue;
      collected += collect(player, binder);
      visible.add(binder.copy());
    }
    IItemHandler handler = inventory(player);
    if (handler != null) {
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack candidate = handler.getStackInSlot(slot);
        if (!candidate.is(RitualsNotRolls.BINDER_ITEM)
            || !data(candidate).autoCollect()
            || data(candidate).total() >= capacity()
            || visible.stream()
                .anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, candidate))
            || !canCollect(player, candidate, handler)) continue;
        // Capability stacks may be copies. Take ownership before changing a stowed binder, then
        // restore it through the same inventory API instead of editing a borrowed snapshot.
        ItemStack taken = handler.extractItem(slot, 1, false);
        if (taken.isEmpty()) continue;
        if (taken.is(RitualsNotRolls.BINDER_ITEM)) collected += collect(player, taken);
        ItemStack remainder =
            slot < handler.getSlots() ? handler.insertItem(slot, taken, false) : taken;
        if (!remainder.isEmpty())
          remainder = ItemHandlerHelper.insertItemStacked(handler, remainder, false);
        if (!remainder.isEmpty()) Knowledge.returnLoose(player, remainder);
      }
    }
    if (collected > 0) {
      player.getInventory().setChanged();
      player.inventoryMenu.broadcastChanges();
    }
    return collected;
  }

  /**
   * Gathers physical and capability-backed inventory pages using this binder's automatic policy.
   */
  public static int collect(Player player, ItemStack binder) {
    if (player.level().isClientSide
        || !binder.is(RitualsNotRolls.BINDER_ITEM)
        || !data(binder).autoCollect()) return 0;
    int collected = 0;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack candidate = player.getInventory().getItem(slot);
      int amount = insertionLimit(binder, candidate, true, Integer.MAX_VALUE);
      if (amount == 0) continue;
      ItemStack taken = player.getInventory().removeItem(slot, amount);
      collected += insert(binder, taken, true, amount);
      if (!taken.isEmpty()) Knowledge.returnLoose(player, taken);
    }
    IItemHandler handler = inventory(player);
    if (handler != null) {
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack candidate = handler.getStackInSlot(slot);
        int amount = insertionLimit(binder, candidate, true, Integer.MAX_VALUE);
        if (amount == 0) continue;
        ItemStack taken = handler.extractItem(slot, amount, false);
        collected += insert(binder, taken, true, amount);
        if (!taken.isEmpty()) Knowledge.returnLoose(player, taken);
      }
    }
    if (collected > 0) player.getInventory().setChanged();
    return collected;
  }

  /**
   * Explicit cursor gathering honors the duplicate filter independently of automatic collection.
   */
  public static int gather(
      Player player, net.minecraft.world.inventory.AbstractContainerMenu menu, ItemStack binder) {
    if (player.level().isClientSide || !binder.is(RitualsNotRolls.BINDER_ITEM)) return 0;
    int collected = 0;
    var protectedPages = new ArrayList<ItemStack>();
    for (var slot : menu.slots) {
      if (!slot.mayPickup(player)) {
        if (slot.container == player.getInventory() && isPage(slot.getItem()))
          protectedPages.add(slot.getItem().copy());
        continue;
      }
      int amount = gatherLimit(binder, slot.getItem());
      if (amount == 0) continue;
      ItemStack taken = slot.safeTake(amount, amount, player);
      collected += insert(binder, taken, false, gatherLimit(binder, taken));
      if (!taken.isEmpty()) Knowledge.returnLoose(player, taken);
    }
    var handler = inventory(player);
    if (handler != null) {
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack candidate = handler.getStackInSlot(slot);
        int amount = gatherLimit(binder, candidate);
        if (amount == 0
            || protectedPages.stream()
                .anyMatch(p -> ItemStack.isSameItemSameComponents(p, candidate))) continue;
        ItemStack taken = handler.extractItem(slot, amount, false);
        collected += insert(binder, taken, false, gatherLimit(binder, taken));
        if (!taken.isEmpty()) Knowledge.returnLoose(player, taken);
      }
    }
    if (collected > 0) player.getInventory().setChanged();
    menu.broadcastChanges();
    return collected;
  }

  private static int gatherLimit(ItemStack binder, ItemStack page) {
    int amount = insertionLimit(binder, page, false, Integer.MAX_VALUE);
    if (amount > 0 && data(binder).filterDuplicates()) {
      if (data(binder).entries().stream().anyMatch(entry -> sameKnowledge(entry.page(), page)))
        return 0;
      amount = Math.min(amount, 1);
    }
    return amount;
  }

  private static boolean canCollect(Player player, ItemStack binder, IItemHandler handler) {
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
      if (insertionLimit(binder, player.getInventory().getItem(slot), true, 1) > 0) return true;
    for (int slot = 0; slot < handler.getSlots(); slot++)
      if (insertionLimit(binder, handler.getStackInSlot(slot), true, 1) > 0) return true;
    return false;
  }

  private static IItemHandler inventory(Player player) {
    var handler = player.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, null);
    return handler != null ? handler : player.getCapability(Capabilities.ItemHandler.ENTITY);
  }
}
