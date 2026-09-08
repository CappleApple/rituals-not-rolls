package com.cappleapple.ritualsnotrolls.api;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.ritual.RitualEngine;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * Register adapters during common setup. Null means "not handled"; use NeoForge
 * insertion/extraction contracts.
 */
public final class RitualApi {
  public static final TagKey<Block> PEDESTALS =
      TagKey.create(Registries.BLOCK, RitualsNotRolls.id("enchanting_pedestals"));
  public static final TagKey<Block> BOOKSHELVES =
      TagKey.create(Registries.BLOCK, RitualsNotRolls.id("knowledge_bookshelves"));

  public interface Adapter {
    IItemHandler find(ServerLevel level, BlockPos pos);
  }

  public interface CandidateProvider {
    Collection<BlockPos> positions(ServerLevel level, BlockPos table, int radius);
  }

  private static final List<Adapter> PEDESTAL_ADAPTERS = new CopyOnWriteArrayList<>(),
      SHELF_ADAPTERS = new CopyOnWriteArrayList<>(),
      INVENTORY_ADAPTERS = new CopyOnWriteArrayList<>();
  private static final List<CandidateProvider> CANDIDATES = new CopyOnWriteArrayList<>();

  public static void registerPedestal(Adapter adapter) {
    PEDESTAL_ADAPTERS.add(adapter);
  }

  public static void registerBookshelf(Adapter adapter) {
    SHELF_ADAPTERS.add(adapter);
  }

  public static void registerInventory(Adapter adapter) {
    INVENTORY_ADAPTERS.add(adapter);
  }

  public static void registerCandidateProvider(CandidateProvider provider) {
    CANDIDATES.add(provider);
  }

  public static Collection<BlockPos> extraCandidates(
      ServerLevel level, BlockPos table, int radius) {
    Set<BlockPos> out = new HashSet<>();
    for (var provider : CANDIDATES) out.addAll(provider.positions(level, table, radius));
    return out;
  }

  private static IItemHandler adapted(List<Adapter> list, ServerLevel level, BlockPos pos) {
    for (var adapter : list) {
      var result = adapter.find(level, pos);
      if (result != null) return result;
    }
    return null;
  }

  public static IItemHandler inventory(ServerLevel level, BlockPos pos) {
    var custom = adapted(INVENTORY_ADAPTERS, level, pos);
    if (custom != null) return custom;
    var direct = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    if (direct != null) return direct;
    // Sided-only inventories are valid; pick a deterministic exposed face. Never bypass extraction
    // rules.
    for (var face : Direction.values()) {
      var sided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
      if (sided != null) return sided;
    }
    return null;
  }

  public static IItemHandler pedestal(ServerLevel level, BlockPos pos) {
    var custom = adapted(PEDESTAL_ADAPTERS, level, pos);
    if (custom == null)
      custom = com.cappleapple.ritualsnotrolls.compat.ForeignPedestals.find(level, pos);
    return custom != null
        ? custom
        : level.getBlockState(pos).is(PEDESTALS) ? inventory(level, pos) : null;
  }

  public static IItemHandler bookshelf(ServerLevel level, BlockPos pos) {
    var custom = adapted(SHELF_ADAPTERS, level, pos);
    if (custom != null) return custom;
    var be = level.getBlockEntity(pos);
    if (be instanceof ChiseledBookShelfBlockEntity shelf) return new InvWrapper(shelf);
    if (level.getBlockState(pos).is(BOOKSHELVES)) {
      var handler = inventory(level, pos);
      return handler != null
          ? handler
          : be instanceof Container container ? new InvWrapper(container) : null;
    }
    return null;
  }

  public static Definitions.Snapshot definitions() {
    return Definitions.SERVER;
  }

  public static String ritualState(ServerLevel level, BlockPos pos) {
    return RitualEngine.state(level, pos);
  }
}
