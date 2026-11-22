package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public class ErodedSandBlock extends Block implements Waterloggable {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 4);

    // Collision shapes: flat 10/16 for stages 1–4; full height for stage 0.
    private static final VoxelShape[] COLLISION_SHAPES = {
        Block.createCuboidShape(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.createCuboidShape(0, 0, 0, 16, 10, 16), // stage 1
        Block.createCuboidShape(0, 0, 0, 16, 10, 16), // stage 2
        Block.createCuboidShape(0, 0, 0, 16, 10, 16), // stage 3
        Block.createCuboidShape(0, 0, 0, 16, 10, 16), // stage 4
    };

    // Outline shapes: match the visual model height for each stage.
    private static final VoxelShape[] OUTLINE_SHAPES = {
        Block.createCuboidShape(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.createCuboidShape(0, 0, 0, 16, 14, 16), // stage 1 — 14/16
        Block.createCuboidShape(0, 0, 0, 16, 14, 16), // stage 2 — 14/16
        Block.createCuboidShape(0, 0, 0, 16, 12, 16), // stage 3 — 12/16
        Block.createCuboidShape(0, 0, 0, 16, 10, 16), // stage 4 — 10/16
    };

    public ErodedSandBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.SOUTH)
                .with(STAGE, 0)
                .with(Properties.WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, Properties.WATERLOGGED);
    }

    // Only stages 1–4 (sunken) accept waterlogging; stage 0 is full-height and does not.
    @Override
    public boolean canFillWithFluid(PlayerEntity player, BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.get(Properties.WATERLOGGED) && state.get(STAGE) > 0;
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!canFillWithFluid(null, world, pos, state, fluidState.getFluid())) return false;
        world.setBlockState(pos, state.with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        world.scheduleFluidTick(pos, fluidState.getFluid(), fluidState.getFluid().getTickRate(world));
        return true;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(Properties.WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        // Reverts as part of the shape update, so it also runs during client placement prediction and
        // covers every way a block can appear or change above (pistons, falling blocks, trapdoors opening...).
        if (direction == Direction.UP
                && BlockThresholds.classifyAboveSand(neighborState, world, neighborPos) == BlockThresholds.SandAbove.TOUCHES_GROUND) {
            if (world instanceof World w && !w.isClient) ErosionMapManager.get(w).removeEntry(pos);
            return Blocks.SAND.getDefaultState();
        }
        world.scheduleBlockTick(pos, this, FALL_DELAY);
        if (state.get(Properties.WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    /** Same delay as vanilla FallingBlock.getFallDelay(). */
    private static final int FALL_DELAY = 2;

    /** Vanilla sand's support test: nothing solid below (and not below the world). */
    private static boolean isUnsupported(World world, BlockPos pos) {
        return FallingBlock.canFallThrough(world.getBlockState(pos.down())) && pos.getY() >= world.getBottomY();
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleBlockTick(pos, this, FALL_DELAY);
    }

    /** Unsupported eroded sand turns into vanilla sand (erosion lost); vanilla sand then falls by its own rules. */
    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!isUnsupported(world, pos)) return;
        world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
        ErosionMapManager.get(world).removeEntry(pos);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        if (!BlockThresholds.shouldSandRevert(world, pos)) return;
        world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
        ErosionMapManager.get(world).removeEntry(pos);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (isUnsupported(world, pos)) {
            world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
            ErosionMapManager.get(world).removeEntry(pos);
            return;
        }
        if (BlockThresholds.shouldSandRevert(world, pos)) {
            world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
            ErosionMapManager.get(world).removeEntry(pos);
            return;
        }
        if (!TRMTConfig.get().deErosion.sandEnabled) return;
        int stage = state.get(STAGE);
        // Permanent stage: no natural de-erosion, with or without an entry.
        if (BlockThresholds.isPermanentSand(stage)) return;

        ErosionMapManager manager = ErosionMapManager.get(world);
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        long currentTime = world.getTime();
        long timeout = BlockThresholds.getSandDeErosionTimeout(stage);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        if (stage > 0) {
            BlockState newState = state.with(STAGE, stage - 1);
            // Stage 0 cannot be waterlogged — clear the flag when reverting to it.
            if (stage == 1) newState = newState.with(Properties.WATERLOGGED, false);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, newCooldownTime);
        } else {
            world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPES[state.get(STAGE)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPES[state.get(STAGE)];
    }
}
