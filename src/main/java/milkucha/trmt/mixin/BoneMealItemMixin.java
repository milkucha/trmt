package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public class BoneMealItemMixin {

    /**
     * Bonemeal on eroded grass / dirt / coarse dirt recovers it (and its 3x3 neighbours, see
     * {@link ErosionRecovery}) instead of running vanilla. One bonemeal per use.
     */
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void trmt$onBoneMealUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (!(world instanceof ServerLevel serverWorld)) return;

        BlockPos pos = context.getClickedPos();
        if (!ErosionRecovery.Tool.BONEMEAL.acts(world.getBlockState(pos))) return;

        ErosionRecovery.recoverArea(serverWorld, pos, context.getHorizontalDirection(), ErosionRecovery.Tool.BONEMEAL);

        Player player = context.getPlayer();
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
