package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.*;

/**
 * Extraction receipts own only items actually removed. Rollback returns those items or safely drops
 * the remainder.
 */
public final class Transfers {
  public record Receipt(IItemHandler handler, int slot, BlockPos pos, ItemStack stack) {}

  public static boolean same(ItemStack current, ItemStack expected) {
    return current.getCount() >= expected.getCount()
        && ItemStack.isSameItemSameComponents(current, expected);
  }

  public static void rollback(ServerLevel level, List<Receipt> receipts) {
    for (int i = receipts.size() - 1; i >= 0; i--) {
      Receipt r = receipts.get(i);
      ItemStack left = r.handler.insertItem(r.slot, r.stack, false);
      if (!left.isEmpty()) left = ItemHandlerHelper.insertItemStacked(r.handler, left, false);
      if (!left.isEmpty()) {
        var at = RitualSpace.toWorld(level, r.pos, Vec3.atBottomCenterOf(r.pos).add(0, 1, 0));
        Containers.dropItemStack(level, at.x, at.y, at.z, left);
      }
    }
  }

  public static boolean moveOne(
      ServerLevel level,
      RitualNetwork.SlotRef source,
      RitualNetwork.Pedestal destination,
      int targetSlot) {
    if (!same(source.handler().getStackInSlot(source.slot()), source.stack().copyWithCount(1)))
      return false;
    ItemStack probe = source.handler().extractItem(source.slot(), 1, true);
    if (probe.isEmpty() || !destination.handler().insertItem(targetSlot, probe, true).isEmpty())
      return false;
    ItemStack actual = source.handler().extractItem(source.slot(), 1, false);
    if (actual.isEmpty()) return false;
    if (actual.getCount() != 1 || !ItemStack.isSameItemSameComponents(actual, probe)) {
      rollback(level, List.of(new Receipt(source.handler(), source.slot(), source.pos(), actual)));
      return false;
    }
    ItemStack remainder = destination.handler().insertItem(targetSlot, actual, false);
    if (!remainder.isEmpty())
      rollback(
          level, List.of(new Receipt(source.handler(), source.slot(), source.pos(), remainder)));
    return remainder.isEmpty();
  }
}
