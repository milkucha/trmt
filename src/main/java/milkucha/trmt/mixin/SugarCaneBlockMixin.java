package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
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
        if (below.is(TRMTBlocks.ERODED_GRASS_BLOCK) || below.is(TRMTBlocks.ERODED_DIRT)
                || below.is(TRMTBlocks.ERODED_COARSE_DIRT)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (world.getFluidState(pos.relative(dir)).is(Fluids.WATER)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    // canSurvive calls BlockState.is(TagKey) twice: BlockTags.DIRT (ordinal 0), then BlockTags.SAND (ordinal 1).
    @Redirect(method = "canSurvive", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSandDirtCheck(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }

    @Redirect(method = "canSurvive", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean trmt$erodedSandCountsAsSandSandCheck(BlockState state, TagKey<Block> tag) {
        return BlockThresholds.isInSandAware(state, tag);
    }
}
