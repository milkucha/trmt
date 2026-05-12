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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ErodedSandBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

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
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.SOUTH).setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
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
            world.setBlock(pos, state.setValue(STAGE, stage - 1), Block.UPDATE_ALL);
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
