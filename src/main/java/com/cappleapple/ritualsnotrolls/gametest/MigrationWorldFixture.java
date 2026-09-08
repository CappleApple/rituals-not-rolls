package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.ritual.RitualNetwork;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Three-boot, opt-in disk migration gate. Never included in the production JAR. */
@EventBusSubscriber(modid = RitualsNotRolls.ID)
public final class MigrationWorldFixture {
  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    if (!Boolean.getBoolean("ritualsnotrolls.migrationFixture")) return;
    var server = event.getServer();
    try {
      if (ModList.get().isLoaded("immersiveenchanting"))
        throw new IllegalStateException("Legacy mod must be absent");
      Path stageFile = Path.of("migration-stage.txt");
      int stage =
          Files.exists(stageFile) ? Integer.parseInt(Files.readString(stageFile).trim()) : 0;
      var level = server.overworld();
      // The second shelf is outside the spawn area and loads on demand on the next boot.
      for (int index = 0; index < 2; index++) {
        BlockPos pos = new BlockPos(index * 2048, 64, index * 2048);
        if (stage == 0) {
          BlockState state =
              Blocks.CHISELED_BOOKSHELF
                  .defaultBlockState()
                  .setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.getFirst(), true);
          level.setBlockAndUpdate(pos, state);
          var legacy =
              new ChiseledBookShelfBlockEntity(pos, state) {
                @Override
                protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
                  super.saveAdditional(tag, registries);
                  ListTag items = new ListTag();
                  var raw = MigrationGameTests.ancient("minecraft:mending", 1);
                  raw.putByte("Slot", (byte) 0);
                  items.add(raw);
                  tag.put("Items", items);
                }
              };
          level.setBlockEntity(legacy);
          legacy.setChanged();
        } else {
          var shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(pos);
          ItemStack page = shelf.getItem(0);
          if (!page.is(RitualsNotRolls.PAGE)
              || !Knowledge.data(page).enchantment().toString().equals("minecraft:mending"))
            throw new IllegalStateException("Saved legacy shelf did not migrate: " + pos);
          if (!RitualNetwork.scan(level, pos.offset(1, 0, 0), false, true)
              .knowledge()
              .containsKey(Knowledge.data(page).enchantment()))
            throw new IllegalStateException("Migrated page not usable as shelf knowledge");
          Path previous = Path.of("migration-page-" + index + ".snbt");
          String encoded = page.save(level.registryAccess()).toString();
          if (stage == 1) Files.writeString(previous, encoded);
          else if (!Files.readString(previous).equals(encoded))
            throw new IllegalStateException("Restart changed converted page");
          shelf.setChanged();
        }
      }
      Files.writeString(stageFile, Integer.toString(stage + 1));
      RitualsNotRolls.LOGGER.info(
          "MIGRATION WORLD GATE PASS stage={} (0=seed, 1=legacy load, 2=idempotent restart)",
          stage);
    } catch (Exception ex) {
      RitualsNotRolls.LOGGER.error("MIGRATION WORLD GATE FAILED", ex);
    } finally {
      server.halt(false);
    }
  }
}
