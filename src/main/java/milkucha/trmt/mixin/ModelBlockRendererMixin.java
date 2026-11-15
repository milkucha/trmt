package milkucha.trmt.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Per-face AO off (C7): the lowered top face of sunken eroded sand (stage 1-4) carries
 * {@code "neoforge_data": {"ambient_occlusion": false}} in its model JSON, so its BakedQuad has
 * {@code materialInfo().ambientOcclusion() == false}. NeoForge 26.1's patched {@code tesselateAmbientOcclusion}
 * renders such culled quads with {@code lighter.prepareQuadFlat(..., lightCoords = -1, ...)} (ordinal 1), and -1
 * makes the lighter sample the cull-face neighbour only when the face is flush with the block edge or the
 * collision shape is a full block; for the sunken top (y 14/12/10, collision 10 high) it samples the sand block
 * itself. A model with {@code "ambientocclusion": false} (the look before C7, vanilla {@code tesselateFlat})
 * passes {@code lighter.getLightCoords(state, level, pos.relative(direction))}, the block above. For TRMT
 * blocks this passes that same neighbour light; shading inside {@code prepareQuadFlat} is the same in both
 * paths. Other mods' quads are untouched. Only reached when smooth lighting is on.
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @Shadow
    @Final
    private BlockModelLighter lighter;

    @Unique
    private final BlockPos.MutableBlockPos trmt$lightPos = new BlockPos.MutableBlockPos();

    @ModifyArg(
        method = "tesselateAmbientOcclusion",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockModelLighter;prepareQuadFlat(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;ILnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V",
            ordinal = 1
        ),
        index = 3
    )
    private int trmt$cullFaceNeighbourLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                            int lightCoords, BakedQuad quad, QuadInstance outputInstance) {
        if (lightCoords != BlockModelLighter.CHECK_LIGHT
                || !"trmt".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return lightCoords;
        }
        return this.lighter.getLightCoords(state, level, this.trmt$lightPos.setWithOffset(pos, quad.direction()));
    }
}
