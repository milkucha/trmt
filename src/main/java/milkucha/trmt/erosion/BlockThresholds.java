package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-block-type threshold ranges for the erosion transformation chain.
 * Ranges are read from {@link TRMTConfig} so they can be tuned via
 * {@code config/trmt.json} without recompiling.
 */
public final class BlockThresholds {

    private BlockThresholds() {}

    /**
     * Returns a random threshold for the given block type, drawn uniformly from its
     * configured range.  Call this once per block position when it first becomes tracked.
     */
    public static float randomThreshold(Block block) {
        // Eroded variants use the same range as their vanilla counterpart.
        if (block == TRMTBlocks.ERODED_COARSE_DIRT || block == TRMTBlocks.ERODED_ROOTED_DIRT) {
            block = Blocks.COARSE_DIRT;
        }

        TRMTConfig cfg = TRMTConfig.get();
        float min, max;

        if (block == Blocks.GRASS_BLOCK) {
            min = cfg.grassBlockMin;
            max = cfg.grassBlockMax;
        } else if (block == Blocks.DIRT) {
            min = cfg.dirtMin;
            max = cfg.dirtMax;
        } else if (block == Blocks.COARSE_DIRT) {
            min = cfg.coarseDirtMin;
            max = cfg.coarseDirtMax;
        } else {
            min = cfg.grassBlockMin;
            max = cfg.grassBlockMax;
        }

        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
