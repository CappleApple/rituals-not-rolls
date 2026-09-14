package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
  @WrapMethod(method = "clicked")
  private void ritualsnotrolls$context(
      int slotId, int button, ClickType type, Player player, Operation<Void> original) {
    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
    if (type == ClickType.PICKUP_ALL && menu.getCarried().is(RitualsNotRolls.BOOK)) {
      Knowledge.gather(player, menu, menu.getCarried());
      return;
    }
    if (type == ClickType.PICKUP_ALL && menu.getCarried().is(RitualsNotRolls.BINDER_ITEM)) {
      BinderStorage.gather(player, menu, menu.getCarried());
      return;
    }
    if (!player.level().isClientSide
        && type == ClickType.QUICK_MOVE
        && slotId >= 0
        && slotId < menu.slots.size()) {
      Slot slot = menu.slots.get(slotId);
      if (slot.container != player.getInventory()
          && slot.mayPickup(player)
          && Knowledge.absorb(player, slot.getItem(), true, true)) {
        ItemStack taken = slot.safeTake(1, 1, player);
        if (!taken.isEmpty() && !Knowledge.absorb(player, taken, true, false))
          Knowledge.returnLoose(player, taken);
        menu.broadcastChanges();
        if (!slot.hasItem()) return;
      }
    }
    try (var ignored = AcquisitionContext.suppress()) {
      original.call(slotId, button, type, player);
    }
  }
}
