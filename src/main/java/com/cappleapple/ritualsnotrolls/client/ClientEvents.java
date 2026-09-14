package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.MaterialTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;

@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class ClientEvents {
  @SubscribeEvent
  public static void particles(RegisterParticleProvidersEvent event) {
    event.registerSpecial(RitualsNotRolls.RITUAL_PARTICLE.get(), new RitualParticleProvider());
  }

  @SubscribeEvent
  public static void screens(RegisterMenuScreensEvent event) {
    event.register(RitualsNotRolls.BOOK_MENU.get(), BookScreen::new);
    event.register(RitualsNotRolls.BINDER_MENU.get(), BinderScreen::new);
    event.register(RitualsNotRolls.RITUAL_MENU.get(), RitualScreen::new);
  }

  @SubscribeEvent
  public static void renderer(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(RitualsNotRolls.PEDESTAL_ENTITY.get(), PedestalRenderer::new);
  }

  @SubscribeEvent
  public static void extensions(RegisterClientExtensionsEvent event) {
    event.registerItem(
        new IClientItemExtensions() {
          private KnowledgeRenderer renderer;

          @Override
          public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) renderer = new KnowledgeRenderer(Minecraft.getInstance());
            return renderer;
          }
        },
        RitualsNotRolls.PAGE.get(),
        RitualsNotRolls.BOOK.get());
  }

  @SubscribeEvent
  public static void tooltips(RegisterClientTooltipComponentFactoriesEvent event) {
    event.register(MaterialTooltip.class, MaterialTooltipRenderer::new);
  }

  @SubscribeEvent
  public static void binderTooltip(
      net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
    if (!event.getItemStack().is(RitualsNotRolls.BINDER_ITEM)
        || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;
    var data = com.cappleapple.ritualsnotrolls.knowledge.BinderStorage.data(event.getItemStack());
    event
        .getToolTip()
        .add(
            net.minecraft.network.chat.Component.translatable(
                    "ritualsnotrolls.binder.auto_collect",
                    net.minecraft.network.chat.Component.translatable(
                        data.autoCollect() ? "options.on" : "options.off"))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    event
        .getToolTip()
        .add(
            net.minecraft.network.chat.Component.translatable(
                    "ritualsnotrolls.binder.filter_duplicates",
                    net.minecraft.network.chat.Component.translatable(
                        data.filterDuplicates() ? "options.on" : "options.off"))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
  }

  @SubscribeEvent
  public static void reload(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(
        (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
            manager -> {
              KnowledgeRenderer.clearCache();
              com.cappleapple.ritualsnotrolls.data.Affinity.clearCache();
            });
  }
}
