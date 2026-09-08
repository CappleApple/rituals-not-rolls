package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.api.RitualApi;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Offerings disappear on arrival, but remain recoverable until a successful commit. */
public final class RitualConsumption {
  public static final String KEY = "ritualsnotrolls_reserved_offerings";
  private final List<Transfers.Receipt> receipts = new ArrayList<>();

  public int count(BlockPos pos) {
    return receipts.stream()
        .filter(r -> r.pos().equals(pos))
        .mapToInt(r -> r.stack().getCount())
        .sum();
  }

  public String take(ServerLevel level, ItemEntity target, RitualMath.Plan plan, BlockPos pos) {
    int amount = plan.withdrawals().getOrDefault(pos, 0) - count(pos);
    if (amount <= 0) return "";
    var expected =
        plan.evaluation().used().stream().filter(p -> p.pos().equals(pos)).findFirst().orElse(null);
    var handler = RitualApi.pedestal(level, pos);
    if (expected == null
        || handler == null
        || !ItemStack.matches(handler.getStackInSlot(0), expected.stack()))
      return "A contributing pedestal changed";
    var probe = handler.extractItem(0, amount, true);
    if (probe.getCount() != amount || !ItemStack.isSameItemSameComponents(probe, expected.stack()))
      return "A material cannot be extracted";
    var removed = handler.extractItem(0, amount, false);
    if (!removed.isEmpty()) receipts.add(new Transfers.Receipt(handler, 0, pos, removed));
    persist(level, target);
    if (removed.getCount() != amount || !ItemStack.isSameItemSameComponents(removed, probe))
      return "A material changed during extraction";
    return "";
  }

  private void persist(ServerLevel level, ItemEntity target) {
    ListTag saved = new ListTag();
    for (var receipt : receipts) saved.add(receipt.stack().save(level.registryAccess()));
    target.getPersistentData().put(KEY, saved);
  }

  public RitualNetwork.Snapshot overlay(RitualNetwork.Snapshot network) {
    var pedestals = new ArrayList<RitualNetwork.Pedestal>();
    for (var p : network.pedestals()) {
      ItemStack stack = p.stack().copy();
      for (var receipt : receipts)
        if (receipt.pos().equals(p.pos())) {
          if (stack.isEmpty()) stack = receipt.stack().copy();
          else if (ItemStack.isSameItemSameComponents(stack, receipt.stack()))
            stack.grow(receipt.stack().getCount());
        }
      pedestals.add(
          new RitualNetwork.Pedestal(p.pos(), p.handler(), stack, p.catalyst(), p.subtraction()));
    }
    return new RitualNetwork.Snapshot(
        network.knowledge(),
        network.shelves(),
        List.copyOf(pedestals),
        network.storage(),
        network.sources(),
        network.table());
  }

  public void finish(ItemEntity target) {
    receipts.clear();
    target.getPersistentData().remove(KEY);
  }

  public void rollback(ServerLevel level, ItemEntity target) {
    for (var receipt : receipts) {
      var pos = receipt.pos();
      var handler =
          level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
              ? RitualApi.pedestal(level, pos)
              : null;
      ItemStack rest =
          handler == null ? receipt.stack() : handler.insertItem(0, receipt.stack(), false);
      if (!rest.isEmpty())
        Containers.dropItemStack(level, target.getX(), target.getY(), target.getZ(), rest);
    }
    finish(target);
  }

  /**
   * Captured targets restored from disk return reserved offerings without loading pedestal chunks.
   */
  public static void recover(ItemEntity target) {
    var data = target.getPersistentData();
    if (!(target.level() instanceof ServerLevel level) || !data.contains(KEY)) return;
    var items = data.getList(KEY, 10).copy();
    data.remove(KEY);
    for (var tag : items)
      ItemStack.parse(level.registryAccess(), tag)
          .ifPresent(
              stack ->
                  Containers.dropItemStack(
                      level, target.getX(), target.getY(), target.getZ(), stack));
  }
}
