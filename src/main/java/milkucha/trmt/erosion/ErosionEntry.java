package milkucha.trmt.erosion;

import net.minecraft.block.Block;

/**
 * Holds erosion progress for a single block position inside a chunk.
 * Stores the block type it was created for so stale entries (e.g. dirt that
 * became grass via vanilla spreading) are detected and reset on the next step.
 */
public class ErosionEntry {

    private final Block trackedBlock;
    private int walkedOnCount;
    private long lastTouchedGameTime;

    public ErosionEntry(Block trackedBlock, int walkedOnCount, long lastTouchedGameTime) {
        this.trackedBlock = trackedBlock;
        this.walkedOnCount = walkedOnCount;
        this.lastTouchedGameTime = lastTouchedGameTime;
    }

    public Block getTrackedBlock() {
        return trackedBlock;
    }

    public int getWalkedOnCount() {
        return walkedOnCount;
    }

    public long getLastTouchedGameTime() {
        return lastTouchedGameTime;
    }

    /** Increments the step count and records the current game time. */
    public void recordStep(long currentGameTime) {
        this.walkedOnCount++;
        this.lastTouchedGameTime = currentGameTime;
    }

    /** Returns true if this entry carries no meaningful erosion (can be pruned). */
    public boolean isEmpty() {
        return walkedOnCount <= 0;
    }
}
