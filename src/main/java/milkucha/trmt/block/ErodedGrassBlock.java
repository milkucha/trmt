package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;


/**
 * Grass block produced by foot-traffic erosion.
 * Stores a FACING direction (established when grass first erodes) for UV rotation,
 * and a STAGE (0–4) matching eroded_grass_block_s0 through eroded_grass_block_s4 models.
 * SNOWY mirrors vanilla snowy grass ({@code SnowyDirtBlock}): true while the block above is in {@code #minecraft:snow}
 * (snow layers, snow block, powder snow); snowy variants use the vanilla snowy grass side.
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

    /** Same property instance as vanilla grass ({@code SnowyDirtBlock.SNOWY}). */
    public static final BooleanProperty SNOWY = BlockStateProperties.SNOWY;

    public ErodedGrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(STAGE, 0).setValue(SNOWY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, SNOWY);
    }

    /** Vanilla snowy grass's {@code isSnowySetting}: the block above counts as snow cover. */
    public static boolean isSnow(BlockState state) {
        return state.is(BlockTags.SNOW);
    }

    /** Whether a grass block (eroded or vanilla) at {@code pos} should be snowy now. */
    public static boolean snowyAt(BlockGetter world, BlockPos pos) {
        return isSnow(world.getBlockState(pos.above()));
    }

    /** Vanilla grass_block for {@code pos}, with {@code snowy} set from the block above (eroded grass recovering / reverting). */
    public static BlockState grassStateFor(BlockGetter world, BlockPos pos) {
        return Blocks.GRASS_BLOCK.defaultBlockState().setValue(SNOWY, snowyAt(world, pos));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(SNOWY, snowyAt(ctx.getLevel(), ctx.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (direction == Direction.UP) {
            return state.setValue(SNOWY, isSnow(neighborState));
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
                                @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, world, pos, block, orientation, movedByPiston);
        if (!world.isClientSide() && BlockThresholds.shouldRevertUnder(world, pos)) {
            world.setBlock(pos, grassStateFor(world, pos), Block.UPDATE_ALL);
            ErosionMapManager.get(world).removeEntry(pos);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (BlockThresholds.shouldRevertUnder(world, pos)) {
            world.setBlock(pos, grassStateFor(world, pos), Block.UPDATE_ALL);
            ErosionMapManager.get(world).removeEntry(pos);
            return;
        }
        if (!milkucha.trmt.TRMTConfig.get().deErosion.grassEnabled) return;
        int blockStage = state.getValue(STAGE);
        // Permanent stage: no natural de-erosion (and so no grass sprout), with or without an entry.
        if (BlockThresholds.isPermanentGrass(blockStage)) return;

        ErosionMapManager manager = ErosionMapManager.get(world);
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        long currentTime = world.getGameTime();
        // Map block STAGE 0–4 to old grass stages 1–5 for the per-stage timeout config.
        long timeout = BlockThresholds.getGrassDeErosionTimeout(blockStage + 1);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;
        long newCooldownTime = (entry != null) ? entry.getLastTouchedGameTime() + timeout : currentTime;

        if (blockStage > 0) {
            world.setBlock(pos, state.setValue(STAGE, blockStage - 1), Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), newCooldownTime);
            if (random.nextFloat() < 0.05f && world.getBlockState(pos.above()).isAir()) {
                world.setBlock(pos.above(), Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else {
            // Stage 0 → revert to vanilla grass_block (snowy if still under snow).
            world.setBlock(pos, grassStateFor(world, pos), Block.UPDATE_ALL);
            manager.removeEntry(pos);
        }
    }
}
