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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ErodedSandBlock extends Block implements SimpleWaterloggedBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape[] COLLISION_SHAPES = {
        Block.box(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.box(0, 0, 0, 16, 10, 16), // stage 1
        Block.box(0, 0, 0, 16, 10, 16), // stage 2
        Block.box(0, 0, 0, 16, 10, 16), // stage 3
        Block.box(0, 0, 0, 16, 10, 16), // stage 4
    };

    private static final VoxelShape[] OUTLINE_SHAPES = {
        Block.box(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.box(0, 0, 0, 16, 14, 16), // stage 1
        Block.box(0, 0, 0, 16, 14, 16), // stage 2
        Block.box(0, 0, 0, 16, 12, 16), // stage 3
        Block.box(0, 0, 0, 16, 10, 16), // stage 4
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

    // --- SimpleWaterloggedBlock ---

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity entity, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(WATERLOGGED) && state.getValue(STAGE) > 0 && fluid == Fluids.WATER;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        // Only reachable when canPlaceLiquid returned true (stage > 0, not yet waterlogged).
        level.setBlock(pos, state.setValue(WATERLOGGED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return true;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                  BlockPos pos, Direction direction, BlockPos neighborPos,
                                  BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            tickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    // --- Neighbor / opaque revert ---

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
                                @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, world, pos, block, orientation, movedByPiston);
        if (!world.isClientSide() && world.getBlockState(pos.above()).canOcclude()) {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
        }
    }

    // --- Tick ---

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getBlockState(pos.above()).canOcclude()) {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
            return;
        }
        if (!TRMTConfig.get().deErosion.sandEnabled) return;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        int stage = state.getValue(STAGE);
        long currentTime = world.getGameTime();
        long timeout = BlockThresholds.getSandDeErosionTimeout(stage);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;
        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        if (stage > 0) {
            BlockState newState = state.setValue(STAGE, stage - 1);
            // Stage 0 does not accept waterlogging; clear it when reverting 1→0.
            if (stage == 1) newState = newState.setValue(WATERLOGGED, false);
            world.setBlock(pos, newState, Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, newCooldownTime);
        } else {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            manager.removeEntry(pos);
        }
    }

    // --- Shapes ---

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPES[state.getValue(STAGE)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES[state.getValue(STAGE)];
    }
}
