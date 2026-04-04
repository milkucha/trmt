package milkucha.trmt.erosion;

import net.minecraft.block.Block;

/**
 * Holds erosion progress for a single block position inside a chunk.
 * Stores the block type it was created for so stale entries (e.g. dirt that
 * became grass via vanilla spreading) are detected and reset on the next step.
 */
public class ErosionEntry {

    private final Block trackedBlock;
    /** Randomly-determined erosion threshold for this specific position. Set once at creation. */
    private final float threshold;
    private float walkedOnCount;
    private long lastTouchedGameTime;

    public ErosionEntry(Block trackedBlock, float threshold, float walkedOnCount, long lastTouchedGameTime) {
        this.trackedBlock = trackedBlock;
        this.threshold = threshold;
        this.walkedOnCount = walkedOnCount;
        this.lastTouchedGameTime = lastTouchedGameTime;
    }

    public Block getTrackedBlock() {
        return trackedBlock;
    }

    public float getThreshold() {
        return threshold;
    }

    public float getWalkedOnCount() {
        return walkedOnCount;
    }

    public long getLastTouchedGameTime() {
        return lastTouchedGameTime;
    }

    /** Adds {@code amount} to the erosion count and records the current game time. */
    public void recordStep(float amount, long currentGameTime) {
        this.walkedOnCount += amount;
        this.lastTouchedGameTime = currentGameTime;
    }

    /** Returns true if this entry carries no meaningful erosion (can be pruned). */
    public boolean isEmpty() {
        return walkedOnCount <= 0;
    }
}
