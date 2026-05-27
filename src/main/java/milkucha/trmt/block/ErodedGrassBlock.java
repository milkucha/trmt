package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Grass block produced by foot-traffic erosion.
 * Stores a FACING direction (established when grass first erodes) for UV rotation,
 * and a STAGE (0–4) matching eroded_grass_block_s0 through eroded_grass_block_s4 models.
 * Never placed by players or generated naturally — only set by the erosion system.
 */
public class ErodedGrassBlock extends Block {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    /**
     * Visual erosion stage (0–4).
     * 0 = least eroded (grass_block_eroded_0 model), 4 = most eroded (grass_block_eroded_4 model).
     * Maps to old grass erosion stages 1–5: stage+1 is used for de-erosion timeout lookup.
     */
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 4);

    public ErodedGrassBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(STAGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        if (!world.getBlockState(pos.up()).isOpaque()) return;
        world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        ErosionMapManager.getInstance().removeEntry(pos);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (world.getBlockState(pos.up()).isOpaque()) {
            world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            ErosionMapManager.getInstance().removeEntry(pos);
            return;
        }
        if (!milkucha.trmt.TRMTConfig.get().deErosion.grassEnabled) return;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        int blockStage = state.get(STAGE);
        long currentTime = world.getTime();
        // Map block STAGE 0–4 to old grass stages 1–5 for the per-stage timeout config.
        long timeout = BlockThresholds.getGrassDeErosionTimeout(blockStage + 1);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        if (blockStage > 0) {
            world.setBlockState(pos, state.with(STAGE, blockStage - 1), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, newCooldownTime);
            if (random.nextFloat() < 0.05f && world.getBlockState(pos.up()).isAir()) {
                world.setBlockState(pos.up(), Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_ALL);
            }
        } else {
            // Stage 0 → revert to vanilla grass_block.
            world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
        }
    }
}
