package com.cappleapple.ritualsnotrolls;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.*;

public final class DiscoveryLoot extends LootModifier {
  public static final MapCodec<DiscoveryLoot> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              codecStart(i)
                  .and(
                      Codec.doubleRange(0, 1)
                          .optionalFieldOf("chance", 0.0)
                          .forGetter(m -> m.chance))
                  .apply(i, DiscoveryLoot::new));
  private final double chance;

  public DiscoveryLoot(LootItemCondition[] conditions, double chance) {
    super(conditions);
    this.chance = chance;
  }

  @Override
  protected ObjectArrayList<ItemStack> doApply(
      ObjectArrayList<ItemStack> loot, LootContext context) {
    var origin = context.getParamOrNull(LootContextParams.ORIGIN);
    boolean chest =
        context.getQueriedLootTableId().getPath().startsWith("chests/")
            || origin != null
                && context.getLevel().getBlockEntity(net.minecraft.core.BlockPos.containing(origin))
                    instanceof
                    net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
    var converted = replaceBooks(loot, context.getRandom());
    loot.clear();
    loot.addAll(converted);
    // The optional bonus page remains a chest-only addition; conversion applies to every table.
    if (chest
        && chance > 0
        && context.getRandom().nextDouble() < chance
        && !Definitions.SERVER.enchantments().isEmpty()) {
      var all = new ArrayList<>(Definitions.SERVER.enchantments().values());
      var definition = all.get(context.getRandom().nextInt(all.size()));
      loot.add(
          Knowledge.page(
              definition.enchantment(),
              definition
                  .materials()
                  .get(context.getRandom().nextInt(definition.materials().size()))
                  .id()));
    }
    return loot;
  }

  public static java.util.List<ItemStack> replaceBooks(
      java.util.List<ItemStack> loot, RandomSource random) {
    java.util.List<ItemStack> result = new ArrayList<>();
    for (var stack : loot) {
      if (!stack.is(Items.ENCHANTED_BOOK)) {
        result.add(stack);
        continue;
      }
      var stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
      var retained = new ItemEnchantments.Mutable(stored);
      boolean replaced = false;
      for (var holder : stored.keySet()) {
        var id = holder.unwrapKey().orElseThrow().location();
        var definition = Definitions.SERVER.get(id);
        if (definition == null || definition.materials().isEmpty()) continue;
        for (int i = 0; i < stack.getCount(); i++)
          result.add(
              Knowledge.page(
                  id,
                  definition.materials().get(random.nextInt(definition.materials().size())).id()));
        retained.set(holder, 0);
        replaced = true;
      }
      if (!replaced) result.add(stack);
      else if (!retained.toImmutable().isEmpty()) {
        var remaining = stack.copy();
        remaining.set(DataComponents.STORED_ENCHANTMENTS, retained.toImmutable());
        result.add(remaining);
      }
    }
    return java.util.List.copyOf(result);
  }

  @Override
  public MapCodec<? extends IGlobalLootModifier> codec() {
    return CODEC;
  }

  /** Catch direct world drops from mods too, while leaving player-thrown ritual targets intact. */
  public static void replaceDrop(net.minecraft.world.entity.item.ItemEntity entity) {
    if (!entity.getItem().is(Items.ENCHANTED_BOOK)
        || entity.getOwner() instanceof net.minecraft.world.entity.player.Player) return;
    var original = entity.getItem();
    var converted = replaceBooks(java.util.List.of(original), entity.level().getRandom());
    if (converted.size() == 1 && converted.getFirst() == original) return;
    // Preserve pickup delay, age, motion and ownership on every replacement.
    var saved = entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    saved.remove("UUID");
    entity.setItem(converted.getFirst());
    for (int i = 1; i < converted.size(); i++) {
      var extra =
          new net.minecraft.world.entity.item.ItemEntity(
              entity.level(), entity.getX(), entity.getY(), entity.getZ(), converted.get(i));
      extra.load(saved);
      extra.setItem(converted.get(i));
      entity.level().addFreshEntity(extra);
    }
  }
}
