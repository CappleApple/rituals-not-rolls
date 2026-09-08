package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Runtime model composition: the inset is an actual enchanted-book stack passed through
 * ItemRenderer with the client level/player. CIT/model override integrations therefore see the
 * enchantment component.
 */
public final class KnowledgeRenderer extends BlockEntityWithoutLevelRenderer {
  private final Minecraft minecraft;
  private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();
  private static final List<java.util.function.Function<ResourceLocation, ItemStack>>
      ICON_PROVIDERS = new ArrayList<>();
  private boolean rendering;

  public KnowledgeRenderer(Minecraft minecraft) {
    super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    this.minecraft = minecraft;
  }

  public static void clearCache() {
    EXISTS.clear();
    PageMesh.clearCache();
  }

  /** Optional visual adapters can supply another actual item icon without a hard dependency. */
  public static void registerIconProvider(
      java.util.function.Function<ResourceLocation, ItemStack> provider) {
    ICON_PROVIDERS.add(provider);
  }

  public static ItemStack enchantedBook(ResourceLocation id) {
    for (var provider : ICON_PROVIDERS) {
      ItemStack stack = provider.apply(id);
      if (stack != null && !stack.isEmpty()) return stack;
    }
    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
    var minecraft = Minecraft.getInstance();
    if (minecraft.level != null)
      minecraft
          .level
          .registryAccess()
          .registryOrThrow(Registries.ENCHANTMENT)
          .getHolder(id)
          .ifPresent(
              holder -> {
                var ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                ench.set(holder, 1);
                book.set(DataComponents.STORED_ENCHANTMENTS, ench.toImmutable());
              });
    book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
    return book;
  }

  @Override
  public void renderByItem(
      ItemStack stack,
      ItemDisplayContext context,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (rendering) return;
    rendering = true;
    try {
      boolean book = stack.is(RitualsNotRolls.BOOK);
      var data = Knowledge.data(stack);
      if (book) {
        var visual =
            data == null
                ? new ItemStack(Items.ENCHANTED_BOOK)
                : enchantedBook(data.enchantment()).copy();
        visual.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        minecraft
            .getItemRenderer()
            .renderStatic(
                minecraft.player,
                visual,
                context,
                context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                pose,
                buffers,
                minecraft.level,
                light,
                overlay,
                0);
        pose.popPose();
        return;
      }
      ResourceLocation texture = RitualsNotRolls.id("textures/item/" + "page_base" + ".png");
      boolean custom = false;
      if (data != null) {
        var candidate =
            RitualsNotRolls.id(
                "textures/item/"
                    + "knowledge_pages/"
                    + data.enchantment().getNamespace()
                    + "/"
                    + data.enchantment().getPath()
                    + ".png");
        if (EXISTS.computeIfAbsent(
            candidate, p -> minecraft.getResourceManager().getResource(p).isPresent())) {
          texture = candidate;
          custom = true;
        }
      }
      PageMesh.render(
          texture, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay);
      if (data != null && !custom) {
        // Both printed faces share the same centered pivot as a vanilla generated item.
        for (int face = 0; face < 2; face++) {
          pose.pushPose();
          pose.translate(.5, .5, .5);
          if (face == 1) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
          pose.translate(-.5, -.5, 1.0 / 32 + .004);
          var definition = Definitions.CLIENT.get(data.enchantment());
          if (definition != null
              && definition.visual().isPresent()
              && EXISTS.computeIfAbsent(
                  definition.visual().get(),
                  p -> minecraft.getResourceManager().getResource(p).isPresent())) {
            pose.pushPose();
            pose.translate(.24, .24, 0);
            pose.scale(.52f, .52f, .52f);
            quad(
                pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(definition.visual().get())),
                light,
                overlay);
            pose.popPose();
          } else {
            pose.pushPose();
            pose.translate(.5, .51, 0);
            pose.scale(.54f, .54f, .025f);
            MultiBufferSource tinted = type -> new Sepia(buffers.getBuffer(type));
            minecraft
                .getItemRenderer()
                .renderStatic(
                    minecraft.player,
                    enchantedBook(data.enchantment()),
                    ItemDisplayContext.GUI,
                    false,
                    pose,
                    tinted,
                    minecraft.level,
                    light,
                    overlay,
                    0);
            pose.popPose();
          }
          pose.popPose();
        }
      }
    } finally {
      rendering = false;
    }
  }

  private static void quad(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
    vertex(pose, buffer, 0, 0, 0, 1, light, overlay);
    vertex(pose, buffer, 1, 0, 1, 1, light, overlay);
    vertex(pose, buffer, 1, 1, 1, 0, light, overlay);
    vertex(pose, buffer, 0, 1, 0, 0, light, overlay);
  }

  private static void vertex(
      PoseStack pose,
      VertexConsumer b,
      float x,
      float y,
      float u,
      float v,
      int light,
      int overlay) {
    b.addVertex(pose.last().pose(), x, y, 0)
        .setColor(255, 255, 255, 255)
        .setUv(u, v)
        .setOverlay(overlay)
        .setLight(light)
        .setNormal(pose.last(), 0, 0, 1);
  }

  private record Sepia(VertexConsumer delegate) implements VertexConsumer {
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      delegate.addVertex(x, y, z);
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      delegate.setColor((int) (r * .83), (int) (g * .72), (int) (b * .48), a);
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      delegate.setUv(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      delegate.setUv1(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      delegate.setUv2(u, v);
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      delegate.setNormal(x, y, z);
      return this;
    }
  }
}
