package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-block-type threshold ranges for the erosion transformation chain.
 * Ranges are read from {@link TRMTConfig} so they can be tuned via
 * {@code config/trmt.json} without recompiling.
 */
public final class BlockThresholds {

    private static volatile Set<Block> resolvedVegetation = null;

    public static void invalidateVegetationCache() {
        resolvedVegetation = null;
    }

    public static Set<Block> getVegetationSet() {
        if (resolvedVegetation == null) {
            List<String> ids = TRMTConfig.get().erodableVegetation;
            Set<Block> resolved = new HashSet<>();
            if (ids != null) {
                for (String id : ids) {
                    Identifier identifier = Identifier.tryParse(id);
                    if (identifier == null) {
                        TRMT.LOGGER.warn("[TRMT] erodableVegetation: '{}' is not a valid identifier, skipping", id);
                        continue;
                    }
                    Registries.BLOCK.getOrEmpty(identifier).ifPresentOrElse(
                        resolved::add,
                        () -> TRMT.LOGGER.warn("[TRMT] erodableVegetation: unknown block '{}', skipping", id)
                    );
                }
            }
            resolvedVegetation = Set.copyOf(resolved);
        }
        return resolvedVegetation;
    }

    private BlockThresholds() {}

    public static boolean isVegetation(Block block) {
        return getVegetationSet().contains(block);
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
        ErosionThresholdRange range = ErosionLogic.getThresholdRange(block)
                .orElseGet(() -> getFallbackThresholdRange(block));

        float min = range.min(), max = range.max();

        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static ErosionThresholdRange getFallbackThresholdRange(Block block) {
        if (getVegetationSet().contains(block)) {
            TRMTConfig.VegetationThreshold range = TRMTConfig.get().erosionThresholds.vegetation;
            return new ErosionThresholdRange(range.min, range.max);
        }

        return new ErosionThresholdRange(2.0f, 4.0f);
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

    /** Returns the de-erosion inactivity timeout (ticks) for the given grass erosion stage (1–5). */
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

    /** Returns the de-erosion inactivity timeout (ticks) for the given eroded sand stage (0–4). */
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

    /** Returns the de-erosion inactivity timeout (ticks) for the given eroded dirt block type. */
    public static long getDirtDeErosionTimeout(Block block) {
        TRMTConfig cfg = TRMTConfig.get();
        TRMTConfig.DirtDeErosion d = cfg.deErosionTimeoutDays.dirt;
        if (block == TRMTBlocks.ERODED_DIRT) return (long)(d.erodedDirt       * TICKS_PER_DAY);
        return (long)(d.erodedCoarseDirt * TICKS_PER_DAY);
    }
}
