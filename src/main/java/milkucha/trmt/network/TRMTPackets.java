package milkucha.trmt.network;

import net.minecraft.util.Identifier;

/** Packet identifiers for TRMT networking. */
public final class TRMTPackets {

    /** Full erosion data for one chunk. Sent to a player when it starts watching that chunk. */
    public static final Identifier SYNC_CHUNK   = new Identifier("trmt", "sync_chunk");

    /** Single-block stage update. Sent to players watching the chunk whenever a stage advances or resets. */
    public static final Identifier UPDATE_STAGE = new Identifier("trmt", "update_stage");

    /** C2S: the client's debug HUD state (one boolean). Erosion sync is only sent while it is on. */
    public static final Identifier HUD_STATE    = new Identifier("trmt", "hud_state");

    /** Login query: server sends its version, client responds with its own. */
    public static final Identifier VERSION_CHECK = new Identifier("trmt", "version_check");

    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";

    private TRMTPackets() {}
}
