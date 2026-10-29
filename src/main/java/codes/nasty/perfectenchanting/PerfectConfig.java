package codes.nasty.perfectenchanting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Server-side settings, stored in {@code config/perfectenchanting.json}.
 * Unknown keys are ignored and missing keys fall back to the defaults below,
 * so the file is rewritten on every successful load to pick up new options.
 * Clients get the values they need when they open a table, see {@link TableSettings}.
 */
public final class PerfectConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(PerfectEnchanting.MOD_ID + ".json");
	private static PerfectConfig instance = new PerfectConfig();

	/** Lapis lazuli per enchantment level. 0 makes enchanting free. */
	public int lapisPerLevel = 1;
	/**
	 * Multiplies the cost by the enchantment's rarity (its vanilla anvil cost: 1 common, 2 uncommon, 4 rare, 8 very rare),
	 * so Sharpness V costs 5 lapis and Silk Touch 8. Off: every level costs exactly {@link #lapisPerLevel}.
	 */
	public boolean scaleCostByRarity = true;
	/** Lapis lazuli for renaming an item. */
	public int renameCost = 0;
	/** Allows combinations vanilla forbids, e.g. Sharpness with Smite or Mending with Infinity. */
	public boolean allowIncompatibleEnchantments = false;
	public boolean allowCurses = true;
	public boolean creativeIgnoresCosts = true;
	/** Enchantment ids that the table never offers, e.g. "minecraft:mending". */
	public List<String> disabledEnchantments = new ArrayList<>();

	public static PerfectConfig get() {
		return instance;
	}

	/** Loads the config at startup; a broken file is reported and the defaults are used instead. */
	public static void load() {
		try {
			reload();
		} catch (IOException | JsonParseException e) {
			PerfectEnchanting.LOGGER.error("Could not read {}, using default settings", PATH, e);
			instance = new PerfectConfig();
		}
	}

	public static void reload() throws IOException {
		PerfectConfig config = new PerfectConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				PerfectConfig parsed = GSON.fromJson(reader, PerfectConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			}
		}
		config.sanitize();
		instance = config;
		save(config);
	}

	/**
	 * Activates settings changed in the config screen and writes them to the file.
	 * Clients get the new values the next time they open a table.
	 */
	public static void update(PerfectConfig config) {
		config.sanitize();
		instance = config;
		save(config);
	}

	/** A deep copy, for the config screen to change. */
	public PerfectConfig copy() {
		return GSON.fromJson(GSON.toJsonTree(this), PerfectConfig.class);
	}

	private static void save(PerfectConfig config) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			PerfectEnchanting.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	/** The part of the config that clients need to show the same list and costs as the server. */
	public TableSettings toSettings() {
		List<ResourceKey<net.minecraft.world.item.enchantment.Enchantment>> disabled = new ArrayList<>();
		for (String id : disabledEnchantments) {
			Identifier parsed = Identifier.tryParse(id);
			if (parsed == null) {
				PerfectEnchanting.LOGGER.warn("Ignoring invalid enchantment id '{}' in disabledEnchantments", id);
			} else {
				disabled.add(ResourceKey.create(Registries.ENCHANTMENT, parsed));
			}
		}
		return new TableSettings(lapisPerLevel, scaleCostByRarity, renameCost, allowIncompatibleEnchantments, allowCurses, creativeIgnoresCosts, disabled);
	}

	private void sanitize() {
		lapisPerLevel = clamp(lapisPerLevel, 0, 64);
		renameCost = clamp(renameCost, 0, 64);
		if (disabledEnchantments == null) {
			disabledEnchantments = new ArrayList<>();
		}
		disabledEnchantments.removeIf(Objects::isNull);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
