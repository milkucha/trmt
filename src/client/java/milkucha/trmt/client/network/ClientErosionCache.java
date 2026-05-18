package milkucha.trmt.client.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientErosionCache {

	public static final class Entry {
		public final int stage;
		public final float walkedOnCount;
		public final float threshold;
		public final long lastTouchedGameTime;

		public Entry(int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
			this.stage = stage;
			this.walkedOnCount = walkedOnCount;
			this.threshold = threshold;
			this.lastTouchedGameTime = lastTouchedGameTime;
		}
	}

	private static final ClientErosionCache INSTANCE = new ClientErosionCache();

	private final ConcurrentHashMap<ChunkPos, Map<BlockPos, Entry>> chunks = new ConcurrentHashMap<>();

	private ClientErosionCache() {}

	public static ClientErosionCache getInstance() {
		return INSTANCE;
	}

	public int getStage(BlockPos pos) {
		Entry e = getEntry(pos);
		return e != null ? e.stage : 0;
	}

	public Entry getEntry(BlockPos pos) {
		Map<BlockPos, Entry> chunk = chunks.get(new ChunkPos(pos));
		if (chunk == null) return null;
		return chunk.get(pos);
	}

	public void setChunk(ChunkPos chunkPos, Map<BlockPos, Entry> chunkEntries) {
		if (chunkEntries.isEmpty()) {
			chunks.remove(chunkPos);
		} else {
			chunks.put(chunkPos, new HashMap<>(chunkEntries));
		}
	}

	public void setEntry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
		ChunkPos chunkPos = new ChunkPos(pos);
		if (stage <= 0) {
			Map<BlockPos, Entry> chunk = chunks.get(chunkPos);
			if (chunk != null) {
				chunk.remove(pos);
				if (chunk.isEmpty()) chunks.remove(chunkPos);
			}
		} else {
			chunks.computeIfAbsent(chunkPos, k -> new ConcurrentHashMap<>())
				.put(pos.immutable(), new Entry(stage, walkedOnCount, threshold, lastTouchedGameTime));
		}
	}

	public void clear() {
		chunks.clear();
	}
}
