package com.cappleapple.ritualsnotrolls.knowledge;

import com.cappleapple.ritualsnotrolls.menu.BinderMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class BinderItem extends Item {
  public BinderItem() {
    super(new Item.Properties().stacksTo(1));
  }

  @Override
  public void inventoryTick(
      ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
    if (entity instanceof Player player) BinderStorage.tick(player);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (!level.isClientSide && player instanceof ServerPlayer server) {
      int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
      server.openMenu(
          new SimpleMenuProvider((id, inv, p) -> new BinderMenu(id, inv, slot), getName(stack)),
          buf -> buf.writeVarInt(slot));
    }
    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    var data = BinderStorage.data(stack);
    lines.add(
        Component.translatable(
                "ritualsnotrolls.binder.pages", data.total(), BinderStorage.capacity())
            .withStyle(ChatFormatting.GOLD));
  }
}
