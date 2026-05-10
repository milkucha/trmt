package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpreadingSnowyBlock.class)
public class GrassBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void trmt$onRandomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap map = manager.getChunkMap(ChunkPos.containing(pos));
        if (map == null) return;
        // Entry exists → suppress vanilla spreading while this block is being walked on.
        // De-erosion of eroded grass is handled by ErodedGrassBlock.randomTick.
        if (map.getEntry(pos) != null) ci.cancel();
    }
}
