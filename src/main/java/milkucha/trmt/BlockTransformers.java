package milkucha.trmt;

import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Registers vanilla block-transformer entries (26.3+) so hoes till and shovels flatten our
 * eroded blocks the same way they do vanilla dirt/grass. This replaces the pre-26.3 approach of
 * mixing into {@code HoeItem}/{@code ShovelItem}, which no longer exist as dedicated classes now
 * that tilling/flattening/stripping moved to the data-driven block-transformer system.
 *
 * <p>Erosion-tracking cleanup for the resulting block change is handled by
 * {@code ErodedGrassBlock#affectNeighborsAfterRemoval}/{@code ErodedDirtBlock#affectNeighborsAfterRemoval},
 * since the block-transformer system itself has no post-transform callback hook.
 */
public final class BlockTransformers {

    private BlockTransformers() {}

    public static void register() {
        Block[] tillable = { TRMTBlocks.ERODED_GRASS_BLOCK, TRMTBlocks.ERODED_DIRT };
        BlockTransformerHelper.registerTilling(tillable, Blocks.FARMLAND);
        BlockTransformerHelper.registerFlattening(tillable, Blocks.DIRT_PATH);
    }
}
