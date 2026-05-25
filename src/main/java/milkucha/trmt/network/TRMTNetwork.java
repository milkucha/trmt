package milkucha.trmt.network;

import milkucha.trmt.TRMTNeoForge;
import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TRMTNetwork {

    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TRMTNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
                    Map<BlockPos, ClientErosionCache.Entry> entries = new HashMap<>(payload.entries().size());
                    for (Map.Entry<BlockPos, SyncChunkMessage.Entry> e : payload.entries().entrySet()) {
                        SyncChunkMessage.Entry d = e.getValue();
                        entries.put(e.getKey(),
                                new ClientErosionCache.Entry(d.stage(), d.walkedOnCount(), d.threshold(), d.lastTouchedGameTime()));
                    }
                    ClientErosionCache.getInstance().setChunk(chunkPos, entries);
                }));
        registrar.playToClient(UpdateStagePayload.TYPE, UpdateStagePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() ->
                        ClientErosionCache.getInstance().setEntry(
                                payload.pos(), payload.stage(), payload.walkedOnCount(),
                                payload.threshold(), payload.lastTouchedGameTime())));
        registrar.playToClient(VersionCheckPayload.TYPE, VersionCheckPayload.STREAM_CODEC,
                (payload, ctx) ->
                        PacketDistributor.sendToServer(new VersionResponsePayload(getModVersion())));
        registrar.playToServer(VersionResponsePayload.TYPE, VersionResponsePayload.STREAM_CODEC,
                (payload, ctx) -> {
                    String clientVer = payload.version();
                    String serverVer = getModVersion();
                    if (isClientOutdated(clientVer, serverVer)) {
                        ctx.disconnect(Component.translatable("trmt.disconnect.outdated", clientVer, serverVer));
                    }
                });
    }

    public static void sendVersionCheck(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new VersionCheckPayload(getModVersion()));
    }

    public static void sendSyncChunk(ServerPlayer player, int chunkX, int chunkZ, List<SyncChunkMessage.Entry> entries) {
        Map<BlockPos, SyncChunkMessage.Entry> entryMap = new HashMap<>(entries.size());
        for (SyncChunkMessage.Entry e : entries) entryMap.put(e.pos(), e);
        PacketDistributor.sendToPlayer(player, new SyncChunkPayload(chunkX, chunkZ, entryMap));
    }

    public static void broadcastUpdateStage(MinecraftServer server, BlockPos pos, int stage,
                                             float walkedOnCount, float threshold, long lastTouchedGameTime) {
        PacketDistributor.sendToAllPlayers(new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime));
    }

    static String getModVersion() {
        return ModList.get()
                .getModContainerById(TRMTNeoForge.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("0.0.0");
    }

    static boolean isClientOutdated(String clientVer, String serverVer) {
        int[] cv = parseVersionParts(clientVer);
        int[] sv = parseVersionParts(serverVer);
        for (int i = 0; i < Math.max(cv.length, sv.length); i++) {
            int c = i < cv.length ? cv[i] : 0;
            int s = i < sv.length ? sv[i] : 0;
            if (c < s) return true;
            if (c > s) return false;
        }
        return false;
    }

    private static int[] parseVersionParts(String ver) {
        String[] parts = ver.split("-")[0].split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ── Public message type (used by ErosionMapManager) ────────────────────

    public static final class SyncChunkMessage {
        public record Entry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {}
        private SyncChunkMessage() {}
    }

    // ── Payload records ─────────────────────────────────────────────────────

    record SyncChunkPayload(int chunkX, int chunkZ, Map<BlockPos, SyncChunkMessage.Entry> entries)
            implements CustomPacketPayload {
        static final Type<SyncChunkPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("trmt", "sync_chunk"));
        static final StreamCodec<RegistryFriendlyByteBuf, SyncChunkPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.chunkX); buf.writeInt(p.chunkZ); buf.writeInt(p.entries.size());
                    for (Map.Entry<BlockPos, SyncChunkMessage.Entry> e : p.entries.entrySet()) {
                        buf.writeBlockPos(e.getKey());
                        SyncChunkMessage.Entry d = e.getValue();
                        buf.writeInt(d.stage()); buf.writeFloat(d.walkedOnCount());
                        buf.writeFloat(d.threshold()); buf.writeLong(d.lastTouchedGameTime());
                    }
                },
                buf -> {
                    int cx = buf.readInt(), cz = buf.readInt(), count = buf.readInt();
                    Map<BlockPos, SyncChunkMessage.Entry> entries = new HashMap<>(count);
                    for (int i = 0; i < count; i++) {
                        BlockPos pos = buf.readBlockPos();
                        entries.put(pos, new SyncChunkMessage.Entry(
                                pos, buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readLong()));
                    }
                    return new SyncChunkPayload(cx, cz, entries);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record UpdateStagePayload(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime)
            implements CustomPacketPayload {
        static final Type<UpdateStagePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("trmt", "update_stage"));
        static final StreamCodec<RegistryFriendlyByteBuf, UpdateStagePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos); buf.writeInt(p.stage); buf.writeFloat(p.walkedOnCount);
                    buf.writeFloat(p.threshold); buf.writeLong(p.lastTouchedGameTime);
                },
                buf -> new UpdateStagePayload(buf.readBlockPos(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readLong())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record VersionCheckPayload(String version) implements CustomPacketPayload {
        static final Type<VersionCheckPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("trmt", "version_check"));
        static final StreamCodec<RegistryFriendlyByteBuf, VersionCheckPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.version),
                buf -> new VersionCheckPayload(buf.readUtf())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record VersionResponsePayload(String version) implements CustomPacketPayload {
        static final Type<VersionResponsePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("trmt", "version_response"));
        static final StreamCodec<RegistryFriendlyByteBuf, VersionResponsePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.version),
                buf -> new VersionResponsePayload(buf.readUtf())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private TRMTNetwork() {}
}
