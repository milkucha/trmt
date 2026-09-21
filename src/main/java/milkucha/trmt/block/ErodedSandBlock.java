package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

public class ErodedSandBlock extends Block implements SimpleWaterloggedBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape[] COLLISION_SHAPES = {
        Block.box(0, 0, 0, 16, 16, 16), // stage 0
        Block.box(0, 0, 0, 16, 10, 16), // stage 1
        Block.box(0, 0, 0, 16, 10, 16), // stage 2
        Block.box(0, 0, 0, 16, 10, 16), // stage 3
        Block.box(0, 0, 0, 16, 10, 16)  // stage 4
    };

    private static final VoxelShape[] OUTLINE_SHAPES = {
        Block.box(0, 0, 0, 16, 16, 16), // stage 0
        Block.box(0, 0, 0, 16, 14, 16), // stage 1 - matches model height
        Block.box(0, 0, 0, 16, 14, 16), // stage 2 - matches model height
        Block.box(0, 0, 0, 16, 12, 16), // stage 3 - matches model height
        Block.box(0, 0, 0, 16, 10, 16)  // stage 4 - matches model height (same as collision)
    };

    public ErodedSandBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(STAGE, 0)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, WATERLOGGED);
    }

    // --- Waterloggable Overrides ---

    /** Returns the fluid state based on the block's waterlogged property. */
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /** Checks if the block can be filled with a fluid (only sunken stages).
     *  26.3: BucketPickup/LiquidBlockContainer now take LivingEntity instead of Player. */
    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return state.getValue(STAGE) > 0 && !state.getValue(WATERLOGGED) && fluid == Fluids.WATER;
    }

    /** Attempts to fill the block with water and schedules a tick. */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (state.getValue(STAGE) > 0 && !state.getValue(WATERLOGGED)) {
            if (!level.isClientSide()) {
                // Set the block state and schedule a tick to propagate fluids correctly.
                level.setBlock(pos, state.setValue(WATERLOGGED, true), Block.UPDATE_ALL);
                level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
            }
            return true;
        }
        return false;
    }

    /** Attempts to drain water from the block. */
    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(STAGE) > 0 && state.getValue(WATERLOGGED)) {
            level.setBlock(pos, state.setValue(WATERLOGGED, false), Block.UPDATE_ALL);
            return new ItemStack(Items.WATER_BUCKET);
        }
        return ItemStack.EMPTY;
    }

    /** Handles fluid ticking during neighbor updates. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                      Direction directionToNeighbor, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbor, neighborPos, neighborState, random);
    }

    // --- Block Overrides (Lifecycle) ---

    /** 26.3: neighborChanged's 5th parameter changed from a source BlockPos to a nullable Orientation. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock, net.minecraft.world.level.redstone.Orientation orientation, boolean notify) {
        super.neighborChanged(state, level, pos, sourceBlock, orientation, notify);
        if (level.isClientSide()) return;
        // Only react if the block above is opaque and we are not in a client context.
        if (!level.getBlockState(pos.above()).canOcclude()) return;

        // Reset to base sand state upon neighbor interaction, simulating removal of erosion effect.
        level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        ErosionMapManager.getInstance().removeEntry(pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Check if the block above is opaque (i.e., not air/empty space).
        if (!level.getBlockState(pos.above()).canOcclude()) return;

        if (!TRMTConfig.get().deErosion.sandEnabled) return;

        ChunkErosionMap chunkMap = ErosionMapManager.getInstance().getChunkMap(ChunkPos.containing(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        int stage = state.getValue(STAGE);
        long currentTime = level.getGameTime();
        // Determine the required cooldown time based on current stage.
        long timeout = BlockThresholds.getSandDeErosionTimeout(stage);
        if (BlockThresholds.isIsolated(level, pos, ErosionMapManager.getInstance())) {
            timeout /= 2; // Halve timeout if isolated
        }

        // Check if the cooldown period has passed since last touch/check.
        long requiredTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;
        if (currentTime < requiredTime) return;

        // --- Erosion Logic ---
        if (stage > 0) {
            // De-erode: Move to the previous stage, preserving waterlogged state if it was sunken enough.
            boolean keepWaterlogged = stage > 1 && state.getValue(WATERLOGGED);
            level.setBlock(pos, state.setValue(STAGE, Math.max(0, stage - 1)).setValue(WATERLOGGED, keepWaterlogged), Block.UPDATE_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
            // Write the new cooldown time for the next check.
            ErosionMapManager.getInstance().writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, currentTime);
        } else {
            // If stage is 0 (full height), revert to vanilla sand and clear tracking data.
            level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPES[state.getValue(STAGE)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES[state.getValue(STAGE)];
    }
}
