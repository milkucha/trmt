package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionRecovery;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public class BoneMealItemMixin {

    /**
     * Bonemeal on eroded grass / dirt / coarse dirt recovers it (and its cross neighbours, see
     * {@link ErosionRecovery}) instead of running vanilla. One bonemeal per use.
     */
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void trmt$onBoneMealUse(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;

        BlockPos pos = context.getBlockPos();
        if (!ErosionRecovery.Tool.BONEMEAL.acts(world.getBlockState(pos))) return;

        ErosionRecovery.recoverCross(serverWorld, pos, context.getHorizontalPlayerFacing(), ErosionRecovery.Tool.BONEMEAL);

        PlayerEntity player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getStack().decrement(1);
        }
        cir.setReturnValue(ActionResult.SUCCESS);
    }
}
