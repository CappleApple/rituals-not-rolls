package com.cappleapple.ritualsnotrolls.api;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;

public abstract class RitualEvent extends Event {
  public final ServerLevel level;
  public final BlockPos table;
  public final ServerPlayer player;
  public final ItemStack target;
  public final Map<ResourceLocation, Integer> enchantments;

  protected RitualEvent(
      ServerLevel level,
      BlockPos table,
      ServerPlayer player,
      ItemStack target,
      Map<ResourceLocation, Integer> enchantments) {
    this.level = level;
    this.table = table;
    this.player = player;
    this.target = target.copy();
    this.enchantments = Map.copyOf(enchantments);
  }

  public static final class Start extends RitualEvent implements ICancellableEvent {
    public Start(
        ServerLevel l, BlockPos p, ServerPlayer s, ItemStack t, Map<ResourceLocation, Integer> e) {
      super(l, p, s, t, e);
    }
  }

  public static final class Complete extends RitualEvent {
    public Complete(
        ServerLevel l, BlockPos p, ServerPlayer s, ItemStack t, Map<ResourceLocation, Integer> e) {
      super(l, p, s, t, e);
    }
  }
}
