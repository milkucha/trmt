package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
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

        // Eroded sand stage 0 is full-height — allow placement without water check (same as vanilla sand).
        if (below.is(TRMTBlocks.ERODED_SAND.get()) && below.getValue(ErodedSandBlock.STAGE) == 0) {
            cir.setReturnValue(true);
            return;
        }

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
}
