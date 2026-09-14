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
import net.minecraft.world.item.Items;
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
    BOOK,
    BIND,
    BOUND_BOOK
  }

  private record Destination(BlockPos pos, int slot, ItemStack expected) {}

  private record Ingredient(ItemEntity entity, ItemStack expected, Vec3 start) {}

  private record Library(boolean hasBook, boolean known, List<Destination> accepting) {}

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
      int duration,
      List<Ingredient> leather) {}

  public static boolean knowledge(ItemStack stack) {
    return stack.is(RitualsNotRolls.PAGE)
        || stack.is(RitualsNotRolls.BOOK)
        || stack.is(RitualsNotRolls.BINDER_ITEM);
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
    if (entity.getItem().is(RitualsNotRolls.BINDER_ITEM))
      return BinderTransfers.start(level, table, entity);
    var data = Knowledge.data(entity.getItem());
    if (data == null) return false;
    List<Destination> choices;
    Kind kind = entity.getItem().is(RitualsNotRolls.PAGE) ? Kind.PAGE : Kind.BOOK;
    if (kind == Kind.PAGE) {
      if (data.entries().size() != 1) return false;
      var library = library(level, table, entity.getItem(), null);
      if (library.known) return false;
      choices = library.accepting;
      if (!library.hasBook) {
        choices = emptySlots(level, table);
        if (choices.isEmpty()) return false;
        var destination = choices.get(level.random.nextInt(choices.size()));
        var leather = claimLeather(level, table, entity);
        if (leather.isEmpty()) return false;
        begin(
            level,
            entity,
            table,
            destination,
            Kind.BIND,
            Vec3.atBottomCenterOf(table).add(0, 1.65, 0),
            leather);
        return true;
      }
    } else {
      if (entity.getItem().getCount() != 1) return false;
      choices = emptySlots(level, table);
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

  /** Check books themselves: loose shelf pages must not prevent binding a book. */
  private static Library library(
      ServerLevel level, BlockPos table, ItemStack page, Flight ignored) {
    var data = Knowledge.data(page);
    boolean hasBook = false, known = false;
    List<Destination> accepting = new ArrayList<>();
    for (var pos : RitualNetwork.scan(level, table, false, true).shelves()) {
      var handler = RitualApi.bookshelf(level, pos);
      if (handler == null) continue;
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        var book = handler.getStackInSlot(slot);
        var contents = Knowledge.data(book);
        if (!book.is(RitualsNotRolls.BOOK)
            || contents == null
            || !contents.enchantment().equals(data.enchantment())) continue;
        hasBook = true;
        if (contents.entries().containsAll(data.entries())) known = true;
        if (!reserved(level, pos, slot)
            && Knowledge.accepts(book, page, false)
            && handler.extractItem(slot, 1, true).getCount() == 1)
          accepting.add(new Destination(pos, slot, book.copy()));
      }
    }
    // A book on its way into a shared library also reserves its enchantment while binding.
    for (var flight : FLIGHTS.getOrDefault(level, Map.of()).values()) {
      if (flight == ignored
          || flight.kind == Kind.RETRIEVE
          || (flight.kind == Kind.PAGE && ignored != null)
          || !inLibrary(level, table, flight.destination.pos)) continue;
      var contents = Knowledge.data(flight.expected);
      if (contents != null && contents.enchantment().equals(data.enchantment())) {
        if (flight.kind != Kind.PAGE) hasBook = true;
        if (contents.entries().containsAll(data.entries())) known = true;
      }
    }
    return new Library(hasBook, known, accepting);
  }

  private static boolean inLibrary(ServerLevel level, BlockPos table, BlockPos pos) {
    return RitualSpace.loaded(level, pos)
        && RitualSpace.sameSpace(level, table, pos)
        && pos.distSqr(table) <= Config.RADIUS.get() * Config.RADIUS.get();
  }

  private static List<Destination> emptySlots(ServerLevel level, BlockPos table) {
    List<Destination> choices = new ArrayList<>();
    int radius = Config.RADIUS.get();
    for (int cx = (table.getX() - radius) >> 4; cx <= (table.getX() + radius) >> 4; cx++)
      for (int cz = (table.getZ() - radius) >> 4; cz <= (table.getZ() + radius) >> 4; cz++) {
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) continue;
        for (var be : chunk.getBlockEntities().values()) {
          if (!(be instanceof ChiseledBookShelfBlockEntity shelf)
              || !inLibrary(level, table, shelf.getBlockPos())) continue;
          for (int slot = 0; slot < shelf.getContainerSize(); slot++)
            if (shelf.getItem(slot).isEmpty() && !reserved(level, shelf.getBlockPos(), slot))
              choices.add(new Destination(shelf.getBlockPos().immutable(), slot, ItemStack.EMPTY));
        }
      }
    return choices;
  }

  /** Split only the three committed-to-animation leather items; surplus drops remain usable. */
  private static List<Ingredient> claimLeather(ServerLevel level, BlockPos table, ItemEntity page) {
    var local = new AABB(table).inflate(1.3, 1.5, 1.3);
    var center = RitualSpace.worldCenter(level, table);
    var bounds = new AABB(center, center);
    for (double x : new double[] {local.minX, local.maxX})
      for (double y : new double[] {local.minY, local.maxY})
        for (double z : new double[] {local.minZ, local.maxZ}) {
          var corner = RitualSpace.toWorld(level, table, new Vec3(x, y, z));
          bounds = bounds.minmax(new AABB(corner, corner));
        }
    var candidates =
        level.getEntitiesOfClass(
            ItemEntity.class,
            bounds,
            e ->
                e.isAlive()
                    && e.getItem().is(Items.LEATHER)
                    && !active(e)
                    && !e.getPersistentData().getBoolean("ritualsnotrolls_captured")
                    && e.getOwner() == page.getOwner()
                    && local.contains(RitualSpace.toLocal(level, table, e.position())));
    candidates.sort(
        Comparator.comparingDouble((ItemEntity e) -> e.distanceToSqr(page))
            .thenComparing(ItemEntity::getUUID));
    if (candidates.stream().mapToLong(e -> e.getItem().getCount()).sum() < 3) return List.of();
    // Claim the page before entity-join callbacks can attempt to bind it again.
    capture(page);
    List<Ingredient> leather = new ArrayList<>();
    int remaining = 3;
    for (var source : candidates) {
      int amount = Math.min(remaining, source.getItem().getCount());
      ItemEntity claimed = source;
      if (amount < source.getItem().getCount()) {
        var original = source.getItem().copy();
        var part = original.copyWithCount(amount);
        claimed = new ItemEntity(level, source.getX(), source.getY(), source.getZ(), part);
        claimed.setThrower((ServerPlayer) page.getOwner());
        if (!level.addFreshEntity(claimed)
            || !source.isAlive()
            || !ItemStack.matches(source.getItem(), original)
            || !claimed.isAlive()
            || !ItemStack.matches(claimed.getItem(), original.copyWithCount(amount))) {
          claimed.discard();
          leather.forEach(i -> release(i.entity));
          release(page);
          return List.of();
        }
        // Entity join restores saved capture flags, so claim a split only after it has joined.
        capture(claimed);
        source.setItem(original.copyWithCount(original.getCount() - amount));
      } else capture(claimed);
      leather.add(
          new Ingredient(
              claimed,
              claimed.getItem().copy(),
              RitualSpace.toLocal(level, table, claimed.position())));
      remaining -= amount;
      if (remaining == 0) break;
    }
    return List.copyOf(leather);
  }

  static void capture(ItemEntity entity) {
    if (!active(entity)) {
      entity.getPersistentData().putBoolean(GRAVITY, entity.isNoGravity());
      entity.getPersistentData().putBoolean(CAPTURED, true);
    }
    entity.setNoGravity(true);
    entity.setDeltaMovement(Vec3.ZERO);
    // Never-pickup items cannot merge either; each ingredient claim stays a real stable stack.
    entity.setNeverPickUp();
    entity.hasImpulse = true;
  }

  private static void begin(
      ServerLevel level,
      ItemEntity entity,
      BlockPos table,
      Destination destination,
      Kind kind,
      Vec3 end) {
    begin(level, entity, table, destination, kind, end, List.of());
  }

  private static void begin(
      ServerLevel level,
      ItemEntity entity,
      BlockPos table,
      Destination destination,
      Kind kind,
      Vec3 end,
      List<Ingredient> leather) {
    capture(entity);
    var start = RitualSpace.toLocal(level, table, entity.position());
    int duration = kind == Kind.BIND ? 40 : Math.min(50, 20 + (int) (start.distanceTo(end) * 2));
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
                duration,
                leather));
    sparkle(level, entity.position(), 12);
    RitualSpace.sound(
        level, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, .4F, 1.6F);
  }

  public static void tick(ServerLevel level) {
    var flights = FLIGHTS.get(level);
    if (flights == null) {
      BinderTransfers.tick(level);
      return;
    }
    for (var flight : new ArrayList<>(flights.values())) {
      var entity = flight.entity;
      if (!entity.isAlive()
          || !ItemStack.matches(entity.getItem(), flight.expected)
          || !RitualSpace.loaded(level, flight.table)
          || !RitualSpace.loaded(level, flight.destination.pos)
          || !Objects.equals(flight.frame, RitualSpace.frameId(level, flight.table))
          || !RitualSpace.sameSpace(level, flight.table, flight.destination.pos)
          || !level.getBlockState(flight.table).is(Blocks.ENCHANTING_TABLE)
          || RitualApi.bookshelf(level, flight.destination.pos) == null
          || (flight.kind == Kind.BIND
              && (!(entity.getOwner() instanceof ServerPlayer owner)
                  || owner.isRemoved()
                  || owner.serverLevel() != level
                  || owner.distanceToSqr(RitualSpace.worldCenter(level, flight.table)) > 4096))
          || flight.leather.stream()
              .anyMatch(
                  i -> !i.entity.isAlive() || !ItemStack.matches(i.entity.getItem(), i.expected))) {
        release(flight);
        flights.remove(entity.getUUID());
        continue;
      }
      double t = Math.min(1, (level.getGameTime() - flight.started) / (double) flight.duration);
      double eased = t * t * (3 - 2 * t);
      var local = flight.start.lerp(flight.end, eased).add(0, Math.sin(t * Math.PI) * .8, 0);
      if (flight.kind == Kind.BIND) {
        local =
            flight
                .start
                .lerp(flight.end, Math.min(1, t * 3))
                .add(0, Math.sin(t * Math.PI * 4) * .08, 0);
        for (int i = 0; i < flight.leather.size(); i++) {
          var ingredient = flight.leather.get(i);
          double angle = t * Math.PI * 4 + i * Math.PI * 2 / flight.leather.size();
          double radius = .7 * Math.sin(t * Math.PI);
          var orbit = flight.end.add(Math.cos(angle) * radius, .1, Math.sin(angle) * radius);
          var position =
              RitualSpace.toWorld(
                  level, flight.table, ingredient.start.lerp(orbit, Math.min(1, t * 4)));
          ingredient.entity.setPos(position);
          ingredient.entity.setDeltaMovement(Vec3.ZERO);
          ingredient.entity.setNeverPickUp();
          ingredient.entity.hasImpulse = true;
          if (level.getGameTime() % 2 == 0) sparkle(level, position, 2);
        }
      }
      var at = RitualSpace.toWorld(level, flight.table, local);
      entity.setPos(at);
      entity.setDeltaMovement(Vec3.ZERO);
      entity.hasImpulse = true;
      entity.setNeverPickUp();
      if (level.getGameTime() % 2 == 0) sparkle(level, at.add(0, .15, 0), 2);
      if (t >= 1) {
        boolean bound = flight.kind == Kind.BIND && bind(level, flight);
        if (!bound) {
          if (flight.kind != Kind.RETRIEVE && flight.kind != Kind.BIND) insert(level, flight);
          release(flight);
        }
        sparkle(level, at, 16);
        flights.remove(entity.getUUID(), flight);
      }
    }
    if (flights.isEmpty()) FLIGHTS.remove(level);
    BinderTransfers.tick(level);
  }

  /** All ingredients still exist until there is a real book to own their value. */
  private static boolean bind(ServerLevel level, Flight flight) {
    var destination = flight.destination;
    var handler = RitualApi.bookshelf(level, destination.pos);
    if (!(level.getBlockEntity(destination.pos) instanceof ChiseledBookShelfBlockEntity)
        || handler == null
        || destination.slot >= handler.getSlots()
        || !handler.getStackInSlot(destination.slot).isEmpty()
        || library(level, flight.table, flight.expected, flight).hasBook) return false;
    var book = Knowledge.book(Knowledge.data(flight.expected));
    if (!handler.insertItem(destination.slot, book, true).isEmpty()) return false;
    var entity = flight.entity;
    if (flight.expected.getCount() > 1) {
      var remainder =
          new ItemEntity(
              level,
              entity.getX(),
              entity.getY(),
              entity.getZ(),
              flight.expected.copyWithCount(flight.expected.getCount() - 1));
      remainder.setThrower((ServerPlayer) entity.getOwner());
      remainder.setPickUpDelay(20);
      if (!level.addFreshEntity(remainder)) return false;
      if (!entity.isAlive()
          || !ItemStack.matches(entity.getItem(), flight.expected)
          || !remainder.isAlive()
          || !ItemStack.matches(
              remainder.getItem(), flight.expected.copyWithCount(flight.expected.getCount() - 1))
          || flight.leather.stream()
              .anyMatch(
                  i -> !i.entity.isAlive() || !ItemStack.matches(i.entity.getItem(), i.expected))
          || !handler.getStackInSlot(destination.slot).isEmpty()
          || library(level, flight.table, flight.expected, flight).hasBook) {
        remainder.discard();
        return false;
      }
    }
    flight.leather.forEach(i -> i.entity.discard());
    restore(entity);
    entity.setItem(book);
    begin(
        level,
        entity,
        flight.table,
        destination,
        Kind.BOUND_BOOK,
        shelfFront(level, destination.pos, destination.slot));
    RitualSpace.sound(
        level, flight.table, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, .7F, .8F);
    return true;
  }

  private static void insert(ServerLevel level, Flight flight) {
    var destination = flight.destination;
    var handler = RitualApi.bookshelf(level, destination.pos);
    if (handler == null || destination.slot >= handler.getSlots()) return;
    var current = handler.getStackInSlot(destination.slot).copy();
    if (flight.kind == Kind.PAGE) {
      // Concurrent arrivals may add entries to this same book while a page is in flight.
      var before = Knowledge.data(destination.expected);
      var now = Knowledge.data(current);
      if (before == null
          || now == null
          || !before.enchantment().equals(now.enchantment())
          || !now.entries().containsAll(before.entries())) return;
      var expected = destination.expected.copy();
      expected.set(RitualsNotRolls.KNOWLEDGE, now);
      if (!ItemStack.matches(current, expected)) return;
    } else if (!ItemStack.matches(current, destination.expected)) return;
    var entity = flight.entity;
    if (flight.kind == Kind.BOOK || flight.kind == Kind.BOUND_BOOK) {
      if (flight.kind == Kind.BOUND_BOOK
          && library(level, flight.table, entity.getItem(), flight).hasBook) return;
      if (!(level.getBlockEntity(destination.pos) instanceof ChiseledBookShelfBlockEntity)
          || !handler.insertItem(destination.slot, entity.getItem(), true).isEmpty()) return;
      var remainder = handler.insertItem(destination.slot, entity.getItem().copy(), false);
      if (remainder.isEmpty()) entity.discard();
      else entity.setItem(remainder);
      return;
    }
    if (library(level, flight.table, entity.getItem(), flight).known
        || !Knowledge.accepts(current, entity.getItem(), false)) return;
    var extracted = handler.extractItem(destination.slot, 1, false);
    if (extracted.isEmpty()) return;
    var receipt = new Transfers.Receipt(handler, destination.slot, destination.pos, extracted);
    if (!ItemStack.matches(extracted, current)) {
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
                    && f.kind != Kind.PAGE
                    && f.destination.pos.equals(pos)
                    && f.destination.slot == slot);
  }

  static boolean pending(ServerLevel level, BlockPos table) {
    return FLIGHTS.getOrDefault(level, Map.of()).values().stream()
        .anyMatch(f -> f.kind != Kind.RETRIEVE && inLibrary(level, table, f.destination.pos));
  }

  static void cancel(ServerLevel level, ItemEntity entity) {
    var flights = FLIGHTS.get(level);
    var flight = flights == null ? null : flights.remove(entity.getUUID());
    if (flight != null) release(flight);
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

  private static void release(Flight flight) {
    release(flight.entity);
    flight.leather.forEach(i -> release(i.entity));
  }

  static void release(ItemEntity entity) {
    restore(entity);
    entity.setDeltaMovement(0, .08, 0);
  }

  public static void unload(ServerLevel level) {
    BinderTransfers.unload(level);
    var flights = FLIGHTS.remove(level);
    if (flights != null) flights.values().forEach(KnowledgeTransfers::release);
  }

  public static void clear() {
    BinderTransfers.clear();
    new ArrayList<>(FLIGHTS.keySet()).forEach(KnowledgeTransfers::unload);
  }
}
