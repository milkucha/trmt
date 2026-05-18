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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ErodedSandBlock extends Block {

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

	private static final VoxelShape[] COLLISION_SHAPES = {
		Shapes.block(),
		Shapes.box(0, 0, 0, 1, 10 / 16.0, 1),
		Shapes.box(0, 0, 0, 1, 10 / 16.0, 1),
		Shapes.box(0, 0, 0, 1, 10 / 16.0, 1),
		Shapes.box(0, 0, 0, 1, 10 / 16.0, 1),
	};

	private static final VoxelShape[] OUTLINE_SHAPES = {
		Shapes.block(),
		Shapes.box(0, 0, 0, 1, 14 / 16.0, 1),
		Shapes.box(0, 0, 0, 1, 14 / 16.0, 1),
		Shapes.box(0, 0, 0, 1, 12 / 16.0, 1),
		Shapes.box(0, 0, 0, 1, 10 / 16.0, 1),
	};

	public ErodedSandBlock(Properties settings) {
		super(settings);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.SOUTH).setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STAGE);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!TRMTConfig.get().deErosion.sandEnabled) return;
		ErosionMapManager manager = ErosionMapManager.getInstance();
		ChunkErosionMap chunkMap = manager.getChunkMap(new net.minecraft.world.level.ChunkPos(pos));
		ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

		int stage = state.getValue(STAGE);
		long currentTime = level.getGameTime();
		long timeout = BlockThresholds.getSandDeErosionTimeout(stage);
		if (BlockThresholds.isIsolated(level, pos, manager)) timeout /= 2;
		if (entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) return;

		if (stage > 0) {
			level.setBlock(pos, state.setValue(STAGE, stage - 1), TRMTFlags.BLOCK_UPDATE);
			manager.removeEntry(pos);
			manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), currentTime);
		} else {
			level.setBlock(pos, Blocks.SAND.defaultBlockState(), TRMTFlags.BLOCK_UPDATE);
			manager.removeEntry(pos);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE_SHAPES[state.getValue(STAGE)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLLISION_SHAPES[state.getValue(STAGE)];
	}

	@SuppressWarnings("deprecation")
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}
}
