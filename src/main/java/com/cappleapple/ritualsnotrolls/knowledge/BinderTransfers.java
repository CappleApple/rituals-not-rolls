package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A dropped binder meters real page submissions while the ordinary library flights run together.
 */
public final class BinderTransfers {
  private static final Map<ServerLevel, Map<UUID, Job>> JOBS = new WeakHashMap<>();

  private static final class Job {
    final ItemEntity entity;
    final BlockPos table;
    final UUID frame;
    ItemStack expected;
    long nextBatch;
    int cursor, misses;

    Job(ServerLevel level, BlockPos table, ItemEntity entity) {
      this.entity = entity;
      this.table = table.immutable();
      this.frame = RitualSpace.frameId(level, table);
      expected = entity.getItem().copy();
      nextBatch = level.getGameTime();
    }
  }

  private BinderTransfers() {}

  static boolean start(ServerLevel level, BlockPos table, ItemEntity entity) {
    if (entity.getItem().getCount() != 1 || BinderStorage.data(entity.getItem()).total() == 0)
      return false;
    KnowledgeTransfers.capture(entity);
    JOBS.computeIfAbsent(level, l -> new LinkedHashMap<>())
        .put(entity.getUUID(), new Job(level, table, entity));
    return true;
  }

  static void tick(ServerLevel level) {
    var jobs = JOBS.get(level);
    if (jobs == null) return;
    for (var job : new ArrayList<>(jobs.values())) {
      var entity = job.entity;
      if (!entity.isAlive()
          || !ItemStack.matches(entity.getItem(), job.expected)
          || !(entity.getOwner() instanceof ServerPlayer owner)
          || owner.isRemoved()
          || owner.serverLevel() != level
          || !RitualSpace.loaded(level, job.table)
          || !Objects.equals(job.frame, RitualSpace.frameId(level, job.table))
          || !level.getBlockState(job.table).is(Blocks.ENCHANTING_TABLE)
          || owner.distanceToSqr(RitualSpace.worldCenter(level, job.table)) > 4096) {
        finish(job, jobs);
        continue;
      }
      var position =
          RitualSpace.toWorld(
              level,
              job.table,
              Vec3.atBottomCenterOf(job.table)
                  .add(.7, 1.8 + Math.sin(level.getGameTime() * .1) * .06, 0));
      entity.setPos(position);
      entity.setDeltaMovement(Vec3.ZERO);
      entity.setNeverPickUp();
      entity.hasImpulse = true;
      if (level.getGameTime() % 10 == 0)
        level.sendParticles(
            ParticleTypes.END_ROD, position.x, position.y, position.z, 1, .06, .06, .06, .005);
      if (level.getGameTime() < job.nextBatch) continue;
      job.nextBatch = level.getGameTime() + 20;
      int attempts =
          Math.min(
              BinderStorage.pagesPerSecond(),
              BinderStorage.data(entity.getItem()).entries().size());
      for (int i = 0; i < attempts; i++) {
        var contents = BinderStorage.data(entity.getItem());
        if (contents.entries().isEmpty()) break;
        job.cursor = Math.floorMod(job.cursor, contents.entries().size());
        int index = job.cursor;
        var entry = contents.entries().get(index);
        if (submit(level, job, owner, index, entry.page())) {
          job.misses = 0;
          if (entry.count() > 1) job.cursor++;
        } else {
          job.misses++;
          job.cursor++;
        }
      }
      int entries = BinderStorage.data(entity.getItem()).entries().size();
      if ((entries == 0 || job.misses >= entries) && !KnowledgeTransfers.pending(level, job.table))
        finish(job, jobs);
    }
    if (jobs.isEmpty()) JOBS.remove(level);
  }

  private static boolean submit(
      ServerLevel level, Job job, ServerPlayer owner, int index, ItemStack page) {
    var at = RitualSpace.toWorld(level, job.table, Vec3.atBottomCenterOf(job.table).add(0, 1.4, 0));
    var entity = new ItemEntity(level, at.x, at.y, at.z, page.copyWithCount(1));
    entity.setThrower(owner);
    entity.setNeverPickUp();
    if (!level.addFreshEntity(entity)) return false;
    if (!job.entity.isAlive()
        || !ItemStack.matches(job.entity.getItem(), job.expected)
        || !entity.isAlive()
        || !ItemStack.matches(entity.getItem(), page.copyWithCount(1))
        || !KnowledgeTransfers.tryStart(level, job.table, entity)) {
      entity.discard();
      return false;
    }
    // Entity-join callbacks from binding can change the source binder; never debit a stale view.
    if (!job.entity.isAlive()
        || !ItemStack.matches(job.entity.getItem(), job.expected)
        || !entity.isAlive()
        || !ItemStack.matches(entity.getItem(), page.copyWithCount(1))) {
      KnowledgeTransfers.cancel(level, entity);
      entity.discard();
      return false;
    }
    var updated = job.entity.getItem().copy();
    var taken = BinderStorage.extract(updated, index, 1);
    if (!ItemStack.matches(taken, page.copyWithCount(1))) {
      KnowledgeTransfers.cancel(level, entity);
      entity.discard();
      return false;
    }
    job.entity.setItem(updated);
    job.expected = updated.copy();
    return true;
  }

  private static void finish(Job job, Map<UUID, Job> jobs) {
    KnowledgeTransfers.release(job.entity);
    jobs.remove(job.entity.getUUID());
  }

  static void unload(ServerLevel level) {
    var jobs = JOBS.remove(level);
    if (jobs != null) jobs.values().forEach(j -> KnowledgeTransfers.release(j.entity));
  }

  static void clear() {
    new ArrayList<>(JOBS.keySet()).forEach(BinderTransfers::unload);
  }
}
