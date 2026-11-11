package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public class SugarCaneBlockMixin {

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void trmt$allowOnErodedBlocks(BlockState state, WorldView world, BlockPos pos,
                                           CallbackInfoReturnable<Boolean> cir) {
        BlockState below = world.getBlockState(pos.down());

        // Eroded sand stage 0 is full-height — allow placement without water check (same as vanilla sand).
        if (below.isOf(TRMTBlocks.ERODED_SAND) && below.get(ErodedSandBlock.STAGE) == 0) {
            cir.setReturnValue(true);
            return;
        }

        // Eroded grass/dirt are full-height blocks — allow placement when water is adjacent (vanilla rule).
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
}
