package milkucha.trmt.block;

import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public class ErodedSandBlock extends ErodedBlock {

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

    // Outline shapes: match the visual model height for each stage so the selection
    // box and raycasting target align with what the player sees.
    private static final VoxelShape[] OUTLINE_SHAPES = {
        Block.createCuboidShape(0, 0, 0, 16, 16, 16), // stage 0 — full height
        Block.createCuboidShape(0, 0, 0, 16, 14, 16), // stage 1 — 14/16
        Block.createCuboidShape(0, 0, 0, 16, 14, 16), // stage 2 — 14/16
        Block.createCuboidShape(0, 0, 0, 16, 12, 16), // stage 3 — 12/16
        Block.createCuboidShape(0, 0, 0, 16, 10, 16), // stage 4 — 10/16
    };

    public ErodedSandBlock(Settings settings) {
        super(settings,
                () -> TRMTConfig.get().deErosion.sandEnabled,
                state -> BlockThresholds.getSandDeErosionTimeout(state.get(STAGE)),
                state -> {
                    int stage = state.get(STAGE);
                    if (stage > 0) {
                        return new DeErosionResult(state.with(STAGE, stage - 1), true);
                    }
                    return new DeErosionResult(Blocks.SAND.getDefaultState(), false);
                });
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(STAGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
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
