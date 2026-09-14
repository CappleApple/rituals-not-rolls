package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class Knowledge {
  private Knowledge() {}

  public static final Map<ResourceLocation, Component> NAMES =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static int warningRevision = -1;
  private static final Set<String> WARNED = new HashSet<>();

  public static void warnStale(KnowledgeData data) {
    if (warningRevision != Definitions.SERVER.revision()) {
      warningRevision = Definitions.SERVER.revision();
      WARNED.clear();
    }
    var definition = Definitions.SERVER.get(data.enchantment());
    for (String entry : data.entries())
      if ((definition == null || definition.affinity(entry) == null)
          && WARNED.add(data.enchantment() + "/" + entry))
        RitualsNotRolls.LOGGER.warn(
            "Preserving unresolved knowledge entry {}/{}; restore its stable datapack ID to"
                + " reactivate it",
            data.enchantment(),
            entry);
  }

  public static KnowledgeData data(ItemStack stack) {
    return stack.get(RitualsNotRolls.KNOWLEDGE);
  }

  public static ItemStack page(ResourceLocation enchantment, String entry) {
    ItemStack stack = new ItemStack(RitualsNotRolls.PAGE.get());
    stack.set(RitualsNotRolls.KNOWLEDGE, new KnowledgeData(enchantment, List.of(entry), true));
    return stack;
  }

  public static ItemStack book(KnowledgeData data) {
    ItemStack stack = new ItemStack(RitualsNotRolls.BOOK.get());
    stack.set(RitualsNotRolls.KNOWLEDGE, data);
    return stack;
  }

  public static boolean accepts(ItemStack book, ItemStack page, boolean automatic) {
    KnowledgeData b = data(book), p = data(page);
    return book.is(RitualsNotRolls.BOOK)
        && page.is(RitualsNotRolls.PAGE)
        && b != null
        && p != null
        && p.entries().size() == 1
        && b.enchantment().equals(p.enchantment())
        && (!automatic || b.autoAdd())
        && !b.entries().contains(p.entries().getFirst());
  }

  /** Mutates both stacks only after acceptance; duplicates are never consumed. */
  public static boolean add(ItemStack book, ItemStack page, boolean automatic) {
    if (!accepts(book, page, automatic)) return false;
    book.set(RitualsNotRolls.KNOWLEDGE, data(book).add(data(page).entries().getFirst()));
    page.shrink(1);
    return true;
  }

  public static boolean absorb(
      Player player, ItemStack incoming, boolean automatic, boolean simulate) {
    if (player.level().isClientSide || incoming.isEmpty()) return false;
    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
      ItemStack book = player.getInventory().getItem(i);
      if (accepts(book, incoming, automatic)) {
        if (!simulate) {
          add(book, incoming, automatic);
          player.getInventory().setChanged();
        }
        return true;
      }
    }
    return false;
  }

  public static int gather(Player player, AbstractContainerMenu menu, ItemStack book) {
    if (player.level().isClientSide || !book.is(RitualsNotRolls.BOOK)) return 0;
    int n = 0;
    List<ItemStack> protectedPages = new ArrayList<>();
    for (var slot : menu.slots) {
      if (!slot.mayPickup(player)) {
        if (slot.container == player.getInventory() && accepts(book, slot.getItem(), false))
          protectedPages.add(slot.getItem().copy());
        continue;
      }
      if (accepts(book, slot.getItem(), false)) {
        // Respect specialized output slots and their onTake bookkeeping.
        ItemStack taken = slot.safeTake(1, 1, player);
        if (!taken.isEmpty()) {
          if (add(book, taken, false)) n++;
          if (!taken.isEmpty()) returnLoose(player, taken);
        }
      }
    }
    // The automation capability can expose storage beyond the visible player slots. NeoForge's
    // default ENTITY provider may otherwise mask a mod's expanded player inventory.
    var inventory = player.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, null);
    if (inventory == null) inventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
    if (inventory != null) {
      // Re-read the slot count: extracting the last stack can shrink a dynamic handler.
      for (int slot = 0; slot < inventory.getSlots(); slot++) {
        ItemStack candidate = inventory.getStackInSlot(slot);
        if (!accepts(book, candidate, false)) continue;
        // Capability indices need not match menu indices. Preserve visible pickup restrictions
        // conservatively for matching pages instead of bypassing them through another view.
        if (protectedPages.stream().anyMatch(p -> ItemStack.isSameItemSameComponents(p, candidate)))
          continue;
        ItemStack taken = inventory.extractItem(slot, 1, false);
        if (add(book, taken, false)) n++;
        if (!taken.isEmpty()) returnLoose(player, taken);
      }
    }
    if (n > 0) player.getInventory().setChanged();
    menu.broadcastChanges();
    return n;
  }

  public static ItemStack tear(ItemStack book, String entry) {
    KnowledgeData b = data(book);
    if (b == null || !book.is(RitualsNotRolls.BOOK) || !b.entries().contains(entry))
      return ItemStack.EMPTY;
    book.set(RitualsNotRolls.KNOWLEDGE, b.remove(entry));
    return page(b.enchantment(), entry);
  }

  public static void returnLoose(Player player, ItemStack stack) {
    try (var ignored = AcquisitionContext.suppress()) {
      if (!player.getInventory().add(stack) && !stack.isEmpty()) {
        var entity = player.drop(stack, false);
        // Give the owner time to move a torn page; pickup suppression is serialized on this entity.
        if (entity != null)
          entity.getPersistentData().putBoolean("ritualsnotrolls_loose_page", true);
      }
    }
  }

  public static Component name(
      ResourceLocation enchantment, net.minecraft.core.HolderLookup.Provider registries) {
    if (registries != null) {
      var holder =
          registries
              .lookupOrThrow(Registries.ENCHANTMENT)
              .get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, enchantment));
      if (holder.isPresent()) return holder.get().value().description();
    }
    if (NAMES.containsKey(enchantment)) return NAMES.get(enchantment);
    return Component.translatable(
        "enchantment."
            + enchantment.getNamespace()
            + "."
            + enchantment.getPath().replace('/', '.'));
  }

  public static int total(KnowledgeData data, boolean client) {
    var d = Definitions.forSide(client).get(data.enchantment());
    return d == null ? 0 : d.materials().size();
  }
}
