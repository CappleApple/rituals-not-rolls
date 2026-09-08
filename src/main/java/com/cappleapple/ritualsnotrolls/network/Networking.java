package com.cappleapple.ritualsnotrolls.network;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.menu.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Networking {
  public record Action(int menu, int sequence, String action, String value)
      implements CustomPacketPayload {
    public static final Type<Action> TYPE = new Type<>(RitualsNotRolls.id("action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeVarInt(p.sequence);
              b.writeUtf(p.action, 32);
              b.writeUtf(p.value, 512);
            },
            b -> new Action(b.readVarInt(), b.readVarInt(), b.readUtf(32), b.readUtf(512)));

    @Override
    public Type<Action> type() {
      return TYPE;
    }
  }

  public record State(int menu, CompoundTag state) implements CustomPacketPayload {
    public static final Type<State> TYPE = new Type<>(RitualsNotRolls.id("state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            State::menu,
            ByteBufCodecs.COMPOUND_TAG,
            State::state,
            State::new);

    @Override
    public Type<State> type() {
      return TYPE;
    }
  }

  public record DefinitionChunk(int revision, String kind, String text, boolean last)
      implements CustomPacketPayload {
    public static final Type<DefinitionChunk> TYPE = new Type<>(RitualsNotRolls.id("definition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DefinitionChunk> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.revision);
              b.writeUtf(p.kind, 256);
              b.writeUtf(p.text, 20000);
              b.writeBoolean(p.last);
            },
            b ->
                new DefinitionChunk(
                    b.readVarInt(), b.readUtf(256), b.readUtf(20000), b.readBoolean()));

    @Override
    public Type<DefinitionChunk> type() {
      return TYPE;
    }
  }

  public record PedestalModifiers(
      ResourceLocation dimension, net.minecraft.core.BlockPos pos, CompoundTag data)
      implements CustomPacketPayload {
    public static final Type<PedestalModifiers> TYPE =
        new Type<>(RitualsNotRolls.id("pedestal_modifiers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PedestalModifiers> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeResourceLocation(p.dimension);
              b.writeBlockPos(p.pos);
              b.writeNbt(p.data);
            },
            b -> new PedestalModifiers(b.readResourceLocation(), b.readBlockPos(), b.readNbt()));

    public Type<PedestalModifiers> type() {
      return TYPE;
    }
  }

  private static final Map<String, StringBuilder> PARTS = new HashMap<>();

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("6");
    registrar.playToClient(
        PedestalModifiers.TYPE,
        PedestalModifiers.CODEC,
        (p, context) -> {
          var level = context.player().level();
          if (!level.dimension().location().equals(p.dimension())) return;
          var be = level.getBlockEntity(p.pos());
          if (be != null
              && com.cappleapple.ritualsnotrolls.compat.ForeignPedestals.supported(
                  be.getBlockState()))
            be.getPersistentData()
                .put(com.cappleapple.ritualsnotrolls.compat.ForeignPedestals.KEY, p.data());
        });
    registrar.playToServer(
        Action.TYPE,
        Action.CODEC,
        (p, context) -> {
          if (!(context.player() instanceof ServerPlayer player)
              || player.containerMenu.containerId != p.menu
              || !player.containerMenu.stillValid(player)) return;
          try {
            if (player.containerMenu instanceof BookMenu menu
                && p.sequence == menu.actionSequence + 1) {
              menu.actionSequence = p.sequence;
              menu.action(p.action, p.value);
            }
          } catch (IllegalArgumentException e) {
            RitualsNotRolls.LOGGER.debug(
                "Rejected invalid ritual menu action from {}", player.getGameProfile().getName());
          }
        });
    registrar.playToClient(
        State.TYPE,
        State.CODEC,
        (p, context) -> {
          if (context.player().containerMenu instanceof RitualMenu menu
              && menu.containerId == p.menu) menu.clientState = p.state;
          if (context.player().containerMenu instanceof BookMenu menu && menu.containerId == p.menu)
            menu.clientBook =
                net.minecraft.world.item.ItemStack.parseOptional(
                    context.player().registryAccess(), p.state.getCompound("book"));
        });
    registrar.playToClient(
        DefinitionChunk.TYPE,
        DefinitionChunk.CODEC,
        (p, context) -> {
          if (p.kind.equals("reset")) {
            PARTS.clear();
            Definitions.CLIENT =
                new Definitions.Snapshot(Map.of(), RitualRules.DEFAULT, p.revision);
            return;
          }
          if (p.revision != Definitions.CLIENT.revision()) return;
          var text = PARTS.computeIfAbsent(p.kind, k -> new StringBuilder());
          text.append(p.text);
          if (!p.last) return;
          PARTS.remove(p.kind);
          if (p.kind.equals("rules"))
            Definitions.CLIENT =
                new Definitions.Snapshot(
                    Definitions.CLIENT.enchantments(),
                    RitualRules.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(text.toString()))
                        .getOrThrow(),
                    p.revision);
          else {
            var definition =
                RitualDefinition.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(text.toString()))
                    .getOrThrow();
            Map<ResourceLocation, RitualDefinition> map =
                new TreeMap<>(Definitions.CLIENT.enchantments());
            map.put(definition.enchantment(), definition);
            context
                .player()
                .registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolder(definition.enchantment())
                .ifPresent(
                    h ->
                        com.cappleapple.ritualsnotrolls.knowledge.Knowledge.NAMES.put(
                            definition.enchantment(), h.value().description()));
            Definitions.CLIENT =
                new Definitions.Snapshot(map, Definitions.CLIENT.rules(), p.revision);
          }
        });
  }

  public static void syncDefinitions(ServerPlayer player) {
    var data = Definitions.SERVER;
    PacketDistributor.sendToPlayer(player, new DefinitionChunk(data.revision(), "reset", "", true));
    sendChunks(
        player,
        "rules",
        RitualRules.CODEC.encodeStart(JsonOps.INSTANCE, data.rules()).getOrThrow().toString(),
        data.revision());
    data.enchantments()
        .forEach(
            (id, d) -> sendChunks(player, id.toString(), Definitions.encode(d), data.revision()));
  }

  private static void sendChunks(ServerPlayer player, String kind, String json, int revision) {
    for (int i = 0; i < json.length(); i += 20000)
      PacketDistributor.sendToPlayer(
          player,
          new DefinitionChunk(
              revision,
              kind,
              json.substring(i, Math.min(i + 20000, json.length())),
              i + 20000 >= json.length()));
  }

  public static void sendState(ServerPlayer player, int menu, CompoundTag state) {
    PacketDistributor.sendToPlayer(player, new State(menu, state));
  }
}
