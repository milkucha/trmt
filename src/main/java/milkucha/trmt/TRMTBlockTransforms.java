package milkucha.trmt;

import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.ReplaceablePredicate;

import java.util.List;

/**
 * Hoe tilling and shovel flattening of eroded grass / eroded dirt.
 *
 * <p>26.3 replaced {@code HoeItem}/{@code ShovelItem} with data-driven block transformers
 * ({@code minecraft:hoe} / {@code minecraft:shovel}); Fabric API appends these entries to them.
 * The conditions are the ones the old {@code HoeItemMixin}/{@code ShovelItemMixin} used, not
 * vanilla's: the hoe needs air or a replaceable block above and not the bottom face; the shovel
 * has no extra condition. {@code updateFromNeighbors} is off because the old mixins set the
 * block without a shape update. The erosion entry is removed by {@code BlockTransformerMixin}.
 */
public final class TRMTBlockTransforms {

    private TRMTBlockTransforms() {}

    public static void register() {
        BlockPredicate erodedGrassOrDirt = BlockPredicate.matchesBlocks(TRMTBlocks.ERODED_GRASS_BLOCK, TRMTBlocks.ERODED_DIRT);

        BlockTransformerHelper.registerHoe(BlockTransformer.BlockTransformData.builder(
                        BlockPredicate.allOf(erodedGrassOrDirt, new ReplaceablePredicate(Direction.UP.getUnitVec3i())),
                        Blocks.FARMLAND)
                .sound(SoundEvents.HOE_TILL)
                .disallowedFaces(List.of(Direction.DOWN))
                .updateFromNeighbors(false)
                .build());

        BlockTransformerHelper.registerShovel(BlockTransformer.BlockTransformData.builder(
                        erodedGrassOrDirt,
                        Blocks.DIRT_PATH)
                .sound(SoundEvents.SHOVEL_FLATTEN)
                .updateFromNeighbors(false)
                .build());
    }
}
