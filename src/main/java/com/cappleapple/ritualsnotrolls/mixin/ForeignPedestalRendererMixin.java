package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.client.ForeignPedestalRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class ForeignPedestalRendererMixin {
  @Inject(method = "render", at = @At("TAIL"))
  private void ritualsnotrolls$modifiers(
      BlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
    ForeignPedestalRenderer.render(be, pose, buffers);
  }
}
