package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.pedestal.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class ChainGameTests {
  private static ResourceLocation id(String name) {
    return ResourceLocation.withDefaultNamespace(name);
  }

  private static final ResourceLocation SHARP = id("sharpness"), LOOT = id("looting");

  private static RitualDefinition definition(
      ResourceLocation id, Map<Item, Double> items, double... costs) {
    var affinities = new ArrayList<Affinity>();
    items.forEach(
        (item, power) -> {
          var key = BuiltInRegistries.ITEM.getKey(item);
          affinities.add(new Affinity(key.getPath(), Optional.of(key), Optional.empty(), power, 1));
        });
    Map<String, Double> thresholds = new LinkedHashMap<>();
    for (int i = 0; i < costs.length; i++) thresholds.put("" + (i + 1), costs[i]);
    return new RitualDefinition(
        id,
        affinities,
        thresholds,
        List.of(),
        Optional.empty(),
        Optional.of(id("enchant")),
        Optional.empty(),
        Optional.of("#AFCFFF"));
  }

  private static final class Room implements AutoCloseable {
    final GameTestHelper h;
    final BlockPos table;
    final ServerPlayer player;
    final Definitions.Snapshot original = Definitions.SERVER;
    final boolean chained = Config.CHAIN_ANIMATIONS.get();

    Room(GameTestHelper h, RitualDefinition... definitions) {
      this.h = h;
      table = h.absolutePos(new BlockPos(20, 1, 20));
      h.getLevel().setBlock(table, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
      player = RitualGameTests.player(h);
      player.setPos(Vec3.atCenterOf(table));
      var map = new TreeMap<ResourceLocation, RitualDefinition>();
      for (var definition : definitions) map.put(definition.enchantment(), definition);
      Definitions.SERVER = new Definitions.Snapshot(map, original.rules(), original.revision() + 1);
      Config.CHAIN_ANIMATIONS.set(true);
    }

    void books(int x, int z, ResourceLocation... ids) {
      var pos = table.offset(x, 0, z);
      h.getLevel().setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
      var shelf = (ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(pos);
      for (int i = 0; i < ids.length; i++) {
        var def = Definitions.SERVER.get(ids[i]);
        shelf.setItem(
            i,
            Knowledge.book(
                new KnowledgeData(
                    ids[i], def.materials().stream().map(Affinity::id).toList(), true)));
      }
    }

    PedestalEntity pedestal(int x, int z, Item item, boolean consumed, boolean subtracted) {
      var pos = table.offset(x, 0, z);
      h.getLevel().setBlock(pos, RitualsNotRolls.PEDESTAL.get().defaultBlockState(), 3);
      var p = (PedestalEntity) h.getLevel().getBlockEntity(pos);
      p.items.setStackInSlot(0, new ItemStack(item));
      if (consumed)
        p.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
      if (subtracted)
        p.items.setStackInSlot(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()));
      return p;
    }

    RitualNetwork.Snapshot network() {
      return RitualNetwork.scan(h.getLevel(), table, false, true);
    }

    RitualMath.Plan plan(ItemStack target) {
      return RitualMath.automatic(player, target, network());
    }

    ItemStack enchanted(ResourceLocation id, int level) {
      return RitualMath.apply(player, new ItemStack(Items.DIAMOND_SWORD), Map.of(id, level));
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

    void commit(ItemEntity entity, RitualMath.Plan plan) {
      var reason =
          RitualEngine.commit(h.getLevel(), table, player, entity, entity.getItem().copy(), plan);
      h.assertTrue(reason.isEmpty(), "Commit: " + reason);
    }

    List<RitualEffects.Path> paths(RitualMath.Plan plan) {
      return RitualEffects.paths(h.getLevel(), network(), plan, table);
    }

    @Override
    public void close() {
      Definitions.SERVER = original;
      Config.CHAIN_ANIMATIONS.set(chained);
      RitualNetwork.invalidate(h.getLevel());
    }
  }

  private static double power(RitualMath.Plan p, ResourceLocation id) {
    return p.evaluation().lines().stream()
        .filter(l -> l.enchantment().equals(id))
        .findFirst()
        .orElseThrow()
        .power();
  }

  @GameTest(template = "network")
  public static void sharedRelativePowerActuallyUpgradesBoth(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 10.0), 2, 5),
            definition(LOOT, Map.of(Items.DIAMOND, 20.0), 3, 10))) {
      r.books(3, 0, SHARP, LOOT);
      r.pedestal(1, 2, Items.DIAMOND, false, false);
      var target =
          RitualMath.apply(r.player, new ItemStack(Items.DIAMOND_SWORD), Map.of(SHARP, 1, LOOT, 1));
      var plan = r.plan(target);
      h.assertTrue(
          plan.evaluation().ready() && plan.selected().equals(Map.of(SHARP, 2, LOOT, 2)),
          "Both advance after the split");
      h.assertTrue(power(plan, SHARP) == 5 && power(plan, LOOT) == 10, "10/20 becomes 5/10");
      var entity = r.drop(target);
      r.commit(entity, plan);
      h.assertTrue(
          RitualMath.enchantments(entity.getItem()).entrySet().stream()
              .allMatch(e -> e.getIntValue() == 2),
          "Both levels committed");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void sharingThatWouldLoseTheUpgradeIsRejected(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 10.0), 2, 11),
            definition(LOOT, Map.of(Items.DIAMOND, 20.0), 3, 15))) {
      r.books(3, 0, SHARP, LOOT);
      r.pedestal(1, 2, Items.DIAMOND, false, false);
      var target =
          RitualMath.apply(r.player, new ItemStack(Items.DIAMOND_SWORD), Map.of(SHARP, 1, LOOT, 1));
      var plan = r.plan(target);
      h.assertTrue(
          plan.selected().equals(Map.of(LOOT, 2)) && power(plan, LOOT) == 20,
          "Only Looting benefits and receives all its power");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void strongestPowerWinsWhenSharingCannotUpgradeBoth(GameTestHelper h) {
    for (double lootingCost : new double[] {4, 8}) {
      try (var r =
          new Room(
              h,
              definition(SHARP, Map.of(Items.DIAMOND, 20.0), 15),
              definition(LOOT, Map.of(Items.DIAMOND, 10.0), lootingCost))) {
        r.books(3, 0, LOOT, SHARP);
        r.pedestal(1, 2, Items.DIAMOND, false, false);
        var target = new ItemStack(Items.DIAMOND_SWORD);
        var plan = r.plan(target);
        h.assertTrue(
            plan.evaluation().ready()
                && plan.selected().equals(Map.of(SHARP, 1))
                && power(plan, SHARP) == 20,
            "Higher 20-power Sharpness wins over earlier-ID 10-power Looting, even if Looting alone"
                + " survives sharing");
        var entity = r.drop(target);
        r.commit(entity, plan);
        h.assertTrue(
            RitualMath.enchantments(entity.getItem()).size() == 1,
            "Only the stronger enchantment is committed");
      }
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void priorityIncludesPowerFromConsumedMaterials(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 10.0, Items.IRON_INGOT, 7.0), 22),
            definition(LOOT, Map.of(Items.DIAMOND, 20.0), 15))) {
      r.books(3, 0, LOOT, SHARP);
      r.pedestal(1, 2, Items.DIAMOND, false, false);
      var consumed = r.pedestal(-1, 2, Items.IRON_INGOT, true, false);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      h.assertTrue(
          plan.selected().equals(Map.of(SHARP, 1)) && power(plan, SHARP) == 24,
          "10 plus doubled 7 outranks 20 power before any sharing");
      h.assertTrue(
          plan.withdrawals().equals(Map.of(consumed.getBlockPos(), 1)),
          "Only the contributing consumed material is charged");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void equalPowerUsesStableIdTieBreak(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 20.0), 15),
            definition(LOOT, Map.of(Items.DIAMOND, 20.0), 15))) {
      r.books(3, 0, SHARP, LOOT);
      r.pedestal(1, 2, Items.DIAMOND, false, false);
      h.assertTrue(
          r.plan(new ItemStack(Items.DIAMOND_SWORD)).selected().equals(Map.of(LOOT, 1)),
          "Equal powers retain deterministic registry-ID priority");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void subtractionPriorityUsesPowerMagnitude(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 30.0), 10, 30),
            definition(LOOT, Map.of(Items.DIAMOND, 20.0), 10, 20))) {
      r.books(3, 0, LOOT, SHARP);
      r.pedestal(1, 2, Items.DIAMOND, false, true);
      var target =
          RitualMath.apply(r.player, new ItemStack(Items.DIAMOND_SWORD), Map.of(SHARP, 2, LOOT, 2));
      var plan = r.plan(target);
      h.assertTrue(
          plan.selected().equals(Map.of(SHARP, 0)) && power(plan, SHARP) == -30,
          "Stronger subtraction wins when sharing cannot remove a level from both");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void allKnowledgePagesUseRecipeViewerHiddenTag(GameTestHelper h) {
    var hidden = net.neoforged.neoforge.common.Tags.Items.HIDDEN_FROM_RECIPE_VIEWERS;
    h.assertTrue(
        Knowledge.page(SHARP, "diamond").is(hidden)
            && Knowledge.page(LOOT, "iron_ingot").is(hidden),
        "Component-bearing page variants are hidden by their item tag");
    h.assertTrue(
        !new ItemStack(RitualsNotRolls.BOOK.get()).is(hidden),
        "Knowledge books remain available in recipe viewers");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void unconsumedDuplicatesAreNotChainLinks(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10, 25))) {
      r.books(-6, 0, SHARP);
      var nearest = r.pedestal(-5, 0, Items.DIAMOND, false, false);
      r.pedestal(-2, 0, Items.DIAMOND, false, false);
      r.pedestal(2, 0, Items.DIAMOND, false, false);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      h.assertTrue(
          plan.chains().size() == 1
              && plan.chains().getFirst().outward().size() == 1
              && plan.evaluation().used().getFirst().pos().equals(nearest.getBlockPos()),
          "Only the first reusable copy participates");
      h.assertTrue(
          power(plan, SHARP) == 20 && r.paths(plan).size() == 2,
          "Duplicates add neither links nor return bonuses");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void eachConsumedDuplicateContributesAndIsSpentOnce(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10, 80))) {
      r.books(0, -5, SHARP);
      var a = r.pedestal(-4, 1, Items.DIAMOND, true, false);
      var b = r.pedestal(4, 1, Items.DIAMOND, true, false);
      var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
      var plan = r.plan(target.getItem());
      h.assertTrue(
          power(plan, SHARP) == 80
              && plan.withdrawals().equals(Map.of(a.getBlockPos(), 1, b.getBlockPos(), 1)),
          "Both marked copies supply doubled power");
      r.commit(target, plan);
      h.assertTrue(
          a.items.getStackInSlot(0).isEmpty()
              && b.items.getStackInSlot(0).isEmpty()
              && !a.items.getStackInSlot(1).isEmpty()
              && !b.items.getStackInSlot(1).isEmpty(),
          "Both materials spent; both modifiers retained");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void returningToSamePedestalAddsOnlyHalfItsBase(GameTestHelper h) {
    try (var r =
        new Room(
            h, definition(SHARP, Map.of(Items.DIAMOND, 20.0, Items.IRON_INGOT, 10.0), 10, 65))) {
      r.books(-6, 0, SHARP);
      var near = r.pedestal(2, 0, Items.DIAMOND, true, false);
      r.pedestal(4, 0, Items.IRON_INGOT, false, false);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      var chain = plan.chains().getFirst();
      h.assertTrue(
          chain.returning().size() == 1
              && chain.visits(near.getBlockPos()) == 1.5
              && power(plan, SHARP) == 70,
          "20 x 2 x 1.5 + 10 = 70");
      var paths = r.paths(plan);
      var nearCenter = PedestalGeometry.displayPosition(near.getBlockPos(), near.getBlockState());
      h.assertTrue(
          paths.size() == 4
              && paths.get(0).to().equals(nearCenter)
              && paths.get(2).to().equals(nearCenter),
          "Book, nearest, farthest, returning nearest, table");
      for (int i = 1; i < paths.size(); i++)
        h.assertTrue(
            paths.get(i).delay() > paths.get(i - 1).delay()
                && paths.get(i).from().equals(paths.get(i - 1).to()),
            "Hops are continuous and ordered");
      var entity = r.drop(new ItemStack(Items.DIAMOND_SWORD));
      r.commit(entity, plan);
      h.assertTrue(
          near.items.getStackInSlot(0).isEmpty(), "Return visit spends the one item exactly once");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void unconsumedReturnVisitGetsBonusWhileSeparateDuplicateStaysExcluded(
      GameTestHelper h) {
    try (var r =
        new Room(
            h, definition(SHARP, Map.of(Items.DIAMOND, 20.0, Items.IRON_INGOT, 10.0), 10, 40))) {
      r.books(-6, 0, SHARP);
      var original = r.pedestal(2, 0, Items.DIAMOND, false, false);
      var farthest = r.pedestal(4, 0, Items.IRON_INGOT, false, false);
      var duplicate = r.pedestal(6, 0, Items.DIAMOND, false, false);
      var entity = r.drop(new ItemStack(Items.DIAMOND_SWORD));
      var plan = r.plan(entity.getItem());
      var chain = plan.chains().getFirst();
      h.assertTrue(
          plan.evaluation().ready()
              && plan.selected().equals(Map.of(SHARP, 2))
              && power(plan, SHARP) == 40,
          "Unconsumed diamond 20 x 1.5 + iron 10 reaches level II");
      h.assertTrue(
          chain.outward().stream()
                  .map(RitualNetwork.Pedestal::pos)
                  .toList()
                  .equals(List.of(original.getBlockPos(), farthest.getBlockPos()))
              && chain.returning().stream()
                  .map(RitualNetwork.Pedestal::pos)
                  .toList()
                  .equals(List.of(original.getBlockPos())),
          "The original is revisited; the separate duplicate is not an outward or return link");
      var paths = r.paths(plan);
      var center =
          PedestalGeometry.displayPosition(original.getBlockPos(), original.getBlockState());
      h.assertTrue(
          paths.size() == 4 && paths.get(0).to().equals(center) && paths.get(2).to().equals(center),
          "Particles revisit the same unconsumed pedestal before reaching the table");
      h.assertTrue(
          plan.withdrawals().isEmpty() && plan.consumed().isEmpty(),
          "The bonus needs no consumption");
      r.commit(entity, plan);
      h.assertTrue(
          original.items.getStackInSlot(0).is(Items.DIAMOND)
              && farthest.items.getStackInSlot(0).is(Items.IRON_INGOT)
              && duplicate.items.getStackInSlot(0).is(Items.DIAMOND),
          "All unconsumed materials remain after commit");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void consumedExtraCopyCanChainAlongsideRevisitedUnconsumedOriginal(
      GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 20.0, Items.IRON_INGOT, 10.0), 10, 40, 85))) {
      r.books(-6, 0, SHARP);
      var original = r.pedestal(2, 0, Items.DIAMOND, false, false);
      r.pedestal(4, 0, Items.IRON_INGOT, false, false);
      var consumed = r.pedestal(6, 0, Items.DIAMOND, true, false);
      var excluded = r.pedestal(7, 0, Items.DIAMOND, false, false);
      var entity = r.drop(new ItemStack(Items.DIAMOND_SWORD));
      var plan = r.plan(entity.getItem());
      h.assertTrue(
          plan.evaluation().ready()
              && plan.selected().equals(Map.of(SHARP, 3))
              && power(plan, SHARP) == 85,
          "Unconsumed diamond 30 + revisited iron 15 + consumed duplicate 40");
      h.assertTrue(
          plan.chains().getFirst().outward().size() == 3
              && plan.chains().getFirst().visits(original.getBlockPos()) == 1.5
              && !plan.users().containsKey(excluded.getBlockPos())
              && plan.withdrawals().equals(Map.of(consumed.getBlockPos(), 1)),
          "Only the consumed extra copy joins the chain and is charged");
      r.commit(entity, plan);
      h.assertTrue(
          original.items.getStackInSlot(0).is(Items.DIAMOND)
              && excluded.items.getStackInSlot(0).is(Items.DIAMOND)
              && consumed.items.getStackInSlot(0).isEmpty()
              && !consumed.items.getStackInSlot(1).isEmpty(),
          "Original and excluded copy stay; the consumed copy is spent once");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void disabledChainingUsesConcurrentMaterialFlightsWithoutReturnBonus(
      GameTestHelper h) {
    try (var r =
        new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0, Items.IRON_INGOT, 10.0), 10))) {
      r.books(-6, 0, SHARP);
      r.pedestal(2, 0, Items.DIAMOND, false, false);
      r.pedestal(4, 0, Items.IRON_INGOT, false, false);
      Config.CHAIN_ANIMATIONS.set(false);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      h.assertTrue(
          power(plan, SHARP) == 30 && plan.chains().getFirst().returning().isEmpty(),
          "No second visit without chaining");
      h.assertTrue(
          r.paths(plan).stream().filter(p -> p.knowledge() && p.delay() == 0).count() == 2,
          "Both book-to-material flows start together");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void separateBookshelvesGiveSharedMaterialsIndependentRoutes(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 20.0), 15),
            definition(LOOT, Map.of(Items.DIAMOND, 30.0), 25))) {
      r.books(-6, 0, SHARP);
      r.books(6, 0, LOOT);
      var a = r.pedestal(-5, 1, Items.DIAMOND, false, false);
      var b = r.pedestal(5, 1, Items.DIAMOND, false, false);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      h.assertTrue(
          plan.selected().size() == 2 && power(plan, SHARP) == 20 && power(plan, LOOT) == 30,
          "Independent chains do not divide power");
      h.assertTrue(
          plan.users().get(a.getBlockPos()).equals(Set.of(SHARP))
              && plan.users().get(b.getBlockPos()).equals(Set.of(LOOT)),
          "Nearest shelves own separate copies");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void differentStarterMaterialsSplitASharedBookshelf(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.IRON_INGOT, 2.0, Items.DIAMOND, 20.0), 21),
            definition(LOOT, Map.of(Items.EMERALD, 3.0, Items.DIAMOND, 30.0), 32))) {
      r.books(0, -6, SHARP, LOOT);
      r.pedestal(-2, -5, Items.IRON_INGOT, false, false);
      r.pedestal(2, -5, Items.EMERALD, false, false);
      var a = r.pedestal(-5, -3, Items.DIAMOND, false, false);
      var b = r.pedestal(5, -3, Items.DIAMOND, false, false);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      h.assertTrue(
          plan.selected().size() == 2 && power(plan, SHARP) == 22 && power(plan, LOOT) == 33,
          "Distinct starters isolate downstream shared materials");
      h.assertTrue(
          plan.users().get(a.getBlockPos()).equals(Set.of(SHARP))
              && plan.users().get(b.getBlockPos()).equals(Set.of(LOOT)),
          "Each diamond belongs to its nearest starter branch");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void completionBurstRequiresAnActualAdditionOrUpgrade(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 30.0), 10, 20, 30),
            definition(LOOT, Map.of(Items.IRON_INGOT, 10.0), 10))) {
      var target = r.enchanted(SHARP, 2);
      var center = Vec3.atCenterOf(r.table);
      for (int remainingLevel : new int[] {0, 1, 2})
        h.assertTrue(
            RitualEffects.completionParticles(center, target, Map.of(SHARP, remainingLevel), 2)
                .isEmpty(),
            "Removal, reduction or unchanged levels do not burst, even with XP catalysts");
      h.assertTrue(
          RitualEffects.completionParticles(center, target, Map.of(SHARP, 3), 0).size() == 96,
          "An upgrade still bursts");
      h.assertTrue(
          RitualEffects.completionParticles(center, target, Map.of(SHARP, 1, LOOT, 1), 0).size()
              == 96,
          "Mixed disenchanting and enchanting still bursts");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void subtractionWithConsumptionRemovesAnEntireCurse(GameTestHelper h) {
    var curse = id("vanishing_curse");
    try (var r = new Room(h, definition(curse, Map.of(Items.DIAMOND, 10.0), 20))) {
      r.books(3, 0, curse);
      var pedestal = r.pedestal(1, 2, Items.DIAMOND, true, true);
      var target = r.drop(r.enchanted(curse, 1));
      var plan = r.plan(target.getItem());
      h.assertTrue(
          plan.evaluation().ready() && plan.selected().get(curse) == 0 && power(plan, curse) == -20,
          "Combined modifiers double negative power");
      var paths = r.paths(plan);
      h.assertTrue(
          paths.size() == 2
              && paths.getFirst().from().equals(Vec3.atLowerCornerOf(r.table).add(.5, 1.7, .5))
              && paths.stream().noneMatch(RitualEffects.Path::orbit)
              && paths.stream().allMatch(RitualEffects.Path::draining),
          "Complete removal runs the inverse route without a surviving ring");
      r.commit(target, plan);
      h.assertTrue(
          RitualMath.enchantments(target.getItem()).isEmpty()
              && pedestal.items.getStackInSlot(0).isEmpty()
              && pedestal.items.getStackInSlot(1).getCount() == 1
              && pedestal.items.getStackInSlot(2).getCount() == 1,
          "Curse and material removed, both modifiers retained");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void partialSuccessfulSubtractionRetainsProportionateRing(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 30.0), 10, 30, 60))) {
      r.books(3, 0, SHARP);
      r.pedestal(1, 2, Items.DIAMOND, false, true);
      var plan = r.plan(r.enchanted(SHARP, 3));
      h.assertTrue(
          plan.selected().equals(Map.of(SHARP, 2)) && plan.remainingFractions().get(SHARP) == .5,
          "Half of the starting power remains");
      h.assertTrue(
          r.paths(plan).stream().anyMatch(p -> p.orbit() && p.fraction() == .5f && p.knowledge()),
          "The book supplies the proportionate surviving orbit");
      var target = r.drop(r.enchanted(SHARP, 3));
      r.commit(target, plan);
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void weakOrAbsentSubtractionDoesNothingAndSpendsNothing(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 4.0), 10, 30))) {
      r.books(3, 0, SHARP);
      var pedestal = r.pedestal(1, 2, Items.DIAMOND, true, true);
      r.pedestal(-2, 0, RitualsNotRolls.XP_CATALYST.get(), false, false);
      r.player.giveExperiencePoints(1000);
      for (var stack : List.of(r.enchanted(SHARP, 2), new ItemStack(Items.DIAMOND_SWORD))) {
        var target = r.drop(stack);
        var plan = r.plan(stack);
        h.assertTrue(
            !plan.evaluation().ready() && plan.chains().isEmpty() && plan.withdrawals().isEmpty(),
            "Ineffective subtraction has no active materials or chains");
        h.assertTrue(
            !RitualEngine.tryStart(h.getLevel(), r.table, target)
                && RitualEngine.state(h.getLevel(), r.table).equals("idle"),
            "No animation starts");
        h.assertTrue(
            RitualMath.experiencePoints(r.player) == 1000
                && pedestal.items.getStackInSlot(0).getCount() == 1,
            "No XP or material spent");
        target.discard();
      }
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void modifierChangeCancelsAnAlreadyCalculatedPlan(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10, 30))) {
      r.books(3, 0, SHARP);
      var p = r.pedestal(1, 2, Items.DIAMOND, true, false);
      var target = r.drop(new ItemStack(Items.DIAMOND_SWORD));
      var plan = r.plan(target.getItem());
      p.items.setStackInSlot(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()));
      h.assertTrue(
          !RitualEngine.commit(
                      h.getLevel(), r.table, r.player, target, target.getItem().copy(), plan)
                  .isEmpty()
              && p.items.getStackInSlot(0).getCount() == 1
              && RitualMath.enchantments(target.getItem()).isEmpty(),
          "Changing direction cancels without spending");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void multipleModifiersPersistAndOldInventoriesExpand(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10))) {
      var p = r.pedestal(1, 2, Items.DIAMOND, true, true);
      var saved = p.saveWithoutMetadata(h.getLevel().registryAccess());
      var copy = new PedestalEntity(p.getBlockPos(), p.getBlockState());
      copy.loadWithComponents(saved, h.getLevel().registryAccess());
      h.assertTrue(
          copy.items.getSlots() == 3
              && copy.items.getStackInSlot(1).is(RitualsNotRolls.CONSUMPTION_CATALYST)
              && copy.items.getStackInSlot(2).is(RitualsNotRolls.SUBTRACTION_CATALYST),
          "Both modifiers survive save/load");
      var old = new ItemStackHandler(2);
      old.setStackInSlot(0, new ItemStack(Items.DIAMOND));
      old.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
      saved.put("inventory", old.serializeNBT(h.getLevel().registryAccess()));
      copy.loadWithComponents(saved, h.getLevel().registryAccess());
      h.assertTrue(
          copy.items.getSlots() == 3
              && copy.items.getStackInSlot(0).is(Items.DIAMOND)
              && copy.items
                  .insertItem(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()), false)
                  .isEmpty(),
          "Old two-slot pedestal accepts a second modifier without losing items");
      copy.items.setStackInSlot(0, ItemStack.EMPTY);
      h.assertTrue(
          copy.items.insertItem(0, new ItemStack(Items.DIAMOND, 64), false).getCount() == 63
              && copy.items.getStackInSlot(0).getCount() == 1,
          "Only one material fits on a pedestal");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void configuredChestBooksConvertAndUnsupportedPartsRemain(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10))) {
      var configured = RitualMath.apply(r.player, new ItemStack(Items.BOOK), Map.of(SHARP, 3));
      var unsupported = RitualMath.apply(r.player, new ItemStack(Items.BOOK), Map.of(LOOT, 2));
      var mixed = RitualMath.apply(r.player, new ItemStack(Items.BOOK), Map.of(SHARP, 3, LOOT, 2));
      mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Treasured book"));
      var converted =
          DiscoveryLoot.replaceBooks(
              List.of(configured, unsupported, mixed, new ItemStack(Items.GOLD_INGOT)),
              RandomSource.create(5));
      h.assertTrue(
          converted.stream().filter(s -> s.is(RitualsNotRolls.PAGE)).count() == 2,
          "One knowledge page per configured enchantment");
      h.assertTrue(
          converted.stream().filter(s -> s.is(Items.ENCHANTED_BOOK)).count() == 2
              && converted.contains(unsupported),
          "Unsupported books remain");
      var retained =
          converted.stream()
              .filter(s -> s.has(DataComponents.CUSTOM_NAME))
              .findFirst()
              .orElseThrow();
      h.assertTrue(
          RitualMath.enchantments(retained).size() == 1
              && retained.get(DataComponents.CUSTOM_NAME).getString().equals("Treasured book")
              && RitualMath.enchantments(mixed).size() == 2,
          "Mixed book retains unsupported enchantment, name, and input is unchanged");
      h.assertTrue(
          converted.stream()
              .filter(s -> s.is(RitualsNotRolls.PAGE))
              .allMatch(s -> Knowledge.data(s).enchantment().equals(SHARP)),
          "Only configured discoveries are created");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void missingDefinitionsCommandUsesActualLoadedRegistry(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10))) {
      var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
      var expected = new TreeSet<>(registry.keySet());
      expected.remove(SHARP);
      h.assertTrue(
          new TreeSet<>(DebugCommands.missing(h.getLevel().getServer())).equals(expected),
          "Every registered enchantment without loaded data is listed");
      h.assertTrue(
          h.getLevel()
                  .getServer()
                  .getCommands()
                  .getDispatcher()
                  .findNode(List.of("ritual", "missing"))
              != null,
          "Command is registered");
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void enchantabilityUsesGentleConfigurableScaling(GameTestHelper h) {
    boolean enabled = Config.ENCHANTABILITY.get();
    double exponent = Config.ENCHANTABILITY_EXPONENT.get();
    try {
      Config.ENCHANTABILITY.set(true);
      Config.ENCHANTABILITY_EXPONENT.set(.5);
      h.assertTrue(
          RitualMath.enchantabilityMultiplier(new ItemStack(Items.DIAMOND_SWORD)) == 1,
          "Rating 10 is neutral");
      double gold = RitualMath.enchantabilityMultiplier(new ItemStack(Items.GOLDEN_SWORD));
      h.assertTrue(
          Math.abs(gold - Math.sqrt(10.0 / 22)) < 1e-8, "High enchantability reduces cost gently");
      h.assertTrue(
          RitualMath.enchantabilityMultiplier(new ItemStack(Items.BOW)) > 1
              && RitualMath.enchantabilityMultiplier(new ItemStack(Items.BOOK)) == 1,
          "Low rating costs more; books remain neutral");
      Config.ENCHANTABILITY_EXPONENT.set(1.0);
      h.assertTrue(
          Math.abs(
                  RitualMath.enchantabilityMultiplier(new ItemStack(Items.GOLDEN_SWORD))
                      - 10.0 / 22)
              < 1e-8,
          "Exponent is configurable");
      Config.ENCHANTABILITY.set(false);
      h.assertTrue(
          RitualMath.enchantabilityMultiplier(new ItemStack(Items.GOLDEN_SWORD)) == 1,
          "Scaling can be disabled");
    } finally {
      Config.ENCHANTABILITY.set(enabled);
      Config.ENCHANTABILITY_EXPONENT.set(exponent);
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void defaultFullMaterialSetExactlyReachesVanillaMaximum(GameTestHelper h) {
    var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    for (var definition : Definitions.SERVER.enchantments().values()) {
      var entries =
          definition.materials().stream()
              .map(Affinity::id)
              .collect(java.util.stream.Collectors.toSet());
      Map<ResourceLocation, Double> powers = new HashMap<>();
      for (var affinity : definition.materials())
        affinity
            .item()
            .ifPresent(
                id ->
                    powers.put(
                        id,
                        RitualMath.base(
                            definition, entries, new ItemStack(BuiltInRegistries.ITEM.get(id)))));
      double total = powers.values().stream().mapToDouble(Double::doubleValue).sum();
      int maximum = registry.get(definition.enchantment()).getMaxLevel();
      h.assertTrue(
          Math.abs(total - definition.required(maximum)) < 1e-8,
          definition.enchantment() + ": complete distinct set reaches vanilla maximum");
      for (double power : powers.values())
        h.assertTrue(
            total - power < definition.required(maximum),
            "Omitting any material falls short of vanilla maximum");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void chestModifierRecognizesContextAndPreservesNonChestBooks(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0), 10))) {
      var book = RitualMath.apply(r.player, new ItemStack(Items.BOOK), Map.of(SHARP, 2));
      var params =
          new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
              .withParameter(
                  net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                  Vec3.atCenterOf(r.table))
              .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
      var modifier =
          new DiscoveryLoot(
              new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0], 0);
      for (boolean chest : List.of(false, true)) {
        var context =
            new net.minecraft.world.level.storage.loot.LootContext.Builder(params)
                .withQueriedLootTableId(
                    id(chest ? "chests/simple_dungeon" : "gameplay/fishing/treasure"))
                .create(Optional.empty());
        var output =
            modifier.apply(
                new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(List.of(book.copy())), context);
        h.assertTrue(
            output.size() == 1
                && (chest
                    ? output.getFirst().is(RitualsNotRolls.PAGE)
                    : ItemStack.matches(book, output.getFirst())),
            "Conversion is restricted to chest loot");
      }
      h.getLevel().setBlock(r.table, Blocks.CHEST.defaultBlockState(), 3);
      var context =
          new net.minecraft.world.level.storage.loot.LootContext.Builder(params)
              .withQueriedLootTableId(id("custom_structure/treasure"))
              .create(Optional.empty());
      h.assertTrue(
          modifier
              .apply(
                  new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(List.of(book.copy())),
                  context)
              .getFirst()
              .is(RitualsNotRolls.PAGE),
          "Container-origin contexts support custom loot-table paths");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void equalPowerReplacementStillCancelsCapturedResources(GameTestHelper h) {
    try (var r =
        new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 20.0, Items.EMERALD, 20.0), 10))) {
      r.books(3, 0, SHARP);
      var p = r.pedestal(1, 2, Items.DIAMOND, true, false);
      var entity = r.drop(new ItemStack(Items.DIAMOND_SWORD));
      var plan = r.plan(entity.getItem());
      p.items.setStackInSlot(0, new ItemStack(Items.EMERALD));
      h.assertTrue(
          !RitualEngine.commit(
                      h.getLevel(), r.table, r.player, entity, entity.getItem().copy(), plan)
                  .isEmpty()
              && p.items.getStackInSlot(0).is(Items.EMERALD)
              && RitualMath.enchantments(entity.getItem()).isEmpty(),
          "A different item cannot replace a reserved material just because its power matches");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void mixedDirectionsDoNotGrantUntraveledReturnBonuses(GameTestHelper h) {
    try (var r =
        new Room(
            h, definition(SHARP, Map.of(Items.DIAMOND, 40.0, Items.IRON_INGOT, 10.0), 10, 35))) {
      r.books(-6, 0, SHARP);
      var positive = r.pedestal(2, 0, Items.DIAMOND, false, false);
      r.pedestal(4, 0, Items.IRON_INGOT, false, true);
      var plan = r.plan(new ItemStack(Items.DIAMOND_SWORD));
      h.assertTrue(
          plan.selected().equals(Map.of(SHARP, 1))
              && power(plan, SHARP) == 30
              && plan.chains().getFirst().visits(positive.getBlockPos()) == 1,
          "A separate reverse flow cannot give the positive route a second visit");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void incomingPowerOffsetsPartialRemovalAndItsSurvivingRing(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 20.0, Items.IRON_INGOT, 50.0), 10, 30, 60))) {
      r.books(3, 0, SHARP);
      r.pedestal(1, 2, Items.DIAMOND, false, false);
      r.pedestal(-2, -2, Items.IRON_INGOT, false, true);
      var plan = r.plan(r.enchanted(SHARP, 3));
      h.assertTrue(
          plan.selected().equals(Map.of(SHARP, 2))
              && power(plan, SHARP) == -30
              && plan.remainingFractions().get(SHARP) == .375,
          "60 existing + 20 incoming - 50 removed leaves 30 of an 80-power budget");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void failedPreviewUsesNativeMaximumAndDoesNotDiluteSuccess(GameTestHelper h) {
    try (var r =
        new Room(
            h,
            definition(SHARP, Map.of(Items.DIAMOND, 40.0), 10, 20, 30, 40, 100, 1000),
            definition(LOOT, Map.of(Items.DIAMOND, 5.0), 30, 50, 80))) {
      r.books(3, 0, SHARP, LOOT);
      r.pedestal(1, 2, Items.DIAMOND, true, false);
      var a = RitualAttempt.create(r.player, new ItemStack(Items.DIAMOND_SWORD), r.network());
      h.assertTrue(
          a.failing().equals(Set.of(LOOT)), "Insufficient shared enchant previews failure");
      h.assertTrue(
          a.nativeCosts().get(SHARP) == 100, "Native Sharpness V baseline, not configured VI");
      h.assertTrue(
          a.successful().users().values().stream().allMatch(ids -> ids.equals(Set.of(SHARP))),
          "Failure never takes allocated power from success");
      h.assertTrue(power(a.successful(), SHARP) >= 80, "Success retains unsplit power");
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void reservedOfferingRecoveryRunsOnlyOnce(GameTestHelper h) {
    try (var r = new Room(h, definition(SHARP, Map.of(Items.DIAMOND, 40.0), 10, 30, 60))) {
      r.books(3, 0, SHARP);
      var p = r.pedestal(1, 2, Items.DIAMOND, true, false);
      var item = new ItemStack(Items.DIAMOND_SWORD);
      var target = r.drop(item);
      var plan = r.plan(item);
      var escrow = new RitualConsumption();
      h.assertTrue(escrow.take(h.getLevel(), target, plan, p.getBlockPos()).isEmpty(), "Reserved");
      var saved = target.getPersistentData().copy();
      var restored =
          new ItemEntity(h.getLevel(), target.getX(), target.getY(), target.getZ(), item.copy());
      restored.getPersistentData().merge(saved);
      var area = new net.minecraft.world.phys.AABB(r.table).inflate(10);
      int before =
          h.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
              .filter(e -> e.getItem().is(Items.DIAMOND))
              .mapToInt(e -> e.getItem().getCount())
              .sum();
      RitualConsumption.recover(restored);
      RitualConsumption.recover(restored);
      int after =
          h.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
              .filter(e -> e.getItem().is(Items.DIAMOND))
              .mapToInt(e -> e.getItem().getCount())
              .sum();
      h.assertTrue(
          after == before + 1 && !restored.getPersistentData().contains(RitualConsumption.KEY),
          "Disk recovery refunds exactly once");
      escrow.finish(target);
    }
    h.succeed();
  }

  @GameTest(template = "network")
  public static void continuousRouteParticlePacketRoundTrip(GameTestHelper h) {
    var c =
        RitualSpline.through(
            List.of(new Vec3(4, 65, 0), new Vec3(2, 65, 4), new Vec3(0, 66, 0)), false);
    var f =
        new RitualParticleOptions.Flight(
            c.end(),
            Vec3.ZERO,
            c.duration(),
            180,
            true,
            .3f,
            -1,
            4,
            false,
            1.3f,
            Optional.of(c),
            new RitualInstability(.7f, 12, 50, 80, 130));
    var effect = new RitualParticleOptions(id("enchant"), 0xaabbcc).flying(f);
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      RitualParticleOptions.STREAM.encode(buffer, effect);
      h.assertTrue(
          effect.equals(RitualParticleOptions.STREAM.decode(buffer)),
          "Complete spline and failure clock round-trip on wire");
    } finally {
      buffer.release();
    }
    h.succeed();
  }
}
