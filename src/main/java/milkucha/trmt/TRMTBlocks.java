package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TRMTBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TRMT.MOD_ID);

    public static final DeferredBlock<Block> ERODED_DIRT = BLOCKS.register("eroded_dirt",
        () -> new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks()));

    public static final DeferredBlock<Block> ERODED_COARSE_DIRT = BLOCKS.register("eroded_coarse_dirt",
        () -> new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT).randomTicks()));

    public static final DeferredBlock<Block> ERODED_GRASS_BLOCK = BLOCKS.register("eroded_grass_block",
        () -> new ErodedGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).mapColor(MapColor.DIRT).randomTicks()));

    public static final DeferredBlock<Block> ERODED_SAND = BLOCKS.register("eroded_sand",
        () -> new ErodedSandBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion().randomTicks()));

    private TRMTBlocks() {}
}
