package milkucha.trmt.mixin;

import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Cactus canSurvive checks BlockTags.SAND; eroded sand (any stage) counts as vanilla sand there. */
@Mixin(CactusBlock.class)
public class CactusBlockMixin {

    @Redirect(method = "canSurvive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSand(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
