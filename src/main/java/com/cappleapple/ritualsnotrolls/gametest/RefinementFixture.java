package com.cappleapple.ritualsnotrolls.gametest;

import static net.minecraft.commands.Commands.*;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class RefinementFixture {
  static final BlockPos TABLE = new BlockPos(0, 65, 0),
      SUPP = new BlockPos(-4, 65, 0),
      IRON = new BlockPos(-1, 65, 3),
      WEAK = new BlockPos(2, 65, 3);

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent e) {
    if (!Boolean.getBoolean("ritualsnotrolls.refinementFixture")) return;
    e.getDispatcher()
        .register(
            literal("ritualrefine")
                .requires(s -> s.hasPermission(2))
                .then(
                    literal("setup")
                        .executes(
                            c -> {
                              setup(c.getSource().getPlayerOrException(), false);
                              return 1;
                            }))
                .then(
                    literal("start")
                        .executes(
                            c -> {
                              start(c.getSource().getPlayerOrException(), false);
                              return 1;
                            }))
                .then(
                    literal("failure")
                        .executes(
                            c -> {
                              setup(c.getSource().getPlayerOrException(), true);
                              start(c.getSource().getPlayerOrException(), true);
                              return 1;
                            }))
                .then(
                    literal("subtract")
                        .executes(
                            c -> {
                              setup(c.getSource().getPlayerOrException(), false);
                              var p = c.getSource().getPlayerOrException();
                              RitualApi.pedestal(p.serverLevel(), WEAK).extractItem(0, 1, false);
                              drop(
                                  p,
                                  RitualMath.apply(
                                      p,
                                      new ItemStack(Items.DIAMOND_SWORD),
                                      Map.of(
                                          ResourceLocation.withDefaultNamespace("sharpness"), 5)));
                              return 1;
                            })));
  }

  static void setup(ServerPlayer p, boolean failure) {
    var level = p.serverLevel();
    RitualEngine.clear();
    for (int x = -8; x <= 8; x++)
      for (int z = -8; z <= 8; z++) {
        level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
        for (int y = 65; y <= 69; y++)
          level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
      }
    level
        .getEntitiesOfClass(ItemEntity.class, new AABB(TABLE).inflate(15))
        .forEach(net.minecraft.world.entity.Entity::discard);
    level.setBlock(TABLE, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
    var book = new BlockPos(-6, 65, 0);
    level.setBlock(book, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
    var shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(book);
    if (!failure) shelf.setItem(0, RitualGameTests.book("diamond", "netherite_scrap"));
    shelf.setItem(
        1,
        Knowledge.book(
            new KnowledgeData(
                ResourceLocation.withDefaultNamespace("looting"), List.of("gold_nugget"), true)));
    if (!failure) {
      pedestal(level, SUPP, "supplementaries:pedestal", Items.DIAMOND, true);
      pedestal(level, IRON, "irons_spellbooks:pedestal", Items.NETHERITE_SCRAP, true);
    }
    pedestal(level, WEAK, "ritualsnotrolls:pedestal", Items.GOLD_NUGGET, false);
    p.getInventory().clearContent();
    p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    p.connection.teleport(.5, 68, -7, 0, 25);
    level.setDayTime(1000);
    level.setWeatherParameters(6000, 0, false, false);
  }

  static void pedestal(ServerLevel level, BlockPos pos, String id, Item item, boolean subtraction) {
    level.setBlock(
        pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(), 3);
    var h = RitualApi.pedestal(level, pos);
    h.insertItem(0, new ItemStack(item), false);
    h.insertItem(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()), false);
    if (subtraction)
      h.insertItem(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()), false);
  }

  static void start(ServerPlayer p, boolean failure) {
    if (!failure)
      for (var pos : List.of(SUPP, IRON))
        RitualApi.pedestal(p.serverLevel(), pos).extractItem(2, 1, false);
    drop(p, new ItemStack(Items.DIAMOND_SWORD));
  }

  static void drop(ServerPlayer p, ItemStack item) {
    var entity = new ItemEntity(p.serverLevel(), .5, 66, .5, item);
    entity.setThrower(p);
    entity.setPickUpDelay(20);
    p.serverLevel().addFreshEntity(entity);
    if (!RitualEngine.tryStart(p.serverLevel(), TABLE, entity))
      throw new IllegalStateException("Fixture failed capture");
  }
}
