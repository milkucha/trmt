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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dirt block produced by foot-traffic erosion.
 * Stores a {@link #FACING} direction so downstream stages preserve the rotation that
 * was established when the preceding grass stage was eroded.
 * Never placed by players or generated naturally - only set by the erosion system.
 */
public class ErodedDirtBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    /** Preserves the rotation of the eroded grass stage that preceded this block. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage for eroded_dirt (0-3).
     * 0 = plain eroded dirt, 1-3 = progressively more eroded using eroded_dirt_0/1/2 textures.
     * Only used by the ERODED_DIRT block; other eroded blocks always stay at stage 0.
     */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

    public ErodedDirtBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(STAGE, 0));
    }

    /**
     * Called when this block is replaced by something else (including via the vanilla
     * hoe/shovel block-transformer system). Clears any stale erosion tracking data.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        ErosionMapManager.getInstance().removeEntry(pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    /**
     * Handles neighbor updates when this block is placed or updated next to another block.
     * 26.3: the 5th parameter changed from a source BlockPos to a nullable Orientation.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock, net.minecraft.world.level.redstone.Orientation orientation, boolean notify) {
        super.neighborChanged(state, level, pos, sourceBlock, orientation, notify);
        if (level.isClientSide()) return;
        // Check if the block above is opaque before reverting state
        if (!level.getBlockState(pos.above()).canOcclude()) return;

        // Determine revert state based on adjacent eroded blocks
        BlockState revertTo = state.is(TRMTBlocks.ERODED_COARSE_DIRT)
                ? Blocks.COARSE_DIRT.defaultBlockState()
                : Blocks.DIRT.defaultBlockState();
        level.setBlock(pos, revertTo, Block.UPDATE_ALL);
        ErosionMapManager.getInstance().removeEntry(pos);
    }

    /** Handles random ticking for erosion simulation (De-erosion). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        // Check if the block above is opaque before proceeding with erosion logic
        if (!level.getBlockState(pos.above()).canOcclude()) return;

        if (!TRMTConfig.get().deErosion.dirtEnabled) return;
        ChunkErosionMap chunkMap = manager.getChunkMap(ChunkPos.containing(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        long currentTime = level.getGameTime();
        // Use BlockThresholds to calculate timeout, assuming it handles the new API structure
        long timeout = BlockThresholds.getDirtDeErosionTimeout(state.getBlock());
        if (BlockThresholds.isIsolated(level, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        // Calculate new cooldown time for the next tick
        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        Direction facing = state.getValue(FACING);
        Block block = state.getBlock();

        if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            // De-erode to the most eroded dirt stage, preserving rotation.
            level.setBlock(pos, TRMTBlocks.ERODED_DIRT.defaultBlockState().setValue(FACING, facing).setValue(STAGE, 3), Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, newCooldownTime);
        } else if (block == TRMTBlocks.ERODED_DIRT) {
            int stage = state.getValue(STAGE);
            if (stage > 0) {
                // Step down one visual stage, preserving rotation.
                level.setBlock(pos, state.setValue(STAGE, Math.max(0, stage - 1)), Block.UPDATE_ALL);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, newCooldownTime);
            } else {
                // Stage 0 -> revert to eroded grass block at its most-eroded stage, preserving rotation.
                level.setBlock(pos,
                        TRMTBlocks.ERODED_GRASS_BLOCK.defaultBlockState()
                                .setValue(ErodedGrassBlock.FACING, facing)
                                .setValue(ErodedGrassBlock.STAGE, 4),
                        Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, newCooldownTime);
            }
        }
    }
}
