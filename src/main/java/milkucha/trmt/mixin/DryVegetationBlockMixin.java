package milkucha.trmt.mixin;

import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dead bush (and short/tall dry grass, which inherit this method) check BlockTags.DRY_VEGETATION_MAY_PLACE_ON in
 * mayPlaceOn; eroded sand (any stage) counts as vanilla sand there. 1.21.11 has no DeadBushBlock class any more.
 */
@Mixin(DryVegetationBlock.class)
public class DryVegetationBlockMixin {

    @Redirect(method = "mayPlaceOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSand(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
