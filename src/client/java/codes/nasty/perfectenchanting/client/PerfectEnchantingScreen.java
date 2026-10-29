package codes.nasty.perfectenchanting.client;

import codes.nasty.perfectenchanting.EnchantRules;
import codes.nasty.perfectenchanting.EnchantRules.Option;
import codes.nasty.perfectenchanting.EnchantRules.State;
import codes.nasty.perfectenchanting.PerfectEnchanting;
import codes.nasty.perfectenchanting.menu.PerfectEnchantingMenu;
import codes.nasty.perfectenchanting.network.TablePackets;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Rename field, search field and a scrollable list of every enchantment the item supports.
 * Each row has a level picker (arrows or mouse wheel, starts at the highest level); clicking the rest of the
 * row asks the server to apply that level. The list is built from {@link EnchantRules} with the settings the
 * server sent along, so it shows exactly what the server will accept.
 */
public class PerfectEnchantingScreen extends AbstractContainerScreen<PerfectEnchantingMenu> {
	private static final Identifier TEXTURE = PerfectEnchanting.id("textures/gui/container/perfect_enchanting_table.png");

	private static final int RENAME_FIELD_X = 28;
	private static final int RENAME_FIELD_Y = 17;
	public static final int RENAME_BUTTON_X = 208;
	public static final int RENAME_BUTTON_Y = 17;
	private static final int BUTTON_SIZE = 16;
	public static final int SEARCH_FIELD_X = 28;
	public static final int SEARCH_FIELD_Y = 35;

	public static final int LIST_X = 8;
	public static final int LIST_Y = 55;
	private static final int LIST_WIDTH = 200;
	public static final int ROW_HEIGHT = 19;
	private static final int VISIBLE_ROWS = 5;

	private static final int SCROLLER_X = 211;
	private static final int SCROLLER_Y = 55;
	private static final int SCROLLER_TRACK = 95;
	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;

	/** Row-relative level picker: down arrow, numeral, up arrow. Clicks in here never apply anything. */
	public static final int LEVEL_DOWN_X = 144;
	public static final int LEVEL_UP_X = 186;
	private static final int ARROW_AREA = 12;
	private static final int PICKER_END = LEVEL_UP_X + ARROW_AREA;

	/** Sprites next to the panel in the GUI texture. */
	private static final int SPRITE_U = 232;
	private static final int SCROLLER_V = 0;
	private static final int BUTTON_V = 16;
	private static final int BUTTON_HOVER_V = 32;
	private static final int BUTTON_DISABLED_V = 48;
	private static final int LAPIS_ICON_V = 74;

	private static final int TEXT_MUTED = 0xFFA9B6DD;
	private static final int TEXT_LEVEL = 0xFFFFE08A;
	private static final int TEXT_RED = 0xFFFF6464;

	private EditBox nameBox;
	private EditBox searchBox;
	/** Levels picked with the arrows; enchantments without an entry start at their highest level. */
	private final Map<ResourceKey<Enchantment>, Integer> chosenLevels = new HashMap<>();
	private List<Option> rows = List.of();
	private int optionCount;
	private ItemStack rowsStack = ItemStack.EMPTY;
	private String rowsQuery = "";
	/** The item the name field was last filled from. */
	private ItemStack namedStack = ItemStack.EMPTY;
	private int scrollRow;
	private boolean scrolling;

	public PerfectEnchantingScreen(PerfectEnchantingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		// At most 240 high: that is all an auto GUI scale leaves at e.g. 2560x1440 or 854x480.
		this.imageWidth = 232;
		this.imageHeight = 237;
	}

	@Override
	protected void init() {
		super.init();
		// init() also runs on resize, so keep what was typed.
		String name = this.nameBox != null ? this.nameBox.getValue() : nameOf(this.menu.getInputItem());
		String query = this.searchBox != null ? this.searchBox.getValue() : "";

		this.nameBox = this.textField(RENAME_FIELD_X + 4, RENAME_FIELD_Y + 4, 170, Component.translatable("perfectenchanting.gui.rename"));
		this.nameBox.setMaxLength(EnchantRules.MAX_NAME_LENGTH);
		this.nameBox.setHint(Component.translatable("perfectenchanting.gui.name_hint").setStyle(EditBox.DEFAULT_HINT_STYLE));
		this.nameBox.setValue(name);
		this.nameBox.setEditable(!this.menu.getInputItem().isEmpty());

		this.searchBox = this.textField(SEARCH_FIELD_X + 4, SEARCH_FIELD_Y + 4, 176, Component.translatable("perfectenchanting.gui.search"));
		this.searchBox.setMaxLength(50);
		this.searchBox.setHint(Component.translatable("perfectenchanting.gui.search").setStyle(EditBox.SEARCH_HINT_STYLE));
		this.searchBox.setValue(query);
		this.searchBox.setResponder(text -> this.scrollRow = 0);
	}

	private EditBox textField(int x, int y, int width, Component narration) {
		EditBox box = new EditBox(this.font, this.leftPos + x, this.topPos + y, width, 12, narration);
		box.setBordered(false);
		box.setTextColor(-1);
		box.setTextColorUneditable(0xFF707070);
		box.setInvertHighlightedTextColor(false);
		this.addRenderableWidget(box);
		return box;
	}

	private static String nameOf(ItemStack stack) {
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		// Refill the name field only for another item or a new name, so enchanting does not wipe what is being typed.
		ItemStack stack = this.menu.getInputItem();
		if (!ItemStack.isSameItem(stack, this.namedStack) || !nameOf(stack).equals(nameOf(this.namedStack))) {
			this.nameBox.setValue(nameOf(stack));
			this.nameBox.setEditable(!stack.isEmpty());
			if (stack.isEmpty() && this.getFocused() == this.nameBox) {
				this.setFocused(null);
			}
		}
		this.namedStack = stack.copy();
		this.scrollRow = Mth.clamp(this.scrollRow, 0, this.maxScroll());
	}

	// ---------------------------------------------------------------- list

	/** Rows for the current item and search text, rebuilt only when one of them changes. */
	private List<Option> rows() {
		ItemStack stack = this.menu.getInputItem();
		String query = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		if (!ItemStack.matches(stack, this.rowsStack) || !query.equals(this.rowsQuery)) {
			HolderLookup<Enchantment> enchantments = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			List<Option> options = new ArrayList<>(EnchantRules.options(stack, enchantments, this.menu.getSettings()));
			this.optionCount = options.size();
			options.removeIf(option -> !matchesSearch(option, query));
			// Alphabetical and stable, so a row never jumps away under the cursor after enchanting; curses go last.
			options.sort(Comparator.comparing(Option::isCurse).thenComparing(PerfectEnchantingScreen::plainName, String.CASE_INSENSITIVE_ORDER));
			this.rows = options;
			this.rowsStack = stack.copy();
			this.rowsQuery = query;
		}
		return this.rows;
	}

	private static String plainName(Option option) {
		return option.enchantment().value().description().getString();
	}

	/** Matches the translated name and the id, so "sharp" finds Sharpness in every language. */
	private static boolean matchesSearch(Option option, String query) {
		if (query.isEmpty()) {
			return true;
		}
		String id = option.enchantment().unwrapKey().map(key -> key.identifier().toString()).orElse("");
		return plainName(option).toLowerCase(Locale.ROOT).contains(query) || id.contains(query);
	}

	private static ResourceKey<Enchantment> key(Option option) {
		return option.enchantment().unwrapKey().orElseThrow();
	}

	private int chosenLevel(Option option) {
		int level = this.chosenLevels.getOrDefault(key(option), option.maxLevel());
		return Mth.clamp(level, option.minTargetLevel(), option.maxLevel());
	}

	private void setChosenLevel(Option option, int level) {
		int clamped = Mth.clamp(level, option.minTargetLevel(), option.maxLevel());
		if (clamped != this.chosenLevel(option)) {
			this.chosenLevels.put(key(option), clamped);
			this.playClickSound();
		}
	}

	private boolean costsWaived() {
		return this.menu.costsWaivedFor(this.minecraft.player);
	}

	private boolean canAfford(int cost) {
		return this.menu.canAfford(this.minecraft.player, cost);
	}

	private boolean canRename() {
		return EnchantRules.isRenameChange(this.menu.getInputItem(), this.nameBox.getValue())
			&& this.canAfford(this.menu.getSettings().renameCost());
	}

	private void submitRename() {
		if (this.canRename()) {
			ClientPlayNetworking.send(new TablePackets.RenameItem(this.menu.containerId, this.nameBox.getValue()));
			this.setFocused(null);
		}
	}

	private void playClickSound() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private int maxScroll() {
		return Math.max(0, this.rows().size() - VISIBLE_ROWS);
	}

	/** Index of the row under the mouse, or -1. */
	private int rowAt(double mouseX, double mouseY) {
		double relX = mouseX - (this.leftPos + LIST_X);
		double relY = mouseY - (this.topPos + LIST_Y);
		if (relX < 0 || relX >= LIST_WIDTH || relY < 0 || relY >= VISIBLE_ROWS * ROW_HEIGHT) {
			return -1;
		}
		int index = this.scrollRow + (int) (relY / ROW_HEIGHT);
		return index < this.rows().size() ? index : -1;
	}

	private static boolean inArea(double relX, int areaX) {
		return relX >= areaX && relX < areaX + ARROW_AREA;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	/** Only the title: the "Inventory" label is left out to keep the screen within 240 pixels. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		this.renderRenameButton(graphics, mouseX, mouseY);
		this.renderScroller(graphics, mouseX, mouseY);

		List<Option> rows = this.rows();
		if (rows.isEmpty()) {
			this.renderStatusMessage(graphics);
			return;
		}
		int hovered = this.rowAt(mouseX, mouseY);
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = this.scrollRow + row;
			if (index >= rows.size()) {
				break;
			}
			int x = this.leftPos + LIST_X;
			int y = this.topPos + LIST_Y + row * ROW_HEIGHT;
			this.renderRow(graphics, rows.get(index), x, y, index == hovered ? mouseX - x : -1);
		}
	}

	private void renderRenameButton(GuiGraphics graphics, int mouseX, int mouseY) {
		boolean active = this.canRename();
		boolean hovered = this.isHovering(RENAME_BUTTON_X, RENAME_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
		int v = !active ? BUTTON_DISABLED_V : hovered ? BUTTON_HOVER_V : BUTTON_V;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + RENAME_BUTTON_X, this.topPos + RENAME_BUTTON_Y, SPRITE_U, v, BUTTON_SIZE, BUTTON_SIZE, 256, 256);
		if (active && hovered) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private void renderScroller(GuiGraphics graphics, int mouseX, int mouseY) {
		int maxScroll = this.maxScroll();
		int offset = maxScroll > 0 ? Math.round((SCROLLER_TRACK - SCROLLER_HEIGHT) * this.scrollRow / (float) maxScroll) : 0;
		int x = this.leftPos + SCROLLER_X;
		int y = this.topPos + SCROLLER_Y + offset;
		float u = maxScroll > 0 ? SPRITE_U : SPRITE_U + SCROLLER_WIDTH;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u, SCROLLER_V, SCROLLER_WIDTH, SCROLLER_HEIGHT, 256, 256);
		if (maxScroll > 0 && mouseX >= x && mouseX < x + SCROLLER_WIDTH && mouseY >= y && mouseY < y + SCROLLER_HEIGHT) {
			graphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
		}
	}

	private void renderStatusMessage(GuiGraphics graphics) {
		String key;
		if (this.menu.getInputItem().isEmpty()) {
			key = "perfectenchanting.gui.empty";
		} else if (this.optionCount == 0) {
			key = "perfectenchanting.gui.nothing";
		} else {
			key = "perfectenchanting.gui.no_results";
		}
		List<FormattedCharSequence> lines = this.font.split(Component.translatable(key), LIST_WIDTH - 20);
		int centerX = this.leftPos + LIST_X + LIST_WIDTH / 2;
		int y = this.topPos + LIST_Y + (VISIBLE_ROWS * ROW_HEIGHT - lines.size() * 10) / 2;
		for (FormattedCharSequence line : lines) {
			graphics.drawCenteredString(this.font, line, centerX, y, TEXT_MUTED);
			y += 10;
		}
	}

	/**
	 * @param hoverX mouse x relative to the row, or -1 if the row is not hovered
	 */
	private void renderRow(GuiGraphics graphics, Option option, int x, int y, int hoverX) {
		boolean hovered = hoverX >= 0;
		boolean available = option.state() == State.AVAILABLE;
		int level = available ? this.chosenLevel(option) : option.currentLevel();
		int cost = available ? option.cost(level) : 0;
		boolean affordable = available && this.canAfford(cost);
		boolean overPicker = hovered && hoverX >= LEVEL_DOWN_X;

		int background = switch (option.state()) {
			case AVAILABLE -> affordable
				? (hovered && !overPicker ? 0xFF3D5299 : 0xFF2C3B70)
				: (hovered && !overPicker ? 0xFF4A3A62 : 0xFF3A2F50);
			case MAXED -> 0xFF22432E;
			case INCOMPATIBLE -> 0xFF3B2A30;
		};
		graphics.fill(x, y, x + LIST_WIDTH, y + ROW_HEIGHT - 1, background);
		graphics.fill(x, y, x + LIST_WIDTH, y + 1, 0x30FFFFFF);
		graphics.fill(x, y + ROW_HEIGHT - 2, x + LIST_WIDTH, y + ROW_HEIGHT - 1, 0x40000000);
		if (hovered && available && affordable && !overPicker) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		int nameColor = switch (option.state()) {
			case AVAILABLE -> option.isCurse() ? 0xFFFF7A7A : 0xFFFFFFFF;
			case MAXED -> 0xFFB6E8B6;
			case INCOMPATIBLE -> 0xFF9A9A9A;
		};
		Component status;
		int statusColor;
		if (option.state() == State.MAXED) {
			status = Component.translatable("perfectenchanting.gui.status.maxed");
			statusColor = 0xFF7FD47F;
		} else if (option.state() == State.INCOMPATIBLE) {
			status = Component.translatable("perfectenchanting.gui.status.conflict", option.conflicts().getFirst().value().description());
			statusColor = 0xFFE07070;
		} else if (option.currentLevel() > 0) {
			status = Component.translatable("perfectenchanting.gui.status.current", EnchantRules.levelName(option.currentLevel()));
			statusColor = TEXT_MUTED;
		} else {
			status = Component.translatable("perfectenchanting.gui.status.new");
			statusColor = TEXT_MUTED;
		}

		int textRight;
		if (available) {
			textRight = LEVEL_DOWN_X - 2;
			this.renderLevelPicker(graphics, option, level, x, y, hoverX);
			this.renderCost(graphics, cost, affordable, x, y);
		} else {
			String current = option.currentLevel() > 0 ? EnchantRules.levelName(option.currentLevel()).getString() : "";
			graphics.drawString(this.font, current, x + LIST_WIDTH - 4 - this.font.width(current), y + 2, 0xFF9A9A9A);
			textRight = LIST_WIDTH - 8 - this.font.width(current);
		}
		graphics.drawString(this.font, this.ellipsize(plainName(option), textRight - 4), x + 4, y + 2, nameColor);
		graphics.drawString(this.font, this.ellipsize(status.getString(), textRight - 4), x + 4, y + 10, statusColor);
	}

	private void renderLevelPicker(GuiGraphics graphics, Option option, int level, int x, int y, int hoverX) {
		boolean canDown = level > option.minTargetLevel();
		boolean canUp = level < option.maxLevel();
		this.renderArrow(graphics, x + LEVEL_DOWN_X, y, false, canDown, canDown && inArea(hoverX, LEVEL_DOWN_X));
		this.renderArrow(graphics, x + LEVEL_UP_X, y, true, canUp, canUp && inArea(hoverX, LEVEL_UP_X));
		String numeral = EnchantRules.levelName(level).getString();
		int center = x + (LEVEL_DOWN_X + ARROW_AREA + LEVEL_UP_X) / 2;
		graphics.drawString(this.font, numeral, center - this.font.width(numeral) / 2, y + 2, TEXT_LEVEL);
	}

	/** A 4x7 triangle centred in a 12x9 area. */
	private void renderArrow(GuiGraphics graphics, int areaX, int y, boolean pointsRight, boolean enabled, boolean hovered) {
		if (hovered) {
			graphics.fill(areaX, y + 1, areaX + ARROW_AREA, y + 10, 0x40FFFFFF);
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
		int color = !enabled ? 0xFF4C5272 : hovered ? 0xFFFFFFFF : 0xFFC8D2F0;
		int left = areaX + 4;
		int centerY = y + 5;
		for (int i = 0; i < 4; i++) {
			int column = pointsRight ? left + i : left + 3 - i;
			int half = 3 - i;
			graphics.fill(column, centerY - half, column + 1, centerY + half + 1, color);
		}
	}

	/** Cost with a small lapis icon, right-aligned on the second line. */
	private void renderCost(GuiGraphics graphics, int cost, boolean affordable, int x, int y) {
		int right = x + LIST_WIDTH - 4;
		if (cost <= 0 || this.costsWaived()) {
			Component free = Component.translatable("perfectenchanting.gui.free");
			graphics.drawString(this.font, free, right - this.font.width(free), y + 10, 0xFF7FD47F);
			return;
		}
		int iconX = right - 8;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, iconX, y + 9, SPRITE_U, LAPIS_ICON_V, 8, 8, 256, 256);
		String text = Integer.toString(cost);
		graphics.drawString(this.font, text, iconX - 1 - this.font.width(text), y + 10, affordable ? 0xFFA8C4FF : TEXT_RED);
	}

	private String ellipsize(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…"))) + "…";
	}

	// ---------------------------------------------------------------- tooltips

	@Override
	protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderTooltip(graphics, mouseX, mouseY);

		if (this.isHovering(RENAME_BUTTON_X, RENAME_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
			graphics.setComponentTooltipForNextFrame(this.font, this.renameTooltip(), mouseX, mouseY);
			return;
		}
		if (this.menu.getLapisCount() == 0
			&& this.isHovering(PerfectEnchantingMenu.LAPIS_X, PerfectEnchantingMenu.LAPIS_Y, 16, 16, mouseX, mouseY)) {
			graphics.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("perfectenchanting.tooltip.lapis_slot"),
				Component.translatable("perfectenchanting.tooltip.lapis_slot.desc").withStyle(ChatFormatting.GRAY)
			), mouseX, mouseY);
			return;
		}
		int index = this.rowAt(mouseX, mouseY);
		if (index >= 0) {
			graphics.setComponentTooltipForNextFrame(this.font, this.rowTooltip(this.rows().get(index)), mouseX, mouseY);
		}
	}

	private List<Component> renameTooltip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("perfectenchanting.tooltip.rename"));
		ItemStack stack = this.menu.getInputItem();
		String name = this.nameBox.getValue();
		if (stack.isEmpty()) {
			lines.add(Component.translatable("perfectenchanting.tooltip.rename.no_item").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		if (!EnchantRules.isRenameChange(stack, name)) {
			lines.add(Component.translatable("perfectenchanting.tooltip.rename.unchanged").withStyle(ChatFormatting.GRAY));
		} else if (StringUtil.isBlank(name)) {
			lines.add(Component.translatable("perfectenchanting.tooltip.rename.reset").withStyle(ChatFormatting.GRAY));
		}
		int cost = this.menu.getSettings().renameCost();
		if (cost <= 0 || this.costsWaived()) {
			lines.add(Component.translatable("perfectenchanting.tooltip.free").withStyle(ChatFormatting.GREEN));
		} else {
			lines.add(Component.translatable("perfectenchanting.tooltip.cost", cost).withStyle(this.canAfford(cost) ? ChatFormatting.GRAY : ChatFormatting.RED));
		}
		return lines;
	}

	private List<Component> rowTooltip(Option option) {
		List<Component> lines = new ArrayList<>();
		ChatFormatting titleColor = option.isCurse() ? ChatFormatting.RED : ChatFormatting.AQUA;
		switch (option.state()) {
			case AVAILABLE -> {
				int level = this.chosenLevel(option);
				int cost = option.cost(level);
				Component title = option.currentLevel() > 0
					? Component.translatable("perfectenchanting.tooltip.upgrade",
						EnchantRules.enchantmentName(option.enchantment(), option.currentLevel()), EnchantRules.levelName(level))
					: EnchantRules.enchantmentName(option.enchantment(), level);
				lines.add(title.copy().withStyle(titleColor));
				if (this.costsWaived()) {
					lines.add(Component.translatable("perfectenchanting.tooltip.free_creative", cost).withStyle(ChatFormatting.GREEN));
				} else if (cost > 0) {
					lines.add(Component.translatable("perfectenchanting.tooltip.cost", cost).withStyle(this.canAfford(cost) ? ChatFormatting.GRAY : ChatFormatting.RED));
				} else {
					lines.add(Component.translatable("perfectenchanting.tooltip.free").withStyle(ChatFormatting.GREEN));
				}
				lines.add(Component.translatable("perfectenchanting.tooltip.max_level", EnchantRules.levelName(option.maxLevel())).withStyle(ChatFormatting.GRAY));
				if (!option.conflicts().isEmpty()) {
					lines.add(Component.translatable("perfectenchanting.tooltip.conflict_allowed").withStyle(ChatFormatting.GOLD));
				}
				if (option.isCurse()) {
					lines.add(Component.translatable("perfectenchanting.tooltip.curse").withStyle(ChatFormatting.RED));
				}
				if (option.maxLevel() > option.minTargetLevel()) {
					lines.add(Component.translatable("perfectenchanting.tooltip.pick_level").withStyle(ChatFormatting.DARK_GRAY));
				}
				lines.add(this.canAfford(cost)
					? Component.translatable("perfectenchanting.tooltip.click").withStyle(ChatFormatting.YELLOW)
					: Component.translatable("perfectenchanting.tooltip.not_enough_lapis", this.menu.getLapisCount(), cost).withStyle(ChatFormatting.RED));
			}
			case MAXED -> {
				lines.add(EnchantRules.enchantmentName(option.enchantment(), option.currentLevel()).withStyle(titleColor));
				lines.add(Component.translatable("perfectenchanting.tooltip.maxed").withStyle(ChatFormatting.GREEN));
			}
			case INCOMPATIBLE -> {
				lines.add(option.enchantment().value().description().copy().withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("perfectenchanting.tooltip.incompatible").withStyle(ChatFormatting.RED));
				option.conflicts().forEach(conflict -> lines.add(
					Component.literal("  ").append(conflict.value().description()).withStyle(ChatFormatting.RED)));
			}
		}
		return lines;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Clicking anything but a text field drops the keyboard focus, so E closes the screen again.
		if (!this.nameBox.isMouseOver(event.x(), event.y()) && !this.searchBox.isMouseOver(event.x(), event.y())) {
			this.setFocused(null);
		}
		if (event.button() == 0) {
			if (this.isHovering(RENAME_BUTTON_X, RENAME_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, event.x(), event.y())) {
				if (this.canRename()) {
					this.playClickSound();
					this.submitRename();
				}
				return true;
			}
			int index = this.rowAt(event.x(), event.y());
			if (index >= 0) {
				this.clickRow(this.rows().get(index), event.x() - (this.leftPos + LIST_X));
				return true;
			}
			double relX = event.x() - (this.leftPos + SCROLLER_X);
			double relY = event.y() - (this.topPos + SCROLLER_Y);
			if (this.maxScroll() > 0 && relX >= 0 && relX < SCROLLER_WIDTH && relY >= 0 && relY < SCROLLER_TRACK) {
				this.scrolling = true;
				this.scrollTo(event.y());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void clickRow(Option option, double relX) {
		if (option.state() != State.AVAILABLE) {
			return;
		}
		int level = this.chosenLevel(option);
		if (relX >= LEVEL_DOWN_X) {
			if (inArea(relX, LEVEL_DOWN_X)) {
				this.setChosenLevel(option, level - 1);
			} else if (inArea(relX, LEVEL_UP_X)) {
				this.setChosenLevel(option, level + 1);
			}
			return;
		}
		if (this.canAfford(option.cost(level))) {
			ClientPlayNetworking.send(new TablePackets.ApplyEnchantment(this.menu.containerId, key(option), level));
			this.chosenLevels.remove(key(option));
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.scrolling) {
			this.scrollTo(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.scrolling = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// The wheel over a level picker changes the level, anywhere else it scrolls the list.
		int index = this.rowAt(mouseX, mouseY);
		double relX = mouseX - (this.leftPos + LIST_X);
		if (index >= 0 && scrollY != 0 && relX >= LEVEL_DOWN_X && relX < PICKER_END) {
			Option option = this.rows().get(index);
			if (option.state() == State.AVAILABLE) {
				this.setChosenLevel(option, this.chosenLevel(option) + (int) Math.signum(scrollY));
			}
			return true;
		}
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		int maxScroll = this.maxScroll();
		if (maxScroll > 0 && scrollY != 0) {
			this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, maxScroll);
			return true;
		}
		return false;
	}

	private void scrollTo(double mouseY) {
		float progress = (float) (mouseY - (this.topPos + SCROLLER_Y) - SCROLLER_HEIGHT / 2.0) / (SCROLLER_TRACK - SCROLLER_HEIGHT);
		this.scrollRow = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * this.maxScroll());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!event.isEscape()) {
			if (this.nameBox.isFocused()) {
				if (event.isConfirmation()) {
					this.submitRename();
					return true;
				}
				// Swallow every key while typing, so E or number keys do not close the screen or move items.
				if (this.nameBox.keyPressed(event) || this.nameBox.canConsumeInput()) {
					return true;
				}
			}
			if (this.searchBox.isFocused() && (this.searchBox.keyPressed(event) || this.searchBox.canConsumeInput())) {
				return true;
			}
		}
		return super.keyPressed(event);
	}
}
