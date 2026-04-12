package milkucha.trmt.client.render;

import milkucha.trmt.client.network.ClientErosionCache;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.function.Supplier;

/**
 * Wraps the vanilla grass_block baked model. For terrain rendering (via Indigo/FabricBakedModel),
 * substitutes the appropriate eroded model when the block position has an erosion stage > 0.
 * Falls back to the vanilla model for stage 0 and all non-terrain uses.
 */
public class GrassErosionProxyModel implements BakedModel, FabricBakedModel {

    private final BakedModel vanilla;

    /** Lazily initialised; Renderer may not be present at class-load time. */
    private static RenderMaterial cutoutMaterial;

    private static RenderMaterial cutoutMaterial() {
        if (cutoutMaterial == null) {
            cutoutMaterial = RendererAccess.INSTANCE.getRenderer()
                    .materialFinder()
                    .blendMode(BlendMode.CUTOUT)
                    .find();
        }
        return cutoutMaterial;
    }

    public GrassErosionProxyModel(BakedModel vanilla) {
        this.vanilla = vanilla;
    }

    // --- FabricBakedModel (terrain path, called by Indigo with BlockPos) ---

    @Override
    public boolean isVanillaAdapter() {
        return false; // Must return false so Indigo calls emitBlockQuads.
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                               Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                               RenderContext context) {
        BakedModel target = resolveModel(pos);
        if (target == vanilla) {
            ((FabricBakedModel) target).emitBlockQuads(world, state, pos, randomSupplier, context);
            return;
        }
        int rotation = uvRotationFor(pos); // 0=0°, 1=90°, 2=180°, 3=270°
        RenderMaterial cutout = cutoutMaterial();
        context.pushTransform(quad -> {
            Direction face = quad.nominalFace();
            // Apply CUTOUT to UP and all side faces so transparent pixels in the eroded
            // top overlay and the grass_block_side_overlay are discarded correctly.
            if (face != null && face != Direction.DOWN) {
                quad.material(cutout);
            }
            if (face == Direction.UP && rotation != 0) {
                rotateUvs(quad, rotation);
            }
            return true;
        });
        ((FabricBakedModel) target).emitBlockQuads(world, state, pos, randomSupplier, context);
        context.popTransform();
    }

    /** Permutes the UV coordinates of all 4 vertices by {@code steps} positions (each step = 90° CW). */
    private static void rotateUvs(MutableQuadView quad, int steps) {
        float[] us = new float[4];
        float[] vs = new float[4];
        for (int i = 0; i < 4; i++) {
            us[i] = quad.u(i);
            vs[i] = quad.v(i);
        }
        for (int i = 0; i < 4; i++) {
            int src = (i + steps) & 3;
            quad.uv(i, us[src], vs[src]);
        }
    }

    /** Deterministic 0–3 from block position — never flickers across frames. */
    private static int uvRotationFor(BlockPos pos) {
        int h = (pos.getX() * 1619) ^ (pos.getZ() * 31337);
        return ((h >>> 4) ^ (h >>> 8)) & 3;
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                               RenderContext context) {
        ((FabricBakedModel) vanilla).emitItemQuads(stack, randomSupplier, context);
    }

    // --- Vanilla BakedModel delegates (used by HUD, item rendering, etc.) ---

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face, net.minecraft.util.math.random.Random random) {
        return vanilla.getQuads(state, face, random);
    }

    @Override public boolean useAmbientOcclusion() { return vanilla.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return vanilla.hasDepth(); }
    @Override public boolean isSideLit() { return vanilla.isSideLit(); }
    @Override public boolean isBuiltin() { return vanilla.isBuiltin(); }
    @Override public Sprite getParticleSprite() { return vanilla.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return vanilla.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return vanilla.getOverrides(); }

    // --- Helpers ---

    private BakedModel resolveModel(BlockPos pos) {
        int stage = ClientErosionCache.getInstance().getStage(pos);
        if (stage > 0) {
            BakedModel eroded = ErodedGrassModels.getModel(stage);
            if (eroded != null) return eroded;
        }
        return vanilla;
    }
}
