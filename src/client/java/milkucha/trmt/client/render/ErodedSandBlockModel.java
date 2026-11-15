package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
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
 * FabricBakedModel wrapper for the sunken eroded sand states (stage 1-4).
 * The model keeps ambient occlusion (side faces match vanilla neighbours), but the
 * lowered top face is rendered flat-lit: vanilla-style AO on an inset top face samples
 * the full-cube neighbours at the face's own level and darkens it by ~40-60 %.
 * Only the material's AO flag changes; blend mode stays DEFAULT (the block's render layer).
 * Needs a FRAPI renderer (Indigo, or Indium with Sodium); without one getQuads() is used
 * and the top face gets AO like the rest of the model.
 */
public class ErodedSandBlockModel implements BakedModel {

    private final BakedModel wrapped;
    // Vanilla JSON quads all carry the renderer's standard material, so one cached pair suffices.
    // Chunk meshing is multi-threaded: base and result live in one immutable holder.
    private record Cached(RenderMaterial base, RenderMaterial noAo) {}
    private static volatile Cached cached;

    private static RenderMaterial noAo(RenderMaterial base) {
        Cached c = cached;
        if (c == null || c.base() != base) {
            MaterialFinder finder = RendererAccess.INSTANCE.getRenderer().materialFinder();
            c = new Cached(base, finder.copyFrom(base).ambientOcclusion(TriState.FALSE).find());
            cached = c;
        }
        return c.noAo();
    }

    public ErodedSandBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() { return false; }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                               Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                               RenderContext context) {
        context.pushTransform(quad -> {
            if (quad.nominalFace() == Direction.UP) {
                quad.material(noAo(quad.material()));
            }
            return true;
        });
        wrapped.emitBlockQuads(world, state, pos, randomSupplier, context);
        context.popTransform();
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                              RenderContext context) {
        wrapped.emitItemQuads(stack, randomSupplier, context);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face,
                                    net.minecraft.util.math.random.Random random) {
        return wrapped.getQuads(state, face, random);
    }

    @Override public boolean useAmbientOcclusion() { return wrapped.useAmbientOcclusion(); }
    @Override public boolean hasDepth()             { return wrapped.hasDepth(); }
    @Override public boolean isSideLit()            { return wrapped.isSideLit(); }
    @Override public boolean isBuiltin()            { return wrapped.isBuiltin(); }
    @Override public Sprite getParticleSprite()     { return wrapped.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return wrapped.getTransformation(); }
    @Override public ModelOverrideList getOverrides()        { return wrapped.getOverrides(); }
}
