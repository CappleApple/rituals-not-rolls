package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.RitualApi;
import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.*;

/** Real dropped items travel between the table and shelves; storage changes only at arrival. */
public final class KnowledgeTransfers {
  private static final String CAPTURED = "ritualsnotrolls_library_flight";
  private static final String GRAVITY = "ritualsnotrolls_library_gravity";
  private static final String RETURNED = "ritualsnotrolls_library_returned";
  private static final Map<ServerLevel, Map<UUID, Flight>> FLIGHTS = new WeakHashMap<>();

  private enum Kind {
    RETRIEVE,
    PAGE,
    BOOK
  }

  private record Destination(BlockPos pos, int slot, ItemStack expected) {}

  private record Flight(
      ItemEntity entity,
      ItemStack expected,
      BlockPos table,
      UUID frame,
      Destination destination,
      Kind kind,
      Vec3 start,
      Vec3 end,
      long started,
      int duration) {}

  public static boolean knowledge(ItemStack stack) {
    return stack.is(RitualsNotRolls.PAGE) || stack.is(RitualsNotRolls.BOOK);
  }

  public static boolean active(ItemEntity entity) {
    return entity.getPersistentData().getBoolean(CAPTURED);
  }

  /** Shift-click retrieves a book first, otherwise exactly one loose page. */
  public static boolean retrieve(
      ServerPlayer player, BlockPos table, ResourceLocation enchantment) {
    var level = player.serverLevel();
    if (!RitualSpace.loaded(level, table)
        || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)
        || player.distanceToSqr(RitualSpace.worldCenter(level, table)) > 64) return false;
    var shelves = RitualNetwork.scan(level, table, false, true).shelves();
    for (var type : List.of(RitualsNotRolls.BOOK.get(), RitualsNotRolls.PAGE.get())) {
      for (var pos : shelves) {
        var handler = RitualApi.bookshelf(level, pos);
        if (handler == null) continue;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
          var current = handler.getStackInSlot(slot);
          var data = Knowledge.data(current);
          if (!current.is(type)
              || data == null
              || !data.enchantment().equals(enchantment)
              || reserved(level, pos, slot)) continue;
          var probe = handler.extractItem(slot, 1, true);
          if (probe.getCount() != 1 || !ItemStack.isSameItemSameComponents(current, probe))
            continue;
          var taken = handler.extractItem(slot, 1, false);
          if (taken.isEmpty()) continue;
          var start = RitualSpace.toWorld(level, table, shelfFront(level, pos, slot));
          var entity = new ItemEntity(level, start.x, start.y, start.z, taken);
          entity.setThrower(player);
          entity.getPersistentData().putBoolean(RETURNED, true);
          entity.getPersistentData().putBoolean("ritualsnotrolls_loose_page", true);
          if (!level.addFreshEntity(entity)) {
            Transfers.rollback(level, List.of(new Transfers.Receipt(handler, slot, pos, taken)));
            return false;
          }
          begin(
              level,
              entity,
              table,
              new Destination(pos, slot, ItemStack.EMPTY),
              Kind.RETRIEVE,
              Vec3.atBottomCenterOf(table).add(0, 1.55, 0));
          return true;
        }
      }
    }
    return false;
  }

  public static boolean tryStart(ServerLevel level, BlockPos table, ItemEntity entity) {
    if (!knowledge(entity.getItem())
        || active(entity)
        || entity.getPersistentData().getBoolean(RETURNED)
        || !(entity.getOwner() instanceof ServerPlayer player)
        || player.isRemoved()
        || player.serverLevel() != level
        || !entity.isAlive()
        || !RitualSpace.loaded(level, table)
        || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)
        || player.distanceToSqr(RitualSpace.worldCenter(level, table)) > 4096
        || !new AABB(table)
            .inflate(1.3, 1.5, 1.3)
            .contains(RitualSpace.toLocal(level, table, entity.position()))) return false;
    var data = Knowledge.data(entity.getItem());
    if (data == null) return false;
    List<Destination> choices = new ArrayList<>();
    Kind kind = entity.getItem().is(RitualsNotRolls.PAGE) ? Kind.PAGE : Kind.BOOK;
    if (kind == Kind.PAGE) {
      if (data.entries().size() != 1) return false;
      for (var pos : RitualNetwork.scan(level, table, false, true).shelves()) {
        var handler = RitualApi.bookshelf(level, pos);
        if (handler == null) continue;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
          var book = handler.getStackInSlot(slot);
          if (!reserved(level, pos, slot)
              && Knowledge.accepts(book, entity.getItem(), false)
              && handler.extractItem(slot, 1, true).getCount() == 1)
            choices.add(new Destination(pos, slot, book.copy()));
        }
      }
    } else {
      if (entity.getItem().getCount() != 1) return false;
      int radius = Config.RADIUS.get();
      for (int cx = (table.getX() - radius) >> 4; cx <= (table.getX() + radius) >> 4; cx++)
        for (int cz = (table.getZ() - radius) >> 4; cz <= (table.getZ() + radius) >> 4; cz++) {
          var chunk = level.getChunkSource().getChunkNow(cx, cz);
          if (chunk == null) continue;
          for (var be : chunk.getBlockEntities().values()) {
            if (!(be instanceof ChiseledBookShelfBlockEntity shelf)
                || !RitualSpace.sameSpace(level, table, shelf.getBlockPos())
                || shelf.getBlockPos().distSqr(table) > radius * radius) continue;
            for (int slot = 0; slot < shelf.getContainerSize(); slot++)
              if (shelf.getItem(slot).isEmpty() && !reserved(level, shelf.getBlockPos(), slot))
                choices.add(
                    new Destination(shelf.getBlockPos().immutable(), slot, ItemStack.EMPTY));
          }
        }
    }
    if (choices.isEmpty()) return false;
    // A page chooses a stable nearest accepting book; books use a random open shelf slot.
    choices.sort(
        Comparator.comparingDouble((Destination d) -> d.pos.distSqr(table))
            .thenComparingLong(d -> d.pos.asLong())
            .thenComparingInt(Destination::slot));
    var destination = choices.get(kind == Kind.BOOK ? level.random.nextInt(choices.size()) : 0);
    begin(
        level,
        entity,
        table,
        destination,
        kind,
        shelfFront(level, destination.pos, destination.slot));
    return true;
  }

  private static void begin(
      ServerLevel level,
      ItemEntity entity,
      BlockPos table,
      Destination destination,
      Kind kind,
      Vec3 end) {
    entity.getPersistentData().putBoolean(GRAVITY, entity.isNoGravity());
    entity.getPersistentData().putBoolean(CAPTURED, true);
    entity.setNoGravity(true);
    entity.setDeltaMovement(Vec3.ZERO);
    entity.setPickUpDelay(10);
    entity.hasImpulse = true;
    var start = RitualSpace.toLocal(level, table, entity.position());
    int duration = Math.min(50, 20 + (int) (start.distanceTo(end) * 2));
    FLIGHTS
        .computeIfAbsent(level, l -> new LinkedHashMap<>())
        .put(
            entity.getUUID(),
            new Flight(
                entity,
                entity.getItem().copy(),
                table.immutable(),
                RitualSpace.frameId(level, table),
                destination,
                kind,
                start,
                end,
                level.getGameTime(),
                duration));
    sparkle(level, entity.position(), 12);
    RitualSpace.sound(
        level, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, .4F, 1.6F);
  }

  public static void tick(ServerLevel level) {
    var flights = FLIGHTS.get(level);
    if (flights == null) return;
    for (var flight : new ArrayList<>(flights.values())) {
      var entity = flight.entity;
      if (!entity.isAlive()
          || !ItemStack.matches(entity.getItem(), flight.expected)
          || !RitualSpace.loaded(level, flight.table)
          || !RitualSpace.loaded(level, flight.destination.pos)
          || !Objects.equals(flight.frame, RitualSpace.frameId(level, flight.table))
          || !RitualSpace.sameSpace(level, flight.table, flight.destination.pos)
          || !level.getBlockState(flight.table).is(Blocks.ENCHANTING_TABLE)
          || RitualApi.bookshelf(level, flight.destination.pos) == null) {
        release(entity);
        flights.remove(entity.getUUID());
        continue;
      }
      double t = Math.min(1, (level.getGameTime() - flight.started) / (double) flight.duration);
      double eased = t * t * (3 - 2 * t);
      var local = flight.start.lerp(flight.end, eased).add(0, Math.sin(t * Math.PI) * .8, 0);
      var at = RitualSpace.toWorld(level, flight.table, local);
      entity.setPos(at);
      entity.setDeltaMovement(Vec3.ZERO);
      entity.hasImpulse = true;
      entity.setPickUpDelay(10);
      if (level.getGameTime() % 2 == 0) sparkle(level, at.add(0, .15, 0), 2);
      if (t >= 1) {
        if (flight.kind != Kind.RETRIEVE) insert(level, flight);
        sparkle(level, at, 16);
        release(entity);
        flights.remove(entity.getUUID());
      }
    }
    if (flights.isEmpty()) FLIGHTS.remove(level);
  }

  private static void insert(ServerLevel level, Flight flight) {
    var destination = flight.destination;
    var handler = RitualApi.bookshelf(level, destination.pos);
    if (handler == null || destination.slot >= handler.getSlots()) return;
    var current = handler.getStackInSlot(destination.slot);
    if (!ItemStack.matches(current, destination.expected)) return;
    var entity = flight.entity;
    if (flight.kind == Kind.BOOK) {
      if (!(level.getBlockEntity(destination.pos) instanceof ChiseledBookShelfBlockEntity)
          || !handler.insertItem(destination.slot, entity.getItem(), true).isEmpty()) return;
      var remainder = handler.insertItem(destination.slot, entity.getItem().copy(), false);
      if (remainder.isEmpty()) entity.discard();
      else entity.setItem(remainder);
      return;
    }
    if (!Knowledge.accepts(current, entity.getItem(), false)) return;
    var extracted = handler.extractItem(destination.slot, 1, false);
    if (extracted.isEmpty()) return;
    var receipt = new Transfers.Receipt(handler, destination.slot, destination.pos, extracted);
    if (!ItemStack.matches(extracted, destination.expected)) {
      Transfers.rollback(level, List.of(receipt));
      return;
    }
    var book = extracted.copy();
    var page = entity.getItem().copy();
    if (!Knowledge.add(book, page, false)
        || !handler.insertItem(destination.slot, book, true).isEmpty()
        || !handler.insertItem(destination.slot, book, false).isEmpty()) {
      Transfers.rollback(level, List.of(receipt));
      return;
    }
    if (page.isEmpty()) entity.discard();
    else entity.setItem(page);
  }

  private static boolean reserved(ServerLevel level, BlockPos pos, int slot) {
    return FLIGHTS.getOrDefault(level, Map.of()).values().stream()
        .anyMatch(
            f ->
                f.kind != Kind.RETRIEVE
                    && f.destination.pos.equals(pos)
                    && f.destination.slot == slot);
  }

  private static Vec3 shelfFront(ServerLevel level, BlockPos pos, int slot) {
    var state = level.getBlockState(pos);
    if (!state.hasProperty(
        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))
      return Vec3.atCenterOf(pos);
    var facing =
        state.getValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties
                .HORIZONTAL_FACING);
    // Slots run left to right when looking at the shelf front.
    var side = facing.getCounterClockWise();
    double offset = ((slot % 3) - 1) * .25;
    return Vec3.atCenterOf(pos)
        .add(
            facing.getStepX() * .6 + side.getStepX() * offset,
            slot < 3 ? .2 : -.2,
            facing.getStepZ() * .6 + side.getStepZ() * offset);
  }

  private static void sparkle(ServerLevel level, Vec3 at, int count) {
    level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, count, .13, .13, .13, .15);
    level.sendParticles(
        ParticleTypes.END_ROD, at.x, at.y, at.z, Math.max(1, count / 4), .08, .08, .08, .005);
  }

  /** Serialized recovery also releases a flight if the world closes or its chunk unloads. */
  public static void restore(ItemEntity entity) {
    if (!active(entity)) return;
    entity.setNoGravity(entity.getPersistentData().getBoolean(GRAVITY));
    entity.getPersistentData().remove(CAPTURED);
    entity.getPersistentData().remove(GRAVITY);
    entity.setPickUpDelay(20);
    entity.hasImpulse = true;
  }

  private static void release(ItemEntity entity) {
    restore(entity);
    entity.setDeltaMovement(0, .08, 0);
  }

  public static void unload(ServerLevel level) {
    var flights = FLIGHTS.remove(level);
    if (flights != null) flights.values().forEach(f -> release(f.entity));
  }

  public static void clear() {
    new ArrayList<>(FLIGHTS.keySet()).forEach(KnowledgeTransfers::unload);
  }
}
