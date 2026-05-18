package milkucha.trmt.block;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTFlags;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dirt block produced by foot-traffic erosion.
 */
public class ErodedDirtBlock extends Block {

	private static final VoxelShape SHAPE = Shapes.block();

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

	public ErodedDirtBlock(Properties settings) {
		super(settings);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.SOUTH).setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STAGE);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!TRMTConfig.get().deErosion.dirtEnabled) return;
		ErosionMapManager manager = ErosionMapManager.getInstance();
		ChunkErosionMap chunkMap = manager.getChunkMap(new net.minecraft.world.level.ChunkPos(pos));
		ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

		long currentTime = level.getGameTime();
		long timeout = BlockThresholds.getDirtDeErosionTimeout(state.getBlock());
		if (BlockThresholds.isIsolated(level, pos, manager)) timeout /= 2;
		if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

		Direction facing = state.getValue(FACING);
		Block block = state.getBlock();

		if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
			level.setBlock(pos, TRMTBlocks.ERODED_DIRT.get().defaultBlockState().setValue(FACING, facing).setValue(STAGE, 3), TRMTFlags.BLOCK_UPDATE);
			manager.removeEntry(pos);
			manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), currentTime);
		} else if (block == TRMTBlocks.ERODED_DIRT.get()) {
			int stage = state.getValue(STAGE);
			if (stage > 0) {
				level.setBlock(pos, state.setValue(STAGE, stage - 1), TRMTFlags.BLOCK_UPDATE);
				manager.removeEntry(pos);
				manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), currentTime);
			} else {
				level.setBlock(pos,
					TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
						.setValue(ErodedGrassBlock.FACING, facing)
						.setValue(ErodedGrassBlock.STAGE, 4),
					TRMTFlags.BLOCK_UPDATE);
				manager.removeEntry(pos);
				manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), currentTime);
			}
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}
}
