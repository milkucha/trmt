package milkucha.trmt.erosion;

import milkucha.trmt.network.TRMTPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public record TRMTSyncChunkPayload(ChunkPos chunkPos, Map<BlockPos, ErosionEntry> entries)
        implements CustomPacketPayload {

    public static final Type<TRMTSyncChunkPayload> TYPE = new Type<>(TRMTPackets.SYNC_CHUNK);

    public static final StreamCodec<FriendlyByteBuf, TRMTSyncChunkPayload> CODEC =
            StreamCodec.of(TRMTSyncChunkPayload::encode, TRMTSyncChunkPayload::decode);

    private static void encode(FriendlyByteBuf buf, TRMTSyncChunkPayload payload) {
        buf.writeInt(payload.chunkPos().x());
        buf.writeInt(payload.chunkPos().z());
        buf.writeInt(payload.entries().size());
        for (Map.Entry<BlockPos, ErosionEntry> e : payload.entries().entrySet()) {
            buf.writeBlockPos(e.getKey());
            buf.writeInt(e.getValue().getErosionStage());
            buf.writeFloat(e.getValue().getWalkedOnCount());
            buf.writeFloat(e.getValue().getThreshold());
            buf.writeLong(e.getValue().getLastTouchedGameTime());
        }
    }

    private static TRMTSyncChunkPayload decode(FriendlyByteBuf buf) {
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int count  = buf.readInt();
        Map<BlockPos, ErosionEntry> entries = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            int   stage   = buf.readInt();
            float walked  = buf.readFloat();
            float thresh  = buf.readFloat();
            long  time    = buf.readLong();
            entries.put(pos, new ErosionEntry(null, thresh, walked, time, stage));
        }
        return new TRMTSyncChunkPayload(new ChunkPos(chunkX, chunkZ), entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
