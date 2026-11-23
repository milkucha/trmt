package milkucha.trmt;

import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

import java.util.List;

/**
 * Hoe tilling and shovel flattening of eroded grass / eroded dirt.
 *
 * <p>26.3 replaced {@code HoeItem}/{@code ShovelItem} with data-driven block transformers
 * ({@code minecraft:hoe} / {@code minecraft:shovel}); Fabric API appends these entries to them.
 * Both use vanilla's own hoe and shovel conditions (as in {@code data/minecraft/block_transformer/
 * hoe.json} / {@code shovel.json}): the block above is in {@code #minecraft:air} and the clicked face
 * is not the bottom face; otherwise the use passes. {@code updateFromNeighbors}
 * is off because the old mixins set the block without a shape update. The erosion entry is
 * removed by {@code BlockTransformerMixin}.
 */
public final class TRMTBlockTransforms {

    private TRMTBlockTransforms() {}

    public static void register() {
        BlockPredicate erodedGrassOrDirt = BlockPredicate.matchesBlocks(TRMTBlocks.ERODED_GRASS_BLOCK, TRMTBlocks.ERODED_DIRT);

        BlockTransformerHelper.registerHoe(BlockTransformer.BlockTransformData.builder(
                        BlockPredicate.allOf(erodedGrassOrDirt, BlockPredicate.matchesTag(Direction.UP.getUnitVec3i(), BlockTags.AIR)),
                        Blocks.FARMLAND)
                .sound(SoundEvents.HOE_TILL)
                .disallowedFaces(List.of(Direction.DOWN))
                .updateFromNeighbors(false)
                .build());

        BlockTransformerHelper.registerShovel(BlockTransformer.BlockTransformData.builder(
                        BlockPredicate.allOf(erodedGrassOrDirt, BlockPredicate.matchesTag(Direction.UP.getUnitVec3i(), BlockTags.AIR)),
                        Blocks.DIRT_PATH)
                .sound(SoundEvents.SHOVEL_FLATTEN)
                .disallowedFaces(List.of(Direction.DOWN))
                .updateFromNeighbors(false)
                .build());
    }
}
