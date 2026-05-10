package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends hoe tilling to cover trmt:eroded_coarse_dirt,
 * converting it to farmland — matching the behaviour of vanilla dirt/grass tilling.
 */
@Mixin(HoeItem.class)
public class HoeItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void trmt$tillErodedBlocks(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = context.getClickedPos();
        Level world = context.getLevel();
        BlockState state = world.getBlockState(pos);

        if (!state.is(TRMTBlocks.ERODED_GRASS_BLOCK) && !state.is(TRMTBlocks.ERODED_DIRT)) {
            return;
        }

        // Hoes can only till if the block above is air or replaceable — same rule as vanilla.
        BlockState above = world.getBlockState(pos.above());
        if (!above.isAir() && !above.canBeReplaced()) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        // Don't till the bottom face (matches vanilla hoe behaviour).
        if (context.getClickedFace() == Direction.DOWN) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        Player player = context.getPlayer();
        world.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!world.isClientSide()) {
            world.setBlock(pos, Blocks.FARMLAND.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
            ErosionMapManager.getInstance().removeEntry(pos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player,
                        context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        cir.setReturnValue(world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }
}
