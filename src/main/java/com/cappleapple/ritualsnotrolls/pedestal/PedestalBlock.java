package com.cappleapple.ritualsnotrolls.pedestal;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

public final class PedestalBlock extends BaseEntityBlock {
  public static final MapCodec<PedestalBlock> CODEC = simpleCodec(PedestalBlock::new);
  public static final DirectionProperty FACING = BlockStateProperties.FACING;
  private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
  private static final VoxelShape SHAPE =
      Shapes.or(box(2, 0, 2, 14, 3, 14), box(5, 3, 5, 11, 12, 11), box(1, 12, 1, 15, 15, 15));

  static {
    for (var direction : Direction.values()) {
      VoxelShape rotated = Shapes.empty();
      for (var box : SHAPE.toAabbs()) {
        double minX = 1, minY = 1, minZ = 1, maxX = 0, maxY = 0, maxZ = 0;
        for (double x : new double[] {box.minX, box.maxX})
          for (double y : new double[] {box.minY, box.maxY})
            for (double z : new double[] {box.minZ, box.maxZ}) {
              var v =
                  PedestalGeometry.rotate(new Vec3(x - .5, y - .5, z - .5), direction)
                      .add(.5, .5, .5);
              minX = Math.min(minX, v.x);
              minY = Math.min(minY, v.y);
              minZ = Math.min(minZ, v.z);
              maxX = Math.max(maxX, v.x);
              maxY = Math.max(maxY, v.y);
              maxZ = Math.max(maxZ, v.z);
            }
        rotated = Shapes.or(rotated, Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
      }
      SHAPES.put(direction, rotated.optimize());
    }
  }

  public PedestalBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getClickedFace());
  }

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    return rotate(state, mirror.getRotation(state.getValue(FACING)));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new PedestalEntity(pos, state);
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPES.get(state.getValue(FACING));
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (level.getBlockEntity(pos) instanceof PedestalEntity pedestal) {
      if (!level.isClientSide) {
        int slot = PedestalEntity.modifierSlot(stack);
        if (player.isShiftKeyDown() || stack.isEmpty()) {
          int remove = slot;
          if (player.isShiftKeyDown() && slot == 0)
            for (int i = 1; i < pedestal.items.getSlots(); i++)
              if (!pedestal.items.getStackInSlot(i).isEmpty()) remove = i;
          ItemStack taken = pedestal.items.extractItem(remove, 64, false);
          if (!player.getInventory().add(taken)) player.drop(taken, false);
        } else {
          int offered = 1;
          ItemStack rest = pedestal.items.insertItem(slot, stack.copyWithCount(offered), false);
          int inserted = offered - rest.getCount();
          if (inserted > 0) stack.shrink(inserted);
          else {
            ItemStack taken = pedestal.items.extractItem(slot, 64, false);
            if (!player.getInventory().add(taken)) player.drop(taken, false);
          }
        }
      }
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    return useItemOn(ItemStack.EMPTY, state, level, pos, player, InteractionHand.MAIN_HAND, hit)
            == ItemInteractionResult.SUCCESS
        ? InteractionResult.SUCCESS
        : InteractionResult.CONSUME;
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
    if (!state.is(next.getBlock())
        && level.getBlockEntity(pos) instanceof PedestalEntity pedestal) {
      for (int i = 0; i < pedestal.items.getSlots(); i++)
        Containers.dropItemStack(
            level,
            pos.getX() + .5,
            pos.getY() + .7,
            pos.getZ() + .5,
            pedestal.items.getStackInSlot(i));
    }
    super.onRemove(state, level, pos, next, moved);
  }
}
