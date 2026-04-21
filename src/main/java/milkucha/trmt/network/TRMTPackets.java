package milkucha.trmt.network;

import net.minecraft.util.Identifier;

/** Packet identifiers for TRMT networking. */
public final class TRMTPackets {

    /** Full erosion data for one chunk. Sent to each player on join. */
    public static final Identifier SYNC_CHUNK   = new Identifier("trmt", "sync_chunk");

    /** Single-block stage update. Sent to all players whenever a stage advances or resets. */
    public static final Identifier UPDATE_STAGE = new Identifier("trmt", "update_stage");

    /** Login query: server sends its version, client responds with its own. */
    public static final Identifier VERSION_CHECK = new Identifier("trmt", "version_check");

    public static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/the-roads-more-travelled";

    private TRMTPackets() {}
}
