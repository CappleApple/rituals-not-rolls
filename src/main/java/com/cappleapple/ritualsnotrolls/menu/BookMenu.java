package com.cappleapple.ritualsnotrolls.menu;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class BookMenu extends AbstractContainerMenu {
  public final Inventory inventory;
  public final int bookSlot;
  private final ItemStack boundBook;
  public int actionSequence;

  public BookMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    this(id, inventory, buffer.readVarInt());
  }

  public BookMenu(int id, Inventory inventory, int bookSlot) {
    super(RitualsNotRolls.BOOK_MENU.get(), id);
    this.inventory = inventory;
    this.bookSlot = bookSlot;
    boundBook = inventory.getItem(bookSlot);
  }

  public ItemStack clientBook = ItemStack.EMPTY;
  private long lastSync = -100;

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (inventory.player instanceof net.minecraft.server.level.ServerPlayer player
        && player.level().getGameTime() - lastSync >= 10) sync();
  }

  private void sync() {
    if (!(inventory.player instanceof net.minecraft.server.level.ServerPlayer player)) return;
    lastSync = player.level().getGameTime();
    var state = new net.minecraft.nbt.CompoundTag();
    state.put("book", book().save(player.registryAccess()));
    com.cappleapple.ritualsnotrolls.network.Networking.sendState(player, containerId, state);
  }

  public ItemStack book() {
    return inventory.player.level().isClientSide && !clientBook.isEmpty()
        ? clientBook
        : inventory.getItem(bookSlot);
  }

  @Override
  public boolean stillValid(Player p) {
    return inventory.getItem(bookSlot) == boundBook
        && book().is(RitualsNotRolls.BOOK)
        && Knowledge.data(book()) != null;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {}

  public void action(String action, String value) {
    if (!stillValid(inventory.player) || inventory.player.level().isClientSide) return;
    if (action.equals("auto"))
      book().set(RitualsNotRolls.KNOWLEDGE, Knowledge.data(book()).toggle());
    if (action.equals("tear")) {
      ItemStack page = Knowledge.tear(book(), value);
      if (!page.isEmpty()) Knowledge.returnLoose(inventory.player, page);
    }
    if (action.equals("gather"))
      Knowledge.gather(inventory.player, inventory.player.inventoryMenu, book());
    inventory.setChanged();
    sync();
    inventory.player.inventoryMenu.broadcastChanges();
  }
}
