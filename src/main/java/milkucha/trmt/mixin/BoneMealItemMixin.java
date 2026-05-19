package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
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
        Block block = state.getBlock();

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long currentTime = serverWorld.getTime();

        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            int stage = state.get(ErodedGrassBlock.STAGE);
            if (stage > 0) {
                world.setBlockState(pos, state.with(ErodedGrassBlock.STAGE, stage - 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
            } else {
                // Stage 0 → fully recovered, revert to vanilla grass.
                world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
            }
        } else if (block == TRMTBlocks.ERODED_DIRT) {
            int stage = state.get(ErodedDirtBlock.STAGE);
            Direction facing = state.get(ErodedDirtBlock.FACING);
            ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
            ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;
            Block originalBlock = entry != null ? entry.getOriginalBlock() : null;
            if (stage > 0) {
                world.setBlockState(pos, state.with(ErodedDirtBlock.STAGE, stage - 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, currentTime,
                        originalBlock != null ? originalBlock : Blocks.GRASS_BLOCK);
            } else {
                if (originalBlock == Blocks.DIRT || originalBlock == Blocks.COARSE_DIRT) {
                    world.setBlockState(pos, originalBlock.getDefaultState(), Block.NOTIFY_ALL);
                    manager.removeEntry(pos);
                } else {
                    // Stage 0 → revert to most-eroded grass stage, preserving facing.
                    world.setBlockState(pos,
                            TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                                    .with(ErodedGrassBlock.FACING, facing)
                                    .with(ErodedGrassBlock.STAGE, 4),
                            Block.NOTIFY_ALL);
                    manager.removeEntry(pos);
                    manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
                }
            }
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            Direction facing = state.get(ErodedDirtBlock.FACING);
            ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
            ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;
            Block originalBlock = entry != null ? entry.getOriginalBlock() : null;
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_DIRT.getDefaultState()
                            .with(ErodedDirtBlock.FACING, facing)
                            .with(ErodedDirtBlock.STAGE, 3),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, currentTime,
                    originalBlock != null ? originalBlock : Blocks.GRASS_BLOCK);
        } else {
            return;
        }

        PlayerEntity player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getStack().decrement(1);
        }

        serverWorld.syncWorldEvent(2005, pos, 0);
        cir.setReturnValue(ActionResult.SUCCESS);
    }
}
