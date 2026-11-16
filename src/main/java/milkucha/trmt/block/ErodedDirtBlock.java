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
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
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
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage for eroded_dirt (0–3).
     * 0 = plain eroded dirt, 1–3 = progressively more eroded using eroded_dirt_0/1/2 textures.
     * Only used by the ERODED_DIRT block; other eroded blocks always stay at stage 0.
     */
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 3);

    /**
     * True when this eroded dirt was worn from plain dirt (not from grass). Such blocks recover back
     * to plain dirt instead of grass. Default false, so eroded dirt from older worlds recovers to grass.
     */
    public static final BooleanProperty FROM_DIRT = BooleanProperty.of("from_dirt");

    public ErodedDirtBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(STAGE, 0).with(FROM_DIRT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, FROM_DIRT);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        if (!BlockThresholds.shouldRevertUnder(world, pos)) return;
        BlockState revertTo = state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)
                ? Blocks.COARSE_DIRT.getDefaultState()
                : Blocks.DIRT.getDefaultState();
        world.setBlockState(pos, revertTo, Block.NOTIFY_ALL);
        ErosionMapManager.get(world).removeEntry(pos);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        ErosionMapManager manager = ErosionMapManager.get(world);
        if (BlockThresholds.shouldRevertUnder(world, pos)) {
            BlockState revertTo = state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)
                    ? Blocks.COARSE_DIRT.getDefaultState()
                    : Blocks.DIRT.getDefaultState();
            world.setBlockState(pos, revertTo, Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }
        if (!TRMTConfig.get().deErosion.dirtEnabled) return;
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        long currentTime = world.getTime();
        long timeout = BlockThresholds.getDirtDeErosionTimeout(state.getBlock());
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;
        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        Direction facing = state.get(FACING);
        Block block = state.getBlock();

        if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            // De-erode to the most eroded dirt stage.
            world.setBlockState(pos, TRMTBlocks.ERODED_DIRT.getDefaultState().with(FACING, facing).with(STAGE, 3).with(FROM_DIRT, state.get(FROM_DIRT)), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, newCooldownTime);
        } else if (block == TRMTBlocks.ERODED_DIRT) {
            int stage = state.get(STAGE);
            if (state.get(FROM_DIRT) && stage <= 1) {
                // Worn from plain dirt: fully recovered, back to vanilla dirt (never grass).
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
            } else if (stage > 0) {
                // Step down one visual stage.
                world.setBlockState(pos, state.with(STAGE, stage - 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, newCooldownTime);
            } else {
                // Stage 0 → revert to eroded grass block at its most-eroded stage, preserving rotation.
                world.setBlockState(pos,
                        TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                                .with(ErodedGrassBlock.FACING, facing)
                                .with(ErodedGrassBlock.STAGE, 4),
                        Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, newCooldownTime);
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
