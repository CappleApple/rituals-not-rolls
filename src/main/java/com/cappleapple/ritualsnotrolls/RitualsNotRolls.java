package com.cappleapple.ritualsnotrolls;

import com.cappleapple.ritualsnotrolls.data.*;
import com.cappleapple.ritualsnotrolls.knowledge.*;
import com.cappleapple.ritualsnotrolls.menu.*;
import com.cappleapple.ritualsnotrolls.network.Networking;
import com.cappleapple.ritualsnotrolls.pedestal.*;
import com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(RitualsNotRolls.ID)
public final class RitualsNotRolls {
  public static final String ID = "ritualsnotrolls";
  public static final Logger LOGGER = LogUtils.getLogger();

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(ID, path);
  }

  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
  public static final DeferredRegister.DataComponents COMPONENTS =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ID);
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, ID);
  public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
      DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);
  public static final DeferredRegister<ParticleType<?>> PARTICLES =
      DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
  public static final DeferredHolder<ParticleType<?>, ParticleType<RitualParticleOptions>>
      RITUAL_PARTICLE =
          PARTICLES.register(
              "ritual",
              () ->
                  new ParticleType<RitualParticleOptions>(false) {
                    @Override
                    public MapCodec<RitualParticleOptions> codec() {
                      return RitualParticleOptions.CODEC;
                    }

                    @Override
                    public StreamCodec<? super RegistryFriendlyByteBuf, RitualParticleOptions>
                        streamCodec() {
                      return RitualParticleOptions.STREAM;
                    }
                  });
  public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
      DeferredRegister.create(Registries.SOUND_EVENT, ID);
  public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ID);
  public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT =
      DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ID);
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
  public static final DeferredHolder<DataComponentType<?>, DataComponentType<KnowledgeData>>
      KNOWLEDGE =
          COMPONENTS.registerComponentType(
              "knowledge",
              b ->
                  b.persistent(KnowledgeData.CODEC)
                      .networkSynchronized(
                          net.minecraft.network.codec.ByteBufCodecs.fromCodec(
                              KnowledgeData.CODEC)));
  public static final DeferredHolder<DataComponentType<?>, DataComponentType<BinderData>>
      BINDER_DATA =
          COMPONENTS.registerComponentType(
              "binder", b -> b.persistent(BinderData.CODEC).networkSynchronized(BinderData.STREAM));
  public static final DeferredItem<BinderItem> BINDER_ITEM =
      ITEMS.register("knowledge_binder", BinderItem::new);
  public static final DeferredItem<KnowledgeItem> PAGE =
      ITEMS.register("knowledge_page", () -> new KnowledgeItem(false));
  public static final DeferredItem<KnowledgeItem> BOOK =
      ITEMS.register("knowledge_book", () -> new KnowledgeItem(true));
  public static final DeferredItem<Item> CONSUMPTION_CATALYST =
      ITEMS.registerSimpleItem("consumption_catalyst", new Item.Properties());
  public static final DeferredItem<Item> SUBTRACTION_CATALYST =
      ITEMS.registerSimpleItem("subtraction_catalyst", new Item.Properties());
  public static final DeferredItem<Item> XP_CATALYST =
      ITEMS.registerSimpleItem("experience_catalyst", new Item.Properties().stacksTo(1));
  public static final DeferredBlock<PedestalBlock> PEDESTAL =
      BLOCKS.register(
          "pedestal",
          () ->
              new PedestalBlock(
                  BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_STONE_BRICKS)
                      .noOcclusion()));
  public static final DeferredItem<BlockItem> PEDESTAL_ITEM =
      ITEMS.registerSimpleBlockItem(PEDESTAL);
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PedestalEntity>>
      PEDESTAL_ENTITY =
          BLOCK_ENTITIES.register(
              "pedestal",
              () -> BlockEntityType.Builder.of(PedestalEntity::new, PEDESTAL.get()).build(null));
  public static final DeferredHolder<MenuType<?>, MenuType<BinderMenu>> BINDER_MENU =
      MENUS.register("knowledge_binder", () -> IMenuTypeExtension.create(BinderMenu::new));
  public static final DeferredHolder<MenuType<?>, MenuType<BookMenu>> BOOK_MENU =
      MENUS.register("knowledge_book", () -> IMenuTypeExtension.create(BookMenu::new));
  public static final DeferredHolder<MenuType<?>, MenuType<RitualMenu>> RITUAL_MENU =
      MENUS.register("ritual", () -> IMenuTypeExtension.create(RitualMenu::new));
  public static final DeferredHolder<
          RecipeSerializer<?>, SimpleCraftingRecipeSerializer<KnowledgeBookRecipe>>
      BOOK_RECIPE =
          RECIPES.register(
              "knowledge_book",
              () -> new SimpleCraftingRecipeSerializer<>(KnowledgeBookRecipe::new));
  public static final DeferredHolder<
          RecipeSerializer<?>, SimpleCraftingRecipeSerializer<EnchantedBookPageRecipe>>
      PAGE_RECIPE =
          RECIPES.register(
              "enchanted_book_page",
              () -> new SimpleCraftingRecipeSerializer<>(EnchantedBookPageRecipe::new));
  public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> ASSEMBLY =
      ATTACHMENTS.register(
          "arcane_assembly",
          () -> AttachmentType.builder(() -> 0).serialize(Codec.intRange(0, 255)).build());
  public static final DeferredHolder<
          MapCodec<? extends IGlobalLootModifier>, MapCodec<DiscoveryLoot>>
      DISCOVERY = LOOT.register("discovery", () -> DiscoveryLoot.CODEC);
  public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUNDS =
      new LinkedHashMap<>();

  static {
    for (String sound :
        List.of(
            "capture",
            "knowledge",
            "material",
            "consumption",
            "experience",
            "complete",
            "disenchant",
            "failure"))
      SOUNDS.put(
          sound,
          SOUND_EVENTS.register(sound, () -> SoundEvent.createVariableRangeEvent(id(sound))));
  }

  public RitualsNotRolls(IEventBus bus, ModContainer container) {
    ITEMS.register(bus);
    BLOCKS.register(bus);
    COMPONENTS.register(bus);
    BLOCK_ENTITIES.register(bus);
    MENUS.register(bus);
    RECIPES.register(bus);
    SOUND_EVENTS.register(bus);
    PARTICLES.register(bus);
    ATTACHMENTS.register(bus);
    LOOT.register(bus);
    TABS.register(
        "rituals",
        () ->
            CreativeModeTab.builder()
                .title(
                    net.minecraft.network.chat.Component.translatable("itemGroup.ritualsnotrolls"))
                .icon(() -> new ItemStack(PEDESTAL_ITEM.get()))
                .displayItems(
                    (parameters, output) -> {
                      output.accept(PEDESTAL_ITEM);
                      output.accept(BINDER_ITEM);
                      output.accept(CONSUMPTION_CATALYST);
                      output.accept(SUBTRACTION_CATALYST);
                      output.accept(XP_CATALYST);
                      var definitions =
                          Definitions.CLIENT.enchantments().isEmpty()
                              ? Definitions.SERVER
                              : Definitions.CLIENT;
                      for (var def : definitions.enchantments().values())
                        for (var affinity : def.materials())
                          output.accept(Knowledge.page(def.enchantment(), affinity.id()));
                    })
                .build());
    TABS.register(bus);
    container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    bus.addListener(Networking::register);
    bus.addListener(
        (net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> {
          if (event.getConfig().getSpec() != Config.SPEC) return;
          var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
          if (server != null)
            server.execute(
                () -> {
                  Definitions.refreshRules();
                  for (var player : server.getPlayerList().getPlayers())
                    Networking.syncDefinitions(player);
                });
        });
    bus.addListener(
        (RegisterCapabilitiesEvent event) ->
            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, PEDESTAL_ENTITY.get(), (be, side) -> be.items));
    NeoForge.EVENT_BUS.register(CommonEvents.class);
    NeoForge.EVENT_BUS.addListener(BinderStorage::onPlayerTick);
  }
}
