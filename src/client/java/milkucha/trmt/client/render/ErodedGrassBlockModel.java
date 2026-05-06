package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ErodedGrassBlockModel implements BakedModel, FabricBakedModel {

    private final BakedModel wrapped;
    private static RenderMaterial cutoutMaterial;
    private static RenderMaterial defaultMaterial;

    private static RenderMaterial cutoutMaterial() {
        if (cutoutMaterial == null) {
            cutoutMaterial = Renderer.get().materialFinder().blendMode(BlendMode.CUTOUT).find();
        }
        return cutoutMaterial;
    }

    private static RenderMaterial defaultMaterial() {
        if (defaultMaterial == null) {
            defaultMaterial = Renderer.get().materialFinder().find();
        }
        return defaultMaterial;
    }

    public ErodedGrassBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() { return false; }

    @Override
    public void emitBlockQuads(QuadEmitter emitter, BlockRenderView world, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, Predicate<Direction> cullTest) {
        Random random = randomSupplier.get();
        for (Direction face : Direction.values()) {
            if (cullTest.test(face)) continue;
            for (BakedQuad quad : wrapped.getQuads(state, face, random)) {
                emitter.fromVanilla(quad, face == Direction.DOWN ? defaultMaterial() : cutoutMaterial(), face);
                emitter.emit();
            }
        }
        for (BakedQuad quad : wrapped.getQuads(state, null, random)) {
            emitter.fromVanilla(quad, cutoutMaterial(), null);
            emitter.emit();
        }
    }

    @Override
    public void emitItemQuads(QuadEmitter emitter, Supplier<Random> randomSupplier) {
        if (wrapped instanceof FabricBakedModel fabricModel && !fabricModel.isVanillaAdapter()) {
            fabricModel.emitItemQuads(emitter, randomSupplier);
        } else {
            for (BakedQuad quad : wrapped.getQuads(null, null, randomSupplier.get())) {
                emitter.fromVanilla(quad, defaultMaterial(), null);
                emitter.emit();
            }
        }
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
        return wrapped.getQuads(state, face, random);
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean hasDepth()             { return wrapped.hasDepth(); }
    @Override public boolean isSideLit()            { return wrapped.isSideLit(); }
    @Override public Sprite getParticleSprite()     { return wrapped.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return wrapped.getTransformation(); }
}
