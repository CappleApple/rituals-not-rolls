package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.data.Definitions;
import com.cappleapple.ritualsnotrolls.menu.BookMenu;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class KnowledgeItem extends Item {
  private final boolean book;

  public KnowledgeItem(boolean book) {
    super(new Item.Properties().stacksTo(book ? 1 : 64));
    this.book = book;
  }

  @Override
  public Component getName(ItemStack stack) {
    var data = Knowledge.data(stack);
    return data == null
        ? super.getName(stack)
        : Component.translatable(
            book
                ? "item.ritualsnotrolls.knowledge_book.named"
                : "item.ritualsnotrolls.knowledge_page.named",
            Knowledge.name(data.enchantment(), null));
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    var data = Knowledge.data(stack);
    if (data == null) return;
    boolean client = context.level() != null && context.level().isClientSide;
    var def = Definitions.forSide(client).get(data.enchantment());
    if (book) {
      lines.add(
          Component.translatable(
                  "ritualsnotrolls.pages", data.entries().size(), Knowledge.total(data, client))
              .withStyle(ChatFormatting.GOLD));
      lines.add(
          Component.translatable(
                  "ritualsnotrolls.auto_add",
                  Component.translatable(data.autoAdd() ? "options.on" : "options.off"))
              .withStyle(ChatFormatting.GRAY));
      lines.add(
          Component.translatable("ritualsnotrolls.book_hint").withStyle(ChatFormatting.DARK_GRAY));
    } else if (!data.entries().isEmpty()) {
      var affinity = def == null ? null : def.affinity(data.entries().getFirst());
      if (affinity == null)
        lines.add(
            Component.translatable("ritualsnotrolls.unresolved").withStyle(ChatFormatting.GRAY));
      else if (!client) {
        // Plain-text fallback for server-side tooltip consumers; the client renders an inline icon
        // row.
        lines.add(
            affinity
                .name(context.level() == null ? 0 : context.level().getGameTime())
                .copy()
                .withStyle(ChatFormatting.GRAY));
        lines.add(def.powerTier(affinity).description().withStyle(ChatFormatting.GOLD));
      }
    }
  }

  @Override
  public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
    var d = Knowledge.data(stack);
    return !book && d != null && !d.entries().isEmpty()
        ? Optional.of(new MaterialTooltip(d.enchantment(), d.entries().getFirst()))
        : Optional.empty();
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (!level.isClientSide && Knowledge.data(stack) != null) {
      if (book && player instanceof ServerPlayer server) {
        Knowledge.warnStale(Knowledge.data(stack));
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        server.openMenu(
            new SimpleMenuProvider((id, inv, p) -> new BookMenu(id, inv, slot), getName(stack)),
            buf -> buf.writeVarInt(slot));
      } else if (Knowledge.absorb(player, stack, false, false)) player.getInventory().setChanged();
    }
    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
  }
}
