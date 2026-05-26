package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TRMTBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("trmt");

    public static final DeferredBlock<ErodedDirtBlock> ERODED_DIRT = BLOCKS.register("eroded_dirt",
            () -> new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_dirt")))));

    public static final DeferredBlock<ErodedDirtBlock> ERODED_COARSE_DIRT = BLOCKS.register("eroded_coarse_dirt",
            () -> new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT).randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_coarse_dirt")))));

    public static final DeferredBlock<ErodedGrassBlock> ERODED_GRASS_BLOCK = BLOCKS.register("eroded_grass_block",
            () -> new ErodedGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)
                    .mapColor(MapColor.DIRT).randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_grass_block")))));

    public static final DeferredBlock<ErodedSandBlock> ERODED_SAND = BLOCKS.register("eroded_sand",
            () -> new ErodedSandBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
                    .mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion().randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_sand")))));

    private TRMTBlocks() {}
}
