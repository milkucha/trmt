package milkucha.trmt.erosion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChunkErosionMap {
    private final Map<BlockPos, ErosionEntry> entries = new HashMap<>();

    public void recordStep(BlockPos pos, Block block, float amount, long currentGameTime) {
        BlockPos key = pos.immutable();
        ErosionEntry existing = entries.get(key);
        if (existing != null && existing.getTrackedBlock() != block) {
            entries.remove(key);
            existing = null;
        }
        if (existing == null) {
            float threshold = BlockThresholds.randomThreshold(block);
            entries.put(key, new ErosionEntry(block, threshold, 0f, currentGameTime));
        }
        entries.get(key).recordStep(amount, currentGameTime);
    }

    public ErosionEntry getEntry(BlockPos pos) { return entries.get(pos); }
    public Map<BlockPos, ErosionEntry> getEntries() { return Collections.unmodifiableMap(entries); }
    void putEntry(BlockPos pos, ErosionEntry entry) { entries.put(pos.immutable(), entry); }
    public void removeEntry(BlockPos pos) { entries.remove(pos); }
    public void pruneEmpty() { entries.entrySet().removeIf(e -> e.getValue().isEmpty()); }
    public boolean isEmpty() { return entries.isEmpty(); }
}
