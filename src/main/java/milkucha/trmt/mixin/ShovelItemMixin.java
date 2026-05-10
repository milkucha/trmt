package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShovelItem;
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
 * Extends shovel path-flattening to cover trmt:eroded_coarse_dirt,
 * matching the behaviour of its vanilla counterpart (coarse_dirt).
 */
@Mixin(ShovelItem.class)
public class ShovelItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void trmt$flattenErodedBlocks(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = context.getClickedPos();
        Level world = context.getLevel();
        BlockState state = world.getBlockState(pos);

        if (!state.is(TRMTBlocks.ERODED_GRASS_BLOCK) && !state.is(TRMTBlocks.ERODED_DIRT)) {
            return;
        }

        Player player = context.getPlayer();
        world.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!world.isClientSide()) {
            world.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(),
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
