package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.api.*;
import com.cappleapple.ritualsnotrolls.compat.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class PedestalCompatFixture {
  static void check(boolean result, String message) {
    if (!result) throw new IllegalStateException(message);
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    if (!Boolean.getBoolean("ritualsnotrolls.pedestalCompatFixture")) return;
    var server = event.getServer();
    var level = server.overworld();
    try {
      var cookie =
          CommonListenerCookie.createInitial(
              new GameProfile(UUID.randomUUID(), "PedestalQA"), false);
      var player =
          new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
      player.connection =
          new ServerGamePacketListenerImpl(
              server, new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override
            public void send(Packet<?> packet) {}

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {}
          };
      int index = 0;
      for (String name : List.of("supplementaries:pedestal", "irons_spellbooks:pedestal")) {
        var table = new BlockPos(index++ * 40, 65, 0);
        player.setPos(Vec3.atCenterOf(table));
        var pos = table.offset(0, 0, 3);
        var shelfPos = table.offset(3, 0, 3);
        level.setBlock(table, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level
            .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5))
            .forEach(net.minecraft.world.entity.Entity::discard);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(
            pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name)).defaultBlockState(), 3);
        level.setBlock(shelfPos, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
        ((ChiseledBookShelfBlockEntity) level.getBlockEntity(shelfPos))
            .setItem(0, RitualGameTests.book("diamond"));
        var handler = RitualApi.pedestal(level, pos);
        check(handler != null && handler.getSlots() == 3, "Native bridge " + name);
        check(
            handler.insertItem(0, new ItemStack(Items.DIAMOND, 2), false).getCount() == 1,
            "One native held item");
        check(
            handler
                .insertItem(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()), false)
                .isEmpty(),
            "Consumption attaches");
        check(
            handler
                .insertItem(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()), false)
                .isEmpty(),
            "Subtraction attaches alongside consumption");
        var be = level.getBlockEntity(pos);
        var saved = be.saveWithFullMetadata(level.registryAccess());
        var restored =
            BlockEntity.loadStatic(pos, be.getBlockState(), saved, level.registryAccess());
        restored.setLevel(level);
        check(
            ForeignPedestals.modifiers(restored)
                .getStackInSlot(0)
                .is(RitualsNotRolls.CONSUMPTION_CATALYST),
            "Attached modifier NBT persistence");
        check(
            ForeignPedestals.modifiers(restored)
                .getStackInSlot(1)
                .is(RitualsNotRolls.SUBTRACTION_CATALYST),
            "Second modifier NBT persistence");
        player.getInventory().selected = 8;
        player.setShiftKeyDown(true);
        for (int click = 0; click < 3; click++) {
          var interaction =
              new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                  player,
                  net.minecraft.world.InteractionHand.MAIN_HAND,
                  pos,
                  new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
          check(
              ForeignPedestals.interact(interaction) == (click < 2),
              "Attached modifiers remove first; then native interaction resumes");
        }
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        handler.insertItem(1, new ItemStack(RitualsNotRolls.CONSUMPTION_CATALYST.get()), false);
        handler.insertItem(2, new ItemStack(RitualsNotRolls.SUBTRACTION_CATALYST.get()), false);
        var item =
            RitualMath.apply(
                player, new ItemStack(Items.DIAMOND_SWORD), Map.of(RitualGameTests.SHARP, 4));
        var entity = new ItemEntity(level, table.getX() + .5, 66, 0.5, item.copy());
        var plan =
            RitualMath.automatic(player, item, RitualNetwork.scan(level, table, false, true));
        check(
            plan.evaluation().ready() && !plan.withdrawals().isEmpty(),
            "Real native pedestal powers disenchantment");
        var reserved = new RitualConsumption();
        check(reserved.take(level, entity, plan, pos).isEmpty(), "Native early extraction");
        check(handler.getStackInSlot(0).isEmpty(), "Native held item visibly removed");
        check(
            RitualMath.planFor(
                    player,
                    item,
                    plan.selected(),
                    reserved.overlay(RitualNetwork.scan(level, table, false, true)))
                .evaluation()
                .ready(),
            "Reserved power remains valid");
        reserved.rollback(level, entity);
        check(handler.getStackInSlot(0).is(Items.DIAMOND), "Native refund succeeds");
        check(
            RitualEngine.commit(level, table, player, entity, item, plan).isEmpty(),
            "Native transactional commit");
        check(
            handler.getStackInSlot(0).isEmpty()
                && !handler.getStackInSlot(1).isEmpty()
                && !handler.getStackInSlot(2).isEmpty(),
            "Only offering consumed");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        int drops =
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                .filter(
                    e ->
                        e.getItem().is(RitualsNotRolls.CONSUMPTION_CATALYST)
                            || e.getItem().is(RitualsNotRolls.SUBTRACTION_CATALYST))
                .mapToInt(e -> e.getItem().getCount())
                .sum();
        check(drops == 2, "Breaking native pedestal returns both modifiers exactly once: " + drops);
        RitualsNotRolls.LOGGER.info(
            "PEDESTAL COMPAT PASS {}: native inventory, modifiers, persistence, early take/refund,"
                + " disenchant commit, break drops",
            name);
      }
      RitualsNotRolls.LOGGER.info("PEDESTAL COMPAT GATE PASS");
    } catch (Exception e) {
      RitualsNotRolls.LOGGER.error("PEDESTAL COMPAT GATE FAILED", e);
    } finally {
      server.halt(false);
    }
  }
}
