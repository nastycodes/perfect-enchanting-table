package codes.nasty.perfectenchanting.test;

import codes.nasty.perfectenchanting.EnchantRules;
import codes.nasty.perfectenchanting.PerfectConfig;
import codes.nasty.perfectenchanting.PerfectEnchanting;
import codes.nasty.perfectenchanting.TableSettings;
import codes.nasty.perfectenchanting.client.PerfectEnchantingScreen;
import codes.nasty.perfectenchanting.client.config.ConfigScreen;
import codes.nasty.perfectenchanting.menu.PerfectEnchantingMenu;
import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.ModMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * End-to-end check of the table in a real client (English, survival, 1920x1080):
 * searches, picks levels with the arrows, enchants by clicking rows, renames with the keyboard, enchants a book and
 * verifies everything on the server, including the lapis that was paid. Then changes settings through the config
 * screen behind Mod Menu's config button and checks that a reopened table gets them.
 * Run with {@code gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots.
 */
public class PerfectEnchantingClientGameTest implements FabricClientGameTest {
	private static final int IMAGE_WIDTH = 232;
	private static final int IMAGE_HEIGHT = 237;
	private static final int HOTBAR_0 = PerfectEnchantingMenu.HOTBAR_START;
	private static final int HOTBAR_1 = PerfectEnchantingMenu.HOTBAR_START + 1;
	private static final int HOTBAR_2 = PerfectEnchantingMenu.HOTBAR_START + 2;

	/** Where {@link #prepare} put the table; set on the server thread before anything reads it. */
	private static volatile BlockPos pos;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> client.getLanguageManager().setSelected("en_us"));
		context.runOnClient(client -> client.reloadResourcePacks());
		context.waitFor(client -> client.getOverlay() == null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			server.runOnServer(PerfectEnchantingClientGameTest::checkData);
			server.runOnServer(PerfectEnchantingClientGameTest::prepare);
			context.waitForScreen(PerfectEnchantingScreen.class);
			context.waitTicks(5);
			screenshot(context, "perfect_01_empty");

			quickMove(context, HOTBAR_0);
			quickMove(context, HOTBAR_1);
			context.waitTicks(5);
			int lapis = server.computeOnServer(PerfectEnchantingClientGameTest::lapis);
			assertEquals(64, lapis, "lapis in the lapis slot");
			hoverRow(context, 0);
			context.waitTicks(3);
			screenshot(context, "perfect_02_sword_list");

			// Search by id works in every language; Sharpness starts at its highest level, V.
			search(context, "sharp");
			hoverRow(context, 0);
			context.waitTicks(3);
			screenshot(context, "perfect_03_search_sharpness");
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			assertEquals(5, levelOf(server, Enchantments.SHARPNESS), "Sharpness after one click");
			lapis -= 5 * anvilCost(server, Enchantments.SHARPNESS);
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after Sharpness V");

			// Smite conflicts with Sharpness: clicking it must not change or cost anything.
			assertTrue(server.computeOnServer(s -> EnchantRules.option(menu(s).getInputItem(), holder(s, Enchantments.SMITE), menu(s).getSettings()).state())
				== EnchantRules.State.INCOMPATIBLE, "Smite is listed as incompatible");
			search(context, "smite");
			hoverRow(context, 0);
			context.waitTicks(3);
			screenshot(context, "perfect_04_conflict");
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			assertEquals(0, levelOf(server, Enchantments.SMITE), "Smite on a sword with Sharpness");
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after clicking Smite");

			// Unbreaking: one arrow click down to II and apply, then raise to III and only pay for the added level.
			search(context, "unbreaking");
			clickGui(context, PerfectEnchantingScreen.LIST_X + PerfectEnchantingScreen.LEVEL_DOWN_X + 6, rowY(0) + 5);
			context.waitTicks(2);
			hoverRow(context, 0);
			context.waitTicks(2);
			screenshot(context, "perfect_05_level_picked");
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			assertEquals(2, levelOf(server, Enchantments.UNBREAKING), "Unbreaking after picking II");
			lapis -= 2 * anvilCost(server, Enchantments.UNBREAKING);
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after Unbreaking II");
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			assertEquals(3, levelOf(server, Enchantments.UNBREAKING), "Unbreaking after raising it");
			lapis -= anvilCost(server, Enchantments.UNBREAKING);
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after Unbreaking II -> III");

			search(context, "mending");
			hoverRow(context, 0);
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			assertEquals(1, levelOf(server, Enchantments.MENDING), "Mending");
			lapis -= anvilCost(server, Enchantments.MENDING);
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after Mending");

			search(context, "");
			hoverRow(context, 2);
			context.waitTicks(3);
			screenshot(context, "perfect_06_enchanted");

			// Rename with the keyboard: clear, type, Enter. The inventory key (E) must not close the screen while typing.
			clickGui(context, PerfectEnchantingScreen.RENAME_BUTTON_X - 20, PerfectEnchantingScreen.RENAME_BUTTON_Y + 8);
			clearFocusedField(context);
			context.getInput().pressKey(InputConstants.KEY_E);
			context.waitTicks(2);
			assertTrue(context.computeOnClient(client -> client.screen instanceof PerfectEnchantingScreen), "screen still open after pressing E in the name field");
			context.getInput().typeChars("Excalibur");
			context.waitTicks(2);
			hoverGui(context, PerfectEnchantingScreen.RENAME_BUTTON_X + 8, PerfectEnchantingScreen.RENAME_BUTTON_Y + 8);
			context.waitTicks(2);
			screenshot(context, "perfect_07_rename_typed");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(5);
			String name = server.computeOnServer(s -> menu(s).getInputItem().getHoverName().getString());
			assertTrue("Excalibur".equals(name), "item name after renaming: " + name);
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after renaming (free by default)");

			// A plain book becomes an enchanted book with the enchantment stored.
			quickMove(context, PerfectEnchantingMenu.ITEM_SLOT);
			quickMove(context, HOTBAR_2);
			context.waitTicks(5);
			search(context, "mending");
			hoverRow(context, 0);
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			ItemStack book = server.computeOnServer(s -> menu(s).getInputItem().copy());
			assertTrue(book.is(Items.ENCHANTED_BOOK), "book turned into " + book);
			assertEquals(1, storedLevel(server, book, Enchantments.MENDING), "Mending stored on the book");
			lapis -= anvilCost(server, Enchantments.MENDING);
			assertEquals(lapis, server.computeOnServer(PerfectEnchantingClientGameTest::lapis), "lapis after the book");
			search(context, "");
			context.waitTicks(3);
			screenshot(context, "perfect_08_book");

			// Closing hands everything back.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
			context.waitTicks(5);
			ItemStack sword = server.computeOnServer(s -> find(player(s).getInventory(), Items.NETHERITE_SWORD));
			assertTrue("Excalibur".equals(sword.getHoverName().getString()), "returned sword name: " + sword.getHoverName().getString());
			assertEquals(5, storedLevel(server, sword, Enchantments.SHARPNESS), "returned sword Sharpness");
			assertEquals(3, storedLevel(server, sword, Enchantments.UNBREAKING), "returned sword Unbreaking");
			assertEquals(1, storedLevel(server, sword, Enchantments.MENDING), "returned sword Mending");
			assertTrue(!server.computeOnServer(s -> find(player(s).getInventory(), Items.ENCHANTED_BOOK)).isEmpty(), "enchanted book returned");
			int lapisLeft = lapis;
			assertEquals(lapisLeft, server.computeOnServer(s -> find(player(s).getInventory(), Items.LAPIS_LAZULI).getCount()), "lapis returned");
			PerfectEnchanting.LOGGER.info("[gametest] all checks passed, {} lapis left", lapisLeft);

			// Look at the placed table for a screenshot of the model and the floating book.
			server.runCommand("time set noon");
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.connection.teleport(player.getX(), player.getY(), player.getZ(), -90.0F, 30.0F);
			});
			context.waitTicks(40);
			screenshot(context, "perfect_09_block");

			checkConfigScreen(context, server);
			PerfectEnchanting.LOGGER.info("[gametest] config screen checks passed");
		}
	}

	/**
	 * Opens the settings the way Mod Menu's config button does, changes the lapis cost and disables Mending with the
	 * list editor, saves, and checks that the next table the player opens offers exactly that.
	 */
	private static void checkConfigScreen(ClientGameTestContext context, TestServerContext server) {
		context.setScreen(() -> ModMenu.getConfigScreen(PerfectEnchanting.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(5);
		EditBox lapisPerLevel = widget(context, EditBox.class, "config.perfectenchanting.lapisPerLevel");
		hoverWidget(context, lapisPerLevel);
		screenshot(context, "perfect_10_config");

		// Values outside 0..64 turn the field red and block saving.
		context.runOnClient(client -> lapisPerLevel.setValue("65"));
		context.waitTicks(2);
		assertTrue(!widget(context, Button.class, "gui.done").active, "Done is blocked while a field is invalid");
		context.runOnClient(client -> lapisPerLevel.setValue("2"));
		context.waitTicks(2);
		assertTrue(widget(context, Button.class, "gui.done").active, "Done works again with a valid number");

		// The disabled enchantments open in a text editor; the list is empty by default.
		Button disabled = widget(context, Button.class, "config.perfectenchanting.ui.edit");
		scrollTo(context, disabled);
		clickWidget(context, disabled);
		context.waitFor(client -> !(client.screen instanceof ConfigScreen));
		context.getInput().typeChars("Minecraft:Mending");
		context.waitTicks(2);
		assertTrue(!widget(context, Button.class, "gui.done").active, "an invalid id blocks Done in the list editor");
		clearFocusedField(context);
		context.getInput().typeChars("minecraft:mending");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.getInput().typeChars("minecraft:thorns");
		context.waitTicks(3);
		screenshot(context, "perfect_11_config_list");
		context.clickScreenButton("gui.done");
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(3);
		context.clickScreenButton("gui.done");
		context.waitForScreen(null);
		context.waitTicks(10);

		assertEquals(2, server.computeOnServer(s -> PerfectConfig.get().lapisPerLevel), "lapis per level saved through the screen");
		assertTrue(server.computeOnServer(s -> PerfectConfig.get().disabledEnchantments).equals(List.of("minecraft:mending", "minecraft:thorns")),
			"disabled enchantments saved through the screen");
		String file = readConfigFile();
		assertTrue(file.contains("\"lapisPerLevel\": 2") && file.contains("minecraft:mending"), "the screen wrote config/perfectenchanting.json");

		// A table opened afterwards sends the new values to the client.
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.openMenu(player.level().getBlockState(pos).getMenuProvider(player.level(), pos));
		});
		context.waitForScreen(PerfectEnchantingScreen.class);
		TableSettings settings = context.computeOnClient(client -> ((PerfectEnchantingScreen) client.screen).getMenu().getSettings());
		assertEquals(2, settings.lapisPerLevel(), "lapis per level the client got with the table");
		assertTrue(settings.disabledEnchantments().equals(List.of(Enchantments.MENDING, Enchantments.THORNS)), "disabled enchantments the client got: " + settings.disabledEnchantments());
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// Esc with unsaved changes asks first; discarding keeps the saved values.
		context.setScreen(() -> ModMenu.getConfigScreen(PerfectEnchanting.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		EditBox reopened = widget(context, EditBox.class, "config.perfectenchanting.lapisPerLevel");
		assertTrue(context.computeOnClient(client -> reopened.getValue()).equals("2"), "the screen shows the saved value");
		context.runOnClient(client -> reopened.setValue("9"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(ConfirmScreen.class);
		context.clickScreenButton("config.perfectenchanting.ui.discard.yes");
		context.waitForScreen(null);
		context.waitTicks(5);
		assertEquals(2, server.computeOnServer(s -> PerfectConfig.get().lapisPerLevel), "discarded changes are not saved");

		server.runOnServer(s -> PerfectConfig.update(new PerfectConfig()));
	}

	private static String readConfigFile() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve(PerfectEnchanting.MOD_ID + ".json"));
		} catch (IOException e) {
			throw new AssertionError("config file not readable", e);
		}
	}

	/** Finds a widget of the current screen, also inside lists, by the translation key of its message. */
	private static <T extends AbstractWidget> T widget(ClientGameTestContext context, Class<T> type, String translationKey) {
		return context.computeOnClient(client -> {
			List<GuiEventListener> todo = new ArrayList<>(client.screen.children());
			while (!todo.isEmpty()) {
				GuiEventListener listener = todo.removeLast();
				if (type.isInstance(listener) && ((AbstractWidget) listener).getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals(translationKey)) {
					return type.cast(listener);
				}
				if (listener instanceof ContainerEventHandler container) {
					todo.addAll(container.children());
				}
			}
			throw new AssertionError("no " + type.getSimpleName() + " with message " + translationKey);
		});
	}

	/** Takes a screenshot without recipe/advancement toasts and chat lines, so only the GUI is on it. */
	private static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.gui.getChat().clearMessages(false);
		});
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	/**
	 * Scrolls the config list down until the widget is fully visible. Rows only get their position when they are drawn,
	 * so a row below the visible part still reports its old position until it scrolls into view.
	 */
	private static void scrollTo(ClientGameTestContext context, AbstractWidget widget) {
		double[] center = context.computeOnClient(client -> new double[] {client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight() / 2.0});
		context.getInput().setCursorPos(center[0], center[1]);
		for (int i = 0; i < 60 && !context.computeOnClient(client -> widget.getY() >= 40 && widget.getBottom() <= client.getWindow().getGuiScaledHeight() - 40); i++) {
			context.getInput().scroll(-1);
			context.waitTicks(1);
		}
	}

	private static void hoverWidget(ClientGameTestContext context, AbstractWidget widget) {
		double[] cursor = context.computeOnClient(client -> {
			double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(3);
	}

	private static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
		hoverWidget(context, widget);
		context.getInput().pressMouse(0);
		context.waitTicks(3);
	}

	private static void checkData(MinecraftServer server) {
		ResourceKey<Recipe<?>> recipe = ResourceKey.create(Registries.RECIPE, PerfectEnchanting.TABLE_ID);
		assertTrue(server.getRecipeManager().byKey(recipe).isPresent(), "crafting recipe is loaded");
		var lootTable = ResourceKey.create(Registries.LOOT_TABLE, PerfectEnchanting.id("blocks/perfect_enchanting_table"));
		assertTrue(server.reloadableRegistries().getLootTable(lootTable) != LootTable.EMPTY, "block loot table is loaded");
		assertTrue(BlockEntityType.ENCHANTING_TABLE.isValid(PerfectEnchanting.PERFECT_ENCHANTING_TABLE.defaultBlockState()), "table uses the enchanting table block entity");
	}

	private static void prepare(MinecraftServer server) {
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
		player.getInventory().setItem(1, new ItemStack(Items.LAPIS_LAZULI, 64));
		player.getInventory().setItem(2, new ItemStack(Items.BOOK));

		pos = player.blockPosition().east(2);
		player.level().setBlockAndUpdate(pos, PerfectEnchanting.PERFECT_ENCHANTING_TABLE.defaultBlockState());
		assertTrue(player.level().getBlockEntity(pos) instanceof EnchantingTableBlockEntity, "block entity for the floating book");
		player.openMenu(player.level().getBlockState(pos).getMenuProvider(player.level(), pos));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static PerfectEnchantingMenu menu(MinecraftServer server) {
		return (PerfectEnchantingMenu) player(server).containerMenu;
	}

	private static int lapis(MinecraftServer server) {
		return menu(server).getLapisCount();
	}

	private static Holder<Enchantment> holder(MinecraftServer server, ResourceKey<Enchantment> key) {
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static int anvilCost(TestServerContext server, ResourceKey<Enchantment> key) {
		return server.computeOnServer(s -> holder(s, key).value().getAnvilCost());
	}

	private static int levelOf(TestServerContext server, ResourceKey<Enchantment> key) {
		return server.computeOnServer(s -> EnchantmentHelper.getItemEnchantmentLevel(holder(s, key), menu(s).getInputItem()));
	}

	/** Level on an item or stored on an enchanted book. */
	private static int storedLevel(TestServerContext server, ItemStack stack, ResourceKey<Enchantment> key) {
		return server.computeOnServer(s -> EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(holder(s, key)));
	}

	private static ItemStack find(Inventory inventory, net.minecraft.world.item.Item item) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				return inventory.getItem(i).copy();
			}
		}
		return ItemStack.EMPTY;
	}

	/** Shift-clicks a menu slot through the same path a real click takes. */
	private static void quickMove(ClientGameTestContext context, int slot) {
		context.runOnClient(client -> client.gameMode.handleInventoryMouseClick(
			client.player.containerMenu.containerId, slot, 0, ClickType.QUICK_MOVE, client.player));
	}

	/** Clicks into the search field and replaces its text by typing. */
	private static void search(ClientGameTestContext context, String text) {
		clickGui(context, PerfectEnchantingScreen.SEARCH_FIELD_X + 60, PerfectEnchantingScreen.SEARCH_FIELD_Y + 8);
		clearFocusedField(context);
		context.getInput().typeChars(text);
		context.waitTicks(2);
	}

	/**
	 * Empties the focused text field with End and Backspace. Ctrl+A would be shorter, but the test input
	 * sends key events without modifiers, so the text field never sees Ctrl.
	 */
	private static void clearFocusedField(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_END);
		for (int i = 0; i < 24; i++) {
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		}
	}

	private static int rowY(int row) {
		return PerfectEnchantingScreen.LIST_Y + row * PerfectEnchantingScreen.ROW_HEIGHT;
	}

	/** Puts the cursor on the name of a visible row, the part of the row that enchants when clicked. */
	private static void hoverRow(ClientGameTestContext context, int row) {
		hoverGui(context, PerfectEnchantingScreen.LIST_X + 40, rowY(row) + 9);
	}

	private static void clickGui(ClientGameTestContext context, int guiX, int guiY) {
		hoverGui(context, guiX, guiY);
		context.getInput().pressMouse(0);
		context.waitTicks(1);
	}

	/** Puts the cursor on a point given in the table GUI's own coordinates. */
	private static void hoverGui(ClientGameTestContext context, int guiX, int guiY) {
		double[] pos = context.computeOnClient(client -> {
			var window = client.getWindow();
			double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
			int left = (window.getGuiScaledWidth() - IMAGE_WIDTH) / 2;
			int top = (window.getGuiScaledHeight() - IMAGE_HEIGHT) / 2;
			return new double[] {(left + guiX + 0.5) * scale, (top + guiY + 0.5) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertEquals(int expected, int actual, String what) {
		if (expected != actual) {
			throw new AssertionError(what + ": expected " + expected + ", got " + actual);
		}
	}
}
