package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.compat.ForeignPedestals;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Some native pedestals override save/loadAdditional without calling their superclass. */
@Mixin(BlockEntity.class)
public abstract class ForeignPedestalPersistenceMixin {
  @Unique private CompoundTag ritualsnotrolls$previousModifiers;

  @Inject(
      method = {"saveWithoutMetadata", "saveCustomOnly"},
      at = @At("RETURN"))
  private void ritualsnotrolls$save(
      HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> ci) {
    var be = (BlockEntity) (Object) this;
    if (ForeignPedestals.supported(be.getBlockState())
        && be.getPersistentData().contains(ForeignPedestals.KEY))
      ci.getReturnValue()
          .put(
              ForeignPedestals.KEY,
              be.getPersistentData().getCompound(ForeignPedestals.KEY).copy());
  }

  @Inject(
      method = {"loadWithComponents", "loadCustomOnly"},
      at = @At("HEAD"))
  private void ritualsnotrolls$remember(
      CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
    var be = (BlockEntity) (Object) this;
    if (ForeignPedestals.supported(be.getBlockState())) {
      var source = tag.contains(ForeignPedestals.KEY) ? tag : tag.getCompound("NeoForgeData");
      ritualsnotrolls$previousModifiers =
          source.contains(ForeignPedestals.KEY)
              ? source.getCompound(ForeignPedestals.KEY).copy()
              : be.getPersistentData().getCompound(ForeignPedestals.KEY).copy();
    }
  }

  @Inject(
      method = {"loadWithComponents", "loadCustomOnly"},
      at = @At("TAIL"))
  private void ritualsnotrolls$load(
      CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
    if (ritualsnotrolls$previousModifiers != null) {
      ((BlockEntity) (Object) this)
          .getPersistentData()
          .put(ForeignPedestals.KEY, ritualsnotrolls$previousModifiers);
      ritualsnotrolls$previousModifiers = null;
    }
  }
}
