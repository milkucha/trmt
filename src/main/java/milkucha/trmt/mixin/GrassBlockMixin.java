package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ChunkErosionMap;
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
        // Entry exists → suppress vanilla spreading while this block is being walked on.
        // De-erosion of eroded grass is handled by ErodedGrassBlock.randomTick.
        if (map.getEntry(pos) != null) ci.cancel();
    }
}
