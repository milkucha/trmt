package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpreadingSnowyDirtBlock.class)
public class GrassBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void trmt$onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap map = manager.getChunkMap(new ChunkPos(pos));
        if (map == null) return;
        if (map.getEntry(pos) != null) ci.cancel();
    }
}
