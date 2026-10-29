package codes.nasty.perfectenchanting.network;

import codes.nasty.perfectenchanting.EnchantRules;
import codes.nasty.perfectenchanting.PerfectEnchanting;
import codes.nasty.perfectenchanting.menu.PerfectEnchantingMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * Client-to-server requests of the table GUI. Enchantments are sent by id instead of a list index,
 * so a search filter or a different sort order on the client can never pick the wrong one.
 */
public final class TablePackets {
	private TablePackets() {
	}

	public record ApplyEnchantment(int containerId, ResourceKey<Enchantment> enchantment, int level) implements CustomPacketPayload {
		public static final Type<ApplyEnchantment> TYPE = new Type<>(PerfectEnchanting.id("apply_enchantment"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ApplyEnchantment> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ApplyEnchantment::containerId,
			ResourceKey.streamCodec(Registries.ENCHANTMENT), ApplyEnchantment::enchantment,
			ByteBufCodecs.VAR_INT, ApplyEnchantment::level,
			ApplyEnchantment::new
		);

		@Override
		public Type<ApplyEnchantment> type() {
			return TYPE;
		}
	}

	public record RenameItem(int containerId, String name) implements CustomPacketPayload {
		public static final Type<RenameItem> TYPE = new Type<>(PerfectEnchanting.id("rename_item"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RenameItem> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RenameItem::containerId,
			ByteBufCodecs.stringUtf8(EnchantRules.MAX_NAME_LENGTH * 4), RenameItem::name,
			RenameItem::new
		);

		@Override
		public Type<RenameItem> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ApplyEnchantment.TYPE, ApplyEnchantment.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RenameItem.TYPE, RenameItem.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ApplyEnchantment.TYPE, (payload, context) -> {
			PerfectEnchantingMenu menu = openMenu(context.player(), payload.containerId());
			if (menu != null) {
				menu.applyEnchantment(context.player(), payload.enchantment(), payload.level());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RenameItem.TYPE, (payload, context) -> {
			PerfectEnchantingMenu menu = openMenu(context.player(), payload.containerId());
			if (menu != null) {
				menu.renameItem(context.player(), payload.name());
			}
		});
	}

	/** The table menu the player has open, if the request belongs to it and the player may still use it. */
	private static @Nullable PerfectEnchantingMenu openMenu(ServerPlayer player, int containerId) {
		if (player.containerMenu instanceof PerfectEnchantingMenu menu
			&& menu.containerId == containerId
			&& menu.stillValid(player)
			&& !player.isSpectator()) {
			return menu;
		}
		return null;
	}
}
