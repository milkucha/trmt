package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registry for all custom TRMT blocks.
 */
public final class TRMTBlocks {

    /**
     * Dirt produced by the final grass erosion stage. One pixel shorter than a normal block,
     * stores the rotation of the preceding grass stage. Never obtainable as an item.
     */
    public static final Block ERODED_DIRT = Registry.register(
            Registries.BLOCK,
            new Identifier("trmt", "eroded_dirt"),
            new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.DIRT).nonOpaque().ticksRandomly())
    );

    /**
     * Coarse dirt produced by erosion. One pixel shorter than a normal block,
     * visually identical to vanilla coarse dirt. Never obtainable as an item.
     */
    public static final Block ERODED_COARSE_DIRT = Registry.register(
            Registries.BLOCK,
            new Identifier("trmt", "eroded_coarse_dirt"),
            new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.COARSE_DIRT).nonOpaque().ticksRandomly())
    );

    /**
     * Rooted dirt produced by erosion. One pixel shorter than a normal block,
     * visually identical to vanilla rooted dirt. The terminal state of the erosion chain.
     * Never obtainable as an item.
     */
    public static final Block ERODED_ROOTED_DIRT = Registry.register(
            Registries.BLOCK,
            new Identifier("trmt", "eroded_rooted_dirt"),
            new ErodedDirtBlock(AbstractBlock.Settings.copy(Blocks.ROOTED_DIRT).nonOpaque().ticksRandomly())
    );

    /**
     * Sand produced by foot-traffic erosion. Full block, sandstone_bottom top face,
     * sand texture on all other faces. Never obtainable as an item.
     */
    public static final Block ERODED_SAND = Registry.register(
            Registries.BLOCK,
            new Identifier("trmt", "eroded_sand"),
            new ErodedSandBlock(AbstractBlock.Settings.copy(Blocks.SAND).nonOpaque())
    );

    private TRMTBlocks() {}

    /** Called from TRMT.onInitialize() to force static initialisation. */
    public static void register() {}
}
