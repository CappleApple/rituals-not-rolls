package com.cappleapple.ritualsnotrolls.compat;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.api.RitualApi;
import com.cappleapple.ritualsnotrolls.network.Networking;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Optional native-inventory bridges; no compile-time dependency on either pedestal mod. */
public final class ForeignPedestals {
  public static final String KEY = "ritualsnotrolls_modifiers";

  public static boolean supported(BlockState state) {
    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    return id.equals("supplementaries:pedestal") || id.equals("irons_spellbooks:pedestal");
  }

  private record Access(Method get, Method set) {}

  private static final ClassValue<Optional<Access>> HELD =
      new ClassValue<>() {
        protected Optional<Access> computeValue(Class<?> type) {
          try {
            return Optional.of(
                new Access(
                    type.getMethod("getHeldItem"), type.getMethod("setHeldItem", ItemStack.class)));
          } catch (ReflectiveOperationException e) {
            return Optional.empty();
          }
        }
      };

  public static ItemStackHandler modifiers(BlockEntity be) {
    var handler =
        new ItemStackHandler(2) {
          public int getSlotLimit(int slot) {
            return 1;
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0
                ? stack.is(RitualsNotRolls.CONSUMPTION_CATALYST)
                : stack.is(RitualsNotRolls.SUBTRACTION_CATALYST);
          }

          protected void onContentsChanged(int slot) {
            be.getPersistentData().put(KEY, serializeNBT(be.getLevel().registryAccess()));
            be.setChanged();
            if (be.getLevel() instanceof ServerLevel level) {
              PacketDistributor.sendToPlayersTrackingChunk(
                  level,
                  new net.minecraft.world.level.ChunkPos(be.getBlockPos()),
                  new Networking.PedestalModifiers(
                      level.dimension().location(),
                      be.getBlockPos(),
                      be.getPersistentData().getCompound(KEY).copy()));
            }
          }
        };
    if (be.getLevel() != null && be.getPersistentData().contains(KEY)) {
      var nbt = be.getPersistentData().getCompound(KEY).copy();
      nbt.putInt("Size", 2);
      handler.deserializeNBT(be.getLevel().registryAccess(), nbt);
    }
    return handler;
  }

  public static IItemHandler find(ServerLevel level, BlockPos pos) {
    if (!supported(level.getBlockState(pos))) return null;
    var be = level.getBlockEntity(pos);
    if (be == null) return null;
    IItemHandler nativeItems = RitualApi.inventory(level, pos);
    if (nativeItems == null && be instanceof WorldlyContainer container)
      nativeItems = new SidedInvWrapper(container, Direction.DOWN);
    if (nativeItems == null && be instanceof Container container)
      nativeItems = new InvWrapper(container);
    if (nativeItems == null) {
      var access = HELD.get(be.getClass()).orElse(null);
      if (access == null) return null;
      nativeItems =
          new IItemHandler() {
            public int getSlots() {
              return 1;
            }

            public ItemStack getStackInSlot(int slot) {
              try {
                return (ItemStack) access.get().invoke(be);
              } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read native pedestal", e);
              }
            }

            private void set(ItemStack stack) {
              try {
                access.set().invoke(be, stack);
                be.setChanged();
              } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot update native pedestal", e);
              }
            }

            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
              if (stack.isEmpty() || !getStackInSlot(0).isEmpty()) return stack;
              if (!simulate) set(stack.copyWithCount(1));
              return stack.copyWithCount(stack.getCount() - 1);
            }

            public ItemStack extractItem(int slot, int amount, boolean simulate) {
              if (amount <= 0) return ItemStack.EMPTY;
              var current = getStackInSlot(0);
              var taken = current.copyWithCount(Math.min(amount, current.getCount()));
              if (!simulate) set(current.copyWithCount(current.getCount() - taken.getCount()));
              return taken;
            }

            public int getSlotLimit(int slot) {
              return 1;
            }

            public boolean isItemValid(int slot, ItemStack stack) {
              return !PedestalEntity.isModifier(stack);
            }
          };
    }
    if (nativeItems.getSlots() == 0) return null;
    final IItemHandler nativeHandler = nativeItems;
    return new IItemHandler() {
      public int getSlots() {
        return 3;
      }

      public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? nativeHandler.getStackInSlot(0) : modifiers(be).getStackInSlot(slot - 1);
      }

      public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        if (slot > 0) return modifiers(be).insertItem(slot - 1, stack, simulate);
        if (!nativeHandler.getStackInSlot(0).isEmpty()) return stack;
        var rest =
            nativeHandler.insertItem(
                0, stack.copyWithCount(Math.min(1, stack.getCount())), simulate);
        return rest.isEmpty() ? stack.copyWithCount(stack.getCount() - 1) : stack;
      }

      public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return slot == 0
            ? nativeHandler.extractItem(0, amount, simulate)
            : modifiers(be).extractItem(slot - 1, amount, simulate);
      }

      public int getSlotLimit(int slot) {
        return 1;
      }

      public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0
            ? !PedestalEntity.isModifier(stack) && nativeHandler.isItemValid(0, stack)
            : modifiers(be).isItemValid(slot - 1, stack);
      }
    };
  }

  public static boolean interact(PlayerInteractEvent.RightClickBlock event) {
    if (!supported(event.getLevel().getBlockState(event.getPos()))) return false;
    var stack = event.getItemStack();
    if (!PedestalEntity.isModifier(stack)
        && !(stack.isEmpty() && event.getEntity().isShiftKeyDown())) return false;
    if (stack.isEmpty()) {
      var be = event.getLevel().getBlockEntity(event.getPos());
      if (be == null) return false;
      var attached = modifiers(be);
      if (attached.getStackInSlot(0).isEmpty() && attached.getStackInSlot(1).isEmpty())
        return false;
    }
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    if (!(event.getLevel() instanceof ServerLevel level)) return true;
    var handler = find(level, event.getPos());
    if (handler == null) return true;
    int slot = PedestalEntity.modifierSlot(stack);
    if (slot == 0) slot = !handler.getStackInSlot(2).isEmpty() ? 2 : 1;
    var player = event.getEntity();
    if (player.isShiftKeyDown() || !handler.getStackInSlot(slot).isEmpty()) {
      var taken = handler.extractItem(slot, 1, false);
      if (!player.getInventory().add(taken)) player.drop(taken, false);
    } else if (handler.insertItem(slot, stack.copyWithCount(1), false).isEmpty()) stack.shrink(1);
    return true;
  }

  public static void removed(BlockEntity be) {
    if (!(be.getLevel() instanceof ServerLevel level)
        || !supported(be.getBlockState())
        || !level
            .getChunkSource()
            .hasChunk(be.getBlockPos().getX() >> 4, be.getBlockPos().getZ() >> 4)
        || level.getBlockState(be.getBlockPos()).is(be.getBlockState().getBlock())) return;
    var contents = modifiers(be);
    be.getPersistentData().remove(KEY);
    for (int i = 0; i < contents.getSlots(); i++)
      Containers.dropItemStack(
          level,
          be.getBlockPos().getX() + .5,
          be.getBlockPos().getY() + 1,
          be.getBlockPos().getZ() + .5,
          contents.getStackInSlot(i));
  }
}
