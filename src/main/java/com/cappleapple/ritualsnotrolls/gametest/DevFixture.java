package com.cappleapple.ritualsnotrolls.gametest;

import static net.minecraft.commands.Commands.*;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Opt-in development fixture; excluded from the artifact. */
@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class DevFixture {
  private static final BlockPos TABLE = new BlockPos(0, 64, 0);

  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    if (!Boolean.getBoolean("ritualsnotrolls.devFixture")) return;
    event
        .getDispatcher()
        .register(
            literal("ritualtest")
                .requires(s -> s.hasPermission(2))
                .then(
                    literal("subtract")
                        .executes(
                            c -> {
                              subtraction(c.getSource().getPlayerOrException());
                              return 1;
                            }))
                .then(
                    literal("pages")
                        .executes(
                            c -> {
                              pages(c.getSource().getPlayerOrException());
                              return 1;
                            }))
                .then(
                    literal("setup")
                        .executes(
                            c -> {
                              setup(c.getSource().getPlayerOrException());
                              return 1;
                            }))
                .then(
                    literal("book")
                        .executes(
                            c -> {
                              var p = c.getSource().getPlayerOrException();
                              p.getInventory().selected = 0;
                              p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
                              return 1;
                            }))
                .then(
                    literal("ritual")
                        .executes(
                            c -> {
                              var p = c.getSource().getPlayerOrException();
                              p.openMenu(
                                  new SimpleMenuProvider(
                                      (id, inv, player) -> new RitualMenu(id, inv, TABLE),
                                      Component.literal("Ritual Enchanting")),
                                  buf -> buf.writeBlockPos(TABLE));
                              var m = (RitualMenu) p.containerMenu;
                              m.sync();
                              return 1;
                            })));
  }

  private static void subtraction(ServerPlayer player) {
    var level = player.serverLevel();
    level
        .getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(TABLE).inflate(12))
        .forEach(net.minecraft.world.entity.Entity::discard);
    for (int x = -8; x <= 8; x++)
      for (int z = -8; z <= 8; z++)
        for (int y = 0; y <= 4; y++)
          level.setBlock(TABLE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
    level.setBlock(TABLE, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
    var shelfPos = TABLE.offset(2, 0, 0);
    level.setBlock(shelfPos, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
    var sharp = ResourceLocation.withDefaultNamespace("sharpness");
    ((ChiseledBookShelfBlockEntity) level.getBlockEntity(shelfPos))
        .setItem(
            0,
            Knowledge.book(new KnowledgeData(sharp, List.of("diamond", "netherite_scrap"), true)));
    for (int x : new int[] {-3, -5}) {
      var pos = TABLE.offset(x, 0, 0);
      level.setBlock(pos, RitualsNotRolls.PEDESTAL.get().defaultBlockState(), 3);
      var be = (PedestalEntity) level.getBlockEntity(pos);
      be.items.setStackInSlot(0, new ItemStack(x == -3 ? Items.DIAMOND : Items.NETHERITE_SCRAP));
      be.items.setStackInSlot(
          1, x == -3 ? new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()) : ItemStack.EMPTY);
      be.items.setStackInSlot(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()));
    }
    level
        .getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(TABLE).inflate(12))
        .forEach(net.minecraft.world.entity.Entity::discard);
    player.getInventory().clearContent();
    player
        .getInventory()
        .setItem(
            1,
            com.cappleapple.ritualsnotrolls.ritual.RitualMath.apply(
                player, new ItemStack(Items.DIAMOND_SWORD), Map.of(sharp, 5)));
    player.getInventory().selected = 1;
    player.connection.teleport(.5, 64, -1.5, 0, 15);
    player.getInventory().setChanged();
  }

  private static void pages(ServerPlayer player) {
    var level = player.serverLevel();
    var page = Knowledge.page(ResourceLocation.withDefaultNamespace("sharpness"), "diamond");
    for (int x : new int[] {0, 2}) {
      var pos = new BlockPos(x, 64, 8);
      level.setBlock(pos, RitualsNotRolls.PEDESTAL.get().defaultBlockState(), 3);
      var be = (PedestalEntity) level.getBlockEntity(pos);
      be.items.setStackInSlot(0, x == 0 ? page.copy() : new ItemStack(Items.PAPER));
      be.items.setStackInSlot(1, ItemStack.EMPTY);
    }
    var drop = new net.minecraft.world.entity.item.ItemEntity(level, .5, 64, 6.3, page.copy());
    drop.setNeverPickUp();
    level.addFreshEntity(drop);
    player.connection.teleport(.5, 64, 5.2, 0, 10);
  }

  private static void setup(ServerPlayer p) {
    var level = p.serverLevel();
    level.setDayTime(6000);
    level.setWeatherParameters(6000, 0, false, false);
    level
        .getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(TABLE).inflate(12))
        .forEach(net.minecraft.world.entity.Entity::discard);
    for (int x = -8; x <= 8; x++)
      for (int z = -8; z <= 8; z++)
        level.setBlock(new BlockPos(x, 63, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    // Reset prior fixture variants, including subtraction pedestals outside the display grid.
    for (int x = -8; x <= 8; x++)
      for (int z = -8; z <= 8; z++)
        for (int y = 0; y <= 4; y++)
          level.setBlock(TABLE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
    level.setBlock(TABLE, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
    level.getBlockEntity(TABLE).setData(RitualsNotRolls.ASSEMBLY, 5);
    level.setBlock(TABLE.offset(2, 0, 0), Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
    var shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(TABLE.offset(2, 0, 0));
    String[] enchantments = {
      "sharpness", "unbreaking", "fire_aspect", "looting", "smite", "bane_of_arthropods"
    };
    for (int i = 0; i < enchantments.length; i++) {
      var id = ResourceLocation.parse("minecraft:" + enchantments[i]);
      var d = Definitions.SERVER.get(id);
      shelf.setItem(
          i,
          Knowledge.book(
              new KnowledgeData(id, d.materials().stream().map(a -> a.id()).toList(), true)));
    }
    Item[] items = {
      Items.DIAMOND,
      Items.NETHERITE_SCRAP,
      Items.AMETHYST_SHARD,
      Items.IRON_INGOT,
      Items.BLAZE_ROD,
      Items.NETHER_STAR,
      Items.EMERALD,
      Items.FLINT
    };
    Direction[] facings = {
      Direction.NORTH,
      Direction.UP,
      Direction.DOWN,
      Direction.EAST,
      Direction.WEST,
      Direction.SOUTH,
      Direction.UP,
      Direction.DOWN
    };
    for (int i = 0; i < items.length; i++) {
      // Clear the previous low ceiling fixture and give the held item actual air below the lip.
      if (facings[i] == Direction.DOWN)
        level.setBlock(
            TABLE.offset(-3 + (i % 4) * 2, 0, i < 4 ? 3 : -3), Blocks.AIR.defaultBlockState(), 3);
      BlockPos pos =
          TABLE.offset(-3 + (i % 4) * 2, facings[i] == Direction.DOWN ? 2 : 0, i < 4 ? 3 : -3);
      if (facings[i] == Direction.DOWN)
        level.setBlock(pos.above(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
      level.setBlock(
          pos,
          RitualsNotRolls.PEDESTAL
              .get()
              .defaultBlockState()
              .setValue(com.cappleapple.ritualsnotrolls.pedestal.PedestalBlock.FACING, facings[i]),
          3);
      var be = (PedestalEntity) level.getBlockEntity(pos);
      be.items.setStackInSlot(0, new ItemStack(items[i]));
      be.items.setStackInSlot(2, ItemStack.EMPTY);
      be.items.setStackInSlot(
          1, i < 3 ? new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()) : ItemStack.EMPTY);
    }
    for (int x : new int[] {-4, 4}) {
      var pos = TABLE.offset(x, 0, 0);
      level.setBlock(pos, RitualsNotRolls.PEDESTAL.get().defaultBlockState(), 3);
      var be = (PedestalEntity) level.getBlockEntity(pos);
      be.items.setStackInSlot(0, new ItemStack(RitualsNotRolls.XP_CATALYST.get()));
      be.items.setStackInSlot(1, ItemStack.EMPTY);
    }
    level
        .getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(TABLE).inflate(12))
        .forEach(net.minecraft.world.entity.Entity::discard);
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    p.getInventory().clearContent();
    p.getInventory().setItem(0, shelf.getItem(0).copy());
    p.getInventory().setItem(1, new ItemStack(Items.NETHERITE_SWORD));
    p.getInventory()
        .setItem(2, Knowledge.page(ResourceLocation.parse("minecraft:looting"), "emerald"));
    p.getInventory()
        .setItem(3, Knowledge.page(ResourceLocation.parse("minecraft:fire_aspect"), "blaze_rod"));
    p.getInventory().setItem(4, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()));
    p.getInventory().setItem(5, new ItemStack(RitualsNotRolls.XP_CATALYST.get()));
    p.experienceLevel = 0;
    p.experienceProgress = 0;
    p.totalExperience = 0;
    p.giveExperiencePoints(1395);
    p.connection.teleport(.5, 64, -1.5, 0, 15);
    p.getInventory().setChanged();
  }
}
