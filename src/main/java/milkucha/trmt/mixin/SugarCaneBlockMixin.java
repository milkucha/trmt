package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public class SugarCaneBlockMixin {

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void trmt$allowOnErodedBlocks(BlockState state, WorldView world, BlockPos pos,
                                           CallbackInfoReturnable<Boolean> cir) {
        BlockState below = world.getBlockState(pos.down());

        // Eroded sand (any stage) is handled by the isIn redirects below: it counts as sand, so the vanilla water check applies.
        // Eroded grass/dirt are full-height blocks — allow placement when adjacent water is present (vanilla rule).
        if (below.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)
                || below.isOf(TRMTBlocks.ERODED_DIRT)
                || below.isOf(TRMTBlocks.ERODED_COARSE_DIRT)) {
            BlockPos floorPos = pos.down();
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (world.getFluidState(floorPos.offset(dir)).isOf(Fluids.WATER)
                        || world.getBlockState(floorPos.offset(dir)).isOf(Blocks.FROSTED_ICE)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(false);
        }
    }

    // canPlaceAt calls BlockState.isIn twice: BlockTags.DIRT (ordinal 0), then BlockTags.SAND (ordinal 1).
    @Redirect(method = "canPlaceAt", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSandDirtCheck(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }

    @Redirect(method = "canPlaceAt", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSandSandCheck(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
