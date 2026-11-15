package milkucha.trmt.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Per-face AO off (C7): the lowered top face of sunken eroded sand (stage 1-4) carries
 * {@code "neoforge_data": {"ambient_occlusion": false}} in its model JSON, so its BakedQuad has
 * {@code hasAmbientOcclusion() == false}. NeoForge 21.11's patched {@code renderModelFaceAO} renders
 * such quads with {@code renderModelQuadFlat(..., light = -1, calculateLight = true, ...)}, which samples
 * the light at the cull-face neighbour only when the face is flush with the block edge or the collision
 * shape is a full block; for the sunken top (y 14/12/10, collision 10 high) it samples the sand block
 * itself. A model with {@code "ambientocclusion": false} (the look before C7, vanilla
 * {@code tesselateWithoutAO}) samples the cull-face neighbour (the block above). For TRMT blocks' no-AO
 * quads this passes that neighbour light with {@code calculateLight = false}, as {@code tesselateWithoutAO}
 * does; shading ({@code applyFlatQuadBrightness}) is the same in both paths. Other mods' quads are untouched.
 * Only reached when smooth lighting is on.
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @Unique
    private static final String QUAD_FLAT =
        "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;renderModelQuadFlat(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;IIZLcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/block/model/BakedQuad;Lnet/minecraft/client/renderer/block/ModelBlockRenderer$CommonRenderStorage;)V";

    @ModifyArg(method = "renderModelFaceAO", at = @At(value = "INVOKE", target = QUAD_FLAT), index = 3)
    private int trmt$cullFaceNeighbourLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                            int light, int overlay, boolean calculateLight,
                                            PoseStack pose, VertexConsumer consumer, BakedQuad quad,
                                            ModelBlockRenderer.CommonRenderStorage storage) {
        if (!trmt$isTrmt(state)) {
            return light;
        }
        return storage.cache.getLightColor(state, level, storage.scratchPos.setWithOffset(pos, quad.direction()));
    }

    @ModifyArg(method = "renderModelFaceAO", at = @At(value = "INVOKE", target = QUAD_FLAT), index = 5)
    private boolean trmt$skipGeometryLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                           int light, int overlay, boolean calculateLight,
                                           PoseStack pose, VertexConsumer consumer, BakedQuad quad,
                                           ModelBlockRenderer.CommonRenderStorage storage) {
        return calculateLight && !trmt$isTrmt(state);
    }

    @Unique
    private static boolean trmt$isTrmt(BlockState state) {
        return "trmt".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }
}
