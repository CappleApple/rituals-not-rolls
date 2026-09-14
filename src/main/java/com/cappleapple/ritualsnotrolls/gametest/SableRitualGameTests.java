package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.knowledge.KnowledgeTransfers;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import com.cappleapple.ritualsnotrolls.ritual.*;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Optional integration tests against real Sable plots, assembly, and physics handles. */
@GameTestHolder("ritualsnotrolls_sable")
public final class SableRitualGameTests {
  @GameTestGenerator
  public static Collection<TestFunction> tests() {
    if (!ModList.get().isLoaded("sable")) return List.of();
    return List.of(
        test("assembled_network_and_capture", SableRitualGameTests::networkAndCapture),
        test("moving_ritual_completes", SableRitualGameTests::movingRitualCompletes),
        test("moving_library_transfers", SableRitualGameTests::movingLibraryTransfers),
        test("removed_plot_refunds_offerings", SableRitualGameTests::removedPlotRefunds));
  }

  private static TestFunction test(String name, Consumer<GameTestHelper> test) {
    return new TestFunction(
        "sable_rituals",
        "sableritualgametests." + name,
        "ritualsnotrolls_sable:network",
        Rotation.NONE,
        600,
        0,
        true,
        test);
  }

  private static void networkAndCapture(GameTestHelper h) {
    var r = new Vessel(h);
    r.after(
        5,
        () -> {
          var network = r.network();
          h.assertTrue(
              network.knowledge().get(RitualGameTests.SHARP).contains("diamond"),
              "Assembled shelf retains its knowledge component");
          h.assertTrue(
              network.pedestals().stream().anyMatch(p -> p.stack().is(Items.DIAMOND)),
              "Assembled pedestal retains its material inventory");
          h.assertTrue(
              network.shelves().contains(r.table.offset(2, 0, 0)),
              "Network uses the actual plot block addresses");
          h.assertTrue(RitualSpace.loaded(h.getLevel(), r.table), "Plot chunk is loaded");
          var menu = new RitualMenu(71, r.player.getInventory(), r.table);
          h.assertTrue(menu.stillValid(r.player), "Nearby world-space player can use moving table");
          r.player.setPos(r.world(Vec3.atCenterOf(r.table)).add(0, 10, 0));
          h.assertTrue(
              !menu.stillValid(r.player), "Vertical distance still closes moving table menu");
          r.followPlayer();
          var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
          h.assertTrue(
              RitualSpace.nearbyTables(h.getLevel(), target).contains(r.table),
              "World-space drop discovers the rotated moving table");
          h.assertTrue(
              RitualEngine.tryStart(h.getLevel(), r.table, target),
              "World-space dropped item starts a plot-space ritual");
          h.assertTrue(target.isNoGravity(), "Capture owns item gravity");
          h.getLevel().setBlock(r.table, Blocks.AIR.defaultBlockState(), 3);
          RitualEngine.tick(h.getLevel());
          h.assertTrue(
              !target.isNoGravity() && RitualEngine.state(h.getLevel(), r.table).equals("idle"),
              "Removing moving table cancels and releases target");
          h.assertTrue(!menu.stillValid(r.player), "Removed table closes its menu");
          r.succeed();
        });
  }

  private static void movingRitualCompletes(GameTestHelper h) {
    var r = new Vessel(h);
    r.after(
        5,
        () -> {
          r.pedestal()
              .items
              .setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
          var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
          var attempt = RitualAttempt.create(r.player, target.getItem(), r.network());
          var localAnchor = RitualEffects.targetCenter(r.table, attempt.visual()).add(0, -.2, 0);
          h.assertTrue(
              RitualEngine.tryStart(h.getLevel(), r.table, target), "Moving ritual starts");
          r.after(
              20,
              () -> {
                r.shift.add(7, 2, -5);
                r.updatePose();
                RitualEngine.tick(h.getLevel());
                h.assertTrue(
                    target.position().distanceToSqr(r.world(localAnchor)) < 1e-8,
                    "Captured item follows a translated and rotated table in world space");
                h.assertTrue(
                    RitualMath.enchantments(target.getItem()).isEmpty(),
                    "Movement does not prematurely commit the ritual");
              });
          r.after(
              480,
              () -> {
                h.assertTrue(
                    !RitualMath.enchantments(target.getItem()).isEmpty(),
                    "Moving ritual completes and enchants its target");
                h.assertTrue(
                    target.isAlive() && !target.isNoGravity(),
                    "Completion releases the intact enchanted target");
                h.assertTrue(
                    RitualEngine.state(h.getLevel(), r.table).equals("idle"),
                    "Completed moving table releases its claim");
                h.assertTrue(
                    r.pedestal().items.getStackInSlot(0).isEmpty()
                        && !target.getPersistentData().contains(RitualConsumption.KEY),
                    "Successful moving ritual consumes its offering and clears escrow");
                r.succeed();
              });
        });
  }

  private static void movingLibraryTransfers(GameTestHelper h) {
    var r = new Vessel(h);
    r.after(
        5,
        () -> {
          h.assertTrue(
              KnowledgeTransfers.retrieve(r.player, r.table, RitualGameTests.SHARP),
              "Book retrieval starts from a rotated plot shelf");
          var retrieved =
              r.items().stream().filter(KnowledgeTransfers::active).findFirst().orElseThrow();
          h.assertTrue(r.shelf().getItem(0).isEmpty(), "Retrieval extracts exactly one real book");
          r.after(
              8,
              () -> {
                r.shift.add(5, 1, 0);
                r.updatePose();
                KnowledgeTransfers.tick(h.getLevel());
                var local = RitualSpace.toLocal(h.getLevel(), r.table, retrieved.position());
                h.assertTrue(
                    local.distanceToSqr(Vec3.atCenterOf(r.table)) < 30,
                    "Retrieval flight stays near its moving local shelf and table");
              });
          r.after(
              55,
              () -> {
                h.assertTrue(
                    retrieved.isAlive()
                        && !retrieved.isNoGravity()
                        && retrieved.getItem().is(RitualsNotRolls.BOOK),
                    "Retrieved book is released intact");
                retrieved.discard();
                r.shelf().setItem(0, RitualGameTests.book("diamond"));
                var page = r.drop(RitualGameTests.page("netherite_scrap"));
                h.assertTrue(
                    KnowledgeTransfers.tryStart(h.getLevel(), r.table, page),
                    "World-space page starts filing into moving shelf");
                r.after(
                    55,
                    () -> {
                      h.assertTrue(
                          !page.isAlive()
                              && Knowledge.data(r.shelf().getItem(0))
                                  .entries()
                                  .contains("netherite_scrap"),
                          "Moving shelf receives the page exactly once on arrival");
                      var book = r.drop(RitualGameTests.book("iron_ingot"));
                      h.assertTrue(
                          KnowledgeTransfers.tryStart(h.getLevel(), r.table, book),
                          "Whole book reserves an empty moving shelf slot");
                      r.after(
                          55,
                          () -> {
                            h.assertTrue(
                                !book.isAlive(), "Whole book is filed on the moving shelf");
                            long books =
                                java.util.stream.IntStream.range(0, r.shelf().getContainerSize())
                                    .filter(
                                        slot -> r.shelf().getItem(slot).is(RitualsNotRolls.BOOK))
                                    .count();
                            h.assertTrue(books == 2, "Shelf contains exactly the two filed books");
                            r.succeed();
                          });
                    });
              });
        });
  }

  private static void removedPlotRefunds(GameTestHelper h) {
    var r = new Vessel(h);
    r.after(
        5,
        () -> {
          r.pedestal()
              .items
              .setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
          var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
          h.assertTrue(
              RitualEngine.tryStart(h.getLevel(), r.table, target), "Sacrifice ritual starts");
          awaitOffering(r, target);
        });
  }

  private static void awaitOffering(Vessel r, ItemEntity target) {
    var h = r.h;
    r.after(
        1,
        () -> {
          if (!r.pedestal().items.getStackInSlot(0).isEmpty()) {
            h.assertTrue(
                RitualEngine.state(h.getLevel(), r.table).equals("channeling"),
                "Sacrifice remains active until its offering arrives");
            awaitOffering(r, target);
            return;
          }
          h.assertTrue(
              target.getPersistentData().contains(RitualConsumption.KEY),
              "Consumed plot offering is persisted in the active session escrow");
          var location = target.position();
          r.remove();
          h.assertTrue(
              !RitualSpace.loaded(h.getLevel(), r.table), "Removed plot cannot resolve a chunk");
          RitualEngine.tick(h.getLevel());
          RitualEngine.restoreCapturedItem(target);
          h.assertTrue(
              target.isAlive()
                  && !target.isNoGravity()
                  && RitualMath.enchantments(target.getItem()).isEmpty(),
              "Removed plot releases its unchanged target");
          h.assertTrue(
              !target.getPersistentData().contains(RitualConsumption.KEY),
              "Recovery clears persisted escrow before another load");
          int refunded =
              h
                  .getLevel()
                  .getEntitiesOfClass(ItemEntity.class, new AABB(location, location).inflate(3))
                  .stream()
                  .filter(e -> e.getItem().is(Items.DIAMOND))
                  .mapToInt(e -> e.getItem().getCount())
                  .sum();
          h.assertTrue(refunded == 1, "Offering returns exactly once at the world-space target");
          target.discard();
          h.succeed();
        });
  }

  private static final class Vessel {
    final GameTestHelper h;
    final SubLevelAccess vessel;
    final BlockPos table;
    final ServerPlayer player;
    final Vector3d origin;
    final Vector3d shift = new Vector3d(6, 3, 2);
    final Object handle;
    final List<ItemEntity> ownedItems = new ArrayList<>();
    boolean removed;

    Vessel(GameTestHelper h) {
      this.h = h;
      var source = h.absolutePos(new BlockPos(20, 5, 20));
      var blocks = new ArrayList<BlockPos>();
      for (int x = -1; x <= 3; x++)
        for (int z = -1; z <= 3; z++) {
          var pos = source.offset(x, -1, z);
          h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
          blocks.add(pos);
        }
      h.getLevel().setBlock(source, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
      h.getLevel()
          .setBlock(source.offset(2, 0, 0), Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
      h.getLevel()
          .setBlock(source.offset(0, 0, 2), RitualsNotRolls.PEDESTAL.get().defaultBlockState(), 3);
      blocks.addAll(List.of(source, source.offset(2, 0, 0), source.offset(0, 0, 2)));
      ((ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(source.offset(2, 0, 0)))
          .setItem(0, RitualGameTests.book("diamond", "netherite_scrap"));
      ((PedestalEntity) h.getLevel().getBlockEntity(source.offset(0, 0, 2)))
          .items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
      vessel =
          (SubLevelAccess)
              callStatic(
                  "dev.ryanhcode.sable.api.SubLevelAssemblyHelper",
                  "assembleBlocks",
                  new Class<?>[] {
                    ServerLevel.class, BlockPos.class, Iterable.class, BoundingBox3ic.class
                  },
                  h.getLevel(),
                  source,
                  blocks,
                  BoundingBox3i.from(blocks));
      Object plot = call(vessel, "getPlot");
      table = ((BlockPos) call(plot, "getCenterBlock")).immutable();
      origin = new Vector3d(vessel.logicalPose().position());
      handle =
          callStatic(
              "dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle",
              "of",
              new Class<?>[] {type("dev.ryanhcode.sable.sublevel.ServerSubLevel")},
              vessel);
      player = RitualGameTests.player(h);
      updatePose();
      RitualNetwork.invalidate(h.getLevel());
      h.onEachTick(
          () -> {
            if (!removed) updatePose();
          });
    }

    void updatePose() {
      var pose = (Pose3d) vessel.logicalPose();
      pose.position().set(origin).add(shift);
      pose.orientation().rotationYXZ(.65 + h.getTick() * .003, .12, .08);
      call(
          handle,
          "teleport",
          new Class<?>[] {Vector3dc.class, Quaterniondc.class},
          pose.position(),
          pose.orientation());
      call(vessel, "forceUpdateGlobalBounds");
      followPlayer();
    }

    void followPlayer() {
      player.setPos(world(Vec3.atCenterOf(table)).add(0, 2, 0));
    }

    Vec3 world(Vec3 point) {
      return vessel.logicalPose().transformPosition(point);
    }

    RitualNetwork.Snapshot network() {
      return RitualNetwork.scan(h.getLevel(), table, false, true);
    }

    ChiseledBookShelfBlockEntity shelf() {
      return (ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(table.offset(2, 0, 0));
    }

    PedestalEntity pedestal() {
      return (PedestalEntity) h.getLevel().getBlockEntity(table.offset(0, 0, 2));
    }

    ItemEntity drop(ItemStack stack) {
      var point = world(Vec3.atBottomCenterOf(table).add(0, 1.1, 0));
      var entity = new ItemEntity(h.getLevel(), point.x, point.y, point.z, stack);
      entity.setThrower(player);
      entity.setPickUpDelay(100);
      h.getLevel().addFreshEntity(entity);
      ownedItems.add(entity);
      return entity;
    }

    List<ItemEntity> items() {
      var point = world(Vec3.atCenterOf(table));
      return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(point, point).inflate(10));
    }

    void after(long ticks, Runnable action) {
      h.runAfterDelay(
          ticks,
          () -> {
            try {
              action.run();
            } catch (RuntimeException | Error failure) {
              try {
                remove();
              } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
              }
              failure.printStackTrace();
              throw failure;
            }
          });
    }

    void remove() {
      if (removed) return;
      removed = true;
      var containerType = type("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
      Object container =
          exact(
              containerType,
              null,
              "getContainer",
              type("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer"),
              new Class<?>[] {ServerLevel.class},
              h.getLevel());
      var reasonType = type("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
      Object reason =
          Arrays.stream(reasonType.getEnumConstants())
              .filter(value -> ((Enum<?>) value).name().equals("REMOVED"))
              .findFirst()
              .orElseThrow();
      exact(
          containerType,
          container,
          "removeSubLevel",
          void.class,
          new Class<?>[] {type("dev.ryanhcode.sable.sublevel.SubLevel"), reasonType},
          vessel,
          reason);
      exact(containerType, container, "processSubLevelRemovals", void.class, new Class<?>[0]);
      RitualNetwork.invalidate(h.getLevel());
    }

    void succeed() {
      items().forEach(ItemEntity::discard);
      ownedItems.forEach(ItemEntity::discard);
      remove();
      RitualEngine.tick(h.getLevel());
      h.succeed();
    }
  }

  // Exact method handles avoid reflection resolving SubLevelContainer's ClientLevel overload.
  private static Object exact(
      Class<?> owner,
      Object target,
      String name,
      Class<?> result,
      Class<?>[] parameters,
      Object... arguments) {
    try {
      var signature = MethodType.methodType(result, parameters);
      var lookup = MethodHandles.publicLookup();
      var method =
          target == null
              ? lookup.findStatic(owner, name, signature)
              : lookup.findVirtual(owner, name, signature);
      var values = new ArrayList<Object>(Arrays.asList(arguments));
      if (target != null) values.addFirst(target);
      return method.invokeWithArguments(values);
    } catch (Throwable failure) {
      if (failure instanceof RuntimeException cause) throw cause;
      if (failure instanceof Error cause) throw cause;
      throw new IllegalStateException("Sable fixture failed: " + name, failure);
    }
  }

  private static Class<?> type(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Missing Sable test API " + name, e);
    }
  }

  private static Object call(Object target, String name) {
    return call(target, name, new Class<?>[0]);
  }

  private static Object call(
      Object target, String name, Class<?>[] parameters, Object... arguments) {
    return invoke(target.getClass(), target, name, parameters, arguments);
  }

  private static Object callStatic(
      String owner, String name, Class<?>[] parameters, Object... arguments) {
    return invoke(type(owner), null, name, parameters, arguments);
  }

  private static Object invoke(
      Class<?> owner, Object target, String name, Class<?>[] parameters, Object... arguments) {
    try {
      return owner.getMethod(name, parameters).invoke(target, arguments);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException cause) throw cause;
      if (e.getCause() instanceof Error cause) throw cause;
      throw new IllegalStateException("Sable fixture failed: " + name, e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Sable 2.0.5 fixture API unavailable: " + name, e);
    }
  }
}
