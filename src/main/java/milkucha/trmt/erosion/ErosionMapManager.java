package milkucha.trmt.erosion;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Server-side singleton that holds all per-chunk erosion maps for the current world session.
 * Reset when the server stops so data does not bleed between sessions.
 */
public class ErosionMapManager {

    private static ErosionMapManager INSTANCE;

    // Key: ChunkPos. Only chunks with at least one stepped-on block have an entry.
    private final Map<ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();

    /**
     * Positions whose erosion stage just advanced. Drained each client tick to schedule
     * chunk section re-renders. Thread-safe: written on server tick, read on client tick.
     */
    private final Queue<BlockPos> pendingRerenders = new ConcurrentLinkedQueue<>();

    private ErosionMapManager() {}

    public static ErosionMapManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ErosionMapManager();
        }
        return INSTANCE;
    }

    /** Called on server stop to release all in-memory state. */
    public static void reset() {
        INSTANCE = null;
    }

    /**
     * Records one step at worldPos for the given block type.
     * Creates a ChunkErosionMap for the chunk if needed.
     *
     * @param worldPos        World-space block position that was stepped on.
     * @param block           The block currently at that position.
     * @param currentGameTime Current world game time in ticks.
     */
    public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = chunkMaps.computeIfAbsent(chunkPos, k -> new ChunkErosionMap());
        map.recordStep(worldPos, block, amount, currentGameTime);
    }

    /**
     * Removes the erosion entry for worldPos (called after a block transformation resets progress).
     * Drops the ChunkErosionMap entirely if it becomes empty.
     */
    public void removeEntry(BlockPos worldPos) {
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = chunkMaps.get(chunkPos);
        if (map == null) return;
        map.removeEntry(worldPos);
        if (map.isEmpty()) {
            chunkMaps.remove(chunkPos);
        }
    }

    /**
     * Returns the ChunkErosionMap for the given chunk, or null if no block there has been walked on.
     */
    public ChunkErosionMap getChunkMap(ChunkPos chunkPos) {
        return chunkMaps.get(chunkPos);
    }

    /**
     * Returns an unmodifiable view of all chunk maps. Used by the debug HUD.
     */
    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        return Collections.unmodifiableMap(chunkMaps);
    }

    /** Enqueues a position for chunk-section re-render. Called on the server tick thread. */
    public void markForRerender(BlockPos pos) {
        pendingRerenders.add(pos.toImmutable());
    }

    /**
     * Drains all queued re-render positions and passes each to {@code action}.
     * Called on the client tick thread; thread-safe via ConcurrentLinkedQueue.
     */
    public void drainRerenders(Consumer<BlockPos> action) {
        BlockPos pos;
        while ((pos = pendingRerenders.poll()) != null) {
            action.accept(pos);
        }
    }
}
