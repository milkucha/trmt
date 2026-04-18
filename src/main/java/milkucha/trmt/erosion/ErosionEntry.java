package milkucha.trmt.erosion;

import net.minecraft.block.Block;

/**
 * Holds erosion progress for a single block position inside a chunk.
 * Stores the block type it was created for so stale entries (e.g. dirt that
 * became grass via vanilla spreading) are detected and reset on the next step.
 *
 * For GRASS_BLOCK, erosionStage tracks how visually degraded the surface is:
 *   0 = pristine grass, 1-5 = eroded stages 0-4 (rendered client-side via custom models).
 * Each stage has its own randomly-drawn threshold; walkedOnCount resets when a stage advances.
 */
public class ErosionEntry {

    private final Block trackedBlock;
    /** Randomly-determined erosion threshold for this specific position and stage. */
    private float threshold;
    private float walkedOnCount;
    private long lastTouchedGameTime;
    /** Grass erosion visual stage: 0 = pristine, 1–5 = eroded_0 through eroded_4. */
    private int erosionStage;

    public ErosionEntry(Block trackedBlock, float threshold, float walkedOnCount, long lastTouchedGameTime) {
        this.trackedBlock = trackedBlock;
        this.threshold = threshold;
        this.walkedOnCount = walkedOnCount;
        this.lastTouchedGameTime = lastTouchedGameTime;
        this.erosionStage = 0;
    }

    /** Deserialization constructor — restores all fields including erosion stage. */
    ErosionEntry(Block trackedBlock, float threshold, float walkedOnCount, long lastTouchedGameTime, int erosionStage) {
        this.trackedBlock = trackedBlock;
        this.threshold = threshold;
        this.walkedOnCount = walkedOnCount;
        this.lastTouchedGameTime = lastTouchedGameTime;
        this.erosionStage = erosionStage;
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

    public int getErosionStage() {
        return erosionStage;
    }

    /** Adds {@code amount} to the erosion count and records the current game time. */
    public void recordStep(float amount, long currentGameTime) {
        this.walkedOnCount += amount;
        this.lastTouchedGameTime = currentGameTime;
    }

    /**
     * Advances grass erosion by one visual stage.
     * Resets walkedOnCount to 0 and sets a new random threshold for the next stage.
     */
    public void advanceGrassStage(float newThreshold) {
        this.erosionStage++;
        this.walkedOnCount = 0f;
        this.threshold = newThreshold;
    }

    /**
     * Reverts grass erosion by one visual stage, resets progress, and updates
     * {@code lastTouchedGameTime} to act as a cooldown against immediate re-reversion.
     */
    public void revertGrassStage(float newThreshold, long currentGameTime) {
        this.erosionStage--;
        this.walkedOnCount = 0f;
        this.threshold = newThreshold;
        this.lastTouchedGameTime = currentGameTime;
    }

    /** Returns true if this entry carries no meaningful erosion (can be pruned). */
    public boolean isEmpty() {
        return walkedOnCount <= 0;
    }
}
