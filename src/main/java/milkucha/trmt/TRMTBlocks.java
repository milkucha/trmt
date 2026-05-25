package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TRMTBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, "trmt");

    public static final RegistryObject<ErodedDirtBlock> ERODED_DIRT = BLOCKS.register("eroded_dirt",
            () -> new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks()));

    public static final RegistryObject<ErodedDirtBlock> ERODED_COARSE_DIRT = BLOCKS.register("eroded_coarse_dirt",
            () -> new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT).randomTicks()));

    public static final RegistryObject<ErodedGrassBlock> ERODED_GRASS_BLOCK = BLOCKS.register("eroded_grass_block",
            () -> new ErodedGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)
                    .mapColor(MapColor.DIRT).randomTicks()));

    public static final RegistryObject<ErodedSandBlock> ERODED_SAND = BLOCKS.register("eroded_sand",
            () -> new ErodedSandBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
                    .mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion().randomTicks()));

    private TRMTBlocks() {}
}
