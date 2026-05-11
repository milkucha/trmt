package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Registry for all custom TRMT blocks.
 */
public final class TRMTBlocks {

    private TRMTBlocks() {}

    public static final Block ERODED_DIRT = registerBlock("eroded_dirt", new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.DIRT)
            .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TRMT.MOD_ID, "eroded_dirt")))
            .ticksRandomly()));

    public static final Block ERODED_COARSE_DIRT = registerBlock("eroded_coarse_dirt", new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.COARSE_DIRT)
            .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TRMT.MOD_ID, "eroded_coarse_dirt")))
            .ticksRandomly()));

    public static final Block ERODED_GRASS_BLOCK = registerBlock("eroded_grass_block", new ErodedGrassBlock(AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK)
            .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TRMT.MOD_ID, "eroded_grass_block")))
            .ticksRandomly()));

    public static final Block ERODED_SAND = registerBlock("eroded_sand", new ErodedSandBlock(AbstractBlock.Settings.copy(Blocks.SAND)
            .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TRMT.MOD_ID, "eroded_sand")))
            .ticksRandomly()));

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(TRMT.MOD_ID, name);
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void register() {
        TRMT.LOGGER.info("[TRMT] Registering blocks...");
    }
}
