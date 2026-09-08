package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.ritual.RitualMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class EnchantingTableMixin {
  @Inject(method = "setPlacedBy", at = @At("TAIL"))
  private void ritualsnotrolls$upgrade(
      Level level,
      BlockPos pos,
      BlockState state,
      LivingEntity placer,
      ItemStack stack,
      CallbackInfo ci) {
    if (level.isClientSide || !state.is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE))
      return;
    var be = level.getBlockEntity(pos);
    if (be == null) return;
    level
        .registryAccess()
        .registryOrThrow(Registries.ENCHANTMENT)
        .getHolder(RitualsNotRolls.id("arcane_assembly"))
        .ifPresent(
            h -> {
              be.setData(RitualsNotRolls.ASSEMBLY, RitualMath.enchantments(stack).getLevel(h));
              be.setChanged();
            });
  }
}
