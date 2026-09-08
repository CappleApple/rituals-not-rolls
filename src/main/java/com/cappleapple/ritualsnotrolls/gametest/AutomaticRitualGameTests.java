package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.*;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class AutomaticRitualGameTests {
  private record Room(
      GameTestHelper h,
      ServerPlayer player,
      BlockPos table,
      PedestalEntity material,
      ChiseledBookShelfBlockEntity shelf) {
    RitualNetwork.Snapshot network() {
      return RitualNetwork.scan(h.getLevel(), table, false, true);
    }

    RitualMath.Plan plan(ItemStack target) {
      return RitualMath.automatic(player, target, network());
    }

    ItemEntity drop(ItemStack stack) {
      var entity =
          new ItemEntity(
              h.getLevel(), table.getX() + .5, table.getY() + 1, table.getZ() + .5, stack);
      entity.setThrower(player);
      entity.setPickUpDelay(20);
      h.getLevel().addFreshEntity(entity);
      return entity;
    }

    PedestalEntity pedestal(int dx, ItemStack item, ItemStack catalyst) {
      var pos = table.offset(dx, 0, 2);
      h.getLevel().setBlock(pos, RitualsNotRolls.PEDESTAL.get().defaultBlockState(), 3);
      var p = (PedestalEntity) h.getLevel().getBlockEntity(pos);
      p.items.setStackInSlot(0, item);
      p.items.setStackInSlot(1, catalyst);
      return p;
    }
  }

  private static Room room(GameTestHelper h) {
    h.setBlock(20, 1, 20, Blocks.ENCHANTING_TABLE);
    h.setBlock(22, 1, 20, Blocks.CHISELED_BOOKSHELF);
    h.setBlock(20, 1, 22, RitualsNotRolls.PEDESTAL.get());
    var table = h.absolutePos(new BlockPos(20, 1, 20));
    var player = RitualGameTests.player(h);
    player.setPos(RitualGameTests.Vec3At(table));
    var shelf = (ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(table.offset(2, 0, 0));
    shelf.setItem(0, RitualGameTests.book("diamond", "netherite_scrap"));
    var pedestal = (PedestalEntity) h.getLevel().getBlockEntity(table.offset(0, 0, 2));
    pedestal.items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
    return new Room(h, player, table, pedestal, shelf);
  }

  private static ItemStack xp() {
    return new ItemStack(RitualsNotRolls.XP_CATALYST.get());
  }

  private static void twoXp(Room room) {
    room.pedestal(1, xp(), ItemStack.EMPTY);
    room.pedestal(2, xp(), ItemStack.EMPTY);
  }

  private static String commit(Room room, ItemEntity target, RitualMath.Plan plan) {
    return RitualEngine.commit(
        room.h.getLevel(), room.table, room.player, target, target.getItem().copy(), plan);
  }

  @GameTest(template = "network")
  public static void highestAffordableLevelsAreAutomatic(GameTestHelper h) {
    var r = room(h);
    r.pedestal(1, new ItemStack(Items.NETHERITE_SCRAP), ItemStack.EMPTY);
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(
        plan.evaluation().ready() && plan.selected().get(RitualGameTests.SHARP) == 3,
        "85 power automatically chooses Sharpness III");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void incompatibleAndUnknownEnchantmentsAreSkipped(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(
        1,
        Knowledge.book(
            new KnowledgeData(
                ResourceLocation.parse("minecraft:protection"), List.of("diamond"), true)));
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(
        plan.selected().keySet().equals(Set.of(RitualGameTests.SHARP)),
        "Sword receives only supported known enchantments");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void referenceListsOnlyCurrentPhysicalKnowledge(GameTestHelper h) {
    var r = room(h);
    r.player
        .getInventory()
        .setItem(
            0,
            Knowledge.book(
                new KnowledgeData(RitualGameTests.UNBREAKING, List.of("ancient_debris"), true)));
    var menu = new RitualMenu(12, r.player.getInventory(), r.table);
    var state = menu.snapshot();
    h.assertTrue(menu.slots.isEmpty(), "Reference cannot hold or move a target");
    h.assertTrue(
        state.getList("knowledge", 10).size() == 1
            && state
                .getList("knowledge", 10)
                .getCompound(0)
                .getString("id")
                .equals("minecraft:sharpness"),
        "Only shelf knowledge is listed");
    r.shelf.setItem(0, ItemStack.EMPTY);
    h.assertTrue(
        menu.snapshot().getList("knowledge", 10).isEmpty(),
        "Removed book disappears from reference");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void staleAffinitiesAreNotListed(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.book("removed_affinity"));
    h.assertTrue(
        new RitualMenu(12, r.player.getInventory(), r.table)
            .snapshot()
            .getList("knowledge", 10)
            .isEmpty(),
        "No unresolved discovery becomes usable knowledge");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void sacrificeOnlyAffectsItsOwnContributingItem(GameTestHelper h) {
    var r = room(h);
    r.material.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var scrap = r.pedestal(1, new ItemStack(Items.NETHERITE_SCRAP), ItemStack.EMPTY);
    var duplicate = r.pedestal(2, new ItemStack(Items.DIAMOND), ItemStack.EMPTY);
    var unrelated = r.pedestal(3, new ItemStack(Items.DIRT), ItemStack.EMPTY);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    h.assertTrue(
        plan.selected().get(RitualGameTests.SHARP) == 4
            && plan.evaluation().lines().getFirst().power() == 145,
        "Only marked diamond doubles: 60 + 55 + 30 = 145");
    h.assertTrue(commit(r, target, plan).isEmpty(), "Automatic sacrifice commits");
    h.assertTrue(
        scrap.items.getStackInSlot(0).is(Items.NETHERITE_SCRAP),
        "Unmarked material remains intact");
    int diamonds =
        r.material.items.getStackInSlot(0).getCount()
            + duplicate.items.getStackInSlot(0).getCount();
    h.assertTrue(
        diamonds == 1 && unrelated.items.getStackInSlot(0).is(Items.DIRT),
        "Duplicate and unrelated materials remain");
    h.assertTrue(!r.material.items.getStackInSlot(1).isEmpty(), "Catalyst remains");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void twoXpCatalystsCost550AndGiveTwentyPercent(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(1395);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    h.assertTrue(
        plan.experienceCatalysts() == 2 && plan.selected().get(RitualGameTests.SHARP) == 2,
        "36 power reaches Sharpness II");
    h.assertTrue(
        Math.abs(plan.evaluation().lines().getFirst().power() - 36) < 1e-9,
        "20 percent additive power");
    h.assertTrue(commit(r, target, plan).isEmpty(), "XP ritual commits");
    h.assertTrue(
        RitualMath.experiencePoints(r.player) == 845,
        "Exactly 550 XP was debited, not 320 or 20 current levels");
    h.assertTrue(RitualMath.catalysts(r.network()).experience() == 2, "Both catalysts remain");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void carriedCatalystsHaveNoEffect(GameTestHelper h) {
    var r = room(h);
    r.player.getInventory().setItem(1, xp());
    r.player.getInventory().setItem(2, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(
        plan.experienceCatalysts() == 0
            && plan.consumed().isEmpty()
            && plan.evaluation().lines().getFirst().power() == 30,
        "Only placed catalysts participate");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void twoCatalystsUseFifteenAvailableLevelsForFifteenPercent(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(315);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    h.assertTrue(
        plan.evaluation().ready() && plan.experience().points() == 315,
        "Partial XP budget permits the ritual");
    h.assertTrue(
        Math.abs(plan.evaluation().lines().getFirst().power() - 34.5) < 1e-9,
        "Thirty base power receives fifteen percent, not twenty or raw-XP prorating");
    var menu = new RitualMenu(0, r.player.getInventory(), r.table).snapshot();
    h.assertTrue(
        menu.getLong("xp_cost") == 315
            && menu.getDouble("xp_levels") == 15
            && Math.abs(menu.getDouble("xp_multiplier") - 1.15) < 1e-9,
        "Reference preview uses the payable budget");
    h.assertTrue(
        commit(r, target, plan).isEmpty() && RitualMath.experiencePoints(r.player) == 0,
        "Exactly the available 315 XP is debited");
    h.assertTrue(RitualMath.catalysts(r.network()).experience() == 2, "Both catalysts remain");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void gainingXpDuringRitualDoesNotIncreaseCapturedCharge(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(315);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    r.player.giveExperiencePoints(500);
    h.assertTrue(
        commit(r, target, plan).isEmpty() && RitualMath.experiencePoints(r.player) == 500,
        "Newly gained XP stays with the player and cannot retroactively increase power");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void lostPartialBudgetCannotGrantItsOriginalBonus(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(315);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    r.player.giveExperiencePoints(-1);
    h.assertTrue(
        !commit(r, target, plan).isEmpty()
            && RitualMath.experiencePoints(r.player) == 314
            && RitualMath.enchantments(target.getItem()).isEmpty(),
        "Changed payment cancels without extra charges");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void emptyXpBudgetStillAllowsBasePowerAndHasNoXpTrail(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    h.assertTrue(
        plan.evaluation().ready()
            && plan.experience().points() == 0
            && plan.evaluation().lines().getFirst().power() == 30,
        "Zero XP gives exactly base power");
    h.assertTrue(
        RitualEffects.paths(h.getLevel(), r.network(), plan, r.table).stream()
            .noneMatch(p -> p.enchantment().equals(RitualsNotRolls.id("experience"))),
        "No unpaid XP animation");
    h.assertTrue(
        commit(r, target, plan).isEmpty() && RitualMath.experiencePoints(r.player) == 0,
        "Base-power enchant commits without an XP debit");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void catalystChangesBeforeCommitAreRejected(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(1000);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    r.pedestal(1, ItemStack.EMPTY, ItemStack.EMPTY);
    h.assertTrue(
        !commit(r, target, plan).isEmpty() && RitualMath.experiencePoints(r.player) == 1000,
        "Removed XP catalyst cancels without spending");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void sacrificeRemovalBeforeCommitIsRejected(GameTestHelper h) {
    var r = room(h);
    r.material.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    r.material.items.setStackInSlot(1, ItemStack.EMPTY);
    h.assertTrue(
        !commit(r, target, plan).isEmpty() && !r.material.items.getStackInSlot(0).isEmpty(),
        "Sacrifice configuration cannot change mid-ritual");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void bothParticleRoutesExistForRelevantKnowledge(GameTestHelper h) {
    var r = room(h);
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    var paths =
        RitualEffects.paths(
            h.getLevel(), r.network(), plan.evaluation().used(), plan.selected().keySet(), r.table);
    h.assertTrue(
        paths.size() == 2
            && paths.stream().anyMatch(RitualEffects.Path::knowledge)
            && paths.stream().anyMatch(p -> !p.knowledge()),
        "Book-to-material and material-to-table routes coexist");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void twoDropsCannotCaptureOneTable(GameTestHelper h) {
    var r = room(h);
    var first = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var second = r.drop(new ItemStack(Items.IRON_SWORD));
    second.setThrower(RitualGameTests.player(h));
    h.assertTrue(
        RitualEngine.tryStart(h.getLevel(), r.table, first), "First drop starts without any UI");
    h.assertTrue(
        !RitualEngine.tryStart(h.getLevel(), r.table, second),
        "Second player cannot steal the session");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void oneDropCannotBeCapturedByTwoTables(GameTestHelper h) {
    var r = room(h);
    var second = r.table.offset(1, 0, 0);
    h.getLevel().setBlock(second, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(
        RitualEngine.tryStart(h.getLevel(), r.table, target)
            && !RitualEngine.tryStart(h.getLevel(), second, target),
        "Entity capture is exclusive across tables");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void noUpgradeMeansNoXpCharge(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(1000);
    var item =
        RitualMath.apply(
            r.player, new ItemStack(Items.DIAMOND_SWORD), Map.of(RitualGameTests.SHARP, 2));
    var target = r.drop(item);
    h.assertTrue(
        RitualEngine.tryStart(h.getLevel(), r.table, target)
            && RitualMath.experiencePoints(r.player) == 1000,
        "Unaffordable next level previews failure without XP debit");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void startEventCanCancelAutomaticRitual(GameTestHelper h) {
    var r = room(h);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    java.util.function.Consumer<RitualEvent.Start> listener =
        e -> {
          if (e.table.equals(r.table)) e.setCanceled(true);
        };
    NeoForge.EVENT_BUS.addListener(listener);
    try {
      h.assertTrue(
          !RitualEngine.tryStart(h.getLevel(), r.table, target)
              && RitualEngine.state(h.getLevel(), r.table).equals("idle"),
          "Cancelled event releases table claim");
    } finally {
      NeoForge.EVENT_BUS.unregister(listener);
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void catalystInMaterialSlotIsCounted(GameTestHelper h) {
    var r = room(h);
    r.pedestal(1, xp(), ItemStack.EMPTY);
    r.pedestal(2, xp(), ItemStack.EMPTY);
    h.assertTrue(
        RitualMath.catalysts(r.network()).experience() == 2
            && RitualMath.unique(r.network(), Set.of()).size() == 1,
        "Catalyst items are counted individually but never become imbuement materials");
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 500)
  public static void removingXpDuringAnimationSpendsNothing(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(1000);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, target), "Automatic capture starts");
    int completion = schedule(r, target.getItem()).duration();
    h.runAfterDelay(25, () -> r.player.giveExperiencePoints(-500));
    h.runAfterDelay(
        completion + 5,
        () -> {
          h.assertTrue(
              RitualMath.enchantments(target.getItem()).isEmpty()
                  && RitualMath.experiencePoints(r.player) == 500
                  && !r.material.items.getStackInSlot(0).isEmpty(),
              "Final XP check preserves target and materials");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void automaticSelectionHasNoSmallEnchantmentLimit(GameTestHelper h) {
    var r = room(h);
    var old = Definitions.SERVER;
    Map<ResourceLocation, RitualDefinition> definitions = new TreeMap<>();
    var books = new ItemStackHandler(old.enchantments().size());
    int slot = 0;
    for (var id : old.enchantments().keySet()) {
      definitions.put(
          id,
          new RitualDefinition(
              id,
              List.of(
                  new Affinity(
                      "diamond",
                      Optional.of(ResourceLocation.parse("minecraft:diamond")),
                      Optional.empty(),
                      84,
                      1)),
              Map.of("1", 1.0),
              List.of(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty()));
      books.setStackInSlot(slot++, Knowledge.book(new KnowledgeData(id, List.of("diamond"), true)));
    }
    BlockPos external = r.table.offset(-2, 0, 0);
    RitualApi.registerCandidateProvider(
        (level, table, radius) ->
            level == h.getLevel() && table.equals(r.table) ? List.of(external) : List.of());
    RitualApi.registerBookshelf(
        (level, pos) -> level == h.getLevel() && pos.equals(external) ? books : null);
    try {
      Definitions.SERVER = new Definitions.Snapshot(definitions, old.rules(), old.revision() + 1);
      var plan = r.plan(new ItemStack(Items.BOOK));
      h.assertTrue(
          plan.evaluation().ready() && plan.selected().size() == 42,
          "All 42 compatible known enchantments can be applied together");
    } finally {
      Definitions.SERVER = old;
      RitualNetwork.invalidate(h.getLevel());
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void mixedEnchantmentsKeepTheirOwnParticleEffects(GameTestHelper h) {
    var r = room(h);
    r.pedestal(-1, new ItemStack(Items.ANCIENT_DEBRIS), ItemStack.EMPTY);
    r.shelf.setItem(
        1,
        Knowledge.book(
            new KnowledgeData(RitualGameTests.UNBREAKING, List.of("ancient_debris"), true)));
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    var paths =
        RitualEffects.paths(
            h.getLevel(), r.network(), plan.evaluation().used(), plan.selected().keySet(), r.table);
    h.assertTrue(paths.size() == 4, "Each enchantment has both routes for its own material");
    h.assertTrue(
        paths.stream()
            .filter(p -> p.enchantment().equals(RitualGameTests.SHARP))
            .allMatch(
                p ->
                    p.particle().effect().toString().equals("minecraft:enchant")
                        && p.particle().rgb() == 0xAFCFFF),
        "Sharpness retains its glyphs and tint");
    h.assertTrue(
        paths.stream()
            .filter(p -> p.enchantment().equals(RitualGameTests.UNBREAKING))
            .allMatch(
                p ->
                    p.particle().effect().toString().equals("minecraft:end_rod")
                        && p.particle().rgb() == 0x8FE4FF),
        "Unbreaking retains its sparks and tint");
    h.succeed();
  }

  @GameTest(template = "network", timeoutTicks = 500)
  public static void capturedItemFloatsThenRestoresGravity(GameTestHelper h) {
    var r = room(h);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, target), "Drop captured");
    h.assertTrue(target.isNoGravity(), "Gravity disabled at capture");
    int completion = schedule(r, target.getItem()).duration();
    h.runAfterDelay(
        completion - 1,
        () -> h.assertTrue(target.isNoGravity(), "Held through trail drain and pulse"));
    h.runAfterDelay(
        40,
        () ->
            h.assertTrue(
                target.isNoGravity() && Math.abs(target.getY() - r.table.getY() - 1.5) < .001,
                "Stable floating anchor"));
    h.runAfterDelay(
        90,
        () ->
            h.assertTrue(
                target.isNoGravity() && Math.abs(target.getY() - r.table.getY() - 1.5) < .001,
                "Anchor remains stable later in ritual"));
    h.runAfterDelay(
        completion + 5,
        () -> {
          h.assertTrue(
              !target.isNoGravity()
                  && !target.getPersistentData().getBoolean("ritualsnotrolls_captured"),
              "Completion restores physics and clears recovery marker");
          h.assertTrue(
              !RitualMath.enchantments(target.getItem()).isEmpty(),
              "Floating target completed successfully");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 80)
  public static void interruptionRestoresOriginalGravity(GameTestHelper h) {
    var r = room(h);
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, target), "Drop captured");
    h.getLevel().setBlock(r.table, Blocks.AIR.defaultBlockState(), 3);
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(!target.isNoGravity(), "Breaking the table releases gravity");
          h.assertTrue(
              RitualMath.enchantments(target.getItem()).isEmpty(), "Aborted target is unchanged");
          h.succeed();
        });
  }

  @GameTest(template = "network")
  public static void orphanedCaptureRestoresPhysicsOnLoad(GameTestHelper h) {
    var r = room(h);
    for (boolean original : List.of(false, true)) {
      var target =
          new ItemEntity(
              h.getLevel(),
              r.table.getX(),
              r.table.getY() + 1,
              r.table.getZ(),
              new ItemStack(Items.DIAMOND_SWORD));
      target.setNoGravity(true);
      target.getPersistentData().putBoolean("ritualsnotrolls_captured", true);
      target.getPersistentData().putBoolean("ritualsnotrolls_previous_no_gravity", original);
      h.getLevel().addFreshEntity(target);
      h.assertTrue(
          target.isNoGravity() == original
              && !target.getPersistentData().getBoolean("ritualsnotrolls_captured"),
          "World join restores the pre-ritual gravity state");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void markedDuplicateIsTheOnlyConsumedCopy(GameTestHelper h) {
    var r = room(h);
    var marked =
        r.pedestal(
            1,
            new ItemStack(Items.DIAMOND),
            new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    h.assertTrue(
        plan.evaluation().lines().getFirst().power() == 90,
        "Unmarked original adds 30 and marked duplicate adds 60");
    h.assertTrue(commit(r, target, plan).isEmpty(), "Duplicate selection commits");
    h.assertTrue(
        marked.items.getStackInSlot(0).isEmpty()
            && r.material.items.getStackInSlot(0).is(Items.DIAMOND),
        "Only the marked pedestal is consumed");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void unrelatedSacrificeDoesNotDoubleMaterials(GameTestHelper h) {
    var r = room(h);
    r.pedestal(
        1, new ItemStack(Items.DIRT), new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    h.assertTrue(
        plan.consumed().isEmpty() && plan.evaluation().lines().getFirst().power() == 30,
        "A catalyst on dirt does not double or consume the diamond");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void localCatalystRemovalDuringExtractionRollsBack(GameTestHelper h) {
    var r = room(h);
    r.shelf.setItem(0, RitualGameTests.book("diamond"));
    var pos = r.table.offset(-2, 0, 0);
    var handler =
        new ItemStackHandler(2) {
          @Override
          public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var result = super.extractItem(slot, amount, simulate);
            if (!simulate && slot == 0) setStackInSlot(1, ItemStack.EMPTY);
            return result;
          }
        };
    handler.setStackInSlot(0, new ItemStack(Items.DIAMOND));
    handler.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    RitualApi.registerCandidateProvider(
        (level, table, radius) ->
            level == h.getLevel() && table.equals(r.table) ? List.of(pos) : List.of());
    RitualApi.registerPedestal(
        (level, at) -> level == h.getLevel() && at.equals(pos) ? handler : null);
    r.pedestal(
        2, new ItemStack(Items.DIRT), new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
    var plan = r.plan(target.getItem());
    h.assertTrue(
        !commit(r, target, plan).isEmpty(),
        "Another catalyst cannot mask removal from the consumed pedestal");
    h.assertTrue(
        handler.getStackInSlot(0).is(Items.DIAMOND)
            && RitualMath.enchantments(target.getItem()).isEmpty(),
        "Receipt restores the material");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void experienceFlowsThroughEachDisplayedCatalyst(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(1000);
    var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
    var paths =
        RitualEffects.paths(
            h.getLevel(), r.network(), plan.evaluation().used(), plan.selected().keySet(), r.table);
    var xpPaths =
        paths.stream()
            .filter(p -> p.enchantment().equals(RitualsNotRolls.id("experience")))
            .toList();
    h.assertTrue(
        xpPaths.size() == 4
            && xpPaths.stream().filter(RitualEffects.Path::fromPlayer).count() == 2
            && xpPaths.stream().filter(RitualEffects.Path::orbit).count() == 2,
        "Each catalyst has player input and table output routes");
    h.assertTrue(
        !r.material.items.isItemValid(1, xp()), "XP is a displayed item, not an attachment");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void legacyXpAttachmentMigrationPreservesItems(GameTestHelper h) {
    var r = room(h);
    r.material.items.setStackInSlot(1, xp());
    r.material.migrateLegacyExperience();
    h.assertTrue(
        r.material.items.getStackInSlot(0).is(Items.DIAMOND)
            && r.material.items.getStackInSlot(1).isEmpty(),
        "Occupied legacy pedestal keeps its material");
    h.assertTrue(
        h
            .getLevel()
            .getEntitiesOfClass(
                ItemEntity.class,
                new net.minecraft.world.phys.AABB(r.material.getBlockPos()).inflate(1))
            .stream()
            .anyMatch(e -> e.getItem().is(RitualsNotRolls.XP_CATALYST)),
        "Old XP attachment returned to world");
    var empty = r.pedestal(1, ItemStack.EMPTY, xp());
    empty.migrateLegacyExperience();
    h.assertTrue(
        empty.items.getStackInSlot(0).is(RitualsNotRolls.XP_CATALYST)
            && empty.items.getStackInSlot(1).isEmpty(),
        "Empty legacy pedestal displays its XP catalyst");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void allSixPedestalPlacementsAndShapes(GameTestHelper h) {
    var r = room(h);
    var pos = r.material.getBlockPos();
    double volume = -1;
    for (var direction : Direction.values()) {
      var context =
          new net.minecraft.world.item.context.BlockPlaceContext(
              r.player,
              net.minecraft.world.InteractionHand.MAIN_HAND,
              new ItemStack(RitualsNotRolls.PEDESTAL_ITEM.get()),
              new net.minecraft.world.phys.BlockHitResult(
                  net.minecraft.world.phys.Vec3.atCenterOf(pos), direction, pos, false));
      var state = RitualsNotRolls.PEDESTAL.get().getStateForPlacement(context);
      h.assertTrue(
          state.getValue(com.cappleapple.ritualsnotrolls.pedestal.PedestalBlock.FACING)
              == direction,
          "Placement follows the clicked face");
      h.getLevel().setBlock(pos, state, 3);
      var shape = state.getShape(h.getLevel(), pos);
      double actual =
          shape.toAabbs().stream()
              .mapToDouble(a -> a.getXsize() * a.getYsize() * a.getZsize())
              .sum();
      if (volume < 0) volume = actual;
      h.assertTrue(
          Math.abs(actual - volume) < 1e-9,
          "Rotated collision geometry preserves the pedestal volume");
      var offset =
          com.cappleapple.ritualsnotrolls.pedestal.PedestalGeometry.displayOffset(state)
              .subtract(.5, .5, .5);
      h.assertTrue(
          offset.dot(net.minecraft.world.phys.Vec3.atLowerCornerOf(direction.getNormal())) > .7,
          "Displayed item floats beyond the oriented top");
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void everyDefaultEnchantmentHasUniqueResolvedEffect(GameTestHelper h) {
    Set<String> effects = new HashSet<>();
    for (var def : Definitions.SERVER.enchantments().values()) {
      h.assertTrue(
          def.particle().isPresent() && def.particleColor().isPresent(),
          "Each default supplies its visual identity");
      h.assertTrue(
          net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE
                  .getOptional(def.particle().orElseThrow())
                  .orElse(null)
              instanceof net.minecraft.core.particles.SimpleParticleType,
          "Default effect resolves to a native simple provider");
      h.assertTrue(
          effects.add(def.particle().get() + "/" + def.particleColor().get()),
          "Distinct default effect/color pair");
    }
    h.assertTrue(effects.size() == 42, "All vanilla enchantments covered");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void mendingAndIronUseTheRequestedBalance(GameTestHelper h) {
    var iron = new ItemStack(Items.IRON_INGOT);
    double sharp =
        RitualMath.base(Definitions.SERVER.get(RitualGameTests.SHARP), Set.of("iron_ingot"), iron);
    double knock =
        RitualMath.base(
            Definitions.SERVER.get(ResourceLocation.parse("minecraft:knockback")),
            Set.of("iron_ingot"),
            iron);
    h.assertTrue(sharp == 8 && knock == 24, "Iron favors knockback over sharpness");
    h.assertTrue(
        Definitions.SERVER.get(ResourceLocation.parse("minecraft:mending")).required(1) == 256,
        "Mending requires 256 imbuement power");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void completionBurstRetainsEnchantmentColorsAndXp(GameTestHelper h) {
    var particles =
        RitualEffects.completionParticles(
            net.minecraft.world.phys.Vec3.ZERO,
            Set.of(RitualGameTests.SHARP, RitualGameTests.UNBREAKING),
            2);
    h.assertTrue(particles.size() == 96, "Completion has a fixed particle budget");
    Set<Integer> colors = new HashSet<>(), octants = new HashSet<>();
    for (var p : particles) {
      colors.add(p.rgb());
      var f = p.flight().orElseThrow();
      var d = f.bend();
      h.assertTrue(
          f.burst() && !f.orbit() && f.remaining() >= 32 && f.remaining() <= 44,
          "Completion uses finite outward fading flights");
      octants.add((d.x > 0 ? 1 : 0) | (d.y > 0 ? 2 : 0) | (d.z > 0 ? 4 : 0));
    }
    h.assertTrue(
        colors.equals(Set.of(0xAFCFFF, 0x8FE4FF, 0x98FF50)) && octants.size() == 8,
        "Both enchantment colors and XP spread in all directions");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void sequentialPathsUseEvaluatedPowerAndGateLaterEnchantments(GameTestHelper h) {
    var r = room(h);
    r.pedestal(-1, new ItemStack(Items.ANCIENT_DEBRIS), ItemStack.EMPTY);
    var sharp = RitualGameTests.SHARP;
    var unbreaking = ResourceLocation.withDefaultNamespace("unbreaking");
    r.shelf.setItem(
        1, Knowledge.book(new KnowledgeData(unbreaking, List.of("ancient_debris"), true)));
    var network = r.network();
    var plan = RitualMath.automatic(r.player, new ItemStack(Items.DIAMOND_SWORD), network);
    var paths =
        RitualEffects.paths(
            h.getLevel(), network, plan.evaluation().used(), plan.selected().keySet(), r.table);
    var schedule = RitualEffects.schedule(paths, plan.evaluation(), 120, true);
    h.assertTrue(schedule.stages().size() == 2, "Two enchantments participate");
    var first = schedule.stages().getFirst();
    var second = schedule.stages().getLast();
    h.assertTrue(second.start() == first.formed(), "Next channel starts when the first ring forms");
    for (var stage : schedule.stages()) {
      double power =
          plan.evaluation().lines().stream()
              .filter(l -> l.enchantment().equals(stage.enchantment()))
              .findFirst()
              .orElseThrow()
              .power();
      h.assertTrue(
          stage.radius() == RitualAnimation.radius(power),
          "Radius uses actual evaluated material and catalyst power");
    }
    int firstRgb = Definitions.SERVER.get(first.enchantment()).particleRgb();
    int secondRgb = Definitions.SERVER.get(second.enchantment()).particleRgb();
    var before =
        RitualEffects.emissions(paths, schedule, Vec3.ZERO, r.player.getId(), second.start() - 2);
    h.assertTrue(
        before.stream().allMatch(e -> e.particle().rgb() == firstRgb),
        "Later effects cannot start early");
    Set<Integer> seen = new HashSet<>();
    for (int age = second.start(); age < schedule.sourceStop(); age += 2)
      RitualEffects.emissions(paths, schedule, Vec3.ZERO, r.player.getId(), age)
          .forEach(e -> seen.add(e.particle().rgb()));
    h.assertTrue(
        seen.contains(firstRgb) && seen.contains(secondRgb),
        "Earlier and later effects coexist before the finish");
    var together = RitualEffects.schedule(paths, plan.evaluation(), 120, false);
    h.assertTrue(
        together.stages().stream().allMatch(stage -> stage.start() == 0),
        "Config alternative starts every channel together");
    h.succeed();
  }

  private static RitualAnimation.Schedule schedule(Room r, ItemStack item) {
    var a = RitualAttempt.create(r.player, item, r.network());
    return RitualEffects.schedule(
        RitualEffects.paths(
            r.h.getLevel(),
            r.network(),
            a.visual(),
            r.table,
            r.player.position().add(0, r.player.getBbHeight() * .6, 0)),
        a.visual().evaluation(),
        120,
        true,
        a.nativeCosts(),
        a.minima(),
        a.failing(),
        a.successful().experience().points());
  }

  @GameTest(template = "network", timeoutTicks = 500)
  public static void failedConsumedChainReleasesUnchangedAndNeverSpends(GameTestHelper h) {
    var r = room(h);
    twoXp(r);
    r.player.giveExperiencePoints(1000);
    r.material.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var item =
        RitualMath.apply(
            r.player, new ItemStack(Items.DIAMOND_SWORD), Map.of(RitualGameTests.SHARP, 3));
    var attempt = RitualAttempt.create(r.player, item, r.network());
    h.assertTrue(
        attempt.successful().selected().isEmpty()
            && attempt.failing().contains(RitualGameTests.SHARP),
        "Insufficient upgrade is a failed-only attempt");
    int end = schedule(r, item).duration();
    var entity = r.drop(item.copy());
    h.assertTrue(
        RitualEngine.tryStart(h.getLevel(), r.table, entity), "Weak attempt captures tool");
    h.runAfterDelay(
        end / 2,
        () ->
            h.assertTrue(
                entity.isNoGravity() && !r.material.items.getStackInSlot(0).isEmpty(),
                "Failure animates without taking offering"));
    h.runAfterDelay(
        end + 5,
        () -> {
          h.assertTrue(
              ItemStack.matches(item, entity.getItem()) && !entity.isNoGravity(),
              "Failure releases unchanged tool");
          h.assertTrue(
              r.material.items.getStackInSlot(0).is(Items.DIAMOND)
                  && RitualMath.experiencePoints(r.player) == 1000,
              "No material or XP debit");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 600)
  public static void mixedAttemptConsumesOnlySuccessfulChain(GameTestHelper h) {
    var r = room(h);
    r.material.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var loot = ResourceLocation.withDefaultNamespace("looting");
    r.shelf.setItem(1, Knowledge.book(new KnowledgeData(loot, List.of("gold_nugget"), true)));
    var weak =
        r.pedestal(
            -2,
            new ItemStack(Items.GOLD_NUGGET),
            new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var item = new ItemStack(Items.DIAMOND_SWORD);
    var attempt = RitualAttempt.create(r.player, item, r.network());
    h.assertTrue(
        attempt.successful().selected().containsKey(RitualGameTests.SHARP)
            && attempt.failing().equals(Set.of(loot)),
        "Success and failure coexist");
    int end = schedule(r, item).duration();
    var entity = r.drop(item);
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, entity), "Mixed attempt starts");
    h.runAfterDelay(
        end + 5,
        () -> {
          h.assertTrue(
              !RitualMath.enchantments(entity.getItem()).isEmpty()
                  && r.material.items.getStackInSlot(0).isEmpty(),
              "Success commits and consumes");
          h.assertTrue(
              weak.items.getStackInSlot(0).is(Items.GOLD_NUGGET),
              "Failed chain keeps marked material");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 500)
  public static void offeringsAreTakenInSplineOrderAndCommitOnlyOnce(GameTestHelper h) {
    var r = room(h);
    var catalyst = new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get());
    r.material.items.setStackInSlot(1, catalyst.copy());
    var second = r.pedestal(-4, new ItemStack(Items.NETHERITE_SCRAP), catalyst.copy());
    var item = new ItemStack(Items.DIAMOND_SWORD);
    var a = RitualAttempt.create(r.player, item, r.network());
    var paths = RitualEffects.paths(h.getLevel(), r.network(), a.visual(), r.table);
    var curve =
        paths.stream()
            .filter(p -> p.terminal() && p.enchantment().equals(RitualGameTests.SHARP))
            .findFirst()
            .orElseThrow()
            .curve()
            .orElseThrow();
    var entity = r.drop(item);
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, entity), "Starts consuming chain");
    h.runAfterDelay(
        curve.arrival(1) + 2,
        () ->
            h.assertTrue(
                r.material.items.getStackInSlot(0).isEmpty()
                    && second.items.getStackInSlot(0).is(Items.NETHERITE_SCRAP),
                "Only first reached pedestal consumed"));
    h.runAfterDelay(
        curve.arrival(2) + 2,
        () ->
            h.assertTrue(
                second.items.getStackInSlot(0).isEmpty() && entity.isNoGravity(),
                "Second offering consumed before completion"));
    int end = schedule(r, item).duration();
    h.runAfterDelay(
        end + 5,
        () -> {
          h.assertTrue(
              !entity.isNoGravity() && !RitualMath.enchantments(entity.getItem()).isEmpty(),
              "Escrow commit succeeds after early consumption");
          h.assertTrue(
              !entity.getPersistentData().contains(RitualConsumption.KEY),
              "No recovery duplication after commit");
          h.succeed();
        });
  }

  @GameTest(template = "network", timeoutTicks = 500)
  public static void interruptedEarlyConsumptionReturnsOffering(GameTestHelper h) {
    var r = room(h);
    r.material.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var item = new ItemStack(Items.DIAMOND_SWORD);
    var a = RitualAttempt.create(r.player, item, r.network());
    var curve =
        RitualEffects.paths(h.getLevel(), r.network(), a.visual(), r.table).stream()
            .filter(RitualEffects.Path::terminal)
            .findFirst()
            .orElseThrow()
            .curve()
            .orElseThrow();
    var entity = r.drop(item);
    h.assertTrue(RitualEngine.tryStart(h.getLevel(), r.table, entity), "Starts");
    h.runAfterDelay(
        curve.arrival(1) + 2,
        () -> {
          h.assertTrue(r.material.items.getStackInSlot(0).isEmpty(), "Offering reserved");
          h.getLevel().setBlock(r.table, Blocks.AIR.defaultBlockState(), 3);
        });
    h.runAfterDelay(
        curve.arrival(1) + 8,
        () -> {
          h.assertTrue(
              r.material.items.getStackInSlot(0).is(Items.DIAMOND) && !entity.isNoGravity(),
              "Interruption refunds offering");
          h.assertTrue(
              !entity.getPersistentData().contains(RitualConsumption.KEY), "Escrow cleared");
          h.succeed();
        });
  }
}
