package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All mod blocks registered via {@link DeferredRegister}.
 */
public final class TRMTBlocks {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TRMT.MOD_ID);

	public static final DeferredBlock<ErodedDirtBlock> ERODED_DIRT = BLOCKS.registerBlock("eroded_dirt",
		ErodedDirtBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks());

	public static final DeferredBlock<ErodedDirtBlock> ERODED_COARSE_DIRT = BLOCKS.registerBlock("eroded_coarse_dirt",
		ErodedDirtBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT).randomTicks());

	public static final DeferredBlock<ErodedGrassBlock> ERODED_GRASS_BLOCK = BLOCKS.registerBlock("eroded_grass_block",
		ErodedGrassBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).mapColor(MapColor.DIRT).randomTicks());

	public static final DeferredBlock<ErodedSandBlock> ERODED_SAND = BLOCKS.registerBlock("eroded_sand",
		ErodedSandBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).mapColor(MapColor.SAND).randomTicks().noOcclusion());

	private TRMTBlocks() {}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
	}
}
