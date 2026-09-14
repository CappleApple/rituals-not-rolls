package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RitualsNotRolls.ID)
@PrefixGameTestTemplate(false)
public final class GatherPermissionGameTests {
  @GameTest(template = "empty")
  public static void deniedPlayerSlotStaysProtectedDuringCapabilityGather(GameTestHelper helper) {
    var player = RitualGameTests.player(helper);
    var book = RitualGameTests.book();
    player.getInventory().setItem(1, RitualGameTests.page("diamond").copyWithCount(2));
    player.getInventory().setItem(2, RitualGameTests.page("iron_ingot").copyWithCount(2));
    var output = new SimpleContainer(1);
    output.setItem(0, RitualGameTests.page("flint"));
    var menu = new PermissionMenu(player, output);

    int gathered = Knowledge.gather(player, menu, book);

    helper.assertTrue(gathered == 2, "Only the allowed output and unrelated inventory page gather");
    helper.assertTrue(
        player.getInventory().getItem(1).getCount() == 2
            && !Knowledge.data(book).entries().contains("diamond"),
        "The item handler must not bypass the visible player slot's pickup restriction");
    helper.assertTrue(
        player.getInventory().getItem(2).getCount() == 1
            && Knowledge.data(book).entries().contains("iron_ingot"),
        "A different page outside the menu still gathers once through the inventory capability");
    helper.assertTrue(
        output.getItem(0).isEmpty()
            && Knowledge.data(book).entries().contains("flint")
            && menu.outputTakes == 1,
        "An allowed output slot gathers through safeTake and runs onTake exactly once");
    helper.succeed();
  }

  private static final class PermissionMenu extends AbstractContainerMenu {
    private int outputTakes;

    private PermissionMenu(Player player, SimpleContainer output) {
      super(MenuType.GENERIC_9x3, 41);
      addSlot(
          new Slot(player.getInventory(), 1, 0, 0) {
            @Override
            public boolean mayPickup(Player player) {
              return false;
            }
          });
      addSlot(
          new Slot(output, 0, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
              outputTakes++;
              super.onTake(player, stack);
            }
          });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }
  }
}
