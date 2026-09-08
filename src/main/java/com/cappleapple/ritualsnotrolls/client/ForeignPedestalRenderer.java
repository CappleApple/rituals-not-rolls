package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.compat.ForeignPedestals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ForeignPedestalRenderer {
  public static void render(BlockEntity be, PoseStack pose, MultiBufferSource buffers) {
    if (!be.hasLevel() || !ForeignPedestals.supported(be.getBlockState())) return;
    var inventory = ForeignPedestals.modifiers(be);
    List<ItemStack> items = new ArrayList<>();
    for (int i = 0; i < inventory.getSlots(); i++)
      if (!inventory.getStackInSlot(i).isEmpty()) items.add(inventory.getStackInSlot(i));
    if (items.isEmpty()) return;
    int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
    for (int side = 0; side < 4; side++)
      for (int i = 0; i < items.size(); i++) {
        pose.pushPose();
        boolean supplementaries =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(be.getBlockState().getBlock())
                .getNamespace()
                .equals("supplementaries");
        pose.translate(.5, supplementaries ? .90625 : .875, .5);
        pose.mulPose(Axis.YP.rotationDegrees(side * 90));
        pose.translate((i - (items.size() - 1) / 2.0) * .23, 0, supplementaries ? -.442 : -.505);
        pose.scale(.17f, .17f, .008f);
        Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(
                items.get(i),
                ItemDisplayContext.FIXED,
                light,
                OverlayTexture.NO_OVERLAY,
                pose,
                buffers,
                be.getLevel(),
                0);
        pose.popPose();
      }
  }
}
