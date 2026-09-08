package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;

/** NeoForge's player inventory wrappers delegate to InvWrapper, including simulation. */
@Mixin(InvWrapper.class)
public abstract class InvWrapperMixin {
  @WrapMethod(method = "insertItem")
  private ItemStack ritualsnotrolls$insert(
      int slot, ItemStack stack, boolean simulate, Operation<ItemStack> original) {
    InvWrapper self = (InvWrapper) (Object) this;
    if (AcquisitionContext.allowed()
        && self.getInv() instanceof Inventory inv
        && slot >= 0
        && slot < self.getSlots()
        && inv.canPlaceItem(slot, stack)
        && Knowledge.absorb(inv.player, stack, true, true)) {
      ItemStack remainder = stack.copy();
      if (simulate) remainder.shrink(1);
      else Knowledge.absorb(inv.player, remainder, true, false);
      return remainder.isEmpty() ? ItemStack.EMPTY : original.call(slot, remainder, simulate);
    }
    return original.call(slot, stack, simulate);
  }
}
