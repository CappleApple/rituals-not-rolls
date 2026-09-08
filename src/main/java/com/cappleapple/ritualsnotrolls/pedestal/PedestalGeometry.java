package com.cappleapple.ritualsnotrolls.pedestal;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Matches the six block-model rotations; displayed items retain world-up orientation. */
public final class PedestalGeometry {
  public static Vec3 rotate(Vec3 v, Direction facing) {
    return switch (facing) {
      case UP -> v;
      case DOWN -> new Vec3(v.x, -v.y, -v.z);
      case NORTH -> new Vec3(v.x, v.z, -v.y);
      case SOUTH -> new Vec3(v.x, -v.z, v.y);
      case EAST -> new Vec3(v.y, v.z, v.x);
      case WEST -> new Vec3(-v.y, v.z, -v.x);
    };
  }

  public static Direction facing(BlockState state) {
    return state.hasProperty(PedestalBlock.FACING)
        ? state.getValue(PedestalBlock.FACING)
        : Direction.UP;
  }

  public static Vec3 displayOffset(BlockState state) {
    var id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    if (id.equals("irons_spellbooks:pedestal")) return new Vec3(.5, 1.5, .5);
    if (id.equals("supplementaries:pedestal")) return new Vec3(.5, 1.25, .5);
    return new Vec3(.5, .5, .5).add(Vec3.atLowerCornerOf(facing(state).getNormal()).scale(.72));
  }

  public static Vec3 displayPosition(BlockPos pos, BlockState state) {
    return Vec3.atLowerCornerOf(pos).add(displayOffset(state));
  }
}
