package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets hoes till trmt:eroded_grass_block and trmt:eroded_dirt into farmland,
 * under the same conditions as vanilla: the clicked face is not the bottom face and
 * the block above is air. Otherwise the use passes.
 */
@Mixin(HoeItem.class)
public class HoeItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void trmt$tillErodedBlocks(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK) && !state.isOf(TRMTBlocks.ERODED_DIRT)) {
            return;
        }

        // Hoes can only till if the block above is air — same rule as vanilla.
        if (!world.getBlockState(pos.up()).isAir()) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        // Don't till the bottom face (matches vanilla hoe behaviour).
        if (context.getSide() == Direction.DOWN) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        PlayerEntity player = context.getPlayer();
        world.playSound(player, pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (!world.isClient) {
            world.setBlockState(pos, Blocks.FARMLAND.getDefaultState(),
                    Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
            ErosionMapManager.get(world).removeEntry(pos);
            if (player != null) {
                context.getStack().damage(1, player,
                        context.getHand() == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        cir.setReturnValue(ActionResult.success(world.isClient));
    }
}
