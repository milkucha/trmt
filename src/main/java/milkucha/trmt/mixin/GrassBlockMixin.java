package milkucha.trmt.mixin;

import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpreadableBlock.class)
public class GrassBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void trmt$onRandomTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap map = manager.getChunkMap(new ChunkPos(pos));
        if (map == null) return;

        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;

        // Entry exists → suppress vanilla spreading and vanilla dirt-in-darkness conversion.
        ci.cancel();

        int stage = entry.getErosionStage();
        if (stage == 0) return; // pristine entry — nothing to de-erode

        long currentTime = world.getTime();
        long timeout = BlockThresholds.getGrassDeErosionTimeout(stage);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (currentTime - entry.getLastTouchedGameTime() <= timeout) return;

        // Entry is stale — revert one stage.
        if (stage == 1) {
            manager.removeEntry(pos); // back to pristine; broadcasts stage=0 to clients
        } else {
            manager.revertGrassStage(pos, currentTime); // updates entry in place with cooldown
            manager.markForRerender(pos);               // broadcasts new lower stage to clients
        }
    }
}
