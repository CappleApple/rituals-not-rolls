package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.knowledge.AcquisitionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
  @WrapMethod(method = "playerTouch")
  private void ritualsnotrolls$loosePage(Player player, Operation<Void> original) {
    ItemEntity self = (ItemEntity) (Object) this;
    if (self.getPersistentData().getBoolean("ritualsnotrolls_loose_page")) {
      try (var ignored = AcquisitionContext.suppress()) {
        original.call(player);
      }
    } else original.call(player);
  }
}
