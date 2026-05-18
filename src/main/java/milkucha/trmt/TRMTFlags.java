package milkucha.trmt;

import net.minecraft.world.level.block.Block;

/**
 * Vanilla block notification flags (Mojang mappings). Yarn's {@code Block.NOTIFY_ALL} corresponds to
 * neighbours + client sync.
 */
public final class TRMTFlags {
	private TRMTFlags() {}

	public static final int BLOCK_UPDATE = Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS;
}
