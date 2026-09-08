package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.compat.AncientBookMigration;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class MigrationGameTests {
  static CompoundTag ancient(String enchantment, int count) {
    CompoundTag item = new CompoundTag();
    item.putString("id", AncientBookMigration.OLD_ITEM);
    item.putInt("count", count);
    CompoundTag components = new CompoundTag();
    CompoundTag levels = new CompoundTag();
    levels.putInt(enchantment, 1);
    CompoundTag stored = new CompoundTag();
    stored.put("levels", levels);
    components.put("minecraft:stored_enchantments", stored);
    CompoundTag replicated = new CompoundTag();
    replicated.putBoolean("is_replicated", true);
    components.put("immersiveenchanting:replicated", replicated);
    item.put("components", components);
    return item;
  }

  private static ItemStack parse(GameTestHelper h, CompoundTag tag) {
    return ItemStack.parse(h.getLevel().registryAccess(), tag).orElseThrow();
  }

  private static void page(GameTestHelper h, ItemStack item, String enchantment, int count) {
    var knowledge = Knowledge.data(item);
    h.assertTrue(item.is(RitualsNotRolls.PAGE) && item.getCount() == count, "Converted page count");
    h.assertTrue(
        knowledge != null
            && knowledge.enchantment().toString().equals(enchantment)
            && knowledge.entries().size() == 1,
        "Exactly one affinity for the original enchantment");
    h.assertTrue(
        Definitions.SERVER.get(knowledge.enchantment()).affinity(knowledge.entries().getFirst())
            != null,
        "Affinity exists in current ritual data");
  }

  @GameTest(template = "network")
  public static void loosePageUsesVanillaBookshelfInteraction(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(22, 1, 20));
    h.setBlock(22, 1, 20, Blocks.CHISELED_BOOKSHELF);
    var p = RitualGameTests.player(h);
    var held = Knowledge.page(RitualGameTests.SHARP, "diamond");
    held.setCount(3);
    p.setItemInHand(InteractionHand.MAIN_HAND, held);
    var hit =
        new BlockHitResult(
            new Vec3(pos.getX() + .8, pos.getY() + .75, pos.getZ()), Direction.NORTH, pos, false);
    h.getLevel()
        .getBlockState(pos)
        .useItemOn(held, h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    var shelf = (ChiseledBookShelfBlockEntity) h.getLevel().getBlockEntity(pos);
    h.assertTrue(
        shelf.getItem(0).is(RitualsNotRolls.PAGE)
            && shelf.getItem(0).getCount() == 1
            && held.getCount() == 2,
        "Normal right click inserts one loose page");
    h.assertTrue(
        h.getLevel()
            .getBlockState(pos)
            .getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.getFirst()),
        "Shelf occupied visual updates");
    var table = h.absolutePos(new BlockPos(20, 1, 20));
    h.assertTrue(
        RitualNetwork.scan(h.getLevel(), table, false, true)
            .knowledge()
            .get(RitualGameTests.SHARP)
            .contains("diamond"),
        "Inserted page supplies knowledge");
    h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), p, hit);
    h.assertTrue(
        shelf.getItem(0).isEmpty()
            && !RitualNetwork.scan(h.getLevel(), table, false, false)
                .knowledge()
                .containsKey(RitualGameTests.SHARP),
        "Removing the page immediately removes knowledge even with cached topology");
    h.succeed();
  }

  @GameTest(template = "network")
  public static void loosePagesAndBooksShareRitualSources(GameTestHelper h) {
    var table = h.absolutePos(new BlockPos(20, 1, 20));
    h.setBlock(22, 1, 20, Blocks.CHISELED_BOOKSHELF);
    h.setBlock(20, 1, 22, RitualsNotRolls.PEDESTAL.get());
    var shelf =
        (ChiseledBookShelfBlockEntity)
            h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(22, 1, 20)));
    shelf.setItem(0, RitualGameTests.page("diamond"));
    shelf.setItem(1, RitualGameTests.book("diamond", "netherite_scrap"));
    var malformed = new ItemStack(RitualsNotRolls.PAGE.get());
    malformed.set(
        RitualsNotRolls.KNOWLEDGE,
        new KnowledgeData(RitualGameTests.SHARP, List.of("invalid_a", "invalid_b"), true));
    shelf.setItem(2, malformed);
    ((PedestalEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(20, 1, 22))))
        .items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
    var network = RitualNetwork.scan(h.getLevel(), table, false, true);
    h.assertTrue(
        network.knowledge().get(RitualGameTests.SHARP).equals(Set.of("diamond", "netherite_scrap")),
        "Knowledge union deduplicates page/book entries and ignores malformed pages");
    var evaluated =
        RitualMath.evaluate(
            RitualGameTests.player(h),
            new ItemStack(Items.DIAMOND_SWORD),
            Map.of(RitualGameTests.SHARP, 1),
            Set.of(),
            0,
            network);
    h.assertTrue(
        evaluated.ready() && evaluated.lines().getFirst().power() == 30,
        "Page knowledge powers the ordinary ritual without duplication");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void ancientBookConvertsBeforeMissingRegistryDecode(GameTestHelper h) {
    var raw = ancient("minecraft:mending", 16);
    var original = raw.copy();
    var migrated = parse(h, raw);
    page(h, migrated, "minecraft:mending", 16);
    h.assertTrue(raw.equals(original), "Decoder never mutates caller NBT");
    h.assertTrue(
        migrated
            .get(DataComponents.CUSTOM_DATA)
            .copyTag()
            .getCompound(AncientBookMigration.ARCHIVE)
            .equals(raw),
        "Removed-mod components and complete source stack are preserved");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void legacyNoNetworkComponentMigrates(GameTestHelper h) {
    var raw = ancient("minecraft:mending", 1);
    var components = raw.getCompound("components");
    components.remove("minecraft:stored_enchantments");
    CompoundTag legacy = new CompoundTag();
    legacy.putString("value1", "minecraft:sharpness");
    components.put("immersiveenchanting:no_network", legacy);
    page(h, parse(h, raw), "minecraft:sharpness", 1);
    // An already migrated modern component takes priority over any leftover legacy component.
    components.put(
        "minecraft:stored_enchantments",
        ancient("minecraft:mending", 1)
            .getCompound("components")
            .get("minecraft:stored_enchantments"));
    page(h, parse(h, raw), "minecraft:mending", 1);
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void migrationCoversAllPersistentItemCodecs(GameTestHelper h) {
    var raw = ancient("minecraft:sharpness", 3);
    var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    for (var codec : List.of(ItemStack.CODEC, ItemStack.STRICT_CODEC, ItemStack.OPTIONAL_CODEC))
      page(h, codec.parse(ops, raw).getOrThrow(), "minecraft:sharpness", 3);
    for (var codec : List.of(ItemStack.SINGLE_ITEM_CODEC, ItemStack.STRICT_SINGLE_ITEM_CODEC))
      page(h, codec.parse(ops, raw).getOrThrow(), "minecraft:sharpness", 1);
    var json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, raw);
    page(
        h,
        ItemStack.CODEC
            .parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
            .getOrThrow(),
        "minecraft:sharpness",
        3);
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void nestedContainerAndBundleAncientBooksLoad(GameTestHelper h) {
    var raw = ancient("minecraft:unbreaking", 2);
    CompoundTag outer = new CompoundTag();
    outer.putString("id", "minecraft:shulker_box");
    CompoundTag components = new CompoundTag();
    ListTag contents = new ListTag();
    CompoundTag slot = new CompoundTag();
    slot.putInt("slot", 4);
    CompoundTag bundle = new CompoundTag();
    bundle.putString("id", "minecraft:bundle");
    CompoundTag bundleComponents = new CompoundTag();
    ListTag bundleItems = new ListTag();
    bundleItems.add(raw);
    bundleComponents.put("minecraft:bundle_contents", bundleItems);
    bundle.put("components", bundleComponents);
    slot.put("item", bundle);
    contents.add(slot);
    components.put("minecraft:container", contents);
    outer.put("components", components);
    var box = parse(h, outer);
    var loadedBundle =
        box.get(DataComponents.CONTAINER).stream()
            .filter(s -> !s.isEmpty())
            .findFirst()
            .orElseThrow();
    page(
        h,
        loadedBundle.get(DataComponents.BUNDLE_CONTENTS).items().iterator().next(),
        "minecraft:unbreaking",
        2);
    h.assertTrue(
        ItemStack.matches(box, parse(h, (CompoundTag) box.save(h.getLevel().registryAccess()))),
        "Nested save/reload is idempotent");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void playerInventoryEnderChestAndDroppedBooksLoad(GameTestHelper h) {
    var raw = ancient("minecraft:looting", 4);
    raw.putByte("Slot", (byte) 5);
    ListTag inventory = new ListTag();
    inventory.add(raw);
    var p = RitualGameTests.player(h);
    p.getInventory().load(inventory);
    page(h, p.getInventory().getItem(5), "minecraft:looting", 4);
    p.getEnderChestInventory().fromTag(inventory, h.getLevel().registryAccess());
    page(h, p.getEnderChestInventory().getItem(5), "minecraft:looting", 4);
    var entity = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.BOOK));
    CompoundTag entityTag = new CompoundTag();
    entity.saveWithoutId(entityTag);
    entityTag.put("Item", raw);
    entity.load(entityTag);
    page(h, entity.getItem(), "minecraft:looting", 4);
    h.succeed();
  }

  @GameTest(template = "network")
  public static void legacyShelfContentsBecomeUsablePagesOnLoad(GameTestHelper h) {
    h.setBlock(22, 1, 20, Blocks.CHISELED_BOOKSHELF);
    var shelf =
        (ChiseledBookShelfBlockEntity)
            h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(22, 1, 20)));
    var nbt = shelf.saveWithFullMetadata(h.getLevel().registryAccess());
    var raw = ancient("minecraft:sharpness", 1);
    raw.putByte("Slot", (byte) 0);
    ListTag items = new ListTag();
    items.add(raw);
    nbt.put("Items", items);
    shelf.loadWithComponents(nbt, h.getLevel().registryAccess());
    var item = shelf.getItem(0);
    page(h, item, "minecraft:sharpness", 1);
    var network =
        RitualNetwork.scan(h.getLevel(), h.absolutePos(new BlockPos(20, 1, 20)), false, true);
    h.assertTrue(
        network
            .knowledge()
            .get(RitualGameTests.SHARP)
            .equals(Set.copyOf(Knowledge.data(item).entries())),
        "Previously saved ancient books immediately supply the matching page's knowledge");
    var saved = shelf.saveWithFullMetadata(h.getLevel().registryAccess());
    shelf.loadWithComponents(saved, h.getLevel().registryAccess());
    h.assertTrue(
        ItemStack.matches(item, shelf.getItem(0)), "Shelf second load keeps the selected affinity");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void unsupportedAncientBookSurvivesUntilDataIsAdded(GameTestHelper h) {
    var original = Definitions.SERVER;
    ItemStack pending;
    try {
      Definitions.SERVER =
          new Definitions.Snapshot(Map.of(), original.rules(), original.revision());
      pending = parse(h, ancient("minecraft:mending", 7));
      h.assertTrue(
          pending.is(Items.BOOK) && Knowledge.data(pending) == null,
          "Unsupported enchantment is preserved without inventing knowledge");
      h.assertTrue(
          ItemStack.matches(
              pending, parse(h, (CompoundTag) pending.save(h.getLevel().registryAccess()))),
          "Pending save/reload neither erases nor nests its archive");
    } finally {
      Definitions.SERVER = original;
    }
    // Preserve the current count, including if the player split the pending book stack.
    pending.setCount(2);
    page(
        h,
        parse(h, (CompoundTag) pending.save(h.getLevel().registryAccess())),
        "minecraft:mending",
        2);
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void malformedAndRemovedEnchantmentsKeepOriginalData(GameTestHelper h) {
    for (var raw : List.of(ancient("removedmod:unknown", 1), ancient("not a valid id", 1))) {
      var item = parse(h, raw);
      h.assertTrue(
          item.is(Items.BOOK)
              && item.get(DataComponents.CUSTOM_DATA)
                  .copyTag()
                  .getCompound(AncientBookMigration.ARCHIVE)
                  .equals(raw),
          "Unresolvable enchantment data preserved");
    }
    var multiple = ancient("minecraft:sharpness", 1);
    multiple
        .getCompound("components")
        .getCompound("minecraft:stored_enchantments")
        .getCompound("levels")
        .putInt("minecraft:mending", 1);
    h.assertTrue(
        parse(h, multiple).is(Items.BOOK), "Ambiguous multi-enchantment books are preserved whole");
    var empty = ancient("minecraft:sharpness", 1);
    empty.remove("components");
    h.assertTrue(parse(h, empty).is(Items.BOOK), "Missing enchantment does not invent a page");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void migrationKeepsNamesAndIgnoresOrdinaryItems(GameTestHelper h) {
    var raw = ancient("minecraft:mending", 1);
    raw.getCompound("components").putString("minecraft:custom_name", "{\"text\":\"Old treasure\"}");
    var page = parse(h, raw);
    h.assertTrue(page.getHoverName().getString().equals("Old treasure"), "Custom name retained");
    h.assertTrue(
        ItemStack.matches(page, parse(h, (CompoundTag) page.save(h.getLevel().registryAccess()))),
        "Converted pages never reroll or archive repeatedly");
    var ordinary = new ItemStack(Items.ENCHANTED_BOOK);
    h.assertTrue(
        ItemStack.matches(
            ordinary, parse(h, (CompoundTag) ordinary.save(h.getLevel().registryAccess()))),
        "Existing vanilla books are untouched");
    raw.getCompound("components").putString("minecraft:custom_name", "not valid json");
    page(h, parse(h, raw), "minecraft:mending", 1);
    h.succeed();
  }
}
