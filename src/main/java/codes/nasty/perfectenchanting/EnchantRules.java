package codes.nasty.perfectenchanting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * Decides which enchantments the table offers for an item and what they cost.
 * The client uses it to build the list, the server to validate every request, so both always agree.
 */
public final class EnchantRules {
	/** Longest name an item can get, same as the anvil. */
	public static final int MAX_NAME_LENGTH = 50;

	public enum State {
		/** Can be added or raised. */
		AVAILABLE,
		/** The item already has the highest level. */
		MAXED,
		/** Conflicts with an enchantment on the item (e.g. Smite on a sword with Sharpness). */
		INCOMPATIBLE
	}

	/**
	 * One enchantment the item supports.
	 *
	 * @param conflicts enchantments on the item that rule this one out (empty unless {@link State#INCOMPATIBLE})
	 */
	public record Option(Holder<Enchantment> enchantment, int currentLevel, State state, List<Holder<Enchantment>> conflicts, int costPerLevel) {
		public int maxLevel() {
			return this.enchantment.value().getMaxLevel();
		}

		/** Lowest level that can be picked: one above the current level. */
		public int minTargetLevel() {
			return Math.max(this.currentLevel + 1, this.enchantment.value().getMinLevel());
		}

		/** Only the levels that are added get paid for, so raising III to V costs two levels. */
		public int cost(int targetLevel) {
			return this.costPerLevel * (targetLevel - this.currentLevel);
		}

		public boolean isCurse() {
			return this.enchantment.is(EnchantmentTags.CURSE);
		}
	}

	private EnchantRules() {
	}

	/** Books take every enchantment; a plain book becomes an enchanted book, like at the vanilla table. */
	public static boolean isBook(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
	}

	/** Every enchantment the item supports, in registry order. */
	public static List<Option> options(ItemStack stack, HolderLookup<Enchantment> enchantments, TableSettings settings) {
		List<Option> options = new ArrayList<>();
		if (stack.isEmpty()) {
			return options;
		}
		enchantments.listElements().forEach(enchantment -> {
			Option option = option(stack, enchantment, settings);
			if (option != null) {
				options.add(option);
			}
		});
		return options;
	}

	/** The option for one enchantment, or null if the table does not offer it for this item. */
	public static @Nullable Option option(ItemStack stack, Holder<Enchantment> enchantment, TableSettings settings) {
		if (stack.isEmpty() || !isOffered(stack, enchantment, settings)) {
			return null;
		}
		ItemEnchantments present = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		int currentLevel = present.getLevel(enchantment);

		List<Holder<Enchantment>> conflicts = new ArrayList<>();
		for (Holder<Enchantment> other : present.keySet()) {
			if (!other.equals(enchantment) && !Enchantment.areCompatible(other, enchantment)) {
				conflicts.add(other);
			}
		}

		State state;
		if (currentLevel >= enchantment.value().getMaxLevel()) {
			state = State.MAXED;
		} else if (!conflicts.isEmpty() && !settings.allowIncompatible()) {
			state = State.INCOMPATIBLE;
		} else {
			state = State.AVAILABLE;
		}
		return new Option(enchantment, currentLevel, state, List.copyOf(conflicts), costPerLevel(enchantment, settings));
	}

	private static boolean isOffered(ItemStack stack, Holder<Enchantment> enchantment, TableSettings settings) {
		if (enchantment.unwrapKey().map(settings.disabledEnchantments()::contains).orElse(false)) {
			return false;
		}
		if (!settings.allowCurses() && enchantment.is(EnchantmentTags.CURSE)) {
			return false;
		}
		return isBook(stack) || enchantment.value().canEnchant(stack);
	}

	/** Whether renaming to this name would change anything; a blank name stands for "remove the custom name", like at the anvil. */
	public static boolean isRenameChange(ItemStack stack, String name) {
		if (stack.isEmpty()) {
			return false;
		}
		if (StringUtil.isBlank(name)) {
			return stack.has(DataComponents.CUSTOM_NAME);
		}
		return !name.equals(stack.getHoverName().getString());
	}

	public static int costPerLevel(Holder<Enchantment> enchantment, TableSettings settings) {
		int rarity = settings.scaleCostByRarity() ? Math.max(1, enchantment.value().getAnvilCost()) : 1;
		return settings.lapisPerLevel() * rarity;
	}

	/** "Sharpness V"; single-level enchantments like Mending get no numeral. */
	public static MutableComponent enchantmentName(Holder<Enchantment> enchantment, int level) {
		MutableComponent name = enchantment.value().description().copy();
		if (level != 1 || enchantment.value().getMaxLevel() != 1) {
			name.append(CommonComponents.SPACE).append(levelName(level));
		}
		return name;
	}

	/** Roman numeral; levels without a translation (above X, e.g. from other mods) fall back to digits. */
	public static Component levelName(int level) {
		return Component.translatableWithFallback("enchantment.level." + level, Integer.toString(level));
	}
}
