package milkucha.trmt.mixin;

import milkucha.trmt.erosion.DeErosionLogic;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public class BoneMealItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void trmt$onBoneMealUse(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;

        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (!trmt$deErodeWithBoneMeal(state, serverWorld, pos)) {
            return;
        }

        PlayerEntity player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getStack().decrement(1);
        }

        serverWorld.syncWorldEvent(2005, pos, 0);
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    @Unique
    private boolean trmt$deErodeWithBoneMeal(BlockState state, ServerWorld world, BlockPos pos) {
        return DeErosionLogic.tryBonemealDeErode(world, ErosionMapManager.getInstance(), pos, state);
    }
}
