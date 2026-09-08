package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.compat.AncientBookMigration;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackMigrationMixin {
  // Wrap at construction: OPTIONAL/STRICT and nested container codecs must all capture the
  // migrating codec too. Hooking parse() alone misses bundles and item-container components.
  @ModifyExpressionValue(
      method = "<clinit>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/serialization/Codec;lazyInitialized(Ljava/util/function/Supplier;)Lcom/mojang/serialization/Codec;"),
      require = 2,
      allow = 2)
  private static Codec<ItemStack> ritualsnotrolls$migrateAncientBooks(Codec<ItemStack> original) {
    return AncientBookMigration.wrap(original);
  }
}
