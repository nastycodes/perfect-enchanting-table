package codes.nasty.perfectenchanting;

import codes.nasty.perfectenchanting.block.PerfectEnchantingTableBlock;
import codes.nasty.perfectenchanting.menu.PerfectEnchantingMenu;
import codes.nasty.perfectenchanting.network.TablePackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerfectEnchanting implements ModInitializer {
	public static final String MOD_ID = "perfectenchanting";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier TABLE_ID = id("perfect_enchanting_table");
	private static final ResourceKey<Block> TABLE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, TABLE_ID);
	private static final ResourceKey<Item> TABLE_ITEM_KEY = ResourceKey.create(Registries.ITEM, TABLE_ID);

	/** Same properties as the vanilla enchanting table, only in lapis blue. */
	public static final Block PERFECT_ENCHANTING_TABLE = Registry.register(
		BuiltInRegistries.BLOCK,
		TABLE_BLOCK_KEY,
		new PerfectEnchantingTableBlock(BlockBehaviour.Properties.of()
			.setId(TABLE_BLOCK_KEY)
			.mapColor(MapColor.LAPIS)
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresCorrectToolForDrops()
			.lightLevel(state -> 7)
			.strength(5.0F, 1200.0F))
	);

	public static final Item PERFECT_ENCHANTING_TABLE_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		TABLE_ITEM_KEY,
		new BlockItem(PERFECT_ENCHANTING_TABLE, new Item.Properties().setId(TABLE_ITEM_KEY).useBlockDescriptionPrefix().rarity(Rarity.RARE))
	);

	public static final MenuType<PerfectEnchantingMenu> PERFECT_ENCHANTING_MENU = Registry.register(
		BuiltInRegistries.MENU,
		TABLE_ID,
		new ExtendedScreenHandlerType<>(PerfectEnchantingMenu::new, TableSettings.STREAM_CODEC)
	);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PerfectConfig.load();
		// The table reuses the vanilla block entity for the floating book.
		BlockEntityType.ENCHANTING_TABLE.addSupportedBlock(PERFECT_ENCHANTING_TABLE);
		TablePackets.register();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(entries -> entries.addAfter(Items.ENCHANTING_TABLE, PERFECT_ENCHANTING_TABLE_ITEM));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal(MOD_ID)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(context -> {
					try {
						PerfectConfig.reload();
					} catch (Exception e) {
						context.getSource().sendFailure(Component.translatable("perfectenchanting.command.reload_failed", e.getMessage()));
						return 0;
					}
					context.getSource().sendSuccess(() -> Component.translatable("perfectenchanting.command.reloaded"), true);
					return 1;
				}))
		));
	}
}
