package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A falling block resting on sunken eroded sand (collision lower than a full block) has a block position
 * (floor of its Y) inside the sand cell, which is not replaceable, so vanilla drops it as an item. Treat the
 * cell above the sand as the landing cell instead; placing the block there reverts the sand to vanilla sand
 * (see {@code ErodedSandBlock#getStateForNeighborUpdate}). Every other surface is unchanged.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    @Redirect(
            method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/FallingBlockEntity;getBlockPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos trmt$landAboveSunkenSand(FallingBlockEntity self) {
        BlockPos pos = self.getBlockPos();
        BlockState state = self.getWorld().getBlockState(pos);
        if (state.isOf(TRMTBlocks.ERODED_SAND) && state.get(ErodedSandBlock.STAGE) > 0) {
            return pos.up();
        }
        return pos;
    }
}
