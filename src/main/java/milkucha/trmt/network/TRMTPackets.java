package milkucha.trmt.network;

import net.minecraft.util.Identifier;

/** Packet identifiers for server → client erosion sync. */
public final class TRMTPackets {

    /** Full erosion data for one chunk. Sent to each player on join. */
    public static final Identifier SYNC_CHUNK   = new Identifier("trmt", "sync_chunk");

    /** Single-block stage update. Sent to all players whenever a stage advances or resets. */
    public static final Identifier UPDATE_STAGE = new Identifier("trmt", "update_stage");

    private TRMTPackets() {}
}
