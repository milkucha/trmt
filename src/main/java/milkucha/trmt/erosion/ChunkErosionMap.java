package milkucha.trmt.erosion;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Sparse erosion map for a single chunk.
 * Only positions that have been stepped on are stored.
 */
public class ChunkErosionMap {

    // Key: block position (world coordinates). Only positions with nonzero wear are present.
    private final Map<BlockPos, ErosionEntry> entries = new HashMap<>();

    /**
     * Records one step at the given world block position for the given block type.
     * If an entry already exists for a *different* block type (e.g. dirt that became
     * grass via vanilla spreading), it is discarded first so count always starts fresh.
     *
     * @param pos             World-space block position that was stepped on.
     * @param block           The block currently at that position.
     * @param currentGameTime Current world game time (ticks).
     */
    public void recordStep(BlockPos pos, Block block, long currentGameTime) {
        BlockPos key = pos.toImmutable();
        ErosionEntry existing = entries.get(key);
        if (existing != null && existing.getTrackedBlock() != block) {
            // Block type changed since we last saw this position — reset progress.
            entries.remove(key);
        }
        ErosionEntry entry = entries.computeIfAbsent(
                key,
                k -> new ErosionEntry(block, 0, currentGameTime)
        );
        entry.recordStep(currentGameTime);
    }

    /**
     * Returns the ErosionEntry for a position, or null if the position has never been stepped on.
     */
    public ErosionEntry getEntry(BlockPos pos) {
        return entries.get(pos);
    }

    /**
     * Returns an unmodifiable view of all active erosion entries.
     * Useful for debug rendering and serialisation.
     */
    public Map<BlockPos, ErosionEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    /** Removes the entry for the given position (e.g. after a block transformation). */
    public void removeEntry(BlockPos pos) {
        entries.remove(pos);
    }

    /**
     * Removes any entry whose walkedOnCount has dropped to zero (e.g. after full decay).
     */
    public void pruneEmpty() {
        entries.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
