package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
  @WrapMethod(method = "add(ILnet/minecraft/world/item/ItemStack;)Z")
  private boolean ritualsnotrolls$acquire(int slot, ItemStack stack, Operation<Boolean> original) {
    Inventory self = (Inventory) (Object) this;
    int before = stack.getCount();
    if (AcquisitionContext.allowed())
      while (!stack.isEmpty() && Knowledge.absorb(self.player, stack, true, false)) {}
    return stack.isEmpty() || original.call(slot, stack) || stack.getCount() < before;
  }
}
