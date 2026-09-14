package com.cappleapple.ritualsnotrolls.menu;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.BinderData;
import com.cappleapple.ritualsnotrolls.knowledge.BinderStorage;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.network.Networking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** A bound, server-authoritative view that sends only the current twelve card pockets. */
public final class BinderMenu extends AbstractContainerMenu {
  public static final int CARDS_PER_SPREAD = 12;
  public final Inventory inventory;
  public final int binderSlot;
  public int actionSequence;
  private final ItemStack boundBinder;
  private BinderData seenData;
  private int revision;
  private int page;
  private long lastCheck = -100;
  private int lastCapacity = -1;
  private CompoundTag clientState = new CompoundTag();
  private List<Card> clientCards = List.of();

  public record Card(ItemStack page, int count) {}

  public BinderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    this(id, inventory, buffer.readVarInt());
  }

  public BinderMenu(int id, Inventory inventory, int binderSlot) {
    super(RitualsNotRolls.BINDER_MENU.get(), id);
    if (binderSlot < 0 || binderSlot >= inventory.getContainerSize())
      throw new IllegalArgumentException("Invalid binder slot");
    this.inventory = inventory;
    this.binderSlot = binderSlot;
    boundBinder = inventory.getItem(binderSlot);
    refresh();
  }

  public ItemStack binder() {
    return inventory.getItem(binderSlot);
  }

  private boolean refresh() {
    BinderData current = BinderStorage.data(binder());
    if (current == seenData) return false;
    seenData = current;
    revision++;
    page = Math.clamp(page, 0, Math.max(0, (current.entries().size() - 1) / CARDS_PER_SPREAD));
    return true;
  }

  @Override
  public boolean stillValid(Player player) {
    return player == inventory.player
        && inventory.getItem(binderSlot) == boundBinder
        && boundBinder.is(RitualsNotRolls.BINDER_ITEM);
  }

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (!(inventory.player instanceof ServerPlayer player) || !stillValid(player)) return;
    if (player.level().getGameTime() - lastCheck >= 10) {
      boolean first = lastCheck < 0;
      lastCheck = player.level().getGameTime();
      if (refresh() || first || BinderStorage.capacity() != lastCapacity) sync();
    }
  }

  private void sync() {
    if (!(inventory.player instanceof ServerPlayer player)) return;
    refresh();
    var state = new CompoundTag();
    state.putInt("revision", revision);
    state.putInt("page", page);
    state.putInt(
        "spreads",
        Math.max(1, (seenData.entries().size() + CARDS_PER_SPREAD - 1) / CARDS_PER_SPREAD));
    state.putInt("total", seenData.total());
    lastCapacity = BinderStorage.capacity();
    state.putInt("capacity", lastCapacity);
    state.putBoolean("auto_collect", seenData.autoCollect());
    state.putBoolean("filter_duplicates", seenData.filterDuplicates());
    var cards = new ListTag();
    for (int index = page * CARDS_PER_SPREAD;
        index < Math.min(seenData.entries().size(), (page + 1) * CARDS_PER_SPREAD);
        index++) {
      var entry = seenData.entries().get(index);
      var card = new CompoundTag();
      card.put("page", entry.page().save(player.registryAccess()));
      card.putInt("count", entry.count());
      cards.add(card);
    }
    state.put("cards", cards);
    Networking.sendState(player, containerId, state);
  }

  public void applyState(CompoundTag state) {
    clientState = state;
    var cards = new ArrayList<Card>();
    var list = state.getList("cards", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(CARDS_PER_SPREAD, list.size()); i++) {
      var card = list.getCompound(i);
      cards.add(
          new Card(
              ItemStack.parseOptional(inventory.player.registryAccess(), card.getCompound("page")),
              card.getInt("count")));
    }
    clientCards = List.copyOf(cards);
  }

  public List<Card> cards() {
    return clientCards;
  }

  public int viewRevision() {
    return inventory.player.level().isClientSide ? clientState.getInt("revision") : revision;
  }

  public int viewPage() {
    return inventory.player.level().isClientSide ? clientState.getInt("page") : page;
  }

  public int spreads() {
    return Math.max(1, clientState.getInt("spreads"));
  }

  public int total() {
    return clientState.getInt("total");
  }

  public int capacity() {
    return clientState.getInt("capacity");
  }

  public boolean autoCollect() {
    return !clientState.contains("auto_collect") || clientState.getBoolean("auto_collect");
  }

  public boolean filterDuplicates() {
    return !clientState.contains("filter_duplicates")
        || clientState.getBoolean("filter_duplicates");
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {}

  @Override
  public void removed(Player player) {
    super.removed(player);
    if (!player.level().isClientSide) BinderStorage.pauseCollection(player, 20);
  }

  public void action(String action, String value) {
    if (inventory.player.level().isClientSide || !stillValid(inventory.player)) return;
    refresh();
    switch (action) {
      case "auto" ->
          binder()
              .set(RitualsNotRolls.BINDER_DATA, seenData.withAutoCollect(!seenData.autoCollect()));
      case "filter" ->
          binder()
              .set(
                  RitualsNotRolls.BINDER_DATA,
                  seenData.withFilterDuplicates(!seenData.filterDuplicates()));
      case "page" -> {
        int requested =
            Math.clamp(
                Integer.parseInt(value),
                0,
                Math.max(0, (seenData.entries().size() - 1) / CARDS_PER_SPREAD));
        if (requested != page) {
          page = requested;
          revision++;
        }
      }
      case "take" -> {
        String[] parts = value.split(":", -1);
        if (parts.length != 3) return;
        int expectedRevision = Integer.parseInt(parts[0]);
        int card = Integer.parseInt(parts[1]);
        int amount = Integer.parseInt(parts[2]);
        int index = page * CARDS_PER_SPREAD + card;
        // A previous click or inventory collection can remove a card and shift subsequent indices.
        // Reject stale requests instead of returning a different page from the one clicked.
        if (expectedRevision == revision
            && card >= 0
            && card < CARDS_PER_SPREAD
            && index < seenData.entries().size()
            && (amount == 1 || amount == 64)) {
          ItemStack taken = BinderStorage.extract(binder(), index, amount);
          if (!taken.isEmpty()) Knowledge.returnLoose(inventory.player, taken);
        }
      }
      default -> {
        return;
      }
    }
    inventory.setChanged();
    sync();
    inventory.player.inventoryMenu.broadcastChanges();
  }
}
