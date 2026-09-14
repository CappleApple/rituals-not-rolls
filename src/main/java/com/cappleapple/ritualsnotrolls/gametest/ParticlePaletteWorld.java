package com.cappleapple.ritualsnotrolls.gametest;

import static net.minecraft.commands.Commands.literal;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Isolated particle display wall in the opt-in disposable server world. */
@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class ParticlePaletteWorld {
  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    if (!Boolean.getBoolean("ritualsnotrolls.devFixture")) return;
    event
        .getDispatcher()
        .register(
            literal("ritualpalette")
                .requires(s -> s.hasPermission(2))
                .executes(
                    c -> {
                      var player = c.getSource().getPlayerOrException();
                      var level = player.serverLevel();
                      for (var pos : BlockPos.betweenClosed(28, 83, -12, 52, 98, 2)) {
                        boolean wall = pos.getZ() == 2;
                        level.setBlockAndUpdate(
                            pos, (wall ? Blocks.BLACK_CONCRETE : Blocks.AIR).defaultBlockState());
                      }
                      level.setDayTime(6000);
                      level.setWeatherParameters(6000, 0, false, false);
                      player.closeContainer();
                      player.setGameMode(GameType.SPECTATOR);
                      player.connection.teleport(40, 88.38, -10, 0, 0);
                      return 1;
                    }));
  }
}
