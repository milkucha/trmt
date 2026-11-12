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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.IPlantable;

public class ErodedSandBlock extends Block implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

    // Collision shapes: flat 10/16 for stages 1–4; full height for stage 0.
    private static final VoxelShape[] COLLISION_SHAPES = {
        Block.box(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.box(0, 0, 0, 16, 10, 16), // stage 1
        Block.box(0, 0, 0, 16, 10, 16), // stage 2
        Block.box(0, 0, 0, 16, 10, 16), // stage 3
        Block.box(0, 0, 0, 16, 10, 16), // stage 4
    };

    // Outline shapes: match the visual model height for each stage.
    private static final VoxelShape[] OUTLINE_SHAPES = {
        Block.box(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.box(0, 0, 0, 16, 14, 16), // stage 1 — 14/16
        Block.box(0, 0, 0, 16, 14, 16), // stage 2 — 14/16
        Block.box(0, 0, 0, 16, 12, 16), // stage 3 — 12/16
        Block.box(0, 0, 0, 16, 10, 16), // stage 4 — 10/16
    };

    public ErodedSandBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(STAGE, 0)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, BlockStateProperties.WATERLOGGED);
    }

    // Only stages 1–4 (sunken) accept waterlogging; stage 0 is full-height and does not.
    @Override
    public boolean canPlaceLiquid(BlockGetter world, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(BlockStateProperties.WATERLOGGED) && state.getValue(STAGE) > 0;
    }

    @Override
    public boolean placeLiquid(LevelAccessor world, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!canPlaceLiquid(world, pos, state, fluidState.getType())) return false;
        world.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, true),
                Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
        world.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(world));
        return true;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // Reverts as part of the shape update, so it also runs during client placement prediction and
        // covers every way a block can appear or change above (pistons, falling blocks, trapdoors opening...).
        if (direction == Direction.UP
                && BlockThresholds.classifyAboveSand(neighborState, world, neighborPos) == BlockThresholds.SandAbove.TOUCHES_GROUND) {
            // The server-level guard matters: updateShape also runs on the client and on worldgen regions.
            if (world instanceof Level w && !w.isClientSide) ErosionMapManager.get(w).removeEntry(pos);
            return Blocks.SAND.defaultBlockState();
        }
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean isMoving) {
        super.neighborChanged(state, world, pos, sourceBlock, sourcePos, isMoving);
        if (world.isClientSide) return;
        if (!BlockThresholds.shouldSandRevert(world, pos)) return;
        world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        ErosionMapManager.get(world).removeEntry(pos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (BlockThresholds.shouldSandRevert(world, pos)) {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            ErosionMapManager.get(world).removeEntry(pos);
            return;
        }
        if (!TRMTConfig.get().deErosion.sandEnabled) return;

        ErosionMapManager manager = ErosionMapManager.get(world);
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
            // Stage 0 cannot be waterlogged — clear the flag when reverting to it.
            if (stage == 1) newState = newState.setValue(BlockStateProperties.WATERLOGGED, false);
            world.setBlock(pos, newState, Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), newCooldownTime);
        } else {
            world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            manager.removeEntry(pos);
        }
    }

    /**
     * Forge 47 asks the soil block (IForgeBlock.canSustainPlant) before the vanilla tag checks, and cactus
     * has no vanilla tag check at all. Answer exactly as vanilla sand would, for every stage: sugar cane
     * (still needs adjacent water), cactus, and bush plants such as the dead bush.
     */
    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, IPlantable plantable) {
        return Blocks.SAND.canSustainPlant(Blocks.SAND.defaultBlockState(), level, pos, facing, plantable);
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
