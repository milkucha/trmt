package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public class SugarCaneBlockMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void trmt$allowOnErodedBlocks(BlockState state, LevelReader world, BlockPos pos,
                                           CallbackInfoReturnable<Boolean> cir) {
        BlockState below = world.getBlockState(pos.below());

        // Eroded sand (any stage) is handled by the is(TagKey) redirects below: it counts as sand, so the vanilla water check applies.
        // Eroded grass/dirt are full-height blocks — allow placement when adjacent water is present (vanilla rule).
        if (below.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())
                || below.is(TRMTBlocks.ERODED_DIRT.get())
                || below.is(TRMTBlocks.ERODED_COARSE_DIRT.get())) {
            BlockPos floorPos = pos.below();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (world.getFluidState(floorPos.relative(dir)).is(Fluids.WATER)
                        || world.getBlockState(floorPos.relative(dir)).is(Blocks.FROSTED_ICE)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(false);
        }
    }

    // 26.1: canSurvive calls BlockState.is(TagKey) twice: BlockTags.SUPPORTS_SUGAR_CANE (ordinal 0, on the block below; contains #sand)
    // and BlockTags.SUPPORTS_SUGAR_CANE_ADJACENTLY (ordinal 1, on the neighbours of the base; frosted ice) - only ordinal 0 is redirected.
    @Redirect(method = "canSurvive", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSand(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
