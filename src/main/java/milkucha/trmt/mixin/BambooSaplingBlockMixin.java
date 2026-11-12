package milkucha.trmt.mixin;

import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bamboo sapling canSurvive checks BlockTags.BAMBOO_PLANTABLE_ON; eroded sand (any stage) counts as vanilla sand there. */
@Mixin(BambooSaplingBlock.class)
public class BambooSaplingBlockMixin {

    @Redirect(method = "canSurvive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSand(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
