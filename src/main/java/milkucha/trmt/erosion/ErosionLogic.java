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
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

public final class ErosionLogic {

    private ErosionLogic() {}

    public static boolean isTracked(BlockState state, TRMTConfig.ErosionToggles erosion) {
        Block block = state.getBlock();
        return (erosion.grassEnabled && (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (state.isOf(Blocks.DIRT) || state.isOf(TRMTBlocks.ERODED_DIRT)))
                || (erosion.sandEnabled && (state.isOf(Blocks.SAND) || state.isOf(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block));
    }

    public static BlockPos getGroundPos(Entity entity) {
        BlockPos groundPos = entity.getBlockPos().down();
        World world = entity.getWorld();
        BlockState groundUpState = world.getBlockState(groundPos.up());
        if (groundUpState.isOf(TRMTBlocks.ERODED_SAND) || groundUpState.isOf(Blocks.SAND)) {
            return groundPos.up();
        }
        return groundPos;
    }

    public static void stepVegetation(World world, ErosionMapManager manager,
                                      BlockPos groundPos, float amount, long gameTime) {
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        BlockPos vegPos = groundPos.up();
        BlockState vegState = world.getBlockState(vegPos);
        if (!erosion.vegetationEnabled || !BlockThresholds.isVegetation(vegState.getBlock())) {
            return;
        }

        manager.onStep(vegPos, vegState.getBlock(), amount, gameTime);
        tryBreakVegetation(world, manager, vegPos, vegState);
        manager.broadcastEntryUpdate(vegPos, vegState.getBlock());
    }

    public static boolean stepGround(World world, ErosionMapManager manager,
                                     BlockPos groundPos, float amount, long gameTime) {
        stepVegetation(world, manager, groundPos, amount, gameTime);

        BlockState state = world.getBlockState(groundPos);
        if (!isTracked(state, TRMTConfig.get().erosion)) {
            return false;
        }

        Block block = state.getBlock();
        manager.onStep(groundPos, block, amount, gameTime);
        tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);
        return true;
    }

    public static void stepIfTracked(World world, ErosionMapManager manager,
                                     BlockPos pos, float amount, long gameTime) {
        BlockState state = world.getBlockState(pos);
        if (!isTracked(state, TRMTConfig.get().erosion)) {
            return;
        }

        manager.onStep(pos, state.getBlock(), amount, gameTime);
        tryTransform(world, manager, pos);
    }

    public static void tryBreakVegetation(World world, ErosionMapManager manager,
                                          BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.getBlock() instanceof TallPlantBlock
                && state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.up();
            if (world.getBlockState(upper).isOf(state.getBlock())) {
                world.removeBlock(upper, false);
            }
            if (state.isOf(Blocks.TALL_GRASS)) {
                world.setBlockState(pos, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                return;
            }
        }

        float dropChance = TRMTConfig.get().erosionThresholds.vegetation.dropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        world.breakBlock(pos, drops);
        manager.removeEntry(pos);
    }

    public static void tryTransform(World world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.isOf(Blocks.SAND)) {
            if (!world.getBlockState(pos.up()).isAir()) return;
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
            if (!world.getBlockState(pos.up()).isAir()) return;
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
            Block originalBlock = manager.getOriginalBlock(pos, Blocks.GRASS_BLOCK);
            if (currentStage < 4) {
                world.setBlockState(pos, state.with(ErodedGrassBlock.STAGE, currentStage + 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getTime(), originalBlock);
                return;
            }
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime(), originalBlock);
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_DIRT)) {
            Direction facing = state.get(ErodedDirtBlock.FACING);
            int currentStage = state.get(ErodedDirtBlock.STAGE);
            Block originalBlock = manager.getOriginalBlock(pos, Blocks.GRASS_BLOCK);
            if (currentStage < 3) {
                world.setBlockState(pos, state.with(ErodedDirtBlock.STAGE, currentStage + 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime(), originalBlock);
                return;
            }
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_COARSE_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_COARSE_DIRT, world.getTime(), originalBlock);
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
        manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime(), Blocks.DIRT);
    }

    private static Direction rotationToFacing(int rotation) {
        return switch (rotation) {
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }
}
