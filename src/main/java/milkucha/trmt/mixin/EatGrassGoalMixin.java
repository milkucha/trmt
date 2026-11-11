package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.EatGrassGoal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets sheep graze on eroded grass. Eating it advances one erosion stage (stage 4 becomes
 * eroded dirt stage 0), mirroring one step of wear, instead of turning it into vanilla dirt.
 */
@Mixin(EatGrassGoal.class)
public class EatGrassGoalMixin {

    private static final String IS_OF = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z";

    @Redirect(method = "canStart", at = @At(value = "INVOKE", target = IS_OF))
    private boolean trmt$canStartOnErodedGrass(BlockState state, Block block) {
        return state.isOf(block) || (block == Blocks.GRASS_BLOCK && state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = IS_OF))
    private boolean trmt$tickOnErodedGrass(BlockState state, Block block) {
        return state.isOf(block) || (block == Blocks.GRASS_BLOCK && state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK));
    }

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean trmt$eatErodedGrass(World world, BlockPos pos, BlockState newState, int flags) {
        BlockState current = world.getBlockState(pos);
        if (!current.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            return world.setBlockState(pos, newState, flags); // vanilla grass -> dirt
        }

        int stage = current.get(ErodedGrassBlock.STAGE);
        BlockState result;
        Block resultBlock;
        if (stage < 4) {
            result = current.with(ErodedGrassBlock.STAGE, stage + 1);
            resultBlock = TRMTBlocks.ERODED_GRASS_BLOCK;
        } else {
            Direction facing = current.get(ErodedGrassBlock.FACING);
            result = TRMTBlocks.ERODED_DIRT.getDefaultState()
                    .with(ErodedDirtBlock.FACING, facing)
                    .with(ErodedDirtBlock.STAGE, 0)
                    .with(ErodedDirtBlock.FROM_DIRT, false);
            resultBlock = TRMTBlocks.ERODED_DIRT;
        }

        boolean changed = world.setBlockState(pos, result, Block.NOTIFY_ALL);
        ErosionMapManager manager = ErosionMapManager.get(world);
        manager.removeEntry(pos);
        manager.writeCooldownEntry(pos, resultBlock, world.getTime());
        manager.broadcastEntryUpdate(pos, resultBlock);
        return changed;
    }
}
