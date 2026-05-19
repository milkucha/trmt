package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Dirt block produced by foot-traffic erosion.
 * Stores a {@link #FACING} direction so downstream stages preserve the rotation that
 * was established when the preceding grass stage was eroded.
 * Never placed by players or generated naturally — only set by the erosion system.
 */
public class ErodedDirtBlock extends ErodedBlock {

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    /** Preserves the rotation of the eroded grass stage that preceded this block. */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage for eroded_dirt (0–3).
     * 0 = plain eroded dirt, 1–3 = progressively more eroded using eroded_dirt_0/1/2 textures.
     * Only used by the ERODED_DIRT block; other eroded blocks always stay at stage 0.
     */
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 3);

    public ErodedDirtBlock(Settings settings) {
        super(settings,
                () -> TRMTConfig.get().deErosion.dirtEnabled,
                state -> BlockThresholds.getDirtDeErosionTimeout(state.getBlock()),
                ErodedDirtBlock::fallbackDeErosion);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(STAGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    private static DeErosionResult fallbackDeErosion(BlockState state) {
        Direction facing = state.get(FACING);
        Block block = state.getBlock();

        if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            return new DeErosionResult(
                    TRMTBlocks.ERODED_DIRT.getDefaultState().with(FACING, facing).with(STAGE, 3),
                    true);
        }

        int stage = state.get(STAGE);
        if (stage > 0) {
            return new DeErosionResult(state.with(STAGE, stage - 1), true);
        }

        return new DeErosionResult(
                TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                        .with(ErodedGrassBlock.FACING, facing)
                        .with(ErodedGrassBlock.STAGE, 4),
                true);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
