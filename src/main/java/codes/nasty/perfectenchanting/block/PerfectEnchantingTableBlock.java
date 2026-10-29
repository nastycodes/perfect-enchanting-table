package codes.nasty.perfectenchanting.block;

import codes.nasty.perfectenchanting.PerfectConfig;
import codes.nasty.perfectenchanting.TableSettings;
import codes.nasty.perfectenchanting.menu.PerfectEnchantingMenu;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Looks and behaves like an enchanting table (same shape, floating book, bookshelf particles), but opens the
 * perfect enchanting menu. It reuses the vanilla enchanting table block entity, which is why the block is
 * registered as a supported block of {@code BlockEntityType.ENCHANTING_TABLE}.
 */
public class PerfectEnchantingTableBlock extends EnchantingTableBlock {
	public static final MapCodec<PerfectEnchantingTableBlock> CODEC = simpleCodec(PerfectEnchantingTableBlock::new);
	private static final Component CONTAINER_TITLE = Component.translatable("container.perfectenchanting.perfect_enchanting_table");

	public PerfectEnchantingTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("unchecked")
	public MapCodec<EnchantingTableBlock> codec() {
		return (MapCodec<EnchantingTableBlock>) (MapCodec<?>) CODEC;
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		// A table renamed in an anvil keeps its name as the GUI title, like the vanilla one.
		Component title = level.getBlockEntity(pos) instanceof Nameable nameable && nameable.hasCustomName()
			? nameable.getCustomName()
			: CONTAINER_TITLE;
		return new TableMenuProvider(ContainerLevelAccess.create(level, pos), title, PerfectConfig.get().toSettings());
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		super.animateTick(state, level, pos, random);
		// The perfect table needs no bookshelves, so it draws glyphs into the book on its own.
		if (random.nextInt(4) == 0) {
			level.addParticle(
				ParticleTypes.ENCHANT,
				pos.getX() + 0.5,
				pos.getY() + 2.0,
				pos.getZ() + 0.5,
				random.nextFloat() * 3.0F - 1.5F,
				-random.nextFloat() - 0.5F,
				random.nextFloat() * 3.0F - 1.5F
			);
		}
	}

	private record TableMenuProvider(ContainerLevelAccess access, Component title, TableSettings settings)
		implements ExtendedScreenHandlerFactory<TableSettings> {
		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
			return new PerfectEnchantingMenu(containerId, inventory, this.access, this.settings);
		}

		@Override
		public Component getDisplayName() {
			return this.title;
		}

		@Override
		public TableSettings getScreenOpeningData(ServerPlayer player) {
			return this.settings;
		}
	}
}
