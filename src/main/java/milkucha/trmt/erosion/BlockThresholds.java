package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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

    public static boolean isLeaves(Block block) {
        return block instanceof LeavesBlock;
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
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            block = Blocks.GRASS_BLOCK;
        } else if (block == TRMTBlocks.ERODED_DIRT) {
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
        } else if (block instanceof LeavesBlock) {
            min = cfg.leavesMin;
            max = cfg.leavesMax;
        } else {
            min = cfg.grassBlockMin;
            max = cfg.grassBlockMax;
        }

        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static final long TICKS_PER_DAY = 24000L;

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
                        || neighborBlock == TRMTBlocks.ERODED_ROOTED_DIRT) {
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

    /** Returns the de-erosion inactivity timeout (ticks) for the given grass erosion stage (1–5). */
    public static long getGrassDeErosionTimeout(int stage) {
        TRMTConfig cfg = TRMTConfig.get();
        return switch (stage) {
            case 1  -> (long)(cfg.deErosionTimeoutDays_grassStage1 * TICKS_PER_DAY);
            case 2  -> (long)(cfg.deErosionTimeoutDays_grassStage2 * TICKS_PER_DAY);
            case 3  -> (long)(cfg.deErosionTimeoutDays_grassStage3 * TICKS_PER_DAY);
            case 4  -> (long)(cfg.deErosionTimeoutDays_grassStage4 * TICKS_PER_DAY);
            default -> (long)(cfg.deErosionTimeoutDays_grassStage5 * TICKS_PER_DAY);
        };
    }

    /** Returns the de-erosion inactivity timeout (ticks) for the given eroded dirt block type. */
    public static long getDirtDeErosionTimeout(Block block) {
        TRMTConfig cfg = TRMTConfig.get();
        if (block == TRMTBlocks.ERODED_DIRT)        return (long)(cfg.deErosionTimeoutDays_erodedDirt       * TICKS_PER_DAY);
        if (block == TRMTBlocks.ERODED_COARSE_DIRT) return (long)(cfg.deErosionTimeoutDays_erodedCoarseDirt  * TICKS_PER_DAY);
        return (long)(cfg.deErosionTimeoutDays_erodedRootedDirt * TICKS_PER_DAY);
    }
}
