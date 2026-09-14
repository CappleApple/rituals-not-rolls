package com.cappleapple.ritualsnotrolls.menu;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.compat.RitualSpace;
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

/** Library reference and shelf retrieval. Ritual targets are thrown into the world. */
public final class RitualMenu extends AbstractContainerMenu {
  public final ServerPlayer player;
  public final Inventory inventory;
  public final BlockPos table;
  private final UUID frame;
  private final ItemStack filter;
  public CompoundTag clientState = new CompoundTag();
  private long lastSync = -100;
  public int actionSequence;
  private long lastRetrieval = -100;

  public void action(String action, String value) {
    if (player == null || !stillValid(player) || !action.equals("retrieve")) return;
    long now = player.level().getGameTime();
    if (now - lastRetrieval < 4) return;
    var id = net.minecraft.resources.ResourceLocation.parse(value);
    var rows = snapshot().getList("knowledge", Tag.TAG_COMPOUND);
    if (rows.stream().noneMatch(row -> ((CompoundTag) row).getString("id").equals(value))) return;
    lastRetrieval = now;
    if (com.cappleapple.ritualsnotrolls.knowledge.KnowledgeTransfers.retrieve(player, table, id))
      sync();
  }

  public RitualMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    this(
        id,
        inventory,
        buffer != null && buffer.readableBytes() >= 8 ? buffer.readBlockPos() : BlockPos.ZERO);
  }

  public RitualMenu(int id, Inventory inventory, BlockPos table) {
    this(id, inventory, table, ItemStack.EMPTY);
  }

  public RitualMenu(int id, Inventory inventory, BlockPos table, ItemStack filter) {
    super(RitualsNotRolls.RITUAL_MENU.get(), id);
    this.inventory = inventory;
    player = inventory.player instanceof ServerPlayer p ? p : null;
    this.table = table.immutable();
    frame = RitualSpace.frameId(inventory.player.level(), table);
    boolean acceptsAny =
        !filter.isEmpty()
            && inventory
                .player
                .registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .holders()
                .anyMatch(enchantment -> RitualMath.applicable(filter, enchantment));
    this.filter = acceptsAny ? filter.copy() : ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player p) {
    return !p.isRemoved()
        && RitualSpace.loaded(p.level(), table)
        && Objects.equals(frame, RitualSpace.frameId(p.level(), table))
        && p.level().getBlockState(table).is(Blocks.ENCHANTING_TABLE)
        && p.distanceToSqr(RitualSpace.worldCenter(p.level(), table)) <= 64;
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
    var guide = new CompoundTag();
    guide.putInt("radius", Config.RADIUS.get());
    guide.putBoolean("enchantability", Config.ENCHANTABILITY.get());
    guide.putDouble("base", Config.ENCHANTABILITY_BASE.get());
    guide.putDouble("exponent", Config.ENCHANTABILITY_EXPONENT.get());
    guide.putString("equation", Config.ENCHANTABILITY_EQUATION.get());
    guide.putBoolean("chains", Config.CHAIN_ANIMATIONS.get());
    guide.putDouble("return_bonus", Config.CHAIN_RETURN_BONUS.get());
    state.put("guide", guide);
    state.put("filter", filter.saveOptional(player.registryAccess()));
    state.putBoolean("filtered", !filter.isEmpty());
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
              if (!filter.isEmpty()) {
                var enchantment =
                    player
                        .registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getHolder(id)
                        .orElse(null);
                if (enchantment == null || !RitualMath.applicable(filter, enchantment)) return;
              }
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
    if (player == null || !stillValid(player)) return;
    lastSync = player.level().getGameTime();
    Networking.sendState(player, containerId, snapshot());
  }
}
