package milkucha.trmt.network;

import net.minecraft.resources.Identifier;

public final class TRMTPackets {

    public static final Identifier SYNC_CHUNK = Identifier.fromNamespaceAndPath("trmt", "sync_chunk");
    public static final Identifier UPDATE_STAGE = Identifier.fromNamespaceAndPath("trmt", "update_stage");
    public static final Identifier VERSION_CHECK = Identifier.fromNamespaceAndPath("trmt", "version_check");

    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";

    private TRMTPackets() {}
}
