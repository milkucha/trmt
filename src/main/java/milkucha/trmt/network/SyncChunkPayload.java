package milkucha.trmt.network;

import milkucha.trmt.TRMT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/** Full erosion data for one chunk (join sync). */
public record SyncChunkPayload(ChunkPos chunkPos, Map<BlockPos, Entry> entries) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SyncChunkPayload> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TRMT.MOD_ID, "sync_chunk"));

	public static final StreamCodec<FriendlyByteBuf, SyncChunkPayload> STREAM_CODEC =
		StreamCodec.of((buf, p) -> p.write(buf), SyncChunkPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeInt(chunkPos.x);
		buf.writeInt(chunkPos.z);
		buf.writeInt(entries.size());
		for (var e : entries.entrySet()) {
			buf.writeBlockPos(e.getKey());
			e.getValue().write(buf);
		}
	}

	private static SyncChunkPayload read(FriendlyByteBuf buf) {
		int cx = buf.readInt();
		int cz = buf.readInt();
		ChunkPos cp = new ChunkPos(cx, cz);
		int n = buf.readInt();
		Map<BlockPos, Entry> map = HashMap.newHashMap(n);
		for (int i = 0; i < n; i++) {
			map.put(buf.readBlockPos(), Entry.read(buf));
		}
		return new SyncChunkPayload(cp, map);
	}

	@Override
	public CustomPacketPayload.Type<SyncChunkPayload> type() {
		return TYPE;
	}

	public record Entry(int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
		public void write(FriendlyByteBuf buf) {
			buf.writeInt(stage);
			buf.writeFloat(walkedOnCount);
			buf.writeFloat(threshold);
			buf.writeLong(lastTouchedGameTime);
		}

		public static Entry read(FriendlyByteBuf buf) {
			return new Entry(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readLong());
		}
	}
}
