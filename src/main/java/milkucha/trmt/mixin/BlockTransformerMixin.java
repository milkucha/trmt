package milkucha.trmt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes the erosion entry when a block transformer (hoe tilling, shovel flattening; see
 * {@code TRMTBlockTransforms}) turns eroded grass / eroded dirt into another block.
 * Wraps the single {@code Level.setBlock} call in {@code BlockTransformer.transformBlock}.
 */
@Mixin(BlockTransformer.class)
public class BlockTransformerMixin {

    @WrapOperation(method = "transformBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean trmt$removeErosionEntry(Level level, BlockPos pos, BlockState newState, int flags,
                                            Operation<Boolean> original) {
        BlockState oldState = level.getBlockState(pos);
        boolean result = original.call(level, pos, newState, flags);
        if (!level.isClientSide()
                && (oldState.is(TRMTBlocks.ERODED_GRASS_BLOCK) || oldState.is(TRMTBlocks.ERODED_DIRT))) {
            ErosionMapManager.get(level).removeEntry(pos);
        }
        return result;
    }
}
