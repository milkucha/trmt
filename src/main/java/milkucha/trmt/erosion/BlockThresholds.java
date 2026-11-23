package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTNeoForge;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

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
                        TRMTNeoForge.LOGGER.warn("[TRMT] erodableVegetation: '{}' is not a valid identifier, skipping", id);
                        continue;
                    }
                    BuiltInRegistries.BLOCK.getOptional(identifier).ifPresentOrElse(
                        resolved::add,
                        () -> TRMTNeoForge.LOGGER.warn("[TRMT] erodableVegetation: unknown block '{}', skipping", id)
                    );
                }
            }
            resolvedVegetation = Set.copyOf(resolved);
        }
        return resolvedVegetation;
    }

    // -- "block above hides the ground" rules --------------------------------

    private static volatile Set<Block> resolvedRevertAlways = null;
    private static volatile Set<Block> resolvedRevertNever = null;

    /** Drops the cached {@code revertAlwaysBlocks} / {@code revertNeverBlocks} sets; call whenever the config is (re)loaded. */
    public static void invalidateRevertCaches() {
        resolvedRevertAlways = null;
        resolvedRevertNever = null;
    }

    private static Set<Block> resolveBlockList(List<String> ids, String key) {
        Set<Block> resolved = new HashSet<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier identifier = Identifier.tryParse(id);
                if (identifier == null) {
                    TRMTNeoForge.LOGGER.warn("[TRMT] {}: '{}' is not a valid identifier, skipping", key, id);
                    continue;
                }
                BuiltInRegistries.BLOCK.getOptional(identifier).ifPresentOrElse(
                    resolved::add,
                    () -> TRMTNeoForge.LOGGER.warn("[TRMT] {}: unknown block '{}', skipping", key, id)
                );
            }
        }
        return resolved;
    }

    private static void resolveRevertSets() {
        TRMTConfig cfg = TRMTConfig.get();
        Set<Block> always = resolveBlockList(cfg.revertAlwaysBlocks, "revertAlwaysBlocks");
        Set<Block> never = resolveBlockList(cfg.revertNeverBlocks, "revertNeverBlocks");
        for (Block b : never) {
            if (always.remove(b)) {
                TRMTNeoForge.LOGGER.warn("[TRMT] '{}' is in both revertAlwaysBlocks and revertNeverBlocks; revertNeverBlocks wins",
                        BuiltInRegistries.BLOCK.getKey(b));
            }
        }
        resolvedRevertAlways = Set.copyOf(always);
        resolvedRevertNever = Set.copyOf(never);
    }

    public static Set<Block> getRevertAlwaysSet() {
        if (resolvedRevertAlways == null) resolveRevertSets();
        return resolvedRevertAlways;
    }

    public static Set<Block> getRevertNeverSet() {
        if (resolvedRevertNever == null) resolveRevertSets();
        return resolvedRevertNever;
    }

    /**
     * Eroded dirt / coarse dirt / grass: true if the block above hides the ground completely and the
     * eroded block must revert to vanilla. That is a full-cube outline that is also opaque, plus the
     * "always" blocks (shulker boxes and {@code revertAlwaysBlocks}); powder snow and
     * {@code revertNeverBlocks} never revert it (never wins over always). Not used for sand.
     */
    public static boolean shouldRevertUnder(BlockGetter world, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = world.getBlockState(above);
        Block block = aboveState.getBlock();
        if (aboveState.isAir()) return false;
        if (aboveState.is(Blocks.POWDER_SNOW) || getRevertNeverSet().contains(block)) return false;
        if (aboveState.is(BlockTags.SHULKER_BOXES) || getRevertAlwaysSet().contains(block)) return true;
        return aboveState.canOcclude() && Block.isShapeFullBlock(aboveState.getShape(world, above));
    }

    /** What the block above an eroded sand block does to it; see {@link #classifyAboveSand}. */
    public enum SandAbove {
        /** Air, fluid, moving piston, empty outline: the sand stays as is (erosion needs air above, see {@link #canSandErodeFurther}). */
        IGNORED,
        /** The block does not reach the ground (min Y above 0, a fence gate or a wall banner): placeable, sand stays eroded. */
        RAISED,
        /** Anything else: the sand reverts to vanilla sand. */
        TOUCHES_GROUND
    }

    /** Classifies {@code aboveState} (the state at {@code abovePos}) for the eroded sand below it, any stage. */
    public static SandAbove classifyAboveSand(BlockState aboveState, BlockGetter world, BlockPos abovePos) {
        if (aboveState.isAir() || aboveState.getBlock() instanceof LiquidBlock || aboveState.is(Blocks.MOVING_PISTON)) {
            return SandAbove.IGNORED;
        }
        VoxelShape shape = aboveState.getShape(world, abovePos);
        if (shape.isEmpty()) return SandAbove.IGNORED;
        // Fence gates and wall banners count as raised regardless of their outline; standing banners do not.
        if (aboveState.is(BlockTags.FENCE_GATES) || aboveState.getBlock() instanceof WallBannerBlock
                || shape.min(Direction.Axis.Y) > 1.0E-6) return SandAbove.RAISED;
        return SandAbove.TOUCHES_GROUND;
    }

    /** True if the block above the eroded sand at {@code pos} makes it revert to vanilla sand. */
    public static boolean shouldSandRevert(BlockGetter world, BlockPos pos) {
        BlockPos above = pos.above();
        return classifyAboveSand(world.getBlockState(above), world, above) == SandAbove.TOUCHES_GROUND;
    }

    /** True if sand at {@code pos} may advance its erosion: only air above it (a fluid or any other block stops erosion). */
    public static boolean canSandErodeFurther(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos.above()).isAir();
    }

    /**
     * True if placing {@code placed} at {@code pos} on top of eroded sand must be refused: it would touch the
     * ground (so the sand would have to revert) without being a full cube or a sand plant. Full cubes are
     * allowed (the sand reverts), blocks that stay clear of the ground are allowed (the sand stays eroded).
     */
    public static boolean refusesPlacementOnErodedSand(BlockState placed, BlockGetter world, BlockPos pos) {
        if (isSandPlant(placed.getBlock())) return false;
        if (classifyAboveSand(placed, world, pos) != SandAbove.TOUCHES_GROUND) return false;
        return !Block.isShapeFullBlock(placed.getShape(world, pos));
    }

    /**
     * Sugar cane, cactus, dead bush (and the other {@code DryVegetationBlock}s, short/tall dry grass, which
     * share its placement check) and bamboo (stalk and sapling): the plants that may be planted on sand.
     */
    public static boolean isSandPlant(Block block) {
        return block instanceof SugarCaneBlock || block instanceof CactusBlock || block instanceof DryVegetationBlock
                || block instanceof BambooStalkBlock || block instanceof BambooSaplingBlock;
    }

    /**
     * {@code state.is(tag)} where eroded sand counts as vanilla sand. Redirected into the sand plants'
     * placement checks, so their normal conditions apply to eroded sand of every stage.
     */
    public static boolean isInSandAware(BlockState state, TagKey<Block> tag) {
        return (state.is(TRMTBlocks.ERODED_SAND.get()) ? Blocks.SAND.defaultBlockState() : state).is(tag);
    }

    private BlockThresholds() {}

    public static boolean isVegetation(Block block) {
        return getVegetationSet().contains(block);
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
        } else if (block == Blocks.SNOW) {
            range = cfg.erosionThresholds.snow;
        } else if (block == Blocks.SNOW_BLOCK) {
            range = cfg.erosionThresholds.snowBlock;
        } else if (getVegetationSet().contains(block)) {
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

    /**
     * Returns true if none of the 12 slope-aware horizontal neighbours (4 directions × 3 heights)
     * are an eroded block — meaning this block is an isolated erosion patch and should de-erode faster.
     */
    public static boolean isIsolated(Level world, BlockPos pos, ErosionMapManager manager) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.relative(dir).above(dy);
                BlockState neighborState = world.getBlockState(neighbor);
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
        if (block == TRMTBlocks.ERODED_DIRT.get()) return (long)(d.erodedDirt       * TICKS_PER_DAY);
        return (long)(d.erodedCoarseDirt * TICKS_PER_DAY);
    }

    // -- permanent stages (never de-erode naturally) ---------------------------

    /** True if eroded grass of block STAGE {@code blockStage} (0–4, config key stage1–stage5) never de-erodes naturally. */
    public static boolean isPermanentGrass(int blockStage) {
        TRMTConfig.GrassPermanent g = TRMTConfig.get().permanentStages.grass;
        return switch (blockStage) {
            case 0  -> g.stage1;
            case 1  -> g.stage2;
            case 2  -> g.stage3;
            case 3  -> g.stage4;
            default -> g.stage5;
        };
    }

    /** True if eroded sand of block STAGE {@code blockStage} (0–4, config key stage1–stage5) never de-erodes naturally. */
    public static boolean isPermanentSand(int blockStage) {
        TRMTConfig.SandPermanent s = TRMTConfig.get().permanentStages.sand;
        return switch (blockStage) {
            case 0  -> s.stage1;
            case 1  -> s.stage2;
            case 2  -> s.stage3;
            case 3  -> s.stage4;
            default -> s.stage5;
        };
    }

    /** True if the eroded dirt block type (every stage of eroded_dirt, or eroded_coarse_dirt) never de-erodes naturally. */
    public static boolean isPermanentDirt(Block block) {
        TRMTConfig.DirtPermanent d = TRMTConfig.get().permanentStages.dirt;
        if (block == TRMTBlocks.ERODED_DIRT.get()) return d.erodedDirt;
        if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) return d.erodedCoarseDirt;
        return false;
    }

    /** True if {@code state} (any eroded block) is a permanent stage; false for every other block. */
    public static boolean isPermanentStage(BlockState state) {
        Block block = state.getBlock();
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) return isPermanentGrass(state.getValue(ErodedGrassBlock.STAGE));
        if (block == TRMTBlocks.ERODED_SAND.get()) return isPermanentSand(state.getValue(ErodedSandBlock.STAGE));
        return isPermanentDirt(block);
    }

    // -- mob erosion whitelist -------------------------------------------------

    private static volatile List<String> mobWhitelistFrom = null;
    private static volatile Set<EntityType<?>> resolvedMobWhitelist = Set.of();

    /**
     * True if free-roaming mobs of {@code type} erode (config {@code erosionMobWhitelist}, entity type ids).
     * The resolved set is cached against the identity of the config list, so every config (re)load rebuilds it.
     */
    public static boolean isErodingMob(EntityType<?> type) {
        List<String> ids = TRMTConfig.get().erosionMobWhitelist;
        if (ids != mobWhitelistFrom) {
            Set<EntityType<?>> resolved = new HashSet<>();
            if (ids != null) {
                for (String id : ids) {
                    Identifier identifier = Identifier.tryParse(id);
                    if (identifier == null) {
                        TRMTNeoForge.LOGGER.warn("[TRMT] erosionMobWhitelist: '{}' is not a valid identifier, skipping", id);
                        continue;
                    }
                    BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).ifPresentOrElse(
                        resolved::add,
                        () -> TRMTNeoForge.LOGGER.warn("[TRMT] erosionMobWhitelist: unknown entity type '{}', skipping", id)
                    );
                }
            }
            resolvedMobWhitelist = Set.copyOf(resolved);
            mobWhitelistFrom = ids;
        }
        return resolvedMobWhitelist.contains(type);
    }
}
