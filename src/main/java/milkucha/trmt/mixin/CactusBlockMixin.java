package milkucha.trmt.mixin;

import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CactusBlock;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Cactus' canPlaceAt checks BlockTags.SAND; eroded sand (any stage) counts as vanilla sand there. */
@Mixin(CactusBlock.class)
public class CactusBlockMixin {

    @Redirect(method = "canPlaceAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSand(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
