package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class TRMTBlocks {

    public static final Block ERODED_DIRT = Registry.register(
            Registries.BLOCK,
            Identifier.of("trmt", "eroded_dirt"),
            new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.DIRT).ticksRandomly())
    );

    public static final Block ERODED_COARSE_DIRT = Registry.register(
            Registries.BLOCK,
            Identifier.of("trmt", "eroded_coarse_dirt"),
            new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.COARSE_DIRT).ticksRandomly())
    );

    public static final Block ERODED_GRASS_BLOCK = Registry.register(
            Registries.BLOCK,
            Identifier.of("trmt", "eroded_grass_block"),
            new ErodedGrassBlock(AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK).mapColor(MapColor.DIRT_BROWN).ticksRandomly())
    );

    public static final Block ERODED_SAND = Registry.register(
            Registries.BLOCK,
            Identifier.of("trmt", "eroded_sand"),
            new ErodedSandBlock(AbstractBlock.Settings.copy(Blocks.SAND).mapColor(MapColor.TERRACOTTA_YELLOW).nonOpaque().ticksRandomly())
    );

    private TRMTBlocks() {}

    public static void register() {}
}
