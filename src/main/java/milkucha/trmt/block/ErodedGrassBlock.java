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
 * and a STAGE (0–4) matching eroded_grass_block_s0 through eroded_grass_block_s4 models.
 * Never placed by players or generated naturally — only set by the erosion system.
 */
public class ErodedGrassBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage (0–4).
     * 0 = least eroded (grass_block_eroded_0 model), 4 = most eroded (grass_block_eroded_4 model).
     * Maps to old grass erosion stages 1–5: stage+1 is used for de-erosion timeout lookup.
     */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

    public ErodedGrassBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.SOUTH).setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!TRMTConfig.get().deErosion.grassEnabled) return;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap chunkMap = manager.getChunkMap(ChunkPos.containing(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        int blockStage = state.getValue(STAGE);
        long currentTime = world.getGameTime();
        // Map block STAGE 0–4 to old grass stages 1–5 for the per-stage timeout config.
        long timeout = BlockThresholds.getGrassDeErosionTimeout(blockStage + 1);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        if (blockStage > 0) {
            world.setBlock(pos, state.setValue(STAGE, blockStage - 1), Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
        } else {
            // Stage 0 → revert to vanilla grass_block.
            world.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            manager.removeEntry(pos);
        }
    }
}
