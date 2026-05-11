package milkucha.trmt.erosion;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import com.mojang.serialization.Codec;

// BlockPos must be encoded as a plain string so that Codec.unboundedMap can use it as an
// NBT compound key. BlockPos.CODEC encodes to an int array which is NOT a string and causes
// "IllegalStateException: Not a string" on every world save.

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

    /** Encodes BlockPos as the string "x,y,z" so it is a valid NBT compound key. */
    private static final Codec<BlockPos> BLOCK_POS_STRING_CODEC = Codec.STRING.xmap(
            s -> { String[] p = s.split(","); return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); },
            bp -> bp.getX() + "," + bp.getY() + "," + bp.getZ()
    );

    public static final Codec<ChunkErosionMap> CODEC = Codec.unboundedMap(BLOCK_POS_STRING_CODEC, ErosionEntry.CODEC)
            .xmap(ChunkErosionMap::new, ChunkErosionMap::getEntries);

    public ChunkErosionMap() {}

    public ChunkErosionMap(Map<BlockPos, ErosionEntry> entries) {
        this.entries.putAll(entries);
    }

    /**
     * Records one step at the given world block position for the given block type.
     * If an entry already exists for a *different* block type (e.g. dirt that became
     * grass via vanilla spreading), it is discarded first so count always starts fresh.
     *
     * @param pos             World-space block position that was stepped on.
     * @param block           The block currently at that position.
     * @param currentGameTime Current world game time (ticks).
     */
    public void recordStep(BlockPos pos, Block block, float amount, long currentGameTime) {
        BlockPos key = pos.toImmutable();
        ErosionEntry existing = entries.get(key);
        if (existing != null && existing.getTrackedBlock() != block) {
            // Block type changed since we last saw this position — reset progress.
            entries.remove(key);
            existing = null;
        }
        if (existing == null) {
            // First time this position is tracked: draw its random threshold.
            float threshold = BlockThresholds.randomThreshold(block);
            entries.put(key, new ErosionEntry(block, threshold, 0f, currentGameTime));
        }
        entries.get(key).recordStep(amount, currentGameTime);
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

    /** Deserialization only — inserts an entry directly, bypassing recordStep logic. */
    void putEntry(BlockPos pos, ErosionEntry entry) {
        entries.put(pos.toImmutable(), entry);
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
