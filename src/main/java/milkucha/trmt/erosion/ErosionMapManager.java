package milkucha.trmt.erosion;

import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Server-side singleton that holds all per-chunk erosion maps for the current world session.
 * Delegates storage to {@link ErosionPersistentState} so data survives across sessions.
 * Reset when the server stops so state does not bleed between sessions.
 */
public class ErosionMapManager {

    private static ErosionMapManager INSTANCE;

    /** Loaded on SERVER_STARTED; null until then. */
    private ErosionPersistentState state;

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

    /** Called on SERVER_STARTED to load (or create) the persistent erosion state. */
    public void loadState(MinecraftServer server) {
        this.state = ErosionPersistentState.getOrCreate(server);
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
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        map.recordStep(worldPos, block, amount, currentGameTime);
        state.markDirty();
    }

    /**
     * Removes the erosion entry for worldPos (called after a block transformation resets progress).
     * Drops the ChunkErosionMap entirely if it becomes empty.
     */
    public void removeEntry(BlockPos worldPos) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        map.removeEntry(worldPos);
        state.removeChunkMapIfEmpty(chunkPos);
        state.markDirty();
    }

    /**
     * Returns the ChunkErosionMap for the given chunk, or null if no block there has been walked on.
     */
    public ChunkErosionMap getChunkMap(ChunkPos chunkPos) {
        if (state == null) return null;
        return state.getChunkMap(chunkPos);
    }

    /**
     * Returns an unmodifiable view of all chunk maps. Used by the debug HUD.
     */
    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        if (state == null) return Collections.emptyMap();
        return state.getAllChunkMaps();
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
