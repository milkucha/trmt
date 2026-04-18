package milkucha.trmt.client.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of erosion data received from the server.
 * Stores stage, walkedOnCount and threshold so the debug HUD can display full information.
 * Thread-safe: written on the Netty receive thread, read on the render thread.
 */
public final class ClientErosionCache {

    /** All erosion data the client knows about for one block position. */
    public static final class Entry {
        public final int   stage;
        public final float walkedOnCount;
        public final float threshold;
        public final long  lastTouchedGameTime;

        public Entry(int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
            this.stage               = stage;
            this.walkedOnCount       = walkedOnCount;
            this.threshold           = threshold;
            this.lastTouchedGameTime = lastTouchedGameTime;
        }
    }

    private static final ClientErosionCache INSTANCE = new ClientErosionCache();

    // Outer map is ConcurrentHashMap for safe cross-thread access.
    // Inner maps are replaced atomically via setChunk, so no lock needed there.
    private final ConcurrentHashMap<ChunkPos, Map<BlockPos, Entry>> chunks = new ConcurrentHashMap<>();

    private ClientErosionCache() {}

    public static ClientErosionCache getInstance() {
        return INSTANCE;
    }

    /** Returns the erosion stage at {@code pos}, or 0 if unknown / not eroded. */
    public int getStage(BlockPos pos) {
        Entry e = getEntry(pos);
        return e != null ? e.stage : 0;
    }

    /** Returns the full entry for {@code pos}, or null if unknown / not eroded. */
    public Entry getEntry(BlockPos pos) {
        Map<BlockPos, Entry> chunk = chunks.get(new ChunkPos(pos));
        if (chunk == null) return null;
        return chunk.get(pos);
    }

    /** Replaces all data for a chunk (received on join). */
    public void setChunk(ChunkPos chunkPos, Map<BlockPos, Entry> chunkEntries) {
        if (chunkEntries.isEmpty()) {
            chunks.remove(chunkPos);
        } else {
            chunks.put(chunkPos, new HashMap<>(chunkEntries));
        }
    }

    /**
     * Updates (or removes) data for a single block.
     * stage ≤ 0 clears the entry.
     */
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
                  .put(pos.toImmutable(), new Entry(stage, walkedOnCount, threshold, lastTouchedGameTime));
        }
    }

    /** Clears all cached data (called on server disconnect). */
    public void clear() {
        chunks.clear();
    }
}
