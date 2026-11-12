package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public class SugarCaneBlockMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void trmt$allowOnErodedBlocks(BlockState state, LevelReader world, BlockPos pos,
                                           CallbackInfoReturnable<Boolean> cir) {
        BlockState below = world.getBlockState(pos.below());

        // Eroded grass/dirt are full-height blocks — allow placement when adjacent water is present (vanilla rule).
        // Eroded sand (any stage) is not handled here: ErodedSandBlock.canSustainPlant answers like vanilla sand, so the vanilla water check applies.
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
}
