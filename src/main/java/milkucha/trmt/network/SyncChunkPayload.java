package milkucha.trmt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Full erosion data for one chunk. Sent to each player on join. */
public record SyncChunkPayload(int chunkX, int chunkZ, List<Entry> entries) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(TRMTPackets.MOD_ID, "sync_chunk");
    public static final CustomPacketPayload.Type<SyncChunkPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public record Entry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChunkPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.chunkX);
                buf.writeInt(payload.chunkZ);
                buf.writeInt(payload.entries.size());
                for (Entry e : payload.entries) {
                    buf.writeBlockPos(e.pos());
                    buf.writeInt(e.stage());
                    buf.writeFloat(e.walkedOnCount());
                    buf.writeFloat(e.threshold());
                    buf.writeLong(e.lastTouchedGameTime());
                }
            },
            buf -> {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                int count = buf.readInt();
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new Entry(buf.readBlockPos(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readLong()));
                }
                return new SyncChunkPayload(chunkX, chunkZ, entries);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
