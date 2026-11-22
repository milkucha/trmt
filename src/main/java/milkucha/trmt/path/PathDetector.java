package milkucha.trmt.path;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stateless scoring of "is this spot a path, and how worn is it" for the path speed buff.
 *
 * <p>A pure function of block states: it reads nothing but the {@link BlockGetter} and the
 * {@link PathWeights} it is handed (no erosion map, no server, no config singleton), and has no
 * server-only imports, so client code can call it too.
 *
 * <p>Window: the 3x3 columns around {@code feet}, over three layers (feet-1, feet, feet+1) = 27
 * positions. {@code density = count / 9}: a 1-wide straight path scores 3/9, 2-wide 6/9, 3-wide 9/9.
 */
public final class PathDetector {

    private PathDetector() {}

    /** Extra depth margin that must be crossed before the tier drops again. */
    private static final float TIER_MARGIN = 0.25f;

    /**
     * @param count    number of window positions whose block counts as path (weight above 0)
     * @param avgDepth sum of the weights of those blocks divided by {@code count} (0 when {@code count} is 0)
     */
    public record PathSample(int count, float avgDepth) {
        /** May exceed 1.0 on slopes (more than 9 counted blocks in the 27 positions). */
        public float density() {
            return count / 9f;
        }
    }

    /** Depth weight per eroded block state, built once from the config; 0 means "does not count". */
    public static final class PathWeights {
        private final float[] grass = new float[5];
        private final float[] dirt = new float[4];
        private final float coarseDirt;
        private final float[] sand = new float[5];

        /** Fixed per-stage depth weights; a class switched off in the config counts as weight 0 (not path). */
        public PathWeights(TRMTConfig.PathBlockToggles c) {
            if (c.erodedGrass) { grass[0] = 1f; grass[1] = 1f; grass[2] = 2f; grass[3] = 2f; grass[4] = 2f; }
            if (c.erodedDirt) { dirt[0] = 3f; dirt[1] = 3f; dirt[2] = 3f; dirt[3] = 3f; }
            coarseDirt = c.erodedCoarseDirt ? 3f : 0f;
            if (c.erodedSand) { sand[0] = 1f; sand[1] = 1f; sand[2] = 2f; sand[3] = 2f; sand[4] = 3f; }
        }

        /** Depth weight of {@code state}; 0 for anything that is not one of the four eroded blocks. */
        public float weight(BlockState state) {
            if (state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())) return grass[state.getValue(ErodedGrassBlock.STAGE)];
            if (state.is(TRMTBlocks.ERODED_DIRT.get())) return dirt[state.getValue(ErodedDirtBlock.STAGE)];
            if (state.is(TRMTBlocks.ERODED_COARSE_DIRT.get())) return coarseDirt;
            if (state.is(TRMTBlocks.ERODED_SAND.get())) return sand[state.getValue(ErodedSandBlock.STAGE)];
            return 0f;
        }
    }

    /**
     * Scores the window around {@code feet} (the floor of the standing entity's position, not the
     * block under it: sunken eroded sand has the feet inside the block, flat ground has the
     * eroded layer at feet-1; both are inside the window).
     */
    public static PathSample sample(BlockGetter world, BlockPos feet, PathWeights weights) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        float sum = 0f;
        for (int y = feet.getY() - 1; y <= feet.getY() + 1; y++) {
            for (int x = feet.getX() - 1; x <= feet.getX() + 1; x++) {
                for (int z = feet.getZ() - 1; z <= feet.getZ() + 1; z++) {
                    float w = weights.weight(world.getBlockState(pos.set(x, y, z)));
                    if (w > 0f) {
                        count++;
                        sum += w;
                    }
                }
            }
        }
        return new PathSample(count, count == 0 ? 0f : sum / count);
    }

    /**
     * The tier (1-3) for a depth with no history: 3 from {@code tier3MinDepth}, 2 from
     * {@code tier2MinDepth}, else 1.
     */
    public static int baselineTier(float avgDepth, TRMTConfig.PathSpeedBuffConfig cfg) {
        if (avgDepth >= cfg.tier3MinDepth) return 3;
        if (avgDepth >= cfg.tier2MinDepth) return 2;
        return 1;
    }

    /**
     * Next tier (1-3) when the path is on, with a margin against flicker: moving up uses the plain
     * thresholds, moving down from tier N needs {@code avgDepth < tierNMinDepth - tierMargin}.
     * {@code currentTier} 0 (no buff yet) just returns the baseline tier.
     */
    public static int nextTier(int currentTier, float avgDepth, TRMTConfig.PathSpeedBuffConfig cfg) {
        int baseline = baselineTier(avgDepth, cfg);
        if (currentTier <= 0 || baseline >= currentTier) return baseline;
        int tier = Math.min(currentTier, 3);
        while (tier > 1 && avgDepth < minDepth(tier, cfg) - TIER_MARGIN) tier--;
        return Math.max(tier, baseline);
    }

    private static float minDepth(int tier, TRMTConfig.PathSpeedBuffConfig cfg) {
        return tier >= 3 ? cfg.tier3MinDepth : cfg.tier2MinDepth;
    }

    /** The speed bonus of a tier, in percent (0 for tier 0). */
    public static float tierPercent(int tier, TRMTConfig.PathSpeedBuffConfig cfg) {
        return switch (tier) {
            case 1 -> cfg.tier1Percent;
            case 2 -> cfg.tier2Percent;
            case 3 -> cfg.tier3Percent;
            default -> 0f;
        };
    }
}
