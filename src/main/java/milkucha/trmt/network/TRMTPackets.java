package milkucha.trmt.network;

import net.minecraft.resources.ResourceLocation;

public final class TRMTPackets {
    public static final ResourceLocation SYNC_CHUNK = ResourceLocation.fromNamespaceAndPath("trmt", "sync_chunk");
    public static final ResourceLocation UPDATE_STAGE = ResourceLocation.fromNamespaceAndPath("trmt", "update_stage");
    public static final ResourceLocation VERSION_CHECK = ResourceLocation.fromNamespaceAndPath("trmt", "version_check");
    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";
    private TRMTPackets() {}
}
