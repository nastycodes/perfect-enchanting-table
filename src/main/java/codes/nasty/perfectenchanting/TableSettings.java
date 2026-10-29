package codes.nasty.perfectenchanting;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Snapshot of the server config that a table works with. The server sends it along when the menu opens,
 * so the client computes exactly the same list, states and costs without having the config file.
 */
public record TableSettings(
	int lapisPerLevel,
	boolean scaleCostByRarity,
	int renameCost,
	boolean allowIncompatible,
	boolean allowCurses,
	boolean creativeIgnoresCosts,
	List<ResourceKey<Enchantment>> disabledEnchantments
) {
	public static final StreamCodec<RegistryFriendlyByteBuf, TableSettings> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, TableSettings::lapisPerLevel,
		ByteBufCodecs.BOOL, TableSettings::scaleCostByRarity,
		ByteBufCodecs.VAR_INT, TableSettings::renameCost,
		ByteBufCodecs.BOOL, TableSettings::allowIncompatible,
		ByteBufCodecs.BOOL, TableSettings::allowCurses,
		ByteBufCodecs.BOOL, TableSettings::creativeIgnoresCosts,
		ResourceKey.streamCodec(Registries.ENCHANTMENT).apply(ByteBufCodecs.list()), TableSettings::disabledEnchantments,
		TableSettings::new
	);

	public TableSettings {
		disabledEnchantments = List.copyOf(disabledEnchantments);
	}
}
