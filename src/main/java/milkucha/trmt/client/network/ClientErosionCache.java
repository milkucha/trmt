package milkucha.trmt.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server only sends data for the player's current dimension, and the packets carry no dimension,
 * so everything is dropped whenever the client level changes (dimension change, respawn, new server).
 */
public final class ClientErosionCache {

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

    private final ConcurrentHashMap<ChunkPos, Map<BlockPos, Entry>> chunks = new ConcurrentHashMap<>();

    /** The client level the cached data belongs to. */
    private volatile Level world;

    private ClientErosionCache() {}

    public static ClientErosionCache getInstance() {
        return INSTANCE;
    }

    public int getStage(BlockPos pos) {
        Entry e = getEntry(pos);
        return e != null ? e.stage : 0;
    }

    public Entry getEntry(BlockPos pos) {
        checkWorld();
        Map<BlockPos, Entry> chunk = chunks.get(new ChunkPos(pos));
        if (chunk == null) return null;
        return chunk.get(pos);
    }

    public void setChunk(ChunkPos chunkPos, Map<BlockPos, Entry> chunkEntries) {
        checkWorld();
        if (chunkEntries.isEmpty()) {
            chunks.remove(chunkPos);
        } else {
            chunks.put(chunkPos, new HashMap<>(chunkEntries));
        }
    }

    public void setEntry(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        checkWorld();
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

    /** Drops all data for a chunk (called when the chunk loads or unloads on the client). */
    public void clearChunk(ChunkPos chunkPos) {
        chunks.remove(chunkPos);
    }

    /** Drops everything cached for a previous client level (another dimension's data must never show). */
    private void checkWorld() {
        Level current = Minecraft.getInstance().level;
        if (current != world) {
            chunks.clear();
            world = current;
        }
    }

    public void clear() {
        chunks.clear();
    }
}
