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

/**
 * Grass block produced by foot-traffic erosion.
 * Stores a FACING direction (established when grass first erodes) for UV rotation,
 * and a STAGE (0-4) matching eroded_grass_block_s0 through eroded_grass_block_s4 models.
 * Never placed by players or generated naturally - only set by the erosion system.
 */
public class ErodedGrassBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage (0-4).
     * 0 = least eroded (grass_block_eroded_0 model), 4 = most eroded (grass_block_eroded_4 model).
     * Maps to old grass erosion stages 1-5: stage+1 is used for de-erosion timeout lookup.
     */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

    public ErodedGrassBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.SOUTH).setValue(STAGE, 0));
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
        // If the block above is opaque, revert to vanilla grass and clear tracking data.
        if (!level.getBlockState(pos.above()).canOcclude()) return;
        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        ErosionMapManager.getInstance().removeEntry(pos);
    }

    /** Handles random ticking for erosion simulation (De-erosion). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Check if the block above is opaque before proceeding with erosion logic.
        if (!level.getBlockState(pos.above()).canOcclude()) return;

        if (!TRMTConfig.get().deErosion.grassEnabled) return;
        ChunkErosionMap chunkMap = ErosionMapManager.getInstance().getChunkMap(ChunkPos.containing(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        int blockStage = state.getValue(STAGE);
        long currentTime = level.getGameTime();
        // Map block STAGE 0-4 to old grass stages 1-5 for the per-stage timeout config.
        long timeout = BlockThresholds.getGrassDeErosionTimeout(blockStage + 1);
        if (BlockThresholds.isIsolated(level, pos, ErosionMapManager.getInstance())) {
            timeout /= 2; // Halve timeout if isolated
        }
        // Check cooldown period: If current time is before the required time, do nothing.
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        if (blockStage > 0) {
            // De-erode: Step down one visual stage, preserving rotation.
            level.setBlock(pos, state.setValue(STAGE, Math.max(0, blockStage - 1)), Block.UPDATE_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
            // Write the new cooldown time for the next check.
            ErosionMapManager.getInstance().writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, newCooldownTime);
        } else {
            // Stage 0 -> revert to vanilla grass block.
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
        }
    }
}
