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
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long currentTime = serverLevel.getGameTime();

        if (block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) {
            int stage = state.getValue(ErodedGrassBlock.STAGE);
            if (stage > 0) {
                level.setBlock(pos, state.setValue(ErodedGrassBlock.STAGE, stage - 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), currentTime);
            } else {
                level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                manager.removeEntry(pos);
            }
        } else if (block == TRMTBlocks.ERODED_DIRT.get()) {
            int stage = state.getValue(ErodedDirtBlock.STAGE);
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            if (stage > 0) {
                level.setBlock(pos, state.setValue(ErodedDirtBlock.STAGE, stage - 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), currentTime);
            } else {
                level.setBlock(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
                        .setValue(ErodedGrassBlock.FACING, facing)
                        .setValue(ErodedGrassBlock.STAGE, 4),
                    Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), currentTime);
            }
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            level.setBlock(pos,
                TRMTBlocks.ERODED_DIRT.get().defaultBlockState()
                    .setValue(ErodedDirtBlock.FACING, facing)
                    .setValue(ErodedDirtBlock.STAGE, 3),
                Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), currentTime);
        } else {
            return;
        }

        Player player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }

        serverLevel.levelEvent(2005, pos, 0);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
