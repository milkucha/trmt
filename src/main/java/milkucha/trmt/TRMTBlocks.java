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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class TRMTBlocks {

    public static final Block ERODED_DIRT = register("eroded_dirt", ErodedDirtBlock::new,
            AbstractBlock.Settings.copy(Blocks.DIRT).ticksRandomly());

    public static final Block ERODED_COARSE_DIRT = register("eroded_coarse_dirt", ErodedDirtBlock::new,
            AbstractBlock.Settings.copy(Blocks.COARSE_DIRT).ticksRandomly());

    public static final Block ERODED_GRASS_BLOCK = register("eroded_grass_block", ErodedGrassBlock::new,
            AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK).mapColor(MapColor.DIRT_BROWN).ticksRandomly());

    public static final Block ERODED_SAND = register("eroded_sand", ErodedSandBlock::new,
            AbstractBlock.Settings.copy(Blocks.SAND).mapColor(MapColor.TERRACOTTA_YELLOW).nonOpaque().ticksRandomly());

    private TRMTBlocks() {}

    public static void register() {}

    private static <T extends Block> T register(String name, Function<AbstractBlock.Settings, T> factory, AbstractBlock.Settings settings) {
        Identifier id = Identifier.of("trmt", name);
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);
        return Registry.register(Registries.BLOCK, key, factory.apply(settings.registryKey(key)));
    }
}
