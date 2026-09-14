package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.RitualEvent;
import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.KnowledgeTransfers;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Only newly thrown targets and active rituals are examined; idle tables never scan their
 * surroundings.
 */
public final class RitualEngine {
  private static final Map<ServerLevel, Map<BlockPos, Session>> SESSIONS = new WeakHashMap<>();
  private static final Map<ServerLevel, Map<UUID, ItemEntity>> DROPS = new WeakHashMap<>();
  private static final Map<ServerLevel, List<Tail>> TAILS = new WeakHashMap<>();

  // Visuals only: no item/player references, table reservation, resource charges, or rescans.
  private record Tail(
      BlockPos table,
      UUID frameId,
      List<RitualEffects.Path> paths,
      RitualAnimation.Schedule animation,
      long started) {}

  private static final class Session {
    final ServerPlayer owner;
    final ServerLevel level;
    final ItemStack target;
    final RitualMath.Plan plan;
    final ItemEntity entity;
    final int revision;
    final long started;
    final List<RitualEffects.Path> paths;
    final RitualAnimation.Schedule animation;
    final Vec3 anchor;
    final UUID frameId;
    final RitualConsumption consumption = new RitualConsumption();
    final Map<BlockPos, Integer> offeringTimes = new LinkedHashMap<>();
    final Set<ResourceLocation> failedSounds = new HashSet<>();

    Session(
        ServerPlayer player,
        ItemEntity entity,
        RitualAttempt attempt,
        RitualNetwork.Snapshot network,
        BlockPos table) {
      owner = player;
      level = player.serverLevel();
      this.entity = entity;
      target = entity.getItem().copy();
      this.plan = attempt.successful();
      revision = Definitions.SERVER.revision();
      started = player.level().getGameTime();
      anchor = RitualEffects.targetCenter(table, attempt.visual()).add(0, -.2, 0);
      frameId = RitualSpace.frameId(level, table);
      paths =
          RitualEffects.paths(
              player.serverLevel(),
              network,
              attempt.visual(),
              table,
              RitualSpace.toLocal(
                  level, table, player.position().add(0, player.getBbHeight() * .6, 0)));
      animation =
          RitualEffects.schedule(
              paths,
              attempt.visual().evaluation(),
              Definitions.SERVER.rules().duration(),
              Config.SEQUENTIAL_ANIMATIONS.get(),
              attempt.nativeCosts(),
              attempt.minima(),
              attempt.failing(),
              plan.experience().points());
      for (var stage : animation.stages()) {
        if (!plan.selected().containsKey(stage.enchantment())) continue;
        for (var path : paths)
          if (path.enchantment().equals(stage.enchantment())) {
            for (var pedestal : plan.evaluation().used()) {
              var item =
                  com.cappleapple.ritualsnotrolls.pedestal.PedestalGeometry.displayPosition(
                      pedestal.pos(), player.level().getBlockState(pedestal.pos()));
              if (plan.withdrawals().containsKey(pedestal.pos())
                  && path.to().distanceToSqr(item) < 1e-6)
                offeringTimes.merge(
                    pedestal.pos(), stage.start() + path.delay() + path.span(), Math::min);
            }
          }
      }
    }
  }

  public static String state(ServerLevel level, BlockPos pos) {
    return SESSIONS.getOrDefault(level, Map.of()).containsKey(pos) ? "channeling" : "idle";
  }

  /** Called once when a new ItemEntity joins the server world, not when an old item is loaded. */
  public static void observe(ItemEntity entity) {
    if (entity.level() instanceof ServerLevel level)
      DROPS.computeIfAbsent(level, l -> new LinkedHashMap<>()).put(entity.getUUID(), entity);
  }

  public static boolean tryStart(ServerLevel level, BlockPos table, ItemEntity entity) {
    if (KnowledgeTransfers.knowledge(entity.getItem()))
      return KnowledgeTransfers.tryStart(level, table, entity);
    if (!(entity.getOwner() instanceof ServerPlayer owner)
        || owner.serverLevel() != level
        || owner.isRemoved()
        || !RitualSpace.loaded(level, table)
        || owner.position().distanceToSqr(RitualSpace.worldCenter(level, table)) > 4096
        || !entity.isAlive()
        || entity.getItem().getCount() != 1
        || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)
        || !new AABB(table)
            .inflate(1.8, 1.5, 1.8)
            .contains(RitualSpace.toLocal(level, table, entity.position()))) return false;
    var sessions = SESSIONS.computeIfAbsent(level, l -> new LinkedHashMap<>());
    if (sessions.containsKey(table) || sessions.values().stream().anyMatch(s -> s.entity == entity))
      return false;
    var network = RitualNetwork.scan(level, table, false, true);
    var attempt = RitualAttempt.create(owner, entity.getItem(), network);
    var plan = attempt.successful();
    if (!plan.evaluation().ready() && attempt.failing().isEmpty()) return false;
    var session = new Session(owner, entity, attempt, network, table);
    // Claim before callbacks so a reentrant listener cannot capture this table or entity twice.
    sessions.put(table.immutable(), session);
    if (NeoForge.EVENT_BUS
        .post(new RitualEvent.Start(level, table, owner, session.target, plan.selected()))
        .isCanceled()) {
      sessions.remove(table);
      return false;
    }
    entity.getPersistentData().putBoolean("ritualsnotrolls_captured", true);
    entity
        .getPersistentData()
        .putBoolean("ritualsnotrolls_previous_no_gravity", entity.isNoGravity());
    entity.setNoGravity(true);
    entity.setDeltaMovement(Vec3.ZERO);
    entity.hasImpulse = true;
    entity.setPickUpDelay(10);
    sound(level, table, "capture");
    RitualEffects.emit(level, table, session.paths, session.animation, owner, 0);
    return true;
  }

  public static void restoreCapturedItem(ItemEntity entity) {
    KnowledgeTransfers.restore(entity);
    var data = entity.getPersistentData();
    if (!data.getBoolean("ritualsnotrolls_captured")) return;
    RitualConsumption.recover(entity);
    entity.setNoGravity(data.getBoolean("ritualsnotrolls_previous_no_gravity"));
    data.remove("ritualsnotrolls_captured");
    data.remove("ritualsnotrolls_previous_no_gravity");
    entity.setPickUpDelay(10);
    entity.hasImpulse = true;
  }

  public static void clear() {
    KnowledgeTransfers.clear();
    SESSIONS
        .values()
        .forEach(
            map ->
                map.values()
                    .forEach(
                        s -> {
                          s.consumption.rollback(s.level, s.entity);
                          restoreCapturedItem(s.entity);
                        }));
    SESSIONS.clear();
    DROPS.clear();
    TAILS.clear();
  }

  public static void unload(ServerLevel level) {
    KnowledgeTransfers.unload(level);
    var sessions = SESSIONS.remove(level);
    if (sessions != null) sessions.values().forEach(s -> abort(s, "Ritual interrupted"));
    DROPS.remove(level);
    TAILS.remove(level);
  }

  private static void detect(ServerLevel level) {
    var drops = DROPS.get(level);
    if (drops == null || level.getGameTime() % 5 != 0) return;
    for (var entity : new ArrayList<>(drops.values())) {
      if (!entity.isAlive()
          || entity.tickCount > Config.DROP_CAPTURE_TICKS.get()
          || (entity.getItem().getCount() != 1 && !KnowledgeTransfers.knowledge(entity.getItem()))
          || KnowledgeTransfers.active(entity)
          || !(entity.getOwner() instanceof ServerPlayer owner)
          || owner.isRemoved()) {
        drops.remove(entity.getUUID());
        continue;
      }
      if (SESSIONS.getOrDefault(level, Map.of()).values().stream()
          .anyMatch(s -> s.entity == entity)) {
        drops.remove(entity.getUUID());
        continue;
      }
      var tables = RitualSpace.nearbyTables(level, entity);
      for (var table : tables)
        if (tryStart(level, table, entity)) {
          drops.remove(entity.getUUID());
          break;
        }
    }
  }

  private static void tickTails(ServerLevel level) {
    var tails = TAILS.get(level);
    if (tails == null) return;
    tails.removeIf(
        tail -> {
          int age = (int) (level.getGameTime() - tail.started());
          if (age >= tail.animation().duration() + RitualEffects.tailDuration(tail.paths())
              || !RitualSpace.loaded(level, tail.table())
              || !Objects.equals(tail.frameId(), RitualSpace.frameId(level, tail.table())))
            return true;
          if (age % 2 == 0)
            RitualEffects.emitTail(level, tail.table(), tail.paths(), tail.animation(), age);
          return false;
        });
    if (tails.isEmpty()) TAILS.remove(level);
  }

  public static void tick(ServerLevel level) {
    KnowledgeTransfers.tick(level);
    tickTails(level);
    detect(level);
    var sessions = SESSIONS.get(level);
    if (sessions == null) return;
    // Event listeners may legitimately begin another ritual. Iterate a stable session snapshot.
    for (var entry : new ArrayList<>(sessions.entrySet())) {
      BlockPos pos = entry.getKey();
      Session s = entry.getValue();
      if (s.owner.isRemoved()
          || s.owner.serverLevel() != level
          || s.owner.position().distanceToSqr(RitualSpace.worldCenter(level, pos)) > 4096
          || !RitualSpace.loaded(level, pos)
          || !Objects.equals(s.frameId, RitualSpace.frameId(level, pos))
          || !level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)
          || s.revision != Definitions.SERVER.revision()) {
        abort(s, "Ritual interrupted; no resources spent");
        sessions.remove(pos, s);
        continue;
      }
      if (!s.entity.isAlive() || !ItemStack.matches(s.entity.getItem(), s.target)) {
        abort(s, "Target changed; ritual cancelled");
        sessions.remove(pos, s);
        continue;
      }
      int age = (int) (level.getGameTime() - s.started), duration = s.animation.duration();
      // Keep physics still on both sides; the vanilla item renderer supplies smooth bob/rotation.
      Vec3 target = RitualSpace.toWorld(level, pos, s.anchor);
      s.entity.setPos(age < 15 ? s.entity.position().lerp(target, .3) : target);
      if (age < 15 || s.frameId != null) s.entity.hasImpulse = true;
      s.entity.setDeltaMovement(Vec3.ZERO);
      s.entity.setPickUpDelay(10);
      if (age == 15) sound(level, pos, "knowledge");
      if (age == duration / 3) sound(level, pos, "material");
      if (age == duration / 2 && s.plan.experience().points() > 0) sound(level, pos, "experience");
      boolean interrupted = false;
      for (var offering :
          s.offeringTimes.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()) {
        if (age < offering.getValue() || s.consumption.count(offering.getKey()) > 0) continue;
        String failure = s.consumption.take(level, s.entity, s.plan, offering.getKey());
        if (!failure.isEmpty()) {
          abort(s, failure);
          sessions.remove(pos, s);
          interrupted = true;
          break;
        }
        sound(level, offering.getKey(), "consumption");
      }
      if (interrupted) continue;
      for (var stage : s.animation.stages())
        if (stage.failAt() >= 0 && age >= stage.failAt() && s.failedSounds.add(stage.enchantment()))
          sound(level, pos, "failure");
      if (age % 2 == 0) RitualEffects.emit(level, pos, s.paths, s.animation, s.owner, age);
      if (age >= duration) {
        String failure;
        if (s.plan.selected().isEmpty()) {
          s.consumption.rollback(level, s.entity);
          restoreCapturedItem(s.entity);
          s.entity.setPickUpDelay(20);
          s.entity.setDeltaMovement(0, .12, 0);
          failure = "";
        } else failure = commit(level, pos, s.owner, s.entity, s.target, s.plan, s.consumption);
        if (!failure.isEmpty()) abort(s, failure);
        else if (RitualEffects.tailDuration(s.paths) > 0) {
          TAILS
              .computeIfAbsent(level, l -> new ArrayList<>())
              .add(new Tail(pos.immutable(), s.frameId, s.paths, s.animation, s.started));
          if (age % 2 == 0) RitualEffects.emitTail(level, pos, s.paths, s.animation, age);
        }
        sessions.remove(pos, s);
      }
    }
  }

  public static String commit(
      ServerLevel level,
      BlockPos pos,
      ServerPlayer player,
      ItemEntity entity,
      ItemStack target,
      Map<ResourceLocation, Integer> selected,
      Set<ResourceLocation> consumed,
      int xp) {
    var plan =
        RitualMath.planFor(player, target, selected, RitualNetwork.scan(level, pos, false, true));
    if (xp != plan.experienceCatalysts() || !consumed.equals(plan.consumed()))
      return "Catalysts changed";
    return commit(level, pos, player, entity, target, plan);
  }

  public static String commit(
      ServerLevel level,
      BlockPos pos,
      ServerPlayer player,
      ItemEntity entity,
      ItemStack target,
      RitualMath.Plan plan) {
    return commit(level, pos, player, entity, target, plan, new RitualConsumption());
  }

  private static String commit(
      ServerLevel level,
      BlockPos pos,
      ServerPlayer player,
      ItemEntity entity,
      ItemStack target,
      RitualMath.Plan plan,
      RitualConsumption reserved) {
    if (!entity.isAlive() || !ItemStack.matches(entity.getItem(), target)) return "Target changed";
    if (!level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) return "Table removed";
    var network = reserved.overlay(RitualNetwork.scan(level, pos, false, true));
    if (RitualMath.experiencePoints(player) < plan.experience().points())
      return "Reserved experience is no longer available";
    var checked =
        RitualMath.planFor(player, target, plan.selected(), network, plan.experience().points());
    if (!checked.evaluation().ready()) return checked.evaluation().reason();
    if (!checked.withdrawals().equals(plan.withdrawals())
        || !checked.users().equals(plan.users())
        || !checked.evaluation().lines().equals(plan.evaluation().lines())
        || !chainIdentity(checked).equals(chainIdentity(plan)))
      return "Ritual resources or routes changed";
    for (var expected : plan.evaluation().used()) {
      var current =
          checked.evaluation().used().stream()
              .filter(p -> p.pos().equals(expected.pos()))
              .findFirst()
              .orElse(null);
      if (current == null
          || !ItemStack.matches(expected.stack(), current.stack())
          || expected.catalyst() != current.catalyst()
          || expected.subtraction() != current.subtraction())
        return "A contributing pedestal changed";
    }
    int xp = plan.experienceCatalysts();
    var selected = plan.selected();
    if (xp != checked.experienceCatalysts()) return "Experience catalysts changed";
    int revision = Definitions.SERVER.revision();
    ItemStack result = RitualMath.apply(player, target, selected);
    List<Transfers.Receipt> receipts = new ArrayList<>();
    for (var pedestal : checked.evaluation().used()) {
      var remainingStack = pedestal.stack().copy();
      remainingStack.shrink(reserved.count(pedestal.pos()));
      if (!ItemStack.matches(pedestal.handler().getStackInSlot(0), remainingStack)) {
        Transfers.rollback(level, receipts);
        return "A pedestal changed";
      }
      int count =
          checked.withdrawals().getOrDefault(pedestal.pos(), 0) - reserved.count(pedestal.pos());
      if (count <= 0) continue;
      ItemStack probe = pedestal.handler().extractItem(0, count, true);
      if (probe.getCount() != count
          || !ItemStack.isSameItemSameComponents(probe, pedestal.stack())) {
        Transfers.rollback(level, receipts);
        return "A material cannot be extracted";
      }
      ItemStack removed = pedestal.handler().extractItem(0, count, false);
      if (!removed.isEmpty())
        receipts.add(new Transfers.Receipt(pedestal.handler(), 0, pedestal.pos(), removed));
      if (removed.getCount() != count
          || !ItemStack.isSameItemSameComponents(removed, pedestal.stack())) {
        Transfers.rollback(level, receipts);
        return "A material changed during extraction";
      }
    }
    var after = RitualNetwork.scan(level, pos, false, true);
    boolean valid =
        entity.isAlive()
            && ItemStack.matches(entity.getItem(), target)
            && level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)
            && revision == Definitions.SERVER.revision()
            && RitualMath.experiencePoints(player) >= plan.experience().points()
            && new HashSet<>(after.sources()).equals(new HashSet<>(network.sources()));
    for (var before : network.pedestals()) {
      if (!checked.users().getOrDefault(before.pos(), Set.of()).isEmpty()
          || before.stack().is(RitualsNotRolls.XP_CATALYST)) {
        var current =
            after.pedestals().stream()
                .filter(p -> p.pos().equals(before.pos()))
                .findFirst()
                .orElse(null);
        var expected = before.stack().copy();
        expected.shrink(checked.withdrawals().getOrDefault(before.pos(), 0));
        valid &=
            current != null
                && current.catalyst() == before.catalyst()
                && current.subtraction() == before.subtraction()
                && ItemStack.matches(current.stack(), expected);
      }
    }
    valid &= RitualMath.catalysts(after).experience() == xp;
    if (!valid) {
      Transfers.rollback(level, receipts);
      return "Ritual resources changed during extraction";
    }
    // There are no asynchronous operations or mod callbacks between XP debit and target
    // replacement.
    if (plan.experience().points() > 0) player.giveExperiencePoints(-plan.experience().points());
    reserved.finish(entity);
    entity.setItem(result);
    restoreCapturedItem(entity);
    entity.setPickUpDelay(20);
    entity.setDeltaMovement(0, .18, 0);
    if (!receipts.isEmpty()) {
      for (var receipt : receipts) {
        sound(level, receipt.pos(), "consumption");
        RitualConsumption.breakParticles(level, receipt.pos(), receipt.stack());
      }
    }
    var registry =
        player
            .registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
    var before = RitualMath.enchantments(target);
    if (selected.entrySet().stream()
        .anyMatch(
            e -> e.getValue() > before.getLevel(registry.getHolder(e.getKey()).orElseThrow())))
      sound(level, pos, "complete");
    if (selected.entrySet().stream()
        .anyMatch(
            e -> e.getValue() < before.getLevel(registry.getHolder(e.getKey()).orElseThrow())))
      sound(level, pos, "disenchant");
    RitualEffects.complete(
        level,
        pos,
        RitualSpace.toLocal(level, pos, entity.position()).add(0, .2, 0),
        target,
        selected,
        plan.experience().points() > 0 ? xp : 0);
    var first = selected.keySet().stream().findFirst().map(Definitions.SERVER::get).orElse(null);
    if (first != null && first.sound().isPresent())
      BuiltInRegistries.SOUND_EVENT
          .getOptional(first.sound().get())
          .ifPresent(sound -> RitualSpace.sound(level, pos, sound, SoundSource.BLOCKS, 1, 1));
    NeoForge.EVENT_BUS.post(new RitualEvent.Complete(level, pos, player, result, selected));
    return "";
  }

  private static List<String> chainIdentity(RitualMath.Plan plan) {
    return plan.chains().stream()
        .map(
            c ->
                c.enchantment()
                    + "@"
                    + c.source()
                    + ":"
                    + c.outward().stream().map(p -> p.pos().toString()).toList()
                    + ":"
                    + c.returning().stream().map(p -> p.pos().toString()).toList()
                    + ":"
                    + c.chained())
        .toList();
  }

  private static void abort(Session s, String reason) {
    s.consumption.rollback(s.level, s.entity);
    if (s.entity.isAlive()) {
      restoreCapturedItem(s.entity);
      s.entity.setPickUpDelay(10);
      s.entity.setDeltaMovement(0, .12, 0);
    }
    if (!s.owner.isRemoved()) s.owner.displayClientMessage(Component.literal(reason), true);
  }

  private static void sound(ServerLevel level, BlockPos pos, String name) {
    RitualSpace.sound(level, pos, RitualsNotRolls.SOUNDS.get(name).get(), SoundSource.BLOCKS, 1, 1);
  }
}
