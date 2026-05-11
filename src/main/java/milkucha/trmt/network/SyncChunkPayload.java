package milkucha.trmt.network;

import milkucha.trmt.erosion.ErosionEntry;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Payload for full chunk erosion data sync.
 */
public record SyncChunkPayload(ChunkPos chunkPos, Map<BlockPos, ErosionEntry> entries) implements CustomPayload {
    public static final Id<SyncChunkPayload> ID = new Id<>(Identifier.of("trmt", "sync_chunk"));

    // Note: This codec is complex because it encodes a map. 
    // In a real mod, we'd use a more optimized way, but this matches the old logic.
    public static final PacketCodec<PacketByteBuf, SyncChunkPayload> CODEC = CustomPayload.codecOf(
            (value, buf) -> {
                buf.writeInt(value.chunkPos().x);
                buf.writeInt(value.chunkPos().z);
                buf.writeInt(value.entries().size());
                for (Map.Entry<BlockPos, ErosionEntry> e : value.entries().entrySet()) {
                    buf.writeBlockPos(e.getKey());
                    buf.writeInt(e.getValue().getErosionStage());
                    buf.writeFloat(e.getValue().getWalkedOnCount());
                    buf.writeFloat(e.getValue().getThreshold());
                    buf.writeLong(e.getValue().getLastTouchedGameTime());
                }
            },
            buf -> {
                ChunkPos cp = new ChunkPos(buf.readInt(), buf.readInt());
                int size = buf.readInt();
                Map<BlockPos, ErosionEntry> entries = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    BlockPos pos = buf.readBlockPos();
                    int stage = buf.readInt();
                    float count = buf.readFloat();
                    float threshold = buf.readFloat();
                    long lastTime = buf.readLong();
                    // Use GRASS_BLOCK as a sentinel — the client only uses stage/count/threshold/lastTime.
                    entries.put(pos, new ErosionEntry(Blocks.GRASS_BLOCK, threshold, count, lastTime, stage));
                }
                return new SyncChunkPayload(cp, entries);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
