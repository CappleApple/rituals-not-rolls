package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.compat.ForeignPedestals;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class ForeignPedestalRemovalMixin {
  @Inject(method = "setRemoved", at = @At("HEAD"))
  private void ritualsnotrolls$returnModifiers(CallbackInfo ci) {
    ForeignPedestals.removed((BlockEntity) (Object) this);
  }
}
