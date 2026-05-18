package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public class SugarCaneBlockMixin {

	@Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
	private void trmt$allowOnErodedSandStage0(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos,
	                                          CallbackInfoReturnable<Boolean> cir) {
		BlockState below = level.getBlockState(pos.below());
		if (below.is(TRMTBlocks.ERODED_SAND.get()) && below.getValue(ErodedSandBlock.STAGE) == 0) {
			cir.setReturnValue(true);
		}
	}
}
