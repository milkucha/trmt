package milkucha.trmt.network;

import milkucha.trmt.TRMTForge;
import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class TRMTNetwork {

    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("trmt", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncChunkMessage.class,
                SyncChunkMessage::encode, SyncChunkMessage::decode, SyncChunkMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, UpdateStageMessage.class,
                UpdateStageMessage::encode, UpdateStageMessage::decode, UpdateStageMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, VersionCheckMessage.class,
                VersionCheckMessage::encode, VersionCheckMessage::decode, VersionCheckMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, VersionResponseMessage.class,
                VersionResponseMessage::encode, VersionResponseMessage::decode, VersionResponseMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendVersionCheck(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new VersionCheckMessage(getModVersion()));
    }

    static String getModVersion() {
        return ModList.get()
                .getModContainerById(TRMTForge.MOD_ID)
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

    public static void sendSyncChunk(ServerPlayer player, int chunkX, int chunkZ, List<SyncChunkMessage.Entry> entries) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncChunkMessage(chunkX, chunkZ, entries));
    }

    public static void broadcastUpdateStage(MinecraftServer server, BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        UpdateStageMessage msg = new UpdateStageMessage(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
        }
    }

    // ── SyncChunkMessage ──────────────────────────────────────────────────

    public static class SyncChunkMessage {

        public record Entry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {}

        final int chunkX, chunkZ;
        final List<Entry> entries;

        SyncChunkMessage(int chunkX, int chunkZ, List<Entry> entries) {
            this.chunkX  = chunkX;
            this.chunkZ  = chunkZ;
            this.entries = entries;
        }

        static void encode(SyncChunkMessage msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.chunkX);
            buf.writeInt(msg.chunkZ);
            buf.writeInt(msg.entries.size());
            for (Entry e : msg.entries) {
                buf.writeBlockPos(e.pos());
                buf.writeInt(e.stage());
                buf.writeFloat(e.walkedOnCount());
                buf.writeFloat(e.threshold());
                buf.writeLong(e.lastTouchedGameTime());
            }
        }

        static SyncChunkMessage decode(FriendlyByteBuf buf) {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            int count  = buf.readInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(
                        buf.readBlockPos(),
                        buf.readInt(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readLong()
                ));
            }
            return new SyncChunkMessage(chunkX, chunkZ, entries);
        }

        static void handle(SyncChunkMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ChunkPos chunkPos = new ChunkPos(msg.chunkX, msg.chunkZ);
                Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(msg.entries.size());
                for (Entry e : msg.entries) {
                    chunkEntries.put(e.pos(),
                            new ClientErosionCache.Entry(e.stage(), e.walkedOnCount(), e.threshold(), e.lastTouchedGameTime()));
                }
                ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries);
            });
            ctx.setPacketHandled(true);
        }
    }

    // ── UpdateStageMessage ────────────────────────────────────────────────

    public static class UpdateStageMessage {
        final BlockPos pos;
        final int   stage;
        final float walkedOnCount;
        final float threshold;
        final long  lastTouchedGameTime;

        UpdateStageMessage(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
            this.pos                 = pos;
            this.stage               = stage;
            this.walkedOnCount       = walkedOnCount;
            this.threshold           = threshold;
            this.lastTouchedGameTime = lastTouchedGameTime;
        }

        static void encode(UpdateStageMessage msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeInt(msg.stage);
            buf.writeFloat(msg.walkedOnCount);
            buf.writeFloat(msg.threshold);
            buf.writeLong(msg.lastTouchedGameTime);
        }

        static UpdateStageMessage decode(FriendlyByteBuf buf) {
            return new UpdateStageMessage(
                    buf.readBlockPos(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readLong()
            );
        }

        static void handle(UpdateStageMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() ->
                    ClientErosionCache.getInstance().setEntry(
                            msg.pos, msg.stage, msg.walkedOnCount, msg.threshold, msg.lastTouchedGameTime));
            ctx.setPacketHandled(true);
        }
    }

    // ── VersionCheckMessage ───────────────────────────────────────────────
    // Server → client: carry server mod version so client can respond.

    public static class VersionCheckMessage {
        final String serverVersion;

        VersionCheckMessage(String serverVersion) {
            this.serverVersion = serverVersion;
        }

        static void encode(VersionCheckMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.serverVersion);
        }

        static VersionCheckMessage decode(FriendlyByteBuf buf) {
            return new VersionCheckMessage(buf.readUtf());
        }

        static void handle(VersionCheckMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            // Client replies with its own version; server will kick if outdated.
            ctx.enqueueWork(() ->
                    CHANNEL.sendToServer(new VersionResponseMessage(getModVersion())));
            ctx.setPacketHandled(true);
        }
    }

    // ── VersionResponseMessage ────────────────────────────────────────────
    // Client → server: carry client mod version so server can compare and kick.

    public static class VersionResponseMessage {
        final String clientVersion;

        VersionResponseMessage(String clientVersion) {
            this.clientVersion = clientVersion;
        }

        static void encode(VersionResponseMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.clientVersion);
        }

        static VersionResponseMessage decode(FriendlyByteBuf buf) {
            return new VersionResponseMessage(buf.readUtf());
        }

        static void handle(VersionResponseMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                String serverVer = getModVersion();
                if (isClientOutdated(msg.clientVersion, serverVer)) {
                    player.connection.disconnect(
                            Component.translatable("trmt.disconnect.outdated", msg.clientVersion, serverVer));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    private TRMTNetwork() {}
}
