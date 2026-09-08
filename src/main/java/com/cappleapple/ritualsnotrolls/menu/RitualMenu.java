package com.cappleapple.ritualsnotrolls.menu;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.network.Networking;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Read-only library reference. No target slot, selection, XP allocation, or start action exists.
 */
public final class RitualMenu extends AbstractContainerMenu {
  public final ServerPlayer player;
  public final Inventory inventory;
  public final BlockPos table;
  public CompoundTag clientState = new CompoundTag();
  private long lastSync = -100;

  public RitualMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    this(
        id,
        inventory,
        buffer != null && buffer.readableBytes() >= 8 ? buffer.readBlockPos() : BlockPos.ZERO);
  }

  public RitualMenu(int id, Inventory inventory, BlockPos table) {
    super(RitualsNotRolls.RITUAL_MENU.get(), id);
    this.inventory = inventory;
    player = inventory.player instanceof ServerPlayer p ? p : null;
    this.table = table.immutable();
  }

  @Override
  public boolean stillValid(Player p) {
    return !p.isRemoved()
        && p.level().getBlockState(table).is(Blocks.ENCHANTING_TABLE)
        && p.distanceToSqr(table.getX() + .5, table.getY() + .5, table.getZ() + .5) <= 64;
  }

  @Override
  public ItemStack quickMoveStack(Player p, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player p) {}

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (player != null && player.level().getGameTime() - lastSync >= 10) sync();
  }

  public CompoundTag snapshot() {
    var network = RitualNetwork.scan(player.serverLevel(), table, false, false);
    var catalysts = RitualMath.catalysts(network);
    var rules = Definitions.SERVER.rules();
    CompoundTag state = new CompoundTag();
    state.putInt("shelves", network.shelves().size());
    state.putInt("pedestals", network.pedestals().size());
    state.putBoolean("sacrifice", catalysts.sacrifice());
    state.putInt(
        "sacrifice_pedestals",
        (int) network.pedestals().stream().filter(RitualNetwork.Pedestal::catalyst).count());
    state.putDouble("consumption_multiplier", rules.consumptionMultiplier());
    state.putInt("xp_catalysts", catalysts.experience());
    var experience = rules.experience(catalysts.experience(), RitualMath.experiencePoints(player));
    state.putDouble("xp_multiplier", experience.multiplier());
    state.putDouble("xp_levels", experience.levels());
    state.putLong("xp_cost", experience.points());
    state.putInt("xp_available", RitualMath.experiencePoints(player));
    ListTag knowledge = new ListTag();
    network
        .knowledge()
        .forEach(
            (id, entries) -> {
              var def = Definitions.SERVER.get(id);
              if (def == null) return;
              var valid = entries.stream().filter(e -> def.affinity(e) != null).sorted().toList();
              if (valid.isEmpty()) return;
              var row = new CompoundTag();
              row.putString("id", id.toString());
              ListTag list = new ListTag();
              valid.forEach(e -> list.add(StringTag.valueOf(e)));
              row.put("entries", list);
              var contributions =
                  RitualMath.materials(
                      network, RitualChains.resolve(network, Set.of(id)), experience.multiplier());
              row.putDouble(
                  "power",
                  contributions.stream().mapToDouble(m -> m.powers().getOrDefault(id, 0.0)).sum());
              var byPosition = new HashMap<BlockPos, Double>();
              contributions.forEach(m -> byPosition.put(m.pos(), m.powers().getOrDefault(id, 0.0)));
              ListTag affinities = new ListTag();
              for (var entry : valid) {
                var affinity = def.affinity(entry);
                var a = new CompoundTag();
                a.putString("id", entry);
                var matching =
                    network.pedestals().stream()
                        .filter(p -> byPosition.containsKey(p.pos()))
                        .filter(p -> affinity.matches(p.stack()))
                        .toList();
                a.putInt("placed", matching.size());
                a.putInt(
                    "sacrificed",
                    (int) matching.stream().filter(RitualNetwork.Pedestal::catalyst).count());
                a.putInt(
                    "subtracted",
                    (int) matching.stream().filter(RitualNetwork.Pedestal::subtraction).count());
                a.putDouble(
                    "effective", matching.stream().mapToDouble(p -> byPosition.get(p.pos())).sum());
                affinities.add(a);
              }
              row.put("affinities", affinities);
              knowledge.add(row);
            });
    state.put("knowledge", knowledge);
    return state;
  }

  public void sync() {
    if (player == null) return;
    lastSync = player.level().getGameTime();
    Networking.sendState(player, containerId, snapshot());
  }
}
