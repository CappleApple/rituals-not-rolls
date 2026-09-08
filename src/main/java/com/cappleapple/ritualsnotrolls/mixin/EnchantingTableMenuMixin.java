package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Other mods asking the table for a menu provider also receive the ritual menu. */
@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableMenuMixin {
  @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
  private void ritualsnotrolls$menu(
      BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> ci) {
    ci.setReturnValue(
        new SimpleMenuProvider(
            (id, inventory, player) -> new RitualMenu(id, inventory, pos),
            Component.translatable("container.ritualsnotrolls.ritual")));
  }
}
