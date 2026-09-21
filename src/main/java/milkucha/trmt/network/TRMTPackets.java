package milkucha.trmt.network;

import net.minecraft.resources.Identifier;

/** Packet identifiers for TRMT networking. */
public final class TRMTPackets {

    public static final String MOD_ID = "trmt";

    /** Login query: server sends its version, client responds with its own. */
    public static final Identifier VERSION_CHECK = Identifier.fromNamespaceAndPath(MOD_ID, "version_check");

    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";

    private TRMTPackets() {}
}
