package com.cappleapple.ritualsnotrolls.pedestal;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class PedestalEntity extends BlockEntity {
  public final ItemStackHandler items =
      new ItemStackHandler(3) {
        @Override
        public int getSlotLimit(int slot) {
          return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return switch (slot) {
            case 0 -> !isModifier(stack);
            case 1 -> stack.is(RitualsNotRolls.CONSUMPTION_CATALYST);
            case 2 -> stack.is(RitualsNotRolls.SUBTRACTION_CATALYST);
            default -> false;
          };
        }

        @Override
        protected void onContentsChanged(int slot) {
          setChanged();
          if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
      };

  public static boolean isModifier(ItemStack stack) {
    return stack.is(RitualsNotRolls.CONSUMPTION_CATALYST)
        || stack.is(RitualsNotRolls.SUBTRACTION_CATALYST);
  }

  public static int modifierSlot(ItemStack stack) {
    return stack.is(RitualsNotRolls.CONSUMPTION_CATALYST)
        ? 1
        : stack.is(RitualsNotRolls.SUBTRACTION_CATALYST) ? 2 : 0;
  }

  public PedestalEntity(BlockPos pos, BlockState state) {
    super(RitualsNotRolls.PEDESTAL_ENTITY.get(), pos, state);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level != null && !level.isClientSide) migrateLegacyExperience();
  }

  /**
   * Preserve old attached XP catalysts: display on an empty pedestal, otherwise return to world.
   */
  public void migrateLegacyExperience() {
    if (level == null
        || level.isClientSide
        || !items.getStackInSlot(1).is(RitualsNotRolls.XP_CATALYST)) return;
    var catalyst = items.extractItem(1, 1, false);
    var remainder = items.insertItem(0, catalyst, false);
    if (!remainder.isEmpty())
      net.minecraft.world.Containers.dropItemStack(
          level,
          worldPosition.getX() + .5,
          worldPosition.getY() + .5,
          worldPosition.getZ() + .5,
          remainder);
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
    super.saveAdditional(tag, lookup);
    tag.put("inventory", items.serializeNBT(lookup));
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
    super.loadAdditional(tag, lookup);
    var inventory = tag.getCompound("inventory").copy();
    inventory.putInt("Size", 3);
    items.deserializeNBT(lookup, inventory);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
    return saveWithoutMetadata(lookup);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
