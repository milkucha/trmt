package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-block-type threshold ranges for the erosion transformation chain.
 * Ranges are read from {@link TRMTConfig} so they can be tuned via
 * {@code config/trmt.json} without recompiling.
 */
public final class BlockThresholds {

    /** All vegetation blocks that are subject to erosion trampling. */
    public static final Set<Block> VEGETATION = Set.of(
            Blocks.GRASS, Blocks.TALL_GRASS,
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
            Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.WITHER_ROSE,
            Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY
    );

    private BlockThresholds() {}

    public static boolean isVegetation(Block block) {
        return VEGETATION.contains(block);
    }

    /**
     * Deterministic rotation index (0–3) derived from block position, matching the UV
     * rotation applied to eroded grass top textures in {@code GrassErosionProxyModel}.
     * 0 = 0°, 1 = 90° CW, 2 = 180°, 3 = 270° CW.
     */
    public static int posRotation(BlockPos pos) {
        int h = (pos.getX() * 1619) ^ (pos.getZ() * 31337);
        return ((h >>> 4) ^ (h >>> 8)) & 3;
    }

    /**
     * Returns a random threshold for the given block type, drawn uniformly from its
     * configured range.  Call this once per block position when it first becomes tracked.
     */
    public static float randomThreshold(Block block) {
        // Eroded variants use the same range as their vanilla counterpart.
        if (block == TRMTBlocks.ERODED_DIRT) {
            block = Blocks.DIRT;
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT || block == TRMTBlocks.ERODED_ROOTED_DIRT) {
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
        } else if (VEGETATION.contains(block)) {
            min = cfg.vegetationMin;
            max = cfg.vegetationMax;
        } else {
            min = cfg.grassBlockMin;
            max = cfg.grassBlockMax;
        }

        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
