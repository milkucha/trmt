package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

public class TRMTWalkingErosionUtil {
    public static void stepAdjacent(World world, ErosionMapManager manager,
                                    BlockPos pos, float amount, long gameTime) {
        BlockState adjState = world.getBlockState(pos);
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        if ((erosion.grassEnabled && (adjState.isOf(Blocks.GRASS_BLOCK) || adjState.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (adjState.isOf(Blocks.DIRT) || adjState.isOf(TRMTBlocks.ERODED_DIRT)))
                || (erosion.sandEnabled && (adjState.isOf(Blocks.SAND) || adjState.isOf(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(adjState.getBlock()))) {
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            tryTransform(world, manager, pos);
        }
    }

    public static void tryBreakVegetation(World world, ErosionMapManager manager,
                                          BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        // For double-height plants, remove the upper half first (no drops from upper half).
        if (state.getBlock() instanceof TallPlantBlock
                && state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.up();
            if (world.getBlockState(upper).isOf(state.getBlock())) {
                world.removeBlock(upper, false);
            }
        }

        float dropChance = TRMTConfig.get().erosionThresholds.vegetation.dropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        world.breakBlock(pos, drops);
        manager.removeEntry(pos);
    }

    /**
     * Checks whether the block at {@code pos} has accumulated enough erosion to transform,
     * and if so, advances it to the next stage in the chain and clears its entry.
     */
    public static void tryTransform(World world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) {
            return;
        }

        // Threshold reached — advance visual stage or transform the block.
        if (state.isOf(Blocks.SAND)) {
            Direction erodedFacing = rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_SAND.getDefaultState()
                            .with(ErodedSandBlock.FACING, erodedFacing)
                            .with(ErodedSandBlock.STAGE, 0),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getTime());
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_SAND)) {
            int stage = state.get(ErodedSandBlock.STAGE);
            if (stage < 4) {
                world.setBlockState(pos, state.with(ErodedSandBlock.STAGE, stage + 1), Block.NOTIFY_ALL);
            }
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getTime());
            return;
        }

        if (BlockThresholds.isLeaves(state.getBlock())) {
            float dropChance = TRMTConfig.get().erosionThresholds.leaves.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            world.breakBlock(pos, drops);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(Blocks.GRASS_BLOCK)) {
            // Threshold reached — place the real eroded grass block at stage 0.
            Direction erodedFacing = rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                            .with(ErodedGrassBlock.FACING, erodedFacing)
                            .with(ErodedGrassBlock.STAGE, 0),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getTime());
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            Direction facing = state.get(ErodedGrassBlock.FACING);
            int currentStage = state.get(ErodedGrassBlock.STAGE);
            if (currentStage < 4) {
                world.setBlockState(pos, state.with(ErodedGrassBlock.STAGE, currentStage + 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getTime());
                return;
            }
            // Stage 4 reached — convert to eroded_dirt, carrying FACING forward.
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_DIRT)) {
            Direction facing = state.get(ErodedDirtBlock.FACING);
            int currentStage = state.get(ErodedDirtBlock.STAGE);
            if (currentStage < 3) {
                // Advance to the next visual stage, preserving facing.
                world.setBlockState(pos,
                        state.with(ErodedDirtBlock.STAGE, currentStage + 1),
                        Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                return;
            }
            // Stage 3 reached — carry rotation forward to eroded_coarse_dirt.
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_COARSE_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (!state.isOf(Blocks.DIRT)) return;
        Direction erodedFacing = rotationToFacing(BlockThresholds.posRotation(pos));
        world.setBlockState(pos,
                TRMTBlocks.ERODED_DIRT.getDefaultState()
                        .with(ErodedDirtBlock.FACING, erodedFacing)
                        .with(ErodedDirtBlock.STAGE, 1),
                Block.NOTIFY_ALL);
        manager.removeEntry(pos);
    }

    /**
     * Maps a position rotation index (0–3, matching {@link BlockThresholds#posRotation})
     * to the corresponding {@link Direction} for the FACING block-state property.
     * 0 = SOUTH (0°), 1 = WEST (90° CW), 2 = NORTH (180°), 3 = EAST (270° CW).
     */
    public static Direction rotationToFacing(int rotation) {
        return switch (rotation) {
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }
}
