package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public class SugarCaneBlockMixin {

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void trmt$allowOnErodedSandStage0(BlockState state, WorldView world, BlockPos pos,
                                               CallbackInfoReturnable<Boolean> cir) {
        BlockState below = world.getBlockState(pos.down());
        if (below.isOf(TRMTBlocks.ERODED_SAND) && below.get(ErodedSandBlock.STAGE) == 0) {
            cir.setReturnValue(true);
        }
    }
}
