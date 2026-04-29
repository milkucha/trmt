package milkucha.trmt.erosion;

import net.minecraft.world.level.block.Block;

public class ErosionEntry {
    private final Block trackedBlock;
    private float threshold;
    private float walkedOnCount;
    private long lastTouchedGameTime;
    private int erosionStage;

    public ErosionEntry(Block trackedBlock, float threshold, float walkedOnCount, long lastTouchedGameTime) {
        this.trackedBlock = trackedBlock;
        this.threshold = threshold;
        this.walkedOnCount = walkedOnCount;
        this.lastTouchedGameTime = lastTouchedGameTime;
        this.erosionStage = 0;
    }

    ErosionEntry(Block trackedBlock, float threshold, float walkedOnCount, long lastTouchedGameTime, int erosionStage) {
        this.trackedBlock = trackedBlock;
        this.threshold = threshold;
        this.walkedOnCount = walkedOnCount;
        this.lastTouchedGameTime = lastTouchedGameTime;
        this.erosionStage = erosionStage;
    }

    public Block getTrackedBlock() { return trackedBlock; }
    public float getThreshold() { return threshold; }
    public float getWalkedOnCount() { return walkedOnCount; }
    public long getLastTouchedGameTime() { return lastTouchedGameTime; }
    public int getErosionStage() { return erosionStage; }

    public void recordStep(float amount, long currentGameTime) {
        this.walkedOnCount += amount;
        this.lastTouchedGameTime = currentGameTime;
    }

    public void advanceGrassStage(float newThreshold) {
        this.erosionStage++;
        this.walkedOnCount = 0f;
        this.threshold = newThreshold;
    }

    public void revertGrassStage(float newThreshold, long currentGameTime) {
        this.erosionStage--;
        this.walkedOnCount = 0f;
        this.threshold = newThreshold;
        this.lastTouchedGameTime = currentGameTime;
    }

    public boolean isEmpty() { return walkedOnCount <= 0; }
}
