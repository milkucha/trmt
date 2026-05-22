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

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity entity, BlockGetter getter, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(WATERLOGGED) && state.getValue(STAGE) > 0 && fluid == Fluids.WATER;
    }

    @Override
    public boolean placeLiquid(LevelAccessor world, BlockPos pos, BlockState state, FluidState fluidState) {
        if (state.getValue(WATERLOGGED) || state.getValue(STAGE) == 0) return false;
        if (!world.isClientSide()) {
            world.setBlock(pos, state.setValue(WATERLOGGED, true), Block.UPDATE_ALL);
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return true;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.defaultFluidState() : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess tickAccess,
                                   BlockPos pos, Direction direction, BlockPos neighborPos,
                                   BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            tickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, world, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock,
                                 @Nullable Orientation wireOrientation, boolean notify) {
        super.neighborChanged(state, world, pos, sourceBlock, wireOrientation, notify);
        if (!world.isClientSide() && world.getBlockState(pos.above()).canOcclude()) {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getBlockState(pos.above()).canOcclude()) {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        if (!TRMTConfig.get().deErosion.sandEnabled) return;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap chunkMap = manager.getChunkMap(ChunkPos.containing(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        int stage = state.getValue(STAGE);
        long currentTime = world.getGameTime();
        long timeout = BlockThresholds.getSandDeErosionTimeout(stage);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        if (stage > 0) {
            boolean waterlogged = state.getValue(WATERLOGGED);
            BlockState newState = state.setValue(STAGE, stage - 1);
            // Stage 0 cannot be waterlogged; clear the flag when reverting to it.
            newState = newState.setValue(WATERLOGGED, stage - 1 > 0 && waterlogged);
            world.setBlock(pos, newState, Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, currentTime);
        } else {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            manager.removeEntry(pos);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPES[state.getValue(STAGE)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES[state.getValue(STAGE)];
    }
}
