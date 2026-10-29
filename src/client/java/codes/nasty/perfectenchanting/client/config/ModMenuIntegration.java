package codes.nasty.perfectenchanting.client.config;

import codes.nasty.perfectenchanting.PerfectConfig;
import codes.nasty.perfectenchanting.PerfectEnchanting;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.ArrayList;
import net.minecraft.client.gui.screens.Screen;

/** The config button in Mod Menu's mod list. Mod Menu is optional; without it this class is never loaded. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::createScreen;
	}

	public static Screen createScreen(Screen parent) {
		return new ConfigScreen<>(PerfectEnchanting.MOD_ID, parent, PerfectConfig.get().copy(), new PerfectConfig(), PerfectConfig::update)
			.section("costs")
			.integer("lapisPerLevel", 0, 64, c -> c.lapisPerLevel, (c, v) -> c.lapisPerLevel = v)
			.toggle("scaleCostByRarity", c -> c.scaleCostByRarity, (c, v) -> c.scaleCostByRarity = v)
			.integer("renameCost", 0, 64, c -> c.renameCost, (c, v) -> c.renameCost = v)
			.toggle("creativeIgnoresCosts", c -> c.creativeIgnoresCosts, (c, v) -> c.creativeIgnoresCosts = v)
			.section("enchantments")
			.toggle("allowIncompatibleEnchantments", c -> c.allowIncompatibleEnchantments, (c, v) -> c.allowIncompatibleEnchantments = v)
			.toggle("allowCurses", c -> c.allowCurses, (c, v) -> c.allowCurses = v)
			.list("disabledEnchantments", ConfigScreen.ID, c -> c.disabledEnchantments, (c, v) -> c.disabledEnchantments = new ArrayList<>(v));
	}
}
