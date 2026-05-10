package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public class BoneMealItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void trmt$onBoneMealUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (!(world instanceof ServerLevel serverWorld)) return;

        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long currentTime = serverWorld.getGameTime();

        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            int stage = state.getValue(ErodedGrassBlock.STAGE);
            if (stage > 0) {
                world.setBlock(pos, state.setValue(ErodedGrassBlock.STAGE, stage - 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
            } else {
                // Stage 0 → fully recovered, revert to vanilla grass.
                world.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                manager.removeEntry(pos);
            }
        } else if (block == TRMTBlocks.ERODED_DIRT) {
            int stage = state.getValue(ErodedDirtBlock.STAGE);
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            if (stage > 0) {
                world.setBlock(pos, state.setValue(ErodedDirtBlock.STAGE, stage - 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, currentTime);
            } else {
                // Stage 0 → revert to most-eroded grass stage, preserving facing.
                world.setBlock(pos,
                        TRMTBlocks.ERODED_GRASS_BLOCK.defaultBlockState()
                                .setValue(ErodedGrassBlock.FACING, facing)
                                .setValue(ErodedGrassBlock.STAGE, 4),
                        Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
            }
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            world.setBlock(pos,
                    TRMTBlocks.ERODED_DIRT.defaultBlockState()
                            .setValue(ErodedDirtBlock.FACING, facing)
                            .setValue(ErodedDirtBlock.STAGE, 3),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, currentTime);
        } else {
            return;
        }

        Player player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }

        serverWorld.levelEvent(2005, pos, 0);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
