package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockThresholds {

    public static final Set<Block> VEGETATION = Set.of(
        Blocks.SHORT_GRASS, Blocks.TALL_GRASS,
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

    public static int posRotation(BlockPos pos) {
        int h = (pos.getX() * 1619) ^ (pos.getZ() * 31337);
        return ((h >>> 4) ^ (h >>> 8)) & 3;
    }

    public static float randomThreshold(Block block) {
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) {
            block = Blocks.GRASS_BLOCK;
        } else if (block == TRMTBlocks.ERODED_DIRT.get()) {
            block = Blocks.DIRT;
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
            block = Blocks.COARSE_DIRT;
        } else if (block == TRMTBlocks.ERODED_SAND.get()) {
            block = Blocks.SAND;
        }

        TRMTConfig cfg = TRMTConfig.get();
        TRMTConfig.MinMax range;

        if (block == Blocks.GRASS_BLOCK) {
            range = cfg.erosionThresholds.grass;
        } else if (block == Blocks.DIRT) {
            range = cfg.erosionThresholds.dirt;
        } else if (block == Blocks.COARSE_DIRT) {
            range = cfg.erosionThresholds.coarseDirt;
        } else if (block == Blocks.SAND) {
            range = cfg.erosionThresholds.sand;
        } else if (VEGETATION.contains(block)) {
            range = cfg.erosionThresholds.vegetation;
        } else if (block instanceof LeavesBlock) {
            range = cfg.erosionThresholds.leaves;
        } else {
            range = cfg.erosionThresholds.grass;
        }
        float min = range.min, max = range.max;
        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static final long TICKS_PER_DAY = 24000L;

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public static boolean isIsolated(Level level, BlockPos pos, ErosionMapManager manager) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.relative(dir).above(dy);
                BlockState neighborState = level.getBlockState(neighbor);
                Block neighborBlock = neighborState.getBlock();
                if (neighborBlock == TRMTBlocks.ERODED_GRASS_BLOCK.get()
                        || neighborBlock == TRMTBlocks.ERODED_DIRT.get()
                        || neighborBlock == TRMTBlocks.ERODED_COARSE_DIRT.get()
                        || neighborBlock == TRMTBlocks.ERODED_SAND.get()) {
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

    public static long getGrassDeErosionTimeout(int stage) {
        TRMTConfig cfg = TRMTConfig.get();
        TRMTConfig.GrassDeErosion g = cfg.deErosionTimeoutDays.grass;
        return switch (stage) {
            case 1  -> (long)(g.stage1 * TICKS_PER_DAY);
            case 2  -> (long)(g.stage2 * TICKS_PER_DAY);
            case 3  -> (long)(g.stage3 * TICKS_PER_DAY);
            case 4  -> (long)(g.stage4 * TICKS_PER_DAY);
            default -> (long)(g.stage5 * TICKS_PER_DAY);
        };
    }

    public static long getSandDeErosionTimeout(int stage) {
        TRMTConfig cfg = TRMTConfig.get();
        TRMTConfig.SandDeErosion s = cfg.deErosionTimeoutDays.sand;
        return (long)((switch (stage) {
            case 0  -> s.stage1;
            case 1  -> s.stage2;
            case 2  -> s.stage3;
            case 3  -> s.stage4;
            default -> s.stage5;
        }) * TICKS_PER_DAY);
    }

    public static long getDirtDeErosionTimeout(Block block) {
        TRMTConfig cfg = TRMTConfig.get();
        TRMTConfig.DirtDeErosion d = cfg.deErosionTimeoutDays.dirt;
        if (block == TRMTBlocks.ERODED_DIRT.get()) return (long)(d.erodedDirt * TICKS_PER_DAY);
        return (long)(d.erodedCoarseDirt * TICKS_PER_DAY);
    }
}
