package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.menu.*;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class RitualGameTests {
  static final ResourceLocation SHARP = ResourceLocation.parse("minecraft:sharpness"),
      UNBREAKING = ResourceLocation.parse("minecraft:unbreaking");

  static ItemStack page(String id) {
    return Knowledge.page(SHARP, id);
  }

  static ItemStack book(String... entries) {
    return Knowledge.book(new KnowledgeData(SHARP, List.of(entries), true));
  }

  static void check(GameTestHelper h, boolean test, String reason) {
    h.assertTrue(test, reason);
  }

  static ServerPlayer player(GameTestHelper h) {
    var cookie =
        CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "RitualTest"), false);
    var p =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            cookie.gameProfile(),
            cookie.clientInformation());
    p.connection =
        new ServerGamePacketListenerImpl(
            h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p, cookie) {
          @Override
          public void send(Packet<?> packet) {}

          @Override
          public void send(Packet<?> packet, PacketSendListener listener) {}
        };
    p.setPos(Vec3At(h.absolutePos(new BlockPos(2, 1, 2))));
    return p;
  }

  static net.minecraft.world.phys.Vec3 Vec3At(BlockPos pos) {
    return net.minecraft.world.phys.Vec3.atCenterOf(pos);
  }

  @GameTest(template = "empty")
  public static void pageIdentityAndCodec(GameTestHelper h) {
    ItemStack p = page("diamond");
    var copy =
        ItemStack.parse(h.getLevel().registryAccess(), p.save(h.getLevel().registryAccess()))
            .orElseThrow();
    check(h, ItemStack.matches(p, copy), "Page component round-trip");
    check(h, Knowledge.data(p).entries().equals(List.of("diamond")), "Stable identity");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void bookCrafting(GameTestHelper h) {
    var input =
        CraftingInput.of(
            2,
            2,
            List.of(
                new ItemStack(Items.LEATHER),
                page("diamond"),
                new ItemStack(Items.LEATHER),
                new ItemStack(Items.LEATHER)));
    var r = new KnowledgeBookRecipe(CraftingBookCategory.MISC);
    check(h, r.matches(input, h.getLevel()), "Three leather + page");
    check(
        h,
        Knowledge.data(r.assemble(input, h.getLevel().registryAccess()))
            .entries()
            .equals(List.of("diamond")),
        "Initial page retained");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void bookAddAndDuplicate(GameTestHelper h) {
    var b = book();
    var p = page("diamond");
    check(h, Knowledge.add(b, p, false) && p.isEmpty(), "Added page");
    var duplicate = page("diamond");
    check(
        h,
        !Knowledge.add(b, duplicate, false) && duplicate.getCount() == 1,
        "Duplicate remains loose");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void tearIsExactlyOnce(GameTestHelper h) {
    var b = book("diamond");
    check(h, !Knowledge.tear(b, "diamond").isEmpty(), "First tear returns page");
    check(h, Knowledge.tear(b, "diamond").isEmpty(), "Repeated tear cannot duplicate");
    check(h, Knowledge.data(b).entries().isEmpty(), "Entry removed");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void autoAddEnabled(GameTestHelper h) {
    var p = player(h);
    p.getInventory().setItem(0, book());
    var incoming = page("diamond");
    check(
        h, p.getInventory().add(incoming) && incoming.isEmpty(), "Inventory.add absorbs incoming");
    check(
        h, Knowledge.data(p.getInventory().getItem(0)).entries().size() == 1, "Book gained entry");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void autoAddDisabled(GameTestHelper h) {
    var p = player(h);
    var b = book();
    b.set(RitualsNotRolls.KNOWLEDGE, Knowledge.data(b).toggle());
    p.getInventory().setItem(0, b);
    p.getInventory().add(page("diamond"));
    check(h, Knowledge.data(b).entries().isEmpty(), "Disabled book stays empty");
    check(h, p.getInventory().contains(page("diamond")), "Page is loose");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void manualInventoryMoveStaysLoose(GameTestHelper h) {
    var p = player(h);
    var b = book();
    p.getInventory().setItem(0, b);
    p.getInventory().setItem(1, page("diamond"));
    var m = p.inventoryMenu;
    m.clicked(37, 0, ClickType.PICKUP, p);
    m.clicked(38, 0, ClickType.PICKUP, p);
    check(h, Knowledge.data(b).entries().isEmpty(), "Manual rearrange is not acquisition");
    check(h, p.getInventory().getItem(2).is(RitualsNotRolls.PAGE), "Page moved normally");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void pickupAbsorbs(GameTestHelper h) {
    var p = player(h);
    var b = book();
    p.getInventory().setItem(0, b);
    var e = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), page("diamond"));
    e.setNoPickUpDelay();
    h.getLevel().addFreshEntity(e);
    e.playerTouch(p);
    check(h, Knowledge.data(b).entries().contains("diamond"), "World pickup absorbed");
    check(h, !e.isAlive(), "Pickup removed the page entity");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void tornOverflowPickupStaysLoose(GameTestHelper h) {
    var p = player(h);
    var b = book();
    p.getInventory().setItem(0, b);
    var e = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), page("diamond"));
    e.getPersistentData().putBoolean("ritualsnotrolls_loose_page", true);
    e.setNoPickUpDelay();
    e.playerTouch(p);
    check(h, Knowledge.data(b).entries().isEmpty(), "Intentional loose-page pickup");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void shiftTransferAbsorbs(GameTestHelper h) {
    var p = player(h);
    var b = book();
    p.getInventory().setItem(0, b);
    var chest = new SimpleContainer(27);
    chest.setItem(0, page("diamond"));
    var m = new ChestMenu(MenuType.GENERIC_9x3, 12, p.getInventory(), chest, 3);
    m.clicked(0, 0, ClickType.QUICK_MOVE, p);
    check(
        h,
        Knowledge.data(b).entries().contains("diamond") && chest.getItem(0).isEmpty(),
        "External shift transfer absorbed");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void shiftWithinInventoryStaysLoose(GameTestHelper h) {
    var p = player(h);
    var b = book();
    p.getInventory().setItem(0, b);
    p.getInventory().setItem(1, page("diamond"));
    p.inventoryMenu.clicked(37, 0, ClickType.QUICK_MOVE, p);
    check(h, Knowledge.data(b).entries().isEmpty(), "Internal shift is rearrangement");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void doubleClickGathersOnlyNewEntries(GameTestHelper h) {
    var p = player(h);
    var b = book("diamond");
    var chest = new SimpleContainer(27);
    chest.setItem(0, page("flint"));
    chest.setItem(1, page("diamond"));
    chest.setItem(2, Knowledge.page(UNBREAKING, "diamond"));
    var m = new ChestMenu(MenuType.GENERIC_9x3, 13, p.getInventory(), chest, 3);
    m.setCarried(b);
    m.clicked(0, 0, ClickType.PICKUP_ALL, p);
    check(
        h,
        Knowledge.data(b).entries().size() == 2 && chest.getItem(0).isEmpty(),
        "Collected matching page");
    check(
        h,
        chest.getItem(1).getCount() == 1 && chest.getItem(2).getCount() == 1,
        "Duplicates and unrelated pages untouched");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void itemHandlerSimulationAndCommit(GameTestHelper h) {
    var p = player(h);
    var b = book();
    p.getInventory().setItem(0, b);
    var handler = new PlayerInvWrapper(p.getInventory());
    var incoming = page("diamond");
    check(h, handler.insertItem(1, incoming, true).isEmpty(), "Simulation accepts");
    check(
        h, Knowledge.data(b).entries().isEmpty() && incoming.getCount() == 1, "Simulation is pure");
    check(h, handler.insertItem(1, incoming, false).isEmpty(), "Commit accepts");
    check(
        h,
        Knowledge.data(b).entries().size() == 1 && incoming.getCount() == 1,
        "Input stack is not mutated");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void totalPageCountAndTagAffinity(GameTestHelper h) {
    var d = Definitions.SERVER.get(SHARP);
    check(
        h,
        Knowledge.total(Knowledge.data(book("diamond")), false) == d.materials().size(),
        "Current datapack page total");
    check(
        h,
        d.affinity("amethyst_group").matches(new ItemStack(Items.AMETHYST_SHARD)),
        "NeoForge tag affinity resolves");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void materialPowerIsPerEnchantment(GameTestHelper h) {
    var iron = new ItemStack(Items.IRON_INGOT);
    double sharp = RitualMath.base(Definitions.SERVER.get(SHARP), Set.of("iron_ingot"), iron),
        knock =
            RitualMath.base(
                Definitions.SERVER.get(ResourceLocation.parse("minecraft:knockback")),
                Set.of("iron_ingot"),
                iron);
    check(h, sharp == 8 && knock == 24, "Same item has independent affinity powers");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void conflictingCostAndBeyondVanilla(GameTestHelper h) {
    var p = player(h);
    var target = new ItemStack(Items.DIAMOND_SWORD);
    Map<ResourceLocation, Integer> selected = new LinkedHashMap<>();
    selected.put(SHARP, 6);
    selected.put(ResourceLocation.parse("minecraft:smite"), 2);
    selected.put(ResourceLocation.parse("minecraft:bane_of_arthropods"), 1);
    var cost = RitualMath.conflicts(target, selected, Definitions.SERVER);
    check(
        h,
        new ArrayList<>(cost.values()).equals(List.of(1.0, 2.0, 4.0)),
        "Ordered exponential conflict math");
    var result = RitualMath.apply(p, target, selected);
    check(
        h,
        RitualMath.enchantments(result)
                .getLevel(
                    p.registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT)
                        .getHolder(SHARP)
                        .orElseThrow())
            == 6,
        "Vanilla maximum does not clamp ritual");
    check(h, RitualMath.enchantments(result).size() == 3, "Conflicting enchantments coexist");
    h.succeed();
  }

  private record Fixture(
      ServerPlayer player,
      BlockPos table,
      PedestalEntity pedestal,
      ChiseledBookShelfBlockEntity shelf) {}

  private static Fixture fixture(GameTestHelper h) {
    h.setBlock(20, 1, 20, Blocks.ENCHANTING_TABLE);
    h.setBlock(22, 1, 20, Blocks.CHISELED_BOOKSHELF);
    h.setBlock(20, 1, 22, RitualsNotRolls.PEDESTAL.get());
    BlockPos table = h.absolutePos(new BlockPos(20, 1, 20));
    var p = player(h);
    p.setPos(Vec3At(table));
    var shelf =
        (ChiseledBookShelfBlockEntity)
            h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(22, 1, 20)));
    shelf.setItem(0, book("diamond"));
    var pedestal =
        (PedestalEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(20, 1, 22)));
    pedestal.items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
    RitualNetwork.invalidate(h.getLevel());
    return new Fixture(p, table, pedestal, shelf);
  }

  @GameTest(template = "network")
  public static void bookshelfAndPedestalDiscovery(GameTestHelper h) {
    var f = fixture(h);
    var n = RitualNetwork.scan(h.getLevel(), f.table, false, true);
    check(h, n.knowledge().get(SHARP).contains("diamond"), "Nearby chiseled shelf knowledge");
    check(h, n.pedestals().size() == 1, "Pedestal detected");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void inventoryKnowledgeDoesNotCount(GameTestHelper h) {
    var f = fixture(h);
    f.shelf.setItem(0, ItemStack.EMPTY);
    f.player.getInventory().setItem(0, book("diamond"));
    var e =
        RitualMath.evaluate(
            f.player,
            new ItemStack(Items.DIAMOND_SWORD),
            Map.of(SHARP, 1),
            Set.of(),
            0,
            RitualNetwork.scan(h.getLevel(), f.table, false, true));
    check(
        h,
        !e.ready() && e.lines().getFirst().power() == 0,
        "Player books do not grant ritual knowledge");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void duplicatePedestalsDoNotDoublePower(GameTestHelper h) {
    var f = fixture(h);
    h.setBlock(21, 1, 22, RitualsNotRolls.PEDESTAL.get());
    ((PedestalEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(21, 1, 22))))
        .items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
    var e =
        RitualMath.evaluate(
            f.player,
            new ItemStack(Items.DIAMOND_SWORD),
            Map.of(SHARP, 2),
            Set.of(),
            0,
            RitualNetwork.scan(h.getLevel(), f.table, false, true));
    check(h, e.lines().getFirst().power() == 30 && !e.ready(), "One actual diamond ID only");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void weakSharedAffinityDoesNotTakePower(GameTestHelper h) {
    var f = fixture(h);
    f.shelf.setItem(1, Knowledge.book(new KnowledgeData(UNBREAKING, List.of("diamond"), true)));
    var plan =
        RitualMath.automatic(
            f.player,
            new ItemStack(Items.DIAMOND_SWORD),
            RitualNetwork.scan(h.getLevel(), f.table, false, true));
    check(
        h,
        plan.evaluation().ready()
            && plan.selected().keySet().equals(Set.of(SHARP))
            && plan.evaluation().lines().getFirst().power() == 30,
        "Weak Unbreaking affinity does not steal Sharpness power");
    h.succeed();
  }

  private static ItemEntity entity(GameTestHelper h, Fixture f) {
    var e =
        new ItemEntity(
            h.getLevel(),
            f.table.getX() + .5,
            f.table.getY() + 1,
            f.table.getZ() + .5,
            new ItemStack(Items.DIAMOND_SWORD));
    h.getLevel().addFreshEntity(e);
    return e;
  }

  @GameTest(template = "network")
  public static void nonconsumingCommit(GameTestHelper h) {
    var f = fixture(h);
    var e = entity(h, f);
    String reason =
        RitualEngine.commit(
            h.getLevel(), f.table, f.player, e, e.getItem().copy(), Map.of(SHARP, 1), Set.of(), 0);
    check(h, reason.isEmpty(), reason);
    check(h, f.pedestal.items.getStackInSlot(0).getCount() == 1, "Normal material retained");
    check(h, RitualMath.enchantments(e.getItem()).size() == 1, "Target enchanted");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void consumingCommit(GameTestHelper h) {
    var f = fixture(h);
    f.pedestal.items.setStackInSlot(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    var e = entity(h, f);
    String reason =
        RitualEngine.commit(
            h.getLevel(),
            f.table,
            f.player,
            e,
            e.getItem().copy(),
            Map.of(SHARP, 2),
            Set.of(ResourceLocation.parse("minecraft:diamond")),
            0);
    check(h, reason.isEmpty(), reason);
    check(
        h, f.pedestal.items.getStackInSlot(0).isEmpty(), "Sacrifice removed exactly one material");
    check(h, !f.pedestal.items.getStackInSlot(1).isEmpty(), "Catalyst is reusable");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void failedRitualDoesNotSpend(GameTestHelper h) {
    var f = fixture(h);
    f.player.giveExperiencePoints(200);
    int xp = RitualMath.experiencePoints(f.player);
    f.player.getInventory().setItem(0, new ItemStack(RitualsNotRolls.XP_CATALYST.get()));
    var e = entity(h, f);
    String reason =
        RitualEngine.commit(
            h.getLevel(),
            f.table,
            f.player,
            e,
            e.getItem().copy(),
            Map.of(SHARP, 6),
            Set.of(),
            100);
    check(h, !reason.isEmpty(), "Insufficient power fails");
    check(
        h,
        RitualMath.experiencePoints(f.player) == xp
            && !f.pedestal.items.getStackInSlot(0).isEmpty()
            && RitualMath.enchantments(e.getItem()).isEmpty(),
        "Failure spends nothing");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void missingCatalystPreventsSpending(GameTestHelper h) {
    var f = fixture(h);
    var e = entity(h, f);
    String reason =
        RitualEngine.commit(
            h.getLevel(),
            f.table,
            f.player,
            e,
            e.getItem().copy(),
            Map.of(SHARP, 2),
            Set.of(ResourceLocation.parse("minecraft:diamond")),
            0);
    check(
        h,
        !reason.isEmpty() && !f.pedestal.items.getStackInSlot(0).isEmpty(),
        "Consumption needs catalyst");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void finalValidationSeesRemovedKnowledge(GameTestHelper h) {
    var f = fixture(h);
    var e = entity(h, f);
    var n = RitualNetwork.scan(h.getLevel(), f.table, false, false);
    check(h, !n.knowledge().isEmpty(), "Knowledge cached");
    f.shelf.setItem(0, ItemStack.EMPTY);
    check(
        h,
        !RitualEngine.commit(
                h.getLevel(),
                f.table,
                f.player,
                e,
                e.getItem().copy(),
                Map.of(SHARP, 1),
                Set.of(),
                0)
            .isEmpty(),
        "Commit rereads shelf contents");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void duplicateCommitRejected(GameTestHelper h) {
    var f = fixture(h);
    var e = entity(h, f);
    var target = e.getItem().copy();
    check(
        h,
        RitualEngine.commit(
                h.getLevel(), f.table, f.player, e, target, Map.of(SHARP, 1), Set.of(), 0)
            .isEmpty(),
        "First commit");
    check(
        h,
        !RitualEngine.commit(
                h.getLevel(), f.table, f.player, e, target, Map.of(SHARP, 1), Set.of(), 0)
            .isEmpty(),
        "Repeated request cannot execute again");
    h.succeed();
  }
}
