package milkucha.trmt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncChunkPayload(int chunkX, int chunkZ, List<Entry> entries) implements CustomPacketPayload {

    public record Entry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {}

    public static final Type<SyncChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("trmt", "sync_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChunkPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeInt(payload.entries().size());
            for (Entry e : payload.entries()) {
                BlockPos.STREAM_CODEC.encode(buf, e.pos());
                buf.writeInt(e.stage());
                buf.writeFloat(e.walkedOnCount());
                buf.writeFloat(e.threshold());
                buf.writeLong(e.lastTouchedGameTime());
            }
        },
        buf -> {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            int count  = buf.readInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(
                    BlockPos.STREAM_CODEC.decode(buf),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readLong()
                ));
            }
            return new SyncChunkPayload(chunkX, chunkZ, entries);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
