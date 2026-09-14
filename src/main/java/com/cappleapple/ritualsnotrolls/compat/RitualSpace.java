package com.cappleapple.ritualsnotrolls.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Block addresses stay in their plot; entity positions and packet origins stay in the world. */
public final class RitualSpace {
  private RitualSpace() {}

  public static UUID frameId(Level level, BlockPos anchor) {
    var subLevel = SableCompanion.INSTANCE.getContaining(level, anchor);
    return subLevel == null ? null : subLevel.getUniqueId();
  }

  public static boolean isMoving(Level level, BlockPos anchor) {
    return SableCompanion.INSTANCE.getContaining(level, anchor) != null;
  }

  public static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
    return Objects.equals(frameId(level, a), frameId(level, b));
  }

  public static boolean loaded(Level level, BlockPos pos) {
    if (SableCompanion.INSTANCE.isInPlotGrid(level, pos) && !isMoving(level, pos)) return false;
    return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
  }

  public static Vec3 toWorld(Level level, BlockPos anchor, Vec3 local) {
    var subLevel = SableCompanion.INSTANCE.getContaining(level, anchor);
    return subLevel == null ? local : subLevel.logicalPose().transformPosition(local);
  }

  public static Vec3 toLocal(Level level, BlockPos anchor, Vec3 world) {
    var subLevel = SableCompanion.INSTANCE.getContaining(level, anchor);
    return subLevel == null ? world : subLevel.logicalPose().transformPositionInverse(world);
  }

  public static Vec3 toRenderWorld(Level level, BlockPos anchor, Vec3 local) {
    var subLevel = SableCompanion.INSTANCE.getContaining(level, anchor);
    return subLevel instanceof ClientSubLevelAccess client
        ? client.renderPose().transformPosition(local)
        : toWorld(level, anchor, local);
  }

  public static Vec3 toRenderLocal(Level level, BlockPos anchor, Vec3 world) {
    var subLevel = SableCompanion.INSTANCE.getContaining(level, anchor);
    return subLevel instanceof ClientSubLevelAccess client
        ? client.renderPose().transformPositionInverse(world)
        : toLocal(level, anchor, world);
  }

  public static BlockPos trackedPosition(Entity entity) {
    var subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
    return subLevel == null
        ? entity.blockPosition()
        : BlockPos.containing(subLevel.logicalPose().transformPositionInverse(entity.position()));
  }

  public static Vec3 worldCenter(Level level, BlockPos pos) {
    return toWorld(level, pos, Vec3.atCenterOf(pos));
  }

  public static void sound(
      ServerLevel level,
      BlockPos pos,
      SoundEvent sound,
      SoundSource source,
      float volume,
      float pitch) {
    var at = worldCenter(level, pos);
    level.playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
  }

  /** Nearby loaded indexes only, including tracked vessels whose broadphase bounds may lag. */
  public static List<BlockPos> nearbyTables(ServerLevel level, Entity entity) {
    Set<BlockPos> tables = new HashSet<>();
    collectTables(level, entity.position(), null, tables);
    Map<UUID, SubLevelAccess> subLevels = new LinkedHashMap<>();
    add(subLevels, SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity));
    if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
        && item.getOwner() != null)
      add(subLevels, SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(item.getOwner()));
    for (var subLevel :
        SableCompanion.INSTANCE.getAllIntersecting(
            level, new BoundingBox3d(new AABB(entity.position(), entity.position()).inflate(4))))
      add(subLevels, subLevel);
    for (var subLevel : subLevels.values())
      collectTables(
          level,
          subLevel.logicalPose().transformPositionInverse(entity.position()),
          subLevel.getUniqueId(),
          tables);
    return tables.stream()
        .sorted(
            Comparator.<BlockPos>comparingDouble(
                    p -> entity.position().distanceToSqr(worldCenter(level, p)))
                .thenComparingLong(BlockPos::asLong))
        .toList();
  }

  private static void add(Map<UUID, SubLevelAccess> subLevels, SubLevelAccess subLevel) {
    if (subLevel != null) subLevels.putIfAbsent(subLevel.getUniqueId(), subLevel);
  }

  private static void collectTables(
      ServerLevel level, Vec3 local, UUID frame, Set<BlockPos> tables) {
    var center = BlockPos.containing(local);
    // The capture box extends 1.8 blocks beyond each face of the table block.
    for (int cx = (center.getX() - 3) >> 4; cx <= (center.getX() + 3) >> 4; cx++)
      for (int cz = (center.getZ() - 3) >> 4; cz <= (center.getZ() + 3) >> 4; cz++) {
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) continue;
        for (var pos : chunk.getBlockEntitiesPos())
          if (Objects.equals(frameId(level, pos), frame)
              && new AABB(pos).inflate(1.8, 1.5, 1.8).contains(local)
              && chunk.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) tables.add(pos.immutable());
      }
  }
}
