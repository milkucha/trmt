package milkucha.trmt.client.render;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Simple delegating BakedModel wrapper for ErodedGrassBlock block-state models.
 * In NeoForge, the cutout render type is specified via {@code "render_type": "minecraft:cutout_mipped"}
 * in the model JSON files, so this wrapper mainly exists as a hook point for the
 * {@link ErodedGrassBlockModels} model modification pass.
 */
public class ErodedGrassBlockModel implements BakedModel {

    private final BakedModel wrapped;

    public ErodedGrassBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) { return wrapped.getQuads(state, face, random); }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return wrapped.isGui3d(); }
    @Override public boolean usesBlockLight() { return wrapped.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return wrapped.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return wrapped.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return wrapped.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return wrapped.getOverrides(); }
}
