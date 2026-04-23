package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ShovelItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends shovel path-flattening to cover trmt:eroded_coarse_dirt,
 * matching the behaviour of its vanilla counterpart (coarse_dirt).
 */
@Mixin(ShovelItem.class)
public class ShovelItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void trmt$flattenErodedBlocks(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)) {
            return;
        }

        PlayerEntity player = context.getPlayer();
        world.playSound(player, pos, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (!world.isClient) {
            world.setBlockState(pos, Blocks.DIRT_PATH.getDefaultState(),
                    Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
            if (player != null) {
                context.getStack().damage(1, player, p -> p.sendToolBreakStatus(context.getHand()));
            }
        }
        cir.setReturnValue(ActionResult.success(world.isClient));
    }
}
