package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Dirt block produced by foot-traffic erosion.
 * Stores a {@link #FACING} direction so downstream stages preserve the rotation that
 * was established when the preceding grass stage was eroded.
 * Never placed by players or generated naturally — only set by the erosion system.
 */
public class ErodedDirtBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    /** Preserves the rotation of the eroded grass stage that preceded this block. */
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage for eroded_dirt (0–3).
     * 0 = plain eroded dirt, 1–3 = progressively more eroded using eroded_dirt_0/1/2 textures.
     * Only used by the ERODED_DIRT block; other eroded blocks always stay at stage 0.
     */
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 3);

    public ErodedDirtBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(STAGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        long currentTime = world.getTime();
        long timeout = BlockThresholds.getDirtDeErosionTimeout(state.getBlock());
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        Direction facing = state.get(FACING);
        Block block = state.getBlock();

        if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            // De-erode to the most eroded dirt stage.
            world.setBlockState(pos, TRMTBlocks.ERODED_DIRT.getDefaultState().with(FACING, facing).with(STAGE, 3), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, currentTime);
        } else if (block == TRMTBlocks.ERODED_DIRT) {
            int stage = state.get(STAGE);
            if (stage > 0) {
                // Step down one visual stage.
                world.setBlockState(pos, state.with(STAGE, stage - 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, currentTime);
            } else {
                // Stage 0 → revert to eroded grass block at its most-eroded stage, preserving rotation.
                world.setBlockState(pos,
                        TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                                .with(ErodedGrassBlock.FACING, facing)
                                .with(ErodedGrassBlock.STAGE, 4),
                        Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
            }
        }
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
