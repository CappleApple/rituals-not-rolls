package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.RitualApi;
import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Topology scans chunk block-entity indexes, never a 33-cubed block volume. Contents are read only
 * on demand.
 */
public final class RitualNetwork {
  public record SlotRef(BlockPos pos, IItemHandler handler, int slot, ItemStack stack) {}

  public record Pedestal(
      BlockPos pos, IItemHandler handler, ItemStack stack, boolean catalyst, boolean subtraction) {
    public Pedestal(BlockPos pos, IItemHandler handler, ItemStack stack, boolean catalyst) {
      this(pos, handler, stack, catalyst, false);
    }
  }

  public static boolean hasModifier(IItemHandler handler, net.minecraft.world.item.Item item) {
    for (int i = 1; i < handler.getSlots(); i++)
      if (handler.getStackInSlot(i).is(item)) return true;
    return false;
  }

  public record KnowledgeSource(BlockPos pos, ResourceLocation enchantment, Set<String> entries) {}

  public record Snapshot(
      Map<ResourceLocation, Set<String>> knowledge,
      List<BlockPos> shelves,
      List<Pedestal> pedestals,
      List<SlotRef> storage,
      List<KnowledgeSource> sources,
      BlockPos table) {
    public Snapshot(
        Map<ResourceLocation, Set<String>> knowledge,
        List<BlockPos> shelves,
        List<Pedestal> pedestals,
        List<SlotRef> storage,
        List<KnowledgeSource> sources) {
      this(knowledge, shelves, pedestals, storage, sources, BlockPos.ZERO);
    }
  }

  private record Topology(List<BlockPos> positions, long expires, int revision, UUID frameId) {}

  private static final Map<ServerLevel, Map<BlockPos, Topology>> CACHE = new WeakHashMap<>();

  public static void invalidate(ServerLevel level) {
    CACHE.remove(level);
  }

  public static void clear() {
    CACHE.clear();
  }

  public static Snapshot scan(
      ServerLevel level, BlockPos center, boolean inventories, boolean fresh) {
    int radius = Math.max(Config.RADIUS.get(), Config.INVENTORY_RADIUS.get());
    var cache = CACHE.computeIfAbsent(level, l -> new HashMap<>());
    Topology topology = cache.get(center);
    if (fresh
        || topology == null
        || topology.expires < level.getGameTime()
        || topology.revision != Definitions.SERVER.revision()
        || !Objects.equals(topology.frameId, RitualSpace.frameId(level, center))) {
      Set<BlockPos> positions = new TreeSet<>(Comparator.comparingLong(BlockPos::asLong));
      for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
        for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
          LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
          if (chunk != null)
            for (BlockPos pos : chunk.getBlockEntitiesPos())
              if (pos.distSqr(center) <= radius * radius
                  && RitualSpace.sameSpace(level, center, pos)) positions.add(pos.immutable());
        }
      for (var pos : RitualApi.extraCandidates(level, center, radius))
        if (pos.distSqr(center) <= radius * radius
            && RitualSpace.sameSpace(level, center, pos)
            && RitualSpace.loaded(level, pos)) positions.add(pos.immutable());
      topology =
          new Topology(
              List.copyOf(positions),
              level.getGameTime() + Config.CACHE_TICKS.get(),
              Definitions.SERVER.revision(),
              RitualSpace.frameId(level, center));
      // Bound abandoned table caches without any world polling.
      if (cache.size() > 256)
        cache.entrySet().removeIf(e -> e.getValue().expires < level.getGameTime());
      cache.put(center.immutable(), topology);
    }
    Map<ResourceLocation, Set<String>> knowledge = new TreeMap<>();
    List<BlockPos> shelves = new ArrayList<>();
    List<KnowledgeSource> sources = new ArrayList<>();
    List<Pedestal> pedestals = new ArrayList<>();
    List<SlotRef> storage = new ArrayList<>();
    for (BlockPos pos : topology.positions) {
      if (!RitualSpace.loaded(level, pos) || !RitualSpace.sameSpace(level, center, pos)) continue;
      boolean ritualRange = pos.distSqr(center) <= Config.RADIUS.get() * Config.RADIUS.get();
      IItemHandler shelf = ritualRange ? RitualApi.bookshelf(level, pos) : null;
      if (shelf != null) {
        boolean has = false;
        for (int i = 0; i < shelf.getSlots(); i++) {
          var book = shelf.getStackInSlot(i);
          var d = Knowledge.data(book);
          if (d != null
              && (book.is(RitualsNotRolls.BOOK)
                  || (book.is(RitualsNotRolls.PAGE) && d.entries().size() == 1))) {
            Knowledge.warnStale(d);
            knowledge.computeIfAbsent(d.enchantment(), k -> new HashSet<>()).addAll(d.entries());
            sources.add(new KnowledgeSource(pos, d.enchantment(), Set.copyOf(d.entries())));
            has = true;
          }
        }
        if (has) shelves.add(pos);
        continue;
      }
      IItemHandler pedestal = ritualRange ? RitualApi.pedestal(level, pos) : null;
      if (pedestal != null && pedestal.getSlots() > 0) {
        pedestals.add(
            new Pedestal(
                pos,
                pedestal,
                pedestal.getStackInSlot(0).copy(),
                hasModifier(pedestal, RitualsNotRolls.CONSUMPTION_CATALYST.get()),
                hasModifier(pedestal, RitualsNotRolls.SUBTRACTION_CATALYST.get())));
        continue;
      }
      if (inventories
          && pos.distSqr(center) <= Config.INVENTORY_RADIUS.get() * Config.INVENTORY_RADIUS.get()) {
        var handler = RitualApi.inventory(level, pos);
        if (handler != null)
          for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && !handler.extractItem(i, 1, true).isEmpty())
              storage.add(new SlotRef(pos, handler, i, stack.copy()));
          }
      }
    }
    return new Snapshot(
        knowledge,
        List.copyOf(shelves),
        List.copyOf(pedestals),
        List.copyOf(storage),
        List.copyOf(sources),
        center.immutable());
  }
}
