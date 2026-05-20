package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-block-type threshold ranges for the erosion transformation chain.
 * Ranges are read from datapack erosion transforms when available.
 */
public final class BlockThresholds {

    private BlockThresholds() {}

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
        ErosionThresholdRange range = ErosionLogic.getThresholdRange(block)
                .orElseGet(BlockThresholds::getFallbackThresholdRange);

        float min = range.min(), max = range.max();

        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static ErosionThresholdRange getFallbackThresholdRange() {
        return new ErosionThresholdRange(2.0f, 4.0f);
    }

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /**
     * Returns true if none of the 12 slope-aware horizontal neighbours (4 directions × 3 heights)
     * are an eroded block — meaning this block is an isolated erosion patch and should de-erode faster.
     */
    public static boolean isIsolated(World world, BlockPos pos, ErosionMapManager manager) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.offset(dir).up(dy);
                BlockState neighborState = world.getBlockState(neighbor);
                Block neighborBlock = neighborState.getBlock();
                if (neighborBlock == TRMTBlocks.ERODED_GRASS_BLOCK
                        || neighborBlock == TRMTBlocks.ERODED_DIRT
                        || neighborBlock == TRMTBlocks.ERODED_COARSE_DIRT
                        || neighborBlock == TRMTBlocks.ERODED_SAND) {
                    return false;
                }
                if (neighborBlock == Blocks.GRASS_BLOCK) {
                    ChunkErosionMap map = manager.getChunkMap(new ChunkPos(neighbor));
                    if (map != null) {
                        ErosionEntry e = map.getEntry(neighbor);
                        if (e != null && e.getErosionStage() > 0) return false;
                    }
                }
            }
        }
        return true;
    }
}
