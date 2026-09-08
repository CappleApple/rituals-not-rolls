package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.pedestal.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;

public final class PedestalRenderer implements BlockEntityRenderer<PedestalEntity> {
  public PedestalRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(
      PedestalEntity pedestal,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    float time = pedestal.getLevel() == null ? 0 : pedestal.getLevel().getGameTime() + partial;
    pose.pushPose();
    var offset = PedestalGeometry.displayOffset(pedestal.getBlockState());
    pose.translate(offset.x, offset.y + Math.sin(time * .04) * .04, offset.z);
    pose.mulPose(Axis.YP.rotationDegrees(time * 1.5f));
    pose.scale(.65f, .65f, .65f);
    Minecraft.getInstance()
        .getItemRenderer()
        .renderStatic(
            pedestal.items.getStackInSlot(0),
            ItemDisplayContext.GROUND,
            light,
            overlay,
            pose,
            buffers,
            pedestal.getLevel(),
            0);
    pose.popPose();
    var modifiers =
        java.util.stream.IntStream.range(1, pedestal.items.getSlots())
            .mapToObj(pedestal.items::getStackInSlot)
            .filter(s -> !s.isEmpty())
            .toList();
    if (!modifiers.isEmpty()) {
      pose.pushPose();
      pose.translate(.5, .5, .5);
      var facing = PedestalGeometry.facing(pedestal.getBlockState());
      float yaw =
          switch (facing) {
            case EAST -> -90;
            case WEST -> -270;
            default -> 0;
          };
      float pitch =
          switch (facing) {
            case DOWN -> -180;
            case SOUTH -> -270;
            case UP -> 0;
            default -> -90;
          };
      pose.mulPose(Axis.YP.rotationDegrees(yaw));
      pose.mulPose(Axis.XP.rotationDegrees(pitch));
      // Four decals sit on the four side faces of the top rim and rotate with the pedestal.
      for (int side = 0; side < 4; side++)
        for (int modifier = 0; modifier < modifiers.size(); modifier++) {
          pose.pushPose();
          pose.mulPose(Axis.YP.rotationDegrees(side * 90));
          pose.translate((modifier - (modifiers.size() - 1) / 2.0) * .23, .34375, -.439);
          pose.scale(.17f, .17f, .008f);
          Minecraft.getInstance()
              .getItemRenderer()
              .renderStatic(
                  modifiers.get(modifier),
                  ItemDisplayContext.FIXED,
                  light,
                  overlay,
                  pose,
                  buffers,
                  pedestal.getLevel(),
                  0);
          pose.popPose();
        }
      pose.popPose();
    }
  }
}
