package milkucha.trmt;

import milkucha.trmt.block.ErodedCoarseDirtBlock;
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
     * Coarse dirt produced by erosion. One pixel shorter than a normal block,
     * visually identical to vanilla coarse dirt. Never obtainable as an item.
     */
    public static final Block ERODED_COARSE_DIRT = Registry.register(
            Registries.BLOCK,
            new Identifier("trmt", "eroded_coarse_dirt"),
            new ErodedCoarseDirtBlock(AbstractBlock.Settings.copy(Blocks.COARSE_DIRT).nonOpaque())
    );

    private TRMTBlocks() {}

    /** Called from TRMT.onInitialize() to force static initialisation. */
    public static void register() {}
}
