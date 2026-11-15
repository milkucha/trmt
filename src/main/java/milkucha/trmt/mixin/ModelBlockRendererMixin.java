package milkucha.trmt.mixin;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Per-face AO off (C7): the lowered top face of sunken eroded sand (stage 1-4) carries
 * {@code "neoforge_data": {"ambient_occlusion": false}} in its model JSON, so its BakedQuad has
 * {@code hasAmbientOcclusion() == false}. NeoForge 21.1's patched vanilla AO path
 * ({@code renderModelFaceAO}) already renders such quads flat via
 * {@code ClientHooks.calculateFaceWithoutAO}, but samples the light at the block's own position
 * when the face is not flush with the block boundary (isFaceCubic false). A model with
 * {@code "ambientocclusion": false} (the look before C7) samples the cull-face neighbour instead
 * (the block above for the up face). This forces the neighbour sample for TRMT blocks' no-AO quads
 * only, so other mods' quads keep NeoForge's behaviour. Only reached when smooth lighting is on.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @ModifyArg(
        method = "renderModelFaceAO",
        at = @At(value = "INVOKE",
                 target = "Lnet/neoforged/neoforge/client/ClientHooks;calculateFaceWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/model/BakedQuad;Z[F[I)Z"),
        index = 4
    )
    private boolean trmt$cullFaceNeighbourLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                                BakedQuad quad, boolean isFaceCubic,
                                                float[] brightness, int[] lightmap) {
        if (isFaceCubic || quad.hasAmbientOcclusion()) {
            return isFaceCubic;
        }
        return "trmt".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }
}
