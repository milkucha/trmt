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
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Grass block produced by foot-traffic erosion.
 * Stores a FACING direction (established when grass first erodes) for UV rotation,
 * and a STAGE (0–4) matching eroded_grass_block_s0 through eroded_grass_block_s4 models.
 * SNOWY mirrors vanilla {@code SnowyBlock}: true while the block above is in {@code #minecraft:snow}
 * (snow layers, snow block, powder snow); snowy variants use the vanilla snowy grass side.
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

    /** Same property instance as vanilla grass ({@code SnowyBlock.SNOWY}). */
    public static final BooleanProperty SNOWY = Properties.SNOWY;

    public ErodedGrassBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(STAGE, 0).with(SNOWY, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, SNOWY);
    }

    /** Vanilla {@code SnowyBlock.isSnow}: the block above counts as snow cover. */
    public static boolean isSnow(BlockState state) {
        return state.isIn(BlockTags.SNOW);
    }

    /** Whether a grass block (eroded or vanilla) at {@code pos} should be snowy now. */
    public static boolean snowyAt(BlockView world, BlockPos pos) {
        return isSnow(world.getBlockState(pos.up()));
    }

    /** Vanilla grass_block for {@code pos}, with {@code snowy} set from the block above (eroded grass recovering / reverting). */
    public static BlockState grassStateFor(BlockView world, BlockPos pos) {
        return Blocks.GRASS_BLOCK.getDefaultState().with(SNOWY, snowyAt(world, pos));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(SNOWY, snowyAt(ctx.getWorld(), ctx.getBlockPos()));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.with(SNOWY, isSnow(neighborState));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        if (!BlockThresholds.shouldRevertUnder(world, pos)) return;
        world.setBlockState(pos, grassStateFor(world, pos), Block.NOTIFY_ALL);
        ErosionMapManager.get(world).removeEntry(pos);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        ErosionMapManager manager = ErosionMapManager.get(world);
        if (BlockThresholds.shouldRevertUnder(world, pos)) {
            world.setBlockState(pos, grassStateFor(world, pos), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }
        if (!TRMTConfig.get().deErosion.grassEnabled) return;
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
                world.setBlockState(pos.up(), Blocks.GRASS.getDefaultState(), Block.NOTIFY_ALL);
            }
        } else {
            // Stage 0 → revert to vanilla grass_block (snowy if still under snow).
            world.setBlockState(pos, grassStateFor(world, pos), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
        }
    }
}
