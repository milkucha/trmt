package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
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
 * FabricBakedModel wrapper for ErodedGrassBlock block-state models.
 * Applies CUTOUT material to every non-DOWN quad so that transparent pixels in the
 * eroded-top overlay and the grass_block_side_overlay are discarded correctly.
 * Stage texture selection and FACING Y-rotation are already baked into the wrapped
 * model by the block-state system, so this class needs no per-position lookups.
 */
public class ErodedGrassBlockModel implements BakedModel {

    private final BakedModel wrapped;
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

    public ErodedGrassBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() { return false; }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                               Supplier<net.minecraft.util.math.random.Random> randomSupplier,
                               RenderContext context) {
        context.pushTransform(quad -> {
            if (quad.nominalFace() != Direction.DOWN) {
                quad.material(cutoutMaterial());
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

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean hasDepth()             { return wrapped.hasDepth(); }
    @Override public boolean isSideLit()            { return wrapped.isSideLit(); }
    @Override public boolean isBuiltin()            { return wrapped.isBuiltin(); }
    @Override public Sprite getParticleSprite()     { return wrapped.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return wrapped.getTransformation(); }
    @Override public ModelOverrideList getOverrides()        { return wrapped.getOverrides(); }
}
