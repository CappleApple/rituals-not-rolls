package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.knowledge.BinderStorage;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.knowledge.KnowledgeData;
import com.cappleapple.ritualsnotrolls.menu.BookMenu;
import com.cappleapple.ritualsnotrolls.network.Networking;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Exercises standard NeoForge inventory access against real optional Bundled Not Siloed storage.
 */
@GameTestHolder("ritualsnotrolls_bundled")
public final class BundledKnowledgeGameTests {
  @GameTestGenerator
  public static Collection<TestFunction> tests() {
    if (!ModList.get().isLoaded("bundlednotsiloed")) return List.of();
    return List.of(
        test(
            "capability_enumerates_and_extracts_hidden_pages",
            BundledKnowledgeGameTests::capability),
        test("book_button_gathers_stowed_pages", BundledKnowledgeGameTests::bookButton),
        test("scrolled_inventory_gathers_each_page_once", BundledKnowledgeGameTests::scrolled),
        test("singleton_pages_shrink_handler_during_gather", BundledKnowledgeGameTests::shrinking),
        test("visible_binder_collects_stowed_pages", BundledKnowledgeGameTests::visibleBinder),
        test(
            "stowed_binder_collects_without_visible_binder",
            BundledKnowledgeGameTests::stowedBinder),
        test(
            "stowed_binder_survives_shrinking_handler", BundledKnowledgeGameTests::shrinkingBinder),
        test(
            "cursor_double_click_gathers_chest_and_stowed_pages",
            BundledKnowledgeGameTests::cursor));
  }

  private static TestFunction test(String name, Consumer<GameTestHelper> action) {
    return new TestFunction(
        "bundled_knowledge",
        "bundledknowledgegametests." + name,
        "ritualsnotrolls_bundled:empty",
        Rotation.NONE,
        100,
        0,
        true,
        action);
  }

  private static void capability(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(0, book());
    f.put(80, page("flint", 3));
    var entity = f.player.getCapability(Capabilities.ItemHandler.ENTITY);
    var automation = f.player.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, null);
    RitualsNotRolls.LOGGER.info(
        "Bundled knowledge fixture: ENTITY={} slots={}, ENTITY_AUTOMATION={} slots={}",
        entity == null ? "missing" : entity.getClass().getName(),
        entity == null ? -1 : entity.getSlots(),
        automation == null ? "missing" : automation.getClass().getName(),
        automation == null ? -1 : automation.getSlots());
    h.assertTrue(automation != null, "Bundled player exposes NeoForge automation inventory");
    h.assertTrue(
        automation
            .getClass()
            .getName()
            .equals("com.cappleapple.bundlednotsiloed.compat.DynamicItemHandler"),
        "Automation capability resolves the real Bundled inventory provider");
    h.assertTrue(
        automation.getSlots() > 80, "Capability reports hidden logical slots beyond vanilla");
    h.assertTrue(
        ItemStack.matches(automation.getStackInSlot(80), page("flint", 3)),
        "Capability reads the actual hidden page stack");
    ItemStack simulated = automation.extractItem(80, 1, true);
    h.assertTrue(
        ItemStack.matches(simulated, page("flint", 1)), "Simulation extracts one matching page");
    f.count(80, 3, "Simulation preserves the backend quantity");
    ItemStack taken = automation.extractItem(80, 1, false);
    h.assertTrue(ItemStack.matches(taken, page("flint", 1)), "Commit returns one actual page");
    f.count(80, 2, "Commit consumes exactly one backend page");
    f.valid();
    h.succeed();
  }

  private static void bookButton(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(0, book("diamond"));
    f.put(1, page("flint", 2));
    f.put(36, page("gold_ingot", 3));
    f.put(37, page("flint", 2));
    f.put(80, page("netherite_scrap", 4));
    f.put(81, page("diamond", 5));
    f.put(82, Knowledge.page(RitualGameTests.UNBREAKING, "diamond").copyWithCount(6));
    ItemStack bound = f.player.getInventory().getItem(0);
    var menu = f.openBook();
    long before = f.revision();
    menu.action("gather", "");
    f.entries(bound, "diamond", "flint", "gold_ingot", "netherite_scrap");
    f.count(1, 1, "Visible page stack is consumed once");
    f.count(36, 2, "First stowed page stack is consumed once");
    f.count(37, 2, "Hidden duplicate of the consumed visible page is preserved");
    f.count(80, 3, "Distant stowed page stack is consumed once");
    f.count(81, 5, "Already known pages are preserved");
    f.count(82, 6, "Pages for another enchantment are preserved");
    h.assertTrue(f.revision() > before, "Gather updates the authoritative inventory revision");
    h.assertTrue(
        f.player.getInventory().getItem(0) == bound && menu.stillValid(f.player),
        "Gather preserves the bound live book and keeps its menu valid");
    h.assertTrue(!f.states.isEmpty(), "Gather sends the book menu state");
    ItemStack synced =
        ItemStack.parseOptional(
            f.player.registryAccess(), f.states.getLast().state().getCompound("book"));
    h.assertTrue(ItemStack.matches(synced, bound), "Book menu sync contains the gathered entries");
    long after = f.revision();
    menu.action("gather", "");
    f.count(1, 1, "Repeated gather preserves visible duplicates");
    f.count(36, 2, "Repeated gather preserves stowed duplicates");
    f.count(37, 2, "Repeated gather preserves the hidden visible-page duplicate");
    f.count(80, 3, "Repeated gather preserves distant duplicates");
    h.assertTrue(f.revision() == after, "Repeated gather leaves backend contents unchanged");
    h.assertTrue(menu.stillValid(f.player), "Repeated gather keeps the book menu valid");
    f.valid();
    h.succeed();
  }

  private static void scrolled(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(0, book("netherite_scrap"));
    f.put(9, page("diamond", 2));
    f.put(36, page("flint", 3));
    f.put(80, page("gold_ingot", 4));
    f.put(81, page("netherite_scrap", 5));
    call(f.data, "showInventoryRange", new Class<?>[] {int.class}, 80);
    h.assertTrue(
        ItemStack.matches(f.player.getInventory().getItem(9), page("gold_ingot", 4)),
        "Scrolled main inventory displays logical slot 80");
    ItemStack bound = f.player.getInventory().getItem(0);
    var menu = f.openBook();
    menu.action("gather", "");
    f.entries(bound, "netherite_scrap", "diamond", "flint", "gold_ingot");
    f.count(9, 1, "Gather reaches the main slot hidden by scrolling");
    f.count(36, 2, "Gather reaches the stowed slot outside the current viewport");
    f.count(80, 3, "Displayed logical page is consumed only once");
    f.count(81, 5, "Scrolled duplicate stack remains untouched");
    h.assertTrue(menu.stillValid(f.player), "Scrolled gathering preserves the hotbar book binding");
    h.assertTrue(
        f.player.getInventory().getItem(0) == bound,
        "Book keeps its authoritative stack reference");
    f.valid();
    h.succeed();
  }

  private static void cursor(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(9, page("gold_ingot", 2));
    f.put(80, page("flint", 3));
    f.put(81, page("diamond", 4));
    f.put(82, Knowledge.page(RitualGameTests.UNBREAKING, "diamond").copyWithCount(5));
    var chest = new SimpleContainer(27);
    chest.setItem(0, page("netherite_scrap", 2));
    chest.setItem(1, page("diamond", 3));
    chest.setItem(2, Knowledge.page(RitualGameTests.UNBREAKING, "diamond").copyWithCount(4));
    var menu = ChestMenu.threeRows(72, f.player.getInventory(), chest);
    f.player.containerMenu = menu;
    ItemStack book = book("diamond");
    menu.setCarried(book);
    menu.clicked(0, 0, ClickType.PICKUP_ALL, f.player);
    f.entries(book, "diamond", "netherite_scrap", "gold_ingot", "flint");
    h.assertTrue(menu.getCarried() == book, "Double-click keeps the book on the cursor");
    h.assertTrue(chest.getItem(0).getCount() == 1, "Chest contributes exactly one new page");
    h.assertTrue(
        chest.getItem(1).getCount() == 3 && chest.getItem(2).getCount() == 4,
        "Chest duplicates and other enchantments remain untouched");
    f.count(9, 1, "Cursor gather consumes one visible player page");
    f.count(80, 2, "Cursor gather consumes one stowed player page");
    f.count(81, 4, "Cursor gather preserves stowed duplicates");
    f.count(82, 5, "Cursor gather preserves other enchantments");
    menu.clicked(0, 0, ClickType.PICKUP_ALL, f.player);
    h.assertTrue(
        chest.getItem(0).getCount() == 1, "Repeated double-click preserves chest duplicates");
    f.count(80, 2, "Repeated double-click preserves stowed duplicates");
    f.valid();
    h.succeed();
  }

  private static void shrinking(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(0, book());
    f.put(36, page("flint", 1));
    f.put(80, page("diamond", 1));
    IItemHandler handler = f.player.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, null);
    h.assertTrue(
        handler != null && handler.getSlots() > 80, "Fixture begins with a distant logical extent");
    ItemStack bound = f.player.getInventory().getItem(0);
    var menu = f.openBook();
    menu.action("gather", "");
    f.entries(bound, "flint", "diamond");
    f.count(36, 0, "First singleton page is consumed");
    f.count(80, 0, "Trailing singleton page is consumed");
    h.assertTrue(
        handler.getSlots() < 36, "Consuming the last hidden page shrinks the handler extent");
    h.assertTrue(
        menu.stillValid(f.player) && f.player.getInventory().getItem(0) == bound,
        "Shrinking the handler preserves the bound book");
    long after = f.revision();
    menu.action("gather", "");
    h.assertTrue(
        after == f.revision() && menu.stillValid(f.player),
        "Gathering again safely leaves the smaller inventory unchanged");
    f.valid();
    h.succeed();
  }

  private static ItemStack book(String... entries) {
    return Knowledge.book(new KnowledgeData(RitualGameTests.SHARP, List.of(entries), false));
  }

  private static void visibleBinder(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(0, new ItemStack(RitualsNotRolls.BINDER_ITEM.get()));
    f.put(80, page("diamond", 2));
    f.put(81, page("flint", 1));
    f.put(82, page("diamond", 3));
    f.put(83, Knowledge.page(RitualGameTests.UNBREAKING, "diamond"));
    ItemStack bound = f.player.getInventory().getItem(0);
    h.assertTrue(
        BinderStorage.collect(f.player) == 3,
        "Visible binder collects every new stowed knowledge identity");
    h.assertTrue(
        f.player.getInventory().getItem(0) == bound,
        "Collection preserves the live binder identity");
    h.assertTrue(
        BinderStorage.data(bound).total() == 3,
        "All collected pages are stored in the actual binder");
    f.count(80, 1, "Only one first diamond page is collected");
    f.count(81, 0, "Singleton new page is collected");
    f.count(82, 3, "Duplicate page stack is preserved");
    h.assertTrue(
        BinderStorage.collect(f.player) == 0, "A second scan leaves all duplicate pages loose");
    f.valid();
    h.succeed();
  }

  private static void stowedBinder(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(80, new ItemStack(RitualsNotRolls.BINDER_ITEM.get()));
    f.put(1, page("diamond", 4));
    f.put(140, page("flint", 3));
    h.assertTrue(
        BinderStorage.collect(f.player) == 2,
        "A stowed binder collects visible and hidden pages without any visible binder");
    var handler = f.player.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, null);
    h.assertTrue(
        handler != null && BinderStorage.data(handler.getStackInSlot(80)).total() == 2,
        "Updated binder is committed to the authoritative hidden slot");
    f.count(1, 3, "Visible page duplicate count is preserved");
    f.count(140, 2, "Hidden page duplicate count is preserved");
    long revision = f.revision();
    h.assertTrue(
        BinderStorage.collect(f.player) == 0 && f.revision() == revision,
        "No-op scans do not extract and reinsert the stowed binder");
    f.valid();
    h.succeed();
  }

  private static void shrinkingBinder(GameTestHelper h) {
    var f = new Fixture(h);
    f.put(79, page("diamond", 1));
    f.put(80, new ItemStack(RitualsNotRolls.BINDER_ITEM.get()));
    h.assertTrue(
        BinderStorage.collect(f.player) == 1, "Stowed binder consumes the last singleton page");
    var handler = f.player.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, null);
    h.assertTrue(handler != null, "Expanded inventory remains available");
    int binders = 0;
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack stack = handler.getStackInSlot(slot);
      if (stack.is(RitualsNotRolls.BINDER_ITEM)) {
        binders++;
        h.assertTrue(
            BinderStorage.data(stack).total() == 1,
            "Binder restored after slot-count shrink retains the collected page");
      }
    }
    h.assertTrue(binders == 1, "Shrinking and reinsertion retain exactly one binder");
    f.valid();
    h.succeed();
  }

  private static ItemStack page(String entry, int count) {
    return RitualGameTests.page(entry).copyWithCount(count);
  }

  private static final class Fixture {
    final GameTestHelper helper;
    final ServerPlayer player;
    final Object data;
    final Object backend;
    final List<Networking.State> states = new ArrayList<>();

    @SuppressWarnings("unchecked")
    Fixture(GameTestHelper h) {
      helper = h;
      player = RitualGameTests.player(h);
      try {
        var attachment =
            (Supplier<AttachmentType<Object>>)
                Class.forName("com.cappleapple.bundlednotsiloed.data.ModAttachments")
                    .getField("PLAYER_DATA")
                    .get(null);
        data = player.getData(attachment.get());
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Bundled 1.4.5 test attachment is unavailable", failure);
      }
      call(data, "setMigratedVanillaInventory");
      backend = call(data, "inventory");
      var capacity =
          BuiltInRegistries.ATTRIBUTE
              .getHolder(ResourceLocation.parse("bundlednotsiloed:inventory_capacity"))
              .orElseThrow();
      player.getAttribute(capacity).setBaseValue(4096);
      var cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
      player.connection =
          new ServerGamePacketListenerImpl(
              h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override
            public void send(Packet<?> packet) {
              capture(packet);
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
              capture(packet);
            }
          };
    }

    void capture(Packet<?> packet) {
      if (packet instanceof ClientboundCustomPayloadPacket custom
          && custom.payload() instanceof Networking.State state) states.add(state);
    }

    void put(int slot, ItemStack stack) {
      call(
          backend,
          "replaceSyntheticSlotFromItemUse",
          new Class<?>[] {int.class, ItemStack.class},
          slot,
          stack);
    }

    BookMenu openBook() {
      var menu = new BookMenu(71, player.getInventory(), 0);
      player.containerMenu = menu;
      helper.assertTrue(menu.stillValid(player), "Fixture opens the authoritative held book");
      return menu;
    }

    long revision() {
      return ((Number) call(backend, "revision")).longValue();
    }

    void count(int slot, int expected, String message) {
      ItemStack stack =
          (ItemStack) call(backend, "syntheticStack", new Class<?>[] {int.class}, slot);
      helper.assertTrue(
          stack.getCount() == expected,
          message + ": expected " + expected + ", got " + stack.getCount());
    }

    void entries(ItemStack book, String... entries) {
      var actual = Knowledge.data(book).entries();
      helper.assertTrue(
          actual.size() == entries.length && actual.containsAll(List.of(entries)),
          "Book contains each matching new entry exactly once: " + actual);
    }

    void valid() {
      helper.assertTrue(
          (Boolean) call(backend, "validate"), "Bundled backend remains valid after gathering");
    }
  }

  private static Object call(Object target, String method) {
    return call(target, method, new Class<?>[0]);
  }

  private static Object call(
      Object target, String method, Class<?>[] parameters, Object... arguments) {
    try {
      return target.getClass().getMethod(method, parameters).invoke(target, arguments);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException cause) throw cause;
      if (failure.getCause() instanceof Error cause) throw cause;
      throw new IllegalStateException("Bundled test fixture failed: " + method, failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Bundled 1.4.5 test API unavailable: " + method, failure);
    }
  }
}
