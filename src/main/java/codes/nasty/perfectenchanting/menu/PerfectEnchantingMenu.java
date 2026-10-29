package codes.nasty.perfectenchanting.menu;

import codes.nasty.perfectenchanting.EnchantRules;
import codes.nasty.perfectenchanting.PerfectEnchanting;
import codes.nasty.perfectenchanting.TableSettings;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * Item slot, lapis slot and the player inventory. The screen lists the enchantments from
 * {@link EnchantRules} and sends the player's choice by id; {@link #applyEnchantment} and
 * {@link #renameItem} check everything again on the server before anything is paid or changed.
 */
public class PerfectEnchantingMenu extends AbstractContainerMenu {
	public static final int ITEM_SLOT = 0;
	public static final int LAPIS_SLOT = 1;
	public static final int INV_START = 2;
	public static final int HOTBAR_START = 29;
	private static final int HOTBAR_END = 38;

	/** Slot positions in GUI coordinates, shared with the screen and its texture. */
	public static final int ITEM_X = 8;
	public static final int ITEM_Y = 17;
	public static final int LAPIS_X = 8;
	public static final int LAPIS_Y = 35;
	public static final int INVENTORY_X = 36;
	public static final int INVENTORY_Y = 155;

	private static final Identifier EMPTY_SLOT_LAPIS_LAZULI = Identifier.withDefaultNamespace("container/slot/lapis_lazuli");

	private final ContainerLevelAccess access;
	private final TableSettings settings;
	private final Container tableSlots = new SimpleContainer(2) {
		@Override
		public void setChanged() {
			super.setChanged();
			PerfectEnchantingMenu.this.slotsChanged(this);
		}
	};

	/** Client side: the settings come with the open-screen packet. */
	public PerfectEnchantingMenu(int containerId, Inventory inventory, TableSettings settings) {
		this(containerId, inventory, ContainerLevelAccess.NULL, settings);
	}

	public PerfectEnchantingMenu(int containerId, Inventory inventory, ContainerLevelAccess access, TableSettings settings) {
		super(PerfectEnchanting.PERFECT_ENCHANTING_MENU, containerId);
		this.access = access;
		this.settings = settings;

		this.addSlot(new Slot(this.tableSlots, ITEM_SLOT, ITEM_X, ITEM_Y) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(this.tableSlots, LAPIS_SLOT, LAPIS_X, LAPIS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.LAPIS_LAZULI);
			}

			@Override
			public Identifier getNoItemIcon() {
				return EMPTY_SLOT_LAPIS_LAZULI;
			}
		});
		this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public TableSettings getSettings() {
		return this.settings;
	}

	public ItemStack getInputItem() {
		return this.tableSlots.getItem(ITEM_SLOT);
	}

	public int getLapisCount() {
		ItemStack lapis = this.tableSlots.getItem(LAPIS_SLOT);
		return lapis.is(Items.LAPIS_LAZULI) ? lapis.getCount() : 0;
	}

	public boolean costsWaivedFor(Player player) {
		return this.settings.creativeIgnoresCosts() && player.hasInfiniteMaterials();
	}

	public boolean canAfford(Player player, int cost) {
		return cost <= 0 || this.costsWaivedFor(player) || this.getLapisCount() >= cost;
	}

	private void pay(Player player, int cost) {
		if (cost > 0 && !this.costsWaivedFor(player)) {
			this.tableSlots.getItem(LAPIS_SLOT).shrink(cost);
		}
	}

	/** Adds the enchantment at the given level, or raises it to that level. Returns false if the request is not allowed. */
	public boolean applyEnchantment(ServerPlayer player, ResourceKey<Enchantment> key, int level) {
		ItemStack stack = this.getInputItem();
		Holder<Enchantment> enchantment = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
		if (stack.isEmpty() || enchantment == null) {
			return false;
		}
		EnchantRules.Option option = EnchantRules.option(stack, enchantment, this.settings);
		if (option == null
			|| option.state() != EnchantRules.State.AVAILABLE
			|| level < option.minTargetLevel()
			|| level > option.maxLevel()) {
			return false;
		}
		int cost = option.cost(level);
		if (!this.canAfford(player, cost)) {
			return false;
		}

		this.pay(player, cost);
		if (stack.is(Items.BOOK)) {
			stack = stack.transmuteCopy(Items.ENCHANTED_BOOK);
			this.tableSlots.setItem(ITEM_SLOT, stack);
		}
		EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(enchantment, level));
		player.awardStat(Stats.ENCHANT_ITEM);
		CriteriaTriggers.ENCHANTED_ITEM.trigger(player, stack, 0);

		this.tableSlots.setChanged();
		this.access.execute(PerfectEnchantingMenu::playEnchantEffects);
		return true;
	}

	/** Renames like the anvil: a blank name removes the custom name. Returns false if nothing changed. */
	public boolean renameItem(ServerPlayer player, String requestedName) {
		ItemStack stack = this.getInputItem();
		String name = StringUtil.filterText(requestedName);
		if (name.length() > EnchantRules.MAX_NAME_LENGTH
			|| !EnchantRules.isRenameChange(stack, name)
			|| !this.canAfford(player, this.settings.renameCost())) {
			return false;
		}

		this.pay(player, this.settings.renameCost());
		if (StringUtil.isBlank(name)) {
			stack.remove(DataComponents.CUSTOM_NAME);
		} else {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		}

		this.tableSlots.setChanged();
		this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F));
		return true;
	}

	private static void playEnchantEffects(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		if (level instanceof ServerLevel serverLevel) {
			double x = pos.getX() + 0.5;
			double y = pos.getY() + 1.2;
			double z = pos.getZ() + 0.5;
			serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y + 0.3, z, 40, 0.3, 0.3, 0.3, 1.0);
			serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 12, 0.3, 0.2, 0.3, 0.1);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, PerfectEnchanting.PERFECT_ENCHANTING_TABLE);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> this.clearContainer(player, this.tableSlots));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < INV_START) {
			if (!this.moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(Items.LAPIS_LAZULI)) {
			if (!this.moveItemStackTo(stack, LAPIS_SLOT, LAPIS_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, ITEM_SLOT, ITEM_SLOT + 1, false)) {
			// Like the vanilla table: a full item slot stops the shift-click instead of shuffling the stack around.
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}
}
