package milkucha.trmt.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record SyncChunkPayload(int chunkX, int chunkZ, List<Entry> entries) implements CustomPayload {

    public record Entry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {}

    public static final Id<SyncChunkPayload> ID = new Id<>(Identifier.of("trmt", "sync_chunk"));

    public static final PacketCodec<RegistryByteBuf, SyncChunkPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            buf.writeInt(payload.chunkX());
            buf.writeInt(payload.chunkZ());
            buf.writeInt(payload.entries().size());
            for (Entry e : payload.entries()) {
                BlockPos.PACKET_CODEC.encode(buf, e.pos());
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
                    BlockPos.PACKET_CODEC.decode(buf),
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
    public Id<? extends CustomPayload> getId() { return ID; }
}
