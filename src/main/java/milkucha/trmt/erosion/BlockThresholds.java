package milkucha.trmt.erosion;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-block-type threshold ranges for the erosion transformation chain.
 * Each entry is {min, max}. A random value within the range is drawn once
 * when a block position first becomes tracked and stored in its ErosionEntry.
 */
public final class BlockThresholds {

    private static final Map<Block, float[]> RANGES = Map.of(
            Blocks.GRASS_BLOCK, new float[]{2.0f, 3.0f},
            Blocks.DIRT,        new float[]{2.0f, 3.0f},
            Blocks.COARSE_DIRT, new float[]{4.0f, 6.0f},
            Blocks.ROOTED_DIRT, new float[]{8.0f, 10.0f}
    );

    private static final float DEFAULT_MIN = 2.0f;
    private static final float DEFAULT_MAX = 3.0f;

    private BlockThresholds() {}

    /**
     * Returns a random threshold for the given block type, drawn uniformly from its range.
     * Call this once per block position when it first becomes tracked.
     */
    public static float randomThreshold(Block block) {
        // Eroded coarse dirt uses the same threshold range as regular coarse dirt.
        Block lookup = (block == milkucha.trmt.TRMTBlocks.ERODED_COARSE_DIRT) ? Blocks.COARSE_DIRT : block;
        float[] range = RANGES.getOrDefault(lookup, new float[]{DEFAULT_MIN, DEFAULT_MAX});
        return range[0] + ThreadLocalRandom.current().nextFloat() * (range[1] - range[0]);
    }
}
