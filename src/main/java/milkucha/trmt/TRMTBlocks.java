package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class TRMTBlocks {

    public static final Block ERODED_DIRT = Registry.register(
            BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath("trmt", "eroded_dirt"),
            new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_dirt"))))
    );

    public static final Block ERODED_COARSE_DIRT = Registry.register(
            BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath("trmt", "eroded_coarse_dirt"),
            new ErodedDirtBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT).randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_coarse_dirt"))))
    );

    public static final Block ERODED_GRASS_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath("trmt", "eroded_grass_block"),
            new ErodedGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).mapColor(MapColor.DIRT).randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_grass_block"))))
    );

    public static final Block ERODED_SAND = Registry.register(
            BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath("trmt", "eroded_sand"),
            new ErodedSandBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion().randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("trmt", "eroded_sand"))))
    );

    private TRMTBlocks() {}

    public static void register() {}
}
