package milkucha.trmt.network;

import milkucha.trmt.TRMTForge;
import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TRMTNetwork {

    public static final String MODRINTH_URL = "https://modrinth.com/mod/the-roads-more-travelled";

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath("trmt", "main"))
            .networkProtocolVersion(1)
            .optional()
            .simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(SyncChunkMessage.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncChunkMessage::encode)
                .decoder(SyncChunkMessage::decode)
                .consumerMainThread(SyncChunkMessage::handle)
                .add();
        CHANNEL.messageBuilder(UpdateStageMessage.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UpdateStageMessage::encode)
                .decoder(UpdateStageMessage::decode)
                .consumerMainThread(UpdateStageMessage::handle)
                .add();
        CHANNEL.messageBuilder(VersionCheckMessage.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VersionCheckMessage::encode)
                .decoder(VersionCheckMessage::decode)
                .consumerMainThread(VersionCheckMessage::handle)
                .add();
        CHANNEL.messageBuilder(VersionResponseMessage.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VersionResponseMessage::encode)
                .decoder(VersionResponseMessage::decode)
                .consumerMainThread(VersionResponseMessage::handle)
                .add();
        CHANNEL.build();
    }

    public static void sendVersionCheck(ServerPlayer player) {
        CHANNEL.send(new VersionCheckMessage(getModVersion()), PacketDistributor.PLAYER.with(player));
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
        CHANNEL.send(new SyncChunkMessage(chunkX, chunkZ, entries), PacketDistributor.PLAYER.with(player));
    }

    public static void broadcastUpdateStage(MinecraftServer server, BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        UpdateStageMessage msg = new UpdateStageMessage(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(msg, PacketDistributor.PLAYER.with(player));
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

        // consumerMainThread already enqueues this on the main thread and marks packet handled.
        static void handle(SyncChunkMessage msg, CustomPayloadEvent.Context ctx) {
            ChunkPos chunkPos = new ChunkPos(msg.chunkX, msg.chunkZ);
            Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(msg.entries.size());
            for (Entry e : msg.entries) {
                chunkEntries.put(e.pos(),
                        new ClientErosionCache.Entry(e.stage(), e.walkedOnCount(), e.threshold(), e.lastTouchedGameTime()));
            }
            ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries);
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

        // consumerMainThread already enqueues this on the main thread and marks packet handled.
        static void handle(UpdateStageMessage msg, CustomPayloadEvent.Context ctx) {
            ClientErosionCache.getInstance().setEntry(
                    msg.pos, msg.stage, msg.walkedOnCount, msg.threshold, msg.lastTouchedGameTime);
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

        static void handle(VersionCheckMessage msg, CustomPayloadEvent.Context ctx) {
            // Client replies with its own version; server will kick if outdated.
            CHANNEL.send(new VersionResponseMessage(getModVersion()), PacketDistributor.SERVER.noArg());
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

        static void handle(VersionResponseMessage msg, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            String serverVer = getModVersion();
            if (isClientOutdated(msg.clientVersion, serverVer)) {
                player.connection.disconnect(
                        Component.translatable("trmt.disconnect.outdated", msg.clientVersion, serverVer));
            }
        }
    }

    private TRMTNetwork() {}
}
