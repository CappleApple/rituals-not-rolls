package com.cappleapple.ritualsnotrolls;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.cappleapple.ritualsnotrolls.network.Networking;
import com.cappleapple.ritualsnotrolls.ritual.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class CommonEvents {
  @SubscribeEvent
  public static void tags(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
    com.cappleapple.ritualsnotrolls.data.Affinity.clearCache();
  }

  @SubscribeEvent
  public static void reload(AddReloadListenerEvent event) {
    event.addListener(
        new Definitions(
            event.getRegistryAccess().registryOrThrow(Registries.ENCHANTMENT)::containsKey));
  }

  @SubscribeEvent
  public static void sync(OnDatapackSyncEvent event) {
    if (event.getPlayer() != null) Networking.syncDefinitions(event.getPlayer());
    else {
      DebugCommands.validate(event.getPlayerList().getServer());
      for (var player : event.getPlayerList().getPlayers()) Networking.syncDefinitions(player);
    }
  }

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent event) {
    DebugCommands.register(event.getDispatcher());
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    if (event.getLevel() instanceof ServerLevel level) RitualEngine.tick(level);
  }

  @SubscribeEvent
  public static void itemJoined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
    if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
        && !event.getLevel().isClientSide) {
      RitualEngine.restoreCapturedItem(item);
      if (!event.loadedFromDisk()) {
        DiscoveryLoot.replaceDrop(item);
        RitualEngine.observe(item);
      }
    }
  }

  @SubscribeEvent
  public static void unloaded(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level) RitualEngine.unload(level);
  }

  @SubscribeEvent
  public static void stopping(ServerStoppingEvent event) {
    RitualEngine.clear();
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent event) {
    RitualEngine.clear();
    RitualNetwork.clear();
  }

  @SubscribeEvent
  public static void chunk(ChunkEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level) RitualNetwork.invalidate(level);
  }

  @SubscribeEvent
  public static void loadChunk(ChunkEvent.Load event) {
    if (event.getLevel() instanceof ServerLevel level) RitualNetwork.invalidate(level);
  }

  @SubscribeEvent
  public static void placed(BlockEvent.EntityPlaceEvent event) {
    if (event.getLevel() instanceof ServerLevel level) RitualNetwork.invalidate(level);
  }

  @SubscribeEvent
  public static void broken(BlockEvent.BreakEvent event) {
    if (event.getLevel() instanceof ServerLevel level) RitualNetwork.invalidate(level);
  }

  @SubscribeEvent
  public static void sent(ChunkWatchEvent.Sent event) {
    for (var be : event.getChunk().getBlockEntities().values())
      if (com.cappleapple.ritualsnotrolls.compat.ForeignPedestals.supported(be.getBlockState()))
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            event.getPlayer(),
            new Networking.PedestalModifiers(
                event.getLevel().dimension().location(),
                be.getBlockPos(),
                be.getPersistentData()
                    .getCompound(com.cappleapple.ritualsnotrolls.compat.ForeignPedestals.KEY)
                    .copy()));
  }

  @SubscribeEvent
  public static void interact(PlayerInteractEvent.RightClickBlock event) {
    if (com.cappleapple.ritualsnotrolls.compat.ForeignPedestals.interact(event)) return;
    if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.ENCHANTING_TABLE)) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.getHand() != InteractionHand.MAIN_HAND) return;
    var be = event.getLevel().getBlockEntity(event.getPos());
    if (be == null) return;
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new RitualMenu(id, inv, event.getPos(), player.getMainHandItem()),
            Component.translatable("container.ritualsnotrolls.ritual")),
        buf -> buf.writeBlockPos(event.getPos()));
  }

  @SubscribeEvent
  public static void drops(BlockDropsEvent event) {
    if (event.getState().is(Blocks.ENCHANTING_TABLE) && event.getBlockEntity() != null) {
      int level = event.getBlockEntity().getData(RitualsNotRolls.ASSEMBLY);
      if (level <= 0) return;
      var holder =
          event
              .getLevel()
              .registryAccess()
              .registryOrThrow(Registries.ENCHANTMENT)
              .getHolder(RitualsNotRolls.id("arcane_assembly"));
      holder.ifPresent(
          h ->
              event.getDrops().stream()
                  .filter(e -> e.getItem().is(Items.ENCHANTING_TABLE))
                  .forEach(e -> e.getItem().enchant(h, level)));
    }
  }

  @SubscribeEvent
  public static void starting(ServerAboutToStartEvent event) {
    Definitions.refreshRules();
  }

  @SubscribeEvent
  public static void validate(ServerStartedEvent event) {
    DebugCommands.validate(event.getServer());
  }
}
