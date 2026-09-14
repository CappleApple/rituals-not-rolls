package com.cappleapple.ritualsnotrolls.gametest;

import static net.minecraft.commands.Commands.literal;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.BinderData;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Opt-in client fixture; excluded from release artifacts. */
@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class BinderDevFixture {
  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    if (!Boolean.getBoolean("ritualsnotrolls.devFixture")) return;
    event
        .getDispatcher()
        .register(
            literal("ritualbinder")
                .requires(source -> source.hasPermission(2))
                .executes(
                    context -> {
                      prepare(context.getSource().getPlayerOrException(), false);
                      return 1;
                    })
                .then(
                    literal("empty")
                        .executes(
                            context -> {
                              prepare(context.getSource().getPlayerOrException(), true);
                              return 1;
                            })));
  }

  private static void prepare(ServerPlayer player, boolean empty) {
    player.closeContainer();
    player.getInventory().clearContent();
    player.getInventory().selected = 0;
    var entries = new ArrayList<BinderData.Entry>();
    if (!empty) {
      outer:
      for (var definition : new java.util.TreeMap<>(Definitions.SERVER.enchantments()).values()) {
        for (var affinity : definition.materials().stream().limit(3).toList()) {
          entries.add(
              new BinderData.Entry(
                  Knowledge.page(definition.enchantment(), affinity.id()),
                  entries.isEmpty() ? 65 : 1));
          if (entries.size() == 20) break outer;
        }
      }
      if (entries.size() != 20)
        throw new IllegalStateException("Binder fixture requires twenty defined pages");
    }
    var binder = new ItemStack(RitualsNotRolls.BINDER_ITEM.get());
    binder.set(RitualsNotRolls.BINDER_DATA, new BinderData(entries, true, true));
    player.getInventory().setItem(0, binder);
    player.getInventory().setChanged();
    player.inventoryMenu.broadcastChanges();
    player.connection.send(
        new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
    RitualsNotRolls.LOGGER.info("BINDER_FIXTURE_READY: entries={}", entries.size());
  }
}
