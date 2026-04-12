package milkucha.trmt.client.render;

import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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
        ((FabricBakedModel) target).emitBlockQuads(world, state, pos, randomSupplier, context);
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
        ChunkErosionMap map = ErosionMapManager.getInstance().getChunkMap(new ChunkPos(pos));
        if (map != null) {
            ErosionEntry entry = map.getEntry(pos);
            if (entry != null && entry.getErosionStage() > 0) {
                BakedModel eroded = ErodedGrassModels.getModel(entry.getErosionStage());
                if (eroded != null) return eroded;
            }
        }
        return vanilla;
    }
}
