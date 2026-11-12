package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets sheep graze on eroded grass. Eating it advances one erosion stage (stage 4 becomes
 * eroded dirt stage 0), mirroring one step of wear, instead of turning it into vanilla dirt.
 */
@Mixin(EatBlockGoal.class)
public class EatBlockGoalMixin {

    // On 26.1 BlockState.is(Block) is the generic TypedInstance.is(T), erased to is(Object).
    private static final String IS_BLOCK = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z";

    @Redirect(method = "canUse", at = @At(value = "INVOKE", target = IS_BLOCK))
    private boolean trmt$canUseOnErodedGrass(BlockState state, Object block) {
        return state.is((Block) block) || (block == Blocks.GRASS_BLOCK && state.is(TRMTBlocks.ERODED_GRASS_BLOCK));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = IS_BLOCK))
    private boolean trmt$tickOnErodedGrass(BlockState state, Object block) {
        return state.is((Block) block) || (block == Blocks.GRASS_BLOCK && state.is(TRMTBlocks.ERODED_GRASS_BLOCK));
    }

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean trmt$eatErodedGrass(Level world, BlockPos pos, BlockState newState, int flags) {
        BlockState current = world.getBlockState(pos);
        if (!current.is(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            return world.setBlock(pos, newState, flags); // vanilla grass -> dirt
        }

        int stage = current.getValue(ErodedGrassBlock.STAGE);
        BlockState result;
        Block resultBlock;
        if (stage < 4) {
            result = current.setValue(ErodedGrassBlock.STAGE, stage + 1);
            resultBlock = TRMTBlocks.ERODED_GRASS_BLOCK;
        } else {
            Direction facing = current.getValue(ErodedGrassBlock.FACING);
            result = TRMTBlocks.ERODED_DIRT.defaultBlockState()
                    .setValue(ErodedDirtBlock.FACING, facing)
                    .setValue(ErodedDirtBlock.STAGE, 0)
                    .setValue(ErodedDirtBlock.FROM_DIRT, false);
            resultBlock = TRMTBlocks.ERODED_DIRT;
        }

        boolean changed = world.setBlock(pos, result, Block.UPDATE_ALL);
        ErosionMapManager manager = ErosionMapManager.get(world);
        manager.removeEntry(pos);
        manager.writeCooldownEntry(pos, resultBlock, world.getGameTime());
        manager.broadcastEntryUpdate(pos, resultBlock);
        return changed;
    }
}
