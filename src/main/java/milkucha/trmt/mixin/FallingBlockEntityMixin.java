package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A falling block resting on sunken eroded sand (collision lower than a full block) has a block position
 * (floor of its Y) inside the sand cell, which is not replaceable, so vanilla drops it as an item. Treat the
 * cell above the sand as the landing cell instead; placing the block there reverts the sand to vanilla sand
 * (see {@code ErodedSandBlock#updateShape}). Every other surface is unchanged.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    @Redirect(
            method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos trmt$landAboveSunkenSand(FallingBlockEntity self) {
        BlockPos pos = self.blockPosition();
        BlockState state = self.level().getBlockState(pos);
        if (state.is(TRMTBlocks.ERODED_SAND.get()) && state.getValue(ErodedSandBlock.STAGE) > 0) {
            return pos.above();
        }
        return pos;
    }
}
