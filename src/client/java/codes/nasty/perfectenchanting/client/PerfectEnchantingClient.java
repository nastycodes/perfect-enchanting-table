package codes.nasty.perfectenchanting.client;

import codes.nasty.perfectenchanting.PerfectEnchanting;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class PerfectEnchantingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(PerfectEnchanting.PERFECT_ENCHANTING_MENU, PerfectEnchantingScreen::new);
	}
}
