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

import java.util.function.Function;

public final class TRMTBlocks {

    public static final Block ERODED_DIRT = register(
            "eroded_dirt",
            properties -> new ErodedDirtBlock(properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks()
    );

    public static final Block ERODED_COARSE_DIRT = register(
            "eroded_coarse_dirt",
            properties -> new ErodedDirtBlock(properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT).randomTicks()
    );

    public static final Block ERODED_GRASS_BLOCK = register(
            "eroded_grass_block",
            properties -> new ErodedGrassBlock(properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).mapColor(MapColor.DIRT).randomTicks()
    );

    public static final Block ERODED_SAND = register(
            "eroded_sand",
            properties -> new ErodedSandBlock(properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion().randomTicks()
    );

    private TRMTBlocks() {}

    public static void register() {}

    private static Block register(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath("trmt", path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }
}
