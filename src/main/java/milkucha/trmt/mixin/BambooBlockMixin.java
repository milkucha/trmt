package milkucha.trmt.mixin;

import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bamboo's BAMBOO_PLANTABLE_ON checks (getPlacementState and canPlaceAt): eroded sand (any stage) counts as vanilla sand. */
@Mixin(BambooBlock.class)
public class BambooBlockMixin {

    @Redirect(method = "canPlaceAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSandPlaceAt(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }

    @Redirect(method = "getPlacementState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSandPlacement(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
